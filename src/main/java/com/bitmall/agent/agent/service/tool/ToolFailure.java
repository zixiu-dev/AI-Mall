package com.bitmall.agent.agent.service.tool;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLTransientException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/** 工具错误协议。修正参数与重新执行业务使用不同错误码，底层连接信息只留在日志中。 */
public final class ToolFailure {
  private ToolFailure() {}

  /** 统一保留兼容字段 ok/error，同时提供后端可判断的错误类型和下一步动作。 */
  public static String result(ObjectMapper json, String code, String message, String nextAction) {
    return json.writeValueAsString(Map.of(
        "ok", false, "code", code, "error", message, "nextAction", nextAction));
  }

  /** 只对白名单中的瞬时故障重试，程序错误和业务拒绝不会因为包含“超时”文字而被重试。 */
  public static boolean isTransient(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException || cause instanceof ConnectException
          || cause instanceof TimeoutException || cause instanceof SQLTransientException
          || cause instanceof TransientDataAccessException
          || cause instanceof DataAccessResourceFailureException
          || cause instanceof org.redisson.client.RedisTimeoutException
          || cause instanceof org.redisson.client.RedisConnectionException) {
        return true;
      }
      if (cause instanceof RestClientResponseException response) {
        int status = response.getStatusCode().value();
        if (status == 429 || status == 502 || status == 503 || status == 504) return true;
      }
      if (cause == cause.getCause()) break;
    }
    return false;
  }
}
