package com.provectus.kafka.ui.util;

import com.provectus.kafka.ui.config.ClustersProperties;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;

public final class SslPropertiesUtil {

  private SslPropertiesUtil() {
  }

  public static void addKafkaSslProperties(@Nullable ClustersProperties.TruststoreConfig truststoreConfig,
                                           Properties sink) {
    if (truststoreConfig != null && truststoreConfig.getTruststoreLocation() != null) {
      sink.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreConfig.getTruststoreLocation());
      if (truststoreConfig.getTruststorePassword() != null) {
        sink.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststoreConfig.getTruststorePassword());
      }
    }
  }

  public static void addKafkaSslKeyStoreConfig(@Nullable ClustersProperties.KeystoreConfig sslKeystoreConfig,
                                           Properties sink) {
    if (sslKeystoreConfig != null && sslKeystoreConfig.getKeystoreLocation() != null) {
      sink.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreConfig.getKeystoreLocation());
      if (sslKeystoreConfig.getKeystorePassword() != null) {
        sink.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystoreConfig.getKeystorePassword());
      }
      // 还要设置security.protocol为SSL
      sink.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name);
    }
  }
}
