# Chapter 24 JDBC 入门 - 自测题

## Q1（概念）：`PreparedStatement` 为什么能防 SQL 注入？给出 `Statement` 被注入的具体示例。

**参考答案：**

**机制不同：**

- `Statement`：把 SQL 字符串原样送到 DB，DB 解析时分不清"用户输入"和"SQL 关键字"。
- `PreparedStatement`：先把带 `?` 的 SQL **发到 DB 预编译**，生成执行计划；后面 `setXxx` 设的值作为**数据**单独传，永远不参与 SQL 语法解析。

**注入示例：**

```java
// ❌ Statement
String name = request.getParameter("name");
String sql = "SELECT * FROM users WHERE name = '" + name + "'";
stmt.executeQuery(sql);
```

如果用户输入 `name = "x' OR '1'='1"`，拼出来：

```sql
SELECT * FROM users WHERE name = 'x' OR '1'='1'
```

结果：整张表泄漏。

更狠的输入 `name = "x'; DROP TABLE users; --"`：

```sql
SELECT * FROM users WHERE name = 'x'; DROP TABLE users; --'
```

JDBC 默认禁止多语句执行（URL 加 `allowMultiQueries=true` 才允许），但**单语句仍可被改写**（UNION 注入、布尔盲注、时间盲注）。

**改成 PreparedStatement：**

```java
// ✅
String sql = "SELECT * FROM users WHERE name = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, name);                  // 哪怕 name 含引号，也只是一个普通字符串值
ResultSet rs = ps.executeQuery();
```

驱动会做正确的转义（更准确：参数走 binary protocol 单独传输到 server，不进 SQL 文本）。即使 `name = "x' OR '1'='1"`，DB 也会真的去找 `name` 字段等于这个 17 字符串的行——肯定找不到，返回空。

**面试加分点**：

- PreparedStatement 还有 2 个好处：①预编译复用 plan cache；②类型校验（`setLong` 传 "abc" 编译就报错）。
- 万一参数本身就是表名 / 列名（不能用 `?` 占位），必须用**白名单校验**而不是拼字符串：
  ```java
  Set<String> allowed = Set.of("id", "name", "email");
  if (!allowed.contains(orderBy)) throw new IllegalArgumentException();
  String sql = "SELECT * FROM users ORDER BY " + orderBy;   // 安全
  ```

---

## Q2（概念）：HikariCP 的 `maximumPoolSize` 为什么不是越大越好？怎么估算合理值？

**参考答案：**

**直觉**：连接越多越能扛并发——错。

**真相**：每个连接在 DB 端对应一个进程/线程（MySQL 是线程），消耗 CPU、内存、锁资源。**池子大于 DB 能承受的并发，会反向拖垮 DB**。

**经典公式**（PostgreSQL 文档推荐）：

```
connections = (cpu_cores × 2) + effective_spindle_count
```

对 SSD：spindle ≈ 1。8 核机器 → 约 18 个连接。

**Hikari 官方建议**：**10 个连接已经足够大多数应用**——只要 DB 处理快，连接很快归还，复用率高。

**反例（"连接越多越好"的灾难）**：

- 设 `maximumPoolSize=200`，并发到 200 个请求同时进 DB。
- DB 200 个线程争 CPU、争 buffer pool 锁。
- 单个查询从 10ms 慢到 500ms。
- 业务线程拿着连接更久。
- HikariCP 池满，新请求阻塞。
- 出现"连接已满 + 查询全慢 + 业务超时"的死亡螺旋。

**实战调整步骤**：

1. **从 10 开始**，压测看 QPS、p99 延迟、DB CPU。
2. 加大池子，如果 **QPS 不涨甚至下降**，说明 DB 已经是瓶颈，加连接没用。
3. 关键指标：
   - HikariCP `metrics`：`pool.PendingConnections`（等池的线程数）持续 > 0 → 池太小；
   - DB `SHOW PROCESSLIST` 大量 SLEEP → 池太大；
   - DB CPU 100% → 优化 SQL / 加索引，不是加连接。

**别的关键参数**：

| 参数 | 含义 | 推荐值 |
|------|-----|------|
| `minimumIdle` | 池中常驻空闲连接数 | 一般 = maximumPoolSize（避免抖动） |
| `connectionTimeout` | 拿不到连接的等待上限 | 3000 ms（业务超时一般 5s，留缓冲） |
| `maxLifetime` | 连接最大存活时间 | < DB `wait_timeout` 30 秒以上，避免拿到已被服务端关掉的连接 |
| `idleTimeout` | 空闲多久被回收 | 10 分钟（前提 minIdle < maxPool） |
| `leakDetectionThreshold` | 借出超过多久告警泄漏 | 开发期开 30 秒；生产关或调长 |

**口诀**：**先调 SQL，再调索引，最后才调连接池**。连接池调参是"放大效果"，不是"创造性能"。

---

## Q3（实操）：以下 JDBC 代码有 6 处问题，找出并改正

```java
public User findByName(String name) {
    Connection conn = DriverManager.getConnection(url, user, pwd);
    Statement stmt = conn.createStatement();
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    ResultSet rs = stmt.executeQuery(sql);
    rs.next();
    User u = new User();
    u.setId(rs.getInt(1));
    u.setName(rs.getString(2));
    conn.close();
    return u;
}
```

**参考答案：**

**问题 1：用了 `Statement` → SQL 注入。** 改 `PreparedStatement` + `?`。

**问题 2：资源没关（Statement / ResultSet 都漏）。** 中间抛异常时 conn.close 也跑不到。用 try-with-resources 三层嵌套。

**问题 3：没判断 `rs.next()` 的返回值。** 查不到行时返回 false，但后面照样 `getInt` → `SQLException: Before start of result set`。

**问题 4：用 `getInt(1)` 列序号，且类型错（应该是 long）。** 用列名 + 正确类型：`rs.getLong("id")`。

**问题 5：`SELECT *` 反模式。** 加新列时映射错位、浪费带宽。明确列出。

**问题 6：每次 `DriverManager.getConnection` 没用连接池。** 每次 ~50ms，且并发上来 DB 扛不住。注入 `DataSource`。

**修正后：**

```java
public Optional<User> findByName(String name) {
    String sql = "SELECT id, email, name, created_at FROM users WHERE name = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, name);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getObject("created_at", LocalDateTime.class)
            ));
        }
    } catch (SQLException e) {
        throw new DataAccessException("findByName failed: " + name, e);
    }
}
```

---

## Q4（实操）：用 JDBC 实现"批量插入 10 万条数据"，要求总耗时 < 2 秒

**参考答案：**

逐条插入（10 万次 round-trip）≈ 60 秒。优化点：

1. `addBatch` + `executeBatch`
2. JDBC URL 加 `rewriteBatchedStatements=true`（MySQL 驱动把 N 条 INSERT 合成 1 条 `VALUES (...), (...), ...`）
3. 关闭 autoCommit，一次性 commit
4. 分批 commit，避免单事务过大

```java
public void bulkInsert(List<User> users) {
    String sql = "INSERT INTO users (email, name, created_at) VALUES (?, ?, ?)";
    int batchSize = 1000;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        conn.setAutoCommit(false);
        int count = 0;
        for (User u : users) {
            ps.setString(1, u.email());
            ps.setString(2, u.name());
            ps.setObject(3, LocalDateTime.now());
            ps.addBatch();

            if (++count % batchSize == 0) {
                ps.executeBatch();
                conn.commit();
                ps.clearBatch();
            }
        }
        ps.executeBatch();
        conn.commit();
        conn.setAutoCommit(true);

    } catch (SQLException e) {
        throw new RuntimeException("bulkInsert failed", e);
    }
}
```

**配套配置：**

```yaml
spring.datasource:
  url: jdbc:mysql://localhost:3306/blog?rewriteBatchedStatements=true&useServerPrepStmts=false
```

`useServerPrepStmts=false` 是 batch rewrite 启用的前提（驱动默认就是 false）。

**实测对比（本地 MySQL 8 + SSD）：**

| 写法 | 耗时 |
|------|-----|
| 朴素逐条 | ~70 秒 |
| 仅 addBatch / executeBatch | ~12 秒 |
| 加 `rewriteBatchedStatements=true` | **~1.2 秒**（提速约 60 倍） |

**生产警告：**

- 单事务过大会让 binlog / undo log 巨大，主从延迟严重 → 分批 commit。
- 不要在业务 API 里跑——长事务拖死整库；用定时任务或异步队列。
- 极限场景用 `LOAD DATA INFILE`（文件直导），比 INSERT 更快。

---

## Q5（综合）：基于裸 JDBC 写一个"模拟 Spring `@Transactional` 的事务管理器"

**题目**：

设计 `TxTemplate`，让业务可以这样写：

```java
txTemplate.execute(() -> {
    userDao.insert("a@x.com", "A");
    userDao.insert("a@x.com", "A");      // 唯一约束冲突，整体回滚
    return null;
});
```

要求：
1. 同一 `execute` 块内**所有 DAO 操作共用同一个 Connection**（事务才生效）。
2. 任何 RuntimeException 触发回滚。
3. 正常退出自动 commit。
4. 连接最后归还池。

**参考答案：**

**核心难点**：DAO 内部 `dataSource.getConnection()` 拿的是新连接，事务跨不了。Spring 的解法：用 `ThreadLocal` 绑定"当前事务的连接"，DAO 拿之前先查 ThreadLocal。

```java
package com.example.blog.tx;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public class TxTemplate {
    private final DataSource dataSource;
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    public TxTemplate(DataSource dataSource) { this.dataSource = dataSource; }

    /** DAO 调用此方法拿连接，确保事务内复用 */
    public Connection getConnection() throws SQLException {
        Connection conn = CURRENT.get();
        if (conn != null) return new NonClosingConnection(conn);   // 包一层防 DAO close 掉
        return dataSource.getConnection();                          // 事务外，新建
    }

    /** 事务执行入口 */
    public <T> T execute(Supplier<T> action) {
        if (CURRENT.get() != null) {
            // 嵌套：直接执行，不开新事务（Spring REQUIRED 默认行为）
            return action.get();
        }

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            CURRENT.set(conn);

            T result = action.get();
            conn.commit();
            return result;

        } catch (RuntimeException | Error e) {
            rollbackQuietly(conn);
            throw e;
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw new RuntimeException(e);
        } finally {
            CURRENT.remove();
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();           // 归还池
                } catch (SQLException ignored) {}
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
    }
}
```

`NonClosingConnection` 用 JDK 动态代理拦截 close：

```java
class NonClosingConnection {
    static Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> {
                if ("close".equals(method.getName())) return null;   // 忽略
                return method.invoke(real, args);
            }
        );
    }
}
```

**改造 DAO 使用 `TxTemplate`：**

```java
public class UserDao {
    private final TxTemplate tx;
    public UserDao(TxTemplate tx) { this.tx = tx; }

    public Long insert(String email, String name) {
        String sql = "INSERT INTO users (email, name, created_at) VALUES (?, ?, ?)";
        try (Connection conn = tx.getConnection();    // 关键：走 tx
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.setObject(3, LocalDateTime.now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**验证：**

```java
@Test
void execute_should_rollback_when_exception_thrown() {
    assertThatThrownBy(() -> tx.execute(() -> {
        userDao.insert("a@x.com", "A");
        userDao.insert("a@x.com", "A");           // unique 冲突
        return null;
    })).isInstanceOf(RuntimeException.class);

    assertThat(userDao.findByEmail("a@x.com")).isEmpty();   // 第一次插入也被回滚
}
```

**对照真实 Spring：**

| 我们的 TxTemplate | Spring `@Transactional` |
|-----------------|----------------------|
| `ThreadLocal<Connection>` | `TransactionSynchronizationManager` 也是 ThreadLocal |
| 嵌套事务直接合并 | `PROPAGATION_REQUIRED`（默认行为） |
| 任何异常回滚 | 默认只回滚 `RuntimeException` + `Error`（**checked 异常不回滚！**） |
| 没有传播行为 / 隔离级别 | 全支持 `REQUIRES_NEW` / `NESTED` / 不同隔离级别 |
| 动态代理屏蔽 close | Spring 用 `TransactionAwareDataSourceProxy` 做同样的事 |

**收获**：写完这道题后看 Spring 源码 `DataSourceTransactionManager`，会有"原来就这样"的顿悟——所有声明式事务的底层都是这一套 ThreadLocal + 手动 commit/rollback。
