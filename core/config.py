# -*- coding: utf-8 -*-
"""配置常量与读写"""
import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DB_FILE = BASE_DIR / "chain.db"
WALLETS_FILE = BASE_DIR / "wallets.json"
CONFIG_FILE = BASE_DIR / "config.json"
TMP_DIR = BASE_DIR / "tmp"

DIFFICULTY = 4
MINER_REWARD = 50
INITIAL_SUPPLY = 100000
INITIAL_OWNER = "XiaoHei"
ADDR_HEX_LEN = 16

TMP_DIR.mkdir(exist_ok=True)


def default_config() -> dict:
    return {
        "difficulty": DIFFICULTY,
        "miner_reward": MINER_REWARD,
        "webui_host": "127.0.0.1",
        "webui_port": 8222,
        "webui_allow_lan": False,
        "mempool_max": 200,
        "tx_fee": 1,
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
    cfg["difficulty"] = max(1, int(cfg.get("difficulty", DIFFICULTY)))
    cfg["miner_reward"] = max(0, int(cfg.get("miner_reward", MINER_REWARD)))
    if not cfg.get("webui_allow_lan"):
        cfg["webui_host"] = "127.0.0.1"
    return cfg
