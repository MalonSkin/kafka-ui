package com.provectus.kafka.ui.service;

import com.google.common.util.concurrent.RateLimiter;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.emitter.BackwardEmitter;
import com.provectus.kafka.ui.emitter.ForwardEmitter;
import com.provectus.kafka.ui.emitter.MessageFilters;
import com.provectus.kafka.ui.emitter.TailingEmitter;
import com.provectus.kafka.ui.exception.TopicNotFoundException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.model.ConsumerPosition;
import com.provectus.kafka.ui.model.CreateTopicMessageDTO;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.MessageFilterTypeDTO;
import com.provectus.kafka.ui.model.SeekDirectionDTO;
import com.provectus.kafka.ui.model.SmartFilterTestExecutionDTO;
import com.provectus.kafka.ui.model.SmartFilterTestExecutionResultDTO;
import com.provectus.kafka.ui.model.TopicMessageDTO;
import com.provectus.kafka.ui.model.TopicMessageEventDTO;
import com.provectus.kafka.ui.serdes.ProducerRecordCreator;
import com.provectus.kafka.ui.util.SslPropertiesUtil;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka 消息服务。
 *
 * <p>提供消息的消费、生产和删除功能，主要包括：
 * <ul>
 *   <li>支持正向（FORWARD）、反向（BACKWARD）和尾随（TAILING）三种消息消费模式</li>
 *   <li>消息的发送（支持指定分区、自定义序列化方式和自定义 Header）</li>
 *   <li>按分区删除主题中的消息记录</li>
 *   <li>Smart Filter（Groovy 脚本）的编译与执行测试</li>
 *   <li>分页大小的配置管理及 UI 消息速率限制（尾随模式下限制为 20 条/秒）</li>
 * </ul>
 *
 * <p>消息消费通过 {@link DeserializationService} 进行反序列化，
 * 并支持基于 Groovy 脚本或 Header 的消息过滤。
 */
@Service
@Slf4j
public class MessagesService {

  private static final int DEFAULT_MAX_PAGE_SIZE = 500;
  private static final int DEFAULT_PAGE_SIZE = 100;
  // limiting UI messages rate to 20/sec in tailing mode
  private static final int TAILING_UI_MESSAGE_THROTTLE_RATE = 20;

  private final AdminClientService adminClientService;
  private final DeserializationService deserializationService;
  private final ConsumerGroupService consumerGroupService;
  private final int maxPageSize;
  private final int defaultPageSize;

  /**
   * 构造消息服务实例。
   *
   * @param adminClientService     AdminClient 服务，用于集群管理操作
   * @param deserializationService 反序列化服务，用于消息的序列化和反序列化
   * @param consumerGroupService   消费者组服务，用于创建消费者实例
   * @param properties             集群配置属性，包含分页大小等轮询配置
   */
  public MessagesService(AdminClientService adminClientService,
                         DeserializationService deserializationService,
                         ConsumerGroupService consumerGroupService,
                         ClustersProperties properties) {
    this.adminClientService = adminClientService;
    this.deserializationService = deserializationService;
    this.consumerGroupService = consumerGroupService;

    var pollingProps = Optional.ofNullable(properties.getPolling())
        .orElseGet(ClustersProperties.PollingProperties::new);
    this.maxPageSize = Optional.ofNullable(pollingProps.getMaxPageSize())
        .orElse(DEFAULT_MAX_PAGE_SIZE);
    this.defaultPageSize = Optional.ofNullable(pollingProps.getDefaultPageSize())
        .orElse(DEFAULT_PAGE_SIZE);
  }

  /**
   * 验证主题是否存在并返回其描述信息。
   *
   * @param cluster   目标 Kafka 集群
   * @param topicName 主题名称
   * @return 包含 {@link TopicDescription} 的 Mono，若主题不存在则触发 {@link TopicNotFoundException}
   */
  private Mono<TopicDescription> withExistingTopic(KafkaCluster cluster, String topicName) {
    return adminClientService.get(cluster)
        .flatMap(client -> client.describeTopic(topicName))
        .switchIfEmpty(Mono.error(new TopicNotFoundException()));
  }

  /**
   * 执行 Smart Filter（Groovy 脚本）的测试。
   *
   * <p>编译并执行用户提供的 Groovy 脚本过滤器，使用测试数据验证其正确性。
   * 返回编译错误、执行错误或过滤结果。
   *
   * @param execData 包含过滤器代码和测试数据的 {@link SmartFilterTestExecutionDTO}
   * @return 包含执行结果或错误信息的 {@link SmartFilterTestExecutionResultDTO}
   */
  public static SmartFilterTestExecutionResultDTO execSmartFilterTest(SmartFilterTestExecutionDTO execData) {
    Predicate<TopicMessageDTO> predicate;
    try {
      predicate = MessageFilters.createMsgFilter(
          execData.getFilterCode(),
          MessageFilterTypeDTO.GROOVY_SCRIPT
      );
    } catch (Exception e) {
      log.info("Smart filter '{}' compilation error", execData.getFilterCode(), e);
      return new SmartFilterTestExecutionResultDTO()
          .error("Compilation error : " + e.getMessage());
    }
    try {
      var result = predicate.test(
          new TopicMessageDTO()
              .key(execData.getKey())
              .content(execData.getValue())
              .headers(execData.getHeaders())
              .offset(execData.getOffset())
              .partition(execData.getPartition())
              .timestamp(
                  Optional.ofNullable(execData.getTimestampMs())
                      .map(ts -> OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC))
                      .orElse(null))
      );
      return new SmartFilterTestExecutionResultDTO()
          .result(result);
    } catch (Exception e) {
      log.info("Smart filter {} execution error", execData, e);
      return new SmartFilterTestExecutionResultDTO()
          .error("Execution error : " + e.getMessage());
    }
  }

  /**
   * 删除指定主题中的消息记录。
   *
   * <p>仅删除指定分区中的消息。通过将分区的最早偏移量设置为当前最新偏移量来实现消息删除。
   *
   * @param cluster             目标 Kafka 集群
   * @param topicName           主题名称
   * @param partitionsToInclude 需要删除消息的分区列表，为空则删除所有分区
   * @return 删除完成的 Mono
   */
  public Mono<Void> deleteTopicMessages(KafkaCluster cluster, String topicName,
                                        List<Integer> partitionsToInclude) {
    return withExistingTopic(cluster, topicName)
        .flatMap(td ->
            offsetsForDeletion(cluster, topicName, partitionsToInclude)
                .flatMap(offsets ->
                    adminClientService.get(cluster).flatMap(ac -> ac.deleteRecords(offsets))));
  }

  /**
   * 计算需要删除消息的分区及其最新偏移量。
   *
   * <p>筛选出非空分区（起始偏移量 != 最新偏移量），并根据指定的分区列表进行过滤。
   *
   * @param cluster             目标 Kafka 集群
   * @param topicName           主题名称
   * @param partitionsToInclude 需要包含的分区列表，为空则包含所有分区
   * @return 分区到最新偏移量的映射
   */
  private Mono<Map<TopicPartition, Long>> offsetsForDeletion(KafkaCluster cluster, String topicName,
                                                             List<Integer> partitionsToInclude) {
    return adminClientService.get(cluster).flatMap(ac ->
        ac.listTopicOffsets(topicName, OffsetSpec.earliest(), true)
            .zipWith(ac.listTopicOffsets(topicName, OffsetSpec.latest(), true),
                (start, end) ->
                    end.entrySet().stream()
                        .filter(e -> partitionsToInclude.isEmpty()
                            || partitionsToInclude.contains(e.getKey().partition()))
                        // we only need non-empty partitions (where start offset != end offset)
                        .filter(entry -> !entry.getValue().equals(start.get(entry.getKey())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
    );
  }

  /**
   * 向指定主题发送消息。
   *
   * <p>验证主题存在后，在有界弹性线程池上执行消息发送操作。
   * 支持指定分区、自定义 Key/Value 序列化方式和自定义 Header。
   *
   * @param cluster 目标 Kafka 集群
   * @param topic   目标主题名称
   * @param msg     消息内容（包含 Key、Value、Header、分区和序列化方式）
   * @return 包含发送结果元数据的 Mono
   */
  public Mono<RecordMetadata> sendMessage(KafkaCluster cluster, String topic,
                                          CreateTopicMessageDTO msg) {
    return withExistingTopic(cluster, topic)
        .publishOn(Schedulers.boundedElastic())
        .flatMap(desc -> sendMessageImpl(cluster, desc, msg));
  }

  private Mono<RecordMetadata> sendMessageImpl(KafkaCluster cluster,
                                               TopicDescription topicDescription,
                                               CreateTopicMessageDTO msg) {
    if (msg.getPartition() != null
        && msg.getPartition() > topicDescription.partitions().size() - 1) {
      return Mono.error(new ValidationException("Invalid partition"));
    }
    ProducerRecordCreator producerRecordCreator =
        deserializationService.producerRecordCreator(
            cluster,
            topicDescription.name(),
            msg.getKeySerde().get(),
            msg.getValueSerde().get()
        );

    try (KafkaProducer<byte[], byte[]> producer = createProducer(cluster, Map.of())) {
      ProducerRecord<byte[], byte[]> producerRecord = producerRecordCreator.create(
          topicDescription.name(),
          msg.getPartition(),
          msg.getKey().orElse(null),
          msg.getContent().orElse(null),
          msg.getHeaders()
      );
      CompletableFuture<RecordMetadata> cf = new CompletableFuture<>();
      producer.send(producerRecord, (metadata, exception) -> {
        if (exception != null) {
          cf.completeExceptionally(exception);
        } else {
          cf.complete(metadata);
        }
      });
      return Mono.fromFuture(cf);
    } catch (Throwable e) {
      return Mono.error(e);
    }
  }

  /**
   * 创建 Kafka 生产者实例。
   *
   * <p>配置集群的 SSL 属性、Bootstrap Servers，并使用 ByteArray 序列化器。
   * 默认禁用 SSL 主机名验证。支持通过 additionalProps 添加额外配置。
   *
   * @param cluster        目标 Kafka 集群
   * @param additionalProps 额外的生产者配置属性
   * @return 配置完成的 {@link KafkaProducer} 实例
   */
  public static KafkaProducer<byte[], byte[]> createProducer(KafkaCluster cluster,
                                                             Map<String, Object> additionalProps) {
    Properties properties = new Properties();
    SslPropertiesUtil.addKafkaSslProperties(cluster.getOriginalProperties().getSsl(), properties);
    // 设置SSL的Keystore配置
    SslPropertiesUtil.addKafkaSslKeyStoreConfig(cluster.getOriginalProperties().getSslKeystoreConfig(), properties);
    properties.putAll(cluster.getProperties());
    // 默认设置禁用主机名验证
    if (!cluster.getProperties().containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)) {
      properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, StringUtils.EMPTY);
    }
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.putAll(additionalProps);
    return new KafkaProducer<>(properties);
  }

  /**
   * 加载主题中的消息，支持多种消费模式和过滤方式。
   *
   * <p>根据 seekDirection 参数选择不同的消费模式：
   * <ul>
   *   <li>FORWARD — 从指定位置正向消费</li>
   *   <li>BACKWARD — 从指定位置反向消费</li>
   *   <li>TAILING — 尾随模式持续消费新消息（UI 消息速率限制为 20 条/秒）</li>
   * </ul>
   *
   * @param cluster          目标 Kafka 集群
   * @param topic            主题名称
   * @param consumerPosition 消费起始位置
   * @param query            过滤查询表达式（可为 null）
   * @param filterQueryType  过滤类型（Groovy 脚本或 Header 过滤）
   * @param pageSize         每页消息数量（可为 null，使用默认值）
   * @param seekDirection    消费方向（FORWARD/BACKWARD/TAILING）
   * @param keySerde         Key 的反序列化方式（可为 null）
   * @param valueSerde       Value 的反序列化方式（可为 null）
   * @return 消息事件流 {@link TopicMessageEventDTO}
   */
  public Flux<TopicMessageEventDTO> loadMessages(KafkaCluster cluster, String topic,
                                                 ConsumerPosition consumerPosition,
                                                 @Nullable String query,
                                                 MessageFilterTypeDTO filterQueryType,
                                                 @Nullable Integer pageSize,
                                                 SeekDirectionDTO seekDirection,
                                                 @Nullable String keySerde,
                                                 @Nullable String valueSerde) {
    return withExistingTopic(cluster, topic)
        .flux()
        .publishOn(Schedulers.boundedElastic())
        .flatMap(td -> loadMessagesImpl(cluster, topic, consumerPosition, query,
            filterQueryType, fixPageSize(pageSize), seekDirection, keySerde, valueSerde));
  }

  /**
   * 校正分页大小，确保在有效范围内。
   *
   * @param pageSize 请求的分页大小（可为 null）
   * @return 校正后的分页大小（1 到 maxPageSize 之间），无效值返回默认值
   */
  private int fixPageSize(@Nullable Integer pageSize) {
    return Optional.ofNullable(pageSize)
        .filter(ps -> ps > 0 && ps <= maxPageSize)
        .orElse(defaultPageSize);
  }

  private Flux<TopicMessageEventDTO> loadMessagesImpl(KafkaCluster cluster,
                                                      String topic,
                                                      ConsumerPosition consumerPosition,
                                                      @Nullable String query,
                                                      MessageFilterTypeDTO filterQueryType,
                                                      int limit,
                                                      SeekDirectionDTO seekDirection,
                                                      @Nullable String keySerde,
                                                      @Nullable String valueSerde) {

    var deserializer = deserializationService.deserializerFor(cluster, topic, keySerde, valueSerde);
    var filter = getMsgFilter(query, filterQueryType);
    var emitter = switch (seekDirection) {
      case FORWARD -> new ForwardEmitter(
          () -> consumerGroupService.createConsumer(cluster),
          consumerPosition, limit, deserializer, filter, cluster.getPollingSettings()
      );
      case BACKWARD -> new BackwardEmitter(
          () -> consumerGroupService.createConsumer(cluster),
          consumerPosition, limit, deserializer, filter, cluster.getPollingSettings()
      );
      case TAILING -> new TailingEmitter(
          () -> consumerGroupService.createConsumer(cluster),
          consumerPosition, deserializer, filter, cluster.getPollingSettings()
      );
    };
    return Flux.create(emitter)
        .map(throttleUiPublish(seekDirection));
  }

  private Predicate<TopicMessageDTO> getMsgFilter(String query,
                                                  MessageFilterTypeDTO filterQueryType) {
    if (StringUtils.isEmpty(query)) {
      return evt -> true;
    }
    return MessageFilters.createMsgFilter(query, filterQueryType);
  }

  private <T> UnaryOperator<T> throttleUiPublish(SeekDirectionDTO seekDirection) {
    if (seekDirection == SeekDirectionDTO.TAILING) {
      RateLimiter rateLimiter = RateLimiter.create(TAILING_UI_MESSAGE_THROTTLE_RATE);
      return m -> {
        rateLimiter.acquire(1);
        return m;
      };
    }
    // there is no need to throttle UI production rate for non-tailing modes, since max number of produced
    // messages is limited for them (with page size)
    return UnaryOperator.identity();
  }

}
