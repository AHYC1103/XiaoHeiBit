"""
兑换池模块：TRX/BNB → XHB 收15%手续费进池子，XHB → TRX/BNB 从池子自动转出
兑换池有独立钱包，私钥在配置文件里
"""
import json
import os
import time
from .config import BASE_DIR

POOL_FILE = os.path.join(BASE_DIR, "swap_pool.json")
SWAP_CONFIG_FILE = os.path.join(BASE_DIR, "swap_config.json")

# 兑换费率
BUY_FEE_PCT = 15  # 买入时手续费15%进池子
SELL_FEE_PCT = 5   # 卖出时手续费5%（从到账里扣）
MAX_SELL_PCT = 5   # 单次最多兑换池子的5%

# XHB汇率（1 XHB = 1 CNY，TRX/BNB按人民币价换算）
XHB_PER_TRX = 2.31    # 1 TRX = 2.31 XHB
XHB_PER_BNB = 5344.65  # 1 BNB = 5344.65 XHB

# 兑换池钱包配置
DEFAULT_POOL_WALLET = {
    "trx_address": "TYQs7e3jbjAUpvrM6CnukPs5UFifrbvE81",
    "trx_private_key": "c617c8fa689065718f6660cc3594a2e83c75f2a9791d00a65ba6dc77a6cae93a",
    "bnb_address": "0x66781Ac88f5554CA569fB23c537655F81810b917",
    "bnb_private_key": "667de0273725a16a62a5fb3b5da3fa4c48eb30988c75f444a73ef091c0f1305f",
}


def _load_pool():
    if not os.path.exists(POOL_FILE):
        return {"trx": 0.0, "bnb": 0.0, "total_earned": 0.0}
    with open(POOL_FILE, "r") as f:
        return json.load(f)


def _save_pool(pool):
    with open(POOL_FILE, "w") as f:
        json.dump(pool, f, indent=2)


def _load_pool_wallet():
    if os.path.exists(SWAP_CONFIG_FILE):
        with open(SWAP_CONFIG_FILE, "r") as f:
            return json.load(f)
    # 首次运行写入默认配置
    with open(SWAP_CONFIG_FILE, "w") as f:
        json.dump(DEFAULT_POOL_WALLET, f, indent=2)
    return DEFAULT_POOL_WALLET


def get_pool():
    return _load_pool()


def get_pool_wallet():
    return _load_pool_wallet()


def add_fee(coin: str, amount: float):
    """买入时收手续费进池子（记账，实际币已在兑换池地址）"""
    pool = _load_pool()
    if coin == "TRX":
        pool["trx"] += amount
    elif coin == "BNB":
        pool["bnb"] += amount
    pool["total_earned"] += amount
    _save_pool(pool)


def get_quote(from_coin: str, to_coin: str, amount: float) -> dict:
    """获取兑换报价"""
    pool = _load_pool()

    if from_coin == "XHB" and to_coin == "TRX":
        trx_amount = amount / XHB_PER_TRX
        fee = trx_amount * SELL_FEE_PCT / 100
        receive = trx_amount - fee
        pool_bal = pool["trx"]
        max_sell = pool_bal * MAX_SELL_PCT / 100
        return {
            "ok": True,
            "receive": round(receive, 6),
            "fee": round(fee, 6),
            "pool_balance": round(pool_bal, 6),
            "max_sell": round(max_sell, 6),
            "can_sell": receive <= max_sell and pool_bal >= receive,
        }
    elif from_coin == "XHB" and to_coin == "BNB":
        bnb_amount = amount / XHB_PER_BNB
        fee = bnb_amount * SELL_FEE_PCT / 100
        receive = bnb_amount - fee
        pool_bal = pool["bnb"]
        max_sell = pool_bal * MAX_SELL_PCT / 100
        return {
            "ok": True,
            "receive": round(receive, 8),
            "fee": round(fee, 8),
            "pool_balance": round(pool_bal, 8),
            "max_sell": round(max_sell, 8),
            "can_sell": receive <= max_sell and pool_bal >= receive,
        }
    elif from_coin == "TRX" and to_coin == "XHB":
        fee = amount * BUY_FEE_PCT / 100
        effective = amount - fee
        xhb = effective * XHB_PER_TRX
        return {
            "ok": True,
            "receive": round(xhb, 4),
            "fee": round(fee, 6),
            "fee_pct": BUY_FEE_PCT,
        }
    elif from_coin == "BNB" and to_coin == "XHB":
        fee = amount * BUY_FEE_PCT / 100
        effective = amount - fee
        xhb = effective * XHB_PER_BNB
        return {
            "ok": True,
            "receive": round(xhb, 4),
            "fee": round(fee, 8),
            "fee_pct": BUY_FEE_PCT,
        }
    return {"ok": False, "error": "不支持的兑换对"}


def _send_trx(to_address: str, amount: float) -> tuple:
    """从兑换池转TRX给用户（纯requests+ecdsa实现，零外部依赖）"""
    try:
        import requests
        import ecdsa
        import hashlib
        import base58
        from ecdsa.util import sigdecode_string
        from ecdsa.ellipticcurve import Point
        from ecdsa.numbertheory import inverse_mod

        # 纯Python Keccak256（不依赖eth-utils）
        def _keccak256(data: bytes) -> bytes:
            # 精简版Keccak-256
            RC = [
                0x0000000000000001, 0x0000000000008082, 0x800000000000808A, 0x8000000080008000,
                0x000000000000808B, 0x0000000080000001, 0x8000000080008081, 0x8000000000008009,
                0x000000000000008A, 0x0000000000000088, 0x0000000080008009, 0x000000008000000A,
                0x000000008000808B, 0x800000000000008B, 0x8000000000008089, 0x8000000000008003,
                0x8000000000008002, 0x8000000000000080, 0x000000000000800A, 0x800000008000000A,
                0x8000000080008081, 0x8000000000008080, 0x0000000080000001, 0x8000000080008008,
            ]
            r = [[0,36,3,41,18],[1,44,10,45,2],[62,6,43,15,61],[28,55,25,21,56],[27,20,39,8,14]]
            def rol(x, s):
                return ((x << s) | (x >> (64-s))) & 0xFFFFFFFFFFFFFFFF
            state = [[0]*5 for _ in range(5)]
            # padding
            data += b'\x01'
            while len(data) % 136 != 0:
                data += b'\x00'
            data = data[:-1] + b'\x80'
            for offset in range(0, len(data), 136):
                block = data[offset:offset+136]
                for i in range(136//8):
                    lane = int.from_bytes(block[i*8:(i+1)*8], 'little')
                    x, y = i % 5, i // 5
                    state[x][y] ^= lane
                for _round in range(24):
                    C = [state[x][0]^state[x][1]^state[x][2]^state[x][3]^state[x][4] for x in range(5)]
                    D = [C[(x-1)%5]^rol(C[(x+1)%5],1) for x in range(5)]
                    for x in range(5):
                        for y in range(5):
                            state[x][y] ^= D[x]
                    B = [[0]*5 for _ in range(5)]
                    for x in range(5):
                        for y in range(5):
                            B[y][(2*x+3*y)%5] = rol(state[x][y], r[x][y] % 64)
                    for x in range(5):
                        for y in range(5):
                            state[x][y] = B[x][y] ^ ((~B[(x+1)%5][y]) & B[(x+2)%5][y])
                    state[0][0] ^= RC[_round]
            out = b''
            for i in range(32//8):
                x, y = i % 5, i // 5
                out += state[x][y].to_bytes(8, 'little')
            return out

        wallet = _load_pool_wallet()
        owner_addr = wallet["trx_address"]
        priv_hex = wallet["trx_private_key"]

        def _addr2hex(addr):
            return base58.b58decode(addr)[:-4].hex()

        def _pub2addr(pub_bytes):
            addr_bytes = b"\x41" + _keccak256(pub_bytes)[-20:]
            checksum = hashlib.sha256(hashlib.sha256(addr_bytes).digest()).digest()[:4]
            return base58.b58encode(addr_bytes + checksum).decode()

        # 1. 创建交易
        resp = requests.post("https://api.trongrid.io/wallet/createtransaction", json={
            "owner_address": _addr2hex(owner_addr),
            "to_address": _addr2hex(to_address),
            "amount": int(amount * 1_000_000),
        }, timeout=15)
        tx = resp.json()
        if "txID" not in tx:
            return False, tx.get("Error", tx.get("message", "创建交易失败"))

        # 2. 签名
        sk = ecdsa.SigningKey.from_string(bytes.fromhex(priv_hex), curve=ecdsa.SECP256k1)
        tx_id = bytes.fromhex(tx["txID"])
        sig = sk.sign_deterministic(tx_id, hashfunc=hashlib.sha256)
        r_int, s_int = sigdecode_string(sig, ecdsa.SECP256k1.order)
        r_bytes = r_int.to_bytes(32, 'big')
        s_bytes = s_int.to_bytes(32, 'big')

        # 计算恢复ID v
        curve = ecdsa.SECP256k1
        G = curve.generator
        n = curve.order
        p = curve.curve.p()
        a = curve.curve.a()
        b = curve.curve.b()
        e = int.from_bytes(tx_id, 'big')
        v_found = 0
        for v in [0, 1]:
            try:
                alpha = (pow(r_int, 3, p) + a * r_int + b) % p
                beta = pow(alpha, (p + 1) // 4, p)
                y = beta if beta % 2 == v else p - beta
                R = Point(curve.curve, r_int, y, n)
                r_inv = inverse_mod(r_int, n)
                Q = r_inv * (s_int * R + (-e % n) * G)
                pub_bytes = Q.x().to_bytes(32, 'big') + Q.y().to_bytes(32, 'big')
                if _pub2addr(pub_bytes) == owner_addr:
                    v_found = v
                    break
            except:
                continue

        signature = (r_bytes + s_bytes + bytes([v_found])).hex()
        tx["signature"] = [signature]

        # 3. 广播
        resp = requests.post("https://api.trongrid.io/wallet/broadcasttransaction", json=tx, timeout=15)
        result = resp.json()
        if result.get("result"):
            return True, tx["txID"]
        return False, result.get("message", result.get("Error", "广播失败"))
    except Exception as e:
        return False, str(e)


def _send_bnb(to_address: str, amount: float) -> tuple:
    """从兑换池转BNB（pending，需手动转账，web3在Termux装不上）"""
    return False, "BNB自动转账暂未启用，请手动转账"


def execute_swap_xhb_to_trx(xhb_amount: float, user_trx_addr: str) -> dict:
    """执行XHB→TRX兑换，自动从兑换池转TRX"""
    quote = get_quote("XHB", "TRX", xhb_amount)
    if not quote.get("ok"):
        return quote
    if not quote.get("can_sell"):
        return {"ok": False, "error": f"池子余额不足或超过单次上限（最多{quote['max_sell']} TRX）"}

    # 自动转账
    ok, txid = _send_trx(user_trx_addr, quote["receive"])
    if not ok:
        return {"ok": False, "error": f"TRX转账失败: {txid}"}

    # 扣池子余额
    pool = _load_pool()
    pool["trx"] -= quote["receive"]
    _save_pool(pool)

    # 记录日志
    _log_swap("XHB_TO_TRX", xhb_amount, quote["receive"], user_trx_addr, txid)
    return {"ok": True, "receive": quote["receive"], "txid": txid, "msg": "兑换成功，TRX已转出"}


def execute_swap_xhb_to_bnb(xhb_amount: float, user_bnb_addr: str) -> dict:
    """执行XHB→BNB兑换，自动从兑换池转BNB"""
    quote = get_quote("XHB", "BNB", xhb_amount)
    if not quote.get("ok"):
        return quote
    if not quote.get("can_sell"):
        return {"ok": False, "error": f"池子余额不足或超过单次上限（最多{quote['max_sell']} BNB）"}

    ok, txid = _send_bnb(user_bnb_addr, quote["receive"])
    if not ok:
        return {"ok": False, "error": f"BNB转账失败: {txid}"}

    pool = _load_pool()
    pool["bnb"] -= quote["receive"]
    _save_pool(pool)

    _log_swap("XHB_TO_BNB", xhb_amount, quote["receive"], user_bnb_addr, txid)
    return {"ok": True, "receive": quote["receive"], "txid": txid, "msg": "兑换成功，BNB已转出"}


def _log_swap(swap_type: str, xhb_amount: float, receive_amount: float, user_addr: str, txid: str):
    log_file = os.path.join(BASE_DIR, "swap_log.json")
    logs = []
    if os.path.exists(log_file):
        with open(log_file, "r") as f:
            logs = json.load(f)
    logs.append({
        "time": int(time.time()),
        "type": swap_type,
        "xhb_amount": xhb_amount,
        "receive_amount": receive_amount,
        "user_addr": user_addr,
        "txid": txid,
        "status": "completed",
    })
    with open(log_file, "w") as f:
        json.dump(logs, f, indent=2)
