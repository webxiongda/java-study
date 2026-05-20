# Chapter 40 里程碑：博客 API v1 - 项目任务

> 前置：[[31-Spring核心]] → [[39-GitHub实战]]
> 阶段二（21-40）的产出验收。 这一版还不要求 JWT 鉴权和缓存。

## 任务概述

基于 Spring Boot + MyBatis + MySQL 实现可演示的博客 CRUD API v1，作为后续认证 / 缓存扩展的底座。

## 量化验收清单

### 一、接口与数据

| # | 项 | 标准 |
|---|---|---|
| 1 | 接口数 | ≥ 8 个：article CRUD + 列表分页 + 发布/下架 + tag list + healthcheck |
| 2 | 统一响应 | `ApiResponse<T>(code, message, data)` |
| 3 | 错误码 | 用 `ErrorCode` 枚举（不裸抛 500） |
| 4 | 参数校验 | `@Valid + jakarta.validation`，至少 5 个字段约束 |
| 5 | 全局异常处理 | `@RestControllerAdvice` 覆盖 Validation + Business + 其他 |
| 6 | 列表分页 | `PageHelper`，返回 `PageDTO<T>(total, pageNum, pageSize, records)` |
| 7 | 排序 | 至少 2 个排序字段，白名单防注入 |

### 二、代码质量

| # | 项 | 标准 |
|---|---|---|
| 8 | 分层 | Controller / Service / Mapper / DTO / Domain 各司其职 |
| 9 | 事务 | 写接口加 `@Transactional`，至少 1 处用到 `propagation` |
| 10 | DTO vs Domain | Mapper 返回 Domain，Controller 出参 DTO，禁止 Domain 直接外露 |
| 11 | 日志 | Service 关键路径 INFO 输出参数；异常带 stack |
| 12 | 命名 | 包结构 + 类名 + 方法名见名知意，无 `util1 / aaa` |

### 三、测试与文档

| # | 项 | 标准 |
|---|---|---|
| 13 | 单测 | ≥ 5 个 JUnit 5 用例（Service 层） |
| 14 | 集成测试 | ≥ 2 个 `@SpringBootTest` + MockMvc 端到端 |
| 15 | OpenAPI | Swagger UI 可访问，每个 Controller 标 `@Tag`，每个接口标 `@Operation` |
| 16 | README | 含启动命令、数据库初始化 SQL、Postman/curl 示例 |

### 四、Git 与运行

| # | 项 | 标准 |
|---|---|---|
| 17 | 提交粒度 | 每章一个清晰 commit；本里程碑打 tag `v1.0.0` |
| 18 | `.gitignore` | 排除 target/ idea/ logs/ env |
| 19 | 启动 | `mvn spring-boot:run` 一次成功，端口 8080 |
| 20 | 健康检查 | `/actuator/health` 返回 UP |

## 提交

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 复盘问题

1. 列出全部接口和它们的依赖（DB / 外部服务）。
2. 假如一个 Service 方法被两个不同事务嵌套调用，会发生什么？
3. 哪个接口在 1000 QPS 下会先撑不住？为什么？
4. 这一版与 v2（50 章）的差距是什么？
