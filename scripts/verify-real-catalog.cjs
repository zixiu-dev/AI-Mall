// 核对真实目录、全部本地图片与来源，桌面/手机截图只对专用 demo 进程执行。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const fs = require("node:fs/promises"),
  assert = require("node:assert/strict");
const base = process.env.STOREFRONT_TEST_URL || "http://localhost:18080";
const out = ".runtime/real-catalog";
(async () => {
  await fs.mkdir(out, { recursive: true });
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
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  try {
    const catalog = JSON.parse(
      await fs.readFile(
        "src/main/resources/catalog/real-products.json",
        "utf8",
      ),
    );
    assert.equal(catalog.products.length, 32);
    const details = [];
    for (const expected of catalog.products) {
      const response = await context.request.get(
        base + "/api/store/products/" + expected.id,
      );
      assert(response.ok());
      const detail = await response.json();
      assert.equal(detail.name, expected.name);
      assert.equal(detail.attributes.sourceUrl, expected.sourceUrl);
      assert.equal(detail.skus.length, expected.skus.length);
      for (const sku of detail.skus) {
        assert.equal(
          Number(sku.price),
          expected.skus.find((s) => s.id === sku.id).price,
        );
      }
      const image = await context.request.get(base + expected.imagePath);
      assert(image.ok());
      assert(image.headers()["content-type"].startsWith("image/"));
      assert((await image.body()).length > 1000);
      details.push({
        id: expected.id,
        name: detail.name,
        skus: detail.skus.length,
        image: expected.imagePath,
      });
    }
    const retired = await context.request.get(base + "/api/store/products/101");
    assert(!retired.ok());
    await page.goto(base);
    await page.locator(".deal-product").first().waitFor();
    await page.waitForFunction(() =>
      [...document.querySelectorAll(".deal-product img")].every(
        (i) => i.complete && i.naturalWidth > 0,
      ),
    );
    await page.screenshot({ path: out + "/home-desktop.png", fullPage: false });
    assert(
      !(await page.locator("#valueProducts").innerText()).includes("直降"),
    );
    assert(
      !(await page.locator("#productGrid").innerText()).includes("人买过"),
    );
    assert(!(await page.locator("#productGrid").innerText()).includes("★"));
    for (const id of [201, 205, 209, 210, 211, 212, 217, 223, 228, 231]) {
      await page.goto(base + "/#/product/" + id);
      await page.locator("#detailBuy").waitFor();
      assert(await page.locator(".product-source a").count());
      assert(
        (await page.locator(".product-source").innerText()).includes(
          "2026-11-16",
        ),
      );
      assert(
        (await page.locator("#detailStock").innerText()).includes("演示库存"),
      );
      await page.waitForFunction(
        () =>
          document.querySelector(".detail-image img")?.complete &&
          document.querySelector(".detail-image img")?.naturalWidth > 0,
      );
      if ([201, 205, 209].includes(id))
        await page.screenshot({
          path: out + "/product-" + id + ".png",
          fullPage: true,
        });
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base + "/#/product/205");
    await page.locator("#detailBuy").waitFor();
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    await page.screenshot({ path: out + "/iphone-mobile.png", fullPage: true });
    assert.deepEqual(errors, []);
    const report = {
      at: new Date().toISOString(),
      products: details.length,
      skus: details.reduce((n, p) => n + p.skus, 0),
      photos: 32,
      errors,
      checks: [
        "全部商品详情与核实清单一致",
        "全部图片由本地HTTP正常返回",
        "旧目录已下架",
        "无虚构评分销量及直降宣传",
        "10款跨品类详情具有来源日期",
        "演示库存明确标记",
        "手机iPhone详情无溢出",
      ],
      details,
    };
    await fs.writeFile(
      out + "/browser-report.json",
      JSON.stringify(report, null, 2),
    );
    console.log(JSON.stringify({ ...report, details: undefined }, null, 2));
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
