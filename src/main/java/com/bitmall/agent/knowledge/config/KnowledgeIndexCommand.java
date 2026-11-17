package com.bitmall.agent.knowledge.config;

import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexRepository;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.dao.repository.PgVectorIndexRepository;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.service.retrieval.SpringAiEmbeddingClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

/** 独立离线入口：只迁移/读取知识库和发布索引，不启动 Web、交易恢复任务或 MQ。 */
@EnableAutoConfiguration
@MapperScan(
    basePackages = "com.bitmall.agent.knowledge.dao.mapper",
    annotationClass = org.apache.ibatis.annotations.Mapper.class)
@Import({
  KnowledgeProperties.class,
  PgVectorProperties.class,
  PgVectorIndexRepository.class,
  KnowledgeRepository.class,
  KnowledgeIndexRepository.class,
  KnowledgeIndexService.class,
  SpringAiEmbeddingClient.class
})
public class KnowledgeIndexCommand {
  public static void main(String[] args) {
    try (var context =
        new SpringApplicationBuilder(KnowledgeIndexCommand.class)
            .web(WebApplicationType.NONE)
            .run(args)) {
      String version = context.getBean(KnowledgeIndexService.class).rebuild();
      org.slf4j.LoggerFactory.getLogger(KnowledgeIndexCommand.class).info("知识索引发布成功：{}", version);
    }
  }
}
