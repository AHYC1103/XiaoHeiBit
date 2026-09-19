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
HTML_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "webui.html")
ICO_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ico")


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
    return send_file(HTML_PATH)


@app.route("/ico/<path:filename>")
def serve_ico(filename):
    return send_from_directory(ICO_DIR, filename)

@app.route("/jsqr.js")
def serve_jsqr():
    return send_file("/data/data/com.termux/files/home/xiaoheibit/jsqr.js", mimetype="application/javascript")


# ---------------- API ----------------
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
    return jsonify(core.get_tx_history(get_conn(), a))


@app.route("/api/mempool")
def api_mempool():
    return jsonify({"pending": core.get_mempool(get_conn())})


@app.route("/api/wallets")
def api_wallets():
    ws = core.list_wallets(get_conn())
    return jsonify({"wallets": ws, "current": core.get_current_wallet().address if core.get_current_wallet() else None})


@app.route("/api/wallet/create", methods=["POST"])
def api_wallet_create():
    n = len(core._wallet_data().get("wallets", [])) + 1
    w = core.create_wallet(f"钱包#{n}")
    return jsonify({"address": w.address, "public_key": w.public_key_hex, "name": w.name})

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


@app.route("/api/wallet/import", methods=["POST"])
def api_wallet_import():
    d = request.get_json(silent=True) or {}
    priv = str(d.get("private_key", "")).strip()
    name = str(d.get("name", "导入钱包"))[:20]
    if not priv: return jsonify({"error":"私钥不能为空"}), 400
    try:
        w = core.import_wallet(priv, name)
        return jsonify({"address": w.address})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/wallet/export", methods=["POST"])
def api_wallet_export():
    d = request.get_json(silent=True) or {}
    try:
        idx = int(d.get("index", -1))
        data = core._wallet_data()
        priv = data["wallets"][idx]["private_key_hex"]
        return jsonify({"private_key": priv})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/mine", methods=["POST"])
def api_mine():
    d = request.get_json(silent=True) or {}
    n, err = check_count(d.get("count", 1))
    if err:
        return jsonify({"error": err}), 400
    wallet = core.get_current_wallet()
    if not wallet:
        return jsonify({"error": "没有当前钱包, 无法领取挖矿奖励"}), 400
    _interrupt_flag.clear()
    result = core.mine_blocks(get_conn(), n, wallet.address, cfg(),
                              interrupt=lambda: _interrupt_flag.is_set())
    if result.get("error"):
        return jsonify({"error": result["error"]}), 500
    return jsonify({"mined": result["mined"], "miner": wallet.address,
                    "balance": core.get_balance(get_conn(), wallet.address)})


@app.route("/api/validate")
def api_validate():
    ok, msg = core.validate_chain(get_conn(), cfg())
    return jsonify({"valid": ok, "message": msg})


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
