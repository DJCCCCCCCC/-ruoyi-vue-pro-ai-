#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[1/3] Maven 打包后端..."
mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true

test -f yudao-server/target/yudao-server.jar

cd docker

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "[提示] 已创建 .env，请填入 API Key"
fi

echo "[2/3] Docker 构建并启动（使用本地 jar）..."
docker compose -f docker-compose.yml -f docker-compose.jar.yml --env-file .env up -d --build

echo ""
echo "[3/3] 完成"
echo "  聊天演示: http://localhost:5173"
echo "  管理后台: http://localhost:8080  (admin / admin123)"
echo "  后端 API: http://localhost:48080"
echo ""
