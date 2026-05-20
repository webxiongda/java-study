# Chapter 45 Web 安全防护 - 实操 Demo

## Demo 目标

四个常见攻击的代码级防护：CORS / CSRF / XSS / 限流（Bucket4j + Redis 令牌桶），以及响应头加固。

## 前置条件

- 44 章 SecurityFilterChain 已就绪。
- 47 章 Redis 已接入（限流用）。

## 增量依赖

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>${bucket4j.version}</version>
</dependency>
<!-- 可选：Bucket4j Redis 后端做分布式限流 -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-redis-common</artifactId>
    <version>${bucket4j.version}</version>
</dependency>
```

## 1. CORS（跨域）

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of("https://blog.example.com", "http://localhost:5173"));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
    c.setExposedHeaders(List.of("X-Trace-Id"));
    c.setAllowCredentials(true);             // 带 cookie 时必须明确 origin，不能是 *
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
}
```

> `allowCredentials=true` 时 `allowedOrigins` **禁止用 `*`**，否则浏览器报错。

## 2. CSRF

JWT 无状态后端默认可关：

```java
http.csrf(c -> c.disable());
```

但**只要还用 Cookie 维持会话**，必须开启：

```java
http.csrf(c -> c
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
```

## 3. XSS（输出转义 + 入参清理）

### 3.1 服务端清洗富文本（Markdown / HTML 输入）

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

```java
public final class HtmlSanitizer {
    private static final Safelist SAFE = Safelist.relaxed()
        .addProtocols("a", "href", "http", "https", "mailto")
        .addEnforcedAttribute("a", "rel", "nofollow noopener");
    public static String clean(String html) {
        return html == null ? null : Jsoup.clean(html, SAFE);
    }
}

// 用法
article.setContent(HtmlSanitizer.clean(req.content()));
```

### 3.2 响应头加固

```java
http.headers(h -> h
    .contentTypeOptions(Customizer.withDefaults())                // X-Content-Type-Options: nosniff
    .frameOptions(f -> f.deny())                                  // X-Frame-Options: DENY
    .referrerPolicy(r -> r.policy(ReferrerPolicy.SAME_ORIGIN))
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; img-src 'self' data:; script-src 'self'"))
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true).maxAgeInSeconds(31536000)));
```

## 4. SQL 注入

MyBatis 用 `#{}` 占位（默认 PreparedStatement），**禁止用 `${}` 拼用户输入**。 排序列允许 `${}` 时必须白名单：

```java
private static final Set<String> SORTABLE = Set.of("created_at", "view_count", "id");
public List<Article> list(String sortBy, String dir) {
    if (!SORTABLE.contains(sortBy)) sortBy = "created_at";
    if (!"asc".equalsIgnoreCase(dir)) dir = "desc";
    return mapper.list(sortBy, dir);    // 仅这里允许 ${}
}
```

## 5. 限流（Bucket4j 令牌桶）

### 5.1 单机版（开发够用）

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    // 每 IP 1 个桶：1 秒 5 个 token，最多 10
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket bucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder()
            .addLimit(limit -> limit.capacity(10).refillIntervally(5, Duration.ofSeconds(1)))
            .build());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws IOException, ServletException {
        String ip = Optional.ofNullable(req.getHeader("X-Forwarded-For"))
                            .orElse(req.getRemoteAddr()).split(",")[0].trim();
        ConsumptionProbe probe = bucket(ip).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            resp.setStatus(429);
            resp.setHeader("Retry-After", String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("""
                {"code":42900,"message":"rate limited"}""");
            return;
        }
        resp.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        chain.doFilter(req, resp);
    }
}
```

### 5.2 分布式：Redis Lua 令牌桶

```java
private static final String LUA = """
    local key = KEYS[1]
    local rate = tonumber(ARGV[1])      -- token/s
    local capacity = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])
    local requested = tonumber(ARGV[4])
    local data = redis.call('HMGET', key, 'tokens', 'ts')
    local tokens = tonumber(data[1]) or capacity
    local ts = tonumber(data[2]) or now
    local delta = math.max(0, now - ts)
    tokens = math.min(capacity, tokens + delta * rate)
    local allowed = 0
    if tokens >= requested then
      tokens = tokens - requested
      allowed = 1
    end
    redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
    redis.call('EXPIRE', key, math.ceil(capacity / rate) * 2)
    return allowed
    """;

public boolean tryAcquire(String key, double rate, int capacity) {
    Long ok = redis.execute(new DefaultRedisScript<>(LUA, Long.class),
        List.of("rl:" + key),
        String.valueOf(rate), String.valueOf(capacity),
        String.valueOf(System.currentTimeMillis() / 1000.0), "1");
    return Long.valueOf(1L).equals(ok);
}
```

## 6. 敏感字段日志脱敏

```java
public record UserDTO(Long id, String username,
                      @JsonSerialize(using = MaskedSerializer.class) String phone) {}

public class MaskedSerializer extends JsonSerializer<String> {
    @Override public void serialize(String v, JsonGenerator g, SerializerProvider s) throws IOException {
        if (v == null || v.length() < 7) { g.writeString(v); return; }
        g.writeString(v.substring(0, 3) + "****" + v.substring(v.length() - 4));
    }
}
```

## 运行与验证

| 检查项 | 命令 / 期望 |
|---|---|
| CORS | 浏览器从 `http://other.com` 请求 → 被拦截 |
| 限流 | `for i in $(seq 1 20); do curl :8080/api/articles; done` → 后面几条 429 |
| XSS | 提交 `<script>alert(1)</script>` → DB 中只剩 `&lt;script&gt;` |
| 安全头 | `curl -I :8080` 含 `X-Content-Type-Options`, `Content-Security-Policy`, `Strict-Transport-Security` |
| SQL 注入 | `?sortBy=id;DROP TABLE` → 被白名单过滤 |

## 常见坑

- CORS `allowCredentials=true` + `allowedOrigins=*` → 浏览器静默拒绝。
- 限流键用 `remoteAddr` 但前面有 Nginx → 全部 IP 变成 Nginx 地址。 必须取 `X-Forwarded-For` 第一个。
- 单机 Bucket4j 在多副本下被绕过 → 上 Redis 版本。
- 关 CSRF 后又用 Cookie 鉴权 → 风险开放。 二选一。
- 只在 Controller 转义、Service 接受脏数据 → 一旦另一个入口（如定时任务）写 DB 就漏。

## 提交

```bash
git commit -m "chapter 45: cors/csrf/xss/rate-limit + security headers"
```
