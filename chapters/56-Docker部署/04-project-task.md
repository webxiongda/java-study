# Chapter 56 Docker 部署 - 项目任务

## 任务概述

把博客 API **完整容器化**:

1. 多阶段 Dockerfile (Maven 构建 + JRE 运行 + 非 root + healthcheck)
2. docker-compose.yml 一键起 app + mysql + redis + rabbitmq + minio + prometheus + grafana
3. `.env` 管理密钥
4. CI 流水线 (GitHub Actions): build + scan + push
5. 滚动部署脚本

## 业务背景

之前章节代码都本地跑, 想给别人 / 面试官 / 简历演示, 必须能 `git clone && docker compose up -d` 一行起项目。 同时镜像要小 / 安全 / 可观测, 这是工程能力的体现。

## 任务拆解

### Step 1: Dockerfile (30 分钟)

```dockerfile
# Stage 1: build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: run
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r app && useradd -r -g app app && \
    mkdir -p /var/log/blog && chown -R app:app /var/log/blog

WORKDIR /app
COPY --from=builder --chown=app:app /build/target/*.jar app.jar
USER app

ENV JAVA_OPTS="-Xms512m -Xmx700m -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/blog/ \
    -XX:+ExitOnOutOfMemoryError \
    -Xlog:gc*:file=/var/log/blog/gc.log:t,uptime:filecount=5,filesize=20M"

EXPOSE 8080
VOLUME ["/var/log/blog"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT exec java $JAVA_OPTS -jar /app/app.jar
```

`.dockerignore`:
```
target/
.git/
.idea/
*.iml
.env*
!.env.example
node_modules/
*.md
```

### Step 2: docker-compose.yml (40 分钟)

完整文件 (节选关键, 详见 Ch50 的 demo):

```yaml
version: "3.8"

services:
  blog-api:
    build: .
    image: blog-api:${TAG:-latest}
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/blog?useUnicode=true&characterEncoding=utf-8
      SPRING_DATASOURCE_USERNAME: blog
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: admin
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      STORAGE_ENDPOINT: http://minio:9000
      STORAGE_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      STORAGE_SECRET_KEY: ${MINIO_SECRET_KEY}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_started }
      rabbitmq: { condition: service_healthy }
      minio: { condition: service_started }
    healthcheck:
      test: ["CMD","curl","-f","http://localhost:8080/actuator/health"]
      interval: 30s
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits: { memory: 1G, cpus: "1.0" }
    volumes:
      - ./logs:/var/log/blog
    networks: [backend]

  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: blog
      MYSQL_USER: blog
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD","mysqladmin","ping","-h","localhost","-uroot","-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      retries: 5
    networks: [backend]

  redis:
    image: redis:7.4-alpine
    command: ["redis-server","--appendonly","yes"]
    volumes: [redis-data:/data]
    networks: [backend]

  rabbitmq:
    image: rabbitmq:3.13-management
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    healthcheck:
      test: ["CMD","rabbitmq-diagnostics","ping"]
      interval: 30s
      retries: 3
    volumes: [rabbitmq-data:/var/lib/rabbitmq]
    networks: [backend]

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes: [minio-data:/data]
    networks: [backend]

  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    networks: [backend]

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana-provisioning:/etc/grafana/provisioning:ro
    networks: [backend]

volumes:
  mysql-data:
  redis-data:
  rabbitmq-data:
  minio-data:
  grafana-data:

networks:
  backend:
    driver: bridge
```

`.env.example`:
```
DB_PASSWORD=changeme
MYSQL_ROOT_PASSWORD=changemeroot
RABBITMQ_PASSWORD=changemerabbit
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=changememinio
JWT_SECRET=at-least-32-chars-replace-me-now
GRAFANA_PASSWORD=admin
TAG=latest
```

`.gitignore` 加 `.env` (不要 commit 真实密码)。

### Step 3: GitHub Actions CI (30 分钟)

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: maven }

      - name: Test
        run: mvn -B test

      - name: Build
        run: mvn -B clean package -DskipTests

      - uses: docker/setup-buildx-action@v3

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - uses: docker/build-push-action@v5
        with:
          context: .
          push: ${{ github.ref == 'refs/heads/main' }}
          tags: |
            ghcr.io/${{ github.repository }}:${{ github.sha }}
            ghcr.io/${{ github.repository }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Scan image
        if: github.ref == 'refs/heads/main'
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}:${{ github.sha }}
          severity: HIGH,CRITICAL
          exit-code: '1'
```

### Step 4: 部署脚本 (15 分钟)

`scripts/deploy.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

TAG=${1:-latest}
export TAG

echo "Pulling blog-api:$TAG ..."
docker compose pull blog-api

echo "Recreating ..."
docker compose up -d blog-api

echo "Waiting for healthy ..."
for i in {1..30}; do
    health=$(docker inspect --format='{{.State.Health.Status}}' blog-api 2>/dev/null || echo "starting")
    if [ "$health" = "healthy" ]; then
        echo "OK"
        exit 0
    fi
    sleep 5
done

echo "FAILED to become healthy, rolling back logs:"
docker compose logs --tail 100 blog-api
exit 1
```

### Step 5: README 启动指南 (10 分钟)

```markdown
## 快速启动

```bash
git clone ...
cd blog
cp .env.example .env
# 编辑 .env 改密码
docker compose up -d

# 等 1-2 分钟启动
curl http://localhost:8080/actuator/health    # {"status":"UP"}
open http://localhost:9001    # MinIO 控制台
open http://localhost:15672   # RabbitMQ 控制台
open http://localhost:3000    # Grafana
```
```

## 交付物

- [ ] `Dockerfile` 多阶段 + 非 root + healthcheck
- [ ] `.dockerignore`
- [ ] `docker-compose.yml` 7 服务一键起
- [ ] `.env.example` + `.gitignore` 加 .env
- [ ] `sql/init/01-schema.sql` 自动初始化数据库
- [ ] `.github/workflows/ci.yml` (test / build / scan / push)
- [ ] `scripts/deploy.sh` 滚动部署 + 健康检查 + 回滚
- [ ] `monitoring/prometheus.yml` + Grafana dashboards
- [ ] README 启动指南
- [ ] git commit: `ch56: full dockerization + CI + monitoring`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 镜像大小 | `docker images blog-api` < 300MB |
| 一键起 | 全新机器, 5 分钟内 `docker compose up -d` 全部 healthy |
| 启动顺序 | MySQL ready 后 blog-api 才启动 (不会因 DB 连不上重启) |
| 非 root | `docker exec blog-api whoami` 返回 `app` |
| 健康检查 | `docker inspect blog-api | grep Health` 显示 healthy |
| 密钥隔离 | grep 镜像 + git 仓库, 无明文密码 |
| OOM 处理 | 触发 OOM 后, `logs/*.hprof` 产生, 容器自动重启 |
| CI 漏洞 | Trivy 阻断 HIGH/CRITICAL CVE |
| 部署回滚 | `deploy.sh <旧 SHA>` 能回到老版本 |

## 扩展挑战

1. **Distroless 镜像**: 切到 `gcr.io/distroless/java21-debian12`, 镜像降到 < 200MB
2. **多架构**: `docker buildx build --platform linux/amd64,linux/arm64`, 支持 M 系列 Mac
3. **k8s Helm Chart**: 把 compose 改成 k8s Deployment + Service + ConfigMap + Secret
4. **蓝绿部署**: 起 blog-api-blue 和 -green, nginx upstream 切流
5. **secret management**: 用 docker secret 或 Vault 代替 .env
6. **GitOps**: ArgoCD 监听 git 仓库, 自动同步集群
