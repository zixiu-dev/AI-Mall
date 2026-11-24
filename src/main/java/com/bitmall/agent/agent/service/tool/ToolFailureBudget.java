package com.bitmall.agent.agent.service.tool;

import java.util.HashMap;
import java.util.Map;

/** 一轮请求内按工具累计失败预算，模型更换调用 ID 或参数也无法重置次数。 */
public final class ToolFailureBudget {
  private final Map<String, Integer> invalidArguments = new HashMap<>();
  private final Map<String, Integer> retries = new HashMap<>();
  private final Map<String, String> stopped = new HashMap<>();

  /** 累计参数错误；第三次错误意味着两次修正机会已用完。 */
  public synchronized int invalid(String tool) {
    return invalidArguments.merge(tool, 1, Integer::sum);
  }

  /** 返回本轮第几次重试；大于 2 时执行器必须停止。 */
  public synchronized int retry(String tool) {
    return retries.merge(tool, 1, Integer::sum);
  }

  /** 保存终止结果，让本轮后续调用复用同一回执。 */
  public synchronized void stop(String tool, String result) {
    stopped.put(tool, result);
  }

  /** 没有终止记录时返回 null，允许正常执行。 */
  public synchronized String stopped(String tool) {
    return stopped.get(tool);
  }
}
