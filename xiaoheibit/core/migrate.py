# -*- coding: utf-8 -*-
"""迁移工具：旧 chain.json → chain.db"""
import json
import time
from pathlib import Path
from .blockchain import get_top_height


def migrate_from_json(conn, json_path) -> dict:
    p = Path(json_path)
    if not p.exists():
        return {"error": f"找不到文件 {p}"}
    try:
        old = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        return {"error": f"JSON解析失败: {e}"}
    chain = old.get("chain", [])
    if not chain:
        return {"error": "旧文件中没有chain数据"}
    if get_top_height(conn) >= 0:
        return {"error": "chain.db 已有数据, 请先清空再迁移"}
    cur = conn.cursor()
    blocks_n = tx_n = 0
    try:
        conn.execute("BEGIN;")
        for b in chain:
            height = b.get("index", blocks_n)
            prev_hash = b.get("prev_hash", b.get("previous_hash", "0"))
            block_hash = b.get("hash")
            nonce = b.get("nonce", 0)
            ts = b.get("timestamp", int(time.time()))
            if not block_hash:
                raise ValueError(f"区块#{height} 缺少hash字段")
            cur.execute("INSERT INTO blocks(height, prev_hash, block_hash, nonce, timestamp) VALUES (?,?,?,?,?)",
                        (height, prev_hash, block_hash, nonce, ts))
            for tx in b.get("transactions", []):
                cur.execute("INSERT INTO transactions(block_height, sender, receiver, amount, nonce, signature, public_key, timestamp) VALUES (?,?,?,?,?,?,?,?)",
                            (height, tx.get("sender"), tx.get("receiver"), tx.get("amount"),
                             tx.get("nonce"), tx.get("signature", ""), tx.get("public_key"),
                             tx.get("timestamp", ts)))
                tx_n += 1
            blocks_n += 1
        conn.commit()
        return {"blocks": blocks_n, "transactions": tx_n}
    except Exception as e:
        conn.rollback()
        return {"error": f"迁移失败, 已回滚: {e}"}
