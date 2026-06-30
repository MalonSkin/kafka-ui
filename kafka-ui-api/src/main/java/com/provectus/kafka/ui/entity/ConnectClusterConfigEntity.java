package com.provectus.kafka.ui.entity;

import com.provectus.kafka.ui.entity.converter.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka Connect 集群配置实体。
 * <p>
 * 存储单个 Kafka Connect 集群的连接信息，包括地址、认证和 SSL Keystore 配置。
 * 一个 Kafka 集群可以关联多个 Connect 集群（1:N）。
 * </p>
 */
@Entity
@Table(name = "connect_cluster_config")
@Data
@NoArgsConstructor
public class ConnectClusterConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属集群（多对一） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private ClusterConfigEntity cluster;

    /** Connect 集群名称 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Connect 集群 REST API 地址 */
    @Column(name = "address", nullable = false, length = 512)
    private String address;

    /** 认证用户名 */
    @Column(name = "username")
    private String username;

    /** 认证密码（AES-GCM 加密存储） */
    @Column(name = "password")
    @Convert(converter = EncryptedStringConverter.class)
    private String password;

    /** Keystore 文件路径（用于 SSL 客户端认证） */
    @Column(name = "keystore_location")
    private String keystoreLocation;

    /** Keystore 密码（AES-GCM 加密存储） */
    @Column(name = "keystore_password")
    @Convert(converter = EncryptedStringConverter.class)
    private String keystorePassword;
}
