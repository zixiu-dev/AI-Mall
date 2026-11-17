#!/usr/bin/env python3
"""生成文档 SVG；加 --render 用本机 Chromium/Chrome 渲染 2 倍 PNG，无第三方 Python 依赖。"""
from pathlib import Path
from html import escape
import argparse, os, shutil, subprocess, tempfile, unicodedata
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/images'
COLORS = {
 'blue': ('#eff6ff','#b8d0ee','#2466b5'),
 'purple': ('#f6f3ff','#d4c4ef','#7151a6'),
 'orange': ('#fff7ed','#ebccaa','#b66824'),
 'teal': ('#eef9f7','#b5ded5','#247d71'),
 'red': ('#fff3f2','#edc5c0','#b35249'),
 'gray': ('#f1f5fa','#c8d5e5','#466484'),
}
class Diagram:
 def __init__(self,name,title,subtitle,h=900):
  self.name,self.h=name,h
  self.p=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="{h}" viewBox="0 0 1440 {h}" role="img" aria-labelledby="title desc"><title id="title">{escape(title)}</title><desc id="desc">{escape(subtitle)}</desc><defs><marker id="a" markerWidth="9" markerHeight="9" refX="7" refY="4.5" orient="auto"><path d="M1 1 L7 4.5 L1 8" fill="none" stroke="#7b8ba5" stroke-width="1.5"/></marker></defs><style>text{{font-family:"PingFang SC","Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#172b4d}}</style>']
  self.rect(0,0,1440,h,'#fbfcff',r=0)
  self.text(32,51,title,32,'#142b4b',True)
  self.text(34,88,subtitle,19,'#586c88')
  self.rect(1246,28,160,35,'#eaf1fb',r=17)
  self.text(1326,51,'智购 · 设计图',16,'#365c8b',True,'middle')
 def rect(self,x,y,w,h,fill,stroke='none',r=12,dash=False):
  self.p.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}"'+(' stroke-dasharray="6 5"' if dash else '')+'/>')
 def text(self,x,y,s,size=19,color='#172b4d',bold=False,anchor=None):
  self.p.append(f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" style="fill:{color}"'+(' font-weight="600"' if bold else '')+(f' text-anchor="{anchor}"' if anchor else '')+f'>{escape(s)}</text>')
 def panel(self,x,y,w,h,title,c='blue'):
  bg,border,ink=COLORS[c];self.rect(x,y,w,h,bg,border)
  self.text(x+20,y+34,title,23,ink,True)
 def card(self,x,y,w,h,title,lines=(),c='blue',fill='white'):
  bg,border,ink=COLORS[c];self.rect(x,y,w,h,'#ffffff' if fill=='white' else bg,border)
  self.rect(x+14,y+17,4,23,ink,r=2)
  self.text(x+28,y+36,title,22,ink,True)
  for i,s in enumerate(lines):self.text(x+17,y+69+i*28,s,19,'#43566f')
 def path(self,points,label='',lx=None,ly=None,arrow=True,dash=False):
  d='M'+' L'.join(f'{x} {y}' for x,y in points)
  self.p.append(f'<path d="{d}" fill="none" stroke="#7b8ba5" stroke-width="1.7"'+(' marker-end="url(#a)"' if arrow else '')+(' stroke-dasharray="5 5"' if dash else '')+'/>')
  if label:
   x,y=(lx,ly) if lx is not None else ((points[0][0]+points[-1][0])/2,(points[0][1]+points[-1][1])/2-8)
   width=sum(1 if unicodedata.east_asian_width(v) in 'WF' else .55 for v in label)*17+12
   self.rect(x-width/2,y-18,width,26,'#fbfcff',r=3);self.text(x,y,label,17,'#60728a',anchor='middle')
 def down(self,x,y1,y2,label=''):self.path([(x,y1),(x,y2)],label)
 def foot(self,s):
  self.path([(32,self.h-64),(1408,self.h-64)],arrow=False)
  self.text(34,self.h-29,s,18,'#687c96')
 def save(self):
  self.p.append('</svg>');OUT.mkdir(exist_ok=True)
  p=OUT/(self.name+'.svg');p.write_text('\n'.join(self.p),encoding='utf-8');return p

def diagrams():
 result=[]
 # RAG：离线版本发布与外层 Agent 的知识工具流水线。
 d=Diagram('knowledge-retrieval','Agentic RAG · 从子问题到可核对证据','同一个 ReAct 选择工具；搜索内部固定流水线；KEYWORD 默认启用，HYBRID 已完成百炼 / DeepSeek 真实联调',1260)
 d.panel(32,124,1376,194,'离线索引 · 不在用户聊天中构建，不在数据库事务中等模型','gray')
 for x,t,ls in [
  (52,'原文与源数据',['Markdown / manifest → 新迁移','knowledge_document：22 篇']),
  (390,'候选版本 BUILDING',['元数据 / 文本哈希校验','短政策整篇，长文按段落']),
  (728,'真实 embedding',['独立百炼地址 / 模型 / Key','网络调用在事务外完成']),
  (1066,'PG V1 原子发布',['vector(1024) / JSONB / 指针','源未变 → PG 事务切换指针'])
 ]:d.card(x,177,320,118,t,ls,'gray')
 for x in [372,710,1048]:d.path([(x,234),(x+18,234)])
 d.panel(32,346,1376,220,'外层 Spring AI · 理解完整请求，组织工具调用，不增加固定 Planner','purple')
 d.card(52,401,320,138,'① 可信本轮上下文',['用户原文 / 有限消息 / 期限','保留身份与交易确认门禁','例：Apple 退货 + Nike 配送'],'purple')
 d.card(434,401,430,138,'② 同一个 ReAct 选择工具',['search_knowledge(query, queries)','queries 只放 1～3 个知识子问题','商品 / 订单 / 结算继续用原工具'],'purple')
 d.card(926,401,462,138,'③ 工具参数与原意校验',['递归数组校验 / 去重 / 互斥参数','iPhone → APPLE；未知品牌不删掉','按主体与类别配对，歧义则补问'],'blue')
 d.path([(372,468),(434,468)]);d.path([(864,468),(926,468)])
 d.down(1157,539,645)
 d.panel(32,590,1376,327,'知识服务内部 · 有界只读并行，同一发布版本与同一硬过滤范围','blue')
 d.card(982,645,406,195,'④ 可选查询增强',['默认 DIRECT，不额外调用模型','开启后：本次最多 1 个批量请求','原查询 + 最多 2 个表达变体','越界 / 超时 → 原查询并警告','同轮缓存，换问题允许新增强'],'purple')
 d.card(530,645,406,90,'⑤ DB_KEYWORD',['SQL LIKE；不是 BM25'],'orange')
 d.card(530,766,406,111,'⑤ VECTOR',['PostgreSQL + pgvector','精确 / HNSW（不足精确兜底）'],'teal')
 d.card(52,645,430,232,'⑥ 融合与完整证据预算',['RRF：Σ 1 / (60 + rank)，rank ≥ 1','按文档 / 版本 / 块去重','每组最多 2 条，总计最多 6 条','工具另受 4000 字符预算限制','移除整块，同步更新覆盖缺口','范围不因相关性分数而放宽'],'blue')
 d.path([(982,688),(936,688)]);d.path([(982,813),(936,813)])
 d.path([(530,688),(482,688)]);d.path([(530,813),(506,813),(506,813),(482,813)])
 d.path([(1225,318),(1422,318),(1422,575),(730,575),(730,645)],'仅读发布版本',830,575,dash=True)
 d.down(267,877,970)
 d.card(52,970,430,155,'⑦ 交还证据与缺口',['groups / hits / coverage / warnings','来源、渠道、版本及真实检索模式','单路失败明确降级；两路失败 ERROR'],'teal')
 d.card(530,970,406,155,'⑧ 回到外层工具循环',['模型引用资料，可继续查订单','业务事实仍由业务工具返回','最后回答或按原规则请求确认'],'purple')
 d.card(984,970,404,155,'只读与执行边界',['政策不等于售后资格或交易授权','正文不是系统 / 工具指令','FOUND 不等于下单 / 退款成功'],'orange')
 d.path([(482,1046),(530,1046)])
 d.text(52,1170,'内容状态：FOUND / PARTIAL / NO_MATCH / CLARIFICATION_REQUIRED；基础设施错误不能伪装成无资料。',20,'#b35249',True)
 d.foot('向量排序在 PG 执行；HNSW 使用取决于查询计划；LIKE 不是 BM25，未接 cross-encoder。')
 result.append(d.save())
 # API journey
 d=Diagram('api-journey','接口全景 · 一次购买如何串起各功能','按用户任务组织接口；同一套 Service 同时供页面与 Agent 工具使用',850)
 d.panel(32,124,1376,160,'01  发现商品与准备购买','blue')
 for x,t,ls in [(50,'搜索与筛选',['GET /products','关键词 / 预算 / 同 SKU 条件']), (392,'详情选规格',['GET /products/{id}','明确 SKU、价格与展示库存']), (734,'购物车 / 优惠券',['GET /cart · GET /coupons','增改条目 / 领取用户券']), (1076,'结算试算',['POST /checkout/quote','数据库重算金额，不占库存'])]:d.card(x,175,314,94,t,ls[:1],'blue')
 for x in [364,706,1048]:d.path([(x,222),(x+28,222)])
 d.down(1233,284,318)
 d.panel(32,318,1376,178,'02  提交一次稳定的购买意图','orange')
 for x,t,ls in [(50,'后端发号',['POST /order-tokens','只发 UUID，不创建任务']), (500,'正式提交',['POST /orders/submit','trace + 购买参数，绑定快照']), (950,'后端执行',['SYNC 建单 / ASYNC 受理','用户不能指定绕过业务校验'])]:d.card(x,369,440,112,t,ls,'orange')
 d.path([(490,426),(500,426)]);d.path([(940,426),(950,426)])
 d.down(1170,496,530)
 d.panel(32,530,1376,178,'03  查询结果与订单后续','teal')
 for x,t,ls in [(50,'原 trace 查询',['GET /order-traces/{id}','PROCESSING / SUCCESS / 失败']), (500,'查看当前订单',['GET /orders','建单后是 PENDING_PAYMENT']), (950,'关闭或模拟支付',['POST /orders/{id}/cancel','demo-pay：显式启用的演示入口'])]:d.card(x,581,440,112,t,ls,'teal')
 d.path([(490,638),(500,638)]);d.path([(940,638),(950,638)])
 d.text(50,750,'Agent：chat / chat/stream → 查询工具；写工具 → 确认卡 → actions/{id}/confirm → 相同业务服务',21,'#7151a6',True)
 d.foot('图中商城路径省略 /api/store；物流、售后、知识还通过内部服务和 Agent 工具开放，不虚构 REST 接口。');result.append(d.save())
 # Order
 d=Diagram('order-submission','下单主链路 · 共用前置与两种执行方式','trace 绑定、预占意图分别独立提交；Redis 不属于 MySQL 原子事务',950)
 for x,t,ls in [(32,'① 幂等入口',['user + trace 的 AOP 锁','归属 / 快照摘要 / 并发门禁']), (382,'② 固定购买意图',['独立事务绑定 trace 与快照','独立事务保存 RESERVING']), (732,'③ 共享库存预扣',['首次选择并固定 SYNC / ASYNC','每 SKU Lua：检查、扣数、留标志'])]:d.card(x,131,330,123,t,ls,'blue')
 d.path([(362,192),(382,192)]);d.path([(712,192),(732,192)])
 d.card(1082,131,326,123,'库存池共用',['普通配置两种模式都预扣','多 SKU 部分成功可幂等释放'],'teal')
 d.path([(897,254),(897,284),(352,284),(352,316)])
 d.path([(897,284),(1056,284),(1056,316)])
 d.panel(32,316,650,350,'SYNC  ·  请求内完成建单','orange')
 d.card(52,369,610,185,'建单数据库事务',['锁 trace / CAS → 责任链重校验','条件扣库存、增加待支付占用、锁券','订单 / 明细、唯一 trace、定时关单命令','SUCCESS + orderId + ORDER_CREATED Outbox'],'orange')
 d.down(357,554,577)
 d.card(52,577,610,73,'提交后返回：SUCCESS + orderId',[],'orange')
 d.panel(710,316,698,350,'ASYNC  ·  先受理，再由消费者建单','purple')
 d.card(730,369,658,98,'受理数据库事务',['PROCESSING / RESERVED + ORDER_BUILD Outbox'],'purple')
 d.text(750,501,'提交后返回 PROCESSING + trace（已受理）',21,'#7151a6',True)
 d.down(1060,514,535)
 d.card(730,535,658,114,'Outbox → RocketMQ → 消费者',['消费幂等 → 读取后端快照 → 共用建单事务','提交 SUCCESS + orderId；前端按原 trace 查询'],'purple')
 d.down(357,666,707);d.down(1060,666,707)
 d.panel(32,707,1376,141,'事务提交以后：整理与恢复','teal')
 d.text(53,781,'确认 Redis 预占 · 失效展示缓存 · 转发订单事件；失败保留持久化进度，由恢复任务重试。',21)
 d.text(53,820,'建单失败：先回滚，再独立记失败与释放任务。已 SUCCESS 不能因确认延迟而释放成功订单。',20,'#247d71')
 d.foot('SUCCESS = 建单事务提交，订单待支付；不代表已付款，也不代表全部 Redis 整理和后续消息已追平。');result.append(d.save())
 # Agent
 d=Diagram('agent-execution','Agent 执行链路 · 从用户意图到真实业务','模型负责理解和选择工具；价格、库存、资格、订单结果由业务服务判断',930)
 d.card(32,130,322,123,'① 会话入口',['校验归属 / 本进程会话门禁','保存消息，准备历史与偏好'],'blue')
 d.card(379,130,442,123,'② Spring AI Agent',['理解 → 工具调用 → 工具结果 → 再推理','SSE 推送文字增量与工具执行事件'],'purple')
 d.card(846,130,562,123,'③ 两次工具检查',['推理前：按可见 Skills 过滤工具','执行时：开放名单 / Schema / 可信上下文'],'purple')
 d.path([(354,191),(379,191)]);d.path([(821,191),(846,191)])
 d.path([(1127,253),(1127,287),(357,287),(357,319)]);d.path([(1127,287),(1060,287),(1060,319)])
 d.panel(32,319,650,284,'查询工具：直接读取真实数据','blue')
 d.card(52,373,610,102,'商品 / 试算 / 订单 / 物流 / 规则',['ToolDispatcher → 对应业务 Service'],'blue')
 d.down(357,475,497)
 d.text(64,530,'工具返回结构化事实，模型据此回答。',22,'#2466b5',True)
 d.text(64,567,'商品编号和成交条件不能靠聊天内容猜测。',20,'#52647e')
 d.panel(710,319,698,284,'写工具：先提议，再由用户明确确认','orange')
 d.card(730,373,658,102,'持久化 pending_action',['保存用户 / 会话 / 原始参数 / 摘要 / CAS 状态'],'orange')
 d.down(1060,475,497)
 d.text(750,530,'用户确认 → 校验归属 → 恢复后端参数',22,'#b66824',True)
 d.text(750,567,'ConfirmedActionExecutor → 共用业务 Service',20,'#52647e')
 d.down(357,603,638);d.down(1060,603,638)
 d.panel(32,638,1376,178,'④ 保存真实回执与可恢复上下文','teal')
 for x,t,ls in [(52,'页面消息与摘要',['chat_message / conversation']), (502,'Spring AI 消息与工具配对',['agent_scope_state / spring-ai-v1']), (952,'长期偏好与购买结果',['user_memory / trace / order'])]:d.card(x,692,430,103,t,ls,'teal')
 d.text(52,853,'确认卡 EXECUTED：工具已执行；异步购买仍可能 PROCESSING，最终成功必须查询 trace。',21,'#7151a6',True)
 d.foot('当前会话门禁仅单进程；Skills 是业务手册；知识检索可用 PostgreSQL + pgvector 混合召回，RRF 后交回模型。');result.append(d.save())
 # Cache
 d=Diagram('catalog-cache-flow','商品详情缓存 · 命中、构建与故障保护','仅缓存公共展示数据；结算和建单重新读取权威数据',1030)
 steps=[('① 参数校验','商品 ID 必须为正整数，非法参数直接拒绝'),('② Caffeine 本地缓存','命中即返回，本地命中不查询 Bloom'),('③ Bloom → Redis 详情缓存','先过滤；可能存在/未知才读共享缓存'),('④ Redisson RLock + 双重检查','看门狗跨实例续期；拿锁后重查缓存'),('⑤ 获取回源许可 → 数据库 loader','查商品、SKU 与属性；每实例最多 16 个 loader'),('⑥ 校验后回填并释放自己的锁','有商品：详情；查不到：短 TTL 空结果')]
 for i,(t,ls) in enumerate(steps):
  y=132+i*130;d.card(32,y,766,105,t,[ls],'blue' if i<4 else 'teal')
  if i<5:d.down(415,y+105,y+130)
 d.panel(832,132,576,195,'穿透 / 击穿 / 雪崩分别应对','purple')
 for y,s in [(211,'穿透：Bloom + 空值缓存'),(256,'击穿：Redisson RLock + 锁后双检'),(301,'雪崩：TTL 抖动 + 有界回源')]:d.text(853,y,s,21)
 d.panel(832,350,576,230,'默认缓存策略','orange')
 for y,s in [(425,'详情 20～24s；空值 3～3.6s'),(466,'Caffeine 2s；HotKey 决定是否提升'),(507,'默认 HOTKEY，热点功能默认关闭'),(548,'ALL / OFF 用于显式策略与对照压测')]:d.text(853,y,s,21)
 d.panel(832,603,576,284,'旧值与故障不能无限扩散','teal')
 for y,s in [(679,'事务提交 → 切换缓存代际 + 广播'),(722,'旧回填校验代际 / 锁所有者 / 修订号'),(765,'Bloom 未就绪或故障：绕过过滤'),(808,'Redis 故障：冷却 + 有界回源'),(851,'过载返回 503，保护数据库')]:d.text(853,y,s,20)
 d.text(53,947,'RLock 自动续期，竞争等待最多 1200ms；事务内查询绕过缓存。展示最终一致，不用于交易判定。',21,'#466484')
 d.foot('空值只处理“可能存在但数据库查不到”的详情；布隆明确否定时不回源。默认未把所有商品正文预装进 Redis。');result.append(d.save())
 # Idempotency
 d=Diagram('idempotency-layers','幂等的三层保障 · 各自保护什么','同一购买重试复用 trace；以下按保障职责分层，不表示执行顺序',910)
 d.card(32,132,668,112,'原 trace 重试',['参数摘要不变 → 返回原状态或原订单'],'blue')
 d.card(724,132,684,112,'新 trace 再次购买',['即使商品、数量相同，也允许新的购买意图'],'teal')
 layers=[('01  接口并发保护','AOP + Redisson 看门狗锁',['键：ORDER_SUBMIT:<userId>:<traceId>','减少同时进入；释放后请求仍可能再次到达'],'blue'),('02  数据库结果兜底','行锁 + 状态 / version CAS + 唯一约束',['锁 trace 并重检；customer_order.submit_trace_id 唯一','即使 Redis 锁丢失，同 trace 仍不能再建第二单'],'orange'),('03  库存操作幂等','每 SKU + trace 的 Hash / Lua 状态机',['RESERVED → CONFIRMED / RELEASED / RETURNED','重复预扣、释放、返还不会再次修改计数'],'teal')]
 for i,(label,t,ls,c) in enumerate(layers):
  y=276+i*143;d.panel(32,y,1376,121,label,c);d.text(52,y+78,t,23,COLORS[c][2],True)
  for j,s in enumerate(ls):d.text(610,y+49+j*37,s,20)
 d.panel(32,731,1376,94,'MQ 快速去重：Redis String','purple')
 d.text(52,802,'ORDER_BUILD:<trace>   ·   PROCESSING:<owner> 600s → DONE 7200s；Lua 只允许所有者完成或删除。',20)
 d.foot('DONE 是消费处理结束的标记；可能记录了业务拒绝。订单结果看数据库 trace，库存 Redis 故障不能靠锁降级绕过。');result.append(d.save())
 # Messaging
 d=Diagram('outbox-recovery','可靠异步 · 业务状态、消息与恢复依据','业务事实与发送意图同事务；至少一次投递，消费者必须幂等',950)
 d.panel(32,130,1376,162,'① 异步受理事务：以下内容一起提交','orange')
 for x,t,ls in [(52,'trace：PROCESSING',['受理结果 + RESERVED + 处理期限']), (730,'Outbox：ORDER_BUILD',['稳定 eventId；消息只传 traceId'])]:d.card(x,185,658,89,t,ls,'orange')
 d.text(52,329,'COMMIT 后可返回“已受理”；发送失败不丢发送意图，后台仍可扫描。',22,'#b66824',True)
 for x,t,ls in [(32,'② Outbox 转发',['租约抢占 → 发送','成功标 SENT / 失败重试']), (500,'③ RocketMQ',['可重复投递','建单消费组独立']), (968,'④ 建单消费者',['消费幂等 → 读取后端快照','共用建单事务 → SUCCESS'])]:d.card(x,366,440,124,t,ls,'purple')
 d.path([(472,428),(500,428)]);d.path([(940,428),(968,428)]);d.down(1188,490,530)
 d.panel(32,530,1376,304,'⑤ 持久化进度支持幂等恢复','teal')
 for x,y,t,ls in [(52,585,'Lua 前 / 部分预扣后中断',['RESERVING + 快照 → 逐 SKU 释放']), (730,585,'受理提交但消息未发送',['Outbox → 原 eventId 重发']), (52,705,'建单提交但 Redis 未确认',['SUCCESS / CONFIRM_PENDING → 只补确认']), (730,705,'关单提交但 Redis 未返还',['order_inventory_return → 幂等 RETURN'])]:d.card(x,y,658,102,t,ls,'teal')
 d.text(53,876,'Outbox.SENT ≠ 建单成功；事件审计回执 ≠ 真实履约；trace.SUCCESS ≠ 已支付。',23,'#7151a6',True)
 d.foot('Broker 已收到、本地未记 SENT 时会重发；当前扫描和整理能力有限，需要同时关注后置积压及最老任务。');result.append(d.save())
 # Pressure
 d=Diagram('pressure-routing','AUTO 模式切换 · 先观测，再控制新购买','依据交易压力，首次预占时固定本次模式；浏览 HotKey 不参与路由',920)
 d.panel(32,132,502,466,'① 每秒采样与协调','blue')
 for i,(t,ls) in enumerate([('到达与耗时',['提交 QPS / 同步 P95 / 在途请求']),('数据库压力',['Hikari 活跃比例 / 等待线程']),('持久化积压',['未完成建单数 / 最老任务年龄'])]):d.card(52,190+i*130,462,111,t,ls,'blue')
 d.text(52,577,'在线实例 → Redis Lua 聚合与协调',21,'#2466b5',True)
 d.path([(534,360),(580,360)])
 d.panel(580,132,828,466,'② 高低阈值分离，避免频繁抖动','purple')
 d.card(600,204,338,109,'SYNC',['请求内共用建单事务'],'orange')
 d.card(1050,204,338,109,'ASYNC',['受理 + Outbox，消费者建单'],'purple')
 d.path([(938,235),(1050,235)],'高压 × 3',994,209)
 d.path([(1050,289),(938,289)],'低压 × 10',994,334)
 d.text(606,406,'冷却 30s；高低阈值不同，首次意图固定路线。',23,'#7151a6',True)
 d.text(606,458,'请求只读有效本地快照，不逐请求 COUNT 数据库。',21)
 d.text(606,509,'预算默认 60 QPS / 实例，需要按资源与压测校准。',21)
 d.text(606,560,'asyncReady 目前是功能开关，并非 Broker 实时探活。',20,'#52647e')
 d.panel(32,630,1376,178,'③ 接单保护：压力不能无限转移到队列','teal')
 for x,t,ls in [(52,'采样可信',['失败或超过 5s：拒绝新受理']), (502,'积压有界',['任务数 / 最老年龄超限：拒绝']), (952,'已受理仍继续恢复',['不因模式切换重写旧 trace'])]:d.card(x,684,430,104,t,ls,'teal')
 d.foot('采样驱动的是软阈值，不是精确全局限流器。异步只缓冲突发，最终吞吐仍受数据库和后置恢复能力限制。');result.append(d.save())
 # Data
 d=Diagram('data-relations','数据关系 · 购买意图、成交记录与恢复进度','只画核心业务关联；箭头表达引用 / 业务联系，不全部代表数据库外键',970)
 d.panel(32,130,414,615,'商品与规格','blue')
 d.card(52,198,374,124,'product',['商品名称 / 品牌 / 描述','product_attribute：材质、用途'],'blue')
 d.down(239,322,382,'一个商品多个 SKU')
 d.card(52,382,374,155,'product_sku',['具体规格 / 单价 / stock','locked_stock：待支付占用','sku_attribute：颜色、尺码等'],'blue')
 d.text(52,612,'历史订单保留名称 / 单价 / 地址快照',18,'#52647e')
 d.text(52,652,'商品变化不应改变过去的成交内容。',19,'#52647e')
 d.panel(474,130,464,615,'一次购买到真实订单','orange')
 d.card(494,198,424,124,'order_submit_trace',['用户 / 快照 / mode / status','reserve_status：库存整理进度'],'orange')
 d.down(706,322,382,'submit_trace_id 唯一')
 d.card(494,382,424,124,'customer_order',['订单状态 / 金额 / 地址快照','PENDING_PAYMENT / PAID / CANCELLED'],'orange')
 d.down(706,506,570,'一个订单多条明细')
 d.card(494,570,424,124,'order_item',['关联 product / SKU','保存名称、规格、成交单价与数量'],'orange')
 d.path([(494,634),(460,634),(460,460),(426,460)],arrow=True)
 d.panel(966,130,442,615,'授权、券与可靠恢复','teal')
 d.card(986,198,402,115,'pending_action',['确认卡参数 / 摘要 / CAS','订单提议关联 trace'],'purple')
 d.card(986,337,402,105,'user_coupon',['券状态 / used_order_id'],'teal')
 d.card(986,466,402,105,'order_message_outbox',['待发送命令 / 事件 / 租约'],'teal')
 d.card(986,595,402,105,'order_inventory_return',['关单后的 Redis 返还进度'],'teal')
 d.path([(918,258),(986,258)],arrow=False,dash=True)
 d.panel(32,779,1376,97,'两个生命周期必须分开','purple')
 d.text(52,848,'trace.SUCCESS：曾成功建单；订单可能后来取消。SUCCESS + CONFIRM_PENDING：成功但 Redis 确认待重试。',20)
 d.foot('完整用户、购物车、会话、知识、售后表见正文；模型状态、页面消息和长期偏好因用途不同而分开保存。');result.append(d.save())
 # Deploy
 d=Diagram('deployment-modes','运行方式 · 先选数据环境，再选助手模式','demo 与普通配置是运行环境；真实模型开关可叠加到对应环境上',920)
 d.panel(32,132,668,388,'A  ·  demo：快速看业务闭环','blue')
 d.card(52,190,628,128,'Spring Boot + H2 内存数据库',['商品 / 购物车 / 券 / 同步建单 / 确认卡','重启重置；关闭交易预扣和 MQ'],'blue')
 d.text(52,370,'默认确定性助手，不消耗模型调用。',23,'#2466b5',True)
 d.text(52,417,'首次编译仍需要安装 HotKey 客户端依赖。',21)
 d.text(52,465,'缓存 Redis 未运行时走有界数据库回源。',21)
 d.panel(724,132,684,388,'B  ·  普通配置：真实组件联调','orange')
 d.card(744,190,644,128,'Spring Boot 模块化单体',['MySQL + 商品缓存 Redis + 交易 Redis','SYNC / ASYNC / AUTO；MQ 单独准备'],'orange')
 d.text(744,370,'商品缓存可淘汰；交易 Redis 为 noeviction。',22,'#b66824',True)
 d.text(744,417,'Compose 提供 MySQL、Redis、可选 HotKey。',21)
 d.text(744,465,'初始化后的库存池不能随意清空或关闭预扣。',21)
 d.down(366,520,559);d.down(1066,520,559)
 d.panel(32,559,1376,149,'C  ·  可选真实 Agent：在所选环境上启用模型','purple')
 d.text(53,636,'AGENT_AI_ENABLED=true + 模型 API Key / Base URL / Model → Spring AI Agent → 真实业务工具',21)
 d.text(53,680,'失败明确报告；不偷偷切换 demo 冒充真实模型。商品缓存、订单幂等仍由原业务层处理。',20,'#7151a6')
 d.panel(32,743,1376,87,'观察入口','teal')
 d.text(250,794,'商城 :8080  ·  管理端 127.0.0.1:9080  ·  Agent status + 实际工具结果共同验证运行状态',21)
 d.foot('环境变量需通过进程环境导入；.env.example 是变量清单。首次构建、端口和故障排查命令见正文。');result.append(d.save())
 # Testing
 d=Diagram('measurement-pipeline','压测口径 · 请求、受理、建单分别计数','同机单 JVM 实测；成功查询 QPS 与建单 TPS 使用不同的真实事件',930)
 for x,t,ls in [(32,'① 发压器',['查询：wrk 闭环多连接','下单：Node 固定到达率']), (500,'② 应用处理完成',['HTTP 总响应 / 2xx 分开','受理 PROCESSING 不等于建单']), (968,'③ 事务真正提交',['afterCommit 计新订单','重复消息不重复计 TPS'])]:d.card(x,133,440,123,t,ls,'blue')
 d.path([(472,195),(500,195)]);d.path([(940,195),(968,195)])
 d.panel(32,294,1376,175,'固定 1000ms 自然秒桶：独立记录事件，读取已关闭的秒','purple')
 for i,t in enumerate(['[s, s+1)','[s+1, s+2)','[s+2, s+3)','[s+3, s+4)']):
  x=52+i*338;d.card(x,354,316,91,t,['成功 QPS / 新订单 TPS'],'purple')
 d.text(52,511,'峰值：选最大完整一秒；平均：成功总数 ÷ 实际秒数。首尾不足一秒不按比例放大。',22,'#7151a6',True)
 d.panel(32,551,1376,213,'不同场景分开验证','teal')
 for x,t,ls in [(52,'冷构建 / 持续重建',['1024 个不同商品 key','看 loader / 回填 / 双检命中']), (502,'同步 / 异步建单',['同 SKU 与 64 SKU 分开','受理、成功、失败、终态核对']), (952,'后置与守恒检查',['Redis 整理 / Outbox 积压','库存、订单件数、trace 一致'])]:d.card(x,607,430,136,t,ls,'teal')
 d.text(52,810,'不能互相替代：一秒峰值 ≠ 一分钟均值；HTTP 延迟 ≠ Redis GET 延迟；建单 TPS ≠ 全链路追平。',21,'#b66824',True)
 d.foot('数值与硬件、连接数、错误率和场景一起阅读。原始报告在本机 .runtime；测试与压测结果统一维护在这一篇。');result.append(d.save())
 # Payment timeout
 d=Diagram('payment-timeout','15 分钟未支付 · MQ 定时检查与幂等关单','从实际建单开始计时；消息只触发检查，数据库状态与期限决定是否关闭',920)
 d.panel(32,131,1376,172,'① 建单事务：订单与定时命令一起提交','orange')
 d.card(52,188,658,97,'PENDING_PAYMENT + payment_expires_at',['默认建单时间 + 900 秒；已有订单沿用原期限'],'orange')
 d.card(730,188,658,97,'ORDER_PAYMENT_TIMEOUT Outbox',['固定 epoch 毫秒；发送重试不能重新加 15 分钟'],'orange')
 d.path([(1060,303),(1060,324),(252,324),(252,342)])
 d.card(32,342,440,116,'② 提交后可靠发送',['RocketMQ 5 绝对时刻投递','已到期的积压命令立即补发'],'purple')
 d.card(500,342,440,116,'③ Broker 到期投递',['独立消费组 / 只订阅超时 Tag','消息可重复、到达可能滞后'],'purple')
 d.card(968,342,440,116,'④ 订单事务重新检查',['锁订单 → 数据库状态与期限','与支付 / 手动取消争同一把行锁'],'purple')
 d.path([(472,400),(500,400)]);d.path([(940,400),(968,400)])
 d.path([(1188,458),(1188,491),(270,491),(270,526)]);d.path([(1188,491),(728,491),(728,526)]);d.down(1188,491,526)
 d.card(32,526,440,147,'仍待支付且已到期',['CLOSED / 状态 CAS','返可售库存、释放待支付占用和券','持久化 Redis RETURN，提交后整理'],'teal')
 d.card(500,526,440,147,'已经支付或关闭',['不再变更订单','不返还已支付购买的库存','重复消息不会重复释放'],'blue')
 d.card(968,526,440,147,'仍待支付但未到期',['不相信消息里的时间','抛异常重试，避免提前确认完成','基础设施异常同样交给 MQ 重试'],'orange')
 d.panel(32,712,1376,111,'⑤ 数据库到期扫描兜底','teal')
 d.text(52,789,'MQ 关闭 / 发送积压 / 消费失败：扫描到期订单，进入相同关单事务；Redis 返还失败继续幂等恢复。',21)
 d.foot('支付入口也会复核截止时间；不保证恰好第 900 秒处理完，保证未到期不因提前消息关闭、已支付不被误关。');result.append(d.save())
 # Interview
 d=Diagram('interview-story','面试讲述路线 · 从一条请求展开设计','先讲业务正常流程，再讲重复、故障与取舍，用证据支撑结果',890)
 d.panel(32,131,1376,181,'第一步：从普通用户购买讲起','blue')
 for x,t,ls in [(52,'查询与选 SKU',['展示缓存保护数据库']), (502,'结算与绑定 trace',['核对价格 / 地址 / 券']), (952,'预扣与建单事务',['订单成功后等待支付'])]:d.card(x,188,430,103,t,ls,'blue')
 d.path([(482,239),(502,239)]);d.path([(932,239),(952,239)])
 d.down(1167,312,350)
 d.panel(32,350,668,224,'第二步：解释流量和故障','orange')
 d.text(52,427,'高压力 → 可靠受理 → Outbox → MQ 建单',22,'#b66824',True)
 d.text(52,474,'追问：锁过期？重复消息？预扣后崩溃？',21)
 d.text(52,522,'回答：数据库兜底 + Lua 幂等 + 持久化恢复',21)
 d.panel(724,350,684,224,'第三步：换成 Agent 入口','purple')
 d.text(744,427,'模型选工具 → 确认卡 → 用户批准 → 业务',22,'#7151a6',True)
 d.text(744,474,'追问：模型猜价格？未确认购买？上下文丢失？',21)
 d.text(744,522,'回答：真实工具结果 + 双检 + 后端参数与状态',21)
 d.down(366,574,613);d.down(1066,574,613)
 d.panel(32,613,1376,155,'第四步：用实测与边界收尾','teal')
 d.text(52,685,'说明机器、场景、QPS / TPS 口径、错误与后置积压；区分当前实现和后续优化。',22)
 d.text(52,733,'单体 / 演示身份 / 模拟支付 / 知识默认关键词、可选混合 / 单进程会话门禁：边界要主动讲清。',20,'#247d71')
 d.foot('每个设计都回答四件事：遇到什么问题 → 请求怎么走 → 靠什么保证 → 失败后怎么办。');result.append(d.save())
 return result

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--render',action='store_true');p.add_argument('--chrome',default=os.environ.get('CHROME_BIN'));p.add_argument('--only',help='只渲染指定文件名前缀，逗号分隔');args=p.parse_args()
 paths=diagrams()
 if args.render:paths.append(OUT / "system-architecture.svg")
 if args.only:paths=[x for x in paths if x.stem in args.only.split(',')]
 if args.render:
  chrome=args.chrome or shutil.which('chromium') or shutil.which('google-chrome') or '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  if not Path(chrome).exists():p.error('未发现 Chrome/Chromium，请用 --chrome 指定')
  import xml.etree.ElementTree as ET
  with tempfile.TemporaryDirectory(prefix='mall-doc-render-') as profile:
   for svg in paths:
    size=ET.parse(svg).getroot().attrib
    w,h=size['width'],size['height']
    cmd=[chrome,'--headless','--no-sandbox','--disable-gpu','--no-first-run','--no-default-browser-check',f'--user-data-dir={profile}',f'--screenshot={svg.with_suffix(".png")}',f'--window-size={w},{h}','--force-device-scale-factor=2','--hide-scrollbars',svg.as_uri()]
    completed=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=40)
    if completed.returncode or not svg.with_suffix('.png').exists():raise RuntimeError(completed.stderr.decode(errors='replace')[-2500:])
    print('Rendered',svg.stem,flush=True)
 else:print(f'Generated {len(paths)} SVGs; add --render for 2x PNG.')
if __name__=='__main__':main()
