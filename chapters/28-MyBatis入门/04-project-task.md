# Chapter 28 MyBatis 入门 - 项目任务

## 任务概述

把博客 DAO 层从 Chapter 24 的手写 JDBC 全部迁到 MyBatis，配单测覆盖 ≥ 70%。

## 业务背景

24 章的 `UserDao` 已经 200 多行，每个方法都是「拿连接 → ps.setXxx → while rs.next」。这一章用 MyBatis 砍掉样板，并把 27 章新设计的 6 张表的 DAO 全部建起来。

## 任务拆解

### Step 1：加依赖

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

`application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true
    username: root
    password: ${DB_PASSWORD}

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### Step 2：建实体 + Mapper 接口

`entity/`：`User`、`Post`、`Comment`、`Tag`、`PostTag`。

`dao/`：每张表 1 个 Mapper 接口，至少 6 个方法：

- `findById` / `findByXxx`（带索引能命中的查询）
- `insert`（开 `useGeneratedKeys`）
- `update`（按主键）
- `softDelete`（UPDATE is_deleted=1）
- `pageByXxx`（keyset 分页）
- `batchInsert`（foreach）

### Step 3：XML 映射

`resources/mapper/UserMapper.xml` 等 5 个文件，要求：

- 全部用 `#{}`，禁用 `${}`（除非白名单后的排序列）
- 多参数用 `@Param`
- 列名靠驼峰自动映射，不写多余 resultMap

### Step 4：单测

`src/test/java/.../UserMapperTest.java`：

```java
@SpringBootTest
@Transactional   // 自动回滚
class UserMapperTest {

    @Autowired UserMapper userMapper;

    @Test void findById_returns_user() {
        Long id = userMapper.insert(User.builder().email("t@x.com").nickname("t").build());
        assertThat(userMapper.findById(id).getEmail()).isEqualTo("t@x.com");
    }
}
```

每个 Mapper 至少 5 个测试用例。

### Step 5：失败场景

故意写一个 `${kw}` 的 Mapper 方法 → 写 SQL 注入测试用例证明可以拖库 → 改成 `#{kw}` → 测试通过。

### Step 6：开 SQL 日志看真实带参

在 yml 里临时换 P6Spy：

```yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/blog
```

跑一遍 `UserMapperTest`，截图 `logs/spy.log` 里带参 SQL 贴到 `docs/sql-log.md`。

## 交付物

- [ ] `src/main/java/com/example/dao/*.java`：5 个 Mapper 接口
- [ ] `src/main/resources/mapper/*.xml`：5 个映射
- [ ] `src/test/java/com/example/dao/*Test.java`：≥ 25 个测试
- [ ] `docs/sql-log.md`：P6Spy 截图
- [ ] `docs/sql-injection-demo.md`：注入复现 + 修复

## 验收清单

| 项 | 标准 |
|----|------|
| Mapper 全部走 `#{}` | grep `'${'` 在 mapper 下应只命中白名单后的列名 |
| 测试通过 | `mvn test` 全绿，覆盖率 ≥ 70% |
| 无 N+1 | 取「文章 + 作者名」用反范式 user_name，不要循环查 user |
| 命名一致 | 接口名 = `XxxMapper`；namespace = 接口全限定名 |
| 事务正确 | 测试用 `@Transactional` 自动回滚，不污染数据 |

## 扩展挑战

1. **MyBatis-Plus 对比**：选 1 张表用 MyBatis-Plus 的 BaseMapper，写一段博客对比"自动 CRUD 节省了多少代码 / 牺牲了什么"。
2. **PageHelper 接入**：分页接口从手算 offset 改成 `PageHelper.startPage(page, size)` 风格，对比可读性。
3. **TypeHandler**：写一个 `JsonTypeHandler<List<String>>`，让 Java List 自动序列化成 JSON 列。
4. **Mapper 接口生成器**：用 MyBatis Generator (MBG) 反向从 DB 生成 Mapper，对比手写 Mapper 的取舍。
