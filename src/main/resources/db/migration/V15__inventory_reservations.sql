-- 库存池初始化标记不能随着 Redis 丢失而消失，防止用旧数据库余额盲目重建。
ALTER TABLE product_sku ADD COLUMN redis_inventory_initialized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_submit_trace ADD COLUMN submission_hash CHAR(64);
CREATE TABLE order_inventory_return (
 order_id VARCHAR(40) NOT NULL REFERENCES customer_order(id),
 sku_id BIGINT NOT NULL REFERENCES product_sku(id),
 reservation_id VARCHAR(90) NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(order_id,sku_id)
);
CREATE INDEX idx_inventory_return_pending ON order_inventory_return(completed,created_at);
