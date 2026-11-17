import { catalogInfo, priceBasis } from "/catalog.js?v=real-catalog-20261116";
import { openView } from "/navigation.js?v=storefront-20261116";
// Storefront merchandising uses the same catalog and prices as checkout.
const $ = (selector) => document.querySelector(selector);
const keys = { favorites: "wowo.favorites", recent: "wowo.recent" };
function readIds(key) {
  try {
    const value = JSON.parse(localStorage.getItem(keys[key]) || "[]");
    return Array.isArray(value)
      ? [...new Set(value.filter(Number.isSafeInteger))].slice(0, 50)
      : [];
  } catch {
    return [];
  }
}
function writeIds(key, ids) {
  try {
    localStorage.setItem(keys[key], JSON.stringify(ids));
    return true;
  } catch {
    return false;
  }
}
let favoriteIds = readIds("favorites");
let recentIds = readIds("recent");
let notify = () => {};
function remember(id) {
  recentIds = [id, ...recentIds.filter((value) => value !== id)].slice(0, 12);
  writeIds("recent", recentIds);
  document.dispatchEvent(new Event("wowo:history"));
}
function syncFavorites() {
  document.querySelectorAll("[data-favorite]").forEach((button) => {
    const selected = favoriteIds.includes(Number(button.dataset.favorite));
    button.setAttribute("aria-pressed", String(selected));
    button.setAttribute("aria-label", selected ? "取消收藏" : "收藏商品");
    button.textContent = selected ? "♥" : "♡";
  });
}

export function showProductDetails(product, helpers) {
  const { productImage, esc, money, addToCart, openCheckout, openChat } =
    helpers;
  remember(product.id);
  let selected =
    product.skus.find(
      (sku) => sku.id === product.preferredSkuId && sku.stock > 0,
    ) ||
    product.skus.find((sku) => sku.stock > 0) ||
    product.skus[0];
  if (!selected) {
    notify("该商品暂时没有可售规格");
    return;
  }
  let quantity = 1;
  const attributeLabels = {
    type: "商品类型",
    material: "材质",
    useCase: "主要用途",
    screen: "屏幕",
    weight: "重量",
    processor: "处理器",
    connection: "连接方式",
    layout: "配列",
    noiseCancellation: "降噪方式",
    batteryLife: "续航",
    waterResistance: "防水等级",
    positioning: "定位",
    power: "供电",
    brewing: "萃取方式",
    lighting: "光源",
    control: "控制",
    capacity: "容量",
    features: "特点",
    outsole: "鞋底",
    fit: "鞋楦",
    battery: "电池",
    ports: "接口",
    diameter: "尺寸",
    bedSize: "适用床型",
    roast: "烘焙",
    flavor: "风味",
    shelfLife: "保质期",
    packaging: "包装",
    skinType: "适用肤质",
    color: "标准颜色",
    size: "尺码",
    memory: "内存",
    storage: "存储",
    switch: "轴体",
    resolution: "分辨率",
    model: "品牌款号",
  };
  const paramsHtml = (attrs) =>
    Object.entries(attrs || {})
      .filter(
        ([key]) =>
          ![
            "sourceUrl",
            "sourceCheckedAt",
            "sourcePublishedAt",
            "priceBasis",
            "stockBasis",
          ].includes(key),
      )
      .map(
        ([k, v]) =>
          `<div><dt>${esc(attributeLabels[k] || k)}</dt><dd>${esc(v)}</dd></div>`,
      )
      .join("");
  const content = $("#productDialogContent");
  content.innerHTML = `<div class="detail-breadcrumb">商品详情 / ${esc(product.category)} / ${esc(product.brand)}</div><div class="detail-layout"><div class="detail-gallery"><div class="detail-image">${productImage(product.imageKey)}</div><div class="detail-gallery-note"><span>品牌资料图 · 配色以所选规格为准</span><button class="favorite-toggle" data-favorite="${product.id}">♡</button></div><div class="detail-promise"><span>✓ 多种规格</span><span>✓ 结算核价</span><span>✓ 助手协助</span></div></div><div class="detail-info"><span class="detail-brand">${esc(product.brand)} · 精选好物</span><h2>${esc(product.name)}</h2><p class="detail-summary">${esc(product.summary)}</p><div class="detail-price-box"><span>${priceBasis(product.id)}</span><strong id="detailPrice"></strong><del id="detailOriginal"></del><small id="detailSaving"></small></div><div class="detail-offer"><b>优惠</b><span>展示价格为人工核对的品牌参考价。优惠券、运费与库存为本项目体验规则。</span></div><div class="detail-specs"><b>选择规格</b><div class="spec-buttons">${product.skus.map((sku) => `<button data-select-sku="${sku.id}" ${sku.stock < 1 ? "disabled" : ""}>${esc(sku.spec)}${sku.stock < 1 ? " · 暂时缺货" : ""}</button>`).join("")}</div></div><div class="detail-quantity"><b>购买数量</b><div><button id="detailMinus" aria-label="减少购买数量">−</button><input id="detailQuantity" type="number" min="1" value="1" aria-label="购买数量"><button id="detailPlus" aria-label="增加购买数量">＋</button></div><small id="detailStock"></small></div><div class="detail-actions"><button id="detailBuy">立即购买</button><button id="detailCart">加入购物车</button></div><button class="detail-ask" id="detailAsk">✦ 这件适合我吗？问问沃沃</button></div></div>`;
  content.insertAdjacentHTML(
    "beforeend",
    `<section class="detail-parameters"><h3>商品参数</h3><p>以下为商品与当前规格信息；实际成交价格和库存会在结算时再次核对。</p><dl>${paramsHtml(product.attributes)}</dl><h4>当前规格参数</h4><dl id="selectedSkuParameters"></dl></section>`,
  );
  const info = catalogInfo(product.id);
  if (info) {
    const link = document.createElement("a");
    link.href = info.sourceUrl;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = "查看品牌来源 ↗";
    const source = document.createElement("section");
    source.className = "product-source";
    const text = document.createElement("p");
    text.textContent = `${info.priceBasis}${info.publishedAt ? "（发布于 " + info.publishedAt + "）" : ""} · 核对日期 ${info.checkedAt}。价格不会自动跟随官网更新；本项目不代表品牌官方销售渠道。`;
    source.append(link, text);
    content.querySelector(".detail-price-box").after(source);
  }
  function update() {
    const max = Math.min(selected.stock, 20);
    quantity = Math.max(
      1,
      Math.min(
        Number.isFinite(quantity) ? Math.floor(quantity) : 1,
        Math.max(1, max),
      ),
    );
    $("#selectedSkuParameters").innerHTML = paramsHtml(selected.attributes);
    $("#detailPrice").textContent = `¥${money(selected.price)}`;
    $("#detailOriginal").textContent =
      selected.originalPrice > selected.price
        ? `¥${money(selected.originalPrice)}`
        : "";
    $("#detailSaving").textContent =
      selected.originalPrice > selected.price
        ? `品牌参考价差 ¥${money(selected.originalPrice - selected.price)}`
        : "";
    $("#detailStock").textContent =
      selected.stock > 0 ? `演示库存 · 剩余 ${selected.stock} 件` : "暂时缺货";
    $("#detailQuantity").value = quantity;
    $("#detailQuantity").max = Math.max(1, max);
    $("#detailQuantity").disabled = selected.stock < 1;
    $("#detailMinus").disabled = quantity <= 1;
    $("#detailPlus").disabled = quantity >= max;
    $("#detailBuy").disabled = $("#detailCart").disabled = selected.stock < 1;
    content.querySelectorAll("[data-select-sku]").forEach((button) => {
      const active = Number(button.dataset.selectSku) === selected.id;
      button.classList.toggle("selected", active);
      button.setAttribute("aria-pressed", String(active));
    });
  }
  content.onclick = (event) => {
    const button = event.target.closest("[data-select-sku]");
    if (button) {
      selected = product.skus.find(
        (sku) => sku.id === Number(button.dataset.selectSku),
      );
      update();
    }
  };
  $("#detailMinus").onclick = () => {
    quantity--;
    update();
  };
  $("#detailPlus").onclick = () => {
    quantity++;
    update();
  };
  $("#detailQuantity").onchange = (event) => {
    quantity = Number(event.target.value);
    update();
  };
  $("#detailCart").onclick = async () => {
    const button = $("#detailCart");
    button.disabled = true;
    try {
      await addToCart(selected.id, quantity);
    } finally {
      button.disabled = selected.stock < 1;
    }
  };
  $("#detailBuy").onclick = () => {
    openCheckout("DIRECT", selected.id, quantity);
  };
  $("#detailAsk").onclick = () => {
    openChat(`请介绍 ${product.name}，${selected.spec}，帮我分析是否适合我。`);
  };
  update();
  syncFavorites();
  openView("productDialog", product.id);
}

export async function initStorefront(helpers) {
  const { api, showProduct, browse, toast, productImage, esc, money, compact } =
    helpers;
  notify = toast;
  let catalog = [],
    library = "favorites";
  const suggestions = $("#searchSuggestions");
  const mini = (product, extra = "") =>
    `<button class="mini-product" data-view-product="${product.id}">${extra}${productImage(product.imageKey)}<b>${esc(product.name)}</b><strong>¥${money(product.price)}</strong></button>`;
  function selectedProducts(ids) {
    return ids
      .map((id) => catalog.find((product) => product.id === id))
      .filter(Boolean);
  }
  function renderLibrary() {
    const products = selectedProducts(
      library === "favorites" ? favoriteIds : recentIds,
    );
    $("#libraryTitle").textContent =
      library === "favorites" ? `我的收藏 · ${products.length}` : "浏览足迹";
    $("#libraryProducts").innerHTML = products.length
      ? products
          .map(
            (product) =>
              `<div class="library-item">${mini(product)}${library === "favorites" ? `<button data-favorite="${product.id}" class="favorite-toggle">♥</button>` : ""}</div>`,
          )
          .join("")
      : `<div class="empty-panel"><b>${library === "favorites" ? "还没有收藏商品" : "还没有浏览记录"}</b>${library === "favorites" ? "点击商品上的爱心，把喜欢的好物留下。" : "查看商品详情后，会在这里留下足迹。"}</div>`;
    syncFavorites();
  }
  function renderRecent() {
    const products = selectedProducts(recentIds).slice(0, 6);
    $("#recentSection").hidden = !products.length;
    $("#recentProducts").innerHTML = products
      .map((product) => mini(product))
      .join("");
  }
  let suggestTimer,
    suggestRequest = 0;
  async function renderSuggestions() {
    const request = ++suggestRequest,
      value = $("#searchInput").value.trim();
    try {
      const data = await api(
        "/api/store/products?" +
          new URLSearchParams({ query: value, limit: "5", inStock: "true" }),
      );
      if (
        request !== suggestRequest ||
        document.activeElement !== $("#searchInput")
      )
        return;
      const products = data.items || [];
      suggestions.innerHTML =
        `<small>${value ? "你可能在找" : "热门好物"}</small>` +
        (products.length
          ? products
              .map(
                (product) =>
                  `<button data-search-term="${esc(product.name).replaceAll('"', "&quot;")}"><span>${esc(product.name)}</span><b>¥${money(product.price)}</b></button>`,
              )
              .join("")
          : "<p>没有匹配商品，试试关键词或使用下方预算、规格筛选</p>");
      suggestions.hidden = false;
    } catch {
      if (request === suggestRequest) suggestions.hidden = true;
    }
  }
  $("#searchInput").addEventListener("input", () => {
    suggestRequest++;
    clearTimeout(suggestTimer);
    suggestions.hidden = true;
    suggestTimer = setTimeout(renderSuggestions, 250);
  });
  $("#searchInput").addEventListener("focus", renderSuggestions);
  $("#searchInput").addEventListener("keydown", (event) => {
    if (event.key === "Escape" || event.key === "Enter") {
      suggestRequest++;
      clearTimeout(suggestTimer);
      suggestions.hidden = true;
    }
    if (event.key === "ArrowDown" && !suggestions.hidden) {
      event.preventDefault();
      suggestions.querySelector("button")?.focus();
    }
  });
  suggestions.addEventListener("keydown", (event) => {
    const buttons = [...suggestions.querySelectorAll("button")],
      index = buttons.indexOf(document.activeElement);
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      buttons[
        (index + (event.key === "ArrowDown" ? 1 : -1) + buttons.length) %
          buttons.length
      ]?.focus();
    }
    if (event.key === "Escape") {
      $("#searchInput").focus();
      suggestions.hidden = true;
    }
  });
  document.addEventListener("focusin", (event) => {
    if (!event.target.closest(".search-area")) suggestions.hidden = true;
  });
  document.addEventListener("click", (event) => {
    if (!event.target.closest(".search-area")) suggestions.hidden = true;
    const favorite = event.target.closest("[data-favorite]");
    if (favorite) {
      const id = Number(favorite.dataset.favorite),
        exists = favoriteIds.includes(id);
      favoriteIds = exists
        ? favoriteIds.filter((value) => value !== id)
        : [id, ...favoriteIds].slice(0, 50);
      const saved = writeIds("favorites", favoriteIds);
      syncFavorites();
      if ($("#libraryDialog").open) renderLibrary();
      toast(
        saved
          ? exists
            ? "已取消收藏"
            : "已加入收藏"
          : "本次已记录，但浏览器不允许持久保存",
      );
      return;
    }
    const view = event.target.closest("[data-view-product]");
    if (view) {
      if ($("#libraryDialog").open) $("#libraryDialog").close();
      showProduct(Number(view.dataset.viewProduct));
      return;
    }
    const shortcut = event.target.closest("[data-library]");
    if (shortcut) {
      library = shortcut.dataset.library;
      renderLibrary();
      if (!$("#libraryDialog").open) $("#libraryDialog").showModal();
      return;
    }
    const search = event.target.closest("[data-keyword], [data-search-term]");
    if (search) {
      suggestions.hidden = true;
      browse("", search.dataset.keyword || search.dataset.searchTerm);
    }
  });
  $("#clearRecent").onclick = () => {
    recentIds = [];
    writeIds("recent", []);
    renderRecent();
    toast("已清空浏览记录");
  };
  $("#backToTop").onclick = () =>
    window.scrollTo({
      top: 0,
      behavior: matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth",
    });
  document.addEventListener("wowo:history", renderRecent);
  document.addEventListener("wowo:catalog-rendered", syncFavorites);
  document.addEventListener("wowo:coupons", (event) => {
    $("#homeCoupons").innerHTML = event.detail
      .slice(0, 3)
      .map(
        (coupon) =>
          `<button data-home-coupon><strong><small>¥</small>${money(coupon.discountAmount)}</strong><span>${coupon.thresholdAmount > 0 ? `满 ${money(coupon.thresholdAmount)} 元可用` : "无门槛优惠"}<b>${coupon.userStatus === "USED" ? "已使用" : coupon.userCouponId ? "已领取 · 去使用" : "去领取 →"}</b></span></button>`,
      )
      .join("");
    $("#homeCoupons")
      .querySelectorAll("[data-home-coupon]")
      .forEach((button) => (button.onclick = () => helpers.loadCoupons(true)));
  });
  try {
    let page = 1,
      more = true;
    while (more && page <= 2) {
      const result = await api(`/api/store/products?limit=30&page=${page++}`);
      catalog.push(...(result.items || []));
      more = result.hasMore;
    }

    helpers.onCatalogReady?.(catalog);
    const picks = [205, 201, 209, 217, 225];
    const deals = picks
      .map((id) => catalog.find((p) => p.id === id))
      .filter(Boolean);
    $("#valueProducts").innerHTML = deals
      .map(
        (product) =>
          `<button class="deal-product" data-view-product="${product.id}"><span class="saving-label">${priceBasis(product.id)}</span>${productImage(product.imageKey)}<b>${esc(product.name)}</b><div><strong>¥${money(product.price)}</strong>${product.originalPrice > product.price ? `<del>¥${money(product.originalPrice)}</del>` : ""}</div></button>`,
      )
      .join("");
    $("#rankingProducts").innerHTML = [...catalog]
      .slice(0, 3)
      .map(
        (product, index) =>
          `<button class="rank-product" data-view-product="${product.id}"><span class="rank-number">${String(index + 1).padStart(2, "0")}</span>${productImage(product.imageKey)}<div><b>${esc(product.name)}</b><small>${esc(product.brand)} · ${priceBasis(product.id)}</small><strong>¥${money(product.price)}</strong></div></button>`,
      )
      .join("");
    $("#digitalProducts").innerHTML = catalog
      .filter((product) => product.category === "数码影音")
      .slice(0, 2)
      .map((product) => mini(product))
      .join("");
    $("#livingProducts").innerHTML = catalog
      .filter((product) => product.category === "居家生活")
      .slice(0, 2)
      .map((product) => mini(product))
      .join("");
    renderRecent();
    if ($("#libraryDialog").open) renderLibrary();
    syncFavorites();
  } catch {
    for (const id of [
      "valueProducts",
      "rankingProducts",
      "digitalProducts",
      "livingProducts",
    ])
      document.getElementById(id).innerHTML =
        '<p class="section-loading">商品暂时无法加载，请刷新重试</p>';
  }
}
