# Chapter 43 权限控制 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：RBAC 和 ABAC 的差别？项目里什么时候该用 ABAC？

**参考答案：**

| 维度 | RBAC | ABAC |
|------|------|------|
| 核心 | 角色 → 权限 | 属性 → 策略表达式 |
| 例子 | `hasRole("ADMIN")` | `user.dept == resource.dept && action == "read"` |
| 配置方式 | 数据库表关联 | 策略引擎（OPA、AWS IAM）|
| 学习曲线 | 低 | 高 |
| 适用 | 90% 业务系统 | 多租户、云权限、金融合规 |

**判断要不要 ABAC**：

- 同一角色但行为不同（不同地区销售看不同客户）→ ABAC。
- 角色组合爆炸（管理员 × 区域 × 产品线 × 客户类型）→ ABAC。
- 权限要根据时间 / IP / 设备动态决定 → ABAC。
- 否则 → RBAC + 数据级权限（owner 校验）足够。

**项目常见做法**：

- 90% 接口走 RBAC（角色 + 权限 code）。
- 数据级（"只能看自己的""只能改部门的"）放 `@PreAuthorize` 或 SQL `WHERE` 注入。
- 上 ABAC 之前先把 RBAC 玩透，过早抽象只会维护成本爆炸。

---

## Q2（概念）：`@PreAuthorize` 和 `@PostAuthorize` 的区别？什么时候用 `@PostAuthorize`？

**参考答案：**

### 差异

| 注解 | 时机 | SpEL 可访问 |
|------|------|-------------|
| `@PreAuthorize` | 方法**调用前** | 方法参数 `#id` + `authentication` |
| `@PostAuthorize` | 方法**返回后** | 返回值 `returnObject` + `authentication` |

### `@PreAuthorize` 适合：

- 角色检查：`hasRole('ADMIN')`
- 权限检查：`hasAuthority('post:write')`
- 参数级：`#userId == authentication.principal.id`

### `@PostAuthorize` 适合：

```java
@PostAuthorize("returnObject.authorId == authentication.principal.id or hasRole('ADMIN')")
public Post getById(Long id) {
    return postMapper.selectById(id);   // 查到才能判断 authorId
}
```

**为什么不能用 @PreAuthorize**：调用前不知道 `id` 对应的文章作者是谁，要先查 DB。

### 性能注意

- `@PostAuthorize` 即使返回了也要校验，**业务方法照样执行**。
- 如果方法有副作用（写库、发消息），别用 `@PostAuthorize`——已经执行了！只能配合纯查询方法。
- 真正高安全场景：把 `authorId` 也作为参数传进来，用 `@PreAuthorize` 提前拦。

### 替代方案：返回前过滤

```java
@PreAuthorize("authentication.principal != null")
public List<Post> myPosts() {
    Long uid = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return postMapper.selectByAuthor(uid);   // SQL 层直接限制，更安全
}
```

**最佳实践**：能在 SQL 层 `WHERE author_id = #{uid}` 解决的，别靠注解过滤。

---

## Q3（实操）：以下代码有 5 处 RBAC 反模式，找出并改正。

```java
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    @Autowired private PostService postService;

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        String token = req.getHeader("Authorization").substring(7);
        Long uid = JwtUtil.parseUserId(token);                     // ①
        Post p = postService.getById(id);
        if (!p.getAuthorId().equals(uid)) {                        // ②③
            throw new RuntimeException("无权操作");
        }
        postService.delete(id);
        return Result.ok();
    }

    @GetMapping("/admin/all")
    public Result<List<Post>> adminAll() {                         // ④
        return Result.ok(postService.findAll());
    }
}

@Service
public class PostService {
    @Transactional
    public void delete(Long id) {                                   // ⑤
        postMapper.deleteById(id);
    }
}
```

**参考答案：**

**① 在 Controller 里手动解析 token**

破坏 Spring Security 链，且 Controller 充满样板代码。应用 `Authentication` 参数注入：

```java
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id, Authentication auth) {
    postService.delete(id);   // 鉴权下沉到 Service
    return Result.ok(null);
}
```

**② 鉴权写在 Controller**

下次 BatchDeleteController 又要重写一遍。规则放 Service `@PreAuthorize` 集中。

**③ 没考虑 ADMIN 能删任何文章**

`p.authorId.equals(uid)` 把管理员也拦了。改：

```java
@PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
public void delete(Long id) { ... }
```

**④ 用 URL 路径"看起来像"管理后台但没校验**

`/admin/all` 在 `/api/v1/posts/` 下面，根本没在 SecurityConfig 里被 `hasRole("ADMIN")` 拦。正确：

- 路径前缀统一 `/api/v1/admin/**`，SecurityConfig 里一次性拦截。
- 类级注解：`@PreAuthorize("hasRole('ADMIN')")` 标在 Controller 类上。

**⑤ Service 删除方法没鉴权**

定时任务 / Service 互调能绕过 Controller 直接执行。

```java
@PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
@Transactional
public void delete(Long id) { ... }
```

**额外**：`throw new RuntimeException("无权操作")` → 应抛 `AccessDeniedException`，全局异常处理器返回 403。

---

## Q4（实操）：写一个数据级权限注解 `@DataScope`，让 Service 方法自动注入 `WHERE author_id = currentUserId`（管理员不限制）。

**参考答案：**

### 一、注解定义

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    String alias() default "";    // SQL 中的表别名，如 "p"
    String column() default "author_id";
}
```

### 二、AOP 切面 + ThreadLocal

```java
public class DataScopeContext {
    private static final ThreadLocal<String> SCOPE_SQL = new ThreadLocal<>();
    public static void set(String sql) { SCOPE_SQL.set(sql); }
    public static String get() { return SCOPE_SQL.get(); }
    public static void clear() { SCOPE_SQL.remove(); }
}

@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    @Around("@annotation(scope)")
    public Object inject(ProceedingJoinPoint pjp, DataScope scope) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin) {
            Long uid = (Long) auth.getPrincipal();
            String prefix = scope.alias().isEmpty() ? "" : scope.alias() + ".";
            DataScopeContext.set(" AND " + prefix + scope.column() + " = " + uid);
        }
        try {
            return pjp.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
```

### 三、MyBatis Interceptor 注入 SQL

```java
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare",
    args = {Connection.class, Integer.class})})
@Component
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        String injected = DataScopeContext.get();
        if (injected != null) {
            BoundSql bound = handler.getBoundSql();
            String sql = bound.getSql();
            if (sql.toUpperCase().contains("WHERE")) {
                sql = sql.replaceFirst("(?i)WHERE", "WHERE 1=1 " + injected + " AND ");
            } else {
                sql += " WHERE 1=1 " + injected;
            }
            Field field = bound.getClass().getDeclaredField("sql");
            field.setAccessible(true);
            field.set(bound, sql);
        }
        return invocation.proceed();
    }
}
```

### 四、用法

```java
@Service
public class PostService {

    @DataScope(alias = "p", column = "author_id")
    public List<PostVO> myPosts(PostQuery q) {
        return postMapper.selectByQuery(q);
    }
}
```

### 五、关键点

1. **ThreadLocal 必须 clear**：用 `try-finally`，否则线程池场景污染。
2. **生产推荐用 JSqlParser**：手写 replace 容易在嵌套 SQL 出错。
3. **管理员豁免**：在 AOP 层判断 isAdmin。
4. **测试覆盖**：分别用 USER / ADMIN 调一次，断言 SQL 文本不同。
5. **替代方案**：MyBatis-Plus 内置 `DataPermissionInterceptor`，业务代码 0 改动。

---

## Q5（综合）：博客系统 50 万用户、权限粒度细到接口（200+ 权限码）。现状两个问题：

1. JWT 里塞 perms 后大小 4KB，HTTP 头超限被网关拦
2. 改用户角色要等 token 过期（15min）才生效，运营投诉

请重新设计权限校验链路。

**参考答案：**

### 一、问题诊断

- JWT 4KB 太大：Nginx 默认 `large_client_header_buffers 4 8k`，加业务 header 容易超。
- 权限延迟生效：JWT 自包含特性的副作用。

### 二、推荐方案：JWT 瘦身 + 服务端查权限

**1. JWT 只放角色，不放权限**

```java
// 改前
{"sub":"1", "roles":["USER"], "perms":[200 个 code]}    // 4KB

// 改后
{"sub":"1", "roles":["USER"], "ver":7}                  // 200B
```

**2. 权限实时从缓存查**

```java
@Service
public class PermissionService {

    @Cacheable(value="user-perms", key="#userId")
    public Set<String> getPerms(Long userId) {
        return permissionMapper.selectPermsByUser(userId);
    }

    @CacheEvict(value="user-perms", key="#userId")
    public void evict(Long userId) {}
}
```

TTL = 30 分钟，命中率 > 99%。

**3. JwtAuthFilter 改造**

```java
Claims c = jwtService.verify(token);
Long userId = Long.valueOf(c.getSubject());

UserStatus s = userStatusCache.get(userId);
if (s.status() == 0) throw new ...;
if (s.tokenVersion() > c.get("ver", Integer.class)) throw new ...;

Set<String> perms = permissionService.getPerms(userId);
List<SimpleGrantedAuthority> auths = new ArrayList<>();
c.get("roles", List.class).forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
perms.forEach(p -> auths.add(new SimpleGrantedAuthority(p)));

setContext(userId, auths);
```

### 三、改角色立即生效

```java
@Service
public class UserRoleService {

    @Transactional
    public void assignRole(Long userId, Long roleId) {
        userRoleMapper.insert(userId, roleId);
        permissionService.evict(userId);
        userStatusService.bumpTokenVersion(userId);   // 旧 JWT 立刻 401
    }
}
```

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| 加权限（不踢人） | 等 token 过期 | 缓存失效后下一次请求生效（<30s） |
| 改角色 / 改密 / 封禁 | 等 token 过期 | 立即生效（用户必须重登） |
| 移除权限 | 等 token 过期 | 缓存失效后立即生效 |

### 四、性能 & 容量

| 维度 | 数字 |
|------|------|
| JWT 大小 | 4KB → 200B（↓ 95%） |
| 每请求新增 Redis | 1 次 GET（~0.5ms） |
| 缓存命中率 | > 99% |
| Redis 内存 | 50w 用户 × 1KB ≈ 500MB |

### 五、其他配套

| 风险 | 措施 |
|------|------|
| 权限表查询慢 | 三层 JOIN 用覆盖索引；50w 用户 × 200 权限 ≈ 1 亿行视图，必要时物化 |
| 缓存击穿 | 热点用户加 mutex（Redisson 锁） |
| 缓存雪崩 | 不同用户 TTL ± 5min 随机抖动 |
| 全员失效 | `permission:global:version` 全局版本号，紧急时 +1 |

### 六、收益对比

| 维度 | 改前 | 改后 |
|------|------|------|
| JWT 大小 | 4KB | 200B |
| 改角色生效时间 | 15min | < 30s |
| 网关 4KB 拒收 | 频发 | 消失 |
| 单机 QPS 损失 | 0 | ≤ 1%（Redis 一次） |

**关键点**：JWT 是**身份令牌**，不是**权限快照**。把权限当数据，而不是当身份，能解耦"撤销立即生效"和"无状态验签"。这是 50w+ 用户系统的标准做法。
