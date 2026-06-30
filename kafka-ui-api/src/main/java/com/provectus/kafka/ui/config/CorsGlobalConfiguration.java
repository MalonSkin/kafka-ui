package com.provectus.kafka.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CORS（跨域资源共享）全局配置。
 * <p>
 * 配置 WebFilter 来处理跨域请求，允许前端应用从不同域名访问 API。
 * 对于预检请求（OPTIONS 方法），直接返回 200 OK 而不继续处理链。
 * </p>
 * <p>
 * 配置说明：
 * <ul>
 *   <li>允许所有来源（Access-Control-Allow-Origin: *）</li>
 *   <li>允许 GET、PUT、POST、DELETE、OPTIONS 方法</li>
 *   <li>预检请求缓存时间为 3600 秒</li>
 *   <li>允许 Content-Type 请求头</li>
 * </ul>
 * </p>
 */
@Configuration
public class CorsGlobalConfiguration {

  /**
   * 创建 CORS 过滤器 Bean。
   * <p>
   * 为每个响应添加 CORS 相关的 HTTP 头。
   * 对于 OPTIONS 预检请求，直接返回 OK 状态码，不继续执行后续过滤器。
   * </p>
   *
   * @return CORS WebFilter 实例
   */
  @Bean
  public WebFilter corsFilter() {
    return (final ServerWebExchange ctx, final WebFilterChain chain) -> {
      final ServerHttpRequest request = ctx.getRequest();

      final ServerHttpResponse response = ctx.getResponse();
      final HttpHeaders headers = response.getHeaders();
      // 添加 CORS 响应头
      headers.add("Access-Control-Allow-Origin", "*");
      headers.add("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS");
      headers.add("Access-Control-Max-Age", "3600");
      headers.add("Access-Control-Allow-Headers", "Content-Type");

      // 预检请求直接返回，不继续处理
      if (request.getMethod() == HttpMethod.OPTIONS) {
        response.setStatusCode(HttpStatus.OK);
        return Mono.empty();
      }

      return chain.filter(ctx);
    };
  }

}
