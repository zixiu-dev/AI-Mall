package com.bitmall.agent.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 拆分后的 HTTP 边界测试。 验证查会话历史仍检查用户归属，以及确认接口仍要求用户明确提交同意或拒绝。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ControllerBoundaryIntegrationTest {
  /** 本机模拟 HTTP 请求工具，在测试中验证真实 Controller 和服务调用链。 */
  @Autowired MockMvc mvc;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  @Autowired ObjectMapper json;

  /** 先创建会话，再分别用所属用户和其他用户读取历史，验证只有所属用户能读取。 */
  @Test
  void conversationHistoryChecksOwnershipThroughTheService() throws Exception {
    String response =
        mvc.perform(
                post("/api/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"推荐电脑\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String conversationId = json.readTree(response).path("conversationId").asText();

    mvc.perform(get("/api/agent/conversations/{id}/messages", conversationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2));
    mvc.perform(
            get("/api/agent/conversations/{id}/messages", conversationId).param("userId", "10002"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("不属于当前用户")));
  }

  /** 确认请求缺少 approved 时必须返回参数错误，不能把缺失值当成默认同意。 */
  @Test
  void actionControllerRequiresAnExplicitDecision() throws Exception {
    mvc.perform(
            post("/api/agent/actions/unknown/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":10001}"))
        .andExpect(status().isBadRequest());
  }
}
