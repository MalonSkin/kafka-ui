package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.BrokersApi;
import com.provectus.kafka.ui.mapper.ClusterMapper;
import com.provectus.kafka.ui.model.BrokerConfigDTO;
import com.provectus.kafka.ui.model.BrokerConfigItemDTO;
import com.provectus.kafka.ui.model.BrokerDTO;
import com.provectus.kafka.ui.model.BrokerLogdirUpdateDTO;
import com.provectus.kafka.ui.model.BrokerMetricsDTO;
import com.provectus.kafka.ui.model.BrokersLogdirsDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.ClusterConfigAction;
import com.provectus.kafka.ui.service.BrokerService;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka Broker 管理控制器
 *
 * 提供 Kafka Broker 的管理操作，包括：
 * 1. 获取 Broker 列表及基本信息
 * 2. 获取单个 Broker 的监控指标（CPU、内存、网络等）
 * 3. 获取 Broker 的 Log 目录信息（支持按 Broker ID 过滤）
 * 4. 获取 Broker 配置项
 * 5. 修改 Broker 配置项（动态配置）
 * 6. 更新 Topic 分区在 Broker 间的 Log 目录分配
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BrokersController extends AbstractController implements BrokersApi {

  /** Broker ID 参数名常量，用于审计日志记录 */
  private static final String BROKER_ID = "brokerId";

  /** Broker 业务服务，处理 Broker 相关的核心逻辑 */
  private final BrokerService brokerService;

  /** 集群模型映射器，将内部模型转换为 DTO */
  private final ClusterMapper clusterMapper;

  /**
   * 获取集群中所有 Broker 列表
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return Broker 列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<BrokerDTO>>> getBrokers(String clusterName,
                                                          ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getBrokers")
        .build();

    var job = brokerService.getBrokers(getCluster(clusterName)).map(clusterMapper::toBrokerDto);
    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(job))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取指定 Broker 的监控指标
   *
   * @param clusterName 集群名称
   * @param id          Broker ID
   * @param exchange    服务器交换对象
   * @return Broker 监控指标 DTO（CPU、内存、网络使用率等），Broker 不存在时返回 404
   */
  @Override
  public Mono<ResponseEntity<BrokerMetricsDTO>> getBrokersMetrics(String clusterName, Integer id,
                                                                  ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getBrokersMetrics")
        .operationParams(Map.of("id", id))
        .build();

    return validateAccess(context)
        .then(
            brokerService.getBrokerMetrics(getCluster(clusterName), id)
                .map(clusterMapper::toBrokerMetrics)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build())
        )
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取 Broker 的 Log 目录信息
   *
   * @param clusterName 集群名称
   * @param brokers     要查询的 Broker ID 列表（为空则查询所有 Broker）
   * @param exchange    服务器交换对象
   * @return Broker Log 目录信息列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<BrokersLogdirsDTO>>> getAllBrokersLogdirs(String clusterName,
                                                                            @Nullable List<Integer> brokers,
                                                                            ServerWebExchange exchange) {

    List<Integer> brokerIds = brokers == null ? List.of() : brokers;

    var context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getAllBrokersLogdirs")
        .operationParams(Map.of("brokerIds", brokerIds))
        .build();

    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(
            brokerService.getAllBrokersLogdirs(getCluster(clusterName), brokerIds)))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取指定 Broker 的配置项列表
   *
   * @param clusterName 集群名称
   * @param id          Broker ID
   * @param exchange    服务器交换对象
   * @return Broker 配置项列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<BrokerConfigDTO>>> getBrokerConfig(String clusterName,
                                                                     Integer id,
                                                                     ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .clusterConfigActions(ClusterConfigAction.VIEW)
        .operationName("getBrokerConfig")
        .operationParams(Map.of(BROKER_ID, id))
        .build();

    return validateAccess(context).thenReturn(
        ResponseEntity.ok(
            brokerService.getBrokerConfig(getCluster(clusterName), id)
                .map(clusterMapper::toBrokerConfig))
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 更新 Topic 分区在 Broker 间的 Log 目录分配
   *
   * 用于将特定 Topic 分区的日志从一个目录迁移到另一个目录，
   * 通常在磁盘扩容或负载均衡时使用。
   *
   * @param clusterName  集群名称
   * @param id           Broker ID
   * @param brokerLogdir Log 目录更新参数（包含 Topic 分区和目标目录映射）
   * @param exchange     服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> updateBrokerTopicPartitionLogDir(String clusterName,
                                                                     Integer id,
                                                                     Mono<BrokerLogdirUpdateDTO> brokerLogdir,
                                                                     ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .clusterConfigActions(ClusterConfigAction.VIEW, ClusterConfigAction.EDIT)
        .operationName("updateBrokerTopicPartitionLogDir")
        .operationParams(Map.of(BROKER_ID, id))
        .build();

    return validateAccess(context).then(
        brokerLogdir
            .flatMap(bld -> brokerService.updateBrokerLogDir(getCluster(clusterName), id, bld))
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 修改指定 Broker 的配置项
   *
   * 仅支持动态配置（Kafka 运行时可修改的配置项）。
   *
   * @param clusterName  集群名称
   * @param id           Broker ID
   * @param name         配置项名称
   * @param brokerConfig 配置项新值
   * @param exchange     服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> updateBrokerConfigByName(String clusterName,
                                                             Integer id,
                                                             String name,
                                                             Mono<BrokerConfigItemDTO> brokerConfig,
                                                             ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .clusterConfigActions(ClusterConfigAction.VIEW, ClusterConfigAction.EDIT)
        .operationName("updateBrokerConfigByName")
        .operationParams(Map.of(BROKER_ID, id))
        .build();

    return validateAccess(context).then(
        brokerConfig
            .flatMap(bci -> brokerService.updateBrokerConfigByName(
                getCluster(clusterName), id, name, bci.getValue()))
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }
}
