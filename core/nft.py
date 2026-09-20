"""NFT模块 - 独立文件，所有NFT逻辑都在这里"""
import sqlite3, time, json, secrets

def init_nft_tables(conn: sqlite3.Connection):
    cur = conn.cursor()
    cur.execute("""CREATE TABLE IF NOT EXISTS nfts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        cid TEXT NOT NULL,
        supply INTEGER DEFAULT 1,
        description TEXT DEFAULT '',
        minter TEXT NOT NULL,
        owner TEXT NOT NULL,
        created_at INTEGER NOT NULL
    )""")
    cur.execute("""CREATE TABLE IF NOT EXISTS nft_transfer_codes (
        code TEXT PRIMARY KEY,
        nft_id INTEGER NOT NULL,
        from_addr TEXT NOT NULL,
        used INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL
    )""")
    conn.commit()

def mint_nft(conn, minter, name, cid, supply=1, description=''):
    init_nft_tables(conn)
    cur = conn.cursor()
    cur.execute("""INSERT INTO nfts (name, cid, supply, description, minter, owner, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (name, cid, supply, description, minter, minter, int(time.time())))
    conn.commit()
    return {"id": cur.lastrowid, "name": name, "cid": cid, "supply": supply, "owner": minter}

def list_nfts_by_owner(conn, owner):
    init_nft_tables(conn)
    cur = conn.cursor()
    cur.execute("""SELECT * FROM nfts WHERE owner=? ORDER BY id DESC""", (owner,))
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, r)) for r in cur.fetchall()]

def get_nft(conn, nft_id):
    init_nft_tables(conn)
    cur = conn.cursor()
    cur.execute("SELECT * FROM nfts WHERE id=?", (nft_id,))
    cols = [d[0] for d in cur.description]
    r = cur.fetchone()
    return dict(zip(cols, r)) if r else None

def transfer_nft(conn, nft_id, from_addr, to_addr):
    init_nft_tables(conn)
    nft = get_nft(conn, nft_id)
    if not nft: return False, "NFT不存在"
    if nft['owner'] != from_addr: return False, "不是你的NFT"
    cur = conn.cursor()
    cur.execute("UPDATE nfts SET owner=? WHERE id=?", (to_addr, nft_id))
    conn.commit()
    return True, nft

# ===== 一次性发送码 =====
def create_transfer_code(conn, nft_id, from_addr):
    """生成一次性发送码，返回code字符串"""
    init_nft_tables(conn)
    nft = get_nft(conn, nft_id)
    if not nft: return None, "NFT不存在"
    if nft['owner'] != from_addr: return None, "不是你的NFT"
    # 作废旧码
    cur = conn.cursor()
    cur.execute("UPDATE nft_transfer_codes SET used=1 WHERE nft_id=? AND from_addr=?", (nft_id, from_addr))
    code = secrets.token_hex(16)  # 32位随机码
    ts = int(time.time())
    full_code = f"XiaoHeiBit$Nft${code}${ts}"
    cur.execute("""INSERT INTO nft_transfer_codes (code, nft_id, from_addr, used, created_at)
                   VALUES (?, ?, ?, 0, ?)""", (code, nft_id, from_addr, ts))
    conn.commit()
    return full_code, nft

def confirm_transfer(conn, code, to_addr):
    """接收方扫码确认，完成转移，码作废"""
    init_nft_tables(conn)
    # 解析格式 XiaoHeiBit$Nft$random$ts
    parts = code.split("$")
    if len(parts) >= 3 and parts[0] == "XiaoHeiBit" and parts[1] == "Nft":
        code = parts[2]
    cur = conn.cursor()
    cur.execute("SELECT * FROM nft_transfer_codes WHERE code=? AND used=0", (code,))
    cols = [d[0] for d in cur.description]
    r = cur.fetchone()
    if not r: return False, "码无效或已使用"
    info = dict(zip(cols, r))
    ok, nft = transfer_nft(conn, info['nft_id'], info['from_addr'], to_addr)
    if not ok: return False, nft
    cur.execute("UPDATE nft_transfer_codes SET used=1 WHERE code=?", (code,))
    conn.commit()
    return True, nft

