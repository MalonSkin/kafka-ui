package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.model.ClusterFeature;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.service.ReactiveAdminClient.ClusterDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.acl.AclOperation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 集群特性探测服务。
 *
 * <p>检测指定 Kafka 集群支持的功能特性（{@link ClusterFeature}），包括：</p>
 * <ul>
 *   <li>Kafka Connect 是否可用</li>
 *   <li>KSQL DB 是否可用</li>
 *   <li>Schema Registry 是否可用</li>
 *   <li>Topic 删除功能是否启用</li>
 *   <li>ACL 查看 / 编辑权限</li>
 * </ul>
 *
 * @see ClusterFeature
 * @see ReactiveAdminClient
 */
@Service
@Slf4j
public class FeatureService {

  /**
   * 检测并返回集群当前可用的功能特性列表。
   *
   * @param adminClient       响应式 AdminClient
   * @param cluster           Kafka 集群信息
   * @param clusterDescription 集群描述信息（用于判断 ACL 权限）
   * @return 可用特性列表
   */
  public Mono<List<ClusterFeature>> getAvailableFeatures(ReactiveAdminClient adminClient,
                                                         KafkaCluster cluster,
                                                         ClusterDescription clusterDescription) {
    List<Mono<ClusterFeature>> features = new ArrayList<>();

    if (Optional.ofNullable(cluster.getConnectsClients())
        .filter(Predicate.not(Map::isEmpty))
        .isPresent()) {
      features.add(Mono.just(ClusterFeature.KAFKA_CONNECT));
    }

    if (cluster.getKsqlClient() != null) {
      features.add(Mono.just(ClusterFeature.KSQL_DB));
    }

    if (cluster.getSchemaRegistryClient() != null) {
      features.add(Mono.just(ClusterFeature.SCHEMA_REGISTRY));
    }

    features.add(topicDeletionEnabled(adminClient));
    features.add(aclView(adminClient));
    features.add(aclEdit(adminClient, clusterDescription));

    return Flux.fromIterable(features).flatMap(m -> m).collectList();
  }

  /**
   * 检测 Topic 删除功能是否启用。
   *
   * @param adminClient 响应式 AdminClient
   * @return 若启用则返回 {@link ClusterFeature#TOPIC_DELETION}，否则返回空
   */
  private Mono<ClusterFeature> topicDeletionEnabled(ReactiveAdminClient adminClient) {
    return adminClient.isTopicDeletionEnabled()
        ? Mono.just(ClusterFeature.TOPIC_DELETION)
        : Mono.empty();
  }

  /**
   * 检测当前用户是否具有 ACL 编辑权限。
   *
   * @param adminClient        响应式 AdminClient
   * @param clusterDescription 集群描述信息（包含已授权操作）
   * @return 若可编辑则返回 {@link ClusterFeature#KAFKA_ACL_EDIT}，否则返回空
   */
  private Mono<ClusterFeature> aclEdit(ReactiveAdminClient adminClient, ClusterDescription clusterDescription) {
    var authorizedOps = Optional.ofNullable(clusterDescription.getAuthorizedOperations()).orElse(Set.of());
    boolean canEdit = aclViewEnabled(adminClient)
        && (authorizedOps.contains(AclOperation.ALL) || authorizedOps.contains(AclOperation.ALTER));
    return canEdit
        ? Mono.just(ClusterFeature.KAFKA_ACL_EDIT)
        : Mono.empty();
  }

  /**
   * 检测集群是否启用了 ACL 查看功能。
   *
   * @param adminClient 响应式 AdminClient
   * @return 若可查看则返回 {@link ClusterFeature#KAFKA_ACL_VIEW}，否则返回空
   */
  private Mono<ClusterFeature> aclView(ReactiveAdminClient adminClient) {
    return aclViewEnabled(adminClient)
        ? Mono.just(ClusterFeature.KAFKA_ACL_VIEW)
        : Mono.empty();
  }

  /**
   * 判断集群是否启用了授权安全机制（AUTHORIZED_SECURITY_ENABLED）。
   *
   * @param adminClient 响应式 AdminClient
   * @return 如果启用了安全授权则返回 true
   */
  private boolean aclViewEnabled(ReactiveAdminClient adminClient) {
    return adminClient.getClusterFeatures().contains(ReactiveAdminClient.SupportedFeature.AUTHORIZED_SECURITY_ENABLED);
  }

}
