"use strict";

const byId = (id) => document.getElementById(id);
const statusLabels = {
  GENERATING: "生成中", COMPLETED: "已完成", FAILED: "失败", SUCCESS: "成功",
  REJECTED: "拒绝", CANCELLED: "取消", SKIPPED: "跳过", TIMEOUT: "超时",
  AWAITING_CONFIRMATION: "待确认",
};
const candidateLabels = { primary: "主模型", bailian: "备用模型（百炼）" };
const eventLabels = {
  QUEUE_WAIT: "等待执行",
  MODEL_CALL: "模型调用",
  MODEL_SKIPPED: "熔断跳过",
  TOOL_CALL: "工具调用",
  CONFIRMATION_RECOVERED: "恢复确认卡",
};
const toolLabels = {
  product_search: "搜索商品",
  product_detail: "查询商品详情",
  checkout_quote: "核算优惠和金额",
  coupon_list: "查询优惠券",
  coupon_claim: "准备领券",
  order_query: "查询订单",
  order_create: "准备下单",
  order_cancel: "准备取消订单",
  load_skill: "读取办理流程",
  search_knowledge: "检索商城规则",
  save_preference: "保存购物偏好",
};
const eventStyles = {
  QUEUE_WAIT: ["queue", "◷"],
  MODEL_CALL: ["model", "◈"],
  MODEL_SKIPPED: ["skipped", "⊘"],
  TOOL_CALL: ["tool", "◇"],
  CONFIRMATION_RECOVERED: ["confirmation", "↺"],
};
const eventTime = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit", minute: "2-digit", second: "2-digit", fractionalSecondDigits: 3,
  hourCycle: "h23",
});

let selectedUser = "";
let selectedTurn = "";
let listRequest;
let detailRequest;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text != null) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function icon(symbol, type) {
  const node = element("span", symbol, "node-icon " + type);
  node.setAttribute("aria-hidden", "true");
  return node;
}

function setStatus(node, value) {
  node.className = "status " + String(value ?? "").toLowerCase();
  node.textContent = statusLabels[value] ?? value ?? "未知";
}

function notice(id, message = "", error = false) {
  const node = byId(id);
  node.textContent = message;
  node.classList.toggle("error", error);
}

async function readTurns(path, userId, signal) {
  const query = new URLSearchParams({ userId });
  const response = await fetch("/agent-execution/api/turns" + path + "?" + query, { signal });
  if (!response.ok) {
    const message = response.status === 404 ? "该用户下查不到这轮回答。"
      : response.status === 400 ? "请检查输入的编号。"
      : "读取失败，请稍后再试。";
    throw new Error(message);
  }
  return response.json();
}

function buildRecentTurn(turn) {
  const id = String(turn.turnId);
  const row = element("li");
  const select = element("button", null, "turn-button");
  select.type = "button";
  select.dataset.turnId = id;
  const status = element("span");
  setStatus(status, turn.status);
  const created = element("time", turn.createdAt.replace("T", " ").slice(0, 19), "muted");
  created.dateTime = turn.createdAt;
  select.append(element("strong", "轮次 #" + id), status, created);
  select.addEventListener("click", () => {
    byId("turn-id").value = "";
    void loadDetail(id);
  });
  row.append(select);
  return row;
}

function buildRecentThreads(turns) {
  const threads = new Map();
  for (const turn of turns) {
    const thread = threads.get(turn.conversationId) ?? [];
    thread.push(turn);
    threads.set(turn.conversationId, thread);
  }

  return Array.from(threads, ([id, thread]) => {
    const row = element("li");
    const toggle = element("details", null, "thread");
    const summary = element("summary", null, "thread-heading");
    const shortId = id.length > 14 ? id.slice(0, 8) + "…" + id.slice(-4) : id;
    const name = element("span", "会话 " + shortId, "thread-name");
    name.title = id;
    summary.append(icon("▤", "thread-icon"), name, element("span", thread.length + " 轮", "thread-count"));
    const children = element("ul", null, "thread-turns");
    children.append(...thread.map(buildRecentTurn));
    toggle.append(summary, children);
    row.append(toggle);
    return row;
  });
}

function highlightTurn(turnId) {
  for (const button of byId("recent-list").querySelectorAll(".turn-button")) {
    const selected = button.dataset.turnId === turnId;
    if (selected) {
      button.setAttribute("aria-current", "true");
      button.closest(".thread").open = true;
    } else {
      button.removeAttribute("aria-current");
    }
  }
}

function formatMs(ms) {
  return ms == null ? "—" : ms + " ms";
}

function appendFact(target, label, value, className) {
  if (value == null) return;
  const fact = element("div", null, className ?? "fact");
  fact.append(element("dt", label), element("dd", value));
  target.append(fact);
}

function buildEvent(entry, toolsByCall, missingSource = false) {
  const isModel = entry.itemType === "MODEL_CALL";
  const isTool = entry.itemType === "TOOL_CALL";
  const [style, symbol] = eventStyles[entry.itemType] ?? ["queue", "·"];
  const label = eventLabels[entry.itemType] ?? entry.itemType;
  const toolLabel = isTool && Object.hasOwn(toolLabels, entry.toolName) ? toolLabels[entry.toolName] : null;
  let title = label;
  if (isTool) title = toolLabel ?? entry.toolName ?? label;
  if (isModel && entry.attemptNo != null) title += " " + entry.attemptNo;

  const row = element("li", null, "branch event " + style);
  row.dataset.sequence = entry.sequenceNo;
  const card = element("div", null, "event-content");
  const heading = element("div", null, "event-head");
  const name = element("div", null, "event-name");
  name.append(icon(symbol, style), element("strong", title));
  const summary = element("div", null, "event-summary");
  const status = element("span");
  setStatus(status, entry.status);
  const sequence = element("span", "#" + entry.sequenceNo, "event-sequence");
  sequence.title = "事件序号";
  summary.append(sequence, status, element("span", formatMs(entry.durationMs), "event-duration"));
  heading.append(name, summary);
  card.append(heading);

  const identifiers = element("div", null, "event-identifiers");
  const candidate = candidateLabels[entry.candidate] ?? entry.candidate;
  if (candidate) identifiers.append(element("span", candidate));
  if (entry.model) identifiers.append(element("code", entry.model));
  if (toolLabel) identifiers.append(element("code", entry.toolName));
  if (identifiers.childElementCount) card.append(identifiers);

  const facts = element("dl", null, "facts");
  const timestamp = new Date(entry.startedAt + "Z");
  if (!Number.isNaN(timestamp.getTime())) {
    appendFact(facts, "开始", eventTime.format(timestamp));
  }
  if (isModel) {
    appendFact(facts, "首段", formatMs(entry.firstTextMs));
    appendFact(facts, "输入 Token", entry.inputTokens ?? "—");
    appendFact(facts, "输出 Token", entry.outputTokens ?? "—");
  }
  appendFact(facts, "实际执行", entry.executionCount == null ? null : entry.executionCount + " 次");
  appendFact(facts, "确认卡", entry.actionId);
  appendFact(facts, "错误码", entry.errorCode, "fact error-code");
  if (facts.childElementCount) card.append(facts);
  if (missingSource) {
    card.append(element("p", "来源模型 #" + entry.modelCallSequence + " 暂无记录", "missing-source"));
  }
  row.append(card);

  const tools = isModel ? toolsByCall.get(entry.sequenceNo) : null;
  if (tools?.length) {
    const children = element("ol", null, "branches tool-branches");
    children.setAttribute("aria-label", "模型触发的工具");
    children.append(...tools.map((tool) => buildEvent(tool, toolsByCall)));
    row.append(children);
  }
  return row;
}

function buildStep(step, toolsByCall) {
  const row = element("li", null, "branch step");
  const toggle = element("details", null, "step-toggle");
  toggle.dataset.step = step.number;
  toggle.open = true;
  const heading = element("summary", null, "step-heading");
  const label = element("strong", "第 " + step.number + " 步推理");
  const labelGroup = element("span", null, "step-label");
  labelGroup.append(icon("↳", "step-icon"), label);
  heading.append(labelGroup, element("span", step.modelCount + " 次模型请求 · " + step.toolCount + " 个工具", "muted"));
  const children = element("ol", null, "branches step-branches");
  for (const entry of step.entries) {
    children.append(buildEvent(entry, toolsByCall,
      entry.itemType === "TOOL_CALL" && entry.modelCallSequence != null));
  }
  toggle.append(heading, children);
  row.append(toggle);
  return row;
}

function renderTree(entries) {
  const models = new Map(entries.filter((entry) => entry.itemType === "MODEL_CALL")
    .map((entry) => [entry.sequenceNo, entry]));
  const toolsByCall = new Map();
  const steps = new Map();
  const roots = [];
  let toolCount = 0;

  // 明细可能分批到达。只有来源已在同一步落库时才建立父子关系。
  for (const entry of entries) {
    const isTool = entry.itemType === "TOOL_CALL";
    if (isTool) toolCount++;
    if (entry.stepNo == null) {
      roots.push(entry);
      continue;
    }
    let step = steps.get(entry.stepNo);
    if (!step) {
      step = { number: entry.stepNo, entries: [], modelCount: 0, toolCount: 0 };
      steps.set(entry.stepNo, step);
      roots.push(step);
    }
    if (entry.itemType === "MODEL_CALL") step.modelCount++;
    if (isTool) step.toolCount++;
    const source = isTool ? models.get(entry.modelCallSequence) : null;
    if (source?.stepNo === entry.stepNo) {
      const tools = toolsByCall.get(source.sequenceNo) ?? [];
      tools.push(entry);
      toolsByCall.set(source.sequenceNo, tools);
    } else {
      step.entries.push(entry);
    }
  }

  const stats = byId("turn-stats");
  stats.replaceChildren();
  if (entries.length) {
    appendFact(stats, "推理步骤", steps.size);
    appendFact(stats, "模型请求", models.size);
    appendFact(stats, "工具调用", toolCount);
  }
  byId("empty-tree").hidden = entries.length > 0;
  byId("execution-tree").replaceChildren(...roots.map((root) => root.entries
    ? buildStep(root, toolsByCall)
    : buildEvent(root, toolsByCall, root.itemType === "TOOL_CALL" && root.modelCallSequence != null)));
}

async function loadDetail(turnId) {
  detailRequest?.abort();
  const { signal } = (detailRequest = new AbortController());
  selectedTurn = turnId;
  highlightTurn(turnId);
  byId("refresh").disabled = !turnId;
  byId("turn-detail").hidden = true;
  if (!turnId) {
    notice("detail-message", "选择一轮回答查看明细。");
    return;
  }
  notice("detail-message", "正在读取执行明细…");
  try {
    const detail = await readTurns("/" + turnId, selectedUser, signal);
    if (signal.aborted) return;
    byId("detail-turn").textContent = "轮次 #" + detail.turnId;
    setStatus(byId("detail-status"), detail.status);
    byId("detail-conversation").textContent = detail.conversationId;
    byId("detail-note").hidden = detail.status !== "GENERATING";
    renderTree(detail.entries);
    byId("turn-detail").hidden = false;
    notice("detail-message");
  } catch (error) {
    if (!signal.aborted) notice("detail-message", error.message, true);
  }
}

async function loadUser(userId, turnId) {
  listRequest?.abort();
  const { signal } = (listRequest = new AbortController());
  selectedUser = userId;
  byId("recent-list").replaceChildren();
  byId("recent-count").textContent = "";
  notice("recent-message", "正在读取最近轮次…");
  // 输入编号时先查明细，列表失败也不影响编号查询。
  void loadDetail(turnId);
  try {
    const turns = await readTurns("", userId, signal);
    if (signal.aborted) return;
    byId("recent-list").replaceChildren(...buildRecentThreads(turns));
    byId("recent-count").textContent = "最近 " + turns.length + " 轮";
    notice("recent-message", turns.length ? "" : "这个用户还没有后台聊天记录。");
    if (!selectedTurn && turns.length) {
      void loadDetail(String(turns[0].turnId));
    } else {
      highlightTurn(selectedTurn);
    }
  } catch (error) {
    if (!signal.aborted) notice("recent-message", error.message, true);
  }
}

byId("search").addEventListener("submit", (event) => {
  event.preventDefault();
  void loadUser(byId("user-id").value, byId("turn-id").value);
});
byId("refresh").addEventListener("click", () => void loadUser(selectedUser, selectedTurn));
void loadUser(byId("user-id").value, "");
