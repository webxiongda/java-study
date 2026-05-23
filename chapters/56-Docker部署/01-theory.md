# Chapter 56 Docker 部署 - 理论篇

## 一、学习定位

把博客 API + MySQL + Redis + RabbitMQ + MinIO 用 Docker Compose 一键起。 这是从"本地能跑"到"任何机器能跑"的关键一步。

- 优先级: L1 (无 Docker 经验等于半成品后端)
- 投入: 4 小时
- 产出: `docker compose up` 一行启动整个项目

## 二、核心概念

### 1. 镜像 (Image) vs 容器 (Container)

- **镜像**: 静态文件 (类似类), 分层 (layer) 存储, 只读
- **容器**: 镜像的运行实例 (类似对象), 有读写层
- 一个镜像可以起多个容器

### 2. Dockerfile 关键指令

```dockerfile
FROM eclipse-temurin:21-jre-jammy    # 基础镜像 (使用 jre 不是 jdk, 镜像小)
LABEL maintainer="me@example.com"     # 元信息
WORKDIR /app                          # 工作目录 (自动创建)
COPY target/app.jar app.jar           # 复制文件
RUN groupadd -r app && useradd -r -g app app   # 创建非 root 用户
USER app                              # 切换用户 (安全)
EXPOSE 8080                           # 文档化端口 (不实际开放)
ENV JAVA_OPTS=""                      # 环境变量
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]    # exec 形式 (推荐)
CMD ["--spring.profiles.active=prod"]     # 默认参数 (可被覆盖)
```

**RUN vs CMD vs ENTRYPOINT**:
- `RUN`: 构建时执行 (生成新层)
- `CMD`: 默认启动命令 (可被 `docker run xxx` 末尾参数覆盖)
- `ENTRYPOINT`: 固定启动命令 (`docker run xxx args` 的 args 作为参数)

### 3. 镜像分层 & 缓存

每个 `RUN` / `COPY` / `ADD` 产生一层。 Docker 构建时按层缓存, 只要前面层没变就复用。

**实践**: 把变化频率低的放前面 (依赖), 变化频率高的放后面 (代码)。

```dockerfile
# 错误 (改代码就重下依赖)
COPY . /app
RUN mvn package

# 正确 (POM 没变, 直接复用 deps 层)
COPY pom.xml /app/
RUN mvn dependency:go-offline
COPY src /app/src/
RUN mvn package
```

### 4. 多阶段构建 (Multi-stage)

```dockerfile
# 阶段 1: 构建
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN mvn -B clean package -DskipTests

# 阶段 2: 运行 (不带 Maven / 源码)
FROM eclipse-temurin:21-jre-jammy
COPY --from=builder /build/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

**收益**: 最终镜像可从 1GB+ 降到 200MB 以下 (没有 Maven / JDK / 源码)。

### 5. Docker Compose 编排

```yaml
version: "3.8"
services:
  app:
    build: .
    image: blog-api:latest
    container_name: blog-api
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/blog
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_started }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
    restart: unless-stopped
    deploy:
      resources:
        limits: { memory: 1G, cpus: "1.0" }
    logging:
      driver: json-file
      options: { max-size: "10m", max-file: "3" }

  mysql:
    image: mysql:8.4
    environment: { MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: blog }
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5

  redis:
    image: redis:7.4-alpine
    volumes: [redis-data:/data]

volumes:
  mysql-data:
  redis-data:
```

**关键点**:
- `depends_on.condition`: 等依赖 healthy 再启动应用 (避免应用比 MySQL 早起来 → 连接失败)
- `restart: unless-stopped`: 异常退出自动重启
- `deploy.resources.limits`: 限内存 / CPU
- `logging`: 限日志大小, 防磁盘打爆
- volume 持久化数据 (容器删了, 数据还在)

### 6. 网络

```
默认: 同 compose project 内的服务通过 service name 互通 (DNS)
      mysql:3306 / redis:6379

自定义网络:
  networks:
    backend: { driver: bridge }
  services:
    app:
      networks: [backend, frontend]
```

### 7. 健康检查 (HEALTHCHECK)

- Dockerfile 里写 `HEALTHCHECK`, compose 也可覆盖
- 状态: `starting` → `healthy` / `unhealthy`
- 配合 `depends_on.condition: service_healthy` 实现启动编排
- Spring Boot Actuator 暴露 `/actuator/health`, k8s liveness/readiness 也用它

### 8. 容器化 JVM 注意事项

- JDK 10+ 默认自动识别 cgroup, 但仍 **显式设 -Xmx**
- 容器内存 limit = 1G → 推荐 -Xmx 取 50%-70% (`-Xmx700m`)
- 不要给 root, 用 `USER app`
- 日志写 stdout (`-Dlogging.file.name=` 不要设), 让 docker logging driver 接管

### 9. 镜像安全

- 用 **官方镜像 / Distroless / Alpine** (信任 + 体积小)
- **不带 shell** (Distroless): 黑客攻入也没 sh 可用
- 镜像扫描: Trivy / Docker Scout (`docker scout cves blog-api:latest`)
- 不存密钥到镜像 → 用 env 或 secret manager
- 用 `.dockerignore` 排除 `.git` `target` `*.env` 等

### 10. 推送 / 拉取私有仓库

```bash
docker login registry.example.com
docker tag blog-api:latest registry.example.com/blog/api:v2.1
docker push registry.example.com/blog/api:v2.1
```

## 三、工作流

```
本地: mvn package → docker build → docker compose up
CI/CD: git push → 流水线 mvn + buildx + push → k8s/服务器 pull + 重启
```

## 四、项目落地

博客 API:
1. 多阶段 Dockerfile (Maven 构建 + JRE 运行)
2. docker-compose.yml 一键起 app + mysql + redis + rabbitmq + minio + prometheus + grafana
3. 健康检查保证启动顺序
4. .dockerignore 排除非必要
5. 镜像扫描进 CI

## 五、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| 用 root 跑 | 容器逃逸风险大 | `USER app` |
| 不设 -Xmx | OOM 时 cgroup 杀进程, 看不到原因 | 显式 -Xmx + OOMKilled 监控 |
| 日志写文件 | 容器删了日志没了 | 写 stdout |
| 没 .dockerignore | `target/` 上 G 的二进制进镜像 | 加 .dockerignore |
| 用 latest tag | 无法回滚 | 用 git sha / semver tag |
| 数据库密码硬编码 | 镜像泄漏 = 密码泄漏 | 用 env / docker secret |
| 一次构建装所有依赖 | 改 1 行代码重下所有依赖 | 分层 (pom 先, 代码后) |
| 用 apt-get install 后没清理 | 镜像膨胀 | `&& rm -rf /var/lib/apt/lists/*` |
| 启动顺序错 | app 先于 mysql, 连接失败 | depends_on healthy |
| 容器跑很久但磁盘满 | 日志没限大小 | logging.options.max-size |

## 六、面试高频

1. Dockerfile 里 `CMD` 和 `ENTRYPOINT` 区别?
2. 多阶段构建为什么能减小镜像?
3. 容器里 JVM 怎么设内存? 容器 OOMKilled 怎么排查?
4. docker compose 怎么保证 MySQL 先启动?
5. 镜像 / 容器 / 仓库 关系?
6. 你的镜像怎么瘦身的? (Alpine / Distroless / 分层 / 多阶段)
7. 容器为什么不用 root?
8. 容器化 vs 物理机部署 优劣?
9. Docker 网络模式有哪些? (bridge / host / none / overlay)
10. Docker volume vs bind mount?

## 七、Docker vs k8s

Docker 适合: 开发 / 测试 / 小型生产
k8s 适合: 多节点 / 滚动发布 / 弹性扩缩

学习路径: 先把 Docker Compose 用熟, 再上 k8s。 本章只到 Compose, k8s 是下一阶段。
