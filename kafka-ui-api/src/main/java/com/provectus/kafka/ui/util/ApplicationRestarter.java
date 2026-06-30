package com.provectus.kafka.ui.util;

import com.provectus.kafka.ui.KafkaUiApplication;
import java.io.Closeable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 应用重启器
 *
 * 监听应用启动事件，在运行时实现应用的优雅重启。
 * 主要用于动态配置变更后需要重启生效的场景。
 *
 * 实现原理：
 * 1. 监听 {@link ApplicationStartedEvent} 保存启动参数和上下文
 * 2. 收到重启请求时，先关闭当前应用上下文（逐级关闭父上下文）
 * 3. 使用保存的原始启动参数重新启动应用
 *
 * 注意：重启在独立的非守护线程中执行，避免被 JVM 随主线程退出而终止。
 *
 * @author kafka-ui
 */
@Slf4j
@Component
public class ApplicationRestarter implements ApplicationListener<ApplicationStartedEvent> {

  /** 应用启动参数，用于重启时保持相同的启动配置 */
  private String[] applicationArgs;

  /** Spring 应用上下文，用于重启前关闭当前上下文 */
  private ApplicationContext applicationContext;

  /**
   * 监听应用启动事件，保存启动参数和应用上下文
   *
   * @param event 应用启动完成事件
   */
  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    this.applicationArgs = event.getArgs();
    this.applicationContext = event.getApplicationContext();
  }

  /**
   * 请求重启应用
   *
   * 在新的非守护线程中执行重启操作：
   * 1. 关闭当前应用上下文（包括所有父上下文）
   * 2. 使用原始启动参数重新启动应用
   *
   * 使用独立线程的原因：避免在关闭上下文时阻塞调用方，
   * 同时确保重启过程不被主线程退出干扰。
   */
  public void requestRestart() {
    log.info("Restarting application");
    Thread thread = new Thread(() -> {
      closeApplicationContext(applicationContext);
      KafkaUiApplication.startApplication(applicationArgs);
    });
    thread.setName("restartedMain-" + System.currentTimeMillis());
    thread.setDaemon(false);
    thread.start();
  }

  /**
   * 逐级关闭应用上下文
   *
   * 从当前上下文开始，沿 parent 链逐级关闭所有实现了 Closeable 接口的上下文。
   * 确保所有层级的 Spring 容器都被正确释放资源。
   *
   * @param context 当前应用上下文
   * @throws RuntimeException 如果关闭过程中发生异常
   */
  private void closeApplicationContext(ApplicationContext context) {
    while (context instanceof Closeable) {
      try {
        ((Closeable) context).close();
      } catch (Exception e) {
        log.warn("Error stopping application before restart", e);
        throw new RuntimeException(e);
      }
      context = context.getParent();
    }
  }
}
