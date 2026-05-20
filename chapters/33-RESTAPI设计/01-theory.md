# Chapter 33 REST API 设计 - 理论

> 前置：[[32-SpringBoot入门]]
> 后续：[[34-参数校验与异常处理]] → [[35-分层架构与事务]] → [[40-里程碑：博客APIv1]]
> 优先级：L3 面试高频  预计：3 小时

## 1. REST 是什么

REST = Representational State Transfer。 核心约束（Fielding 论文）：

- **资源**：URL 标识资源（名词），不是动作。
- **统一接口**：HTTP 方法 = 动作（GET/POST/PUT/PATCH/DELETE）。
- **无状态**：每次请求自包含，服务端不存会话（认证靠 token）。
- **可缓存**：响应可被中间层缓存（GET）。
- **分层系统**：客户端不感知后端是单体还是多层代理。

> 项目里达到「资源 + HTTP 方法 + JSON + 合理状态码」就够了，不要为了纯粹 REST 牺牲可读性。

## 2. 资源命名

| 规则 | 示例 | 反例 |
|---|---|---|
| 用**名词复数** | `/articles` | `/getArticles` `/article` |
| 用 ID 表示单个资源 | `/articles/42` | `/articles?id=42` |
| 嵌套表层级 | `/articles/42/comments` | `/comments/byArticle/42` |
| 动作放查询 / 子路径 | `/articles/42:publish` 或 `POST /articles/42/publish` | `/publishArticle?id=42` |
| 小写 + 连字符 | `/blog-posts` | `/BlogPosts` `/blog_posts` |

**层级别太深**：超过 2 层就该重新设计。 `/users/1/articles/2/comments/3/likes` ❌。

## 3. HTTP 方法语义

| 方法 | 语义 | 幂等 | 安全 | 典型 |
|---|---|---|---|---|
| GET | 读 | ✅ | ✅ | 列表 / 详情 |
| POST | 新建 / 非幂等动作 | ❌ | ❌ | 创建文章、发邮件 |
| PUT | **整体替换** | ✅ | ❌ | 全量更新 |
| PATCH | **部分更新** | ⚠（按设计） | ❌ | 改标题、改状态 |
| DELETE | 删除 | ✅ | ❌ | 删文章 |

- **幂等**：N 次请求和 1 次效果相同。 网络重试关键。
- **安全**：不改变服务端状态。

> PUT 整体替换：缺字段会被设为 null。 项目里多用 PATCH。

## 4. 状态码（背 10 个够用）

| 码 | 含义 | 典型 |
|---|---|---|
| 200 OK | 成功 | GET / PATCH / DELETE 成功 |
| 201 Created | 新资源已建 | POST 成功，建议返回 `Location` 头 |
| 204 No Content | 成功无返回体 | DELETE 成功 |
| 400 Bad Request | 参数错（语法 / 校验失败） | `@Valid` 失败 |
| 401 Unauthorized | **未认证**（没登录 / token 无效） | 未带 / 过期 JWT |
| 403 Forbidden | **未授权**（认证了但没权限） | 普通用户访问管理接口 |
| 404 Not Found | 资源不存在 | 文章 id 不存在 |
| 409 Conflict | 资源冲突 | 唯一约束、版本冲突 |
| 422 Unprocessable Entity | 语义错（业务规则失败） | 库存为 0 仍下单 |
| 500 Internal Server Error | 服务端异常 | 未捕获异常 |
| 503 Service Unavailable | 临时不可用 | 限流、依赖宕机 |

**别滥用 200**：返回 `{"code": 500, "msg": "..."}` 但 HTTP 状态 200 → 网关 / 监控 / 重试机制全失效。

## 5. 统一响应结构

```json
{
  "code": 0,                 // 业务码：0 成功，非 0 自定义
  "message": "ok",
  "data": { ... } | [ ... ] | null,
  "traceId": "abc-123"       // 便于排查
}
```

```java
public record ApiResponse<T>(int code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data)       { return new ApiResponse<>(0, "ok", data, MDC.get("traceId")); }
    public static <T> ApiResponse<T> fail(int c, String m) { return new ApiResponse<>(c, m, null, MDC.get("traceId")); }
}
```

- **HTTP 状态码**反映传输 / 协议层结果；
- **业务码**反映业务层结果（如 1001=用户不存在）。
- 两者各司其职，不要混。

## 6. DTO 分层

```
Request DTO   ←→  Controller   ←→  Service  ←→  Entity   ←→  DB
Response DTO  ←/                       ↑
                                       └── 内部领域对象（DO/Domain）
```

| 层 | 对象 | 作用 |
|---|---|---|
| 接口层 | `XxxRequest` / `XxxResponse` | 接收 / 返回，加校验注解 |
| 业务层 | Domain / Entity | 业务逻辑、ORM 映射 |
| 持久层 | PO（与 Entity 重合时合并） | DB 表结构 |

**为什么不直接用 Entity 当接口**：

- 暴露密码 hash、内部字段（`deleted`、`version`）。
- 接口字段需求变化频繁，污染领域模型。
- 接口和数据库耦合，DB 改字段就要改 API。

工具：MapStruct（编译期生成 mapper）。

## 7. 分页 / 排序 / 过滤

```
GET /articles?page=1&size=20&sort=createdAt,desc&status=published&keyword=java
```

```java
public record PageDTO<T>(List<T> records, long total, int page, int size) {}
```

**约定**：

- `page` 从 1 开始（用户视角友好）；内部转 `offset = (page-1)*size`。
- `size` **设上限**（如 100），防恶意拉万条。
- 排序字段**白名单**，防 SQL 注入。
- 大表别用 `count(*)` + offset，超过 1w 偏移用 keyset 分页（`where id > lastId limit N`）。

## 8. 版本化

| 方式 | 示例 | 评价 |
|---|---|---|
| URL 路径 ⭐ | `/v1/articles` | 简单直观，主流 |
| Header | `Accept: application/vnd.blog.v2+json` | 纯 REST，但调试不便 |
| Query | `/articles?v=2` | 不推荐 |

> 项目里用 `/api/v1/...`。 破坏性变更才升大版本；加字段不要升版本。

## 9. 错误处理（→ 34 章详解）

```java
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg));
    }
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleNotFound() { return ResponseEntity.status(404).body(ApiResponse.fail(404, "not found")); }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBiz(BusinessException e) { return ResponseEntity.status(422).body(ApiResponse.fail(e.code(), e.getMessage())); }
}
```

## 10. 幂等性设计（POST 类写接口）

- **客户端生成请求 id**：header `Idempotency-Key: uuid`，服务端用 Redis SETNX 去重。
- **乐观锁**：`update ... where id=? and version=?`。
- **唯一索引**：业务 key（订单号、外部交易号）建唯一索引，DB 兜底。

## 11. 项目里的「博客 API」规范模板

```
POST   /api/v1/articles               # 发文，返回 201 + Location
GET    /api/v1/articles?page&size     # 列表
GET    /api/v1/articles/{id}          # 详情
PATCH  /api/v1/articles/{id}          # 更新（部分）
DELETE /api/v1/articles/{id}          # 删除，204

POST   /api/v1/articles/{id}/comments # 评论
GET    /api/v1/articles/{id}/comments # 评论列表

POST   /api/v1/auth/login             # 登录
POST   /api/v1/auth/refresh           # 刷新 token
POST   /api/v1/auth/logout            # 退出
```

所有响应统一 `ApiResponse<T>`；所有 5xx 由 `@RestControllerAdvice` 兜底。

## 12. 常见坑

- 所有接口都 POST → 不可缓存、不可重试。
- 用 GET 改状态 / 删除 → 爬虫触发数据修改。
- 200 包错 → 监控 / 网关识别不到失败。
- 没分页上限 → 一次拉百万行 OOM。
- 返回 Entity 暴露内部字段 / 密码 hash。
- 排序字段直接拼 SQL → 注入。
- 创建接口不返回新资源 id → 客户端需再查一遍。

## 13. 面试高频

1. REST 的核心约束有哪些？
2. PUT vs PATCH 区别？哪个幂等？
3. 401 / 403 区别？
4. 状态码该用 HTTP 还是业务码？
5. 为什么要 DTO 不直接用 Entity？
6. 接口怎么做幂等？
7. 大表分页 offset 慢怎么办？
8. API 版本化怎么做，什么时候升版？

更多 → [[interview-bank|面试题库]] Web 区。
