# -*- coding: utf-8 -*-
"""XiaoHeiBit 核心包：re-export 所有公开接口"""
import sqlite3
from .config import (
    BASE_DIR, DB_FILE, CHAIN_DB, TRANSFER_DB, NFT_DB,
    WALLETS_FILE, CONFIG_FILE, TMP_DIR,
    DIFFICULTY, MINER_REWARD, INITIAL_SUPPLY, INITIAL_OWNER, ADDR_HEX_LEN,
    default_config, load_config,
)
from .utils import is_valid_address, calc_block_hash, calc_tx_hash, tx_for_hash
from .wallet import (
    _wallet_data, _wallet_save,
    Wallet, verify_signature, address_from_public_key,
    create_wallet, import_wallet, switch_wallet, delete_wallet, create_wallet_with_mnemonic, import_wallet_with_mnemonic, generate_mnemonic, mnemonic_to_private_key,
    get_current_wallet, list_wallets,
)
from .transaction import (
    validate_tx_fields, validate_tx_signature, validate_tx,
    add_transaction, get_mempool,
)
from .blockchain import (
    get_balance, get_top_height, ensure_genesis, mine_blocks,
    get_block, get_recent_blocks, get_tx_history, validate_chain,
)
from .migrate import migrate_from_json


def init_db() -> sqlite3.Connection:
    """打开 chain.db, ATTACH transfer.db 和 nft.db, 建表"""
    conn = sqlite3.connect(str(CHAIN_DB))
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    conn.row_factory = sqlite3.Row
    # ATTACH 其他两个 db
    conn.execute(f"ATTACH DATABASE '{TRANSFER_DB}' AS transfer")
    conn.execute(f"ATTACH DATABASE '{NFT_DB}' AS nft")
    cur = conn.cursor()
    # blocks 在 main (chain.db)
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
    """)
    # transactions + mempool 在 transfer schema (transfer.db)
    cur.executescript("""
    CREATE TABLE IF NOT EXISTS transfer.transactions (
        tx_id INTEGER PRIMARY KEY AUTOINCREMENT,
        block_height INTEGER NOT NULL,
        sender TEXT NOT NULL,
        receiver TEXT NOT NULL,
        amount REAL NOT NULL,
        nonce INTEGER,
        signature TEXT NOT NULL,
        public_key TEXT,
        timestamp INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS transfer.idx_tx_sender ON transactions(sender);
    CREATE INDEX IF NOT EXISTS transfer.idx_tx_receiver ON transactions(receiver);

    CREATE TABLE IF NOT EXISTS transfer.mempool (
        mp_id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender TEXT NOT NULL,
        receiver TEXT NOT NULL,
        amount REAL NOT NULL,
        nonce INTEGER,
        signature TEXT NOT NULL,
        public_key TEXT,
        timestamp INTEGER NOT NULL,
        tx_hash TEXT NOT NULL UNIQUE
    );
    """)
    conn.commit()
    return conn
