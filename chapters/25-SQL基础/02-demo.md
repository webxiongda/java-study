# Chapter 25 SQL 基础 - 实操 Demo

## Demo 目标

在博客库里建 3 张表（`users` / `posts` / `comments`）、塞 1 万行测试数据、跑 10 个真实查询、用 `EXPLAIN` 看执行计划，最后做一次"加索引前 vs 加索引后"的对比。

## 前置

- MySQL 8 已起（Chapter 24 已建库 `blog`）
- 命令行客户端：`mysql -uroot -p`

## 一、建表（DDL）

```sql
USE blog;
DROP TABLE IF EXISTS comments, posts, users;

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,         -- 1=active 0=banned
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE posts (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 0,         -- 0=draft 1=published
    view_count  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE comments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 二、灌数据

```sql
-- 1000 个用户
INSERT INTO users (email, name)
SELECT CONCAT('u', n, '@x.com'), CONCAT('User', n)
FROM (SELECT a.N + b.N*10 + c.N*100 + 1 AS n
      FROM (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
           (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
           (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
     ) t
WHERE n <= 1000;

-- 1 万篇 post（每人平均 10 篇）
INSERT INTO posts (user_id, title, status, view_count, created_at)
SELECT
    FLOOR(1 + RAND() * 1000),
    CONCAT('Post-', n),
    IF(RAND() < 0.8, 1, 0),
    FLOOR(RAND() * 10000),
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM (/* 同上 10000 行 */) t;
```

（生产用 `mysqlslap` 或 Python 脚本灌数据更方便。）

## 三、10 个示例查询

### 1. 等值查询

```sql
SELECT * FROM users WHERE email = 'u500@x.com';
```

### 2. 范围 + 排序

```sql
SELECT id, title, view_count
FROM posts
WHERE status = 1 AND created_at >= '2024-01-01'
ORDER BY view_count DESC
LIMIT 10;
```

### 3. JOIN

```sql
SELECT u.name, COUNT(p.id) AS post_cnt
FROM users u LEFT JOIN posts p ON u.id = p.user_id AND p.status = 1
GROUP BY u.id, u.name
ORDER BY post_cnt DESC
LIMIT 10;
```

### 4. 子查询：找出至少有 5 篇已发布文章的用户

```sql
SELECT u.id, u.name
FROM users u
WHERE u.id IN (
    SELECT user_id FROM posts WHERE status = 1 GROUP BY user_id HAVING COUNT(*) >= 5
);
```

### 5. EXISTS 改写（效率通常更好）

```sql
SELECT u.id, u.name
FROM users u
WHERE EXISTS (
    SELECT 1 FROM posts p
    WHERE p.user_id = u.id AND p.status = 1
);
```

### 6. 聚合 + HAVING

```sql
SELECT user_id, COUNT(*) AS cnt
FROM comments
GROUP BY user_id
HAVING cnt > 50
ORDER BY cnt DESC;
```

### 7. 窗口函数（MySQL 8+）：每个用户阅读量 Top1 的 post

```sql
SELECT * FROM (
    SELECT id, user_id, title, view_count,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY view_count DESC) AS rn
    FROM posts WHERE status = 1
) t
WHERE rn = 1;
```

### 8. 日期分组：每天发布数

```sql
SELECT DATE(created_at) AS d, COUNT(*) AS cnt
FROM posts
WHERE status = 1 AND created_at >= '2024-01-01'
GROUP BY DATE(created_at)
ORDER BY d;
```

### 9. UPDATE 配合 JOIN

```sql
-- 把"已发布且评论数 >= 10 的文章" 的 view_count +100（活跃文章流量加权）
UPDATE posts p
JOIN (SELECT post_id, COUNT(*) AS cnt FROM comments GROUP BY post_id) c
  ON p.id = c.post_id
SET p.view_count = p.view_count + 100
WHERE p.status = 1 AND c.cnt >= 10;
```

### 10. DELETE + LIMIT（防误删保护）

```sql
DELETE FROM comments
WHERE created_at < NOW() - INTERVAL 1 YEAR
LIMIT 1000;            -- 批量删，每次 1000 行，避免大事务
```

## 四、EXPLAIN 对比演示

```sql
-- 加索引前
EXPLAIN SELECT id, title FROM posts WHERE user_id = 500 AND status = 1;
```

输出 `type=ALL, rows=10000` → 全表扫描。

```sql
-- 加联合索引
ALTER TABLE posts ADD INDEX idx_user_status (user_id, status);

-- 加索引后
EXPLAIN SELECT id, title FROM posts WHERE user_id = 500 AND status = 1;
```

输出 `type=ref, key=idx_user_status, rows=8` → 索引扫描。

## 五、失败场景：索引失效演示

```sql
-- ❌ 类型隐式转换（status 是 TINYINT，传字符串）
EXPLAIN SELECT * FROM posts WHERE status = '1' AND user_id = 500;
-- 在某些 case 下仍能命中，但同样的列上加函数就一定失效：

EXPLAIN SELECT * FROM posts WHERE YEAR(created_at) = 2024;
-- type=ALL，全表扫描

-- ✅ 改成范围
EXPLAIN SELECT * FROM posts WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01';
-- type=range
```

## 提交建议

```bash
git add db/schema.sql db/seed.sql docs/sql-cheatsheet.md
git commit -m "chapter 25: schema + 10K seed data + EXPLAIN benchmarks"
```
