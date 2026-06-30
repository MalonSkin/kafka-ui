package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ConnectClusterConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Kafka Connect 集群配置 Repository。
 */
@Repository
public interface ConnectClusterConfigRepository extends JpaRepository<ConnectClusterConfigEntity, Long> {
}
