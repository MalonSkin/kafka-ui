package com.provectus.kafka.ui.util;

import com.provectus.kafka.ui.config.ClustersProperties;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;

/**
 * Kafka SSL 属性工具类
 *
 * 提供将 SSL/TLS 配置转换为 Kafka 客户端 Properties 的工具方法。
 * 用于 Kafka AdminClient、Producer、Consumer 等客户端的安全连接配置。
 *
 * 支持的 SSL 配置：
 * - Truststore：用于验证服务端证书（单向认证）
 * - Keystore：用于提供客户端证书（双向认证 mTLS）
 *
 * @author kafka-ui
 */
public final class SslPropertiesUtil {

  private SslPropertiesUtil() {
  }

  /**
   * 添加 Kafka Truststore 相关的 SSL 属性
   *
   * 将 Truststore 配置转换为 Kafka 客户端标准属性：
   * - ssl.truststore.location：Truststore 文件路径
   * - ssl.truststore.password：Truststore 密码
   *
   * 仅在 truststoreConfig 不为 null 且 truststoreLocation 已设置时才添加属性。
   * 密码为可选项，仅在配置了密码时才添加。
   *
   * @param truststoreConfig Truststore 配置（可为 null）
   * @param sink             目标 Properties 对象，属性将添加到此对象中
   */
  public static void addKafkaSslProperties(@Nullable ClustersProperties.TruststoreConfig truststoreConfig,
                                           Properties sink) {
    if (truststoreConfig != null && truststoreConfig.getTruststoreLocation() != null) {
      sink.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreConfig.getTruststoreLocation());
      if (truststoreConfig.getTruststorePassword() != null) {
        sink.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststoreConfig.getTruststorePassword());
      }
    }
  }

  /**
   * 添加 Kafka Keystore 相关的 SSL 属性
   *
   * 将 Keystore 配置转换为 Kafka 客户端标准属性：
   * - ssl.keystore.location：Keystore 文件路径
   * - ssl.keystore.password：Keystore 密码
   * - security.protocol：设置为 SSL（启用 Keystore 时自动切换为 SSL 协议）
   *
   * 注意：当配置了 Keystore 时，会自动将 security.protocol 设置为 SSL，
   * 以确保 Kafka 客户端使用 SSL 协议进行连接（支持 mTLS 双向认证）。
   *
   * @param sslKeystoreConfig Keystore 配置（可为 null）
   * @param sink              目标 Properties 对象，属性将添加到此对象中
   */
  public static void addKafkaSslKeyStoreConfig(@Nullable ClustersProperties.KeystoreConfig sslKeystoreConfig,
                                           Properties sink) {
    if (sslKeystoreConfig != null && sslKeystoreConfig.getKeystoreLocation() != null) {
      sink.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreConfig.getKeystoreLocation());
      if (sslKeystoreConfig.getKeystorePassword() != null) {
        sink.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystoreConfig.getKeystorePassword());
      }
      // 配置 Keystore 意味着需要 mTLS，自动切换为 SSL 协议
      sink.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name);
    }
  }
}
