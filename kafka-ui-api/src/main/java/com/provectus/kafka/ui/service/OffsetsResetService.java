package com.provectus.kafka.ui.service;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.kafka.common.ConsumerGroupState.DEAD;
import static org.apache.kafka.common.ConsumerGroupState.EMPTY;

import com.google.common.base.Preconditions;
import com.provectus.kafka.ui.exception.NotFoundException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.model.KafkaCluster;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 消费者组偏移量重置服务。
 *
 * <p>实现 KIP-122（https://cwiki.apache.org/confluence/display/KAFKA/KIP-122%3A+Add+Reset+Consumer+Group+Offsets+tooling），
 * 功能等价于 {@code kafka-consumer-groups --reset-offsets} 控制台命令
 * （参见 kafka.admin.ConsumerGroupCommand）。</p>
 *
 * <p>支持以下重置策略：</p>
 * <ul>
 *   <li>重置到最早偏移量（earliest）</li>
 *   <li>重置到最新偏移量（latest）</li>
 *   <li>重置到指定时间戳</li>
 *   <li>重置到指定偏移量</li>
 * </ul>
 *
 * <p>重置前会校验消费者组是否存在且处于非活跃状态（DEAD 或 EMPTY）。</p>
 *
 * @see AdminClientService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OffsetsResetService {

  private final AdminClientService adminClientService;

  /**
   * 将指定消费者组的偏移量重置到最早位置。
   *
   * @param cluster    Kafka 集群
   * @param group      消费者组 ID
   * @param topic      Topic 名称
   * @param partitions 要重置的分区列表
   * @return 完成信号
   */
  public Mono<Void> resetToEarliest(
      KafkaCluster cluster, String group, String topic, Collection<Integer> partitions) {
    return checkGroupCondition(cluster, group)
        .flatMap(ac ->
            offsets(ac, topic, partitions, OffsetSpec.earliest())
                .flatMap(offsets -> resetOffsets(ac, group, offsets)));
  }

  /**
   * 根据 OffsetSpec 查询指定 Topic 的偏移量。
   *
   * @param client     响应式 AdminClient
   * @param topic      Topic 名称
   * @param partitions 分区列表，为 null 时查询所有分区
   * @param spec       偏移量规格（earliest / latest / timestamp）
   * @return 分区到偏移量的映射
   */
  private Mono<Map<TopicPartition, Long>> offsets(ReactiveAdminClient client,
                                                  String topic,
                                                  @Nullable Collection<Integer> partitions,
                                                  OffsetSpec spec) {
    if (partitions == null) {
      return client.listTopicOffsets(topic, spec, true);
    }
    return client.listOffsets(
        partitions.stream().map(idx -> new TopicPartition(topic, idx)).collect(toSet()),
        spec,
        true
    );
  }

  /**
   * 将指定消费者组的偏移量重置到最新位置。
   *
   * @param cluster    Kafka 集群
   * @param group      消费者组 ID
   * @param topic      Topic 名称
   * @param partitions 要重置的分区列表
   * @return 完成信号
   */
  public Mono<Void> resetToLatest(
      KafkaCluster cluster, String group, String topic, Collection<Integer> partitions) {
    return checkGroupCondition(cluster, group)
        .flatMap(ac ->
            offsets(ac, topic, partitions, OffsetSpec.latest())
                .flatMap(offsets -> resetOffsets(ac, group, offsets)));
  }

  /**
   * 将指定消费者组的偏移量重置到指定时间戳对应的位置。
   *
   * <p>对于未找到对应时间戳偏移量的分区，将使用该分区的最新偏移量。</p>
   *
   * @param cluster         Kafka 集群
   * @param group           消费者组 ID
   * @param topic           Topic 名称
   * @param partitions      要重置的分区列表
   * @param targetTimestamp 目标时间戳（毫秒）
   * @return 完成信号
   */
  public Mono<Void> resetToTimestamp(
      KafkaCluster cluster, String group, String topic, Collection<Integer> partitions,
      long targetTimestamp) {
    return checkGroupCondition(cluster, group)
        .flatMap(ac ->
            offsets(ac, topic, partitions, OffsetSpec.forTimestamp(targetTimestamp))
                .flatMap(
                    foundOffsets -> offsets(ac, topic, partitions, OffsetSpec.latest())
                        .map(endOffsets -> editTsOffsets(foundOffsets, endOffsets))
                )
                .flatMap(offsets -> resetOffsets(ac, group, offsets))
        );
  }

  /**
   * 将指定消费者组的偏移量重置到用户指定的具体值。
   *
   * <p>若指定偏移量超出分区的 earliest-latest 范围，将自动调整到边界值。</p>
   *
   * @param cluster       Kafka 集群
   * @param group         消费者组 ID
   * @param topic         Topic 名称
   * @param targetOffsets 分区号到目标偏移量的映射
   * @return 完成信号
   */
  public Mono<Void> resetToOffsets(
      KafkaCluster cluster, String group, String topic, Map<Integer, Long> targetOffsets) {
    Preconditions.checkNotNull(targetOffsets);
    var partitionOffsets = targetOffsets.entrySet().stream()
        .collect(toMap(e -> new TopicPartition(topic, e.getKey()), Map.Entry::getValue));
    return checkGroupCondition(cluster, group).flatMap(
        ac ->
            ac.listOffsets(partitionOffsets.keySet(), OffsetSpec.earliest(), true)
                .flatMap(earliest ->
                    ac.listOffsets(partitionOffsets.keySet(), OffsetSpec.latest(), true)
                        .map(latest -> editOffsetsBounds(partitionOffsets, earliest, latest))
                        .flatMap(offsetsToCommit -> resetOffsets(ac, group, offsetsToCommit)))
    );
  }

  /**
   * 校验消费者组是否存在且处于可重置状态（DEAD 或 EMPTY）。
   *
   * @param cluster Kafka 集群
   * @param groupId 消费者组 ID
   * @return 校验通过时返回 ReactiveAdminClient 实例
   * @throws NotFoundException    消费者组不存在时抛出
   * @throws ValidationException  消费者组处于活跃状态时抛出
   */
  private Mono<ReactiveAdminClient> checkGroupCondition(KafkaCluster cluster, String groupId) {
    return adminClientService.get(cluster)
        .flatMap(ac ->
            // we need to call listConsumerGroups() to check group existence, because
            // describeConsumerGroups() will return consumer group even if it doesn't exist
            ac.listConsumerGroupNames()
                .filter(cgs -> cgs.stream().anyMatch(g -> g.equals(groupId)))
                .flatMap(cgs -> ac.describeConsumerGroups(List.of(groupId)))
                .filter(cgs -> cgs.containsKey(groupId))
                .map(cgs -> cgs.get(groupId))
                .flatMap(cg -> {
                  if (!Set.of(DEAD, EMPTY).contains(cg.state())) {
                    return Mono.error(
                        new ValidationException(
                            String.format(
                                "Group's offsets can be reset only if group is inactive,"
                                    + " but group is in %s state",
                                cg.state()
                            )
                        )
                    );
                  }
                  return Mono.just(ac);
                })
                .switchIfEmpty(Mono.error(new NotFoundException("Consumer group not found")))
        );
  }

  /**
   * 合并基于时间戳查询到的偏移量与分区末尾偏移量。
   *
   * <p>对于未按时间戳找到偏移量的分区，使用对应分区的末尾偏移量作为回退。</p>
   *
   * @param foundTsOffsets 按时间戳查询到的偏移量
   * @param endOffsets     各分区的末尾偏移量
   * @return 合并后的分区偏移量映射
   */
  private Map<TopicPartition, Long> editTsOffsets(Map<TopicPartition, Long> foundTsOffsets,
                                                  Map<TopicPartition, Long> endOffsets) {
    // for partitions where we didnt find offset by timestamp, we use end offsets
    Map<TopicPartition, Long> result = new HashMap<>(endOffsets);
    result.putAll(foundTsOffsets);
    return result;
  }

  /**
   * 校验并修正提交的偏移量，确保其在 earliest-latest 范围内。
   *
   * <p>若偏移量低于最早偏移量则重置为最早值，若高于最新偏移量则重置为最新值
   * （与 kafka.admin.ConsumerGroupCommand.scala 的逻辑保持一致）。</p>
   *
   * @param offsetsToCheck  待校验的偏移量映射
   * @param earliestOffsets 各分区的最早偏移量
   * @param latestOffsets   各分区的最新偏移量
   * @return 校正后的分区偏移量映射
   */
  private Map<TopicPartition, Long> editOffsetsBounds(Map<TopicPartition, Long> offsetsToCheck,
                                                      Map<TopicPartition, Long> earliestOffsets,
                                                      Map<TopicPartition, Long> latestOffsets) {
    var result = new HashMap<TopicPartition, Long>();
    offsetsToCheck.forEach((tp, offset) -> {
      if (earliestOffsets.get(tp) > offset) {
        log.warn("Offset for partition {} is lower than earliest offset, resetting to earliest",
            tp);
        result.put(tp, earliestOffsets.get(tp));
      } else if (latestOffsets.get(tp) < offset) {
        log.warn("Offset for partition {} is greater than latest offset, resetting to latest", tp);
        result.put(tp, latestOffsets.get(tp));
      } else {
        result.put(tp, offset);
      }
    });
    return result;
  }

  /**
   * 执行消费者组偏移量的实际重置操作。
   *
   * @param adminClient 响应式 AdminClient
   * @param groupId     消费者组 ID
   * @param offsets     目标偏移量映射
   * @return 完成信号
   */
  private Mono<Void> resetOffsets(ReactiveAdminClient adminClient,
                                  String groupId,
                                  Map<TopicPartition, Long> offsets) {
    return adminClient.alterConsumerGroupOffsets(groupId, offsets);
  }

}
