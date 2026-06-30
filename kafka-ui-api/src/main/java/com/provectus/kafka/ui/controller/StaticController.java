package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.util.ResourceUtil;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 静态资源控制器
 *
 * 提供前端静态资源的渲染和缓存，包括：
 * 1. index.html 主页面（支持上下文路径替换）
 * 2. manifest.json PWA 配置文件
 *
 * <p>使用 AtomicReference 实现线程安全的单例缓存，
 * 首次请求时渲染文件并缓存，后续请求直接返回缓存内容。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StaticController {

  /** index.html 资源文件 */
  @Value("classpath:static/index.html")
  private Resource indexFile;

  /** manifest.json 资源文件 */
  @Value("classpath:static/manifest.json")
  private Resource manifestFile;

  /** 渲染后的 index.html 缓存（线程安全） */
  private final AtomicReference<String> renderedIndexFile = new AtomicReference<>();

  /** 渲染后的 manifest.json 缓存（线程安全） */
  private final AtomicReference<String> renderedManifestFile = new AtomicReference<>();

  /**
   * 获取渲染后的 index.html 页面
   *
   * 资源路径中的 "assets/ 会被替换为包含上下文路径的完整路径。
   *
   * @param exchange 服务器交换对象
   * @return 渲染后的 HTML 内容
   */
  @GetMapping(value = "/index.html", produces = {"text/html"})
  public Mono<ResponseEntity<String>> getIndex(ServerWebExchange exchange) {
    return Mono.just(ResponseEntity.ok(getRenderedFile(exchange, renderedIndexFile, indexFile)));
  }

  /**
   * 获取渲染后的 manifest.json 文件
   *
   * PWA 配置文件，资源路径中的 "assets/ 会被替换为包含上下文路径的完整路径。
   *
   * @param exchange 服务器交换对象
   * @return 渲染后的 JSON 内容
   */
  @GetMapping(value = "/manifest.json", produces = {"application/json"})
  public Mono<ResponseEntity<String>> getManifest(ServerWebExchange exchange) {
    return Mono.just(ResponseEntity.ok(getRenderedFile(exchange, renderedManifestFile, manifestFile)));
  }

  /**
   * 获取已渲染的文件内容（带缓存）
   *
   * 使用 AtomicReference 实现线程安全的懒加载缓存。首次调用时渲染文件并缓存，
   * 后续调用直接返回缓存内容。
   *
   * @param exchange     服务器交换对象（用于获取上下文路径）
   * @param renderedFile 缓存引用（线程安全的单例缓存）
   * @param file         原始资源文件
   * @return 渲染后的文件内容
   */
  public String getRenderedFile(ServerWebExchange exchange, AtomicReference<String> renderedFile, Resource file) {
    String rendered = renderedFile.get();
    if (rendered == null) {
      rendered = buildFile(file, exchange.getRequest().getPath().contextPath().value());
      if (renderedFile.compareAndSet(null, rendered)) {
        return rendered;
      } else {
        return renderedFile.get();
      }
    } else {
      return rendered;
    }
  }

  /**
   * 读取并渲染资源文件
   *
   * 将资源文件中的资产路径 "assets/ 替换为包含上下文路径的完整路径，
   * 并替换 PUBLIC-PATH-VARIABLE 占位符。
   *
   * @param file        原始资源文件
   * @param contextPath 应用上下文路径
   * @return 渲染后的文件内容
   */
  @SneakyThrows
  private String buildFile(Resource file, String contextPath) {
    return ResourceUtil.readAsString(file)
        .replace("\"assets/", "\"" + contextPath + "/assets/")
        .replace("PUBLIC-PATH-VARIABLE",  contextPath);
  }
}
