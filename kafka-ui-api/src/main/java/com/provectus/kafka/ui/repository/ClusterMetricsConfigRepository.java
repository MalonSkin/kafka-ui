package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ClusterMetricsConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 集群指标采集配置 Repository。
 */
@Repository
public interface ClusterMetricsConfigRepository extends JpaRepository<ClusterMetricsConfigEntity, Long> {
}
