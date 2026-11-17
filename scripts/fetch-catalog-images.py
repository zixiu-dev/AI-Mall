#!/usr/bin/env python3
"""按已核对的公开品牌图片地址保存资产；不抓登录信息，不在运行时依赖第三方图床。"""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'src/main/resources/catalog/real-products.json'

def download(product):
    target = ROOT / 'src/main/resources/static' / product['imagePath'].lstrip('/')
    target.parent.mkdir(parents=True, exist_ok=True)
    address = urllib.parse.quote(product['imageSource'], safe=':/?=&%,+;')
    request = urllib.request.Request(address, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(request, timeout=45) as response:
        content = response.read()
        mime = response.headers.get('Content-Type', '')
    if not mime.startswith('image/') or len(content) < 1000:
        raise ValueError(f"商品 {product['id']} 不是有效图片：{mime}")
    target.write_bytes(content)
    return {'id': product['id'], 'path': product['imagePath'], 'source': product['imageSource'],
            'bytes': len(content), 'contentType': mime, 'sha256': hashlib.sha256(content).hexdigest()}

if __name__ == '__main__':
    products = json.loads(CATALOG.read_text())['products']
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(download, products))
    evidence = ROOT / '.runtime/real-catalog/image-downloads.json'
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text(json.dumps(results, ensure_ascii=False, indent=2))
    print(f"Saved {len(results)} product images; {sum(i['bytes'] for i in results):,} bytes")
