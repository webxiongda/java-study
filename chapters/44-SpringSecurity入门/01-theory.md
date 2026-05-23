# Chapter 44 Spring Security 入门 - 理论

> 前置：[[41-认证基础]] [[42-JWT实战]] [[43-权限控制]]
> 后续：[[45-Web安全防护]]
> 优先级：L1 必须掌握  预计：4 小时

## 1. 为什么需要这一章

41-43 章你已经会**手写**认证 + 权限了。但生产项目几乎都用 Spring Security，因为：

- **15+ 个 Filter** 已经写好（CSRF、CORS、Session、Auth、LogoutFilter…）
- 统一的扩展点（`AuthenticationProvider`、`UserDetailsService`、`AccessDecisionManager`）
- OAuth2 / OIDC / SAML 等协议开箱即用
- 安全漏洞官方修复（你手写的拦截器需要自己跟 CVE）

**学习路径**：先理解 Filter Chain 整体，再看几个核心扩展点，最后把 42/43 章的代码"接入"Spring Security。

## 2. Spring Security 6.x 整体架构

```
HTTP Request
   ↓
[DelegatingFilterProxy] ← Servlet 容器入口
   ↓
[FilterChainProxy]      ← Spring 管理的 Filter 调度器
   ↓
SecurityFilterChain (你自己的 @Bean，可有多个)
   ├─ DisableEncodeUrlFilter
   ├─ WebAsyncManagerIntegrationFilter
   ├─ SecurityContextHolderFilter ← 从存储读 Authentication
   ├─ HeaderWriterFilter          ← 加 X-Frame-Options 等安全 header
   ├─ CorsFilter                  ← 跨域
   ├─ CsrfFilter                  ← CSRF
   ├─ LogoutFilter
   ├─ JwtAuthFilter (你加的)      ← 解 token → SecurityContext
   ├─ UsernamePasswordAuthenticationFilter ← 表单登录用
   ├─ RequestCacheAwareFilter
   ├─ SecurityContextHolderAwareRequestFilter
   ├─ AnonymousAuthenticationFilter ← 未认证用户给个匿名身份
   ├─ ExceptionTranslationFilter  ← 把 AccessDeniedException 转成 401/403
   └─ AuthorizationFilter         ← 路由级授权
   ↓
DispatcherServlet → @Controller
```

**关键 3 个角色**：

| 角色 | 职责 |
|------|------|
| `SecurityContextHolder` | ThreadLocal，存当前请求的 `Authentication` |
| `Authentication` | 身份（Principal）+ 凭证（Credentials）+ 权限（Authorities）+ 是否已认证 |
| `GrantedAuthority` | 权限码，`hasRole/hasAuthority` 看的就是这个 |

## 3. Spring Security 6.x 新写法（vs 5.x）

| 维度 | 5.x（已 deprecated） | 6.x（推荐） |
|------|----------------------|-------------|
| 配置类 | `extends WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain` |
| 路径匹配 | `antMatchers("/x")` | `requestMatchers("/x")` |
| DSL | `.and()` 链式 | Lambda DSL `http.csrf(c -> c.disable())` |
| 授权 | `authorizeRequests()` | `authorizeHttpRequests()` |
| 方法注解 | `@EnableGlobalMethodSecurity(prePostEnabled=true)` | `@EnableMethodSecurity` |

**最小可运行 SecurityFilterChain**：

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .build();
    }
    @Bean public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

## 4. 认证（Authentication）核心扩展点

```
Login Request
   ↓
AuthenticationManager.authenticate(token)
   ↓
DaoAuthenticationProvider (默认实现之一)
   ├─ UserDetailsService.loadUserByUsername()  ← 你实现，查 DB
   └─ PasswordEncoder.matches()                ← 你配 BCrypt
   ↓
Authentication (已认证) → SecurityContextHolder
```

**自定义 UserDetailsService**：

```java
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserWithAuthsDO u = userMapper.findAuthsByUsername(username);
        if (u == null) throw new UsernameNotFoundException(username);

        List<GrantedAuthority> auths = new ArrayList<>();
        u.getRoles().forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
        u.getPermissions().forEach(p -> auths.add(new SimpleGrantedAuthority(p)));

        return User.builder()
            .username(u.getUsername())
            .password(u.getPasswordHash())   // 已是 BCrypt
            .authorities(auths)
            .disabled(u.getStatus() == 0)
            .build();
    }
}
```

**自定义 AuthenticationProvider**（更灵活，可以做手机号 / 邮箱 / OAuth2 多端登录）：

```java
@Component
public class SmsAuthenticationProvider implements AuthenticationProvider {
    @Override
    public Authentication authenticate(Authentication auth) {
        SmsAuthToken token = (SmsAuthToken) auth;
        // 校验短信码 → 加载用户
        return new SmsAuthToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> cls) { return SmsAuthToken.class.isAssignableFrom(cls); }
}
```

## 5. 授权（Authorization）三个层级

| 层级 | 配置位置 | 适合 |
|------|---------|------|
| **URL 级** | `SecurityFilterChain.authorizeHttpRequests` | 粗粒度（公开 / 内部 / 后台） |
| **方法级** | `@PreAuthorize` / `@PostAuthorize` | 细粒度（按角色 / 权限） |
| **数据级** | SpEL 表达式访问 Bean / 参数 | 行级权限（owner、租户） |

```java
// URL 级
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers(POST, "/api/posts").hasAuthority("post:write")

// 方法级
@PreAuthorize("hasRole('ADMIN')")

// 数据级（调 Bean）
@PreAuthorize("hasRole('ADMIN') or @postSec.isOwner(#id, authentication)")
```

## 6. 密码哈希

Spring Security 强制密码用 `PasswordEncoder`。

| 实现 | 用途 |
|------|------|
| `BCryptPasswordEncoder` | 默认推荐，cost ≥ 12 |
| `Argon2PasswordEncoder` | 现代化（OWASP 2026 首选） |
| `Pbkdf2PasswordEncoder` | FIPS 合规 |
| `NoOpPasswordEncoder` | **仅测试**，明文比对 |
| `DelegatingPasswordEncoder` | 多算法共存（迁移场景） |

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // 默认 DelegatingPasswordEncoder，支持 {bcrypt} {argon2} {noop} 前缀
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

## 7. 异常处理：401 vs 403

Spring Security 把所有安全异常归到两类：

| 异常类型 | 含义 | HTTP | 处理器 |
|----------|------|------|--------|
| `AuthenticationException` | 没认证 / 凭证不对 | 401 | `AuthenticationEntryPoint` |
| `AccessDeniedException` | 已认证但权限不够 | 403 | `AccessDeniedHandler` |

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, resp, e) ->
        writeJson(resp, 401, ErrorCode.UNAUTHORIZED))
    .accessDeniedHandler((req, resp, e) ->
        writeJson(resp, 403, ErrorCode.FORBIDDEN))
)
```

**常见错误**：自定义异常处理器但忘了在 SecurityConfig 里注册 → Spring 默认行为接管，返回 HTML 登录页。

## 8. CSRF 在 Token 模式下的取舍

| 场景 | CSRF |
|------|------|
| 浏览器 + Cookie（Session 登录） | **必须开**，否则被 CSRF |
| 浏览器 + Authorization Header（JWT） | 可关（Header 不被自动带） |
| 移动端 / 服务端调用 | 可关 |

```java
.csrf(csrf -> csrf.disable())   // JWT 模式
```

> 详见 45 章。

## 9. CORS 与 Security 的交互顺序

```
浏览器 OPTIONS preflight 不带 Authorization
   ↓
Spring Security 收到 → 默认拒绝 401
   ↓
解法：CorsFilter 必须在 Security FilterChain 之前 / 同步配 cors()
```

正确写法：

```java
http.cors(Customizer.withDefaults())    // 启用 CORS
    ...

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOriginPatterns(List.of("http://localhost:*"));
    c.setAllowedMethods(List.of("*"));
    c.setAllowedHeaders(List.of("*"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
}
```

## 10. 与 JWT / RBAC 的衔接（42-43 章接入点）

| 接入点 | 改动 |
|--------|------|
| `JwtAuthFilter` | `addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)` |
| 路由公开 / 鉴权 | 改写 41 章的 `permitAll` / `authenticated` 为 6.x DSL |
| 方法级 | `@EnableMethodSecurity` + 43 章的 `@PreAuthorize` 注解 |
| 401/403 输出 | `ExceptionHandling` 注册 entry point + denied handler |
| 密码 | 41 章手写的 `BCryptPasswordEncoder` 改成 Spring Security 提供的 |

接入完成后，**业务代码不变**，只是 Filter 链由框架管理。

## 11. 在博客项目里的落点

```
com.example.blog.security/
├── SecurityConfig.java            # @Configuration 主入口
├── JwtAuthFilter.java             # 复用 42 章
├── UserDetailsServiceImpl.java    # 适配 RBAC 5 表
├── PostSecurityChecker.java       # @PreAuthorize 用的 Bean
├── handler/
│   ├── JsonAuthEntryPoint.java    # 401 输出 JSON
│   └── JsonAccessDeniedHandler.java
└── config/
    └── CorsConfig.java
```

## 12. 常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| `requestMatchers` 找不到 | 用了 5.x 的 `antMatchers` | 改 6.x API |
| `hasRole("ADMIN")` 永远 false | 权限里没加 `ROLE_` 前缀 | 加前缀，或改 `hasAuthority("ADMIN")` |
| `@PreAuthorize` 不生效 | 没 `@EnableMethodSecurity` | 加注解 |
| CORS preflight 401 | 没 `http.cors()` 或 CorsFilter 不在 Security 之前 | 配 CORS Bean + `.cors()` |
| 浏览器登录后跳 HTML 登录页 | 没配 `AuthenticationEntryPoint` | 注册 JSON 401 输出 |
| Session 还在创建 | 没 `STATELESS` | `sessionCreationPolicy(STATELESS)` |
| `formLogin()` 错误启用 | Boot 自动配置 | 显式关：`formLogin(f -> f.disable())` |

## 13. 面试高频

1. Spring Security 的 Filter Chain 是怎么工作的？
2. `Authentication` / `AuthenticationManager` / `AuthenticationProvider` / `UserDetailsService` 各自什么作用？
3. `hasRole` 和 `hasAuthority` 区别？
4. 6.x 比 5.x 主要变化？
5. JWT 模式下 Security 还有什么必须配的？
6. `@PreAuthorize` 和 `@PostAuthorize` 区别？什么时候用哪个？
7. CSRF 在前后端分离 + JWT 项目里要不要开？
8. 怎么处理 401 / 403 返回统一 JSON？

更多 → [[interview-bank|面试题库]] `spring-security` 区。
