package com.bitmall.agent.common.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScript;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.core.io.ClassPathResource;
import org.redisson.config.SingleServerConfig;

/** Redisson 连接和原子脚本的公共适配；字符串编码保持与既有库存键兼容，不重写交易状态。 */
public final class RedissonSupport {
  private RedissonSupport() {}

  /** 两种 Redis 独立连接；延迟初始化让离线 demo 不必在装配时连接外部 Redis。 */
  public static RedissonClient connect(String host, int port, String password, int database,
      int connectTimeout, int commandTimeout) {
    Config config = new Config();
    config.setLazyInitialization(true).setThreads(2).setNettyThreads(2);
    config.setCodec(StringCodec.INSTANCE);
    SingleServerConfig server = config.useSingleServer().setAddress("redis://" + host + ":" + port)
        .setDatabase(database).setConnectionPoolSize(16).setConnectionMinimumIdleSize(1)
        .setSubscriptionConnectionPoolSize(4).setSubscriptionConnectionMinimumIdleSize(1)
        .setConnectTimeout(connectTimeout).setTimeout(commandTimeout).setRetryAttempts(0);
    if (password != null && !password.isBlank()) {
      server.setPassword(password);
    }
    return Redisson.create(config);
  }

  /** 同一次 Redis 执行中保持多键原子性；键需使用相同 hash tag。 */
  public static <T> T eval(RedissonClient client, String lua, RScript.ReturnType type,
      List<String> keys, Object... arguments) {
    return client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
        lua, type, new ArrayList<Object>(keys), arguments);
  }

  /** 读取仓库已有 Lua 原文，避免继续绑定 Spring RedisScript 类型。 */
  public static String script(String name) {
    try (var input = new ClassPathResource("lua/" + name).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("读取 Redis 原子脚本失败：" + name, ex);
    }
  }
}
