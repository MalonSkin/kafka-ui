package com.provectus.kafka.ui.util;

import static com.provectus.kafka.ui.config.ClustersProperties.TruststoreConfig;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.connect.api.KafkaConnectClientApi;
import com.provectus.kafka.ui.model.ApplicationPropertyValidationDTO;
import com.provectus.kafka.ui.service.ReactiveAdminClient;
import com.provectus.kafka.ui.service.ksql.KsqlApiClient;
import com.provectus.kafka.ui.sr.api.KafkaSrClientApi;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.util.ResourceUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka 服务连接验证工具类
 *
 * 提供对 Kafka 集群及其关联服务（Schema Registry、Kafka Connect、KSQL）的连接验证功能。
 * 用于在添加或编辑集群配置时，验证各服务的可达性和配置正确性。
 *
 * 验证流程：
 * 1. 根据配置创建对应的客户端
 * 2. 尝试执行一个轻量级操作（如获取主题列表、兼容性级别等）
 * 3. 返回验证结果 DTO，包含成功/失败状态和错误信息
 *
 * 所有验证方法返回 {@link ApplicationPropertyValidationDTO}：
 * - error=false 表示验证通过
 * - error=true 且 errorMessage 包含具体错误描述
 *
 * @author kafka-ui
 */
@Slf4j
public final class KafkaServicesValidation {

  private KafkaServicesValidation() {
  }

  /**
   * 创建验证通过的结果
   *
   * @return error=false 的验证结果 DTO
   */
  private static Mono<ApplicationPropertyValidationDTO> valid() {
    return Mono.just(new ApplicationPropertyValidationDTO().error(false));
  }

  /**
   * 创建验证失败的结果（基于错误消息）
   *
   * @param errorMsg 错误描述信息
   * @return error=true 的验证结果 DTO
   */
  private static Mono<ApplicationPropertyValidationDTO> invalid(String errorMsg) {
    return Mono.just(new ApplicationPropertyValidationDTO().error(true).errorMessage(errorMsg));
  }

  /**
   * 创建验证失败的结果（基于异常）
   *
   * @param th 异常对象，取其 message 作为错误描述
   * @return error=true 的验证结果 DTO
   */
  private static Mono<ApplicationPropertyValidationDTO> invalid(Throwable th) {
    return Mono.just(new ApplicationPropertyValidationDTO().error(true).errorMessage(th.getMessage()));
  }

  /**
   * 验证 Truststore 配置的有效性
   *
   * 尝试加载指定的 Truststore 文件并初始化 TrustManagerFactory，
   * 验证文件路径、密码和格式是否正确。
   *
   * @param truststoreConfig Truststore 配置（路径和密码）
   * @return 如果验证通过返回 empty；如果失败返回包含错误消息的 Optional
   */
  public static Optional<String> validateTruststore(TruststoreConfig truststoreConfig) {
    if (truststoreConfig.getTruststoreLocation() != null && truststoreConfig.getTruststorePassword() != null) {
      try (FileInputStream fileInputStream = new FileInputStream(
             (ResourceUtils.getFile(truststoreConfig.getTruststoreLocation())))) {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(fileInputStream, truststoreConfig.getTruststorePassword().toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        trustManagerFactory.init(trustStore);
      } catch (Exception e) {
        return Optional.of(e.getMessage());
      }
    }
    return Optional.empty();
  }

  /**
   * 将 Map 转换为 Properties 对象
   *
   * @param propertiesMap 属性键值对 Map，可为 null
   * @return 转换后的 Properties 对象
   */
  private static Properties convertProperties(Map<String, Object> propertiesMap) {
    Properties properties = new Properties();
    if (propertiesMap != null) {
      properties.putAll(propertiesMap);
    }
    return properties;
  }

  /**
   * 验证 Kafka 集群连接
   *
   * 创建 AdminClient 并尝试执行 listTopics 操作来验证集群的可达性。
   * 验证完成后自动关闭 AdminClient 释放资源。
   *
   * 验证配置优化：
   * - 设置较短的超时时间（5秒）以加快验证速度
   * - 设置重试次数为 1 以减少等待
   * - 使用唯一客户端 ID 避免冲突
   *
   * @param clusterProperties 集群配置属性
   * @return 验证结果的 Mono
   */
  public static Mono<ApplicationPropertyValidationDTO> validateClusterConnection(
      ClustersProperties.Cluster clusterProperties) {
    String bootstrapServers = clusterProperties.getBootstrapServers();
    Properties clusterProps = convertProperties(clusterProperties.getProperties());
    TruststoreConfig ssl = clusterProperties.getSsl();
    ClustersProperties.KeystoreConfig sslKeystoreConfig = clusterProperties.getSslKeystoreConfig();

    Properties properties = new Properties();
    SslPropertiesUtil.addKafkaSslProperties(ssl, properties);
    // 设置SSL的Keystore配置
    SslPropertiesUtil.addKafkaSslKeyStoreConfig(sslKeystoreConfig, properties);
    // 设置其他参数
    properties.putAll(clusterProps);
    // 默认设置禁用主机名验证，避免自签名证书导致的连接失败
    if (!clusterProps.containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)) {
      properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, StringUtils.EMPTY);
    }
    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    // 缩短超时和重试，加快验证速度
    properties.put(AdminClientConfig.RETRIES_CONFIG, 1);
    properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
    properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5_000);
    properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "kui-admin-client-validation-" + System.currentTimeMillis());
    AdminClient adminClient = null;
    try {
      adminClient = AdminClient.create(properties);
    } catch (Exception e) {
      log.error("Error creating admin client during validation", e);
      return invalid("Error while creating AdminClient. See logs for details.");
    }
    return Mono.just(adminClient)
        .then(ReactiveAdminClient.toMono(adminClient.listTopics().names()))
        .then(valid())
        .doOnTerminate(adminClient::close)
        .onErrorResume(th -> {
          log.error("Error connecting to cluster", th);
          return KafkaServicesValidation.invalid("Error connecting to cluster. See logs for details.");
        });
  }

  /**
   * 验证 Schema Registry 连接
   *
   * 通过获取全局兼容性级别来验证 Schema Registry 的可达性。
   * 使用 ReactiveFailover 支持多 Schema Registry 的故障转移。
   *
   * @param clientSupplier Schema Registry 客户端的供应者
   * @return 验证结果的 Mono
   */
  public static Mono<ApplicationPropertyValidationDTO> validateSchemaRegistry(
      Supplier<ReactiveFailover<KafkaSrClientApi>> clientSupplier) {
    ReactiveFailover<KafkaSrClientApi> client;
    try {
      client = clientSupplier.get();
    } catch (Exception e) {
      log.error("Error creating Schema Registry client", e);
      return invalid("Error creating Schema Registry client: " + e.getMessage());
    }
    return client
        .mono(KafkaSrClientApi::getGlobalCompatibilityLevel)
        .then(valid())
        .onErrorResume(KafkaServicesValidation::invalid);
  }

  /**
   * 验证 Kafka Connect 连接
   *
   * 通过获取连接器插件列表来验证 Kafka Connect 的可达性。
   * 使用 ReactiveFailover 支持多 Connect 集群的故障转移。
   *
   * @param clientSupplier Kafka Connect 客户端的供应者
   * @return 验证结果的 Mono
   */
  public static Mono<ApplicationPropertyValidationDTO> validateConnect(
      Supplier<ReactiveFailover<KafkaConnectClientApi>> clientSupplier) {
    ReactiveFailover<KafkaConnectClientApi> client;
    try {
      client = clientSupplier.get();
    } catch (Exception e) {
      log.error("Error creating Connect client", e);
      return invalid("Error creating Connect client: " + e.getMessage());
    }
    return client.flux(KafkaConnectClientApi::getConnectorPlugins)
        .collectList()
        .then(valid())
        .onErrorResume(KafkaServicesValidation::invalid);
  }

  /**
   * 验证 KSQL 连接
   *
   * 通过执行 "SHOW VARIABLES;" 语句来验证 KSQL 的可达性。
   * 同时检查返回结果中是否包含错误响应。
   * 使用 ReactiveFailover 支持多 KSQL 集群的故障转移。
   *
   * @param clientSupplier KSQL 客户端的供应者
   * @return 验证结果的 Mono
   */
  public static Mono<ApplicationPropertyValidationDTO> validateKsql(
      Supplier<ReactiveFailover<KsqlApiClient>> clientSupplier) {
    ReactiveFailover<KsqlApiClient> client;
    try {
      client = clientSupplier.get();
    } catch (Exception e) {
      log.error("Error creating Ksql client", e);
      return invalid("Error creating Ksql client: " + e.getMessage());
    }
    return client.flux(c -> c.execute("SHOW VARIABLES;", Map.of()))
        .collectList()
        .flatMap(ksqlResults ->
            // 检查返回结果中是否有错误响应
            Flux.fromIterable(ksqlResults)
                .filter(KsqlApiClient.KsqlResponseTable::isError)
                .flatMap(err -> invalid("Error response from ksql: " + err))
                .next()
                .switchIfEmpty(valid())
        )
        .onErrorResume(KafkaServicesValidation::invalid);
  }


}
