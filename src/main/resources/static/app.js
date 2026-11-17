import { productPhoto, priceBasis } from "/catalog.js?v=real-catalog-20261116";
import {
  openView,
  closeView,
  isView,
  navigate,
  initNavigation,
  confirmOperation,
  currentPage,
} from "/navigation.js?v=storefront-20261116";
import {
  initStorefront,
  showProductDetails,
} from "/storefront.js?v=real-catalog-20261116";
const state = {
  products: [],
  cart: [],
  coupons: [],
  checkout: null,
  category: "",
  query: "",
  sort: "recommended",
  filters: {},
  page: 1,
  total: 0,
  hasMore: false,
  conversationId:
    localStorage.getItem("wowo.conversationId") ||
    localStorage.getItem("bitmall.conversationId") ||
    null,
  busy: false,
};
function productImage(key) {
  return `<img class="product-image" src="${productPhoto(key)}" alt="" loading="lazy" width="360" height="300">`;
}
// 商品目录请求用递增版本丢弃旧响应，避免快速切换条件时出现串页。
let productRequest = 0;
const $ = (s) => document.querySelector(s),
  grid = $("#productGrid"),
  panel = $("#chatPanel"),
  messages = $("#chatMessages"),
  input = $("#chatInput");

async function api(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(data.message || `请求失败（${response.status}）`);
  return data;
}
// 按 SSE 事件读取模型输出和工具进度，真正的写操作仍需确认卡。
async function streamApi(url, body, onEvent) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || `请求失败（${response.status}）`);
  }
  if (!response.body) throw new Error("浏览器不支持流式响应");
  const reader = response.body.getReader(),
    decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder
      .decode(value || new Uint8Array(), { stream: !done })
      .replace(/\r\n/g, "\n");
    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      if (!block.trim() || block.startsWith(":")) continue;
      let event = "message";
      const lines = [];
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:"))
          lines.push(line.slice(5).trimStart());
      }
      if (lines.length) onEvent(event, JSON.parse(lines.join("\n")));
    }
    if (done) break;
  }
}
const filterNames = {
  brand: "品牌",
  minPrice: "最低价",
  maxPrice: "最高价",
  color: "颜色",
  size: "尺码",
  material: "材质",
  useCase: "用途",
  inStock: "仅看有货",
};
let optionsRequest = 0;
let checkoutRequestVersion = 0;
async function loadFilterOptions() {
  const request = ++optionsRequest;
  try {
    const data = await api(
      "/api/store/product-filters?" +
        new URLSearchParams(state.category ? { category: state.category } : {}),
    );
    if (request !== optionsRequest) return;
    for (const [key, label] of Object.entries(filterNames)) {
      const select = $(`#catalogFilters select[name="${key}"]`);
      if (!select) continue;
      select.innerHTML =
        `<option value="">全部${label}</option>` +
        (data[key] || [])
          .map(
            (v) =>
              `<option value="${esc(v).replaceAll('"', "&quot;")}">${esc(v)}</option>`,
          )
          .join("");
      select.value = state.filters[key] || "";
    }
  } catch (e) {
    toast("筛选选项暂时无法加载，仍可使用关键词和预算查询");
  }
}
function syncFilterForm() {
  for (const key of Object.keys(filterNames)) {
    const el = $(`#catalogFilters [name="${key}"]`);
    if (el.type === "checkbox") el.checked = !!state.filters[key];
    else el.value = state.filters[key] || "";
  }
}
async function loadProducts() {
  const request = ++productRequest;
  $("#resultLabel").textContent = "正在筛选商品…";
  grid.setAttribute("aria-busy", "true");
  grid.innerHTML = '<div class="empty-state">正在筛选商品…</div>';
  $("#previousPage").disabled = $("#nextPage").disabled = true;
  const p = new URLSearchParams({
    limit: "12",
    page: String(state.page),
    sort: state.sort,
    ...state.filters,
  });
  if (state.query) p.set("query", state.query);
  if (state.category) p.set("category", state.category);
  try {
    const data = await api(`/api/store/products?${p}`);
    if (request !== productRequest) return;
    grid.setAttribute("aria-busy", "false");
    state.products = data.items || [];
    state.total = data.total;
    state.hasMore = data.hasMore;
    state.suggestions = data.suggestions || [];
    renderProducts();
  } catch (e) {
    if (request === productRequest) {
      grid.setAttribute("aria-busy", "false");
      grid.innerHTML = `<div class="empty-state">商品加载失败：${esc(e.message)}<br><button data-reload-products>重新加载 →</button></div>`;
      $("#resultLabel").textContent = "查询未完成";
      $("#pageLabel").textContent = "";
    }
  }
}
function renderProducts() {
  $("#resultLabel").textContent =
    `共 ${state.total} 件商品 · 当前展示 ${state.products.length} 件`;
  $("#clearSearch").hidden =
    !state.query && !state.category && !Object.keys(state.filters).length;
  $("#pageLabel").textContent =
    `第 ${state.page} / ${Math.max(1, Math.ceil(state.total / 12))} 页`;
  $("#previousPage").disabled = state.page <= 1;
  $("#nextPage").disabled = !state.hasMore;
  const chips = {
    ...(state.query ? { query: state.query } : {}),
    ...(state.category ? { category: state.category } : {}),
    ...state.filters,
  };
  $("#activeFilters").innerHTML = Object.entries(chips)
    .map(
      ([k, v]) =>
        `<span>${esc({ query: "关键词", category: "品类", ...filterNames }[k])}${k === "inStock" ? "" : `：${esc(String(v))}`}</span>`,
    )
    .join("");
  if (!state.products.length) {
    grid.innerHTML = `<div class="empty-state"><b>当前条件下没有匹配商品</b><br>${esc(state.suggestions[0] || "可以调整关键词或筛选条件，也可以让沃沃帮你挑选。")}<br><small>不会自动更改你的预算、颜色和尺码</small></div>`;
    return;
  }
  const products = state.products;
  grid.innerHTML = products
    .map(
      (p) =>
        `<article class="product-card" data-product-id="${p.id}"><div class="product-art art-${attr(p.imageKey)}"><div class="product-badge">${priceBasis(p.id)}</div>${productImage(p.imageKey)}<button class="favorite-toggle" data-favorite="${p.id}" aria-label="收藏商品" aria-pressed="false">♡</button></div><div class="product-info"><div class="product-meta">${esc(p.brand)} · ${esc(p.category)}</div><h3 title="${esc(p.name).replaceAll('"', "&quot;")}">${esc(p.name)}</h3><p>${esc(p.summary)}</p><div class="product-tags">${[
          p.attributes?.material,
          p.attributes?.useCase,
          p.attributes?.type,
        ]
          .filter(Boolean)
          .map((v) => `<span>${esc(v)}</span>`)
          .join(
            "",
          )}</div><small class="matched-spec">${esc(p.matchedSpec || "")} · ${p.stock > 0 ? "有货" : "暂时缺货"}${p.matchedSkuCount > 1 ? ` · ${p.matchedSkuCount}款匹配规格` : ""}</small><div class="product-bottom"><div class="price"><strong>¥${money(p.price)}</strong>${p.originalPrice > p.price ? `<del>¥${money(p.originalPrice)}</del>` : ""}</div><div class="rating">参考价格 · 详情可核对来源</div></div></div><div class="card-actions"><button data-action="detail">查看选购</button><button data-action="ask">✦ 帮我选</button></div></article>`,
    )
    .join("");
  document.dispatchEvent(new Event("wowo:catalog-rendered"));
}
async function showProduct(id) {
  navigate("product", id);
  $("#productDialogContent").innerHTML =
    '<div class="empty-panel">正在加载商品详情…</div>';
  const request = ++detailRequest;
  try {
    const p = await api(`/api/store/products/${id}`);
    if (
      request !== detailRequest ||
      currentPage() !== "product" ||
      Number(location.hash.split("/")[2]) !== id
    )
      return;
    p.preferredSkuId = state.products.find(
      (item) => item.id === id,
    )?.matchedSkuId;
    showProductDetails(p, {
      productImage,
      esc,
      money,
      addToCart,
      openCheckout,
      openChat,
    });
  } catch (e) {
    if (request === detailRequest && currentPage() === "product")
      $("#productDialogContent").innerHTML =
        `<div class="empty-panel"><b>暂时无法查看这件商品</b><p>${esc(e.message)}</p><a href="#/catalog">返回商品列表 →</a></div>`;
  }
}
let detailRequest = 0;

function showAgentMode(mode) {
  const label = $("#agentMode");
  if (label)
    label.textContent =
      mode === "spring-ai"
        ? "AI 模式 · 可以帮你查，也可以帮你办"
        : mode === "demo"
          ? "演示模式 · 规则回复，未连接大模型"
          : "暂时无法确认助手模式";
}
async function refreshAgentMode() {
  try {
    showAgentMode((await api("/api/agent/status")).mode);
  } catch (e) {
    showAgentMode(null);
  }
}
function openChat(prefill = "") {
  refreshAgentMode();
  panel.classList.add("open");
  panel.setAttribute("aria-hidden", "false");
  if (prefill) input.value = prefill;
  setTimeout(() => input.focus(), 100);
}
function closeChat() {
  panel.classList.remove("open");
  panel.setAttribute("aria-hidden", "true");
  $("#chatFab").focus();
}
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && panel.classList.contains("open")) closeChat();
});
// 聊天仅管理会话展示，不在前端执行库存或订单规则。
const toolLabels = {
  product_search: "搜索商品",
  product_detail: "查看商品规格",
  checkout_quote: "核算优惠和金额",
  coupon_list: "查询优惠券",
  coupon_claim: "准备领券确认",
  order_query: "查询订单",
  order_create: "准备下单确认",
  order_cancel: "准备取消确认",
  load_skill: "读取办理流程",
  search_knowledge: "检索商城规则",
  save_preference: "保存购物偏好",
};
async function sendMessage(text) {
  const message = text.trim();
  if (!message || state.busy) return;
  state.busy = true;
  addMessage("user", message);
  input.value = "";
  resize();
  const streaming = addLoading(),
    bubble = streaming.querySelector(".bubble"),
    progress = document.createElement("div");
  progress.className = "agent-progress";
  progress.setAttribute("role", "status");
  progress.textContent = "正在处理…";
  messages.appendChild(progress);
  let content = "",
    finished = false;
  try {
    await streamApi(
      "/api/agent/chat/stream",
      { userId: 10001, conversationId: state.conversationId, message },
      (event, data) => {
        if (data.conversationId) {
          state.conversationId = data.conversationId;
          localStorage.setItem("wowo.conversationId", state.conversationId);
        }
        if (event === "tool_start" || event === "tool_end") {
          const label = toolLabels[data.content] || "处理商城业务";
          progress.textContent =
            event === "tool_start" ? `${label}…` : `${label}已返回，继续处理…`;
          scroll();
        } else if (event === "reset") {
          content = "";
          streaming.classList.add("loading");
          bubble.innerHTML = "<span></span><span></span><span></span>";
        } else if (event === "delta") {
          content += data.content || "";
          streaming.classList.remove("loading");
          bubble.innerHTML = format(content);
          scroll();
        } else if (event === "done") {
          finished = true;
          const reply = data.reply;
          showAgentMode(reply.debug?.mode);
          content = reply.content;
          streaming.classList.remove("loading");
          bubble.innerHTML = format(content);
          if (reply.pendingAction) addConfirm(reply.pendingAction);
          scroll();
        } else if (event === "error") {
          finished = true;
          content = data.content || "流式响应失败";
          streaming.classList.remove("loading");
          bubble.innerHTML = format(content);
          scroll();
        }
      },
    );
    if (!finished) throw new Error("流式连接提前结束");
  } catch (e) {
    if (!content) {
      streaming.remove();
      addMessage("assistant", `这次请求没有处理成功：${e.message}`);
    } else {
      streaming.classList.remove("loading");
      bubble.innerHTML = format(`${content}\n\n[连接中断：${e.message}]`);
    }
  } finally {
    progress.remove();
    state.busy = false;
  }
}
async function confirmAction(id, approved, card) {
  if (state.busy) return;
  state.busy = true;
  card.querySelectorAll("button").forEach((b) => (b.disabled = true));
  const loading = addLoading();
  try {
    const reply = await api(`/api/agent/actions/${id}/confirm`, {
      method: "POST",
      body: JSON.stringify({ userId: 10001, approved }),
    });
    loading.remove();
    card.remove();
    addMessage("assistant", reply.content);
    if (reply.orderTraceId) rememberOrder(reply.orderTraceId, "agent");
    if (approved) {
      loadProducts();
      loadCart();
      loadCoupons();
    }
  } catch (e) {
    loading.remove();
    addMessage("assistant", `确认结果处理失败：${e.message}`);
    card.querySelectorAll("button").forEach((b) => (b.disabled = false));
  } finally {
    state.busy = false;
  }
}
// 购物车写操作串行执行，防止快速点击用旧数量覆盖新结果；金额始终使用后端汇总。
let cartMutation = false;
async function loadCart(open = false) {
  if (open) openView("cartDialog");
  try {
    const data = await api("/api/store/cart?userId=10001");
    state.cart = data.items || [];
    $("#cartCount").textContent = data.itemCount || 0;
    $("#railCartCount").textContent = data.itemCount || 0;
    renderCart(data);
  } catch (e) {
    $("#cartList").innerHTML =
      `<div class="empty-panel"><b>购物车暂时无法加载</b><p>${esc(e.message)}</p><button data-reload-cart>重新加载 →</button></div>`;
    $("#cartCheckoutButton").disabled = true;
    toast(e.message);
  }
}
function renderCart(data) {
  const selected = state.cart.filter((i) => i.selected),
    count = selected.reduce((n, i) => n + i.quantity, 0),
    all = $("#selectAllCart");
  $("#cartTotal").textContent = `¥${money(data.selectedAmount || 0)}`;
  $("#cartSelectedCount").textContent = `${count} 件`;
  $("#cartItemLabel").textContent = `(${state.cart.length})`;
  $("#cartCheckoutButton").disabled = !selected.length || cartMutation;
  $("#cartCheckoutButton").textContent =
    `去结算${count ? "（" + count + "）" : ""} →`;
  all.checked = state.cart.length > 0 && selected.length === state.cart.length;
  all.indeterminate =
    selected.length > 0 && selected.length < state.cart.length;
  all.disabled = !state.cart.length || cartMutation;
  const box = $("#cartList");
  if (!state.cart.length) {
    box.innerHTML =
      '<div class="empty-panel"><b>购物车还是空的</b><p>把喜欢的好物带回来吧。</p><a href="#/catalog">去发现好物 →</a></div>';
    return;
  }
  box.innerHTML = state.cart
    .map(
      (i) =>
        `<div class="cart-row" data-cart-id="${i.id}"><input type="checkbox" data-cart-select aria-label="选择 ${esc(i.productName)}" ${i.selected ? "checked" : ""}><a class="cart-art" href="#/product/${i.productId}" aria-label="查看 ${esc(i.productName)}">${productImage(i.imageKey)}</a><div class="cart-info"><a href="#/product/${i.productId}">${esc(i.productName)}</a><small>${esc(i.skuSpec)} · ${i.stock ? "有货" : "暂时缺货"}<br>单价 ¥${money(i.price)}</small><div class="quantity"><button data-qty="-1" aria-label="减少 ${esc(i.productName)} 的数量" ${i.quantity <= 1 ? "disabled" : ""}>−</button><span>${i.quantity}</span><button data-qty="1" aria-label="增加 ${esc(i.productName)} 的数量" ${i.quantity >= Math.min(i.stock, 20) ? "disabled" : ""}>＋</button></div></div><div class="cart-row-price"><strong>¥${money(Number(i.price) * i.quantity)}</strong><button class="cart-remove" data-remove-cart>移除</button></div></div>`,
    )
    .join("");
}
async function mutateCart(work) {
  if (cartMutation) return;
  cartMutation = true;
  $("#cartCheckoutButton").disabled = true;
  $("#selectAllCart").disabled = true;
  $("#cartList")
    .querySelectorAll("button,input")
    .forEach((el) => (el.disabled = true));
  try {
    await work();
  } catch (e) {
    toast(e.message);
  } finally {
    cartMutation = false;
    await loadCart();
  }
}

async function addToCart(skuId, quantity = 1) {
  try {
    await api("/api/store/cart", {
      method: "POST",
      body: JSON.stringify({ userId: 10001, skuId, quantity }),
    });
    await loadCart();
    toast("已加入购物车");
  } catch (e) {
    toast(e.message);
  }
}
let couponTab = "all";
function couponState(c) {
  if (c.userStatus === "USED") return "used";
  if (c.userStatus === "LOCKED") return "locked";
  if (c.userStatus === "EXPIRED" || new Date(c.endAt).getTime() < Date.now())
    return "expired";
  if (new Date(c.startAt).getTime() > Date.now()) return "upcoming";
  return c.userCouponId ? "available" : "unclaimed";
}
async function loadCoupons(open = false) {
  if (open) openView("couponDialog");
  try {
    const data = await api("/api/store/coupons?userId=10001");
    state.coupons = data.items || [];
    renderCoupons();
    document.dispatchEvent(
      new CustomEvent("wowo:coupons", { detail: state.coupons }),
    );
  } catch (e) {
    $("#couponList").innerHTML =
      `<div class="empty-panel"><b>优惠券暂时无法加载</b><p>${esc(e.message)}</p><button data-reload-coupons>重新加载 →</button></div>`;
    toast(e.message);
  }
}
function renderCoupons() {
  const list = state.coupons.filter(
    (c) => couponTab === "all" || couponState(c) === couponTab,
  );
  $("#couponTabs")
    .querySelectorAll("button")
    .forEach((b) =>
      b.classList.toggle("active", b.dataset.couponTab === couponTab),
    );
  if (!list.length) {
    $("#couponList").innerHTML =
      '<div class="empty-panel"><b>这里还没有优惠券</b><p>到领券中心看看有没有喜欢的优惠。</p></div>';
    return;
  }
  const labels = {
    used: "已使用",
    locked: "订单使用中",
    expired: "已过期",
    upcoming: "未生效",
    available: "已领取",
    unclaimed: "待领取",
  };
  $("#couponList").innerHTML = `<div class="coupon-list">${list
    .map((c) => {
      const status = couponState(c),
        canClaim = status === "unclaimed" && c.remainingStock > 0;
      return `<article class="coupon-card ${["used", "locked", "expired", "upcoming"].includes(status) ? "is-disabled" : ""}"><span class="coupon-label">${labels[status]}</span><div class="coupon-value"><b><small>¥</small>${money(c.discountAmount)}</b><small>${Number(c.thresholdAmount) > 0 ? "满 " + money(c.thresholdAmount) + " 元可用" : "无门槛"}</small></div><h3>${esc(c.name)}</h3><p>全店符合条件的商品可用<br>${date(c.startAt)} — ${date(c.endAt)}<br>${status === "unclaimed" ? "剩余 " + c.remainingStock + " 张" : "每笔订单限用一张"}</p>${status === "available" ? "<button data-use-coupon>去使用 →</button>" : `<button data-claim-coupon="${c.id}" ${canClaim ? "" : "disabled"}>${canClaim ? "立即领取" : status === "unclaimed" ? "已领完" : labels[status]}</button>`}</article>`;
    })
    .join("")}</div>`;
}

async function claimCoupon(couponId) {
  try {
    await api(`/api/store/coupons/${couponId}/claim`, {
      method: "POST",
      body: JSON.stringify({ userId: 10001 }),
    });
    toast("优惠券领取成功");
    await loadCoupons();
  } catch (e) {
    toast(e.message);
  }
}
async function openCheckout(source, skuId = null, quantity = 1) {
  state.checkout = { request: { userId: 10001, source, skuId, quantity } };
  const checkout = state.checkout;
  openView("checkoutDialog");
  $("#checkoutContent").innerHTML =
    '<div class="empty-panel">正在核对商品与优惠…</div>';
  $("#checkoutPay").textContent = "—";
  $("#submitOrderButton").disabled = true;
  await loadCoupons();
  if (state.checkout !== checkout || currentPage() !== "checkout") return;
  await refreshCheckout(null);
}
// 改动购买内容立即作废旧凭证；试算完成前不能用旧 trace 提交新选择。
async function refreshCheckout(userCouponId) {
  const checkout = state.checkout,
    version = ++checkoutRequestVersion;
  if (!checkout || checkout.submitting) return;
  checkout.traceId = null;
  checkout.preparing = true;
  const button = $("#submitOrderButton");
  button.disabled = true;
  button.textContent = "正在核算…";
  try {
    const request = { ...checkout.request, userCouponId: userCouponId || null };
    const quote = await api("/api/store/checkout/quote", {
      method: "POST",
      body: JSON.stringify(request),
    });
    const [prepared, options] = await Promise.all([
      api("/api/store/order-tokens", { method: "POST" }),
      api("/api/store/order-options"),
    ]);
    if (version !== checkoutRequestVersion || state.checkout !== checkout)
      return;
    state.checkout = {
      request,
      quote,
      traceId: prepared.traceId,
      mode: options.submissionMode,
      preparing: false,
      submitting: false,
    };
    renderCheckout();
  } catch (e) {
    if (version !== checkoutRequestVersion || state.checkout !== checkout)
      return;
    checkout.preparing = false;
    checkout.quote = null;
    toast(e.message);
    button.textContent = "请重新确认购买内容";
    $("#checkoutPay").textContent = "—";
    $("#checkoutContent").innerHTML =
      `<div class="empty-panel"><b>暂时无法完成试算</b><p>${esc(e.message)}</p><button id="retryCheckout">重新试算 →</button></div>`;
    $("#retryCheckout").onclick = () => refreshCheckout(userCouponId);
  }
}
function checkoutButtonLabel(mode) {
  return mode === "ASYNC"
    ? "确认排队购买"
    : mode === "AUTO"
      ? "确认购买"
      : "提交订单";
}
// 结算只展示服务端报价，地址、商品快照和券门槛都不能由页面自行推断。
function renderCheckout() {
  const q = state.checkout.quote,
    available = state.coupons.filter(
      (c) =>
        couponState(c) === "available" &&
        Number(c.thresholdAmount) <= Number(q.goodsAmount),
    );
  $("#checkoutContent").innerHTML =
    `<div class="checkout-layout"><div class="checkout-main"><section class="checkout-section address-section"><h3>收货信息 <small>默认地址</small></h3><p>${esc(q.address)}</p><small>配送参考：${esc(q.estimatedDelivery)}，实际进度以订单查询为准。</small></section><div class="checkout-items">${q.items.map((i) => `<div class="checkout-item"><div class="checkout-art">${productImage(i.imageKey)}</div><span><b>${esc(i.productName)}</b><small>${esc(i.skuSpec)} · 数量 ${i.quantity}</small></span><strong>¥${money(Number(i.price) * i.quantity)}</strong></div>`).join("")}</div><section class="checkout-section"><h3>使用优惠券</h3><select class="coupon-select" id="checkoutCoupon" aria-label="选择结算优惠券"><option value="">不使用优惠券</option>${available.map((c) => `<option value="${c.userCouponId}" ${q.userCouponId === c.userCouponId ? "selected" : ""}>${esc(c.name)}（减 ¥${money(c.discountAmount)}）</option>`).join("")}</select><p class="checkout-coupon-note">${available.length ? "已为你筛选符合当前商品金额的优惠券。" : "暂无符合当前金额且已领取的可用券。"}</p></section>${["ASYNC", "AUTO"].includes(state.checkout.mode) ? '<p class="checkout-queue-note">繁忙时购买需要稍候，请以订单中心的最终结果为准。</p>' : ""}</div><aside class="checkout-aside"><div class="price-summary"><h3>金额明细</h3><div><span>商品金额</span><span>¥${money(q.goodsAmount)}</span></div><div><span>优惠抵扣</span><span>− ¥${money(q.discountAmount)}</span></div><div><span>运费</span><span>¥${money(q.freightAmount)}</span></div><div><span>应付金额</span><strong>¥${money(q.payAmount)}</strong></div></div><p>提交后将生成待付款订单，请及时前往订单中心处理。体验商城不产生真实交易。</p></aside></div>`;
  $("#checkoutPay").textContent = `¥${money(q.payAmount)}`;
  $("#submitOrderButton").textContent = checkoutButtonLabel(
    state.checkout.mode,
  );
  $("#submitOrderButton").disabled =
    !state.checkout.traceId || !!state.checkout.submitting;
  $("#checkoutCoupon").onchange = (e) =>
    refreshCheckout(e.target.value ? Number(e.target.value) : null);
}
// 首次提交绑定编号和购买内容；网络重试和刷新后保留两者，不能重新生成另一笔购买。
async function submitOrder() {
  if (
    !state.checkout?.traceId ||
    state.checkout.preparing ||
    state.checkout.submitting
  )
    return;
  const checkout = state.checkout;
  checkout.submitting = true;
  const coupon = $("#checkoutCoupon");
  if (coupon) coupon.disabled = true;
  const button = $("#submitOrderButton"),
    traceId = state.checkout.traceId;
  button.disabled = true;
  button.textContent = "提交中…";
  const purchase = {
    ...checkout.request,
    expectedPayAmount: checkout.quote.payAmount,
    expectedItems: checkout.quote.items.map((i) => ({
      skuId: i.skuId,
      quantity: i.quantity,
    })),
  };
  rememberOrder(traceId, "page", purchase);
  try {
    const result = await api("/api/store/orders/submit", {
      method: "POST",
      body: JSON.stringify({ userId: 10001, traceId, purchase }),
    });
    if (result.status === "SUCCESS") {
      await finishTrackedOrder(result);
    } else if (["FAILED", "EXPIRED"].includes(result.status)) {
      forgetOrder(traceId);
      checkout.traceId = null;
      toast(result.message);
      return;
    } else if (result.status === "PROCESSING")
      toast("购买已进入处理队列，可在订单中心查看结果");
    else {
      toast(result.message || "购买尚未受理，请使用原编号重试");
      return;
    }
    closeView("checkoutDialog");
    await loadOrders(true);
  } catch (e) {
    toast(`${e.message}；将使用原编号查询结果`);
  } finally {
    checkout.submitting = false;
    button.disabled = !checkout.traceId;
    button.textContent = checkout.traceId
      ? checkoutButtonLabel(checkout.mode)
      : "请重新确认购买内容";
    if (coupon) coupon.disabled = false;
    scheduleOrderPoll();
  }
}

// 持久化待查询编号，页面刷新后仍能找到已经受理但尚未完成的购买。
let trackedOrders = {};
try {
  trackedOrders = JSON.parse(
    localStorage.getItem("wowo.pendingOrders") || "{}",
  );
} catch (e) {
  trackedOrders = {};
}
let orderPollTimer = null;
function rememberOrder(traceId, origin, purchase = null) {
  trackedOrders[traceId] = {
    ...trackedOrders[traceId],
    origin,
    createdAt: Date.now(),
    purchase,
    status: "INIT",
  };
  localStorage.setItem("wowo.pendingOrders", JSON.stringify(trackedOrders));
  renderPendingOrders();
  scheduleOrderPoll();
}
function forgetOrder(traceId) {
  delete trackedOrders[traceId];
  localStorage.setItem("wowo.pendingOrders", JSON.stringify(trackedOrders));
  renderPendingOrders();
}
function scheduleOrderPoll() {
  if (!orderPollTimer && Object.keys(trackedOrders).length)
    orderPollTimer = setTimeout(pollOrderProgress, 2000);
}
async function finishTrackedOrder(result) {
  const item = trackedOrders[result.traceId];
  if (!item) return;
  forgetOrder(result.traceId);
  // 超时后轮询确认成功时也关闭原确认页，避免用户继续盯着提交按钮。
  if (state.checkout?.traceId === result.traceId && isView("checkoutDialog"))
    closeView("checkoutDialog");
  toast(`下单成功：${result.orderId}`);
  if (item?.origin === "agent")
    addMessage(
      "assistant",
      `订单已创建，订单号：${result.orderId}，应付 ¥${money(result.payAmount)}，请到订单中心查看当前支付状态。`,
    );
  await Promise.all([loadProducts(), loadCart(), loadCoupons()]);
  await loadOrders(item.origin === "page");
}
async function pollOrderProgress() {
  orderPollTimer = null;
  for (const traceId of Object.keys(trackedOrders)) {
    try {
      const result = await api(
        `/api/store/order-traces/${encodeURIComponent(traceId)}?userId=10001`,
      );
      if (trackedOrders[traceId]) {
        trackedOrders[traceId].status = result.status;
        localStorage.setItem(
          "wowo.pendingOrders",
          JSON.stringify(trackedOrders),
        );
      }
      if (result.status === "SUCCESS") await finishTrackedOrder(result);
      else if (["FAILED", "EXPIRED"].includes(result.status)) {
        const item = trackedOrders[traceId];
        forgetOrder(traceId);
        toast(result.message);
        if (item?.origin === "agent")
          addMessage("assistant", `本次下单未成功：${result.message}`);
      } else if (result.status === "INIT") {
        // 请求可能根本没有到达服务端：保留原凭证，允许继续提交，而不是创建新 trace。
        trackedOrders[traceId].status = "INIT";
        renderPendingOrders();
      }
    } catch (e) {
      /* 网络故障不等于下单失败，保留编号继续查询。 */
    }
  }
  scheduleOrderPoll();
}
function renderPendingOrders() {
  const list = $("#ordersList");
  if (!list) return;
  let box = $("#pendingOrderList");
  if (!box) {
    box = document.createElement("div");
    box.id = "pendingOrderList";
    list.before(box);
  }
  box.innerHTML = Object.keys(trackedOrders)
    .map(
      (id) =>
        `<article class="order-card"><b>正在核实下单结果</b><p>查询编号：${esc(id)}</p><small>系统会自动更新结果，请勿重复发起购买。</small>${trackedOrders[id].status === "INIT" ? `<button data-retry-trace="${attr(id)}">继续提交这次购买</button>` : ""}</article>`,
    )
    .join("");
  box.querySelectorAll("[data-retry-trace]").forEach(
    (button) =>
      (button.onclick = async () => {
        button.disabled = true;
        try {
          const result = await api("/api/store/orders/submit", {
            method: "POST",
            body: JSON.stringify({
              userId: 10001,
              traceId: button.dataset.retryTrace,
              purchase: trackedOrders[button.dataset.retryTrace]?.purchase,
            }),
          });
          if (result.status === "SUCCESS") await finishTrackedOrder(result);
          else if (["FAILED", "EXPIRED"].includes(result.status)) {
            forgetOrder(result.traceId);
            toast(result.message);
          }
        } catch (e) {
          toast(e.message);
        } finally {
          button.disabled = false;
          scheduleOrderPoll();
        }
      }),
  );
}
// 加载页面时恢复尚未完成的查询。
setTimeout(() => {
  renderPendingOrders();
  scheduleOrderPoll();
}, 0);
let demoPaymentEnabled = false;
let orderItems = [],
  orderTab = "ALL";
async function loadOrders(open = false) {
  if (open) openView("ordersDialog");
  try {
    const [data, options] = await Promise.all([
      api("/api/store/orders?userId=10001"),
      api("/api/store/order-options"),
    ]);
    demoPaymentEnabled = options.demoPaymentEnabled === true;
    orderItems = data.items || [];
    renderOrders();
    renderPendingOrders();
  } catch (e) {
    $("#ordersList").innerHTML =
      `<div class="empty-panel"><b>订单暂时无法加载</b><p>${esc(e.message)}</p><button data-reload-orders>重新加载 →</button></div>`;
    toast(e.message);
  }
}
// 历史订单名称与价格保持快照，仅使用当前陈列图辅助识别，匹配不到时使用通用商品图标。
function orderArt(item) {
  const p = (state.catalog || []).find((p) => p.name === item.productName);
  return `<div class="order-art">${p ? productImage(p.imageKey) : '<svg class="icon" aria-hidden="true"><use href="#i-bag"/></svg>'}</div>`;
}
function orderLines(order) {
  return order.items
    .map(
      (i) =>
        `<div class="order-item-line">${orderArt(i)}<span>${esc(i.productName)}<small>${esc(i.skuSpec)} · 数量 ${i.quantity}</small></span><b>¥${money(Number(i.unitPrice) * i.quantity)}</b></div>`,
    )
    .join("");
}
function renderOrders() {
  const query = $("#orderSearch").value.trim().toLowerCase();
  const orders = orderItems.filter(
    (o) =>
      (orderTab === "ALL" ||
        (orderTab === "CLOSED"
          ? ["CLOSED", "CANCELLED"].includes(o.status)
          : o.status === orderTab)) &&
      (!query ||
        String(o.id).toLowerCase().includes(query) ||
        o.items.some((i) => i.productName.toLowerCase().includes(query))),
  );
  $("#orderTabs")
    .querySelectorAll("button")
    .forEach((b) =>
      b.classList.toggle("active", b.dataset.orderTab === orderTab),
    );
  if (!orders.length) {
    $("#ordersList").innerHTML =
      `<div class="empty-panel"><b>${orderItems.length ? "没有匹配的订单" : "还没有订单"}</b><p>${orderItems.length ? "试试其他状态、订单号或商品名称。" : "喜欢的好物，值得带回家。"}</p><a href="#/catalog">继续逛逛 →</a></div>`;
    return;
  }
  $("#ordersList").innerHTML = orders
    .map(
      (o) =>
        `<article class="order-card" data-order-id="${esc(o.id)}"><div class="order-head"><span><time>${dateTime(o.createdAt)}</time><span>订单号 ${esc(o.id)}</span></span><span class="order-status status-${attr(o.status)}">${statusText(o.status)}</span></div><div class="order-goods">${orderLines(o)}</div><div class="order-total"><span>${o.couponName ? "已用 " + esc(o.couponName) + " · " : ""}${o.status === "PENDING_PAYMENT" ? "应付" : "订单金额"} <strong>¥${money(o.payAmount)}</strong>${o.status === "PENDING_PAYMENT" ? `<small data-deadline="${esc(o.paymentExpiresAt)}">请于 ${dateTime(o.paymentExpiresAt)} 前支付</small>` : ""}</span><div class="order-actions"><button data-order-detail>订单详情</button><button data-ask-order>问助手</button>${o.status === "PENDING_PAYMENT" ? `${demoPaymentEnabled ? "<button data-demo-pay>模拟支付</button>" : ""}<button class="danger" data-cancel-order>取消订单</button>` : ""}</div></div></article>`,
    )
    .join("");
  updateDeadlines();
}
function showOrderDetail(id) {
  const o = orderItems.find((o) => o.id === id);
  if (!o) return;
  $("#orderDetailContent").innerHTML =
    `<div class="order-detail-state"><h3>${statusText(o.status)}</h3><p>${o.status === "PENDING_PAYMENT" ? "订单已创建，请在支付期限内完成付款。" : o.status === "PAID" ? "已记录付款，配送情况请向助手查询。" : ["CLOSED", "CANCELLED"].includes(o.status) ? "订单已关闭，库存与优惠券以服务端处理结果为准。" : "订单状态以服务端记录为准。"}</p></div><dl class="order-detail-grid"><div><dt>订单编号</dt><dd>${esc(o.id)}</dd></div><div><dt>创建时间</dt><dd>${dateTime(o.createdAt)}</dd></div><div><dt>收货地址</dt><dd>${esc(o.address || "未提供")}</dd></div><div><dt>支付截止</dt><dd>${dateTime(o.paymentExpiresAt) || "—"}</dd></div></dl>${orderLines(o)}${o.logistics?.length ? `<section class="order-logistics"><h3>物流动态</h3><ol>${o.logistics.map((event) => `<li><time>${dateTime(event.eventTime)}</time><b>${esc(event.status)}</b><p>${esc(event.description)}</p></li>`).join("")}</ol></section>` : ""}<div class="price-summary"><div><span>商品金额</span><b>¥${money(o.goodsAmount)}</b></div><div><span>优惠抵扣</span><b>− ¥${money(o.discountAmount)}</b></div><div><span>运费</span><b>¥${money(o.freightAmount)}</b></div><div><span>应付金额</span><b>¥${money(o.payAmount)}</b></div></div><p class="order-detail-note">商品名称、规格与金额为下单快照。当前为体验商城，不产生真实付款和发货。</p>`;
  $("#orderDetailDialog").showModal();
}
let deadlineRefreshAt = 0;
function updateDeadlines() {
  let expired = false;
  document.querySelectorAll("[data-deadline]").forEach((el) => {
    const remain = Math.ceil(
      (new Date(el.dataset.deadline).getTime() - Date.now()) / 1000,
    );
    if (!Number.isFinite(remain)) {
      el.textContent = "请查看订单支付截止时间";
      return;
    }
    if (remain <= 0) {
      el.textContent = "支付期限已到，正在核实订单状态";
      expired = true;
    } else
      el.textContent = `剩余 ${String(Math.floor(remain / 60)).padStart(2, "0")}:${String(remain % 60).padStart(2, "0")} 支付 · 超时自动关闭`;
  });
  // 前端到期只刷新真实状态，不自行宣布订单已关闭；失败时最多每 15 秒重试。
  if (expired && Date.now() - deadlineRefreshAt > 15000) {
    deadlineRefreshAt = Date.now();
    loadOrders();
  }
}
setInterval(() => {
  if (isView("ordersDialog") && !document.hidden) updateDeadlines();
}, 1000);

// 演示环境显式显示模拟支付，重复点击仍由后端订单状态保证幂等。
async function payManualOrder(orderId, button) {
  button.disabled = true;
  try {
    const result = await api(
      `/api/store/orders/${encodeURIComponent(orderId)}/demo-pay`,
      { method: "POST", body: JSON.stringify({ userId: 10001 }) },
    );
    toast(result.message);
    await Promise.all([loadOrders(), loadProducts(), loadCoupons()]);
  } catch (e) {
    toast(`${e.message}；请刷新订单核对状态`);
  } finally {
    button.disabled = false;
  }
}
async function cancelManualOrder(orderId) {
  if (
    !(await confirmOperation(
      "取消这笔订单？",
      "取消后将释放库存和占用的优惠券。确认前请核对订单号：" + orderId,
    ))
  )
    return;
  try {
    await api(`/api/store/orders/${orderId}/cancel`, {
      method: "POST",
      body: JSON.stringify({ userId: 10001 }),
    });
    toast("订单已取消，库存和优惠券已返还");
    await Promise.all([loadOrders(), loadProducts(), loadCoupons()]);
  } catch (e) {
    toast(e.message);
  }
}
function addMessage(role, content) {
  const el = document.createElement("div");
  el.className = `message ${role}`;
  el.innerHTML =
    role === "assistant"
      ? `<div class="mini-avatar">✦</div><div class="bubble">${format(content)}</div>`
      : `<div class="bubble">${format(content)}</div>`;
  messages.appendChild(el);
  scroll();
  return el;
}
function addLoading() {
  const el = document.createElement("div");
  el.className = "message assistant loading";
  el.innerHTML =
    '<div class="mini-avatar">✦</div><div class="bubble"><span></span><span></span><span></span></div>';
  messages.appendChild(el);
  scroll();
  return el;
}
function addConfirm(action) {
  const card = document.createElement("div");
  card.className = "confirm-card";
  const coupon = action.toolName === "coupon_claim",
    cancel = action.toolName === "order_cancel";
  card.innerHTML = `<small>需要你的确认</small><p>${esc(action.summary)}</p><div class="confirm-actions"><button class="reject">${cancel ? "暂不取消" : coupon ? "暂不领取" : "取消"}</button><button class="approve">${cancel ? "确认取消订单" : coupon ? "确认领取" : "确认执行"}</button></div>`;
  card.querySelector(".reject").onclick = () =>
    confirmAction(action.id, false, card);
  card.querySelector(".approve").onclick = () =>
    confirmAction(action.id, true, card);
  messages.appendChild(card);
  scroll();
}
function scroll() {
  messages.scrollTop = messages.scrollHeight;
}
function resize() {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 100) + "px";
}
function money(v) {
  return Number(v).toLocaleString("zh-CN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}
function compact(v) {
  return v > 9999 ? `${(v / 10000).toFixed(1)}万` : v;
}
function date(v) {
  return String(v || "").slice(0, 10);
}
function dateTime(v) {
  return String(v || "")
    .replace("T", " ")
    .slice(0, 16);
}
function statusText(v) {
  return (
    {
      PAID: "已付款",
      PENDING_PAYMENT: "待付款",
      SHIPPED: "配送中",
      COMPLETED: "已完成",
      CANCELLED: "已取消",
      CLOSED: "已超时关闭",
    }[v] || v
  );
}
function esc(v) {
  const d = document.createElement("div");
  d.textContent = v ?? "";
  return d.innerHTML.replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}
function attr(v) {
  return String(v ?? "").replace(/[^a-zA-Z0-9_-]/g, "");
}
function inlineMarkdown(value) {
  // 引用先变成占位符，避免后续 Markdown 替换改写安全序列化后的 href。
  const links = [];
  const text = value.replace(
    /\[([^\]\n]+)\]\((https:\/\/[^\s)]+)\)/g,
    (whole, label, address) => {
      try {
        const decoder = document.createElement("textarea");
        decoder.innerHTML = address;
        const url = new URL(decoder.value);
        if (url.protocol !== "https:" || url.username || url.password)
          return whole;
        const link = document.createElement("a");
        link.href = url.href;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        decoder.innerHTML = label;
        link.textContent = decoder.value;
        links.push(link.outerHTML);
        return `\uE000${links.length - 1}\uE001`;
      } catch {
        return whole;
      }
    },
  );
  return text
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\uE000(\d+)\uE001/g, (_, index) => links[Number(index)] || "");
}

function format(value) {
  const lines = esc(value).replace(/\r/g, "").split("\n"),
    html = [];
  for (let i = 0; i < lines.length; ) {
    const line = lines[i];
    if (!line.trim()) {
      i++;
      continue;
    }
    if (
      /^\|.*\|$/.test(line.trim()) &&
      i + 1 < lines.length &&
      /^\|?[\s:|-]+\|?$/.test(lines[i + 1].trim())
    ) {
      const cells = (row) =>
        row
          .trim()
          .replace(/^\||\|$/g, "")
          .split("|")
          .map((cell) => inlineMarkdown(cell.trim()));
      const head = cells(line);
      i += 2;
      const rows = [];
      while (i < lines.length && /^\|.*\|$/.test(lines[i].trim()))
        rows.push(cells(lines[i++]));
      html.push(
        `<div class="md-table-wrap"><table><thead><tr>${head.map((cell) => `<th>${cell}</th>`).join("")}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`,
      );
      continue;
    }
    if (/^[-*]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i]))
        items.push(
          `<li>${inlineMarkdown(lines[i++].replace(/^[-*]\s+/, ""))}</li>`,
        );
      html.push(`<ul>${items.join("")}</ul>`);
      continue;
    }
    if (/^#{1,3}\s+/.test(line)) {
      html.push(`<h4>${inlineMarkdown(line.replace(/^#{1,3}\s+/, ""))}</h4>`);
      i++;
      continue;
    }
    const paragraph = [];
    while (
      i < lines.length &&
      lines[i].trim() &&
      !/^[-*]\s+/.test(lines[i]) &&
      !/^#{1,3}\s+/.test(lines[i]) &&
      !(
        i + 1 < lines.length &&
        /^\|.*\|$/.test(lines[i].trim()) &&
        /^\|?[\s:|-]+\|?$/.test(lines[i + 1].trim())
      )
    )
      paragraph.push(inlineMarkdown(lines[i++]));
    html.push(`<p>${paragraph.join("<br>")}</p>`);
  }
  return html.join("");
}
function toast(m) {
  const e = $("#toast");
  e.textContent = m;
  e.classList.add("show");
  setTimeout(() => e.classList.remove("show"), 2300);
}

function browse(category = "", query = "") {
  navigate("catalog");
  state.category = category;
  state.query = query;
  state.filters = {};
  state.page = 1;
  syncFilterForm();
  loadFilterOptions();
  $("#searchInput").value = query;
  document
    .querySelectorAll("#categoryTabs button")
    .forEach((b) =>
      b.classList.toggle("active", b.dataset.category === category),
    );
  loadProducts();
}
$("#searchButton").onclick = () => browse("", $("#searchInput").value.trim());
$("#searchInput").onkeydown = (e) => {
  if (e.key === "Enter") browse("", e.target.value.trim());
};
$("#clearSearch").onclick = () => browse();
$("#productSort").onchange = (e) => {
  state.sort = e.target.value;
  state.page = 1;
  loadProducts();
};
$("#catalogFilters").onsubmit = (e) => {
  e.preventDefault();
  const f = new FormData(e.currentTarget),
    filters = {};
  for (const [key, value] of f.entries())
    if (String(value).trim())
      filters[key] = key === "inStock" ? "true" : String(value).trim();
  if (
    filters.minPrice &&
    filters.maxPrice &&
    Number(filters.minPrice) > Number(filters.maxPrice)
  ) {
    toast("最低价格不能高于最高价格");
    return;
  }
  state.filters = filters;
  state.page = 1;
  loadProducts();
};
$("#previousPage").onclick = () => {
  if (state.page > 1) {
    state.page--;
    loadProducts();
  }
};
$("#nextPage").onclick = () => {
  if (state.hasMore) {
    state.page++;
    loadProducts();
  }
};
document
  .querySelectorAll("[data-category]")
  .forEach((b) => (b.onclick = () => browse(b.dataset.category)));
document.querySelectorAll("[data-open]").forEach(
  (b) =>
    (b.onclick = () =>
      ({
        cart: () => loadCart(true),
        orders: () => loadOrders(true),
        coupons: () => loadCoupons(true),
        chat: () => openChat(),
      })[b.dataset.open]()),
);
grid.onclick = (e) => {
  if (e.target.closest("[data-reload-products]")) {
    loadProducts();
    return;
  }
  const card = e.target.closest(".product-card");
  if (!card) return;
  const p = state.products.find((x) => x.id === Number(card.dataset.productId));
  if (e.target.closest("[data-favorite]")) return;
  if (e.target.closest('[data-action="ask"]'))
    openChat(
      `请介绍一下商品 ${p.name}（商品ID ${p.id}），并帮我判断是否适合我。`,
    );
  else showProduct(p.id);
};
$("#closeDialog").onclick = () => closeView("productDialog");
$("#couponsButton").onclick = () => loadCoupons(true);
$("#cartButton").onclick = () => loadCart(true);
$("#ordersButton").onclick = () => loadOrders(true);
$("#couponList").onclick = (e) => {
  const button = e.target.closest("[data-claim-coupon]");
  if (button) claimCoupon(Number(button.dataset.claimCoupon));
};
$("#cartList").onclick = async (e) => {
  if (e.target.closest("[data-reload-cart]")) {
    loadCart();
    return;
  }
  const row = e.target.closest("[data-cart-id]");
  if (!row || cartMutation) return;
  const item = state.cart.find((i) => i.id === Number(row.dataset.cartId));
  if (e.target.closest("[data-remove-cart]")) {
    if (
      await confirmOperation(
        "移除这件商品？",
        item.productName + " 将从购物车移除，你仍可重新加入。",
      )
    )
      mutateCart(() =>
        api(`/api/store/cart/${item.id}?userId=10001`, { method: "DELETE" }),
      );
    return;
  }
  const qty = e.target.closest("[data-qty]");
  if (qty) {
    const quantity = Math.max(
      1,
      Math.min(20, item.stock, item.quantity + Number(qty.dataset.qty)),
    );
    mutateCart(() =>
      api(`/api/store/cart/${item.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          userId: 10001,
          quantity,
          selected: item.selected,
        }),
      }),
    );
  }
};
$("#cartList").onchange = (e) => {
  if (!e.target.matches("[data-cart-select]") || cartMutation) return;
  const item = state.cart.find(
    (i) => i.id === Number(e.target.closest("[data-cart-id]").dataset.cartId),
  );
  mutateCart(() =>
    api(`/api/store/cart/${item.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        userId: 10001,
        quantity: item.quantity,
        selected: e.target.checked,
      }),
    }),
  );
};
$("#selectAllCart").onchange = (e) => {
  const selected = e.target.checked;
  mutateCart(async () => {
    const results = await Promise.allSettled(
      state.cart
        .filter((i) => i.selected !== selected)
        .map((i) =>
          api(`/api/store/cart/${i.id}`, {
            method: "PATCH",
            body: JSON.stringify({
              userId: 10001,
              quantity: i.quantity,
              selected,
            }),
          }),
        ),
    );
    const failed = results.find((r) => r.status === "rejected");
    if (failed)
      throw new Error("部分商品未更新，请核对勾选：" + failed.reason.message);
  });
};
$("#cartCheckoutButton").onclick = () => {
  if (!cartMutation && state.cart.some((i) => i.selected)) openCheckout("CART");
};
$("#submitOrderButton").onclick = submitOrder;
$("#couponTabs").onclick = (e) => {
  const b = e.target.closest("[data-coupon-tab]");
  if (b) {
    couponTab = b.dataset.couponTab;
    renderCoupons();
  }
};
$("#couponList").onclick = async (e) => {
  const b = e.target.closest("[data-claim-coupon]");
  if (b) {
    b.disabled = true;
    try {
      await claimCoupon(Number(b.dataset.claimCoupon));
    } finally {
      b.disabled = false;
    }
  }
  if (e.target.closest("[data-use-coupon]")) browse();
  if (e.target.closest("[data-reload-coupons]")) loadCoupons();
};
$("#orderTabs").onclick = (e) => {
  const b = e.target.closest("[data-order-tab]");
  if (b) {
    orderTab = b.dataset.orderTab;
    renderOrders();
  }
};
$("#orderSearch").oninput = () => renderOrders();
$("#refreshOrders").onclick = () => loadOrders();
$("#ordersList").onclick = (e) => {
  if (e.target.closest("[data-reload-orders]")) {
    loadOrders();
    return;
  }
  const card = e.target.closest("[data-order-id]");
  if (!card) return;
  const id = card.dataset.orderId;
  if (e.target.closest("[data-demo-pay]"))
    payManualOrder(id, e.target.closest("[data-demo-pay]"));
  else if (e.target.closest("[data-cancel-order]")) cancelManualOrder(id);
  else if (e.target.closest("[data-order-detail]")) showOrderDetail(id);
  else if (e.target.closest("[data-ask-order]"))
    openChat(`帮我查询订单 ${id} 的状态和物流`);
};
document
  .querySelectorAll("[data-close-dialog]")
  .forEach(
    (button) =>
      (button.onclick = () =>
        document.getElementById(button.dataset.closeDialog).close()),
  );

$("#chatFab").onclick = () => openChat();
$("#heroChatButton").onclick = () =>
  openChat("推荐一款适合日常通勤、性价比高的商品");
$("#closeChat").onclick = closeChat;
$("#suggestions").onclick = (e) => {
  if (e.target.matches("button")) sendMessage(e.target.textContent);
};
$("#chatForm").onsubmit = (e) => {
  e.preventDefault();
  sendMessage(input.value);
};
input.oninput = resize;
input.onkeydown = (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage(input.value);
  }
};
// A pausable, keyboard-accessible carousel; no auto advance while reading or interacting.
let slideIndex = 0,
  slidePaused = matchMedia("(prefers-reduced-motion: reduce)").matches,
  slideHovered = false;
const carousel = $("#carousel"),
  slides = [...document.querySelectorAll("[data-slide]")];
function showSlide(index) {
  slideIndex = (index + slides.length) % slides.length;
  slides.forEach((slide, i) => (slide.hidden = i !== slideIndex));
  document
    .querySelectorAll("[data-goto-slide]")
    .forEach((dot, i) =>
      dot.setAttribute("aria-pressed", String(i === slideIndex)),
    );
}
function updatePause() {
  const b = $("#pauseSlides");
  b.textContent = slidePaused ? "▶" : "Ⅱ";
  b.setAttribute("aria-label", slidePaused ? "开启自动轮播" : "暂停自动轮播");
}
$("#prevSlide").onclick = () => showSlide(slideIndex - 1);
$("#nextSlide").onclick = () => showSlide(slideIndex + 1);
document
  .querySelectorAll("[data-goto-slide]")
  .forEach((b) => (b.onclick = () => showSlide(Number(b.dataset.gotoSlide))));
$("#pauseSlides").onclick = () => {
  slidePaused = !slidePaused;
  updatePause();
};
carousel.onmouseenter = () => (slideHovered = true);
carousel.onmouseleave = () => (slideHovered = false);
carousel.onkeydown = (e) => {
  if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
    e.preventDefault();
    showSlide(slideIndex + (e.key === "ArrowLeft" ? -1 : 1));
  }
};
setInterval(() => {
  if (
    !slidePaused &&
    !slideHovered &&
    !document.hidden &&
    !carousel.contains(document.activeElement)
  )
    showSlide(slideIndex + 1);
}, 5500);
initStorefront({
  api,
  showProduct,
  openChat,
  browse,
  loadCoupons,
  toast,
  productImage,
  esc,
  money,
  compact,
  onCatalogReady: (items) => {
    state.catalog = items;
    if (orderItems.length) renderOrders();
  },
});
initNavigation({
  home: () => {},
  catalog: () => {},
  product: (id) => showProduct(Number(id)),
  cart: () => loadCart(),
  coupons: () => loadCoupons(),
  orders: () => loadOrders(),
  checkout: () => {
    if (!state.checkout?.quote) {
      $("#submitOrderButton").disabled = true;
    }
  },
});
refreshAgentMode();
updatePause();
loadFilterOptions();
loadProducts();
loadCart();
loadCoupons();
