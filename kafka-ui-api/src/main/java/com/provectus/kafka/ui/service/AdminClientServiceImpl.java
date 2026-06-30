package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.util.SslPropertiesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AdminClient 服务的默认实现。
 *
 * <p>管理 {@link ReactiveAdminClient} 实例的创建、缓存和销毁。
 * 每个 Kafka 集群对应一个 ReactiveAdminClient 实例，通过集群名称作为缓存键。
 * 客户端实例在首次请求时懒创建，并在集群移除或服务关闭时释放。</p>
 *
 * <p>主要职责：</p>
 * <ul>
 *   <li>根据集群配置（Bootstrap Servers、SSL 等）创建原生 Kafka AdminClient</li>
 *   <li>将原生 AdminClient 包装为响应式的 {@link ReactiveAdminClient}</li>
 *   <li>通过 {@link ConcurrentHashMap} 缓存客户端实例，避免重复创建</li>
 *   <li>提供客户端的关闭和资源清理能力</li>
 * </ul>
 *
 * @see AdminClientService
 * @see ReactiveAdminClient
 */
@Service
@Slf4j
public class AdminClientServiceImpl implements AdminClientService, Closeable {

  /** 默认的 AdminClient 请求超时时间（毫秒） */
  private static final int DEFAULT_CLIENT_TIMEOUT_MS = 30_000;

  /** 客户端 ID 序列生成器，保证每个客户端 ID 唯一 */
  private static final AtomicLong CLIENT_ID_SEQ = new AtomicLong();

  /** 集群名称到 ReactiveAdminClient 的缓存映射 */
  private final Map<String, ReactiveAdminClient> adminClientCache = new ConcurrentHashMap<>();

  /** AdminClient 请求超时时间（毫秒） */
  private final int clientTimeout;

  /**
   * 构造 AdminClientServiceImpl 实例。
   *
   * @param clustersProperties 全局集群配置属性，用于读取 AdminClient 超时时间等配置
   */
  public AdminClientServiceImpl(ClustersProperties clustersProperties) {
    this.clientTimeout = Optional.ofNullable(clustersProperties.getAdminClientTimeout())
        .orElse(DEFAULT_CLIENT_TIMEOUT_MS);
  }

  /**
   * 获取指定集群的 ReactiveAdminClient 实例。
   *
   * <p>优先从缓存中获取；若缓存未命中，则创建新的 AdminClient 并放入缓存。</p>
   *
   * @param cluster Kafka 集群信息
   * @return 该集群对应的 ReactiveAdminClient 实例
   */
  @Override
  public Mono<ReactiveAdminClient> get(KafkaCluster cluster) {
    return Mono.justOrEmpty(adminClientCache.get(cluster.getName()))
        .switchIfEmpty(createAdminClient(cluster))
        .map(e -> adminClientCache.computeIfAbsent(cluster.getName(), key -> e));
  }

  /**
   * 为指定集群创建新的 ReactiveAdminClient 实例。
   *
   * <p>构建过程包括：</p>
   * <ol>
   *   <li>从集群配置中提取 SSL/TLS 相关属性</li>
   *   <li>设置 Bootstrap Servers 地址</li>
   *   <li>配置请求超时时间和客户端 ID</li>
   *   <li>创建原生 Kafka AdminClient</li>
   *   <li>将其包装为响应式的 ReactiveAdminClient</li>
   * </ol>
   *
   * @param cluster Kafka 集群信息
   * @return 新创建的 ReactiveAdminClient 实例
   * @throws IllegalStateException 如果创建过程中发生错误
   */
  private Mono<ReactiveAdminClient> createAdminClient(KafkaCluster cluster) {
    return Mono.fromSupplier(() -> {
      Properties properties = new Properties();
      SslPropertiesUtil.addKafkaSslProperties(cluster.getOriginalProperties().getSsl(), properties);
      // 设置SSL的Keystore配置
      SslPropertiesUtil.addKafkaSslKeyStoreConfig(cluster.getOriginalProperties().getSslKeystoreConfig(), properties);
      properties.putAll(cluster.getProperties());
      // 默认设置禁用主机名验证
      if (!cluster.getProperties().containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)) {
        properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, StringUtils.EMPTY);
      }
      properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
      properties.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, clientTimeout);
      properties.putIfAbsent(
          AdminClientConfig.CLIENT_ID_CONFIG,
          "kafka-ui-admin-" + Instant.now().getEpochSecond() + "-" + CLIENT_ID_SEQ.incrementAndGet()
      );
      return AdminClient.create(properties);
    }).flatMap(ac -> ReactiveAdminClient.create(ac).doOnError(th -> ac.close()))
        .onErrorMap(th -> new IllegalStateException(
            "Error while creating AdminClient for Cluster " + cluster.getName(), th));
  }

  /**
   * 关闭并移除指定集群的 AdminClient。
   *
   * <p>从缓存中移除客户端实例并释放其底层资源。</p>
   *
   * @param clusterName 要关闭的集群名称
   */
  @Override
  public void closeClient(String clusterName) {
    ReactiveAdminClient client = adminClientCache.remove(clusterName);
    if (client != null) {
      client.close();
      log.info("Closed AdminClient for cluster: {}", clusterName);
    }
  }

  /**
   * 关闭所有缓存的 AdminClient 实例并清空缓存。
   *
   * <p>通常在应用关闭时调用，确保释放所有网络连接和系统资源。</p>
   */
  @Override
  public void close() {
    adminClientCache.values().forEach(ReactiveAdminClient::close);
  }
}
