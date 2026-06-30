package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.model.KafkaCluster;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kafka集群存储组件。
 * <p>
 * 负责管理和存储所有已配置的 Kafka 集群实例。支持两种数据来源：
 * <ul>
 *   <li>数据库存储（优先）：通过 ClusterConfigPersistenceService 从数据库加载</li>
 *   <li>配置文件（兼容）：从 application.yml 中的 kafka.clusters 配置加载</li>
 * </ul>
 * </p>
 * <p>
 * 启动时优先从数据库加载集群配置，对于数据库中不存在的集群再从配置文件加载。
 * 运行时的增删改操作同时更新内存缓存和数据库。
 * </p>
 * <p>
 * 使用 ConcurrentHashMap 和 ReadWriteLock 保证线程安全。
 * </p>
 *
 * @author kafka-ui
 */
@Slf4j
@Component
public class ClustersStorage {

  /**
   * 存储所有 Kafka 集群实例的线程安全映射。
   * Key: 集群名称 (来自配置文件的 kafka.clusters[].name)
   * Value: KafkaCluster 实例，包含连接客户端、Schema Registry 等
   */
  private final ConcurrentHashMap<String, KafkaCluster> kafkaClusters = new ConcurrentHashMap<>();

  /**
   * 读写锁，用于保证集群操作的线程安全。
   * 读操作（get）可以并发执行，
   * 写操作（add/remove）需要独占锁。
   */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /** 集群工厂，用于创建 KafkaCluster 实例 */
  private final KafkaClusterFactory factory;

  /** 全局集群配置属性 */
  private final ClustersProperties properties;

  /** 集群配置持久化服务，用于数据库 CRUD 操作 */
  private final ClusterConfigPersistenceService persistenceService;

  /** 编程式事务模板，用于在写锁内精确控制事务提交边界 */
  private final TransactionTemplate transactionTemplate;

  /**
   * 构造函数 - 从数据库和配置文件中初始化所有集群。
   * <p>
   * 加载优先级：数据库 > 配置文件。
   * 对于同名集群，数据库中的配置优先。
   * </p>
   *
   * @param properties         集群配置属性，包含所有 kafka.clusters 配置
   * @param factory            集群工厂，用于创建 KafkaCluster 实例
   * @param persistenceService 集群配置持久化服务
   * @param transactionTemplate 编程式事务模板
   */
  public ClustersStorage(ClustersProperties properties, KafkaClusterFactory factory,
                         ClusterConfigPersistenceService persistenceService,
                         TransactionTemplate transactionTemplate) {
    this.factory = factory;
    this.properties = properties;
    this.persistenceService = persistenceService;
    this.transactionTemplate = transactionTemplate;

    // 第一步：从数据库加载集群配置（优先）
    loadClustersFromDatabase();

    // 第二步：从配置文件加载（仅加载数据库中不存在的集群）
    loadClustersFromProperties();
  }

  /**
   * 从数据库加载集群配置并创建 KafkaCluster 实例。
   */
  private void loadClustersFromDatabase() {
    try {
      List<ClustersProperties.Cluster> dbClusters = persistenceService.loadAllClusters();
      for (ClustersProperties.Cluster clusterConfig : dbClusters) {
        try {
          KafkaCluster cluster = factory.create(properties, clusterConfig);
          kafkaClusters.put(clusterConfig.getName(), cluster);
          log.info("从数据库加载 Kafka 集群: {}", clusterConfig.getName());
        } catch (Exception e) {
          log.error("从数据库加载 Kafka 集群失败: {}", clusterConfig.getName(), e);
        }
      }
    } catch (Exception e) {
      log.warn("从数据库加载集群配置失败，将仅使用配置文件", e);
    }
  }

  /**
   * 从配置文件加载集群配置（仅加载数据库中不存在的集群）。
   */
  private void loadClustersFromProperties() {
    properties.getClusters().forEach(c -> {
      if (!kafkaClusters.containsKey(c.getName())) {
        try {
          KafkaCluster cluster = factory.create(properties, c);
          kafkaClusters.put(c.getName(), cluster);
          log.info("从配置文件加载 Kafka 集群: {}", c.getName());
          // 同步到数据库（首次启动时将配置文件中的集群持久化）
          try {
            persistenceService.saveCluster(c);
            log.info("配置文件中的集群已同步到数据库: {}", c.getName());
          } catch (Exception ex) {
            log.warn("同步集群配置到数据库失败: {}", c.getName(), ex);
          }
        } catch (Exception e) {
          log.error("从配置文件加载 Kafka 集群失败: {}", c.getName(), e);
        }
      }
    });
  }

  /**
   * 获取所有已配置的 Kafka 集群集合。
   *
   * @return 所有 KafkaCluster 实例的集合
   */
  public Collection<KafkaCluster> getKafkaClusters() {
    lock.readLock().lock();
    try {
      return kafkaClusters.values();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 根据集群名称查找对应的 KafkaCluster 实例。
   *
   * @param clusterName 集群名称
   * @return 包装在 Optional 中的 KafkaCluster 实例，如果不存在则返回 empty
   */
  public Optional<KafkaCluster> getClusterByName(String clusterName) {
    lock.readLock().lock();
    try {
      return Optional.ofNullable(kafkaClusters.get(clusterName));
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 动态添加新的 Kafka 集群（同时持久化到数据库）。
   * <p>
   * 该方法支持在运行时添加新的 Kafka 集群，无需重启服务。
   * 添加过程包括：
   * 1. 检查集群名称是否已存在
   * 2. 使用 KafkaClusterFactory 创建集群实例（验证配置有效性）
   * 3. 持久化配置到数据库
   * 4. 将集群添加到内存存储
   * </p>
   *
   * @param clusterProperties 集群配置属性
   * @return 创建的 KafkaCluster 实例
   * @throws IllegalArgumentException 如果集群名称已存在
   * @throws RuntimeException 如果集群创建失败
   */
  public KafkaCluster addCluster(ClustersProperties.Cluster clusterProperties) {
    lock.writeLock().lock();
    try {
      String clusterName = clusterProperties.getName();
      if (kafkaClusters.containsKey(clusterName)) {
        throw new IllegalArgumentException("Cluster already exists: " + clusterName);
      }

      // 验证集群配置
      properties.validateAndSetDefaults();

      // 先创建集群实例（验证配置有效性）
      // 如果 factory.create() 失败，不会写入数据库，避免幽灵配置
      KafkaCluster cluster = factory.create(properties, clusterProperties);

      // 实例创建成功后再持久化到数据库
      transactionTemplate.executeWithoutResult(status -> {
        persistenceService.saveCluster(clusterProperties);
      });
      log.info("集群配置已持久化到数据库: {}", clusterName);

      // 将集群添加到内存存储
      kafkaClusters.put(clusterName, cluster);

      log.info("成功添加 Kafka 集群: {}", clusterName);
      return cluster;
    } catch (Exception e) {
      log.error("添加 Kafka 集群失败: {}", clusterProperties.getName(), e);
      throw e;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 动态删除 Kafka 集群（同时从数据库删除）。
   * <p>
   * 该方法支持在运行时删除 Kafka 集群，无需重启服务。
   * 删除过程包括：
   * 1. 检查集群是否存在
   * 2. 从内存存储中移除集群
   * 3. 从数据库中删除配置
   * </p>
   *
   * @param clusterName 要删除的集群名称
   * @return 被删除的 KafkaCluster 实例，如果不存在则返回 empty
   */
  public Optional<KafkaCluster> removeCluster(String clusterName) {
    lock.writeLock().lock();
    try {
      KafkaCluster removed = kafkaClusters.remove(clusterName);
      if (removed != null) {
        // 在写锁内提交事务，确保事务边界与锁边界一致
        transactionTemplate.executeWithoutResult(status -> {
          persistenceService.deleteCluster(clusterName);
        });
        log.info("成功删除 Kafka 集群（内存+数据库）: {}", clusterName);
      } else {
        log.warn("尝试删除不存在的集群: {}", clusterName);
      }
      return Optional.ofNullable(removed);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 替换集群配置（同时更新数据库）。
   * <p>
   * 先删除旧集群，再用新配置创建。数据库同步更新。
   * </p>
   *
   * @param newGlobalProperties 新的全局集群配置
   * @param clusterProperties   新的单个集群配置
   * @return 新创建的 KafkaCluster 实例
   */
  public KafkaCluster replaceCluster(ClustersProperties newGlobalProperties,
                                      ClustersProperties.Cluster clusterProperties) {
    lock.writeLock().lock();
    try {
      String clusterName = clusterProperties.getName();

      // 先用新配置创建集群实例（验证配置有效性）
      // 此步骤不涉及内存或数据库修改，失败时直接抛出异常，内存和数据库状态均不受影响
      newGlobalProperties.validateAndSetDefaults();
      KafkaCluster newCluster = factory.create(newGlobalProperties, clusterProperties);

      // 将 deleteCluster 和 saveCluster 放入同一个事务中，保证原子性：
      // 两者要么同时成功，要么同时回滚，避免出现旧记录残留或新记录丢失的中间状态
      transactionTemplate.executeWithoutResult(status -> {
        persistenceService.deleteCluster(clusterName);
        persistenceService.saveCluster(clusterProperties);
      });
      log.info("集群配置已原子替换（删除旧配置+保存新配置）: {}", clusterName);

      // 事务提交成功后，才从内存中移除旧集群并放入新集群
      // 确保内存状态与数据库状态始终一致：事务失败时内存不变，事务成功时内存同步更新
      KafkaCluster oldCluster = kafkaClusters.remove(clusterName);
      if (oldCluster != null) {
        log.info("已从内存移除旧集群: {}", clusterName);
      }
      kafkaClusters.put(clusterName, newCluster);

      log.info("成功替换 Kafka 集群: {}", clusterName);
      return newCluster;
    } catch (Exception e) {
      log.error("替换 Kafka 集群失败: {}", clusterProperties.getName(), e);
      throw e;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 检查集群是否存在。
   *
   * @param clusterName 集群名称
   * @return true 如果集群存在
   */
  public boolean hasCluster(String clusterName) {
    lock.readLock().lock();
    try {
      return kafkaClusters.containsKey(clusterName);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 获取集群数量。
   *
   * @return 当前存储的集群数量
   */
  public int getClusterCount() {
    lock.readLock().lock();
    try {
      return kafkaClusters.size();
    } finally {
      lock.readLock().unlock();
    }
  }
}
