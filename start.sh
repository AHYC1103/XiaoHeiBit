#!/data/data/com.termux/files/usr/bin/bash
# XiaoHeiBit 一键启动
cd ~/xiaoheibit
echo "======== 卡卡的很正常 ========"
if ! python3 -c "import ecdsa" 2>/dev/null; then echo "[*] 安装 ecdsa..."; pip install ecdsa; fi
if ! python3 -c "import flask" 2>/dev/null; then echo "[*] 安装 flask..."; pip install flask; fi
python3 -c "import core; c=core.init_db(); core.ensure_genesis(c, core.load_config()); c.close()"
echo "[*] WebUI: http://127.0.0.1:8222 (仅本机)"
echo "[*] 后端: api.py | 前端: webui.html"
echo "[*] Ctrl+C 停止"
exec python3 api.py
