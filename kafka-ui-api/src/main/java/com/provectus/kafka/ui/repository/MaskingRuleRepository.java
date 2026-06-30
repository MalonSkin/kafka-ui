package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.MaskingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 数据脱敏规则 Repository。
 */
@Repository
public interface MaskingRuleRepository extends JpaRepository<MaskingRuleEntity, Long> {
}
