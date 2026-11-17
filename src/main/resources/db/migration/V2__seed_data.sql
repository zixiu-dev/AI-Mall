INSERT INTO app_user(id, nickname, phone) VALUES
(10001, '小林', '13800000001');

INSERT INTO user_address(id, user_id, receiver, phone, province, city, district, detail, is_default) VALUES
(20001, 10001, '林同学', '13800000001', '浙江省', '杭州市', '西湖区', '文三路 99 号 3 幢 1201', TRUE);

INSERT INTO product(id, name, category, brand, summary, image_key, rating, sales_count) VALUES
(101, 'NovaBook Pro 14', '电脑办公', 'Nova', '轻薄笔记本电脑，2.8K 高刷屏，适合编程、设计和移动办公。', 'laptop-blue', 4.9, 8620),
(102, 'KeyFlow 75 机械键盘', '电脑办公', 'KeyFlow', '75% 配列，三模连接，热插拔轴座与长续航。', 'keyboard', 4.8, 15632),
(103, 'AirBeat Pro 降噪耳机', '数码影音', 'AirBeat', '自适应主动降噪，空间音频，单次续航 38 小时。', 'headphone', 4.8, 23110),
(104, 'PixelWatch S3', '智能穿戴', 'Pixel', '全天候健康监测、双频 GPS 与 5ATM 防水。', 'watch', 4.7, 9730),
(105, 'BrewGo 随行咖啡机', '生活家电', 'BrewGo', '便携胶囊咖啡机，支持冷热萃取，USB-C 充电。', 'coffee', 4.6, 5210),
(106, 'Lumina 护眼台灯', '生活家电', 'Lumina', '全光谱无蓝光频闪，智能感光和番茄钟模式。', 'lamp', 4.9, 12450),
(107, 'TrailPack 28L 通勤包', '运动户外', 'Trail', '防泼水面料，独立电脑仓，背负透气，适合通勤短途。', 'backpack', 4.8, 18700),
(108, 'CloudStep 日常跑鞋', '运动户外', 'CloudStep', '轻量缓震鞋底，宽楦设计，适合日常慢跑与通勤。', 'shoes', 4.7, 28600);

INSERT INTO product_sku(id, product_id, sku_code, spec, price, original_price, stock) VALUES
(1001, 101, 'NBP14-16-512', '星海蓝 / 16GB / 512GB', 4999.00, 5499.00, 23),
(1002, 101, 'NBP14-32-1T', '星海蓝 / 32GB / 1TB', 6199.00, 6699.00, 8),
(1003, 102, 'KF75-MINT', '薄荷绿 / 线性轴', 399.00, 459.00, 76),
(1004, 102, 'KF75-GRAY', '深空灰 / 段落轴', 429.00, 489.00, 44),
(1005, 103, 'ABP-BLACK', '曜石黑', 899.00, 1099.00, 65),
(1006, 104, 'PWS3-46', '曜石黑 / 46mm', 1599.00, 1799.00, 31),
(1007, 105, 'BG-CREAM', '奶油白', 499.00, 599.00, 18),
(1008, 106, 'LUMINA-WHITE', '云朵白', 299.00, 359.00, 102),
(1009, 107, 'TP28-BLACK', '夜行黑', 329.00, 399.00, 87),
(1010, 108, 'CS-42-GRAY', '烟灰 / 42 码', 459.00, 529.00, 36),
(1011, 108, 'CS-43-GRAY', '烟灰 / 43 码', 459.00, 529.00, 19),
(1012, 108, 'CS-42-WHITE', '云白 / 42 码', 459.00, 529.00, 28);

INSERT INTO customer_order(id, user_id, status, goods_amount, discount_amount, freight_amount, pay_amount, address_snapshot, created_at, paid_at) VALUES
('BM202610010001', 10001, 'SHIPPED', 899.00, 80.00, 0.00, 819.00, '林同学 13800000001 浙江省杭州市西湖区文三路99号3幢1201', TIMESTAMP '2026-10-01 10:20:00', TIMESTAMP '2026-10-01 10:21:00'),
('BM202609180002', 10001, 'COMPLETED', 299.00, 20.00, 0.00, 279.00, '林同学 13800000001 浙江省杭州市西湖区文三路99号3幢1201', TIMESTAMP '2026-09-18 19:30:00', TIMESTAMP '2026-09-18 19:31:00');

INSERT INTO order_item(order_id, product_id, sku_id, product_name, sku_spec, unit_price, quantity) VALUES
('BM202610010001', 103, 1005, 'AirBeat Pro 降噪耳机', '曜石黑', 899.00, 1),
('BM202609180002', 106, 1008, 'Lumina 护眼台灯', '云朵白', 299.00, 1);

INSERT INTO logistics_event(order_id, event_time, status, description) VALUES
('BM202610010001', TIMESTAMP '2026-10-01 16:00:00', 'PICKED_UP', '商家已发货，包裹已由杭州余杭集散中心揽收'),
('BM202610010001', TIMESTAMP '2026-10-02 07:40:00', 'IN_TRANSIT', '包裹已到达杭州西湖营业部，正在分拣'),
('BM202610010001', TIMESTAMP '2026-10-02 09:15:00', 'OUT_FOR_DELIVERY', '派送员王师傅正在配送，预计 18:00 前送达'),
('BM202609180002', TIMESTAMP '2026-09-20 14:10:00', 'DELIVERED', '包裹已由本人签收');

INSERT INTO knowledge_document(category, title, keywords, content) VALUES
('售后', '七天无理由退货规则', '退货 退款 七天无理由 拆封', '自商品签收次日起 7 天内，商品保持完好且不影响二次销售的，可申请七天无理由退货。定制商品、激活后的数字商品以及已拆封的贴身用品除外。质量问题退货由平台承担运费。'),
('售后', '退款到账时效', '退款 到账 原路退回 时间', '售后审核通过后，退款原路退回。余额通常即时到账；银行卡和信用卡通常需要 1 至 5 个工作日，具体以支付机构处理时间为准。'),
('配送', '配送和运费规则', '配送 运费 包邮 时效', '实付商品金额满 99 元免基础运费，不满 99 元收取 8 元运费。现货商品通常在付款后 24 小时内发出，江浙沪多数地区次日或隔日送达。'),
('订单', '订单取消规则', '取消订单 已发货 未发货', '待付款订单可直接取消。已付款但尚未出库的订单可以申请取消；已经发货的订单不能直接取消，可在签收后根据售后规则申请退货。'),
('优惠', '优惠计算规则', '优惠券 满减 叠加', '商品直降与店铺满减可以叠加，优惠券每笔订单限用一张。优惠金额不包含运费，退款时按商品实际支付金额计算。'),
('保修', '数码商品保修', '保修 电脑 耳机 手表', '电脑整机享受 2 年有限保修，耳机和智能穿戴设备享受 1 年有限保修。人为损坏、进液和非官方拆修不在免费保修范围内。');

INSERT INTO agent_skill(code, name, description, manual, tool_names) VALUES
('order_ops', '订单办理', '创建订单或取消订单时使用', '先查询或核对商品规格、库存和结算价格；创建订单前确认收货地址；取消订单前查询订单当前状态；任何写操作必须展示具体对象、金额及影响，得到用户确认后才能执行。', '["order_create", "order_cancel"]'),
('after_sales', '售后办理', '退款、退货或换货时使用', '先查询订单并核验归属，再调用售后资格预检；缺少售后类型或原因时一次性向用户补问；提交前展示订单、类型、退款金额和原因，得到用户确认后才能执行。', '["after_sales_precheck", "after_sales_submit"]');

INSERT INTO user_memory(user_id, memory_key, memory_value, source_conversation_id) VALUES
(10001, 'shoe_size', '用户通常穿 42 码鞋', NULL),
(10001, 'shopping_preference', '用户更看重性价比和通勤实用性', NULL);
