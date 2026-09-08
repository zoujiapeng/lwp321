#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAR="$ROOT/dist/talk-records.jar"
command -v java >/dev/null || { echo '请先安装 Java 17 或更高版本'; exit 1; }
[ -f "$JAR" ] || { echo '缺少 dist/talk-records.jar。请下载运行包，或执行 bash scripts/build.sh'; exit 1; }
mkdir -p runtime
if [ -f runtime/app.pid ] && kill -0 "$(cat runtime/app.pid)" 2>/dev/null && ps -p "$(cat runtime/app.pid)" -o args= | grep -Fq "$JAR"; then echo '服务已在运行'; exit 0; fi
if [ ! -f .env.local ]; then
  command -v openssl >/dev/null || { echo '首次启动需要 openssl 生成随机初始密码'; exit 1; }
  printf 'APP_ADMIN_PASSWORD=Start9!%s\nSERVER_ADDRESS=127.0.0.1\nSERVER_PORT=8080\n' "$(openssl rand -hex 18)" > .env.local
  chmod 600 .env.local
  echo '已生成独立初始密码，保存在 .env.local（仅当前用户可读）。初始账号：admin。'
fi
set -a; source .env.local; set +a
read -r -a JAVA_ARGS <<< "${JAVA_OPTS:--Xms128m -Xmx512m}"
nohup java "${JAVA_ARGS[@]}" -jar "$JAR" > runtime/app.log 2>&1 &
PID=$!; echo "$PID" > runtime/app.pid
HOST="${SERVER_ADDRESS:-127.0.0.1}"; [ "$HOST" != '0.0.0.0' ] || HOST=127.0.0.1
for i in $(seq 1 90); do
  if ! kill -0 "$PID" 2>/dev/null; then echo '启动失败，请查看 runtime/app.log'; tail -n 20 runtime/app.log; exit 1; fi
  if command -v curl >/dev/null && curl -fsS "http://$HOST:${SERVER_PORT:-8080}/api/health" >/dev/null 2>&1; then
    echo "服务已启动：http://$HOST:${SERVER_PORT:-8080}"
    echo '初始密码可用 grep APP_ADMIN_PASSWORD .env.local 查看；首次登录必须修改。'
    exit 0
  fi
  sleep 1
done
echo '服务仍在启动或未安装curl，请检查 runtime/app.log；PID已写入 runtime/app.pid。'
