# Chapter 33 REST API 设计 - 项目任务

## 任务概述

把博客的 CRUD 从"能跑"升级到"符合 RESTful 规范"：正确的 HTTP 方法、状态码、统一响应、keyset 分页、版本化路径。

## 业务背景

32 章写的接口只是"能跑"，但路径、状态码、响应格式都没规范。这一章做一次「API 大扫除」，产出一份可以发给前端同学的接口文档。

## 任务拆解

### Step 1：统一响应结构

```java
// common 模块
public record Result<T>(int code, String msg, T data) {
    public static <T> Result<T> ok(T data) { return new Result<>(0, "ok", data); }
    public static Result<?> error(int code, String msg) { return new Result<>(code, msg, null); }
}

public record PageResult<T>(List<T> items, Long nextLastId, boolean hasMore) {}
```

把所有 Controller 的返回类型改为 `Result<XxxVO>`。

### Step 2：资源路径整改

按以下规范改路径：

| 操作 | 改前（乱） | 改后（规范）|
|------|----------|------------|
| 查列表 | `GET /postList` | `GET /api/v1/posts` |
| 发文章 | `POST /createPost` | `POST /api/v1/posts` |
| 改状态 | `POST /changeStatus` | `PATCH /api/v1/posts/{id}/status` |
| 删文章 | `GET /deletePost?id=1` | `DELETE /api/v1/posts/{id}` |
| 发评论 | `POST /addComment` | `POST /api/v1/posts/{id}/comments` |

### Step 3：状态码

- 创建 → 201
- 成功无内容（删除）→ 204
- 资源不存在 → 404
- 参数错误 → 400

所有 HTTP 状态码不能全返回 200。

### Step 4：keyset 分页

把所有 `page/size` OFFSET 分页改成 `lastId/size` keyset 分页，Service 层改 SQL（28-29 章的 XML 已有），Controller 把 nextLastId 放到响应里。

### Step 5：curl 验证脚本

写 `scripts/api-test.sh`，覆盖所有接口（列表 / 详情 / 创建 / 改状态 / 删除 / 发评论），每个用 jq 检查 `code == 0` 且关键字段存在。

### Step 6：错误路径测试

在脚本里加：

- 请求不存在的文章 → 断言 HTTP 404
- 传非法 size=999 → 断言 HTTP 400

## 交付物

- [ ] `common/Result.java` + `common/PageResult.java`
- [ ] `PostController.java`（6 个接口，路径全规范）
- [ ] `CommentController.java`（3 个接口）
- [ ] `scripts/api-test.sh`：全接口 curl 验证脚本
- [ ] `docs/api-design.md`：接口设计决策记录（为什么用 PATCH 不用 PUT）

## 验收清单

| 项 | 标准 |
|----|------|
| 路径全是名词 | grep `@GetMapping` 无动词（list/create/delete）|
| 状态码正确 | 创建 201，删除 204，不存在 404 |
| 统一响应 | 所有接口返回 `{"code":0,"msg":"ok","data":{...}}` |
| keyset 分页 | 响应含 nextLastId + hasMore，无 page/offset |
| 错误路径 | 404 / 400 有标准 JSON 错误体 |

## 扩展挑战

1. **API 版本策略**：实现 v1/v2 两个版本的同一接口共存，v2 新增 `summary` 字段，v1 不变。
2. **ETag 缓存**：文章详情接口加 `ETag`，二次请求走 `If-None-Match` 返回 304。
3. **限速**：用 Bucket4j 给搜索接口加每分钟 30 次限速，超出返回 429。
4. **HATEOAS**：给文章详情加 `_links: {self, comments, author}`，用 Spring HATEOAS 实现。
