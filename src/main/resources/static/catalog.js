// 图片与来源索引只负责展示；SKU、库存、试算和下单金额始终读取后端数据库。
let products = [];
try {
  const response = await fetch(
    "/catalog/products.json?v=real-catalog-20261116",
  );
  if (response.ok) products = (await response.json()).products || [];
} catch {
  /* 元数据异常时保留真实接口查询，图片明确展示占位。 */
}
const byId = new Map(products.map((product) => [product.id, product]));
const byImage = new Map(products.map((product) => [product.imageKey, product]));
export function catalogInfo(id) {
  return byId.get(Number(id));
}
export function productPhoto(key) {
  return (
    byImage.get(key)?.imagePath ||
    (String(key).startsWith("real-")
      ? "/images/product-placeholder.svg"
      : `/images/${String(key).replace(/[^a-zA-Z0-9_-]/g, "")}.svg`)
  );
}
export function priceBasis(id) {
  return byId.get(Number(id))?.priceBasis || "参考价格";
}
