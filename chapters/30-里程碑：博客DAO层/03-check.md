# Chapter 30 里程碑：博客 DAO 层 - 自测与验收

> 5 题（里程碑级跨章综合）：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：分层架构里 Controller / Service / DAO 各自的职责是什么？写在哪一层会成为「臭代码」？

**参考答案：**

| 层 | 职责 | 典型代码 |
|---|------|---------|
| Controller | HTTP 协议适配、DTO ↔ VO、参数校验、调 Service | `@PostMapping`、`@Valid`、`return Result.ok(vo)` |
| Service | 业务编排、事务边界、领域规则、跨多个 DAO 协调 | `@Transactional`、调多个 Mapper、抛业务异常 |
| DAO | 与数据库交互、单 SQL 原子操作 | Mapper 接口 + XML，**禁止业务判断** |

**典型坏味道**：

| 反例 | 应该在哪 |
|------|---------|
| Controller 里直接 `userMapper.insert(...)` | 搬到 Service |
| Service 里 `try { mapper.xxx() } catch (SQLException)` | 不要 catch，让事务回滚 |
| DAO 里 `if (status == 0) throw new BusinessException(...)` | 业务规则属于 Service |
| Mapper.xml 里 `WHERE user_id = ${userId}` | 改 `#{}` |
| Service 里返回 Entity 给 Controller | 转 VO（剔除敏感字段） |
| Controller 直接抛 SQLException 给前端 | `@RestControllerAdvice` 统一处理 |

**关键点**：

- **依赖方向只向下**，DAO 永远不调 Service。
- **每层只做自己的事**，跨层职责会让代码越来越乱。
- **业务异常在 Service 抛，HTTP 状态在 Controller / GlobalExceptionHandler 决定**。

---

## Q2（概念）：`@Transactional` 在哪些情况下会失效？请列 5 种并给修法。

**参考答案：**

| 失效场景 | 原因 | 修法 |
|---------|------|------|
| 1. 类内方法互调 | Spring AOP 走代理，self call 绕过代理 | 抽到另一个 Bean / 注入自己 `AopContext.currentProxy()` |
| 2. 方法非 public | Spring 代理只增强 public 方法 | 改 public |
| 3. 内部 try-catch 吞了异常 | 没异常抛出，AOP 检测不到回滚信号 | 不 catch，或 catch 后 `throw new RuntimeException(...)` |
| 4. 抛 checked exception 但没声明 rollbackFor | 默认只回滚 RuntimeException | `@Transactional(rollbackFor = Exception.class)` |
| 5. 方法所在类没被 Spring 管理 | 不是 Bean 就没代理 | 加 `@Component`/`@Service` |
| 6. 数据库引擎不支持事务 | MyISAM 没事务 | 改 InnoDB |
| 7. 多数据源没指定 transactionManager | 走了错的 TM | `@Transactional("orderTxManager")` |
| 8. 异步方法 `@Async` | 跨线程，事务上下文丢失 | 异步方法内自己开新事务 |

**示例 1：self call 失效**

```java
@Service
public class A {
    public void outer() { inner(); }            // ❌ 不走代理
    @Transactional
    public void inner() { ... throw ...; }      // 不会回滚
}
```

修法：

```java
@Service
public class A {
    @Resource A self;                            // 注入自身代理
    public void outer() { self.inner(); }        // ✅ 走代理
    @Transactional public void inner() {...}
}
```

**示例 2：checked 不回滚**

```java
@Transactional
public void doIt() throws IOException {
    mapper.insert(...);
    throw new IOException();  // checked，默认不回滚！
}
```

修法：`@Transactional(rollbackFor = Exception.class)`

**关键点**：`@Transactional` 是 Spring AOP 实现，所有"绕过 AOP 的写法"都会失效。理解原理才能少踩坑。

---

## Q3（实操）：以下 Service 代码有 6 处问题，请重写。

```java
@RestController
public class PostController {

    @Autowired PostMapper postMapper;
    @Autowired UserMapper userMapper;

    @PostMapping("/post")
    public Post create(@RequestBody Post p) {
        try {
            User u = userMapper.findById(p.getUserId());
            if (u == null) return null;
            p.setStatus(1);
            postMapper.insert(p);
            postMapper.updateUserPostCount(p.getUserId());
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
```

**参考答案（重写版）：**

```java
// 1) Controller 只做 HTTP 适配
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;          // ① 注入 Service，不直接调 Mapper
                                                    // ② @RequiredArgsConstructor 替代 @Autowired
    @PostMapping
    public Result<PostVO> create(@Valid @RequestBody PostCreateReq req,    // ③ 用 DTO + @Valid
                                 @AuthenticationPrincipal Long currentUserId) {
        Long id = postService.publish(currentUserId, req);                 // ④ userId 从认证拿，不让前端传
        return Result.ok(postService.getVO(id));                          // ⑤ 返回 VO 不返回 Entity
    }
}

// 2) Service 编排 + 事务
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)                          // ⑥ 事务在 Service
    public Long publish(Long userId, PostCreateReq req) {
        User u = userMapper.findById(userId);
        if (u == null) throw new BusinessException("USER_NOT_FOUND");      // ⑦ 抛业务异常，不返 null
        if (u.getStatus() != 1) throw new BusinessException("USER_BANNED");

        Post p = Post.builder()
            .userId(userId).userName(u.getNickname())
            .title(req.getTitle()).summary(req.getSummary()).content(req.getContent())
            .status(req.isPublish() ? 1 : 0)
            .build();
        postMapper.insert(p);
        postMapper.incrementUserPostCount(userId);
        return p.getId();
    }
}

// 3) DTO/VO 隔离
public record PostCreateReq(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 500) String summary,
    @NotBlank String content,
    boolean publish,
    List<Long> tagIds
) {}

public record PostVO(Long id, String title, String summary, String userName, LocalDateTime createdAt) {}

// 4) 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> biz(BusinessException e) {
        return ResponseEntity.badRequest().body(Result.error(e.getCode(), e.getMessage()));
    }
}
```

**6 处问题汇总**：

1. Controller 直接调 Mapper（应走 Service）。
2. 没用 DTO，把 Entity 当请求体（前端可任意改 status / userId）。
3. `userId` 让前端传 → 越权风险（应从 token 取）。
4. 业务异常返回 null → 调用方不知道是什么错。
5. `try-catch Exception` 吞掉所有异常 → 事务无法回滚 + 错误无声失败。
6. 返回 Entity 给前端 → 暴露 password / is_deleted 等。

**额外改进**：

- 路径规范 `/api/v1/posts`。
- `@Valid` 触发 Bean Validation。
- 全局异常处理器统一错误响应。

---

## Q4（实操）：写一个 Mapper 集成测试，验证「发布带标签的文章」会同时插入 post + post_tag 两张表，且事务在抛异常时回滚。

**参考答案：**

```java
@SpringBootTest
@Testcontainers
class PostServiceIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("blog");

    @DynamicPropertySource
    static void cfg(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired PostService postService;
    @Autowired UserMapper userMapper;
    @Autowired PostMapper postMapper;
    @Autowired PostTagMapper postTagMapper;
    @Autowired TagMapper tagMapper;

    @Test
    @Transactional  // 测试方法自动回滚，不污染数据
    void publish_inserts_post_and_tags() {
        // given
        Long uid = userMapper.insert(User.builder()
            .email("t@x.com").nickname("T").password("x").status(1).build());
        Long t1 = tagMapper.insert(Tag.builder().name("java").build());
        Long t2 = tagMapper.insert(Tag.builder().name("spring").build());

        // when
        Long pid = postService.publish(uid,
            new PostCreateReq("hi", "abc", "body", true, List.of(t1, t2)));

        // then
        assertThat(postMapper.findById(pid).getTitle()).isEqualTo("hi");
        assertThat(postTagMapper.findTagIdsByPost(pid))
            .containsExactlyInAnyOrder(t1, t2);
    }

    @Test
    @Transactional
    void publish_rolls_back_when_user_banned() {
        // given
        Long uid = userMapper.insert(User.builder()
            .email("b@x.com").nickname("B").password("x").status(0).build());  // banned
        long before = postMapper.countAll();

        // when / then
        assertThatThrownBy(() -> postService.publish(uid,
                new PostCreateReq("hi", "s", "c", true, List.of(1L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("BANNED");

        // 数据库无副作用
        assertThat(postMapper.countAll()).isEqualTo(before);
    }

    @Test
    @Transactional
    void publish_rolls_back_when_tag_insert_fails() {
        // given：用一个不存在的 tagId 触发外键错（或自定义异常）
        Long uid = userMapper.insert(User.builder()
            .email("c@x.com").nickname("C").password("x").status(1).build());
        long before = postMapper.countAll();

        // when
        assertThatThrownBy(() -> postService.publish(uid,
                new PostCreateReq("hi","s","c",true, List.of(99999999L))))
            .isInstanceOf(DataAccessException.class);

        // then：post 也应回滚
        assertThat(postMapper.countAll()).isEqualTo(before);
    }
}
```

**关键点**：

1. **Testcontainers MySQL** 而不是 H2，避免 SQL 方言差异掩盖 bug。
2. **测试方法 `@Transactional`** 自动回滚，多个用例间互不影响。
3. **三种用例**：成功、业务异常回滚、DB 异常回滚——确保事务两条边都验证。
4. **AAA**：given/when/then 清晰分段。
5. `assertThatThrownBy` 比 `try { fail() } catch { ... }` 可读性高 10 倍。

---

## Q5（综合）：里程碑场景题——上线 1 个月后，监控告警「发布文章接口 P99 = 4 秒」。请描述你的排查 + 优化思路（DAO 层视角）。

**参考答案：**

### 一、定位（先证据，再判断）

1. **看 traceId**：从告警里取 traceId，到 ELK 拉日志，看到底慢在哪一段：
   - Controller 入参解析？
   - Service 处理？
   - 哪条 SQL？

2. **看慢 SQL 日志 + MyBatis 慢 SQL 插件**（29 章写的）：
   ```
   SLOW_SQL traceId=xxx cost=3200ms sql=[INSERT ... post_tag ...]
   ```

3. **EXPLAIN 慢的 SQL**：发现 `INSERT ... SELECT` 或某个 SELECT 走了全表。

### 二、常见 4 类根因

| 现象 | 根因 | 修法 |
|------|------|------|
| 单 SQL 慢 | 索引失效 / 函数包裹列 / 索引选错 | 改 SQL、`ANALYZE TABLE`、`FORCE INDEX` |
| N+1 | `<association select=>` 嵌套查询 | 改嵌套 resultMap / 业务层 IN |
| 长事务 | 事务里调外部 API、循环单条 insert | 把 IO 提到事务外、改批量 |
| 锁等待 | UPDATE 没命中索引 / 高并发抢同一行 | 加索引 / 拆热点行 / 乐观锁 |

### 三、本场景假设：发布接口慢

抓到 SQL：

```sql
-- 慢：循环单条插 post_tag
INSERT INTO post_tag(post_id, tag_id) VALUES (?, ?);
-- 10 个 tag = 10 次往返
```

**改成批量**（29 章学的）：

```xml
<insert id="batchInsert">
    INSERT INTO post_tag(post_id, tag_id) VALUES
    <foreach collection="tagIds" item="t" separator=",">
        (#{postId}, #{t})
    </foreach>
</insert>
```

P99 从 4s 降到 300 ms。

### 四、再深一层：还能优化吗？

| 优化 | 收益 |
|------|------|
| 把"通知粉丝" 异步化（MQ）| 主路径再快 50 ms |
| 用 `useGeneratedKeys` 一次回填 id，去掉额外 SELECT | -10 ms |
| 发布频次低，可不在事务里更新 user_post_count，改异步 | 事务粒度更小 |
| 数据库连接池 maxPoolSize 调优 | 高并发不再排队 |

### 五、防回归

1. **DAO 单测加 SQL 数断言**：用 `DataSource` 代理统计 SQL 次数，断言 `publish()` 最多 4 条 SQL。
2. **CI 加 EXPLAIN gate**：所有新 SQL 自动 EXPLAIN，出现 `type=ALL` 红线。
3. **慢 SQL 告警**：插件持续运行，> 500 ms 告警到群。

### 六、汇报里程碑

```
[POST /posts] P99
- 修复前: 4200 ms
- 修复后: 280 ms
- 改动: post_tag 批量插入 + user_post_count 异步
- 防回归: SQL count 断言 + EXPLAIN CI gate
```

**关键点**：

1. **先看证据再下判断**：traceId + 慢 SQL 日志，不要凭直觉猜。
2. **优化 4 类根因**：单 SQL / N+1 / 长事务 / 锁——大部分性能问题逃不出这 4 类。
3. **优化完一定要加 gate**，否则下次别人写新代码又会回退。
4. 真实工程里，**优化的难点是定位，不是修法**——所以可观测性（log + trace + metric）永远是第一投资。
