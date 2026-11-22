# 可读性重构交接说明

本文件交给接手的 AI，用来**验证**并**继续**一轮可读性重构。工作区根目录：`/Users/wowo/Agent/bit-mall-agent`。

## 1. 背景与目标

用户认为原代码"对 AI 友好、对人 review 不友好"，要求找出这类代码并重写。重构必须保持行为完全不变，不改业务逻辑。

用户的风格要求（必须遵守）：

- 所有 `if` / `for` / `while` / `else` 都加花括号，一行只写一条语句。
- 复杂的布尔条件拆成命名清楚的局部变量或私有方法；魔法字符串、魔法数字提成常量。
- 只导入具体的类。例外：注解包和工具类的 DSL 静态导入保留 `*`，如 `mybatisplus.annotation.*`、`web.bind.annotation.*`、`ToolDefinitions.*`。
- 右侧看不出类型时不用 `var`；不用单字母变量名；一行只声明一个字段。
- 数据类用 Lombok `@Getter`；不用 `record`。Java 17。
- 注释写"做什么 + 为什么"，删掉只复述名字的注释（如"获取 X""商品品牌。"）。
- **每个注入字段都必须有一行注释**，说明本类拿它做什么。Spring 依赖和 `@ConfigurationProperties` 的字段都算，配置字段要写清含义、单位、默认值和取值范围。同一类型在不同类里不能用同一句模板注释。
- 写进注释的行为说明，必须先对照源码（Java、Mapper XML、Lua 脚本）核实过。
- 项目约定见 `AGENTS.md`。纯重构不改变可观察行为，按约定不建 OpenSpec change。

## 2. 当前状态（2026-11-22 续接后）

本轮可读性整理已完成。行为保持不变，按 `openspec/workflow.md` 的重构约定不新建功能差异。

- 基线和修改后各运行一次 `mvn -o clean test`，均为 **262 项总计，241 项通过，21 项跳过，0 失败、0 错误**。原文“262 个通过、21 跳过”统计口径有误，现已更正。
- 跳过的 21 项是未配置外部环境的 MySQL（9）、pgvector（9）、真实 RAG 模型（3）测试。Redis 使用本机临时实例，MQ 与模型使用测试替身，本轮未重连真实 Broker 或云模型。
- 上轮未测试的配置绑定、Lombok getter 和 MQ 消息序列化改动已包含在本轮 clean 回归中。
- 压力统计、监控端点、LEGACY 索引发布、知识文本处理、提示词组装和 Agent 循环已完成后续整理；检索计划内部类 `Plan` 改用 `@Getter`。
- 新改的 9 个类（含 `Plan`）通过 `javap -public` 对照，公开 API 一致。提示词组装方法的 `javap -c -p` 输出一致。
- 使用重构前保存的原始 `OrderSnapshotService` 编译产物对照当前实现，3888 组参数的 `commandHash` 一致，覆盖空值、金额小数位、DIRECT/CART、明细顺序等。未读取线上真实购买数据。
- 使用续接前后的独立 class 对照，5000 步压力计数及窗口快照、508 组知识切块/标识符提取结果一致。覆盖时间跳变、桶过期、负耗时、溢出桶、正文长度边界和完整段落。
- 注入注释扫描列出的 6 项已人工核对，均为内部数据类字段，不是漏注释的 Spring 依赖。

本机证据位于 `.runtime/readability-refactor/`，包含两次测试日志、Surefire 报告、摘要、公开 API 对照和临时兼容性检查程序及日志；该目录不提交 Git。源码备份位置见本机 `/tmp/bitmall-readability-backup-path`，只用于本轮对照。

上轮的 281 个类字节码一致性结论来自原交接记录，本轮没有重新执行该项全量对照；本轮验证范围见上面清单。

## 3. 如何复验

```bash
mvn -o clean test
```

带 `clean` 以确保重新编译当前源码；离线依赖未准备好时去掉 `-o`。Surefire 的 Tests run 包含跳过项，实际通过数需扣除 Skipped、Failures 和 Errors。

当前验证结果也已汇入 [测试总文档](docs/testing.md#2026-11-22-可读性重构回归)。

## 4. 保持的业务边界

- `OrderRecoveryService`、`OrderSubmissionService`、`OrderCreationService`、`OrderAsyncAdmissionService` 的事务边界、状态推进、执行顺序及异常范围在本轮未调整，现有同步/异步、库存与死信恢复回归通过。
- `InventoryRedisStore` 的返回码常量已对照 `inventory_reserve.lua`、`inventory_finish.lua`、`inventory_initialize.lua` 和 `inventory_adopt.lua` 核对，本轮不修改脚本或 Redis key。
- `KnowledgeIndexRepository` 仍在同一事务内先锁发布指针和源文档、复核快照、插入块，再完成版本和切换指针；写块失败仍向外传播并回滚。
- `OrderPressureWindow` 保留十秒滚动平均口径，P95 按 10ms 桶上界估计；5s 及以上进入末桶，返回 5010ms，不是精确延迟测量。
- `KnowledgeText` 的 1200 字符是目标块长，完整超长段落可以超过它；正文上限仍为 12000 个 Java 字符，文档哈希字段和顺序不变。
- `SpringAiResponder` 保留工具权限、逐个执行、取消传播、回执保存和确认卡校验；中断后由下一次用户请求恢复历史并开始新循环，未增加自动恢复确认前循环的功能。
- 上轮 26 个数据类替换 Lombok 后的 `hashCode` 数值和 `toString` 格式变化属于既有改动，本轮未进一步调整；`OrderDetailVO` 保留手写相等性实现。

## 5. 后续范围

原交接列出的六个整理点及 `Plan` getter 已完成。后续如继续扩大可读性整理，按业务逐类核对实际职责；本轮结果不代表所有源码都经过完整人工审计。真实 MySQL、pgvector、Broker、云模型及性能压测仍需单独配置环境并运行，不用本轮替身测试替代。

## 6. 可复用的脚本（在 `/tmp/refactor-scripts/`）

| 脚本 | 用途 |
|---|---|
| `injected.py <源码根>` | 列出 Spring Bean 中没有注释的注入字段 |
| `add_braces.py <源码根>` | 给单语句的 if/for/while/else 补花括号；处理不了的会打印出来交给人工 |
| `expand_wildcards.py <源码根> <cp文件> <文件...>` | 展开 `*` 导入 |
| `explicit_var.py <源码根> <cp文件>` | 用 javac 推断出的类型替换 `var` |
| `lombok_getters.py <文件...>` | 把平凡 getter 换成 `@Getter` |

classpath 保存在 `/tmp/cp.txt`。如果文件不存在，用下面的命令重新生成：

```bash
mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
```

## 7. 推荐的验证方法与常见坑

- **机械改动做字节码对比**：把改前和改后的源码分别用 `javac -nowarn -parameters -d <目录> -cp "$(cat /tmp/cp.txt)"` 编译，再逐个比较 `javap -c -p` 的输出（默认不含行号表）。只要结果一致，就能证明行为不变。
- **Lombok 改动对比公开 API**：比较 `javap -public` 的输出（排序后再比）。
- **javac 输出是中文**：用脚本解析错误信息时，要加 `-J-Duser.language=en -J-Duser.country=US`。
- **shell 是 zsh**：`$VAR` 不会自动按空格拆分，文件列表要用数组 `"${ARR[@]}"`。
- **awk 的 `length` 按字节计数**：一个汉字算 3 个字节，判断行长时会误报。
- **仓库不是统一按 google-java-format 排版的**：抽查 200 个已跟踪文件，有 133 个和它的输出不一致。不要全量格式化，只格式化自己整体重写过的文件。
- **不要做 git 写操作**：工作区里有用户原本未提交的大量改动，不要 add、checkout、stash 或 reset。

## 8. 回退

`/tmp` 下有每一步之前的源码备份，用对应目录里的 `src` 覆盖即可。注意 `/tmp` 可能在重启后被清空。

| 备份目录 | 对应的时间点 |
|---|---|
| `/tmp/bitmall-pre-refactor` | 整个重构开始之前（`diff -r` 它和 `src`，就能单独看出本次重构的改动） |
| `/tmp/bitmall-before-braces` | 补花括号之前 |
| `/tmp/bitmall-before-wildcards` | 展开 `*` 导入之前 |
| `/tmp/bitmall-before-var` | 替换 `var` 之前 |
| `/tmp/bitmall-before-split` | 拆分多字段声明之前 |
| `/tmp/bitmall-before-getters` | 第一批 getter 转 Lombok 之前 |
| `/tmp/bitmall-before-getters2` | 第二批 getter 转 Lombok 之前 |
