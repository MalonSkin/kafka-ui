package com.provectus.kafka.ui.service;

import static com.provectus.kafka.ui.service.ReactiveAdminClient.ClusterDescription;

import com.provectus.kafka.ui.model.ClusterFeature;
import com.provectus.kafka.ui.model.InternalLogDirStats;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.Metrics;
import com.provectus.kafka.ui.model.ServerStatusDTO;
import com.provectus.kafka.ui.model.Statistics;
import com.provectus.kafka.ui.service.metrics.MetricsCollector;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 集群统计信息服务。
 *
 * <p>负责收集 Kafka 集群的综合统计信息，包括 Broker 指标、
 * 日志目录信息、可用特性、Topic 配置和 Topic 描述等。
 * 收集结果通过 {@link StatisticsCache} 进行缓存。</p>
 *
 * @see StatisticsCache
 * @see AdminClientService
 * @see MetricsCollector
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

  private final MetricsCollector metricsCollector;
  private final AdminClientService adminClientService;
  private final FeatureService featureService;
  private final StatisticsCache cache;

  /**
   * 更新指定集群的统计信息缓存。
   *
   * @param c Kafka 集群
   * @return 更新后的 Statistics 对象
   */
  public Mono<Statistics> updateCache(KafkaCluster c) {
    return getStatistics(c).doOnSuccess(m -> cache.replace(c, m));
  }

  /**
   * 收集集群的完整统计信息，包括 Broker 指标、日志目录、特性、Topic 配置和描述。
   *
   * @param cluster Kafka 集群
   * @return 聚合后的 Statistics 对象；收集失败时返回携带异常信息的空 Statistics
   */
  private Mono<Statistics> getStatistics(KafkaCluster cluster) {
    return adminClientService.get(cluster).flatMap(ac ->
            ac.describeCluster().flatMap(description ->
                ac.updateInternalStats(description.getController()).then(
                    Mono.zip(
                        List.of(
                            metricsCollector.getBrokerMetrics(cluster, description.getNodes()),
                            getLogDirInfo(description, ac),
                            featureService.getAvailableFeatures(ac, cluster, description),
                            loadTopicConfigs(cluster),
                            describeTopics(cluster)),
                        results ->
                            Statistics.builder()
                                .status(ServerStatusDTO.ONLINE)
                                .clusterDescription(description)
                                .version(ac.getVersion())
                                .metrics((Metrics) results[0])
                                .logDirInfo((InternalLogDirStats) results[1])
                                .features((List<ClusterFeature>) results[2])
                                .topicConfigs((Map<String, List<ConfigEntry>>) results[3])
                                .topicDescriptions((Map<String, TopicDescription>) results[4])
                                .build()
                    ))))
        .doOnError(e ->
            log.error("Failed to collect cluster {} info", cluster.getName(), e))
        .onErrorResume(
            e -> Mono.just(Statistics.empty().toBuilder().lastKafkaException(e).build()));
  }

  /**
   * 获取集群所有 Broker 的日志目录统计信息。
   *
   * @param desc 集群描述信息
   * @param ac   响应式 AdminClient
   * @return 日志目录统计信息
   */
  private Mono<InternalLogDirStats> getLogDirInfo(ClusterDescription desc, ReactiveAdminClient ac) {
    var brokerIds = desc.getNodes().stream().map(Node::id).collect(Collectors.toSet());
    return ac.describeLogDirs(brokerIds).map(InternalLogDirStats::new);
  }

  /**
   * 获取集群所有 Topic 的描述信息。
   *
   * @param c Kafka 集群
   * @return Topic 名称到 TopicDescription 的映射
   */
  private Mono<Map<String, TopicDescription>> describeTopics(KafkaCluster c) {
    return adminClientService.get(c).flatMap(ReactiveAdminClient::describeTopics);
  }

  /**
   * 加载集群所有 Topic 的配置信息。
   *
   * @param c Kafka 集群
   * @return Topic 名称到配置项列表的映射
   */
  private Mono<Map<String, List<ConfigEntry>>> loadTopicConfigs(KafkaCluster c) {
    return adminClientService.get(c).flatMap(ReactiveAdminClient::getTopicsConfig);
  }

}
