# Chapter 38 OpenAPI 文档 - 项目任务

## 任务概述

给博客所有 API 加上 OpenAPI 3 文档：所有 Controller 标注 `@Operation`、所有 DTO 配 `@Schema`、Swagger UI 可在线测试、生产环境关闭文档。

## 业务背景

后端写"不文档化"的 API 只配自己用——没有文档就没有前端对接、没有测试、没有市场。这一章把博客全套 API 文档化。

## 任务拆解

### Step 1：引入 SpringDoc

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

### Step 2：写 OpenAPI Bean

```java
@Bean
public OpenAPI blogOpenAPI() {
    return new OpenAPI()
        .info(new Info().title("Blog API").version("1.0.0")
            .description("博客 v1 后端接口"))
        .addSecurityItem(new SecurityRequirement().addList("bearer"))
        .components(new Components().addSecuritySchemes("bearer",
            new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("bearer").bearerFormat("JWT")));
}
```

### Step 3：给所有 Controller 加 @Operation

遍历 `controller/` 下所有类，每个方法加 `@Operation(summary = "...", description = "...")`。

### Step 4：给所有 DTO 加 @Schema

所有 Request / Response DTO：

- `@Schema(description = "...")` 在类
- `@Schema(description = "...", example = "...")` 在字段

example 必须填真实的示例值（如 email: `"user@example.com"`，id: `1`），不能空着。

### Step 5：Swagger 分组

client / admin 分组：

```yaml
springdoc:
  group-configs:
    - group: client
      paths-to-match: /api/v1/**
      paths-to-exclude: /api/v1/admin/**
    - group: admin
      paths-to-match: /api/v1/admin/**
```

验证：`/swagger-ui/index.html?group=client` 能选分组。

### Step 6：生产关闭 Swagger

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

验证：`spring.profiles.active=prod` 启动后 `/swagger-ui/index.html` 返回 404。

### Step 7：验证可以 curl

打开 Swagger UI → 点开一个接口 → "Try it out" → 发送 → 确认返回正确的 JSON。

## 交付物

- [ ] `OpenAPIConfig.java`（`@Configuration` + `OpenAPI` Bean）
- [ ] 所有 Controller 方法加 `@Operation`
- [ ] 所有 DTO 加 `@Schema(description + example)`
- [ ] `application.yml` 加 `springdoc` 分组配置
- [ ] `application-prod.yml` 关闭文档
- [ ] `docs/api-doc-screenshot.png`：Swagger UI 页面截图

## 验收清单

| 项 | 标准 |
|----|------|
| Swagger UI 可访问 | `/swagger-ui/index.html` 显示完整 API 列表 |
| 每个接口有 description | 方法上 `@Operation(summary=xxx)` |
| 每个字段有 example | DTO 里每个字段 `@Schema(example=xxx)` |
| JWT Bearer 可配 | Swagger UI "Authorize" 按钮存在，粘 Token 后可调接口 |
| 分组有效 | client 分组不显示 admin 接口 |
| 生产 404 | prod profile 下 Swagger 不可访问 |

## 扩展挑战

1. **导出 OpenAPI JSON**：启动后 curl `/v3/api-docs` → 保存为 `docs/openapi.json`，用 Swagger Editor 在线编辑。
2. **Codegen 生成前端 SDK**：`npx @openapitools/openapi-generator-cli generate -i docs/openapi.json -g typescript-axios -o ../frontend/src/api`，验证前端可调用。
3. **自定主题**：Swagger UI 换成 Scalar UI `/swagger-ui` → 加 `springdoc.swagger-ui.path: /swagger-ui` 保留兼容。
4. **接口变更对比**：Git CI 对比两次 commit 的 `openapi.json` diff，接口变更自动通知到钉钉群。
