# Chapter 41 认证基础 - 实操 Demo

## Demo 目标

实现注册 + 登录 + 退出最小闭环：BCrypt 哈希存密码、登录失败计数、登录成功签发简单 Token（先用 UUID 存 Redis，42 章换成 JWT）。

**完成后能演示**：
- `POST /auth/register` 注册新用户 → 201
- `POST /auth/login` 登录 → 拿到 token
- `GET /me` 带 token → 返回当前用户
- `POST /auth/logout` 退出 → token 失效
- 故意输错密码 5 次 → 锁 15 分钟

## 前置条件

- JDK 21 + Maven
- MySQL 8 + Redis 7（无 Redis 可用 ConcurrentHashMap 兜底）
- 项目基于 40 章 blog-api，新增 `auth` 包

## 一、依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

> 引入 `spring-boot-starter-security` 是为了拿 `BCryptPasswordEncoder`。如果想完全手写，可只引 `org.springframework.security:spring-security-crypto`。

## 二、数据库

```sql
CREATE TABLE IF NOT EXISTS user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(128) NOT NULL,
  password_hash VARCHAR(60) NOT NULL COMMENT 'BCrypt 固定 60 字符',
  status TINYINT DEFAULT 1 COMMENT '1 启用 0 禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**为什么 `password_hash` 用 `varchar(60)`**：BCrypt 输出固定 60 字符，长度不变。

## 三、配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379

auth:
  login:
    max-fail: 5
    lock-minutes: 15
  token:
    ttl-minutes: 30
```

```java
@Configuration
public class AuthConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // cost=12 大约 250ms
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new TokenAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

## 四、注册接口

```java
public record RegisterReq(
    @NotBlank @Size(min=4, max=32) String username,
    @Email String email,
    @NotBlank @Size(min=8, max=64) String password
) {}

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Long> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;

    @Transactional
    public Long register(RegisterReq req) {
        if (userMapper.existsByUsername(req.username())) {
            throw new BusinessException(ErrorCode.USER_EXISTS, "用户名已被占用");
        }
        UserDO user = new UserDO();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        userMapper.insert(user);
        return user.getId();
    }
}
```

**关键点**：

1. **唯一性靠 DB 唯一索引兜底**，应用层先查再插会有并发漏洞。捕获 `DuplicateKeyException` 统一报"用户名已被占用"。
2. 返回**不带 password_hash**。`UserVO` 只暴露 id / username / email。

## 五、登录接口（含失败计数）

```java
public record LoginReq(
    @NotBlank String username,
    @NotBlank String password
) {}

public record LoginResp(String token, Long userId, String username) {}

@PostMapping("/login")
public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
    return Result.ok(authService.login(req));
}
```

```java
public LoginResp login(LoginReq req) {
    String lockKey = "login:lock:" + req.username();
    if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
        throw new BusinessException(ErrorCode.LOGIN_LOCKED, "账号已锁定，请 15 分钟后重试");
    }

    UserDO user = userMapper.findByUsername(req.username());
    boolean ok = user != null && encoder.matches(req.password(), user.getPasswordHash());

    if (!ok) {
        String failKey = "login:fail:" + req.username();
        Long fails = redis.opsForValue().increment(failKey);
        redis.expire(failKey, Duration.ofMinutes(15));
        if (fails != null && fails >= 5) {
            redis.opsForValue().set(lockKey, "1", Duration.ofMinutes(15));
        }
        throw new BusinessException(ErrorCode.AUTH_FAILED, "用户名或密码错误");
    }

    redis.delete("login:fail:" + req.username());
    String token = UUID.randomUUID().toString().replace("-", "");
    redis.opsForValue().set("auth:token:" + token,
        user.getId().toString(), Duration.ofMinutes(30));
    return new LoginResp(token, user.getId(), user.getUsername());
}
```

**关键点**：

1. **统一错误信息**：用户不存在 / 密码错误返回同一句话，防用户名枚举。
2. **失败计数与锁定分两个 key**：fail 计数 15 分钟自动衰减，lock 独立 TTL 便于运营手工解锁。
3. **Token 不要用顺序 ID 或可猜的字符串**，至少 128bit 熵。

## 六、Token 校验 Filter

```java
public class TokenAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            StringRedisTemplate redis = SpringContextHolder.bean(StringRedisTemplate.class);
            String userId = redis.opsForValue().get("auth:token:" + token);
            if (userId != null) {
                redis.expire("auth:token:" + token, Duration.ofMinutes(30));   // 滑动续期
                var authToken = new UsernamePasswordAuthenticationToken(
                    Long.valueOf(userId), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        chain.doFilter(req, resp);
    }
}
```

## 七、当前用户接口

```java
@GetMapping("/me")
public Result<UserVO> me(Authentication auth) {
    Long userId = (Long) auth.getPrincipal();
    return Result.ok(userService.getById(userId));
}
```

## 八、退出接口

```java
@PostMapping("/logout")
public Result<Void> logout(@RequestHeader("Authorization") String auth) {
    String token = auth.substring(7);
    redis.delete("auth:token:" + token);
    return Result.ok(null);
}
```

## 九、运行与验证

```bash
mvn spring-boot:run

# 注册
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"a@x.com","password":"Aa12345!"}'
# → {"code":0, "data": 1}

# 登录
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Aa12345!"}' | jq -r .data.token)

# 带 token
curl http://localhost:8080/me -H "Authorization: Bearer $TOKEN"
# → {"code":0, "data": {"id":1,"username":"alice","email":"a@x.com"}}

# 错密码 5 次 → 锁
for i in {1..6}; do
  curl -X POST http://localhost:8080/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"alice","password":"wrong"}'
  echo
done
# 第 6 次返回"账号已锁定"

# 退出
curl -X POST http://localhost:8080/auth/logout -H "Authorization: Bearer $TOKEN"
# 再调 /me → 401
```

## 十、错误场景演示

| 场景 | 期望 |
|------|------|
| 注册时 username 已存在 | 409 `用户名已被占用` |
| 注册时 password 长度 < 8 | 400 + 字段错误 |
| 登录时密码错 | 401 `用户名或密码错误`（与"用户不存在"同提示） |
| 登录连错 5 次 | 后续登录返回 `账号已锁定` |
| `/me` 不带 token | 401 |
| `/me` 带过期 token | 401 |

## 十一、常见坑

1. `BCryptPasswordEncoder` 强度参数默认是 10，生产建议 ≥ 12。注意 cost 每 +1 慢一倍，单测里别用 12。
2. Filter 里直接 `new TokenAuthFilter()` 拿不到 Spring Bean，要么注入构造器、要么通过 `SpringContextHolder` 手动取。
3. 失败次数用 `incr` 后必须 `expire`，否则 key 永久存在，攻击者重置后能继续刷。
4. Token TTL 不能太长（>1 天就该用 JWT + refresh）。
5. 退出时如果 token 已经过期，`redis.delete` 不会报错（幂等），不要额外校验。

## 提交

```bash
git add .
git commit -m "ch41: auth foundation - register/login/logout with bcrypt and login throttling"
```
