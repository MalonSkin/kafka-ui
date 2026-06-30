package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.SchemasApi;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.mapper.KafkaSrMapper;
import com.provectus.kafka.ui.mapper.KafkaSrMapperImpl;
import com.provectus.kafka.ui.model.CompatibilityCheckResponseDTO;
import com.provectus.kafka.ui.model.CompatibilityLevelDTO;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.model.NewSchemaSubjectDTO;
import com.provectus.kafka.ui.model.SchemaSubjectDTO;
import com.provectus.kafka.ui.model.SchemaSubjectsResponseDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.SchemaAction;
import com.provectus.kafka.ui.service.SchemaRegistryService;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Schema Registry 管理控制器
 *
 * 提供 Confluent Schema Registry 的管理操作，包括：
 * 1. Schema 主题的 CRUD 操作（创建、查看、删除）
 * 2. Schema 版本管理（查看所有版本、按版本号查看、删除指定版本）
 * 3. Schema 兼容性检查和兼容性级别设置（全局和主题级别）
 * 4. Schema 主题分页列表查询（支持搜索）
 *
 * <p>需要集群配置了 Schema Registry 才能使用，否则操作会抛出 ValidationException。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class SchemasController extends AbstractController implements SchemasApi {

  /** 默认分页大小 */
  private static final Integer DEFAULT_PAGE_SIZE = 25;

  /** Schema Registry 模型映射器，将内部模型转换为 DTO */
  private final KafkaSrMapper kafkaSrMapper = new KafkaSrMapperImpl();

  /** Schema Registry 业务服务，处理 Schema 的核心逻辑 */
  private final SchemaRegistryService schemaRegistryService;

  /**
   * 获取集群实例并验证 Schema Registry 是否已配置
   *
   * @param clusterName 集群名称
   * @return 集群实例
   * @throws ClusterNotFoundException 如果集群不存在
   * @throws ValidationException      如果集群未配置 Schema Registry
   */
  @Override
  protected KafkaCluster getCluster(String clusterName) {
    var c = super.getCluster(clusterName);
    if (c.getSchemaRegistryClient() == null) {
      throw new ValidationException("Schema Registry is not set for cluster " + clusterName);
    }
    return c;
  }

  /**
   * 检查新 Schema 与现有 Schema 的兼容性
   *
   * @param clusterName        集群名称
   * @param subject            Schema 主题名称
   * @param newSchemaSubjectMono 新 Schema 内容
   * @param exchange           服务器交换对象
   * @return 兼容性检查结果（是否兼容及详细信息）
   */
  @Override
  public Mono<ResponseEntity<CompatibilityCheckResponseDTO>> checkSchemaCompatibility(
      String clusterName, String subject, @Valid Mono<NewSchemaSubjectDTO> newSchemaSubjectMono,
      ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subject)
        .schemaActions(SchemaAction.VIEW)
        .operationName("checkSchemaCompatibility")
        .build();

    return validateAccess(context).then(
        newSchemaSubjectMono.flatMap(subjectDTO ->
                schemaRegistryService.checksSchemaCompatibility(
                    getCluster(clusterName),
                    subject,
                    kafkaSrMapper.fromDto(subjectDTO)
                ))
            .map(kafkaSrMapper::toDto)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 注册新 Schema
   *
   * @param clusterName        集群名称
   * @param newSchemaSubjectMono 新 Schema 内容（包含主题名称、Schema 定义、类型等）
   * @param exchange           服务器交换对象
   * @return 注册后的 Schema 信息（包含版本号等）
   */
  @Override
  public Mono<ResponseEntity<SchemaSubjectDTO>> createNewSchema(
      String clusterName, @Valid Mono<NewSchemaSubjectDTO> newSchemaSubjectMono,
      ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schemaActions(SchemaAction.CREATE)
        .operationName("createNewSchema")
        .build();

    return validateAccess(context).then(
        newSchemaSubjectMono.flatMap(newSubject ->
                schemaRegistryService.registerNewSchema(
                    getCluster(clusterName),
                    newSubject.getSubject(),
                    kafkaSrMapper.fromDto(newSubject)
                )
            ).map(kafkaSrMapper::toDto)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 删除 Schema 主题的最新版本
   *
   * @param clusterName 集群名称
   * @param subject     Schema 主题名称
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteLatestSchema(
      String clusterName, String subject, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subject)
        .schemaActions(SchemaAction.DELETE)
        .operationName("deleteLatestSchema")
        .build();

    return validateAccess(context).then(
        schemaRegistryService.deleteLatestSchemaSubject(getCluster(clusterName), subject)
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }

  /**
   * 删除 Schema 主题的所有版本
   *
   * @param clusterName 集群名称
   * @param subject     Schema 主题名称
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteSchema(
      String clusterName, String subject, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subject)
        .schemaActions(SchemaAction.DELETE)
        .operationName("deleteSchema")
        .build();

    return validateAccess(context).then(
        schemaRegistryService.deleteSchemaSubjectEntirely(getCluster(clusterName), subject)
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }

  /**
   * 删除 Schema 主题的指定版本
   *
   * @param clusterName 集群名称
   * @param subjectName Schema 主题名称
   * @param version     要删除的版本号
   * @param exchange    服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteSchemaByVersion(
      String clusterName, String subjectName, Integer version, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subjectName)
        .schemaActions(SchemaAction.DELETE)
        .operationName("deleteSchemaByVersion")
        .build();

    return validateAccess(context).then(
        schemaRegistryService.deleteSchemaSubjectByVersion(getCluster(clusterName), subjectName, version)
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }

  /**
   * 获取 Schema 主题的所有版本
   *
   * @param clusterName 集群名称
   * @param subjectName Schema 主题名称
   * @param exchange    服务器交换对象
   * @return Schema 版本列表流
   */
  @Override
  public Mono<ResponseEntity<Flux<SchemaSubjectDTO>>> getAllVersionsBySubject(
      String clusterName, String subjectName, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subjectName)
        .schemaActions(SchemaAction.VIEW)
        .operationName("getAllVersionsBySubject")
        .build();

    Flux<SchemaSubjectDTO> schemas =
        schemaRegistryService.getAllVersionsBySubject(getCluster(clusterName), subjectName)
            .map(kafkaSrMapper::toDto);

    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(schemas))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取全局 Schema 兼容性级别
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return 全局兼容性级别 DTO，未设置时返回 404
   */
  @Override
  public Mono<ResponseEntity<CompatibilityLevelDTO>> getGlobalSchemaCompatibilityLevel(
      String clusterName, ServerWebExchange exchange) {
    return schemaRegistryService.getGlobalSchemaCompatibilityLevel(getCluster(clusterName))
        .map(c -> new CompatibilityLevelDTO().compatibility(kafkaSrMapper.toDto(c)))
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }

  /**
   * 获取 Schema 主题的最新版本
   *
   * @param clusterName 集群名称
   * @param subject     Schema 主题名称
   * @param exchange    服务器交换对象
   * @return 最新版本的 Schema 信息
   */
  @Override
  public Mono<ResponseEntity<SchemaSubjectDTO>> getLatestSchema(String clusterName,
                                                                String subject,
                                                                ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subject)
        .schemaActions(SchemaAction.VIEW)
        .operationName("getLatestSchema")
        .build();

    return validateAccess(context).then(
        schemaRegistryService.getLatestSchemaVersionBySubject(getCluster(clusterName), subject)
            .map(kafkaSrMapper::toDto)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 获取 Schema 主题的指定版本
   *
   * @param clusterName 集群名称
   * @param subject     Schema 主题名称
   * @param version     版本号
   * @param exchange    服务器交换对象
   * @return 指定版本的 Schema 信息
   */
  @Override
  public Mono<ResponseEntity<SchemaSubjectDTO>> getSchemaByVersion(
      String clusterName, String subject, Integer version, ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schema(subject)
        .schemaActions(SchemaAction.VIEW)
        .operationName("getSchemaByVersion")
        .operationParams(Map.of("subject", subject, "version", version))
        .build();

    return validateAccess(context).then(
        schemaRegistryService.getSchemaSubjectByVersion(
                getCluster(clusterName), subject, version)
            .map(kafkaSrMapper::toDto)
            .map(ResponseEntity::ok)
    ).doOnEach(sig -> audit(context, sig));
  }

  /**
   * 分页获取 Schema 主题列表
   *
   * @param clusterName        集群名称
   * @param pageNum            页码（从 1 开始，默认 1）
   * @param perPage            每页大小（默认 25）
   * @param search             搜索关键词（按主题名称模糊匹配）
   * @param serverWebExchange  服务器交换对象
   * @return Schema 主题分页响应（包含 Schema 列表和总页数）
   */
  @Override
  public Mono<ResponseEntity<SchemaSubjectsResponseDTO>> getSchemas(String clusterName,
                                                                    @Valid Integer pageNum,
                                                                    @Valid Integer perPage,
                                                                    @Valid String search,
                                                                    ServerWebExchange serverWebExchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .operationName("getSchemas")
        .build();

    return schemaRegistryService
        .getAllSubjectNames(getCluster(clusterName))
        .flatMapIterable(l -> l)
        .filterWhen(schema -> accessControlService.isSchemaAccessible(schema, clusterName))
        .collectList()
        .flatMap(subjects -> {
          int pageSize = perPage != null && perPage > 0 ? perPage : DEFAULT_PAGE_SIZE;
          int subjectToSkip = ((pageNum != null && pageNum > 0 ? pageNum : 1) - 1) * pageSize;
          List<String> filteredSubjects = subjects
              .stream()
              .filter(subj -> search == null || StringUtils.containsIgnoreCase(subj, search))
              .sorted().toList();
          var totalPages = (filteredSubjects.size() / pageSize)
              + (filteredSubjects.size() % pageSize == 0 ? 0 : 1);
          List<String> subjectsToRender = filteredSubjects.stream()
              .skip(subjectToSkip)
              .limit(pageSize)
              .toList();
          return schemaRegistryService.getAllLatestVersionSchemas(getCluster(clusterName), subjectsToRender)
              .map(subjs -> subjs.stream().map(kafkaSrMapper::toDto).toList())
              .map(subjs -> new SchemaSubjectsResponseDTO().pageCount(totalPages).schemas(subjs));
        }).map(ResponseEntity::ok)
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 更新全局 Schema 兼容性级别
   *
   * @param clusterName            集群名称
   * @param compatibilityLevelMono 新的兼容性级别
   * @param exchange               服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> updateGlobalSchemaCompatibilityLevel(
      String clusterName, @Valid Mono<CompatibilityLevelDTO> compatibilityLevelMono,
      ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schemaActions(SchemaAction.MODIFY_GLOBAL_COMPATIBILITY)
        .operationName("updateGlobalSchemaCompatibilityLevel")
        .build();

    return validateAccess(context).then(
        compatibilityLevelMono
            .flatMap(compatibilityLevelDTO ->
                schemaRegistryService.updateGlobalSchemaCompatibility(
                    getCluster(clusterName),
                    kafkaSrMapper.fromDto(compatibilityLevelDTO.getCompatibility())
                ))
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }

  /**
   * 更新指定 Schema 主题的兼容性级别
   *
   * @param clusterName            集群名称
   * @param subject                Schema 主题名称
   * @param compatibilityLevelMono 新的兼容性级别
   * @param exchange               服务器交换对象
   * @return 200 OK
   */
  @Override
  public Mono<ResponseEntity<Void>> updateSchemaCompatibilityLevel(
      String clusterName, String subject, @Valid Mono<CompatibilityLevelDTO> compatibilityLevelMono,
      ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .schemaActions(SchemaAction.EDIT)
        .operationName("updateSchemaCompatibilityLevel")
        .operationParams(Map.of("subject", subject))
        .build();

    return validateAccess(context).then(
        compatibilityLevelMono
            .flatMap(compatibilityLevelDTO ->
                schemaRegistryService.updateSchemaCompatibility(
                    getCluster(clusterName),
                    subject,
                    kafkaSrMapper.fromDto(compatibilityLevelDTO.getCompatibility())
                ))
            .doOnEach(sig -> audit(context, sig))
            .thenReturn(ResponseEntity.ok().build())
    );
  }
}
