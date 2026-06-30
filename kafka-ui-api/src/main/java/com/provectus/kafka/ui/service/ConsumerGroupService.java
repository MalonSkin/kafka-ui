package com.provectus.kafka.ui.service;

import com.google.common.collect.Streams;
import com.google.common.collect.Table;
import com.provectus.kafka.ui.emitter.EnhancedConsumer;
import com.provectus.kafka.ui.model.ConsumerGroupOrderingDTO;
import com.provectus.kafka.ui.model.InternalConsumerGroup;
import com.provectus.kafka.ui.model.InternalTopicConsumerGroup;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.SortOrderDTO;
import com.provectus.kafka.ui.service.rbac.AccessControlService;
import com.provectus.kafka.ui.util.ApplicationMetrics;
import com.provectus.kafka.ui.util.SslPropertiesUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Kafka 消费者组管理服务。
 *
 * <p>提供消费者组的查询、分页排序和删除功能，主要包括：
 * <ul>
 *   <li>获取所有消费者组列表及其消费偏移量和 Lag 信息</li>
 *   <li>获取指定主题关联的消费者组信息</li>
 *   <li>消费者组的分页查询，支持按名称、状态、成员数、消费延迟、主题数排序</li>
 *   <li>消费者组详情的查询与删除</li>
 *   <li>创建 Kafka 消费者实例（用于消息消费场景）</li>
 * </ul>
 *
 * <p>通过 {@link AccessControlService} 实现基于 RBAC 的消费者组访问控制。
 */
@Service
@RequiredArgsConstructor
public class ConsumerGroupService {

    private final AdminClientService adminClientService;
    private final AccessControlService accessControlService;

    private Mono<List<InternalConsumerGroup>> getConsumerGroups(
            ReactiveAdminClient ac,
            List<ConsumerGroupDescription> descriptions) {
        var groupNames = descriptions.stream().map(ConsumerGroupDescription::groupId).toList();
        // 1. getting committed offsets for all groups
        return ac.listConsumerGroupOffsets(groupNames, null)
                .flatMap((Table<String, TopicPartition, Long> committedOffsets) -> {
                    // 2. getting end offsets for partitions with committed offsets
                    return ac.listOffsets(committedOffsets.columnKeySet(), OffsetSpec.latest(), false)
                            .map(endOffsets ->
                                    descriptions.stream()
                                            .map(desc -> {
                                                var groupOffsets = committedOffsets.row(desc.groupId());
                                                var endOffsetsForGroup = new HashMap<>(endOffsets);
                                                endOffsetsForGroup.keySet().retainAll(groupOffsets.keySet());
                                                // 3. gathering description & offsets
                                                return InternalConsumerGroup.create(desc, groupOffsets, endOffsetsForGroup);
                                            })
                                            .collect(Collectors.toList()));
                });
    }

    /**
     * 获取指定主题关联的所有消费者组信息。
     *
     * <p>通过以下步骤获取数据：
     * <ol>
     *   <li>获取主题各分区的最新偏移量</li>
     *   <li>获取所有消费者组描述信息</li>
     *   <li>查询各消费者组的已提交偏移量</li>
     *   <li>筛选出与该主题相关的消费者组（有活跃成员或已提交偏移量）</li>
     *   <li>构建消费者组的消费进度信息</li>
     * </ol>
     *
     * @param cluster 目标 Kafka 集群
     * @param topic   主题名称
     * @return 包含 {@link InternalTopicConsumerGroup} 列表的 Mono
     */
    public Mono<List<InternalTopicConsumerGroup>> getConsumerGroupsForTopic(KafkaCluster cluster,
                                                                            String topic) {
        return adminClientService.get(cluster)
                // 1. getting topic's end offsets
                .flatMap(ac -> ac.listTopicOffsets(topic, OffsetSpec.latest(), false)
                        .flatMap(endOffsets -> {
                            var tps = new ArrayList<>(endOffsets.keySet());
                            // 2. getting all consumer groups
                            return describeConsumerGroups(ac)
                                    .flatMap((List<ConsumerGroupDescription> groups) -> {
                                                // 3. trying to find committed offsets for topic
                                                var groupNames = groups.stream().map(ConsumerGroupDescription::groupId).toList();
                                                return ac.listConsumerGroupOffsets(groupNames, tps).map(offsets ->
                                                        groups.stream()
                                                                // 4. keeping only groups that relates to topic
                                                                .filter(g -> isConsumerGroupRelatesToTopic(topic, g, offsets.containsRow(g.groupId())))
                                                                .map(g ->
                                                                        // 5. constructing results
                                                                        InternalTopicConsumerGroup.create(topic, g, offsets.row(g.groupId()), endOffsets))
                                                                .toList()
                                                );
                                            }
                                    );
                        }));
    }

    private boolean isConsumerGroupRelatesToTopic(String topic,
                                                  ConsumerGroupDescription description,
                                                  boolean hasCommittedOffsets) {
        boolean hasActiveMembersForTopic = description.members()
                .stream()
                .anyMatch(m -> m.assignment().topicPartitions().stream().anyMatch(tp -> tp.topic().equals(topic)));
        return hasActiveMembersForTopic || hasCommittedOffsets;
    }

    public record ConsumerGroupsPage(List<InternalConsumerGroup> consumerGroups, int totalPages) {
    }

    private record GroupWithDescr(InternalConsumerGroup icg, ConsumerGroupDescription cgd) {
    }

    /**
     * 分页查询消费者组列表。
     *
     * <p>支持按名称搜索过滤，以及按以下字段排序：
     * <ul>
     *   <li>NAME — 消费者组名称</li>
     *   <li>STATE — 消费者组状态（STABLE > COMPLETING_REBALANCE > PREPARING_REBALANCE > EMPTY > DEAD > UNKNOWN）</li>
     *   <li>MEMBERS — 成员数量</li>
     *   <li>MESSAGES_BEHIND — 消费延迟（Lag）</li>
     *   <li>TOPIC_NUM — 关联主题数量</li>
     * </ul>
     *
     * <p>查询结果经过 RBAC 访问控制过滤。
     *
     * @param cluster     目标 Kafka 集群
     * @param pageNum     页码（从 1 开始）
     * @param perPage     每页数量
     * @param search      搜索关键字（可为 null）
     * @param orderBy     排序字段
     * @param sortOrderDto 排序方向（ASC/DESC）
     * @return 包含分页结果的 {@link ConsumerGroupsPage} 的 Mono
     */
    public Mono<ConsumerGroupsPage> getConsumerGroupsPage(
            KafkaCluster cluster,
            int pageNum,
            int perPage,
            @Nullable String search,
            ConsumerGroupOrderingDTO orderBy,
            SortOrderDTO sortOrderDto) {
        return adminClientService.get(cluster).flatMap(ac ->
                ac.listConsumerGroups()
                        .map(listing -> search == null
                                ? listing
                                : listing.stream()
                                .filter(g -> StringUtils.containsIgnoreCase(g.groupId(), search))
                                .toList()
                        )
                        .flatMapIterable(lst -> lst)
                        .filterWhen(cg -> accessControlService.isConsumerGroupAccessible(cg.groupId(), cluster.getName()))
                        .collectList()
                        .flatMap(allGroups ->
                                loadSortedDescriptions(ac, allGroups, pageNum, perPage, orderBy, sortOrderDto)
                                        .flatMap(descriptions -> getConsumerGroups(ac, descriptions)
                                                .map(page -> new ConsumerGroupsPage(
                                                        page,
                                                        (allGroups.size() / perPage) + (allGroups.size() % perPage == 0 ? 0 : 1))))));
    }

    private Mono<List<ConsumerGroupDescription>> loadSortedDescriptions(ReactiveAdminClient ac,
                                                                        List<ConsumerGroupListing> groups,
                                                                        int pageNum,
                                                                        int perPage,
                                                                        ConsumerGroupOrderingDTO orderBy,
                                                                        SortOrderDTO sortOrderDto) {
        return switch (orderBy) {
            case NAME -> {
                Comparator<ConsumerGroupListing> comparator = Comparator.comparing(ConsumerGroupListing::groupId);
                yield loadDescriptionsByListings(ac, groups, comparator, pageNum, perPage, sortOrderDto);
            }
            case STATE -> {
                ToIntFunction<ConsumerGroupListing> statesPriorities =
                        cg -> switch (cg.state().orElse(ConsumerGroupState.UNKNOWN)) {
                            case STABLE -> 0;
                            case COMPLETING_REBALANCE -> 1;
                            case PREPARING_REBALANCE -> 2;
                            case EMPTY -> 3;
                            case DEAD -> 4;
                            case UNKNOWN -> 5;
                        };
                var comparator = Comparator.comparingInt(statesPriorities);
                yield loadDescriptionsByListings(ac, groups, comparator, pageNum, perPage, sortOrderDto);
            }
            case MEMBERS -> {
                var comparator = Comparator.<ConsumerGroupDescription>comparingInt(cg -> cg.members().size());
                var groupNames = groups.stream().map(ConsumerGroupListing::groupId).toList();
                yield ac.describeConsumerGroups(groupNames)
                        .map(descriptions ->
                                sortAndPaginate(descriptions.values(), comparator, pageNum, perPage, sortOrderDto).toList());
            }
            case MESSAGES_BEHIND -> {

                Comparator<GroupWithDescr> comparator = Comparator.comparingLong(gwd ->
                        gwd.icg.getConsumerLag() == null ? 0L : gwd.icg.getConsumerLag());

                yield loadDescriptionsByInternalConsumerGroups(ac, groups, comparator, pageNum, perPage, sortOrderDto);
            }

            case TOPIC_NUM -> {

                Comparator<GroupWithDescr> comparator = Comparator.comparingInt(gwd -> gwd.icg.getTopicNum());

                yield loadDescriptionsByInternalConsumerGroups(ac, groups, comparator, pageNum, perPage, sortOrderDto);

            }
        };
    }

    private Mono<List<ConsumerGroupDescription>> loadDescriptionsByListings(ReactiveAdminClient ac,
                                                                            List<ConsumerGroupListing> listings,
                                                                            Comparator<ConsumerGroupListing> comparator,
                                                                            int pageNum,
                                                                            int perPage,
                                                                            SortOrderDTO sortOrderDto) {
        List<String> sortedGroups = sortAndPaginate(listings, comparator, pageNum, perPage, sortOrderDto)
                .map(ConsumerGroupListing::groupId)
                .toList();
        return ac.describeConsumerGroups(sortedGroups)
                .map(descrMap -> sortedGroups.stream().map(descrMap::get).toList());
    }

    private <T> Stream<T> sortAndPaginate(Collection<T> collection,
                                          Comparator<T> comparator,
                                          int pageNum,
                                          int perPage,
                                          SortOrderDTO sortOrderDto) {
        return collection.stream()
                .sorted(sortOrderDto == SortOrderDTO.ASC ? comparator : comparator.reversed())
                .skip((long) (pageNum - 1) * perPage)
                .limit(perPage);
    }

    private Mono<List<ConsumerGroupDescription>> describeConsumerGroups(ReactiveAdminClient ac) {
        return ac.listConsumerGroupNames()
                .flatMap(ac::describeConsumerGroups)
                .map(cgs -> new ArrayList<>(cgs.values()));
    }


    private Mono<List<ConsumerGroupDescription>> loadDescriptionsByInternalConsumerGroups(ReactiveAdminClient ac,
                                                                                          List<ConsumerGroupListing> groups,
                                                                                          Comparator<GroupWithDescr> comparator,
                                                                                          int pageNum,
                                                                                          int perPage,
                                                                                          SortOrderDTO sortOrderDto) {
        var groupNames = groups.stream().map(ConsumerGroupListing::groupId).toList();

        return ac.describeConsumerGroups(groupNames)
                .flatMap(descriptionsMap -> {
                            List<ConsumerGroupDescription> descriptions = descriptionsMap.values().stream().toList();
                            return getConsumerGroups(ac, descriptions)
                                    .map(icg -> Streams.zip(icg.stream(), descriptions.stream(), GroupWithDescr::new).toList())
                                    .map(gwd -> sortAndPaginate(gwd, comparator, pageNum, perPage, sortOrderDto)
                                            .map(GroupWithDescr::cgd).toList());
                        }
                );

    }

    /**
     * 获取指定消费者组的详细信息。
     *
     * <p>包括消费者组描述、已提交偏移量和消费延迟（Lag）信息。
     *
     * @param cluster         目标 Kafka 集群
     * @param consumerGroupId 消费者组 ID
     * @return 包含 {@link InternalConsumerGroup} 的 Mono
     */
    public Mono<InternalConsumerGroup> getConsumerGroupDetail(KafkaCluster cluster,
                                                              String consumerGroupId) {
        return adminClientService.get(cluster)
                .flatMap(ac -> ac.describeConsumerGroups(List.of(consumerGroupId))
                        .filter(m -> m.containsKey(consumerGroupId))
                        .map(r -> r.get(consumerGroupId))
                        .flatMap(descr ->
                                getConsumerGroups(ac, List.of(descr))
                                        .filter(groups -> !groups.isEmpty())
                                        .map(groups -> groups.get(0))));
    }

    /**
     * 删除指定的消费者组。
     *
     * @param cluster 目标 Kafka 集群
     * @param groupId 消费者组 ID
     * @return 删除完成的 Mono
     */
    public Mono<Void> deleteConsumerGroupById(KafkaCluster cluster,
                                              String groupId) {
        return adminClientService.get(cluster)
                .flatMap(adminClient -> adminClient.deleteConsumerGroups(List.of(groupId)));
    }

    /**
     * 创建默认配置的 Kafka 消费者实例。
     *
     * @param cluster 目标 Kafka 集群
     * @return 配置完成的 {@link EnhancedConsumer} 实例
     */
    public EnhancedConsumer createConsumer(KafkaCluster cluster) {
        return createConsumer(cluster, Map.of());
    }

    /**
     * 创建自定义配置的 Kafka 消费者实例。
     *
     * <p>默认配置包括：
     * <ul>
     *   <li>自动提交关闭（enable.auto.commit=false）</li>
     *   <li>偏移量重置策略为 earliest</li>
     *   <li>禁止自动创建主题</li>
     *   <li>禁用 SSL 主机名验证</li>
     *   <li>客户端 ID 格式为 kafka-ui-consumer-{timestamp}</li>
     * </ul>
     *
     * @param cluster    目标 Kafka 集群
     * @param properties 额外的消费者配置属性（会覆盖默认配置）
     * @return 配置完成的 {@link EnhancedConsumer} 实例
     */
    public EnhancedConsumer createConsumer(KafkaCluster cluster,
                                           Map<String, Object> properties) {
        Properties props = new Properties();
        SslPropertiesUtil.addKafkaSslProperties(cluster.getOriginalProperties().getSsl(), props);
        // 设置SSL的Keystore配置
        SslPropertiesUtil.addKafkaSslKeyStoreConfig(cluster.getOriginalProperties().getSslKeystoreConfig(), props);
        props.putAll(cluster.getProperties());
        // 默认设置禁用主机名验证
        if (!cluster.getProperties().containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)) {
            props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, StringUtils.EMPTY);
        }
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafka-ui-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        props.putAll(properties);

        return new EnhancedConsumer(
                props,
                cluster.getPollingSettings().getPollingThrottler(),
                ApplicationMetrics.forCluster(cluster)
        );
    }

}
