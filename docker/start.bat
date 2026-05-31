@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

if not exist ".env" (
  echo [提示] 未找到 .env，已从 .env.example 复制，请编辑后填入 API Key
  copy /Y .env.example .env >nul
)

echo.
echo ========================================
echo   AI 支付风险检测系统 - Docker 全量构建
echo ========================================
echo.
echo 请确认 Docker Desktop 已启动！
echo 首次构建约 15~30 分钟，请耐心等待...
echo.

docker compose --env-file .env up -d --build
if errorlevel 1 (
  echo.
  echo [错误] 启动失败，请检查 Docker Desktop 是否运行
  exit /b 1
)

echo.
echo ========================================
echo   启动完成
echo ========================================
echo   聊天演示:  http://localhost:5173
echo   管理后台:  http://localhost:8080  (admin / admin123)
echo   后端 API:  http://localhost:48080
echo.
echo   查看日志: docker compose logs -f server
echo   停止服务: docker compose down
echo ========================================
echo.

endlocal
