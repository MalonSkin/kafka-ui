package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ClusterAuditConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 集群审计配置 Repository。
 */
@Repository
public interface ClusterAuditConfigRepository extends JpaRepository<ClusterAuditConfigEntity, Long> {
}
