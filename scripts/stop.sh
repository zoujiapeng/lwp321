#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIDFILE="$ROOT/runtime/app.pid"
[ -f "$PIDFILE" ] || { echo '未找到服务PID'; exit 0; }
PID="$(cat "$PIDFILE")"
if ! kill -0 "$PID" 2>/dev/null; then rm -f "$PIDFILE"; echo '服务未运行'; exit 0; fi
ps -p "$PID" -o args= | grep -Fq "$ROOT/dist/talk-records.jar" || { echo 'PID不属于本系统，未执行停止'; exit 1; }
kill -TERM "$PID"
for i in $(seq 1 45); do
  if ! kill -0 "$PID" 2>/dev/null; then rm -f "$PIDFILE"; echo '服务已正常停止'; exit 0; fi
  sleep 1
done
echo '服务仍未退出。为保护数据库，没有强制终止，请检查日志。'; exit 1
