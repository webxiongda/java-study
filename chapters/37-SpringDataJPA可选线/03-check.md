# Chapter 37 Spring Data JPA 可选线 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：JPA 方法名解析的规则是什么？`findByUserEmailAndStatusOrderByCreatedAtDesc(Long userId, String email, int status)` 生成的 SQL 是什么？

**参考答案：**

**解析规则**：按关键词从左到右切分，每段对应一组条件。

| 关键词 | 效果 |
|--------|------|
| `findBy` / `queryBy` / `getBy` | 查询入口 |
| `And` / `Or` | 连接条件 |
| `Between` / `LessThan` / `GreaterThan` | 比较操作 |
| `Containing` / `Like` | 模糊 |
| `OrderBy` | 排序 |
| `Desc` / `Asc` | 排序方向 |
| `IgnoreCase` | 忽略大小写 |

生成的 SQL（Hibernate 生成，近似）：

```sql
SELECT u FROM User u
WHERE u.email = ?1 AND u.status = ?2
ORDER BY u.createdAt DESC
```

**切分过程**：

```
findBy → Email → And → Status → OrderBy → CreatedAt → Desc
        ↓                 ↓
     u.email=?        u.status=?
```

**限制**：

- 超过 4 个条件，方法名太长（如 `findByStatusAndTypeAndCategoryAndCreatedAtBetweenAndDeleted`）。
- 条件越多，解析错误越难排查（Hibernate 不会提前报错，只在运行时报）。

**结论**：简单条件用方法名（`findByEmail`），复杂条件用 `@Query`，不纠结。

---

## Q2（概念）：`FetchType.LAZY` 和 `EAGER` 的区别？为什么开发时感受不到 N+1？

**参考答案：**

| 维度 | LAZY | EAGER |
|------|------|-------|
| 加载时机 | 第一次访问关联字段时 | 加载主实体时 |
| SQL 数 | 主 + N（N+1 风险）| 主 + JOIN（1 条但数据量大）|
| 使用时风险 | 事务外读取抛 `LazyInitializationException` | 无（已加载）|
| 默认（@ManyToOne）| LAZY（Hibernate 5.3+）| EAGER（老版本）|
| 默认（@OneToMany）| LAZY | LAZY |

**为什么开发时感受不到 N+1**：

1. **开发数据少**（几十行），N+1 只多几十条 SQL，毫秒级完成。
2. **`show-sql` 滚动太快**，开发者不看日志。
3. 测试环境接口返回 200ms，开发觉得"正常"。
4. 生产 100 万行，N+1 = 100 万条额外 SQL → 秒级超时告警。

**修法预判**：

- 所有 `@ManyToOne`、`@OneToMany` 手写 `FetchType.LAZY`（不要依赖默认）。
- 列表 API 一定用 `@EntityGraph` 或 `JOIN FETCH`。
- 单测断言 SQL 数：用数据源代理统计 SQL 执行次数。

---

## Q3（实操）：以下 JPA 代码有 6 处问题，找出并改正。

```java
@Entity @Table(name = "post")
public class Post {
    @Id @GeneratedValue
    private Long id;

    @OneToMany
    private List<Comment> comments;  // ①

    @ManyToOne
    private User author;             // ②
}

@Service
public class PostService {
    @Autowired PostRepository repo;  // ③

    public PostVO detail(Long id) {
        Post p = repo.findById(id).orElse(null); // ④
        return new PostVO(p.getId(), p.getAuthor().getName()); // ⑤
    }
}
```

**参考答案（改后）：**

```java
@Entity @Table(name = "post")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)   // ⑥ 自增配策略
    private Long id;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  // ① 缺 mappedBy 和 LAZY
    private List<Comment> comments;

    @ManyToOne(fetch = FetchType.LAZY)                         // ② 显式 LAZY
    @JoinColumn(name = "user_id")
    private User author;
}

@Service @RequiredArgsConstructor
public class PostService {
    private final PostRepository repo;   // ③ 构造器注入

    @Transactional(readOnly = true)                        // ⑦ 事务包围
    public PostVO detail(Long id) {
        Post p = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("post not found: " + id)); // ④ orElse(null) 后面 NPE
        String authorName = p.getAuthor().getName();
        return new PostVO(p.getId(), authorName);           // ⑤ 在事务内访问懒加载
    }
}
```

**6 处问题**：

1. `@OneToMany` 缺 `mappedBy` → Hibernate 会建第三张关联表；缺 `FetchType.LAZY`。
2. `@ManyToOne` 缺 `FetchType.LAZY` → 默认 EAGER，每次查 post 都 JOIN user。
3. `@Autowired` 字段注入 → 改构造器注入。
4. `orElse(null)` → 后续 NPE；应抛业务异常。
5. `p.getAuthor().getName()` 在事务外 → `LazyInitializationException`。
6. `@GeneratedValue` 没指定 `strategy` → 默认 AUTO，MySQL 下可能用 Sequence 表，开销大。
7. 查询方法缺 `@Transactional(readOnly=true)`。

---

## Q4（实操）：用 `@EntityGraph` 修复 "取文章列表 + 作者昵称" 的 N+1 问题，并写单测验证只发 1 条 SQL。

**参考答案：**

### Repository

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p WHERE p.id > :lastId ORDER BY p.id")
    List<Post> pageWithAuthor(@Param("lastId") long lastId, Pageable pageable);
}
```

### 验证（单测）

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = NONE)
class PostRepositoryTest {

    @Autowired PostRepository repo;
    @Autowired EntityManager em;

    @Test
    @Commit   // 不自动回滚，SQL 真正发出去
    void pageWithAuthor_uses_single_query() {
        em.clear();

        List<Post> posts = repo.pageWithAuthor(0L, PageRequest.ofSize(10));

        // 访问作者不触发额外 SQL
        for (Post p : posts) {
            assertThat(p.getAuthor().getEmail()).isNotNull();
        }
    }
}
```

手动验证：看控制台日志中只有 1 条 `SELECT ... LEFT JOIN`，没有 N 条 `SELECT user ... WHERE user.id=?`。

**关键点**：

1. `@EntityGraph(attributePaths = "author")` 等价于 `JOIN FETCH p.author`，让 Hibernate 在这一次查询里提前 JOIN 出来。
2. 使用 `@EntityGraph` 后，author 虽然声明为 LAZY，但在标注的查询中也会被提前加载。
3. 不放心的话，在 `@Test` 里装一个 `DataSourceSpy` 拦截器，`assertThat(ds.getQueryCount()).isEqualTo(1)`。

---

## Q5（综合）：假设博客从 MyBatis 迁移到 Spring Data JPA，给出最小可行迁移路径，并分析各步风险。

**参考答案：**

### 方案 1：从边缘模块逐步替换（推荐）

| 步骤 | 内容 | 风险 | 回滚方式 |
|------|------|------|---------|
| 1 | 建 `TagJpaRepository`，新接口走 JPA | 无（新旧共存）| 删 JPA 接口 |
| 2 | Comment 模块替换 | 低 | 切回 MyBatis Mapper |
| 3 | User / Post 主表替换 | 中（注意 N+1 和事务行为）| 灰度到原 MyBatis 版 |
| 4 | 全部替换后删旧 MyBatis 文件 | 低 | Git 恢复 |

### 方案 2：双 Controller 并行

```java
// 旧（不变）
@RestController @RequestMapping("/api/v1/posts")
public class PostController { /* MyBatis */ }

// 新（JPA 版）
@RestController @RequestMapping("/api/jpa/posts")
public class JpaPostController { /* JPA */ }
```

- 优：并行运行，全量流量对比。
- 缺：维护两套 Controller。

### 最小可行迁移路径

```
1. 写对比文档 docs/jpa-vs-mybatis.md
2. Tag 表迁移
3. 生产灰度 Tag 接口 5% 流量（1 周观察无问题）
4. Comment 表迁移 + 灰度
5. User/Post 迁移（带 @EntityGraph）
6. 删旧 MyBatis 文件（全量灰度 2 周后）
```

### 生产迁移注意

| 风险 | 防范 |
|------|------|
| N+1 | 上线前 `show-sql` 确认，用 `@EntityGraph` 修复 |
| ddl-auto 删表 | 生产用 `validate`，不用 `update` |
| 乐观锁冲突 | 原表没有 `version` 列 → 先加 migration 再迁移 |
| 缓存行为 | JPA 缓存 vs MyBatis 缓存不同，压测对比 QPS |
| 异常类型 | MyBatis `DataAccessException` vs JPA `DataIntegrityViolationException`，全局异常处理器要覆盖 |

**关键点**：

1. ORM 迁移最大的风险是**行为差异**（SQL 不同、事务边界不同、缓存行为不同）——必须灰度。
2. **双 ORM 共存**技术上完全可行（不同 `@MapperScan`/`@EnableJpaRepositories` 包），无需迁移基础设施。
3. 如果你只做国内后端开发，找工作 80% 的公司用 MyBatis，JPA 更多是一道面试题。但这个对比实验的价值在于理解 ORM 的设计取舍。
