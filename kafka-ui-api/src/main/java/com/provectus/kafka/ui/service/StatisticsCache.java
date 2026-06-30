package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.ServerStatusDTO;
import com.provectus.kafka.ui.model.Statistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.stereotype.Component;

/**
 * 集群统计信息缓存。
 *
 * <p>基于 {@link ConcurrentHashMap} 的线程安全缓存，用于存储各 Kafka 集群的
 * {@link Statistics} 对象。支持全量替换、增量更新 Topic 描述与配置、
 * 以及 Topic 删除时的缓存清理。</p>
 *
 * <p>在应用启动时，会为 {@link ClustersStorage} 中注册的每个集群
 * 初始化一个 {@link ServerStatusDTO#INITIALIZING} 状态的占位对象。</p>
 *
 * @see Statistics
 * @see ClustersStorage
 */
@Component
public class StatisticsCache {

  private final Map<String, Statistics> cache = new ConcurrentHashMap<>();

  /**
   * 构造统计缓存实例，并为所有已注册集群初始化 INITIALIZING 状态的占位对象。
   *
   * @param clustersStorage 集群存储，用于获取已注册的集群列表
   */
  public StatisticsCache(ClustersStorage clustersStorage) {
    var initializing = Statistics.empty().toBuilder().status(ServerStatusDTO.INITIALIZING).build();
    clustersStorage.getKafkaClusters().forEach(c -> cache.put(c.getName(), initializing));
  }

  /**
   * 全量替换指定集群的统计信息。
   *
   * @param c     Kafka 集群
   * @param stats 新的统计数据
   */
  public synchronized void replace(KafkaCluster c, Statistics stats) {
    cache.put(c.getName(), stats);
  }

  /**
   * 增量更新指定集群的 Topic 描述和配置信息。
   *
   * @param c             Kafka 集群
   * @param descriptions  新的 Topic 描述（合并到已有数据中）
   * @param configs       新的 Topic 配置（合并到已有数据中）
   */
  public synchronized void update(KafkaCluster c,
                                  Map<String, TopicDescription> descriptions,
                                  Map<String, List<ConfigEntry>> configs) {
    var metrics = get(c);
    var updatedDescriptions = new HashMap<>(metrics.getTopicDescriptions());
    updatedDescriptions.putAll(descriptions);
    var updatedConfigs = new HashMap<>(metrics.getTopicConfigs());
    updatedConfigs.putAll(configs);
    replace(
        c,
        metrics.toBuilder()
            .topicDescriptions(updatedDescriptions)
            .topicConfigs(updatedConfigs)
            .build()
    );
  }

  /**
   * Topic 删除时清理缓存中对应的描述和配置数据。
   *
   * @param c     Kafka 集群
   * @param topic 被删除的 Topic 名称
   */
  public synchronized void onTopicDelete(KafkaCluster c, String topic) {
    var metrics = get(c);
    var updatedDescriptions = new HashMap<>(metrics.getTopicDescriptions());
    updatedDescriptions.remove(topic);
    var updatedConfigs = new HashMap<>(metrics.getTopicConfigs());
    updatedConfigs.remove(topic);
    replace(
        c,
        metrics.toBuilder()
            .topicDescriptions(updatedDescriptions)
            .topicConfigs(updatedConfigs)
            .build()
    );
  }

  /**
   * 获取指定集群的统计信息。
   *
   * <p>若集群尚未在缓存中（例如新添加的集群），则自动创建一个
   * {@link ServerStatusDTO#INITIALIZING} 状态的占位对象并放入缓存，
   * 与构造函数中的初始化逻辑保持一致。</p>
   *
   * @param c Kafka 集群
   * @return 统计信息，不会返回 {@code null}
   */
  public Statistics get(KafkaCluster c) {
    return cache.computeIfAbsent(c.getName(),
        name -> Statistics.empty().toBuilder().status(ServerStatusDTO.INITIALIZING).build());
  }

}
