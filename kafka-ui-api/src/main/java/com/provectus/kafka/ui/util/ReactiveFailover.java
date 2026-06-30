package com.provectus.kafka.ui.util;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 响应式故障转移（Failover）工具类
 *
 * 提供对多个发布者（如 Kafka 集群、Schema Registry 等）的故障转移支持。
 * 当某个发布者调用失败时，自动切换到下一个可用的发布者，提高系统可用性。
 *
 * 核心机制：
 * - 维护一个发布者列表，按顺序尝试调用
 * - 失败的发布者会被标记为不可用，在宽限期（grace period）后自动恢复
 * - 支持 Mono 和 Flux 两种响应式调用方式
 * - 线程安全的发布者选择逻辑
 *
 * 使用场景：
 * - 多集群 Kafka 连接的故障转移
 * - Schema Registry 的高可用访问
 * - Kafka Connect / KSQL 的故障转移
 *
 * @param <T> 发布者类型（如 KafkaSrClientApi、KafkaConnectClientApi 等）
 * @author kafka-ui
 */
public class ReactiveFailover<T> {

  /** 默认重试宽限期：发布者失败后等待此时间再重试 */
  public static final Duration DEFAULT_RETRY_GRACE_PERIOD_MS = Duration.ofSeconds(5);

  /** 连接被拒绝异常的过滤器，用于识别 Connection refused 错误 */
  public static final Predicate<Throwable> CONNECTION_REFUSED_EXCEPTION_FILTER =
      error -> error.getCause() instanceof IOException && error.getCause().getMessage().contains("Connection refused");

  /** 发布者持有者列表，每个元素包装一个发布者实例 */
  private final List<PublisherHolder<T>> publishers;

  /** 当前活跃发布者的起始索引，用于轮询选择 */
  private int currentIndex = 0;

  /** 触发故障转移的异常谓词，只有匹配的异常才会触发切换 */
  private final Predicate<Throwable> failoverExceptionsPredicate;

  /** 所有发布者均不可用时的错误消息 */
  private final String noAvailablePublishersMsg;

  /**
   * 创建单发布者的故障转移实例（主要用于测试场景）
   *
   * @param publisher 单个发布者实例
   * @return 故障转移实例
   */
  // creates single-publisher failover (basically for tests usage)
  public static <T> ReactiveFailover<T> createNoop(T publisher) {
    return create(
        List.of(publisher),
        th -> true,
        "publisher is not available",
        DEFAULT_RETRY_GRACE_PERIOD_MS
    );
  }

  /**
   * 创建故障转移实例（直接传入发布者列表）
   *
   * @param publishers               发布者列表
   * @param failoverExeptionsPredicate 触发故障转移的异常谓词
   * @param noAvailablePublishersMsg 所有发布者不可用时的错误消息
   * @param retryGracePeriodMs       失败后重试的宽限期
   * @return 故障转移实例
   */
  public static <T> ReactiveFailover<T> create(List<T> publishers,
                                               Predicate<Throwable> failoverExeptionsPredicate,
                                               String noAvailablePublishersMsg,
                                               Duration retryGracePeriodMs) {
    return new ReactiveFailover<>(
        publishers.stream().map(p -> new PublisherHolder<>(() -> p, retryGracePeriodMs.toMillis())).toList(),
        failoverExeptionsPredicate,
        noAvailablePublishersMsg
    );
  }

  /**
   * 创建故障转移实例（通过工厂函数延迟创建发布者）
   *
   * 支持延迟初始化：发布者实例在首次调用时才通过工厂函数创建。
   *
   * @param args                     工厂函数的参数列表
   * @param factory                  从参数创建发布者的工厂函数
   * @param failoverExeptionsPredicate 触发故障转移的异常谓词
   * @param noAvailablePublishersMsg 所有发布者不可用时的错误消息
   * @param retryGracePeriodMs       失败后重试的宽限期
   * @return 故障转移实例
   */
  public static <T, A> ReactiveFailover<T> create(List<A> args,
                                                  Function<A, T> factory,
                                                  Predicate<Throwable> failoverExeptionsPredicate,
                                                  String noAvailablePublishersMsg,
                                                  Duration retryGracePeriodMs) {
    return new ReactiveFailover<>(
        args.stream().map(arg ->
            new PublisherHolder<>(() -> factory.apply(arg), retryGracePeriodMs.toMillis())).toList(),
        failoverExeptionsPredicate,
        noAvailablePublishersMsg
    );
  }

  /**
   * 私有构造函数，通过工厂方法创建实例
   *
   * @param publishers               发布者持有者列表
   * @param failoverExceptionsPredicate 触发故障转移的异常谓词
   * @param noAvailablePublishersMsg 所有发布者不可用时的错误消息
   */
  private ReactiveFailover(List<PublisherHolder<T>> publishers,
                   Predicate<Throwable> failoverExceptionsPredicate,
                   String noAvailablePublishersMsg) {
    Preconditions.checkArgument(!publishers.isEmpty());
    this.publishers = publishers;
    this.failoverExceptionsPredicate = failoverExceptionsPredicate;
    this.noAvailablePublishersMsg = noAvailablePublishersMsg;
  }

  /**
   * 执行 Mono 类型的故障转移调用
   *
   * 获取当前活跃的发布者列表，依次尝试执行操作。
   * 如果当前发布者失败且匹配故障转移条件，自动切换到下一个发布者重试。
   *
   * @param f   对发布者执行的操作函数
   * @param <V> 返回值类型
   * @return 操作结果的 Mono
   * @throws IllegalStateException 如果所有发布者均不可用
   */
  public <V> Mono<V> mono(Function<T, Mono<V>> f) {
    List<PublisherHolder<T>> candidates = getActivePublishers();
    if (candidates.isEmpty()) {
      return Mono.error(() -> new IllegalStateException(noAvailablePublishersMsg));
    }
    return mono(f, candidates);
  }

  /**
   * 递归执行 Mono 故障转移调用
   *
   * 从候选列表中取第一个发布者执行操作，失败时标记为不可用，
   * 并递归尝试剩余候选者。
   *
   * @param f          对发布者执行的操作函数
   * @param candidates 当前候选发布者列表
   * @return 操作结果的 Mono
   */
  private <V> Mono<V> mono(Function<T, Mono<V>> f, List<PublisherHolder<T>> candidates) {
    var publisher = candidates.get(0);
    return publisher.get()
        .flatMap(f)
        .onErrorResume(failoverExceptionsPredicate, th -> {
          publisher.markFailed();
          if (candidates.size() == 1) {
            return Mono.error(th);
          }
          // 过滤出仍然活跃的候选者，跳过当前失败的发布者
          var newCandidates = candidates.stream().skip(1).filter(PublisherHolder::isActive).toList();
          if (newCandidates.isEmpty()) {
            return Mono.error(th);
          }
          return mono(f, newCandidates);
        });
  }

  /**
   * 执行 Flux 类型的故障转移调用
   *
   * 获取当前活跃的发布者列表，依次尝试执行操作。
   * 如果当前发布者失败且匹配故障转移条件，自动切换到下一个发布者重试。
   *
   * @param f   对发布者执行的操作函数
   * @param <V> 返回值类型
   * @return 操作结果的 Flux
   * @throws IllegalStateException 如果所有发布者均不可用
   */
  public <V> Flux<V> flux(Function<T, Flux<V>> f) {
    List<PublisherHolder<T>> candidates = getActivePublishers();
    if (candidates.isEmpty()) {
      return Flux.error(() -> new IllegalStateException(noAvailablePublishersMsg));
    }
    return flux(f, candidates);
  }

  /**
   * 递归执行 Flux 故障转移调用
   *
   * 从候选列表中取第一个发布者执行操作，失败时标记为不可用，
   * 并递归尝试剩余候选者。
   *
   * @param f          对发布者执行的操作函数
   * @param candidates 当前候选发布者列表
   * @return 操作结果的 Flux
   */
  private <V> Flux<V> flux(Function<T, Flux<V>> f, List<PublisherHolder<T>> candidates) {
    var publisher = candidates.get(0);
    return publisher.get()
        .flatMapMany(f)
        .onErrorResume(failoverExceptionsPredicate, th -> {
          publisher.markFailed();
          if (candidates.size() == 1) {
            return Flux.error(th);
          }
          var newCandidates = candidates.stream().skip(1).filter(PublisherHolder::isActive).toList();
          if (newCandidates.isEmpty()) {
            return Flux.error(th);
          }
          return flux(f, newCandidates);
        });
  }

  /**
   * 获取当前活跃的发布者列表
   *
   * 从 currentIndex 开始遍历，收集所有处于活跃状态的发布者。
   * 如果当前索引指向的发布者已失效，则向前推进 currentIndex。
   * 该方法是线程安全的。
   *
   * @return 活跃发布者列表，按优先级排序
   */
  private synchronized List<PublisherHolder<T>> getActivePublishers() {
    var result = new ArrayList<PublisherHolder<T>>();
    for (int i = 0, j = currentIndex; i < publishers.size(); i++) {
      var publisher = publishers.get(j);
      if (publisher.isActive()) {
        result.add(publisher);
      } else if (currentIndex == j) {
        // 当前起始位置的发布者不可用，向前推进索引
        currentIndex = ++currentIndex == publishers.size() ? 0 : currentIndex;
      }
      j = ++j == publishers.size() ? 0 : j;
    }
    return result;
  }

  /**
   * 发布者持有者
   *
   * 包装单个发布者实例，提供延迟初始化、失败标记和活跃状态判断功能。
   * 通过宽限期机制，失败的发布者在一段时间后会自动恢复为活跃状态。
   *
   * @param <T> 发布者类型
   */
  static class PublisherHolder<T> {

    /** 重试宽限期（毫秒），失败后经过此时间才可重试 */
    private final long retryGracePeriodMs;

    /** 发布者的供应者函数，用于延迟创建实例 */
    private final Supplier<T> supplier;

    /** 最后一次错误的时间戳（毫秒），用于判断是否过了宽限期 */
    private final AtomicLong lastErrorTs = new AtomicLong();

    /** 缓存的发布者实例，首次调用时通过 supplier 创建 */
    private T publisherInstance;

    PublisherHolder(Supplier<T> supplier, long retryGracePeriodMs) {
      this.supplier = supplier;
      this.retryGracePeriodMs = retryGracePeriodMs;
    }

    /**
     * 获取发布者实例
     *
     * 如果实例尚未创建，则通过 supplier 延迟初始化。
     * 该方法是线程安全的。
     *
     * @return 包装在 Mono 中的发布者实例
     */
    synchronized Mono<T> get() {
      if (publisherInstance == null) {
        try {
          publisherInstance = supplier.get();
        } catch (Throwable th) {
          return Mono.error(th);
        }
      }
      return Mono.just(publisherInstance);
    }

    /**
     * 标记发布者为失败状态
     *
     * 记录当前时间戳，后续通过 isActive() 判断宽限期是否已过。
     */
    void markFailed() {
      lastErrorTs.set(System.currentTimeMillis());
    }

    /**
     * 判断发布者是否处于活跃状态
     *
     * 如果距离上次失败的时间超过宽限期，则认为发布者已恢复。
     * 从未失败的发布者（lastErrorTs=0）始终返回 true。
     *
     * @return true 表示发布者可用，false 表示在宽限期内不可用
     */
    boolean isActive() {
      return System.currentTimeMillis() - lastErrorTs.get() > retryGracePeriodMs;
    }
  }

}
