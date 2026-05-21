# Chapter 32 Spring Boot 入门 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：Spring Boot 自动配置是怎么工作的？请用 `DataSourceAutoConfiguration` 举例说明 3 个关键注解的作用。

**参考答案：**

**机制**：Spring Boot 在启动时读取 classpath 下 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Boot 3.x），列出所有候选自动配置类，再逐个评估 `@ConditionalOnXxx` 决定是否生效。

```java
@AutoConfiguration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })  // ①
@ConditionalOnMissingBean(DataSource.class)                            // ②
@EnableConfigurationProperties(DataSourceProperties.class)             // ③
public class DataSourceAutoConfiguration {
    @Bean @ConditionalOnProperty(name="spring.datasource.url")
    public DataSource dataSource(DataSourceProperties props) {
        return DataSourceBuilder.create()
            .url(props.getUrl()).username(props.getUsername())
            .password(props.getPassword()).build();
    }
}
```

| 注解 | 作用 | `DataSourceAutoConfiguration` 场景 |
|-----|------|-----------------------------------|
| `@ConditionalOnClass` | classpath 有这些类时才激活 | HikariCP jar 存在才配 |
| `@ConditionalOnMissingBean` | 容器里没有这类 Bean 时才创建 | 你自己写了 `DataSource` Bean，自动配置就退出 |
| `@EnableConfigurationProperties` | 把 properties 类注册为 Bean，让 `@Value` 绑定 | `spring.datasource.*` 映射到 `DataSourceProperties` |

**怎么覆盖**：

```java
@Configuration
public class MyDbConfig {
    @Bean
    @Primary
    public DataSource customDs() {
        // 自己写 → @ConditionalOnMissingBean 让自动配置不创建
    }
}
```

**关键点**：自动配置是"约定优于配置"的体现——**有就用，没有就给你配**。你写的 Bean 永远优先。

---

## Q2（概念）：`application.yml` 和 `application-prod.yml` 是什么关系？配置项的优先级从高到低怎么排？

**参考答案：**

**关系**：

- `application.yml` 是基础配置，所有 Profile 下都加载。
- `application-prod.yml` 是 prod Profile 的覆盖，只在 `spring.profiles.active=prod` 时加载，且会**覆盖**基础配置里的同名 key。

**加载顺序图**：

```
application.yml        ←  基础，所有 profile 都有
application-dev.yml    ←  dev profile 叠加（覆盖基础同名 key）
application-prod.yml   ←  prod profile 叠加
```

**优先级（高到低）**：

```
1. 命令行参数               --server.port=9090
2. Java 系统属性            -Dserver.port=9090
3. 环境变量                 SERVER_PORT=9090
4. application-{profile}.yml（外部文件）
5. application-{profile}.yml（jar 包内）
6. application.yml（外部文件）
7. application.yml（jar 包内）
8. @PropertySource         @PropertySource("classpath:custom.properties")
9. 默认值（代码里的 defaultValue）
```

**外部文件** = jar 同目录的 `config/application.yml`，优先级高于 jar 包内。

**生产实践**：

```bash
java -jar blog.jar \
  --spring.profiles.active=prod \
  --spring.datasource.password=${DB_PASS}  # 密码从命令行/ENV 注入
```

**关键点**：不要把密码写到任何 yml 文件里提交到 Git；始终用环境变量或 Vault 覆盖。

---

## Q3（实操）：把以下 31 章的纯 Spring 代码改写成 Spring Boot 版本，并列出哪些配置"消失"了。

```java
// 31 章的写法
@Configuration
@ComponentScan("com.example.demo")
@EnableAspectJAutoProxy
@PropertySource("classpath:application.properties")
public class AppConfig {
    @Bean
    public HikariDataSource dataSource(
        @Value("${db.url}") String url,
        @Value("${db.user}") String user,
        @Value("${db.pass}") String pass) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(pass);
        return ds;
    }
}

public class Main {
    public static void main(String[] args) {
        new AnnotationConfigApplicationContext(AppConfig.class);
    }
}
```

**参考答案（Boot 版本）：**

```java
// Boot 只需这些
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

`application.yml`：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost/blog}
    username: ${DB_USER:root}
    password: ${DB_PASS:}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 5000
```

**消失的配置对照**：

| 31 章手写 | Boot 自动 | 负责的 AutoConfiguration |
|---------|---------|------------------------|
| `@ComponentScan("com.example.demo")` | 主类包自动扫 | `@SpringBootApplication` 内置 |
| `@EnableAspectJAutoProxy` | spring-aspects 在 classpath → 自动开 | `AopAutoConfiguration` |
| `@PropertySource("classpath:application.properties")` | `application.yml` 自动读 | `ConfigFileApplicationListener` |
| `@Bean DataSource dataSource()` | `spring.datasource.*` 自动初始化 HikariCP | `DataSourceAutoConfiguration` |
| `new AnnotationConfigApplicationContext()` | `SpringApplication.run()` | `SpringApplication` |
| 手配 Tomcat / DispatcherServlet | 自动内嵌 | `EmbeddedTomcatAutoConfiguration` + `DispatcherServletAutoConfiguration` |

**没消失的（你还需要写的）**：

- 实体类、Mapper 接口、Service、Controller（业务代码不会自动生成）。
- Flyway migration 文件（放 `db/migration/` 即可，自动执行）。
- 自定义配置（如 `@ConfigurationProperties`）。

---

## Q4（实操）：写一个 `DbHealthIndicator`，当 MySQL 连接正常时返回 `UP + latency`，断连时返回 `DOWN + 错误信息`。并写单测 mock DataSource 的两种情况。

**参考答案：**

```java
@Component
@RequiredArgsConstructor
public class DbHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            long ms = System.currentTimeMillis() - start;
            return Health.up()
                .withDetail("ping", "ok")
                .withDetail("latencyMs", ms)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

**单测**：

```java
@ExtendWith(MockitoExtension.class)
class DbHealthIndicatorTest {

    @Mock DataSource dataSource;
    @Mock Connection conn;
    @Mock PreparedStatement ps;
    @Mock ResultSet rs;
    @InjectMocks DbHealthIndicator indicator;

    @Test
    void returns_up_when_ping_ok() throws Exception {
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);

        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsKey("latencyMs");
    }

    @Test
    void returns_down_when_connection_fails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error").toString()).contains("connection refused");
    }
}
```

**关键点**：

1. **try-with-resources 三层**：Connection / Statement / ResultSet，缺一会泄漏。
2. **记录延迟**：运维用 `/actuator/health` 判断 DB 响应情况。
3. **mock 层次**：dataSource → conn → ps → rs 要一层一层 when()，否则 NPE。
4. **实际生产**：Spring Boot 已内置 `DataSourceHealthIndicator`（引入 spring-boot-starter-jdbc 自动激活），自定义 HealthIndicator 更多用于缓存/外部 API 检查。

---

## Q5（综合）：线上 Boot 应用突然 `OUT_OF_MEMORY`，你只能访问 `/actuator` 端点（无法 SSH）。请描述如何用 Actuator 定位问题，并给出 3 种防治手段。

**参考答案：**

### 一、通过 Actuator 定位

```bash
# 看 JVM 内存
curl http://app/actuator/metrics/jvm.memory.used
# {"name":"jvm.memory.used","measurements":[{"statistic":"VALUE","value":1.5e9}]}

# 看各内存区域
curl "http://app/actuator/metrics/jvm.memory.used?tag=area:heap"
curl "http://app/actuator/metrics/jvm.memory.used?tag=area:nonheap"

# 看当前线程数（排除线程泄漏）
curl http://app/actuator/metrics/jvm.threads.live

# 看 DB 连接池
curl http://app/actuator/metrics/hikaricp.connections.active
curl http://app/actuator/metrics/hikaricp.connections.pending

# 看 GC 频率（频繁 GC = 内存快撑不住）
curl http://app/actuator/metrics/jvm.gc.pause
```

**判断**：

| 指标 | 异常 | 可能原因 |
|------|------|---------|
| heap 接近 Xmx | > 90% | 内存泄漏 / 大对象未释放 |
| threads.live 持续增长 | > 500 | 线程泄漏（`@Async` 无界线程池） |
| hikaricp.pending > 0 | 持续 > 0 | DB 慢查询撑满连接池 |
| gc.pause 频繁 < 1s | Full GC / 10s | 老年代快满 |

### 二、获取 Heap Dump（通过 Actuator）

```bash
# Boot 3.x 默认不开，需先配置
management.endpoint.heapdump.enabled=true
# 然后
curl http://app/actuator/heapdump -o app.hprof
# 用 MAT / VisualVM 分析大对象
```

### 三、3 种防治手段

**防治 1：限制大查询返回行数**

```java
// Mapper 层
public List<Post> findAll();  // ❌ 无限制，百万行撑满 heap
// 改
public List<Post> findPage(@Param("size") int size, @Param("offset") long offset);
```

**防治 2：`@Async` 加线程池上限**

```java
@Bean
public Executor asyncExecutor() {
    var exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(4);
    exec.setMaxPoolSize(10);
    exec.setQueueCapacity(100);         // 超出拒绝，不无限堆积
    exec.setRejectedExecutionHandler(new CallerRunsPolicy());
    return exec;
}
```

**防治 3：JVM 参数收紧**

```bash
java -Xms512m -Xmx1g \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/logs/dump.hprof \
     -XX:+ExitOnOutOfMemoryError \   # OOM 立即退出让 K8s 重启，而非僵死
     -jar app.jar
```

**+ OOM 告警**：Prometheus 采集 `jvm.memory.used` → Grafana 告警（> 85% heap 发钉钉/邮件）。

### 四、 Actuator 安全提醒

暴露的端点应放在内网，且 `/actuator/heapdump` 和 `/actuator/env` 必须鉴权（含敏感信息）。

**关键点**：

1. `/actuator/metrics` 是最快的在线诊断，不需要 SSH 就能看内存/线程/GC。
2. `HeapDumpOnOutOfMemoryError` 要提前配，OOM 发生时来不及做。
3. 无界队列 + 无界线程池是 OOM 的头号元凶，上线前必须审核。
