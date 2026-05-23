# Chapter 43 权限控制 - 实操 Demo

## Demo 目标

在 42 章 JWT 基础上加 RBAC：用户分两种角色（ADMIN / USER），权限分接口级和数据级。完成后能演示：

- 普通用户能改 / 删**自己的**文章
- 普通用户**不能**改 / 删别人的文章 → 403
- 管理员能改 / 删任何文章
- `/api/v1/admin/**` 只有管理员能访问

## 前置条件

- 已完成 41 / 42 章（认证 + JWT）
- MySQL 8 / Redis 7
- Spring Security 6（Boot 3.3 默认）

## 一、数据库（RBAC 5 表 + 种子数据）

```sql
CREATE TABLE role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL
);

CREATE TABLE permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

INSERT INTO role(code, name) VALUES
  ('ADMIN','管理员'), ('USER','普通用户');

INSERT INTO permission(code, name) VALUES
  ('post:read','读取文章'),
  ('post:write','发布文章'),
  ('post:delete-any','删除任意文章');

-- ADMIN 拿所有权限
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.code='ADMIN';

-- USER 只能 read + write
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code='USER' AND p.code IN ('post:read','post:write');

-- 新用户默认 USER
INSERT INTO user_role(user_id, role_id)
SELECT u.id, r.id FROM user u, role r WHERE r.code='USER';
```

## 二、查询用户角色 + 权限（UserMapper）

```xml
<resultMap id="userWithAuths" type="UserWithAuthsDO">
  <id property="id" column="user_id"/>
  <result property="username" column="username"/>
  <result property="passwordHash" column="password_hash"/>
  <collection property="roles" ofType="String">
    <result column="role_code"/>
  </collection>
  <collection property="permissions" ofType="String">
    <result column="perm_code"/>
  </collection>
</resultMap>

<select id="findAuthsByUsername" resultMap="userWithAuths">
  SELECT u.id AS user_id, u.username, u.password_hash,
         r.code AS role_code, p.code AS perm_code
  FROM user u
  LEFT JOIN user_role ur ON ur.user_id = u.id
  LEFT JOIN role r ON r.id = ur.role_id
  LEFT JOIN role_permission rp ON rp.role_id = r.id
  LEFT JOIN permission p ON p.id = rp.permission_id
  WHERE u.username = #{username}
</select>
```

## 三、签 JWT 时塞 roles + perms

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public TokenPair login(LoginReq req) {
        UserWithAuthsDO u = userMapper.findAuthsByUsername(req.username());
        // ... 校验密码 ...

        Map<String, Object> extra = Map.of(
            "roles", u.getRoles(),
            "perms", u.getPermissions()
        );
        return jwtService.issue(u.getId(), extra);
    }
}
```

## 四、JwtAuthFilter 把权限放进 SecurityContext

```java
@Override
protected void doFilterInternal(...) throws ... {
    List<String> roles = claims.get("roles", List.class);
    List<String> perms = claims.get("perms", List.class);

    List<SimpleGrantedAuthority> auths = new ArrayList<>();
    roles.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
    perms.forEach(p -> auths.add(new SimpleGrantedAuthority(p)));

    var token = new UsernamePasswordAuthenticationToken(userId, null, auths);
    SecurityContextHolder.getContext().setAuthentication(token);
}
```

> **关键**：roles 必须以 `ROLE_` 前缀注入，`hasRole("ADMIN")` 才能匹配；perms 不加前缀。

## 五、URL 级控制（SecurityConfig）

```java
@Bean
public SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/posts").hasAuthority("post:write")
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, resp, ex) -> resp.sendError(401, "Unauthorized"))
            .accessDeniedHandler((req, resp, ex) -> resp.sendError(403, "Forbidden")))
        .build();
}
```

## 六、方法级 + 数据级控制（@PreAuthorize）

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {}
```

```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostMapper postMapper;

    public PostVO getById(Long id) { ... }

    @PreAuthorize("hasAuthority('post:write')")
    public Long create(PostCreateReq req, Long userId) { ... }

    @PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
    public void update(Long id, PostUpdateReq req) { ... }

    @PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
    public void delete(Long id) { ... }
}

@Component("postSec")
@RequiredArgsConstructor
public class PostSecurityChecker {
    private final PostMapper postMapper;

    public boolean isOwner(Long postId, Authentication auth) {
        Long uid = (Long) auth.getPrincipal();
        Long authorId = postMapper.selectAuthorId(postId);
        return authorId != null && authorId.equals(uid);
    }
}
```

## 七、Admin 接口示例

```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")   // 类级生效
public class AdminController {

    @GetMapping("/users")
    public Result<List<UserVO>> listAllUsers() { ... }

    @PutMapping("/users/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) { ... }
}
```

## 八、运行与验证

```bash
mvn spring-boot:run

# 1. 登录普通用户 alice
TOKEN_ALICE=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Aa12345!"}' | jq -r .data.access)

# 2. alice 发文章
POST_ID=$(curl -s -X POST localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H 'Content-Type: application/json' \
  -d '{"title":"hello","content":"world","tagIds":[1]}' | jq -r .data)

# 3. alice 改自己的文章 → 200
curl -X PUT localhost:8080/api/v1/posts/$POST_ID \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -d '{"title":"updated"}' -H 'Content-Type: application/json'

# 4. bob 想改 alice 的文章 → 403
TOKEN_BOB=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -d '{"username":"bob","password":"Aa12345!"}' \
  -H 'Content-Type: application/json' | jq -r .data.access)
curl -X PUT localhost:8080/api/v1/posts/$POST_ID \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -d '{"title":"hacked"}' -H 'Content-Type: application/json'
# → 403 Forbidden

# 5. admin 改任何人的文章 → 200
TOKEN_ADMIN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -d '{"username":"admin","password":"Admin123!"}' \
  -H 'Content-Type: application/json' | jq -r .data.access)
curl -X PUT localhost:8080/api/v1/posts/$POST_ID \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -d '{"title":"by admin"}' -H 'Content-Type: application/json'

# 6. alice 访问 admin 接口 → 403
curl localhost:8080/api/v1/admin/users -H "Authorization: Bearer $TOKEN_ALICE"
```

## 九、错误场景

| 场景 | 期望 |
|------|------|
| 不带 token 访问 `POST /api/v1/posts` | 401 |
| Token 篡改 | 401 |
| 普通用户访问 `/api/v1/admin/**` | 403 |
| 普通用户改 / 删别人的文章 | 403 |
| 用户被管理员封禁后访问 | 401 |
| 撤了 USER 的 post:write 权限 | 当前 token 仍能写，下次登录失效 |

## 十、常见坑

1. **roles 没加 `ROLE_` 前缀** → `hasRole("ADMIN")` 永远 false。
2. **`@PreAuthorize` SpEL `#id` 与方法参数名不一致** → 永远 false。**写单测**。
3. **数据级权限漏判** → 越权。所有 update/delete 都过 `@PreAuthorize`，禁止裸方法。
4. **权限变更不立即生效** → JWT 自包含。等过期 / 版本号 / 短 TTL。
5. **OPTIONS 请求被拦截** → CORS preflight 不带 token，要 `permitAll()` 或 CorsFilter 优先。

## 提交

```bash
git add .
git commit -m "ch43: RBAC with @PreAuthorize + data-level ownership check"
```
