# Chapter 38 OpenAPI 文档 - 理论篇

## 一、学习定位

写 API 不给文档 = 写代码不用注释。OpenAPI（原 Swagger）是 REST API 文档的事实标准：**注解标注 → 自动生成文档 → 前端可在线调试**。

- 优先级：L1
- 预计投入：3 小时
- 阶段产出：博客 API 全部接口都有 OpenAPI 文档，可在线 curl 调试

## 二、核心概念

### 1. OpenAPI 3 规范

```
openapi: 3.0.3
info:
  title: Blog API
  version: 1.0.0
paths:
  /api/v1/posts:
    get:
      summary: 文章列表
      parameters:
        - name: lastId
          in: query
          schema: { type: integer }
```

**YAML → 代码**：SpringDoc（`springdoc-openapi-starter-webmvc-ui`）自动从 Controller 注解和 `@Schema` 生成这个 YAML，并内嵌 Swagger UI。

### 2. 与旧版（SpringFox）的区别

| 维度 | SpringFox（3.x） | SpringDoc（2.x，推荐） |
|------|-----------------|---------------------|
| 维护 | 半放弃状态 | 活跃 |
| Spring Boot 3 支持 | ❌ | ✅ |
| OpenAPI 版本 | 2.x（Swagger） | 3.x（OpenAPI） |
| 集成 | 要手配多个 Bean | `springdoc-openapi-starter-webmvc-ui` 自动 |
| 分组 | `@GroupedOpenApi` | 配置文件 `groups` |

> **结论**：新项目直接用 SpringDoc，不要碰 SpringFox。

### 3. 关键注解

```java
@Operation(summary = "发布文章", description = "发布文章并绑定标签")
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Result<PostVO> create(
    @RequestBody @Valid
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "创建文章请求体", required = true)
    PostCreateReq req,

    @Parameter(description = "当前用户ID")
    @AuthenticationPrincipal Long userId) { ... }
```

| 注解 | 位置 | 作用 |
|------|------|------|
| `@Operation` | 方法 | 接口描述 |
| `@Parameter` | 参数 | 单个参数说明 |
| `@Schema` | DTO 字段 | 字段描述 / 示例 / 可选 |
| `@ApiResponse` | 方法 | 响应描述 |
| `@Tag` | 类 | Controller 分类 |

### 4. DTO 加 Schema 描述

```java
@Schema(description = "创建文章请求")
public record PostCreateReq(
    @NotBlank
    @Schema(description = "文章标题", example = "Spring Boot 入门", maxLength = 200)
    String title,

    @Size(max = 500)
    @Schema(description = "摘要", example = "快速上手 Spring Boot")
    String summary,

    @NotBlank
    @Schema(description = "正文", example = "# 正文内容...")
    String content,

    @Schema(description = "是否直接发布", example = "true")
    boolean publish,

    @Schema(description = "标签 ID 列表", example = "[1, 2, 3]")
    List<Long> tagIds
) {}

@Schema(description = "文章 VO")
public record PostVO(
    @Schema(description = "文章 ID", example = "1")
    Long id,

    @Schema(description = "标题", example = "Spring Boot 入门")
    String title,

    @Schema(description = "作者昵称", example = "xiong")
    String userName,

    @Schema(description = "创建时间", example = "2025-01-01T12:00:00")
    LocalDateTime createdAt
) {}
```

### 5. 分组文档（前后端分离）

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    groups-order: DESC
  group-configs:
    - group: admin
      paths-to-match: /api/v1/admin/**
    - group: client
      paths-to-match: /api/v1/**
      paths-to-exclude: /api/v1/admin/**
```

访问：

- `/swagger-ui/index.html` → 混合
- `/swagger-ui/index.html?group=client` → 只客户端接口
- `/v3/api-docs` → JSON 原始

### 6. 安全配置

JWT 鉴权时 Swagger 需要知道 Authorization header：

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info().title("Blog API").version("1.0.0")
            .description("博客后端 API 接口文档"))
        .addSecurityItem(new SecurityRequirement().addList("bearer"))
        .components(new Components().addSecuritySchemes("bearer",
            new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("bearer").bearerFormat("JWT")));
}
```

Swagger UI 上会多出一个"Authorize"按钮，点它粘 JWT Token，所有请求自动带 `Authorization: Bearer xxx`。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `springdoc-openapi-starter-webmvc-ui` 自动 `@Bean OpenAPIResource` |
| 配置 | `springdoc.*` 控分组 / 路径 / 缓存 |
| 执行 | 启动时扫描所有 `@RestController` 方法 + `@Schema` 注解 → 生成 OpenAPI JSON |
| 边界 | `@Schema` 缺了默认从 Java 类型推断；`example` 需要人工配 |
| 验证 | 打开 `/swagger-ui/index.html` 逐个接口试 curl |

## 四、在博客项目里的落点

- 所有 Controller 方法加 `@Operation`。
- 所有 Request/Response DTO 加 `@Schema description + example`。
- JWT SecurityScheme 配好，Swagger UI 上能测试带 Token 的接口。
- 分组：client / admin 分组（前后端只看到自己关心的）。

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| Spring Boot 3 启动报 `NoClassDefFoundError` | 用了 SpringFox（不兼容） | 换 SpringDoc |
| 枚举显示为"string" | 没配 `@Schema(implementation = String.class)` | 显式标类型 |
| 404 找不到 swagger-ui | 路径有权限拦截（Spring Security）| `.antMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()` |
| JsonIgnore 字段仍显示 | SpringDoc 尊重 Jackson 注解 | 确认 Jackson 注解正确 |
| 请求体示例全是空对象 | `@Schema` 缺 `example` | 补 `example` |

## 六、面试高频问题

1. OpenAPI 3 和 Swagger 2 的关系？
2. SpringDoc 和 SpringFox 的区别？
3. 怎么给 API 分组文档？
4. OpenAPI 怎么配置 JWT Auth？
5. 怎么让 Swagger UI 在生产环境不可用？
6. DTO 的 `@Schema` 缺了 `example` 会怎样？
