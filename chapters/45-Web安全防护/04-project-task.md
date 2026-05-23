# Chapter 45 Web 安全防护 - 项目任务

## 任务概述

给博客 API 上线一套完整 Web 安全防护: **CORS / CSRF / XSS / SQL 注入 / 限流 / 敏感字段脱敏 / 安全响应头**。每一项都有对应测试用例和 curl 验证。产出 `docs/security-checklist.md`。

## 业务背景

44 章 SecurityConfig 解决了认证授权框架, 但站点对外暴露还有大量"工程化安全"要做。这些是已经发生过的真实事故, 几乎每家公司都有过对应 P0:

- 评论区可以 XSS → 偷 cookie → 接管账号
- 登录接口不限流 → 撞库 → 千万级账号泄漏
- 排序参数拼 SQL → 拖库
- 错误页吐 stacktrace → 框架版本暴露 → 定向漏洞利用
- 接口返回 phone / idcard 明文 → 上"暗网交易记录"

本章要做的是: 把这些写进 SecurityConfig / Service / Filter / 单测, 并产出可向面试官展示的 checklist。

## 任务拆解

### Step 1: CORS + 安全响应头 (30 分钟)

`SecurityConfig`:

```java
@Bean
public SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .headers(h -> h
            .contentTypeOptions(Customizer.withDefaults())
            .frameOptions(f -> f.deny())
            .referrerPolicy(r -> r.policy(ReferrerPolicy.SAME_ORIGIN))
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; img-src 'self' data: https:; " +
                "script-src 'self'; style-src 'self' 'unsafe-inline'"))
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000)))
        // ... 其他配置
        .build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origins);
    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Trace-Id"));
    c.setExposedHeaders(List.of("Authorization","X-Trace-Id"));
    c.setAllowCredentials(true);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
}
```

`application.yml`:

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:5173
      - https://blog.example.com
```

### Step 2: XSS 富文本清洗 (30 分钟)

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
        .addEnforcedAttribute("a", "rel", "nofollow noopener")
        .addEnforcedAttribute("a", "target", "_blank")
        .removeProtocols("a", "href", "javascript", "data");
    public static String clean(String html) {
        return html == null ? null : Jsoup.clean(html, SAFE);
    }
}
```

`PostService.create` / `update` / `CommentService.create` 在 setContent 前调用 `HtmlSanitizer.clean()`。

### Step 3: SQL 注入防护 (15 分钟)

排查所有 `${}` 用法:

```bash
grep -r '\${' src/main/resources/mapper/
```

任何用户输入字段都必须 `#{}`。 排序字段允许 `${}` 但必须白名单:

```java
private static final Set<String> SORTABLE = Set.of("created_at", "view_count", "id");
private static final Set<String> DIR = Set.of("asc", "desc");

public PageVO<PostVO> list(String sortBy, String dir, int page, int size) {
    if (!SORTABLE.contains(sortBy)) sortBy = "created_at";
    if (!DIR.contains(dir.toLowerCase())) dir = "desc";
    // ...
}
```

### Step 4: 限流 (60 分钟)

#### 4.1 全局限流 (Bucket4j 单机版)

```java
@Component
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket bucketOf(String key) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
            .addLimit(l -> l.capacity(100).refillIntervally(100, Duration.ofMinutes(1)))
            .build());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                     FilterChain chain) throws IOException, ServletException {
        String ip = clientIp(req);
        if (!bucketOf(ip).tryConsume(1)) {
            resp.setStatus(429);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.getWriter().write("""
                {"code":429,"msg":"请求过于频繁"}""");
            return;
        }
        chain.doFilter(req, resp);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return req.getRemoteAddr();
        return xff.split(",")[0].trim();
    }
}
```

#### 4.2 登录接口单独限流

```java
@PostMapping("/api/v1/auth/login")
public TokenPair login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
    String key = "login:" + clientIp(http) + ":" + req.username();
    if (!loginRateLimit.tryAcquire(key, 60_000, 5)) {   // 5 次/分钟
        throw new TooManyRequestsException("登录失败过多, 请稍后再试");
    }
    return authService.login(req);
}
```

#### 4.3 注解版 RateLimit (见 03-check.md Q4)

### Step 5: 敏感字段脱敏 (20 分钟)

```java
public class PhoneMaskSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String v, JsonGenerator g, SerializerProvider s) throws IOException {
        if (v == null || v.length() < 7) { g.writeString(v); return; }
        g.writeString(v.substring(0, 3) + "****" + v.substring(v.length() - 4));
    }
}

public record UserVO(Long id, String username, String email,
                      @JsonSerialize(using = PhoneMaskSerializer.class) String phone) {}
```

`Logback` 模式串过滤密码:

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %replace(%msg){'(password|token)=[^,\s]*', '$1=***'}%n</pattern>
```

### Step 6: 错误信息收敛 (10 分钟)

`application-prod.yml`:

```yaml
server:
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
    include-exception: false
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

`GlobalExceptionHandler` 统一返回 `{"code","msg"}`, 不带框架细节。

### Step 7: 安全 checklist 文档 (30 分钟)

写 `docs/security-checklist.md`, 按 03-check.md Q5 七大类列出实施项, 每项含:

- 风险描述
- 当前实施 (代码 / 配置位置)
- 验证命令
- 备注 (如果暂未做, 为什么 + 计划)

### Step 8: 自动化测试 (30 分钟)

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecuritySmokeTest {

    @Autowired MockMvc mvc;

    @Test
    void responseHasSecurityHeaders() throws Exception {
        mvc.perform(get("/api/v1/posts").with(anonymous()))
           .andExpect(header().exists("X-Content-Type-Options"))
           .andExpect(header().exists("X-Frame-Options"))
           .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void xssCommentIsSanitized() throws Exception {
        String input = "<p>hello <script>alert(1)</script></p>";
        // 入库后回读, content 不应含 <script>
    }

    @Test
    void loginRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").content(...)).andReturn();
        }
        mvc.perform(post("/api/v1/auth/login").content(...))
           .andExpect(status().is(429));
    }
}
```

## 交付物

- [ ] `SecurityConfig` 安全响应头 (CSP / HSTS / X-Frame-Options / nosniff)
- [ ] `CorsConfigurationSource` 白名单, 从 `application.yml` 读取
- [ ] `HtmlSanitizer` 工具类 + Service 层调用
- [ ] Mapper 全部 `#{}`, 排序字段白名单
- [ ] `GlobalRateLimitFilter` (IP 维度) + `LoginRateLimit` (IP + 用户名)
- [ ] 敏感字段 `@JsonSerialize(using=PhoneMaskSerializer.class)`
- [ ] Logback 模式串脱敏 password / token
- [ ] `application-prod.yml` 错误信息收敛
- [ ] `docs/security-checklist.md` ≥ 12 项
- [ ] `SecuritySmokeTest` ≥ 5 个用例
- [ ] git commit: `ch45: harden web security (cors/csrf/xss/rate-limit/headers)`

## 验收清单

| 验收项 | 标准 |
|---|---|
| CORS 白名单 | 从 evil.com 访问 → 浏览器拒绝; 从 allowed-origin 访问 → 200 |
| XSS 防护 | 评论提交 `<script>alert(1)</script>` → DB 中只有清洗后的纯文本 |
| 安全头 | `curl -I /api/v1/posts` 含 CSP / HSTS / X-Frame-Options / nosniff |
| 排序白名单 | `?sortBy=id;DROP%20TABLE%20post` → 落到默认 created_at, 无 SQL 错误 |
| 全局限流 | 100 次 burst 后第 101 个 → 429, Retry-After 头 |
| 登录限流 | 同 IP 同账号 5 次失败 → 429 持续 1 分钟 |
| 脱敏 | `/api/v1/users/me` 返回 phone 形如 `138****1234` |
| 日志脱敏 | 登录失败日志 grep 不到明文 password |
| 错误收敛 | prod 配置下 500 错误返回 `{"code":500,"msg":"系统繁忙"}`, 不含 stacktrace |
| Swagger | prod 配置下 `/swagger-ui` 返回 404 |

## 扩展挑战

1. **CSP report-only 模式 + 上报**: 先用 `Content-Security-Policy-Report-Only`, 把违规请求上报到 `/csp-report`, 跑一周后再切 enforce。
2. **OWASP ZAP 扫描**: 起服务后跑 `docker run owasp/zap2docker-weekly zap-baseline.py -t http://host.docker.internal:8080`, 把报告附进 checklist。
3. **接 hCaptcha 验证码**: 登录失败 3 次后必须过验证码, 用 hCaptcha 免费版。
4. **依赖漏洞扫描进 CI**: `mvn org.owasp:dependency-check-maven:check`, 把 CVSS ≥ 7.0 的依赖列出来, fail the build。
5. **接 Nginx WAF (ModSecurity)**: 应用层防护后再加一层。 重点防 SQL 注入 + 路径穿越。
