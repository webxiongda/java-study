# Chapter 56 Docker 部署 - 自测与验收

## Q1 概念: Dockerfile `CMD` vs `ENTRYPOINT` 区别? 写个组合示例

| | CMD | ENTRYPOINT |
|---|---|---|
| 角色 | 默认参数 | 主命令 (固定) |
| 覆盖 | `docker run img xx` 末尾 xx 覆盖 CMD | 末尾参数作为 ENTRYPOINT 的 args |
| 必须 | 不必须 | 不必须, 推荐有 |
| 推荐组合 | ENTRYPOINT 定主程, CMD 设默认参数 | |

```dockerfile
ENTRYPOINT ["java","-jar","/app/app.jar"]
CMD ["--spring.profiles.active=prod"]
```

行为:
- `docker run img` → 跑 `java -jar /app/app.jar --spring.profiles.active=prod`
- `docker run img --spring.profiles.active=test` → CMD 被覆盖, 跑 `java -jar /app/app.jar --spring.profiles.active=test`

**易错点**: 用 shell 形式 `ENTRYPOINT java -jar app.jar` (无方括号) 会变成 `/bin/sh -c "java -jar app.jar"`, 进程是 sh 而不是 Java, **收不到 SIGTERM 信号**, 优雅停机失败。 **必须用 exec 形式 (JSON 数组)**。

---

## Q2 概念: 多阶段构建怎么瘦身? 写一个 Spring Boot 项目的 Dockerfile

```dockerfile
# ============ Stage 1: 构建 ============
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom 用于缓存 deps
COPY pom.xml ./
RUN mvn -B dependency:go-offline -DskipTests

# 再复制源码构建
COPY src ./src
RUN mvn -B clean package -DskipTests -Dmaven.test.skip=true

# ============ Stage 2: 运行 ============
FROM eclipse-temurin:21-jre-jammy

# 装 curl 给 healthcheck 用
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 非 root 用户
RUN groupadd -r app && useradd -r -g app app

WORKDIR /app
COPY --from=builder --chown=app:app /build/target/*.jar app.jar
USER app

ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/ \
    -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT exec java $JAVA_OPTS -jar /app/app.jar
```

**瘦身原理**:
- 阶段 1 (Maven + JDK 21): 1.3GB, **构建完整就丢**
- 阶段 2 (JRE 21 Jammy): ~250MB, 只保留 app.jar + curl
- 总输出镜像 ~250MB (没多阶段的话 ~1.5GB)

**进一步**: 用 Distroless `gcr.io/distroless/java21-debian12` 可降到 ~180MB, 但没 shell, 不能 exec 进去调试。

---

## Q3 代码题 (Docker Compose): 写一个完整 docker-compose.yml

需求: 启动 blog-api + MySQL + Redis, 应用必须等 MySQL ready 才启动, 数据持久化, 限内存。

```yaml
version: "3.8"

services:
  blog-api:
    build: .
    image: blog-api:${TAG:-latest}
    container_name: blog-api
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/blog?useUnicode=true&characterEncoding=utf-8
      SPRING_DATASOURCE_USERNAME: blog
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_started }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      start_period: 60s
      retries: 3
    restart: unless-stopped
    deploy:
      resources:
        limits: { memory: 1G, cpus: "1.0" }
    logging:
      driver: json-file
      options: { max-size: "10m", max-file: "3" }
    networks: [backend]

  mysql:
    image: mysql:8.4
    container_name: blog-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: blog
      MYSQL_USER: blog
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [backend]

  redis:
    image: redis:7.4-alpine
    container_name: blog-redis
    command: ["redis-server","--appendonly","yes"]
    volumes: [redis-data:/data]
    networks: [backend]

volumes:
  mysql-data:
  redis-data:

networks:
  backend:
    driver: bridge
```

`.env`:
```
DB_PASSWORD=changeme
MYSQL_ROOT_PASSWORD=changemeroot
JWT_SECRET=at-least-32-chars-please-replace-me
```

**关键设计点**:
- 数据库密码用 `.env` 变量, 不写死
- `depends_on.condition: service_healthy` 保证 MySQL ready 应用才启动
- volume 命名 (`mysql-data`) 而不是 bind mount, 跨平台兼容
- SQL 初始化挂 `/docker-entrypoint-initdb.d` (MySQL 镜像约定)
- 健康检查的 `start_period` 设 60s, Spring Boot 启动通常 30-60 秒
- `restart: unless-stopped` (异常重启, 手动 stop 不重启)
- `logging` 限大小防日志打爆磁盘
- 共用 `backend` network, MySQL/Redis 不暴露端口到主机 (只允许 blog-api 内网访问)

---

## Q4 排查题: 容器启动后立即退出, 怎么排查?

**步骤**:

```bash
# 1. 看容器状态 (Exited?)
docker ps -a

# 2. 看退出码
docker inspect <container> | grep ExitCode
# 0 = 正常, 137 = SIGKILL (一般 OOM), 139 = SegFault, 143 = SIGTERM, 1 = 应用错误

# 3. 看日志
docker logs <container>
docker logs --tail 100 -f <container>

# 4. 进容器 (如果还能跑起来)
docker run -it --rm --entrypoint /bin/sh blog-api:latest
# 在容器里手动执行启动命令看具体错误

# 5. 看资源限制
docker inspect <container> | grep -E "Memory|CpuShares"
```

**常见退出码**:

| 码 | 含义 | 常见原因 |
|---|---|---|
| 0 | 正常退出 | 应用 main 跑完 (不是常驻进程?) |
| 1 | 通用错误 | 应用抛异常 |
| 125 | docker 命令错 | 镜像不存在 / 参数错 |
| 126 | 容器命令不可执行 | 权限不对 |
| 127 | 命令找不到 | ENTRYPOINT 路径错 |
| 137 | SIGKILL | OOMKilled (内存超 limit) |
| 139 | SegFault | native 库错 |
| 143 | SIGTERM | 正常停止 |

**Spring Boot 容器常见死法**:
- DB 连不上: 看 logs 找 `Communications link failure` → 检查 `depends_on` 和网络
- 端口冲突: `Web server failed to start. Port 8080 was already in use`
- 内存不足: OOMKilled (137) → 调 `-Xmx` 或加 limit
- 配置不对: 缺 env → 应用启动报错

---

## Q5 综合: 把博客 API 上生产 (从本地到云服务器), 详细流程?

**完整发布链路**:

```
1. 本地开发 → mvn test
2. git push → GitHub Actions CI
3. CI: mvn package + docker build + docker scout
4. CI: docker push registry/blog-api:$GIT_SHA
5. CI: ssh prod-server, pull + 滚动重启
6. 验证: curl /actuator/health, 看 Grafana 指标
```

**GitHub Actions 关键步骤**:

```yaml
- run: mvn -B clean package
- uses: docker/setup-buildx-action@v3
- uses: docker/login-action@v3
  with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
- uses: docker/build-push-action@v5
  with:
    context: .
    push: true
    tags: |
      ghcr.io/me/blog-api:${{ github.sha }}
      ghcr.io/me/blog-api:latest
    cache-from: type=gha
    cache-to: type=gha,mode=max
- name: Scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ghcr.io/me/blog-api:${{ github.sha }}
    severity: HIGH,CRITICAL
    exit-code: 1   # 高危漏洞阻断
- name: Deploy
  uses: appleboy/ssh-action@v1.0.0
  with:
    host: ${{ secrets.PROD_HOST }}
    script: |
      cd /opt/blog
      export TAG=${{ github.sha }}
      docker compose pull blog-api
      docker compose up -d blog-api
      sleep 30
      curl -f http://localhost:8080/actuator/health || (docker compose logs --tail 100 blog-api; exit 1)
```

**回滚**:
```bash
TAG=<上一个 SHA> docker compose up -d blog-api
```

**蓝绿 / 滚动 (升级版)**:
- 起 blog-api-blue 和 blog-api-green 两个容器
- nginx upstream 切流
- 验证 green 健康再下 blue

**面试 2 分钟讲法**:
> "本地用 docker compose 一键起整套依赖。 CI 跑测试 + 多阶段构建 + Trivy 扫高危, 推到 ghcr.io 用 commit SHA 打 tag。 部署用 SSH 拉新镜像 + compose up -d, 30 秒后健康检查 fail 自动回滚。 镜像 ~200MB 用 JRE Jammy + 多阶段, 非 root 用户跑, 限内存 1G -Xmx 700m。 日志走 stdout 接 docker driver, 限大小防磁盘炸。"

---

## 通过标准

- [ ] 能讲清 CMD / ENTRYPOINT 区别, 写正确的 exec 形式
- [ ] 能写完整的多阶段 Dockerfile (Maven + JRE)
- [ ] 能写带 healthcheck / depends_on / volume / network 的 docker-compose.yml
- [ ] 能讲完整容器启动失败排查流程 + 退出码含义
- [ ] 能讲从本地到生产的完整 CI/CD 链路
