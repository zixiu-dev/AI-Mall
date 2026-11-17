#!/usr/bin/env python3
"""从核实清单生成首次目录迁移。已存在的历史迁移只允许校验，不自动覆盖。"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def sql(value):
    if value is None:
        return 'NULL'
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"

def build(products):
    lines = [
        '-- 真实型号与人民币参考价，来源见 catalog/real-products.json。',
        '-- 原商品仅下架：不复用 SKU，不改库存、历史订单、trace 或预占。',
        'UPDATE product SET enabled=FALSE WHERE id BETWEEN 101 AND 132;',
        'UPDATE product_sku SET enabled=FALSE WHERE product_id BETWEEN 101 AND 132;',
    ]
    for p in products:
        lines.append('INSERT INTO product(id,name,category,brand,summary,image_key,rating,sales_count) VALUES (' + ','.join(sql(v) for v in [p['id'],p['name'],p['category'],p['brand'],p['summary'],p['imageKey'],0,0]) + ');')
        attrs = {**p['attributes'],'sourceUrl':p['sourceUrl'],'sourceCheckedAt':p['checkedAt'],'priceBasis':p['priceBasis'],'stockBasis':'本项目演示库存'}
        if p['publishedAt']:
            attrs['sourcePublishedAt'] = p['publishedAt']
        for key,value in attrs.items():
            assert len(str(value))<=240, (p['id'],key)
            lines.append('INSERT INTO product_attribute(product_id,attr_key,attr_value) VALUES (' + ','.join(sql(v) for v in [p['id'],key,value]) + ');')
        for s in p['skus']:
            lines.append('INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock) VALUES (' + ','.join(sql(v) for v in [s['id'],p['id'],s['code'],s['spec'],s['price'],s['originalPrice'],s['stock']]) + ');')
            for key,value in s['attributes'].items():
                lines.append('INSERT INTO sku_attribute(sku_id,attr_key,attr_value) VALUES (' + ','.join(sql(v) for v in [s['id'],key,value]) + ');')
    return '\n'.join(lines)+'\n'

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version',required=True,type=int)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    catalog=json.loads((ROOT/'src/main/resources/catalog/real-products.json').read_text())
    content=build(catalog['products'])
    for dialect in ['migration','mysql']:
        target=ROOT/f'src/main/resources/db/{dialect}/V{args.version}__real_brand_catalog.sql'
        if args.check:
            assert target.read_text()==content, f'{target} 与清单不一致'
        elif target.exists():
            raise SystemExit(f'拒绝改写已有迁移：{target}。后续目录变更请新增版本。')
        else:
            target.write_text(content)
    public={'version':catalog['version'],'inventoryNote':catalog['inventoryNote'],'products':[{k:p[k] for k in ['id','name','imageKey','imagePath','sourceUrl','priceBasis','publishedAt','checkedAt']} for p in catalog['products']]}
    target=ROOT/'src/main/resources/static/catalog/products.json';target.parent.mkdir(exist_ok=True)
    if args.check:
        assert json.loads(target.read_text())==public
    else:
        target.write_text(json.dumps(public,ensure_ascii=False,indent=2)+'\n')
    print('Catalog SQL and presentation metadata match' if args.check else 'Generated frozen catalog migration and presentation metadata')
