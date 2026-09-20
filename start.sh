#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
PIDFILE=tmp/run.pid
# 停旧进程
[ -f "$PIDFILE" ] && kill -9 "$(cat "$PIDFILE")" 2>/dev/null
pkill -9 -f 'main.py' 2>/dev/null
sleep 1
mkdir -p tmp
# 启动，日志进 tmp/server.log
nohup python3 main.py > tmp/server.log 2>&1 &
echo $! > "$PIDFILE"
sleep 2
PORT=$(python3 -c "import json;print(json.load(open('config.json'))['webui_port'])" 2>/dev/null || echo 8222)
if curl -s "http://127.0.0.1:$PORT/api/status" >/dev/null; then
  echo "[OK] XiaoHeiBit 主链 http://127.0.0.1:$PORT  pid=$(cat "$PIDFILE")"
else
  echo "[!] 启动异常, 最近日志:"; tail -8 tmp/server.log
fi
