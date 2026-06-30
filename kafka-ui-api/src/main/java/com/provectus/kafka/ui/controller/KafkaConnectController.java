package com.provectus.kafka.ui.controller;

import static com.provectus.kafka.ui.model.ConnectorActionDTO.RESTART;
import static com.provectus.kafka.ui.model.ConnectorActionDTO.RESTART_ALL_TASKS;
import static com.provectus.kafka.ui.model.ConnectorActionDTO.RESTART_FAILED_TASKS;

import com.provectus.kafka.ui.api.KafkaConnectApi;
import com.provectus.kafka.ui.model.ConnectDTO;
import com.provectus.kafka.ui.model.ConnectorActionDTO;
import com.provectus.kafka.ui.model.ConnectorColumnsToSortDTO;
import com.provectus.kafka.ui.model.ConnectorDTO;
import com.provectus.kafka.ui.model.ConnectorPluginConfigValidationResponseDTO;
import com.provectus.kafka.ui.model.ConnectorPluginDTO;
import com.provectus.kafka.ui.model.FullConnectorInfoDTO;
import com.provectus.kafka.ui.model.NewConnectorDTO;
import com.provectus.kafka.ui.model.SortOrderDTO;
import com.provectus.kafka.ui.model.TaskDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.ConnectAction;
import com.provectus.kafka.ui.service.KafkaConnectService;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka Connect 管理控制器
 *
 * 提供 Kafka Connect 集群和连接器的管理操作，包括：
 * 1. 获取 Connect 集群列表
 * 2. 连接器的 CRUD 操作（创建、查看、删除）
 * 3. 连接器状态管理（暂停、恢复、重启）
 * 4. 连接器任务管理（查看任务列表、重启任务）
 * 5. 连接器配置管理（查看、更新配置）
 * 6. 连接器插件管理（查看插件列表、验证插件配置）
 * 7. 获取所有连接器信息（跨 Connect 集群，支持搜索和排序）
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class KafkaConnectController extends AbstractController implements KafkaConnectApi {

  /** 需要重启权限的操作集合 */
  private static final Set<ConnectorActionDTO> RESTART_ACTIONS
      = Set.of(RESTART, RESTART_FAILED_TASKS, RESTART_ALL_TASKS);

  /** 连接器名称操作参数键 */
  private static final String CONNECTOR_NAME = "connectorName";

  /** Kafka Connect 业务服务，处理连接器的核心逻辑 */
  private final KafkaConnectService kafkaConnectService;

  /**
   * 获取所有可访问的 Connect 集群列表
   *
   * 结果会根据当前用户的 RBAC 权限进行过滤。
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return Connect 集群列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<ConnectDTO>>> getConnects(String clusterName,
                                                            ServerWebExchange exchange) {

    Flux<ConnectDTO> availableConnects = kafkaConnectService.getConnects(getCluster(clusterName))
        .filterWhen(dto -> accessControlService.isConnectAccessible(dto, clusterName));

    return Mono.just(ResponseEntity.ok(availableConnects));
  }

  /**
   * 获取指定 Connect 集群的连接器名称列表
   *
   * @param clusterName  集群名称
   * @param connectName  Connect 集群名称
   * @param exchange     服务器交换对象
   * @return 连接器名称列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<String>>> getConnectors(String clusterName, String connectName,
                                                          ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW)
        .operationName("getConnectors")
        .build();

    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(kafkaConnectService.getConnectorNames(getCluster(clusterName), connectName)))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 创建新连接器
   *
   * @param clusterName  集群名称
   * @param connectName  Connect 集群名称
   * @param connector    连接器配置（包含名称、配置等）
   * @param exchange     服务器交换对象
   * @return 创建后的连接器信息
   */
  @Override
  public Mono<ResponseEntity<ConnectorDTO>> createConnector(String clusterName, String connectName,
                                                            @Valid Mono<NewConnectorDTO> connector,
                                                            ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW, ConnectAction.CREATE)
        .operationName("createConnector")
        .build();

    return validateAccess(context).then(
        kafkaConnectService.createConnector(getCluster(clusterName), connectName, connector)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取连接器详情
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param exchange      服务器交换对象
   * @return 连接器详情 DTO
   */
  @Override
  public Mono<ResponseEntity<ConnectorDTO>> getConnector(String clusterName, String connectName,
                                                         String connectorName,
                                                         ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW)
        .connector(connectorName)
        .operationName("getConnector")
        .build();

    return validateAccess(context).then(
        kafkaConnectService.getConnector(getCluster(clusterName), connectName, connectorName)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 删除连接器
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param exchange      服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteConnector(String clusterName, String connectName,
                                                    String connectorName,
                                                    ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW, ConnectAction.EDIT)
        .operationName("deleteConnector")
        .operationParams(Map.of(CONNECTOR_NAME, connectName))
        .build();

    return validateAccess(context).then(
        kafkaConnectService.deleteConnector(getCluster(clusterName), connectName, connectorName)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }


  /**
   * 获取所有 Connect 集群的连接器信息
   *
   * 跨所有 Connect 集群查询连接器，支持搜索和排序。结果会根据用户的 RBAC 权限进行过滤。
   *
   * @param clusterName 集群名称
   * @param search      搜索关键词（按连接器名称模糊匹配）
   * @param orderBy     排序字段（名称、Connect 集群、类型、状态）
   * @param sortOrder   排序方向（ASC/DESC）
   * @param exchange    服务器交换对象
   * @return 连接器信息列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<FullConnectorInfoDTO>>> getAllConnectors(
      String clusterName,
      String search,
      ConnectorColumnsToSortDTO orderBy,
      SortOrderDTO sortOrder,
      ServerWebExchange exchange
  ) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .connectActions(ConnectAction.VIEW, ConnectAction.EDIT)
        .operationName("getAllConnectors")
        .build();

    var comparator = sortOrder == null || sortOrder.equals(SortOrderDTO.ASC)
        ? getConnectorsComparator(orderBy)
        : getConnectorsComparator(orderBy).reversed();

    Flux<FullConnectorInfoDTO> job = kafkaConnectService.getAllConnectors(getCluster(clusterName), search)
        .filterWhen(dto -> accessControlService.isConnectAccessible(dto.getConnect(), clusterName))
        .filterWhen(dto -> accessControlService.isConnectorAccessible(dto.getConnect(), dto.getName(), clusterName))
        .sort(comparator);

    return Mono.just(ResponseEntity.ok(job))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取连接器配置
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param exchange      服务器交换对象
   * @return 连接器配置（键值对形式）
   */
  @Override
  public Mono<ResponseEntity<Map<String, Object>>> getConnectorConfig(String clusterName,
                                                                      String connectName,
                                                                      String connectorName,
                                                                      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW)
        .operationName("getConnectorConfig")
        .build();

    return validateAccess(context).then(
        kafkaConnectService
            .getConnectorConfig(getCluster(clusterName), connectName, connectorName)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 更新连接器配置
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param requestBody   新的配置（键值对形式）
   * @param exchange      服务器交换对象
   * @return 更新后的连接器信息
   */
  @Override
  public Mono<ResponseEntity<ConnectorDTO>> setConnectorConfig(String clusterName, String connectName,
                                                               String connectorName,
                                                               Mono<Map<String, Object>> requestBody,
                                                               ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW, ConnectAction.EDIT)
        .operationName("setConnectorConfig")
        .operationParams(Map.of(CONNECTOR_NAME, connectorName))
        .build();

    return validateAccess(context).then(
            kafkaConnectService
                .setConnectorConfig(getCluster(clusterName), connectName, connectorName, requestBody)
                .map(ResponseEntity::ok))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 更新连接器状态（暂停、恢复、重启等）
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param action        操作类型（PAUSE/RESUME/RESTART/RESTART_FAILED_TASKS/RESTART_ALL_TASKS）
   * @param exchange      服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> updateConnectorState(String clusterName, String connectName,
                                                         String connectorName,
                                                         ConnectorActionDTO action,
                                                         ServerWebExchange exchange) {
    ConnectAction[] connectActions;
    if (RESTART_ACTIONS.contains(action)) {
      connectActions = new ConnectAction[] {ConnectAction.VIEW, ConnectAction.RESTART};
    } else {
      connectActions = new ConnectAction[] {ConnectAction.VIEW, ConnectAction.EDIT};
    }

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(connectActions)
        .operationName("updateConnectorState")
        .operationParams(Map.of(CONNECTOR_NAME, connectorName))
        .build();

    return validateAccess(context).then(
        kafkaConnectService
            .updateConnectorState(getCluster(clusterName), connectName, connectorName, action)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取连接器的任务列表
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param exchange      服务器交换对象
   * @return 任务列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<TaskDTO>>> getConnectorTasks(String clusterName,
                                                               String connectName,
                                                               String connectorName,
                                                               ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW)
        .operationName("getConnectorTasks")
        .operationParams(Map.of(CONNECTOR_NAME, connectorName))
        .build();

    return validateAccess(context).thenReturn(
        ResponseEntity
            .ok(kafkaConnectService
                .getConnectorTasks(getCluster(clusterName), connectName, connectorName))
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 重启连接器的指定任务
   *
   * @param clusterName   集群名称
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param taskId        任务 ID
   * @param exchange      服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> restartConnectorTask(String clusterName, String connectName,
                                                         String connectorName, Integer taskId,
                                                         ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW, ConnectAction.RESTART)
        .operationName("restartConnectorTask")
        .operationParams(Map.of(CONNECTOR_NAME, connectorName))
        .build();

    return validateAccess(context).then(
        kafkaConnectService
            .restartConnectorTask(getCluster(clusterName), connectName, connectorName, taskId)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取 Connect 集群的可用连接器插件列表
   *
   * @param clusterName 集群名称
   * @param connectName Connect 集群名称
   * @param exchange    服务器交换对象
   * @return 连接器插件列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<ConnectorPluginDTO>>> getConnectorPlugins(
      String clusterName, String connectName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .connect(connectName)
        .connectActions(ConnectAction.VIEW)
        .operationName("getConnectorPlugins")
        .build();

    return validateAccess(context).then(
        Mono.just(
            ResponseEntity.ok(
                kafkaConnectService.getConnectorPlugins(getCluster(clusterName), connectName)))
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 验证连接器插件配置
   *
   * @param clusterName 集群名称
   * @param connectName Connect 集群名称
   * @param pluginName  插件名称
   * @param requestBody 待验证的配置（键值对形式）
   * @param exchange    服务器交换对象
   * @return 配置验证结果（包含错误信息等）
   */
  @Override
  public Mono<ResponseEntity<ConnectorPluginConfigValidationResponseDTO>> validateConnectorPluginConfig(
      String clusterName, String connectName, String pluginName, @Valid Mono<Map<String, Object>> requestBody,
      ServerWebExchange exchange) {
    return kafkaConnectService
        .validateConnectorPluginConfig(
            getCluster(clusterName), connectName, pluginName, requestBody)
        .map(ResponseEntity::ok);
  }

  /**
   * 根据排序字段获取连接器比较器
   *
   * @param orderBy 排序字段（CONNECT/TYPE/STATUS/NAME）
   * @return 连接器比较器，默认按名称排序
   */
  private Comparator<FullConnectorInfoDTO> getConnectorsComparator(ConnectorColumnsToSortDTO orderBy) {
    var defaultComparator = Comparator.comparing(FullConnectorInfoDTO::getName);
    if (orderBy == null) {
      return defaultComparator;
    }
    return switch (orderBy) {
      case CONNECT -> Comparator.comparing(FullConnectorInfoDTO::getConnect);
      case TYPE -> Comparator.comparing(FullConnectorInfoDTO::getType);
      case STATUS -> Comparator.comparing(fullConnectorInfoDTO -> fullConnectorInfoDTO.getStatus().getState());
      default -> defaultComparator;
    };
  }
}
