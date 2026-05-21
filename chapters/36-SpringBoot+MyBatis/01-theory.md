# Chapter 36 Spring Boot + MyBatis - 理论篇

## 一、学习定位

28-29 章把 MyBatis 跑通了（纯 Spring），这一章专讲 **Boot + MyBatis 整合的所有配置细节**：Starter 帮你做了什么、怎么配多数据源、分页插件、代码生成、Mapper 扫描路径。

- 优先级：L1
- 预计投入：3 小时
- 阶段产出：博客项目的 `blog-api` 模块跑通 Boot + MyBatis，含分页、多数据源、单测

## 二、核心概念

### 1. `mybatis-spring-boot-starter` 帮你做了什么

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```

自动配置链：

| 自动配置类 | 做了什么 |
|----------|---------|
| `MybatisAutoConfiguration` | 读 `mybatis.*` 属性，创建 `SqlSessionFactory` |
| `MybatisLanguageDriverAutoConfiguration` | 注册 `LanguageDriver`（Freemarker / Velocity 模板 SQL）|
| `SqlSessionTemplate` | 线程安全的 SqlSession 代理 Bean |
| `@MapperScan`（自动）| 扫描 `@Mapper` 注解的接口，注册代理 |

你只需在 `application.yml` 里写：

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    default-fetch-size: 100
    default-statement-timeout: 30
  type-aliases-package: com.example.blog.entity
```

### 2. Mapper 扫描的 3 种方式

| 方式 | 例 | 优先级 |
|-----|-----|-------|
| `@Mapper` 接口注解 | `@Mapper public interface UserMapper {}` | 中 |
| 主类 `@MapperScan` | `@MapperScan("com.example.blog.dao")` | 高 |
| `mybatis.mapper-locations` + 自动扫描 | yml 配置 | 与 `@MapperScan` 配合 |

**推荐**：主类或配置类加 `@MapperScan("com.example.blog.dao")`，接口不需要每个加 `@Mapper`。

### 3. 分页插件 PageHelper

```xml
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>2.1.0</version>
</dependency>
```

```yaml
pagehelper:
  helper-dialect: mysql
  reasonable: true
  support-methods-arguments: true
```

使用：

```java
PageHelper.startPage(page, size);   // 拦截下一条 SELECT，自动加 LIMIT + COUNT(*)
List<Post> list = postMapper.findByStatus(1);
PageInfo<Post> info = new PageInfo<>(list);
// info.getTotal(), info.getList(), info.isHasNextPage()
```

**原理**：`PageInterceptor` 拦截 `StatementHandler.prepare()`，在 SQL 前执行 `SELECT COUNT(*)`，在 SQL 后加 `LIMIT ?, ?`。

**缺点**：COUNT 加在所有 SELECT 上（包括子查询），复杂 SQL 性能差；大 OFFSET 仍然慢。**生产推荐 keyset 分页**。

### 4. 多数据源

场景：读写分离（写 → master，读 → slave）。

```java
@Configuration
public class DataSourceConfig {
    @Primary
    @Bean("masterDs")
    @ConfigurationProperties("spring.datasource.master")
    public DataSource masterDataSource() { return DataSourceBuilder.create().build(); }

    @Bean("slaveDs")
    @ConfigurationProperties("spring.datasource.slave")
    public DataSource slaveDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    public DynamicDataSource dynamicDs(
        @Qualifier("masterDs") DataSource master,
        @Qualifier("slaveDs") DataSource slave) {
        Map<Object, Object> m = new HashMap<>();
        m.put("MASTER", master);
        m.put("SLAVE", slave);
        return new DynamicDataSource(master, m);   // 基于 AbstractRoutingDataSource
    }
}
```

路由切换：

```java
@Around("execution(* com.example..dao.*Mapper.find*(..))")
public Object slave(ProceedingJoinPoint pjp) throws Throwable {
    DataSourceContextHolder.setSlave();
    try { return pjp.proceed(); }
    finally { DataSourceContextHolder.clear(); }
}
```

**生产更简单**：用 ShardingSphere JDBC 的 `ReadwriteSplittingDataSourceFactory`，不用手写路由。

### 5. MyBatis-Plus 常用特性

| 特性 | 用法 |
|------|------|
| BaseMapper CRUD | `extends BaseMapper<Post>` |
| LambdaQueryWrapper | `.eq(Post::getStatus, 1).orderByDesc(Post::getId)` |
| 逻辑删除 | `@TableLogic int isDeleted` |
| 乐观锁 | `@Version int version` |
| 自动填充 | `@TableField(fill = FieldFill.INSERT) LocalDateTime createdAt` |
| 防全表更新 | `BlockAttackInnerInterceptor` 注册 |
| 分页 | `IPage<Post> page = mapper.selectPage(new Page<>(1, 10), wrapper)` |

### 6. Mapper 单测策略

```java
// 方式 1：@MybatisTest（只启动 MyBatis 相关 Bean，不加载 Web 层）
@MybatisTest
@AutoConfigureTestDatabase(replace = NONE)   // 用真实 DB，不用 H2
@Testcontainers
class PostMapperTest {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36");
    @DynamicPropertySource static void props(...) {...}

    @Autowired PostMapper postMapper;

    @Test @Transactional
    void find_by_status_returns_published() { ... }
}
```

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `MybatisAutoConfiguration` → `@ConditionalOnSingleCandidate(DataSource.class)` |
| 配置 | `mybatis.configuration.*` 直接映射到 `org.apache.ibatis.session.Configuration` |
| 执行 | `SqlSessionTemplate` 每次从 `SqlSessionFactory` 取 Session；Spring 事务自动绑定 |
| 边界 | 多数据源时 `@Primary` 必须设；事务管理器和数据源必须配套 |
| 验证 | 开 `log-impl: Slf4jImpl`，看带参 SQL |

## 四、在博客项目里的落点

- `@MapperScan("com.example.blog.dao")` 放主类或 `DaoConfig`。
- 全部 Mapper XML 在 `src/main/resources/mapper/`。
- 读写分离：写操作走 master，列表 / 搜索走 slave（可选，51 章并发后再加）。
- 单测：`@MybatisTest + Testcontainers`，无需全量 `@SpringBootTest`，快 3 倍。

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| Mapper Bean 找不到 | 包路径 `@MapperScan` 写错 | 确认主类子包 |
| XML 报 `Invalid bound statement` | namespace 或 id 拼错 | 检查 namespace = 接口全限定名 |
| 多数据源事务混乱 | 两个 DS 共用一个 `PlatformTransactionManager` | 每个 DS 配一个独立 TM |
| PageHelper count 慢 | 复杂 SQL 被 count 包一层 | `PageHelper.startPage(1,10,false)` 跳过 count |
| 驼峰不映射 | 没开 `map-underscore-to-camel-case` | 加到 yml |

## 六、面试高频问题

1. `mybatis-spring-boot-starter` 自动创建了哪些 Bean？
2. `@Mapper` 和 `@MapperScan` 的区别？
3. MyBatis 和 MyBatis-Plus 可以在同一项目共存吗？
4. 多数据源怎么路由到正确的 DS？事务管理器怎么配？
5. PageHelper 的分页原理？为什么大 OFFSET 仍然慢？
6. `@MybatisTest` 和 `@SpringBootTest` 哪个更快？
