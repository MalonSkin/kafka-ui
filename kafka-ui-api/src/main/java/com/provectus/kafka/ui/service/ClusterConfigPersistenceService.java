package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.entity.ClusterAuditConfigEntity;
import com.provectus.kafka.ui.entity.ClusterAuthConfigEntity;
import com.provectus.kafka.ui.entity.ClusterConfigEntity;
import com.provectus.kafka.ui.entity.ClusterMetricsConfigEntity;
import com.provectus.kafka.ui.entity.ClusterSslConfigEntity;
import com.provectus.kafka.ui.entity.ConnectClusterConfigEntity;
import com.provectus.kafka.ui.entity.MaskingRuleEntity;
import com.provectus.kafka.ui.entity.SerdeConfigEntity;
import com.provectus.kafka.ui.repository.ClusterConfigRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集群配置持久化服务。
 * <p>
 * 负责 ClustersProperties.Cluster 与 ClusterConfigEntity 之间的双向转换，
 * 以及集群配置的数据库 CRUD 操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterConfigPersistenceService {

    private final ClusterConfigRepository clusterConfigRepository;

    /**
     * 保存或更新集群配置到数据库。
     * <p>
     * 如果同名集群已存在，则更新其配置；否则创建新记录。
     * </p>
     *
     * @param clusterProperties 集群配置属性
     * @return 保存后的实体
     */
    @Transactional
    public ClusterConfigEntity saveCluster(ClustersProperties.Cluster clusterProperties) {
        String name = clusterProperties.getName();
        ClusterConfigEntity existing = clusterConfigRepository.findByNameWithAssociations(name).orElse(null);
        ClusterConfigEntity entity = toEntity(clusterProperties, existing);
        ClusterConfigEntity saved = clusterConfigRepository.save(entity);
        log.info("集群配置已持久化到数据库: {}", name);
        return saved;
    }

    /**
     * 从数据库加载所有集群配置并转换为 ClustersProperties.Cluster 列表。
     *
     * @return 集群配置列表
     */
    @Transactional(readOnly = true)
    public List<ClustersProperties.Cluster> loadAllClusters() {
        List<ClusterConfigEntity> entities = clusterConfigRepository.findAllWithAssociations();
        return entities.stream()
                .map(this::toProperties)
                .collect(Collectors.toList());
    }

    /**
     * 根据集群名称删除配置。
     *
     * @param name 集群名称
     * @return true 如果删除成功，false 如果集群不存在
     */
    @Transactional
    public boolean deleteCluster(String name) {
        Optional<ClusterConfigEntity> entity = clusterConfigRepository.findByName(name);
        if (entity.isPresent()) {
            clusterConfigRepository.delete(entity.get());
            log.info("集群配置已从数据库删除: {}", name);
            return true;
        }
        log.warn("尝试删除不存在的集群配置: {}", name);
        return false;
    }

    /**
     * 检查指定名称的集群是否存在于数据库中。
     *
     * @param name 集群名称
     * @return true 如果存在
     */
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return clusterConfigRepository.existsByName(name);
    }

    /**
     * 将 ClusterConfigEntity 转换为 ClustersProperties.Cluster。
     *
     * @param entity 数据库实体
     * @return 集群配置对象
     */
    public ClustersProperties.Cluster toProperties(ClusterConfigEntity entity) {
        ClustersProperties.Cluster cluster = new ClustersProperties.Cluster();
        cluster.setName(entity.getName());
        cluster.setBootstrapServers(entity.getBootstrapServers());
        cluster.setReadOnly(entity.isReadOnly());
        cluster.setDefaultKeySerde(entity.getDefaultKeySerde());
        cluster.setDefaultValueSerde(entity.getDefaultValueSerde());
        cluster.setPollingThrottleRate(entity.getPollingThrottleRate());
        cluster.setProperties(entity.getProperties() != null ? new HashMap<>(entity.getProperties()) : null);

        // Schema Registry 配置
        cluster.setSchemaRegistry(entity.getSchemaRegistry());
        if (entity.getSchemaRegistryAuth() != null) {
            ClustersProperties.SchemaRegistryAuth auth = new ClustersProperties.SchemaRegistryAuth();
            auth.setUsername(entity.getSchemaRegistryAuth().getUsername());
            auth.setPassword(entity.getSchemaRegistryAuth().getPassword());
            cluster.setSchemaRegistryAuth(auth);
        }
        if (entity.getSchemaRegistrySsl() != null) {
            ClustersProperties.KeystoreConfig ssl = new ClustersProperties.KeystoreConfig();
            ssl.setKeystoreLocation(entity.getSchemaRegistrySsl().getKeystoreLocation());
            ssl.setKeystorePassword(entity.getSchemaRegistrySsl().getKeystorePassword());
            cluster.setSchemaRegistrySsl(ssl);
        }

        // ksqlDB 配置
        cluster.setKsqldbServer(entity.getKsqldbServer());
        if (entity.getKsqldbServerAuth() != null) {
            ClustersProperties.KsqldbServerAuth auth = new ClustersProperties.KsqldbServerAuth();
            auth.setUsername(entity.getKsqldbServerAuth().getUsername());
            auth.setPassword(entity.getKsqldbServerAuth().getPassword());
            cluster.setKsqldbServerAuth(auth);
        }
        if (entity.getKsqldbServerSsl() != null) {
            ClustersProperties.KeystoreConfig ssl = new ClustersProperties.KeystoreConfig();
            ssl.setKeystoreLocation(entity.getKsqldbServerSsl().getKeystoreLocation());
            ssl.setKeystorePassword(entity.getKsqldbServerSsl().getKeystorePassword());
            cluster.setKsqldbServerSsl(ssl);
        }

        // SSL Truststore/Keystore 配置
        if (entity.getSslTruststore() != null) {
            ClustersProperties.TruststoreConfig ts = new ClustersProperties.TruststoreConfig();
            ts.setTruststoreLocation(entity.getSslTruststore().getTruststoreLocation());
            ts.setTruststorePassword(entity.getSslTruststore().getTruststorePassword());
            cluster.setSsl(ts);
        }
        if (entity.getSslKeystore() != null) {
            ClustersProperties.KeystoreConfig ks = new ClustersProperties.KeystoreConfig();
            ks.setKeystoreLocation(entity.getSslKeystore().getKeystoreLocation());
            ks.setKeystorePassword(entity.getSslKeystore().getKeystorePassword());
            cluster.setSslKeystoreConfig(ks);
        }

        // Kafka Connect 配置（1:N）
        if (entity.getKafkaConnect() != null && !entity.getKafkaConnect().isEmpty()) {
            cluster.setKafkaConnect(entity.getKafkaConnect().stream()
                    .map(this::toConnectCluster)
                    .collect(Collectors.toList()));
        }

        // Serde 配置（1:N）
        if (entity.getSerdes() != null && !entity.getSerdes().isEmpty()) {
            cluster.setSerde(entity.getSerdes().stream()
                    .map(this::toSerdeConfig)
                    .collect(Collectors.toList()));
        }

        // Masking 规则（1:N）
        if (entity.getMaskingRules() != null && !entity.getMaskingRules().isEmpty()) {
            cluster.setMasking(entity.getMaskingRules().stream()
                    .map(this::toMasking)
                    .collect(Collectors.toList()));
        }

        // Metrics 配置
        if (entity.getMetrics() != null) {
            cluster.setMetrics(toMetricsConfigData(entity.getMetrics()));
        }

        // Audit 配置
        if (entity.getAudit() != null) {
            cluster.setAudit(toAuditProperties(entity.getAudit()));
        }

        return cluster;
    }

    /**
     * 将 ClustersProperties.Cluster 转换为 ClusterConfigEntity。
     * <p>
     * 如果提供了 existing 实体，则在其基础上更新；否则创建新实体。
     * </p>
     *
     * @param cluster   集群配置
     * @param existing  已存在的实体（可为 null）
     * @return 转换后的实体
     */
    public ClusterConfigEntity toEntity(ClustersProperties.Cluster cluster, ClusterConfigEntity existing) {
        ClusterConfigEntity entity = existing != null ? existing : new ClusterConfigEntity();
        entity.setName(cluster.getName());
        entity.setBootstrapServers(cluster.getBootstrapServers());
        entity.setReadOnly(cluster.isReadOnly());
        entity.setDefaultKeySerde(cluster.getDefaultKeySerde());
        entity.setDefaultValueSerde(cluster.getDefaultValueSerde());
        entity.setPollingThrottleRate(cluster.getPollingThrottleRate());
        entity.setProperties(cluster.getProperties());

        // Schema Registry 配置
        entity.setSchemaRegistry(cluster.getSchemaRegistry());
        entity.setSchemaRegistryAuth(toAuthEntity(cluster.getSchemaRegistryAuth(), entity.getSchemaRegistryAuth()));
        entity.setSchemaRegistrySsl(toSslEntityForKeystore(cluster.getSchemaRegistrySsl(), entity.getSchemaRegistrySsl()));

        // ksqlDB 配置
        entity.setKsqldbServer(cluster.getKsqldbServer());
        entity.setKsqldbServerAuth(toKsqldbAuthEntity(cluster.getKsqldbServerAuth(), entity.getKsqldbServerAuth()));
        entity.setKsqldbServerSsl(toSslEntityForKeystore(cluster.getKsqldbServerSsl(), entity.getKsqldbServerSsl()));

        // SSL Truststore/Keystore 配置
        entity.setSslTruststore(toSslEntityForTruststore(cluster.getSsl(), entity.getSslTruststore()));
        entity.setSslKeystore(toSslEntityForKeystore(cluster.getSslKeystoreConfig(), entity.getSslKeystore()));

        // Metrics 配置
        entity.setMetrics(toMetricsEntity(cluster.getMetrics(), entity.getMetrics()));

        // Audit 配置
        entity.setAudit(toAuditEntity(cluster.getAudit(), entity.getAudit()));

        // Kafka Connect 配置（1:N）
        syncConnectClusters(entity, cluster.getKafkaConnect());

        // Serde 配置（1:N）
        syncSerdeConfigs(entity, cluster.getSerde());

        // Masking 规则（1:N）
        syncMaskingRules(entity, cluster.getMasking());

        return entity;
    }

    // ==================== 私有转换方法 ====================

    /**
     * 转换 ConnectCluster 配置为实体
     */
    private ConnectClusterConfigEntity toConnectClusterEntity(
            ClustersProperties.ConnectCluster cc, ConnectClusterConfigEntity existing) {
        ConnectClusterConfigEntity entity = existing != null ? existing : new ConnectClusterConfigEntity();
        entity.setName(cc.getName());
        entity.setAddress(cc.getAddress());
        entity.setUsername(cc.getUsername());
        entity.setPassword(cc.getPassword());
        entity.setKeystoreLocation(cc.getKeystoreLocation());
        entity.setKeystorePassword(cc.getKeystorePassword());
        return entity;
    }

    /**
     * 转换 ConnectClusterConfigEntity 为配置对象
     */
    private ClustersProperties.ConnectCluster toConnectCluster(ConnectClusterConfigEntity entity) {
        ClustersProperties.ConnectCluster cc = new ClustersProperties.ConnectCluster();
        cc.setName(entity.getName());
        cc.setAddress(entity.getAddress());
        cc.setUsername(entity.getUsername());
        cc.setPassword(entity.getPassword());
        cc.setKeystoreLocation(entity.getKeystoreLocation());
        cc.setKeystorePassword(entity.getKeystorePassword());
        return cc;
    }

    /**
     * 转换 SerdeConfig 配置为实体
     */
    private SerdeConfigEntity toSerdeConfigEntity(
            ClustersProperties.SerdeConfig sc, SerdeConfigEntity existing) {
        SerdeConfigEntity entity = existing != null ? existing : new SerdeConfigEntity();
        entity.setName(sc.getName());
        entity.setClassName(sc.getClassName());
        entity.setFilePath(sc.getFilePath());
        entity.setProperties(sc.getProperties());
        entity.setTopicKeysPattern(sc.getTopicKeysPattern());
        entity.setTopicValuesPattern(sc.getTopicValuesPattern());
        return entity;
    }

    /**
     * 转换 SerdeConfigEntity 为配置对象
     */
    private ClustersProperties.SerdeConfig toSerdeConfig(SerdeConfigEntity entity) {
        ClustersProperties.SerdeConfig sc = new ClustersProperties.SerdeConfig();
        sc.setName(entity.getName());
        sc.setClassName(entity.getClassName());
        sc.setFilePath(entity.getFilePath());
        sc.setProperties(entity.getProperties());
        sc.setTopicKeysPattern(entity.getTopicKeysPattern());
        sc.setTopicValuesPattern(entity.getTopicValuesPattern());
        return sc;
    }

    /**
     * 转换 Masking 配置为实体
     */
    private MaskingRuleEntity toMaskingEntity(ClustersProperties.Masking m, MaskingRuleEntity existing) {
        MaskingRuleEntity entity = existing != null ? existing : new MaskingRuleEntity();
        entity.setName(m.getName());
        entity.setType(m.getType() != null ? m.getType().name() : null);
        entity.setFields(m.getFields());
        entity.setFieldsNamePattern(m.getFieldsNamePattern());
        entity.setMaskingCharsReplacement(m.getMaskingCharsReplacement());
        entity.setReplacement(m.getReplacement());
        entity.setTopicKeysPattern(m.getTopicKeysPattern());
        entity.setTopicValuesPattern(m.getTopicValuesPattern());
        return entity;
    }

    /**
     * 转换 MaskingRuleEntity 为配置对象
     */
    private ClustersProperties.Masking toMasking(MaskingRuleEntity entity) {
        ClustersProperties.Masking m = new ClustersProperties.Masking();
        m.setName(entity.getName());
        if (entity.getType() != null) {
            m.setType(ClustersProperties.Masking.Type.valueOf(entity.getType()));
        }
        m.setFields(entity.getFields());
        m.setFieldsNamePattern(entity.getFieldsNamePattern());
        m.setMaskingCharsReplacement(entity.getMaskingCharsReplacement());
        m.setReplacement(entity.getReplacement());
        m.setTopicKeysPattern(entity.getTopicKeysPattern());
        m.setTopicValuesPattern(entity.getTopicValuesPattern());
        return m;
    }

    /**
     * 转换 MetricsConfigData 为实体
     */
    private ClusterMetricsConfigEntity toMetricsEntity(
            ClustersProperties.MetricsConfigData data, ClusterMetricsConfigEntity existing) {
        if (data == null) {
            return null;
        }
        ClusterMetricsConfigEntity entity = existing != null ? existing : new ClusterMetricsConfigEntity();
        entity.setType(data.getType());
        entity.setPort(data.getPort());
        entity.setSsl(data.getSsl());
        entity.setUsername(data.getUsername());
        entity.setPassword(data.getPassword());
        entity.setKeystoreLocation(data.getKeystoreLocation());
        entity.setKeystorePassword(data.getKeystorePassword());
        return entity;
    }

    /**
     * 转换 ClusterMetricsConfigEntity 为配置对象
     */
    private ClustersProperties.MetricsConfigData toMetricsConfigData(ClusterMetricsConfigEntity entity) {
        ClustersProperties.MetricsConfigData data = new ClustersProperties.MetricsConfigData();
        data.setType(entity.getType());
        data.setPort(entity.getPort());
        data.setSsl(entity.getSsl());
        data.setUsername(entity.getUsername());
        data.setPassword(entity.getPassword());
        data.setKeystoreLocation(entity.getKeystoreLocation());
        data.setKeystorePassword(entity.getKeystorePassword());
        return data;
    }

    /**
     * 转换 AuditProperties 为实体
     */
    private ClusterAuditConfigEntity toAuditEntity(
            ClustersProperties.AuditProperties data, ClusterAuditConfigEntity existing) {
        if (data == null) {
            return null;
        }
        ClusterAuditConfigEntity entity = existing != null ? existing : new ClusterAuditConfigEntity();
        entity.setTopic(data.getTopic());
        entity.setAuditTopicsPartitions(data.getAuditTopicsPartitions());
        entity.setTopicAuditEnabled(data.getTopicAuditEnabled());
        entity.setConsoleAuditEnabled(data.getConsoleAuditEnabled());
        entity.setLevel(data.getLevel() != null ? data.getLevel().name() : null);
        if (data.getAuditTopicProperties() != null) {
            entity.setAuditTopicProperties(new HashMap<>(data.getAuditTopicProperties()));
        }
        return entity;
    }

    /**
     * 转换 ClusterAuditConfigEntity 为配置对象
     */
    private ClustersProperties.AuditProperties toAuditProperties(ClusterAuditConfigEntity entity) {
        ClustersProperties.AuditProperties data = new ClustersProperties.AuditProperties();
        data.setTopic(entity.getTopic());
        data.setAuditTopicsPartitions(entity.getAuditTopicsPartitions());
        data.setTopicAuditEnabled(entity.getTopicAuditEnabled());
        data.setConsoleAuditEnabled(entity.getConsoleAuditEnabled());
        if (entity.getLevel() != null) {
            data.setLevel(ClustersProperties.AuditProperties.LogLevel.valueOf(entity.getLevel()));
        }
        if (entity.getAuditTopicProperties() != null) {
            Map<String, String> topicProps = new HashMap<>();
            entity.getAuditTopicProperties().forEach((k, v) -> topicProps.put(k, String.valueOf(v)));
            data.setAuditTopicProperties(topicProps);
        }
        return data;
    }

    /**
     * 转换认证配置为实体（Schema Registry Auth）
     */
    private ClusterAuthConfigEntity toAuthEntity(
            ClustersProperties.SchemaRegistryAuth auth, ClusterAuthConfigEntity existing) {
        if (auth == null) {
            return null;
        }
        ClusterAuthConfigEntity entity = existing != null ? existing : new ClusterAuthConfigEntity();
        entity.setUsername(auth.getUsername());
        entity.setPassword(auth.getPassword());
        return entity;
    }

    /**
     * 转换认证配置为实体（ksqlDB Auth）
     */
    private ClusterAuthConfigEntity toKsqldbAuthEntity(
            ClustersProperties.KsqldbServerAuth auth, ClusterAuthConfigEntity existing) {
        if (auth == null) {
            return null;
        }
        ClusterAuthConfigEntity entity = existing != null ? existing : new ClusterAuthConfigEntity();
        entity.setUsername(auth.getUsername());
        entity.setPassword(auth.getPassword());
        return entity;
    }

    /**
     * 转换 KeystoreConfig 为 SSL 实体
     */
    private ClusterSslConfigEntity toSslEntityForKeystore(
            ClustersProperties.KeystoreConfig config, ClusterSslConfigEntity existing) {
        if (config == null) {
            return null;
        }
        ClusterSslConfigEntity entity = existing != null ? existing : new ClusterSslConfigEntity();
        entity.setKeystoreLocation(config.getKeystoreLocation());
        entity.setKeystorePassword(config.getKeystorePassword());
        return entity;
    }

    /**
     * 转换 TruststoreConfig 为 SSL 实体
     */
    private ClusterSslConfigEntity toSslEntityForTruststore(
            ClustersProperties.TruststoreConfig config, ClusterSslConfigEntity existing) {
        if (config == null) {
            return null;
        }
        ClusterSslConfigEntity entity = existing != null ? existing : new ClusterSslConfigEntity();
        entity.setTruststoreLocation(config.getTruststoreLocation());
        entity.setTruststorePassword(config.getTruststorePassword());
        return entity;
    }

    /**
     * 同步 Kafka Connect 集群配置（1:N 关联）。
     * <p>
     * 通过名称匹配实现增量更新：已有的更新、新增的创建、不再存在的删除。
     * </p>
     */
    private void syncConnectClusters(ClusterConfigEntity entity, List<ClustersProperties.ConnectCluster> newList) {
        Set<ConnectClusterConfigEntity> existingSet = entity.getKafkaConnect();
        if (existingSet == null) {
            existingSet = new HashSet<>();
            entity.setKafkaConnect(existingSet);
        }

        if (newList == null || newList.isEmpty()) {
            existingSet.clear();
            return;
        }

        // 按名称索引已有记录
        Map<String, ConnectClusterConfigEntity> existingMap = existingSet.stream()
                .collect(Collectors.toMap(ConnectClusterConfigEntity::getName, e -> e));

        Set<ConnectClusterConfigEntity> result = new HashSet<>();
        for (ClustersProperties.ConnectCluster cc : newList) {
            ConnectClusterConfigEntity existing = existingMap.remove(cc.getName());
            ConnectClusterConfigEntity entity1 = toConnectClusterEntity(cc, existing);
            entity1.setCluster(entity);
            result.add(entity1);
        }
        existingSet.clear();
        existingSet.addAll(result);
    }

    /**
     * 同步 Serde 配置（1:N 关联）。
     */
    private void syncSerdeConfigs(ClusterConfigEntity entity, List<ClustersProperties.SerdeConfig> newList) {
        Set<SerdeConfigEntity> existingSet = entity.getSerdes();
        if (existingSet == null) {
            existingSet = new HashSet<>();
            entity.setSerdes(existingSet);
        }

        if (newList == null || newList.isEmpty()) {
            existingSet.clear();
            return;
        }

        Map<String, SerdeConfigEntity> existingMap = existingSet.stream()
                .collect(Collectors.toMap(SerdeConfigEntity::getName, e -> e));

        Set<SerdeConfigEntity> result = new HashSet<>();
        for (ClustersProperties.SerdeConfig sc : newList) {
            SerdeConfigEntity existing = existingMap.remove(sc.getName());
            SerdeConfigEntity entity1 = toSerdeConfigEntity(sc, existing);
            entity1.setCluster(entity);
            result.add(entity1);
        }
        existingSet.clear();
        existingSet.addAll(result);
    }

    /**
     * 同步 Masking 规则（1:N 关联）。
     */
    private void syncMaskingRules(ClusterConfigEntity entity, List<ClustersProperties.Masking> newList) {
        Set<MaskingRuleEntity> existingSet = entity.getMaskingRules();
        if (existingSet == null) {
            existingSet = new HashSet<>();
            entity.setMaskingRules(existingSet);
        }

        if (newList == null || newList.isEmpty()) {
            existingSet.clear();
            return;
        }

        // 按 name 字段构建 Map，实现按业务名称匹配（兼容存量数据：name 为 null 的旧记录用 filter 过滤）
        Map<String, MaskingRuleEntity> existingMap = existingSet.stream()
                .filter(e -> e.getName() != null)
                .collect(Collectors.toMap(MaskingRuleEntity::getName, e -> e));
        Set<MaskingRuleEntity> result = new HashSet<>();
        for (ClustersProperties.Masking m : newList) {
            // 按 name 匹配已有记录，匹配不上则新建
            MaskingRuleEntity existing = existingMap.remove(m.getName());
            MaskingRuleEntity entity1 = toMaskingEntity(m, existing);
            entity1.setCluster(entity);
            result.add(entity1);
        }
        existingSet.clear();
        existingSet.addAll(result);
    }
}
