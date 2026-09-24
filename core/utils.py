# -*- coding: utf-8 -*-
"""地址与哈希工具"""
import hashlib
import json
from . import config


def is_valid_address(addr: str) -> bool:
    return (isinstance(addr, str) and len(addr) == config.ADDR_HEX_LEN
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
    d = {"sender": tx["sender"], "receiver": tx["receiver"], "amount": tx["amount"],
         "signature": tx.get("signature", ""), "timestamp": tx["timestamp"]}
    if tx.get("public_key"):
        d["public_key"] = tx["public_key"]
    if tx.get("nonce") is not None:
        d["nonce"] = tx["nonce"]
    return d
