package com.bitmall.management.agent;

import com.bitmall.agent.agent.service.chat.AgentTurnService;
import com.bitmall.agent.agent.vo.AgentExecutionVO;
import com.bitmall.agent.agent.vo.AgentTurnSummaryVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 独立管理端的只读页面与接口。 */
@RestController
@RequestMapping("/agent-execution")
@RequiredArgsConstructor
public class AgentExecutionDashboardController {
  private static final String ASSETS = "management/agent-execution/";

  /** 复用单轮执行查询与消息归属规则。 */
  private final AgentTurnService turns;

  @GetMapping(value = {"", "/"}, produces = "text/html;charset=UTF-8")
  public Resource page() {
    return new ClassPathResource(ASSETS + "index.html");
  }

  @GetMapping(value = "/dashboard.css", produces = "text/css;charset=UTF-8")
  public Resource styles() {
    return new ClassPathResource(ASSETS + "dashboard.css");
  }

  @GetMapping(value = "/dashboard.js", produces = "text/javascript;charset=UTF-8")
  public Resource script() {
    return new ClassPathResource(ASSETS + "dashboard.js");
  }

  /** 列出近期可查看的轮次，查询结果由管理端实时读取。 */
  @GetMapping("/api/turns")
  public ResponseEntity<List<AgentTurnSummaryVO>> recent(@RequestParam long userId) {
    requirePositive(userId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(turns.recentTurns(userId));
  }

  /** 不存在和不属于指定用户统一按查不到处理。 */
  @GetMapping("/api/turns/{turnId}")
  public ResponseEntity<AgentExecutionVO> detail(@RequestParam long userId, @PathVariable long turnId) {
    requirePositive(userId);
    requirePositive(turnId);
    AgentExecutionVO result;
    try {
      result = turns.executionDetails(userId, turnId);
    } catch (IllegalArgumentException notOwned) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }

  private static void requirePositive(long id) {
    if (id <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
  }
}
