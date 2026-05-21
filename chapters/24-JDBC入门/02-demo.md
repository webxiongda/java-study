# Chapter 24 JDBC 入门 - 实操 Demo

## Demo 目标

用裸 JDBC 写完整 `UserDao`：建表 → CRUD → 事务 → HikariCP 连接池。**不引入 MyBatis**，纯手感。

## 前置条件

- MySQL 8 本地或 Docker 已起（端口 3306，root/root）
- 已有 baseline pom（第 21 章）

## 增量依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

`spring-boot-starter-jdbc` 会自动带 HikariCP。

## 建库建表

```sql
CREATE DATABASE IF NOT EXISTS blog DEFAULT CHARSET utf8mb4;
USE blog;

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4&rewriteBatchedStatements=true
    username: root
    password: root
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000
```

## User 实体

```java
package com.example.blog.dao;

import java.time.LocalDateTime;

public record User(Long id, String email, String name, LocalDateTime createdAt) {}
```

## UserDao（完整代码）

```java
package com.example.blog.dao;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class UserDao {
    private final DataSource dataSource;

    public UserDao(DataSource dataSource) { this.dataSource = dataSource; }

    /* ========== CREATE ========== */
    public Long insert(String email, String name) {
        String sql = "INSERT INTO users (email, name, created_at) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.setObject(3, LocalDateTime.now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
                throw new SQLException("no generated key");
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    /* ========== READ ========== */
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, email, name, created_at FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("find failed", e);
        }
    }

    public List<User> listAll() {
        String sql = "SELECT id, email, name, created_at FROM users ORDER BY id";
        List<User> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("list failed", e);
        }
        return list;
    }

    /* ========== UPDATE ========== */
    public int updateName(Long id, String newName) {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2, id);
            return ps.executeUpdate();             // 返回影响行数
        } catch (SQLException e) {
            throw new RuntimeException("update failed", e);
        }
    }

    /* ========== DELETE ========== */
    public int delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    /* ========== BATCH ========== */
    public int[] batchInsert(List<User> users) {
        String sql = "INSERT INTO users (email, name, created_at) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (User u : users) {
                ps.setString(1, u.email());
                ps.setString(2, u.name());
                ps.setObject(3, LocalDateTime.now());
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("batch failed", e);
        }
    }

    /* ========== TRANSACTION：转账风格 ========== */
    public void renameTwoUsers(Long id1, String n1, Long id2, String n2) {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(sql)) {
                ps1.setString(1, n1); ps1.setLong(2, id1);
                int r1 = ps1.executeUpdate();
                if (r1 == 0) throw new SQLException("user " + id1 + " not found");
            }
            try (PreparedStatement ps2 = conn.prepareStatement(sql)) {
                ps2.setString(1, n2); ps2.setLong(2, id2);
                int r2 = ps2.executeUpdate();
                if (r2 == 0) throw new SQLException("user " + id2 + " not found");
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("tx failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);       // 还回池前还原
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("name"),
            rs.getObject("created_at", LocalDateTime.class)
        );
    }
}
```

## 启动配置 + 演示

```java
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }

    @Bean
    UserDao userDao(DataSource ds) { return new UserDao(ds); }

    @Bean
    CommandLineRunner demo(UserDao dao) {
        return args -> {
            Long id1 = dao.insert("alice@x.com", "Alice");
            Long id2 = dao.insert("bob@x.com",   "Bob");
            System.out.println("inserted: " + id1 + ", " + id2);

            dao.findById(id1).ifPresent(System.out::println);
            System.out.println("all = " + dao.listAll());

            dao.renameTwoUsers(id1, "Alice2", id2, "Bob2");

            dao.delete(id1);
            dao.delete(id2);
        };
    }
}
```

## 运行 & 期望输出

```bash
mvn spring-boot:run
```

```
inserted: 1, 2
User[id=1, email=alice@x.com, name=Alice, createdAt=2024-06-01T10:00]
all = [User[id=1...], User[id=2...]]
```

## 失败场景演示：事务回滚

把 `renameTwoUsers` 第二个 update 的 id 故意填一个不存在的：

```java
dao.renameTwoUsers(id1, "Alice2", 999999L, "Ghost");
```

期望：
- 抛 `RuntimeException: tx failed`
- 第一个 user 名字**不应被更新**（事务回滚）
- SQL 验证：`SELECT * FROM users WHERE id=1` → name 仍是 "Alice"

如果忘记 `conn.setAutoCommit(false)`，则第一条 UPDATE 已经独立提交，回滚失败——这就是新手最常踩的事务坑。

## 提交建议

```bash
git add pom.xml src/
git commit -m "chapter 24: bare JDBC UserDao with CRUD + tx + batch"
```
