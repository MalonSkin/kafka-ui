package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.client.RetryingKafkaConnectClient;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.config.WebclientProperties;
import com.provectus.kafka.ui.connect.api.KafkaConnectClientApi;
import com.provectus.kafka.ui.emitter.PollingSettings;
import com.provectus.kafka.ui.model.ApplicationPropertyValidationDTO;
import com.provectus.kafka.ui.model.ClusterConfigValidationDTO;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.MetricsConfig;
import com.provectus.kafka.ui.service.ksql.KsqlApiClient;
import com.provectus.kafka.ui.service.masking.DataMasking;
import com.provectus.kafka.ui.sr.ApiClient;
import com.provectus.kafka.ui.sr.api.KafkaSrClientApi;
import com.provectus.kafka.ui.util.KafkaServicesValidation;
import com.provectus.kafka.ui.util.ReactiveFailover;
import com.provectus.kafka.ui.util.WebClientConfigurator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Kafka 集群工厂服务
 *
 * 负责根据配置创建 KafkaCluster 实例。每个 KafkaCluster 包含：
 * - Kafka AdminClient 连接
 * - Schema Registry 客户端（可选）
 * - Kafka Connect 客户端（可选）
 * - KSQL 客户端（可选）
 * - Metrics 配置（可选）
 *
 * 该工厂还提供集群配置验证功能，用于在添加新集群前验证连接是否可用。
 *
 * @author kafka-ui
 */
@Service
@Slf4j
public class KafkaClusterFactory {

  /** 默认 WebClient 内存缓冲区大小：20MB */
  private static final DataSize DEFAULT_WEBCLIENT_BUFFER = DataSize.parse("20MB");

  /** WebClient 最大内存缓冲区大小，用于处理大响应 */
  private final DataSize webClientMaxBuffSize;

  /**
   * 构造函数 - 初始化 WebClient 缓冲区配置
   *
   * @param webclientProperties WebClient 配置属性
   */
  public KafkaClusterFactory(WebclientProperties webclientProperties) {
    this.webClientMaxBuffSize = Optional.ofNullable(webclientProperties.getMaxInMemoryBufferSize())
        .map(DataSize::parse)
        .orElse(DEFAULT_WEBCLIENT_BUFFER);
  }

  /**
   * 根据配置创建 KafkaCluster 实例
   *
   * 创建过程包括：
   * 1. 基础配置：集群名称、Bootstrap Servers、自定义属性
   * 2. 安全配置：SSL/TLS、认证信息
   * 3. 数据脱敏配置：DataMasking 规则
   * 4. 轮询配置：PollingSettings
   * 5. 可选组件：Schema Registry、Kafka Connect、KSQL、Metrics
   *
   * @param properties       全局集群配置
   * @param clusterProperties 单个集群的配置
   * @return 创建好的 KafkaCluster 实例
   */
  public KafkaCluster create(ClustersProperties properties,
                             ClustersProperties.Cluster clusterProperties) {
    KafkaCluster.KafkaClusterBuilder builder = KafkaCluster.builder();

    // 设置基础配置
    builder.name(clusterProperties.getName());
    builder.bootstrapServers(clusterProperties.getBootstrapServers());
    builder.properties(convertProperties(clusterProperties.getProperties()));
    builder.readOnly(clusterProperties.isReadOnly());
    builder.masking(DataMasking.create(clusterProperties.getMasking()));
    builder.pollingSettings(PollingSettings.create(clusterProperties, properties));

    // 根据配置创建可选组件
    if (schemaRegistryConfigured(clusterProperties)) {
      builder.schemaRegistryClient(schemaRegistryClient(clusterProperties));
    }
    if (connectClientsConfigured(clusterProperties)) {
      builder.connectsClients(connectClients(clusterProperties));
    }
    if (ksqlConfigured(clusterProperties)) {
      builder.ksqlClient(ksqlClient(clusterProperties));
    }
    if (metricsConfigured(clusterProperties)) {
      builder.metricsConfig(metricsConfigDataToMetricsConfig(clusterProperties.getMetrics()));
    }
    builder.originalProperties(clusterProperties);
    return builder.build();
  }

  /**
   * 验证集群配置是否可用
   *
   * 验证内容包括：
   * 1. SSL/TLS 证书有效性
   * 2. Kafka 集群连接可用性
   * 3. Schema Registry 连接（如已配置）
   * 4. KSQL 连接（如已配置）
   * 5. Kafka Connect 连接（如已配置）
   *
   * @param clusterProperties 集群配置
   * @return 包含验证结果的 Mono，包括各组件的验证状态
   */
  public Mono<ClusterConfigValidationDTO> validate(ClustersProperties.Cluster clusterProperties) {
    // 验证 SSL 证书
    if (clusterProperties.getSsl() != null) {
      Optional<String> errMsg = KafkaServicesValidation.validateTruststore(clusterProperties.getSsl());
      if (errMsg.isPresent()) {
        return Mono.just(new ClusterConfigValidationDTO()
            .kafka(new ApplicationPropertyValidationDTO()
                .error(true)
                .errorMessage("Truststore not valid: " + errMsg.get())));
      }
    }

    // 并行验证所有组件连接
    return Mono.zip(
        KafkaServicesValidation.validateClusterConnection(clusterProperties),
        schemaRegistryConfigured(clusterProperties)
            ? KafkaServicesValidation.validateSchemaRegistry(
                () -> schemaRegistryClient(clusterProperties)).map(Optional::of)
            : Mono.<Optional<ApplicationPropertyValidationDTO>>just(Optional.empty()),

        ksqlConfigured(clusterProperties)
            ? KafkaServicesValidation.validateKsql(() -> ksqlClient(clusterProperties)).map(Optional::of)
            : Mono.<Optional<ApplicationPropertyValidationDTO>>just(Optional.empty()),

        connectClientsConfigured(clusterProperties)
            ?
            Flux.fromIterable(clusterProperties.getKafkaConnect())
                .flatMap(c ->
                    KafkaServicesValidation.validateConnect(() -> connectClient(clusterProperties, c))
                        .map(r -> Tuples.of(c.getName(), r)))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .map(Optional::of)
            :
            Mono.<Optional<Map<String, ApplicationPropertyValidationDTO>>>just(Optional.empty())
    ).map(tuple -> {
      var validation = new ClusterConfigValidationDTO();
      validation.kafka(tuple.getT1());
      tuple.getT2().ifPresent(validation::schemaRegistry);
      tuple.getT3().ifPresent(validation::ksqldb);
      tuple.getT4().ifPresent(validation::kafkaConnects);
      return validation;
    });
  }

  /**
   * 将 Map 转换为 Properties 对象
   *
   * @param propertiesMap 属性映射
   * @return Properties 对象
   */
  private Properties convertProperties(Map<String, Object> propertiesMap) {
    Properties properties = new Properties();
    if (propertiesMap != null) {
      properties.putAll(propertiesMap);
    }
    return properties;
  }

  /**
   * 检查是否配置了 Kafka Connect
   *
   * @param clusterProperties 集群配置
   * @return true 如果配置了 Kafka Connect
   */
  private boolean connectClientsConfigured(ClustersProperties.Cluster clusterProperties) {
    return clusterProperties.getKafkaConnect() != null;
  }

  /**
   * 创建所有 Kafka Connect 客户端
   *
   * 每个 Connect 集群配置会创建一个 ReactiveFailover 客户端，
   * 支持故障转移和重试机制。
   *
   * @param clusterProperties 集群配置
   * @return Connect 客户端映射，Key 为 Connect 集群名称
   */
  private Map<String, ReactiveFailover<KafkaConnectClientApi>> connectClients(
      ClustersProperties.Cluster clusterProperties) {
    Map<String, ReactiveFailover<KafkaConnectClientApi>> connects = new HashMap<>();
    clusterProperties.getKafkaConnect().forEach(c -> connects.put(c.getName(), connectClient(clusterProperties, c)));
    return connects;
  }

  /**
   * 创建单个 Kafka Connect 客户端
   *
   * 使用 ReactiveFailover 实现高可用，支持多个 Connect 地址的故障转移。
   *
   * @param cluster        集群配置
   * @param connectCluster Connect 集群配置
   * @return 带故障转移的 Connect 客户端
   */
  private ReactiveFailover<KafkaConnectClientApi> connectClient(ClustersProperties.Cluster cluster,
                                                                ClustersProperties.ConnectCluster connectCluster) {
    return ReactiveFailover.create(
        parseUrlList(connectCluster.getAddress()),
        url -> new RetryingKafkaConnectClient(
            connectCluster.toBuilder().address(url).build(),
            cluster.getSsl(),
            webClientMaxBuffSize
        ),
        ReactiveFailover.CONNECTION_REFUSED_EXCEPTION_FILTER,
        "No alive connect instances available",
        ReactiveFailover.DEFAULT_RETRY_GRACE_PERIOD_MS
    );
  }

  /**
   * 检查是否配置了 Schema Registry
   *
   * @param clusterProperties 集群配置
   * @return true 如果配置了 Schema Registry
   */
  private boolean schemaRegistryConfigured(ClustersProperties.Cluster clusterProperties) {
    return clusterProperties.getSchemaRegistry() != null;
  }

  /**
   * 创建 Schema Registry 客户端
   *
   * 使用 ReactiveFailover 实现高可用，支持多个 Schema Registry 地址的故障转移。
   * 配置包括：
   * - SSL/TLS 加密
   * - Basic Auth 认证
   * - 自定义缓冲区大小
   *
   * @param clusterProperties 集群配置
   * @return 带故障转移的 Schema Registry 客户端
   */
  private ReactiveFailover<KafkaSrClientApi> schemaRegistryClient(ClustersProperties.Cluster clusterProperties) {
    var auth = Optional.ofNullable(clusterProperties.getSchemaRegistryAuth())
        .orElse(new ClustersProperties.SchemaRegistryAuth());
    WebClient webClient = new WebClientConfigurator()
        .configureSsl(clusterProperties.getSsl(), clusterProperties.getSchemaRegistrySsl())
        .configureBasicAuth(auth.getUsername(), auth.getPassword())
        .configureBufferSize(webClientMaxBuffSize)
        .build();
    return ReactiveFailover.create(
        parseUrlList(clusterProperties.getSchemaRegistry()),
        url -> new KafkaSrClientApi(new ApiClient(webClient, null, null).setBasePath(url)),
        ReactiveFailover.CONNECTION_REFUSED_EXCEPTION_FILTER,
        "No live schemaRegistry instances available",
        ReactiveFailover.DEFAULT_RETRY_GRACE_PERIOD_MS
    );
  }

  /**
   * 检查是否配置了 KSQL
   *
   * @param clusterProperties 集群配置
   * @return true 如果配置了 KSQL
   */
  private boolean ksqlConfigured(ClustersProperties.Cluster clusterProperties) {
    return clusterProperties.getKsqldbServer() != null;
  }

  /**
   * 创建 KSQL 客户端
   *
   * 使用 ReactiveFailover 实现高可用，支持多个 KSQL 地址的故障转移。
   *
   * @param clusterProperties 集群配置
   * @return 带故障转移的 KSQL 客户端
   */
  private ReactiveFailover<KsqlApiClient> ksqlClient(ClustersProperties.Cluster clusterProperties) {
    return ReactiveFailover.create(
        parseUrlList(clusterProperties.getKsqldbServer()),
        url -> new KsqlApiClient(
            url,
            clusterProperties.getKsqldbServerAuth(),
            clusterProperties.getSsl(),
            clusterProperties.getKsqldbServerSsl(),
            webClientMaxBuffSize
        ),
        ReactiveFailover.CONNECTION_REFUSED_EXCEPTION_FILTER,
        "No live ksqldb instances available",
        ReactiveFailover.DEFAULT_RETRY_GRACE_PERIOD_MS
    );
  }

  /**
   * 解析逗号分隔的 URL 列表
   *
   * @param url 逗号分隔的 URL 字符串
   * @return URL 列表
   */
  private List<String> parseUrlList(String url) {
    return Stream.of(url.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  /**
   * 检查是否配置了 Metrics
   *
   * @param clusterProperties 集群配置
   * @return true 如果配置了 Metrics
   */
  private boolean metricsConfigured(ClustersProperties.Cluster clusterProperties) {
    return clusterProperties.getMetrics() != null;
  }

  /**
   * 将配置数据转换为 MetricsConfig 对象
   *
   * @param metricsConfigData Metrics 配置数据
   * @return MetricsConfig 对象，如果输入为 null 则返回 null
   */
  @Nullable
  private MetricsConfig metricsConfigDataToMetricsConfig(ClustersProperties.MetricsConfigData metricsConfigData) {
    if (metricsConfigData == null) {
      return null;
    }
    MetricsConfig.MetricsConfigBuilder builder = MetricsConfig.builder();
    builder.type(metricsConfigData.getType());
    builder.port(metricsConfigData.getPort());
    builder.ssl(Optional.ofNullable(metricsConfigData.getSsl()).orElse(false));
    builder.username(metricsConfigData.getUsername());
    builder.password(metricsConfigData.getPassword());
    builder.keystoreLocation(metricsConfigData.getKeystoreLocation());
    builder.keystorePassword(metricsConfigData.getKeystorePassword());
    return builder.build();
  }

}
