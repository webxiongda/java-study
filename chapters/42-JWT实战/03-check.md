# Chapter 42 JWT 实战 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：JWT 和 Session 各自的优缺点？项目里你怎么选？

**参考答案：**

| 维度 | Session（Cookie） | JWT |
|------|-------------------|-----|
| **状态** | 有状态（服务端存 sid → userId） | 无状态（payload 自带身份） |
| **存储** | 服务端 Redis | 客户端 localStorage / Cookie |
| **水平扩展** | 需要共享 Redis | 任意实例可验证 |
| **跨域** | Cookie 受同源限制 | Header 携带，天然跨域 |
| **撤销** | 删 Redis key 立即失效 | 难（要黑名单或等过期） |
| **大小** | Cookie 4KB 上限，sid 几十字节 | Header 至少几百字节，每请求重复传 |
| **移动端** | 不友好（Cookie 处理复杂） | 友好（直接 Header） |
| **敏感数据** | 服务端存，安全 | payload 只是 base64，**不能放敏感数据** |

**选型决策树**：

```
单体 + 同源 Web                 → Session + Redis（简单）
前后端分离 / 移动端 / 多域      → JWT
微服务网关统一鉴权               → JWT（网关验签下发用户上下文）
后台管理（高安全 + 立即撤销）    → Session + MFA
公开 API（合作伙伴调用）         → JWT + 短 TTL
```

**面试常考**：

- "JWT 怎么实现退出？" → 把 jti 加 Redis 黑名单，TTL = token 剩余有效期。
- "JWT 改密码怎么让旧 token 失效？" → 在 payload 加 `pwd_ver`，改密时 +1；验签时对比当前用户 pwd_ver。
- "JWT 能不能存密码？" → 不行，payload 是 base64 可解。**只放非敏感的身份信息**（userId, roles, exp）。

---

## Q2（概念）：Access Token + Refresh Token 双 token 的目的是什么？refresh token 轮换（rotation）解决什么问题？

**参考答案：**

### 双 token 的目的

**单一长 token 的问题**：

- 7 天 Access：一旦泄露，攻击者享受 7 天权限。
- 15 分钟 Access：用户体验差，每 15 分钟登一次。

**双 token 解法**：

| Token | 寿命 | 用途 | 存储 |
|-------|------|------|------|
| Access | 15min | 调业务接口 | 内存 / localStorage |
| Refresh | 7-30d | 仅调 `/auth/refresh` 换新 Access | HttpOnly Cookie 或 DB |

```
登录 → 拿 (access, refresh)
↓
正常请求带 access
↓
access 过期 → 用 refresh 调 /auth/refresh → 拿新 access
↓
refresh 过期 → 强制重新登录
```

**安全收益**：

- Access 泄露：最多 15 分钟。
- Refresh 不参与业务调用：被中间人截获的概率低。
- Refresh 走 HttpOnly Cookie：JS 偷不到，XSS 影响小。

### Refresh Rotation（轮换）

**问题**：refresh 在到期前一直有效，泄露后攻击者能持续刷 Access，受害者无感。

**轮换方案**：

```
/auth/refresh 每次调用：
  1. 验旧 refresh 有效 → 拿到 userId
  2. 签发新 access + 新 refresh
  3. 旧 refresh 立即作废（Redis 删除 / DB 状态改 revoked）
  4. 返回 (new_access, new_refresh)
```

**额外检测**：

```
如果旧 refresh 已被作废还来调用 → 强信号：被偷
→ 直接吊销该用户所有 refresh，强制重登
→ 给用户发邮件告警
```

**面试常考**：

- "为什么 refresh 不带在 Authorization 头？" → 减少暴露面，只有 refresh 接口需要它。
- "refresh 存 DB 还是 Redis？" → 都行。Redis 快；DB 便于审计。生产推荐 Redis + 操作日志写 DB。

---

## Q3（实操）：以下 JWT 签发 + 验证代码有 6 处问题，找出并改正。

```java
@Component
public class JwtUtil {
    private static final String SECRET = "secret123";                       // ①
    private static final long EXPIRE = 7 * 24 * 3600 * 1000L;              // ②

    public String sign(Long userId, List<String> roles) {
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("roles", roles)
            .claim("password", "encoded")                                  // ③
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
            .signWith(SignatureAlgorithm.HS256, SECRET.getBytes())          // ④
            .compact();
    }

    public Long parse(String token) {
        try {
            String[] parts = token.split("\\.");
            JsonNode payload = new ObjectMapper().readTree(
                Base64.getDecoder().decode(parts[1]));                       // ⑤⑥
            return payload.get("sub").asLong();
        } catch (Exception e) {
            return null;
        }
    }
}
```

**参考答案：**

**① Secret 写死在代码 / 太短**

源代码进 Git → 泄露后所有 token 失效。HS256 要求 secret ≥ 256bit（32 字节）。

```java
@Value("${jwt.secret}")
private String secret;

private SecretKey key() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
}
```

`application.yml`：

```yaml
jwt:
  secret: ${JWT_SECRET}   # 启动时 export JWT_SECRET=...
```

**② Access Token 7 天太长**

Access 应 ≤ 30 分钟。7 天那种是 refresh token。

```java
private static final long ACCESS_TTL = 15 * 60 * 1000L;
```

**③ 把密码放 payload**

payload 是 base64，任何人都能解。**绝不能放密码、身份证、手机号**。只放 userId、roles、tenantId 等非敏感识别字段。

**④ 老版 API + 算法不固定**

`SignatureAlgorithm.HS256` 是 jjwt 0.11 老 API；新版用 `Jwts.builder().signWith(key, Jwts.SIG.HS256)`。**关键**：解析时必须显式校验 alg，防 `alg=none` 攻击。

```java
return Jwts.builder()
    .subject(userId.toString())
    .claim("roles", roles)
    .expiration(new Date(System.currentTimeMillis() + ACCESS_TTL))
    .issuedAt(new Date())
    .id(UUID.randomUUID().toString())   // jti
    .signWith(key(), Jwts.SIG.HS256)
    .compact();
```

**⑤ 没验签直接解 payload**

任何人伪造一个 JWT 把 sub 改成 admin，这段代码会信任。**必须用库验签**：

```java
public Long parse(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(key())
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return Long.valueOf(claims.getSubject());
}
```

**⑥ 用普通 Base64**

JWT 用 **Base64URL**（替换 `+/` 为 `-_`，去掉 padding）。`Base64.getUrlDecoder()`。

更重要：**不要自己 split + 解析，永远用库**。

**补充：catch Exception 返回 null**

调用方无法区分"签名错"和"过期"。应抛具体异常：`ExpiredJwtException` / `SignatureException` / `MalformedJwtException`，全局异常处理器返回不同的 401 / 403。

---

## Q4（实操）：实现一个完整的 JWT Filter + 黑名单退出方案。

**参考答案：**

### 一、JwtService

```java
@Service
@RequiredArgsConstructor
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.access-ttl}") private long accessTtl;
    @Value("${jwt.refresh-ttl}") private long refreshTtl;
    private final StringRedisTemplate redis;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public TokenPair issue(Long userId, List<String> roles) {
        String access = build(userId, roles, accessTtl, "access");
        String refresh = build(userId, roles, refreshTtl, "refresh");
        redis.opsForValue().set("refresh:" + userId, refresh,
            Duration.ofMillis(refreshTtl));
        return new TokenPair(access, refresh);
    }

    private String build(Long userId, List<String> roles, long ttl, String type) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("roles", roles)
            .claim("type", type)
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ttl))
            .signWith(key(), Jwts.SIG.HS256)
            .compact();
    }

    public Claims verify(String token) {
        Claims c = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
        if (Boolean.TRUE.equals(redis.hasKey("jwt:blacklist:" + c.getId()))) {
            throw new BadCredentialsException("token revoked");
        }
        return c;
    }

    public void blacklist(String token) {
        Claims c = verify(token);
        long remainMs = c.getExpiration().getTime() - System.currentTimeMillis();
        if (remainMs > 0) {
            redis.opsForValue().set("jwt:blacklist:" + c.getId(),
                "1", Duration.ofMillis(remainMs));
        }
    }
}
```

### 二、JwtAuthFilter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = jwtService.verify(token);
                Long userId = Long.valueOf(claims.getSubject());
                List<String> roles = claims.get("roles", List.class);
                var auths = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
                var authToken = new UsernamePasswordAuthenticationToken(userId, null, auths);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, resp);
    }
}
```

### 三、退出接口

```java
@PostMapping("/auth/logout")
public Result<Void> logout(@RequestHeader("Authorization") String header,
                           Authentication auth) {
    String token = header.substring(7);
    jwtService.blacklist(token);
    Long userId = (Long) auth.getPrincipal();
    redis.delete("refresh:" + userId);
    return Result.ok(null);
}
```

### 四、Refresh 接口（含轮换）

```java
@PostMapping("/auth/refresh")
public Result<TokenPair> refresh(@RequestBody RefreshReq req) {
    Claims c = jwtService.verify(req.refreshToken());
    if (!"refresh".equals(c.get("type"))) {
        throw new BadCredentialsException("not a refresh token");
    }
    Long userId = Long.valueOf(c.getSubject());
    String stored = redis.opsForValue().get("refresh:" + userId);
    if (!req.refreshToken().equals(stored)) {
        redis.delete("refresh:" + userId);
        throw new BadCredentialsException("refresh reused - re-login required");
    }
    List<String> roles = c.get("roles", List.class);
    return Result.ok(jwtService.issue(userId, roles));
}
```

### 五、关键点

1. **签发 / 验证集中在一个 Service**：避免散落各处导致密钥读取不一致。
2. **类型字段（type）**：access 调业务接口，refresh 只能用来 /auth/refresh，互不能替代。
3. **黑名单 TTL = token 剩余有效期**：过期后 token 本身无效，黑名单自然清理，省 Redis 空间。
4. **catch JwtException 别打 ERROR 日志**：客户端传过期 token 是常态，INFO/DEBUG 即可。

---

## Q5（综合）：博客系统接入 JWT 后业务方反馈两个问题：

1. 用户改密码后，原 JWT 还能继续用（直到自然过期）
2. 管理员封禁账号后，那人还能操作直到 token 过期

请设计一个不破坏 JWT 无状态优势的解决方案。

**参考答案：**

### 一、问题诊断

JWT 是"自包含 + 无状态"，签发后服务端**没记忆**它存在过。改密 / 封号属于"撤销"语义，与无状态天然冲突。

### 二、方案对比

| 方案 | 优点 | 缺点 | 适用 |
|------|------|------|------|
| 全量黑名单 | 立即生效 | Redis 存储多（每发一个就一条） | 撤销频次低 |
| 短 TTL（5min） + 频繁 refresh | 无状态 | 用户体验差，refresh 压力大 | 不推荐 |
| **token 版本号 + 缓存用户表** | 立即生效，存储小 | 验签后多查一次 Redis | **推荐** |
| 滚动密钥 | 一键失效所有 token | 全员重登 | 紧急止血 |

### 三、推荐方案：用户版本号

**1. user 表加字段**：

```sql
ALTER TABLE user ADD COLUMN token_version INT DEFAULT 0;
ALTER TABLE user ADD COLUMN status TINYINT DEFAULT 1;
```

**2. 签发 token 时把当前版本号塞进 payload**：

```java
public TokenPair issue(Long userId, List<String> roles) {
    int ver = userMapper.getTokenVersion(userId);
    return Jwts.builder()
        .subject(userId.toString())
        .claim("ver", ver)
        .claim("roles", roles)
        ...
        .compact();
}
```

**3. 验签后多一步对比**：

```java
public Claims verify(String token) {
    Claims c = ...;
    Long userId = Long.valueOf(c.getSubject());
    int tokenVer = c.get("ver", Integer.class);

    UserStatus status = userStatusCache.get(userId);
    if (status.status() == 0) throw new BadCredentialsException("user disabled");
    if (status.tokenVersion() > tokenVer) throw new BadCredentialsException("token outdated");
    return c;
}
```

**4. 触发版本号 +1 的操作**：

- 改密码：`UPDATE user SET token_version = token_version + 1 WHERE id = ?`
- 封禁：`UPDATE user SET status = 0`
- 用户主动"退出所有设备"：版本号 +1

**5. 缓存 user 状态**：

```java
@Cacheable("user-status")
public UserStatus get(Long userId) { return userMapper.selectStatus(userId); }

@CacheEvict("user-status")
public void onChange(Long userId) {}
```

改密 / 封禁后立即 evict 缓存。

### 四、性能估算

| 项 | 数字 |
|----|------|
| 每次验签新增成本 | 1 次 Redis GET（~0.5ms） |
| 缓存命中率 | > 99% |
| Redis 内存 | 每用户 ~50 字节，10w 用户约 5MB |

对比"全量黑名单"：每发一个 token 存一条，10w 用户日活每天 100w token，Redis 撑不住。版本号方案与用户数线性。

### 五、其他配套

| 维度 | 措施 |
|------|------|
| 改密接口 | 改完后让前端跳登录页（旧 token 已无效） |
| 封禁接口 | 同步删除 `refresh:{userId}` |
| 紧急止血 | 准备"全局滚动密钥"开关 |
| 监控 | 验签失败率 / 版本 mismatch 数量曲线 |
| 单测 | 改密 → 旧 token 401 → 新登录 OK |

### 六、收益对比

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 改密生效 | ≤ Access TTL（最坏 30min） | < 1s |
| 封号生效 | ≤ Access TTL | < 1s |
| 验签性能损失 | 0 | +0.5ms |
| Redis 存储 | 0 | 用户数 × 50 字节 |
| 客户端改动 | 0 | 0 |

**关键点**：JWT 的"无状态"是相对的，**完全无状态 = 不可撤销**。生产 JWT 实践是"无状态 80% + 关键操作回源"，比纯无状态多 0.5ms，比纯 Session 少一次 Redis 查询。这是工程平衡点。
