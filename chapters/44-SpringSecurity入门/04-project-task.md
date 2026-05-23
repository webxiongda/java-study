# Chapter 44 SpringSecurity入门 - 项目任务

## 任务概述

把博客 API 的"裸 Filter + 手动 401/403"全部替换为 **Spring Security 6.x SecurityFilterChain**，让认证、授权、异常处理、CORS 都走 Security 的标准链路。完成后:

- `JwtAuthFilter` 注册进 Security 链, `addFilterBefore` 在 `UsernamePasswordAuthenticationFilter` 之前
- `/api/v1/auth/**`、`/swagger-ui/**`、`/v3/api-docs/**` 放行, 其余必须认证
- 401 / 403 都返回统一 JSON, 不再 sendError
- 全局 `BCryptPasswordEncoder` 注入, 注册和登录复用同一个 Bean
- CORS preflight 不会被 Security 拦掉

## 业务背景

41-43 章为了快速跑通, 自己在 `OncePerRequestFilter` 里 `chain.doFilter` 后判断 `SecurityContext`、自己写 401/403 响应、用 `if/else` 控制角色。Spring Security 已经把这套做完了, 我们再造轮子的风险:

1. **过滤器顺序乱**: 自定义 Filter 没接在 Security 链里, OPTIONS、CSRF、登录都可能错位
2. **异常处理双轨**: `@RestControllerAdvice` 接不到 `AuthenticationException`, 因为它在 Filter 里就被吞了
3. **方法级注解不生效**: 没 `@EnableMethodSecurity`, `@PreAuthorize` 形同虚设
4. **PasswordEncoder 散落**: 多处 `new BCryptPasswordEncoder()`, 强度不一致

本章是认证授权工程化的收口, 后续 47 (OAuth2) / 45 (Web 安全) 都基于这套链路扩展。

## 任务拆解

### Step 1: 引入依赖 + 关闭默认登录页 (15 分钟)

`pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

启动后默认所有接口被保护 + 默认登录页, 进入下一步前需替换为自定义 `SecurityFilterChain`。

### Step 2: 写 SecurityConfig (40 分钟)

`com.javastudy.config.SecurityConfig`:

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/**",
                                 "/swagger-ui/**",
                                 "/v3/api-docs/**",
                                 "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(this::onAuthError)
                .accessDeniedHandler(this::onForbidden))
            .build();
    }

    private void onAuthError(HttpServletRequest req, HttpServletResponse resp, AuthenticationException ex) throws IOException {
        resp.setStatus(HttpStatus.UNAUTHORIZED.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(resp.getOutputStream(),
            Result.error(401, "未认证或 Token 失效"));
    }

    private void onForbidden(HttpServletRequest req, HttpServletResponse resp, AccessDeniedException ex) throws IOException {
        resp.setStatus(HttpStatus.FORBIDDEN.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(resp.getOutputStream(),
            Result.error(403, "权限不足: " + ex.getMessage()));
    }
}
```

### Step 3: 改造 AuthService 用注入的 PasswordEncoder (15 分钟)

之前 41 章可能写的是 `new BCryptPasswordEncoder()`, 现在改为构造注入:

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;   // <- 注入, 不要自己 new
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public TokenPair login(LoginReq req) {
        UserDO u = userMapper.findByUsername(req.username());
        if (u == null || !passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        return jwtService.issue(u.getId(), Map.of());
    }

    public void register(RegisterReq req) {
        String hash = passwordEncoder.encode(req.password());
        userMapper.insert(new UserDO(null, req.username(), hash, null));
    }
}
```

### Step 4: CORS 配置 (20 分钟)

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
    cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
}
```

前端 5173 (Vite) 能正确发起带 Authorization 的跨域请求, OPTIONS 不被拦。

### Step 5: 全局异常 + 401/403 联通 (20 分钟)

`GlobalExceptionHandler` 不再处理 `AuthenticationException` / `AccessDeniedException` (它们在 Filter 里就被 Security 接住了, 不会冒到 `@ControllerAdvice`)。但要补:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<?> handleValid(MethodArgumentNotValidException ex) { ... }

@ExceptionHandler(BadCredentialsException.class)
public Result<?> handleBadCred() { return Result.error(401, "用户名或密码错误"); }
```

> 注: `BadCredentialsException` 是在 Controller / Service 里抛出的, 会到 `@ControllerAdvice`; 而 `AuthenticationException` 由 Filter 抛, 走 entryPoint。

### Step 6: 单元测试 (30 分钟)

`PostControllerTest`:

```java
@SpringBootTest
@AutoConfigureMockMvc
class PostControllerTest {

    @Autowired MockMvc mvc;

    @Test
    @WithAnonymousUser
    void getPublic_ok() throws Exception {
        mvc.perform(get("/api/v1/posts/1")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void postNeedsAuth_401() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER", authorities = "post:write")
    void postAsUser_ok() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType("application/json")
                .content("{\"title\":\"hi\",\"content\":\"x\"}"))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpointAsUser_403() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
    }
}
```

### Step 7: 端到端 curl 验证 (15 分钟)

```bash
# 未认证 GET 公开接口 → 200
curl -i localhost:8080/api/v1/posts

# 未认证 POST → 401 JSON
curl -i -X POST localhost:8080/api/v1/posts \
  -H 'Content-Type: application/json' -d '{"title":"hi"}'

# 登录拿 token
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Aa12345!"}' | jq -r .data.access)

# 普通用户访问 admin → 403 JSON
curl -i localhost:8080/api/v1/admin/users -H "Authorization: Bearer $TOKEN"

# OPTIONS preflight → 200 (CORS)
curl -i -X OPTIONS localhost:8080/api/v1/posts \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST'
```

## 交付物

- [ ] `SecurityConfig` Bean: SecurityFilterChain + PasswordEncoder + AuthenticationManager + CorsConfigurationSource
- [ ] `JwtAuthFilter` 改造为 `OncePerRequestFilter`, 由 SecurityConfig `addFilterBefore` 注册 (不要在它上面加 `@Component` + `@WebFilter`, 避免双重注册)
- [ ] `AuthService` 使用注入的 `PasswordEncoder`, 全局只此一处定义
- [ ] `GlobalExceptionHandler` 移除对 `AuthenticationException` 的处理, 改由 `authenticationEntryPoint` 统一
- [ ] `application.yml` 删掉 `spring.security.user.name/password` (默认账号密码不要再生效)
- [ ] 至少 4 个 `@WithMockUser` 单测
- [ ] curl 验证脚本放在 `docs/sec-smoke.sh`
- [ ] git commit: `ch44: integrate Spring Security 6 SecurityFilterChain`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 默认登录页 | 启动后访问 `/swagger-ui/index.html` 不弹出 form login |
| 401 响应 | 未认证请求返回 `{"code":401,"msg":"未认证或 Token 失效"}`, Content-Type=application/json |
| 403 响应 | 角色不足请求返回 `{"code":403,"msg":"权限不足: ..."}` |
| CORS preflight | OPTIONS 请求 200 且不需要 token |
| `@PreAuthorize` 生效 | `@WithMockUser(authorities="post:write")` 通过, 不带权限 403 |
| PasswordEncoder 单例 | `grep -r "new BCryptPasswordEncoder" src/main` 仅出现在 `SecurityConfig` |
| Filter 链顺序 | `JwtAuthFilter` 在 `UsernamePasswordAuthenticationFilter` 之前 (打印 `http.getFilterChainProxy()` 验证) |
| Swagger | `/v3/api-docs` 和 `/swagger-ui/**` 无需认证可访问 |

## 扩展挑战

1. **多 SecurityFilterChain 拆分**: Web 端用 Session, App 端用 JWT, 通过两个 `@Bean SecurityFilterChain` + `@Order` + `securityMatcher` 拆分, 见 03-check.md Q5。
2. **CSRF 重启**: Web 端开启 CSRF (`http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))`), 验证不带 token 的 POST 被拒。
3. **AccessDeniedHandler 审计**: 在 `onForbidden` 里同时打 `WARN` 日志 + 写 `auth_audit` 表, 记录 userId / path / method / time。
4. **自定义 SecurityExpressionRoot**: 扩展 `hasOwnership(#id)` 这样的项目内 SpEL 函数, 避免在每个 `@PreAuthorize` 里都写 `@postSec.isOwner(...)`。
5. **去掉 `permitAll()` 名单, 改为元注解 `@PublicApi`**: 通过自定义 `RequestMatcher` 扫描带注解的方法, 实现"白名单可以贴在接口上"。
