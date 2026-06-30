package com.provectus.kafka.ui.config;

import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ContextPathCompositeHandler;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.util.StringUtils;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

/**
 * 应用通用配置类。
 * <p>
 * 提供应用级别的基础 Bean 配置，包括：
 * <ul>
 *   <li>HTTP 请求处理器（支持上下文路径配置）</li>
 *   <li>JMX MBean 导出器（用于监控指标暴露）</li>
 *   <li>JSON Nullable 模块（支持 OpenAPI 生成的可空字段序列化）</li>
 * </ul>
 * </p>
 */
@Configuration
@AllArgsConstructor
public class Config {

  /** Spring 应用上下文 */
  private final ApplicationContext applicationContext;

  /** 服务器配置属性 */
  private final ServerProperties serverProperties;

  /**
   * 创建 HTTP 请求处理器 Bean。
   * <p>
   * 构建 WebFlux 的 HttpHandler，如果配置了上下文路径（context-path），
   * 则使用 ContextPathCompositeHandler 包装，使所有请求自动带上前缀。
   * </p>
   *
   * @param propsProvider WebFlux 属性提供者（预留扩展）
   * @return 配置好的 HttpHandler 实例
   */
  @Bean
  public HttpHandler httpHandler(ObjectProvider<WebFluxProperties> propsProvider) {

    final String basePath = serverProperties.getServlet().getContextPath();

    HttpHandler httpHandler = WebHttpHandlerBuilder
        .applicationContext(this.applicationContext).build();

    // 如果配置了上下文路径，使用 ContextPathCompositeHandler 包装
    if (StringUtils.hasText(basePath)) {
      Map<String, HttpHandler> handlersMap =
          Collections.singletonMap(basePath, httpHandler);
      return new ContextPathCompositeHandler(handlersMap);
    }
    return httpHandler;
  }

  /**
   * 创建 JMX MBean 导出器 Bean。
   * <p>
   * 自动检测并注册所有符合条件的 MBean，
   * 排除名为 "pool" 的 Bean（避免连接池相关的 MBean 冲突）。
   * </p>
   *
   * @return 配置好的 MBeanExporter 实例
   */
  @Bean
  public MBeanExporter exporter() {
    final var exporter = new MBeanExporter();
    exporter.setAutodetect(true);
    // 排除连接池相关的 MBean，避免潜在冲突
    exporter.setExcludedBeans("pool", "dataSource");
    return exporter;
  }

  /**
   * 创建 JsonNullable 模块 Bean。
   * <p>
   * 该模块支持 OpenAPI 生成代码中的 {@code JsonNullable<T>} 类型的序列化/反序列化，
   * 使 WebFlux JSON 映射能够正确处理可空字段。
   * </p>
   *
   * @return JsonNullableModule 实例
   */
  @Bean
  public JsonNullableModule jsonNullableModule() {
    return new JsonNullableModule();
  }
}
