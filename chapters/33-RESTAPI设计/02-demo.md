# Chapter 33 REST API 设计 - 实操 Demo

## Demo 目标

在 Boot 应用里落地 REST 设计最佳实践：资源路径 / HTTP 方法 / 统一响应 / keyset 分页 / HATEOAS 味道 / curl 全流程验证。

## 前置

- 32 章的 Boot 应用已跑通
- `PostService` / `CommentService` 已注入

## 一、资源路径设计

```
POST   /api/v1/posts                      # 发布文章
GET    /api/v1/posts?lastId=0&size=10     # 文章列表（keyset 分页）
GET    /api/v1/posts/{id}                 # 文章详情
PUT    /api/v1/posts/{id}                 # 全量更新
PATCH  /api/v1/posts/{id}/status         # 部分更新（改状态）
DELETE /api/v1/posts/{id}                 # 删除

POST   /api/v1/posts/{id}/comments        # 发评论（子资源）
GET    /api/v1/posts/{id}/comments        # 文章下评论列表
DELETE /api/v1/comments/{id}              # 删评论
```

## 二、统一响应包装

```java
public record Result<T>(int code, String msg, T data) {
    public static <T> Result<T> ok(T data) { return new Result<>(0, "ok", data); }
    public static Result<?> error(int code, String msg) { return new Result<>(code, msg, null); }
}
```

## 三、Controller

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public Result<PageResult<PostVO>> list(
        @RequestParam(defaultValue = "0") Long lastId,
        @RequestParam(defaultValue = "10") @Max(50) int size) {
        return Result.ok(postService.page(lastId, size));
    }

    @GetMapping("/{id}")
    public Result<PostDetailVO> get(@PathVariable Long id) {
        return Result.ok(postService.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostVO> create(@Valid @RequestBody PostCreateReq req,
                                 @AuthenticationPrincipal Long userId) {
        return Result.ok(postService.publish(userId, req));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id,
                                  @RequestBody @Valid StatusReq req,
                                  @AuthenticationPrincipal Long userId) {
        postService.setStatus(userId, id, req.status());
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        postService.delete(userId, id);
    }
}
```

## 四、分页 VO

```java
public record PageResult<T>(List<T> items, Long nextLastId, boolean hasMore) {
    public static <T> PageResult<T> of(List<T> items, int size) {
        boolean hasMore = items.size() == size;
        Long next = hasMore ? items.get(items.size()-1).id() : null;
        return new PageResult<>(items, next, hasMore);
    }
}
```

## 五、curl 全流程验证

```bash
BASE=http://localhost:8080/api/v1

# 发布文章 → 201
curl -s -X POST $BASE/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"REST 设计","summary":"摘要","content":"正文","publish":true}' | jq .

# 列表
curl -s "$BASE/posts?lastId=0&size=3" | jq .

# 详情
curl -s $BASE/posts/1 | jq .

# 改状态（归档）
curl -s -X PATCH $BASE/posts/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":2}' | jq .

# 删除 → 204（无 body）
curl -s -X DELETE $BASE/posts/1 -o /dev/null -w "%{http_code}"
```

期望输出：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "id": 1,
    "title": "REST 设计",
    "status": 1,
    "createdAt": "2025-01-01T12:00:00"
  }
}
```

## 六、失败场景：RESTful 反例

```bash
# ❌ 用 GET 删除
GET /api/deletePost?id=1

# ❌ 动词放路径
POST /api/publishPost

# ❌ 状态码全 200
{"code":404,"msg":"not found","data":null}  # HTTP status 还是 200

# ❌ 分页用 page/size（大 offset 慢）
GET /api/posts?page=1000&size=10   # 实际扫 1000*10 = 10000 行
```

改法对照：

| 反例 | 正确写法 |
|------|---------|
| `GET /deletePost?id=1` | `DELETE /posts/1` |
| `POST /publishPost` | `PATCH /posts/1/status` |
| 全 200 | 资源不存在 → 404，创建 → 201，无内容 → 204 |
| OFFSET 分页 | keyset：`lastId < ?` |

## 提交建议

```bash
git add src/main/java/com/example/blog/controller/
git commit -m "chapter 33: REST API v1 with unified response + keyset paging"
```
