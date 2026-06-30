package com.provectus.kafka.ui.controller;

import static com.provectus.kafka.ui.model.rbac.permission.ApplicationConfigAction.EDIT;
import static com.provectus.kafka.ui.model.rbac.permission.ApplicationConfigAction.VIEW;

import com.provectus.kafka.ui.api.ApplicationConfigApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.model.ApplicationConfigDTO;
import com.provectus.kafka.ui.model.ApplicationConfigPropertiesDTO;
import com.provectus.kafka.ui.model.ClusterConfigDTO;
import com.provectus.kafka.ui.model.ApplicationConfigValidationDTO;
import com.provectus.kafka.ui.model.ApplicationInfoDTO;
import com.provectus.kafka.ui.model.ClusterConfigValidationDTO;
import com.provectus.kafka.ui.model.RestartRequestDTO;
import com.provectus.kafka.ui.model.UploadedFileInfoDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.service.AdminClientService;
import com.provectus.kafka.ui.service.ApplicationInfoService;
import com.provectus.kafka.ui.service.ClusterHotReloadService;
import com.provectus.kafka.ui.service.ClustersStorage;
import com.provectus.kafka.ui.service.KafkaClusterFactory;
import com.provectus.kafka.ui.util.ApplicationRestarter;
import com.provectus.kafka.ui.util.DynamicConfigOperations;
import com.provectus.kafka.ui.util.DynamicConfigOperations.PropertiesStructure;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * 应用配置控制器
 *
 * 提供应用配置的 CRUD 操作，包括：
 * 1. 获取当前配置
 * 2. 保存新配置并重启服务（原有方式）
 * 3. 删除集群配置并重启服务（原有方式）
 * 4. 上传配置相关文件（如 SSL 证书）
 * 5. 验证集群配置是否可用
 * 6. 动态添加集群（热加载，不重启）
 * 7. 动态删除集群（热加载，不重启）
 *
 * @author kafka-ui
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ApplicationConfigController extends AbstractController implements ApplicationConfigApi {

    /** DTO 与内部模型的映射器 */
    private static final PropertiesMapper MAPPER = Mappers.getMapper(PropertiesMapper.class);

    /**
     * DTO 映射接口
     * 使用 MapStruct 自动实现 ApplicationConfigPropertiesDTO 与 PropertiesStructure 的转换
     */
    @Mapper
    interface PropertiesMapper {

        PropertiesStructure fromDto(ApplicationConfigPropertiesDTO dto);

        ApplicationConfigPropertiesDTO toDto(PropertiesStructure propertiesStructure);
    }

    /** 动态配置操作工具，负责配置的读取和持久化 */
    private final DynamicConfigOperations dynamicConfigOperations;

    /** 应用重启器，用于在配置变更后重启服务 */
    private final ApplicationRestarter restarter;

    /** Kafka 集群工厂，用于验证集群配置 */
    private final KafkaClusterFactory kafkaClusterFactory;

    /** 应用信息服务，提供应用版本等信息 */
    private final ApplicationInfoService applicationInfoService;

    /** 集群热加载服务，支持动态添加/删除集群 */
    private final ClusterHotReloadService clusterHotReloadService;

    /** 集群存储，管理内存中的集群实例 */
    private final ClustersStorage clustersStorage;

    /** AdminClient 服务，管理 Kafka AdminClient 的生命周期 */
    private final AdminClientService adminClientService;

    /**
     * 获取应用信息
     *
     * @param exchange 服务器交换对象
     * @return 应用信息 DTO（包含版本号等）
     */
    @Override
    public Mono<ResponseEntity<ApplicationInfoDTO>> getApplicationInfo(ServerWebExchange exchange) {
        return Mono.just(applicationInfoService.getApplicationInfo()).map(ResponseEntity::ok);
    }

    /**
     * 获取当前应用配置
     *
     * 需要 VIEW 权限。从 DynamicConfigOperations 读取当前配置，
     * 转换为 DTO 格式返回给前端。
     *
     * @param exchange 服务器交换对象
     * @return 当前应用配置 DTO
     */
    @Override
    public Mono<ResponseEntity<ApplicationConfigDTO>> getCurrentConfig(ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(VIEW)
                .operationName("getCurrentConfig")
                .build();
        return validateAccess(context)
                .then(Mono.fromSupplier(() -> ResponseEntity.ok(
                        new ApplicationConfigDTO()
                                .properties(MAPPER.toDto(dynamicConfigOperations.getCurrentProperties()))
                )))
                .doOnEach(sig -> audit(context, sig));
    }

    /**
     * 保存新配置并重启服务
     *
     * 需要 EDIT 权限。执行流程：
     * 1. 验证用户权限
     * 2. 将 DTO 转换为内部配置结构
     * 3. 持久化配置到文件
     * 4. 触发服务重启
     *
     * 注意：此方法会触发服务重启，导致短暂的服务不可用。
     * 如需不重启添加集群，请使用 addClusterHotReload 方法。
     *
     * @param restartRequestDto 重启请求 DTO，包含新配置
     * @param exchange          服务器交换对象
     * @return 200 OK
     */
    @Override
    public Mono<ResponseEntity<Void>> restartWithConfig(Mono<RestartRequestDTO> restartRequestDto,
                                                        ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("restartWithConfig")
                .build();
        return validateAccess(context)
                .then(restartRequestDto)
                .doOnNext(restartDto -> {
                    var newPropertiesStructure = MAPPER.fromDto(restartDto.getConfig().getProperties());
                    ClustersProperties newClustersProperties = newPropertiesStructure.getKafka();

                    if (newClustersProperties != null && newClustersProperties.getClusters() != null) {
                        // 获取当前集群名称集合
                        Set<String> currentClusterNames = clustersStorage.getKafkaClusters().stream()
                                .map(c -> c.getName())
                                .collect(Collectors.toSet());

                        // 获取新配置中的集群名称集合
                        Set<String> newClusterNames = newClustersProperties.getClusters().stream()
                                .map(c -> c.getName())
                                .collect(Collectors.toSet());

                        // 1. 对所有新配置中的集群，统一使用 replaceCluster
                        for (ClustersProperties.Cluster clusterConfig : newClustersProperties.getClusters()) {
                            String name = clusterConfig.getName();
                            log.info("Hot-reload: adding/replacing cluster {}", name);
                            adminClientService.closeClient(name);  // 关闭可能存在的旧 AdminClient
                            clustersStorage.replaceCluster(newClustersProperties, clusterConfig);
                        }

                        // 2. 删除不再存在的集群
                        for (String name : currentClusterNames) {
                            if (!newClusterNames.contains(name)) {
                                log.info("Hot-reload: removing cluster {}", name);
                                clustersStorage.removeCluster(name).ifPresent(removed -> {
                                    adminClientService.closeClient(name);
                                });
                            }
                        }
                    }

                    // 持久化配置
                    dynamicConfigOperations.persist(newPropertiesStructure);
                    log.info("Hot-reload: configuration persisted successfully");
                })
                .doOnEach(sig -> audit(context, sig))
                .map(dto -> ResponseEntity.ok().build());
    }

    /**
     * 删除集群配置并重启服务
     *
     * 需要 EDIT 权限。执行流程：
     * 1. 验证用户权限
     * 2. 从当前配置中移除指定集群
     * 3. 清理空的 polling 配置（避免重启报错）
     * 4. 持久化配置到文件
     * 5. 触发服务重启
     *
     * 注意：此方法会触发服务重启。如需不重启删除集群，
     * 请使用 deleteClusterHotReload 方法。
     *
     * @param clusterName 要删除的集群名称
     * @param exchange    服务器交换对象
     * @return 200 OK
     */
    @Override
    public Mono<ResponseEntity<Void>> deleteApplicationConfig(String clusterName, ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("deleteApplicationConfig")
                .build();
        return validateAccess(context)
                .then(Mono.defer(() -> {
                    // 1. 从存储中移除集群并清理资源
                    log.info("Hot-reload: deleting cluster {}", clusterName);
                    clustersStorage.removeCluster(clusterName).ifPresent(removed -> {
                        adminClientService.closeClient(clusterName);
                    });

                    // 2. 更新持久化配置
                    PropertiesStructure currentProperties = dynamicConfigOperations.getCurrentProperties();
                    currentProperties.getKafka().getClusters().removeIf(cluster -> cluster.getName().equals(clusterName));
                    ClustersProperties.PollingProperties polling = currentProperties.getKafka().getPolling();
                    if (polling != null && Objects.isNull(polling.getPollTimeoutMs()) && Objects.isNull(polling.getDefaultPageSize()) && Objects.isNull(polling.getMaxPageSize())) {
                        currentProperties.getKafka().setPolling(null);
                    }
                    var newConfig = MAPPER.fromDto(MAPPER.toDto(currentProperties));
                    dynamicConfigOperations.persist(newConfig);
                    log.info("Hot-reload: cluster {} deleted and config persisted", clusterName);
                    return Mono.empty();
                }))
                .doOnEach(sig -> audit(context, sig))
                .map(dto -> ResponseEntity.ok().build());
    }

    /**
     * 上传配置相关文件
     *
     * 需要 EDIT 权限。用于上传 SSL 证书、密钥等配置相关文件。
     * 文件会被保存到配置的上传目录中。
     *
     * @param fileFlux 文件流
     * @param exchange 服务器交换对象
     * @return 上传文件的信息（包含文件路径）
     */
    @Override
    public Mono<ResponseEntity<UploadedFileInfoDTO>> uploadConfigRelatedFile(Flux<Part> fileFlux,
                                                                             ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("uploadConfigRelatedFile")
                .build();
        return validateAccess(context)
                .then(fileFlux.single())
                .flatMap(file ->
                        dynamicConfigOperations.uploadConfigRelatedFile((FilePart) file)
                                .map(path -> new UploadedFileInfoDTO().location(path.toString()))
                                .map(ResponseEntity::ok))
                .doOnEach(sig -> audit(context, sig));
    }

    /**
     * 验证集群配置是否可用
     *
     * 需要 EDIT 权限。验证内容包括：
     * 1. Kafka 集群连接
     * 2. Schema Registry 连接（如已配置）
     * 3. KSQL 连接（如已配置）
     * 4. Kafka Connect 连接（如已配置）
     *
     * @param configDto 配置 DTO
     * @param exchange  服务器交换对象
     * @return 验证结果，包含各组件的验证状态
     */
    @Override
    public Mono<ResponseEntity<ApplicationConfigValidationDTO>> validateConfig(Mono<ApplicationConfigDTO> configDto,
                                                                               ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("validateConfig")
                .build();
        return validateAccess(context)
                .then(configDto)
                .flatMap(config -> {
                    PropertiesStructure newConfig = MAPPER.fromDto(config.getProperties());
                    ClustersProperties clustersProperties = newConfig.getKafka();
                    return validateClustersConfig(clustersProperties)
                            .map(validations -> new ApplicationConfigValidationDTO().clusters(validations));
                })
                .map(ResponseEntity::ok)
                .doOnEach(sig -> audit(context, sig));
    }

    /**
     * 动态添加集群（热加载，不重启）
     *
     * 需要 EDIT 权限。该方法支持在运行时添加新的 Kafka 集群，
     * 无需重启服务。添加成功后，集群立即可用。
     *
     * 执行流程：
     * 1. 验证用户权限
     * 2. 验证集群配置是否有效
     * 3. 创建集群实例并添加到内存存储
     * 4. 持久化配置到文件
     *
     * @param clusterConfig 集群配置 DTO
     * @param exchange      服务器交换对象
     * @return 200 OK 如果添加成功，400 Bad Request 如果配置无效
     */
    @Override
    public Mono<ResponseEntity<Void>> addClusterHotReload(
            Mono<ClusterConfigDTO> clusterConfigDTO,
            ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("addClusterHotReload")
                .build();
        ObjectMapper mapper = new ObjectMapper();
        return validateAccess(context)
                .then(clusterConfigDTO)
                .map(dto -> mapper.convertValue(dto, ClustersProperties.Cluster.class))
                .flatMap(config -> {
                    try {
                        clusterHotReloadService.addCluster(config);
                        return Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build());
                    } catch (IllegalArgumentException e) {
                        return Mono.<ResponseEntity<Void>>just(ResponseEntity.badRequest().build());
                    }
                })
                .doOnEach(sig -> audit(context, sig));
    }

    /**
     * 动态删除集群（热加载，不重启）
     *
     * 需要 EDIT 权限。该方法支持在运行时删除 Kafka 集群，
     * 无需重启服务。删除成功后，集群立即不可用。
     *
     * 执行流程：
     * 1. 验证用户权限
     * 2. 从内存存储中移除集群
     * 3. 持久化配置到文件
     *
     * @param clusterName 要删除的集群名称
     * @param exchange    服务器交换对象
     * @return 200 OK 如果删除成功，404 Not Found 如果集群不存在
     */
    @Override
    public Mono<ResponseEntity<Void>> deleteClusterHotReload(
            String clusterName,
            ServerWebExchange exchange) {
        var context = AccessContext.builder()
                .applicationConfigActions(EDIT)
                .operationName("deleteClusterHotReload")
                .build();
        return validateAccess(context)
                .then(Mono.defer(() -> {
                    boolean removed = clusterHotReloadService.removeCluster(clusterName);
                    if (removed) {
                        return Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build());
                    } else {
                        return Mono.<ResponseEntity<Void>>just(ResponseEntity.notFound().build());
                    }
                }))
                .doOnEach(sig -> audit(context, sig));
    }

    /**
     * 验证集群配置
     *
     * @param properties 集群配置属性
     * @return 包装在 Mono 中的验证结果映射，Key 为集群名称
     */
    private Mono<Map<String, ClusterConfigValidationDTO>> validateClustersConfig(
            @Nullable ClustersProperties properties) {
        if (properties == null || properties.getClusters() == null) {
            return Mono.just(Map.of());
        }
        properties.validateAndSetDefaults();
        return Flux.fromIterable(properties.getClusters())
                .flatMap(c -> kafkaClusterFactory.validate(c).map(v -> Tuples.of(c.getName(), v)))
                .collectMap(Tuple2::getT1, Tuple2::getT2);
    }
}
