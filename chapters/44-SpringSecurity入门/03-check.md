# Chapter 44 Spring Security 入门 - 自测与验收

> 模板见 `docs/superpowers/specs/2026-05-25-check-template.md`
> 覆盖率自检:`node scripts/check-coverage.mjs '^44-'`

---

### Q1 [L2·概念·章节内测] Spring Security 6.x Filter Chain 工作机制 + 关键 Filter 顺序

**考点**: Spring Security 6.x 整体架构, 关键 3 个角色, FilterChainProxy, 顺序敏感
**参考答案**:

Filter Chain 是 Spring Security 在 Servlet Filter 体系上加的一组**有序、可扩展**的过滤器,由 `FilterChainProxy` 调度,每个 `SecurityFilterChain` 是一个独立的链(可多个,匹配不同 URL 模式)。

**典型链路(顺序很重要)**:

```
HTTP Request
   ↓
[DelegatingFilterProxy]   ← Servlet 容器入口(Tomcat 看见的)
   ↓
[FilterChainProxy]        ← Spring 调度器,根据 URL 选 SecurityFilterChain
   ↓
SecurityContextHolderFilter   ← 从存储读 Authentication 到 ThreadLocal
HeaderWriterFilter            ← 加 X-Frame-Options 等安全 header
CorsFilter                    ← 跨域(必须在 Csrf 前)
CsrfFilter
LogoutFilter
JwtAuthFilter (你加的)        ← addFilterBefore(UsernamePasswordAuth...)
UsernamePasswordAuthenticationFilter  ← 表单登录用
AnonymousAuthenticationFilter
ExceptionTranslationFilter    ← 把 AccessDeniedException 转 401/403
AuthorizationFilter           ← 路由级授权
   ↓
DispatcherServlet → @Controller
```

**3 个核心角色**:

| 角色 | 职责 |
|---|---|
| `SecurityContextHolder` | ThreadLocal,存当前请求的 `Authentication` |
| `Authentication` | 身份(Principal) + 凭证(Credentials) + 权限(Authorities) + 是否已认证 |
| `GrantedAuthority` | 权限码,`hasRole/hasAuthority` 看的就是这个 |

**为什么必须用"链"而不是单个 Filter**:

1. **关注点分离**:CSRF / CORS / 认证 / 授权各自独立可替换
2. **框架可演进**:6.x 删了 `WebSecurityConfigurerAdapter` 但链结构不变,业务代码无感
3. **可裁剪**:`csrf(c -> c.disable())` 关一个;`addFilterBefore` 插自定义
4. **链终止**:任意 Filter 不调 `chain.doFilter()` 即可短路

**🔥追问**:`FilterChainProxy` 和 `DelegatingFilterProxy` 区别?

**参考答案**:`DelegatingFilterProxy` 是 Servlet 容器层的 Filter(Tomcat 直接调度),它的唯一职责是把请求委托给 Spring 上下文里名为 `springSecurityFilterChain` 的 Bean(即 `FilterChainProxy`)。两层分离是为了让 Spring 能管理 Security Filter 的生命周期(`@Autowired` / Bean scope),而 Servlet 容器只看到一个委托代理。

**🔥追问**:为什么 `JwtAuthFilter` 必须 `addFilterBefore(UsernamePasswordAuthenticationFilter.class)`?(答:`UsernamePasswordAuth...` 之后的 Filter 假设 `SecurityContext` 已注入,把 JWT 放它之前保证 token 先解析、用户身份先就位)

**关联**: interview-bank.md#spring-security-filter-chain

---

### Q2 [L2·概念·章节内测] UserDetailsService / AuthenticationProvider / AuthenticationManager 三者关系 + 与 Shiro 对比

**考点**: AuthenticationManager, AuthenticationProvider, UserDetailsService, PasswordEncoder
**参考答案**:

**调用链**:

```
请求带 username/password
   ↓
AuthenticationManager.authenticate(token)         ← 顶层入口
   ↓
ProviderManager(默认实现) → 遍历 AuthenticationProvider 列表
   ↓
匹配的 AuthenticationProvider.authenticate(token)
   ↓(如 DaoAuthenticationProvider)
UserDetailsService.loadUserByUsername()
   ↓
PasswordEncoder.matches() 比对
   ↓
返回 Authentication(已认证) → setContext
```

**各组件职责**:

| 组件 | 职责 | 通常要做什么 |
|---|---|---|
| `AuthenticationManager` | 顶层接口,外部调它做认证 | 一般用默认 `ProviderManager` |
| `AuthenticationProvider` | 负责某一种认证方式(密码 / SMS / OAuth2) | 自定义新登录方式时实现 |
| `UserDetailsService` | 按 username 加载用户 + 权限 | **必须**自定义,适配你的 DB |
| `PasswordEncoder` | 密码哈希与比对 | 配 `BCryptPasswordEncoder(12)` |

**典型场景选型**:

| 场景 | 实现 |
|---|---|
| 用户名 + 密码 | `DaoAuthenticationProvider` + 自定义 `UserDetailsService` |
| 手机号 + 验证码 | 自定义 `AuthenticationProvider` + `SmsCodeAuthenticationToken`(见 Q4) |
| 第三方(OAuth2) | `spring-security-oauth2-client` |
| JWT 模式 | 不走标准 AuthManager,Filter 自己验签 + setContext 即可 |

**Spring Security vs Apache Shiro 对比**(面试常问):

| 维度 | Spring Security | Shiro |
|---|---|---|
| 主战场 | Web / Spring 生态 | 通用(支持非 Spring 项目) |
| 学习曲线 | **陡峭**(Filter 链多,DSL 复杂) | **平缓**(API 简单) |
| Spring 集成 | 原生(`@Bean` / `@EnableMethodSecurity`) | 需要适配器 |
| OAuth2 / OIDC / SAML | 开箱即用 | 不支持(需第三方) |
| CSRF / CORS / Header / CSP | 内置 | 不直接管 |
| 社区与维护 | Spring 官方,演进快(6.x) | Apache,维护慢(2.x 更新少) |
| 选型建议 | Spring Boot 项目首选 | 非 Spring / 老项目 / OAuth2 不重要时 |

**🔥追问**:JWT 模式下还需要 `AuthenticationManager` 吗?

**参考答案**:**不强制**。JWT 流程是 token 验签 → 提取 userId/roles → 直接 `SecurityContextHolder.getContext().setAuthentication(...)`,绕过标准 Provider 链。`AuthenticationManager` 只在**登录(`/api/auth/login`)颁发 token 时**用,后续每个带 token 的请求由 `JwtAuthFilter` 自己处理。

**关联**: interview-bank.md#auth-manager-vs-provider

---

### Q3 [L2·Debug·面试高频] 6 处问题诊断:从 5.x 误用到生产级 6.x 配置

**考点**: WebSecurityConfigurerAdapter, antMatchers, authorizeHttpRequests, NoOpPasswordEncoder, 常见坑, formLogin
**参考答案**:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {   // ①

    @Override
    protected void configure(HttpSecurity http) throws Exception {    // ②
        http
            .authorizeRequests()                                       // ③
                .antMatchers("/api/auth/**").permitAll()               // ④
                .anyRequest().authenticated()
            .and()
            .formLogin()                                                // ⑤
            .and()
            .csrf().disable();
    }

    @Bean
    public PasswordEncoder pwd() {
        return NoOpPasswordEncoder.getInstance();                      // ⑥
    }
}
```

| # | 问题 | 修法 |
|---|---|---|
| ① | `extends WebSecurityConfigurerAdapter`,5.7 deprecated,6.x 删除 | 改 `@Bean SecurityFilterChain` Bean 式 |
| ② | `protected void configure(HttpSecurity)`,老 API | 改 `@Bean SecurityFilterChain chain(HttpSecurity http)` |
| ③ | `authorizeRequests` 是 5.x API | 6.x 用 `authorizeHttpRequests` |
| ④ | `antMatchers` 是 5.x API | 6.x 用 `requestMatchers` |
| ⑤ | JWT / 移动端项目不该开 formLogin,否则 Boot 自动跳 `/login` HTML 页 | `.formLogin(f -> f.disable()).httpBasic(b -> b.disable())` |
| ⑥ | **`NoOpPasswordEncoder` 明文存密码,生产绝对禁止** | `new BCryptPasswordEncoder(12)`(cost ≥ 12) |

**修复后的生产级版本**:

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())                              // JWT 模式关 CSRF
            .cors(Customizer.withDefaults())                     // 启用下面的 CorsBean
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/v3/api-docs/**",
                                 "/swagger-ui/**", "/actuator/health").permitAll()
                .requestMatchers(GET, "/api/articles/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, resp, e) ->
                    writeJson(resp, 401, ErrorCode.UNAUTHORIZED))   // 见 Q4
                .accessDeniedHandler((req, resp, e) ->
                    writeJson(resp, 403, ErrorCode.FORBIDDEN)))
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

**容易再栽的坑**:

| 现象 | 原因 | 修法 |
|---|---|---|
| `requestMatchers` 找不到 | 用了 5.x 的 `antMatchers` | 改 6.x API |
| `hasRole("ADMIN")` 永远 false | 权限里没加 `ROLE_` 前缀 | 数据加前缀,或改用 `hasAuthority("ADMIN")` |
| `@PreAuthorize` 不生效 | 没 `@EnableMethodSecurity` | 加注解 |
| CORS preflight 401 | 没 `http.cors()` 或 CorsFilter 不在 Security 之前 | 配 CORS Bean + `.cors()` |
| Session 还在创建 | 没 `STATELESS` | `sessionCreationPolicy(STATELESS)` |

**🔥追问**:`hasRole("ADMIN")` 和 `hasAuthority("ADMIN")` 有什么区别?

**参考答案**:`hasRole("ADMIN")` 内部会自动加 `ROLE_` 前缀去匹配 `ROLE_ADMIN`;`hasAuthority("ADMIN")` 就是字面匹配 `"ADMIN"`。设计本意是**用 ROLE_ 区分角色与细粒度权限**:`ROLE_ADMIN` / `ROLE_USER` 是角色,`post:write` / `comment:delete` 是 authority。建议团队内部统一一种习惯,不要混用。

**关联**: interview-bank.md#security-5x-to-6x-migration

---

### Q4 [L2·代码编写·面试高频] 401 vs 403 统一 JSON 响应 + 登录走 AuthenticationManager 颁发 JWT

**考点**: 异常处理:401 vs 403, AuthenticationEntryPoint, AccessDeniedHandler, writeJson
**参考答案**:

**401 vs 403 语义**:

| 异常类型 | 含义 | HTTP | 处理器 |
|---|---|---|---|
| `AuthenticationException` | 没认证 / 凭证不对 / token 过期 | **401** | `AuthenticationEntryPoint` |
| `AccessDeniedException` | 已认证但权限不够 | **403** | `AccessDeniedHandler` |

**实现统一 JSON 响应**(配 demo 的 `writeJson`):

```java
@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse resp,
                         AuthenticationException e) throws IOException {
        writeJson(resp, 401, "UNAUTHORIZED", "请登录后访问");
    }
}

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse resp,
                       AccessDeniedException e) throws IOException {
        writeJson(resp, 403, "FORBIDDEN", "权限不足");
    }
}

private static void writeJson(HttpServletResponse resp, int status,
                              String code, String msg) throws IOException {
    resp.setStatus(status);
    resp.setContentType("application/json;charset=UTF-8");
    String body = String.format(
        "{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
        code, msg, System.currentTimeMillis());
    resp.getWriter().write(body);
}

// SecurityConfig 注册
http.exceptionHandling(ex -> ex
    .authenticationEntryPoint(jsonAuthEntryPoint)
    .accessDeniedHandler(jsonAccessDeniedHandler));
```

**常见错误**:自定义了 EntryPoint / DeniedHandler 但**忘了在 `SecurityConfig.exceptionHandling` 里注册** → Spring 默认行为接管 → 返回 HTML 登录页或默认 403 页面。

**手机号 + 验证码登录(走 AuthenticationManager 体系)**:

```java
// 1. 自定义 Token
public class SmsCodeAuthenticationToken extends AbstractAuthenticationToken {
    private final String phone;
    private final String code;
    private Object principal;
    public SmsCodeAuthenticationToken(String phone, String code) {
        super(null); this.phone = phone; this.code = code;
        setAuthenticated(false);
    }
    public SmsCodeAuthenticationToken(Object principal,
            Collection<? extends GrantedAuthority> auths) {
        super(auths); this.principal = principal; this.phone = null; this.code = null;
        super.setAuthenticated(true);
    }
    @Override public Object getCredentials() { return code; }
    @Override public Object getPrincipal() { return principal != null ? principal : phone; }
}

// 2. Provider
@Component
@RequiredArgsConstructor
public class SmsCodeAuthenticationProvider implements AuthenticationProvider {
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Override
    public Authentication authenticate(Authentication auth) {
        SmsCodeAuthenticationToken t = (SmsCodeAuthenticationToken) auth;
        String phone = (String) t.getPrincipal();
        String input = (String) t.getCredentials();
        String stored = redis.opsForValue().get("sms:" + phone);
        if (stored == null || !stored.equals(input))
            throw new BadCredentialsException("验证码错误或过期");
        redis.delete("sms:" + phone);  // 一次性

        UserDO u = userMapper.findByPhone(phone);
        if (u == null) u = userMapper.createByPhone(phone);   // 自动注册
        return new SmsCodeAuthenticationToken(u.getId(),
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
    @Override public boolean supports(Class<?> cls) {
        return SmsCodeAuthenticationToken.class.isAssignableFrom(cls);
    }
}

// 3. Controller 入口 — 走 AuthenticationManager
@PostMapping("/auth/login/sms")
public Result<TokenPair> loginBySms(@RequestBody SmsLoginReq req) {
    Authentication auth = authManager.authenticate(
        new SmsCodeAuthenticationToken(req.phone(), req.code()));
    return Result.ok(jwtService.issue((Long) auth.getPrincipal(), List.of("USER")));
}
```

**关键设计点**:

1. **不要自己写 Filter**:复用 `AuthenticationManager` 体系,以后加邮箱 / 微信登录就再加 Provider
2. **验证码一次性**:用完立即删 Redis,防重放
3. **错误信息脱敏**:手机号不存在 / 验证码错都返回"验证码错误或过期"(防爆破探测手机号)
4. **同 IP / 手机号限流**:5 分钟内只能发 1 次

**🔥追问**:`@PreAuthorize` 和 `@PostAuthorize` 什么时候用?(答:Pre 在方法调用**前**判断,基于参数 / 当前用户;Post 在方法**返回后**判断,基于返回值,常用于"我只能看自己创建的文章" — `@PostAuthorize("returnObject.authorId == authentication.principal.id")`)

**关联**: interview-bank.md#401-vs-403, interview-bank.md#sms-login-spring-security

---

### Q5 [L3·场景设计·面试高频] 博客 API 同时接 Web SPA / 移动 App / 第三方机器,Security 怎么分流?

**考点**: 在博客项目里的落点, 多 SecurityFilterChain, securityMatcher, @Order
**参考答案**:

**需求拆解**:

| 调用方 | 推荐认证 | 原因 |
|---|---|---|
| Web SPA | JWT(Bearer) + CSRF 关 | 浏览器主流,前后端分离 |
| iOS / Android App | JWT(Bearer) | 无 Cookie 概念 |
| 第三方合作伙伴 | API Key + HMAC 签名 | 客户端凭证,防伪造 |

**核心方案 — 多 SecurityFilterChain + securityMatcher 分流**:

Spring Security 6.x 支持多个 `SecurityFilterChain` Bean,按 `securityMatcher` 分配请求,`@Order` 决定匹配顺序。

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // 第三方接口:API Key + HMAC
    @Bean @Order(1)
    public SecurityFilterChain partnerChain(HttpSecurity http,
            ApiKeyAuthFilter apiKeyFilter) throws Exception {
        return http
            .securityMatcher("/api/partner/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    // Web + App:JWT
    @Bean @Order(2)
    public SecurityFilterChain jwtChain(HttpSecurity http,
            JwtAuthFilter jwtFilter) throws Exception {
        return http
            .securityMatcher("/api/v1/**")
            .csrf(c -> c.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(GET, "/api/v1/posts/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonAuthEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler))
            .build();
    }

    // 内部 / 文档:放行
    @Bean @Order(3)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
            .csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .build();
    }
}
```

**API Key + HMAC 防伪造**:

```java
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws IOException, ServletException {
        String apiKey = req.getHeader("X-Api-Key");
        String sig = req.getHeader("X-Signature");
        String ts = req.getHeader("X-Timestamp");

        // 1. 防重放:时间戳与服务器相差 ≤ 5min
        if (Math.abs(System.currentTimeMillis() - Long.parseLong(ts)) > 300_000) {
            resp.sendError(401, "stale request"); return;
        }
        // 2. 查 secret
        Partner partner = partnerService.findByApiKey(apiKey);
        if (partner == null) { resp.sendError(401); return; }
        // 3. HMAC 重算,常量时间比较
        String expected = hmacSha256(partner.getSecret(),
            req.getMethod() + req.getRequestURI() + ts + bodyHash(req));
        if (!MessageDigest.isEqual(expected.getBytes(), sig.getBytes())) {
            resp.sendError(401); return;
        }
        // 4. 注入 SecurityContext
        var auth = new UsernamePasswordAuthenticationToken(partner.getId(), null,
            List.of(new SimpleGrantedAuthority("ROLE_PARTNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, resp);
    }
}
```

**3 类调用方的运营策略对比**:

| 维度 | Web | App | Partner |
|---|---|---|---|
| QPS 限流 | 同 IP 100/s | 同 token 50/s | 同 apiKey 200/s(合同) |
| 错误告警 | 401 突增 | 401 突增 | HMAC 失败立即告警(疑似伪造) |
| Token 撤销 | 版本号方案 | 版本号方案 | 后台立即禁用 apiKey |
| CORS | 严格(only `*.your-domain.com`) | 不开 | 不开 |

**单一 vs 多 Chain 取舍**:

| 维度 | 单一 Chain | 多 Chain 分流 |
|---|---|---|
| 配置复杂度 | 简单 | 中等 |
| 安全隔离 | 同一个 Filter | 不同 URL 不同 Filter,互不影响 |
| 性能 | 所有请求走完整链 | 只走匹配的链 |
| 扩展性 | 加新认证方式要大改 | 加新 `@Order(N)` Bean 即可 |

**🔥追问**:`@Order(1)` 链如果 `securityMatcher` 不匹配,会落到 `@Order(2)` 吗?

**参考答案**:**会**。Spring Security 按 `@Order` 升序逐个尝试 `securityMatcher`,第一个匹配的链接管请求。如果都不匹配,默认行为是**拒绝**(不是放行),所以记得给 `/actuator` `/swagger-ui` 配 `permitAll` 的 chain。

**面试 2 分钟讲法**:

> "多调用方场景下,不要把所有逻辑塞进一个 SecurityFilterChain。Spring Security 6.x 的 `securityMatcher + @Order` 是为这种场景设计的 — 每条链独立配 CSRF / Session / 过滤器 / 异常处理,互不干扰。第三方 API 用 API Key + HMAC 防伪造,带时间戳防重放、用 MessageDigest.isEqual 做常量时间比较;Web / App 走 JWT;内部 actuator 单独 permitAll。这就是生产级 API Gateway 的标准模式。"

**关联**: interview-bank.md#multi-chain-security-design

---

## 通过标准

- [ ] 能默写 6.x Filter Chain 关键 Filter 顺序 + 3 个核心角色(SecurityContextHolder / Authentication / GrantedAuthority)
- [ ] 能讲清 UserDetailsService / Provider / Manager 三者关系 + Spring Security vs Shiro 选型
- [ ] 能识别 5.x → 6.x 6 处典型问题,默写 6.x SecurityFilterChain Bean 写法
- [ ] 能默写 401 vs 403 + 写自定义 EntryPoint / DeniedHandler 输出 JSON
- [ ] 能设计多调用方分流:`securityMatcher + @Order` 多 Chain,讲清 HMAC 防伪造的 4 步
