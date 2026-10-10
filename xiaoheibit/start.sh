#!/bin/bash
cd "$(dirname "$0")"
pkill -f 'main.py' 2>/dev/null
sleep 3
pkill -9 -f 'main.py' 2>/dev/null
sleep 1
mkdir -p tmp
nohup frpc -c frpc.toml > /dev/null 2>&1 &
nohup python3 main.py > /dev/null 2>&1 &
sleep 2
echo "[| START |] XiaoHeiChain API:127.0.0.1:8222"