package com.bitmall.agent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 向量库独立连接与查询方式；不覆盖商城的 spring.datasource。 */
@Component
@ConfigurationProperties("app.knowledge.pgvector")
public class PgVectorProperties {
  private String url = "jdbc:postgresql://127.0.0.1:5433/bitmall_knowledge",
      username = "bitmall",
      password = "",
      searchMode = "EXACT";

  public String getUrl() {
    return url;
  }

  public void setUrl(String v) {
    url = v;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String v) {
    username = v;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }

  public String getSearchMode() {
    return searchMode;
  }

  public void setSearchMode(String v) {
    searchMode = v;
  }

  @jakarta.annotation.PostConstruct
  public void validate() {
    if (!java.util.List.of("EXACT", "HNSW").contains(searchMode)
        || !url.startsWith("jdbc:postgresql:"))
      throw new IllegalArgumentException("PG 向量库地址或检索模式无效");
  }
}
