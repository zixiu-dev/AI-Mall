package com.bitmall.agent.knowledge.controller;

import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只读政策查询接口，与 Agent 共用意图识别和检索服务，不提供任意文档写入。 */
@RestController
@RequestMapping("/api/store/knowledge")
public class KnowledgeController {
  private final KnowledgeService knowledge;

  public KnowledgeController(KnowledgeService knowledge) {
    this.knowledge = knowledge;
  }

  /** 多子问题的只读入口，与 Agent 共用参数校验、分组和检索。 */
  @PostMapping("/search")
  public KnowledgeSearchResultVO searchMultiple(@RequestBody Map<String, Object> args) {
    return retrieve(KnowledgeRetrievalRequest.from(args));
  }

  @GetMapping("/search")
  public KnowledgeSearchResultVO search(
      @RequestParam String query,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String scope) {
    return retrieve(new KnowledgeRetrievalRequest(query, null, brand, category, scope));
  }

  /** 参数错误为 400，检索基础设施/预算故障为 503，不返回看似正常的 NO_MATCH。 */
  private KnowledgeSearchResultVO retrieve(KnowledgeRetrievalRequest request) {
    try {
      return knowledge.retrieve(request, null);
    } catch (IllegalStateException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "知识检索暂不可用，请稍后再试", e);
    }
  }
}
