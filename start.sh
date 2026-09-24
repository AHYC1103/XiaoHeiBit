#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
pkill -9 -f 'main.py|api.py' 2>/dev/null
sleep 3
mkdir -p tmp
nohup python3 main.py > /dev/null 2>&1 &
sleep 2
echo "[*| START |*] XiaoHeiBit API:127.0.0.1:8234"
