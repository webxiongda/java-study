# Chapter 43 权限控制（RBAC） - 理论

> 前置：[[42-JWT实战]]
> 后续：[[44-SpringSecurity入门]]
> 优先级：L2 项目常用  预计：4 小时

## 1. 为什么需要这一章

42 章解决了「你是谁」（认证 Authentication），但还没解决「你能做什么」（授权 Authorization）。 实际系统里管理员能删任何文章，普通用户只能删自己的——这就是**权限**。 RBAC 是工业界用得最多的模型。

## 2. 三种模型对比

| 模型 | 含义 | 适用 |
|---|---|---|
| **ACL**（Access Control List） | 每个资源直接挂用户列表 | 文件系统、Linux 权限 |
| **RBAC**（Role-Based） | 用户 → 角色 → 权限 | 后台系统、绝大多数 Web 应用 ✅ |
| **ABAC**（Attribute-Based） | 基于用户/资源属性 + 策略 | 复杂多租户、云权限（IAM） |

## 3. RBAC 数据模型（5 表 / 3 表两种）

### 标准 5 表（用户-角色-权限多对多）

```sql
-- 用户
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,           -- BCrypt 哈希
  enabled  TINYINT NOT NULL DEFAULT 1
);

-- 角色
CREATE TABLE role (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL UNIQUE,         -- ADMIN, USER, EDITOR
  name VARCHAR(50) NOT NULL
);

-- 权限（粒度到接口或资源动作）
CREATE TABLE permission (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL UNIQUE,        -- article:read, article:delete
  name VARCHAR(100) NOT NULL
);

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);
```

### 简化 3 表（用户-角色，角色字符串列表权限）

小项目直接 `user.roles VARCHAR` 存 JSON 数组也行。 博客 API 推荐 5 表，便于后台动态配权限。

## 4. 在 Spring Security 6 中落地

### 4.1 加载用户 + 权限

```java
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User u = userMapper.findWithRolesAndPermissions(username);
        if (u == null) throw new UsernameNotFoundException(username);

        List<GrantedAuthority> auths = new ArrayList<>();
        // 角色必须以 ROLE_ 开头，hasRole("ADMIN") 才能匹配
        u.getRoles().forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r.getCode())));
        u.getPermissions().forEach(p -> auths.add(new SimpleGrantedAuthority(p.getCode())));

        return new org.springframework.security.core.userdetails.User(
            u.getUsername(), u.getPassword(), u.isEnabled(), true, true, true, auths);
    }
}
```

> JWT 模式下，签发 token 时把权限列表写入 claims；过滤器解析后直接放进 `UsernamePasswordAuthenticationToken` 的 authorities。

### 4.2 URL 级控制

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.GET, "/api/articles/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.DELETE, "/api/articles/**").hasAuthority("article:delete")
    .anyRequest().authenticated());
```

### 4.3 方法级控制（更细）

```java
@Configuration
@EnableMethodSecurity   // Spring Security 6 默认开启 @PreAuthorize
public class MethodSecurityConfig {}

@Service
public class ArticleService {

    @PreAuthorize("hasRole('ADMIN') or @articleAuth.isOwner(#id, authentication)")
    public void delete(Long id) { ... }

    @PreAuthorize("hasAuthority('article:write')")
    public void publish(Long id) { ... }

    @PostAuthorize("returnObject.authorId == authentication.principal.id or hasRole('ADMIN')")
    public Article detail(Long id) { ... }
}

@Component("articleAuth")
public class ArticleAuthChecker {
    public boolean isOwner(Long articleId, Authentication auth) {
        // ... 查 DB 判断
    }
}
```

### 4.4 注解一览

| 注解 | 时机 | 用途 |
|---|---|---|
| `@PreAuthorize` | 方法执行**前** | 最常用，按参数 / authentication 决定 |
| `@PostAuthorize` | 方法执行**后** | 按返回值判定（如「只能查自己的」） |
| `@Secured("ROLE_ADMIN")` | 简单角色检查 | 老式写法 |
| `@RolesAllowed` | 同上，JSR-250 | 同上 |

## 5. 权限粒度选择

| 粒度 | 例子 | 适合 |
|---|---|---|
| 角色级（粗） | `hasRole("ADMIN")` | 后台系统、内部工具 |
| 接口级 | `hasAuthority("article:delete")` | 中大型业务 |
| 数据级（行/字段） | 「只能删自己创建的」 | C 端业务（必备） |

数据级通常要在 SQL 里加 `WHERE author_id = #{currentUid}`，或用 MyBatis 拦截器 / MyBatis-Plus 数据权限插件统一注入。

## 6. 与 JWT 配合

签发 token 时把权限放进 claims，避免每次请求查 DB：

```java
String token = Jwts.builder()
    .subject(String.valueOf(user.getId()))
    .claim("roles", List.of("ADMIN"))
    .claim("perms", List.of("article:write", "article:delete"))
    .signWith(key)
    .compact();
```

权限变更后旧 token 仍带旧权限，3 种处理：
1. 等过期（短 TTL 15 分钟，一般可接受）。
2. 强制刷新（Redis 记录 user 的 version，token 带 version，不一致 401）。
3. 关键操作 + 双因素 / 二次确认。

## 7. 常见坑

- 忘记前缀 `ROLE_` → `hasRole("ADMIN")` 永远失败。 二者必须配对。
- 把权限明细全塞 token → 头变巨大，HTTP 限制 8KB。 角色 + 粗粒度权限就够。
- `@PreAuthorize` 写错 SpEL（`#id` 与方法参数名不一致）→ 默认编译时不报错，运行时永远 false。 加单测。
- 数据级权限漏一个查询 → 越权。 必须在 Service 层统一注入 `WHERE owner_id = ?`，别让 Controller 决定。
- 用 `@Secured` 又没开 `@EnableMethodSecurity(securedEnabled = true)` → 注解不生效。

## 8. 面试高频

1. 讲讲 RBAC 模型。 ABAC 区别？
2. Spring Security 怎么实现 URL + 方法两层鉴权？
3. JWT 模式下权限变更怎么生效？
4. `@PreAuthorize` 和 `@PostAuthorize` 区别？
5. 数据级权限怎么做？

更多 → [[interview-bank|面试题库]] `auth-rbac` 区。
