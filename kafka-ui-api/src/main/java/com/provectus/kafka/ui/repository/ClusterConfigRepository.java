package com.provectus.kafka.ui.repository;

import com.provectus.kafka.ui.entity.ClusterConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 集群配置主表 Repository。
 * <p>
 * 提供集群配置的 CRUD 操作及带关联加载的查询方法。
 * </p>
 */
@Repository
public interface ClusterConfigRepository extends JpaRepository<ClusterConfigEntity, Long> {

    /** 根据集群名称查找配置 */
    Optional<ClusterConfigEntity> findByName(String name);

    /** 判断指定名称的集群是否存在 */
    boolean existsByName(String name);

    /**
     * 查询所有集群配置，同时加载全部关联子表（避免 N+1 问题）。
     */
    @Query("SELECT c FROM ClusterConfigEntity c "
            + "LEFT JOIN FETCH c.kafkaConnect "
            + "LEFT JOIN FETCH c.serdes "
            + "LEFT JOIN FETCH c.maskingRules "
            + "LEFT JOIN FETCH c.schemaRegistryAuth "
            + "LEFT JOIN FETCH c.schemaRegistrySsl "
            + "LEFT JOIN FETCH c.ksqldbServerAuth "
            + "LEFT JOIN FETCH c.ksqldbServerSsl "
            + "LEFT JOIN FETCH c.sslTruststore "
            + "LEFT JOIN FETCH c.sslKeystore "
            + "LEFT JOIN FETCH c.metrics "
            + "LEFT JOIN FETCH c.audit")
    List<ClusterConfigEntity> findAllWithAssociations();

    /**
     * 根据名称查询集群配置，同时加载全部关联子表。
     */
    @Query("SELECT c FROM ClusterConfigEntity c "
            + "LEFT JOIN FETCH c.kafkaConnect "
            + "LEFT JOIN FETCH c.serdes "
            + "LEFT JOIN FETCH c.maskingRules "
            + "LEFT JOIN FETCH c.schemaRegistryAuth "
            + "LEFT JOIN FETCH c.schemaRegistrySsl "
            + "LEFT JOIN FETCH c.ksqldbServerAuth "
            + "LEFT JOIN FETCH c.ksqldbServerSsl "
            + "LEFT JOIN FETCH c.sslTruststore "
            + "LEFT JOIN FETCH c.sslKeystore "
            + "LEFT JOIN FETCH c.metrics "
            + "LEFT JOIN FETCH c.audit "
            + "WHERE c.name = :name")
    Optional<ClusterConfigEntity> findByNameWithAssociations(@Param("name") String name);
}
