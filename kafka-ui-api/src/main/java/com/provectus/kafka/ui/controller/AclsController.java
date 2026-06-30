package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.AclsApi;
import com.provectus.kafka.ui.mapper.ClusterMapper;
import com.provectus.kafka.ui.model.CreateConsumerAclDTO;
import com.provectus.kafka.ui.model.CreateProducerAclDTO;
import com.provectus.kafka.ui.model.CreateStreamAppAclDTO;
import com.provectus.kafka.ui.model.KafkaAclDTO;
import com.provectus.kafka.ui.model.KafkaAclNamePatternTypeDTO;
import com.provectus.kafka.ui.model.KafkaAclResourceTypeDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.AclAction;
import com.provectus.kafka.ui.service.acl.AclsService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Kafka ACL（访问控制列表）管理控制器
 *
 * 提供 Kafka ACL 的管理操作，包括：
 * 1. ACL 的 CRUD 操作（创建、查看、删除）
 * 2. ACL 列表查询（支持按资源类型和名称模式过滤）
 * 3. ACL 导出为 CSV 格式
 * 4. 从 CSV 同步 ACL 配置
 * 5. 简化创建消费者、生产者、流应用的 ACL
 */
@RestController
@RequiredArgsConstructor
public class AclsController extends AbstractController implements AclsApi {

  /** ACL 业务服务，处理 ACL 的核心逻辑 */
  private final AclsService aclsService;

  /**
   * 创建 ACL
   *
   * @param clusterName 集群名称
   * @param kafkaAclDto ACL 配置（包含资源类型、资源名称、操作、权限等）
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> createAcl(String clusterName, Mono<KafkaAclDTO> kafkaAclDto,
                                              ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("createAcl")
        .build();

    return validateAccess(context)
        .then(kafkaAclDto)
        .map(ClusterMapper::toAclBinding)
        .flatMap(binding -> aclsService.createAcl(getCluster(clusterName), binding))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 删除 ACL
   *
   * @param clusterName 集群名称
   * @param kafkaAclDto 要删除的 ACL 配置
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteAcl(String clusterName, Mono<KafkaAclDTO> kafkaAclDto,
                                              ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("deleteAcl")
        .build();

    return validateAccess(context)
        .then(kafkaAclDto)
        .map(ClusterMapper::toAclBinding)
        .flatMap(binding -> aclsService.deleteAcl(getCluster(clusterName), binding))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 查询 ACL 列表
   *
   * 支持按资源类型、资源名称和名称模式类型进行过滤。
   *
   * @param clusterName      集群名称
   * @param resourceTypeDto  资源类型过滤（TOPIC/GROUP/CLUSTER 等，为空则返回所有类型）
   * @param resourceName     资源名称过滤（为空则返回所有名称）
   * @param namePatternTypeDto 名称模式类型（LITERAL/PREFIXED/ANY）
   * @param exchange         服务器交换对象
   * @return ACL 列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<KafkaAclDTO>>> listAcls(String clusterName,
                                                          KafkaAclResourceTypeDTO resourceTypeDto,
                                                          String resourceName,
                                                          KafkaAclNamePatternTypeDTO namePatternTypeDto,
                                                          ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.VIEW)
        .operationName("listAcls")
        .build();

    var resourceType = Optional.ofNullable(resourceTypeDto)
        .map(ClusterMapper::mapAclResourceTypeDto)
        .orElse(ResourceType.ANY);

    var namePatternType = Optional.ofNullable(namePatternTypeDto)
        .map(ClusterMapper::mapPatternTypeDto)
        .orElse(PatternType.ANY);

    var filter = new ResourcePatternFilter(resourceType, resourceName, namePatternType);

    return validateAccess(context).then(
        Mono.just(
            ResponseEntity.ok(
                aclsService.listAcls(getCluster(clusterName), filter)
                    .map(ClusterMapper::toKafkaAclDto)))
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 将所有 ACL 导出为 CSV 格式
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return ACL 的 CSV 字符串
   */
  @Override
  public Mono<ResponseEntity<String>> getAclAsCsv(String clusterName, ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.VIEW)
        .operationName("getAclAsCsv")
        .build();

    return validateAccess(context).then(
        aclsService.getAclAsCsvString(getCluster(clusterName))
            .map(ResponseEntity::ok)
            .flatMap(Mono::just)
            .doOnEach(sig -> audit(context, sig))
    );
  }

  /**
   * 从 CSV 同步 ACL 配置
   *
   * 将 CSV 格式的 ACL 配置同步到集群，会与现有 ACL 进行差异对比并应用变更。
   *
   * @param clusterName 集群名称
   * @param csvMono     CSV 格式的 ACL 配置
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> syncAclsCsv(String clusterName, Mono<String> csvMono, ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("syncAclsCsv")
        .build();

    return validateAccess(context)
        .then(csvMono)
        .flatMap(csv -> aclsService.syncAclWithAclCsv(getCluster(clusterName), csv))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 简化创建消费者 ACL
   *
   * 为消费者创建标准 ACL（包含 Topic 读取权限和消费者组权限）。
   *
   * @param clusterName         集群名称
   * @param createConsumerAclDto 消费者 ACL 配置（Topic 名称、消费者组、主机等）
   * @param exchange            服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> createConsumerAcl(String clusterName,
                                                      Mono<CreateConsumerAclDTO> createConsumerAclDto,
                                                      ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("createConsumerAcl")
        .build();

    return validateAccess(context)
        .then(createConsumerAclDto)
        .flatMap(req -> aclsService.createConsumerAcl(getCluster(clusterName), req))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 简化创建生产者 ACL
   *
   * 为生产者创建标准 ACL（包含 Topic 写入权限）。
   *
   * @param clusterName         集群名称
   * @param createProducerAclDto 生产者 ACL 配置（Topic 名称、主机等）
   * @param exchange            服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> createProducerAcl(String clusterName,
                                                      Mono<CreateProducerAclDTO> createProducerAclDto,
                                                      ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("createProducerAcl")
        .build();

    return validateAccess(context)
        .then(createProducerAclDto)
        .flatMap(req -> aclsService.createProducerAcl(getCluster(clusterName), req))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }

  /**
   * 简化创建流应用 ACL
   *
   * 为 Kafka Streams 应用创建标准 ACL（包含 Topic 读写权限和消费者组权限）。
   *
   * @param clusterName           集群名称
   * @param createStreamAppAclDto 流应用 ACL 配置（应用 ID、Topic 名称、主机等）
   * @param exchange              服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> createStreamAppAcl(String clusterName,
                                                       Mono<CreateStreamAppAclDTO> createStreamAppAclDto,
                                                       ServerWebExchange exchange) {
    AccessContext context = AccessContext.builder()
        .cluster(clusterName)
        .aclActions(AclAction.EDIT)
        .operationName("createStreamAppAcl")
        .build();

    return validateAccess(context)
        .then(createStreamAppAclDto)
        .flatMap(req -> aclsService.createStreamAppAcl(getCluster(clusterName), req))
        .doOnEach(sig -> audit(context, sig))
        .thenReturn(ResponseEntity.ok().build());
  }
}
