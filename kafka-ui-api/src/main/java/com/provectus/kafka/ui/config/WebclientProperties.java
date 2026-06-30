package com.provectus.kafka.ui.config;

import com.provectus.kafka.ui.exception.ValidationException;
import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * WebClient 配置属性类。
 * <p>
 * 绑定以 "webclient" 为前缀的配置项，用于配置 Spring WebClient 的行为参数。
 * 主要控制内存缓冲区大小，影响处理大型响应的能力。
 * </p>
 * <p>
 * 配置示例：
 * <pre>
 * webclient:
 *   maxInMemoryBufferSize: 10MB
 * </pre>
 * </p>
 */
@Configuration
@ConfigurationProperties("webclient")
@Data
public class WebclientProperties {

  /**
   * WebClient 内存缓冲区最大大小。
   * <p>
   * 支持 Spring DataSize 格式，如 "10MB"、"1GB" 等。
   * 该值影响 WebClient 处理大型响应的能力。
   * </p>
   */
  String maxInMemoryBufferSize;

  /**
   * 初始化时验证配置格式。
   */
  @PostConstruct
  public void validate() {
    validateAndSetDefaultBufferSize();
  }

  /**
   * 验证缓冲区大小配置的格式。
   * <p>
   * 如果配置了 maxInMemoryBufferSize，则验证其是否为合法的 DataSize 格式。
   * 验证失败时抛出 ValidationException。
   * </p>
   *
   * @throws ValidationException 当配置格式无效时抛出
   */
  private void validateAndSetDefaultBufferSize() {
    if (maxInMemoryBufferSize != null) {
      try {
        DataSize.parse(maxInMemoryBufferSize);
      } catch (Exception e) {
        throw new ValidationException("Invalid format for webclient.maxInMemoryBufferSize");
      }
    }
  }

}
