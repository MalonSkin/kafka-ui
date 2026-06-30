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
 * 集群认证配置实体。
 * <p>
 * 存储用户名/密码认证信息，同时服务于 Schema Registry 和 ksqlDB 的认证配置（1:1 复用）。
 * </p>
 */
@Entity
@Table(name = "cluster_auth_config")
@Data
@NoArgsConstructor
public class ClusterAuthConfigEntity {

    /** 主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 认证用户名 */
    @Column(name = "username")
    private String username;

    /** 认证密码（AES-GCM 加密存储） */
    @Column(name = "password")
    @Convert(converter = EncryptedStringConverter.class)
    private String password;
}
