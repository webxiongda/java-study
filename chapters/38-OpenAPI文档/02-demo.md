# Chapter 38 OpenAPI 文档 - 实操 Demo

## Demo 目标

接入 springdoc-openapi 2.x（Spring Boot 3.x 配套），自动生成 OpenAPI 3.0 文档 + Swagger UI + JWT 认证调试。

## 前置条件

- 基线 pom + 36 章 CRUD 接口。
- Spring Boot 3.3.x。

## 增量依赖

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

> ⚠️ 不要用旧的 `springfox-swagger2`，不兼容 Spring Boot 3.x。

## 1. `application.yml`

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs            # JSON 元数据
    enabled: true
  swagger-ui:
    path: /swagger-ui.html        # UI 入口
    tags-sorter: alpha
    operations-sorter: method
  packages-to-scan: com.example.blog.controller
  default-produces-media-type: application/json
```

生产建议关闭：

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## 2. 全局 OpenAPI 配置

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI api() {
        return new OpenAPI()
            .info(new Info()
                .title("Blog API")
                .version("v1.0")
                .description("个人博客后端 API 文档")
                .contact(new Contact().name("xiong").email("dev@example.com"))
                .license(new License().name("MIT")))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)
                    .name("Authorization")))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    // 分组：把不同业务拆开看
    @Bean
    public GroupedOpenApi articleGroup() {
        return GroupedOpenApi.builder()
            .group("article")
            .pathsToMatch("/api/articles/**")
            .build();
    }

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
            .group("auth")
            .pathsToMatch("/api/auth/**")
            .build();
    }
}
```

## 3. 注解控制器

```java
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Validated
@Tag(name = "文章", description = "文章 CRUD")
public class ArticleController {
    private final ArticleService service;

    @Operation(summary = "查询文章详情",
               description = "根据 id 返回文章，不存在则 code=20404")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(schema = @Schema(implementation = ArticleDTO.class))),
        @ApiResponse(responseCode = "404", description = "未找到", content = @Content)
    })
    @GetMapping("/{id}")
    public com.example.blog.dto.ApiResponse<ArticleDTO> detail(
        @Parameter(description = "文章 id", example = "1")
        @PathVariable @Min(1) Long id) {
        return com.example.blog.dto.ApiResponse.ok(service.detail(id));
    }

    @Operation(summary = "创建文章")
    @PostMapping
    public com.example.blog.dto.ApiResponse<Long> create(
        @Valid @RequestBody CreateArticleRequest req) {
        return com.example.blog.dto.ApiResponse.ok(service.create(req));
    }
}
```

## 4. DTO 加描述

```java
@Schema(description = "创建文章请求")
public record CreateArticleRequest(
    @Schema(description = "标题", example = "Hello World", maxLength = 200)
    @NotBlank @Size(max = 200) String title,

    @Schema(description = "正文 Markdown")
    String content,

    @Schema(description = "作者 id", example = "1")
    @NotNull Long authorId
) {}
```

## 5. 公开接口（绕开 JWT 安全）

```java
@Operation(summary = "登录", security = {})  // 空数组 = 此接口不需要 token
@PostMapping("/login")
public ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest req) { ... }
```

## 6. 与 Spring Security 配合放行

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
    // 其余规则……
);
```

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| UI 可访问 | 启动后浏览器 `http://localhost:8080/swagger-ui.html` |
| JSON 元数据 | `curl :8080/v3/api-docs` 输出 OpenAPI 3.0 JSON |
| 分组 | UI 右上角下拉看到 `article` / `auth` 两组 |
| Authorize 按钮 | 输入 access token → 后续请求自动带 `Authorization: Bearer xxx` |
| 参数校验 | UI 中标记 `* required`，DTO 示例值正确显示 |
| prod 关闭 | `SPRING_PROFILES_ACTIVE=prod` 启动，`/swagger-ui.html` 返回 404 |

## 常见坑

- 用了旧 `io.springfox` → 与 Spring Boot 3 不兼容，启动报错。 必须 `org.springdoc`。
- 写成 `springdoc-openapi-ui`（v1.x 名字）→ 找不到包。 2.x 是 `springdoc-openapi-starter-webmvc-ui`。
- Spring Security 没放行 swagger 路径 → `/swagger-ui.html` 跳到登录页。
- 接口上没标 `@Tag` → UI 默认按类名分组，混乱。
- 把生产 UI 公开 + 后端无鉴权 → 接口被遍历探测。 生产必须关或加鉴权。

## 提交

```bash
git commit -m "chapter 38: springdoc openapi 2.x + swagger ui + jwt scheme"
```
