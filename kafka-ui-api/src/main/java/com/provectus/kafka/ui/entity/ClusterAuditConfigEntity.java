package com.provectus.kafka.ui.entity;

import com.provectus.kafka.ui.entity.converter.JsonStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集群审计配置实体。
 * <p>
 * 存储审计日志的输出目标（Topic/控制台）、审计级别等配置信息。
 * </p>
 */
@Entity
@Table(name = "cluster_audit_config")
@Data
@NoArgsConstructor
public class ClusterAuditConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 审计 Topic 名称 */
    @Column(name = "topic")
    private String topic;

    /** 审计 Topic 的分区数 */
    @Column(name = "audit_topics_partitions")
    private Integer auditTopicsPartitions;

    /** 是否启用 Topic 审计 */
    @Column(name = "topic_audit_enabled")
    private Boolean topicAuditEnabled;

    /** 是否启用控制台审计输出 */
    @Column(name = "console_audit_enabled")
    private Boolean consoleAuditEnabled;

    /** 审计日志级别（ALL / ALTER_ONLY） */
    @Column(name = "level")
    private String level;

    /** 审计 Topic 的自定义属性（JSON 格式存储） */
    @Convert(converter = JsonStringConverter.class)
    @Column(name = "audit_topic_properties", columnDefinition = "CLOB")
    private Map<String, Object> auditTopicProperties;
}
