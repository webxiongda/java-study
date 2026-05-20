# Chapter 36 Spring Boot + MyBatis 整合 - 实操 Demo

## Demo 目标

整合骨架：HikariCP 数据源 + 多环境 `application-{dev|prod}.yml` + Mapper 扫描 + 事务 + 一个完整 CRUD 接口。

## 前置条件

- 基线 pom + 28/29 章。
- 包结构：

```
com.example.blog
├── BlogApplication.java
├── config/        // 配置类
├── controller/    // REST 接口
├── service/       // 业务
├── mapper/        // MyBatis Mapper 接口
├── domain/        // 实体
└── dto/           // 请求/响应 DTO
```

## 增量依赖

无（21/28/29 章已含）。

## 1. 主启动类

```java
@SpringBootApplication
@MapperScan("com.example.blog.mapper")
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

> `@MapperScan` 在主类上集中声明，避免每个接口都标 `@Mapper`。

## 2. 多环境配置

`application.yml`（公共）：

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  application:
    name: blog-api
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

server:
  port: 8080
  servlet:
    context-path: /

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.blog.domain
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 10

pagehelper:
  helper-dialect: mysql
  reasonable: true
```

`application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
    username: root
    password: root
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000
      idle-timeout: 60000
      max-lifetime: 1800000

logging:
  level:
    com.example.blog.mapper: DEBUG
    org.springframework.jdbc: INFO
```

`application-prod.yml`：

```yaml
spring:
  datasource:
    url:  ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10

logging:
  level:
    root: INFO
    com.example.blog: INFO
```

> 占位符 `${DB_URL}` 在 prod 由环境变量注入，不要把密码写进文件。

## 3. 完整一刀 CRUD

### 3.1 DTO

```java
public record CreateArticleRequest(
    @NotBlank @Size(max = 200) String title,
    String content,
    @NotNull Long authorId
) {}

public record ArticleDTO(Long id, String title, String content,
                         Long authorId, ArticleStatus status,
                         LocalDateTime createdAt) {
    public static ArticleDTO from(Article a) {
        return new ArticleDTO(a.getId(), a.getTitle(), a.getContent(),
            a.getAuthorId(), a.getStatus(), a.getCreatedAt());
    }
}
```

### 3.2 Service

```java
@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateArticleRequest req) {
        Article a = new Article();
        a.setTitle(req.title());
        a.setContent(req.content());
        a.setAuthorId(req.authorId());
        a.setStatus(ArticleStatus.DRAFT);
        a.setViewCount(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        mapper.insert(a);
        return a.getId();
    }

    @Transactional(readOnly = true)
    public ArticleDTO detail(Long id) {
        Article a = mapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.NOT_FOUND, "article not found");
        return ArticleDTO.from(a);
    }

    @Transactional
    public void publish(Long id) {
        Article a = mapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.NOT_FOUND, "article not found");
        Article u = new Article();
        u.setId(id);
        u.setStatus(ArticleStatus.PUBLISHED);
        mapper.updateSelective(u);
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "article not found");
    }
}
```

### 3.3 Controller

```java
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Validated
public class ArticleController {
    private final ArticleService service;

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CreateArticleRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleDTO> detail(@PathVariable @Min(1) Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
```

## 4. 测试

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleControllerIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void create_thenDetail_ok() throws Exception {
        String body = json.writeValueAsString(
            new CreateArticleRequest("hello", "world", 1L));
        var res = mvc.perform(post("/api/articles")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
        Long id = json.readTree(res.getResponse().getContentAsString())
                      .get("data").asLong();
        mvc.perform(get("/api/articles/{id}", id))
           .andExpect(jsonPath("$.data.title").value("hello"));
    }
}
```

## 运行与验证

| 检查项 | 命令 / 期望 |
|---|---|
| 启动 | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` 看到 HikariCP 启动 + Mapper 加载日志 |
| 创建 | `curl -X POST -H 'Content-Type: application/json' -d '{"title":"a","authorId":1}' :8080/api/articles` → `{"code":0,"data":1}` |
| 查询 | `curl :8080/api/articles/1` → ArticleDTO |
| 校验失败 | title 空 → `code: 10001` |
| 不存在 | id=9999 → `code` 对应 NOT_FOUND |
| prod 启动 | `SPRING_PROFILES_ACTIVE=prod DB_URL=... mvn spring-boot:run` |

## 常见坑

- 主类放错包（`com.example` 而 Mapper 在 `com.other.mapper`） → 扫描不到。 主类必须在最外层包。
- Mapper XML 路径与 `mapper-locations` 不一致 → `Invalid bound statement`。
- HikariCP `maximum-pool-size` 设到 100+ → 反而吞吐下降，DB 连接是稀缺资源。
- 密码硬编码在 yml 提交 → 用环境变量 + `.env.example` 占位。
- 事务方法被同类内非事务方法调用 → 失效。 走代理或拆类。

## 提交

```bash
git commit -m "chapter 36: spring boot + mybatis full crud"
```
