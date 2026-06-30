package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.model.KafkaCluster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 集群热加载服务。
 * <p>
 * 封装集群的动态添加和删除操作，支持在运行时管理 Kafka 集群，
 * 无需重启服务。该服务协调 ClustersStorage、AdminClientService
 * 和 ClusterConfigPersistenceService，确保集群生命周期的正确管理。
 * </p>
 * <p>
 * 所有操作同时更新内存缓存和数据库，保证数据一致性。
 * </p>
 *
 * @author kafka-ui
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterHotReloadService {

  private final ClustersStorage clustersStorage;
  private final KafkaClusterFactory kafkaClusterFactory;
  private final AdminClientService adminClientService;
  private final ClusterConfigPersistenceService persistenceService;

  /**
   * 动态添加新集群。
   * <p>
   * 该方法将集群配置持久化到数据库，并在内存中创建 KafkaCluster 实例。
   * </p>
   *
   * @param clusterProperties 集群配置
   * @return 创建的 KafkaCluster 实例
   * @throws IllegalArgumentException 如果集群名称已存在
   */
  public KafkaCluster addCluster(ClustersProperties.Cluster clusterProperties) {
    String clusterName = clusterProperties.getName();
    log.info("热加载: 添加集群 {}", clusterName);

    // 检查数据库中是否已存在同名集群
    if (persistenceService.existsByName(clusterName)) {
      throw new IllegalArgumentException("集群已存在: " + clusterName);
    }

    KafkaCluster cluster = clustersStorage.addCluster(clusterProperties);
    log.info("热加载: 成功添加集群 {}", clusterName);
    return cluster;
  }

  /**
   * 动态删除集群（含资源清理和数据库删除）。
   *
   * @param clusterName 要删除的集群名称
   * @return true 如果集群存在并被删除，false 如果集群不存在
   */
  public boolean removeCluster(String clusterName) {
    log.info("热加载: 删除集群 {}", clusterName);

    return clustersStorage.removeCluster(clusterName)
        .map(removed -> {
          // 关闭 AdminClient
          adminClientService.closeClient(clusterName);
          log.info("热加载: 成功删除集群并清理资源: {}", clusterName);
          return true;
        })
        .orElse(false);
  }

  /**
   * 替换集群配置（先删后加，含资源清理和数据库更新）。
   *
   * @param newGlobalProperties 新的全局集群配置
   * @param clusterProperties   新的单个集群配置
   * @return 新创建的 KafkaCluster 实例
   */
  public KafkaCluster replaceCluster(ClustersProperties newGlobalProperties,
                                      ClustersProperties.Cluster clusterProperties) {
    String clusterName = clusterProperties.getName();
    log.info("热加载: 替换集群 {}", clusterName);

    // 关闭可能存在的旧 AdminClient
    adminClientService.closeClient(clusterName);

    // 替换集群（ClustersStorage 内部会同步更新数据库）
    KafkaCluster newCluster = clustersStorage.replaceCluster(newGlobalProperties, clusterProperties);
    log.info("热加载: 成功替换集群 {}", clusterName);
    return newCluster;
  }
}
