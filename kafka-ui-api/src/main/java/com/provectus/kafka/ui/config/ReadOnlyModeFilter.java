package com.provectus.kafka.ui.config;

import com.provectus.kafka.ui.exception.ClusterNotFoundException;
import com.provectus.kafka.ui.exception.ReadOnlyModeException;
import com.provectus.kafka.ui.service.ClustersStorage;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 只读模式过滤器，用于拦截对只读集群的写操作。
 * <p>
 * 当集群配置为只读模式时，该过滤器会拦截所有非 GET 请求（即写操作），
 * 并抛出 {@link ReadOnlyModeException} 异常。
 * </p>
 * <p>
 * 处理逻辑：
 * <ol>
 *   <li>GET 请求（安全方法）直接放行</li>
 *   <li>从请求路径中解析集群名称</li>
 *   <li>如果路径不包含集群信息，直接放行</li>
 *   <li>检查集群是否存在，不存在则抛出异常</li>
 *   <li>如果集群为只读模式，抛出 ReadOnlyModeException</li>
 *   <li>否则正常处理请求</li>
 * </ol>
 * </p>
 */
@Order
@Component
@RequiredArgsConstructor
public class ReadOnlyModeFilter implements WebFilter {
  /** 集群名称匹配正则，从 API 路径中提取集群名称 */
  private static final Pattern CLUSTER_NAME_REGEX =
      Pattern.compile("/api/clusters/(?<clusterName>[^/]++)");

  /** 集群存储服务，用于查询集群配置 */
  private final ClustersStorage clustersStorage;

  /**
   * 过滤请求，拦截只读集群的写操作。
   *
   * @param exchange 当前请求的 ServerWebExchange
   * @param chain    过滤器链
   * @return 处理完成的 Mono，或抛出异常的 Mono.error
   */
  @NotNull
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, @NotNull WebFilterChain chain) {
    // GET 请求是安全的读操作，直接放行
    var isSafeMethod = exchange.getRequest().getMethod() == HttpMethod.GET;
    if (isSafeMethod) {
      return chain.filter(exchange);
    }

    // 解析请求路径，提取集群名称
    var path = exchange.getRequest().getPath().pathWithinApplication().value();
    var decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
    var matcher = CLUSTER_NAME_REGEX.matcher(decodedPath);

    // 路径不包含集群信息（如全局配置接口），直接放行
    if (!matcher.find()) {
      return chain.filter(exchange);
    }

    // 查找集群配置
    var clusterName = matcher.group("clusterName");
    var kafkaCluster = clustersStorage.getClusterByName(clusterName)
        .orElseThrow(
            () -> new ClusterNotFoundException(
                String.format("No cluster for name '%s'", clusterName)));

    // 集群非只读模式，正常处理
    if (!kafkaCluster.isReadOnly()) {
      return chain.filter(exchange);
    }

    // 集群为只读模式，拒绝写操作
    return Mono.error(ReadOnlyModeException::new);
  }
}
