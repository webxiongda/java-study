# Chapter 44 Spring Security 入门 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：Spring Security 的 Filter Chain 是什么？为什么强调"链"？

**参考答案：**

Filter Chain 是 Spring Security 在 Servlet Filter 体系上加的一组**有序、可扩展**的过滤器，整体由 `FilterChainProxy` 调度，每个 SecurityFilterChain 是一个独立的链（可多个，匹配不同 URL 模式）。

**核心特点**：

| 维度 | 说明 |
|------|------|
| **顺序敏感** | 例如 `SecurityContextHolderFilter` 必须在所有需要读 Authentication 的 Filter 之前 |
| **职责单一** | 每个 Filter 只做一件事（Csrf / Cors / Auth / Logout / ExceptionTranslation …）|
| **可裁剪** | `csrf(c -> c.disable())` 关掉一个；`addFilterBefore` 插入自定义 |
| **链终止** | 任意 Filter 不 `chain.doFilter()` 即可短路 |

**典型链路**（删减版）：

```
HeaderWriter → CorsFilter → CsrfFilter → JwtAuthFilter →
UsernamePasswordAuthenticationFilter → AnonymousAuthenticationFilter →
ExceptionTranslationFilter → AuthorizationFilter
```

**为什么必须用"链"而不是单个 Filter**：

1. 关注点分离：CSRF、CORS、认证、授权各自独立可替换。
2. 框架可演进：6.x 删了 Adapter 但链结构不变，业务代码无感。
3. 安全可审计：每一个 Filter 都有明确职责，便于对 OWASP / CVE 检查。
4. 性能：未命中链的请求快速过（permitAll），命中的才走完整链。

**面试常考**：

- "FilterChainProxy 和 DelegatingFilterProxy 区别？" → DelegatingFilterProxy 是 Servlet 容器入口（Tomcat 的 Filter），把请求委托给 Spring 管理的 FilterChainProxy。
- "为什么不能直接在 Spring MVC Interceptor 里做安全？" → Interceptor 在 DispatcherServlet 之后，太晚了；Filter 在 Servlet 层，早于路由。

---

## Q2（概念）：`UserDetailsService` / `AuthenticationProvider` / `AuthenticationManager` 三者关系？

**参考答案：**

### 调用链

```
请求带 username/password
   ↓
AuthenticationManager.authenticate(token)   ← 顶层入口
   ↓
ProviderManager (默认实现) → 遍历 AuthenticationProvider 列表
   ↓
匹配的 AuthenticationProvider.authenticate(token)
   ↓
（如 DaoAuthenticationProvider）调 UserDetailsService.loadUserByUsername()
   ↓
PasswordEncoder.matches() 比对
   ↓
返回 Authentication (已认证)
```

### 各自职责

| 组件 | 职责 | 你通常需要做什么 |
|------|------|------------------|
| `AuthenticationManager` | 顶层接口，外部调它做认证 | 一般用默认 `ProviderManager` |
| `AuthenticationProvider` | 负责**某种**认证方式（密码 / SMS / OAuth2） | 自定义新登录方式时实现 |
| `UserDetailsService` | 按 username 加载用户 + 权限 | **必须**自定义 |
| `PasswordEncoder` | 密码哈希与比对 | 配 `BCryptPasswordEncoder(12)` |

### 实际项目典型选择

| 场景 | 实现 |
|------|------|
| 用户名 + 密码登录 | `DaoAuthenticationProvider` + 自定义 `UserDetailsService` |
| 手机号 + 验证码 | 自定义 `AuthenticationProvider` + `SmsCodeAuthenticationToken` |
| 第三方登录（OAuth2） | 用 `spring-security-oauth2-client` |
| JWT 模式 | 不走标准 AuthenticationManager；在 Filter 里直接 `verify token → setContext` |

**面试常考**：

- "自定义登录方式怎么做？" → 写一个 `Token` 类继承 `AbstractAuthenticationToken`，写一个 `Provider` 实现 `supports(Class)` 和 `authenticate(Auth)`，注册到 `AuthenticationManager`。
- "JWT 还要 AuthenticationManager 吗？" → 不强制。JWT Filter 自己验签 + setContext 即可，绕过标准认证链。

---

## Q3（实操）：以下 SecurityConfig 有 6 处问题，找出并改正。

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {           // ①

    @Override
    protected void configure(HttpSecurity http) throws Exception {            // ②
        http
            .authorizeRequests()                                              // ③
                .antMatchers("/api/auth/**").permitAll()                      // ④
                .anyRequest().authenticated()
            .and()
            .formLogin()                                                       // ⑤
            .and()
            .csrf().disable();
    }

    @Bean
    public PasswordEncoder pwd() {
        return NoOpPasswordEncoder.getInstance();                             // ⑥
    }
}
```

**参考答案：**

**① 继承 WebSecurityConfigurerAdapter**

Security 5.7 deprecated，6.x 已删除。改 Bean 式：

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception { ... }
}
```

**② `protected void configure`**

老 API，替换为 `@Bean SecurityFilterChain`。

**③ `authorizeRequests`**

6.x 是 `authorizeHttpRequests`。

```java
.authorizeHttpRequests(a -> a.requestMatchers(...) ...)
```

**④ `antMatchers`**

6.x 是 `requestMatchers`。

**⑤ formLogin**

JWT / 移动端项目不应启用表单登录，否则 Boot 会自动跳 `/login` HTML 页。

```java
.formLogin(f -> f.disable())
.httpBasic(b -> b.disable())
```

**⑥ NoOpPasswordEncoder**

明文存密码，**生产绝对禁止**。改：

```java
@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
```

**修复后的版本**：

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler))
            .build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
```

---

## Q4（实操）：实现一个手机号 + 短信验证码登录，复用 Spring Security 体系（不自己写 Filter）。

**参考答案：**

### 一、定义 SmsCodeAuthenticationToken

```java
public class SmsCodeAuthenticationToken extends AbstractAuthenticationToken {
    private final String phone;
    private final String code;
    private Object principal;

    // 未认证时
    public SmsCodeAuthenticationToken(String phone, String code) {
        super(null);
        this.phone = phone;
        this.code = code;
        setAuthenticated(false);
    }

    // 认证后
    public SmsCodeAuthenticationToken(Object principal,
            Collection<? extends GrantedAuthority> auths) {
        super(auths);
        this.principal = principal;
        this.phone = null;
        this.code = null;
        super.setAuthenticated(true);
    }

    @Override public Object getCredentials() { return code; }
    @Override public Object getPrincipal() { return principal != null ? principal : phone; }
}
```

### 二、SmsCodeAuthenticationProvider

```java
@Component
@RequiredArgsConstructor
public class SmsCodeAuthenticationProvider implements AuthenticationProvider {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Override
    public Authentication authenticate(Authentication auth) {
        SmsCodeAuthenticationToken token = (SmsCodeAuthenticationToken) auth;
        String phone = (String) token.getPrincipal();
        String inputCode = (String) token.getCredentials();

        String stored = redis.opsForValue().get("sms:" + phone);
        if (stored == null || !stored.equals(inputCode)) {
            throw new BadCredentialsException("验证码错误或过期");
        }
        redis.delete("sms:" + phone);   // 一次性

        UserDO u = userMapper.findByPhone(phone);
        if (u == null) {
            // 新手机号自动注册（业务决定）
            u = userMapper.createByPhone(phone);
        }
        List<GrantedAuthority> auths = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new SmsCodeAuthenticationToken(u.getId(), auths);
    }

    @Override
    public boolean supports(Class<?> cls) {
        return SmsCodeAuthenticationToken.class.isAssignableFrom(cls);
    }
}
```

### 三、注册到 AuthenticationManager

```java
@Bean
public AuthenticationManager authManager(
        DaoAuthenticationProvider dao,
        SmsCodeAuthenticationProvider sms) {
    return new ProviderManager(List.of(dao, sms));
}
```

### 四、Controller 入口

```java
@PostMapping("/auth/login/sms")
public Result<TokenPair> loginBySms(@RequestBody SmsLoginReq req,
                                    AuthenticationManager authManager,
                                    JwtService jwtService) {
    Authentication auth = authManager.authenticate(
        new SmsCodeAuthenticationToken(req.phone(), req.code()));
    return Result.ok(jwtService.issue((Long) auth.getPrincipal(), List.of("USER")));
}

@PostMapping("/auth/sms-code")
public Result<Void> sendCode(@RequestParam String phone) {
    String code = String.format("%06d", new Random().nextInt(1000000));
    redis.opsForValue().set("sms:" + phone, code, Duration.ofMinutes(5));
    smsClient.send(phone, "您的验证码：" + code);
    return Result.ok(null);
}
```

### 五、关键点

1. **不要写 Filter**：复用 `AuthenticationManager` 体系，未来加邮箱 / 微信登录就再加 Provider。
2. **验证码一次性**：验证后立即删 Redis，防重放。
3. **手机号防爆破**：同 IP / 手机号 5 分钟内只能发 1 次（业务限流）。
4. **新用户自动注册**：业务决定，写明在文档里。
5. **错误信息脱敏**：手机号不存在 / 验证码错都报"验证码错误或过期"。

---

## Q5（综合）：你的博客 API 接入了 Spring Security 6.x。现在要支持 3 类调用方：

1. Web 浏览器（前后端分离 SPA）
2. iOS / Android App
3. 第三方合作伙伴（机器对机器）

请设计 Security 配置，让这 3 类调用方都用合适的认证机制，且不互相干扰。

**参考答案：**

### 一、需求拆解

| 调用方 | 推荐认证 | 原因 |
|--------|---------|------|
| Web SPA | JWT (Bearer) + CSRF（如果 token 走 Cookie） | 浏览器主流 |
| 移动 App | JWT (Bearer) | 无 Cookie 概念 |
| 第三方机器 | OAuth2 Client Credentials Grant / API Key + HMAC | 客户端凭证 |

### 二、用"多 SecurityFilterChain"分流

Spring Security 支持多个 `SecurityFilterChain` Bean，按 `securityMatcher` 分配请求：

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // 1. 第三方 API：API Key + HMAC
    @Bean
    @Order(1)
    public SecurityFilterChain apiKeyChain(HttpSecurity http,
                                            ApiKeyAuthFilter apiKeyFilter) throws Exception {
        return http
            .securityMatcher("/api/partner/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    // 2. Web + App：JWT
    @Bean
    @Order(2)
    public SecurityFilterChain jwtChain(HttpSecurity http,
                                         JwtAuthFilter jwtFilter) throws Exception {
        return http
            .securityMatcher("/api/v1/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    // 3. 内部 / 默认：放行
    @Bean
    @Order(3)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
            .csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .build();
    }
}
```

### 三、第三方机器认证（API Key + HMAC）

```java
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) throws ... {
        String apiKey = req.getHeader("X-Api-Key");
        String signature = req.getHeader("X-Signature");
        String timestamp = req.getHeader("X-Timestamp");

        // 1. 防重放：时间戳与服务器相差 ≤ 5min
        long ts = Long.parseLong(timestamp);
        if (Math.abs(System.currentTimeMillis() - ts) > 300_000) {
            resp.sendError(401, "stale request"); return;
        }

        // 2. 查 API Key 对应的 secret
        Partner partner = partnerService.findByApiKey(apiKey);
        if (partner == null) { resp.sendError(401); return; }

        // 3. HMAC 重算
        String expected = hmacSha256(partner.getSecret(),
            req.getMethod() + req.getRequestURI() + timestamp + bodyHash(req));
        if (!MessageDigest.isEqual(expected.getBytes(), signature.getBytes())) {
            resp.sendError(401); return;
        }

        // 4. 注入 SecurityContext
        var auth = new UsernamePasswordAuthenticationToken(
            partner.getId(), null,
            List.of(new SimpleGrantedAuthority("ROLE_PARTNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, resp);
    }
}
```

### 四、CORS 分组

Web SPA 需要严格 CORS，App / Partner 不走浏览器不需要：

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();

    CorsConfiguration web = new CorsConfiguration();
    web.setAllowedOriginPatterns(List.of("https://*.your-domain.com"));
    web.setAllowedMethods(List.of("*"));
    web.setAllowCredentials(true);
    src.registerCorsConfiguration("/api/v1/**", web);

    // 合作伙伴接口不开放浏览器跨域
    src.registerCorsConfiguration("/api/partner/**", new CorsConfiguration());
    return src;
}
```

### 五、监控与限流

| 维度 | Web | App | Partner |
|------|-----|-----|---------|
| QPS 限流 | 同 IP 100/s | 同 token 50/s | 同 apiKey 200/s（合同约定） |
| 错误告警 | 401 突增 | 401 突增 | HMAC 失败 → 立即告警（疑似伪造） |
| 审计 | 登录 / 退出 | 登录 / 退出 | 所有请求落表 |
| Token 撤销 | 版本号方案 | 版本号方案 | 后台立即禁用 apiKey |

### 六、收益对比

| 维度 | 单一 Chain | 多 Chain 分流 |
|------|------------|---------------|
| 配置复杂度 | 简单 | 中等 |
| 安全隔离 | 全部走同一个 Filter | 不同 URL 不同 Filter，互不影响 |
| 性能 | Filter 链长，所有请求走完 | 只走匹配的链 |
| 扩展性 | 加新认证方式需大改 | 加新 `@Order(N)` 即可 |

**关键点**：多调用方场景下，**不要把所有逻辑塞进一个 SecurityFilterChain**。Spring Security 6.x 的 `securityMatcher + @Order` 就是为这种场景设计的。这是生产级 API Gateway 的标准模式。
