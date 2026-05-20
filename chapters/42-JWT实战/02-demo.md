# Chapter 42 JWT 实战 - 实操 Demo

## Demo 目标

跑通：登录 → 拿到 Access + Refresh → 用 Access 调受保护接口 → Access 过期 → 用 Refresh 续签 → 登出 → 旧 Refresh 失效。

## 前置条件

- 基线 pom（见 21 章）。
- 24-29 章数据库与 MyBatis（需要 `users` 表）。
- Redis 在跑（用于 Refresh Token 存储 + 黑名单），可用 Docker：`docker run -d -p 6379:6379 redis:7-alpine`。

## 增量依赖

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

## 配置 application.yml

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:please-change-me-to-a-very-long-random-string-at-least-32-bytes}
    access-ttl-minutes: 15
    refresh-ttl-days: 7
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## 完整示例代码

### JwtProperties + JwtService

```java
// com/example/blog/auth/JwtProperties.java
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, int accessTtlMinutes, int refreshTtlDays) {}

// com/example/blog/auth/JwtService.java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccess(Long userId, List<String> roles) {
        return issue(userId, roles, Duration.ofMinutes(props.accessTtlMinutes()), "access");
    }
    public String issueRefresh(Long userId) {
        return issue(userId, List.of(), Duration.ofDays(props.refreshTtlDays()), "refresh");
    }
    private String issue(Long userId, List<String> roles, Duration ttl, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .claim("typ", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }
}
```

### Refresh Token 存储 + 黑名单（Redis）

```java
// com/example/blog/auth/TokenStore.java
@Service
@RequiredArgsConstructor
public class TokenStore {
    private final StringRedisTemplate redis;
    private final JwtProperties props;

    public void saveRefresh(String jti, Long userId) {
        redis.opsForValue().set("refresh:" + jti, String.valueOf(userId),
            Duration.ofDays(props.refreshTtlDays()));
    }
    public boolean isRefreshValid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey("refresh:" + jti));
    }
    public void revokeRefresh(String jti) { redis.delete("refresh:" + jti); }

    public void blacklistAccess(String jti, long secondsLeft) {
        redis.opsForValue().set("bl:" + jti, "1", Duration.ofSeconds(secondsLeft));
    }
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey("bl:" + jti));
    }
}
```

### AuthController

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TokenStore store;

    @PostMapping("/login")
    public ApiResponse<Map<String,String>> login(@Valid @RequestBody LoginRequest req) {
        User u = userService.findByUsername(req.username())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!encoder.matches(req.password(), u.getPasswordHash()))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");

        String access  = jwt.issueAccess(u.getId(), u.getRoles());
        String refresh = jwt.issueRefresh(u.getId());
        store.saveRefresh(jwt.parse(refresh).getId(), u.getId());
        return ApiResponse.ok(Map.of("accessToken", access, "refreshToken", refresh));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String,String>> refresh(@RequestBody Map<String,String> body) {
        Claims c = jwt.parse(body.get("refreshToken"));
        if (!"refresh".equals(c.get("typ")) || !store.isRefreshValid(c.getId()))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 无效");

        // rotation：删除旧的，签发新对
        store.revokeRefresh(c.getId());
        Long userId = Long.valueOf(c.getSubject());
        List<String> roles = userService.findRoles(userId);
        String newAccess  = jwt.issueAccess(userId, roles);
        String newRefresh = jwt.issueRefresh(userId);
        store.saveRefresh(jwt.parse(newRefresh).getId(), userId);
        return ApiResponse.ok(Map.of("accessToken", newAccess, "refreshToken", newRefresh));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String bearer,
                                    @RequestBody Map<String,String> body) {
        Claims access = jwt.parse(bearer.substring(7));
        long secondsLeft = (access.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        if (secondsLeft > 0) store.blacklistAccess(access.getId(), secondsLeft);

        if (body.get("refreshToken") != null)
            store.revokeRefresh(jwt.parse(body.get("refreshToken")).getId());
        return ApiResponse.ok(null);
    }
}
```

### JwtAuthFilter（接入 SecurityFilterChain，见 44 章）

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final TokenStore store;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(h.substring(7));
                if (!"access".equals(c.get("typ"))) throw new JwtException("not access");
                if (store.isBlacklisted(c.getId())) throw new JwtException("blacklisted");

                List<SimpleGrantedAuthority> auth = ((List<String>) c.get("roles")).stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                var token = new UsernamePasswordAuthenticationToken(c.getSubject(), null, auth);
                SecurityContextHolder.getContext().setAuthentication(token);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, resp);
    }
}
```

## 运行与验证

| 步骤 | 命令 | 期望 |
|---|---|---|
| 登录 | `curl -X POST :8080/api/auth/login -d '{"username":"u","password":"p"}'` | 返回 access + refresh |
| 访问受保护接口 | `curl :8080/api/articles -H "Authorization: Bearer $ACCESS"` | 200 |
| 不带 token | `curl :8080/api/articles` | 401 |
| 续签 | `curl -X POST :8080/api/auth/refresh -d '{"refreshToken":"..."}'` | 拿到新对 |
| 用旧 refresh 再续签 | 同上但用上一步删除前的旧 token | 401（rotation 已废） |
| 登出 | `curl -X POST :8080/api/auth/logout ...` | 200 |
| 登出后用同一 access | 同 2 | 401（黑名单） |

## 常见坑

- secret 短于 32 字节 → JJWT 0.12 直接抛 `WeakKeyException`。
- 时钟漂移 → 设置 `Jwts.parser().setAllowedClockSkewSeconds(60)`。
- Refresh 不做 rotation → 一次泄漏 = 永久泄漏。
- Access 黑名单要带 TTL = Access 剩余寿命，避免 Redis 无限增长。

## 提交

```bash
git commit -m "chapter 42: jwt access+refresh with rotation & redis blacklist"
```
