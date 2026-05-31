#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "[提示] 未找到 .env，已从 .env.example 复制，请编辑后填入 API Key"
  cp .env.example .env
fi

echo ""
echo "========================================"
echo "  AI 支付风险检测系统 - Docker 启动"
echo "========================================"
echo ""
echo "首次构建约 10~20 分钟，请耐心等待..."
echo ""

docker compose --env-file .env up -d --build

echo ""
echo "========================================"
echo "  启动完成"
echo "========================================"
echo "  聊天演示:  http://localhost:5173"
echo "  管理后台:  http://localhost:8080  (admin / admin123)"
echo "  后端 API:  http://localhost:48080"
echo ""
echo "  查看日志: docker compose logs -f server"
echo "  停止服务: docker compose down"
echo "========================================"
echo ""
