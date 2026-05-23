# Chapter 60 总结与进阶路线 - 实操 Demo

## Demo 目标

把博客项目打包成 **可写进简历的样板**:

1. README 一句话能让面试官看懂
2. 5 个简历可写的技术亮点
3. STAR 格式的项目描述
4. 自我评估表 (60 章哪些掌握 / 哪些模糊)

## Demo 1: 项目 README.md (面试官看的)

`README.md`:

```markdown
# Blog API - Spring Boot 3 + Java 21 博客后端

一个面向中小型博客 / 内容站的后端 API, 用 60 章学习路径打磨。
**一键启动**: `docker compose up -d`, 1 分钟出 `/actuator/health`。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / 框架 | Java 21 (虚拟线程) + Spring Boot 3.3 |
| 数据 | MySQL 8.4 + JPA / MyBatis + Flyway |
| 缓存 | Redis 7.4 (单 key + ZSET + 分布式锁 Redisson) |
| 消息 | RabbitMQ 3.13 (异步事件) |
| 文件 | MinIO (S3) + Tika 类型校验 |
| 安全 | Spring Security + JWT + RBAC |
| 监控 | Micrometer + Prometheus + Grafana |
| 测试 | JUnit5 + Mockito + Testcontainers (覆盖率 65%) |
| 部署 | Docker Compose + GitHub Actions CI + Trivy 扫描 |

## 核心能力

- **认证授权**: JWT 双 token (access 15min + refresh 7d), RBAC, 接口级权限
- **缓存**: Caffeine L1 + Redis L2 双层, 防穿透 (空值) / 击穿 (分布式锁) / 雪崩 (随机 TTL)
- **异步**: @Async + 线程池隔离 (biz / file / sched), 配合虚拟线程
- **文件**: 类型校验 (Magic Number) + 配额 + MinIO 对象存储 + 缩略图
- **性能**: 详情接口 P99 612ms → 3ms (索引 + N+1 优化 + 双层缓存)
- **可观测**: 完整 Grafana 大盘 + JFR / async-profiler / Arthas 工具链

## 快速开始

\```bash
git clone <repo>
cd blog
cp .env.example .env
docker compose up -d
curl http://localhost:8080/actuator/health    # {"status":"UP"}
open http://localhost:3000                    # Grafana
\```

## 文档

- [架构设计](docs/architecture.md)
- [性能报告](docs/PERFORMANCE.md)
- [API 文档](http://localhost:8080/swagger-ui.html)

## License
MIT
```

## Demo 2: 简历技术亮点 (5 条, 每条带数字)

`docs/RESUME.md`:

```markdown
# 项目: 博客 API (Spring Boot 3 + Java 21)

**一句话**: 独立开发的全栈博客后端, 完成从需求拆解到 Docker 上线全流程,
QPS 峰值 9200, P99 < 5ms, 测试覆盖率 65%。

## 技术亮点

### 1. 双层缓存 + N+1 优化, 详情接口 P99 从 612ms → 3ms (200x 提升)

- 背景: 列表 / 详情接口因 N+1 查询和无缓存, 高并发下 DB 打爆 (单库 CPU 95%)
- 改造: Redis (L2) + Caffeine (L1) 双层缓存, 缓存空值 + 随机 TTL 防雪崩,
        N+1 改为 `IN` 批量 + Map 关联, 加 `(post_id, created_at)` 复合索引
- 收益: P99 从 612ms 降到 3ms, QPS 从 142 提到 9200, DB 流量下降 92%
- 关键文件: `service/PostService.java`, `config/CacheConfig.java`

### 2. 线程池隔离 + 虚拟线程, @Async 任务零阻塞主流程

- 背景: 文件上传 / 发送邮件 / 索引重建 用一个线程池, 慢任务拖垮快任务
- 改造: 拆 biz / file / sched 三个独立池, 每个池独立 metrics, 部分 IO 任务用虚拟线程
- 收益: 文件上传超时不再影响业务接口, P99 抖动从 ±300ms 降到 ±20ms
- 关键文件: `config/AsyncConfig.java`, `service/file/FileUploadService.java`

### 3. JVM + GC 调优, Full GC 从每天 5 次到 0

- 背景: 老版本 -Xmx2g 默认 G1, Full GC 每日 5 次, 每次停顿 800ms (影响所有用户)
- 排查: GC 日志 + JFR 录制 + MAT 堆 dump 分析, 定位是 Caffeine 写入未限流 + 缓存对象过大
- 改造: Caffeine maxSize + soft eviction, 大对象拆 + 升级 Generational ZGC
- 收益: Full GC 0 次, P99 从 GC 影响 800ms 降为基准 + 5ms
- 关键文件: `Dockerfile` (JVM 参数), `docs/jvm-tuning.md`

### 4. 完整测试体系, 覆盖率 65% + Testcontainers 真 MySQL 集成

- 背景: 初版无测试, 改任何代码靠手测, 多次因小改动导致回归 bug
- 改造: 单元 (Mockito) + 切片 (MockMvc) + 集成 (Testcontainers) 三层, JaCoCo 阈值 60% 强制,
        PIT mutation testing score 52%
- 收益: P1 回归 bug 数下降 70%, PR 自动 CI 检查不达标阻断合并
- 关键文件: `src/test/`, `.github/workflows/ci.yml`

### 5. Docker 化 + CI/CD, 一键启动 + 滚动部署 + 漏洞扫描

- 背景: 部署靠 SSH 上服务器 mvn package, 易遗漏配置, 部署时间 20 分钟
- 改造: 多阶段 Dockerfile (镜像 250MB, 非 root + healthcheck), docker-compose 一键起 7 服务,
        GitHub Actions 跑 test + build + Trivy 扫描 + ghcr.io 推送 + deploy.sh 滚动
- 收益: 部署从 20 分钟到 90 秒, 镜像漏洞自动阻断, 新人 onboard 时间从半天到 10 分钟
- 关键文件: `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`
```

## Demo 3: STAR 格式答辩稿

`docs/INTERVIEW-STAR.md`:

```markdown
# 90 秒项目讲法 (STAR)

**Situation (背景)**:
我学完 60 章 Java 后端课, 想做一个能完整体现"能交付生产级 Java 后端"
能力的项目, 选了博客 API, 因为它能覆盖 CRUD、认证、缓存、文件、消息、
监控、部署所有典型场景。

**Task (任务)**:
独立从 0 到 1 完成, 包括需求拆解、技术选型、编码、测试、部署、文档化。
目标: 一键启动 / P99 < 50ms / 测试覆盖率 60% / 简历可写。

**Action (做了什么)**:
后端用 Spring Boot 3 + Java 21 + MySQL + Redis + RabbitMQ + MinIO,
安全用 Spring Security + JWT 双 token + RBAC, 性能上做了双层缓存 +
N+1 优化 + JVM 调优, 部署用多阶段 Dockerfile + docker-compose +
GitHub Actions, 监控用 Prometheus + Grafana。

**Result (结果)**:
详情接口 P99 从 612ms 优化到 3ms (200x), QPS 9200, 测试覆盖率 65%,
镜像 250MB, 一键 90 秒上线。 关键决策都写在 docs/ 下,
开源在 GitHub: <repo-url>
```

## Demo 4: 60 章自评表

`docs/SELF-ASSESS.md`:

```markdown
# 60 章自评

| 模块 | 章节 | 掌握度 | 备注 |
|---|---|---|---|
| Java 基础 | 1-15 | ✅✅✅ | 集合 / 异常 / IO 熟 |
| 多线程 | 16-23 | ✅✅ | AQS 细节需复习 |
| JDK 21 新特性 | 24-29 | ✅✅ | 虚拟线程能讲, Pattern 用得少 |
| 工程能力 | 30 | ✅✅✅ | Git / Maven / 调试熟 |
| Spring | 31-39 | ✅✅ | AOP / 事务能讲, IoC 实现细节模糊 |
| 博客 v1 | 40-45 | ✅✅✅ | 安全相关已上手 |
| 缓存 + 文件 | 46-50 | ✅✅ | Redis 高级用法 (Stream) 还需练 |
| 并发深入 | 51-55 | ✅✅ | JVM dump 分析过一次 |
| 部署 + 测试 | 56-58 | ✅✅✅ | Docker / CI 熟 |
| 系统设计 | 59 | ✅ | 5 道题练过, 但临场可能慌 |
| 总结 | 60 | ✅✅✅ | 当前章 |

## 下一步 (3 个月)

1. **分布式**: Spring Cloud / Dubbo / Seata, 拆 1 个子服务
2. **大数据**: Kafka Streams + ClickHouse 做日志分析
3. **k8s**: 把 Docker Compose 改成 Helm Chart
4. **算法**: LeetCode 100 道 + 系统设计 10 道
5. **博客**: 把博客项目 + 学习笔记发到自己博客 / 掘金

## 1 年路线

- 拿到 P6 Offer
- 主导 1 个中等规模 (10w QPS) 系统重构
- 写 2 个开源工具 + 10 篇技术文
```

## 提交

```bash
git add README.md docs/
git commit -m "ch60: project packaging + resume + self assessment"
git tag v1.0
git push origin main --tags
```
