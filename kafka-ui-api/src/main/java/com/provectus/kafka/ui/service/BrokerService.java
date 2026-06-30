package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.exception.InvalidRequestApiException;
import com.provectus.kafka.ui.exception.LogDirNotFoundApiException;
import com.provectus.kafka.ui.exception.NotFoundException;
import com.provectus.kafka.ui.exception.TopicOrPartitionNotFoundException;
import com.provectus.kafka.ui.mapper.DescribeLogDirsMapper;
import com.provectus.kafka.ui.model.BrokerLogdirUpdateDTO;
import com.provectus.kafka.ui.model.BrokersLogdirsDTO;
import com.provectus.kafka.ui.model.InternalBroker;
import com.provectus.kafka.ui.model.InternalBrokerConfig;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.PartitionDistributionStats;
import com.provectus.kafka.ui.service.metrics.RawMetric;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionReplica;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.LogDirNotFoundException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.requests.DescribeLogDirsResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka Broker 管理服务。
 *
 * <p>提供 Broker 节点的查询与配置管理功能，主要包括：
 * <ul>
 *   <li>获取集群中所有 Broker 节点列表及其分区分布统计</li>
 *   <li>查询和更新 Broker 的配置项</li>
 *   <li>查询和修改 Broker 的日志目录（LogDir）</li>
 *   <li>获取 Broker 级别的监控指标</li>
 * </ul>
 *
 * <p>通过 {@link StatisticsCache} 获取集群描述信息和指标数据，
 * 通过 {@link AdminClientService} 执行管理操作。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerService {

  private final StatisticsCache statisticsCache;
  private final AdminClientService adminClientService;
  private final DescribeLogDirsMapper describeLogDirsMapper;

  private Mono<Map<Integer, List<ConfigEntry>>> loadBrokersConfig(
      KafkaCluster cluster, List<Integer> brokersIds) {
    return adminClientService.get(cluster).flatMap(ac -> ac.loadBrokersConfig(brokersIds));
  }

  private Mono<List<ConfigEntry>> loadBrokersConfig(
      KafkaCluster cluster, Integer brokerId) {
    return loadBrokersConfig(cluster, Collections.singletonList(brokerId))
        .map(map -> map.values().stream().findFirst().orElse(List.of()));
  }

  private Flux<InternalBrokerConfig> getBrokersConfig(KafkaCluster cluster, Integer brokerId) {
    if (statisticsCache.get(cluster).getClusterDescription().getNodes()
        .stream().noneMatch(node -> node.id() == brokerId)) {
      return Flux.error(
          new NotFoundException(String.format("Broker with id %s not found", brokerId)));
    }
    return loadBrokersConfig(cluster, brokerId)
        .map(list -> list.stream()
            .map(InternalBrokerConfig::from)
            .collect(Collectors.toList()))
        .flatMapMany(Flux::fromIterable);
  }

  /**
   * 获取集群中所有 Broker 节点列表。
   *
   * <p>返回的 Broker 信息包含节点详情、分区分布统计和集群统计数据。
   *
   * @param cluster 目标 Kafka 集群
   * @return {@link InternalBroker} 的响应式流
   */
  public Flux<InternalBroker> getBrokers(KafkaCluster cluster) {
    var stats = statisticsCache.get(cluster);
    var partitionsDistribution = PartitionDistributionStats.create(stats);
    return adminClientService
        .get(cluster)
        .flatMap(ReactiveAdminClient::describeCluster)
        .map(description -> description.getNodes().stream()
            .map(node -> new InternalBroker(node, partitionsDistribution, stats))
            .collect(Collectors.toList()))
        .flatMapMany(Flux::fromIterable);
  }

  /**
   * 更新 Broker 上指定分区副本的日志目录。
   *
   * <p>将指定分区的副本从当前日志目录迁移到目标日志目录。
   *
   * @param cluster     目标 Kafka 集群
   * @param broker      Broker ID
   * @param brokerLogDir 日志目录更新参数（包含主题、分区和目标日志目录）
   * @return 更新完成的 Mono
   * @throws TopicOrPartitionNotFoundException 若主题或分区不存在
   * @throws LogDirNotFoundApiException        若目标日志目录不存在
   */
  public Mono<Void> updateBrokerLogDir(KafkaCluster cluster,
                                       Integer broker,
                                       BrokerLogdirUpdateDTO brokerLogDir) {
    return adminClientService.get(cluster)
        .flatMap(ac -> updateBrokerLogDir(ac, brokerLogDir, broker));
  }

  private Mono<Void> updateBrokerLogDir(ReactiveAdminClient admin,
                                        BrokerLogdirUpdateDTO b,
                                        Integer broker) {

    Map<TopicPartitionReplica, String> req = Map.of(
        new TopicPartitionReplica(b.getTopic(), b.getPartition(), broker),
        b.getLogDir());
    return admin.alterReplicaLogDirs(req)
        .onErrorResume(UnknownTopicOrPartitionException.class,
            e -> Mono.error(new TopicOrPartitionNotFoundException()))
        .onErrorResume(LogDirNotFoundException.class,
            e -> Mono.error(new LogDirNotFoundApiException()))
        .doOnError(e -> log.error("Unexpected error", e));
  }

  /**
   * 更新 Broker 的指定配置项。
   *
   * @param cluster 目标 Kafka 集群
   * @param broker  Broker ID
   * @param name    配置项名称
   * @param value   配置项新值
   * @return 更新完成的 Mono
   * @throws InvalidRequestApiException 若请求无效（如配置项不存在或值不合法）
   */
  public Mono<Void> updateBrokerConfigByName(KafkaCluster cluster,
                                             Integer broker,
                                             String name,
                                             String value) {
    return adminClientService.get(cluster)
        .flatMap(ac -> ac.updateBrokerConfigByName(broker, name, value))
        .onErrorResume(InvalidRequestException.class,
            e -> Mono.error(new InvalidRequestApiException(e.getMessage())))
        .doOnError(e -> log.error("Unexpected error", e));
  }

  private Mono<Map<Integer, Map<String, DescribeLogDirsResponse.LogDirInfo>>> getClusterLogDirs(
      KafkaCluster cluster, List<Integer> reqBrokers) {
    return adminClientService.get(cluster)
        .flatMap(admin -> {
          List<Integer> brokers = statisticsCache.get(cluster).getClusterDescription().getNodes()
              .stream()
              .map(Node::id)
              .collect(Collectors.toList());
          if (!reqBrokers.isEmpty()) {
            brokers.retainAll(reqBrokers);
          }
          return admin.describeLogDirs(brokers);
        })
        .onErrorResume(TimeoutException.class, (TimeoutException e) -> {
          log.error("Error during fetching log dirs", e);
          return Mono.just(new HashMap<>());
        });
  }

  /**
   * 获取指定 Broker 的日志目录信息。
   *
   * <p>若指定的 Broker 列表为空，则获取所有 Broker 的日志目录信息。
   * 超时情况下返回空结果而非抛出异常。
   *
   * @param cluster 目标 Kafka 集群
   * @param brokers 需要查询的 Broker ID 列表，为空则查询所有
   * @return {@link BrokersLogdirsDTO} 的响应式流
   */
  public Flux<BrokersLogdirsDTO> getAllBrokersLogdirs(KafkaCluster cluster, List<Integer> brokers) {
    return getClusterLogDirs(cluster, brokers)
        .map(describeLogDirsMapper::toBrokerLogDirsList)
        .flatMapMany(Flux::fromIterable);
  }

  /**
   * 获取指定 Broker 的配置项列表。
   *
   * @param cluster  目标 Kafka 集群
   * @param brokerId Broker ID
   * @return {@link InternalBrokerConfig} 的响应式流
   * @throws NotFoundException 若指定的 Broker 不存在
   */
  public Flux<InternalBrokerConfig> getBrokerConfig(KafkaCluster cluster, Integer brokerId) {
    return getBrokersConfig(cluster, brokerId);
  }

  /**
   * 获取指定 Broker 的监控指标。
   *
   * @param cluster  目标 Kafka 集群
   * @param brokerId Broker ID
   * @return 包含指标列表的 Mono，若无指标数据则返回空
   */
  public Mono<List<RawMetric>> getBrokerMetrics(KafkaCluster cluster, Integer brokerId) {
    return Mono.justOrEmpty(statisticsCache.get(cluster).getMetrics().getPerBrokerMetrics().get(brokerId));
  }

}
