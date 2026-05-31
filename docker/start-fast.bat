@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0\.."

echo [1/3] Maven 打包后端...
call mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true
if errorlevel 1 (
  echo [错误] Maven 打包失败，请检查 JDK/Maven 配置
  exit /b 1
)

if not exist "yudao-server\target\yudao-server.jar" (
  echo [错误] 未找到 yudao-server\target\yudao-server.jar
  exit /b 1
)

cd docker

if not exist ".env" (
  copy /Y .env.example .env >nul
  echo [提示] 已创建 .env，请填入 SPRING_AI_DASHSCOPE_API_KEY 和 YUDAO_PAY_RISK_ASSESS_IPINFO_TOKEN
)

echo [2/3] Docker 构建前端 + 启动全部服务（使用本地 jar）...
docker compose -f docker-compose.yml -f docker-compose.jar.yml --env-file .env up -d --build
if errorlevel 1 (
  echo [错误] Docker 启动失败，请确认 Docker Desktop 已运行
  exit /b 1
)

echo.
echo [3/3] 完成
echo   聊天演示: http://localhost:5173
echo   管理后台: http://localhost:8080  (admin / admin123)
echo   后端 API: http://localhost:48080
echo.

endlocal
