# Chapter 50 里程碑：博客 API v2 - 项目任务

> 前置：[[41-认证基础]] → [[49-文件上传]]
> 这是阶段三的产出验收，必须给面试官 / 简历看的最小可信版本。

## 任务概述

交付**简历级**博客 API v2：在 v1（40 章）的基础上加上认证、权限、缓存、安全、文件、可观测，并打包成可一键启动的 Docker 工程。

## 量化验收清单

### 一、功能完整度

| # | 项 | 标准 |
|---|---|---|
| 1 | 接口数 | ≥ 15 个 REST 接口（auth 4 + article 5 + comment 3 + tag 2 + upload 1） |
| 2 | 统一响应 | 所有接口返回 `ApiResponse<T>`，错误用 `ErrorCode` 枚举（34 章） |
| 3 | 认证 | JWT Access(15m) + Refresh(7d) 双 token，主动登出生效（42 章） |
| 4 | 权限 | 至少 2 个角色（USER/ADMIN）+ `@PreAuthorize` 数据级控制（43 章） |
| 5 | 缓存 | 详情接口走 Cache-Aside + 防穿透/击穿/雪崩三件套（47 章） |
| 6 | 文件 | 头像上传带 MIME 嗅探 + 哈希命名（49 章） |
| 7 | 文档 | Swagger UI 可访问，所有接口标 `@Operation`（38 章） |

### 二、质量指标

| # | 项 | 标准 | 验证 |
|---|---|---|---|
| 8 | 单测覆盖 | Service 层 ≥ 70%（jacoco） | `mvn verify` 看报告 |
| 9 | 集成测试 | 关键路径（登录/发文/评论/上传）各 ≥ 1 个 MockMvc 测试 | `mvn test` 全绿 |
| 10 | 参数校验 | 所有 `@RequestBody` 有 jakarta.validation 注解 | 故意发非法请求 → 10001 |
| 11 | 全局异常 | Business / Validation / System 三类各有处理（34 章） | 测试覆盖三类 |
| 12 | SQL 索引 | 至少 3 个复合索引（如 author+status、tag、created_at） | `SHOW INDEX` |
| 13 | 日志 | 所有请求带 traceId，error 日志含上下文（23 章） | grep 一条 traceId 串完整链路 |

### 三、性能指标（本机压测，参考值）

| # | 项 | 标准 | 工具 |
|---|---|---|---|
| 14 | 详情接口 P99 | < 50ms（命中缓存） | wrk / JMeter |
| 15 | 列表接口 P99 | < 200ms（500 QPS） | 同上 |
| 16 | 登录接口 P99 | < 300ms（BCrypt 慢哈希正常） | 同上 |
| 17 | OOM 测试 | 持续压测 30 分钟内存稳定，无 Full GC > 1s | gc.log |

### 四、可部署性

| # | 项 | 标准 |
|---|---|---|
| 18 | Docker 一键起 | `docker compose up -d` 启动 app + mysql + redis + rabbitmq（56 章） |
| 19 | 环境分离 | dev / prod yml；密码用 `${ENV}` 注入 |
| 20 | 健康检查 | `/actuator/health/liveness` + `/readiness` |
| 21 | 镜像大小 | blog-api:latest < 250MB |

### 五、可面试

| # | 项 | 标准 |
|---|---|---|
| 22 | README | 项目背景 / 技术栈 / 启动 / 接口截图 / 架构图 |
| 23 | 亮点清单 | 列 5 个技术亮点（如「缓存三件套」「JWT 双 token + Redis 黑名单」） |
| 24 | 踩坑清单 | 列 3 个真踩过的坑 + 解决（用 `mistakes.md`） |
| 25 | 2 分钟介绍 | 录一段口播或写成稿子，能流畅讲完 |

## 推荐项目结构

```
blog-api/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
├── sql/
│   └── init/001-schema.sql
├── src/main/java/com/example/blog/
│   ├── BlogApplication.java
│   ├── config/         (Security, OpenAPI, Redis, Mq, Async)
│   ├── controller/     (Auth, Article, Comment, Tag, Upload, Admin)
│   ├── service/
│   ├── mapper/
│   ├── domain/
│   ├── dto/
│   ├── common/         (ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler)
│   ├── security/       (JwtAuthFilter, JwtService, TokenStore)
│   ├── cache/
│   └── filter/         (TraceIdFilter, RateLimitFilter)
└── src/test/...
```

## 提交里程碑

```bash
git tag v2.0.0
git push --tags
docker compose up -d
curl :8080/actuator/health   # {"status":"UP"}
```

## 复盘问题

1. 比 v1 多了哪 5 个能力？为什么先做这些不做别的？
2. 假如 QPS 突然涨 10 倍，第一个瓶颈在哪？怎么改？
3. JWT 怎么撤销？有何代价？
4. 缓存「击穿」和「穿透」区别？分别用哪个方案？
5. 项目里你最不满意的一个设计是什么？怎么改进？
