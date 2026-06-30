package com.provectus.kafka.ui.config;

import com.provectus.kafka.ui.model.MetricsConfig;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Kafka 集群配置属性类。
 * <p>
 * 绑定以 "kafka" 为前缀的配置项，用于管理多个 Kafka 集群的连接信息、
 * Schema Registry、Kafka Connect、ksqlDB 等组件的配置。
 * </p>
 * <p>
 * 核心职责：
 * <ul>
 *   <li>解析并持有所有 Kafka 集群的配置信息</li>
 *   <li>验证集群名称的唯一性</li>
 *   <li>展平嵌套的集群属性为扁平化的 key-value 结构</li>
 *   <li>为未配置的指标类型设置默认值</li>
 * </ul>
 * </p>
 */
@Configuration
@ConfigurationProperties("kafka")
@Data
public class ClustersProperties {

  /** 集群配置列表 */
  List<Cluster> clusters = new ArrayList<>();

  /** 内部 Topic 前缀，用于标识系统内部使用的 Topic */
  String internalTopicPrefix;

  /** AdminClient 操作超时时间（毫秒） */
  Integer adminClientTimeout;

  /** 轮询相关配置 */
  PollingProperties polling = new PollingProperties();

  /**
   * 单个 Kafka 集群的配置信息。
   * <p>
   * 包含集群连接地址、Schema Registry、Kafka Connect、ksqlDB 等组件的配置，
   * 以及序列化/反序列化、数据脱敏、审计等高级功能的配置。
   * </p>
   */
  @Data
  public static class Cluster {
    /** 集群名称，多集群时必须唯一 */
    String name;
    /** Kafka Broker 地址，多个用逗号分隔 */
    String bootstrapServers;
    /** Schema Registry 地址 */
    String schemaRegistry;
    /** Schema Registry 认证配置 */
    SchemaRegistryAuth schemaRegistryAuth;
    /** Schema Registry SSL Keystore 配置 */
    KeystoreConfig schemaRegistrySsl;
    /** ksqlDB Server 地址 */
    String ksqldbServer;
    /** ksqlDB Server 认证配置 */
    KsqldbServerAuth ksqldbServerAuth;
    /** ksqlDB Server SSL Keystore 配置 */
    KeystoreConfig ksqldbServerSsl;
    /** Kafka Connect 集群配置列表 */
    List<ConnectCluster> kafkaConnect;
    /** 指标采集配置 */
    MetricsConfigData metrics;
    /** Kafka 客户端原生属性（嵌套结构会在初始化时被展平） */
    Map<String, Object> properties;
    /** 是否为只读模式，只读模式下禁止写操作 */
    boolean readOnly = false;
    /** 自定义序列化/反序列化配置列表 */
    List<SerdeConfig> serde;
    /** 默认 Key 序列化器名称 */
    String defaultKeySerde;
    /** 默认 Value 序列化器名称 */
    String defaultValueSerde;
    /** 数据脱敏规则列表 */
    List<Masking> masking;
    /** 消息轮询限流速率（每秒请求数） */
    Long pollingThrottleRate;
    /** SSL Truststore 配置 */
    TruststoreConfig ssl;
    /** SSL Keystore 配置（用于 mTLS 认证） */
    KeystoreConfig sslKeystoreConfig;
    /** 审计日志配置 */
    AuditProperties audit;
  }

  /**
   * 消息轮询配置属性。
   * <p>
   * 控制从 Kafka 消费消息时的超时时间和分页参数。
   * </p>
   */
  @Data
  public static class PollingProperties {
    /** 单次轮询超时时间（毫秒） */
    Integer pollTimeoutMs;
    /** 每页最大消息数 */
    Integer maxPageSize;
    /** 默认每页消息数 */
    Integer defaultPageSize;
  }

  /**
   * 指标采集配置数据。
   * <p>
   * 支持 JMX 等指标采集方式，可配置认证和 SSL。
   * toString 时排除 password 字段，防止日志泄露敏感信息。
   * </p>
   */
  @Data
  @ToString(exclude = "password")
  public static class MetricsConfigData {
    /** 指标采集类型（如 "JMX"） */
    String type;
    /** 指标服务端口 */
    Integer port;
    /** 是否启用 SSL */
    Boolean ssl;
    /** 认证用户名 */
    String username;
    /** 认证密码 */
    String password;
    /** Keystore 文件路径 */
    String keystoreLocation;
    /** Keystore 密码 */
    String keystorePassword;
  }

  /**
   * Kafka Connect 集群配置。
   * <p>
   * 支持基本认证和 SSL Keystore 认证方式。
   * toString 时排除敏感字段，防止日志泄露。
   * </p>
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder(toBuilder = true)
  @ToString(exclude = {"password", "keystorePassword"})
  public static class ConnectCluster {
    /** Connect 集群名称 */
    String name;
    /** Connect 集群 REST API 地址 */
    String address;
    /** 认证用户名 */
    String username;
    /** 认证密码 */
    String password;
    /** Keystore 文件路径（用于 SSL 客户端认证） */
    String keystoreLocation;
    /** Keystore 密码 */
    String keystorePassword;
  }

  /**
   * Schema Registry 认证配置。
   * <p>
   * 支持基本用户名/密码认证方式。
   * </p>
   */
  @Data
  @ToString(exclude = {"password"})
  public static class SchemaRegistryAuth {
    /** 认证用户名 */
    String username;
    /** 认证密码 */
    String password;
  }

  /**
   * SSL Truststore 配置。
   * <p>
   * 用于验证服务端证书的信任链。
   * </p>
   */
  @Data
  @ToString(exclude = {"truststorePassword"})
  public static class TruststoreConfig {
    /** Truststore 文件路径 */
    String truststoreLocation;
    /** Truststore 密码 */
    String truststorePassword;
  }

  /**
   * 自定义序列化/反序列化器配置。
   * <p>
   * 支持通过类名或文件路径加载自定义 SerDe，
   * 并可指定 Topic 的 Key/Value 匹配模式。
   * </p>
   */
  @Data
  public static class SerdeConfig {
    /** SerDe 名称，用于在 UI 中显示和引用 */
    String name;
    /** SerDe 实现类的全限定名 */
    String className;
    /** SerDe 实现的文件路径（用于外部 JAR 加载） */
    String filePath;
    /** SerDe 的自定义属性 */
    Map<String, Object> properties;
    /** 匹配 Topic Key 的正则表达式模式 */
    String topicKeysPattern;
    /** 匹配 Topic Value 的正则表达式模式 */
    String topicValuesPattern;
  }

  /**
   * ksqlDB Server 认证配置。
   * <p>
   * 支持基本用户名/密码认证方式。
   * </p>
   */
  @Data
  @ToString(exclude = "password")
  public static class KsqldbServerAuth {
    /** 认证用户名 */
    String username;
    /** 认证密码 */
    String password;
  }

  /**
   * SSL Keystore 配置。
   * <p>
   * 用于客户端证书认证（mTLS），提供客户端身份证明。
   * </p>
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @ToString(exclude = {"keystorePassword"})
  public static class KeystoreConfig {
    /** Keystore 文件路径 */
    String keystoreLocation;
    /** Keystore 密码 */
    String keystorePassword;
  }

  /**
   * 数据脱敏规则配置。
   * <p>
   * 支持三种脱敏方式：
   * <ul>
   *   <li>REMOVE - 完全移除匹配的字段</li>
   *   <li>MASK - 用指定字符替换敏感数据</li>
   *   <li>REPLACE - 用固定字符串替换敏感数据</li>
   * </ul>
   * 可通过字段名列表或正则表达式模式匹配需要脱敏的字段，
   * 并可指定脱敏规则适用的 Topic 范围。
   * </p>
   */
  @Data
  public static class Masking {
    /** 规则业务名称（唯一标识，用于同步匹配） */
    String name;
    /** 脱敏类型 */
    Type type;
    /** 需要脱敏的字段名列表 */
    List<String> fields;
    /** 需要脱敏的字段名正则表达式模式 */
    String fieldsNamePattern;
    /** MASK 类型时的替换字符列表 */
    List<String> maskingCharsReplacement;
    /** REPLACE 类型时的替换字符串 */
    String replacement;
    /** 适用的 Topic Key 正则表达式模式 */
    String topicKeysPattern;
    /** 适用的 Topic Value 正则表达式模式 */
    String topicValuesPattern;

    /**
     * 脱敏类型枚举。
     */
    public enum Type {
      /** 移除字段 */
      REMOVE,
      /** 字符掩码替换 */
      MASK,
      /** 固定字符串替换 */
      REPLACE
    }
  }

  /**
   * 审计日志配置。
   * <p>
   * 支持将操作审计日志写入指定的 Kafka Topic 或输出到控制台。
   * 可配置审计级别：ALL（记录所有操作）或 ALTER_ONLY（仅记录变更操作）。
   * </p>
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AuditProperties {
    /** 审计 Topic 名称 */
    String topic;
    /** 审计 Topic 的分区数 */
    Integer auditTopicsPartitions;
    /** 是否启用 Topic 审计 */
    Boolean topicAuditEnabled;
    /** 是否启用控制台审计输出 */
    Boolean consoleAuditEnabled;
    /** 审计日志级别 */
    LogLevel level;
    /** 审计 Topic 的自定义属性（如 retention.ms 等） */
    Map<String, String> auditTopicProperties;

    /**
     * 审计日志级别枚举。
     */
    public enum LogLevel {
      /** 记录所有操作 */
      ALL,
      /** 仅记录变更操作（默认） */
      ALTER_ONLY
    }
  }

  /**
   * 初始化后验证配置并设置默认值。
   * <p>
   * 在 Spring 容器完成属性注入后自动执行：
   * <ol>
   *   <li>验证集群名称的唯一性</li>
   *   <li>展平嵌套的集群属性</li>
   *   <li>为未配置的指标类型设置默认值</li>
   * </ol>
   * </p>
   */
  @PostConstruct
  public void validateAndSetDefaults() {
    if (clusters != null) {
      validateClusterNames();
      flattenClusterProperties();
      setMetricsDefaults();
    }
  }

  /**
   * 为未配置指标类型的集群设置默认值。
   * <p>
   * 如果集群配置了 metrics 但未指定 type，则默认使用 JMX 指标采集类型。
   * </p>
   */
  private void setMetricsDefaults() {
    for (Cluster cluster : clusters) {
      if (cluster.getMetrics() != null && !StringUtils.hasText(cluster.getMetrics().getType())) {
        cluster.getMetrics().setType(MetricsConfig.JMX_METRICS_TYPE);
      }
    }
  }

  /**
   * 展平所有集群的嵌套属性配置。
   * <p>
   * 将嵌套的 Map 结构转换为扁平化的 key-value 形式，
   * 例如 {"ssl": {"keystore": {"location": "xxx"}}} 转换为 {"ssl.keystore.location": "xxx"}。
   * 这是 Kafka 客户端原生属性的标准格式要求。
   * </p>
   */
  private void flattenClusterProperties() {
    for (Cluster cluster : clusters) {
      cluster.setProperties(flattenClusterProperties(null, cluster.getProperties()));
    }
  }

  /**
   * 递归展平嵌套的属性 Map。
   * <p>
   * 将多层嵌套的 Map 结构转换为点号分隔的扁平化 key-value 形式。
   * 例如：{"a": {"b": "c"}} → {"a.b": "c"}
   * </p>
   *
   * @param prefix      当前键前缀，根级别时为 null
   * @param propertiesMap 待展平的属性 Map
   * @return 展平后的属性 Map
   */
  private Map<String, Object> flattenClusterProperties(@Nullable String prefix,
                                                       @Nullable Map<String, Object> propertiesMap) {
    Map<String, Object> flattened = new HashMap<>();
    if (propertiesMap != null) {
      propertiesMap.forEach((k, v) -> {
        // 拼接完整的键路径，根级别时直接使用原键名
        String key = prefix == null ? k : prefix + "." + k;
        if (v instanceof Map<?, ?>) {
          // 值仍然是 Map，递归展平
          flattened.putAll(flattenClusterProperties(key, (Map<String, Object>) v));
        } else {
          // 到达叶子节点，直接存入结果
          flattened.put(key, v);
        }
      });
    }
    return flattened;
  }

  /**
   * 验证集群名称的合法性和唯一性。
   * <p>
   * 验证规则：
   * <ul>
   *   <li>单集群时，如果未设置名称则自动设置为 "Default"</li>
   *   <li>多集群时，每个集群必须设置名称</li>
   *   <li>所有集群名称必须唯一</li>
   * </ul>
   * </p>
   *
   * @throws IllegalStateException 当集群名称缺失或重复时抛出
   */
  private void validateClusterNames() {
    // 单集群场景：允许不设置名称，自动使用 "Default"
    if (clusters.size() == 1 && !StringUtils.hasText(clusters.get(0).getName())) {
      clusters.get(0).setName("Default");
      return;
    }

    // 多集群场景：验证名称非空且唯一
    Set<String> clusterNames = new HashSet<>();
    for (Cluster clusterProperties : clusters) {
      if (!StringUtils.hasText(clusterProperties.getName())) {
        throw new IllegalStateException(
            "Application config isn't valid. "
                + "Cluster names should be provided in case of multiple clusters present");
      }
      if (!clusterNames.add(clusterProperties.getName())) {
        throw new IllegalStateException(
            "Application config isn't valid. Two clusters can't have the same name");
      }
    }
  }
}
