#!/bin/bash

set -e

PROJECT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$PROJECT_DIR"

echo "========================================"
echo "  高校图书馆阅览桌椅管理系统"
echo "========================================"
echo ""

echo "[1/5] 检查端口占用..."
PORTS=(3529 6629 8329 8229)
for port in "${PORTS[@]}"; do
    if lsof -Pi ":$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ⚠️  端口 $port 被占用，检查占用进程:"
        lsof -Pi ":$port" -sTCP:LISTEN | head -5
        echo ""
    else
        echo "  ✅ 端口 $port 可用"
    fi
done

echo ""
echo "[2/5] 后端编译检查..."
DOCKER_CMD="docker"
if ! command -v docker &> /dev/null; then
    DOCKER_CMD="/usr/local/bin/docker"
fi
$DOCKER_CMD run --rm -v "$PROJECT_DIR/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn compile -q
if [ $? -eq 0 ]; then
    echo "  ✅ 后端编译通过"
else
    echo "  ❌ 后端编译失败"
    exit 1
fi

echo ""
echo "[3/5] 前端构建检查..."
$DOCKER_CMD run --rm -v "$PROJECT_DIR/frontend":/app -w /app node:18-alpine sh -c "npm config set registry https://mirrors.huaweicloud.com/repository/npm/ && npm ci && npm run build"
if [ $? -eq 0 ]; then
    echo "  ✅ 前端构建通过"
else
    echo "  ❌ 前端构建失败"
    exit 1
fi

echo ""
echo "[4/5] Docker 构建启动..."
$DOCKER_CMD compose up -d --build

echo ""
echo "[5/5] 验证服务..."
echo "  等待服务启动..."
sleep 20

echo ""
echo "  检查容器状态:"
$DOCKER_CMD compose ps

echo ""
echo "  验证前端访问 (localhost):"
curl -s -o /dev/null -w "    HTTP Status: %{http_code}\n" http://localhost:8229/

echo "  验证前端访问 (127.0.0.1):"
curl -s -o /dev/null -w "    HTTP Status: %{http_code}\n" http://127.0.0.1:8229/

echo "  验证后端API:"
curl -s -o /dev/null -w "    HTTP Status: %{http_code}\n" http://localhost:8329/api/desk-chair

echo ""
echo "========================================"
echo "  服务已启动完成"
echo "========================================"
echo ""
echo "  前端地址: http://localhost:8229"
echo "  后端地址: http://localhost:8329"
echo "  MySQL: localhost:3529"
echo "  Redis: localhost:6629"
echo ""
