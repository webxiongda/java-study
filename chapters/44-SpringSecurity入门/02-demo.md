# Chapter 44 Spring Security 入门 - 实操 Demo（6.x 写法）

## Demo 目标

用 Spring Security 6.x 推荐的 **`SecurityFilterChain` Bean + Lambda DSL** 写法，整合 42 章的 `JwtAuthFilter`，达到：
- 公开接口：`/api/auth/**` `/v3/api-docs/**` `/swagger-ui/**` 放行
- 其他接口：必须带合法 JWT
- 401 / 403 返回统一 ApiResponse

⚠️ **不要再用 `WebSecurityConfigurerAdapter`**——它在 Security 5.7 已 deprecated，6.x 删除。 见 [[glossary#SecurityFilterChain（Security 6.x）]]。

## 前置条件

- 基线 pom + 42 章已就绪（`JwtAuthFilter` / `JwtService` / `TokenStore` 已经写好）。

## 增量依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

## SecurityConfig（核心代码，背下来）

```java
package com.example.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity   // 启用 @PreAuthorize / @PostAuthorize（43 章会用）
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF：纯 JWT 关掉
            .csrf(csrf -> csrf.disable())

            // 2. CORS：用下面的 Bean
            .cors(Customizer.withDefaults())

            // 3. Session：无状态
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 4. 路由授权（Lambda DSL，6.x 写法）
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**",
                                 "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/articles/**").permitAll()  // 文章只读公开
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // 5. 注册 JWT 过滤器到用户名密码过滤器之前
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // 6. 统一 401 / 403 响应
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, resp, e) -> writeJson(resp, 401, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((req, resp, e)       -> writeJson(resp, 403, ErrorCode.FORBIDDEN))
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.your-domain.com"));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    private static void writeJson(HttpServletResponse resp, int status, ErrorCode err) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(
            new ObjectMapper().writeValueAsString(ApiResponse.fail(err.code, err.message)));
    }
}
```

## 与旧版（5.x）的关键差异

| 5.x（已 deprecated） | 6.x（推荐） |
|---|---|
| `extends WebSecurityConfigurerAdapter` 覆盖 `configure(HttpSecurity)` | `@Bean SecurityFilterChain` |
| `antMatchers("/x")` | `requestMatchers("/x")` |
| `.and()` 链式 | Lambda DSL（`http.csrf(c -> c.disable())`） |
| `authorizeRequests()` | `authorizeHttpRequests()` |
| `@EnableGlobalMethodSecurity(prePostEnabled = true)` | `@EnableMethodSecurity`（默认开启 prePost） |

## 运行与验证

| 场景 | 命令 | 期望 |
|---|---|---|
| 公开 GET | `curl :8080/api/articles/1` | 200 |
| 受保护接口无 token | `curl -X POST :8080/api/articles -d '{}' -H "Content-Type:application/json"` | 401，`code=20001` |
| 受保护接口带合法 token | 带 `Authorization: Bearer $ACCESS` | 200 |
| 普通用户访问 admin | 带 USER 角色 token 访问 `/api/admin/users` | 403，`code=30001` |
| CORS 预检 | `curl -X OPTIONS :8080/api/articles -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST"` | 200 + 正确 `Access-Control-Allow-*` 头 |

## 常见坑

- `requestMatchers` 是 6.x 的；用了 5.x 的 `antMatchers` 会 ClassNotFound。
- 没 `STATELESS` → Security 默认会创建 Session，JWT 模式无意义。
- `addFilterBefore` 位置错 → 用 `UsernamePasswordAuthenticationFilter.class` 之前，确保 SecurityContext 已注入再到 controller。
- `@EnableMethodSecurity` 必须显式开，否则 `@PreAuthorize` 不生效。

## 提交

```bash
git commit -m "chapter 44: spring security 6.x SecurityFilterChain + JWT integration"
```
