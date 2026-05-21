# Chapter 36 Spring Boot + MyBatis - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：`mybatis-spring-boot-starter` 自动注册了哪些关键 Bean？你什么时候需要手动覆盖它们？

**参考答案：**

**自动注册的关键 Bean**：

| Bean | 类型 | 作用 |
|------|------|------|
| `SqlSessionFactory` | `SqlSessionFactory` | 解析 `mybatis-config.xml` + mapper XML，持有数据库配置 |
| `SqlSessionTemplate` | `SqlSession` 代理 | 线程安全，支持 Spring 事务，实际调用委托给它 |
| `MapperFactoryBean<XxxMapper>` | 每个 Mapper 各一个 | JDK 动态代理，`getMapper()` 返回的那个 |
| `MybatisProperties` | `@ConfigurationProperties` | 读取 `mybatis.*` 配置项 |

**什么时候需要手动覆盖**：

| 场景 | 做法 |
|------|------|
| 多数据源（读写分离）| 为每个 DS 各自 `@Bean SqlSessionFactory`，`@Qualifier` 区分 |
| 定制 `TypeHandler` | `@Bean TypeHandler` 或在 `mybatis.type-handlers-package` 注册 |
| 插件（拦截器）| `@Bean Interceptor`（Boot 自动注入）或 `SqlSessionFactory` 手动 `addInterceptors` |
| 特殊 `Configuration` 属性 | `@Bean ConfigurationCustomizer`（最简洁）|

```java
@Bean
public ConfigurationCustomizer myCustomizer() {
    return cfg -> {
        cfg.setDefaultFetchSize(200);
        cfg.addInterceptor(new SlowSqlInterceptor());
    };
}
```

**关键点**：能用 `yml + ConfigurationCustomizer` 就不要重写 `SqlSessionFactory`，减少出错面。

---

## Q2（概念）：PageHelper 的分页原理是什么？和 keyset 分页相比，各适合什么场景？

**参考答案：**

**PageHelper 原理**：

```
PageHelper.startPage(page, size)
  → 往当前线程存分页参数（ThreadLocal）
  → PageInterceptor 拦截下一条 SELECT
  → 先执行 SELECT COUNT(*)  ← 这是性能瓶颈
  → 在原 SQL 后加 LIMIT offset, size
  → 返回 PageInfo<T>
  → ThreadLocal 清除
```

**对比**：

| 维度 | PageHelper（OFFSET 分页）| keyset 分页 |
|------|------------------------|-------------|
| 原理 | `LIMIT offset, size`，DB 跳过前 offset 行 | `WHERE id < lastId ORDER BY id DESC LIMIT size` |
| 性能 | O(offset)，第 1000 页扫 10000 行 | O(1)，永远只扫 size 行 |
| 跳页 | ✅ 可以直接跳到第 N 页 | ❌ 只能翻到下一页 |
| 适用 | 后台管理、导出（页数固定，数据量可控）| 首页、Feed、无限滚动 |
| 排序稳定性 | 需要唯一排序列，否则翻页数据重复 | 必须用唯一列（通常 id）作为锚点 |

**工程建议**：

- App Feed / 博客文章列表 → keyset。
- 后台报表分页（数据量 < 10 万）→ PageHelper。
- 后台导出全量数据 → 游标查询（`fetchSize + ResultHandler` 流式）。

**关键点**：PageHelper 的 COUNT 是最大的成本，在复杂 SQL（含子查询/GROUP BY）时尤其慢，可用 `startPage(page, size, false)` 跳过 count。

---

## Q3（实操）：以下 Boot + MyBatis 配置有 5 处问题，找出并改正。

```yaml
# application.yml
mybatis:
  mapper-locations: mapper/*.xml          # ①
  configuration:
    map_underscore_to_camel_case: true    # ②
    logImpl: stdout_logging               # ③

spring:
  datasource:
    url: jdbc:mysql://localhost/blog
    username: root
```

```java
@SpringBootApplication
@MapperScan("com.example")               // ④ 扫描范围太广
public class BlogApplication {}

public interface PostMapper {
    @Mapper                              // ⑤ 同时用了 @Mapper 和 @MapperScan
    List<Post> findAll();
}
```

**参考答案：**

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml   # ① classpath* 支持多模块；** 递归
  configuration:
    map-underscore-to-camel-case: true           # ② 用连字符（yml 规范），下划线虽可解析但不推荐
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # ③ 完整类名；stdout 开发可用，生产关掉

spring:
  datasource:
    url: jdbc:mysql://localhost/blog?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true
    username: root
    password: ${DB_PASSWORD}                     # ④ 密码不能缺，靠环境变量注入
    hikari:
      maximum-pool-size: 20
```

```java
@SpringBootApplication
@MapperScan("com.example.blog.dao")    // ④ 精确到 dao 包，避免误扫其他包
public class BlogApplication {}

// ⑤ 已有 @MapperScan，接口不需要再加 @Mapper（重复）
public interface PostMapper {
    List<Post> findAll();
}
```

**5 处问题**：

1. `mapper/*.xml` 不加 `classpath*` → 多模块 jar 里的 XML 扫不到；不加 `**` → 子目录扫不到。
2. `map_underscore_to_camel_case` → yml 规范用连字符（spring-boot relaxed binding 其实都能解析，但 `-` 是推荐格式）。
3. `logImpl: stdout_logging` → 应是全限定类名；生产应用 SLF4J 以便配 logback。
4. `@MapperScan("com.example")` 范围太广，可能扫到非 Mapper 的接口报错；另外缺 datasource.password。
5. `@Mapper` + `@MapperScan` 同时用是冗余，任选其一。

---

## Q4（实操）：配置读写分离，写操作走 master 数据源，`find*` 方法走 slave，用 AOP 自动路由，不需要开发在每个方法上手动指定。

**参考答案：**

```java
// 1. 数据源上下文
public class DataSourceHolder {
    static final ThreadLocal<String> KEY = new ThreadLocal<>();
    public static void setMaster() { KEY.set("MASTER"); }
    public static void setSlave() { KEY.set("SLAVE"); }
    public static String get() { return KEY.get() != null ? KEY.get() : "MASTER"; }
    public static void clear() { KEY.remove(); }
}

// 2. AbstractRoutingDataSource 路由
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() { return DataSourceHolder.get(); }
}

// 3. 配置
@Configuration
public class DataSourceConfig {
    @Bean @ConfigurationProperties("spring.datasource.master")
    public DataSource master() { return DataSourceBuilder.create().build(); }

    @Bean @ConfigurationProperties("spring.datasource.slave")
    public DataSource slave() { return DataSourceBuilder.create().build(); }

    @Primary @Bean
    public DataSource routing(DataSource master, DataSource slave) {
        Map<Object, Object> map = Map.of("MASTER", master, "SLAVE", slave);
        RoutingDataSource rds = new RoutingDataSource();
        rds.setTargetDataSources(map);
        rds.setDefaultTargetDataSource(master);
        return rds;
    }
}

// 4. AOP 路由切面（自动按方法名）
@Aspect @Component
public class DataSourceRoutingAspect {
    @Pointcut("within(@org.apache.ibatis.annotations.Mapper *)")
    public void mapper() {}

    @Around("mapper()")
    public Object route(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        if (method.startsWith("find") || method.startsWith("list") || method.startsWith("count")) {
            DataSourceHolder.setSlave();
        } else {
            DataSourceHolder.setMaster();
        }
        try { return pjp.proceed(); }
        finally { DataSourceHolder.clear(); }
    }
}
```

`application.yml`：

```yaml
spring:
  datasource:
    master:
      url: jdbc:mysql://master-host/blog
      username: root
      password: ${MASTER_PASS}
    slave:
      url: jdbc:mysql://slave-host/blog
      username: readonly
      password: ${SLAVE_PASS}
```

**单测验证**：

```java
@Test
void find_should_use_slave() {
    postMapper.findAll();
    assertThat(DataSourceHolder.get()).isEqualTo("SLAVE");
}
```

**关键点**：

1. `AbstractRoutingDataSource` 是 Spring 内置的多数据源路由基类，不用引入额外依赖。
2. **`@Transactional` 绑定了连接**：事务内第一条 SQL 决定数据源，AOP 的切换在事务外才有效 → 读写分离和 Spring 事务结合要小心（一般读操作不开事务 `@Transactional(readOnly=true)` 则可以走 slave）。
3. 生产更稳健的方案用 **ShardingSphere JDBC** 读写分离，不需要手写路由逻辑。

---

## Q5（综合）：博客项目需要迁移到 MyBatis-Plus（现有 Mapper 不能删），新功能用 MP，老功能保持原有 XML Mapper。如何平滑共存？列出完整迁移步骤。

**参考答案：**

### 一、依赖替换

```xml
<!-- 去掉 -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
<!-- 加入（MP 包含 MyBatis，兼容原 XML Mapper）-->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
```

### 二、配置迁移

```yaml
# mybatis.* 改成 mybatis-plus.*（部分沿用）
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml    # 原 XML 照用
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: isDeleted               # 全局软删字段
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### 三、老 Mapper 无需改动

原有 `PostMapper.xml` 不动；`PostMapper` 接口不动（MP 兼容原生 MyBatis）。

### 四、新功能用 MP BaseMapper

```java
// 新 Mapper 继承 BaseMapper
public interface TagMapper extends BaseMapper<Tag> {
    // 复杂 SQL 仍走 XML
    @Select("SELECT * FROM tag WHERE name LIKE CONCAT('%',#{kw},'%')")
    List<Tag> search(@Param("kw") String kw);
}
```

### 五、Entity 加 MP 注解

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("tag")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableLogic          // 逻辑删除
    private Integer isDeleted;
}
```

### 六、Service 用 MP ServiceImpl

```java
@Service
public class TagService extends ServiceImpl<TagMapper, Tag> {
    public List<Tag> popular() {
        return list(new LambdaQueryWrapper<Tag>()
            .orderByDesc(Tag::getPostCount).last("LIMIT 20"));
    }
}
```

### 七、防全表更新拦截器

```java
@Bean
public MybatisPlusInterceptor mpInterceptor() {
    MybatisPlusInterceptor i = new MybatisPlusInterceptor();
    i.addInnerInterceptor(new BlockAttackInnerInterceptor());   // 防 UPDATE/DELETE 全表
    i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return i;
}
```

### 八、灰度验证步骤

1. 起测试环境，跑老接口（原 XML Mapper 路径），断言结果不变。
2. 跑新接口（MP BaseMapper 路径），断言正确。
3. 确认无 `Invalid bound statement` / `Ambiguous mapper` 报错。
4. 生产灰度 5% 流量，监控 SQL 日志和错误率。

**关键点**：

1. MP 完全兼容原生 MyBatis，`@MapperScan` + XML 路径不动即可。
2. `TableLogic` 会在所有 MP 生成的 SELECT / DELETE 上自动追加 `is_deleted=0`，原生 XML 里的 SQL **不自动加**，要自己写。
3. 共存期间同一 Mapper 方法不要既在 XML 写又在 `BaseMapper` 继承，会报 `Ambiguous`。
4. MP 的 `BlockAttackInnerInterceptor` 是生产必配——防止 `updateById` 传了 null id 变成全表更新。
