# Chapter 56 Docker 部署 - 实操 Demo

## Demo 目标

多阶段 Dockerfile + docker-compose 一键启动「app + mysql + redis + rabbitmq」+ 健康检查 + 优雅停止。

## 前置条件

- Docker Desktop / colima 已装。
- 项目可 `mvn package` 出 fat jar。

## 增量依赖

`pom.xml` 中 spring-boot-maven-plugin 已默认能生成可执行 jar。 健康检查需要 Actuator：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

`application-prod.yml`：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true
```

## 1. `Dockerfile`（多阶段 + 分层 jar）

```dockerfile
# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests \
 && java -Djarmode=layertools -jar target/*.jar extract --destination target/layers

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/xiong/blog"
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Spring Boot 分层 jar：依赖很少变更，可被 docker 层缓存
COPY --from=builder /build/target/layers/dependencies/        ./
COPY --from=builder /build/target/layers/spring-boot-loader/  ./
COPY --from=builder /build/target/layers/snapshot-dependencies/ ./
COPY --from=builder /build/target/layers/application/         ./

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/dump \
               -Xlog:gc*:file=/app/logs/gc.log:t,uptime:filecount=5,filesize=20M"

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# 用 exec 形式启动，Java 进程是 PID 1，能正确接收 SIGTERM 优雅关停
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

## 2. `.dockerignore`

```
target/
!target/*.jar
.git/
.idea/
*.iml
logs/
data/
*.log
.DS_Store
```

## 3. `docker-compose.yml`

```yaml
services:
  app:
    build: .
    image: blog-api:latest
    container_name: blog-api
    ports: ["8080:8080"]
    depends_on:
      mysql:    { condition: service_healthy }
      redis:    { condition: service_started }
      rabbitmq: { condition: service_started }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:mysql://mysql:3306/blog?useSSL=false&serverTimezone=Asia/Shanghai
      DB_USER: blog
      DB_PASS: ${DB_PASS}
      SPRING_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      TZ: Asia/Shanghai
    volumes:
      - ./logs:/app/logs
      - ./dump:/app/dump
      - ./data/uploads:/app/data/uploads
    restart: unless-stopped
    stop_grace_period: 30s              # 给优雅关闭时间

  mysql:
    image: mysql:8.4
    container_name: blog-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: blog
      MYSQL_USER: blog
      MYSQL_PASSWORD: ${DB_PASS}
      TZ: Asia/Shanghai
    ports: ["3306:3306"]
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: blog-redis
    command: ["redis-server", "--appendonly", "yes"]
    ports: ["6379:6379"]
    volumes: ["redis_data:/data"]

  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: blog-rmq
    ports: ["5672:5672", "15672:15672"]
    volumes: ["rmq_data:/var/lib/rabbitmq"]

volumes:
  mysql_data:
  redis_data:
  rmq_data:
```

## 4. `.env`（不提交，仅本地）

```
DB_PASS=blogpass
MYSQL_ROOT_PASSWORD=rootpass
```

## 5. 优雅关停（Spring Boot 配合 SIGTERM）

`application.yml`：

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 25s
```

## 运行与验证

| 检查项 | 命令 |
|---|---|
| 构建 | `docker compose build` |
| 启动 | `docker compose up -d` |
| 健康 | `curl :8080/actuator/health` → `{"status":"UP"}` |
| 探针 | `curl :8080/actuator/health/liveness` 和 `/readiness` |
| 日志 | `docker compose logs -f app` |
| 镜像大小 | `docker images blog-api` 应 < 250MB（alpine + jre） |
| 优雅关停 | `docker compose stop app` → 日志看到 "Graceful shutdown" |
| 重启策略 | 杀掉 app 进程 → 容器自动 restart |

## 常见坑

- 单阶段把 maven 也打进镜像 → 1GB+。 必须多阶段。
- 没用 `exec` → shell 是 PID 1，SIGTERM 不传给 Java，被强杀。
- `-Xmx` 写死 → 容器内存改了不生效。 用 `MaxRAMPercentage`。
- compose `depends_on` 不带 `condition` → app 早于 mysql 启动，连接失败重启循环。
- 时区不一致 → 日志时间 UTC，DB 时间本地，对不上。 全局 `TZ=Asia/Shanghai`。
- 用 `latest` 标签 → 滚回上一版本难追溯。 上 CI 后用 `<git-sha>` 或语义版本。

## 提交

```bash
git commit -m "chapter 56: dockerfile multistage + compose with healthcheck"
```
