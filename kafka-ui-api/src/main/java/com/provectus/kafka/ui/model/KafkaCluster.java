package com.provectus.kafka.ui.model;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.connect.api.KafkaConnectClientApi;
import com.provectus.kafka.ui.emitter.PollingSettings;
import com.provectus.kafka.ui.service.ksql.KsqlApiClient;
import com.provectus.kafka.ui.service.masking.DataMasking;
import com.provectus.kafka.ui.sr.api.KafkaSrClientApi;
import com.provectus.kafka.ui.util.ReactiveFailover;
import java.util.Map;
import java.util.Properties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Kafka 集群运行时模型。
 * <p>
 * 表示一个已初始化的 Kafka 集群实例，包含：
 * <ul>
 *   <li>集群连接信息和配置</li>
 *   <li>各种客户端实例（Schema Registry、Kafka Connect、ksqlDB）</li>
 *   <li>运行时配置（轮询设置、数据脱敏、指标配置）</li>
 * </ul>
 * 该类使用 Builder 模式创建，创建后不可修改（所有字段为 final）。
 * </p>
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaCluster {
  /** 原始配置属性，保留用于诊断和调试 */
  private final ClustersProperties.Cluster originalProperties;

  /** 集群名称 */
  private final String name;
  /** Kafka Broker 版本号 */
  private final String version;
  /** Kafka Broker 地址 */
  private final String bootstrapServers;
  /** Kafka 客户端属性（已展平） */
  private final Properties properties;
  /** 是否为只读模式 */
  private final boolean readOnly;
  /** 指标采集配置 */
  private final MetricsConfig metricsConfig;
  /** 数据脱敏配置 */
  private final DataMasking masking;
  /** 消息轮询配置 */
  private final PollingSettings pollingSettings;
  /** Schema Registry 客户端（支持故障转移） */
  private final ReactiveFailover<KafkaSrClientApi> schemaRegistryClient;
  /** Kafka Connect 客户端映射（集群名 -> 客户端，支持故障转移） */
  private final Map<String, ReactiveFailover<KafkaConnectClientApi>> connectsClients;
  /** ksqlDB 客户端（支持故障转移） */
  private final ReactiveFailover<KsqlApiClient> ksqlClient;
}
