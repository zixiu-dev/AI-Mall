-- 用户、商品、会话指南及其他初始记录，来源为项目演示种子数据。
-- Flyway 仅给从 B22 初始化的库插入这些记录。已有迁移历史的库保留现有业务数据。
SET @bitmall_seed_new_database = (
    SELECT COUNT(*) FROM `${flyway:table}` WHERE version = '22' AND type = 'SQL_BASELINE'
);

-- app_user
INSERT INTO `app_user` (`id`, `nickname`, `phone`, `created_at`)
SELECT 10001,'小林','13800000001',CURRENT_TIMESTAMP
WHERE @bitmall_seed_new_database = 1;

-- user_address
INSERT INTO `user_address` (`id`, `user_id`, `receiver`, `phone`, `province`, `city`, `district`, `detail`, `is_default`)
SELECT 20001,10001,'林同学','13800000001','浙江省','杭州市','西湖区','文三路 99 号 3 幢 1201',1
WHERE @bitmall_seed_new_database = 1;

-- product
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 101,'NovaBook Pro 14','电脑办公','Nova','轻薄笔记本电脑，2.8K 高刷屏，适合编程、设计和移动办公。','laptop-blue',4.9,8620,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 102,'KeyFlow 75 机械键盘','电脑办公','KeyFlow','75% 配列，三模连接，热插拔轴座与长续航。','keyboard',4.8,15632,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 103,'AirBeat Pro 降噪耳机','数码影音','AirBeat','自适应主动降噪，空间音频，单次续航 38 小时。','headphone',4.8,23110,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 104,'PixelWatch S3','智能穿戴','Pixel','全天候健康监测、双频 GPS 与 5ATM 防水。','watch',4.7,9730,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 105,'BrewGo 随行咖啡机','生活家电','BrewGo','便携胶囊咖啡机，支持冷热萃取，USB-C 充电。','coffee',4.6,5210,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 106,'Lumina 护眼台灯','生活家电','Lumina','全光谱无蓝光频闪，智能感光和番茄钟模式。','lamp',4.9,12450,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 107,'TrailPack 28L 通勤包','运动户外','Trail','防泼水面料，独立电脑仓，背负透气，适合通勤短途。','backpack',4.8,18700,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 108,'CloudStep 日常跑鞋','运动户外','CloudStep','轻量缓震鞋底，宽楦设计，适合日常慢跑与通勤。','shoes',4.7,28600,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 109,'Nova X1 全能手机','数码影音','Nova','高清影像与长续航，轻松记录生活的每一刻。','phone',4.6,1500,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 110,'SoundBox Mini 蓝牙音箱','数码影音','SoundBox','便携无线音箱，低频饱满，享受随时随地的音乐。','speaker',4.7,2183,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 111,'ViewPlus 27 4K 显示器','电脑办公','ViewPlus','27 英寸高清屏幕，多接口连接，让办公视野更开阔。','monitor',4.8,2866,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 112,'NovaBook Air 13','电脑办公','Nova','轻量机身与长续航，适合学习、文档处理和出差。','laptop-blue',4.9,3549,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 113,'CleanBot S 扫地机器人','生活家电','CleanBot','扫拖一体，智能规划清洁路径，轻松打理日常地面。','vacuum',4.6,4232,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 114,'DailyCook 不粘煎锅','生活家电','DailyCook','轻量锅身，均匀导热，适合日常早餐和家庭煎炒。','pan',4.7,4915,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 115,'TrailPack 轻量徒步包','运动户外','Trail','轻量透气背负，分区收纳，陪你探索周末新路线。','backpack',4.8,5598,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 116,'CloudStep 轻跑训练鞋','运动户外','CloudStep','弹性缓震与防滑底纹，让每一次轻跑更自在。','shoes',4.9,6281,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 117,'DayCup 随行保温杯','居家生活','DayCup','双层保温，密封防漏，通勤与户外都能随手携带。','bottle',4.6,6964,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 118,'SoftNest 纯棉四件套','居家生活','SoftNest','柔软亲肤纯棉面料，为卧室添一份自然舒适。','bed',4.7,7647,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 119,'Morning 挂耳咖啡','食品饮料','Morning','中度烘焙，坚果与可可风味，冲泡方便。','food',4.8,8330,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 120,'NutDay 每日坚果礼盒','食品饮料','NutDay','独立小包，多种坚果搭配，办公室与出游随心享。','food',4.9,9013,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 121,'PureDay 温和洁面乳','个护美妆','PureDay','细腻泡沫与温和清洁，日常护肤从洁面开始。','skincare',4.6,9696,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 122,'Hydra 水润保湿乳','个护美妆','Hydra','轻盈易涂抹，日常保湿，适合通勤随身护理。','skincare',4.7,10379,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 123,'FitBand 运动手环','智能穿戴','FitBand','轻巧佩戴，记录运动与睡眠，支持多种运动模式。','watch',4.8,11062,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 124,'LeafTea 茉莉花茶','食品饮料','LeafTea','清新花香，独立茶包，给忙碌生活一个轻松间隙。','food',4.9,11745,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 125,'SwiftRun 轻量竞速跑鞋','运动户外','SwiftRun','网布透气鞋面，缓震中底，适合日常跑步训练。','shoes',4.8,9800,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 126,'UrbanWalk 城市通勤鞋','运动户外','UrbanWalk','柔软织物鞋面，轻量防滑，适合日常通勤。','shoes',4.7,5300,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 127,'TrailPeak 越野跑鞋','运动户外','Trail','耐磨橡胶外底，适合户外徒步和越野。','shoes',4.9,4200,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 128,'CloudStep 入门慢跑鞋','运动户外','CloudStep','透气网布与轻量缓震，适合入门跑步。','shoes',4.6,12600,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 129,'SwiftRun 长距离跑鞋','运动户外','SwiftRun','厚底缓震设计，适合长距离跑步训练。','shoes',4.8,6500,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 130,'CityPack 轻便电脑背包','运动户外','Trail','尼龙防泼水面料，独立电脑仓，适合通勤办公。','backpack',4.7,3800,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 131,'AirBeat Lite 无线耳机','数码影音','AirBeat','轻巧无线耳机，双麦通话，通勤听歌更方便。','headphone',4.7,7100,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 132,'NovaBook Study 14','电脑办公','Nova','学生轻薄笔记本电脑，适合文档办公与日常学习。','laptop-blue',4.8,4100,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 201,'Apple MacBook Pro 14 英寸 M4','电脑办公','Apple','M4 芯片笔记本电脑，配备 14 英寸 Liquid 视网膜 XDR 显示屏。','real-201',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 202,'Apple MacBook Air 13 英寸 M4','电脑办公','Apple','13 英寸轻薄笔记本电脑，M4 芯片，适合移动办公与学习。','real-202',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 203,'Apple Mac mini M4','电脑办公','Apple','M4 芯片迷你台式电脑，需另外搭配显示器、键盘和鼠标。','real-203',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 204,'Apple Studio Display 27 英寸','电脑办公','Apple','27 英寸 5K 视网膜显示器，标准玻璃与可调倾斜度支架。','real-204',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 205,'Apple iPhone 17','数码影音','Apple','iPhone 17，提供 256GB 和 512GB 存储容量，参考价来自中国大陆购买页。','real-205',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 206,'Apple AirPods Pro 2（USB-C）','数码影音','Apple','第二代 AirPods Pro，配备 USB-C 端口的 MagSafe 充电盒与主动降噪。','real-206',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 207,'Apple AirPods 4','数码影音','Apple','AirPods 4 标准版，无耳塞式设计，配备 USB-C 充电盒。','real-207',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 208,'Apple Watch Series 10','智能穿戴','Apple','Series 10 智能手表，42 毫米铝金属 GPS 机型。','real-208',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 209,'米家智能电饭煲 微压版 3L','生活家电','米家','看得见的真微压','real-209',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 210,'米家扫拖机器人 6','生活家电','米家','滚筒活水净除顽渍，超薄机身灵活绕物','real-210',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 211,'米家冰箱 十字430L 2026款','生活家电','米家','纤薄机身小占地 精控变温更养鲜','real-211',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 212,'米家洗衣机Pro 超电解智投 洗烘12kg','生活家电','米家','12kg大容量洗+9kg烘干，超电解祛渍净白防串色','real-212',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 213,'巨省电 小米空调 1.5匹新1级能效','生活家电','米家','强劲制冷 | 高效节能 | 智能互联','real-213',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 214,'米家微波炉','生活家电','米家','APP操控，精选食谱','real-214',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 215,'米家无线吸尘器 3C','生活家电','米家','120AW飓风吸力','real-215',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 216,'REDMI手环 4','智能穿戴','小米','运动不间断，轻盈长续航','real-216',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 217,'Nike Pegasus 42 GORE-TEX 跑鞋','运动户外','Nike','款式 IQ0450-001，耐克飞马男子防水公路跑步鞋。','real-217',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 218,'Nike Pegasus Premium 跑鞋','运动户外','Nike','款式 HQ2592-020，耐克顶级飞马男子公路跑步鞋。','real-218',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 219,'Nike Streakfly 2 跑鞋','运动户外','Nike','款式 HF6416-101，男子公路竞速跑步鞋。','real-219',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 220,'Nike Zoom Fly 6 SE 跑鞋','运动户外','Nike','款式 IV4083-001，男子公路竞速跑步鞋。','real-220',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 221,'Nike Elemental Premium 双肩包','运动户外','Nike','款式 DN2555-010，双肩包。','real-221',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 222,'Nike Hayward 双肩包','运动户外','Nike','款式 HJ8200-525，双肩包。','real-222',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 223,'IKEA STELNA 斯黛纳 玻璃杯','居家生活','IKEA','STELNA 斯黛纳 玻璃杯，透明玻璃 / 230ml。','real-223',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 224,'IKEA LACK 拉克 边桌','居家生活','IKEA','LACK 拉克 边桌，黑色 / 55×55cm。','real-224',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 225,'IKEA TERTIAL 特提亚 工作灯','居家生活','IKEA','TERTIAL 特提亚 工作灯，深灰色 / 不含灯泡。','real-225',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 226,'IKEA POÄNG 波昂 低背扶手椅','居家生活','IKEA','POÄNG 波昂 低背扶手椅，天然色 / 米色。','real-226',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 227,'IKEA GRANKÖLEN 格兰科伦 被套和枕套','居家生活','IKEA','GRANKÖLEN 格兰科伦 被套和枕套，深蓝色 / 150×200 / 50×80cm。','real-227',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 228,'IKEA KAFFEREP 卡弗雷普 燕麦饼干','食品饮料','IKEA','KAFFEREP 卡弗雷普 燕麦饼干，600g。','real-228',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 229,'IKEA VINTERSAGA 温特萨加 生姜饼干','食品饮料','IKEA','VINTERSAGA 温特萨加 生姜饼干，300g。','real-229',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 230,'IKEA DRYCK 苹果越桔味碳酸饮料','食品饮料','IKEA','DRYCK 苹果越桔味碳酸饮料，750ml。','real-230',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 231,'MUJI 无印良品米糠发酵精华液','个护美妆','MUJI','MUJI 无印良品米糠发酵精华液，50mL；使用方式与适用情况以品牌说明为准。','real-231',0.0,0,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product` (`id`, `name`, `category`, `brand`, `summary`, `image_key`, `rating`, `sales_count`, `enabled`)
SELECT 232,'MUJI 敏感肌用基础补水乳霜','个护美妆','MUJI','MUJI 敏感肌用基础补水乳霜，50g；使用方式与适用情况以品牌说明为准。','real-232',0.0,0,1
WHERE @bitmall_seed_new_database = 1;

-- product_attribute
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'processor','模拟八核处理器'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'screen','14英寸 2.8K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'type','笔记本电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 101,'weight','1.4kg'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 102,'connection','蓝牙 / 2.4G / 有线'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 102,'layout','75%配列'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 102,'material','PBT'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 102,'type','机械键盘'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 102,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 103,'batteryLife','38小时'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 103,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 103,'noiseCancellation','主动降噪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 103,'type','头戴式耳机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 103,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 104,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 104,'positioning','双频GPS'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 104,'type','智能手表'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 104,'useCase','运动'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 104,'waterResistance','5ATM'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 105,'brewing','冷热萃取'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 105,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 105,'power','USB-C充电'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 105,'type','胶囊咖啡机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 105,'useCase','旅行'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 106,'control','智能感光'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 106,'lighting','全光谱'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 106,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 106,'type','护眼台灯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 106,'useCase','学习'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 107,'capacity','28L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 107,'features','防泼水 透气 电脑仓'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 107,'material','尼龙'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 107,'type','背包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 107,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'features','缓震 轻量 通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'fit','宽楦'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 108,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 109,'battery','5000mAh'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 109,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 109,'screen','6.5英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 109,'type','手机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 109,'useCase','日常'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 110,'batteryLife','12小时'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 110,'connection','蓝牙'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 110,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 110,'type','蓝牙音箱'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 110,'useCase','户外'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 111,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 111,'ports','HDMI / USB-C'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 111,'screen','27英寸 4K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 111,'type','显示器'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 111,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 112,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 112,'screen','13英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 112,'type','笔记本电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 112,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 112,'weight','1.2kg'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 113,'features','扫拖一体 智能规划'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 113,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 113,'type','扫地机器人'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 113,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 114,'diameter','28cm'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 114,'features','不粘 电磁炉通用'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 114,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 114,'type','煎锅'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 114,'useCase','烹饪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 115,'capacity','20L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 115,'features','透气 轻量'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 115,'material','尼龙'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 115,'type','背包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 115,'useCase','徒步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'features','缓震 防滑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'fit','标准楦'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 116,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 117,'capacity','500ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 117,'material','不锈钢'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 117,'type','保温杯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 117,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 118,'bedSize','1.8m床'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 118,'material','纯棉'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 118,'type','床品四件套'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 118,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 119,'flavor','坚果 可可'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 119,'roast','中度烘焙'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 119,'shelfLife','12个月'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 119,'type','挂耳咖啡'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 119,'useCase','日常'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 120,'packaging','25g × 30包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 120,'shelfLife','6个月'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 120,'type','坚果礼盒'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 120,'useCase','送礼'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 121,'capacity','150ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 121,'skinType','一般肤质'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 121,'type','洁面乳'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 121,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 122,'capacity','100ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 122,'skinType','一般肤质'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 122,'type','保湿乳'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 122,'useCase','护肤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 123,'features','运动 睡眠记录'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 123,'material','硅胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 123,'type','运动手环'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 123,'useCase','运动'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 124,'flavor','茉莉花'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 124,'packaging','3g × 30包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 124,'shelfLife','18个月'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 124,'type','花茶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 124,'useCase','日常'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'features','轻量 透气 缓震'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'fit','标准楦'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 125,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'features','防滑 轻量'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'fit','宽楦'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'material','织物'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'type','休闲鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 126,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 127,'features','耐磨 防滑 越野'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 127,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 127,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 127,'type','越野跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 127,'useCase','徒步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 128,'features','入门 透气 缓震'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 128,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 128,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 128,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 128,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 129,'features','长距离 厚底 缓震'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 129,'material','网布'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 129,'outsole','橡胶'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 129,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 129,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 130,'capacity','20L / 24L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 130,'features','防泼水 电脑仓'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 130,'material','尼龙'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 130,'type','背包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 130,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 131,'batteryLife','24小时'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 131,'material','塑料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 131,'noiseCancellation','通话降噪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 131,'type','无线耳机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 131,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 132,'material','铝合金'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 132,'screen','14英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 132,'type','笔记本电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 132,'useCase','学习'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 132,'weight','1.5kg'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'processor','Apple M4'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'screen','14英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'sourcePublishedAt','2024-10-30'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'sourceUrl','https://www.apple.com.cn/newsroom/2024/10/apples-new-macbook-pro-features-the-incredibly-powerful-m4-family-of-chips/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'type','笔记本电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 201,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'processor','Apple M4'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'screen','13.6英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'sourcePublishedAt','2025-03-05'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'sourceUrl','https://www.apple.com.cn/newsroom/2025/03/apple-introduces-the-new-macbook-air-with-m4-and-a-gorgeous-new-sky-blue-color/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'type','笔记本电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 202,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'processor','Apple M4'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'sourcePublishedAt','2024-10-29'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'sourceUrl','https://www.apple.com.cn/newsroom/2024/10/apples-all-new-mac-mini-is-even-more-mighty-and-more-mini/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'type','台式电脑'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 203,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'resolution','5K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'screen','27英寸 5K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'sourcePublishedAt','2022-03-08'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'sourceUrl','https://www.apple.com.cn/newsroom/2022/03/apple-unveils-all-new-mac-studio-and-studio-display/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'type','显示器'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 204,'useCase','办公'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'sourceUrl','https://www.apple.com.cn/shop/buy-iphone/iphone-17'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'type','手机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 205,'useCase','日常'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'noiseCancellation','主动降噪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'sourcePublishedAt','2023-09-12'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'sourceUrl','https://www.apple.com.cn/newsroom/2023/09/apple-upgrades-airpods-pro-2nd-generation-with-usb-c-charging/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'type','无线耳机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 206,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'sourcePublishedAt','2024-09-09'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'sourceUrl','https://www.apple.com.cn/newsroom/2024/09/apple-introduces-all-new-airpods-4-with-industry-defining-audio-and-design/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'type','无线耳机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 207,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'diameter','42mm'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'material','铝金属'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'priceBasis','官方发布价'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'sourcePublishedAt','2024-09-09'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'sourceUrl','https://www.apple.com.cn/newsroom/2024/09/introducing-apple-watch-series-10/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'type','智能手表'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 208,'useCase','运动'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'capacity','3L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'sourceUrl','https://www.mi.com/shop/buy?product_id=14536'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'type','电饭煲'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 209,'useCase','烹饪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'sourceUrl','https://www.mi.com/shop/buy?product_id=24243'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'type','扫地机器人'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 210,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'capacity','430L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'sourceUrl','https://www.mi.com/shop/buy?product_id=22503'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'type','冰箱'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 211,'useCase','冷藏'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'capacity','12kg'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'sourceUrl','https://www.mi.com/shop/buy?product_id=22521'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'type','洗烘一体机'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 212,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'sourceUrl','https://www.mi.com/shop/buy?product_id=13143'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'type','空调'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 213,'useCase','温控'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'sourceUrl','https://www.mi.com/shop/buy?product_id=10330'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'type','微波炉'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 214,'useCase','烹饪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'sourceUrl','https://www.mi.com/shop/buy?product_id=20336'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'type','吸尘器'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 215,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'sourceUrl','https://www.mi.com/shop/buy?product_id=25768'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'type','运动手环'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 216,'useCase','运动'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'model','IQ0450-001'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'sourceUrl','https://www.nike.com.cn/t/pegasus-42-gore-tex-耐克飞马男子防水公路跑步鞋-wzt6auPv/IQ0450-001'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 217,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'model','HQ2592-020'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'sourceUrl','https://www.nike.com.cn/t/pegasus-premium-耐克顶级飞马男子公路跑步鞋-VQWzfn/HQ2592-020'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 218,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'model','HF6416-101'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'sourceUrl','https://www.nike.com.cn/t/streakfly-2-男子公路竞速跑步鞋-m74iEgEy/HF6416-101'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 219,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'model','IV4083-001'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'sourceUrl','https://www.nike.com.cn/t/zoom-fly-6-se-男子公路竞速跑步鞋-Wp6eiX9I/IV4083-001'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'type','跑鞋'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 220,'useCase','跑步'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'model','DN2555-010'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'sourceUrl','https://www.nike.com.cn/t/elemental-premium-双肩包-8xlHHcKA/DN2555-010'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'type','背包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 221,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'model','HJ8200-525'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'sourceUrl','https://www.nike.com.cn/t/hayward-双肩包-M6Hglw/HJ8200-525'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'type','背包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 222,'useCase','通勤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'capacity','230ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'material','钢化玻璃'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'sourceUrl','https://www.ikea.cn/cn/zh/p/stelna-si-dai-na-da-bei-tou-ming-bo-li-50258912/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 223,'type','水杯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'sourceUrl','https://www.ikea.cn/cn/zh/p/lack-la-ke-bian-zhuo-hei-se-00352988/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'type','边桌'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 224,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'sourceUrl','https://www.ikea.cn/cn/zh/p/30355438/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'type','台灯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 225,'useCase','学习'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'sourceUrl','https://www.ikea.cn/cn/zh/p/poaeng-bo-ang-di-bei-fu-shou-yi-tian-ran-se-mi-se-kai-tuo-tian-ran-se-mi-se-s69551607/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'type','扶手椅'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 226,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'material','纯棉'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'sourceUrl','https://www.ikea.cn/cn/zh/p/grankoelen-ge-lan-ke-lun-bei-tao-he-zhen-tao-shen-lan-se-60666981/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'type','床品'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 227,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'packaging','600g'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'sourceUrl','https://www.ikea.cn/cn/zh/p/kafferep-yan-mai-bing-gan-30374922/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 228,'type','饼干'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'packaging','300g'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'sourceUrl','https://www.ikea.cn/cn/zh/p/vintersaga-wen-te-sa-jia-bo-pian-sheng-jiang-bing-gan-70611974/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 229,'type','饼干'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'capacity','750ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'sourceUrl','https://www.ikea.cn/cn/zh/p/dryck-bubbel-aepple-lingon-ping-guo-yue-jie-wei-tan-suan-yin-liao-20226767/'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 230,'type','碳酸饮料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'capacity','50mL'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'sourceUrl','https://www.muji.com.cn/cn/store/commodity/103226'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'type','精华液'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 231,'useCase','护肤'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'capacity','50g'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'priceBasis','官网标价快照'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'sourceCheckedAt','2026-11-16'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'sourceUrl','https://www.muji.com.cn/cn/store/commodity/100563'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'stockBasis','本项目演示库存'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'type','保湿乳霜'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_attribute` (`product_id`, `attr_key`, `attr_value`)
SELECT 232,'useCase','护肤'
WHERE @bitmall_seed_new_database = 1;

-- product_sku
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1001,101,'NBP14-16-512','星海蓝 / 16GB / 512GB',4999.00,5499.00,23,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1002,101,'NBP14-32-1T','星海蓝 / 32GB / 1TB',6199.00,6699.00,8,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1003,102,'KF75-MINT','薄荷绿 / 线性轴',399.00,459.00,76,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1004,102,'KF75-GRAY','深空灰 / 段落轴',429.00,489.00,44,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1005,103,'ABP-BLACK','曜石黑',899.00,1099.00,65,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1006,104,'PWS3-46','曜石黑 / 46mm',1599.00,1799.00,31,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1007,105,'BG-CREAM','奶油白',499.00,599.00,18,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1008,106,'LUMINA-WHITE','云朵白',299.00,359.00,102,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1009,107,'TP28-BLACK','夜行黑',329.00,399.00,87,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1010,108,'CS-42-GRAY','烟灰 / 42 码',459.00,529.00,36,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1011,108,'CS-43-GRAY','烟灰 / 43 码',459.00,529.00,19,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1012,108,'CS-42-WHITE','云白 / 42 码',459.00,529.00,28,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1100,109,'WOWO-109','雾蓝 / 256GB',2599.00,2899.00,58,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1101,110,'WOWO-110','石墨灰',199.00,259.00,130,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1102,111,'WOWO-111','27 英寸 / 4K',1299.00,1599.00,42,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1103,112,'WOWO-112','银灰 / 16GB / 512GB',3699.00,4099.00,36,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1104,113,'WOWO-113','珍珠白',1699.00,1999.00,26,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1105,114,'WOWO-114','28cm / 电磁炉通用',159.00,219.00,95,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1106,115,'WOWO-115','森林绿 / 20L',229.00,299.00,78,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1107,116,'WOWO-116','米白 / 42 码',299.00,399.00,65,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1108,117,'WOWO-117','鼠尾草绿 / 500ml',89.00,119.00,180,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1109,118,'WOWO-118','燕麦色 / 1.8m 床',329.00,429.00,76,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1110,119,'WOWO-119','10g × 20 包',59.00,79.00,360,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1111,120,'WOWO-120','25g × 30 包',99.00,139.00,280,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1112,121,'WOWO-121','150ml',69.00,99.00,190,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1113,122,'WOWO-122','100ml',129.00,169.00,120,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1114,123,'WOWO-123','沙岩色',239.00,299.00,83,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1115,124,'WOWO-124','3g × 30 包',49.00,69.00,250,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1200,108,'CS-41-BLACK','黑色 / 41 码',429.00,529.00,17,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1201,108,'CS-42-BLACK','黑色 / 42 码',459.00,529.00,32,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1202,108,'CS-44-BLACK','黑色 / 44 码',499.00,559.00,0,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1203,116,'CS-LIGHT-41','米白 / 41 码',299.00,399.00,22,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1204,116,'CS-LIGHT-43','米白 / 43 码',319.00,399.00,18,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1205,125,'SR-42-BLACK','黑色 / 42 码',399.00,499.00,43,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1206,125,'SR-43-BLACK','黑色 / 43 码',419.00,499.00,28,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1207,125,'SR-42-WHITE','白色 / 42 码',409.00,499.00,16,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1208,126,'UW-42-BLACK','黑色 / 42 码',269.00,329.00,52,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1209,126,'UW-41-WHITE','白色 / 41 码',259.00,329.00,30,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1210,126,'UW-43-BLACK','黑色 / 43 码',279.00,329.00,0,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1211,127,'TP-42-BLACK','黑色 / 42 码',599.00,699.00,23,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1212,127,'TP-43-GREEN','绿色 / 43 码',629.00,729.00,18,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1213,128,'CS-E-41-BLACK','黑色 / 41 码',199.00,259.00,45,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1214,128,'CS-E-42-BLACK','黑色 / 42 码',229.00,279.00,62,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1215,128,'CS-E-43-WHITE','白色 / 43 码',239.00,299.00,31,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1216,129,'SR-L-42-WHITE','白色 / 42 码',489.00,599.00,20,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1217,129,'SR-L-42-BLACK','黑色 / 42 码',529.00,629.00,25,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1218,129,'SR-L-44-BLACK','黑色 / 44 码',549.00,649.00,0,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1219,130,'CP-20-BLACK','黑色 / 20L',189.00,239.00,44,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1220,130,'CP-24-GRAY','灰色 / 24L',219.00,269.00,30,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1221,131,'ABL-BLACK','黑色',199.00,299.00,64,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1222,131,'ABL-WHITE','白色',209.00,299.00,49,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1223,132,'NS14-16-512','银灰 / 16GB / 512GB',2999.00,3299.00,32,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 1224,132,'NS14-32-1T','银灰 / 32GB / 1TB',3899.00,4299.00,15,0,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20101,201,'REAL-201-1','深空黑色 / M4 / 16GB / 512GB',12999.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20102,201,'REAL-201-2','银色 / M4 / 16GB / 512GB',12999.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20201,202,'REAL-202-1','天蓝色 / M4 / 16GB / 256GB',7999.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20301,203,'REAL-203-1','银色 / M4 / 16GB / 256GB',4499.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20401,204,'REAL-204-1','标准玻璃 / 可调倾斜度支架',11499.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20501,205,'REAL-205-1','黑色 / 256GB',6799.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20502,205,'REAL-205-2','黑色 / 512GB',8799.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20601,206,'REAL-206-1','白色 / USB-C MagSafe 充电盒',1899.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20701,207,'REAL-207-1','白色 / 标准版',999.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20801,208,'REAL-208-1','亮黑色 / 42mm / GPS / 运动型表带',2999.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 20901,209,'REAL-209-1','白色 / 3L',459.00,499.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21001,210,'REAL-210-1','标准版',2699.00,3999.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21101,211,'REAL-211-1','十字门 / 430L / 2026款',1949.00,2299.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21201,212,'REAL-212-1','洗烘12kg',3299.00,3999.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21301,213,'REAL-213-1','1.5匹 / 新1级能效',1999.00,2499.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21401,214,'REAL-214-1','白色 / 标准版',429.00,499.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21501,215,'REAL-215-1','标准版',599.00,899.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21601,216,'REAL-216-1','标准版',169.00,NULL,36,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21701,217,'IQ0450-001-42','黑/亮眼柠檬黄/金属暗灰 / 42 码',1099.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21702,217,'IQ0450-001-43','黑/亮眼柠檬黄/金属暗灰 / 43 码',1099.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21801,218,'HQ2592-020-42','大学灰/浅红杉红/浅软石褐/黑 / 42 码',1499.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21802,218,'HQ2592-020-43','大学灰/浅红杉红/浅软石褐/黑 / 43 码',1499.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21901,219,'HF6416-101-42','白色/亮眼柠檬黄/帆白/煤黑 / 42 码',959.00,1199.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 21902,219,'HF6416-101-43','白色/亮眼柠檬黄/帆白/煤黑 / 43 码',959.00,1199.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22001,220,'IV4083-001-42','黑/烟灰/雾灰/白色 / 42 码',799.00,999.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22002,220,'IV4083-001-43','黑/烟灰/雾灰/白色 / 43 码',799.00,999.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22101,221,'DN2555-010-均码','黑/黑/煤黑 / 均码',239.00,299.00,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22201,222,'HJ8200-525-均码','王朝紫/黑/行动葡萄紫 / 均码',449.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22301,223,'REAL-223-1','透明玻璃 / 230ml',3.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22401,224,'REAL-224-1','黑色 / 55×55cm',69.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22501,225,'REAL-225-1','深灰色 / 不含灯泡',89.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22601,226,'REAL-226-1','天然色 / 米色',699.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22701,227,'REAL-227-1','深蓝色 / 150×200 / 50×80cm',199.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22801,228,'REAL-228-1','600g',49.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 22901,229,'REAL-229-1','300g',49.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 23001,230,'REAL-230-1','750ml',39.99,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 23101,231,'REAL-231-1','50mL',128.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `spec`, `price`, `original_price`, `stock`, `enabled`, `locked_stock`, `redis_inventory_initialized`)
SELECT 23201,232,'REAL-232-1','50g',108.00,NULL,80,1,0,0
WHERE @bitmall_seed_new_database = 1;

-- sku_attribute
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1001,'color','蓝色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1001,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1001,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1002,'color','蓝色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1002,'memory','32GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1002,'storage','1TB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1003,'color','绿色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1003,'switch','线性轴'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1004,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1004,'switch','段落轴'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1005,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1006,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1006,'diameter','46mm'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1007,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1008,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1009,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1010,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1010,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1011,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1011,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1012,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1012,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1100,'color','蓝色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1100,'storage','256GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1101,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1102,'resolution','4K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1102,'screen','27英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1103,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1103,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1103,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1104,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1105,'diameter','28cm'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1106,'capacity','20L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1106,'color','绿色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1107,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1107,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1108,'capacity','500ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1108,'color','绿色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1109,'bedSize','1.8m'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1109,'color','米色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1110,'packaging','10g × 20包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1111,'packaging','25g × 30包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1112,'capacity','150ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1113,'capacity','100ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1114,'color','米色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1115,'packaging','3g × 30包'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1200,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1200,'size','41'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1201,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1201,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1202,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1202,'size','44'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1203,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1203,'size','41'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1204,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1204,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1205,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1205,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1206,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1206,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1207,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1207,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1208,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1208,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1209,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1209,'size','41'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1210,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1210,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1211,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1211,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1212,'color','绿色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1212,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1213,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1213,'size','41'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1214,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1214,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1215,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1215,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1216,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1216,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1217,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1217,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1218,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1218,'size','44'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1219,'capacity','20L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1219,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1220,'capacity','24L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1220,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1221,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1222,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1223,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1223,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1223,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1224,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1224,'memory','32GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 1224,'storage','1TB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20101,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20101,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20101,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20102,'color','银色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20102,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20102,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20201,'color','蓝色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20201,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20201,'storage','256GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20301,'color','银色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20301,'memory','16GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20301,'storage','256GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20401,'resolution','5K'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20401,'screen','27英寸'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20501,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20501,'storage','256GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20502,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20502,'storage','512GB'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20601,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20701,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20801,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20801,'diameter','42mm'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20901,'capacity','3L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 20901,'useCase','烹饪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21001,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21101,'capacity','430L'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21101,'useCase','冷藏'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21201,'capacity','12kg'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21201,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21301,'useCase','温控'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21401,'useCase','烹饪'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21501,'useCase','清洁'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21601,'useCase','运动'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21701,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21701,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21702,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21702,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21801,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21801,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21802,'color','灰色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21802,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21901,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21901,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21902,'color','白色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 21902,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22001,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22001,'size','42'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22002,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22002,'size','43'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22101,'color','黑色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22201,'color','紫色'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22301,'capacity','230ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22301,'material','钢化玻璃'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22301,'type','水杯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22401,'type','边桌'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22401,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22501,'type','台灯'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22501,'useCase','学习'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22601,'type','扶手椅'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22601,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22701,'material','纯棉'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22701,'type','床品'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22701,'useCase','居家'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22801,'packaging','600g'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22801,'type','饼干'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22901,'packaging','300g'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 22901,'type','饼干'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 23001,'capacity','750ml'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 23001,'type','碳酸饮料'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 23101,'capacity','50mL'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `sku_attribute` (`sku_id`, `attr_key`, `attr_value`)
SELECT 23201,'capacity','50g'
WHERE @bitmall_seed_new_database = 1;

-- cart_item
INSERT INTO `cart_item` (`id`, `user_id`, `sku_id`, `quantity`, `selected`, `created_at`, `updated_at`)
SELECT 1,10001,1003,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `cart_item` (`id`, `user_id`, `sku_id`, `quantity`, `selected`, `created_at`, `updated_at`)
SELECT 2,10001,1008,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE @bitmall_seed_new_database = 1;

-- coupon
INSERT INTO `coupon` (`id`, `name`, `threshold_amount`, `discount_amount`, `total_stock`, `claimed_count`, `start_at`, `end_at`, `status`)
SELECT 3001,'新人专享 · 满 99 减 15',99.00,15.00,10000,1,'2026-01-01 00:00:00.000000','2027-12-31 23:59:59.000000','ACTIVE'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `coupon` (`id`, `name`, `threshold_amount`, `discount_amount`, `total_stock`, `claimed_count`, `start_at`, `end_at`, `status`)
SELECT 3002,'品质生活 · 满 500 减 80',500.00,80.00,5000,0,'2026-01-01 00:00:00.000000','2027-12-31 23:59:59.000000','ACTIVE'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `coupon` (`id`, `name`, `threshold_amount`, `discount_amount`, `total_stock`, `claimed_count`, `start_at`, `end_at`, `status`)
SELECT 3003,'大额焕新 · 满 3000 减 300',3000.00,300.00,1000,0,'2026-01-01 00:00:00.000000','2027-12-31 23:59:59.000000','ACTIVE'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `coupon` (`id`, `name`, `threshold_amount`, `discount_amount`, `total_stock`, `claimed_count`, `start_at`, `end_at`, `status`)
SELECT 3004,'全场通用 · 无门槛减 8',0.00,8.00,20000,0,'2026-01-01 00:00:00.000000','2027-12-31 23:59:59.000000','ACTIVE'
WHERE @bitmall_seed_new_database = 1;

-- user_coupon
INSERT INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `status`, `claimed_at`, `used_order_id`, `used_at`)
SELECT 1,10001,3001,'AVAILABLE','2026-10-01 09:00:00.000000',NULL,NULL
WHERE @bitmall_seed_new_database = 1;

-- customer_order
INSERT INTO `customer_order` (`id`, `user_id`, `status`, `goods_amount`, `discount_amount`, `freight_amount`, `pay_amount`, `address_snapshot`, `created_at`, `paid_at`, `cancelled_at`, `version`, `coupon_id`, `coupon_name`, `submit_trace_id`, `payment_expires_at`)
SELECT 'BM202609180002',10001,'COMPLETED',299.00,20.00,0.00,279.00,'林同学 13800000001 浙江省杭州市西湖区文三路99号3幢1201','2026-09-18 19:30:00.000000','2026-09-18 19:31:00.000000',NULL,0,NULL,NULL,NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `customer_order` (`id`, `user_id`, `status`, `goods_amount`, `discount_amount`, `freight_amount`, `pay_amount`, `address_snapshot`, `created_at`, `paid_at`, `cancelled_at`, `version`, `coupon_id`, `coupon_name`, `submit_trace_id`, `payment_expires_at`)
SELECT 'BM202610010001',10001,'SHIPPED',899.00,80.00,0.00,819.00,'林同学 13800000001 浙江省杭州市西湖区文三路99号3幢1201','2026-10-01 10:20:00.000000','2026-10-01 10:21:00.000000',NULL,0,NULL,NULL,NULL,NULL
WHERE @bitmall_seed_new_database = 1;

-- order_item
INSERT INTO `order_item` (`id`, `order_id`, `product_id`, `sku_id`, `product_name`, `sku_spec`, `unit_price`, `quantity`)
SELECT 1,'BM202610010001',103,1005,'AirBeat Pro 降噪耳机','曜石黑',899.00,1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `order_item` (`id`, `order_id`, `product_id`, `sku_id`, `product_name`, `sku_spec`, `unit_price`, `quantity`)
SELECT 2,'BM202609180002',106,1008,'Lumina 护眼台灯','云朵白',299.00,1
WHERE @bitmall_seed_new_database = 1;

-- logistics_event
INSERT INTO `logistics_event` (`id`, `order_id`, `event_time`, `status`, `description`)
SELECT 1,'BM202610010001','2026-10-01 16:00:00.000000','PICKED_UP','商家已发货，包裹已由杭州余杭集散中心揽收'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `logistics_event` (`id`, `order_id`, `event_time`, `status`, `description`)
SELECT 2,'BM202610010001','2026-10-02 07:40:00.000000','IN_TRANSIT','包裹已到达杭州西湖营业部，正在分拣'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `logistics_event` (`id`, `order_id`, `event_time`, `status`, `description`)
SELECT 3,'BM202610010001','2026-10-02 09:15:00.000000','OUT_FOR_DELIVERY','派送员王师傅正在配送，预计 18:00 前送达'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `logistics_event` (`id`, `order_id`, `event_time`, `status`, `description`)
SELECT 4,'BM202609180002','2026-09-20 14:10:00.000000','DELIVERED','包裹已由本人签收'
WHERE @bitmall_seed_new_database = 1;

-- user_memory
INSERT INTO `user_memory` (`id`, `user_id`, `memory_key`, `memory_value`, `source_conversation_id`, `version`, `updated_at`)
SELECT 1,10001,'shoe_size','用户通常穿 42 码鞋',NULL,0,CURRENT_TIMESTAMP
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `user_memory` (`id`, `user_id`, `memory_key`, `memory_value`, `source_conversation_id`, `version`, `updated_at`)
SELECT 2,10001,'shopping_preference','用户更看重性价比和通勤实用性',NULL,0,CURRENT_TIMESTAMP
WHERE @bitmall_seed_new_database = 1;

-- agent_skill
INSERT INTO `agent_skill` (`code`, `name`, `description`, `manual`, `tool_names`, `enabled`)
SELECT 'after_sales','售后办理','退款、退货或换货时使用','先查询订单并核验归属，再调用售后资格预检；缺少售后类型或原因时一次性向用户补问；提交前展示订单、类型、退款金额和原因，得到用户确认后才能执行。','[\"after_sales_precheck\", \"after_sales_submit\"]',1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `agent_skill` (`code`, `name`, `description`, `manual`, `tool_names`, `enabled`)
SELECT 'coupon_ops','优惠券办理','用户明确要求领取商城优惠券时加载','# 优惠券领取办理\n\n## 1. 判断用户意图\n只有用户明确要求领取优惠券时才进入办理。查询有哪些券、已有券、使用门槛或是否可用时，只调用 coupon_list 或结算试算，不得生成领取申请。\n\n## 2. 查询候选优惠券\n先调用 coupon_list 获取真实活动和用户领取状态。couponId 是活动 ID，领取时使用；userCouponId 是用户领取后的券 ID，结算时使用，两者不得混用。\n\n## 3. 确定目标优惠券\n目标必须来自用户明确选择或 coupon_list 的结果，不得猜测 couponId。存在多张相似券时展示名称、门槛、优惠金额和有效期并让用户选择；不得擅自全领，也不得把面额最大等同于最优。\n\n## 4. 检查领取条件\n生成确认卡前核对活动有效期、剩余库存和用户领取状态。同一用户对同一活动只能领取一次。已领取时说明 userCouponId 和状态，不得重复调用领取工具。\n\n## 5. 展示领取信息\n展示券名、优惠金额、使用门槛、有效期和必要限制。领取资格不等于使用资格；是否适用于当前订单及实际优惠金额，以 checkout_quote 的后端校验为准。\n\n## 6. 生成确认卡\n用户明确选定后调用 coupon_claim(couponId)。该工具只生成待确认记录，不立即扣库存或创建用户券。只有返回 pendingActionId 后才能提示点击“确认领取”或“暂不领取”，不得用文字表格冒充确认卡。\n\n## 7. 等待用户确认\n收到 AWAITING_CONFIRMATION 后立即结束本轮。聊天中的文字确认不能代替页面确认；不得自动确认、声称已领取、继续试算或下单，也不得同时创建另一笔待确认操作。\n\n## 8. 处理领取结果\n以后端执行回执为准。确认执行时后端会再次检查活动、有效期、库存和重复领取。失败时说明原因并重新查询，不得原样反复调用。成功结果中的 couponId 是活动 ID，userCouponId 才是结算使用的用户券 ID。\n\n## 9. 与下单流程衔接\n用户要求领券后下单时，先完成领券确认。本轮停止；后续根据后台回执或 coupon_list 获取 userCouponId，再调用 checkout_quote 重新试算，最后按下单手册生成下单确认卡。不得假设领券成功后一定可用或自动完成下单。','[\"coupon_claim\"]',1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `agent_skill` (`code`, `name`, `description`, `manual`, `tool_names`, `enabled`)
SELECT 'order_cancel_flow','取消订单','用户明确要求取消已有订单时加载','取消订单办理流程：\n1. 意图确认：只有用户明确要求取消订单才进入办理；仅查询状态、询问规则时不要生成取消申请。\n2. 定位订单：调用 order_query 查询当前用户的订单；订单号来自用户或真实查询结果，不得猜测。目标不明确或有多个候选时补问，不得擅自选择。\n3. 状态检查：仅 PENDING_PAYMENT 允许直接取消；PAID 必须走售后；已取消告知现状，其他状态说明不能直接取消，不得尝试绕过。后端生成确认卡及执行时都会重新校验。\n4. 展示影响：展示订单号、商品、订单金额和取消影响。取消后返还库存并解除优惠券占用；返还的券仍受有效期和使用条件限制。当前是演示交易，没有接入真实支付退款，不得承诺到账或原路退款。\n5. 准备确认：调用 order_cancel(orderId) 生成真实待确认记录，不能以文字表格代替确认卡。仅返回 pendingActionId 后提示点击“确认取消订单”；“暂不取消”表示放弃本次申请，不会取消订单。\n6. 等待用户：收到 AWAITING_CONFIRMATION 后立即结束本轮。文字回复不能替代页面确认，不得自动确认或声称已取消。\n7. 结果处理：以后端执行回执为准。状态变化时重新查询；结果不明时先查订单，不得原样反复调用。禁止将取消请求转成下单或其他写操作。','[\"order_cancel\"]',1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `agent_skill` (`code`, `name`, `description`, `manual`, `tool_names`, `enabled`)
SELECT 'order_create_flow','下单办理','用户明确要求购买或创建订单时加载','商品检索要求：将购买需求拆为关键词与预算、品牌、颜色、尺码、材质、用途等结构化条件调用 product_search，首次只查 3~4 个且 inStock=true。多个关键词用空格分开。返回的 matchedSkuId 和 matchedSpec 来自同一条满足条件的 SKU；通过 product_detail 核对后再选择。结果过多时补问或翻页，无结果时先说明条件，不得擅自放宽预算、颜色、尺码。搜索价格是券前单价，含优惠券和运费的最终金额必须重新试算。\n\n下单办理流程：\n1. 创建订单：先通过商品查询、详情或已有会话信息确定真实 SKU 和数量，缺少规格或数量时补问，不得猜测商品或 SKU ID。\n2. 优惠检查：确定 SKU 和数量后，默认调用 coupon_list 查询候选优惠券及用户领取状态；用户明确要求不用券时跳过。当前只支持商城通用满减券，每笔订单最多使用一张，不支持多券叠加。候选列表不代表最终可用，也不保证最优优惠，资格和金额以后端试算为准。\n3. 已有券：选用用户已领取且可用的 userCouponId 调用 checkout_quote。尚未领券：先说明门槛、优惠金额和有效期，加载 coupon_ops，使用活动 couponId 调用 coupon_claim 生成待确认记录；收到 AWAITING_CONFIRMATION 后立即停止，等待用户点击确认，不得声称已领取或继续建单。后续对话以后台回执和 coupon_list 查询结果确认领取成功，取得 userCouponId 后再试算。不能用活动 couponId 冒充用户券 ID。\n4. 结算：调用 checkout_quote 校验商品和优惠券并取得商品明细、地址及 payAmount。已知 SKU 时不要猜商品 ID 或遍历商品；必要时可先无券试算了解当前金额，但创建订单前应完成优惠检查，并按最终选定方案重新试算。没有合适优惠券时说明不用券。不要自行计算或猜测成交金额。\n5. 下单确认：核对具体商品、规格、数量、地址和最终金额。将试算返回的 addressId、payAmount 分别传入 order_create 的 addressId、expectedPayAmount，用券时保持相同 userCouponId。order_create 仅生成待确认记录，不代表建单成功；提示用户核对页面确认卡，得到后台执行成功回执后才能告知成功。\n6. 所有交易写操作必须经页面确认。用户文字确认不能代替确认接口；等待确认时立即结束本轮。资格、价格或库存变化时，说明后端错误并重新查询、试算或补问，不得原样反复调用。\n7. 确认卡必须真实创建：checkout_quote 只报价。用户明确购买且信息完整、试算成功后，调用 order_create；不得以文字表格代替确认卡。只查询或试算不得发起下单。只有工具返回 pendingActionId 后才能提示点击页面按钮。 确认建单成功后状态为 PENDING_PAYMENT，应提示用户支付，不得声称已付款或已扣款。','[\"order_create\"]',1
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `agent_skill` (`code`, `name`, `description`, `manual`, `tool_names`, `enabled`)
SELECT 'order_ops','订单办理','用户明确要求购买、创建订单或取消订单时优先加载','订单办理流程：\n1. 创建订单：先通过商品查询、详情或已有会话信息确定真实 SKU 和数量，缺少规格或数量时补问，不得猜测商品或 SKU ID。\n2. 优惠检查：确定 SKU 和数量后，默认调用 coupon_list 查询候选优惠券及用户领取状态；用户明确要求不用券时跳过。当前只支持商城通用满减券，每笔订单最多使用一张，不支持多券叠加。候选列表不代表最终可用，也不保证最优优惠，资格和金额以后端试算为准。\n3. 已有券：选用用户已领取且可用的 userCouponId 调用 checkout_quote。尚未领券：先说明门槛、优惠金额和有效期，加载 coupon_ops，使用活动 couponId 调用 coupon_claim 生成待确认记录；收到 AWAITING_CONFIRMATION 后立即停止，等待用户点击确认，不得声称已领取或继续建单。后续对话以后台回执和 coupon_list 查询结果确认领取成功，取得 userCouponId 后再试算。不能用活动 couponId 冒充用户券 ID。\n4. 结算：调用 checkout_quote 校验商品和优惠券并取得商品明细、地址及 payAmount。已知 SKU 时不要猜商品 ID 或遍历商品；必要时可先无券试算了解当前金额，但创建订单前应完成优惠检查，并按最终选定方案重新试算。没有合适优惠券时说明不用券。不要自行计算或猜测成交金额。\n5. 下单确认：核对具体商品、规格、数量、地址和最终金额。将试算返回的 addressId、payAmount 分别传入 order_create 的 addressId、expectedPayAmount，用券时保持相同 userCouponId。order_create 仅生成待确认记录，不代表建单成功；提示用户核对页面确认卡，得到后台执行成功回执后才能告知成功。\n6. 取消订单：先调用 order_query 确认目标订单和当前状态，展示订单、金额和取消影响，再调用 order_cancel 生成待确认记录。\n7. 所有交易写操作必须经页面确认。用户文字确认不能代替确认接口；等待确认时立即结束本轮。资格、价格或库存变化时，说明后端错误并重新查询、试算或补问，不得原样反复调用。\n8. 确认卡必须真实创建：checkout_quote 只报价，不生成确认按钮。用户已明确购买、规格数量完整且试算成功时，立即调用 order_create 生成待确认记录；不要以文字表格代替确认卡，不要在调用 order_create 前再要求用户文字确认。用户只要求查询或试算时，不得擅自发起订单。仅在写工具返回 pendingActionId 后提示用户点击页面按钮。','[\"order_create\", \"order_cancel\"]',0
WHERE @bitmall_seed_new_database = 1;

-- knowledge_document
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 1,'售后','七天无理由退货规则','退货 退款 七天无理由 拆封','自商品签收次日起 7 天内，商品保持完好且不影响二次销售的，可申请七天无理由退货。定制商品、激活后的数字商品以及已拆封的贴身用品除外。质量问题退货由平台承担运费。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 2,'售后','退款到账时效','退款 到账 原路退回 时间','售后审核通过后，退款原路退回。余额通常即时到账；银行卡和信用卡通常需要 1 至 5 个工作日，具体以支付机构处理时间为准。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 3,'配送','配送和运费规则','配送 运费 包邮 时效','实付商品金额满 99 元免基础运费，不满 99 元收取 8 元运费。现货商品通常在付款后 24 小时内发出，江浙沪多数地区次日或隔日送达。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 4,'订单','订单取消规则','取消订单 已发货 未发货','待付款订单可直接取消。已付款但尚未出库的订单可以申请取消；已经发货的订单不能直接取消，可在签收后根据售后规则申请退货。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 5,'优惠','优惠计算规则','优惠券 满减 叠加','商品直降与店铺满减可以叠加，优惠券每笔订单限用一张。优惠金额不包含运费，退款时按商品实际支付金额计算。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 6,'保修','数码商品保修','保修 电脑 耳机 手表','电脑整机享受 2 年有限保修，耳机和智能穿戴设备享受 1 年有限保修。人为损坏、进液和非官方拆修不在免费保修范围内。',0,'PLATFORM','GENERAL',NULL,NULL,NULL,NULL,NULL,'CN_MAINLAND',NULL,NULL
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10001,'退换货','Apple 中国大陆官方商店退货政策','苹果 Apple iPhone iPad MacBook AirPods 退货 退款 换货 无理由 激活 原包装','Apple Store 对符合条件的商品提供交付之日起 14 个自然日内的退货申请窗口。iPhone/iPad 退回时须完好，并带齐原包装、配件与说明材料。请从原 Apple 订单发起申请，核对具体产品限制；不能将此窗口套用到第三方商城订单。',1,'APPLE','RETURNS','apple-returns','https://www.apple.com.cn/shop/help/returns_refund','Apple 中国大陆官方商店退货政策','2026-11-17','中国大陆 Apple Store 在线商店对应订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10002,'退款','Apple 官方商店退款处理说明','Apple 苹果 退款 到账 原路 支付','符合退货条件并按官方流程退回商品后，退款按对应支付方式办理。退款处理与银行卡或支付账户实际到账不是同一时点，需核对 Apple 原订单与支付机构处理情况；本地摘要不承诺统一到账天数。',1,'APPLE','REFUND','apple-refund','https://www.apple.com.cn/shop/help/returns_refund','Apple 官方商店退款处理说明','2026-11-17','中国大陆 Apple Store 在线商店对应订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10003,'保修','Apple 中国大陆有限保修参考','Apple 苹果 保修 质保 维修 三包 硬件','该文档为 Apple 一年有限保修条款，并明确法定消费者权利不被该保修排除。适用品类、购买日期、材料或工艺缺陷及例外条件必须按具体条款核对；不能将单篇条款外推为所有 Apple 商品、主要部件或 AppleCare 服务的统一期限。',1,'APPLE','WARRANTY','apple-warranty','https://www.apple.com.cn/legal/warranty/products/warranty-china.html','Apple 中国大陆有限保修参考','2026-11-17','文档列明的 Apple 品牌硬件及适用保修版本','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10004,'配送','Apple 官方商店发货与交货','Apple 苹果 配送 发货 快递 运费 包邮','Apple 在线订单提供免费标准送货。快送、定时或定制商品的选项与时效依商品和地址而定，结账页及原订单显示具体安排；预计时间不是无条件到货承诺。本站体验订单不共享 Apple 的配送服务。',1,'APPLE','SHIPPING','apple-shipping','https://www.apple.com.cn/shop/help/shipping_delivery','Apple 官方商店发货与交货','2026-11-17','中国大陆 Apple 在线商店配送订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10005,'退换货','小米商城售后退换货政策','小米 米家 Xiaomi Redmi 红米 退货 换货 无理由 冰箱 洗衣机 手机','小米商城售后政策按品类区分无理由退货与质量问题退换修。应核对购买渠道、型号、收货日期、商品完好情况及原包装等条件，不能把手机规则直接用于冰箱、洗衣机或线下订单。具体退货限制以对应品类官方政策为准。',1,'XIAOMI','RETURNS','xiaomi-returns','https://www.mi.com/c/service/exchange','小米商城售后退换货政策','2026-11-17','中国大陆品牌官方线上渠道；须核对具体订单与商品条件','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10006,'保修','小米米家分类保修说明','小米 米家 红米 Redmi 保修 质保 维修 三包 冰箱 空调 洗衣机','小米售后服务按商品品类列示保修与三包要求，部分商品由厂家提供服务且期限存在差异。查询米家冰箱、空调或洗衣机时应提供具体型号、购买日期和故障信息，不能用统一的一年期限替代整机与主要部件条款。',1,'XIAOMI','WARRANTY','xiaomi-warranty','https://www.mi.com/c/service/exchange','小米米家分类保修说明','2026-11-17','中国大陆品牌官方线上渠道；须核对具体订单与商品条件','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10007,'配送','小米商城配送费参考','小米 米家 Redmi 物流 配送 运费 包邮 发货','小米商城运费按商品类别与配送方式区分，部分商品不参与一般包邮活动。配送费用与可选服务应在对应商品和实际结算订单核对，不应将本站满 99 元免基础运费的体验规则说成小米商城通用政策。',1,'XIAOMI','SHIPPING','xiaomi-shipping','https://www.mi.com/service/buy/postage','小米商城配送费参考','2026-11-17','中国大陆品牌官方线上渠道；须核对具体订单与商品条件','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10008,'退换货','Nike 中国大陆销售条款与退货','Nike 耐克 跑鞋 退货 换货 无理由 吊牌 定制 运费','Nike 销售条款规定，除定制商品及平台另有说明外，商品保持原购买状态并符合二次销售条件时，可在收货后 7 天内无理由退货。标签、使用或清洗情况、包装配件及商品页例外均须核对；寄回方式和运费按原耐克平台政策处理。',1,'NIKE','RETURNS','nike-returns','https://agreementservice.svs.nike.com.cn/rest/agreement?agreementType=termsOfSale&country=CN&language=zh&requestType=redirect&uxId=com.nike.tos','Nike 中国大陆销售条款与退货','2026-11-17','中国大陆 Nike 对应官方线上平台订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10009,'退款','Nike 订单退款说明','Nike 耐克 退款 到账 原路 质检','Nike 对退回包裹验收并确认符合退货政策后处理退款。取消订单与退货退款属于不同流程，退款时点、支付方式及实际到账需在原 Nike 订单和帮助页核对，不能仅凭申请提交就说已经到账。',1,'NIKE','REFUND','nike-refund','https://www.nike.com.cn/help/a/refund-info-cn','Nike 订单退款说明','2026-11-17','中国大陆 Nike.com.cn、Nike App、SNKRS 或官方微信小程序对应订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10010,'配送','Nike 会员配送服务','Nike 耐克 会员 配送 发货 运费 包邮','Nike 会员页提供线上会员订单的免费配送权益，退换货仍需满足相应政策，定制商品另有限制。应确认用户是否为适用会员以及订单所处官方渠道；不能据此保证所有非会员、其他平台或本站订单免邮。',1,'NIKE','SHIPPING','nike-shipping','https://www.nike.com.cn/membership/delivery','Nike 会员配送服务','2026-11-17','Nike 官方会员页列明的线上订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10011,'退换货','宜家中国大陆退货政策','IKEA 宜家 退货 换货 无理由 会员 食品 电器','宜家退货窗口为 60 天，宜家俱乐部会员为 365 天，须满足货品完好并提供原始购买凭证及已开具发票等要求。食品、家用电器及特定定制或已加工商品有例外，不能把会员窗口说成所有商品无条件可退。',1,'IKEA','RETURNS','ikea-returns','https://www.ikea.cn/cn/zh/customer-service/returns-claims/return-policy/','宜家中国大陆退货政策','2026-11-17','宜家政策列明的中国大陆门店、官网、App、小程序及天猫渠道','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10012,'保修','宜家品质保证分类说明','IKEA 宜家 保修 质保 品质保证 家具 床垫 凭证','宜家品质保证按产品系列分别列示，并从购买日起算，索赔时需原始购买凭证。床垫、家具与其他产品的保证范围及期限不同，须查看具体产品的完整保证书；不能把某系列的十年保证推广到所有宜家商品。',1,'IKEA','WARRANTY','ikea-warranty','https://www.ikea.cn/cn/zh/customer-service/returns-claims/guarantee/','宜家品质保证分类说明','2026-11-17','宜家品质保证列表和各系列完整保证书列明的产品','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10013,'配送','宜家中国大陆送货服务','IKEA 宜家 配送 运费 送货 快递 家具 大件','宜家区分包裹配送与货车配送，商品价格不含额外配送费用。包裹适用重量、外包装尺寸及品类限制，家具等大件按地址和服务方式核算。请在原宜家购物车输入地址核对费用和安排；本站不会按该资料自动计算运费。',1,'IKEA','SHIPPING','ikea-shipping','https://www.ikea.cn/cn/zh/customer-service/services/delivery/','宜家中国大陆送货服务','2026-11-17','中国大陆宜家送货服务覆盖地区和适用商品','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10014,'退换货','无印良品官方网络商城退换货','MUJI 无印良品 無印良品 退货 换货 无理由 标签 包装','无印良品官方网络商城提供自签收日起 14 日内的符合条件退换货服务，非质量问题需不影响再次销售；标签、包装、配件和商品状况有要求。实体店、官网、京东及天猫等渠道不接受相互出售商品的退换货，不能跨渠道套用。',1,'MUJI','RETURNS','muji-returns','https://www.muji.com.cn/cn/net/contents/contact/return/','无印良品官方网络商城退换货','2026-11-17','无印良品中国大陆官方网络商城对应订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10015,'退款','无印良品网络商城退款流程','MUJI 无印良品 退款 到账 退货 物流 审核','退货退款需要提交申请、商家审核、寄回与上传物流、仓库验收等步骤；仅退款有另一套适用订单流程。页面说明审核与退款处理时点，但不能把提交申请等同退款完成，应在原订单售后详情核对进度。',1,'MUJI','REFUND','muji-refund','https://www.muji.com.cn/cn/net/contents/contact/return/','无印良品网络商城退款流程','2026-11-17','无印良品中国大陆官方网络商城对应订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10016,'配送','无印良品官方网络商城配送说明','MUJI 无印良品 配送 运费 发货 包邮 大件','MUJI 官网配送说明区分小件与大件，区域、件数和商品类型影响运费；大陆以外地区及特定液体、粉尘商品存在配送限制。具体费用以实际商品和结算页面为准，不将其他渠道的包邮活动当作通用承诺。',1,'MUJI','SHIPPING','muji-shipping','https://www.muji.com.cn/cn/net/contents/contact/delivery/','无印良品官方网络商城配送说明','2026-11-17','无印良品中国大陆官方网络商城适用配送订单','CN_MAINLAND','summary-2026-11-17','仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10017,'退换货','沃沃智购体验商城售后办理','退货 换货 退款 售后 申请 订单','本站目前对符合后端状态及归属校验的订单提供售后预检和确认申请，申请成功进入 REVIEWING，并不是真实退款。品牌官方渠道政策不能自动决定本站订单资格；请提供本人订单号和申请类型。',1,'PLATFORM','RETURNS','platform-returns',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10018,'退款','沃沃智购体验退款边界','退款 到账 原路 审核','当前只记录售后申请及审核中状态，没有接入真实退款渠道，因此不能承诺到账时间或声称款项已退回。查询本人售后记录需使用后端状态。',1,'PLATFORM','REFUND','platform-refund',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10019,'配送','沃沃智购体验配送与运费','配送 运费 包邮 发货 物流','本站试算按当前服务规则计算运费：商品优惠后金额满 99 元免基础运费，否则 8 元。预计配送日期为演示信息，不表示品牌承运或真实发货；实际物流只读取本库订单轨迹。',1,'PLATFORM','SHIPPING','platform-shipping',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10020,'订单','沃沃智购待付款订单取消规则','取消 支付 付款 订单','当前普通取消接口只支持待付款订单；已支付等其他状态不能用该接口直接取消。新订单默认 15 分钟未付款自动关闭，取消或关闭以数据库处理结果为准。',1,'PLATFORM','ORDER','platform-order',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10021,'优惠','沃沃智购优惠券使用规则','优惠 优惠券 领券 满减','本站订单每笔限用一张满足有效期和金额门槛的用户券，优惠与运费通过后端试算核对。本站优惠券不代表 Apple、Nike 等品牌官方优惠，也不会与品牌订单共享。',1,'PLATFORM','COUPON','platform-coupon',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;
INSERT INTO `knowledge_document` (`id`, `category`, `title`, `keywords`, `content`, `enabled`, `brand_code`, `document_type`, `document_key`, `source_url`, `source_name`, `source_checked_at`, `applicable_channel`, `region`, `content_version`, `scope_note`)
SELECT 10022,'保修','沃沃智购品牌保修查询说明','保修 质保 维修 三包','本项目没有为全部品牌制定统一的硬件保修年限。请补充品牌、型号、购买地区、日期与渠道，再查看适用的品牌官方条款。',1,'PLATFORM','WARRANTY','platform-warranty',NULL,'沃沃智购当前实现与服务契约','2026-11-17','沃沃智购本项目体验商城','CN_MAINLAND','summary-2026-11-17','本站体验规则；并未接入真实支付、退款或品牌履约。'
WHERE @bitmall_seed_new_database = 1;

-- knowledge_index_head
INSERT INTO `knowledge_index_head` (`head_id`, `index_version`)
SELECT 1,NULL
WHERE @bitmall_seed_new_database = 1;
