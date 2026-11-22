package com.bitmall.agent.common.exception;

import com.bitmall.agent.common.idempotent.IdempotentBusyException;
import com.bitmall.agent.order.exception.OrderRejectedException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 统一把业务异常、参数校验异常和数据库异常转换成 HTTP 错误响应。 前端因此可以用固定的 message 字段显示失败原因。 */
@RestControllerAdvice
public class ApiExceptionHandler {
  /** 结算快照或活动策略的明确拒绝，不伪装成服务器成功。 */
  @ExceptionHandler(OrderRejectedException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> orderRejected(OrderRejectedException exception) {
    return Map.of("success", false, "code", exception.getCode(), "message", exception.getMessage());
  }

  /** 并发重复请求使用原编号查询，不能重新生成一次购买。 */
  @ExceptionHandler(IdempotentBusyException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> duplicate(IdempotentBusyException exception) {
    return Map.of("success", false, "code", "PROCESSING", "message", exception.getMessage());
  }

  /** 限流/暂不可用保留准确 HTTP 状态和重试提示，不把压力保护当成参数错误。 */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(ResponseStatusException exception) {
    ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatusCode());
    if (exception.getStatusCode().value() == 429 || exception.getStatusCode().value() == 503) {
      response.header("Retry-After", "1");
    }
    return response.body(
        Map.of(
            "success",
            false,
            "message",
            exception.getReason() == null ? "请求暂时无法处理" : exception.getReason()));
  }

  /** 日志记录器，用于排查异常和运行状态，不把内部信息直接发给用户。 */
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** 把可预期的业务拒绝转换成 400 响应和可阅读的原因。 */
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> business(Exception exception) {
    return Map.of("success", false, "message", exception.getMessage());
  }

  /** 整理参数校验错误，返回给前端展示。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> validation(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("请求参数不正确");
    return Map.of("success", false, "message", message);
  }

  /** 记录数据库异常，并向用户返回统一错误，避免泄露 SQL 或连接细节。 */
  @ExceptionHandler(DataAccessException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, Object> database(DataAccessException exception) {
    log.error("Database operation failed", exception);
    return Map.of("success", false, "message", "商品服务暂时不可用，请稍后重试");
  }
}
