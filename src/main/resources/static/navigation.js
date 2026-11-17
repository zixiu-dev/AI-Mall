// 只管理页面与浏览器历史；所有商品、金额和订单状态仍由业务接口决定。
const views = {
  home: "homeView",
  catalog: "homeView",
  product: "productDialog",
  cart: "cartDialog",
  coupons: "couponDialog",
  checkout: "checkoutDialog",
  orders: "ordersDialog",
};
const names = {
  home: "首页精选",
  catalog: "全部商品",
  product: "商品详情",
  cart: "购物车",
  coupons: "优惠券中心",
  checkout: "确认订单",
  orders: "我的订单",
};
let enter = {};
export function currentPage() {
  return location.hash.slice(2).split(/[/?]/)[0] || "home";
}
export function isView(id) {
  return !document.getElementById(id).hidden;
}
function paint(page) {
  document
    .querySelectorAll(".route-view")
    .forEach((view) => (view.hidden = view.id !== views[page]));
  document.querySelector("#homeOverview").hidden = page !== "home";
  document.querySelector("#accountLayout").hidden = ![
    "cart",
    "coupons",
    "checkout",
    "orders",
  ].includes(page);
  document.body.dataset.page = page;
  document.title = `${names[page]} · 沃沃智购`;
  document
    .querySelectorAll("[data-nav],[data-account],[data-mobile]")
    .forEach((link) => {
      const active =
        (link.dataset.nav || link.dataset.account || link.dataset.mobile) ===
        page;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
}
export function openView(id, value = "") {
  const page =
    Object.keys(views).find((key) => views[key] === id && key !== "home") ||
    "home";
  navigate(page, value);
}
export function navigate(page, value = "") {
  if (!views[page]) page = "home";
  const hash = `#/${page === "home" ? "" : page}${value ? "/" + encodeURIComponent(value) : ""}`;
  if (location.hash !== hash) history.pushState(null, "", hash);
  paint(page);
  window.scrollTo({ top: 0, behavior: "instant" });
}
export function closeView(id) {
  navigate(id === "productDialog" ? "catalog" : "home");
}
export function initNavigation(callbacks) {
  enter = callbacks;
  async function restore() {
    const page = currentPage();
    let value = "";
    try {
      value = decodeURIComponent(location.hash.slice(2).split("/")[1] || "");
    } catch {
      navigate("home");
      return;
    }
    if (!views[page]) {
      navigate("home");
      return;
    }
    paint(page);
    document.querySelector("#mainContent").focus({ preventScroll: true });
    await enter[page]?.(value);
    window.scrollTo({ top: 0, behavior: "instant" });
  }
  window.addEventListener("hashchange", restore);
  restore();
}
// 原生 dialog 仅用于明确确认；关闭或 Escape 都不会执行写操作。
export function confirmOperation(title, description) {
  const dialog = document.querySelector("#confirmDialog");
  if (dialog.open) return Promise.resolve(false);
  document.querySelector("#confirmTitle").textContent = title;
  document.querySelector("#confirmDescription").textContent = description;
  dialog.returnValue = "cancel";
  dialog.showModal();
  return new Promise((resolve) =>
    dialog.addEventListener(
      "close",
      () => resolve(dialog.returnValue === "confirm"),
      { once: true },
    ),
  );
}
