package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ClusterSslConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 集群 SSL 配置 Repository（1:1 复用，服务于 Truststore 和 Keystore）。
 */
@Repository
public interface ClusterSslConfigRepository extends JpaRepository<ClusterSslConfigEntity, Long> {
}
