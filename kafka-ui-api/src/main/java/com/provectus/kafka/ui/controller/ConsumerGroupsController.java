package com.provectus.kafka.ui.controller;

import static com.provectus.kafka.ui.model.rbac.permission.ConsumerGroupAction.DELETE;
import static com.provectus.kafka.ui.model.rbac.permission.ConsumerGroupAction.RESET_OFFSETS;
import static com.provectus.kafka.ui.model.rbac.permission.ConsumerGroupAction.VIEW;
import static java.util.stream.Collectors.toMap;

import com.provectus.kafka.ui.api.ConsumerGroupsApi;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.mapper.ConsumerGroupMapper;
import com.provectus.kafka.ui.model.ConsumerGroupDTO;
import com.provectus.kafka.ui.model.ConsumerGroupDetailsDTO;
import com.provectus.kafka.ui.model.ConsumerGroupOffsetsResetDTO;
import com.provectus.kafka.ui.model.ConsumerGroupOrderingDTO;
import com.provectus.kafka.ui.model.ConsumerGroupsPageResponseDTO;
import com.provectus.kafka.ui.model.PartitionOffsetDTO;
import com.provectus.kafka.ui.model.SortOrderDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.TopicAction;
import com.provectus.kafka.ui.service.ConsumerGroupService;
import com.provectus.kafka.ui.service.OffsetsResetService;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消费者组管理控制器
 *
 * 提供 Kafka 消费者组的管理操作，包括：
 * 1. 获取消费者组列表（分页、搜索、排序）
 * 2. 获取消费者组详情（成员、偏移量、延迟等）
 * 3. 删除消费者组
 * 4. 重置消费者组偏移量（支持按最早/最新/时间戳/指定偏移量重置）
 * 5. 获取指定 Topic 的消费者组列表
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ConsumerGroupsController extends AbstractController implements ConsumerGroupsApi {

  /** 消费者组业务服务，处理消费者组的核心逻辑 */
  private final ConsumerGroupService consumerGroupService;

  /** 偏移量重置服务，处理消费者组偏移量的重置操作 */
  private final OffsetsResetService offsetsResetService;

  /** 消费者组列表默认分页大小，可通过 consumer.groups.page.size 配置 */
  @Value("${consumer.groups.page.size:25}")
  private int defaultConsumerGroupsPageSize;

  /**
   * 删除消费者组
   *
   * @param clusterName 集群名称
   * @param id          消费者组 ID
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteConsumerGroup(String clusterName,
                                                        String id,
                                                        ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .consumerGroup(id)
        .consumerGroupActions(DELETE)
        .operationName("deleteConsumerGroup")
        .build();

    return validateAccess(context)
        .then(consumerGroupService.deleteConsumerGroupById(getCluster(clusterName), id))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 获取消费者组详情
   *
   * 包含消费者组的状态、成员列表、各分区的消费偏移量和延迟等信息。
   *
   * @param clusterName      集群名称
   * @param consumerGroupId  消费者组 ID
   * @param exchange         服务器交换对象
   * @return 消费者组详情 DTO
   */
  @Override
  public Mono<ResponseEntity<ConsumerGroupDetailsDTO>> getConsumerGroup(String clusterName,
                                                                        String consumerGroupId,
                                                                        ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .consumerGroup(consumerGroupId)
        .consumerGroupActions(VIEW)
        .operationName("getConsumerGroup")
        .build();

    return validateAccess(context)
        .then(consumerGroupService.getConsumerGroupDetail(getCluster(clusterName), consumerGroupId)
            .map(ConsumerGroupMapper::toDetailsDto)
            .map(ResponseEntity::ok))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取消费指定 Topic 的消费者组列表
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param exchange    服务器交换对象
   * @return 消费者组列表流，如果没有消费者则返回 404
   */
  @Override
  public Mono<ResponseEntity<Flux<ConsumerGroupDTO>>> getTopicConsumerGroups(String clusterName,
                                                                             String topicName,
                                                                             ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(TopicAction.VIEW)
        .operationName("getTopicConsumerGroups")
        .build();

    Mono<ResponseEntity<Flux<ConsumerGroupDTO>>> job =
        consumerGroupService.getConsumerGroupsForTopic(getCluster(clusterName), topicName)
            .flatMapMany(Flux::fromIterable)
            .filterWhen(cg -> accessControlService.isConsumerGroupAccessible(cg.getGroupId(), clusterName))
            .map(ConsumerGroupMapper::toDto)
            .collectList()
            .map(Flux::fromIterable)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));

    return validateAccess(context)
        .then(job)
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 分页获取消费者组列表
   *
   * @param clusterName  集群名称
   * @param page         页码（从 1 开始，默认 1）
   * @param perPage      每页大小（默认 25，可通过配置修改）
   * @param search       搜索关键词（按消费者组 ID 模糊匹配）
   * @param orderBy      排序字段（名称等）
   * @param sortOrderDto 排序方向（ASC/DESC）
   * @param exchange     服务器交换对象
   * @return 消费者组分页响应（包含消费者组列表和总页数）
   */
  @Override
  public Mono<ResponseEntity<ConsumerGroupsPageResponseDTO>> getConsumerGroupsPage(
      String clusterName,
      Integer page,
      Integer perPage,
      String search,
      ConsumerGroupOrderingDTO orderBy,
      SortOrderDTO sortOrderDto,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        // consumer group access validation is within the service
        .operationName("getConsumerGroupsPage")
        .build();

    return validateAccess(context).then(
        consumerGroupService.getConsumerGroupsPage(
                getCluster(clusterName),
                Optional.ofNullable(page).filter(i -> i > 0).orElse(1),
                Optional.ofNullable(perPage).filter(i -> i > 0).orElse(defaultConsumerGroupsPageSize),
                search,
                Optional.ofNullable(orderBy).orElse(ConsumerGroupOrderingDTO.NAME),
                Optional.ofNullable(sortOrderDto).orElse(SortOrderDTO.ASC)
            )
            .map(this::convertPage)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 重置消费者组偏移量
   *
   * 支持四种重置方式：
   * - EARLIEST：重置到最早可用偏移量
   * - LATEST：重置到最新偏移量
   * - TIMESTAMP：重置到指定时间戳对应的偏移量
   * - OFFSET：重置到指定的精确偏移量
   *
   * @param clusterName 集群名称
   * @param group       消费者组 ID
   * @param resetDto    重置参数（包含重置类型、Topic、分区等）
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> resetConsumerGroupOffsets(String clusterName,
                                                              String group,
                                                              Mono<ConsumerGroupOffsetsResetDTO> resetDto,
                                                              ServerWebExchange exchange) {
    return resetDto.flatMap(reset -> {
      var context = AccessContext.builder()
          .cluster(clusterName)
          .topic(reset.getTopic())
          .topicActions(TopicAction.VIEW)
          .consumerGroupActions(RESET_OFFSETS)
          .operationName("resetConsumerGroupOffsets")
          .build();

      Supplier<Mono<Void>> mono = () -> {
        var cluster = getCluster(clusterName);
        switch (reset.getResetType()) {
          case EARLIEST:
            return offsetsResetService
                .resetToEarliest(cluster, group, reset.getTopic(), reset.getPartitions());
          case LATEST:
            return offsetsResetService
                .resetToLatest(cluster, group, reset.getTopic(), reset.getPartitions());
          case TIMESTAMP:
            if (reset.getResetToTimestamp() == null) {
              return Mono.error(
                  new ValidationException(
                      "resetToTimestamp is required when TIMESTAMP reset type used"
                  )
              );
            }
            return offsetsResetService
                .resetToTimestamp(cluster, group, reset.getTopic(), reset.getPartitions(),
                    reset.getResetToTimestamp());
          case OFFSET:
            if (CollectionUtils.isEmpty(reset.getPartitionsOffsets())) {
              return Mono.error(
                  new ValidationException(
                      "partitionsOffsets is required when OFFSET reset type used"
                  )
              );
            }
            Map<Integer, Long> offsets = reset.getPartitionsOffsets().stream()
                .collect(toMap(PartitionOffsetDTO::getPartition, PartitionOffsetDTO::getOffset));
            return offsetsResetService.resetToOffsets(cluster, group, reset.getTopic(), offsets);
          default:
            return Mono.error(
                new ValidationException("Unknown resetType " + reset.getResetType())
            );
        }
      };

      return validateAccess(context)
          .then(mono.get())
          .doOnEach(sig -> audit(context, sig));
    }).thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 将内部消费者组分页结果转换为 DTO
   *
   * @param consumerGroupConsumerGroupsPage 内部分页结果
   * @return 消费者组分页响应 DTO
   */
  private ConsumerGroupsPageResponseDTO convertPage(ConsumerGroupService.ConsumerGroupsPage
                                                        consumerGroupConsumerGroupsPage) {
    return new ConsumerGroupsPageResponseDTO()
        .pageCount(consumerGroupConsumerGroupsPage.totalPages())
        .consumerGroups(consumerGroupConsumerGroupsPage.consumerGroups()
            .stream()
            .map(ConsumerGroupMapper::toDto)
            .toList());
  }

}
