#!/usr/bin/env python3
"""在随机临时库、两个 Redis 和本机 RocketMQ 中验证延时关单；不连接共享开发 MQ。"""
import argparse,datetime,json,subprocess,time,uuid
from pathlib import Path
from benchmark_environment import BenchmarkEnvironment

ROOT=Path(__file__).resolve().parents[1]

def wait_until(check,seconds=90):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        result=check()
        if result:return result
        time.sleep(.25)
    raise TimeoutError('等待定时消息或库存整理超时')

def verify(env,mode,seconds,index):
    # 恢复扫描只在应用启动时运行一次，之后一小时不运行；本轮新订单必须靠 MQ 关闭。
    env.start_app(mode,overrides={
        'app.order-processing.payment-ttl-seconds':seconds,
        'app.order-processing.recovery-interval-ms':3600000,
        'app.order-processing.demo-payment-enabled':'true',
        'app.order-messaging.relay-interval-ms':1000,
    })
    sku=980000+index
    env.sql("INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled) "
            "SELECT "+str(sku)+",product_id,'TIMEOUT-"+str(sku)+"',spec,price,original_price,10,TRUE "
            "FROM product_sku WHERE id=20101")
    coupon=int(env.sql('SELECT id FROM user_coupon WHERE user_id=10001 AND coupon_id=3001'))
    def buy(use_coupon=False):
        purchase={'source':'DIRECT','skuId':sku,'quantity':1,'addressId':20001}
        if use_coupon:purchase['userCouponId']=coupon
        quote=env.api('/api/store/checkout/quote',body=purchase)
        purchase['expectedPayAmount']=quote['payAmount']
        trace=env.api('/api/store/order-tokens',body={})['traceId']
        result=env.api('/api/store/orders/submit',body={'traceId':trace,'purchase':purchase})
        if mode=='ASYNC':assert result['status']=='PROCESSING',result
        result=wait_until(lambda: (r if (r:=env.api('/api/store/order-traces/'+trace))['status']=='SUCCESS' else None))
        return {'trace':trace,'order':result['orderId']}
    unpaid=buy(True);paid=buy();cancelled=buy()
    env.api('/api/store/orders/'+paid['order']+'/demo-pay',body={})
    env.api('/api/store/orders/'+cancelled['order']+'/cancel',body={})
    def state(order):return env.sql("SELECT status FROM customer_order WHERE id='"+order+"'")
    deadline=int(env.sql("SELECT deliver_at_epoch_ms FROM order_message_outbox WHERE order_id='"+unpaid['order']+"' AND event_type='ORDER_PAYMENT_TIMEOUT'"))
    assert state(unpaid['order'])=='PENDING_PAYMENT'
    observed=wait_until(lambda: int(time.time()*1000) if state(unpaid['order'])=='CLOSED' else None)
    assert observed>=deadline, '订单被提前关闭'
    assert state(paid['order'])=='PAID'
    assert state(cancelled['order'])=='CANCELLED'
    # 等所有定时消息真正消费，日志证明 paid/cancelled 的延时检查同样到达且没有再释放。
    def consumed():
        log=(env.folder/'application.log').read_text(errors='replace')
        return all('支付超时消息检查完成，orderId='+case['order'] in log for case in [unpaid,paid,cancelled])
    wait_until(consumed)
    def redis_value(key):
        return subprocess.check_output(['redis-cli','-h','127.0.0.1','-p',str(env.stock_port),'GET',key],text=True).strip()
    prefix='peak:'+env.database+':inventory:v1:{'+str(sku)+'}:'
    wait_until(lambda: redis_value(prefix+'available')=='9' and redis_value(prefix+'pending')=='0')
    assert env.sql('SELECT CONCAT(stock,\',\',locked_stock) FROM product_sku WHERE id='+str(sku))=='9,0'
    assert env.sql('SELECT status FROM user_coupon WHERE id='+str(coupon))=='AVAILABLE'
    assert env.sql("SELECT COUNT(*) FROM order_message_outbox WHERE order_id='"+unpaid['order']+"' AND event_type='ORDER_CANCELLED'")=='1'
    assert env.sql("SELECT COUNT(*) FROM order_inventory_return WHERE sku_id="+str(sku)+" AND completed=FALSE")=='0'
    result={'mode':mode,'testPaymentSeconds':seconds,'defaultPaymentSeconds':900,
            'unpaid':'CLOSED','paid':'PAID','manualCancel':'CANCELLED','deadlineEpochMs':deadline,
            'observedClosedEpochMs':observed,'databaseStock':9,'databaseLocked':0,'redisAvailable':9,
            'redisPending':0,'coupon':'AVAILABLE','timerMessagesConsumed':3,'scanIntervalSeconds':3600,
            'orders':[unpaid,paid,cancelled]}
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payment-seconds',type=int,default=12,help='只缩短本轮验证期限，正式默认仍为 900 秒')
    parser.add_argument('--rocketmq-home',type=Path,default=ROOT/'.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
    args=parser.parse_args()
    if args.payment_seconds<8:parser.error('本轮至少保留 8 秒支付窗口')
    folder=ROOT/'.runtime/payment-timeout-tests'/(datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6])
    env=BenchmarkEnvironment(ROOT,folder,args.rocketmq_home)
    report={'runDirectory':str(folder),'scenarios':[]}
    try:
        env.start()
        for index,mode in enumerate(['SYNC','ASYNC']):report['scenarios'].append(verify(env,mode,args.payment_seconds,index))
        report['passed']=True
    finally:
        env.close()
        report['isolatedProcessesStopped']=True;report['isolatedDatabaseDropped']=not env.created
        (folder/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print('Report: '+str(folder/'report.json'),flush=True)
if __name__=='__main__':main()
