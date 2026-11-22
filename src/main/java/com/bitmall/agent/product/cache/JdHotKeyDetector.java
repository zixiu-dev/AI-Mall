package com.bitmall.agent.product.cache;

import com.bitmall.agent.product.config.HotKeyProperties;
import com.jd.platform.hotkey.client.ClientStarter;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.jd.platform.hotkey.client.core.worker.WorkerInfoHolder;
import com.jd.platform.hotkey.client.log.HotKeyLogger;
import com.jd.platform.hotkey.client.log.JdLogger;

import lombok.RequiredArgsConstructor;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import io.netty.channel.Channel;

/** 京东 HotKey 的项目适配器。 应用就绪后在后台启动客户端，查询时上报访问并读取热点标记；启动异常时记录日志，让商城继续使用 Redis 和数据库。 */
@Component
@RequiredArgsConstructor
public class JdHotKeyDetector implements HotKeyDetector {
    /** 读取京东 HotKey 的开关、应用名、etcd 地址和上报参数。 */
    private final HotKeyProperties properties;

    /** 只允许触发一次启动流程；它不是 Worker 已连接成功的证明。 */
    private final AtomicBoolean started = new AtomicBoolean();

    /** 应用就绪后启动一次京东 HotKey 客户端；通过后台守护线程避免初始化阻塞启动事件。 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled() || !started.compareAndSet(false, true)) {
            return;
        }
        // 京东客户端会启动全进程上报和监听线程，普通测试保持关闭。
        JdLogger.setLogger(
                new HotKeyLogger() {
                    /** 把京东客户端的 debug 级日志转交给项目的 SLF4J 日志系统。 */
                    public void debug(Class<?> c, String s) {
                        LoggerFactory.getLogger(c).debug(s);
                    }

                    /** 把京东客户端的 info 级日志转交给项目的 SLF4J 日志系统。 */
                    public void info(Class<?> c, String s) {
                        LoggerFactory.getLogger(c).info(s);
                    }

                    /** 把京东客户端的 warn 级日志转交给项目的 SLF4J 日志系统。 */
                    public void warn(Class<?> c, String s) {
                        LoggerFactory.getLogger(c).warn(s);
                    }

                    /** 把京东客户端的 error 级日志转交给项目的 SLF4J 日志系统。 */
                    public void error(Class<?> c, String s) {
                        LoggerFactory.getLogger(c).error(s);
                    }
                });
        Thread boot =
                new Thread(
                        () -> {
                            try {
                                new ClientStarter.Builder()
                                        .setAppName(properties.getAppName())
                                        .setEtcdServer(properties.getEtcdServer())
                                        .setPushPeriod(properties.getPushPeriodMillis())
                                        .setCaffeineSize(properties.getMaximumKeys())
                                        .build()
                                        .startPipeline();
                            } catch (Exception | LinkageError ex) {
                                started.set(false);
                                LoggerFactory.getLogger(getClass())
                                        .warn("HotKey 启动失败，继续使用 Redis/数据库查询", ex);
                            }
                        },
                        "jd-hotkey-bootstrap");
        boot.setDaemon(true);
        boot.start();
    }

    /** 只有功能开启且启动流程已触发时，才查询京东客户端中的热点标记。 */
    @Override
    public boolean isHot(String key) {
        return properties.isEnabled() && started.get() && JdHotKeyStore.isHotKey(key);
    }

    /** 查看配置开关、启动标记、发现的 Worker 数量和连接状态；启动标记本身不代表已经连上 Worker。 */
    @Override
    public Map<String, Object> status() {
        Channel channel = properties.isEnabled() ? WorkerInfoHolder.chooseChannel("status") : null;
        return Map.of(
                "enabled",
                properties.isEnabled(),
                "pipelineStarted",
                started.get(),
                "appName",
                properties.getAppName(),
                "discoveredWorkers",
                WorkerInfoHolder.getWorkers().size(),
                "workerConnected",
                channel != null && channel.isActive());
    }
}
