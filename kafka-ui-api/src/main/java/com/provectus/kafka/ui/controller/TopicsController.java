package com.provectus.kafka.ui.controller;

import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.CREATE;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.DELETE;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.EDIT;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.MESSAGES_READ;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.VIEW;
import static java.util.stream.Collectors.toList;

import com.provectus.kafka.ui.api.TopicsApi;
import com.provectus.kafka.ui.mapper.ClusterMapper;
import com.provectus.kafka.ui.model.InternalTopic;
import com.provectus.kafka.ui.model.InternalTopicConfig;
import com.provectus.kafka.ui.model.PartitionsIncreaseDTO;
import com.provectus.kafka.ui.model.PartitionsIncreaseResponseDTO;
import com.provectus.kafka.ui.model.ReplicationFactorChangeDTO;
import com.provectus.kafka.ui.model.ReplicationFactorChangeResponseDTO;
import com.provectus.kafka.ui.model.SortOrderDTO;
import com.provectus.kafka.ui.model.TopicAnalysisDTO;
import com.provectus.kafka.ui.model.TopicColumnsToSortDTO;
import com.provectus.kafka.ui.model.TopicConfigDTO;
import com.provectus.kafka.ui.model.TopicCreationDTO;
import com.provectus.kafka.ui.model.TopicDTO;
import com.provectus.kafka.ui.model.TopicDetailsDTO;
import com.provectus.kafka.ui.model.TopicProducerStateDTO;
import com.provectus.kafka.ui.model.TopicUpdateDTO;
import com.provectus.kafka.ui.model.TopicsResponseDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.service.TopicsService;
import com.provectus.kafka.ui.service.analyze.TopicAnalysisService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Topic 管理控制器
 *
 * 提供 Kafka Topic 的完整 CRUD 操作，包括：
 * 1. Topic 的创建、删除、更新、克隆、重建
 * 2. Topic 配置查看
 * 3. Topic 详情查看（含分区、副本等信息）
 * 4. Topic 分页列表查询（支持搜索、排序、过滤内部 Topic）
 * 5. 分区数量增加、副本因子变更
 * 6. Topic 分析功能（异步分析消息内容）
 * 7. Topic 生产者状态查看
 *
 * <p>所有操作需要相应的 RBAC 权限，通过 {@code AccessContext} 进行权限校验。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TopicsController extends AbstractController implements TopicsApi {

  /** 默认分页大小 */
  private static final Integer DEFAULT_PAGE_SIZE = 25;

  /** Topic 业务服务，处理 Topic 的核心业务逻辑 */
  private final TopicsService topicsService;

  /** Topic 分析服务，提供消息内容的异步分析功能 */
  private final TopicAnalysisService topicAnalysisService;

  /** 集群模型映射器，将内部模型转换为 DTO */
  private final ClusterMapper clusterMapper;

  /**
   * 创建新 Topic
   *
   * @param clusterName      集群名称
   * @param topicCreationMono Topic 创建参数（包含名称、分区数、副本因子、配置等）
   * @param exchange          服务器交换对象
   * @return 创建后的 Topic DTO
   */
  @Override
  public Mono<ResponseEntity<TopicDTO>> createTopic(
      String clusterName, @Valid Mono<TopicCreationDTO> topicCreationMono, ServerWebExchange exchange) {
    return topicCreationMono.flatMap(topicCreation -> {
      var context = AccessContext.builder()
          .cluster(clusterName)
          .topicActions(CREATE)
          .operationName("createTopic")
          .operationParams(topicCreation)
          .build();

      return validateAccess(context)
          .then(topicsService.createTopic(getCluster(clusterName), topicCreation))
          .map(clusterMapper::toTopic)
          .map(s -> new ResponseEntity<>(s, HttpStatus.OK))
          .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
          .doOnEach(sig -> audit(context, sig));
    });
  }

  /**
   * 重建 Topic（删除后重新创建）
   *
   * @param clusterName 集群名称
   * @param topicName   要重建的 Topic 名称
   * @param exchange    服务器交换对象
   * @return 重建后的 Topic DTO
   */
  @Override
  public Mono<ResponseEntity<TopicDTO>> recreateTopic(String clusterName,
                                                      String topicName, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW, CREATE, DELETE)
        .operationName("recreateTopic")
        .build();

    return validateAccess(context).then(
        topicsService.recreateTopic(getCluster(clusterName), topicName)
            .map(clusterMapper::toTopic)
            .map(s -> new ResponseEntity<>(s, HttpStatus.CREATED))
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 克隆 Topic（复制配置创建新 Topic）
   *
   * @param clusterName 集群名称
   * @param topicName   源 Topic 名称
   * @param newTopicName 新 Topic 名称
   * @param exchange    服务器交换对象
   * @return 新创建的 Topic DTO
   */
  @Override
  public Mono<ResponseEntity<TopicDTO>> cloneTopic(
      String clusterName, String topicName, String newTopicName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW, CREATE)
        .operationName("cloneTopic")
        .operationParams(Map.of("newTopicName", newTopicName))
        .build();

    return validateAccess(context)
        .then(topicsService.cloneTopic(getCluster(clusterName), topicName, newTopicName)
            .map(clusterMapper::toTopic)
            .map(s -> new ResponseEntity<>(s, HttpStatus.CREATED))
        ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 删除 Topic
   *
   * @param clusterName 集群名称
   * @param topicName   要删除的 Topic 名称
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteTopic(
      String clusterName, String topicName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(DELETE)
        .operationName("deleteTopic")
        .build();

    return validateAccess(context)
        .then(
            topicsService.deleteTopic(getCluster(clusterName), topicName)
                .thenReturn(ResponseEntity.ok().<Void>build())
        ).doOnEach(sig -> audit(context, sig));
  }


  /**
   * 获取 Topic 配置列表
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return Topic 配置列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<TopicConfigDTO>>> getTopicConfigs(
      String clusterName, String topicName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW)
        .operationName("getTopicConfigs")
        .build();

    return validateAccess(context).then(
        topicsService.getTopicConfigs(getCluster(clusterName), topicName)
            .map(lst -> lst.stream()
                .map(InternalTopicConfig::from)
                .map(clusterMapper::toTopicConfig)
                .toList())
            .map(Flux::fromIterable)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取 Topic 详情（含分区、副本、配置等完整信息）
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return Topic 详情 DTO
   */
  @Override
  public Mono<ResponseEntity<TopicDetailsDTO>> getTopicDetails(
      String clusterName, String topicName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW)
        .operationName("getTopicDetails")
        .build();

    return validateAccess(context).then(
        topicsService.getTopicDetails(getCluster(clusterName), topicName)
            .map(clusterMapper::toTopicDetails)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 分页获取 Topic 列表
   *
   * @param clusterName 集群名称
   * @param page        页码（从 1 开始，默认 1）
   * @param perPage     每页大小（默认 25）
   * @param showInternal 是否显示内部 Topic
   * @param search      搜索关键词（按名称模糊匹配）
   * @param orderBy     排序字段（名称、分区数、副本因子等）
   * @param sortOrder   排序方向（ASC/DESC）
   * @param exchange    服务器交换对象
   * @return Topic 分页响应（包含 Topic 列表和总页数）
   */
  @Override
  public Mono<ResponseEntity<TopicsResponseDTO>> getTopics(String clusterName,
                                                           @Valid Integer page,
                                                           @Valid Integer perPage,
                                                           @Valid Boolean showInternal,
                                                           @Valid String search,
                                                           @Valid TopicColumnsToSortDTO orderBy,
                                                           @Valid SortOrderDTO sortOrder,
                                                           ServerWebExchange exchange) {

    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getTopics")
        .build();

    return topicsService.getTopicsForPagination(getCluster(clusterName))
        .flatMap(topics -> accessControlService.filterViewableTopics(topics, clusterName))
        .flatMap(topics -> {
          int pageSize = perPage != null && perPage > 0 ? perPage : DEFAULT_PAGE_SIZE;
          var topicsToSkip = ((page != null && page > 0 ? page : 1) - 1) * pageSize;
          var comparator = sortOrder == null || !sortOrder.equals(SortOrderDTO.DESC)
              ? getComparatorForTopic(orderBy) : getComparatorForTopic(orderBy).reversed();
          List<InternalTopic> filtered = topics.stream()
              .filter(topic -> !topic.isInternal()
                  || showInternal != null && showInternal)
              .filter(topic -> search == null || StringUtils.containsIgnoreCase(topic.getName(), search))
              .sorted(comparator)
              .toList();
          var totalPages = (filtered.size() / pageSize)
              + (filtered.size() % pageSize == 0 ? 0 : 1);

          List<String> topicsPage = filtered.stream()
              .skip(topicsToSkip)
              .limit(pageSize)
              .map(InternalTopic::getName)
              .collect(toList());

          return topicsService.loadTopics(getCluster(clusterName), topicsPage)
              .map(topicsToRender ->
                  new TopicsResponseDTO()
                      .topics(topicsToRender.stream().map(clusterMapper::toTopic).toList())
                      .pageCount(totalPages));
        })
        .map(ResponseEntity::ok)
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 更新 Topic 配置
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param topicUpdate 更新参数（包含配置项键值对）
   * @param exchange    服务器交换对象
   * @return 更新后的 Topic DTO
   */
  @Override
  public Mono<ResponseEntity<TopicDTO>> updateTopic(
      String clusterName, String topicName, @Valid Mono<TopicUpdateDTO> topicUpdate,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW, EDIT)
        .operationName("updateTopic")
        .build();

    return validateAccess(context).then(
        topicsService
            .updateTopic(getCluster(clusterName), topicName, topicUpdate)
            .map(clusterMapper::toTopic)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 增加 Topic 分区数量
   *
   * @param clusterName       集群名称
   * @param topicName         Topic 名称
   * @param partitionsIncrease 分区增加参数（包含目标分区数）
   * @param exchange          服务器交换对象
   * @return 分区增加结果
   */
  @Override
  public Mono<ResponseEntity<PartitionsIncreaseResponseDTO>> increaseTopicPartitions(
      String clusterName, String topicName,
      Mono<PartitionsIncreaseDTO> partitionsIncrease,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW, EDIT)
        .build();

    return validateAccess(context).then(
        partitionsIncrease.flatMap(partitions ->
            topicsService.increaseTopicPartitions(getCluster(clusterName), topicName, partitions)
        ).map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 变更 Topic 副本因子
   *
   * @param clusterName          集群名称
   * @param topicName            Topic 名称
   * @param replicationFactorChange 副本因子变更参数（包含新的副本因子和副本分配方案）
   * @param exchange             服务器交换对象
   * @return 副本因子变更结果
   */
  @Override
  public Mono<ResponseEntity<ReplicationFactorChangeResponseDTO>> changeReplicationFactor(
      String clusterName, String topicName,
      Mono<ReplicationFactorChangeDTO> replicationFactorChange,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW, EDIT)
        .operationName("changeReplicationFactor")
        .build();

    return validateAccess(context).then(
        replicationFactorChange
            .flatMap(rfc ->
                topicsService.changeReplicationFactor(getCluster(clusterName), topicName, rfc))
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 启动 Topic 异步分析任务
   *
   * 分析任务会在后台运行，通过 getTopicAnalysis 获取结果。
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> analyzeTopic(String clusterName, String topicName, ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_READ)
        .operationName("analyzeTopic")
        .build();

    return validateAccess(context).then(
        topicAnalysisService.analyze(getCluster(clusterName), topicName)
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }

  /**
   * 取消正在运行的 Topic 分析任务
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> cancelTopicAnalysis(String clusterName, String topicName,
                                                        ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_READ)
        .operationName("cancelTopicAnalysis")
        .build();

    return validateAccess(context)
        .then(Mono.fromRunnable(() -> topicAnalysisService.cancelAnalysis(getCluster(clusterName), topicName)))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }


  /**
   * 获取 Topic 分析结果
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return Topic 分析结果 DTO，如果分析未启动则返回 404
   */
  @Override
  public Mono<ResponseEntity<TopicAnalysisDTO>> getTopicAnalysis(String clusterName,
                                                                 String topicName,
                                                                 ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_READ)
        .operationName("getTopicAnalysis")
        .build();

    return validateAccess(context)
        .thenReturn(topicAnalysisService.getTopicAnalysis(getCluster(clusterName), topicName)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build()))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取 Topic 活跃生产者状态
   *
   * 返回当前正在向该 Topic 发送消息的生产者信息，按分区和生产者 ID 排序。
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return 活跃生产者状态列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<TopicProducerStateDTO>>> getActiveProducerStates(String clusterName,
                                                                                   String topicName,
                                                                                   ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(VIEW)
        .operationName("getActiveProducerStates")
        .build();

    Comparator<TopicProducerStateDTO> ordering =
        Comparator.comparingInt(TopicProducerStateDTO::getPartition)
            .thenComparing(Comparator.comparing(TopicProducerStateDTO::getProducerId).reversed());

    Flux<TopicProducerStateDTO> states = topicsService.getActiveProducersState(getCluster(clusterName), topicName)
        .flatMapMany(statesMap ->
            Flux.fromStream(
                statesMap.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(p -> clusterMapper.map(e.getKey().partition(), p)))
                    .sorted(ordering)));

    return validateAccess(context)
        .thenReturn(states)
        .map(ResponseEntity::ok)
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 根据排序字段创建 Topic 比较器
   *
   * @param orderBy 排序字段枚举
   * @return Topic 比较器，orderBy 为 null 时按名称排序
   */
  private Comparator<InternalTopic> getComparatorForTopic(
      TopicColumnsToSortDTO orderBy) {
    var defaultComparator = Comparator.comparing(InternalTopic::getName);
    if (orderBy == null) {
      return defaultComparator;
    }
    switch (orderBy) {
      case TOTAL_PARTITIONS:
        return Comparator.comparing(InternalTopic::getPartitionCount);
      case OUT_OF_SYNC_REPLICAS:
        return Comparator.comparing(t -> t.getReplicas() - t.getInSyncReplicas());
      case REPLICATION_FACTOR:
        return Comparator.comparing(InternalTopic::getReplicationFactor);
      case SIZE:
        return Comparator.comparing(InternalTopic::getSegmentSize);
      case NAME:
      default:
        return defaultComparator;
    }
  }
}
