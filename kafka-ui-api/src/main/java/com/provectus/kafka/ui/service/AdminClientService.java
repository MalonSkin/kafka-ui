package com.provectus.kafka.ui.service;

import com.provectus.kafka.ui.model.KafkaCluster;
import reactor.core.publisher.Mono;

/**
 * Kafka AdminClient 服务接口。
 *
 * <p>定义了获取和管理 {@link ReactiveAdminClient} 实例的契约，
 * 负责 AdminClient 的创建、缓存和生命周期管理。</p>
 *
 * @see ReactiveAdminClient
 * @see AdminClientServiceImpl
 */
public interface AdminClientService {

  /**
   * 获取指定集群的 ReactiveAdminClient 实例。
   *
   * <p>如果缓存中已存在该集群的客户端，则直接返回；
   * 否则创建新的客户端实例并缓存。</p>
   *
   * @param cluster Kafka 集群信息
   * @return 该集群对应的 ReactiveAdminClient 实例
   */
  Mono<ReactiveAdminClient> get(KafkaCluster cluster);

  /**
   * 关闭并移除指定集群的 AdminClient 缓存。
   *
   * <p>释放与该集群关联的所有 AdminClient 资源。</p>
   *
   * @param clusterName 要关闭的集群名称
   */
  void closeClient(String clusterName);

}
