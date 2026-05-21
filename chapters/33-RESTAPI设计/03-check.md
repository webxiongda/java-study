# Chapter 33 REST API 设计 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：REST 的 6 个约束是什么？"无状态"在实际开发中意味着什么？

**参考答案：**

**6 个约束**（Roy Fielding 博士论文）：

| 约束 | 含义 | 违反案例 |
|------|------|---------|
| Client-Server | 前后端分离，职责分离 | Server 直接渲染 HTML 页面 |
| Stateless | 每个请求携带完整上下文，服务端不存会话 | Session 存在服务端内存 |
| Cacheable | 响应应标明可否缓存 | 所有接口不设 Cache-Control |
| Uniform Interface | 统一接口（资源 URL + HTTP 动词） | `/doSomething?action=delete` |
| Layered System | 中间层（代理/负载均衡）透明 | 把负载均衡 IP 暴露给客户端 |
| Code on Demand（可选）| 服务端可下发可执行代码 | JS 脚本下发 |

**"无状态"的实际含义**：

- 服务端不存 Session（用户登录状态靠 JWT Token 随请求携带，见 42 章）。
- 好处：**水平扩展**——任何一台服务器都能处理任何请求，不需要粘性会话（sticky session）。
- 代价：**每次请求都要认证**（Authorization header），Token 要携带足够信息（用户 id / 角色等）。

**与 Session 的对比**：

| 维度 | Session（有状态）| JWT（无状态）|
|------|----------------|------------|
| 扩容 | 需要共享 Session 存储（Redis）| 直接加机器 |
| 注销 | 删 Session 立即生效 | Token 未过期仍有效（需黑名单） |
| 存储 | 服务端存 Session | 客户端存 Token |
| 性能 | 每次查 Redis | 本地验证签名 |

**关键点**："REST 无状态"不是说不能用数据库，而是说**HTTP 层**的请求间不应该依赖服务端内存的共享状态。

---

## Q2（概念）：以下场景各应用哪个 HTTP 方法和状态码？

1. 创建文章
2. 查询文章列表
3. 修改文章部分字段（仅 title）
4. 删除文章
5. 文章不存在时的查询
6. 参数校验失败
7. 服务器内部异常

**参考答案：**

| 场景 | Method | 路径 | 成功 code | 失败 code |
|------|--------|------|-----------|---------|
| 创建文章 | POST | `/api/v1/posts` | **201 Created** | 400（参数错）|
| 查询列表 | GET | `/api/v1/posts` | **200 OK** | — |
| 修改 title | PATCH | `/api/v1/posts/{id}` | **200 OK** | 404（不存在）|
| 删除文章 | DELETE | `/api/v1/posts/{id}` | **204 No Content** | 404 |
| 文章不存在 | GET | `/api/v1/posts/999` | — | **404 Not Found** |
| 参数校验失败 | 任意 | 任意 | — | **400 Bad Request** |
| 服务器异常 | 任意 | 任意 | — | **500 Internal Server Error** |

**常犯错误**：

```java
// ❌ 全部返回 200，code 字段传状态
return Result.error(404, "not found");  // HTTP status 却是 200

// ✅ HTTP 状态码才是 REST 协议的状态
@ResponseStatus(HttpStatus.NOT_FOUND)
throw new PostNotFoundException(id);
// 全局异常处理器 @ExceptionHandler 处理
```

**幂等性**：

- GET / PUT / DELETE：幂等（多次调用结果一样）。
- POST：非幂等（多次 POST 创建多条）。
- PATCH：不一定（取决于实现）。

---

## Q3（实操）：以下 Controller 有 7 处 REST 设计问题，找出并改正。

```java
@RestController
public class PostController {

    @GetMapping("/deletePost")                    // ①
    public Map<String, Object> delete(Long id) {
        postService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("result", "ok");               // ②
        return result;
    }

    @PostMapping("/getPostList")                  // ③
    public List<Post> list(                       // ④
        @RequestParam int page,
        @RequestParam int size) {
        return postService.findPage(page, size);  // ⑤ OFFSET 分页
    }

    @PostMapping("/updateTitle")                  // ⑥
    public String updateTitle(Long id, String title) {
        postService.updateTitle(id, title);
        return "success";                          // ⑦
    }
}
```

**参考答案（改后）：**

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ① 删除用 DELETE，路径是名词资源
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)        // ② 204 无内容
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal Long userId) {
        postService.delete(userId, id);
        // 无返回体（标准 REST 删除 204）
    }

    // ③ 列表用 GET；路径是名词
    // ④ 返回 VO 而非 Entity
    // ⑤ keyset 分页代替 OFFSET
    @GetMapping
    public Result<PageResult<PostVO>> list(
        @RequestParam(defaultValue = "0") Long lastId,
        @RequestParam(defaultValue = "10") @Max(50) int size) {
        return Result.ok(postService.page(lastId, size));
    }

    // ⑥ 部分更新用 PATCH，路径 /{id}
    // ⑦ 统一响应包装
    @PatchMapping("/{id}")
    public Result<Void> patch(@PathVariable Long id,
                              @RequestBody @Valid PostPatchReq req) {
        postService.patch(id, req);
        return Result.ok(null);
    }
}
```

**7 处问题**：

1. `@GetMapping("/deletePost")` → 删除应 DELETE，路径是名词。
2. 手拼 Map 响应 → 统一 `Result<T>` 包装。
3. `@PostMapping("/getPostList")` → GET 是查询的语义；路径 `/posts`。
4. 返回 `List<Post>` Entity → 应转 VO（剔除 password / is_deleted）。
5. OFFSET 分页 → keyset。
6. `@PostMapping("/updateTitle")` → 部分更新用 PATCH，路径 `/{id}`。
7. 返回 `"success"` 字符串 → 统一响应结构。

---

## Q4（实操）：设计"评论子资源"的完整 REST API，包括：发评论、列表（分页）、删除。要求带 curl 示例。

**参考答案：**

**路径设计**：

```
POST   /api/v1/posts/{postId}/comments        # 发评论
GET    /api/v1/posts/{postId}/comments        # 分页列表
DELETE /api/v1/comments/{id}                  # 删评论（不需要 postId）
```

**Controller**：

```java
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/v1/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<CommentVO> create(
        @PathVariable Long postId,
        @RequestBody @Valid CommentCreateReq req,
        @AuthenticationPrincipal Long userId) {
        req = req.withPostId(postId).withUserId(userId);
        return Result.ok(commentService.create(req));
    }

    @GetMapping("/api/v1/posts/{postId}/comments")
    public Result<PageResult<CommentVO>> list(
        @PathVariable Long postId,
        @RequestParam(defaultValue = "0") Long lastId,
        @RequestParam(defaultValue = "20") @Max(50) int size) {
        return Result.ok(commentService.page(postId, lastId, size));
    }

    @DeleteMapping("/api/v1/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal Long userId) {
        commentService.delete(userId, id);   // 校验是自己或管理员
    }
}
```

**curl 示例**：

```bash
# 发评论 → 201
curl -X POST http://localhost:8080/api/v1/posts/5/comments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"写得很好","parentId":0}'

# 列表
curl "http://localhost:8080/api/v1/posts/5/comments?lastId=0&size=20"

# 删评论 → 204（无 body）
curl -X DELETE http://localhost:8080/api/v1/comments/42 \
  -H "Authorization: Bearer $TOKEN" -w "%{http_code}"
```

**关键设计决策**：

1. **子资源路径**：`/posts/{postId}/comments` 表明评论属于文章，语义清晰。
2. **删除路径**：`/comments/{id}` 不带 postId，因为 commentId 全局唯一，拿到 id 就够了。
3. **权限**：删除时 Service 层校验 `userId == comment.userId || isAdmin`，不要在 URL 里传 userId。
4. **无 body DELETE**：204 不返回任何内容（RESTful 标准），让前端自行从列表移除。

---

## Q5（综合）：设计博客的"文章搜索"接口，要支持：关键词 / 标签筛选 / 状态筛选 / 排序 / 分页。请给出路径设计 + 请求/响应 Schema + 防滥用限速方案。

**参考答案：**

### 一、路径设计

```
GET /api/v1/posts/search?kw=java&tagIds=1,2&status=1&sort=createdAt_desc&lastId=0&size=10
```

**为什么用 GET**：

- 搜索是查询操作，GET 语义正确、可缓存、可收藏 URL。
- 参数多也没关系，路径用 query string。
- 如果参数复杂到超 URL 长度（8 KB），改 `POST /posts/search`（语义牺牲，换传参方便）。

### 二、请求 Schema

```java
public record PostSearchReq(
    @Size(max=50)
    String kw,                           // 关键词

    @Valid
    List<@Positive Long> tagIds,         // 标签 id 列表

    @Min(0) @Max(2)
    Integer status,                      // 0=草稿 1=发布 2=归档

    @Pattern(regexp="(id|createdAt|viewCount)_(asc|desc)")
    String sort,                         // 白名单正则

    @Min(0)
    Long lastId,

    @Min(1) @Max(50)
    Integer size
) {}
```

### 三、响应 Schema

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "items": [
      {
        "id": 123,
        "title": "Java 深入理解 GC",
        "summary": "...",
        "userName": "xiong",
        "tags": ["java","jvm"],
        "status": 1,
        "viewCount": 5000,
        "createdAt": "2025-01-01T12:00:00Z"
      }
    ],
    "nextLastId": 120,
    "hasMore": true,
    "total": null
  }
}
```

> `total` 不统计（大表 COUNT(*) 慢），告知 `hasMore` 让前端决定是否继续翻页。

### 四、防滥用限速

**1. 参数校验**（Bean Validation）

```java
@Max(50)   // size 最大 50
@Size(max=50)  // kw 最多 50 字符
List<Long> tagIds  // 长度限制
```

**2. 接口限速（Bucket4j / Redis 令牌桶）**

```java
@GetMapping("/search")
public Result<PageResult<PostVO>> search(
    @AuthenticationPrincipal Long userId,
    @Valid PostSearchReq req) {
    rateLimiter.tryConsume(userId, 30, Duration.ofMinutes(1));  // 每人每分钟 30 次
    return Result.ok(postService.search(req));
}
```

**3. 全文搜索不在 MySQL**

`kw` 非空时走 Elasticsearch（Canal 同步），MySQL 只做精确条件过滤。

**4. 慢查询熔断**

用 Resilience4j 包装 search 调用，超时 3 秒降级返回"搜索繁忙请稍后"。

### 五、版本化策略

```
/api/v1/posts/search   # 当前
/api/v2/posts/search   # 将来字段变更时新开，v1 保留 6 个月
```

版本号在路径里，不用 `Accept: vnd.xxx+v2`（可读性好，CDN 友好）。

**关键点**：

1. **GET vs POST 搜索**：参数不超 URL 限制，GET 可缓存优先选；超出或需要复杂 body 才 POST。
2. **排序白名单**：`sort` 参数不能直接拼 SQL（SQL 注入），必须在 Service / Mapper 层做白名单校验。
3. **total 不统计**：首页搜索一般用"有没有更多"代替"共 N 条"，省一次 COUNT。
4. **全文搜索不在 DB**：生产 kw 查询一定走搜索引擎（ES/Meilisearch），MySQL LIKE 撑不住。
