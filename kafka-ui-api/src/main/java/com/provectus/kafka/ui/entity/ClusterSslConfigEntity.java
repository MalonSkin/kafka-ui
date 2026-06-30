package com.provectus.kafka.ui.entity;

import com.provectus.kafka.ui.entity.converter.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集群 SSL 配置实体。
 * <p>
 * 存储 Truststore 和 Keystore 的路径及密码，同时服务于 Schema Registry SSL、
 * ksqlDB SSL、Kafka SSL Truststore/Keystore 等场景（1:1 复用）。
 * </p>
 */
@Entity
@Table(name = "cluster_ssl_config")
@Data
@NoArgsConstructor
public class ClusterSslConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Truststore 文件路径 */
    @Column(name = "truststore_location")
    private String truststoreLocation;

    /** Truststore 密码（AES-GCM 加密存储） */
    @Column(name = "truststore_password")
    @Convert(converter = EncryptedStringConverter.class)
    private String truststorePassword;

    /** Keystore 文件路径 */
    @Column(name = "keystore_location")
    private String keystoreLocation;

    /** Keystore 密码（AES-GCM 加密存储） */
    @Column(name = "keystore_password")
    @Convert(converter = EncryptedStringConverter.class)
    private String keystorePassword;
}
