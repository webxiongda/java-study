# Chapter 41 认证基础 - 项目任务

## 任务概述

在 40 章博客 API v1 的基础上加上**注册 + 登录 + 退出**最小闭环，密码用 BCrypt 存，token 用 Redis 存（v2 阶段 42 章再换 JWT）。完成后博客系统从"任何人都能 CRUD"变成"必须登录才能发文章"。

## 业务背景

v1 时所有接口都允许匿名访问，演示用。v2 之前必须先把"用户身份"这件事接进来：

- 谁发的文章 → `post.author_id`
- 谁能改 / 删自己的文章 → 鉴权（43 章）
- 谁能进后台 → 权限（43 章）

41 章只解决**认证**（你是谁），43 章解决**授权**（你能做什么）。

## 任务拆解

### Step 1：建表 + 用户模型（30 分钟）

按 02-demo.md 的 SQL 建 `user` 表。补充 `created_at` `updated_at` 字段。

`com.example.blog.user.UserDO` / `UserMapper` / `UserMapper.xml`：

- `Long insert(UserDO)`（用 `<selectKey>` 取自增 id）
- `boolean existsByUsername(String)`
- `UserDO findByUsername(String)`
- `void updatePasswordHash(Long id, String hash)`

### Step 2：注册接口（30 分钟）

```
POST /api/v1/auth/register
Body: { "username": "alice", "email": "a@x.com", "password": "Aa12345!" }
Resp: 201 { "code": 0, "data": 123 }   // userId
```

要求：
- `RegisterReq` 用 record，3 个字段加 jakarta.validation 注解。
- 校验失败 → 400（全局异常处理器接管）。
- 密码用 `BCryptPasswordEncoder(12)` hash。
- DB 唯一索引 + 捕获 `DuplicateKeyException` 统一报 `用户已存在`。

### Step 3：登录接口 + 失败计数（45 分钟）

```
POST /api/v1/auth/login
Body: { "username": "alice", "password": "Aa12345!" }
Resp: { "code": 0, "data": { "token": "...", "userId": 1, "username": "alice" } }
```

要求：
- 失败统一报"用户名或密码错误"，无论是用户不存在还是密码不对。
- 失败计数：同 username 失败 5 次 → 锁 15 分钟。
- 成功后清除失败计数。
- Token 用 `SecureRandom` 生成 32 字节，Base64-URL，存 Redis `auth:token:{token}` → `userId`，TTL 30 分钟。

### Step 4：TokenAuthFilter + /me 接口（45 分钟）

```
GET /api/v1/me
Header: Authorization: Bearer {token}
Resp: { "code": 0, "data": { "id": 1, "username": "alice", "email": "a@x.com" } }
```

要求：
- 写 `TokenAuthFilter extends OncePerRequestFilter`。
- 从 Redis 取 userId，写入 `SecurityContextHolder`。
- 滑动续期：每次访问 token TTL 重置为 30 分钟。
- 在 `SecurityConfig` 注册 Filter，`permitAll("/api/v1/auth/**")`，其余 `authenticated()`。

### Step 5：退出接口（15 分钟）

```
POST /api/v1/auth/logout
Header: Authorization: Bearer {token}
Resp: 204
```

要求：
- 删 Redis 中的 token key（幂等）。
- 即使 token 已过期也返回 204。

### Step 6：把 post 接到登录态（30 分钟）

之前 `PostController.create` 写死 `userId=1`，现在改为：

```java
@PostMapping("/api/v1/posts")
public Result<Long> create(@Valid @RequestBody PostCreateReq req,
                           Authentication auth) {
    Long userId = (Long) auth.getPrincipal();
    return Result.ok(postService.publish(req, userId));
}
```

效果：未登录调 → 401，登录后调 → 文章 `author_id` 是当前用户。

### Step 7：测试 + 文档（45 分钟）

- `AuthServiceTest`：注册成功 / 用户名重复 / 登录成功 / 密码错 / 连错 5 次锁定。
- `AuthControllerIT`：完整流程 register → login → /me → logout → /me（401）。
- README 增加"认证流程"段落，含 curl 示例。

## 交付物

- [ ] `user` 表 + `UserMapper`
- [ ] `auth/AuthController, AuthService, RegisterReq, LoginReq, LoginResp`
- [ ] `auth/TokenAuthFilter` + `SecurityConfig` 配置
- [ ] `PostController` 改为从 Authentication 取 userId
- [ ] 6 个测试用例全绿
- [ ] README 加 "Authentication" 段
- [ ] git commit：`ch41: register/login/logout + bcrypt + login throttling`

## 验收清单

| 验收项 | 标准 |
|--------|------|
| 注册成功 | 数据库有记录，password_hash 以 `$2a$12$` 开头 |
| 注册重复 | 返回 409，错误信息脱敏 |
| 登录失败统一提示 | 用户名不存在 / 密码错都返回"用户名或密码错误" |
| 失败锁定 | 连错 5 次后第 6 次返回"账号已锁定"，Redis 有 `login:lock:{user}` |
| Token 鉴权 | 无 token 访问 `/me` → 401，带 token → 200 |
| 滑动续期 | 用 token 访问后 TTL 重置到 30min |
| 退出 | 退出后 `/me` 401 |
| 发文章带身份 | `author_id` 是登录用户 id，不是 1 |

## 扩展挑战

1. **接 Spring Security**：把 Filter 换成 `AuthenticationProvider + AbstractAuthenticationProcessingFilter`，体验框架式写法。准备 44 章。
2. **支持邮箱登录**：登录时 username 字段也接受邮箱，自动判断。
3. **图形验证码**：连错 3 次后强制要求验证码（接极验 / hCaptcha / 自建）。
4. **登录日志**：所有登录尝试（成功/失败/IP/UA）写表 `login_history`，为后续风控做素材。
5. **Refresh Token 占位**：登录响应同时返回 `refreshToken`（用 UUID 占位），下章 42 用 JWT 替换。
