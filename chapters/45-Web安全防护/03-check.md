# Chapter 45 Web 安全防护 - 自测

## Q1 (概念): 区分存储型 XSS / 反射型 XSS / DOM 型 XSS, 各自防护点在哪一层

### 答案

| 类型 | 攻击路径 | 防护层 |
|---|---|---|
| **存储型** | 用户提交 → 入库 → 其他用户读出 → 浏览器执行 | **后端入库前清洗 (Jsoup) + 前端转义 + CSP** |
| **反射型** | URL 参数 → 后端拼到响应 → 浏览器执行 | **后端不能直接拼响应, 用模板引擎 / API 返回 JSON; 前端转义** |
| **DOM 型** | 前端 JS 把 `location.hash` 等不可信源直接 `innerHTML` | **前端不要用 innerHTML, 用 textContent / 框架插值** |

**为什么后端只清洗不够?**
- 后端清洗只解决"入库环节", 但有些场景必须保留 HTML (富文本编辑器)。这时只能"过白名单标签"
- 如果有任何输出位置没用框架自动转义 (拼 HTML 字符串 / `dangerouslySetInnerHTML`), 仍会被打穿
- **CSP 是最后一道防线**: 即使脚本被注入, 只要 CSP 限制了 `script-src 'self'`, 外联恶意域加载不到

**博客 API 的具体做法**:

```java
public Long create(PostCreateReq req, Long userId) {
    PostDO p = new PostDO();
    p.setTitle(req.title());                            // 纯文本, 前端转义即可
    p.setContent(HtmlSanitizer.clean(req.content()));   // 富文本, 后端清洗
    p.setAuthorId(userId);
    postMapper.insert(p);
    return p.getId();
}
```

```java
public final class HtmlSanitizer {
    private static final Safelist SAFE = Safelist.relaxed()
        .addProtocols("a", "href", "http", "https")
        .addEnforcedAttribute("a", "rel", "nofollow noopener")
        .addEnforcedAttribute("a", "target", "_blank");
    public static String clean(String html) {
        return html == null ? null : Jsoup.clean(html, SAFE);
    }
}
```

### 常见坑

- 只在 Controller 清洗, Service 接受脏数据 → 定时任务 / 内部接口写入会绕过
- Markdown 渲染后才清洗 → 渲染过程中 `[xss](javascript:alert(1))` 会被解析为链接
- 用 `String.replaceAll("<script>", "")` 这种黑名单 → 用 `<SCRIPT>` / `<scr<script>ipt>` 都能绕

## Q2 (概念): JWT 后端 vs Cookie 后端, CSRF 防护策略有什么不同, 为什么

### 答案

**CSRF 成立的关键**: 浏览器**自动**把凭证带上跨域请求。Cookie 满足这条; Authorization Header 不满足。

| 项 | Cookie 会话 | JWT (Authorization Header) |
|---|---|---|
| 跨域请求自动带凭证? | **是** (除非 SameSite=Strict) | **否** (JS 必须主动 `Authorization: Bearer ...`) |
| CSRF 风险 | 高 | 极低 |
| CSRF 防护 | **必须**: Token / Double Submit / SameSite | 可关闭 |
| Spring Security 默认 | `csrf().enable` | 显式 `csrf().disable()` |

**为什么 JWT Header 天然不受 CSRF 影响?**

evil.com 的 `<form action=blog.com/transfer>` 提交时, 浏览器:
- 自动带 blog.com 的 cookie ✅ (这就是 CSRF 的成因)
- **不会**自动带 blog.com 的 localStorage 里的 JWT (跨域 JS 没法读 blog.com 的 localStorage)
- 所以服务端拿不到合法 Authorization, 直接 401

**但 JWT 也有要警惕的场景**:
- 如果 JWT 存在 Cookie 里 (避免 XSS 偷), 又回到 Cookie 模型, CSRF 又成立
- 这时需要 SameSite=Strict + 双 token (cookie 存 refresh, header 带 access)

**Spring Security 配置对应**:

```java
// JWT 后端
http.csrf(c -> c.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS));

// Cookie 后端
http.csrf(c -> c
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
```

### 常见坑

- 关了 CSRF, 又把 JWT 塞 Cookie 鉴权 → CSRF 复活
- SameSite=Strict 在 OAuth2 回跳场景断登录态 → 用 Lax + 关键操作二次确认

## Q3 (代码改错): 找出下面接口的安全问题, 给出修复方案

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostMapper postMapper;
    private final JdbcTemplate jdbc;

    @GetMapping("/search")
    public List<Map<String,Object>> search(@RequestParam String keyword,
                                            @RequestParam(defaultValue = "created_at") String sortBy,
                                            @RequestParam(defaultValue = "desc") String dir) {
        String sql = "SELECT * FROM post WHERE title LIKE '%" + keyword + "%' " +
                     "ORDER BY " + sortBy + " " + dir;
        return jdbc.queryForList(sql);
    }

    @PostMapping
    public Long create(@RequestBody PostCreateReq req) {
        PostDO p = new PostDO();
        p.setTitle(req.title());
        p.setContent(req.content());                // 富文本, 用户写啥存啥
        postMapper.insert(p);
        return p.getId();
    }

    @GetMapping("/{id}")
    public PostDO get(@PathVariable Long id) {
        return postMapper.selectById(id);            // 直接返 DO
    }
}
```

### 答案

**问题清单**:

1. **`search` 拼 SQL → SQL 注入**: `keyword` 直接拼, `sortBy` / `dir` 也直接拼。`keyword='; DROP TABLE post; --` 直接拖库
2. **`create` 富文本未清洗 → 存储型 XSS**: 任意 `<script>` 都进 DB
3. **`get` 返回 PostDO 整体**: 如果 PostDO 含 `internalNote` / `deletedFlag` / `editorIp` 之类字段, 全部泄漏
4. **没限流**: 搜索接口很贵, 应限流防爬
5. **未鉴权 (写接口)**: `create` 应该 `hasAuthority('post:write')`

**修复**:

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService service;

    private static final Set<String> SORTABLE = Set.of("created_at", "view_count", "id");

    @GetMapping("/search")
    @RateLimit(windowMs = 60_000, limit = 30)
    public PageVO<PostVO> search(@RequestParam @Length(max = 100) String keyword,
                                  @RequestParam(defaultValue = "created_at") String sortBy,
                                  @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        if (!SORTABLE.contains(sortBy)) sortBy = "created_at";
        if (!"asc".equalsIgnoreCase(dir)) dir = "desc";
        return service.search(keyword, sortBy, dir, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('post:write')")
    public Long create(@Valid @RequestBody PostCreateReq req, Authentication auth) {
        return service.create(req, (Long) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public PostVO get(@PathVariable Long id) {
        return service.getById(id);
    }
}
```

```java
public Long create(PostCreateReq req, Long uid) {
    String safeContent = HtmlSanitizer.clean(req.content());
    PostDO p = new PostDO();
    p.setTitle(req.title());
    p.setContent(safeContent);
    p.setAuthorId(uid);
    postMapper.insert(p);
    return p.getId();
}
```

```xml
<select id="search" resultType="PostDO">
    SELECT id, title, summary, author_id, created_at, view_count
    FROM post
    WHERE title LIKE CONCAT('%', #{keyword}, '%')
    ORDER BY ${sortBy} ${dir}
    LIMIT #{offset}, #{size}
</select>
```

## Q4 (代码题): 实现一个基于 Redis 的滑动窗口限流, 按 (用户ID + API 路径) 限频

### 参考实现

```java
@Component
@RequiredArgsConstructor
public class RateLimitService {
    private final StringRedisTemplate redis;

    private static final String LUA = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        local count = redis.call('ZCARD', key)
        if count >= limit then return 0 end
        redis.call('ZADD', key, now, now .. ':' .. math.random())
        redis.call('PEXPIRE', key, window)
        return 1
        """;

    private static final DefaultRedisScript<Long> SCRIPT =
        new DefaultRedisScript<>(LUA, Long.class);

    public boolean tryAcquire(String key, long windowMs, int limit) {
        Long r = redis.execute(SCRIPT, List.of(key),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(windowMs),
            String.valueOf(limit));
        return Long.valueOf(1L).equals(r);
    }
}
```

```java
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RateLimitService rl;
    private final HttpServletRequest request;

    @Around("@annotation(rateLimit)")
    public Object guard(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        Long uid = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                           .map(a -> (Long) a.getPrincipal())
                           .orElse(-1L);
        String key = "rl:%d:%s".formatted(uid, request.getRequestURI());
        if (!rl.tryAcquire(key, rateLimit.windowMs(), rateLimit.limit())) {
            throw new TooManyRequestsException("请求过于频繁, 请稍后再试");
        }
        return pjp.proceed();
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    long windowMs() default 60_000;
    int limit() default 60;
}
```

```java
@PostMapping("/api/v1/posts")
@RateLimit(windowMs = 60_000, limit = 10)
public Long create(...) { ... }
```

**单测**:

```java
@Test
void shouldLimitTo10PerMinute() throws Exception {
    for (int i = 0; i < 10; i++) {
        mvc.perform(post("/api/v1/posts").content(...)).andExpect(status().isOk());
    }
    mvc.perform(post("/api/v1/posts").content(...)).andExpect(status().is(429));
}
```

**为什么用滑动窗口 (ZSET) 而不是固定窗口?**
- 固定窗口在切换瞬间会有 2 倍流量突刺 (59 秒 100 个 + 0 秒 100 个 = 1 秒内 200)
- ZSET 按时间戳排序, 每次都精确清掉过期成员, 没有突刺
- 代价: 内存开销大 (每个请求一个 ZSET 成员)。 高 QPS 场景改用 Bucket4j 令牌桶

## Q5 (综合题): 生产前安全 Review

某博客 API 准备上线, 你要做安全 Review。 从以下维度列出**至少 12 项检查清单**, 每项给检测命令 / 修复点。

### 答案

**A. 认证授权**

| 检查 | 检测 | 修复 |
|---|---|---|
| 写接口必须鉴权 | grep `@PostMapping/@PutMapping/@DeleteMapping` 看是否都有 `@PreAuthorize` | 加 `@PreAuthorize` |
| 数据级权限 | 所有 update/delete 都过 `@postSec.isOwner` | 见 ch43 |
| JWT 签名密钥 | `git log -p | grep -i jwt.secret` 不能有明文 | 走环境变量 / KMS |
| 密码强度 | BCryptPasswordEncoder rounds ≥ 10 | rounds=12 |

**B. 注入**

| 检查 | 检测 | 修复 |
|---|---|---|
| XSS | 富文本入库前 `HtmlSanitizer.clean()` | Jsoup Safelist |
| SQL 注入 | grep `\${` 在 mapper 中仅白名单字段 | `#{}` + 白名单 |
| 命令注入 | `Runtime.exec(userInput)` → 严禁 | ProcessBuilder + 白名单 |
| 反序列化 | `ObjectInputStream` 用户输入 → 严禁 | Jackson + 类型白名单 |

**C. 传输与会话**

| 检查 | 检测 | 修复 |
|---|---|---|
| HTTPS | Nginx HTTPS, HSTS, HTTP→HTTPS 301 | nginx.conf + `server.forward-headers-strategy=NATIVE` |
| Cookie | HttpOnly + Secure + SameSite | `server.servlet.session.cookie.*` |
| Token 撤销 | 登出 / 改密码后 token 失效 | tokenVersion / 黑名单 |
| 短 TTL | Access ≤ 15min, Refresh ≤ 7d | JwtService 配置 |

**D. 滥用防护**

| 检查 | 检测 | 修复 |
|---|---|---|
| 全局限流 | 100 次 burst, 超过返 429 | 全局 RateLimit Filter |
| 登录限流 | 同 IP 同账号 5 次/分钟 | 单独的 LoginRateLimit |
| 验证码 | 失败 3 次后弹验证码 | hCaptcha / 极验 |
| 请求体大小 | 上传 100MB 应被拒 | `spring.servlet.multipart.max-request-size=10MB` |

**E. 数据保护**

| 检查 | 检测 | 修复 |
|---|---|---|
| 敏感字段返回脱敏 | `curl /api/v1/users/me` 看 phone 是否 138****1234 | MaskedSerializer |
| 日志不打密码 / token | `grep -rE 'password|token' logs/` 应为空 | Logback Pattern + 入口拦截 |
| DB 密钥 | 不在 git, 走环境变量 / Vault | `${DB_PASSWORD}` |
| 删除是软删 | `is_deleted=1` + 审计 | 防误删 |

**F. 响应头加固**

| 检查 | 检测 | 修复 |
|---|---|---|
| 安全头 | `curl -I https://blog.com` 含 CSP / HSTS / X-Frame-Options / nosniff | Spring `http.headers(...)` |
| Server / X-Powered-By | 不应暴露版本 | Nginx 隐藏 |
| 错误页 | 不暴露 stacktrace | `server.error.include-stacktrace=never` |
| Swagger | prod 关闭或加鉴权 | `springdoc.swagger-ui.enabled=false` |

**G. 监控审计**

| 检查 | 检测 | 修复 |
|---|---|---|
| 鉴权失败日志 | 所有 401/403 含 userId / IP / path | `accessDeniedHandler` 打 WARN |
| 关键操作审计 | 改密、改权限、删用户进审计表 | `auth_audit` 表 |
| 异常告警 | 5xx > N% 报警 | Prometheus + Alertmanager |
| 依赖漏洞 | `mvn dependency-check:check` 在 CI 跑 | OWASP Dependency-Check |

**面试 2 分钟讲法**:

> 上线前我会拉清单, 分 7 块: 认证授权 / 注入 / 传输 / 滥用 / 数据 / 响应头 / 监控。重点项有: JWT 密钥不进 git、富文本 Jsoup 清洗、全链路限流 (登录额外严)、CSP 上线。我们做过一次 OWASP ZAP 扫描发现 X 个中危, 主要是 CSP 缺失和登录接口没限流, 我加了 SecurityConfig.headers + Bucket4j 后再扫为 0。
