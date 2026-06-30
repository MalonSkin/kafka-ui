package com.provectus.kafka.ui.controller;

import java.nio.charset.Charset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 认证控制器
 *
 * 提供表单登录页面的渲染和 CSRF Token 处理，用于不使用 OAuth2/OIDC 的基本认证场景。
 * 渲染一个简单的 HTML 登录页面，包含用户名/密码输入框和 CSRF 令牌。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  /**
   * 获取认证登录页面
   *
   * 渲染包含 CSRF Token 的 HTML 登录表单页面。支持错误提示和登出成功提示。
   *
   * @param exchange 服务器交换对象
   * @return HTML 页面字节数组
   */
  @GetMapping(value = "/auth", produces = {"text/html"})
  public Mono<byte[]> getAuth(ServerWebExchange exchange) {
    Mono<CsrfToken> token = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
    return token
        .map(AuthController::csrfToken)
        .defaultIfEmpty("")
        .map(csrfTokenHtmlInput -> createPage(exchange, csrfTokenHtmlInput));
  }

  /**
   * 构建完整的 HTML 登录页面
   *
   * @param exchange           服务器交换对象（用于获取上下文路径）
   * @param csrfTokenHtmlInput CSRF Token 的 HTML 隐藏输入字段
   * @return HTML 页面字节数组
   */
  private byte[] createPage(ServerWebExchange exchange, String csrfTokenHtmlInput) {
    MultiValueMap<String, String> queryParams = exchange.getRequest()
        .getQueryParams();
    String contextPath = exchange.getRequest().getPath().contextPath().value();
    String page =
        "<!DOCTYPE html>\n" + "<html lang=\"en\">\n" + "  <head>\n"
        + "    <meta charset=\"utf-8\">\n"
        + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, "
        + "shrink-to-fit=no\">\n"
        + "    <meta name=\"description\" content=\"\">\n"
        + "    <meta name=\"author\" content=\"\">\n"
        + "    <title>Please sign in</title>\n"
        + "    <link href=\"" + contextPath + "/static/css/bootstrap.min.css\" rel=\"stylesheet\" "
        + "integrity=\"sha384-/Y6pD6FV/Vv2HJnA6t+vslU6fwYXjCFtcEpHbNJ0lyAFsXTsjBbfaDjzALeQsN6M\" "
        + "crossorigin=\"anonymous\">\n"
        + "    <link href=\"" + contextPath + "/static/css/signin.css\" "
        + "rel=\"stylesheet\" crossorigin=\"anonymous\"/>\n"
        + "  </head>\n"
        + "  <body>\n"
        + "     <div class=\"container\">\n"
        + formLogin(queryParams, contextPath, csrfTokenHtmlInput)
        + "    </div>\n"
        + "  </body>\n"
        + "</html>";

    return page.getBytes(Charset.defaultCharset());
  }

  /**
   * 构建登录表单 HTML
   *
   * @param queryParams        查询参数（用于判断是否显示错误/登出提示）
   * @param contextPath        应用上下文路径
   * @param csrfTokenHtmlInput CSRF Token 的 HTML 隐藏输入字段
   * @return 登录表单 HTML 字符串
   */
  private String formLogin(
      MultiValueMap<String, String> queryParams,
      String contextPath, String csrfTokenHtmlInput) {

    boolean isError = queryParams.containsKey("error");
    boolean isLogoutSuccess = queryParams.containsKey("logout");
    return
        "      <form class=\"form-signin\" method=\"post\" action=\"" + contextPath + "/auth\">\n"
        + "        <h2 class=\"form-signin-heading\">Please sign in</h2>\n"
        + createError(isError)
        + createLogoutSuccess(isLogoutSuccess)
        + "        <p>\n"
        + "          <label for=\"username\" class=\"sr-only\">Username</label>\n"
        + "          <input type=\"text\" id=\"username\" name=\"username\" class=\"form-control\" "
        + "placeholder=\"Username\" required autofocus>\n"
        + "        </p>\n" + "        <p>\n"
        + "          <label for=\"password\" class=\"sr-only\">Password</label>\n"
        + "          <input type=\"password\" id=\"password\" name=\"password\" "
        + "class=\"form-control\" placeholder=\"Password\" required>\n"
        + "        </p>\n" + csrfTokenHtmlInput
        + "        <button class=\"btn btn-lg btn-primary btn-block\" "
        + "type=\"submit\">Sign in</button>\n"
        + "      </form>\n";
  }

  /**
   * 将 CsrfToken 转换为 HTML 隐藏输入字段
   *
   * @param token CSRF Token 对象
   * @return HTML 隐藏输入字段字符串
   */
  private static String csrfToken(CsrfToken token) {
    return "          <input type=\"hidden\" name=\""
        + token.getParameterName()
        + "\" value=\""
        + token.getToken()
        + "\">\n";
  }

  /**
   * 创建认证错误提示 HTML
   *
   * @param isError 是否显示错误提示
   * @return 错误提示 HTML 字符串，无错误时返回空字符串
   */
  private static String createError(boolean isError) {
    return isError
        ? "<div class=\"alert alert-danger\" role=\"alert\">Invalid credentials</div>"
        : "";
  }

  /**
   * 创建登出成功提示 HTML
   *
   * @param isLogoutSuccess 是否显示登出成功提示
   * @return 登出成功提示 HTML 字符串，无提示时返回空字符串
   */
  private static String createLogoutSuccess(boolean isLogoutSuccess) {
    return isLogoutSuccess
        ? "<div class=\"alert alert-success\" role=\"alert\">You have been signed out</div>"
        : "";
  }
}
