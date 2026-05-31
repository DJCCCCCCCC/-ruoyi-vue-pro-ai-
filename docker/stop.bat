@echo off
chcp 65001 >nul
cd /d "%~dp0"
docker compose --env-file .env down
echo 已停止所有容器
