# Chapter 24 JDBC 入门 - 项目任务

## 任务概述

为 `blog-api` 引入 MySQL + HikariCP，写一个**裸 JDBC 版 UserDao**（不引 MyBatis），跑通 CRUD + 事务 + 批量。这是后续 MyBatis / 事务章节的基础对照。

## 任务拆解

### Step 1：起 MySQL

```bash
# 用 Docker 起一个本地 MySQL
docker run --name blog-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=blog \
  -p 3306:3306 -d mysql:8.4
```

### Step 2：建表

在 `src/main/resources/db/schema.sql` 写建表脚本（见 `02-demo.md`），手动执行或用 Flyway。

### Step 3：配置 + 加依赖

按 `02-demo.md` 改 `pom.xml`、`application.yml`。

### Step 4：写 `UserDao`

实现：`insert / findById / findByEmail / listAll / updateName / delete / bulkInsert / renameTwoUsers(事务)`。

### Step 5：写测试

- `findById` 找不到返回 `Optional.empty()`
- `insert` 返回自增 id
- `renameTwoUsers` 第二个不存在时第一个**不被改动**
- `bulkInsert` 10 万条 < 2 秒

测试可以用 Testcontainers（推荐）或本地 MySQL。

### Step 6：监控连接池

打开 Spring Boot Actuator：

```yaml
management.endpoints.web.exposure.include: health,metrics,hikari
```

访问 `http://localhost:8080/actuator/metrics/hikaricp.connections.active` 看活跃连接数。

## 交付物

- [ ] `db/schema.sql`、`application.yml`、`pom.xml` 更新
- [ ] `dao/UserDao.java`（全 JDBC，无 ORM）
- [ ] `dao/UserDaoTest.java`（覆盖 CRUD + 事务回滚 + 批量性能）
- [ ] 一份 SQL 注入演示文档：贴出 `Statement` 版与 `PreparedStatement` 版的对照实验

## 验收清单

| 项 | 标准 |
|----|-----|
| 全部 SQL 用 `?` 占位 | grep 不到字符串拼接 SQL |
| 资源关闭 | 全部 try-with-resources |
| 事务行为 | 单元测试覆盖 commit / rollback 两种路径 |
| 批量性能 | 10 万行 ≤ 2 秒（开启 `rewriteBatchedStatements`） |
| 连接归还 | 跑完压测 `hikaricp.connections.active` 回到 0 |

## 扩展挑战

1. **慢查询发现**：把 HikariCP 的 `dataSource.queryTimeout` 设成 1 秒，故意写个 `SELECT SLEEP(2)` 看异常。
2. **连接泄漏检测**：把 `leakDetectionThreshold` 设 5000 ms，故意写一段拿连接不归还的代码，看日志告警。
3. **改成 JdbcTemplate**：用 Spring 的 `JdbcTemplate` / `NamedParameterJdbcTemplate` 重写一遍，对比代码量——这就是 MyBatis 之前的中间形态。
