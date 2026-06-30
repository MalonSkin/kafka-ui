package com.provectus.kafka.ui.controller;

import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.MESSAGES_DELETE;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.MESSAGES_PRODUCE;
import static com.provectus.kafka.ui.model.rbac.permission.TopicAction.MESSAGES_READ;
import static com.provectus.kafka.ui.serde.api.Serde.Target.KEY;
import static com.provectus.kafka.ui.serde.api.Serde.Target.VALUE;
import static java.util.stream.Collectors.toMap;

import com.provectus.kafka.ui.api.MessagesApi;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.model.ConsumerPosition;
import com.provectus.kafka.ui.model.CreateTopicMessageDTO;
import com.provectus.kafka.ui.model.MessageFilterTypeDTO;
import com.provectus.kafka.ui.model.SeekDirectionDTO;
import com.provectus.kafka.ui.model.SeekTypeDTO;
import com.provectus.kafka.ui.model.SerdeUsageDTO;
import com.provectus.kafka.ui.model.SmartFilterTestExecutionDTO;
import com.provectus.kafka.ui.model.SmartFilterTestExecutionResultDTO;
import com.provectus.kafka.ui.model.TopicMessageEventDTO;
import com.provectus.kafka.ui.model.TopicSerdeSuggestionDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.AuditAction;
import com.provectus.kafka.ui.model.rbac.permission.TopicAction;
import com.provectus.kafka.ui.service.DeserializationService;
import com.provectus.kafka.ui.service.MessagesService;
import com.provectus.kafka.ui.util.DynamicConfigOperations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.TopicPartition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka 消息管理控制器
 *
 * 提供 Kafka 消息的读取、生产和删除操作，包括：
 * 1. 消费 Topic 消息（支持按偏移量/时间戳定位，支持消息过滤）
 * 2. 生产消息到 Topic（支持自定义 Key/Value 序列化）
 * 3. 删除指定分区的消息（基于偏移量或时间戳）
 * 4. 获取 Topic 的可用 SerDe（序列化/反序列化器）列表
 * 5. Smart Filter 测试执行（用于验证消息过滤规则）
 *
 * <p>消息读取支持多种定位方式（SeekType）：从头开始、从尾部、按偏移量、按时间戳。
 * 消息过滤支持字符串匹配和 Groovy 脚本（需在配置中启用）。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MessagesController extends AbstractController implements MessagesApi {

  /** 消息业务服务，处理消息的读取、生产和删除 */
  private final MessagesService messagesService;

  /** 反序列化服务，提供可用的 SerDe 列表 */
  private final DeserializationService deserializationService;

  /** 动态配置操作工具，用于检查 Groovy 过滤是否启用等配置 */
  private final DynamicConfigOperations dynamicConfigOperations;

  /**
   * 删除 Topic 中的消息
   *
   * 支持删除指定分区的消息。如果未指定分区，则删除所有分区的消息。
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param partitions  要删除消息的分区列表（为空则删除所有分区）
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteTopicMessages(
      String clusterName, String topicName, @Valid List<Integer> partitions,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_DELETE)
        .build();

    return validateAccess(context).<ResponseEntity<Void>>then(
        messagesService.deleteTopicMessages(
            getCluster(clusterName),
            topicName,
            Optional.ofNullable(partitions).orElse(List.of())
        ).thenReturn(ResponseEntity.ok().build())
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 执行 Smart Filter 测试
   *
   * 用于验证消息过滤规则是否符合预期，不实际消费消息。
   *
   * @param smartFilterTestExecutionDto Smart Filter 测试参数（包含过滤规则和测试数据）
   * @param exchange                    服务器交换对象
   * @return 测试执行结果
   */
  @Override
  public Mono<ResponseEntity<SmartFilterTestExecutionResultDTO>> executeSmartFilterTest(
      Mono<SmartFilterTestExecutionDTO> smartFilterTestExecutionDto, ServerWebExchange exchange) {
    return smartFilterTestExecutionDto
        .map(MessagesService::execSmartFilterTest)
        .map(ResponseEntity::ok);
  }

  /**
   * 消费 Topic 消息
   *
   * 返回消息流，支持按偏移量/时间戳定位、消息过滤、自定义序列化器。
   * 如果使用 Groovy 脚本过滤，需在配置中启用 {@code dynamic.config.operations.filtering.groovy.enabled}。
   *
   * @param clusterName     集群名称
   * @param topicName       Topic 名称
   * @param seekType        定位类型（BEGINNING/LATEST/OFFSET/TIMESTAMP）
   * @param seekTo          定位目标（格式：[partition]::[offset] 或 [partition]::[timestamp]）
   * @param limit           最大返回消息数
   * @param q               过滤查询字符串
   * @param filterQueryType 过滤类型（STRING_CONTAINS/GROOVY_SCRIPT 等）
   * @param seekDirection   消费方向（FORWARD/BACKWARD）
   * @param keySerde        Key 的序列化/反序列化器名称
   * @param valueSerde      Value 的序列化/反序列化器名称
   * @param exchange        服务器交换对象
   * @return 消息事件流（每条消息包含 Key、Value、元数据等）
   */
  @Override
  public Mono<ResponseEntity<Flux<TopicMessageEventDTO>>> getTopicMessages(String clusterName,
                                                                           String topicName,
                                                                           SeekTypeDTO seekType,
                                                                           List<String> seekTo,
                                                                           Integer limit,
                                                                           String q,
                                                                           MessageFilterTypeDTO filterQueryType,
                                                                           SeekDirectionDTO seekDirection,
                                                                           String keySerde,
                                                                           String valueSerde,
                                                                           ServerWebExchange exchange) {
    var contextBuilder = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_READ)
        .operationName("getTopicMessages");

    if (StringUtils.isNoneEmpty(q) && MessageFilterTypeDTO.GROOVY_SCRIPT == filterQueryType) {
      dynamicConfigOperations.checkIfFilteringGroovyEnabled();
    }

    if (auditService.isAuditTopic(getCluster(clusterName), topicName)) {
      contextBuilder.auditActions(AuditAction.VIEW);
    }

    seekType = seekType != null ? seekType : SeekTypeDTO.BEGINNING;
    seekDirection = seekDirection != null ? seekDirection : SeekDirectionDTO.FORWARD;
    filterQueryType = filterQueryType != null ? filterQueryType : MessageFilterTypeDTO.STRING_CONTAINS;

    var positions = new ConsumerPosition(
        seekType,
        topicName,
        parseSeekTo(topicName, seekType, seekTo)
    );
    Mono<ResponseEntity<Flux<TopicMessageEventDTO>>> job = Mono.just(
        ResponseEntity.ok(
            messagesService.loadMessages(
                getCluster(clusterName), topicName, positions, q, filterQueryType,
                limit, seekDirection, keySerde, valueSerde)
        )
    );

    var context = contextBuilder.build();
    return validateAccess(context)
        .then(job)
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 生产消息到 Topic
   *
   * @param clusterName        集群名称
   * @param topicName          目标 Topic 名称
   * @param createTopicMessage 消息内容（包含 Key、Value、分区、Header 等）
   * @param exchange           服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> sendTopicMessages(
      String clusterName, String topicName, @Valid Mono<CreateTopicMessageDTO> createTopicMessage,
      ServerWebExchange exchange) {

    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(MESSAGES_PRODUCE)
        .operationName("sendTopicMessages")
        .build();

    return validateAccess(context).then(
        createTopicMessage.flatMap(msg ->
            messagesService.sendMessage(getCluster(clusterName), topicName, msg).then()
        ).map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 解析 seekTo 参数为 Topic 分区偏移量映射
   *
   * <p>格式：[partition]::[offset] 指定偏移量，或 [partition]::[timestamp in millis] 指定时间戳。
   * 例如："0::100" 表示分区 0 偏移量 100；"1::1620000000000" 表示分区 1 在指定时间戳处。</p>
   *
   * @param topic    Topic 名称
   * @param seekType 定位类型
   * @param seekTo   定位目标字符串列表
   * @return 分区偏移量映射，LATEST/BEGINNING 类型时返回 null
   * @throws ValidationException 当 seekType 需要 seekTo 但未提供时
   * @throws IllegalArgumentException 当 seekTo 格式不正确时
   */
  @Nullable
  private Map<TopicPartition, Long> parseSeekTo(String topic, SeekTypeDTO seekType, List<String> seekTo) {
    if (seekTo == null || seekTo.isEmpty()) {
      if (seekType == SeekTypeDTO.LATEST || seekType == SeekTypeDTO.BEGINNING) {
        return null;
      }
      throw new ValidationException("seekTo should be set if seekType is " + seekType);
    }
    return seekTo.stream()
        .map(p -> {
          String[] split = p.split("::");
          if (split.length != 2) {
            throw new IllegalArgumentException(
                "Wrong seekTo argument format. See API docs for details");
          }

          return Pair.of(
              new TopicPartition(topic, Integer.parseInt(split[0])),
              Long.parseLong(split[1])
          );
        })
        .collect(toMap(Pair::getKey, Pair::getValue));
  }

  /**
   * 获取 Topic 可用的 SerDe（序列化/反序列化器）列表
   *
   * @param clusterName 集群名称
   * @param topicName   Topic 名称
   * @param use         用途（SERIALIZE 用于生产消息，DESERIALIZE 用于消费消息）
   * @param exchange    服务器交换对象
   * @return SerDe 建议（包含 Key 和 Value 各自可用的 SerDe 列表）
   */
  @Override
  public Mono<ResponseEntity<TopicSerdeSuggestionDTO>> getSerdes(String clusterName,
                                                                 String topicName,
                                                                 SerdeUsageDTO use,
                                                                 ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .topic(topicName)
        .topicActions(TopicAction.VIEW)
        .operationName("getSerdes")
        .build();

    TopicSerdeSuggestionDTO dto = new TopicSerdeSuggestionDTO()
        .key(use == SerdeUsageDTO.SERIALIZE
            ? deserializationService.getSerdesForSerialize(getCluster(clusterName), topicName, KEY)
            : deserializationService.getSerdesForDeserialize(getCluster(clusterName), topicName, KEY))
        .value(use == SerdeUsageDTO.SERIALIZE
            ? deserializationService.getSerdesForSerialize(getCluster(clusterName), topicName, VALUE)
            : deserializationService.getSerdesForDeserialize(getCluster(clusterName), topicName, VALUE));

    return validateAccess(context).then(
        Mono.just(dto)
            .subscribeOn(Schedulers.boundedElastic())
            .map(ResponseEntity::ok)
    );
  }




}
