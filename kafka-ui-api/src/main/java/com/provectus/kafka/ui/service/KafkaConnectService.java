package com.provectus.kafka.ui.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.provectus.kafka.ui.connect.api.KafkaConnectClientApi;
import com.provectus.kafka.ui.connect.model.ConnectorStatus;
import com.provectus.kafka.ui.connect.model.ConnectorStatusConnector;
import com.provectus.kafka.ui.connect.model.ConnectorTopics;
import com.provectus.kafka.ui.connect.model.TaskStatus;
import com.provectus.kafka.ui.exception.NotFoundException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.mapper.ClusterMapper;
import com.provectus.kafka.ui.mapper.KafkaConnectMapper;
import com.provectus.kafka.ui.model.ConnectDTO;
import com.provectus.kafka.ui.model.ConnectorActionDTO;
import com.provectus.kafka.ui.model.ConnectorDTO;
import com.provectus.kafka.ui.model.ConnectorPluginConfigValidationResponseDTO;
import com.provectus.kafka.ui.model.ConnectorPluginDTO;
import com.provectus.kafka.ui.model.ConnectorStateDTO;
import com.provectus.kafka.ui.model.ConnectorTaskStatusDTO;
import com.provectus.kafka.ui.model.FullConnectorInfoDTO;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.NewConnectorDTO;
import com.provectus.kafka.ui.model.TaskDTO;
import com.provectus.kafka.ui.model.connect.InternalConnectInfo;
import com.provectus.kafka.ui.util.ReactiveFailover;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka Connect 管理服务。
 *
 * <p>提供 Kafka Connect 集群和连接器的完整管理功能，主要包括：
 * <ul>
 *   <li>获取 Connect 集群列表及所有连接器信息</li>
 *   <li>连接器的创建、查询、更新配置和删除</li>
 *   <li>连接器状态管理（重启、暂停、恢复、重启任务）</li>
 *   <li>连接器任务的查询与重启</li>
 *   <li>连接器插件的查询和配置验证</li>
 *   <li>连接器关联主题的查询</li>
 * </ul>
 *
 * <p>通过 {@link ReactiveFailover} 封装的 {@link KafkaConnectClientApi} 与 Connect REST API 交互，
 * 并通过 {@link KafkaConfigSanitizer} 对敏感配置进行脱敏处理。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConnectService {
  private final ClusterMapper clusterMapper;
  private final KafkaConnectMapper kafkaConnectMapper;
  private final ObjectMapper objectMapper;
  private final KafkaConfigSanitizer kafkaConfigSanitizer;

  /**
   * 获取集群配置的所有 Kafka Connect 集群列表。
   *
   * @param cluster 目标 Kafka 集群
   * @return {@link ConnectDTO} 的响应式流
   */
  public Flux<ConnectDTO> getConnects(KafkaCluster cluster) {
    return Flux.fromIterable(
        Optional.ofNullable(cluster.getOriginalProperties().getKafkaConnect())
            .map(lst -> lst.stream().map(clusterMapper::toKafkaConnect).toList())
            .orElse(List.of())
    );
  }

  /**
   * 获取所有 Connect 集群中的连接器完整信息。
   *
   * <p>汇总每个连接器的配置、任务状态和关联主题信息，
   * 并支持按名称、Connect 集群名、状态和类型进行搜索过滤。
   *
   * @param cluster 目标 Kafka 集群
   * @param search  搜索关键字（可为 null，不区分大小写）
   * @return {@link FullConnectorInfoDTO} 的响应式流
   */
  public Flux<FullConnectorInfoDTO> getAllConnectors(final KafkaCluster cluster,
                                                     @Nullable final String search) {
    return getConnects(cluster)
        .flatMap(connect ->
            getConnectorNamesWithErrorsSuppress(cluster, connect.getName())
                .flatMap(connectorName ->
                    Mono.zip(
                        getConnector(cluster, connect.getName(), connectorName),
                        getConnectorConfig(cluster, connect.getName(), connectorName),
                        getConnectorTasks(cluster, connect.getName(), connectorName).collectList(),
                        getConnectorTopics(cluster, connect.getName(), connectorName)
                    ).map(tuple ->
                        InternalConnectInfo.builder()
                            .connector(tuple.getT1())
                            .config(tuple.getT2())
                            .tasks(tuple.getT3())
                            .topics(tuple.getT4().getTopics())
                            .build())))
        .map(kafkaConnectMapper::fullConnectorInfo)
        .filter(matchesSearchTerm(search));
  }

  private Predicate<FullConnectorInfoDTO> matchesSearchTerm(@Nullable final String search) {
    if (search == null) {
      return c -> true;
    }
    return connector -> getStringsForSearch(connector)
        .anyMatch(string -> StringUtils.containsIgnoreCase(string, search));
  }

  private Stream<String> getStringsForSearch(FullConnectorInfoDTO fullConnectorInfo) {
    return Stream.of(
        fullConnectorInfo.getName(),
        fullConnectorInfo.getConnect(),
        fullConnectorInfo.getStatus().getState().getValue(),
        fullConnectorInfo.getType().getValue());
  }

  /**
   * 获取连接器关联的主题列表。
   *
   * <p>对于旧版本的 Connect API（不支持此端点），返回空列表以保持向后兼容。
   *
   * @param cluster           目标 Kafka 集群
   * @param connectClusterName Connect 集群名称
   * @param connectorName     连接器名称
   * @return 包含关联主题信息的 {@link ConnectorTopics} 的 Mono
   */
  public Mono<ConnectorTopics> getConnectorTopics(KafkaCluster cluster, String connectClusterName,
                                                  String connectorName) {
    return api(cluster, connectClusterName)
        .mono(c -> c.getConnectorTopics(connectorName))
        .map(result -> result.get(connectorName))
        // old Connect API versions don't have this endpoint, setting empty list for
        // backward-compatibility
        .onErrorResume(Exception.class, e -> Mono.just(new ConnectorTopics().topics(List.of())));
  }

  /**
   * 获取指定 Connect 集群中的所有连接器名称。
   *
   * @param cluster     目标 Kafka 集群
   * @param connectName Connect 集群名称
   * @return 连接器名称的响应式流
   */
  public Flux<String> getConnectorNames(KafkaCluster cluster, String connectName) {
    return api(cluster, connectName)
        .flux(client -> client.getConnectors(null))
        // for some reason `getConnectors` method returns the response as a single string
        .collectList().map(e -> e.get(0))
        .map(this::parseConnectorsNamesStringToList)
        .flatMapMany(Flux::fromIterable);
  }

  /**
   * 获取连接器名称列表，通信错误时静默返回空流。
   *
   * @param cluster     目标 Kafka 集群
   * @param connectName Connect 集群名称
   * @return 连接器名称的响应式流，出错时为空流
   */
  public Flux<String> getConnectorNamesWithErrorsSuppress(KafkaCluster cluster, String connectName) {
    return getConnectorNames(cluster, connectName).onErrorComplete();
  }

  @SneakyThrows
  private List<String> parseConnectorsNamesStringToList(String json) {
    return objectMapper.readValue(json, new TypeReference<>() {
    });
  }

  /**
   * 创建新的连接器。
   *
   * <p>创建前检查同名连接器是否已存在，若存在则抛出 {@link ValidationException}。
   * 创建成功后返回完整的连接器信息。
   *
   * @param cluster     目标 Kafka 集群
   * @param connectName Connect 集群名称
   * @param connector   包含连接器创建参数的 Mono
   * @return 包含新创建的 {@link ConnectorDTO} 的 Mono
   * @throws ValidationException 若同名连接器已存在
   */
  public Mono<ConnectorDTO> createConnector(KafkaCluster cluster, String connectName,
                                            Mono<NewConnectorDTO> connector) {
    return api(cluster, connectName)
        .mono(client ->
            connector
                .flatMap(c -> connectorExists(cluster, connectName, c.getName())
                    .map(exists -> {
                      if (Boolean.TRUE.equals(exists)) {
                        throw new ValidationException(
                            String.format("Connector with name %s already exists", c.getName()));
                      }
                      return c;
                    }))
                .map(kafkaConnectMapper::toClient)
                .flatMap(client::createConnector)
                .flatMap(c -> getConnector(cluster, connectName, c.getName()))
        );
  }

  private Mono<Boolean> connectorExists(KafkaCluster cluster, String connectName,
                                        String connectorName) {
    return getConnectorNames(cluster, connectName)
        .any(name -> name.equals(connectorName));
  }

  /**
   * 获取指定连接器的详细信息。
   *
   * <p>包括连接器配置（敏感信息已脱敏）、状态、类型和任务列表。
   * 若存在失败的任务，连接器状态会被标记为 TASK_FAILED。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @return 包含 {@link ConnectorDTO} 的 Mono
   */
  public Mono<ConnectorDTO> getConnector(KafkaCluster cluster, String connectName,
                                         String connectorName) {
    return api(cluster, connectName)
        .mono(client -> client.getConnector(connectorName)
            .map(kafkaConnectMapper::fromClient)
            .flatMap(connector ->
                client.getConnectorStatus(connector.getName())
                    // status request can return 404 if tasks not assigned yet
                    .onErrorResume(WebClientResponseException.NotFound.class,
                        e -> emptyStatus(connectorName))
                    .map(connectorStatus -> {
                      var status = connectorStatus.getConnector();
                      var sanitizedConfig = kafkaConfigSanitizer.sanitizeConnectorConfig(connector.getConfig());
                      ConnectorDTO result = new ConnectorDTO()
                          .connect(connectName)
                          .status(kafkaConnectMapper.fromClient(status))
                          .type(connector.getType())
                          .tasks(connector.getTasks())
                          .name(connector.getName())
                          .config(sanitizedConfig);

                      if (connectorStatus.getTasks() != null) {
                        boolean isAnyTaskFailed = connectorStatus.getTasks().stream()
                            .map(TaskStatus::getState)
                            .anyMatch(TaskStatus.StateEnum.FAILED::equals);

                        if (isAnyTaskFailed) {
                          result.getStatus().state(ConnectorStateDTO.TASK_FAILED);
                        }
                      }
                      return result;
                    })
            )
        );
  }

  private Mono<ConnectorStatus> emptyStatus(String connectorName) {
    return Mono.just(new ConnectorStatus()
        .name(connectorName)
        .tasks(List.of())
        .connector(new ConnectorStatusConnector()
            .state(ConnectorStatusConnector.StateEnum.UNASSIGNED)));
  }

  /**
   * 获取连接器的配置信息（敏感信息已脱敏）。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @return 包含配置 Map 的 Mono
   */
  public Mono<Map<String, Object>> getConnectorConfig(KafkaCluster cluster, String connectName,
                                                      String connectorName) {
    return api(cluster, connectName)
        .mono(c -> c.getConnectorConfig(connectorName))
        .map(kafkaConfigSanitizer::sanitizeConnectorConfig);
  }

  /**
   * 更新连接器的配置。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param requestBody   包含新配置的 Mono
   * @return 包含更新后的 {@link ConnectorDTO} 的 Mono
   */
  public Mono<ConnectorDTO> setConnectorConfig(KafkaCluster cluster, String connectName,
                                               String connectorName, Mono<Map<String, Object>> requestBody) {
    return api(cluster, connectName)
        .mono(c ->
            requestBody
                .flatMap(body -> c.setConnectorConfig(connectorName, body))
                .map(kafkaConnectMapper::fromClient));
  }

  /**
   * 删除指定的连接器。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @return 删除完成的 Mono
   */
  public Mono<Void> deleteConnector(
      KafkaCluster cluster, String connectName, String connectorName) {
    return api(cluster, connectName)
        .mono(c -> c.deleteConnector(connectorName));
  }

  /**
   * 更新连接器的状态（执行连接器操作）。
   *
   * <p>支持的操作类型：
   * <ul>
   *   <li>RESTART — 重启连接器</li>
   *   <li>RESTART_ALL_TASKS — 重启所有任务</li>
   *   <li>RESTART_FAILED_TASKS — 仅重启失败的任务</li>
   *   <li>PAUSE — 暂停连接器</li>
   *   <li>RESUME — 恢复连接器</li>
   * </ul>
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param action        操作类型
   * @return 操作完成的 Mono
   */
  public Mono<Void> updateConnectorState(KafkaCluster cluster, String connectName,
                                         String connectorName, ConnectorActionDTO action) {
    return api(cluster, connectName)
        .mono(client -> {
          switch (action) {
            case RESTART:
              return client.restartConnector(connectorName, false, false);
            case RESTART_ALL_TASKS:
              return restartTasks(cluster, connectName, connectorName, task -> true);
            case RESTART_FAILED_TASKS:
              return restartTasks(cluster, connectName, connectorName,
                  t -> t.getStatus().getState() == ConnectorTaskStatusDTO.FAILED);
            case PAUSE:
              return client.pauseConnector(connectorName);
            case RESUME:
              return client.resumeConnector(connectorName);
            default:
              throw new IllegalStateException("Unexpected value: " + action);
          }
        });
  }

  private Mono<Void> restartTasks(KafkaCluster cluster, String connectName,
                                  String connectorName, Predicate<TaskDTO> taskFilter) {
    return getConnectorTasks(cluster, connectName, connectorName)
        .filter(taskFilter)
        .flatMap(t ->
            restartConnectorTask(cluster, connectName, connectorName, t.getId().getTask()))
        .then();
  }

  /**
   * 获取连接器的所有任务及其状态。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @return {@link TaskDTO} 的响应式流
   */
  public Flux<TaskDTO> getConnectorTasks(KafkaCluster cluster, String connectName, String connectorName) {
    return api(cluster, connectName)
        .flux(client ->
            client.getConnectorTasks(connectorName)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Flux.empty())
                .map(kafkaConnectMapper::fromClient)
                .flatMap(task ->
                    client
                        .getConnectorTaskStatus(connectorName, task.getId().getTask())
                        .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                        .map(kafkaConnectMapper::fromClient)
                        .map(task::status)
                ));
  }

  /**
   * 重启连接器的指定任务。
   *
   * @param cluster       目标 Kafka 集群
   * @param connectName   Connect 集群名称
   * @param connectorName 连接器名称
   * @param taskId        任务 ID
   * @return 重启完成的 Mono
   */
  public Mono<Void> restartConnectorTask(KafkaCluster cluster, String connectName,
                                         String connectorName, Integer taskId) {
    return api(cluster, connectName)
        .mono(client -> client.restartConnectorTask(connectorName, taskId));
  }

  /**
   * 获取指定 Connect 集群中安装的所有连接器插件。
   *
   * @param cluster     目标 Kafka 集群
   * @param connectName Connect 集群名称
   * @return {@link ConnectorPluginDTO} 的响应式流
   */
  public Flux<ConnectorPluginDTO> getConnectorPlugins(KafkaCluster cluster,
                                                      String connectName) {
    return api(cluster, connectName)
        .flux(client -> client.getConnectorPlugins().map(kafkaConnectMapper::fromClient));
  }

  /**
   * 验证连接器插件的配置参数。
   *
   * @param cluster     目标 Kafka 集群
   * @param connectName Connect 集群名称
   * @param pluginName  插件名称
   * @param requestBody 包含待验证配置的 Mono
   * @return 包含验证结果的 {@link ConnectorPluginConfigValidationResponseDTO} 的 Mono
   */
  public Mono<ConnectorPluginConfigValidationResponseDTO> validateConnectorPluginConfig(
      KafkaCluster cluster, String connectName, String pluginName, Mono<Map<String, Object>> requestBody) {
    return api(cluster, connectName)
        .mono(client ->
            requestBody
                .flatMap(body ->
                    client.validateConnectorPluginConfig(pluginName, body))
                .map(kafkaConnectMapper::fromClient)
        );
  }

  private ReactiveFailover<KafkaConnectClientApi> api(KafkaCluster cluster, String connectName) {
    var client = cluster.getConnectsClients().get(connectName);
    if (client == null) {
      throw new NotFoundException(
          "Connect %s not found for cluster %s".formatted(connectName, cluster.getName()));
    }
    return client;
  }
}
