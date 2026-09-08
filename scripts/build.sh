#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v java >/dev/null || { echo '需要安装 Java 17 或更高版本'; exit 1; }
command -v mvn >/dev/null || { echo '需要安装 Maven 3.6.3 或更高版本'; exit 1; }
command -v npm >/dev/null || { echo '需要安装 Node.js 22.12 或更高版本（含 npm）'; exit 1; }
cd "$ROOT/frontend"
if [ -f package-lock.json ]; then npm ci; else npm install; fi
npm run build
rm -rf "$ROOT/backend/src/main/resources/static"
mkdir -p "$ROOT/backend/src/main/resources/static" "$ROOT/dist"
cp -r dist/. "$ROOT/backend/src/main/resources/static/"
cd "$ROOT/backend"
mvn clean verify
cp target/talk-records.jar "$ROOT/dist/talk-records.jar"
echo '构建成功。执行 bash scripts/start.sh 启动。'
