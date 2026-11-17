// 专用 demo 进程验收：真实 HTTP/SSE、政策出处、品牌隔离与不执行交易。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const fs = require("node:fs/promises"),
  assert = require("node:assert/strict");
const base = process.env.STOREFRONT_TEST_URL || "http://localhost:18080";
const out = ".runtime/brand-policies";
(async () => {
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
    }),
    page = await context.newPage(),
    errors = [],
    checks = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  const api = async (path) => {
    const r = await context.request.get(base + path);
    assert(r.ok());
    return r.json();
  };
  const say = async (text) => {
    await page.goto(base);
    await page.evaluate(() => localStorage.removeItem("wowo.conversationId"));
    await page.reload();
    await page.locator("#chatFab").click();
    await page.locator("#chatInput").fill(text);
    await page.locator("#chatForm [type=submit]").click();
    await page.waitForFunction(
      () =>
        document.querySelectorAll(".message.assistant:not(.loading)").length >=
        2,
    );
    return page.locator(".message.assistant .bubble").last();
  };
  try {
    assert.equal((await api("/api/agent/status")).mode, "demo");
    const before = (await api("/api/store/orders?userId=10001")).items.map(
      (i) => i.id,
    );
    const cases = [
      ["iPhone退货政策", "FOUND", ["APPLE"]],
      ["米家冰箱保修", "FOUND", ["XIAOMI"]],
      ["Nike退货规则", "FOUND", ["NIKE"]],
      ["宜家食品能退吗", "FOUND", ["IKEA"]],
      ["MUJI退款多久到账", "FOUND", ["MUJI"]],
      ["华为保修多久", "CLARIFICATION_REQUIRED", []],
      ["苹果售后", "CLARIFICATION_REQUIRED", []],
      ["Nike保修多久", "NO_MATCH", []],
      ["我在你们商城买的iPhone能退吗", "FOUND", ["PLATFORM"]],
      ["苹果和耐克的退货政策对比", "FOUND", ["APPLE", "NIKE"]],
    ];
    for (const [query, status, brands] of cases) {
      const r = await api(
        "/api/store/knowledge/search?" + new URLSearchParams({ query }),
      );
      assert.equal(r.status, status, query);
      assert.deepEqual([...new Set(r.hits.map((i) => i.brand))], brands, query);
      for (const h of r.hits) {
        assert.equal(h.sourceCheckedAt, "2026-11-17");
        if (h.brand !== "PLATFORM") assert(h.sourceUrl.startsWith("https://"));
      }
    }
    checks.push("10组真实API核对品牌、类别、补问与无资料状态");
    let bubble = await say("苹果官网买的iPhone退货有哪些条件？");
    assert((await bubble.innerText()).includes("14"));
    assert(
      (await bubble.locator("a").first().getAttribute("href")).startsWith(
        "https://www.apple.com.cn/shop/help/returns_refund",
      ),
    );
    assert.equal(await page.locator(".confirm-card").count(), 0);
    await page.screenshot({ path: out + "/apple-policy-desktop.png" });
    checks.push("Apple政策回复含可点击官方来源，不生成确认卡");
    bubble = await say("比较一下苹果和耐克的退货政策");
    const content = await bubble.innerText();
    assert(
      content.includes("14") &&
        content.includes("7") &&
        content.includes("Nike"),
    );
    assert.equal(await bubble.locator("a").count(), 2);
    await page.screenshot({ path: out + "/brand-comparison-desktop.png" });
    checks.push("品牌对比各自保留期限和渠道，两个来源");
    bubble = await say("Nike保修多久");
    assert((await bubble.innerText()).includes("未收录"));
    assert.equal(await bubble.locator("a").count(), 0);
    checks.push("没有Nike保修资料时不套用Apple或平台保修");
    bubble = await say("我在你们商城买的iPhone能退吗");
    assert((await bubble.innerText()).includes("REVIEWING"));
    assert(!(await bubble.innerText()).includes("14 个自然日"));
    assert.equal(await page.locator(".confirm-card").count(), 0);
    checks.push("本站购买使用平台规则，咨询不提交售后");
    await page.setViewportSize({ width: 390, height: 844 });
    bubble = await say("宜家的食品可以退吗");
    assert((await bubble.innerText()).includes("食品"));
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    await page.screenshot({ path: out + "/ikea-policy-mobile.png" });
    checks.push("手机政策回答、食品例外与来源可见");
    await page.route("**/api/agent/chat/stream", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body:
          "event: done\ndata: " +
          JSON.stringify({
            reply: {
              content:
                "[正常来源](https://www.apple.com.cn/shop/help/returns_refund) [危险](javascript:alert(1)) <img src=x onerror=window.__policyXss=1> [凭据链接](https://user:pass@example.com/test)",
              debug: { mode: "demo" },
            },
          }) +
          "\n\n",
      }),
    );
    bubble = await say("测试引用格式");
    assert.equal(await bubble.locator("a").count(), 1);
    assert.equal(await bubble.locator("img").count(), 0);
    assert.equal(await page.evaluate(() => window.__policyXss), undefined);
    await page.unroute("**/api/agent/chat/stream");
    checks.push("安全引用允许HTTPS并拒绝脚本、凭据链接与HTML注入");
    const after = (await api("/api/store/orders?userId=10001")).items.map(
      (i) => i.id,
    );
    assert.deepEqual(after, before);
    assert.deepEqual(errors, []);
    checks.push("政策咨询不改订单，浏览器无脚本或资源错误");
    const report = {
      at: new Date().toISOString(),
      checks,
      errors,
      scope:
        "专用 H2/demo，真实HTTP与SSE；末项安全渲染使用注入SSE夹具，无外部模型",
    };
    await fs.writeFile(
      out + "/browser-report.json",
      JSON.stringify(report, null, 2),
    );
    console.log(JSON.stringify(report, null, 2));
  } catch (e) {
    await page.screenshot({ path: out + "/failure.png" });
    throw e;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
