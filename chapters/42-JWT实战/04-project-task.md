# Chapter 42 JWT 实战 - 项目任务

## 任务概述

把 41 章的"UUID token + Redis"方案升级为 **JWT 双 token（Access 15min + Refresh 7d）+ 黑名单退出 + 用户版本号撤销**。完成后博客系统具备生产级认证能力。

## 业务背景

41 章 token 存 Redis，每次验证都要查 Redis，且无法跨服务共享。JWT 后：

- 多服务无状态验签，网关层即可拦截非法请求
- 改密 / 封禁能立即生效（版本号方案）
- 主动退出依赖黑名单（仅退出场景写 Redis，平时不写）

## 任务拆解

### Step 1：引入 JJWT 依赖（10 分钟）

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### Step 2：配置 + 密钥（15 分钟）

`application.yml`：

```yaml
jwt:
  secret: ${JWT_SECRET}              # base64 编码的 32 字节
  access-ttl: 900000                 # 15 min
  refresh-ttl: 604800000             # 7 d
  issuer: blog-api
```

生成密钥：

```bash
# 32 字节 = 256 bit，HS256 最小要求
openssl rand -base64 32
export JWT_SECRET="..."
```

**严禁**把 secret 进 Git。`.env.example` 放占位，真正的值放 shell / 容器环境变量 / Vault。

### Step 3：JwtService（60 分钟）

实现 `issue / verify / blacklist / refresh` 四个方法（见 03-check.md Q4）。

要求：
- 用 `Jwts.parser().verifyWith(key()).build()` 构造解析器，显式校验签名。
- access 和 refresh 在 payload 里加 `type` 字段区分。
- jti 用 `UUID.randomUUID().toString()`。
- 黑名单 key 用 jti：`jwt:blacklist:{jti}`。

### Step 4：替换 41 章的 TokenAuthFilter（30 分钟）

把 `TokenAuthFilter` 改为 `JwtAuthFilter`：

- 从 `Authorization: Bearer xxx` 取 token
- `jwtService.verify(token)` 验签 + 黑名单 + 用户版本号
- 把 userId + roles 写入 SecurityContext
- 验签失败 → clearContext（不抛异常，让 Security 默认 401）

把 41 章 Redis 里的 token 数据迁移：

```bash
# 让所有用户重登
redis-cli --scan --pattern 'auth:token:*' | xargs redis-cli del
```

### Step 5：登录 / 退出 / 刷新（45 分钟）

```
POST /api/v1/auth/login    → 返回 { access, refresh }
POST /api/v1/auth/refresh  → 用 refresh 换新 (access, refresh)，旧 refresh 立即作废
POST /api/v1/auth/logout   → 把当前 access 加黑名单，删 refresh
```

### Step 6：用户版本号撤销机制（45 分钟）

```sql
ALTER TABLE user ADD COLUMN token_version INT DEFAULT 0;
```

- 改密接口：成功后 `token_version + 1`，清除 user-status 缓存
- 封禁接口（管理员）：`status = 0`，清除缓存
- `JwtService.verify` 增加：取 user-status 缓存对比 `tokenVersion` 和 `status`

### Step 7：测试（45 分钟）

- `JwtServiceTest`：签发 → 解析 sub / exp / roles。
- `JwtAuthFilterIT`：合法 token → 200；篡改 → 401；过期 → 401；黑名单 → 401。
- `RefreshIT`：refresh 成功；旧 refresh 复用 → 401 + 所有 refresh 失效。
- `RevokeIT`：改密后旧 token 立即失效。

## 交付物

- [ ] `auth/JwtService` + `auth/JwtAuthFilter` + `auth/UserStatusCache`
- [ ] `application.yml` 中 JWT 配置（secret 从环境变量读）
- [ ] `auth/AuthController` 实现 login/refresh/logout
- [ ] `user` 表新增 `token_version` `status` 字段，含迁移 SQL
- [ ] 4 类测试用例全绿
- [ ] README 更新："认证"段落改为 JWT 流程图 + curl 示例
- [ ] git commit：`ch42: JWT auth with refresh rotation + blacklist + version revocation`

## 验收清单

| 验收项 | 标准 |
|--------|------|
| 签发 access | 长度约 200+ 字符，base64url 三段 |
| 验签 | 篡改 payload 后请求 → 401 `signature mismatch` |
| 过期 | 等 15min 后访问 → 401 `token expired` |
| 退出 | 退出后旧 access 访问 → 401 `token revoked` |
| Refresh 轮换 | 旧 refresh 复用 → 401 + 该用户所有 refresh 被吊销 |
| 改密生效 | 改密后旧 access 立即 401 |
| 封号生效 | 封号后旧 access 立即 401 |
| secret 未入 Git | `git log -p` grep `JWT_SECRET` 无明文 |
| 多实例验证 | 启动 2 个端口 8080 / 8081，token 互通 |

## 扩展挑战

1. **RS256 升级**：从 HS256（对称）换 RS256（非对称），公钥下发给前端 / 网关验签。
2. **JWKs 端点**：暴露 `/.well-known/jwks.json`，符合 OIDC 标准。
3. **设备级管理**：refresh 加 `deviceId` claim，"退出所有设备"删 `refresh:{userId}:*`。
4. **审计日志**：所有 issue / refresh / blacklist 操作写 `auth_audit` 表，含 IP/UA/ts。
5. **接 Spring Security 标准 OAuth2 Resource Server**：用 `spring-boot-starter-oauth2-resource-server`，把自己实现的 Filter 退役。
