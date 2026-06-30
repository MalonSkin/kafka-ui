package com.provectus.kafka.ui.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.provectus.kafka.ui.exception.SchemaCompatibilityException;
import com.provectus.kafka.ui.exception.SchemaNotFoundException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.model.KafkaCluster;
import com.provectus.kafka.ui.sr.api.KafkaSrClientApi;
import com.provectus.kafka.ui.sr.model.Compatibility;
import com.provectus.kafka.ui.sr.model.CompatibilityCheckResponse;
import com.provectus.kafka.ui.sr.model.CompatibilityConfig;
import com.provectus.kafka.ui.sr.model.CompatibilityLevelChange;
import com.provectus.kafka.ui.sr.model.NewSubject;
import com.provectus.kafka.ui.sr.model.SchemaSubject;
import com.provectus.kafka.ui.util.ReactiveFailover;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Schema Registry 管理服务。
 *
 * <p>提供 Schema Registry 的完整交互功能，主要包括：
 * <ul>
 *   <li>Schema 主题（Subject）的查询、注册和删除</li>
 *   <li>Schema 版本的管理（获取所有版本、指定版本、最新版本）</li>
 *   <li>Schema 兼容性级别的查询和更新（全局级别和主题级别）</li>
 *   <li>Schema 兼容性检查</li>
 * </ul>
 *
 * <p>通过 {@link ReactiveFailover} 封装的 {@link KafkaSrClientApi} 与 Schema Registry 交互，
 * 支持故障转移以保证高可用性。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SchemaRegistryService {

  private static final String LATEST = "latest";

  @AllArgsConstructor
  public static class SubjectWithCompatibilityLevel {
    @Delegate
    SchemaSubject subject;
    @Getter
    Compatibility compatibility;
  }

  private ReactiveFailover<KafkaSrClientApi> api(KafkaCluster cluster) {
    return cluster.getSchemaRegistryClient();
  }

  /**
   * 批量获取指定主题的最新版本 Schema 信息。
   *
   * @param cluster  目标 Kafka 集群
   * @param subjects 主题名称列表
   * @return 包含 {@link SubjectWithCompatibilityLevel} 列表的 Mono
   */
  public Mono<List<SubjectWithCompatibilityLevel>> getAllLatestVersionSchemas(KafkaCluster cluster,
                                                                              List<String> subjects) {
    return Flux.fromIterable(subjects)
        .concatMap(subject -> getLatestSchemaVersionBySubject(cluster, subject))
        .collect(Collectors.toList());
  }

  /**
   * 获取所有 Schema 主题（Subject）名称列表。
   *
   * @param cluster 目标 Kafka 集群
   * @return 包含主题名称列表的 Mono
   */
  public Mono<List<String>> getAllSubjectNames(KafkaCluster cluster) {
    return api(cluster)
        .mono(c -> c.getAllSubjectNames(null, false))
        .flatMapIterable(this::parseSubjectListString)
        .collectList();
  }

  @SneakyThrows
  private List<String> parseSubjectListString(String subjectNamesStr) {
    //workaround for https://github.com/spring-projects/spring-framework/issues/24734
    return new JsonMapper().readValue(subjectNamesStr, new TypeReference<List<String>>() {
    });
  }

  /**
   * 获取指定主题的所有版本 Schema 信息。
   *
   * @param cluster 目标 Kafka 集群
   * @param subject 主题名称
   * @return {@link SubjectWithCompatibilityLevel} 的响应式流
   */
  public Flux<SubjectWithCompatibilityLevel> getAllVersionsBySubject(KafkaCluster cluster, String subject) {
    Flux<Integer> versions = getSubjectVersions(cluster, subject);
    return versions.flatMap(version -> getSchemaSubjectByVersion(cluster, subject, version));
  }

  private Flux<Integer> getSubjectVersions(KafkaCluster cluster, String schemaName) {
    return api(cluster).flux(c -> c.getSubjectVersions(schemaName));
  }

  /**
   * 获取指定主题指定版本的 Schema 信息。
   *
   * @param cluster   目标 Kafka 集群
   * @param schemaName 主题名称
   * @param version    版本号
   * @return 包含 {@link SubjectWithCompatibilityLevel} 的 Mono
   */
  public Mono<SubjectWithCompatibilityLevel> getSchemaSubjectByVersion(KafkaCluster cluster,
                                                                       String schemaName,
                                                                       Integer version) {
    return getSchemaSubject(cluster, schemaName, String.valueOf(version));
  }

  /**
   * 获取指定主题的最新版本 Schema 信息。
   *
   * @param cluster    目标 Kafka 集群
   * @param schemaName 主题名称
   * @return 包含 {@link SubjectWithCompatibilityLevel} 的 Mono
   */
  public Mono<SubjectWithCompatibilityLevel> getLatestSchemaVersionBySubject(KafkaCluster cluster,
                                                                             String schemaName) {
    return getSchemaSubject(cluster, schemaName, LATEST);
  }

  private Mono<SubjectWithCompatibilityLevel> getSchemaSubject(KafkaCluster cluster, String schemaName,
                                                               String version) {
    return api(cluster)
        .mono(c -> c.getSubjectVersion(schemaName, version, false))
        .zipWith(getSchemaCompatibilityInfoOrGlobal(cluster, schemaName))
        .map(t -> new SubjectWithCompatibilityLevel(t.getT1(), t.getT2()))
        .onErrorResume(WebClientResponseException.NotFound.class, th -> Mono.error(new SchemaNotFoundException()));
  }

  /**
   * 删除指定主题指定版本的 Schema。
   *
   * @param cluster    目标 Kafka 集群
   * @param schemaName 主题名称
   * @param version    版本号
   * @return 删除完成的 Mono
   */
  public Mono<Void> deleteSchemaSubjectByVersion(KafkaCluster cluster, String schemaName, Integer version) {
    return deleteSchemaSubject(cluster, schemaName, String.valueOf(version));
  }

  /**
   * 删除指定主题的最新版本 Schema。
   *
   * @param cluster    目标 Kafka 集群
   * @param schemaName 主题名称
   * @return 删除完成的 Mono
   */
  public Mono<Void> deleteLatestSchemaSubject(KafkaCluster cluster, String schemaName) {
    return deleteSchemaSubject(cluster, schemaName, LATEST);
  }

  private Mono<Void> deleteSchemaSubject(KafkaCluster cluster, String schemaName, String version) {
    return api(cluster).mono(c -> c.deleteSubjectVersion(schemaName, version, false));
  }

  /**
   * 删除指定主题的所有版本 Schema。
   *
   * @param cluster    目标 Kafka 集群
   * @param schemaName 主题名称
   * @return 删除完成的 Mono
   */
  public Mono<Void> deleteSchemaSubjectEntirely(KafkaCluster cluster, String schemaName) {
    return api(cluster).mono(c -> c.deleteAllSubjectVersions(schemaName, false));
  }

  /**
   * Checks whether the provided schema duplicates the previous or not, creates a new schema
   * and then returns the whole content by requesting its latest version.
   */
  public Mono<SubjectWithCompatibilityLevel> registerNewSchema(KafkaCluster cluster,
                                                               String subject,
                                                               NewSubject newSchemaSubject) {
    return api(cluster)
        .mono(c -> c.registerNewSchema(subject, newSchemaSubject))
        .onErrorMap(WebClientResponseException.Conflict.class,
            th -> new SchemaCompatibilityException())
        .onErrorMap(WebClientResponseException.UnprocessableEntity.class,
            th -> new ValidationException("Invalid schema. Error from registry: " + th.getResponseBodyAsString()))
        .then(getLatestSchemaVersionBySubject(cluster, subject));
  }

  /**
   * 更新指定主题的 Schema 兼容性级别。
   *
   * @param cluster       目标 Kafka 集群
   * @param schemaName    主题名称
   * @param compatibility 兼容性级别
   * @return 更新完成的 Mono
   */
  public Mono<Void> updateSchemaCompatibility(KafkaCluster cluster,
                                              String schemaName,
                                              Compatibility compatibility) {
    return api(cluster)
        .mono(c -> c.updateSubjectCompatibilityLevel(
            schemaName, new CompatibilityLevelChange().compatibility(compatibility)))
        .then();
  }

  /**
   * 更新全局 Schema 兼容性级别。
   *
   * @param cluster       目标 Kafka 集群
   * @param compatibility 兼容性级别
   * @return 更新完成的 Mono
   */
  public Mono<Void> updateGlobalSchemaCompatibility(KafkaCluster cluster,
                                                    Compatibility compatibility) {
    return api(cluster)
        .mono(c -> c.updateGlobalCompatibilityLevel(new CompatibilityLevelChange().compatibility(compatibility)))
        .then();
  }

  /**
   * 获取指定主题的 Schema 兼容性级别。
   *
   * <p>若主题未设置独立的兼容性级别，则返回空（由调用方决定是否回退到全局级别）。
   *
   * @param cluster    目标 Kafka 集群
   * @param schemaName 主题名称
   * @return 包含兼容性级别的 Mono，若未设置则为空
   */
  public Mono<Compatibility> getSchemaCompatibilityLevel(KafkaCluster cluster,
                                                         String schemaName) {
    return api(cluster)
        .mono(c -> c.getSubjectCompatibilityLevel(schemaName, true))
        .map(CompatibilityConfig::getCompatibilityLevel)
        .onErrorResume(error -> Mono.empty());
  }

  /**
   * 获取全局 Schema 兼容性级别。
   *
   * @param cluster 目标 Kafka 集群
   * @return 包含全局兼容性级别的 Mono
   */
  public Mono<Compatibility> getGlobalSchemaCompatibilityLevel(KafkaCluster cluster) {
    return api(cluster)
        .mono(KafkaSrClientApi::getGlobalCompatibilityLevel)
        .map(CompatibilityConfig::getCompatibilityLevel);
  }

  private Mono<Compatibility> getSchemaCompatibilityInfoOrGlobal(KafkaCluster cluster,
                                                                 String schemaName) {
    return getSchemaCompatibilityLevel(cluster, schemaName)
        .switchIfEmpty(this.getGlobalSchemaCompatibilityLevel(cluster));
  }

  /**
   * 检查新 Schema 与指定主题最新版本的兼容性。
   *
   * @param cluster         目标 Kafka 集群
   * @param schemaName      主题名称
   * @param newSchemaSubject 待检查的新 Schema
   * @return 包含兼容性检查结果的 Mono
   */
  public Mono<CompatibilityCheckResponse> checksSchemaCompatibility(KafkaCluster cluster,
                                                                    String schemaName,
                                                                    NewSubject newSchemaSubject) {
    return api(cluster).mono(c -> c.checkSchemaCompatibility(schemaName, LATEST, true, newSchemaSubject));
  }
}
