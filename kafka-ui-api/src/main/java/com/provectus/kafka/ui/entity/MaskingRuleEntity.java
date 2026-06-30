package com.provectus.kafka.ui.entity;

import com.provectus.kafka.ui.entity.converter.JsonStringConverter;
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
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据脱敏规则实体。
 * <p>
 * 支持三种脱敏方式：REMOVE（移除字段）、MASK（字符掩码）、REPLACE（固定替换）。
 * 一个 Kafka 集群可以关联多个脱敏规则（1:N）。
 * </p>
 */
@Entity
@Table(name = "masking_rule")
@Data
@NoArgsConstructor
public class MaskingRuleEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则业务名称（唯一标识，用于同步匹配） */
    @Column(name = "name", nullable = true, length = 255)
    private String name;

    /** 所属集群（多对一） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private ClusterConfigEntity cluster;

    /** 脱敏类型（REMOVE / MASK / REPLACE） */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 需要脱敏的字段名列表（JSON 格式存储） */
    @Convert(converter = JsonStringConverter.class)
    @Column(name = "fields_json", columnDefinition = "CLOB")
    private List<String> fields;

    /** 需要脱敏的字段名正则表达式模式 */
    @Column(name = "fields_name_pattern")
    private String fieldsNamePattern;

    /** MASK 类型时的替换字符列表（JSON 格式存储） */
    @Convert(converter = JsonStringConverter.class)
    @Column(name = "masking_chars_json", columnDefinition = "CLOB")
    private List<String> maskingCharsReplacement;

    /** REPLACE 类型时的替换字符串 */
    @Column(name = "replacement")
    private String replacement;

    /** 适用的 Topic Key 正则表达式模式 */
    @Column(name = "topic_keys_pattern")
    private String topicKeysPattern;

    /** 适用的 Topic Value 正则表达式模式 */
    @Column(name = "topic_values_pattern")
    private String topicValuesPattern;
}
