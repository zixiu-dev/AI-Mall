ALTER TABLE product_sku ADD COLUMN redis_inventory_initialized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_submit_trace ADD COLUMN submission_hash CHAR(64);
CREATE TABLE order_inventory_return (
 order_id VARCHAR(40) NOT NULL,
 sku_id BIGINT NOT NULL,
 reservation_id VARCHAR(90) NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(order_id,sku_id),
 CONSTRAINT fk_inventory_return_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_inventory_return_sku FOREIGN KEY(sku_id) REFERENCES product_sku(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_inventory_return_pending ON order_inventory_return(completed,created_at);
