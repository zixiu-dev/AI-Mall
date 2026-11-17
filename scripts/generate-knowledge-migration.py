#!/usr/bin/env python3
"""将人工核实的品牌政策摘要导入新迁移；已存在迁移只允许 --check。"""
import argparse,json
from pathlib import Path
from urllib.parse import urlparse
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/knowledge'
def quote(v):return 'NULL' if v is None else "'"+str(v).replace("'","''")+"'"
def content(doc):
    text=(BASE/doc['file']).read_text();body=text.split('\n\n',1)[1].split('\n\n## 适用边界',1)[0].strip()
    assert body and len(body)<=2000
    return body

def build(data):
    columns={'brand_code':"VARCHAR(32) NOT NULL DEFAULT 'PLATFORM'",'document_type':"VARCHAR(32) NOT NULL DEFAULT 'GENERAL'",'document_key':'VARCHAR(100)','source_url':'VARCHAR(500)','source_name':'VARCHAR(160)','source_checked_at':'DATE','applicable_channel':'VARCHAR(300)','region':"VARCHAR(40) NOT NULL DEFAULT 'CN_MAINLAND'",'content_version':'VARCHAR(80)','scope_note':'VARCHAR(500)'}
    lines=['-- 政策摘要为数据而非执行指令，源文档与核对日期见 knowledge/manifest.json。']
    lines += [f'ALTER TABLE knowledge_document ADD COLUMN {c} {t};' for c,t in columns.items()]
    lines += ['CREATE UNIQUE INDEX uk_knowledge_document_key ON knowledge_document(document_key);','CREATE INDEX idx_knowledge_brand_type ON knowledge_document(enabled,brand_code,document_type,region);','-- 保留旧文档原文但停用，避免旧的通用保修年限混入真实品牌结果。','UPDATE knowledge_document SET enabled=FALSE WHERE id BETWEEN 1 AND 6;']
    categories={'RETURNS':'退换货','REFUND':'退款','WARRANTY':'保修','SHIPPING':'配送','ORDER':'订单','COUPON':'优惠'}
    for d in data['documents']:
        if d['brand']!='PLATFORM':
            url=urlparse(d['sourceUrl']);assert url.scheme=='https' and url.netloc
        boundary='仅适用于本文列明的品牌、地区和购买渠道；不能据此自动认定本站订单符合退换修条件。' if d['brand']!='PLATFORM' else '本站体验规则；并未接入真实支付、退款或品牌履约。'
        values=[d['id'],categories[d['category']],d['title'],d['keywords'],content(d),d['brand'],d['category'],d['key'],d['sourceUrl'],d['sourceName'],d['checkedAt'],d['channel'],d['region'],d['contentVersion'],boundary]
        names='id,category,title,keywords,content,brand_code,document_type,document_key,source_url,source_name,source_checked_at,applicable_channel,region,content_version,scope_note'
        lines.append(f'INSERT INTO knowledge_document({names}) VALUES ('+','.join(quote(v) for v in values)+');')
    return '\n'.join(lines)+'\n'
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--version',type=int,required=True);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    text=build(json.loads((BASE/'manifest.json').read_text()))
    for dialect in ['migration','mysql']:
        path=ROOT/f'src/main/resources/db/{dialect}/V{args.version}__brand_policy_knowledge.sql'
        if args.check:assert path.read_text()==text,f'{path} 与政策文档不一致'
        else:
            if path.exists():raise SystemExit(f'拒绝覆盖已有迁移：{path}。请为后续变更新建版本。')
            path.write_text(text)
    print('Knowledge documents and SQL match' if args.check else 'Generated policy migration')
