# -*- coding: utf-8 -*-
"""钱包：密钥对、签名、多钱包管理"""
import hashlib
import json
import os
import time
from ecdsa import SigningKey, VerifyingKey, SECP256k1
from ecdsa.util import sigencode_string, sigdecode_string
from mnemonic import Mnemonic
from . import config


# ===== BIP39 助记词支持 =====
def generate_mnemonic(strength=128):
    """生成12个单词的助记词（strength=128对应12个单词，256对应24个）"""
    mnemo = Mnemonic("english")
    return mnemo.generate(strength=strength)


def mnemonic_to_private_key(mnemonic_str: str, passphrase: str = ""):
    """助记词转私钥hex（简化版：直接用seed的前32字节作为私钥）"""
    mnemo = Mnemonic("english")
    if not mnemo.check(mnemonic_str):
        raise ValueError("助记词无效，请检查单词拼写")
    # 生成seed（PBKDF2，带盐"mnemonic"）
    seed = mnemo.to_seed(mnemonic_str, passphrase=passphrase)
    # 简化版：直接取seed前32字节作为私钥hex
    # 注意：正式版应该用BIP32 HD派生子私钥，这里简化先用直接转换
    private_key_hex = seed[:32].hex()
    return private_key_hex


class Wallet:
    def __init__(self, private_key_hex: str = None):
        if private_key_hex:
            self.sk = SigningKey.from_string(bytes.fromhex(private_key_hex), curve=SECP256k1)
        else:
            self.sk = SigningKey.generate(curve=SECP256k1)
        self.vk = self.sk.get_verifying_key()
        self.private_key_hex = self.sk.to_string().hex()
        self.public_key_hex = self.vk.to_string().hex()
        self.address = hashlib.sha256(self.vk.to_string()).hexdigest()[:config.ADDR_HEX_LEN]

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
    return hashlib.sha256(bytes.fromhex(public_key_hex)).hexdigest()[:config.ADDR_HEX_LEN]


def _wallet_data() -> dict:
    if config.WALLETS_FILE.exists():
        try:
            return json.loads(config.WALLETS_FILE.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {"current": -1, "wallets": []}


def _wallet_save(data: dict):
    tmp = config.WALLETS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, config.WALLETS_FILE)
    os.chmod(config.WALLETS_FILE, 0o600)


def create_wallet(name: str = "默认钱包") -> Wallet:
    data = _wallet_data()
    w = Wallet()
    data["wallets"].append({"name": name, "private_key_hex": w.private_key_hex,
                            "created_at": int(time.time())})
    data["current"] = len(data["wallets"]) - 1
    _wallet_save(data)
    return w


def create_wallet_with_mnemonic(name: str = "助记词钱包") -> dict:
    """生成新的助记词并创建钱包，返回钱包对象和助记词"""
    mnemo = generate_mnemonic(strength=128)  # 12个单词
    priv_hex = mnemonic_to_private_key(mnemo)
    w = Wallet(priv_hex)
    data = _wallet_data()
    data["wallets"].append({
        "name": name, 
        "private_key_hex": w.private_key_hex,
        "mnemonic": mnemo,  # 保存助记词
        "created_at": int(time.time())
    })
    data["current"] = len(data["wallets"]) - 1
    _wallet_save(data)
    return {"wallet": w, "mnemonic": mnemo}


def import_wallet_with_mnemonic(mnemonic_str: str, name: str = "导入的助记词钱包") -> Wallet:
    """用助记词导入钱包"""
    priv_hex = mnemonic_to_private_key(mnemonic_str.strip())
    w = Wallet(priv_hex)
    data = _wallet_data()
    # 检查是否已存在
    for i, e in enumerate(data["wallets"]):
        if e["private_key_hex"] == w.private_key_hex:
            data["current"] = i
            _wallet_save(data)
            return w
    # 新钱包
    data["wallets"].append({
        "name": name, 
        "private_key_hex": w.private_key_hex,
        "mnemonic": mnemonic_str.strip(),
        "created_at": int(time.time())
    })
    data["current"] = len(data["wallets"]) - 1
    _wallet_save(data)
    return w


def import_wallet(private_key_hex: str, name: str = "导入钱包") -> Wallet:
    w = Wallet(private_key_hex)
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
    data = _wallet_data()
    wallets = data.get("wallets", [])
    if not (0 <= index < len(wallets)):
        raise ValueError("编号无效")
    removed = wallets.pop(index)
    cur = data.get("current", -1)
    if not wallets:
        cur = -1
    elif cur == index:
        cur = min(index, len(wallets) - 1)
    elif cur > index:
        cur = cur - 1
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


def list_wallets(conn) -> list:
    from .blockchain import get_balance
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
