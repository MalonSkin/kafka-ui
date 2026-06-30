package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.mapper.ClusterMapper;
import com.provectus.kafka.ui.model.ClusterDTO;
import com.provectus.kafka.ui.model.ClusterMetricsDTO;
import com.provectus.kafka.ui.model.ClusterStatsDTO;
import com.provectus.kafka.ui.model.InternalClusterState;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.Metrics;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 集群服务
 *
 * 提供集群信息查询和统计功能。该服务是集群信息的主要提供者，
 * 将内部 KafkaCluster 模型转换为前端所需的 DTO 格式。
 *
 * 主要功能：
 * 1. 获取所有集群列表
 * 2. 获取单个集群的统计信息
 * 3. 获取单个集群的 Metrics 数据
 * 4. 强制刷新集群统计缓存
 *
 * @author kafka-ui
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterService {

  /** 统计信息缓存，定期刷新集群统计数据 */
  private final StatisticsCache statisticsCache;

  /** 集群存储，包含所有已配置的 Kafka 集群实例 */
  private final ClustersStorage clustersStorage;

  /** 集群对象映射器，将内部模型转换为 DTO */
  private final ClusterMapper clusterMapper;

  /** 统计服务，用于获取和刷新集群统计信息 */
  private final StatisticsService statisticsService;

  /**
   * 获取所有集群列表
   *
   * 从 ClustersStorage 获取所有集群，结合 StatisticsCache 中的统计数据，
   * 转换为前端所需的 ClusterDTO 列表。
   *
   * @return 集群 DTO 列表
   */
  public List<ClusterDTO> getClusters() {
    return clustersStorage.getKafkaClusters()
        .stream()
        .map(c -> clusterMapper.toCluster(new InternalClusterState(c, statisticsCache.get(c))))
        .collect(Collectors.toList());
  }

  /**
   * 获取单个集群的统计信息
   *
   * @param cluster 集群实例
   * @return 包装在 Mono 中的集群统计 DTO
   */
  public Mono<ClusterStatsDTO> getClusterStats(KafkaCluster cluster) {
    return Mono.justOrEmpty(
        clusterMapper.toClusterStats(
            new InternalClusterState(cluster, statisticsCache.get(cluster)))
    );
  }

  /**
   * 获取单个集群的 Metrics 数据
   *
   * @param cluster 集群实例
   * @return 包装在 Mono 中的集群 Metrics DTO
   */
  public Mono<ClusterMetricsDTO> getClusterMetrics(KafkaCluster cluster) {
    // 获取集群的 metrics 数据，如果为空则返回空的 metrics DTO，避免 NPE
    Metrics metrics = statisticsCache.get(cluster).getMetrics();
    if (metrics == null) {
      return Mono.just(new ClusterMetricsDTO().items(List.of()));
    }
    return Mono.just(clusterMapper.toClusterMetrics(metrics));
  }

  /**
   * 强制刷新集群统计缓存
   *
   * 触发 StatisticsService 更新指定集群的统计数据，
   * 并返回更新后的集群信息。
   *
   * @param cluster 集群实例
   * @return 包装在 Mono 中的更新后的集群 DTO
   */
  public Mono<ClusterDTO> updateCluster(KafkaCluster cluster) {
    return statisticsService.updateCache(cluster)
        .map(metrics -> clusterMapper.toCluster(new InternalClusterState(cluster, metrics)));
  }
}