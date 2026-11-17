package com.bitmall.agent.common.config;

/** 将旧 base + endpoint 配置转换为 Spring AI 2 模型客户端接受的完整 API 前缀。 */
public final class AiModelEndpoints {
  private AiModelEndpoints() {}

  public static String apiBase(String base, String path, String suffix) {
    if (!path.endsWith(suffix))
      throw new IllegalArgumentException("模型 endpoint 必须以 " + suffix + " 结尾");
    return base.replaceAll("/$", "") + path.substring(0, path.length() - suffix.length());
  }
}
