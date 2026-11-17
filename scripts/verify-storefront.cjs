/* 实际浏览器验收：仅对专用 demo 进程执行，会创建/取消/模拟支付订单。
 * PLAYWRIGHT_MODULE=/path/to/playwright node scripts/verify-storefront.cjs
 * 不使用共享开发环境；所有金额和结果核对真实 REST 返回。
 */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const base = process.env.STOREFRONT_TEST_URL || "http://localhost:18080";
const output =
  process.env.STOREFRONT_TEST_OUTPUT || ".runtime/storefront-tests";
(async () => {
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({
    executablePath:
      process.env.CHROME_PATH ||
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    headless: true,
    args: ["--no-sandbox"],
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    timezoneId: "Asia/Shanghai",
    reducedMotion: "reduce",
  });
  const page = await context.newPage(),
    errors = [],
    expectedFaults = [],
    checks = [];
  let expected404 = false,
    expectedNetworkFailure = false;
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => {
    if (m.type() === "error") {
      if (
        (expected404 && m.text().includes("404")) ||
        (expectedNetworkFailure && m.text().includes("net::ERR_FAILED"))
      )
        expectedFaults.push(m.text());
      else errors.push(m.text());
    }
  });
  const check = (name) => checks.push(name);
  const get = async (url) => {
    const r = await context.request.get(base + url);
    assert(r.ok(), `${url}: ${r.status()}`);
    return r.json();
  };
  const screenshot = async (name, fullPage = true) => {
    await page.evaluate(async () => {
      const imgs = [...document.querySelectorAll("img")];
      imgs.forEach((i) => (i.loading = "eager"));
      await Promise.all(imgs.map((i) => i.decode().catch(() => {})));
    });
    await page.screenshot({ path: path.join(output, name + ".png"), fullPage });
  };
  try {
    const environment = await get("/api/store/order-options");
    assert.equal(
      environment.demoPaymentEnabled,
      true,
      "仅允许对开启模拟支付的专用 demo 进程验收",
    );
    assert.equal(
      (await get("/api/agent/status")).mode,
      "demo",
      "使用专用规则助手隔离环境",
    );
    await page.goto(base);
    await page.locator(".product-card").first().waitFor();
    await page.locator(".deal-product").first().waitFor();
    assert.equal(await page.locator(".product-card").count(), 12);
    await screenshot("home-desktop");
    await screenshot("home-viewport", false);
    check("首页真实商品陈列");
    await page.locator(".mall-nav [data-nav=catalog]").click();
    assert.equal(new URL(page.url()).hash, "#/catalog");
    await page.locator("#searchInput").fill("电脑");
    await page.locator("#searchButton").click();
    await page.locator(".product-card").first().waitFor();
    assert((await page.locator(".product-card").count()) > 0);
    await page.locator(".filter-panel summary").click();
    await page.locator("[name=minPrice]").fill("3000");
    await page.locator("[name=maxPrice]").fill("6000");
    await page.locator("#catalogFilters [type=submit]").click();
    await page.waitForFunction(() =>
      document.querySelector("#activeFilters").textContent.includes("最低价"),
    );
    check("关键词、预算及SKU匹配筛选");
    await page.goto(base + "/#/product/201");
    await page.locator("#detailBuy").waitFor();
    assert((await page.locator("[data-select-sku]").count()) >= 2);
    await page.locator("[data-select-sku]").last().click();
    await page.locator("#detailPlus").click();
    assert.equal(await page.locator("#detailQuantity").inputValue(), "2");
    await page.locator("#detailCart").click();
    await page.waitForFunction(
      () => document.querySelector("#toast").textContent === "已加入购物车",
    );
    await page.locator("#cartButton").click();
    await page.locator(".cart-row").first().waitFor();
    const before = await get("/api/store/cart?userId=10001");
    assert(before.items.some((i) => i.productId === 201 && i.quantity >= 2));
    await page.locator("#selectAllCart").uncheck();
    await page.waitForFunction(
      () =>
        document.querySelector("#cartCheckoutButton").disabled &&
        document.querySelector("#selectAllCart").disabled === false,
    );
    await page.locator("#selectAllCart").check();
    await page.waitForFunction(
      () => !document.querySelector("#cartCheckoutButton").disabled,
    );
    const selected = await get("/api/store/cart?userId=10001");
    assert(selected.items.every((i) => i.selected));
    assert(
      (await page.locator("#cartTotal").innerText())
        .replaceAll(",", "")
        .includes(String(Number(selected.selectedAmount))),
    );
    await screenshot("cart-desktop");
    await page.setViewportSize({ width: 390, height: 844 });
    await screenshot("cart-filled-mobile");
    await page.setViewportSize({ width: 1440, height: 1000 });
    check("加入购物车、数量、全选与后端金额一致");
    await page.locator("[data-remove-cart]").first().click();
    await page.locator("#confirmDialog").waitFor();
    await page.locator("#confirmDialog [value=cancel]").click();
    assert.equal(
      (await get("/api/store/cart?userId=10001")).items.length,
      selected.items.length,
    );
    check("移除确认取消不会删除");
    await page.locator("#couponsButton").click();
    await page.locator(".coupon-card").first().waitFor();
    const claim = page.locator("[data-claim-coupon]:not(:disabled)").first();
    if (await claim.count()) {
      await claim.click();
      await page.waitForFunction(
        () => document.querySelector("#toast").textContent === "优惠券领取成功",
      );
    }
    await page.locator("[data-coupon-tab=available]").click();
    await screenshot("coupons-desktop");
    check("优惠券领取与状态筛选");
    await page.locator("#cartButton").click();
    await page.locator("#cartCheckoutButton").click();
    await page.locator("#checkoutCoupon").waitFor();
    await page.waitForFunction(
      () => !document.querySelector("#submitOrderButton").disabled,
    );
    const options = await page
      .locator("#checkoutCoupon option")
      .evaluateAll((nodes) => nodes.map((n) => n.value).filter(Boolean));
    if (options.length) {
      await page.locator("#checkoutCoupon").selectOption(options[0]);
      await page.waitForFunction(
        () => !document.querySelector("#submitOrderButton").disabled,
      );
    }
    await screenshot("checkout-desktop");
    assert(
      !(await page.locator("#checkoutContent").innerText()).includes(
        "[object Object]",
      ),
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await screenshot("checkout-filled-mobile");
    await page.setViewportSize({ width: 1440, height: 1000 });
    const resultPromise = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/store/orders/submit") &&
        r.request().method() === "POST",
    );
    await page.locator("#submitOrderButton").click();
    const result = await (await resultPromise).json();
    assert.equal(result.status, "SUCCESS");
    assert(result.traceId && result.orderId);
    await page.waitForFunction(() => document.body.dataset.page === "orders");
    await page.locator(`[data-order-id="${result.orderId}"]`).waitFor();
    let order = (await get("/api/store/orders?userId=10001")).items.find(
      (o) => o.id === result.orderId,
    );
    assert.equal(order.status, "PENDING_PAYMENT");
    check("真实试算、稳定trace提交、建单为待付款");
    await page.locator("[data-order-tab=PENDING_PAYMENT]").click();
    await page.locator("#orderSearch").fill(result.orderId);
    assert.equal(await page.locator("#ordersList .order-card").count(), 1);
    assert(
      (await page.locator("[data-deadline]").innerText()).includes("剩余"),
    );
    await screenshot("orders-desktop");
    await page.locator("[data-order-detail]").click();
    await page.locator("#orderDetailDialog").waitFor();
    assert(
      !(await page.locator("#orderDetailContent").innerText()).includes("NaN"),
    );
    await screenshot("order-detail-desktop", false);
    await page.locator("[data-close-dialog=orderDetailDialog]").click();
    check("订单筛选、搜索、详情、支付倒计时");
    await page.locator("[data-cancel-order]").click();
    await page.locator("#confirmDialog [value=cancel]").click();
    assert.equal(
      (await get("/api/store/orders?userId=10001")).items.find(
        (o) => o.id === result.orderId,
      ).status,
      "PENDING_PAYMENT",
    );
    await page.locator("[data-cancel-order]").click();
    await page.locator("#confirmDialog [value=confirm]").click();
    await page.waitForFunction(() =>
      document.querySelector("#toast").textContent.startsWith("订单已取消"),
    );
    assert.equal(
      (await get("/api/store/orders?userId=10001")).items.find(
        (o) => o.id === result.orderId,
      ).status,
      "CANCELLED",
    );
    check("取消需确认并刷新真实状态");
    await page.goto(base + "/#/product/201");
    await page.locator("#detailBuy").click();
    await page.waitForFunction(
      () => !document.querySelector("#submitOrderButton").disabled,
    );
    const paidPromise = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/store/orders/submit") &&
        r.request().method() === "POST",
    );
    await page.locator("#submitOrderButton").click();
    const paid = await (await paidPromise).json();
    assert.equal(paid.status, "SUCCESS");
    await page.waitForFunction(() => document.body.dataset.page === "orders");
    await page.locator("#orderSearch").fill("");
    await page.locator("[data-order-tab=ALL]").click();
    await page
      .locator(`[data-order-id="${paid.orderId}"] [data-demo-pay]`)
      .click();
    await page.waitForFunction(() =>
      document.querySelector("#toast").textContent.includes("支付"),
    );
    assert.equal(
      (await get("/api/store/orders?userId=10001")).items.find(
        (o) => o.id === paid.orderId,
      ).status,
      "PAID",
    );
    check("显式模拟支付区别于建单成功");
    // 真实建单已提交，但刻意丢弃 HTTP 响应；应查询原 trace 恢复，而非再发起一次购买。
    await page.goto(base + "/#/product/201");
    await page.locator("#detailBuy").click();
    await page.waitForFunction(
      () => !document.querySelector("#submitOrderButton").disabled,
    );
    let lostResult,
      submitCalls = 0;
    expectedNetworkFailure = true;
    await page.route("**/api/store/orders/submit", async (route) => {
      submitCalls++;
      const response = await route.fetch();
      lostResult = await response.json();
      await route.abort("failed");
    });
    await page.locator("#submitOrderButton").click();
    await page.waitForFunction(() => document.body.dataset.page === "orders", {
      timeout: 15000,
    });
    await page.locator(`[data-order-id="${lostResult.orderId}"]`).waitFor();
    assert.equal(submitCalls, 1);
    assert.deepEqual(
      await page.evaluate(() =>
        JSON.parse(localStorage.getItem("wowo.pendingOrders")),
      ),
      {},
    );
    assert.equal(
      (await get("/api/store/orders?userId=10001")).items.filter(
        (o) => o.id === lostResult.orderId,
      ).length,
      1,
    );
    await page.unroute("**/api/store/orders/submit");
    expectedNetworkFailure = false;
    check("真实提交响应丢失后查询原trace恢复，无重复建单");
    await page.locator("#chatFab").click();
    await page.locator("#chatInput").fill("推荐一款降噪耳机");
    await page.locator("#chatForm [type=submit]").click();
    await page.waitForFunction(
      () =>
        document.querySelectorAll(".message.assistant:not(.loading)").length >=
        2,
      { timeout: 20000 },
    );
    assert((await page.locator("#agentMode").innerText()).includes("演示模式"));
    await screenshot("assistant-desktop", false);
    await page.locator("#closeChat").click();
    check("助手真实SSE响应及模式标识");
    await page.goto(base + "/#/product/201");
    await page.locator("#detailBuy").waitFor();
    await page.reload();
    await page.locator("#detailBuy").waitFor();
    await page.locator("#closeDialog").click();
    assert.equal(new URL(page.url()).hash, "#/catalog");
    await page.goBack();
    await page.locator("#detailBuy").waitFor();
    check("详情直达、刷新及浏览器返回");
    await page.goto(base + "/#/orders");
    await page.locator("#ordersList .order-card").first().waitFor();
    const shipping = await get("/api/store/orders?userId=10001");
    const shipped = shipping.items.find((o) => o.logistics?.length);
    if (shipped) {
      await page
        .locator(`[data-order-id="${shipped.id}"] [data-order-detail]`)
        .click();
      assert(
        (await page.locator(".order-logistics li").count()) ===
          shipped.logistics.length,
      );
      await page.locator("[data-close-dialog=orderDetailDialog]").click();
      check("历史订单物流轨迹使用后端数据");
    }
    expected404 = true;
    await page.route("**/api/store/products/999999", (r) =>
      r.fulfill({
        status: 404,
        contentType: "application/json",
        body: '{"message":"商品不存在"}',
      }),
    );
    await page.goto(base + "/#/product/999999");
    await page.locator("#productDialogContent .empty-panel b").waitFor();
    assert(
      (await page.locator("#productDialogContent").innerText()).includes(
        "商品不存在",
      ),
    );
    await page.unroute("**/api/store/products/999999");
    expected404 = false;
    check("无效商品显示失败及返回入口");
    for (const width of [390, 360, 768]) {
      await page.setViewportSize({ width, height: 844 });
      for (const route of [
        "",
        "catalog",
        "product/201",
        "cart",
        "coupons",
        "orders",
        "checkout",
      ]) {
        await page.goto(base + "/#/" + route);
        await page.waitForTimeout(450);
        if (route === "product/201") await page.locator("#detailBuy").waitFor();
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth,
          ),
          false,
          `${width} ${route} 溢出`,
        );
        if (width === 390)
          await screenshot((route || "home").replace("/", "-") + "-mobile");
      }
    }
    check("360/390/768宽度七个页面无横向溢出");
    assert.deepEqual(errors, []);
    check("浏览器无JavaScript或资源错误");
    await fs.writeFile(
      path.join(output, "report.json"),
      JSON.stringify(
        {
          at: new Date().toISOString(),
          base,
          checks,
          errors,
          expectedFaults,
          orderId: result.orderId,
          paidOrderId: paid.orderId,
          scope: "隔离 H2、SYNC、无 Redis/MQ、规则助手；模拟支付",
        },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify(
        { passed: checks.length, checks, errors, output },
        null,
        2,
      ),
    );
  } catch (e) {
    await page.screenshot({
      path: path.join(output, "failure.png"),
      fullPage: true,
    });
    console.error(
      JSON.stringify(
        { checks, errors, url: page.url(), failure: e.message },
        null,
        2,
      ),
    );
    throw e;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
