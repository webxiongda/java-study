# Chapter 24 JDBC 入门 - 理论篇

## 一、学习定位

JDBC 是 Java 操作数据库的最底层 API——后面 MyBatis / Spring Data 都是它的封装。理解 JDBC 才能看懂连接池、事务、SQL 注入这些"高级问题"的本质。

- 优先级：L1 必须掌握
- 预计投入：3 小时
- 阶段产出：用裸 JDBC 写一份 `UserDao`，能 CRUD + 防 SQL 注入

## 二、核心概念

### 1. 五大核心对象

```
DriverManager → Connection → Statement / PreparedStatement → ResultSet
                     ↓
                  事务管理（commit / rollback）
```

| 对象 | 职责 | 必须关闭 |
|------|-----|---------|
| `DriverManager` | 根据 URL 找驱动，建连接 | 否（工厂类） |
| `Connection` | 一次会话；事务的边界 | ✅ |
| `Statement` | 执行 SQL（拼字符串，**有注入风险**） | ✅ |
| `PreparedStatement` | 预编译 SQL（参数化，**唯一推荐**） | ✅ |
| `ResultSet` | 查询结果游标 | ✅ |

### 2. 连接 URL 与驱动

```java
String url = "jdbc:mysql://localhost:3306/blog?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
String user = "root";
String password = "...";
Connection conn = DriverManager.getConnection(url, user, password);
```

JDBC 4.0+ 不再需要 `Class.forName("com.mysql.cj.jdbc.Driver")`——`META-INF/services/java.sql.Driver` 由驱动 jar 自动注册（SPI 机制）。

### 3. PreparedStatement vs Statement

```java
// ❌ Statement：字符串拼接 → SQL 注入
String sql = "SELECT * FROM users WHERE name='" + name + "'";
// 如果 name = "x' OR '1'='1" → SELECT * FROM users WHERE name='x' OR '1'='1'
// 整张表都泄漏！
stmt.executeQuery(sql);

// ✅ PreparedStatement：参数化
String sql = "SELECT * FROM users WHERE name = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, name);                     // 驱动负责转义
ResultSet rs = ps.executeQuery();
```

**PreparedStatement 三大好处**：
1. 防 SQL 注入（驱动转义参数）。
2. 预编译——同一 SQL 多次执行只解析一次，DB 端 plan cache 命中。
3. 类型安全（`setInt` / `setString` 编译期就报错）。

### 4. 资源关闭与 try-with-resources

```java
String sql = "SELECT id, name FROM users WHERE id = ?";
try (Connection conn = DriverManager.getConnection(url, u, p);
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setLong(1, id);
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            return new User(rs.getLong("id"), rs.getString("name"));
        }
    }
}
// 三个资源都会按 LIFO 顺序自动关闭
return null;
```

**不关的后果**：Connection 句柄泄漏 → 连接池被掏空 → 全站 hang 死。

### 5. 事务（Transaction）

默认 `Connection` 是 **autoCommit=true**——每条 SQL 一个事务，立即生效。

```java
try (Connection conn = DriverManager.getConnection(url, u, p)) {
    conn.setAutoCommit(false);             // 关闭自动提交
    try {
        ps1.executeUpdate();               // 扣款
        ps2.executeUpdate();               // 加款
        conn.commit();                     // 一起生效
    } catch (SQLException e) {
        conn.rollback();                   // 出错全回滚
        throw e;
    } finally {
        conn.setAutoCommit(true);          // 还回连接池前还原
    }
}
```

**4 个隔离级别**（`conn.setTransactionIsolation(...)`）：

| 级别 | 脏读 | 不可重复读 | 幻读 | MySQL 默认 |
|------|------|----------|------|-----------|
| READ_UNCOMMITTED | 可能 | 可能 | 可能 | ❌ |
| READ_COMMITTED | 不可能 | 可能 | 可能 | ❌（Oracle 默认） |
| REPEATABLE_READ | 不可能 | 不可能 | 可能（InnoDB 用 MVCC 实际也防住） | ✅ |
| SERIALIZABLE | 不可能 | 不可能 | 不可能 | ❌（性能太差） |

### 6. 连接池（HikariCP / Druid）

裸 `DriverManager.getConnection` 每次开销 ~50ms。生产用**连接池**：连接预先建好放池，用完归还。

```java
HikariConfig cfg = new HikariConfig();
cfg.setJdbcUrl(url);
cfg.setUsername(user);
cfg.setPassword(pwd);
cfg.setMaximumPoolSize(20);
cfg.setMinimumIdle(5);
cfg.setConnectionTimeout(3000);            // 3s 拿不到连接就抛
cfg.setIdleTimeout(600_000);
cfg.setMaxLifetime(1_800_000);             // < 数据库的 wait_timeout

DataSource ds = new HikariDataSource(cfg);
try (Connection conn = ds.getConnection()) { /* ... */ }   // close 是归还，不是真关
```

Spring Boot 默认 HikariCP，配置：
```yaml
spring.datasource:
  url: jdbc:mysql://...
  username: root
  password: xxx
  hikari:
    maximum-pool-size: 20
    connection-timeout: 3000
```

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `DriverManager.getConnection(url)` 或 `dataSource.getConnection()` |
| 配置 | URL 参数（`useSSL`、`rewriteBatchedStatements`、`serverTimezone`）+ 池参数 |
| 执行 | `executeQuery` 走查询路径返回 ResultSet；`executeUpdate` 返回影响行数 |
| 边界 | 连接泄漏、ResultSet 没关、自动提交关了忘记还原、批量插入没用 `addBatch` |
| 验证 | 看 `SHOW PROCESSLIST` 当前连接数；DB 慢查询日志；HikariCP `metrics` |

## 四、项目使用场景

- **第 28-29 章 MyBatis**：MyBatis 底层就是 JDBC + ResultSet ↔ Java 对象映射。
- **第 35 章事务**：Spring `@Transactional` 本质是包了一层 `setAutoCommit(false) + commit/rollback`。
- **第 39 章配置**：DataSource 通过 `application.yml` 注入。
- **第 58 章性能**：批量插入用 `PreparedStatement#addBatch()` + `executeBatch()`，1000 倍提速。

## 五、常见问题与坑

| 问题 | 后果 | 处理方式 |
|---|---|---|
| 字符串拼接 SQL | 注入漏洞 | 全部用 `?` 参数化 |
| ResultSet 不关 | 游标泄漏 | try-with-resources 三层 |
| `conn.close()` 后还想用 | `SQLException: closed` | 池化场景：close 是归还，别在 Service 里持有 |
| 拿连接不还（业务异常吞了） | 池被掏空 → `Connection is not available, request timed out` | finally 关 / try-with-resources |
| 跨连接做事务 | 事务隔离失败 | 同一事务必须用同一 Connection（Spring 用 ThreadLocal 绑） |
| 时间字段时区错乱 | UTC 与 +8:00 串了 | URL 加 `serverTimezone=Asia/Shanghai`，统一存 UTC |
| 批量插入不用 batch | 慢 100 倍 | `addBatch()` + `executeBatch()` + URL 加 `rewriteBatchedStatements=true` |

## 六、面试高频问题

1. `Statement` 和 `PreparedStatement` 的区别？为什么后者能防 SQL 注入？
2. JDBC 4.0 之后还需要 `Class.forName(driver)` 吗？为什么？
3. `Connection.close()` 在连接池场景下做了什么？
4. 描述一次 JDBC 查询的完整流程（连接、预编译、执行、读结果、关资源）。
5. 怎么用 JDBC 做一次包含 2 条 update 的事务？
6. HikariCP 的 `maximumPoolSize` 设多大合适？为什么不是越大越好？
7. ResultSet 不关会有什么后果？
