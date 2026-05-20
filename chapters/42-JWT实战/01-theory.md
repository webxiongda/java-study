# Chapter 42 JWT 实战 - 理论

> 前置：[[41-认证基础]]
> 后续：[[43-权限控制]] [[44-SpringSecurity入门]]
> 优先级：L1  预计：4 小时

## 1. 为什么需要这一章

Session-Cookie 方案在多实例 + 跨域 + 移动端时痛苦：要么粘性会话，要么共享 Redis。 JWT 把「身份证明」放进 Token 自身，**服务端无状态**，水平扩展和 SSO 都简单。 代价是「主动登出 / 改密注销」需要额外机制（黑名单 / 短期 Access + 长期 Refresh）。

## 2. JWT 结构

`xxxxx.yyyyy.zzzzz` 三段，Base64URL 编码：

- **Header**：`{"alg":"HS256","typ":"JWT"}`
- **Payload（Claims）**：`{"sub":"123","roles":["USER"],"exp":1716000000,"iat":1715996400,"jti":"uuid"}`
- **Signature**：`HMACSHA256(base64Url(header)+"."+base64Url(payload), secret)`

**关键标准 Claims**：
- `sub` 主体（一般放 userId）
- `exp` 过期时间戳（秒）
- `iat` 签发时间
- `jti` Token 唯一 ID（黑名单要用）
- `iss` 签发者
- `aud` 受众

## 3. 签名算法选择

| 算法 | 类型 | 适用 |
|---|---|---|
| **HS256** | 对称（HMAC-SHA256） | 单系统签发 + 验证 |
| **RS256** | 非对称（RSA 私钥签，公钥验） | 多系统：网关私钥签，下游用公钥验 |
| **ES256** | 非对称（ECDSA） | 同 RS256 但密钥更小 |

入门用 **HS256**；密钥至少 32 字节随机串，放 `application-prod.yml` + 环境变量，**绝不进 Git**。

## 4. Access + Refresh 双 Token 设计（生产标准）

```
                Login (账号密码)
                     │
                     ▼
           Server 校验 → 颁发
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
  Access Token                Refresh Token
  寿命 15 分钟                 寿命 7-30 天
  存内存/localStorage          存 HttpOnly Cookie 或 DB
  每次请求带                   只在续签接口带
                     │
              Access 过期
                     │
                     ▼
              POST /auth/refresh  (带 Refresh)
                     │
              Server 验 Refresh + 签发新 Access
                     │
              （可选）签发新 Refresh 并废弃旧的（rotation）
```

**为什么不直接长 Token**：长 Token 一旦泄漏，攻击者随便用一天。 短 Access 把窗口压到 15 分钟。

**主动登出怎么做**：
- 简单版：Refresh Token 存 Redis，key 为 `refresh:{jti}` → 登出时删 key。
- 严苛版：Access Token 的 `jti` 也进 Redis 黑名单 TTL = Access 寿命。

## 5. 密码哈希

**禁止**：明文、MD5、SHA1。 必须用「加盐 + 慢哈希」：

- **BCrypt**（推荐）：内置 salt + cost factor（10-12），Spring Security 默认。
- Argon2id：更现代，CPU + 内存开销可调。
- 不用 `MessageDigest` 自己拼盐——大概率写错。

```java
PasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("123456");           // 入库
boolean ok = encoder.matches("123456", hashFromDb); // 验证
```

## 6. 拦截器 / Filter 在哪一层

在 Spring Security 6.x 体系里，JWT 解析放在 `OncePerRequestFilter` 子类里，注册到 `SecurityFilterChain`，位置在 `UsernamePasswordAuthenticationFilter` **之前**：

```
Request → JwtAuthFilter (解析 Bearer Token → SecurityContext) → 业务 Filter → Controller
```

不要自己写 `HandlerInterceptor` 做鉴权——Security FilterChain 是标准位置。

## 7. 常见安全坑

- **alg=none**：早期 JJWT 漏洞，反序列化时被改 alg 绕过签名。 用 `jjwt 0.12+` 默认禁。
- **Secret 写死在代码**：用环境变量 / KMS。
- **Refresh 用同一 secret + 同一 alg**：建议至少不同密钥；rotation 后旧 Refresh 立刻失效。
- **Token 放 URL**：日志会记录，泄漏风险。 一律 `Authorization: Bearer xxx`。
- **不校验 `exp`**：JJWT 默认会校验，但你自己解析 Payload 时容易漏。

## 8. 与项目衔接

博客 API：
- `POST /api/auth/login` → 颁发 Access + Refresh。
- `POST /api/auth/refresh` → 用 Refresh 换新 Access。
- `POST /api/auth/logout` → 删 Redis 里的 Refresh + 把当前 Access 的 jti 加黑名单。
- 所有 `/api/articles/**` 写接口都在 Security 链里要求已认证。
