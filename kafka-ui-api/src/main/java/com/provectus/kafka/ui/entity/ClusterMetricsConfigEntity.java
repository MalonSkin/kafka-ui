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
 * 集群指标采集配置实体。
 * <p>
 * 存储 JMX 等指标采集方式的连接信息，包括端口、SSL、认证等配置。
 * </p>
 */
@Entity
@Table(name = "cluster_metrics_config")
@Data
@NoArgsConstructor
public class ClusterMetricsConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 指标采集类型（如 JMX） */
    @Column(name = "type")
    private String type;

    /** 指标服务端口 */
    @Column(name = "port")
    private Integer port;

    /** 是否启用 SSL */
    @Column(name = "ssl")
    private Boolean ssl;

    /** 认证用户名 */
    @Column(name = "username")
    private String username;

    /** 认证密码（AES-GCM 加密存储） */
    @Column(name = "password")
    @Convert(converter = EncryptedStringConverter.class)
    private String password;

    /** Keystore 文件路径 */
    @Column(name = "keystore_location")
    private String keystoreLocation;

    /** Keystore 密码（AES-GCM 加密存储） */
    @Column(name = "keystore_password")
    @Convert(converter = EncryptedStringConverter.class)
    private String keystorePassword;
}
