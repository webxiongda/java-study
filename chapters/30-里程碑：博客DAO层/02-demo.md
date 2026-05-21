# Chapter 30 里程碑：博客 DAO 层 - 实操 Demo

## Demo 目标

从 0 到「能跑、能测、能 review」搭出 `blog-dao` 模块，包含：

- 5 实体 + 5 Mapper（接口 + XML）+ 3 Service
- Flyway 自动建表
- Testcontainers MySQL 跑集成测试
- JaCoCo 输出覆盖率报告
- 一份「8 个核心查询 EXPLAIN 通过」截图

## 前置

- Chapter 21 已建 Maven baseline；27 章 schema 已 ready
- Docker 已装（Testcontainers 要用）

## 一、模块结构

```
blog/
├── pom.xml                       (parent)
├── blog-dao/
│   ├── pom.xml
│   ├── src/main/java/com/example/blog/
│   │   ├── entity/      (User, Post, Tag, PostTag, Comment)
│   │   ├── dao/         (UserMapper, PostMapper, ...)
│   │   └── service/     (UserService, PostService, CommentService)
│   ├── src/main/resources/
│   │   ├── mapper/      (*.xml)
│   │   └── db/migration/V1__schema.sql
│   └── src/test/java/   (集成测试)
```

## 二、核心依赖

`blog-dao/pom.xml`：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.3</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <version>1.20.4</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

JaCoCo 插件：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution><goals><goal>prepare-agent</goal></goals></execution>
        <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
        <execution>
            <id>check</id><phase>test</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules><rule>
                    <element>BUNDLE</element>
                    <limits><limit>
                        <counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.70</minimum>
                    </limit></limits>
                </rule></rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## 三、Service 示例：发布文章

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {
    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final UserMapper userMapper;

    @Transactional
    public Long publish(Long userId, PostCreateReq req) {
        User author = userMapper.findById(userId);
        if (author == null) throw new BusinessException("user not found");
        if (author.getStatus() != 1) throw new BusinessException("user banned");

        Post p = Post.builder()
            .userId(userId)
            .userName(author.getNickname())        // 反范式冗余
            .title(req.getTitle())
            .summary(req.getSummary())
            .content(req.getContent())
            .status(req.isPublish() ? 1 : 0)
            .build();
        postMapper.insert(p);                       // 自增回填 p.id

        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            postTagMapper.batchInsert(p.getId(), req.getTagIds());
        }

        log.info("post published id={} userId={} tags={}", p.getId(), userId, req.getTagIds());
        return p.getId();
    }
}
```

## 四、Testcontainers 集成测试

```java
@SpringBootTest
@Testcontainers
class PostServiceIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("blog").withUsername("root").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired PostService postService;
    @Autowired UserMapper userMapper;

    @Test @Transactional
    void publish_should_insert_post_and_tags() {
        Long uid = userMapper.insert(User.builder()
            .email("a@x.com").nickname("A").password("xx").status(1).build());

        Long pid = postService.publish(uid, new PostCreateReq("hello", "abc", "...", true, List.of(1L,2L)));

        assertThat(pid).isPositive();
        // 验证 tag 关联也写入
    }

    @Test @Transactional
    void publish_should_throw_when_user_banned() {
        Long uid = userMapper.insert(User.builder()
            .email("b@x.com").nickname("B").password("xx").status(0).build());

        assertThatThrownBy(() -> postService.publish(uid, new PostCreateReq("t","s","c",true,null)))
            .hasMessageContaining("banned");
    }
}
```

## 五、跑通

```bash
cd blog-dao
mvn clean verify
# ... Flyway 自动建表
# ... 25 个集成测试通过
# ... JaCoCo: lines covered 78%
# BUILD SUCCESS
```

打开 `target/site/jacoco/index.html` 看覆盖率热力图。

## 六、EXPLAIN 验证

跑 `db/explain.sql`：

```sql
EXPLAIN SELECT id, title FROM post
WHERE user_id = 7 AND status = 1 ORDER BY created_at DESC LIMIT 10;
-- type=ref, key=idx_user_status_time ✅
```

把 8 个核心查询的 EXPLAIN 结果贴到 `docs/explain.md`。

## 七、失败场景：`@Transactional` 失效

```java
@Service
public class BadService {
    public void outer(Long uid) {
        inner(uid);   // ❌ 类内调用，事务注解失效
    }
    @Transactional
    public void inner(Long uid) {
        userMapper.delete(uid);
        throw new RuntimeException("boom");  // 数据竟然被删除了
    }
}
```

修法：把 `inner` 抽到另一个 Bean，或注入自己 (`@Autowired BadService self`)。

## 提交建议

```bash
git add blog-dao/ docs/explain.md
git commit -m "chapter 30 milestone: blog-dao module with 78% coverage + EXPLAIN-verified"
```
