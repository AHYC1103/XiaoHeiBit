# -*- coding: utf-8 -*-
"""区块链：余额、创世、挖矿、区块查询、链校验"""
import time
import sqlite3
from . import config, utils


def get_balance(conn: sqlite3.Connection, address: str) -> int:
    cur = conn.cursor()
    cur.execute("SELECT COALESCE(SUM(amount),0) AS s FROM transactions WHERE receiver=? AND sender<>?",
                (address, address))
    inc = cur.fetchone()["s"]
    cur.execute("SELECT COALESCE(SUM(amount),0) AS s FROM transactions WHERE sender=?",
                (address,))
    out = cur.fetchone()["s"]
    return inc - out


def get_top_height(conn: sqlite3.Connection) -> int:
    cur = conn.cursor()
    cur.execute("SELECT COALESCE(MAX(height),-1) AS h FROM blocks")
    return cur.fetchone()["h"]


def ensure_genesis(conn: sqlite3.Connection, cfg: dict):
    if get_top_height(conn) >= 0:
        return
    genesis_tx = {
        "sender": "SYSTEM", "receiver": config.INITIAL_OWNER, "amount": config.INITIAL_SUPPLY,
        "signature": "GENESIS", "timestamp": int(time.time()),
    }
    txs = [genesis_tx]
    nonce, ts = 0, int(time.time())
    while True:
        h = utils.calc_block_hash(0, "0", txs, ts, nonce)
        if h.startswith("0" * cfg["difficulty"]):
            break
        nonce += 1
    cur = conn.cursor()
    try:
        conn.execute("BEGIN;")
        cur.execute("INSERT INTO blocks(height, prev_hash, block_hash, nonce, timestamp) VALUES (?,?,?,?,?)",
                    (0, "0", h, nonce, ts))
        cur.execute("INSERT INTO transactions(block_height, sender, receiver, amount, signature, timestamp) VALUES (?,?,?,?,?,?)",
                    (0, "SYSTEM", config.INITIAL_OWNER, config.INITIAL_SUPPLY, "GENESIS", ts))
        conn.commit()
    except Exception:
        conn.rollback()
        raise


def mine_blocks(conn: sqlite3.Connection, count: int, miner_address: str, cfg: dict,
                interrupt=lambda: False) -> dict:
    if not utils.is_valid_address(miner_address):
        return {"mined": 0, "error": "矿工地址非法"}
    mined = 0
    for _ in range(count):
        if interrupt():
            break
        cur = conn.cursor()
        cur.execute("SELECT sender, receiver, amount, nonce, signature, public_key, timestamp FROM mempool ORDER BY mp_id")
        pool = [dict(r) for r in cur.fetchall()]
        cur.execute("SELECT COALESCE(MAX(height),-1) AS h FROM blocks")
        prev_h = cur.fetchone()["h"]
        cur.execute("SELECT block_hash FROM blocks WHERE height=?", (prev_h,))
        prev_hash = cur.fetchone()["block_hash"] if prev_h >= 0 else "0"
        reward_tx = {"sender": "SYSTEM", "receiver": miner_address, "amount": cfg["miner_reward"],
                     "signature": "MINER_REWARD", "timestamp": int(time.time())}
        txs = pool + [reward_tx]
        height = prev_h + 1
        ts = int(time.time())
        nonce = 0
        hash_txs = [utils.tx_for_hash(t) for t in txs]
        while True:
            if interrupt():
                return {"mined": mined, "error": None}
            h = utils.calc_block_hash(height, prev_hash, hash_txs, ts, nonce)
            if h.startswith("0" * cfg["difficulty"]):
                break
            nonce += 1
        try:
            conn.execute("BEGIN;")
            cur.execute("INSERT INTO blocks(height, prev_hash, block_hash, nonce, timestamp) VALUES (?,?,?,?,?)",
                        (height, prev_hash, h, nonce, ts))
            for t in txs:
                cur.execute("INSERT INTO transactions(block_height, sender, receiver, amount, nonce, signature, public_key, timestamp) VALUES (?,?,?,?,?,?,?,?)",
                            (height, t["sender"], t["receiver"], t["amount"],
                             t.get("nonce"), t.get("signature"), t.get("public_key"), t["timestamp"]))
            cur.execute("DELETE FROM mempool")
            conn.commit()
            mined += 1
        except Exception:
            conn.rollback()
            return {"mined": mined, "error": "区块写入失败, 已回滚"}
    return {"mined": mined, "error": None}


def get_block(conn: sqlite3.Connection, height: int) -> dict:
    cur = conn.cursor()
    cur.execute("SELECT * FROM blocks WHERE height=?", (height,))
    b = cur.fetchone()
    if not b:
        return None
    cur.execute("SELECT sender, receiver, amount, timestamp FROM transactions WHERE block_height=?",
                (height,))
    return {"height": b["height"], "prev_hash": b["prev_hash"], "hash": b["block_hash"],
            "nonce": b["nonce"], "timestamp": b["timestamp"],
            "transactions": [dict(r) for r in cur.fetchall()]}


def get_recent_blocks(conn: sqlite3.Connection, limit: int = 10) -> list:
    cur = conn.cursor()
    cur.execute("SELECT height, block_hash, nonce, timestamp, "
                "(SELECT COUNT(*) FROM transactions t WHERE t.block_height=b.height) AS tx_count "
                "FROM blocks b ORDER BY height DESC LIMIT ?", (max(1, min(limit, 100)),))
    return [dict(r) for r in cur.fetchall()]


def get_tx_history(conn: sqlite3.Connection, address: str, page: int = 1, size: int = 20) -> dict:
    if not utils.is_valid_address(address):
        return {"error": "地址非法"}
    cur = conn.cursor()
    # 查总数
    cur.execute("SELECT COUNT(*) as total FROM transactions WHERE sender=? OR receiver=?", (address, address))
    total = cur.fetchone()["total"]
    # 分页查询
    offset = (page - 1) * size
    cur.execute("""SELECT t.block_height, t.sender, t.receiver, t.amount, t.timestamp
                   FROM transactions t WHERE t.sender=? OR t.receiver=?
                   ORDER BY t.block_height DESC LIMIT ? OFFSET ?""", (address, address, size, offset))
    confirmed = [dict(r) for r in cur.fetchall()]
    cur.execute("""SELECT sender, receiver, amount, timestamp FROM mempool
                   WHERE sender=? OR receiver=?""", (address, address))
    pending = [dict(r) for r in cur.fetchall()]
    return {"confirmed": confirmed, "pending": pending, "total": total}


def validate_chain(conn: sqlite3.Connection, cfg: dict):
    cur = conn.cursor()
    cur.execute("SELECT height, prev_hash, block_hash, nonce, timestamp FROM blocks ORDER BY height")
    blocks = cur.fetchall()
    if not blocks:
        return True, "空链"
    prev_hash = "0"
    for i, b in enumerate(blocks):
        if b["height"] != i:
            return False, f"高度断裂: 期望{i} 实际{b['height']}"
        if b["prev_hash"] != prev_hash:
            return False, f"区块#{b['height']} 前哈希断裂"
        if not b["block_hash"].startswith("0" * cfg["difficulty"]):
            return False, f"区块#{b['height']} 不满足PoW难度"
        cur.execute("SELECT sender, receiver, amount, nonce, signature, public_key, timestamp FROM transactions WHERE block_height=?",
                    (b["height"],))
        txs = [dict(r) for r in cur.fetchall()]
        h = utils.calc_block_hash(b["height"], b["prev_hash"], [utils.tx_for_hash(t) for t in txs],
                                  b["timestamp"], b["nonce"])
        if h != b["block_hash"]:
            return False, f"区块#{b['height']} 哈希被篡改"
        prev_hash = b["block_hash"]
    return True, "整条链完整"
