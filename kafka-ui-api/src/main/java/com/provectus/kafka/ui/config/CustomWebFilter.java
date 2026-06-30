package com.provectus.kafka.ui.config;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 自定义 Web 过滤器，用于处理前端路由。
 * <p>
 * 该过滤器将以下路径请求重定向到 index.html，以支持前端 SPA（单页应用）的路由：
 * <ul>
 *   <li>以 "/ui" 开头的路径</li>
 *   <li>空路径</li>
 *   <li>根路径 "/"</li>
 * </ul>
 * 这确保了前端路由在页面刷新时能够正常工作。
 * </p>
 */
@Component
public class CustomWebFilter implements WebFilter {

  /**
   * 过滤请求并处理前端路由。
   * <p>
   * 对于 UI 相关路径，将请求转发到 index.html，保留原始的上下文路径。
   * 其他路径则正常传递给后续过滤器处理。
   * </p>
   *
   * @param exchange 当前请求的 ServerWebExchange
   * @param chain    过滤器链
   * @return 处理完成的 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

    // 获取上下文路径
    final String basePath = exchange.getRequest().getPath().contextPath().value();

    // 获取应用内的相对路径
    final String path = exchange.getRequest().getPath().pathWithinApplication().value();

    // 对于 UI 路径，重定向到 index.html 以支持前端路由
    if (path.startsWith("/ui") || path.equals("") || path.equals("/")) {
      return chain.filter(
          exchange.mutate().request(
              exchange.getRequest().mutate()
                  .path(basePath + "/index.html")
                  .contextPath(basePath)
                  .build()
          ).build()
      );
    }

    // 其他路径正常处理
    return chain.filter(exchange);
  }
}