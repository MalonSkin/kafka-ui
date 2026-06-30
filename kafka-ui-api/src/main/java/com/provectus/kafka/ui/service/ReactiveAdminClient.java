package com.provectus.kafka.ui.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.provectus.kafka.ui.exception.IllegalEntityStateException;
import com.provectus.kafka.ui.exception.NotFoundException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.util.KafkaVersion;
import com.provectus.kafka.ui.util.annotation.KafkaClientInternalsDependant;
import java.io.Closeable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitionReassignment;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.ProducerState;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.TopicPartitionReplica;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.ClusterAuthorizationException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.SecurityDisabledException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.requests.DescribeLogDirsResponse;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;


/**
 * 响应式 AdminClient 封装类。
 *
 * <p>将 Kafka 原生 {@link AdminClient} 的阻塞式 API 转换为基于 Reactor 的响应式 API，
 * 提供对 Kafka 集群管理操作的非阻塞访问，包括：Topic 管理、消费者组管理、ACL 管理、
 * Broker 配置管理、偏移量查询等。</p>
 *
 * <p>主要特性：</p>
 * <ul>
 *   <li>自动检测 Kafka 版本并启用对应的功能特性（如增量配置变更）</li>
 *   <li>大批量操作自动分批执行，避免 AdminClient 超时</li>
 *   <li>将 KafkaFuture 转换为 Mono，支持取消和错误处理</li>
 *   <li>过滤不支持的异常，保证调用方的稳定性</li>
 * </ul>
 *
 * @see AdminClient
 * @see Mono
 */
@Slf4j
@AllArgsConstructor
public class ReactiveAdminClient implements Closeable {

  /**
   * Kafka 集群支持的功能特性枚举。
   *
   * <p>根据 Kafka 版本号或运行时检测，判断集群是否支持特定功能，
   * 如增量配置变更、配置文档检索、ACL 安全等。</p>
   */
  public enum SupportedFeature {
    /** 增量配置变更（Kafka 2.3+） */
    INCREMENTAL_ALTER_CONFIGS(2.3f),
    /** 配置文档检索（Kafka 2.6+） */
    CONFIG_DOCUMENTATION_RETRIEVAL(2.6f),
    /** 描述集群时包含授权操作信息（Kafka 2.3+） */
    DESCRIBE_CLUSTER_INCLUDE_AUTHORIZED_OPERATIONS(2.3f),
    /** ACL 授权安全是否启用（运行时检测） */
    AUTHORIZED_SECURITY_ENABLED(ReactiveAdminClient::isAuthorizedSecurityEnabled);

    /** 功能特性判断函数，接收 AdminClient 和 Kafka 版本号，返回是否支持 */
    private final BiFunction<AdminClient, Float, Mono<Boolean>> predicate;

    SupportedFeature(BiFunction<AdminClient, Float, Mono<Boolean>> predicate) {
      this.predicate = predicate;
    }

    /**
     * 基于最低版本号的功能特性构造函数。
     *
     * @param fromVersion 支持该功能的最低 Kafka 版本号
     */
    SupportedFeature(float fromVersion) {
      this.predicate = (admin, ver) -> Mono.just(ver != null && ver >= fromVersion);
    }

    /**
     * 根据 Kafka 版本号检测所有可用的功能特性。
     *
     * @param ac             AdminClient 实例
     * @param kafkaVersionStr Kafka 版本字符串
     * @return 当前集群支持的功能特性集合
     */
    static Mono<Set<SupportedFeature>> forVersion(AdminClient ac, String kafkaVersionStr) {
      @Nullable Float kafkaVersion = KafkaVersion.parse(kafkaVersionStr).orElse(null);
      return Flux.fromArray(SupportedFeature.values())
          .flatMap(f -> f.predicate.apply(ac, kafkaVersion).map(enabled -> Tuples.of(f, enabled)))
          .filter(Tuple2::getT2)
          .map(Tuple2::getT1)
          .collect(Collectors.toSet());
    }
  }

  /**
   * Kafka 集群描述信息。
   *
   * <p>包含集群控制器节点、集群 ID、所有 Broker 节点列表以及授权操作集合。</p>
   */
  @Value
  public static class ClusterDescription {
    /** 集群控制器节点，可能为 null */
    @Nullable
    Node controller;
    /** 集群唯一标识符 */
    String clusterId;
    /** 集群中所有 Broker 节点列表 */
    Collection<Node> nodes;
    /** 当前用户拥有的 ACL 授权操作集合，若 ACL 未启用则为 null */
    @Nullable // ACL 未启用时为 null
    Set<AclOperation> authorizedOperations;
  }

  /**
   * 集群配置相关信息记录。
   *
   * <p>包含 Kafka 版本号、支持的功能特性以及是否允许删除 Topic。
   * 该信息会被缓存并定期更新（默认 1 小时）。</p>
   */
  @Builder
  private record ConfigRelatedInfo(String version,
                                   Set<SupportedFeature> features,
                                   boolean topicDeletionIsAllowed) {

    /** 配置信息缓存有效期，默认 1 小时 */
    static final Duration UPDATE_DURATION = Duration.of(1, ChronoUnit.HOURS);

    /**
     * 从 AdminClient 提取集群配置信息。
     *
     * @param ac AdminClient 实例
     * @return 包含版本号、功能特性和 Topic 删除许可的配置信息
     */
    private static Mono<ConfigRelatedInfo> extract(AdminClient ac) {
      return ReactiveAdminClient.describeClusterImpl(ac, Set.of())
          .flatMap(desc -> {
            // 选择用于获取配置的节点（优先选择控制器节点）
            var targetNodeId = Optional.ofNullable(desc.controller)
                .map(Node::id)
                .orElse(desc.getNodes().iterator().next().id());
            return loadBrokersConfig(ac, List.of(targetNodeId))
                .map(map -> map.isEmpty() ? List.<ConfigEntry>of() : map.get(targetNodeId))
                .flatMap(configs -> {
                  String version = "1.0-UNKNOWN";
                  boolean topicDeletionEnabled = true;
                  for (ConfigEntry entry : configs) {
                    if (entry.name().contains("inter.broker.protocol.version")) {
                      version = entry.value();
                    }
                    if (entry.name().equals("delete.topic.enable")) {
                      topicDeletionEnabled = Boolean.parseBoolean(entry.value());
                    }
                  }
                  final String finalVersion = version;
                  final boolean finalTopicDeletionEnabled = topicDeletionEnabled;
                  return SupportedFeature.forVersion(ac, version)
                      .map(features -> new ConfigRelatedInfo(finalVersion, features, finalTopicDeletionEnabled));
                });
          })
          .cache(UPDATE_DURATION);
    }
  }

  /**
   * 工厂方法：创建 ReactiveAdminClient 实例。
   *
   * <p>异步提取集群配置信息（版本号、功能特性等），然后构建实例。</p>
   *
   * @param adminClient 原生 Kafka AdminClient 实例
   * @return 包含集群配置信息的 ReactiveAdminClient
   */
  public static Mono<ReactiveAdminClient> create(AdminClient adminClient) {
    Mono<ConfigRelatedInfo> configRelatedInfoMono = ConfigRelatedInfo.extract(adminClient);
    return configRelatedInfoMono.map(info -> new ReactiveAdminClient(adminClient, configRelatedInfoMono, info));
  }


  /**
   * 检测集群是否启用了 ACL 授权安全。
   *
   * <p>通过尝试列出 ACL 来判断：若抛出 SecurityDisabledException 等异常则表示未启用。</p>
   *
   * @param ac           AdminClient 实例
   * @param kafkaVersion Kafka 版本号（可为 null）
   * @return true 表示已启用 ACL 安全，false 表示未启用
   */
  private static Mono<Boolean> isAuthorizedSecurityEnabled(AdminClient ac, @Nullable Float kafkaVersion) {
    return toMono(ac.describeAcls(AclBindingFilter.ANY).values())
        .thenReturn(true)
        .doOnError(th -> !(th instanceof SecurityDisabledException)
                && !(th instanceof InvalidRequestException)
                && !(th instanceof UnsupportedVersionException),
            th -> log.debug("Error checking if security enabled", th))
        .onErrorReturn(false);
  }

  /**
   * 将 Kafka 的 KafkaFuture 转换为 Reactor 的 Mono。
   *
   * <p>注意事项：</p>
   * <ul>
   *   <li>如果 KafkaFuture 返回 null，则对应的 Mono 为空（Reactor 不支持 nullable 结果）</li>
   *   <li>自动解包 CompletionException/ExecutionException，直接暴露根本原因</li>
   *   <li>支持取消操作：Mono 取消时会同步取消底层的 KafkaFuture</li>
   *   <li>强制使用 {@link Schedulers#parallel()} 调度后续操作，避免阻塞 AdminClient 的单线程通信线程</li>
   * </ul>
   *
   * @param <T>    KafkaFuture 的结果类型
   * @param future 要转换的 KafkaFuture 实例
   * @return 转换后的 Mono 实例
   */
  public static <T> Mono<T> toMono(KafkaFuture<T> future) {
    return Mono.<T>create(sink -> future.whenComplete((res, ex) -> {
      if (ex != null) {
        // KafkaFuture 文档未明确说明使用哪种异常包装器
        // （文档说是 ExecutionException，但实际观察到 CompletionException，因此两者都检查）
        if (ex instanceof CompletionException || ex instanceof ExecutionException) {
          sink.error(ex.getCause()); // 解包异常，暴露根本原因
        } else {
          sink.error(ex);
        }
      } else {
        sink.success(res);
      }
    })).doOnCancel(() -> future.cancel(true))
        // AdminClient 使用单线程进行 Kafka 通信，
        // 默认情况下 Mono 上的所有下游操作（如 map(..)）都会在该线程上执行。
        // 如果某些下游操作意外阻塞，会导致 AdminClient 的其他请求卡住，从而引发超时异常。
        // 因此，显式设置 Scheduler 用于下游处理，避免阻塞 AdminClient 通信线程。
        .publishOn(Schedulers.parallel());
  }

  //---------------------------------------------------------------------------------

  /** 原生 Kafka AdminClient 实例（包级别可见，便于测试） */
  @Getter(AccessLevel.PACKAGE) // 包级别可见，便于测试
  private final AdminClient client;
  /** 集群配置信息的 Mono（用于定期更新） */
  private final Mono<ConfigRelatedInfo> configRelatedInfoMono;

  /** 当前缓存的集群配置信息（volatile 保证多线程可见性） */
  private volatile ConfigRelatedInfo configRelatedInfo;

  /**
   * 获取当前集群支持的功能特性集合。
   *
   * @return 支持的功能特性集合
   */
  public Set<SupportedFeature> getClusterFeatures() {
    return configRelatedInfo.features();
  }

  /**
   * 列出集群中的所有 Topic 名称。
   *
   * @param listInternal 是否包含内部 Topic（如 __consumer_offsets）
   * @return Topic 名称集合
   */
  public Mono<Set<String>> listTopics(boolean listInternal) {
    return toMono(client.listTopics(new ListTopicsOptions().listInternal(listInternal)).names());
  }

  /**
   * 删除指定的 Topic。
   *
   * @param topicName 要删除的 Topic 名称
   * @return 删除操作完成的 Mono
   */
  public Mono<Void> deleteTopic(String topicName) {
    return toMono(client.deleteTopics(List.of(topicName)).all());
  }

  /**
   * 获取 Kafka 集群的 inter.broker.protocol.version 版本号。
   *
   * @return 版本号字符串（如 "2.8"、"3.0"）
   */
  public String getVersion() {
    return configRelatedInfo.version();
  }

  /**
   * 检查集群是否允许删除 Topic。
   *
   * @return true 表示允许删除，false 表示不允许
   */
  public boolean isTopicDeletionEnabled() {
    return configRelatedInfo.topicDeletionIsAllowed();
  }

  /**
   * 更新内部缓存的集群配置信息。
   *
   * <p>如果控制器节点存在，则触发配置信息的异步刷新（版本号、功能特性等）。</p>
   *
   * @param controller 集群控制器节点（可为 null）
   * @return 更新操作完成的 Mono
   */
  public Mono<Void> updateInternalStats(@Nullable Node controller) {
    if (controller == null) {
      return Mono.empty();
    }
    return configRelatedInfoMono
        .doOnNext(info -> this.configRelatedInfo = info)
        .then();
  }

  /**
   * 获取所有 Topic 的配置信息（包括内部 Topic）。
   *
   * @return Topic 名称到配置项列表的映射
   */
  public Mono<Map<String, List<ConfigEntry>>> getTopicsConfig() {
    return listTopics(true).flatMap(topics -> getTopicsConfig(topics, false));
  }

  /**
   * 获取指定 Topic 的配置信息。
   *
   * <p>注意：会跳过不存在的 Topic（抛出 UnknownTopicOrPartitionException 的）
   * 以及没有 DESCRIBE_CONFIGS 权限的 Topic（抛出 TopicAuthorizationException 的）。</p>
   *
   * @param topicNames 要查询的 Topic 名称集合
   * @param includeDoc 是否包含配置项的文档说明（需要 Kafka 2.6+）
   * @return Topic 名称到配置项列表的映射
   */
  public Mono<Map<String, List<ConfigEntry>>> getTopicsConfig(Collection<String> topicNames, boolean includeDoc) {
    var includeDocFixed = includeDoc && getClusterFeatures().contains(SupportedFeature.CONFIG_DOCUMENTATION_RETRIEVAL);
    // 需要分批调用，因为大量 Topic 可能导致 AdminClient 超时
    return partitionCalls(
        topicNames,
        200,
        part -> getTopicsConfigImpl(part, includeDocFixed),
        mapMerger()
    );
  }

  private Mono<Map<String, List<ConfigEntry>>> getTopicsConfigImpl(Collection<String> topicNames, boolean includeDoc) {
    List<ConfigResource> resources = topicNames.stream()
        .map(topicName -> new ConfigResource(ConfigResource.Type.TOPIC, topicName))
        .collect(toList());

    return toMonoWithExceptionFilter(
        client.describeConfigs(
            resources,
            new DescribeConfigsOptions().includeSynonyms(true).includeDocumentation(includeDoc)).values(),
        UnknownTopicOrPartitionException.class,
        TopicAuthorizationException.class
    ).map(config -> config.entrySet().stream()
        .collect(toMap(
            c -> c.getKey().name(),
            c -> List.copyOf(c.getValue().entries()))));
  }

  /**
   * 加载指定 Broker 的配置信息（静态方法，用于内部调用）。
   *
   * <p>支持多种 Kafka 兼容后端（MSK Serverless、Azure Event Hub 等），
   * 遇到不支持的异常时返回空 Map。</p>
   *
   * @param client    AdminClient 实例
   * @param brokerIds Broker ID 列表
   * @return Broker ID 到配置项列表的映射
   */
  private static Mono<Map<Integer, List<ConfigEntry>>> loadBrokersConfig(AdminClient client, List<Integer> brokerIds) {
    List<ConfigResource> resources = brokerIds.stream()
        .map(brokerId -> new ConfigResource(ConfigResource.Type.BROKER, Integer.toString(brokerId)))
        .collect(toList());
    return toMono(client.describeConfigs(resources).all())
        // 某些 Kafka 后端不支持 Broker 配置检索，
        // 调用 describeConfigs() 时会抛出各种异常
        .onErrorResume(th -> th instanceof InvalidRequestException // MSK Serverless 异常
                || th instanceof UnknownTopicOrPartitionException, // Azure Event Hub 异常
            th -> {
              log.trace("Error while getting configs for brokers {}", brokerIds, th);
              return Mono.just(Map.of());
            })
        // kafka-ui 用户可能没有集群的 DESCRIBE_CONFIGS 权限
        .onErrorResume(ClusterAuthorizationException.class, th -> {
          log.trace("AuthorizationException while getting configs for brokers {}", brokerIds, th);
          return Mono.just(Map.of());
        })
        // 捕获所有剩余异常，以 WARN 级别记录日志
        .onErrorResume(th -> true, th -> {
          log.warn("Unexpected error while getting configs for brokers {}", brokerIds, th);
          return Mono.just(Map.of());
        })
        .map(config -> config.entrySet().stream()
            .collect(toMap(
                c -> Integer.valueOf(c.getKey().name()),
                c -> new ArrayList<>(c.getValue().entries()))));
  }

  /**
   * 加载指定 Broker 的配置信息。
   *
   * <p>若 Broker 不支持配置检索（如 MSK Serverless、Azure Event Hub 等），
   * 或当前用户无权限，则返回空 Map。</p>
   *
   * @param brokerIds Broker ID 列表
   * @return Broker ID 到配置项列表的映射
   */
  public Mono<Map<Integer, List<ConfigEntry>>> loadBrokersConfig(List<Integer> brokerIds) {
    return loadBrokersConfig(client, brokerIds);
  }

  /**
   * 获取所有 Topic（包括内部 Topic）的描述信息。
   *
   * @return Topic 名称到描述信息的映射
   */
  public Mono<Map<String, TopicDescription>> describeTopics() {
    return listTopics(true).flatMap(this::describeTopics);
  }

  /**
   * 获取指定 Topic 的描述信息。
   *
   * <p>大批量 Topic 会自动分批请求（每批 200 个），避免 AdminClient 超时。</p>
   *
   * @param topics Topic 名称集合
   * @return Topic 名称到描述信息的映射
   */
  public Mono<Map<String, TopicDescription>> describeTopics(Collection<String> topics) {
    // 需要分批调用，因为大量 Topic 可能导致 AdminClient 超时
    return partitionCalls(
        topics,
        200,
        this::describeTopicsImpl,
        mapMerger()
    );
  }

  private Mono<Map<String, TopicDescription>> describeTopicsImpl(Collection<String> topics) {
    return toMonoWithExceptionFilter(
        client.describeTopics(topics).topicNameValues(),
        UnknownTopicOrPartitionException.class,
        // 我们只描述从 listTopics() API 获取到的 Topic，因此应该有权限执行此操作，
        // 但也添加此异常处理，以防在调用 listTopics() 后权限被限制的罕见情况
        TopicAuthorizationException.class
    );
  }

  /**
   * 获取单个 Topic 的描述信息。
   *
   * <p>如果 Topic 不存在或不可见，返回空 Mono。</p>
   *
   * @param topic Topic 名称
   * @return Topic 描述信息，若不存在则为空 Mono
   */
  public Mono<TopicDescription> describeTopic(String topic) {
    return describeTopics(List.of(topic)).flatMap(m -> Mono.justOrEmpty(m.get(topic)));
  }

  /**
   * 将 KafkaFuture 的 Map 响应转换为 Mono，并过滤指定类型的异常。
   *
   * <p>Kafka API 通常返回包含 KafkaFuture 值的 Map 响应。如果使用 allOf() 逻辑，
   * 任何一个 Future 失败都会导致整体 Mono 失败。但在某些场景下这不是期望的行为，
   * 例如调用 describeTopics(List names) 时，未知 Topic 会抛出 UnknownTopicOrPartitionException，
   * 我们希望将这些 Topic 从结果 Map 中过滤掉，而不是让整个请求失败。</p>
   *
   * <p>此方法将输入 Map 转换为 Mono&lt;Map&gt;，忽略 KafkaFuture 以 <code>classes</code>
   * 中指定的异常类型完成的条目，以及空 Mono。</p>
   *
   * @param <K>      Map 键类型
   * @param <V>      Map 值类型
   * @param values   包含 KafkaFuture 的输入 Map
   * @param classes  要过滤的异常类型列表
   * @return 过滤异常后的结果 Mono
   */
  @SafeVarargs
  static <K, V> Mono<Map<K, V>> toMonoWithExceptionFilter(Map<K, KafkaFuture<V>> values,
                                                          Class<? extends KafkaException>... classes) {
    if (values.isEmpty()) {
      return Mono.just(Map.of());
    }

    List<Mono<Tuple2<K, Optional<V>>>> monos = values.entrySet().stream()
        .map(e ->
            toMono(e.getValue())
                .map(r -> Tuples.of(e.getKey(), Optional.of(r)))
                .defaultIfEmpty(Tuples.of(e.getKey(), Optional.empty())) // 追踪空 Mono
                .onErrorResume(
                    // 追踪可抑制异常的 Mono
                    th -> Stream.of(classes).anyMatch(clazz -> th.getClass().isAssignableFrom(clazz)),
                    th -> Mono.just(Tuples.of(e.getKey(), Optional.empty()))))
        .toList();

    return Mono.zip(
        monos,
        resultsArr -> Stream.of(resultsArr)
            .map(obj -> (Tuple2<K, Optional<V>>) obj)
            .filter(t -> t.getT2().isPresent()) // 跳过空结果和可抑制的异常
            .collect(Collectors.toMap(Tuple2::getT1, t -> t.getT2().get()))
    );
  }

  /**
   * 获取所有 Broker 的日志目录信息。
   *
   * @return Broker ID 到日志目录信息的映射
   */
  public Mono<Map<Integer, Map<String, DescribeLogDirsResponse.LogDirInfo>>> describeLogDirs() {
    return describeCluster()
        .map(d -> d.getNodes().stream().map(Node::id).collect(toList()))
        .flatMap(this::describeLogDirs);
  }

  /**
   * 获取指定 Broker 的日志目录信息。
   *
   * <p>若集群不支持该 API 或无权限，返回空 Map。</p>
   *
   * @param brokerIds Broker ID 集合
   * @return Broker ID 到日志目录信息的映射
   */
  public Mono<Map<Integer, Map<String, DescribeLogDirsResponse.LogDirInfo>>> describeLogDirs(
      Collection<Integer> brokerIds) {
    return toMono(client.describeLogDirs(brokerIds).all())
        .onErrorResume(UnsupportedVersionException.class, th -> Mono.just(Map.of()))
        .onErrorResume(ClusterAuthorizationException.class, th -> Mono.just(Map.of()))
        .onErrorResume(th -> true, th -> {
          log.warn("Error while calling describeLogDirs", th);
          return Mono.just(Map.of());
        });
  }

  /**
   * 获取集群描述信息（控制器、节点列表、授权操作等）。
   *
   * @return 集群描述信息
   */
  public Mono<ClusterDescription> describeCluster() {
    return describeClusterImpl(client, getClusterFeatures());
  }

  private static Mono<ClusterDescription> describeClusterImpl(AdminClient client, Set<SupportedFeature> features) {
    boolean includeAuthorizedOperations =
        features.contains(SupportedFeature.DESCRIBE_CLUSTER_INCLUDE_AUTHORIZED_OPERATIONS);
    DescribeClusterResult result = client.describeCluster(
        new DescribeClusterOptions().includeAuthorizedOperations(includeAuthorizedOperations));
    var allOfFuture = KafkaFuture.allOf(
        result.controller(), result.clusterId(), result.nodes(), result.authorizedOperations());
    return toMono(allOfFuture).then(
        Mono.fromCallable(() ->
          new ClusterDescription(
            result.controller().get(),
            result.clusterId().get(),
            result.nodes().get(),
            result.authorizedOperations().get()
          )
        )
    );
  }

  /**
   * 删除指定的消费者组。
   *
   * @param groupIds 要删除的消费者组 ID 集合
   * @return 删除操作完成的 Mono
   * @throws NotFoundException          如果消费者组不存在
   * @throws IllegalEntityStateException 如果消费者组不为空（仍有成员）
   */
  public Mono<Void> deleteConsumerGroups(Collection<String> groupIds) {
    return toMono(client.deleteConsumerGroups(groupIds).all())
        .onErrorResume(GroupIdNotFoundException.class,
            th -> Mono.error(new NotFoundException("The group id does not exist")))
        .onErrorResume(GroupNotEmptyException.class,
            th -> Mono.error(new IllegalEntityStateException("The group is not empty")));
  }

  /**
   * 创建新的 Topic。
   *
   * @param name              Topic 名称
   * @param numPartitions     分区数
   * @param replicationFactor 副本因子（可为 null，使用集群默认值）
   * @param configs           Topic 配置键值对
   * @return 创建操作完成的 Mono
   */
  public Mono<Void> createTopic(String name,
                                int numPartitions,
                                @Nullable Integer replicationFactor,
                                Map<String, String> configs) {
    var newTopic = new NewTopic(
        name,
        Optional.of(numPartitions),
        Optional.ofNullable(replicationFactor).map(Integer::shortValue)
    ).configs(configs);
    return toMono(client.createTopics(List.of(newTopic)).all());
  }

  /**
   * 修改分区重分配（用于分区副本迁移）。
   *
   * @param reassignments 分区到新分配的映射（Optional.empty 表示取消正在进行的重分配）
   * @return 操作完成的 Mono
   */
  public Mono<Void> alterPartitionReassignments(
      Map<TopicPartition, Optional<NewPartitionReassignment>> reassignments) {
    return toMono(client.alterPartitionReassignments(reassignments).all());
  }

  /**
   * 为指定 Topic 增加分区数。
   *
   * @param newPartitionsMap Topic 名称到新分区配置的映射
   * @return 操作完成的 Mono
   */
  public Mono<Void> createPartitions(Map<String, NewPartitions> newPartitionsMap) {
    return toMono(client.createPartitions(newPartitionsMap).all());
  }


  /**
   * 更新 Topic 的配置。
   *
   * <p>注意：会用新配置整体替换当前配置。旧配置中存在但新配置中缺失的条目将被重置为默认值。
   * 若集群支持增量配置变更（Kafka 2.3+），则使用增量方式更新；否则使用全量替换。</p>
   *
   * @param topicName Topic 名称
   * @param configs   新的配置键值对
   * @return 更新操作完成的 Mono
   */
  public Mono<Void> updateTopicConfig(String topicName, Map<String, String> configs) {
    if (getClusterFeatures().contains(SupportedFeature.INCREMENTAL_ALTER_CONFIGS)) {
      return getTopicsConfigImpl(List.of(topicName), false)
          .map(conf -> conf.getOrDefault(topicName, List.of()))
          .flatMap(currentConfigs -> incrementalAlterConfig(topicName, currentConfigs, configs));
    } else {
      return alterConfig(topicName, configs);
    }
  }

  /**
   * 列出所有消费者组的名称。
   *
   * @return 消费者组名称列表
   */
  public Mono<List<String>> listConsumerGroupNames() {
    return listConsumerGroups().map(lst -> lst.stream().map(ConsumerGroupListing::groupId).toList());
  }

  /**
   * 列出所有消费者组的详细信息。
   *
   * @return 消费者组列表信息集合
   */
  public Mono<Collection<ConsumerGroupListing>> listConsumerGroups() {
    return toMono(client.listConsumerGroups().all());
  }

  /**
   * 获取指定消费者组的描述信息。
   *
   * <p>大批量消费者组会自动分批请求（每批 25 个，并发 4 个）。</p>
   *
   * @param groupIds 消费者组 ID 集合
   * @return 消费者组 ID 到描述信息的映射
   */
  public Mono<Map<String, ConsumerGroupDescription>> describeConsumerGroups(Collection<String> groupIds) {
    return partitionCalls(
        groupIds,
        25,
        4,
        ids -> toMono(client.describeConsumerGroups(ids).all()),
        mapMerger()
    );
  }

  /**
   * 列出消费者组的已提交偏移量。
   *
   * <p>返回三元组表结构：消费者组名 -> TopicPartition -> 偏移量。
   * 没有已提交偏移量的分区将被跳过。</p>
   *
   * @param consumerGroups 消费者组名称列表
   * @param partitions     要查询的分区列表（null 表示查询所有分区）
   * @return 消费者组 -> 分区 -> 偏移量的三元组表
   */
  public Mono<Table<String, TopicPartition, Long>> listConsumerGroupOffsets(List<String> consumerGroups,
                                                                            // 传入 null 表示查询所有分区
                                                                            @Nullable List<TopicPartition> partitions) {
    Function<Collection<String>, Mono<Map<String, Map<TopicPartition, OffsetAndMetadata>>>> call =
        groups -> toMono(
            client.listConsumerGroupOffsets(
                groups.stream()
                    .collect(Collectors.toMap(
                        g -> g,
                        g -> new ListConsumerGroupOffsetsSpec().topicPartitions(partitions)
                    ))).all()
        );

    Mono<Map<String, Map<TopicPartition, OffsetAndMetadata>>> merged = partitionCalls(
        consumerGroups,
        25,
        4,
        call,
        mapMerger()
    );

    return merged.map(map -> {
      var table = ImmutableTable.<String, TopicPartition, Long>builder();
      map.forEach((g, tpOffsets) -> tpOffsets.forEach((tp, offset) -> {
        if (offset != null) {
          // 对于该消费者组没有已提交偏移量的分区，offset 为 null
          table.put(g, tp, offset.offset());
        }
      }));
      return table.build();
    });
  }

  /**
   * 修改消费者组的已提交偏移量。
   *
   * @param groupId 消费者组 ID
   * @param offsets 分区到新偏移量的映射
   * @return 操作完成的 Mono
   */
  public Mono<Void> alterConsumerGroupOffsets(String groupId, Map<TopicPartition, Long> offsets) {
    return toMono(client.alterConsumerGroupOffsets(
            groupId,
            offsets.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue()))))
        .all());
  }

  /**
   * 列出指定 Topic 所有分区的偏移量。
   *
   * @param topic              Topic 名称
   * @param offsetSpec         偏移量规格（最新、最早或指定时间戳）
   * @param failOnUnknownLeader true - 无 Leader 的分区抛出异常，false - 跳过无 Leader 的分区
   * @return 分区到偏移量的映射
   */
  public Mono<Map<TopicPartition, Long>> listTopicOffsets(String topic,
                                                          OffsetSpec offsetSpec,
                                                          boolean failOnUnknownLeader) {
    return describeTopic(topic)
        .map(td -> filterPartitionsWithLeaderCheck(List.of(td), p -> true, failOnUnknownLeader))
        .flatMap(partitions -> listOffsetsUnsafe(partitions, offsetSpec));
  }

  /**
   * 列出指定分区集合的偏移量。
   *
   * @param partitions         分区集合
   * @param offsetSpec         偏移量规格（最新、最早或指定时间戳）
   * @param failOnUnknownLeader true - 无 Leader 的分区抛出异常，false - 跳过无 Leader 的分区
   * @return 分区到偏移量的映射
   */
  public Mono<Map<TopicPartition, Long>> listOffsets(Collection<TopicPartition> partitions,
                                                     OffsetSpec offsetSpec,
                                                     boolean failOnUnknownLeader) {
    return filterPartitionsWithLeaderCheck(partitions, failOnUnknownLeader)
        .flatMap(parts -> listOffsetsUnsafe(parts, offsetSpec));
  }

  /**
   * 列出指定 Topic 描述信息对应分区的偏移量，自动跳过无 Leader 的分区。
   *
   * @param topicDescriptions Topic 描述信息集合
   * @param offsetSpec        偏移量规格（最新、最早或指定时间戳）
   * @return 分区到偏移量的映射
   */
  public Mono<Map<TopicPartition, Long>> listOffsets(Collection<TopicDescription> topicDescriptions,
                                                     OffsetSpec offsetSpec) {
    return listOffsetsUnsafe(filterPartitionsWithLeaderCheck(topicDescriptions, p -> true, false), offsetSpec);
  }

  private Mono<Collection<TopicPartition>> filterPartitionsWithLeaderCheck(Collection<TopicPartition> partitions,
                                                                           boolean failOnUnknownLeader) {
    var targetTopics = partitions.stream().map(TopicPartition::topic).collect(Collectors.toSet());
    return describeTopicsImpl(targetTopics)
        .map(descriptions ->
            filterPartitionsWithLeaderCheck(
                descriptions.values(), partitions::contains, failOnUnknownLeader));
  }

  @VisibleForTesting
  static Set<TopicPartition> filterPartitionsWithLeaderCheck(Collection<TopicDescription> topicDescriptions,
                                                              Predicate<TopicPartition> partitionPredicate,
                                                              boolean failOnUnknownLeader) {
    var goodPartitions = new HashSet<TopicPartition>();
    for (TopicDescription description : topicDescriptions) {
      var goodTopicPartitions = new ArrayList<TopicPartition>();
      for (TopicPartitionInfo partitionInfo : description.partitions()) {
        TopicPartition topicPartition = new TopicPartition(description.name(), partitionInfo.partition());
        if (partitionInfo.leader() == null) {
          if (failOnUnknownLeader) {
            throw new ValidationException(String.format("Topic partition %s has no leader", topicPartition));
          } else {
            // 如果 Topic 的任何一个分区没有 Leader，则跳过该 Topic 的所有分区
            goodTopicPartitions.clear();
            break;
          }
        }
        if (partitionPredicate.test(topicPartition)) {
          goodTopicPartitions.add(topicPartition);
        }
      }
      goodPartitions.addAll(goodTopicPartitions);
    }
    return goodPartitions;
  }

  // 1. 注意(!): 仅应应用于所有分区都有 Leader 的 Topic 的分区，
  //    否则 AdminClient 会尝试获取 Topic 元数据，失败后无限重试（直到超时）
  // 2. 注意(!): 跳过尚未初始化的分区
  //    （会抛出 UnknownTopicOrPartitionException，例如 Topic 创建后）
  // 3. TODO: 检查 AdminClient 不抛出 LeaderNotAvailableException 而是不断重试是否是 bug
  @KafkaClientInternalsDependant
  @VisibleForTesting
  Mono<Map<TopicPartition, Long>> listOffsetsUnsafe(Collection<TopicPartition> partitions, OffsetSpec offsetSpec) {
    if (partitions.isEmpty()) {
      return Mono.just(Map.of());
    }

    Function<Collection<TopicPartition>, Mono<Map<TopicPartition, Long>>> call =
        parts -> {
          ListOffsetsResult r = client.listOffsets(parts.stream().collect(toMap(tp -> tp, tp -> offsetSpec)));
          Map<TopicPartition, KafkaFuture<ListOffsetsResultInfo>> perPartitionResults = new HashMap<>();
          parts.forEach(p -> perPartitionResults.put(p, r.partitionResult(p)));

          return toMonoWithExceptionFilter(perPartitionResults, UnknownTopicOrPartitionException.class)
              .map(offsets -> offsets.entrySet().stream()
                  // 过滤未找到偏移量的分区
                  .filter(e -> e.getValue().offset() >= 0)
                  .collect(toMap(Map.Entry::getKey, e -> e.getValue().offset())));
        };

    return partitionCalls(
        partitions,
        200,
        call,
        mapMerger()
    );
  }

  /**
   * 列出符合过滤条件的 ACL 绑定。
   *
   * <p>要求集群已启用 ACL 安全，否则会抛出异常。</p>
   *
   * @param filter 资源模式过滤器
   * @return 匹配的 ACL 绑定集合
   */
  public Mono<Collection<AclBinding>> listAcls(ResourcePatternFilter filter) {
    Preconditions.checkArgument(getClusterFeatures().contains(SupportedFeature.AUTHORIZED_SECURITY_ENABLED));
    return toMono(client.describeAcls(new AclBindingFilter(filter, AccessControlEntryFilter.ANY)).values());
  }

  /**
   * 创建 ACL（访问控制列表）绑定规则。
   *
   * <p>要求集群已启用 ACL 安全功能，否则会抛出异常。</p>
   *
   * @param aclBindings 要创建的 ACL 绑定集合
   * @return 创建操作完成的 Mono
   */
  public Mono<Void> createAcls(Collection<AclBinding> aclBindings) {
    Preconditions.checkArgument(getClusterFeatures().contains(SupportedFeature.AUTHORIZED_SECURITY_ENABLED));
    return toMono(client.createAcls(aclBindings).all());
  }

  /**
   * 删除 ACL（访问控制列表）绑定规则。
   *
   * <p>将传入的 ACL 绑定转换为过滤器后执行删除。
   * 要求集群已启用 ACL 安全功能。</p>
   *
   * @param aclBindings 要删除的 ACL 绑定集合
   * @return 删除操作完成的 Mono
   */
  public Mono<Void> deleteAcls(Collection<AclBinding> aclBindings) {
    Preconditions.checkArgument(getClusterFeatures().contains(SupportedFeature.AUTHORIZED_SECURITY_ENABLED));
    var filters = aclBindings.stream().map(AclBinding::toFilter).collect(Collectors.toSet());
    return toMono(client.deleteAcls(filters).all()).then();
  }

  /**
   * 更新指定 Broker 的配置项。
   *
   * <p>使用增量配置变更 API（incrementalAlterConfigs）设置单个配置项的值。</p>
   *
   * @param brokerId Broker ID
   * @param name     配置项名称
   * @param value    配置项的新值
   * @return 更新操作完成的 Mono
   */
  public Mono<Void> updateBrokerConfigByName(Integer brokerId, String name, String value) {
    ConfigResource cr = new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(brokerId));
    AlterConfigOp op = new AlterConfigOp(new ConfigEntry(name, value), AlterConfigOp.OpType.SET);
    return toMono(client.incrementalAlterConfigs(Map.of(cr, List.of(op))).all());
  }

  /**
   * 删除指定分区中偏移量小于给定值的记录。
   *
   * @param offsets 分区到截止偏移量的映射（删除该偏移量之前的所有记录）
   * @return 删除操作完成的 Mono
   */
  public Mono<Void> deleteRecords(Map<TopicPartition, Long> offsets) {
    var records = offsets.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), RecordsToDelete.beforeOffset(entry.getValue())))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    return toMono(client.deleteRecords(records).all());
  }

  /**
   * 修改分区副本的日志目录位置。
   *
   * @param replicaAssignment 副本到目标日志目录的映射
   * @return 操作完成的 Mono
   */
  public Mono<Void> alterReplicaLogDirs(Map<TopicPartitionReplica, String> replicaAssignment) {
    return toMono(client.alterReplicaLogDirs(replicaAssignment).all());
  }

  /**
   * 获取指定 Topic 各分区的活跃生产者状态信息。
   *
   * <p>仅返回存在活跃生产者的分区，没有活跃生产者的分区将被过滤。</p>
   *
   * @param topic Topic 名称
   * @return 分区到活跃生产者状态列表的映射
   */
  public Mono<Map<TopicPartition, List<ProducerState>>> getActiveProducersState(String topic) {
    return describeTopic(topic)
        .map(td -> client.describeProducers(
                IntStream.range(0, td.partitions().size())
                    .mapToObj(i -> new TopicPartition(topic, i))
                    .toList()
            ).all()
        )
        .flatMap(ReactiveAdminClient::toMono)
        .map(map -> map.entrySet().stream()
            .filter(e -> !e.getValue().activeProducers().isEmpty()) // 跳过没有活跃生产者的分区
            .collect(toMap(Map.Entry::getKey, e -> e.getValue().activeProducers())));
  }

  /**
   * 使用增量方式更新 Topic 配置（Kafka 2.3+）。
   *
   * <p>对比当前配置与新配置：</p>
   * <ul>
   *   <li>新配置中存在但当前配置中不存在的条目 → SET 操作</li>
   *   <li>当前配置中存在（且为 DYNAMIC_TOPIC_CONFIG 来源）但新配置中不存在的条目 → DELETE 操作（重置为默认值）</li>
   * </ul>
   *
   * @param topicName      Topic 名称
   * @param currentConfigs 当前 Topic 的配置列表
   * @param newConfigs     要设置的新配置键值对
   * @return 更新操作完成的 Mono
   */
  private Mono<Void> incrementalAlterConfig(String topicName,
                                            List<ConfigEntry> currentConfigs,
                                            Map<String, String> newConfigs) {
    var configsToDelete = currentConfigs.stream()
        .filter(e -> e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG) //manually set configs only
        .filter(e -> !newConfigs.containsKey(e.name()))
        .map(e -> new AlterConfigOp(e, AlterConfigOp.OpType.DELETE));

    var configsToSet = newConfigs.entrySet().stream()
        .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET));

    return toMono(client.incrementalAlterConfigs(
        Map.of(
            new ConfigResource(ConfigResource.Type.TOPIC, topicName),
            Stream.concat(configsToDelete, configsToSet).toList()
        )).all());
  }

  /**
   * 使用全量替换方式更新 Topic 配置（旧版 API，Kafka 2.3 以下使用）。
   *
   * <p>注意：此方法会用新配置完全替换当前配置，
   * 旧配置中存在但新配置中缺失的条目将被重置为默认值。</p>
   *
   * @param topicName Topic 名称
   * @param configs   新的配置键值对
   * @return 更新操作完成的 Mono
   */
  @SuppressWarnings("deprecation")
  private Mono<Void> alterConfig(String topicName, Map<String, String> configs) {
    List<ConfigEntry> configEntries = configs.entrySet().stream()
        .flatMap(cfg -> Stream.of(new ConfigEntry(cfg.getKey(), cfg.getValue())))
        .collect(toList());
    Config config = new Config(configEntries);
    var topicResource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
    return toMono(client.alterConfigs(Map.of(topicResource, config)).all());
  }

  /**
   * 将输入集合分批处理，顺序执行并将结果合并为单个 Mono。
   *
   * <p>用于避免大批量操作导致 AdminClient 超时。每批按指定大小拆分，
   * 依次订阅执行，最终通过 merger 函数合并各批结果。</p>
   *
   * @param <R>          结果类型
   * @param <I>          输入元素类型
   * @param items        待处理的输入集合
   * @param partitionSize 每批的大小
   * @param call         将一批输入转换为 Mono 的函数
   * @param merger       合并两个结果的函数
   * @return 合并后的结果 Mono
   */
  private static <R, I> Mono<R> partitionCalls(Collection<I> items,
                                               int partitionSize,
                                               Function<Collection<I>, Mono<R>> call,
                                               BiFunction<R, R, R> merger) {
    if (items.isEmpty()) {
      return call.apply(items);
    }
    Iterable<List<I>> parts = Iterables.partition(items, partitionSize);
    return Flux.fromIterable(parts)
        .concatMap(call)
        .reduce(merger);
  }

  /**
   * 将输入集合分批处理，以指定并发度并行执行并将结果合并为单个 Mono。
   *
   * <p>与顺序版本的 {@link #partitionCalls(Collection, int, Function, BiFunction)} 不同，
   * 此方法允许多个批次同时执行，通过 concurrency 参数控制并发级别，
   * 适用于对延迟敏感且批次之间无依赖的场景。</p>
   *
   * @param <R>          结果类型
   * @param <I>          输入元素类型
   * @param items        待处理的输入集合
   * @param partitionSize 每批的大小
   * @param concurrency  最大并发批次数
   * @param call         将一批输入转换为 Mono 的函数
   * @param merger       合并两个结果的函数
   * @return 合并后的结果 Mono
   */
  private static <R, I> Mono<R> partitionCalls(Collection<I> items,
                                               int partitionSize,
                                               int concurrency,
                                               Function<Collection<I>, Mono<R>> call,
                                               BiFunction<R, R, R> merger) {
    if (items.isEmpty()) {
      return call.apply(items);
    }
    Iterable<List<I>> parts = Iterables.partition(items, partitionSize);
    return Flux.fromIterable(parts)
        .flatMap(call, concurrency)
        .reduce(merger);
  }

  /**
   * 返回一个 Map 合并函数，将两个 Map 的键值对合并到一个新的 HashMap 中。
   *
   * <p>如果两个 Map 中存在相同的键，后者的值将覆盖前者的值。</p>
   *
   * @param <K> Map 键类型
   * @param <V> Map 值类型
   * @return Map 合并函数
   */
  private static <K, V> BiFunction<Map<K, V>, Map<K, V>, Map<K, V>> mapMerger() {
    return (m1, m2) -> {
      var merged = new HashMap<K, V>();
      merged.putAll(m1);
      merged.putAll(m2);
      return merged;
    };
  }

  /**
   * 关闭底层的 Kafka AdminClient，释放所有网络连接和系统资源。
   */
  @Override
  public void close() {
    client.close();
  }
}
