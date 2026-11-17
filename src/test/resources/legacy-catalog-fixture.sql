-- 明确恢复既有场景依赖的 V16 目录可见性，不覆盖价格、库存、订单或 Flyway 状态。
UPDATE product SET enabled=TRUE WHERE id BETWEEN 101 AND 132;
UPDATE product_sku SET enabled=TRUE WHERE product_id BETWEEN 101 AND 132;
UPDATE product SET enabled=FALSE WHERE id BETWEEN 201 AND 232;
UPDATE product_sku SET enabled=FALSE WHERE product_id BETWEEN 201 AND 232;
