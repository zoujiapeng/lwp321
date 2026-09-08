#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
[ ! -f .env.local ] || { set -a; source .env.local; set +a; }
mkdir -p backups
STAMP="$(date +%Y%m%d-%H%M%S)"
if [[ "${DB_URL:-}" == jdbc:mysql:* ]]; then
  command -v mysqldump >/dev/null || { echo '需要 MySQL 客户端 mysqldump'; exit 1; }
  : "${MYSQL_HOST:?请在.env.local设置MYSQL_HOST}" "${MYSQL_DATABASE:?请设置MYSQL_DATABASE}" "${DB_USER:?请设置DB_USER}" "${DB_PASSWORD:?请设置DB_PASSWORD}"
  FILE="backups/mysql-$STAMP.sql.gz"
  MYSQL_PWD="$DB_PASSWORD" mysqldump -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$DB_USER" --single-transaction --no-tablespaces "$MYSQL_DATABASE" | gzip > "$FILE.tmp"
  mv "$FILE.tmp" "$FILE"
else
  if [ -f runtime/app.pid ] && kill -0 "$(cat runtime/app.pid)" 2>/dev/null; then echo 'H2文件备份前必须先正常停止服务：bash scripts/stop.sh'; exit 1; fi
  [ -f runtime/talk.mv.db ] || { echo '未找到默认H2数据库；自定义DB_URL请使用对应数据库的备份工具'; exit 1; }
  FILE="backups/h2-$STAMP.mv.db"; cp runtime/talk.mv.db "$FILE"
fi
sha256sum "$FILE" > "$FILE.sha256"
echo "备份完成：$FILE。请另行保存到受控备份位置，勿提交到GitHub。"
