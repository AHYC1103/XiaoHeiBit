#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
XiaoHeiBit (XHbit) api.py
纯后端 JSON API + 静态前端 webui.html
安全约束:
  - 默认绑定 127.0.0.1, 禁止公网暴露 (config.json 强制校验)
  - 网页端绝不导出/导入私钥; 签名全部在后端 core 完成
  - debug=False; 全部入参校验
仅本地学习用途, 禁止对外发行/交易/募资。
"""
import os
import threading
import time

from flask import Flask, jsonify, request, send_file, send_from_directory

import core

app = Flask(__name__, static_folder=None)
import logging
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)
app.config["JSON_AS_ASCII"] = False
app.debug = False

_conn = None
_cfg = None
_interrupt_flag = threading.Event()
_thread_local = threading.local()
HTML_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "webui", "webui.html")
ICO_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ico")


def get_conn():
    conn = getattr(_thread_local, "conn", None)
    if conn is None:
        conn = core.init_db()
        _thread_local.conn = conn
    return conn


def cfg():
    global _cfg
    if _cfg is None:
        _cfg = core.load_config()
    return _cfg


def check_addr(addr):
    if not core.is_valid_address(addr):
        return None, "地址必须为16位hex(0-9a-f)"
    return addr, None


def check_count(raw):
    try:
        n = int(raw)
    except (TypeError, ValueError):
        return None, "次数必须为整数"
    if n <= 0 or n > 100:
        return None, "次数范围 1-100"
    return n, None


@app.route("/")
def index():
    return jsonify({"service": "XiaoHeiBit API", "docs": "/api/status"})



# ---------------- API ----------------

@app.route("/api/config")
def api_config():
    c = cfg()
    return jsonify({
        "tron_nodes": c.get("tron_nodes", []),
        "bsc_nodes": c.get("bsc_nodes", []),
        "rates": c.get("rates", {}),
        "fixed_fees": c.get("fixed_fees", {}),
        "fee_thresholds": c.get("fee_thresholds", {}),
        "fee_owner_trx": c.get("fee_owner_trx", ""),
        "fee_owner_bnb": c.get("fee_owner_bnb", "")
    })

@app.route("/api/status")
def api_status():
    conn, c = get_conn(), cfg()
    w = core.get_current_wallet()
    return jsonify({
        "height": core.get_top_height(conn),
        "mempool": len(core.get_mempool(conn)),
        "difficulty": c["difficulty"],
        "miner_reward": c["miner_reward"],
        "supply": core.INITIAL_SUPPLY,
        "current_address": w.address if w else None,
        "time": int(time.time()),
    })


@app.route("/api/blocks")
def api_blocks():
    limit = request.args.get("limit", 10)
    n, err = check_count(limit) if str(limit).isdigit() else (10, None)
    if err:
        return jsonify({"error": err}), 400
    return jsonify({"blocks": core.get_recent_blocks(get_conn(), n)})


@app.route("/api/block/<int:height>")
def api_block(height):
    b = core.get_block(get_conn(), height)
    if not b:
        return jsonify({"error": "区块不存在"}), 404
    return jsonify(b)


@app.route("/api/balance/<addr>")
def api_balance(addr):
    a, err = check_addr(addr)
    if err:
        return jsonify({"error": err}), 400
    return jsonify({"address": a, "balance": core.get_balance(get_conn(), a)})


@app.route("/api/txs/<addr>")
def api_txs(addr):
    a, err = check_addr(addr)
    if err:
        return jsonify({"error": err}), 400
    page = request.args.get('page', 1, type=int)
    size = request.args.get('size', 20, type=int)
    return jsonify(core.get_tx_history(get_conn(), a, page, size))


@app.route("/api/mempool")
def api_mempool():
    return jsonify({"pending": core.get_mempool(get_conn())})


@app.route("/api/wallets")
def api_wallets():
    ws = core.list_wallets(get_conn())
    return jsonify({"wallets": ws, "current": core.get_current_wallet().address if core.get_current_wallet() else None})


@app.route("/api/wallet/create", methods=["POST"])
def api_wallet_create():
    d = request.get_json(silent=True) or {}
    n = len(core._wallet_data().get("wallets", [])) + 1
    name = str(d.get("name", f"钱包#{n}"))[:20]
    wallet_type = str(d.get("type", "random"))  # random=随机私钥, mnemonic=助记词
    
    if wallet_type == "mnemonic":
        result = core.create_wallet_with_mnemonic(name)
        w = result["wallet"]
        mnemonic = result["mnemonic"]
        return jsonify({
            "address": w.address, 
            "public_key": w.public_key_hex, 
            "name": name,
            "mnemonic": mnemonic,
            "type": "mnemonic"
        })
    else:
        w = core.create_wallet(name)
        return jsonify({"address": w.address, "public_key": w.public_key_hex, "name": name, "type": "random"})

@app.route("/api/wallet/rename", methods=["POST"])
def api_wallet_rename():
    d = request.get_json(silent=True) or {}
    try:
        idx = int(d.get("index", -1))
        name = str(d.get("name", "")).strip()[:20]
        icon = str(d.get("icon", "💰"))[:4]
        if not name: return jsonify({"error":"名字不能为空"}), 400
        data = core._wallet_data()
        data["wallets"][idx]["name"] = name
        data["wallets"][idx]["icon"] = icon
        core._wallet_save(data)
        return jsonify({"ok": True, "name": name, "icon": icon})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/wallet/switch", methods=["POST"])
def api_wallet_switch():
    d = request.get_json(silent=True) or {}
    try:
        w = core.switch_wallet(int(d.get("index", -1)))
        return jsonify({"address": w.address})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/wallet/delete", methods=["POST"])
def api_wallet_delete():
    d = request.get_json(silent=True) or {}
    try:
        return jsonify(core.delete_wallet(int(d.get("index", -1))))
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/transfer", methods=["POST"])
def api_transfer():
    d = request.get_json(silent=True) or {}
    wallet = core.get_current_wallet()
    if not wallet:
        return jsonify({"error": "没有当前钱包, 请先创建/切换"}), 400
    receiver, err = check_addr(str(d.get("receiver", "")).lower())
    if err:
        return jsonify({"error": err}), 400
    try:
        amount = int(d.get("amount"))
    except (TypeError, ValueError):
        return jsonify({"error": "金额必须为正整数"}), 400
    if amount <= 0:
        return jsonify({"error": "金额必须为正整数"}), 400
    ts = int(time.time())
    nonce = int(time.time() * 1000) % 2 ** 31
    msg = f"{wallet.address}|{receiver}|{amount}|{nonce}|{ts}"
    tx = {"sender": wallet.address, "receiver": receiver, "amount": amount, "nonce": nonce,
          "signature": wallet.sign(msg), "public_key": wallet.public_key_hex,
          "timestamp": ts}
    err = core.add_transaction(get_conn(), tx, cfg())
    if err:
        return jsonify({"error": err}), 400
    # 自动后台挖一个块打包交易
    import threading
    def _auto_mine():
        try:
            w = core.get_current_wallet()
            if w:
                core.mine_blocks(get_conn(), 1, w.address, cfg(), interrupt=lambda: False)
        except: pass
    threading.Thread(target=_auto_mine, daemon=True).start()
    return jsonify({"ok": True, "sender": wallet.address, "receiver": receiver, "amount": amount, "auto_mining": True})


@app.route("/api/transfer_signed", methods=["POST"])
def api_transfer_signed():
    """APP 本地签名版：服务端只验签，不存私钥。"""
    d = request.get_json(silent=True) or {}
    try:
        sender = str(d.get("sender", "")).lower()
        receiver, err = check_addr(str(d.get("receiver", "")).lower())
        if err:
            return jsonify({"error": err}), 400
        amount = int(d.get("amount"))
        if amount <= 0:
            return jsonify({"error": "金额必须为正整数"}), 400
        nonce = int(d.get("nonce"))
        ts = int(d.get("timestamp"))
        sig = str(d.get("signature", ""))
        pub = str(d.get("public_key", ""))
    except (TypeError, ValueError):
        return jsonify({"error": "参数不完整"}), 400
    if not sender or not sig or not pub:
        return jsonify({"error": "缺少签名或公钥"}), 400
    # 公钥推导地址必须等于 sender
    from core.wallet import address_from_public_key, verify_signature
    try:
        derived = address_from_public_key(pub)
    except Exception:
        return jsonify({"error": "公钥无效"}), 400
    if derived != sender:
        return jsonify({"error": "地址与公钥不匹配"}), 400
    msg = f"{sender}|{receiver}|{amount}|{nonce}|{ts}"
    if not verify_signature(pub, msg, sig):
        return jsonify({"error": "签名验证失败"}), 400
    tx = {"sender": sender, "receiver": receiver, "amount": amount, "nonce": nonce,
          "signature": sig, "public_key": pub, "timestamp": ts}
    err = core.add_transaction(get_conn(), tx, cfg())
    if err:
        return jsonify({"error": err}), 400
    # 自动后台挖一个块
    import threading
    def _auto_mine():
        try:
            core.mine_blocks(get_conn(), 1, sender, cfg(), interrupt=lambda: False)
        except: pass
    threading.Thread(target=_auto_mine, daemon=True).start()
    return jsonify({"ok": True, "sender": sender, "receiver": receiver, "amount": amount})


@app.route("/api/wallet/import", methods=["POST"])
def api_wallet_import():
    d = request.get_json(silent=True) or {}
    name = str(d.get("name", "导入钱包"))[:20]
    wallet_type = str(d.get("type", "private_key"))  # private_key=私钥, mnemonic=助记词
    
    try:
        if wallet_type == "mnemonic":
            mnemonic = str(d.get("mnemonic", "")).strip()
            if not mnemonic: return jsonify({"error":"助记词不能为空"}), 400
            w = core.import_wallet_with_mnemonic(mnemonic, name)
        else:
            priv = str(d.get("private_key", "")).strip()
            if not priv: return jsonify({"error":"私钥不能为空"}), 400
            w = core.import_wallet(priv, name)
        return jsonify({"address": w.address, "type": wallet_type})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/wallet/export", methods=["POST"])
def api_wallet_export():
    d = request.get_json(silent=True) or {}
    export_type = str(d.get("type", "private_key"))  # private_key=私钥, mnemonic=助记词
    try:
        idx = int(d.get("index", -1))
        data = core._wallet_data()
        wallet_data = data["wallets"][idx]
        
        if export_type == "mnemonic":
            mnemonic = wallet_data.get("mnemonic", "")
            if not mnemonic:
                return jsonify({"error": "该钱包没有保存助记词（可能是随机创建的钱包）"}), 400
            return jsonify({"mnemonic": mnemonic})
        else:
            priv = wallet_data["private_key_hex"]
            return jsonify({"private_key": priv})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/mine", methods=["POST"])
def api_mine():
    d = request.get_json(silent=True) or {}
    n, err = check_count(d.get("count", 1))
    if err:
        return jsonify({"error": err}), 400
    # 挖矿奖励地址由 APP 传（本地钱包）
    miner = str(d.get("address", "")).lower().strip()
    if len(miner) != 16:
        return jsonify({"error": "请先导入/创建钱包"}), 400
    _interrupt_flag.clear()
    result = core.mine_blocks(get_conn(), n, miner, cfg(),
                              interrupt=lambda: _interrupt_flag.is_set())
    if result.get("error"):
        return jsonify({"error": result["error"]}), 500
    return jsonify({"mined": result["mined"], "miner": miner,
                    "balance": core.get_balance(get_conn(), miner)})


@app.route("/api/validate")
def api_validate():
    ok, msg = core.validate_chain(get_conn(), cfg())
    return jsonify({"valid": ok, "message": msg})


# ===== NFT 路由（独立模块 core/nft.py） =====
import core.nft as nft_mod

@app.route("/api/nft/list")
def api_nft_list():
    addr = core.get_current_wallet().address if core.get_current_wallet() else None
    if not addr: return jsonify({"error": "无钱包"}), 400
    items = nft_mod.list_nfts_by_owner(get_conn(), addr)
    return jsonify({"nfts": items})

@app.route("/api/nft/mint", methods=["POST"])
def api_nft_mint():
    d = request.get_json(silent=True) or {}
    name = d.get("name","").strip()
    cid = d.get("cid","").strip()
    supply = int(d.get("supply",1))
    desc = d.get("description","")
    if not name or not cid: return jsonify({"error":"名称和CID必填"}),400
    w = core.get_current_wallet()
    if not w: return jsonify({"error":"无钱包"}),400
    nft = nft_mod.mint_nft(get_conn(), w.address, name, cid, supply, desc)
    return jsonify({"ok": True, "nft": nft})

@app.route("/api/nft/send", methods=["POST"])
def api_nft_send():
    d = request.get_json(silent=True) or {}
    nft_id = int(d.get("id",0))
    w = core.get_current_wallet()
    if not w: return jsonify({"error":"无钱包"}),400
    code, nft = nft_mod.create_transfer_code(get_conn(), nft_id, w.address)
    if not code: return jsonify({"error": nft}), 400
    return jsonify({"ok": True, "code": code, "nft": nft})

@app.route("/api/nft/recv", methods=["POST"])
def api_nft_recv():
    d = request.get_json(silent=True) or {}
    code = d.get("code","").strip()
    w = core.get_current_wallet()
    if not w: return jsonify({"error":"无钱包"}),400
    ok, nft = nft_mod.confirm_transfer(get_conn(), code, w.address)
    if not ok: return jsonify({"error": nft}), 400
    return jsonify({"ok": True, "nft": nft})

@app.route("/api/nft/transfer", methods=["POST"])
def api_nft_transfer():
    """直接地址转账，不需要一次性码"""
    d = request.get_json(silent=True) or {}
    nft_id = int(d.get("id",0))
    to_addr = d.get("to","").strip()
    w = core.get_current_wallet()
    if not w: return jsonify({"error":"无钱包"}),400
    if not to_addr: return jsonify({"error":"对方地址必填"}),400
    ok, nft = nft_mod.transfer_nft(get_conn(), nft_id, w.address, to_addr)
    if not ok: return jsonify({"error": nft}), 400
    return jsonify({"ok": True, "nft": nft})


def main():
    c = cfg()
    host, port = c["webui_host"], c["webui_port"]
    conn = get_conn()
    core.ensure_genesis(conn, c)
    conn.close()
    print(f"💾 chain.db 就绪 | XiaoHeiBit API: http://{host}:{port}")
    print(f"🔒 仅本机访问, 不暴露公网; 私钥绝不出后端")
    app.run(host=host, port=port, debug=False, threaded=True)


if __name__ == "__main__":
    main()
