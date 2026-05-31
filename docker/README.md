# AI 支付风险检测系统 - Docker 一键部署

适合比赛演示、答辩现场快速拉起整套环境。

## 包含服务

| 服务 | 端口 | 说明 |
|------|------|------|
| mysql | 3306 | 数据库（自动导入芋道基础库 + 风控表） |
| redis | 6379 | 缓存 |
| server | 48080 | Spring Boot 后端 |
| admin | 8080 | 管理后台（风险评估驾驶舱） |
| chat | 5173 | 聊天式风控演示前端 |

## 前置条件

1. **Docker Desktop 已安装并处于 Running 状态**（必须）
2. 至少 **8GB 内存**（首次全量构建较耗资源）
3. 磁盘空闲 **5GB+**

## 方式一：快速启动（推荐比赛现场）

本地 Maven 打包 + Docker 只跑服务，**比全量 Docker 构建快很多**。

**Windows：**

```bat
cd docker
start-fast.bat
```

**Linux / macOS：**

```bash
cd docker
chmod +x start-fast.sh
./start-fast.sh
```

## 方式二：全量 Docker 构建（无需本机 Maven）

适合没有 JDK/Maven 的机器，首次构建约 **15~30 分钟**。

**Windows：**

```bat
cd docker
start.bat
```

**Linux / macOS：**

```bash
cd docker
chmod +x start.sh
./start.sh
```

## 配置 API 密钥

首次启动前编辑 `docker/.env`（可从 `.env.example` 复制）：

```env
SPRING_AI_DASHSCOPE_API_KEY=你的通义千问Key
YUDAO_PAY_RISK_ASSESS_IPINFO_TOKEN=你的ipinfoToken
```

> 不填密钥容器能启动，但**风险评估接口会失败**。

## 访问地址

| 入口 | URL |
|------|-----|
| 聊天演示 | http://localhost:5173 |
| 管理后台 | http://localhost:8080 |
| 后端 API | http://localhost:48080 |
| Swagger | http://localhost:48080/swagger-ui |

**默认账号**：`admin` / `admin123`（验证码已关闭）

## 常用命令

```bash
cd docker

# 查看状态
docker compose ps

# 查看后端日志（启动慢时 MySQL 初始化需 1~2 分钟）
docker compose logs -f server

# 停止
docker compose down

# 清空数据库重来
docker compose down -v
```

## 故障排查

### Docker 报错 `dockerDesktopLinuxEngine ... cannot find the file`

→ **Docker Desktop 没开**，请先启动 Docker Desktop 等图标变绿再执行。

### 后端反复重启

```bash
docker compose logs mysql   # 确认 MySQL 初始化完成
docker compose logs server  # 看具体报错
```

常见原因：MySQL 还在导入 `ruoyi-vue-pro.sql`（文件较大，首次需等待）。

### 评估接口 500

检查 `.env` 里 API Key，修改后：

```bash
docker compose up -d server
```

### Maven 打包失败（start-fast 方式）

本机需 JDK 8 + Maven 3.8+，且 `settings.xml` 无语法错误。

## 目录说明

```text
docker/
├── docker-compose.yml          # 主编排文件
├── docker-compose.jar.yml      # 叠加：使用本地 jar（快速模式）
├── start.bat / start-fast.bat  # Windows 一键脚本
├── start.sh / start-fast.sh    # Linux 一键脚本
├── .env.example
├── mysql/init/02-pay-risk.sql  # 风控表
├── server/Dockerfile           # 全量 Maven 构建
├── server/Dockerfile.jar       # 仅打包 jar（快速）
├── admin/                      # 管理后台 Nginx 镜像
└── chat-risk/                  # 聊天前端 Nginx 镜像
```

后端 Docker 配置：`yudao-server/src/main/resources/application-docker.yaml`
