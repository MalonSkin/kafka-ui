package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.ClustersApi;
import com.provectus.kafka.ui.model.ClusterDTO;
import com.provectus.kafka.ui.model.ClusterMetricsDTO;
import com.provectus.kafka.ui.model.ClusterStatsDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.service.ClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka 集群管理控制器
 *
 * 提供 Kafka 集群的信息查询和管理操作，包括：
 * 1. 获取所有可访问的集群列表（基于 RBAC 权限过滤）
 * 2. 获取集群监控指标（Broker 数量、Topic 数量、分区数等）
 * 3. 获取集群统计信息（消息总数、数据大小等）
 * 4. 触发集群信息更新（刷新集群元数据）
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClustersController extends AbstractController implements ClustersApi {

  /** 集群业务服务，处理集群信息查询和管理 */
  private final ClusterService clusterService;

  /**
   * 获取所有可访问的集群列表
   *
   * 结果会根据当前用户的 RBAC 权限进行过滤，只返回用户有权限访问的集群。
   *
   * @param exchange 服务器交换对象
   * @return 集群列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<ClusterDTO>>> getClusters(ServerWebExchange exchange) {
    Flux<ClusterDTO> job = Flux.fromIterable(clusterService.getClusters())
        .filterWhen(accessControlService::isClusterAccessible);

    return Mono.just(ResponseEntity.ok(job));
  }

  /**
   * 获取集群监控指标
   *
   * 包括 Broker 数量、Topic 数量、分区数、在线/离线分区数等集群级别指标。
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return 集群监控指标 DTO，集群不可用时返回 404
   */
  @Override
  public Mono<ResponseEntity<ClusterMetricsDTO>> getClusterMetrics(String clusterName,
                                                                   ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getClusterMetrics")
        .build();

    return validateAccess(context)
        .then(
            clusterService.getClusterMetrics(getCluster(clusterName))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build())
        )
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取集群统计信息
   *
   * 包括消息总数、数据大小等统计数据。
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return 集群统计信息 DTO，集群不可用时返回 404
   */
  @Override
  public Mono<ResponseEntity<ClusterStatsDTO>> getClusterStats(String clusterName,
                                                               ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getClusterStats")
        .build();

    return validateAccess(context)
        .then(
            clusterService.getClusterStats(getCluster(clusterName))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build())
        )
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 触发集群信息更新
   *
   * 强制刷新集群元数据（Broker 列表、Topic 列表等），通常在集群状态发生变化后调用。
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return 更新后的集群信息 DTO
   */
  @Override
  public Mono<ResponseEntity<ClusterDTO>> updateClusterInfo(String clusterName,
                                                            ServerWebExchange exchange) {

    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("updateClusterInfo")
        .build();

    return validateAccess(context)
        .then(clusterService.updateCluster(getCluster(clusterName)).map(ResponseEntity::ok))
        .doOnEach(sig -> audit(context, sig));
  }
}
