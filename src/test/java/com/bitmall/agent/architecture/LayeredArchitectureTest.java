package com.bitmall.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 分层约束测试。 防止 Controller 直接访问 Mapper、Service 直接写 JDBC，或 Mapper 反向依赖业务和模型框架。 */
class LayeredArchitectureTest {
  /** 本项目生产 Java 源码目录，分层测试只检查这里的依赖。 */
  private static final Path SOURCE = Path.of("src/main/java/com/bitmall/agent");

  /** 顶层目录只允许业务模块和少量公共组件，不能恢复成全局 controller、service、mapper 目录。 */
  private static final List<String> MODULES =
      List.of(
          "product",
          "cart",
          "coupon",
          "checkout",
          "order",
          "aftersales",
          "user",
          "knowledge",
          "agent",
          "common");

  /** 检查源代码依赖方向，禁止 Controller 直接碰 Mapper 或 Mapper 反向调用上层。 */
  @Test
  void dependenciesFollowControllerServiceMapperDirection() throws Exception {
    Map<String, List<Pattern>> forbidden =
        Map.of(
            "controller",
                List.of(
                    projectDependency("dao\\.(?:mapper|repository)"),
                    Pattern.compile("org\\.springframework\\.jdbc\\.")),
            "service",
                List.of(
                    projectDependency("controller"),
                    Pattern.compile("org\\.springframework\\.jdbc\\.")),
            "mapper",
                List.of(
                    projectDependency("controller"),
                    projectDependency("service"),
                    Pattern.compile("org\\.springframework\\.ai\\."),
                    Pattern.compile(
                        "org\\.springframework\\.transaction\\.annotation\\.Transactional")));
    Map<String, Integer> checked = new java.util.HashMap<>();
    for (Path file : sources(SOURCE)) {
      String source = Files.readString(file);
      for (var layer : forbidden.entrySet()) {
        if (!isInLayer(file, layer.getKey())) continue;
        checked.merge(layer.getKey(), 1, Integer::sum);
        for (Pattern dependency : layer.getValue()) {
          assertThat(dependency.matcher(source).find())
              .as("%s must respect the three-layer dependency direction", file)
              .isFalse();
        }
      }
    }
    // 防止目录匹配写错后检查了零个类，却仍然显示通过。
    for (String layer : forbidden.keySet()) {
      assertThat(checked.getOrDefault(layer, 0)).as("checked %s classes", layer).isPositive();
    }
  }

  /** 持久化已迁移到 MyBatis-Plus，生产代码不能重新引入 JDBC 数据访问。 */
  @Test
  void productionPersistenceDoesNotUseSpringJdbc() throws Exception {
    for (Path file : sources(SOURCE)) {
      assertThat(Files.readString(file))
          .as("%s", file)
          .doesNotContain("import org.springframework.jdbc.", "JdbcClient", "JdbcTemplate");
    }
  }

  /** 检查每个源码的顶层是业务模块、package 与实际路径一致，防止只改文件位置却漏改包名。 */
  @Test
  void sourcesAreOrganizedByBusinessModule() throws Exception {
    for (Path file : sources(SOURCE)) {
      Path relative = SOURCE.relativize(file);
      if (relative.getNameCount() == 1) {
        assertThat(file.getFileName().toString()).isEqualTo("BitMallAgentApplication.java");
        continue;
      }
      assertThat(MODULES).contains(relative.getName(0).toString());
      String expectedPackage =
          "com.bitmall.agent." + relative.getParent().toString().replace('/', '.');
      assertThat(Files.readString(file)).contains("package " + expectedPackage + ";");
    }
    for (String module : MODULES) {
      assertThat(sources(SOURCE.resolve(module)))
          .as("%s must contain real code", module)
          .isNotEmpty();
    }
  }

  /** Agent 可调用商城业务；商城业务不得反向依赖 Agent 的模型运行与确认流程。 */
  @Test
  void commerceModulesDoNotDependOnAgentInternals() throws Exception {
    Pattern agentDependency = Pattern.compile("com\\.bitmall\\.agent\\.agent\\.");
    for (Path file : sources(SOURCE)) {
      if (SOURCE.relativize(file).startsWith("agent")) continue;
      assertThat(agentDependency.matcher(Files.readString(file)).find())
          .as("%s must not depend on Agent internals", file)
          .isFalse();
    }
  }

  /** 按模块后的固定层位置识别源码；工具名称或其他深层目录名不参与判断。 */
  private boolean isInLayer(Path file, String layer) {
    Path relative = SOURCE.relativize(file);
    if (layer.equals("mapper")) {
      return relative.getNameCount() >= 4
          && relative.getName(1).toString().equals("dao")
          && relative.getName(2).toString().equals("mapper");
    }
    return relative.getNameCount() >= 3 && relative.getName(1).toString().equals(layer);
  }

  /** 同时匹配 import 和代码里直接写出的完整类型名，覆盖任何业务模块的指定层。 */
  private Pattern projectDependency(String layer) {
    return Pattern.compile("com\\.bitmall\\.agent\\.[a-z]+\\." + layer + "\\.");
  }

  /** 列出指定目录中的 Java 源文件，供分层检查逐个读取。 */
  private List<Path> sources(Path root) throws Exception {
    try (var files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }
}
