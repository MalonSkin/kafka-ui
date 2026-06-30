package com.provectus.kafka.ui.util;


import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.config.WebclientProperties;
import com.provectus.kafka.ui.config.auth.OAuthProperties;
import com.provectus.kafka.ui.config.auth.RoleBasedAccessControlProperties;
import com.provectus.kafka.ui.exception.FileUploadException;
import com.provectus.kafka.ui.exception.ValidationException;
import com.provectus.kafka.ui.service.ClusterConfigPersistenceService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 动态配置操作工具类。
 * <p>
 * 负责应用配置的动态加载、持久化和文件上传功能。
 * 该组件支持在运行时修改配置而无需重启服务（需要启用动态配置功能）。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>动态配置加载：从数据库和 YAML 文件加载配置，覆盖默认配置</li>
 *   <li>配置持久化：将集群配置变更写入数据库（通过 ClusterConfigPersistenceService）</li>
 *   <li>文件上传：上传 SSL 证书等配置相关文件</li>
 *   <li>配置验证：验证配置格式和必填字段</li>
 * </ul>
 * </p>
 * <p>
 * 配置持久化策略：集群配置优先写入数据库，同时保留 YAML 文件作为备份。
 * </p>
 *
 * @author kafka-ui
 */
@Slf4j
@Component
public class DynamicConfigOperations {

  /** 动态配置启用属性名 */
  static final String DYNAMIC_CONFIG_ENABLED_ENV_PROPERTY = "dynamic.config.enabled";

  /** Groovy 过滤启用属性名 */
  static final String FILTERING_GROOVY_ENABLED_PROPERTY = "filtering.groovy.enabled";

  /** 动态配置文件路径属性名 */
  static final String DYNAMIC_CONFIG_PATH_ENV_PROPERTY = "dynamic.config.path";

  /** 动态配置文件默认路径 */
  static final String DYNAMIC_CONFIG_PATH_ENV_PROPERTY_DEFAULT = "/etc/kafkaui/dynamic_config.yaml";

  /** 配置相关文件上传目录属性名 */
  static final String CONFIG_RELATED_UPLOADS_DIR_PROPERTY = "config.related.uploads.dir";

  /** 配置相关文件上传默认目录 */
  static final String CONFIG_RELATED_UPLOADS_DIR_DEFAULT = "/etc/kafkaui/uploads";

  /**
   * 创建动态配置属性初始化器。
   * <p>
   * 在应用启动时调用，将动态配置加载到 Spring Environment 中，
   * 使其优先级高于 application.yml 等静态配置文件。
   * </p>
   *
   * @return 应用上下文初始化器
   */
  public static ApplicationContextInitializer<ConfigurableApplicationContext> dynamicConfigPropertiesInitializer() {
    return appCtx -> {
      // 修复：ApplicationContextInitializer 阶段 application.yml 可能尚未完全加载，
      // 直接调用 getProperty("spring.datasource.url") 可能返回 null。
      // 改为多来源检查：先查系统属性/环境变量，再遍历已加载的 PropertySources。
      String datasourceUrl = resolveProperty(appCtx, "spring.datasource.url");
      String driverClassName = resolveProperty(appCtx, "spring.datasource.driverClassName");

      if ((datasourceUrl != null && !datasourceUrl.isEmpty())
          || (driverClassName != null && !driverClassName.isEmpty())) {
        org.slf4j.LoggerFactory.getLogger(DynamicConfigOperations.class)
            .info("检测到数据库配置（url={}, driver={}），跳过 YAML 动态配置文件加载，将从数据库加载集群配置",
                datasourceUrl, driverClassName);
        return;
      }
      // 无数据库配置时，从 YAML 文件加载（兼容纯文件模式）
      new DynamicConfigOperations(appCtx, null)
          .loadDynamicPropertySource()
          .ifPresent(source -> appCtx.getEnvironment().getPropertySources().addFirst(source));
    };
  }

  /**
   * 从多个来源解析属性值。
   * <p>
   * 在 ApplicationContextInitializer 阶段，application.yml 可能尚未加载到 Environment 中，
   * 因此需要依次检查以下来源：
   * <ol>
   *   <li>系统属性（-D 参数）</li>
   *   <li>环境变量（SPRING_DATASOURCE_URL 等）</li>
   *   <li>已加载的 PropertySources（可能包含 application.yml）</li>
   * </ol>
   *
   * @param appCtx  应用上下文
   * @param property 属性名（如 spring.datasource.url）
   * @return 属性值，如果所有来源均未找到则返回 null
   */
  private static String resolveProperty(ConfigurableApplicationContext appCtx, String property) {
    // 1. 检查系统属性（-Dspring.datasource.url=xxx）
    String value = System.getProperty(property);
    if (value != null && !value.isEmpty()) {
      return value;
    }
    // 2. 检查环境变量（Spring Boot 支持 SPRING_DATASOURCE_URL 格式）
    String envKey = property.replace('.', '_').toUpperCase();
    value = System.getenv(envKey);
    if (value != null && !value.isEmpty()) {
      return value;
    }
    // 3. 遍历已加载的 PropertySources 查找（此时 application.yml 可能已部分加载）
    for (PropertySource<?> ps : appCtx.getEnvironment().getPropertySources()) {
      Object propVal = ps.getProperty(property);
      if (propVal != null && !propVal.toString().isEmpty()) {
        return propVal.toString();
      }
    }
    return null;
  }

  /** Spring 应用上下文 */
  private final ConfigurableApplicationContext ctx;

  /** 集群配置持久化服务（可选，启动时可能为 null） */
  @Nullable
  private final ClusterConfigPersistenceService persistenceService;

  /**
   * 构造函数（向后兼容，持久化服务为 null）。
   *
   * @param ctx Spring 应用上下文
   */
  public DynamicConfigOperations(ConfigurableApplicationContext ctx) {
    this(ctx, null);
  }

  /**
   * 构造函数。
   *
   * @param ctx               Spring 应用上下文
   * @param persistenceService 集群配置持久化服务
   */
  @org.springframework.beans.factory.annotation.Autowired
  public DynamicConfigOperations(ConfigurableApplicationContext ctx,
                                  @Nullable ClusterConfigPersistenceService persistenceService) {
    this.ctx = ctx;
    this.persistenceService = persistenceService;
  }

  /**
   * 检查动态配置是否启用。
   *
   * @return true 如果 dynamic.config.enabled=true
   */
  public boolean dynamicConfigEnabled() {
    return "true".equalsIgnoreCase(ctx.getEnvironment().getProperty(DYNAMIC_CONFIG_ENABLED_ENV_PROPERTY));
  }

  /**
   * 检查 Groovy 过滤是否启用。
   *
   * @return true 如果 filtering.groovy.enabled=true
   */
  public boolean filteringGroovyEnabled() {
    return "true".equalsIgnoreCase(ctx.getEnvironment().getProperty(FILTERING_GROOVY_ENABLED_PROPERTY));
  }

  /**
   * 获取动态配置文件路径。
   *
   * @return 配置文件路径
   */
  private Path dynamicConfigFilePath() {
    return Paths.get(
        Optional.ofNullable(ctx.getEnvironment().getProperty(DYNAMIC_CONFIG_PATH_ENV_PROPERTY))
            .orElse(DYNAMIC_CONFIG_PATH_ENV_PROPERTY_DEFAULT)
    );
  }

  /**
   * 加载动态配置属性源。
   * <p>
   * 从 YAML 文件加载配置，创建 PropertySource 对象。
   * 如果动态配置未启用或文件不存在，返回 empty。
   * </p>
   *
   * @return 包装在 Optional 中的属性源
   */
  @SneakyThrows
  public Optional<PropertySource<?>> loadDynamicPropertySource() {
    if (dynamicConfigEnabled()) {
      Path configPath = dynamicConfigFilePath();
      if (!Files.exists(configPath) || !Files.isReadable(configPath)) {
        log.warn("动态配置文件 {} 不存在或不可读", configPath);
        return Optional.empty();
      }
      var propertySource = new CompositePropertySource("dynamicProperties");
      new YamlPropertySourceLoader()
          .load("dynamicProperties", new FileSystemResource(configPath))
          .forEach(propertySource::addPropertySource);
      log.info("动态配置已从 {} 加载", configPath);
      return Optional.of(propertySource);
    }
    return Optional.empty();
  }

  /**
   * 获取当前应用配置。
   * <p>
   * 优先从数据库加载集群配置，如果数据库不可用则从配置 Bean 获取。
   * </p>
   *
   * @return 当前配置结构
   * @throws ValidationException 如果动态配置未启用
   */
  public PropertiesStructure getCurrentProperties() {
    checkIfDynamicConfigEnabled();

    // 获取 Kafka 集群配置：优先从数据库加载
    ClustersProperties kafkaProperties = getNullableBean(ClustersProperties.class);
    if (persistenceService != null) {
      try {
        List<ClustersProperties.Cluster> dbClusters = persistenceService.loadAllClusters();
        if (!dbClusters.isEmpty()) {
          if (kafkaProperties == null) {
            kafkaProperties = new ClustersProperties();
          }
          kafkaProperties.setClusters(new ArrayList<>(dbClusters));
          log.debug("从数据库加载了 {} 个集群配置", dbClusters.size());
        }
      } catch (Exception e) {
        log.warn("从数据库加载集群配置失败，使用配置 Bean 中的值", e);
      }
    }

    return PropertiesStructure.builder()
        .kafka(kafkaProperties)
        .rbac(getNullableBean(RoleBasedAccessControlProperties.class))
        .auth(
            PropertiesStructure.Auth.builder()
                .type(ctx.getEnvironment().getProperty("auth.type"))
                .oauth2(getNullableBean(OAuthProperties.class))
                .build())
        .webclient(getNullableBean(WebclientProperties.class))
        .build();
  }

  /**
   * 从容器中获取 Bean，如果不存在返回 null。
   *
   * @param clazz Bean 类型
   * @return Bean 实例或 null
   */
  @Nullable
  private <T> T getNullableBean(Class<T> clazz) {
    try {
      return ctx.getBean(clazz);
    } catch (NoSuchBeanDefinitionException nsbde) {
      return null;
    }
  }

  /**
   * 持久化配置到数据库。
   * <p>
   * 将配置中的每个集群保存到数据库。同时保留 YAML 文件写入作为备份。
   * </p>
   *
   * @param properties 配置结构
   * @throws ValidationException 如果动态配置未启用或写入失败
   */
  public void persist(PropertiesStructure properties) {
    checkIfDynamicConfigEnabled();
    properties.initAndValidate();

    // 保存集群配置到数据库
    if (persistenceService != null && properties.getKafka() != null
        && properties.getKafka().getClusters() != null) {
      for (ClustersProperties.Cluster cluster : properties.getKafka().getClusters()) {
        try {
          persistenceService.saveCluster(cluster);
          log.info("集群配置已持久化到数据库: {}", cluster.getName());
        } catch (Exception e) {
          log.error("持久化集群配置到数据库失败: {}", cluster.getName(), e);
          throw new ValidationException("持久化集群配置失败: " + cluster.getName(), e);
        }
      }
    }

    // 同时写入 YAML 文件作为备份（保持向后兼容）
    try {
      String yaml = serializeToYaml(properties);
      writeYamlToFile(yaml, dynamicConfigFilePath());
      log.info("配置已备份到 YAML 文件: {}", dynamicConfigFilePath());
    } catch (Exception e) {
      // YAML 备份失败不影响主流程，仅记录警告
      log.warn("写入 YAML 备份文件失败（数据库已更新）: {}", e.getMessage());
    }
  }

  /**
   * 上传配置相关文件。
   * <p>
   * 将上传的文件保存到配置的上传目录中。
   * 文件名格式：{原始文件名}-{时间戳}
   * </p>
   *
   * @param file 上传的文件
   * @return 包装在 Mono 中的文件保存路径
   * @throws ValidationException 如果动态配置未启用
   * @throws FileUploadException 如果文件上传失败
   */
  public Mono<Path> uploadConfigRelatedFile(FilePart file) {
    checkIfDynamicConfigEnabled();
    String targetDirStr = ctx.getEnvironment()
        .getProperty(CONFIG_RELATED_UPLOADS_DIR_PROPERTY, CONFIG_RELATED_UPLOADS_DIR_DEFAULT);

    Path targetDir = Path.of(targetDirStr);
    if (!Files.exists(targetDir)) {
      try {
        Files.createDirectories(targetDir);
      } catch (IOException e) {
        return Mono.error(
            new FileUploadException("Error creating directory for uploads %s".formatted(targetDir), e));
      }
    }

    Path targetFilePath = targetDir.resolve(file.filename() + "-" + Instant.now().getEpochSecond());
    log.info("上传配置相关文件 {}", targetFilePath);
    if (Files.exists(targetFilePath)) {
      log.info("文件 {} 已存在，将被覆盖", targetFilePath);
    }

    return file.transferTo(targetFilePath)
        .thenReturn(targetFilePath)
        .doOnError(th -> log.error("上传文件失败 {}", targetFilePath, th))
        .onErrorMap(th -> new FileUploadException(targetFilePath, th));
  }

  /**
   * 检查 Groovy 过滤是否启用，如果未启用则抛出异常。
   *
   * @throws ValidationException 如果 Groovy 过滤未启用
   */
  public void checkIfFilteringGroovyEnabled() {
    if (!filteringGroovyEnabled()) {
      throw new ValidationException(
              "Groovy filters is not allowed. "
                      + "Set filtering.groovy.enabled property to 'true' to enabled it.");
    }
  }

  /**
   * 检查动态配置是否启用，如果未启用则抛出异常。
   *
   * @throws ValidationException 如果动态配置未启用
   */
  private void checkIfDynamicConfigEnabled() {
    if (!dynamicConfigEnabled()) {
      throw new ValidationException(
          "Dynamic config change is not allowed. "
              + "Set dynamic.config.enabled property to 'true' to enabled it.");
    }
  }

  /**
   * 将 YAML 字符串写入文件。
   *
   * @param yaml YAML 字符串
   * @param path 目标文件路径
   * @throws ValidationException 如果文件路径是目录或写入失败
   */
  @SneakyThrows
  private void writeYamlToFile(String yaml, Path path) {
    if (Files.isDirectory(path)) {
      throw new ValidationException("Dynamic file path is a directory, but should be a file path");
    }
    if (!Files.exists(path.getParent())) {
      Files.createDirectories(path.getParent());
    }
    if (Files.exists(path) && !Files.isWritable(path)) {
      throw new ValidationException("File already exists and is not writable");
    }
    try {
      Files.writeString(
          path,
          yaml,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
      );
    } catch (IOException e) {
      throw new ValidationException("Error writing to " + path, e);
    }
  }

  /**
   * 将配置结构序列化为 YAML 字符串。
   * <p>
   * 使用自定义的 Representer 跳过 null 值字段，生成格式化的 YAML。
   * </p>
   *
   * @param props 配置结构
   * @return YAML 字符串
   */
  private String serializeToYaml(PropertiesStructure props) {
    Representer representer = new Representer(new DumperOptions()) {
      @Override
      protected NodeTuple representJavaBeanProperty(Object javaBean,
                                                    Property property,
                                                    Object propertyValue,
                                                    Tag customTag) {
        if (propertyValue == null) {
          return null;
        } else {
          return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }
      }
    };
    var propertyUtils = new PropertyUtils();
    propertyUtils.setBeanAccess(BeanAccess.FIELD);
    representer.setPropertyUtils(propertyUtils);
    representer.addClassTag(PropertiesStructure.class, Tag.MAP);
    representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    return new Yaml(representer).dump(props);
  }

  ///---------------------------------------------------------------------

  /**
   * 配置结构体。
   * <p>
   * 定义应用的完整配置结构，包括：
   * <ul>
   *   <li>kafka: Kafka 集群配置</li>
   *   <li>rbac: 基于角色的访问控制配置</li>
   *   <li>auth: 认证配置（OAuth2 等）</li>
   *   <li>webclient: WebClient 配置</li>
   * </ul>
   * </p>
   */
  @Data
  @Builder
  public static class PropertiesStructure {

    /** Kafka 集群配置 */
    private ClustersProperties kafka;

    /** RBAC 配置 */
    private RoleBasedAccessControlProperties rbac;

    /** 认证配置 */
    private Auth auth;

    /** WebClient 配置 */
    private WebclientProperties webclient;

    /**
     * 认证配置内部类
     */
    @Data
    @Builder
    public static class Auth {
      /** 认证类型（oauth2, ldap 等） */
      String type;
      /** OAuth2 配置 */
      OAuthProperties oauth2;
    }

    /**
     * 初始化并验证配置。
     * <p>
     * 对各配置模块进行初始化和验证：
     * <ul>
     *   <li>kafka: 验证集群名称和属性</li>
     *   <li>rbac: 初始化 RBAC 规则</li>
     *   <li>auth.oauth2: 初始化 OAuth2 配置</li>
     *   <li>webclient: 验证 WebClient 配置</li>
     * </ul>
     * </p>
     */
    public void initAndValidate() {
      Optional.ofNullable(kafka)
          .ifPresent(ClustersProperties::validateAndSetDefaults);

      Optional.ofNullable(rbac)
          .ifPresent(RoleBasedAccessControlProperties::init);

      Optional.ofNullable(auth)
          .flatMap(a -> Optional.ofNullable(a.oauth2))
          .ifPresent(OAuthProperties::init);

      Optional.ofNullable(webclient)
          .ifPresent(WebclientProperties::validate);
    }
  }

}
