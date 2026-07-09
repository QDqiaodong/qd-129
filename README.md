# 高校图书馆阅览桌椅管理系统

## 项目简介

本系统用于高校图书馆后勤管理阅览桌椅，支持阅览分区绑定、分区归属调整、备注标签维护和按标签反向筛选资产。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Axios
- 后端：Spring Boot 3.3、JDK 17、MyBatis Plus、Redis
- 数据库：MySQL 8.0
- 部署：Docker Compose

## 端口说明

| 服务 | 地址或端口 |
| --- | --- |
| 前端访问地址 | http://localhost:8229 |
| 后端 API 地址 | http://localhost:8329/api |
| MySQL | 127.0.0.1:3529 |
| Redis | 127.0.0.1:6629 |

端口统一维护在根目录 `.env`，示例配置见 `.env.example`。

## 启动方式

```bash
cd /Users/Admin/Desktop/solo-0601/qd-0601/qd-组1/qd-129
docker compose up -d --build
```

## 单独编译验证

```bash
cd backend
mvn compile -q
```

```bash
cd frontend
npm ci
npm run build
```

## Docker 构建说明

Docker Compose 使用固定端口并绑定 `127.0.0.1`；前端容器内部端口为 `.env` 中的 `FRONTEND_CONTAINER_PORT=5173`，后端容器内部端口为 `BACKEND_CONTAINER_PORT=8080`。

## 常见问题

- 后端编译失败时先执行 `mvn -version` 检查 JDK，再检查 Lombok、Maven 编译插件和 `pom.xml` 是否被忽略。
- 前端构建失败时优先按实际报错检查 import 路径、导出名、Vite 代理端口和构建期语法。
- 页面中文乱码时检查源码、SQL 初始化脚本、数据库字符集、连接串编码和已有 Docker volume 数据；初始化 SQL 已增加 `SET NAMES utf8mb4;`。
