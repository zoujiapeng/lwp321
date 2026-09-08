#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
FILE="${1:-}"
[ -f "$FILE" ] || { echo '用法：bash scripts/restore-h2.sh backups/h2-日期.mv.db'; exit 1; }
[[ "$FILE" == *.mv.db ]] || { echo '请选择H2的.mv.db备份文件'; exit 1; }
if [ -f runtime/app.pid ] && kill -0 "$(cat runtime/app.pid)" 2>/dev/null; then echo '请先正常停止服务'; exit 1; fi
[ ! -f "$FILE.sha256" ] || sha256sum --check "$FILE.sha256"
read -r -p '恢复会替换当前数据库；当前文件将另行备份。输入 RESTORE 确认：' ANSWER
[ "$ANSWER" = RESTORE ] || { echo '已取消'; exit 0; }
mkdir -p runtime backups
[ ! -f runtime/talk.mv.db ] || cp runtime/talk.mv.db "backups/before-restore-$(date +%Y%m%d-%H%M%S).mv.db"
cp "$FILE" runtime/talk.mv.db
chmod 600 runtime/talk.mv.db
echo '恢复完成。执行 bash scripts/start.sh，并使用备份时的账号密码登录。'
