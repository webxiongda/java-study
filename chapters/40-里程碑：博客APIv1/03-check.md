# Chapter 40 里程碑：博客APIv1 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：博客 API v1 的验收标准应该分成哪几层？为什么不能一次到位？

**参考答案：**

把"做完"拆成 5 层（L0–L4）。每层都有明确产出，不到位不进下一层：

| 层级 | 标准 | 验证命令 |
|------|------|----------|
| **L0 能启动** | `mvn spring-boot:run` 无异常；`/actuator/health` UP | `curl localhost:8080/actuator/health` |
| **L1 能演示** | 5 个 CRUD 接口全部跑通 | Swagger UI Try it out 各点一次 |
| **L2 错误体面** | 校验失败 400、找不到 404、未知 500 | `curl -d '{}'` 故意触发 |
| **L3 有文档** | Swagger 分组、DTO 含 example、接口含 summary | 打开 `/swagger-ui.html` |
| **L4 有测试** | Service ≥ 5 单测、Controller ≥ 2 集成测 | `mvn test` 全绿 |

**为什么不能一次到位**：

1. **反馈周期短**：L0 卡住时，写 L4 的测试代码毫无意义，先确保启动。
2. **避免完美主义陷阱**：v1 的目标是"能演示"，不是"代码最干净"。L3 之前不要做架构重构。
3. **每层都是一个 git commit**：分层让 commit 历史可读，回滚也容易。

**踩坑**：直接写 Controller + Service + Mapper 一把梭，跑起来发现连接不到 DB，排查 1 小时。应该先跑通 `/actuator/health`，再加业务。

---

## Q2（概念）：你的博客 API 项目用了哪些核心依赖？每个依赖解决什么问题？少哪个会出什么后果？

**参考答案：**

v1 最小依赖集（pom.xml）：

| 依赖 | 解决问题 | 缺它的后果 |
|------|----------|------------|
| `spring-boot-starter-web` | DispatcherServlet + Tomcat + Jackson | 没法处理 HTTP |
| `spring-boot-starter-validation` | `@Valid` + Hibernate Validator | `@NotBlank` 等注解不生效 |
| `mybatis-spring-boot-starter` | MyBatis 自动配置 + SqlSessionFactory | 写不了 Mapper |
| `mysql-connector-j` | JDBC 驱动 | `Failed to determine driver` |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI + OpenAPI 3 文档 | `/swagger-ui.html` 404 |
| `spring-boot-starter-actuator` | health / info / metrics 端点 | 没有运维探针 |
| `lombok`（optional） | `@Data` / `@Slf4j` 等 | 实体类要手写 getter/setter |
| `spring-boot-starter-test`（test） | JUnit5 + Mockito + MockMvc | 写不了测试 |

**面试常考**：

- "为什么没有显式声明版本？" → `spring-boot-starter-parent` 已经 BOM 管理。
- "MyBatis 和 Spring Data JPA 选哪个？" → v1 用 MyBatis（SQL 可控、学习路径里已经学了），JPA 不强制。
- "为什么 Lombok 要 optional？" → 编译期注解处理器，运行时不依赖；下游引用者不应被传递依赖。

---

## Q3（实操）：以下统一响应 + 全局异常处理代码有 4 处问题，找出并改正。

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<?> handleAll(Exception e) {
        return Result.fail(500, e.getMessage());        // ①
    }

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBiz(BusinessException e) {   // ②
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();   // ③
        return Result.fail(400, msg);
    }
}

@Data @AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;
    public static <T> Result<T> ok(T data) { return new Result<>(0, "ok", data); }
    public static Result<?> fail(int code, String msg) { return new Result<>(code, msg, null); }   // ④
}
```

**参考答案：**

**① 把原始异常信息返给客户端**

`e.getMessage()` 可能是 SQL 错误、栈信息，会泄露内部细节。应该：

```java
@ExceptionHandler(Exception.class)
public Result<?> handleAll(Exception e) {
    log.error("Unhandled exception", e);                    // 记日志带栈
    return Result.fail(ErrorCode.SYSTEM_ERROR, "系统异常");   // 返回脱敏信息
}
```

**② @ExceptionHandler 顺序问题**

`handleAll(Exception.class)` 在 `handleBiz(BusinessException.class)` 之前，看起来会被先匹配。但 Spring 的 `ExceptionHandlerMethodResolver` 按异常类层级精确匹配，**与声明顺序无关** —— 实际不会出问题。但读代码的人会困惑，规范是把最具体的写在最前面。

**③ getFieldError() 可能 NPE**

如果是 `@Valid` 校验类级别（如自定义 `@PasswordMatches`），fieldError 为 null。要：

```java
String msg = e.getBindingResult().getAllErrors().stream()
    .map(ObjectError::getDefaultMessage)
    .collect(Collectors.joining("; "));
```

同时还应处理 `ConstraintViolationException`（方法参数 @Validated 触发）和 `BindException`（表单绑定）。

**④ Result.fail 泛型丢失**

`Result.fail` 返回 `Result<?>`，调用方拿到 wildcard 没法用。应改成：

```java
public static <T> Result<T> fail(int code, String msg) {
    return new Result<>(code, msg, null);
}
```

---

## Q4（实操）：写一个 Service 单元测试 + Controller 集成测试，覆盖"发布文章"的成功路径和"标签不存在"的失败路径。

**参考答案：**

### Service 单元测试（Mockito）

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostMapper postMapper;
    @Mock TagMapper tagMapper;
    @Mock PostTagMapper postTagMapper;
    @InjectMocks PostServiceImpl postService;

    @Test
    void publish_success_returnsPostId() {
        // given
        PostCreateReq req = new PostCreateReq("Hello", "World", List.of(1L));
        when(tagMapper.selectByIds(List.of(1L))).thenReturn(List.of(new TagDO(1L, "Java")));
        when(postMapper.insert(any())).thenAnswer(inv -> {
            PostDO p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });

        // when
        Long id = postService.publish(req, 1L);

        // then
        assertThat(id).isEqualTo(100L);
        verify(postTagMapper).batchInsert(argThat(list -> list.size() == 1));
    }

    @Test
    void publish_tagNotExist_throwsBusinessException() {
        PostCreateReq req = new PostCreateReq("Hi", "Body", List.of(999L));
        when(tagMapper.selectByIds(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> postService.publish(req, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("标签不存在");

        verify(postMapper, never()).insert(any());
    }
}
```

### Controller 集成测试（MockMvc）

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // 测完自动回滚
class PostControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void publish_success_returns200AndId() throws Exception {
        var req = Map.of("title", "Hello", "content", "World", "tagIds", List.of(1L));

        mvc.perform(post("/api/v1/posts")
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void publish_emptyTitle_returns400() throws Exception {
        var req = Map.of("title", "", "content", "x", "tagIds", List.of(1L));

        mvc.perform(post("/api/v1/posts")
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
}
```

**关键点**：

1. **单测用 Mockito**：不启动 Spring 容器，毫秒级反馈。
2. **集成测用 `@SpringBootTest + MockMvc`**：真实走 DispatcherServlet 链路，验证全局异常处理、参数校验、序列化。
3. **`@Transactional` 自动回滚**：避免测试污染数据。如果用 H2 内存库就不需要。

---

## Q5（综合）：你的博客 API v1 跑通了，现在要做 v2（认证 + 缓存 + MQ）。请说明在不重写代码的前提下，v1 哪些地方需要留扩展点？为什么这样设计？

**参考答案：**

### 一、识别 v2 会动到的链路

```
v1 请求链路：Controller → Service → Mapper → DB
v2 新增：    Filter(JWT) → Controller → Service → Cache(Redis) → Mapper → DB
                                                  → MQ(异步通知)
```

### 二、v1 必须预留的 4 个扩展点

#### 1. 全局拦截位（给 JWT Filter 让路）

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // v1 暂时为空，v2 在这里加 JwtAuthInterceptor
    }
}
```

为什么：v2 加认证时不想动 Controller，所有校验集中在 Filter / Interceptor。

#### 2. 用户身份占位（给 @AuthenticationPrincipal 让路）

```java
// v1：先用固定 userId = 1
@PostMapping("/posts")
public Result<Long> create(@Valid @RequestBody PostCreateReq req) {
    Long userId = SecurityContext.currentUserId();   // v1 返回固定值
    return Result.ok(postService.publish(req, userId));
}
```

为什么：v2 把 `SecurityContext.currentUserId()` 改成读 JWT 即可，Controller 不动。

#### 3. Service 层不直连 Redis / MQ

```java
// v1：直接查 DB
public PostVO getById(Long id) {
    return postMapper.selectVOById(id);
}

// v2：在外层加 @Cacheable，原方法不动
@Cacheable(value="post", key="#id")
public PostVO getById(Long id) { ... }
```

为什么：缓存是横切关注点，靠注解 + AOP 增强，不污染业务代码。

#### 4. 事件发布占位（给 MQ 让路）

```java
@Service @RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final ApplicationEventPublisher publisher;

    @Transactional
    public Long publish(PostCreateReq req, Long userId) {
        // ... 写库
        publisher.publishEvent(new PostPublishedEvent(post.getId(), userId));
        return post.getId();
    }
}

// v1：监听器空实现或写日志
// v2：监听器里发 RabbitMQ / Kafka
@EventListener
public void onPostPublished(PostPublishedEvent e) { ... }
```

为什么：Spring Event 是同步的，但**接口形状和 MQ 一致**，v2 直接把监听器改成 `rabbitTemplate.send()`。

### 三、风险控制

| 风险 | v1 阶段动作 |
|------|-------------|
| v2 引入 Redis 后缓存击穿 | v1 预先把热点接口（getById）抽出独立方法，便于挂 `@Cacheable` |
| v2 引入 JWT 后跨域问题 | v1 加 `CorsFilter`，先把同源问题解掉 |
| v2 引入 MQ 后消息丢失 | v1 用 Spring Event 走通流程，v2 替换实现即可 |
| v2 上线后回滚困难 | v1 的 `application.yml` 用 profile：`auth: enabled: false` |

### 四、收益对比

| 维度 | 没留扩展点 | 留了扩展点 |
|------|------------|------------|
| v2 工作量 | 重写 Controller / Service | 改 4 个文件，加新代码 |
| 回归测试范围 | 全量 | 增量（认证 + 缓存模块） |
| 上线风险 | 大爆炸 | 增量发布 |
| 面试讲法 | "v1 推倒重写" | "我设计的时候已经考虑了认证/缓存的位置" |

**关键点**：里程碑不是终点，**是下一阶段的脚手架**。v1 写完就 tag `v1.0.0`，但代码中要有 v2 的影子（空接口、空配置、占位注解），这样 v2 才能增量推进。
