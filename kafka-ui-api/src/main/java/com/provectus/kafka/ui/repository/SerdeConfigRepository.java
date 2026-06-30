package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.SerdeConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 自定义序列化/反序列化器配置 Repository。
 */
@Repository
public interface SerdeConfigRepository extends JpaRepository<SerdeConfigEntity, Long> {
}
