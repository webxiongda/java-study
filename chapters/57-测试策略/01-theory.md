# Chapter 57 测试策略 - 理论篇

## 一、学习定位

测试不是"写完代码加几个 assert", 而是 **让代码可改、可重构、可上线** 的基础设施。 没测试的项目, 后期人人不敢动。

- 优先级: L1 (中大型项目必备)
- 投入: 5 小时
- 产出: 博客 API 单测覆盖率 ≥ 60%, 关键路径有集成测试

## 二、核心概念

### 1. 测试金字塔

```
        /\
       /E2E\           少 (慢 / 脆 / 高价值)
      /------\
     /集成测试\         中 (Service + DB)
    /----------\
   / 单元测试    \      多 (纯逻辑, 毫秒级)
  /--------------\
```

| 层 | 范围 | 工具 | 数量比 | 单条耗时 |
|---|---|---|---|---|
| 单元 | 单个类/方法, mock 依赖 | JUnit5 + Mockito | 70% | < 100ms |
| 集成 | 多个组件 + 真实 DB/MQ | SpringBootTest + Testcontainers | 25% | 1-5s |
| E2E | 完整请求链路 | MockMvc / RestAssured / Cypress | 5% | 5-30s |

**反模式 - 冰淇淋甜筒**: 顶上一大坨 E2E, 底下没单元测试 → 跑一次 30 分钟, 改任何一行都炸。

### 2. JUnit 5 关键能力

```java
@DisplayName("PostService 测试")
class PostServiceTest {

    @BeforeEach
    void setUp() { /* 每个 test 前跑 */ }

    @AfterEach
    void tearDown() { /* 每个 test 后跑 */ }

    @Test
    void shouldCreatePost_whenValidInput() { /* 命名: should_when */ }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void shouldRejectBlankTitle(String title) {
        assertThatThrownBy(() -> service.create(title, "body"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");
    }

    @Nested
    @DisplayName("当用户未登录")
    class WhenAnonymous { /* 嵌套分组 */ }

    @Test
    @Disabled("flaky, see #123")
    void racyTest() { }
}
```

### 3. Mockito 4 板斧

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository repo;
    @Mock UserService userService;
    @InjectMocks PostService service;     // 自动构造注入

    @Test
    void shouldReturnPost_whenIdExists() {
        // 1. given: stub
        when(repo.findById(1L)).thenReturn(Optional.of(new Post(1L, "hello")));

        // 2. when: 执行
        Post result = service.findById(1L);

        // 3. then: 断言
        assertThat(result.getTitle()).isEqualTo("hello");

        // 4. verify: 行为
        verify(repo, times(1)).findById(1L);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void shouldThrow_whenIdNotExist() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void shouldCaptureArgument() {
        var captor = ArgumentCaptor.forClass(Post.class);
        service.create("t", "b");
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getAuthor()).isNotNull();
    }
}
```

**坑**:
- `@Mock` 字段必须配合 `@ExtendWith(MockitoExtension.class)` 才生效
- `when(...).thenReturn` 不能 stub final 方法 (除非加 mockito-inline)
- `verify` 默认 `times(1)`, 不要漏写

### 4. AssertJ 链式断言

```java
// JUnit 原生 (难读)
assertEquals(3, list.size());
assertTrue(list.contains("a"));

// AssertJ (推荐)
assertThat(list)
    .hasSize(3)
    .contains("a", "b")
    .doesNotContain("z")
    .containsExactlyInAnyOrder("a", "b", "c");

// 对象
assertThat(user)
    .extracting(User::getName, User::getAge)
    .containsExactly("Tom", 25);

// 异常
assertThatThrownBy(() -> service.divide(1, 0))
    .isInstanceOf(ArithmeticException.class)
    .hasMessageContaining("zero");
```

### 5. Spring Boot 测试切片

| 注解 | 加载范围 | 用途 | 速度 |
|---|---|---|---|
| `@SpringBootTest` | 完整 context | 集成测试 | 慢 (3-10s) |
| `@WebMvcTest(PostController.class)` | 只 Web 层 | Controller 单测 | 快 (1-2s) |
| `@DataJpaTest` | 只 JPA + 嵌入式 DB | Repository 测试 | 快 (1-2s) |
| `@JsonTest` | 只 Jackson | 序列化测试 | 极快 |
| `@RestClientTest` | 只 RestTemplate | 客户端测试 | 极快 |

```java
@WebMvcTest(PostController.class)
class PostControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PostService service;    // 注意是 @MockBean 不是 @Mock

    @Test
    void shouldReturn200() throws Exception {
        when(service.findById(1L)).thenReturn(new Post(1L, "hello"));
        mvc.perform(get("/api/v1/posts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("hello"));
    }
}
```

### 6. Testcontainers (真实依赖)

H2 嵌入式 DB 行为和 MySQL 有差异 (语法 / 函数 / 大小写), 集成测试推荐用 **真容器**:

```java
@SpringBootTest
@Testcontainers
class PostIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("blog")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired PostRepository repo;

    @Test
    void shouldPersistPost() {
        var saved = repo.save(new Post(null, "hello", "body"));
        assertThat(saved.getId()).isNotNull();
    }
}
```

**static 容器**: 多个测试类共用一个容器实例, 启动一次。 否则每个类 30 秒, 100 个类 = 50 分钟。

### 7. 数据准备 (Test Fixture)

```java
// 方式 1: @Sql 注解
@Sql({"/sql/init.sql","/sql/posts.sql"})
@Test
void shouldFindAll() { ... }

// 方式 2: TestEntityManager (DataJpaTest)
@Test
void shouldFindByAuthor() {
    em.persist(new Post(...));
    em.flush();
    var found = repo.findByAuthorId(1L);
    assertThat(found).hasSize(1);
}

// 方式 3: builder (PostFixture)
public class PostFixture {
    public static Post.PostBuilder defaultPost() {
        return Post.builder()
            .title("default-title")
            .body("default-body")
            .authorId(1L)
            .createdAt(LocalDateTime.now());
    }
}
// 用: PostFixture.defaultPost().title("自定义").build()
```

### 8. 覆盖率 (JaCoCo)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

`mvn test` 后看 `target/site/jacoco/index.html`。

**坑**: 覆盖率高 ≠ 测试好。 全是 `assertThat(result).isNotNull()` 也能 100% 覆盖率, 但没断言业务逻辑。

### 9. Mutation Testing (PIT)

JaCoCo 只看"代码被执行", PIT 主动 **改坏你的代码** (如把 `>` 改成 `<`), 然后跑测试 — 测试没失败 = 测试无效。

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.16.0</version>
</plugin>
```

`mvn org.pitest:pitest-maven:mutationCoverage` 看 mutation score。 目标 > 60%。

## 三、命名约定

```java
// 推荐: should_<expectedResult>_when_<condition>
shouldReturnUser_whenIdExists()
shouldThrow_whenIdNotFound()
shouldRejectBlankTitle()

// 或: <method>_<scenario>_<expected>
findById_existing_returnsUser()
findById_missing_throws()
```

## 四、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| 测试依赖运行顺序 | 偶发失败 | 每个 test 独立 + `@DirtiesContext` 或 `@Transactional` 回滚 |
| 测试共用静态状态 | 互相污染 | `@BeforeEach` 重置, 不用 static |
| Mock 一切 (Service mock Repo mock Mapper) | 测的是 mock, 不是逻辑 | 找出最有价值的层, 集成测试覆盖整链 |
| H2 替代 MySQL | 语法兼容失败上线才发现 | Testcontainers 真 MySQL |
| 测试代码不审 review | 烂测试越积越多 | 测试也是代码, 同样要 review |
| `@SpringBootTest` 滥用 | 跑一次 5 分钟 | 用切片注解 (`@WebMvcTest` 等) |
| sleep 等异步 | 慢且 flaky | `Awaitility.await().until(...)` |
| 测试代码硬编码当前时间 | 跨时区/明年挂 | 注入 `Clock`, 测试用 `Clock.fixed` |

## 五、面试高频

1. 测试金字塔 3 层是哪些? 比例?
2. `@Mock` 和 `@MockBean` 区别?
3. `@SpringBootTest` 和 `@WebMvcTest` 选哪个? 性能差距?
4. 怎么测 controller? 怎么测 service?
5. Testcontainers 解决了什么? H2 行不行?
6. 异步 (CompletableFuture / @Async / MQ) 怎么测?
7. 覆盖率多少合适? mutation testing 是什么?
8. 测试代码可以 review 吗? 测试代码也算技术债吗?
9. TDD 你实践过吗?
10. 集成测试慢怎么办? (并行 / 容器复用 / @DirtiesContext 谨慎用)

## 六、Demo / 任务

- Demo: 给 PostService 写完整测试 (单元 + 集成 + Controller)
- Task: 博客 API 覆盖率到 60%, 关键路径全部集成测试, CI 中跑测试 + JaCoCo + PIT
