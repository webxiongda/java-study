# Chapter 38 OpenAPI 文档 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：OpenAPI 3 和 Swagger 2 是什么关系？SpringDoc 帮你做了什么？

**参考答案：**

**关系**：

- Swagger 2.x 是 OpenAPI 2.0 规范（也叫 OpenAPI Specification 2.0）。
- **OpenAPI 3.x** 是标准化后的新一代规范（Swagger 捐赠给了 Linux 基金会）。
- 两者不兼容，但 SpringDoc 同时在 jar 里支持两种格式输出（`/v3/api-docs` OAS3，`/v2/api-docs` 兼容 Swagger2）。

| 差异点 | Swagger 2 | OpenAPI 3 |
|--------|----------|-----------|
| 规范版本 | 2.x | 3.0.x / 3.1.x |
| 底层库 | SpringFox | Swagger Core 2.x |
| Spring Boot 3 兼容 | ❌ | ✅ |
| 维护状态 | 停滞 | 活跃 |

**SpringDoc 帮你做的**：

1. **启动扫描**：自动遍历所有 `@RestController` 方法签名，生成 OpenAPI JSON。
2. **注解翻译**：`@Operation` / `@Parameter` / `@ApiResponse` → OpenAPI schema。
3. **内嵌 UI**：`/swagger-ui/index.html` 提供可交互文档，不需要额外部署。
4. **请求验证**：可以在 UI 上直接发送真实请求，不用开 curl。

**关键点**：记住"SpringDoc → OpenAPI 3 → 启动自动扫描"这个链路，旧的 SpringFox 不要再碰。

---

## Q2（概念）：给 DTO 加 `@Schema(example = "...")` 和不加相比，对前端开发的体验差在哪？

**参考答案：**

**不加 `example`**：

```json
// Swagger UI 显示的 schema
{
  "title": "string",      // 看着像假数据
  "summary": "string",
  "content": "string",
  "tagIds": [0]
}

// "Try it out" 时自动填充：
{
  "title": "",             // 前端不知道填什么
  "summary": "",
  "content": "",
  "tagIds": []
}
```

**加了 `example`**：

```json
{
  "title": "Spring Boot 入门教程",  // 前端直接知道：哦，这是一个标题
  "summary": "本文快速上手 Spring Boot",
  "content": "# 正文内容...",
  "tagIds": [1, 2, 3]
}

// "Try it out" 直接有可用的示例值
```

**前端开发的"3 秒"差异**：

- 无 `example`：前端看完文档 → 打开电脑 → 去项目里找类似请求 → 确认字段含义 → 写代码。耗时：30 秒/字段。
- 有 `example`：前端看完文档 → 复制示例值 → 写代码。耗时：3 秒/字段。

**其他影响**：

- `@NotBlank` 只告诉"title 不能为空"，不告诉"什么样的值才是合法的"。`example="Spring入門"` 让前端看到真实格式。
- 枚举类型的字段无 example → 不知道该传"1"还是"active" → 打开 Swagger 文档找 → 还要翻到枚举定义。

**结论**：`@Schema(example = xxx)` 不是可选项，是接口文档的基本素养。

---

## Q3（实操）：以下 Controller 文档注解有 5 处缺失/错误，找出并补全。

```java
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostVO> create(@RequestBody PostCreateReq req,
                                 @AuthenticationPrincipal Long userId) {
        // ...
    }

    @GetMapping
    public Result<PageResult<PostVO>> list(
            @RequestParam Long lastId,
            @RequestParam int size) {
        // ...
    }

    @GetMapping("/{id}")
    public Result<PostDetailVO> get(@PathVariable Long id) {
        // ...
    }
}
```

**参考答案（补全后）：**

```java
@Tag(name = "文章管理", description = "发布、列表、详情、删除")
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    @Operation(summary = "发布文章", description = "发布文章并绑定标签")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostVO> create(
            @RequestBody @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "创建文章请求", required = true)
            PostCreateReq req,
            @Parameter(description = "当前用户ID", hidden = true)
            @AuthenticationPrincipal Long userId) {
        // ...
    }

    @Operation(summary = "文章列表", description = "keyset 分页查询已发布文章")
    @GetMapping
    public Result<PageResult<PostVO>> list(
            @Parameter(description = "上一页最后一条的 id，首页传 0")
            @RequestParam @Min(0) Long lastId,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam @Min(1) @Max(50) int size) {
        // ...
    }

    @Operation(summary = "文章详情", description = "含作者信息和标签列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "文章不存在")
    @GetMapping("/{id}")
    public Result<PostDetailVO> get(
            @Parameter(description = "文章 ID", example = "1")
            @PathVariable Long id) {
        // ...
    }
}
```

**5 处缺失**：

1. 类上缺 `@Tag(name = "文章管理")` → Swagger UI 不分组，所有接口混在一起。
2. 每个方法缺 `@Operation(summary = "...")` → Swagger UI 显示"posts-3"这种无意义名称。
3. `@PathVariable`、`@RequestParam` 缺 `@Parameter(description = "...")` → 前端不知道 lastId 的含义。
4. `@AuthenticationPrincipal` 没加 `hidden = true` → Swagger UI 显示一个正文参数让用户填。
5. 缺 `@ApiResponse(responseCode = "404")` → 前端看不到接口可能返回 404 的文档，不处理错误。

---

## Q4（实操）：在生产环境让 Swagger UI 不可访问，但保留 `/v3/api-docs` JSON 供 CI 做接口对比测试。

**参考答案：**

### 方案 1：Profile 控制（简单）

```yaml
# application-prod.yml
springdoc:
  swagger-ui:
    enabled: false     # UI 不可访问
  api-docs:
    enabled: true      # JSON 仍可访问
```

CI 用 prod profile 但通过内网端口访问 `/v3/api-docs`。

### 方案 2：Spring Security 控制（更细粒度）

```java
@Configuration
@Profile("prod")
public class SwaggerSecurityConfig {
    @Bean
    public SecurityFilterChain swaggerFilter(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/v3/api-docs/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v3/api-docs/**").hasIpAddress("192.168.0.0/16")  // 只有内网可看 JSON
                .requestMatchers("/swagger-ui/**").denyAll()                         // 所有人不可看 UI
            );
        return http.build();
    }
}
```

### 方案 3：条件注解（无 Profile 方案）

```java
@Configuration
@ConditionalOnExpression("${swagger.enabled:true}")
public class SwaggerConfig {}
```

`application-prod.yml` 里 `swagger.enabled: false` → 整个 SwaggerConfig 不加载 → 不扫描 → 不暴露。

### 验证

```bash
# 生产 profile
java -jar blog.jar --spring.profiles.active=prod

# UI → 404
curl http://localhost:8080/swagger-ui/index.html -w "%{http_code}"  # → 404

# JSON → 200（内网可访问）
curl http://localhost:8080/v3/api-docs | jq '.info.title'
# "Blog API"

# JSON → 403（外网）
curl -H "X-Forwarded-For: 1.2.3.4" http://localhost:8080/v3/api-docs -w "%{http_code}" # → 403
```

**关键点**：

1. `swagger-ui.enabled=false` 只关 UI，JSON 端点仍然在——这正好满足 CI 对比需求。
2. JSON 端点暴露了 Schema 信息（数据库字段名、枚举值），如果不希望外网访问，加 IP 白名单或 Security 拦截。
3. CI 中用 diff 工具对比两次 commit 的 `/v3/api-docs` 输出，可以发现"接口被改了但忘了更新前端"。

---

## Q5（综合）：你们公司前后端分离，前端同学说"我需要一份接口文档，要能直接在浏览器里点开用，要示例数据，要显示什么状态码"。请用 OpenAPI + SpringDoc 给出完整方案。

**参考答案：**

### 一、依赖

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

只有这一行依赖，不需要任何 Servlet 配置。

### 二、OpenAPI Bean（配 JWT + Info）

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI blogApi() {
        return new OpenAPI()
            .info(new Info().title("博客 API").version("1.0.0")
                .description("博客后端接口，支持 JWT 认证")
                .contact(new Contact().name("后端组").email("backend@x.com")))
            .addSecurityItem(new SecurityRequirement().addList("bearer"))
            .components(new Components().addSecuritySchemes("bearer",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .bearerFormat("JWT")
                    .description("登录后从 /auth/login 获取 Token，粘贴到这里")));
    }
}
```

### 三、Controller 注解规范

```java
@Tag(name = "文章模块", description = "文章的 CRUD 与搜索")
public class PostController {
    @Operation(summary = "发布文章", description = "发布文章并绑定标签")
    @ApiResponse(responseCode = "201", description = "创建成功，返回文章 VO")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "401", description = "未登录 / Token 过期")
    public Result<PostVO> create() { ... }
}
```

### 四、DTO `@Schema + example`——最影响体验的一步

```java
@Schema(description = "创建文章请求")
public record PostCreateReq(
    @Schema(description = "标题", example = "Spring Boot 入门教程",
            maxLength = 200, requiredMode = REQUIRED)
    @NotBlank @Size(max = 200) String title,
    @Schema(description = "摘要", example = "本文涵盖 Spring Boot 自动配置、Starter、Actuator")
    @Size(max = 500) String summary,
    @Schema(description = "正文 Markdown", example = "# 第一章")
    @NotBlank String content,
    @Schema(description = "是否直接发布", example = "true")
    boolean publish,
    @Schema(description = "标签 ID 列表", example = "[1, 2]")
    List<Long> tagIds
) {}
```

### 五、分组

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  group-configs:
    - group: client
      display-name: "客户端接口"
      paths-to-match: /api/v1/**
      paths-to-exclude: /api/v1/admin/**
    - group: admin
      display-name: "管理后台接口"
      paths-to-match: /api/v1/admin/**
```

### 六、生产关闭 UI

```yaml
# application-prod.yml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

### 七、向前端交付

```bash
# 启动 dev 环境
mvn spring-boot:run -Dspring.profiles.active=dev

# 告诉前端 3 个地址：
# 1. Swagger UI → http://dev.blog.com/swagger-ui.html
#    → 选 Try it out → 粘贴 Token → 直接调接口
# 2. OpenAPI JSON → http://dev.blog.com/v3/api-docs
#    → 导入 Apifox / Postman 自动生成 SDK
# 3. 分组 → http://dev.blog.com/swagger-ui.html?group=client
#    → 只看客户端关心的接口
```

前端打开 Swagger UI，点 Try it out → 粘贴 JWT → 发送请求，**无需**：
- 配置 Postman
- 问后端"这个参数传什么"
- 问后端"404 会返回什么格式"

**关键点**：

1. 最影响前端体验的是**example**，不是 schema 类型定义。
2. **JWT Authorize** 按钮必须在 UI 上可见并可用，否则前端无法测试带 Token 接口。
3. **分组**让前端只看到自己关心的接口，不会被 admin 接口淹没。
4. 生产环境关闭文档后，需要为前端提供 dev / staging 环境的文档地址。
