package com.provectus.kafka.ui.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.Sets;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.exception.TopicMetadataException;
import com.provectus.kafka.ui.exception.TopicNotFoundException;
import com.provectus.kafka.ui.exception.TopicRecreationException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.model.ClusterFeature;
import com.provectus.kafka.ui.model.InternalLogDirStats;
import com.provectus.kafka.ui.model.InternalPartition;
import com.provectus.kafka.ui.model.InternalPartitionsOffsets;
import com.provectus.kafka.ui.model.InternalReplica;
import com.provectus.kafka.ui.model.InternalTopic;
import com.provectus.kafka.ui.model.InternalTopicConfig;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.Metrics;
import com.provectus.kafka.ui.model.PartitionsIncreaseDTO;
import com.provectus.kafka.ui.model.PartitionsIncreaseResponseDTO;
import com.provectus.kafka.ui.model.ReplicationFactorChangeDTO;
import com.provectus.kafka.ui.model.ReplicationFactorChangeResponseDTO;
import com.provectus.kafka.ui.model.Statistics;
import com.provectus.kafka.ui.model.TopicCreationDTO;
import com.provectus.kafka.ui.model.TopicUpdateDTO;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitionReassignment;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.ProducerState;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Kafka 主题管理服务。
 *
 * <p>提供主题的完整生命周期管理功能，包括：
 * <ul>
 *   <li>主题的查询、创建、删除、克隆和重建</li>
 *   <li>主题配置的查看与更新</li>
 *   <li>分区数量的扩容</li>
 *   <li>副本因子的变更（基于 Kafka 5.4+ 的分区重分配）</li>
 *   <li>主题分页列表的获取</li>
 *   <li>生产者状态的查询</li>
 * </ul>
 *
 * <p>所有操作均基于 {@link ReactiveAdminClient} 进行异步调用，
 * 并通过 {@link StatisticsCache} 缓存集群统计信息以提升查询性能。
 */
@Service
@RequiredArgsConstructor
public class TopicsService {

  private final AdminClientService adminClientService;
  private final StatisticsCache statisticsCache;
  private final ClustersProperties clustersProperties;
  @Value("${topic.recreate.maxRetries:15}")
  private int recreateMaxRetries;
  @Value("${topic.recreate.delay.seconds:1}")
  private int recreateDelayInSeconds;
  @Value("${topic.load.after.create.maxRetries:10}")
  private int loadTopicAfterCreateRetries;
  @Value("${topic.load.after.create.delay.ms:500}")
  private int loadTopicAfterCreateDelayInMs;

  /**
   * 批量加载主题的详细信息。
   *
   * <p>通过 AdminClient 查询主题的描述信息和配置，并结合统计缓存中的指标和日志目录信息
   * 构建完整的主题对象列表。同时更新统计缓存。
   *
   * @param c      目标 Kafka 集群
   * @param topics 需要加载的主题名称列表
   * @return 包含 {@link InternalTopic} 列表的 Mono，若列表为空则返回空列表
   */
  public Mono<List<InternalTopic>> loadTopics(KafkaCluster c, List<String> topics) {
    if (topics.isEmpty()) {
      return Mono.just(List.of());
    }
    return adminClientService.get(c)
        .flatMap(ac ->
            ac.describeTopics(topics).zipWith(ac.getTopicsConfig(topics, false),
                (descriptions, configs) -> {
                  statisticsCache.update(c, descriptions, configs);
                  return getPartitionOffsets(descriptions, ac).map(offsets -> {
                    var metrics = statisticsCache.get(c);
                    return createList(
                        topics,
                        descriptions,
                        configs,
                        offsets,
                        metrics.getMetrics(),
                        metrics.getLogDirInfo()
                    );
                  });
                })).flatMap(Function.identity());
  }

  /**
   * 加载单个主题的详细信息。
   *
   * @param c         目标 Kafka 集群
   * @param topicName 主题名称
   * @return 包含 {@link InternalTopic} 的 Mono，若主题不存在则触发 {@link TopicNotFoundException}
   */
  private Mono<InternalTopic> loadTopic(KafkaCluster c, String topicName) {
    return loadTopics(c, List.of(topicName))
        .flatMap(lst -> lst.stream().findFirst()
            .map(Mono::just)
            .orElse(Mono.error(TopicNotFoundException::new)));
  }

  /**
   *  After creation topic can be invisible via API for some time.
   *  To workaround this, we retyring topic loading until it becomes visible.
   */
  private Mono<InternalTopic> loadTopicAfterCreation(KafkaCluster c, String topicName) {
    return loadTopic(c, topicName)
        .retryWhen(
            Retry
                .fixedDelay(
                    loadTopicAfterCreateRetries,
                    Duration.ofMillis(loadTopicAfterCreateDelayInMs)
                )
                .filter(TopicNotFoundException.class::isInstance)
                .onRetryExhaustedThrow((spec, sig) ->
                    new TopicMetadataException(
                        String.format(
                            "Error while loading created topic '%s' - topic is not visible via API "
                                + "after waiting for %d ms.",
                            topicName,
                            loadTopicAfterCreateDelayInMs * loadTopicAfterCreateRetries)))
        );
  }

  /**
   * 根据描述、配置、偏移量和指标信息构建主题对象列表。
   *
   * @param orderedNames      有序的主题名称列表（决定返回结果的顺序）
   * @param descriptions      主题描述信息映射
   * @param configs           主题配置信息映射
   * @param partitionsOffsets 分区偏移量信息
   * @param metrics           集群指标数据
   * @param logDirInfo        日志目录统计信息
   * @return 构建完成的 {@link InternalTopic} 列表
   */
  private List<InternalTopic> createList(List<String> orderedNames,
                                         Map<String, TopicDescription> descriptions,
                                         Map<String, List<ConfigEntry>> configs,
                                         InternalPartitionsOffsets partitionsOffsets,
                                         Metrics metrics,
                                         InternalLogDirStats logDirInfo) {
    return orderedNames.stream()
        .filter(descriptions::containsKey)
        .map(t -> InternalTopic.from(
            descriptions.get(t),
            configs.getOrDefault(t, List.of()),
            partitionsOffsets,
            metrics,
            logDirInfo,
            clustersProperties.getInternalTopicPrefix()
        ))
        .collect(toList());
  }

  /**
   * 获取主题分区的最早和最新偏移量。
   *
   * @param descriptionsMap 主题描述信息映射，用于确定需要查询的分区
   * @param ac              响应式 AdminClient 实例
   * @return 包含各分区最早和最新偏移量的 {@link InternalPartitionsOffsets}
   */
  private Mono<InternalPartitionsOffsets> getPartitionOffsets(Map<String, TopicDescription>
                                                                  descriptionsMap,
                                                              ReactiveAdminClient ac) {
    var descriptions = descriptionsMap.values();
    return ac.listOffsets(descriptions, OffsetSpec.earliest())
        .zipWith(ac.listOffsets(descriptions, OffsetSpec.latest()),
            (earliest, latest) ->
                Sets.intersection(earliest.keySet(), latest.keySet())
                    .stream()
                    .map(tp ->
                        Map.entry(tp,
                            new InternalPartitionsOffsets.Offsets(
                                earliest.get(tp), latest.get(tp))))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
        .map(InternalPartitionsOffsets::new);
  }

  /**
   * 获取指定主题的详细信息。
   *
   * @param cluster   目标 Kafka 集群
   * @param topicName 主题名称
   * @return 包含 {@link InternalTopic} 的 Mono
   */
  public Mono<InternalTopic> getTopicDetails(KafkaCluster cluster, String topicName) {
    return loadTopic(cluster, topicName);
  }

  /**
   * 获取指定主题的配置项列表。
   *
   * <p>处理两种场景：
   * <ol>
   *   <li>主题不存在或不可见 — 抛出 {@link TopicNotFoundException}</li>
   *   <li>主题存在但无 DESCRIBE_CONFIG 权限 — 返回空列表</li>
   * </ol>
   *
   * @param cluster   目标 Kafka 集群
   * @param topicName 主题名称
   * @return 包含配置项列表的 Mono
   */
  public Mono<List<ConfigEntry>> getTopicConfigs(KafkaCluster cluster, String topicName) {
    // there 2 case that we cover here:
    // 1. topic not found/visible - describeTopic() will be empty and we will throw TopicNotFoundException
    // 2. topic is visible, but we don't have DESCRIBE_CONFIG permission - we should return empty list
    return adminClientService.get(cluster)
        .flatMap(ac -> ac.describeTopic(topicName)
            .switchIfEmpty(Mono.error(new TopicNotFoundException()))
            .then(ac.getTopicsConfig(List.of(topicName), true))
            .map(m -> m.values().stream().findFirst().orElse(List.of())));
  }

  /**
   * 创建主题的内部实现。
   *
   * <p>通过 AdminClient 创建主题，创建完成后通过重试机制加载主题信息，
   * 以应对主题在创建后短暂不可见的情况。
   *
   * @param c           目标 Kafka 集群
   * @param adminClient AdminClient 实例
   * @param topicData   主题创建参数
   * @return 包含新创建的 {@link InternalTopic} 的 Mono
   */
  private Mono<InternalTopic> createTopic(KafkaCluster c, ReactiveAdminClient adminClient, TopicCreationDTO topicData) {
    return adminClient.createTopic(
            topicData.getName(),
            topicData.getPartitions(),
            topicData.getReplicationFactor(),
            topicData.getConfigs())
        .thenReturn(topicData)
        .onErrorMap(t -> new TopicMetadataException(t.getMessage(), t))
        .then(loadTopicAfterCreation(c, topicData.getName()));
  }

  /**
   * 创建新的 Kafka 主题。
   *
   * @param cluster       目标 Kafka 集群
   * @param topicCreation 主题创建参数（名称、分区数、副本因子、配置项）
   * @return 包含新创建的 {@link InternalTopic} 的 Mono
   */
  public Mono<InternalTopic> createTopic(KafkaCluster cluster, TopicCreationDTO topicCreation) {
    return adminClientService.get(cluster)
        .flatMap(ac -> createTopic(cluster, ac, topicCreation));
  }

  /**
   * 重建指定主题（先删除后重新创建）。
   *
   * <p>保留原主题的分区数、副本因子和配置信息。删除后会等待指定延迟再执行创建，
   * 并通过重试机制处理 {@link TopicExistsException}（主题删除后可能短暂存在）。
   *
   * @param cluster   目标 Kafka 集群
   * @param topicName 需要重建的主题名称
   * @return 包含重建后的 {@link InternalTopic} 的 Mono
   */
  public Mono<InternalTopic> recreateTopic(KafkaCluster cluster, String topicName) {
    return loadTopic(cluster, topicName)
        .flatMap(t -> deleteTopic(cluster, topicName)
            .thenReturn(t)
            .delayElement(Duration.ofSeconds(recreateDelayInSeconds))
            .flatMap(topic ->
                adminClientService.get(cluster)
                    .flatMap(ac ->
                        ac.createTopic(
                                topic.getName(),
                                topic.getPartitionCount(),
                                topic.getReplicationFactor(),
                                topic.getTopicConfigs()
                                    .stream()
                                    .collect(Collectors.toMap(InternalTopicConfig::getName,
                                        InternalTopicConfig::getValue))
                            )
                            .thenReturn(topicName)
                    )
                    .retryWhen(
                        Retry.fixedDelay(recreateMaxRetries, Duration.ofSeconds(recreateDelayInSeconds))
                            .filter(TopicExistsException.class::isInstance)
                            .onRetryExhaustedThrow((a, b) ->
                                new TopicRecreationException(topicName,
                                    recreateMaxRetries * recreateDelayInSeconds))
                    )
                    .flatMap(a -> loadTopicAfterCreation(cluster, topicName))
            )
        );
  }

  private Mono<InternalTopic> updateTopic(KafkaCluster cluster,
                                          String topicName,
                                          TopicUpdateDTO topicUpdate) {
    return adminClientService.get(cluster)
        .flatMap(ac ->
            ac.updateTopicConfig(topicName, topicUpdate.getConfigs())
                .then(loadTopic(cluster, topicName)));
  }

  /**
   * 更新主题配置。
   *
   * @param cl          目标 Kafka 集群
   * @param topicName   主题名称
   * @param topicUpdate 包含新配置的 {@link TopicUpdateDTO} 的 Mono
   * @return 包含更新后的 {@link InternalTopic} 的 Mono
   */
  public Mono<InternalTopic> updateTopic(KafkaCluster cl, String topicName,
                                    Mono<TopicUpdateDTO> topicUpdate) {
    return topicUpdate
        .flatMap(t -> updateTopic(cl, topicName, t));
  }

  private Mono<InternalTopic> changeReplicationFactor(
      KafkaCluster cluster,
      ReactiveAdminClient adminClient,
      String topicName,
      Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments
  ) {
    return adminClient.alterPartitionReassignments(reassignments)
        .then(loadTopic(cluster, topicName));
  }

  /**
   * Change topic replication factor, works on brokers versions 5.4.x and higher
   */
  /**
   * 变更主题的副本因子（适用于 Kafka 5.4.x 及更高版本）。
   *
   * <p>通过分区重分配机制实现副本因子的增减：
   * <ul>
   *   <li>增加副本因子时，按 Broker 使用率从低到高选择新副本</li>
   *   <li>减少副本因子时，按 Broker 使用率从高到低移除非 Leader 副本</li>
   * </ul>
   *
   * @param cluster                 目标 Kafka 集群
   * @param topicName               主题名称
   * @param replicationFactorChange 副本因子变更参数（包含目标副本因子总数）
   * @return 包含变更结果的 {@link ReplicationFactorChangeResponseDTO} 的 Mono
   * @throws ValidationException 若请求的副本因子与当前值相同、小于等于 0 或超过 Broker 数量
   */
  public Mono<ReplicationFactorChangeResponseDTO> changeReplicationFactor(
      KafkaCluster cluster,
      String topicName,
      ReplicationFactorChangeDTO replicationFactorChange) {
    return loadTopic(cluster, topicName).flatMap(topic -> adminClientService.get(cluster)
        .flatMap(ac -> {
          Integer actual = topic.getReplicationFactor();
          Integer requested = replicationFactorChange.getTotalReplicationFactor();
          Integer brokersCount = statisticsCache.get(cluster).getClusterDescription()
              .getNodes().size();

          if (requested.equals(actual)) {
            return Mono.error(
                new ValidationException(
                    String.format("Topic already has replicationFactor %s.", actual)));
          }
          if (requested <= 0) {
            return Mono.error(
                new ValidationException(
                    String.format("Requested replication factor (%s) should be greater or equal to 1.", requested)));
          }
          if (requested > brokersCount) {
            return Mono.error(
                new ValidationException(
                    String.format("Requested replication factor %s more than brokers count %s.",
                        requested, brokersCount)));
          }
          return changeReplicationFactor(cluster, ac, topicName,
              getPartitionsReassignments(cluster, topic,
                  replicationFactorChange));
        })
        .map(t -> new ReplicationFactorChangeResponseDTO()
            .topicName(t.getName())
            .totalReplicationFactor(t.getReplicationFactor())));
  }

  private Map<TopicPartition, Optional<NewPartitionReassignment>> getPartitionsReassignments(
      KafkaCluster cluster,
      InternalTopic topic,
      ReplicationFactorChangeDTO replicationFactorChange) {
    // Current assignment map (Partition number -> List of brokers)
    Map<Integer, List<Integer>> currentAssignment = getCurrentAssignment(topic);
    // Brokers map (Broker id -> count)
    Map<Integer, Integer> brokersUsage = getBrokersMap(cluster, currentAssignment);
    int currentReplicationFactor = topic.getReplicationFactor();

    // If we should to increase Replication factor
    if (replicationFactorChange.getTotalReplicationFactor() > currentReplicationFactor) {
      // For each partition
      for (var assignmentList : currentAssignment.values()) {
        // Get brokers list sorted by usage
        var brokers = brokersUsage.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .collect(toList());

        // Iterate brokers and try to add them in assignment
        // while partition replicas count != requested replication factor
        for (Integer broker : brokers) {
          if (!assignmentList.contains(broker)) {
            assignmentList.add(broker);
            brokersUsage.merge(broker, 1, Integer::sum);
          }
          if (assignmentList.size() == replicationFactorChange.getTotalReplicationFactor()) {
            break;
          }
        }
        if (assignmentList.size() != replicationFactorChange.getTotalReplicationFactor()) {
          throw new ValidationException("Something went wrong during adding replicas");
        }
      }

      // If we should to decrease Replication factor
    } else if (replicationFactorChange.getTotalReplicationFactor() < currentReplicationFactor) {
      for (Map.Entry<Integer, List<Integer>> assignmentEntry : currentAssignment.entrySet()) {
        var partition = assignmentEntry.getKey();
        var brokers = assignmentEntry.getValue();

        // Get brokers list sorted by usage in reverse order
        var brokersUsageList = brokersUsage.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .map(Map.Entry::getKey)
            .collect(toList());

        // Iterate brokers and try to remove them from assignment
        // while partition replicas count != requested replication factor
        for (Integer broker : brokersUsageList) {
          // Check is the broker the leader of partition
          if (!topic.getPartitions().get(partition).getLeader()
              .equals(broker)) {
            brokers.remove(broker);
            brokersUsage.merge(broker, -1, Integer::sum);
          }
          if (brokers.size() == replicationFactorChange.getTotalReplicationFactor()) {
            break;
          }
        }
        if (brokers.size() != replicationFactorChange.getTotalReplicationFactor()) {
          throw new ValidationException("Something went wrong during removing replicas");
        }
      }
    } else {
      throw new ValidationException("Replication factor already equals requested");
    }

    // Return result map
    return currentAssignment.entrySet().stream().collect(toMap(
        e -> new TopicPartition(topic.getName(), e.getKey()),
        e -> Optional.of(new NewPartitionReassignment(e.getValue()))
    ));
  }

  private Map<Integer, List<Integer>> getCurrentAssignment(InternalTopic topic) {
    return topic.getPartitions().values().stream()
        .collect(toMap(
            InternalPartition::getPartition,
            p -> p.getReplicas().stream()
                .map(InternalReplica::getBroker)
                .collect(toList())
        ));
  }

  private Map<Integer, Integer> getBrokersMap(KafkaCluster cluster,
                                              Map<Integer, List<Integer>> currentAssignment) {
    Map<Integer, Integer> result = statisticsCache.get(cluster).getClusterDescription().getNodes()
        .stream()
        .map(Node::id)
        .collect(toMap(
            c -> c,
            c -> 0
        ));
    currentAssignment.values().forEach(brokers -> brokers
        .forEach(broker -> result.put(broker, result.get(broker) + 1)));

    return result;
  }

  /**
   * 增加主题的分区数量。
   *
   * <p>验证请求的分区数必须大于当前分区数，然后通过 AdminClient 执行分区扩容操作。
   *
   * @param cluster           目标 Kafka 集群
   * @param topicName         主题名称
   * @param partitionsIncrease 分区扩容参数（包含目标分区总数）
   * @return 包含扩容结果的 {@link PartitionsIncreaseResponseDTO} 的 Mono
   * @throws ValidationException 若请求的分区数小于或等于当前分区数
   */
  public Mono<PartitionsIncreaseResponseDTO> increaseTopicPartitions(
      KafkaCluster cluster,
      String topicName,
      PartitionsIncreaseDTO partitionsIncrease) {
    return loadTopic(cluster, topicName).flatMap(topic ->
        adminClientService.get(cluster).flatMap(ac -> {
          Integer actualCount = topic.getPartitionCount();
          Integer requestedCount = partitionsIncrease.getTotalPartitionsCount();

          if (requestedCount < actualCount) {
            return Mono.error(
                new ValidationException(String.format(
                    "Topic currently has %s partitions, which is higher than the requested %s.",
                    actualCount, requestedCount)));
          }
          if (requestedCount.equals(actualCount)) {
            return Mono.error(
                new ValidationException(
                    String.format("Topic already has %s partitions.", actualCount)));
          }

          Map<String, NewPartitions> newPartitionsMap = Collections.singletonMap(
              topicName,
              NewPartitions.increaseTo(partitionsIncrease.getTotalPartitionsCount())
          );
          return ac.createPartitions(newPartitionsMap)
              .then(loadTopic(cluster, topicName));
        }).map(t -> new PartitionsIncreaseResponseDTO()
            .topicName(t.getName())
            .totalPartitionsCount(t.getPartitionCount())
        )
    );
  }

  /**
   * 删除指定的 Kafka 主题。
   *
   * <p>删除前会检查集群是否启用了主题删除功能（{@link ClusterFeature#TOPIC_DELETION}），
   * 若未启用则抛出 {@link ValidationException}。删除成功后会更新统计缓存。
   *
   * @param cluster   目标 Kafka 集群
   * @param topicName 需要删除的主题名称
   * @return 删除完成的 Mono
   * @throws ValidationException 若集群不允许主题删除
   */
  public Mono<Void> deleteTopic(KafkaCluster cluster, String topicName) {
    if (statisticsCache.get(cluster).getFeatures().contains(ClusterFeature.TOPIC_DELETION)) {
      return adminClientService.get(cluster).flatMap(c -> c.deleteTopic(topicName))
          .doOnSuccess(t -> statisticsCache.onTopicDelete(cluster, topicName));
    } else {
      return Mono.error(new ValidationException("Topic deletion restricted"));
    }
  }

  /**
   * 克隆指定主题，使用新名称创建一个配置相同的新主题。
   *
   * <p>复制原主题的分区数、副本因子和所有配置项到新主题。
   *
   * @param cluster      目标 Kafka 集群
   * @param topicName    源主题名称
   * @param newTopicName 新主题名称
   * @return 包含新创建的 {@link InternalTopic} 的 Mono
   */
  public Mono<InternalTopic> cloneTopic(
      KafkaCluster cluster, String topicName, String newTopicName) {
    return loadTopic(cluster, topicName).flatMap(topic ->
        adminClientService.get(cluster)
            .flatMap(ac ->
                ac.createTopic(
                    newTopicName,
                    topic.getPartitionCount(),
                    topic.getReplicationFactor(),
                    topic.getTopicConfigs()
                        .stream()
                        .collect(Collectors
                            .toMap(InternalTopicConfig::getName, InternalTopicConfig::getValue))
                )
            ).thenReturn(newTopicName)
            .flatMap(a -> loadTopicAfterCreation(cluster, newTopicName))
    );
  }

  /**
   * 获取用于分页展示的主题列表。
   *
   * <p>基于统计缓存中的主题信息构建列表，使用空的分区偏移量（分页场景不需要偏移量详情），
   * 并过滤掉已不存在的主题。
   *
   * @param cluster 目标 Kafka 集群
   * @return 包含 {@link InternalTopic} 列表的 Mono
   */
  public Mono<List<InternalTopic>> getTopicsForPagination(KafkaCluster cluster) {
    Statistics stats = statisticsCache.get(cluster);
    return filterExisting(cluster, stats.getTopicDescriptions().keySet())
        .map(lst -> lst.stream()
            .map(topicName ->
                InternalTopic.from(
                    stats.getTopicDescriptions().get(topicName),
                    stats.getTopicConfigs().getOrDefault(topicName, List.of()),
                    InternalPartitionsOffsets.empty(),
                    stats.getMetrics(),
                    stats.getLogDirInfo(),
                    clustersProperties.getInternalTopicPrefix()
                    ))
            .collect(toList())
        );
  }

  /**
   * 获取指定主题的活跃生产者状态信息。
   *
   * @param cluster 目标 Kafka 集群
   * @param topic   主题名称
   * @return 包含分区到生产者状态映射的 Mono
   */
  public Mono<Map<TopicPartition, List<ProducerState>>> getActiveProducersState(KafkaCluster cluster, String topic) {
    return adminClientService.get(cluster)
        .flatMap(ac -> ac.getActiveProducersState(topic));
  }

  private Mono<List<String>> filterExisting(KafkaCluster cluster, Collection<String> topics) {
    return adminClientService.get(cluster)
        .flatMap(ac -> ac.listTopics(true))
        .map(existing -> existing
            .stream()
            .filter(topics::contains)
            .collect(toList()));
  }

}
