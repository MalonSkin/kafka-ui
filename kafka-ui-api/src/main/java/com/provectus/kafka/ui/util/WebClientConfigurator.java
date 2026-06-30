package com.provectus.kafka.ui.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.exception.ValidationException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.ResourceUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置器
 *
 * 提供流式 API（Builder 模式）来构建和配置 Spring WebClient 实例。
 * 主要用于创建访问 Kafka 关联服务（Schema Registry、Kafka Connect、KSQL 等）的 HTTP 客户端。
 *
 * 支持的配置项：
 * - SSL/TLS：配置 Truststore 和 Keystore，支持双向认证（mTLS）
 * - Basic Auth：HTTP 基本认证
 * - 缓冲区大小：控制响应体的最大内存缓冲大小
 * - ObjectMapper：自定义 JSON 序列化/反序列化
 * - 编解码器：自定义请求/响应的编解码配置
 * - 代理：自动读取系统代理配置
 *
 * 使用示例：
 * <pre>{@code
 * WebClient client = new WebClientConfigurator()
 *     .configureSsl(truststoreConfig, keystoreConfig)
 *     .configureBasicAuth(username, password)
 *     .configureBufferSize(DataSize.ofMegabytes(10))
 *     .build();
 * }</pre>
 *
 * @author kafka-ui
 */
public class WebClientConfigurator {

  /** WebClient 构建器，用于配置请求头、编解码器等 */
  private final WebClient.Builder builder = WebClient.builder();

  /** Reactor Netty HTTP 客户端，支持 SSL 和代理配置 */
  private HttpClient httpClient = HttpClient
      .create()
      .proxyWithSystemProperties();

  /**
   * 构造函数，初始化默认的 ObjectMapper 配置
   *
   * 默认配置：
   * - 注册 JavaTimeModule 支持 Java 8 时间类型
   * - 注册 JsonNullableModule 支持 JSON Nullable 包装类型
   * - 忽略未知属性，避免反序列化失败
   */
  public WebClientConfigurator() {
    configureObjectMapper(defaultOM());
  }

  /**
   * 创建默认的 ObjectMapper 实例
   *
   * @return 配置好的 ObjectMapper
   */
  private static ObjectMapper defaultOM() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new JsonNullableModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * 配置 SSL/TLS（使用配置对象）
   *
   * 从 TruststoreConfig 和 KeystoreConfig 中提取路径和密码，
   * 委托给底层的 configureSsl 方法。
   *
   * @param truststoreConfig Truststore 配置（可为 null）
   * @param keystoreConfig   Keystore 配置（可为 null），用于 mTLS 双向认证
   * @return 当前配置器实例，支持链式调用
   */
  public WebClientConfigurator configureSsl(@Nullable ClustersProperties.TruststoreConfig truststoreConfig,
                                            @Nullable ClustersProperties.KeystoreConfig keystoreConfig) {
    return configureSsl(
        keystoreConfig != null ? keystoreConfig.getKeystoreLocation() : null,
        keystoreConfig != null ? keystoreConfig.getKeystorePassword() : null,
        truststoreConfig != null ? truststoreConfig.getTruststoreLocation() : null,
        truststoreConfig != null ? truststoreConfig.getTruststorePassword() : null
    );
  }

  /**
   * 配置 SSL/TLS（使用原始路径和密码参数）
   *
   * 构建 SSL 上下文，支持：
   * - Truststore：验证服务端证书（单向认证）
   * - Keystore：提供客户端证书（双向认证 mTLS）
   *
   * 如果两个路径都为 null，则跳过 SSL 配置。
   *
   * @param keystoreLocation   Keystore 文件路径（可为 null）
   * @param keystorePassword   Keystore 密码（可为 null）
   * @param truststoreLocation Truststore 文件路径（可为 null）
   * @param truststorePassword Truststore 密码（可为 null）
   * @return 当前配置器实例，支持链式调用
   */
  @SneakyThrows
  private WebClientConfigurator configureSsl(
      @Nullable String keystoreLocation,
      @Nullable String keystorePassword,
      @Nullable String truststoreLocation,
      @Nullable String truststorePassword) {
    if (truststoreLocation == null && keystoreLocation == null) {
      return this;
    }

    SslContextBuilder contextBuilder = SslContextBuilder.forClient();
    // 加载 Truststore 用于验证服务端证书
    if (truststoreLocation != null && truststorePassword != null) {
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(
          new FileInputStream((ResourceUtils.getFile(truststoreLocation))),
          truststorePassword.toCharArray()
      );
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm()
      );
      trustManagerFactory.init(trustStore);
      contextBuilder.trustManager(trustManagerFactory);
    }

    // 加载 Keystore 用于客户端证书认证（mTLS）
    if (keystoreLocation != null && keystorePassword != null) {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(
          new FileInputStream(ResourceUtils.getFile(keystoreLocation)),
          keystorePassword.toCharArray()
      );

      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
      contextBuilder.keyManager(keyManagerFactory);
    }

    // 构建 SSL 上下文并应用到 HTTP 客户端
    SslContext context = contextBuilder.build();

    httpClient = httpClient.secure(t -> t.sslContext(context));
    return this;
  }

  /**
   * 配置 HTTP 基本认证
   *
   * 如果用户名和密码都不为 null，则设置 Basic Auth 请求头。
   * 如果只指定了其中一个而缺少另一个，抛出 ValidationException。
   *
   * @param username 用户名（可为 null）
   * @param password 密码（可为 null）
   * @return 当前配置器实例，支持链式调用
   * @throws ValidationException 如果用户名和密码不完整
   */
  public WebClientConfigurator configureBasicAuth(@Nullable String username, @Nullable String password) {
    if (username != null && password != null) {
      builder.defaultHeaders(httpHeaders -> httpHeaders.setBasicAuth(username, password));
    } else if (username != null) {
      throw new ValidationException("You specified username but did not specify password");
    } else if (password != null) {
      throw new ValidationException("You specified password but did not specify username");
    }
    return this;
  }

  /**
   * 配置响应体的最大内存缓冲大小
   *
   * 控制 WebClient 在内存中缓冲响应数据的最大量，
   * 超过此大小将触发错误。适用于处理大型响应的场景。
   *
   * @param maxBuffSize 最大缓冲大小
   * @return 当前配置器实例，支持链式调用
   */
  public WebClientConfigurator configureBufferSize(DataSize maxBuffSize) {
    builder.codecs(c -> c.defaultCodecs().maxInMemorySize((int) maxBuffSize.toBytes()));
    return this;
  }

  /**
   * 配置自定义 ObjectMapper
   *
   * 替换默认的 JSON 序列化/反序列化配置。
   * 同时设置编码器和解码器使用相同的 ObjectMapper。
   *
   * @param mapper 自定义 ObjectMapper 实例
   * @return 当前配置器实例，支持链式调用
   */
  public WebClientConfigurator configureObjectMapper(ObjectMapper mapper) {
    builder.codecs(codecs -> {
      codecs.defaultCodecs()
          .jackson2JsonEncoder(new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
      codecs.defaultCodecs()
          .jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
    });
    return this;
  }

  /**
   * 配置自定义编解码器
   *
   * 允许通过 Consumer 回调对 ClientCodecConfigurer 进行完全自定义配置。
   *
   * @param configurer 编解码器配置回调
   * @return 当前配置器实例，支持链式调用
   */
  public WebClientConfigurator configureCodecs(Consumer<ClientCodecConfigurer> configurer) {
    builder.codecs(configurer);
    return this;
  }

  /**
   * 构建最终的 WebClient 实例
   *
   * 将配置好的 HttpClient（含 SSL 和代理设置）连接到 WebClient.Builder，
   * 返回可用的 WebClient 实例。
   *
   * @return 配置完成的 WebClient 实例
   */
  public WebClient build() {
    return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }
}
