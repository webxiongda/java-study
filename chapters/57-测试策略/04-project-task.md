# Chapter 57 测试策略 - 项目任务

## 任务概述

给博客 API 立 **完整测试体系**:

1. 单元测试覆盖核心 Service (PostService / UserService / CommentService)
2. Controller 切片测试 (MockMvc 验证 HTTP 行为)
3. 集成测试 (Testcontainers, 真 MySQL + Redis)
4. JaCoCo 覆盖率门槛 60%
5. PIT mutation testing 报告
6. CI 跑测试 + 覆盖率检查, 阻断不达标的 PR

## 业务背景

之前章节大量代码没测试, 改东西心里没底。 上线 v2 前必须建测试保护网, 不然以后任何修改都得手测一遍, 速度和质量都不可控。

## 任务拆解

### Step 1: pom 接入测试依赖 (10 分钟)

```xml
<dependencies>
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
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <version>4.2.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
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
                    <phase>verify</phase>
                    <goals><goal>report</goal></goals>
                </execution>
                <execution>
                    <id>check</id>
                    <phase>verify</phase>
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
                                <excludes>
                                    <exclude>com.javastudy.config.*</exclude>
                                    <exclude>com.javastudy.dto.*</exclude>
                                    <exclude>com.javastudy.*Application</exclude>
                                </excludes>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-maven</artifactId>
            <version>1.16.0</version>
            <configuration>
                <targetClasses><param>com.javastudy.service.*</param></targetClasses>
                <targetTests><param>com.javastudy.service.*</param></targetTests>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Step 2: 单元测试 (60 分钟)

建 `src/test/java/com/javastudy/service/` 下:
- `PostServiceTest.java` - 创建 / 查询 / 删除 / 权限校验
- `CommentServiceTest.java` - 创建评论 / 防重复 / 软删
- `AuthServiceTest.java` - 登录 / token / 失败次数限制

每个 Service 至少:
- 1 个 happy path
- 2 个异常分支
- 1 个边界 (空集合 / 边界数字 / 临界状态)

### Step 3: Controller 切片 (30 分钟)

```java
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PostService service;
    @Autowired ObjectMapper om;

    @Test
    void shouldReturn201() throws Exception {
        when(service.create(any(), any(), any()))
            .thenReturn(Post.builder().id(1L).build());

        mvc.perform(post("/api/v1/posts")
                .header("X-User-Id", 1)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("title", "t", "body", "b"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn400_whenBlankTitle() throws Exception {
        mvc.perform(post("/api/v1/posts")
                .header("X-User-Id", 1)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("title", "", "body", "b"))))
            .andExpect(status().isBadRequest());
    }
}
```

覆盖: POST / GET / PUT / DELETE / 404 / 400 / 401 / 403。

### Step 4: 集成测试 (60 分钟)

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class BlogApiIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepo;

    @BeforeEach
    void setUp() {
        userRepo.save(User.builder()
            .username("alice").password("$2a$10$xxx").build());
    }

    @Test
    void shouldCompleteFullFlow_login_create_query() throws Exception {
        // 1. 登录拿 token
        String token = login("alice", "password");

        // 2. 创建文章
        String postJson = mvc.perform(post("/api/v1/posts")
                .header(AUTHORIZATION, "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"title":"集成测试", "body":"正文"}
                """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long postId = JsonPath.read(postJson, "$.id");

        // 3. 查询命中缓存 (第二次 < 5ms)
        mvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("集成测试"));
    }
}
```

### Step 5: 异步测试 (15 分钟)

如果有 `@Async` 或 MQ 消费, 用 Awaitility:

```java
@Test
void shouldEventuallyIndexPost() {
    Long id = postService.create(1L, "标题", "正文");

    await().atMost(5, SECONDS)
        .untilAsserted(() -> {
            assertThat(searchClient.exists("post:" + id)).isTrue();
        });
}
```

### Step 6: CI 接入 (15 分钟)

`.github/workflows/ci.yml` 加:

```yaml
- name: Test + Coverage
  run: mvn -B verify    # verify 阶段触发 jacoco check

- name: Upload coverage
  uses: codecov/codecov-action@v4
  with:
    file: target/site/jacoco/jacoco.xml

- name: Mutation testing (nightly)
  if: github.event_name == 'schedule'
  run: mvn -B org.pitest:pitest-maven:mutationCoverage
```

## 交付物

- [ ] `src/test/java/.../service/*ServiceTest.java` 单元测试 ≥ 10 个文件
- [ ] `src/test/java/.../controller/*ControllerTest.java` 切片测试
- [ ] `src/test/java/.../integration/*IntegrationTest.java` Testcontainers
- [ ] JaCoCo 报告 LINE ≥ 60%
- [ ] PIT mutation score ≥ 50% (起步)
- [ ] CI 跑测试 + JaCoCo check + Codecov 上传
- [ ] 至少 1 个 bug 修复带回归测试 (PR 里同时改代码 + 加测试)
- [ ] git commit: `ch57: comprehensive testing + jacoco + ci`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 单元测试速度 | 整套 < 30s |
| 集成测试 | 全部能并行跑, 不依赖外部环境 |
| 覆盖率 | `mvn verify` jacoco check 通过 (≥ 60%) |
| 关键路径 | 钱 / 权限 / 主流程 都有 happy + sad + boundary |
| Mock 合理 | 不滥用 @MockBean (一个 SpringBootTest 不超 3 个) |
| 测试命名 | 看名字就知道在测什么 |
| CI 阻断 | 测试失败 / 覆盖率不达标 → PR 不能合 |
| 回归测试 | 修 bug 必须先写一个会失败的测试 |

## 扩展挑战

1. **Spring REST Docs**: 测试同时生成 API 文档, 测试通过文档就更新
2. **契约测试 (Pact)**: 上下游服务接口契约自动化
3. **变异测试报告**: PIT 输出弱测试列表, 修补到 score > 70%
4. **Cypress E2E**: 给前端 + 后端写端到端用例
5. **Flaky test 治理**: GitHub Actions 失败重跑 + flaky 测试统计 + 周报
