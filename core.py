#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
XiaoHeiBit (XHbit) core.py
单文件核心：SQLite(chain.db) 存储 + 钱包 + 签名 + PoW + 交易校验 + 链逻辑 + 迁移工具
仅本地学习用途，禁止对外发行/交易/募资。
"""
import hashlib
import json
import os
import secrets
import sqlite3
import sys
import time
from pathlib import Path

from ecdsa import SigningKey, VerifyingKey, SECP256k1
from ecdsa.util import sigencode_string, sigdecode_string

# ---------------- 配置 ----------------
BASE_DIR = Path(__file__).resolve().parent
DB_FILE = BASE_DIR / "chain.db"          # SQLite 账本
WALLETS_FILE = BASE_DIR / "wallets.json"  # 多钱包私钥(敏感, 仅JSON)
CONFIG_FILE = BASE_DIR / "config.json"    # 用户配置
TMP_DIR = BASE_DIR / "tmp"

DIFFICULTY = 4
MINER_REWARD = 50
INITIAL_SUPPLY = 100000
INITIAL_OWNER = "XiaoHei"   # 创世账户(系统内部账户, 无签名)
ADDR_HEX_LEN = 16

TMP_DIR.mkdir(exist_ok=True)


# ---------------- 配置读写 ----------------
def default_config() -> dict:
    return {
        "difficulty": DIFFICULTY,
        "miner_reward": MINER_REWARD,
        "webui_host": "127.0.0.1",
        "webui_port": 8222,
        "webui_allow_lan": False,   # 显式开才允许0.0.0.0监听(局域网/公网)
        "mempool_max": 200,
        "log_level": "INFO",
    }


def load_config() -> dict:
    cfg = default_config()
    if CONFIG_FILE.exists():
        try:
            user_cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
            cfg.update(user_cfg)
        except Exception:
            pass
    # 安全校验
    cfg["difficulty"] = max(1, int(cfg.get("difficulty", DIFFICULTY)))
    cfg["miner_reward"] = max(0, int(cfg.get("miner_reward", MINER_REWARD)))
    # 默认锁127.0.0.1; 只有显式 webui_allow_lan=true 才允许0.0.0.0/局域网IP
    if not cfg.get("webui_allow_lan"):
        cfg["webui_host"] = "127.0.0.1"
    elif cfg.get("webui_host") in ("127.0.0.1", "localhost", "0.0.0.0"):
        pass  # 显式放行
    else:
        # 用户填了具体局域网IP, 也允许
        pass
    return cfg


# ---------------- SQLite ----------------
def init_db() -> sqlite3.Connection:
    """打开 chain.db, 建表(缺则建), 开启安全PRAGMA"""
    conn = sqlite3.connect(DB_FILE)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.executescript("""
    CREATE TABLE IF NOT EXISTS blocks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        height INTEGER NOT NULL UNIQUE,
        prev_hash TEXT NOT NULL,
        block_hash TEXT NOT NULL UNIQUE,
        nonce INTEGER NOT NULL,
        timestamp INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_height ON blocks(height);
    CREATE INDEX IF NOT EXISTS idx_blockhash ON blocks(block_hash);

    CREATE TABLE IF NOT EXISTS transactions (
        tx_id INTEGER PRIMARY KEY AUTOINCREMENT,
        block_height INTEGER NOT NULL,
        sender TEXT NOT NULL,
        receiver TEXT NOT NULL,
        amount INTEGER NOT NULL,
        nonce INTEGER,
        signature TEXT NOT NULL,
        public_key TEXT,
        timestamp INTEGER NOT NULL,
        FOREIGN KEY(block_height) REFERENCES blocks(height)
    );
    CREATE INDEX IF NOT EXISTS idx_tx_sender ON transactions(sender);
    CREATE INDEX IF NOT EXISTS idx_tx_receiver ON transactions(receiver);

    CREATE TABLE IF NOT EXISTS mempool (
        mp_id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender TEXT NOT NULL,
        receiver TEXT NOT NULL,
        amount INTEGER NOT NULL,
        nonce INTEGER,
        signature TEXT NOT NULL,
        public_key TEXT,
        timestamp INTEGER NOT NULL,
        tx_hash TEXT NOT NULL UNIQUE
    );
    """)
    conn.commit()
    return conn


# ---------------- 地址 / 哈希 工具 ----------------
def is_valid_address(addr: str) -> bool:
    return (isinstance(addr, str) and len(addr) == ADDR_HEX_LEN
            and all(c in "0123456789abcdef" for c in addr))


def calc_block_hash(index, prev_hash, transactions, timestamp, nonce) -> str:
    raw = json.dumps({
        "index": index, "prev_hash": prev_hash,
        "transactions": transactions, "timestamp": timestamp, "nonce": nonce,
    }, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def calc_tx_hash(sender, receiver, amount, timestamp) -> str:
    return hashlib.sha256(f"{sender}|{receiver}|{amount}|{timestamp}".encode()).hexdigest()


def tx_for_hash(tx: dict) -> dict:
    """规范化交易字段用于区块哈希: SYSTEM交易无nonce/public_key, 普通交易含"""
    d = {"sender": tx["sender"], "receiver": tx["receiver"], "amount": tx["amount"],
         "signature": tx.get("signature", ""), "timestamp": tx["timestamp"]}
    if tx.get("public_key"):
        d["public_key"] = tx["public_key"]
    if tx.get("nonce") is not None:
        d["nonce"] = tx["nonce"]
    return d


# ---------------- 钱包 ----------------
class Wallet:
    def __init__(self, private_key_hex: str = None):
        if private_key_hex:
            self.sk = SigningKey.from_string(bytes.fromhex(private_key_hex), curve=SECP256k1)
        else:
            self.sk = SigningKey.generate(curve=SECP256k1)
        self.vk = self.sk.get_verifying_key()
        self.private_key_hex = self.sk.to_string().hex()
        self.public_key_hex = self.vk.to_string().hex()
        self.address = hashlib.sha256(self.vk.to_string()).hexdigest()[:ADDR_HEX_LEN]

    def sign(self, message: str) -> str:
        digest = hashlib.sha256(message.encode("utf-8")).digest()
        return self.sk.sign_digest(digest, sigencode=sigencode_string).hex()


def verify_signature(public_key_hex: str, message: str, signature_hex: str) -> bool:
    try:
        vk = VerifyingKey.from_string(bytes.fromhex(public_key_hex), curve=SECP256k1)
        digest = hashlib.sha256(message.encode("utf-8")).digest()
        return vk.verify_digest(bytes.fromhex(signature_hex), digest, sigdecode=sigdecode_string)
    except Exception:
        return False


def address_from_public_key(public_key_hex: str) -> str:
    return hashlib.sha256(bytes.fromhex(public_key_hex)).hexdigest()[:ADDR_HEX_LEN]


# ---------------- wallets.json 多钱包仓库 ----------------
def _wallet_data() -> dict:
    if WALLETS_FILE.exists():
        try:
            return json.loads(WALLETS_FILE.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {"current": -1, "wallets": []}


def _wallet_save(data: dict):
    tmp = WALLETS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, WALLETS_FILE)
    os.chmod(WALLETS_FILE, 0o600)


def create_wallet(name: str = "默认钱包") -> Wallet:
    data = _wallet_data()
    w = Wallet()
    data["wallets"].append({"name": name, "private_key_hex": w.private_key_hex,
                            "created_at": int(time.time())})
    data["current"] = len(data["wallets"]) - 1
    _wallet_save(data)
    return w


def import_wallet(private_key_hex: str, name: str = "导入钱包") -> Wallet:
    w = Wallet(private_key_hex)  # 非法私钥直接抛异常
    data = _wallet_data()
    for i, e in enumerate(data["wallets"]):
        if e["private_key_hex"] == w.private_key_hex:
            data["current"] = i
            _wallet_save(data)
            return w
    data["wallets"].append({"name": name, "private_key_hex": w.private_key_hex,
                            "created_at": int(time.time())})
    data["current"] = len(data["wallets"]) - 1
    _wallet_save(data)
    return w


def switch_wallet(index: int) -> Wallet:
    data = _wallet_data()
    wallets = data.get("wallets", [])
    if not (0 <= index < len(wallets)):
        raise ValueError("编号无效")
    data["current"] = index
    _wallet_save(data)
    return Wallet(wallets[index]["private_key_hex"])


def delete_wallet(index: int) -> dict:
    """删除指定钱包。返回 {deleted, remaining, current_index}"""
    data = _wallet_data()
    wallets = data.get("wallets", [])
    if not (0 <= index < len(wallets)):
        raise ValueError("编号无效")
    removed = wallets.pop(index)
    # 调整current指针
    cur = data.get("current", -1)
    if not wallets:
        cur = -1
    elif cur == index:
        cur = min(index, len(wallets) - 1)  # 删了当前钱包, 指向相邻的
    elif cur > index:
        cur = cur - 1  # 后面的钱包往前挪了一位
    data["current"] = cur
    _wallet_save(data)
    return {"deleted": removed.get("name", "钱包"), "remaining": len(wallets), "current_index": cur}


def get_current_wallet() -> Wallet:
    data = _wallet_data()
    cur = data.get("current", -1)
    wallets = data.get("wallets", [])
    if 0 <= cur < len(wallets):
        return Wallet(wallets[cur]["private_key_hex"])
    return None


def list_wallets(conn: sqlite3.Connection) -> list:
    """返回钱包列表(不含私钥) + 余额"""
    data = _wallet_data()
    out = []
    for i, e in enumerate(data.get("wallets", [])):
        try:
            w = Wallet(e["private_key_hex"])
        except Exception:
            continue
        out.append({
            "index": i, "address": w.address, "name": e.get("name", "钱包"), "icon": e.get("icon", "💰"),
            "balance": get_balance(conn, w.address),
            "is_current": (i == data.get("current", -1)),
        })
    return out


# ---------------- 交易安全校验 ----------------
def validate_tx_fields(tx: dict) -> str:
    """字段级校验, 返回错误信息; 空串=通过"""
    for key in ("sender", "receiver", "amount", "signature", "timestamp", "public_key", "nonce"):
        if key not in tx:
            return f"缺少字段: {key}"
    if not is_valid_address(tx["sender"]):
        return "发送方地址非法"
    if not is_valid_address(tx["receiver"]):
        return "接收方地址非法"
    if not isinstance(tx["amount"], int) or tx["amount"] <= 0:
        return "金额必须为正整数"
    if tx["sender"] == tx["receiver"]:
        return "禁止转账给自己"
    if not isinstance(tx["signature"], str) or len(tx["signature"]) != 128:
        return "签名格式非法"
    return ""


def validate_tx_signature(tx: dict) -> str:
    """签名与公钥校验 (签名消息含nonce, 与历史链规则一致)"""
    if address_from_public_key(tx["public_key"]) != tx["sender"]:
        return "公钥与发送方地址不匹配"
    msg = f"{tx['sender']}|{tx['receiver']}|{tx['amount']}|{tx['nonce']}|{tx['timestamp']}"
    if not verify_signature(tx["public_key"], msg, tx["signature"]):
        return "签名验证失败"
    return ""


def validate_tx(conn: sqlite3.Connection, tx: dict, mempool_max: int = 200) -> str:
    """全套交易校验, 返回错误信息; 空串=通过"""
    err = validate_tx_fields(tx)
    if err:
        return err
    err = validate_tx_signature(tx)
    if err:
        return err
    if get_balance(conn, tx["sender"]) < tx["amount"]:
        return "发送方余额不足"
    # 去重: 交易池已有相同哈希
    txh = calc_tx_hash(tx["sender"], tx["receiver"], tx["amount"], tx["timestamp"])
    cur = conn.cursor()
    cur.execute("SELECT 1 FROM mempool WHERE tx_hash=?", (txh,))
    if cur.fetchone():
        return "交易已存在于待打包池"
    cur.execute("SELECT COUNT(*) AS c FROM mempool")
    if cur.fetchone()["c"] >= mempool_max:
        return "待打包池已满"
    return ""


# ---------------- 链核心 ----------------
def get_balance(conn: sqlite3.Connection, address: str) -> int:
    cur = conn.cursor()
    cur.execute("SELECT COALESCE(SUM(amount),0) AS s FROM transactions WHERE receiver=? AND sender<>?",
                (address, address))
    inc = cur.fetchone()["s"]
    cur.execute("SELECT COALESCE(SUM(amount),0) AS s FROM transactions WHERE sender=? AND sender<>?",
                (address, address))
    out = cur.fetchone()["s"]
    return inc - out


def get_top_height(conn: sqlite3.Connection) -> int:
    cur = conn.cursor()
    cur.execute("SELECT COALESCE(MAX(height),-1) AS h FROM blocks")
    return cur.fetchone()["h"]


def ensure_genesis(conn: sqlite3.Connection, cfg: dict):
    """链为空则创建创世块"""
    if get_top_height(conn) >= 0:
        return
    genesis_tx = {
        "sender": "SYSTEM", "receiver": INITIAL_OWNER, "amount": INITIAL_SUPPLY,
        "signature": "GENESIS", "timestamp": int(time.time()),
    }
    txs = [genesis_tx]
    nonce, ts = 0, int(time.time())
    while True:
        h = calc_block_hash(0, "0", txs, ts, nonce)
        if h.startswith("0" * cfg["difficulty"]):
            break
        nonce += 1
    cur = conn.cursor()
    try:
        conn.execute("BEGIN;")
        cur.execute("INSERT INTO blocks(height, prev_hash, block_hash, nonce, timestamp) VALUES (?,?,?,?,?)",
                    (0, "0", h, nonce, ts))
        cur.execute("INSERT INTO transactions(block_height, sender, receiver, amount, signature, timestamp) VALUES (?,?,?,?,?,?)",
                    (0, "SYSTEM", INITIAL_OWNER, INITIAL_SUPPLY, "GENESIS", ts))
        conn.commit()
    except Exception:
        conn.rollback()
        raise


def add_transaction(conn: sqlite3.Connection, tx: dict, cfg: dict) -> str:
    """校验并入池, 返回错误信息; 空串=成功"""
    err = validate_tx(conn, tx, cfg.get("mempool_max", 200))
    if err:
        return err
    txh = calc_tx_hash(tx["sender"], tx["receiver"], tx["amount"], tx["timestamp"])
    cur = conn.cursor()
    try:
        conn.execute("BEGIN;")
        cur.execute("INSERT INTO mempool(sender, receiver, amount, nonce, signature, public_key, timestamp, tx_hash) VALUES (?,?,?,?,?,?,?,?)",
                    (tx["sender"], tx["receiver"], tx["amount"], tx["nonce"], tx["signature"], tx["public_key"], tx["timestamp"], txh))
        conn.commit()
        return ""
    except Exception as e:
        conn.rollback()
        return f"入池失败: {e}"


def get_mempool(conn: sqlite3.Connection) -> list:
    cur = conn.cursor()
    cur.execute("SELECT sender, receiver, amount, nonce, signature, public_key, timestamp FROM mempool ORDER BY mp_id")
    return [dict(r) for r in cur.fetchall()]


def mine_blocks(conn: sqlite3.Connection, count: int, miner_address: str, cfg: dict,
                interrupt=lambda: False) -> dict:
    """打包挖矿 count 个区块。miner_address 需为合法16位地址。
    返回 {mined, errors, balances}"""
    if not is_valid_address(miner_address):
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
        hash_txs = [tx_for_hash(t) for t in txs]  # 规范化字段后计算哈希(与历史规则一致)
        while True:
            if interrupt():
                return {"mined": mined, "error": None}
            h = calc_block_hash(height, prev_hash, hash_txs, ts, nonce)
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


def get_tx_history(conn: sqlite3.Connection, address: str) -> dict:
    """地址转账记录: 链上 + 待打包"""
    if not is_valid_address(address):
        return {"error": "地址非法"}
    cur = conn.cursor()
    cur.execute("""SELECT t.block_height, t.sender, t.receiver, t.amount, t.timestamp
                   FROM transactions t WHERE t.sender=? OR t.receiver=?
                   ORDER BY t.block_height DESC LIMIT 200""", (address, address))
    confirmed = [dict(r) for r in cur.fetchall()]
    cur.execute("""SELECT sender, receiver, amount, timestamp FROM mempool
                   WHERE sender=? OR receiver=?""", (address, address))
    pending = [dict(r) for r in cur.fetchall()]
    return {"confirmed": confirmed, "pending": pending}


def validate_chain(conn: sqlite3.Connection, cfg: dict) -> (bool, str):
    """整链完整性校验"""
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
        h = calc_block_hash(b["height"], b["prev_hash"], [tx_for_hash(t) for t in txs],
                            b["timestamp"], b["nonce"])
        if h != b["block_hash"]:
            return False, f"区块#{b['height']} 哈希被篡改"
        prev_hash = b["block_hash"]
    return True, "整条链完整"


# ---------------- 迁移工具 ----------------
def migrate_from_json(conn: sqlite3.Connection, json_path) -> dict:
    """旧 chain.json → chain.db。返回统计或错误"""
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


# ---------------- 自测 ----------------
def selftest():
    print("=== XiaoHeiBit core.py 自测 ===")
    conn = init_db()
    cfg = load_config()
    ensure_genesis(conn, cfg)
    print("创世块高度:", get_top_height(conn))

    w1 = create_wallet("测试一号")
    w2 = create_wallet("测试二号")
    print("w1:", w1.address, "| w2:", w2.address)

    # 给w1注资(系统交易)
    funding = {"sender": "SYSTEM", "receiver": w1.address, "amount": 5000,
               "signature": "FUND", "timestamp": int(time.time())}
    # 系统注资直接写入待打包池, 再挖矿
    cur = conn.cursor()
    conn.execute("BEGIN;")
    cur.execute("INSERT INTO mempool(sender, receiver, amount, nonce, signature, public_key, timestamp, tx_hash) VALUES (?,?,?,?,?,?,?,?)",
                ("SYSTEM", w1.address, 5000, None, "FUND", None, funding["timestamp"],
                 calc_tx_hash("SYSTEM", w1.address, 5000, funding["timestamp"])))
    conn.commit()

    r = mine_blocks(conn, 1, w1.address, cfg)
    print("挖矿结果:", r, "| w1余额:", get_balance(conn, w1.address))

    # 签名转账 (签名消息含nonce, 与历史规则一致)
    ts = int(time.time())
    nonce = 7
    tx = {"sender": w1.address, "receiver": w2.address, "amount": 100, "nonce": nonce,
          "signature": w1.sign(f"{w1.address}|{w2.address}|100|{nonce}|{ts}"),
          "public_key": w1.public_key_hex, "timestamp": ts}
    err = add_transaction(conn, tx, cfg)
    print("交易入池:", "成功" if err == "" else f"失败:{err}")
    mine_blocks(conn, 1, w1.address, cfg)
    print("转账后 w1:", get_balance(conn, w1.address), "w2:", get_balance(conn, w2.address))

    # 非法交易测试
    bad = dict(tx)
    bad["signature"] = "00" * 64
    print("伪造签名拦截:", "通过" if add_transaction(conn, bad, cfg) else "❌未拦截!")

    ok, msg = validate_chain(conn, cfg)
    print("链校验:", msg)

    print("转账记录(w1):", get_tx_history(conn, w1.address)["confirmed"][:2])
    conn.close()
    print("=== 自测结束 ===")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    else:
        conn = init_db()
        cfg = load_config()
        ensure_genesis(conn, cfg)
        conn.close()
        print(f"chain.db 就绪 | 难度={cfg['difficulty']} | 奖励={cfg['miner_reward']} XHbit/块")
