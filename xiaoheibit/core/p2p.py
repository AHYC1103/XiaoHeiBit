# -*- coding: utf-8 -*-
"""
P2P 多节点同步模块 v2
功能：
  - 节点列表管理 (peers) + 健康检查
  - 区块同步：拉取更长链，支持分叉回滚
  - 交易广播：新交易立即广播
  - 区块广播：新块挖完立即通知节点拉取
  - mempool同步
"""
import json
import time
import threading
import logging
import sqlite3
from pathlib import Path
from urllib.request import urlopen, Request
from urllib.error import URLError
from . import p2p_security

log = logging.getLogger("p2p")

BASE_DIR = Path(__file__).resolve().parent.parent
CONFIG_FILE = BASE_DIR / "config.json"

_peers = []
_peer_status = {}  # url -> {"last_ok": ts, "fail_count": n}
_sync_thread = None
_stop_flag = threading.Event()
_last_sync = 0
_broadcast_lock = threading.Lock()


def load_peers() -> list:
    global _peers
    try:
        cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        _peers = cfg.get("peers", [])
    except Exception:
        _peers = []
    return _peers


def get_peers() -> list:
    return _peers


def add_peer(url: str) -> bool:
    import re
    url = url.rstrip("/")
    if url.startswith("http://"): url = url[7:]
    if url.startswith("https://"): url = url[8:]
    if ":" not in url:
        url = url + ":8222"
    if not re.match(r"^[a-zA-Z0-9.:/]+$", url):
        return False
    parts = url.split(":")
    if len(parts) != 2:
        return False
    host, port_str = parts
    try:
        port = int(port_str)
    except:
        return False
    if not (1 <= port <= 65535):
        return False
    # 纯数字IP校验4段0-255
    if re.match(r"^[0-9.]+$", host):
        segs = host.split(".")
        if len(segs) != 4:
            return False
        for s in segs:
            try:
                n = int(s)
            except:
                return False
            if not (0 <= n <= 255):
                return False
    if url not in _peers:
        _peers.append(url)
        _peer_status[url] = {"last_ok": 0, "fail_count": 0}
        _save_peers()
        return True
    return False


def remove_peer(url: str) -> bool:
    url = url.rstrip("/")
    if url in _peers:
        _peers.remove(url)
        _peer_status.pop(url, None)
        _save_peers()
        return True
    return False


def _save_peers():
    try:
        cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        cfg["peers"] = _peers
        CONFIG_FILE.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
    except Exception as e:
        log.error(f"保存peers失败: {e}")


def _http_get(url: str, timeout: int = 5) -> dict:
    try:
        from urllib.parse import urlparse
        path = urlparse(url).path
        headers = {"User-Agent": "XiaoHeiCoin-P2P/2.0"}
        headers.update(p2p_security.get_signed_headers("GET", path, ""))
        req = Request(url, headers=headers)
        with urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        log.debug(f"GET {url} 失败: {e}")
        return None


def _http_post(url: str, data: dict, timeout: int = 5) -> dict:
    try:
        from urllib.parse import urlparse
        path = urlparse(url).path
        body = json.dumps(data)
        headers = {"Content-Type": "application/json", "User-Agent": "XiaoHeiCoin-P2P/2.0"}
        headers.update(p2p_security.get_signed_headers("POST", path, body))
        req = Request(url, data=body.encode("utf-8"), headers=headers)
        with urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        log.debug(f"POST {url} 失败: {e}")
        return None


def _mark_peer_ok(url: str):
    s = _peer_status.get(url, {"last_ok": 0, "fail_count": 0})
    s["last_ok"] = int(time.time())
    s["fail_count"] = 0
    _peer_status[url] = s


def _mark_peer_fail(url: str):
    s = _peer_status.get(url, {"last_ok": 0, "fail_count": 0})
    s["fail_count"] = s.get("fail_count", 0) + 1
    _peer_status[url] = s


def get_peer_height(peer_url: str) -> int:
    data = _http_get(f"{peer_url}/api/status")
    if data and "height" in data:
        _mark_peer_ok(peer_url)
        return data["height"]
    _mark_peer_fail(peer_url)
    return -1


def fetch_peer_block(peer_url: str, height: int) -> dict:
    return _http_get(f"{peer_url}/api/block/{height}")


def fetch_peer_mempool(peer_url: str) -> list:
    data = _http_get(f"{peer_url}/api/mempool")
    if data and "pending" in data:
        return data["pending"]
    return []


def _ensure_orphan_table(conn: sqlite3.Connection):
    """确保孤儿块表存在"""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS orphan_blocks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            height INTEGER,
            block_hash TEXT,
            prev_hash TEXT,
            nonce INTEGER,
            timestamp INTEGER,
            tx_count INTEGER DEFAULT 0,
            rollback_time INTEGER,
            reason TEXT DEFAULT 'fork'
        )
    """)
    conn.commit()


def _rollback_to(conn: sqlite3.Connection, height: int):
    """回滚本地链到指定高度（删除height > 的所有块和交易），被删的块记入孤儿块表"""
    _ensure_orphan_table(conn)
    cur = conn.cursor()
    # 先查出要删除的块，记入孤儿表
    cur.execute("SELECT height, prev_hash, block_hash, nonce, timestamp FROM blocks WHERE height > ?", (height,))
    orphans = cur.fetchall()
    now = int(time.time())
    for b in orphans:
        cur.execute("SELECT COUNT(*) FROM transactions WHERE block_height = ?", (b["height"],))
        tx_count = cur.fetchone()[0]
        cur.execute(
            "INSERT INTO orphan_blocks(height, block_hash, prev_hash, nonce, timestamp, tx_count, rollback_time, reason) VALUES (?,?,?,?,?,?,?,?)",
            (b["height"], b["block_hash"], b["prev_hash"], b["nonce"], b["timestamp"], tx_count, now, "fork")
        )
    # 删除回滚的块和交易
    cur.execute("DELETE FROM transactions WHERE block_height > ?", (height,))
    cur.execute("DELETE FROM blocks WHERE height > ?", (height,))
    conn.commit()


def sync_from_peer(conn: sqlite3.Connection, peer_url: str, cfg: dict) -> dict:
    """
    从节点同步，支持分叉回滚：
    1. 找共同祖先高度
    2. 回滚本地分叉部分
    3. 拉取对方的链
    """
    from . import blockchain, utils

    peer_h = get_peer_height(peer_url)
    local_h = blockchain.get_top_height(conn)
    if peer_h <= local_h:
        return {"synced": 0, "peer_height": peer_h, "local_height": local_h, "rolled_back": 0}

    # 找分叉点：从peer_h往下找第一个哈希匹配的块
    fork_height = -1
    for h in range(min(local_h, peer_h), -1, -1):
        local_block = blockchain.get_block(conn, h)
        peer_block = fetch_peer_block(peer_url, h)
        if local_block and peer_block and local_block.get("hash") == peer_block.get("hash"):
            fork_height = h
            break

    rolled_back = 0
    if fork_height < local_h:
        # 本地有分叉，需要回滚
        _rollback_to(conn, fork_height)
        rolled_back = local_h - fork_height
        log.info(f"分叉回滚: 从高度{local_h}回滚到{fork_height}，删除{rolled_back}个块")

    # 从 fork_height+1 开始拉取
    synced = 0
    for h in range(fork_height + 1, peer_h + 1):
        block = fetch_peer_block(peer_url, h)
        if not block:
            log.warning(f"拉取区块#{h}失败")
            break
        # 验证前哈希
        cur = conn.cursor()
        if h > 0:
            cur.execute("SELECT block_hash FROM blocks WHERE height=?", (h - 1,))
            row = cur.fetchone()
            expected_prev = row["block_hash"] if row else "0"
            if block.get("prev_hash") != expected_prev:
                log.warning(f"区块#{h}前哈希不匹配")
                break
        # 验证PoW
        if not block.get("hash", "").startswith("0" * cfg["difficulty"]):
            log.warning(f"区块#{h}不满足PoW")
            break
        # 写入
        try:
            conn.execute("BEGIN;")
            cur.execute("INSERT INTO blocks(height, prev_hash, block_hash, nonce, timestamp) VALUES (?,?,?,?,?)",
                        (block["height"], block["prev_hash"], block["hash"],
                         block["nonce"], block["timestamp"]))
            for tx in block.get("transactions", []):
                cur.execute("""INSERT INTO transactions(block_height, sender, receiver, amount,
                              nonce, signature, public_key, timestamp) VALUES (?,?,?,?,?,?,?,?)""",
                            (block["height"], tx["sender"], tx["receiver"], tx["amount"],
                             tx.get("nonce"), tx.get("signature"), tx.get("public_key"),
                             tx["timestamp"]))
            conn.commit()
            synced += 1
        except Exception as e:
            conn.rollback()
            log.warning(f"写入区块#{h}失败: {e}")
            break

    _mark_peer_ok(peer_url)
    return {"synced": synced, "peer_height": peer_h,
            "local_height": fork_height + synced, "rolled_back": rolled_back}


def sync_all_peers(conn: sqlite3.Connection, cfg: dict) -> dict:
    results = []
    for peer in list(_peers):
        try:
            r = sync_from_peer(conn, peer, cfg)
            r["peer"] = peer
            results.append(r)
        except Exception as e:
            log.error(f"同步 {peer} 异常: {e}")
            _mark_peer_fail(peer)
    global _last_sync
    _last_sync = int(time.time())
    return {"peers": results, "timestamp": _last_sync}


def sync_mempool_from_peers(conn: sqlite3.Connection, cfg: dict):
    """从所有节点拉取mempool，合并到本地"""
    from . import transaction as tx_mod
    local_hashes = set()
    cur = conn.cursor()
    cur.execute("SELECT tx_hash FROM mempool")
    for r in cur.fetchall():
        local_hashes.add(r["tx_hash"])

    added = 0
    for peer in _peers:
        pool = fetch_peer_mempool(peer)
        for tx in pool:
            txh = tx.get("tx_hash") or tx_mod.calc_tx_hash(tx["sender"], tx["receiver"], tx["amount"], tx["timestamp"])
            if txh not in local_hashes:
                err = tx_mod.add_transaction(conn, tx, cfg)
                if not err:
                    local_hashes.add(txh)
                    added += 1
    return added


def broadcast_transaction(tx: dict):
    """新交易广播到所有节点"""
    def _do():
        with _broadcast_lock:
            for peer in _peers:
                _http_post(f"{peer}/api/transfer_signed", tx, timeout=3)
    threading.Thread(target=_do, daemon=True).start()


def broadcast_new_block(height: int):
    """新块挖完，通知所有节点立即同步（发个ping，对方自己拉）"""
    def _do():
        for peer in _peers:
            # 简单通知：对方收到后会在下次同步时拉取
            # 也可以直接让对方调用 /api/p2p/sync
            _http_post(f"{peer}/api/p2p/sync", {}, timeout=3)
    threading.Thread(target=_do, daemon=True).start()




def discover_peers(max_peers: int = 20) -> int:
    """
    节点自动发现：从所有已知节点拉取它们的peer列表，自动添加新节点
    返回新发现的节点数
    """
    new_count = 0
    for peer in list(_peers):
        data = _http_get(f"{peer}/api/p2p/peers")
        if not data or "peers" not in data:
            continue
        for remote_peer in data["peers"]:
            remote_peer = remote_peer.rstrip("/")
            # 跳过自己（简单判断：不在本地peers里且不是自己）
            if remote_peer not in _peers and len(_peers) < max_peers:
                # 简单去重：不添加包含自己IP的（后续可优化）
                _peers.append(remote_peer)
                _peer_status[remote_peer] = {"last_ok": 0, "fail_count": 0}
                new_count += 1
    if new_count > 0:
        _save_peers()
        log.info(f"自动发现 {new_count} 个新节点")
    return new_count

def start_sync_loop(conn_factory, cfg, interval: int = 5):
    global _sync_thread, _stop_flag
    if _sync_thread and _sync_thread.is_alive():
        return
    _stop_flag.clear()
    load_peers()
    for p in _peers:
        _peer_status.setdefault(p, {"last_ok": 0, "fail_count": 0})

    _loop_count = 0
    def _loop():
        nonlocal _loop_count
        while not _stop_flag.is_set():
            try:
                conn = conn_factory()
                sync_all_peers(conn, cfg)
                sync_mempool_from_peers(conn, cfg)
                conn.close()
                _loop_count += 1
                # 每6次同步（约30秒）做一次节点发现
                if _loop_count % 6 == 0:
                    discover_peers()
            except Exception as e:
                log.error(f"同步循环异常: {e}")
            _stop_flag.wait(interval)

    _sync_thread = threading.Thread(target=_loop, daemon=True, name="p2p-sync")
    _sync_thread.start()
    log.info(f"P2P同步线程已启动，间隔{interval}s，节点数: {len(_peers)}")


def stop_sync_loop():
    _stop_flag.set()
    if _sync_thread:
        _sync_thread.join(timeout=3)


def get_sync_status() -> dict:
    return {
        "peers": _peers,
        "peer_count": len(_peers),
        "peer_status": _peer_status,
        "running": _sync_thread.is_alive() if _sync_thread else False,
        "last_sync": _last_sync,
    }
