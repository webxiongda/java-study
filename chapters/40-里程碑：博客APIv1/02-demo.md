# Chapter 40 里程碑：博客APIv1 - 实操 Demo

## Demo 目标

把 Ch21-39 的所有技术栈整合到一个可运行、可演示的博客 API 项目里。完成一个完整的 CRUD 流程：发布文章 → 列表查询（分页）→ 详情 → 更新 → 删除。

## 前置条件

- JDK 21 + Maven 3.9+
- MySQL 8 已启动，有 `root` 可创建数据库
- 熟悉 `mvn spring-boot:run`

## 一、项目骨架（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>blog-api</artifactId>
    <version>1.0.0</version>
    <name>blog-api</name>
    <description>Blog API v1</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- OpenAPI / Swagger -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <version>3.0.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 二、建表 SQL

```sql
-- src/main/resources/sql/init.sql

CREATE DATABASE IF NOT EXISTS blog DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE blog;

-- 文章表
CREATE TABLE post (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title      VARCHAR(200)    NOT NULL COMMENT '标题',
    summary    VARCHAR(500)    DEFAULT '' COMMENT '摘要',
    content    LONGTEXT        NOT NULL COMMENT '正文(Markdown)',
    status     TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿 1-已发布 2-下架',
    author_id  BIGINT UNSIGNED NOT NULL COMMENT '作者ID',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3)     DEFAULT NULL COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_status_created (status, created_at DESC),
    KEY idx_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章';

-- 标签表
CREATE TABLE tag (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(50)     NOT NULL COMMENT '标签名',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';

-- 文章-标签关联表
CREATE TABLE post_tag (
    post_id    BIGINT UNSIGNED NOT NULL COMMENT '文章ID',
    tag_id     BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联';

-- 种子数据
INSERT INTO tag (name) VALUES ('Java'), ('Spring Boot'), ('MySQL'), ('MyBatis'), ('后端架构');
```

## 三、配置

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: blog-api
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    username: root
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  servlet:
    multipart:
      max-file-size: 5MB

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.blog
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

server:
  port: 8080
```

## 四、Java 代码

### 4.1 主类 & 统一响应

```java
// BlogApplication.java
@SpringBootApplication
@MapperScan("com.example.blog.**.mapper")
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

```java
// common/result/Result.java
@Data
@Accessors(chain = true)
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(200).setMessage("success").setData(data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<T>().setCode(code).setMessage(message);
    }
}
```

```java
// common/result/PageResult.java
@Data
public class PageResult<T> {
    private long total;
    private int pageNum;
    private int pageSize;
    private List<T> records;
}
```

### 4.2 错误码 & 全局异常处理

```java
// common/exception/ErrorCode.java
public enum ErrorCode {
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    BUSINESS_ERROR(422, "业务异常"),
    SYSTEM_ERROR(500, "系统错误");

    public final int code;
    public final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

```java
// common/exception/BusinessException.java
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message);
        this.code = errorCode.code;
    }
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.code;
    }
}
```

```java
// common/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("unexpected error", e);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
```

### 4.3 OpenAPI 配置

```java
// common/config/OpenApiConfig.java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI blogApi() {
        return new OpenAPI()
                .info(new Info().title("博客 API").version("1.0.0")
                        .description("博客后端接口 v1，支持文章 CRUD 和标签管理"))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

### 4.4 文章模块（Entity / Req / VO / Mapper / Service / Controller）

```java
// post/PostDO.java
@Data
public class PostDO {
    private Long id;
    private String title;
    private String summary;
    private String content;
    private Integer status;         // 0-草稿 1-已发布 2-下架
    private Long authorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
```

```java
// post/PostCreateReq.java
@Schema(description = "创建文章请求")
@Data
public class PostCreateReq {
    @Schema(description = "标题", example = "Spring Boot 入门教程")
    @NotBlank @Size(max = 200)
    private String title;

    @Schema(description = "摘要", example = "本文快速上手 Spring Boot 自动配置与 Starter")
    @Size(max = 500)
    private String summary;

    @Schema(description = "正文 Markdown", example = "# 第一章\n\nSpring Boot 的核心是自动配置...")
    @NotBlank
    private String content;

    @Schema(description = "是否直接发布", example = "true")
    private Boolean publish;

    @Schema(description = "标签 ID 列表", example = "[1, 2]")
    private List<Long> tagIds;
}
```

```java
// post/PostVO.java
@Schema(description = "文章响应")
@Data
public class PostVO {
    @Schema(description = "文章ID", example = "1")
    private Long id;
    @Schema(description = "标题", example = "Spring Boot 入门教程")
    private String title;
    @Schema(description = "摘要", example = "本文快速上手 Spring Boot 自动配置与 Starter")
    private String summary;
    @Schema(description = "状态", example = "1")
    private Integer status;
    @Schema(description = "作者ID", example = "1")
    private Long authorId;
    @Schema(description = "标签列表")
    private List<String> tags;
    @Schema(description = "创建时间", example = "2025-01-01 10:00:00")
    private LocalDateTime createdAt;
}
```

```java
// post/PostMapper.java
@Mapper
public interface PostMapper {
    @Insert("INSERT INTO post(title, summary, content, status, author_id) " +
            "VALUES(#{title}, #{summary}, #{content}, #{status}, #{authorId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PostDO post);

    PostDO selectById(@Param("id") Long id);

    List<PostDO> selectList(@Param("status") Integer status,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    long countByStatus(@Param("status") Integer status);

    int update(PostDO post);

    int softDelete(@Param("id") Long id);
}
```

```xml
<!-- src/main/resources/mapper/PostMapper.xml -->
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.blog.post.PostMapper">

    <resultMap id="postMap" type="com.example.blog.post.PostDO">
        <id column="id" property="id"/>
        <result column="title" property="title"/>
        <result column="summary" property="summary"/>
        <result column="content" property="content"/>
        <result column="status" property="status"/>
        <result column="author_id" property="authorId"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
        <result column="deleted_at" property="deletedAt"/>
    </resultMap>

    <select id="selectById" resultMap="postMap">
        SELECT * FROM post WHERE id = #{id} AND deleted_at IS NULL
    </select>

    <select id="selectList" resultMap="postMap">
        SELECT * FROM post
        WHERE deleted_at IS NULL
        <if test="status != null">AND status = #{status}</if>
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countByStatus" resultType="long">
        SELECT COUNT(*) FROM post
        WHERE deleted_at IS NULL
        <if test="status != null">AND status = #{status}</if>
    </select>

    <update id="update">
        UPDATE post
        SET title    = #{title},
            summary  = #{summary},
            content  = #{content},
            status   = #{status}
        WHERE id = #{id} AND deleted_at IS NULL
    </update>

    <update id="softDelete">
        UPDATE post SET deleted_at = NOW() WHERE id = #{id} AND deleted_at IS NULL
    </update>

</mapper>
```

```java
// tag/TagMapper.java
@Mapper
public interface TagMapper {
    @Select("SELECT * FROM tag")
    List<TagDO> selectAll();

    @Select("SELECT t.* FROM tag t JOIN post_tag pt ON t.id = pt.tag_id WHERE pt.post_id = #{postId}")
    List<TagDO> selectByPostId(@Param("postId") Long postId);

    @Select("SELECT * FROM tag WHERE id = #{id}")
    TagDO selectById(@Param("id") Long id);
}
```

```java
// tag/TagDO.java
@Data
public class TagDO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
```

```java
// post/PostService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {
    private final PostMapper postMapper;
    private final TagMapper tagMapper;

    @Transactional
    public PostVO publish(PostCreateReq req, Long authorId) {
        // 1. 验证标签存在
        if (req.getTagIds() != null) {
            for (Long tagId : req.getTagIds()) {
                if (tagMapper.selectById(tagId) == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在: " + tagId);
                }
            }
        }

        // 2. 创建文章
        PostDO post = new PostDO();
        post.setTitle(req.getTitle());
        post.setSummary(req.getSummary());
        post.setContent(req.getContent());
        post.setStatus(Boolean.TRUE.equals(req.getPublish()) ? 1 : 0);
        post.setAuthorId(authorId);
        postMapper.insert(post);

        // 3. 绑定标签
        if (req.getTagIds() != null) {
            for (Long tagId : req.getTagIds()) {
                postMapper.insertPostTag(post.getId(), tagId);
            }
        }

        log.info("article published: id={}, title={}, tags={}", post.getId(), post.getTitle(), req.getTagIds());
        return toVO(post);
    }

    public PageResult<PostVO> list(Integer status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<PostDO> list = postMapper.selectList(status, offset, pageSize);
        long total = postMapper.countByStatus(status);
        List<PostVO> vos = list.stream().map(this::toVO).toList();

        PageResult<PostVO> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setRecords(vos);
        return result;
    }

    public PostDetailVO detail(Long id) {
        PostDO post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        List<String> tags = tagMapper.selectByPostId(id).stream()
                .map(TagDO::getName).toList();

        PostDetailVO vo = new PostDetailVO();
        BeanUtils.copyProperties(post, vo);
        vo.setTags(tags);
        return vo;
    }

    @Transactional
    public PostVO update(Long id, PostCreateReq req) {
        PostDO post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        post.setTitle(req.getTitle());
        post.setSummary(req.getSummary());
        post.setContent(req.getContent());
        post.setStatus(Boolean.TRUE.equals(req.getPublish()) ? 1 : 0);
        postMapper.update(post);
        log.info("article updated: id={}", id);
        return toVO(post);
    }

    @Transactional
    public void delete(Long id) {
        PostDO post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        postMapper.softDelete(id);
        log.info("article deleted: id={}", id);
    }

    private PostVO toVO(PostDO post) {
        PostVO vo = new PostVO();
        BeanUtils.copyProperties(post, vo);
        return vo;
    }
}
```

```java
// post/PostDetailVO.java
@Schema(description = "文章详情")
@Data
public class PostDetailVO {
    private Long id;
    private String title;
    private String summary;
    private String content;
    private Integer status;
    private Long authorId;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
// post/PostController.java
@Tag(name = "文章管理", description = "发布、列表、详情、更新、删除")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @Operation(summary = "发布文章")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostVO> create(@RequestBody @Valid PostCreateReq req,
                                 @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        PostVO vo = postService.publish(req, userId != null ? userId : 1L);
        return Result.success(vo);
    }

    @Operation(summary = "文章列表（分页）")
    @GetMapping
    public Result<PageResult<PostVO>> list(
            @Parameter(description = "状态筛选") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(postService.list(status, pageNum, pageSize));
    }

    @Operation(summary = "文章详情")
    @ApiResponse(responseCode = "404", description = "文章不存在")
    @GetMapping("/{id}")
    public Result<PostDetailVO> detail(@Parameter(description = "文章ID", example = "1") @PathVariable Long id) {
        return Result.success(postService.detail(id));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/{id}")
    public Result<PostVO> update(@PathVariable Long id, @RequestBody @Valid PostCreateReq req) {
        return Result.success(postService.update(id, req));
    }

    @Operation(summary = "删除文章（软删除）")
    @ApiResponse(responseCode = "204", description = "删除成功")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        postService.delete(id);
    }
}
```

```java
// tag/TagController.java
@Tag(name = "标签管理")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagMapper tagMapper;

    @Operation(summary = "标签列表")
    @GetMapping
    public Result<List<TagDO>> list() {
        return Result.success(tagMapper.selectAll());
    }
}
```

## 五、运行与验证

```bash
# 1. 创建数据库和表
mysql -u root -p < src/main/resources/sql/init.sql

# 2. 启动应用
mvn spring-boot:run

# 3. 验证健康检查
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# 4. 查看 Swagger UI（浏览器打开）
open http://localhost:8080/swagger-ui.html

# 5. 发布文章
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring Boot 入门","summary":"快速上手","content":"# 正文","publish":true,"tagIds":[1,2]}' \
  | jq .
# → {"code":200,"message":"success","data":{"id":1,"title":"Spring Boot 入门",...}}

# 6. 文章列表
curl -s 'http://localhost:8080/api/v1/posts?pageNum=1&pageSize=10' | jq .
# → {"code":200,"data":{"total":1,"records":[...]}}

# 7. 文章详情
curl -s http://localhost:8080/api/v1/posts/1 | jq .
# → {"code":200,"data":{"id":1,"title":"Spring Boot 入门","tags":["Java","Spring Boot"],...}}

# 8. 更新文章
curl -s -X PUT http://localhost:8080/api/v1/posts/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring Boot 进阶","summary":"深入自动配置","content":"# 进阶正文","publish":true}' \
  | jq .
# → {"code":200,"data":{"id":1,"title":"Spring Boot 进阶",...}}

# 9. 删除文章
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/api/v1/posts/1
# → 204

# 10. 验证 404
curl -s http://localhost:8080/api/v1/posts/999 | jq .
# → {"code":404,"message":"文章不存在","data":null}

# 11. 验证参数校验
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"","content":""}' | jq .
# → {"code":400,"message":"title: 不能为空, content: 不能为空","data":null}
```

## 六、失败场景

### 场景 1：MySQL 没启动

```bash
mvn spring-boot:run
# → 控制台报错：
# Failed to configure DataSource: 'url' attribute is not specified
# 或
# Cannot create PoolableConnectionFactory: Communications link failure
```

**修法**：启动 MySQL 后再启动应用。

### 场景 2：事务不回滚

```java
// ❌ 错误：self-invocation，@Transactional 不生效
@Service
public class PostService {
    public void publishWithTags(PostCreateReq req) {
        this.publish(req, 1L);   // 直接调用本类方法，事务切面不生效
    }

    @Transactional
    public void publish(PostCreateReq req, Long authorId) {
        // publish 逻辑
    }
}
```

**修法**：注入 self 代理：

```java
@Service
public class PostService {
    @Autowired
    private PostService self;   // 注入代理对象

    public void publishWithTags(PostCreateReq req) {
        self.publish(req, 1L);  // 通过代理调用，事务生效
    }
}
```

### 场景 3：分页查不出数据

```java
// ❌ 错误：PageHelper 的 startPage() 后面必须紧跟 Mapper 查询
PageHelper.startPage(1, 10);
postService.someOtherLogic();   // 中间有别的操作，PageHelper 线程绑定到错误查询
List<PostDO> list = postMapper.selectList(...);
```

**修法**：`PageHelper.startPage()` 后立即跟第一条 SELECT。

## 七、测试

```java
// PostServiceTest.java
@SpringBootTest
class PostServiceTest {
    @Autowired
    private PostService postService;
    @Autowired
    private PostMapper postMapper;

    @Test
    @Transactional      // 自动回滚
    void publish_shouldCreatePost() {
        PostCreateReq req = new PostCreateReq();
        req.setTitle("测试文章");
        req.setContent("内容");
        req.setTagIds(List.of(1L));
        PostVO vo = postService.publish(req, 1L);
        assertThat(vo.getId()).isNotNull();
    }

    @Test
    void detail_shouldThrow_whenNotExist() {
        assertThatThrownBy(() -> postService.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文章不存在");
    }
}
```

## 提交建议

```bash
git add .
git commit -m "chapter 40: 博客API v1 里程碑 — 完整 CRUD + 文档 + 测试"
git tag v1.0.0
```
