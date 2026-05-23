# Chapter 43 权限控制 - 项目任务

## 任务概述

在 42 章 JWT 基础上加 **RBAC 鉴权 + 数据级权限**：所有写操作都要鉴权，普通用户只能改自己的文章，管理员可改任何文章，`/admin/**` 路径只有管理员能访问。

## 业务背景

42 章解决"你是谁"，43 章解决"你能做什么"。没有权限控制，任何登录用户都能删别人的文章——这是 P0 安全事故。生产 Web 系统必须：

- 接口级：哪些角色 / 权限能调
- 数据级：能调，但只能操作自己的数据

## 任务拆解

### Step 1：RBAC 5 表 + 种子数据（30 分钟）

按 02-demo.md 的 SQL 建 `role` / `permission` / `user_role` / `role_permission`。

种子数据：
- 角色：ADMIN、USER
- 权限：`post:read`、`post:write`、`post:delete-any`、`user:manage`
- ADMIN 拿全部权限，USER 拿 `post:read`、`post:write`
- 注册新用户时自动绑 USER 角色

### Step 2：UserMapper.findAuthsByUsername（30 分钟）

写一条三表 JOIN SQL，返回 `UserWithAuthsDO { id, username, passwordHash, roles, permissions }`。

用 `<collection>` resultMap 处理多对多。

### Step 3：签 JWT 时塞 roles + perms（15 分钟）

修改 41 / 42 章的 `AuthService.login`，调用 `findAuthsByUsername` 后，把 roles 和 perms 写入 JWT claims。

### Step 4：JwtAuthFilter 注入 SecurityContext（30 分钟）

- roles 加 `ROLE_` 前缀，perms 不加。
- 全部转 `SimpleGrantedAuthority`，写入 `UsernamePasswordAuthenticationToken.authorities`。

### Step 5：URL 级控制 + 方法级控制（45 分钟）

`SecurityConfig`：

```java
.requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.POST, "/api/v1/posts").hasAuthority("post:write")
.anyRequest().authenticated()
```

`@EnableMethodSecurity` + Service 层 `@PreAuthorize`：

```java
@PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
public void update(Long id, ...) { ... }
```

`@Component("postSec") PostSecurityChecker.isOwner` 查 `post.author_id` 与当前用户对比。

### Step 6：AdminController（30 分钟）

```
GET    /api/v1/admin/users         列出所有用户（分页）
PUT    /api/v1/admin/users/{id}/disable  封禁
DELETE /api/v1/admin/posts/{id}    删任意文章
```

类级 `@PreAuthorize("hasRole('ADMIN')")`。

### Step 7：测试（60 分钟）

- `PostServiceTest`：`@WithMockUser(authorities="post:write")` 调 create → ok；不带 → AccessDeniedException。
- `PostSecurityCheckerTest`：自己 / 他人 / null 三种 case。
- `RbacIT`：完整链路。alice 改自己 200、bob 改 alice 403、admin 改 alice 200、alice 调 admin 接口 403。

### Step 8：补全异常处理 + 日志（30 分钟）

- `GlobalExceptionHandler` 加：
  - `AuthenticationException` → 401
  - `AccessDeniedException` → 403
- 所有 403 记日志 `WARN`，含 userId / resource / action，便于审计。

## 交付物

- [ ] 5 张 RBAC 表 + 种子数据 SQL
- [ ] `UserMapper.findAuthsByUsername`（含 collection resultMap）
- [ ] `AuthService.login` 把 roles + perms 写入 JWT
- [ ] `JwtAuthFilter` 解析 roles + perms 进 SecurityContext
- [ ] `SecurityConfig` URL 级规则
- [ ] `PostService` 加 `@PreAuthorize`
- [ ] `PostSecurityChecker` + 单测
- [ ] `AdminController` 至少 3 个接口
- [ ] 端到端集成测试覆盖 6 个场景
- [ ] README 加"权限模型"段落（含 ER 图 / 表）
- [ ] git commit：`ch43: RBAC + data-level ownership check`

## 验收清单

| 验收项 | 标准 |
|--------|------|
| 未认证写操作 | 401 |
| 普通用户调 admin | 403 |
| 普通用户改他人文章 | 403 |
| 普通用户改自己文章 | 200 |
| 管理员改任何人文章 | 200 |
| 撤销 USER 的 `post:write` | 当前 token 内仍能写，下次登录失效（与 42 章一致） |
| 审计日志 | 403 时 log 含 userId / path / method |
| Swagger 安全标记 | 写接口加 `@SecurityRequirement(name="bearer")` |

## 扩展挑战

1. **`@DataScope` 注解 + MyBatis Interceptor**（见 03-check.md Q4）：业务方法零侵入注入 `WHERE author_id = ?`。
2. **权限管理后台接口**：`POST /admin/users/{id}/roles` 给用户分角色，触发 `bumpTokenVersion` 让旧 token 立即失效。
3. **审计表**：`auth_audit(user_id, action, resource_id, granted, ts, ip)`，所有鉴权决策落表，便于查"谁在什么时候删了什么"。
4. **接 Casbin / OPA**：把策略从代码 / 注解搬到外部策略文件，体验 ABAC。
5. **超大用户量优化**（见 03-check.md Q5）：JWT 瘦身、缓存权限、版本号撤销。
