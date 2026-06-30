package com.provectus.kafka.ui.entity;

import com.provectus.kafka.ui.entity.converter.JsonStringConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集群配置主表实体。
 * <p>
 * 存储 Kafka 集群的核心连接信息，并通过外键关联 Schema Registry、ksqlDB、
 * SSL、Metrics、Audit 等配置子表，以及 Kafka Connect、Serde、Masking 等 1:N 子表。
 * </p>
 */
@Entity
@Table(name = "cluster_config")
@Data
@NoArgsConstructor
public class ClusterConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 集群名称（唯一） */
    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    /** Kafka Broker 地址 */
    @Column(name = "bootstrap_servers", nullable = false, length = 1024)
    private String bootstrapServers;

    // ==================== Schema Registry ====================

    /** Schema Registry 地址 */
    @Column(name = "schema_registry", length = 512)
    private String schemaRegistry;

    /** Schema Registry 认证配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_registry_auth_id")
    private ClusterAuthConfigEntity schemaRegistryAuth;

    /** Schema Registry SSL 配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_registry_ssl_id")
    private ClusterSslConfigEntity schemaRegistrySsl;

    // ==================== ksqlDB ====================

    /** ksqlDB Server 地址 */
    @Column(name = "ksqldb_server", length = 512)
    private String ksqldbServer;

    /** ksqlDB Server 认证配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "ksqldb_server_auth_id")
    private ClusterAuthConfigEntity ksqldbServerAuth;

    /** ksqlDB Server SSL 配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "ksqldb_server_ssl_id")
    private ClusterSslConfigEntity ksqldbServerSsl;

    // ==================== SSL ====================

    /** SSL Truststore 配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "ssl_truststore_id")
    private ClusterSslConfigEntity sslTruststore;

    /** SSL Keystore 配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "ssl_keystore_id")
    private ClusterSslConfigEntity sslKeystore;

    // ==================== Metrics ====================

    /** 指标采集配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "metrics_id")
    private ClusterMetricsConfigEntity metrics;

    // ==================== Audit ====================

    /** 审计配置（1:1） */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id")
    private ClusterAuditConfigEntity audit;

    // ==================== 1:N 关联表 ====================

    /** Kafka Connect 集群配置列表（1:N） */
    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ConnectClusterConfigEntity> kafkaConnect = new HashSet<>();

    /** 自定义序列化/反序列化配置列表（1:N） */
    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<SerdeConfigEntity> serdes = new HashSet<>();

    /** 数据脱敏规则列表（1:N） */
    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MaskingRuleEntity> maskingRules = new HashSet<>();

    // ==================== JSON 字段 ====================

    /** Kafka 客户端原生属性（JSON 格式存储） */
    @Convert(converter = JsonStringConverter.class)
    @Column(name = "properties_json", columnDefinition = "CLOB")
    private Map<String, Object> properties;

    // ==================== 标量字段 ====================

    /** 是否为只读模式 */
    @Column(name = "read_only")
    private boolean readOnly = false;

    /** 默认 Key 序列化器名称 */
    @Column(name = "default_key_serde", length = 255)
    private String defaultKeySerde;

    /** 默认 Value 序列化器名称 */
    @Column(name = "default_value_serde", length = 255)
    private String defaultValueSerde;

    /** 消息轮询限流速率 */
    @Column(name = "polling_throttle_rate")
    private Long pollingThrottleRate;

    // ==================== 时间戳 ====================

    /** 创建时间 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 持久化前自动设置创建时间和更新时间 */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** 更新前自动刷新更新时间 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
