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
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义序列化/反序列化器配置实体。
 * <p>
 * 存储 SerDe 的类名、文件路径、自定义属性及 Topic 匹配模式。
 * 一个 Kafka 集群可以关联多个 Serde 配置（1:N）。
 * </p>
 */
@Entity
@Table(name = "serde_config")
@Data
@NoArgsConstructor
public class SerdeConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属集群（多对一） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private ClusterConfigEntity cluster;

    /** SerDe 名称 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** SerDe 实现类的全限定名 */
    @Column(name = "class_name")
    private String className;

    /** SerDe 实现的文件路径（用于外部 JAR 加载） */
    @Column(name = "file_path")
    private String filePath;

    /** SerDe 的自定义属性（JSON 格式存储） */
    @Convert(converter = JsonStringConverter.class)
    @Column(name = "properties_json", columnDefinition = "CLOB")
    private Map<String, Object> properties;

    /** 匹配 Topic Key 的正则表达式模式 */
    @Column(name = "topic_keys_pattern")
    private String topicKeysPattern;

    /** 匹配 Topic Value 的正则表达式模式 */
    @Column(name = "topic_values_pattern")
    private String topicValuesPattern;
}
