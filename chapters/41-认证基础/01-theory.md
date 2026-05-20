# Chapter 41 认证基础 - 理论

> 前置：[[40-里程碑：博客APIv1]]
> 后续：[[42-JWT实战]] → [[43-权限控制]] → [[44-SpringSecurity入门]]
> 优先级：L3 面试高频  预计：3 小时

## 1. 认证 vs 授权（Authentication vs Authorization）

| | 认证（AuthN） | 授权（AuthZ） |
|---|---|---|
| 问题 | 你是谁？ | 你能做什么？ |
| 输入 | 用户名 / 密码、Token、证书 | 已认证身份 + 资源/操作 |
| 输出 | 一个可信的身份（principal） | 允许 / 拒绝 |
| 本章 | ✅ | 详见 43 章 |

> 顺序：先 AuthN 后 AuthZ。 没认证就谈不了授权。

## 2. 三种主流方案对比

| 方案 | 状态 | 适用 | 优点 | 缺点 |
|---|---|---|---|---|
| Cookie + Session | 有状态（服务端存） | 传统单体 / 同域 Web | 撤销简单、成熟 | 不易水平扩展（要 Redis 共享 session）、跨域麻烦 |
| JWT | 无状态（自包含） | 前后端分离 / 移动端 / 微服务 | 跨域、可扩展 | 撤销难、Token 大、签名要保密 |
| OAuth2 / OIDC | 三方授权 | 第三方登录、开放平台 | 标准、第三方信任 | 学习成本高、自己实现易踩坑 |

> 本章只讲 Session 与 JWT。 OAuth2 自学。

## 3. Session 流程

```
1. 客户端 POST /login { user, pwd }
2. 服务端校验 → 生成 sessionId（随机 UUID）→ Redis: sid -> userId, TTL=30m
3. Set-Cookie: JSESSIONID=sid; HttpOnly; Secure; SameSite=Lax
4. 后续请求带 Cookie，服务端用 sid 查 Redis 拿 userId
5. 退出：删除 Redis 中的 sid
```

**Cookie 关键属性**：

- `HttpOnly`：JS 无法读，防 XSS 偷 Cookie。
- `Secure`：仅 HTTPS 传输。
- `SameSite=Lax/Strict`：防 CSRF。
- `Domain` / `Path`：作用域。

## 4. JWT 结构（细节见 42 章）

```
header.payload.signature
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZXhwIjoxNzMyMjA4MDAwfQ.{HMAC-SHA256}
```

- header: `{"alg":"HS256","typ":"JWT"}`
- payload: `sub` 用户 id、`iat` 签发时间、`exp` 过期、自定义 claims（roles、tenant）。
- signature: 用密钥（HS256 对称 / RS256 非对称）签 `base64(header).base64(payload)`。

**核心特性**：

- 无状态：服务端不存，只验签。
- **不加密**：payload base64 可读，**别放敏感数据**（密码、手机号、身份证）。
- **不可改**：改了签名对不上。
- **难撤销**：签出去就生效到 exp，需要黑名单（详见 42）。

## 5. 密码存储：BCrypt（重点）

**绝不**用 MD5 / SHA1 / SHA256 存密码。 它们快，彩虹表 + GPU 秒破。

**正确方案：BCrypt（或 Argon2 / scrypt / PBKDF2）**

```java
String hash = BCrypt.hashpw(rawPwd, BCrypt.gensalt(12));   // cost=12，约 250ms
boolean ok  = BCrypt.checkpw(input, hash);
```

特点：

- 自带 salt：每次 hash 不同，彩虹表失效。
- **可调慢**：cost 每 +1 慢一倍。 2026 年推荐 cost ≥ 12。
- 60 字符固定长度，存 `varchar(60)`。

Spring Security 直接用 `PasswordEncoder`：

```java
@Bean PasswordEncoder pe() { return new BCryptPasswordEncoder(12); }
```

> **历史密码迁移**：用 `DelegatingPasswordEncoder`，前缀 `{bcrypt}xxx` / `{noop}plain`，逐步替换。

## 6. 注册 / 登录流程设计

### 注册

```
1. 校验：用户名长度/字符、密码强度（≥8、含字母数字符号）、邮箱格式
2. 唯一性：username / email 加 DB 唯一索引（别只靠 select 查，并发会重）
3. 密码：BCrypt.hash(cost=12)
4. 落库：user(id, username, email, password_hash, created_at, status)
5. 可选：邮箱验证、初始角色 ROLE_USER
6. 返回：不要把密码 hash 返给前端
```

### 登录

```
1. 限流：同一 username/IP 5 次失败 → 锁 15 分钟（Redis 计数）
2. 查 user by username（统一报「用户名或密码错」，不暴露用户是否存在）
3. BCrypt.checkpw 比对
4. 生成 token（Session 或 JWT，见 42）
5. 记录登录日志（IP、UA、时间）
6. 返回 token + 用户信息（不含密码 hash）
```

> 用户名 / 密码错误必须返回**同一种错误**，否则攻击者能枚举用户名。

## 7. 常见攻击与防护

| 攻击 | 原理 | 防护 |
|---|---|---|
| **撞库** | 拿别站泄露的库来试 | 限流 + 验证码 + 异地登录提醒 |
| **暴力破解** | 字典爆破密码 | 限流 + BCrypt 慢哈希 + 锁定 |
| **彩虹表** | 预计算 hash 反查 | salt + 慢哈希（BCrypt 自带） |
| **重放** | 抓包重发请求 | HTTPS + nonce + 短期 token |
| **XSS** | 注入脚本偷 Cookie/Token | Cookie HttpOnly、CSP、转义、详见 45 |
| **CSRF** | 借用户 Cookie 发请求 | SameSite + CSRF token，详见 45 |
| **会话固定** | 攻击者预先植入 sid | 登录成功后必须**重新生成 sid** |
| **JWT 签名绕过** | alg=none 攻击 | 服务端校验 alg 白名单，不信任 header 中的 alg |
| **时序攻击** | 比较密码时长泄漏信息 | `MessageDigest.isEqual` 常量时间比较 |

## 8. Refresh Token 设计（细节见 42）

```
Access Token（短，15min，带在 Authorization 头，每次请求验签）
Refresh Token（长，7d，仅 /auth/refresh 接口用，HttpOnly Cookie 或专门存储）

流程：
1. 登录 → 返回 access + refresh
2. access 过期 → 用 refresh 调 /auth/refresh → 拿新 access（+ 轮换 refresh）
3. refresh 过期 → 重新登录
4. 退出 → access 加入 Redis 黑名单（TTL=剩余过期时间），refresh 删除
```

**关键点**：

- Refresh 必须**一次性**（轮换）：用过即废，防盗用。
- Refresh 存数据库或 Redis（便于撤销），不要全靠 JWT 自包含。

## 9. 项目场景对照

| 场景 | 推荐 |
|---|---|
| 单体 + Web 前端同域 | Session + Redis 共享 |
| 前后端分离 / 移动端 | JWT + Refresh |
| 多服务（微服务） | JWT（网关验签） |
| 对接第三方登录 | OAuth2 / OIDC |
| 后台管理（高安全） | Session + 二次验证（MFA） |

## 10. 常见坑

- 密码用 MD5 / SHA256 存 → 一次脱库全完。 必须 BCrypt / Argon2。
- 登录失败提示「用户不存在 / 密码错误」分开 → 用户枚举。
- 没限流 → 暴力破解。
- JWT 放 `password` / `身份证` → base64 可解，等于明文。
- JWT 签名密钥写在代码 / 配置文件提交 git → 泄露后所有 token 失效。 用环境变量 / KMS。
- 用 `String.equals` 比较签名 → 时序攻击。 用 `MessageDigest.isEqual`。
- Refresh Token 不轮换 → 一次泄露永久失陷。
- Session 登录后不换 sid → 会话固定攻击。

## 11. 面试高频

1. 认证和授权区别？
2. Session 和 JWT 各自优缺点，怎么选？
3. 密码为什么不能用 MD5/SHA256 存？BCrypt 优势？
4. JWT 能不能存敏感数据，为什么？
5. JWT 怎么实现退出登录？
6. 怎么设计 Refresh Token？为什么要双 token？
7. 怎么防暴力破解 / 撞库？
8. Cookie 的 HttpOnly / Secure / SameSite 各防什么？

更多 → [[interview-bank|面试题库]] 安全区。
