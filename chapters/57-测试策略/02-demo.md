# Chapter 57 测试策略 - 实操 Demo

## Demo 目标

给一个 PostService + PostController 写全套测试: 纯单元 (Mockito) + Web 切片 (MockMvc) + 集成 (Testcontainers)。 跑通 `mvn test` 后看 JaCoCo 报告。

## 前置

- JDK 21, Maven 3.9+
- Docker (Testcontainers 用)

## pom 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.20.3</version>
    <scope>test</scope>
</dependency>
```

## 被测类

```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository repo;
    private final UserService userService;

    public Post create(Long userId, String title, String body) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is blank");
        }
        if (!userService.exists(userId)) {
            throw new UserNotFoundException(userId);
        }
        return repo.save(Post.builder()
            .authorId(userId).title(title).body(body)
            .createdAt(LocalDateTime.now()).build());
    }

    public Post findById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new PostNotFoundException(id));
    }
}
```

## Demo 1: 纯单元测试 (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository repo;
    @Mock UserService userService;
    @InjectMocks PostService service;

    @Test
    @DisplayName("正常创建文章")
    void shouldCreatePost_whenValid() {
        when(userService.exists(1L)).thenReturn(true);
        when(repo.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        Post result = service.create(1L, "hello", "body");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("hello");
        verify(repo).save(argThat(p ->
            p.getAuthorId().equals(1L) && p.getTitle().equals("hello")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("title 为空时抛 IllegalArgument")
    void shouldReject_whenTitleBlank(String title) {
        assertThatThrownBy(() -> service.create(1L, title, "body"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");

        verifyNoInteractions(repo, userService);
    }

    @Test
    @DisplayName("用户不存在抛 UserNotFound")
    void shouldThrow_whenUserNotExist() {
        when(userService.exists(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(99L, "t", "b"))
            .isInstanceOf(UserNotFoundException.class);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("findById 找到")
    void shouldFind_whenExists() {
        Post p = Post.builder().id(1L).title("hi").build();
        when(repo.findById(1L)).thenReturn(Optional.of(p));

        assertThat(service.findById(1L)).isEqualTo(p);
    }

    @Test
    @DisplayName("findById 找不到")
    void shouldThrow_whenNotExists() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
            .isInstanceOf(PostNotFoundException.class);
    }
}
```

## Demo 2: Controller 切片 (MockMvc)

```java
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)    // 关 Security
class PostControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PostService service;
    @Autowired ObjectMapper om;

    @Test
    void shouldReturn200_andJson() throws Exception {
        when(service.findById(1L))
            .thenReturn(Post.builder().id(1L).title("hello").body("b").build());

        mvc.perform(get("/api/v1/posts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("hello"));
    }

    @Test
    void shouldReturn404_whenNotFound() throws Exception {
        when(service.findById(99L)).thenThrow(new PostNotFoundException(99L));

        mvc.perform(get("/api/v1/posts/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn201_whenCreate() throws Exception {
        var req = Map.of("title", "hello", "body", "world");
        when(service.create(eq(1L), eq("hello"), eq("world")))
            .thenReturn(Post.builder().id(10L).title("hello").build());

        mvc.perform(post("/api/v1/posts")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void shouldReturn400_whenBlankTitle() throws Exception {
        var req = Map.of("title", "", "body", "world");

        mvc.perform(post("/api/v1/posts")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }
}
```

## Demo 3: 集成测试 (Testcontainers + 真 MySQL)

```java
@SpringBootTest
@Testcontainers
@Transactional        // 每个 test 后自动回滚
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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired PostService service;
    @Autowired PostRepository repo;
    @Autowired UserRepository userRepo;

    @BeforeEach
    void setUp() {
        userRepo.save(User.builder().id(1L).name("tom").build());
    }

    @Test
    void shouldPersist_andQuery() {
        Post saved = service.create(1L, "标题", "正文");

        assertThat(saved.getId()).isNotNull();

        Post fetched = service.findById(saved.getId());
        assertThat(fetched.getTitle()).isEqualTo("标题");
    }

    @Test
    void shouldRollback_whenUserNotExist() {
        long before = repo.count();

        assertThatThrownBy(() -> service.create(99L, "t", "b"))
            .isInstanceOf(UserNotFoundException.class);

        assertThat(repo.count()).isEqualTo(before);    // 没插
    }
}
```

## Demo 4: 异步测试 (Awaitility)

```java
@Test
void shouldEventuallyConsumeMq() {
    publisher.publish(new PostCreatedEvent(1L));

    await().atMost(5, SECONDS)
        .untilAsserted(() -> {
            assertThat(searchIndex.count("post:1")).isEqualTo(1);
        });
}
```

## 跑测试 + 覆盖率

```bash
mvn clean test
open target/site/jacoco/index.html
```

期望: 看到每个类的覆盖率, PostService 接近 100%。

## 跑 PIT mutation

```bash
mvn org.pitest:pitest-maven:mutationCoverage
open target/pit-reports/index.html
```

看 Mutation Coverage, 目标 > 60%。

## 提交

```bash
git add chapters/57-测试策略/demo/
git commit -m "ch57: unit + slice + integration test demo"
```
