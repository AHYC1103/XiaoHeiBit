# -*- coding: utf-8 -*-
"""交易：校验、签名验证、入池"""
import sqlite3
from . import utils
from .wallet import verify_signature, address_from_public_key
from .blockchain import get_balance


def validate_tx_fields(tx: dict) -> str:
    for key in ("sender", "receiver", "amount", "signature", "timestamp", "public_key", "nonce"):
        if key not in tx:
            return f"缺少字段: {key}"
    if not utils.is_valid_address(tx["sender"]):
        return "发送方地址非法"
    if not utils.is_valid_address(tx["receiver"]):
        return "接收方地址非法"
    if not isinstance(tx["amount"], (int, float)) or tx["amount"] <= 0:
        return "金额必须为正数"
    if tx["sender"] == tx["receiver"]:
        return "禁止转账给自己"
    if not isinstance(tx["signature"], str) or len(tx["signature"]) != 128:
        return "签名格式非法"
    return ""


def validate_tx_signature(tx: dict) -> str:
    if address_from_public_key(tx["public_key"]) != tx["sender"]:
        return "公钥与发送方地址不匹配"
    msg = f"{tx['sender']}|{tx['receiver']}|{'%.16f' % tx['amount']}|{tx['nonce']}|{tx['timestamp']}"
    if not verify_signature(tx["public_key"], msg, tx["signature"]):
        return "签名验证失败"
    return ""


def validate_tx(conn: sqlite3.Connection, tx: dict, mempool_max: int = 200) -> str:
    err = validate_tx_fields(tx)
    if err:
        return err
    err = validate_tx_signature(tx)
    if err:
        return err
    if get_balance(conn, tx["sender"]) < tx["amount"]:
        return "发送方余额不足"
    txh = utils.calc_tx_hash(tx["sender"], tx["receiver"], tx["amount"], tx["timestamp"])
    cur = conn.cursor()
    cur.execute("SELECT 1 FROM mempool WHERE tx_hash=?", (txh,))
    if cur.fetchone():
        return "交易已存在于待打包池"
    cur.execute("SELECT COUNT(*) AS c FROM mempool")
    if cur.fetchone()["c"] >= mempool_max:
        return "待打包池已满"
    return ""


def add_transaction(conn: sqlite3.Connection, tx: dict, cfg: dict) -> str:
    err = validate_tx(conn, tx, cfg.get("mempool_max", 200))
    if err:
        return err
    txh = utils.calc_tx_hash(tx["sender"], tx["receiver"], tx["amount"], tx["timestamp"])
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
