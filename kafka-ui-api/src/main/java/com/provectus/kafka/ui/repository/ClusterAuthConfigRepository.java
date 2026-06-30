package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ClusterAuthConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 集群认证配置 Repository（1:1 复用，服务于 Schema Registry 和 ksqlDB）。
 */
@Repository
public interface ClusterAuthConfigRepository extends JpaRepository<ClusterAuthConfigEntity, Long> {
}
