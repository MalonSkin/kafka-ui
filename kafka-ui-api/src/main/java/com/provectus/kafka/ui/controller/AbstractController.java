package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.exception.ClusterNotFoundException;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.service.ClustersStorage;
import com.provectus.kafka.ui.service.audit.AuditService;
import com.provectus.kafka.ui.service.rbac.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

/**
 * 控制器基类
 *
 * 提供所有 REST 控制器共用的基础设施：
 * 1. 集群实例查找（通过集群名称获取 {@link KafkaCluster}）
 * 2. 访问权限验证（基于 RBAC 模型）
 * 3. 操作审计记录（记录用户操作及结果）
 *
 * <p>所有业务控制器应继承此类，并通过 {@code getCluster()} 获取集群实例，
 * 通过 {@code validateAccess()} 验证权限，通过 {@code audit()} 记录审计日志。</p>
 */
public abstract class AbstractController {

  /** 集群存储，管理内存中的 Kafka 集群实例 */
  protected ClustersStorage clustersStorage;

  /** 访问控制服务，基于 RBAC 模型验证用户权限 */
  protected AccessControlService accessControlService;

  /** 审计服务，记录用户操作用于安全审计 */
  protected AuditService auditService;

  /**
   * 根据集群名称获取集群实例
   *
   * @param name 集群名称（对应配置中的 name 字段）
   * @return 集群实例
   * @throws ClusterNotFoundException 如果集群不存在
   */
  protected KafkaCluster getCluster(String name) {
    return clustersStorage.getClusterByName(name)
        .orElseThrow(() -> new ClusterNotFoundException(
            String.format("Cluster with name '%s' not found", name)));
  }

  /**
   * 验证当前用户是否有权执行指定操作
   *
   * @param context 访问上下文，包含集群、资源、操作类型等信息
   * @return 空的 Mono，验证失败时返回错误信号
   */
  protected Mono<Void> validateAccess(AccessContext context) {
    return accessControlService.validateAccess(context);
  }

  /**
   * 记录操作审计日志
   *
   * @param acxt 访问上下文，包含操作详情
   * @param sig  信号，包含操作结果（成功/失败/取消）
   */
  protected void audit(AccessContext acxt, Signal<?> sig) {
    auditService.audit(acxt, sig);
  }

  /**
   * 注入集群存储服务
   *
   * @param clustersStorage 集群存储实例
   */
  @Autowired
  public void setClustersStorage(ClustersStorage clustersStorage) {
    this.clustersStorage = clustersStorage;
  }

  /**
   * 注入访问控制服务
   *
   * @param accessControlService 访问控制服务实例
   */
  @Autowired
  public void setAccessControlService(AccessControlService accessControlService) {
    this.accessControlService = accessControlService;
  }

  /**
   * 注入审计服务
   *
   * @param auditService 审计服务实例
   */
  @Autowired
  public void setAuditService(AuditService auditService) {
    this.auditService = auditService;
  }
}
