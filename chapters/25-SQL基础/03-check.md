# Chapter 25 SQL 基础 - 自测题

## Q1（概念）：SQL 的逻辑执行顺序是什么？为什么 `WHERE COUNT(*) > 5` 会报错？

**参考答案：**

**逻辑执行顺序（与书写顺序不同）：**

```
1. FROM / JOIN     ← 笛卡尔积 + ON 过滤
2. WHERE           ← 行级过滤
3. GROUP BY        ← 分组
4. HAVING          ← 组级过滤
5. SELECT          ← 投影 / 计算列 / 聚合
6. DISTINCT
7. ORDER BY
8. LIMIT
```

`COUNT(*)`、`SUM(...)`、`AVG(...)` 这些**聚合函数**是在 `GROUP BY` 之后、`SELECT` 阶段才计算的。而 `WHERE` 在 `GROUP BY` 之前——这时**还没有"组"**，自然不能用聚合函数。

```sql
-- ❌ 报错：Invalid use of group function
SELECT user_id, COUNT(*) FROM orders
WHERE COUNT(*) > 5
GROUP BY user_id;

-- ✅ 用 HAVING（组级过滤）
SELECT user_id, COUNT(*) AS cnt FROM orders
GROUP BY user_id
HAVING cnt > 5;
```

**WHERE vs HAVING 选择规则**：

| 条件 | 用哪个 |
|------|------|
| 过滤原始行 | WHERE |
| 过滤聚合后的组 | HAVING |
| 都可以的情况 | **优先 WHERE**（先过滤再分组，扫描数据少） |

```sql
-- 都对，但下面更快
SELECT user_id, COUNT(*) FROM orders
WHERE status = 'paid'         -- ✅ 先过滤掉不付款的行
GROUP BY user_id;

-- 慢：把所有行都分组完，再丢掉
SELECT user_id, COUNT(*) FROM orders
GROUP BY user_id
HAVING SUM(IF(status='paid',1,0)) = COUNT(*);
```

**另一个易错点**：

```sql
-- ✅ 这是合法的，虽然 ORDER BY 在 SELECT 之后，但能用别名
SELECT name AS n FROM users ORDER BY n;

-- ❌ WHERE 不能用别名（WHERE 在 SELECT 之前）
SELECT name AS n FROM users WHERE n = 'Alice';   -- 报错
SELECT name AS n FROM users WHERE name = 'Alice'; -- 改成原列名
```

---

## Q2（概念）：什么是"最左前缀原则"？联合索引 `(a, b, c)` 在以下查询中能否命中？

```sql
1. WHERE a = 1
2. WHERE a = 1 AND b = 2
3. WHERE a = 1 AND b = 2 AND c = 3
4. WHERE a = 1 AND c = 3
5. WHERE b = 2
6. WHERE a = 1 AND b > 2 AND c = 3
7. WHERE a = 1 ORDER BY b
8. ORDER BY a, b, c
```

**参考答案：**

**最左前缀原则**：B+ 树联合索引按 `(a, b, c)` 顺序拼接 key 排序。要用索引，**必须从最左列开始连续匹配**——一旦跳过中间列或最左列缺失，后面的列就用不上索引。

| # | 查询 | 命中情况 | 说明 |
|---|------|---------|------|
| 1 | `a = 1` | ✅ 全部用上 | 最左列 |
| 2 | `a = 1 AND b = 2` | ✅ 全部用上 | 连续 |
| 3 | `a = 1 AND b = 2 AND c = 3` | ✅ 全部用上 | 最理想 |
| 4 | `a = 1 AND c = 3` | ⚠️ 只用上 `a` | b 缺失，c 用不上索引但能"索引下推"（5.6+）做过滤 |
| 5 | `b = 2` | ❌ 不命中 | 最左列 a 缺失 |
| 6 | `a = 1 AND b > 2 AND c = 3` | ⚠️ 用上 `a, b`，**c 用不上** | **范围查询后的列不能再走索引** |
| 7 | `a = 1 ORDER BY b` | ✅ 命中且免排序 | a 等值 + b 顺序刚好和索引一致 |
| 8 | `ORDER BY a, b, c` | ✅ 命中且免排序 | 与索引顺序完全一致，`Extra` 无 `Using filesort` |

**关键规则补充**：

- 等值查询的列顺序**可以与索引顺序不同**（优化器会重排）：`WHERE b = 2 AND a = 1` 等价于 `WHERE a = 1 AND b = 2`，都能命中。
- **范围查询断索引**：第 6 题里 `b > 2` 是范围，c 就用不上索引（B+ 树的特性：b 的范围内 c 不再有序）。
- `IN` 视为等值，`>`/`</`BETWEEN/LIKE 'x%'` 视为范围。

**实战建议**：

```sql
-- 业务高频查询
SELECT * FROM orders WHERE user_id = ? AND status = ? AND created_at > ?;

-- ✅ 正确联合索引（范围列放最后）
CREATE INDEX idx ON orders(user_id, status, created_at);

-- ❌ 错误顺序：范围列放中间，status 用不上索引排序
CREATE INDEX idx ON orders(user_id, created_at, status);
```

**口诀**：**等值在前 → 排序中间 → 范围最后**。

---

## Q3（实操）：以下 5 个查询都很慢，给出诊断结论和优化方案

```sql
-- 表结构：orders(id, user_id, status, amount, created_at) 1000 万行
-- 现有索引：PRIMARY KEY(id), INDEX idx_user(user_id)

-- 查询 1
SELECT * FROM orders WHERE YEAR(created_at) = 2024 AND status = 'paid';

-- 查询 2
SELECT * FROM orders WHERE status = 'paid' ORDER BY created_at DESC LIMIT 100000, 10;

-- 查询 3
SELECT COUNT(*) FROM orders WHERE user_id IN (SELECT id FROM users WHERE status = 1);

-- 查询 4
SELECT * FROM orders WHERE user_id = 123 AND status != 'cancelled' ORDER BY created_at DESC;

-- 查询 5
SELECT * FROM orders WHERE user_id LIKE '%123%';
```

**参考答案：**

### 查询 1：列上加函数 → 索引失效

```sql
-- ❌ YEAR(created_at) 让 created_at 列上没法走索引
-- 优化：改成范围查询
SELECT * FROM orders
WHERE created_at >= '2024-01-01'
  AND created_at <  '2025-01-01'
  AND status = 'paid';

-- 配套索引（status 选择性低，放后面）
CREATE INDEX idx_created_status ON orders(created_at, status);
```

### 查询 2：深分页 `LIMIT 100000, 10`

MySQL 实际要扫描 100010 行，丢掉前 10 万行，只返回 10 行——浪费极大。

```sql
-- ✅ 方案 A：keyset 分页（用上次最后一行的 id / created_at）
SELECT * FROM orders
WHERE status = 'paid' AND created_at < ?      -- 上一页最后一条的 created_at
ORDER BY created_at DESC LIMIT 10;

-- ✅ 方案 B：延迟关联（先查 id 再回表）
SELECT o.* FROM orders o
INNER JOIN (
    SELECT id FROM orders
    WHERE status = 'paid'
    ORDER BY created_at DESC
    LIMIT 100000, 10
) t ON o.id = t.id;
-- 子查询走覆盖索引 (status, created_at, id)，只读 id，比直接 SELECT * 快 N 倍
```

### 查询 3：`IN (子查询)` 在老版本 MySQL 会变 N+1

```sql
-- ✅ 改成 JOIN（MySQL 8.0 优化器一般会自动改写，但 5.7 不一定）
SELECT COUNT(*) FROM orders o
INNER JOIN users u ON o.user_id = u.id
WHERE u.status = 1;

-- 或 EXISTS（语义清晰且高效）
SELECT COUNT(*) FROM orders o
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id AND u.status = 1);
```

确保 `users.id` 是 PK（已有），`orders.user_id` 有索引（已有 `idx_user`），`users.status` 加索引（如果 status=1 的占比 < 30%）。

### 查询 4：`!=` + ORDER BY 用不上索引排序

```sql
-- 问题：!=  让 status 索引失效；ORDER BY created_at 又会 filesort
-- ✅ 联合索引把 user_id 放前，created_at 放后（同 order）
CREATE INDEX idx_user_created ON orders(user_id, created_at);

-- 改写查询：把 != 转成 IN 已知非 cancelled 的状态集合
SELECT * FROM orders
WHERE user_id = 123 AND status IN ('paid', 'pending', 'refunded')
ORDER BY created_at DESC
LIMIT 50;
```

这样：`user_id = 123` 命中索引第一列，`ORDER BY created_at DESC` 直接利用索引顺序，**无需 filesort**。

### 查询 5：前缀通配的 LIKE

```sql
-- ❌ LIKE '%123%' 前面带 % → 索引完全失效，全表扫描
-- 这种"模糊匹配数字 ID"通常是误用：user_id 是数字，应该用等值

-- 如果真的要做"包含某字符串"，需要全文索引或 ES
ALTER TABLE orders ADD FULLTEXT INDEX ft_user (user_id);
SELECT * FROM orders WHERE MATCH(user_id) AGAINST('123');

-- 但更合理是把这种 free-text 查询挪到 Elasticsearch
```

**通用排查流程：**

1. `EXPLAIN` 看 `type`、`key`、`rows`。
2. `EXPLAIN ANALYZE`（MySQL 8）看真实耗时。
3. 看 `Extra`：`Using filesort` 想办法用索引排序；`Using temporary` 想办法避免大 GROUP BY。
4. `SHOW WARNINGS` 查询后看优化器是否做了"隐式类型转换"等改写。

---

## Q4（实操）：用 SQL 求"每个用户最近 3 篇文章"，要求一次查询返回，不能 N+1

**参考答案：**

**朴素 N+1 写法（❌ 不能用）：**

```java
// 先查所有用户，再循环查文章 → 1000 用户 = 1001 次查询
List<User> users = userMapper.findAll();
for (User u : users) {
    u.setRecentPosts(postMapper.findTop3ByUserId(u.getId()));
}
```

**SQL 方案 A：窗口函数（MySQL 8+，推荐）**

```sql
SELECT id, user_id, title, created_at
FROM (
    SELECT id, user_id, title, created_at,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
    FROM posts
    WHERE status = 1
) t
WHERE rn <= 3
ORDER BY user_id, created_at DESC;
```

**讲解**：

- `ROW_NUMBER()` 给每条记录在其所属"用户分组"内分配一个序号（1, 2, 3, ...）。
- `PARTITION BY user_id` = 分组键（不同用户独立编号）。
- `ORDER BY created_at DESC` = 决定哪个是 1（最新的）。
- 外层 `WHERE rn <= 3` 取每组前 3 条。

**SQL 方案 B：自连接（兼容 MySQL 5.7）**

```sql
SELECT p.*
FROM posts p
WHERE p.status = 1
  AND (
    SELECT COUNT(*) FROM posts p2
    WHERE p2.user_id = p.user_id
      AND p2.status = 1
      AND p2.created_at >= p.created_at
  ) <= 3
ORDER BY p.user_id, p.created_at DESC;
```

**讲解**：对每条 post，统计"同用户中比我新或同时间的有几篇"——如果 ≤ 3，就是 Top 3。**复杂度 O(N²)**，仅适合小数据。

**SQL 方案 C：变量模拟（MySQL 5.7 大数据量用）**

```sql
SET @rn := 0, @prev := NULL;
SELECT id, user_id, title, created_at FROM (
    SELECT id, user_id, title, created_at,
           @rn := IF(@prev = user_id, @rn + 1, 1) AS rn,
           @prev := user_id
    FROM (SELECT * FROM posts WHERE status = 1 ORDER BY user_id, created_at DESC) sorted
) t
WHERE rn <= 3;
```

**索引推荐**：

```sql
CREATE INDEX idx_user_status_created ON posts(user_id, status, created_at);
```

**Java 端组装：**

```java
@Mapper
public interface PostMapper {
    @Select("""
        SELECT id, user_id, title, created_at FROM (
            SELECT id, user_id, title, created_at,
                   ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
            FROM posts WHERE status = 1
        ) t WHERE rn <= 3
    """)
    List<Post> findTop3PerUser();
}

// Service：用 Stream 按 user_id 分组
Map<Long, List<Post>> byUser = postMapper.findTop3PerUser()
    .stream()
    .collect(Collectors.groupingBy(Post::getUserId));
```

**总结**：消灭 N+1 的核心思路——**让 SQL 一次返回所有需要的数据，Java 端用 `Collectors.groupingBy` 在内存里组装**。这种"批量化"思维是后端性能优化的核心。

---

## Q5（综合）：设计博客分页 API 的 SQL 和索引

**题目**：

需求：`GET /api/posts?page=N&size=20&user_id=X&keyword=Y&sort=hot|new`

- `user_id`、`keyword` 可选
- `sort=hot`：按 `view_count DESC`
- `sort=new`：按 `created_at DESC`
- 数据量：1 亿行
- 要求：p99 < 100ms，深分页（N=10000）也不能超时

请给出：
1. 索引设计
2. 不同条件组合的 SQL（4 种典型）
3. 深分页优化策略
4. 性能指标 + 监控点

**参考答案：**

### 1. 索引设计

```sql
-- 主键：自带聚集索引
-- 加 4 个二级索引覆盖典型查询
CREATE INDEX idx_status_created   ON posts(status, created_at);
CREATE INDEX idx_status_view      ON posts(status, view_count);
CREATE INDEX idx_user_status_time ON posts(user_id, status, created_at);
CREATE INDEX idx_user_status_view ON posts(user_id, status, view_count);

-- 关键词搜索另外走 Elasticsearch，不在 MySQL 上做 LIKE 全文
```

**为什么 status 放第一位**：业务上几乎所有列表都过滤 `status=1`（已发布）。

### 2. 4 种典型查询

#### A. 无 user_id，按时间排序

```sql
SELECT id, title, created_at, view_count
FROM posts
WHERE status = 1
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
-- 走 idx_status_created
```

#### B. 按 user_id + 时间

```sql
SELECT id, title, created_at, view_count
FROM posts
WHERE user_id = 123 AND status = 1
ORDER BY created_at DESC
LIMIT 20;
-- 走 idx_user_status_time，等值 + 索引排序
```

#### C. 按 user_id + 热度

```sql
SELECT id, title, created_at, view_count
FROM posts
WHERE user_id = 123 AND status = 1
ORDER BY view_count DESC
LIMIT 20;
-- 走 idx_user_status_view
```

#### D. 关键词搜索（走 ES，不走 MySQL）

```
ES 返回匹配的 post_id 列表 → 再用 SQL `WHERE id IN (...)` 回表拿详情
```

### 3. 深分页优化

#### 方案 1：keyset 分页（最优）

```sql
-- 前端传上一页最后一条的 created_at 和 id（id 用于同时间戳去重）
SELECT id, title, created_at FROM posts
WHERE status = 1
  AND (created_at, id) < (?, ?)        -- 行值比较
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

无论翻到多少页，每次只扫 20 行。

#### 方案 2：限制最大 page

```java
if (page * size > 1000) {
    throw new IllegalArgumentException("仅支持前 1000 页，请用搜索或筛选缩小范围");
}
```

业务上 99% 用户不会翻到 50 页之后。

#### 方案 3：延迟关联（兼容旧前端，不能改 API 时用）

```sql
SELECT p.* FROM posts p INNER JOIN (
    SELECT id FROM posts WHERE status = 1
    ORDER BY created_at DESC LIMIT 100000, 20
) t ON p.id = t.id;
```

子查询走覆盖索引只读 id（很轻），最后回表一次拿 20 条详情。

### 4. 性能指标 + 监控

| 指标 | 目标 | 监控方式 |
|------|-----|---------|
| p99 延迟 | < 100 ms | Spring Boot Actuator `http.server.requests` |
| QPS | > 1000 | 压测 + Prometheus |
| 慢查询比例 | < 0.1% | MySQL `slow_query_log` + Grafana |
| 索引命中率 | > 99% | `SHOW GLOBAL STATUS LIKE 'Handler_read_%'` |
| 连接池 active | < 80% maxPool | Hikari metrics |
| Buffer Pool 命中 | > 99% | `SHOW ENGINE INNODB STATUS` |

### 5. 兜底策略

- **缓存热门页**：第 1-5 页放 Redis（TTL 60s），命中率通常 > 50%。
- **限流**：用 Bucket4j 控制单用户翻页频率，防爬虫扫深分页。
- **降级**：DB 慢时直接返回缓存中的"近期 100 篇"，标记 `cached=true`。

**面试讲法**：

> "做这种列表 API，我不会一开始就上分库分表。先 (1) 建对的联合索引、(2) `EXPLAIN` 跑一遍核心 SQL 确认 type=ref 不出现 filesort、(3) 用 keyset 替代深 OFFSET、(4) 加 Redis 缓存热门页。这套打完之后单 MySQL 实例能扛 1 亿数据 + 1000 QPS。再扛不住才考虑分表。**先优化 SQL，最后才动架构。**"
