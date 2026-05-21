# Chapter 25 SQL 基础 - 理论篇

## 一、学习定位

后端工程师 80% 的性能问题都在 SQL。本章覆盖 **DDL / DML / DQL / 索引 / 执行计划** 的核心 80%，让你能独立写出**正确且能扫描索引**的查询。

- 优先级：L1 必须掌握
- 预计投入：4 小时
- 阶段产出：博客 5 张核心表 + 至少 10 个查询能用索引

## 二、核心概念

### 1. SQL 的 4 类语句

| 类型 | 全称 | 例子 | 操作对象 |
|------|------|------|---------|
| DDL | Data Definition Language | `CREATE`, `ALTER`, `DROP`, `TRUNCATE` | 表结构 |
| DML | Data Manipulation Language | `INSERT`, `UPDATE`, `DELETE` | 行数据 |
| DQL | Data Query Language | `SELECT` | 查询 |
| DCL | Data Control Language | `GRANT`, `REVOKE` | 权限 |
| TCL | Transaction Control | `BEGIN`, `COMMIT`, `ROLLBACK` | 事务 |

### 2. SELECT 完整结构与执行顺序

**书写顺序**：

```sql
SELECT   column_list
FROM     table_a JOIN table_b ON ...
WHERE    row_filter
GROUP BY group_keys
HAVING   group_filter
ORDER BY sort_keys
LIMIT    page;
```

**逻辑执行顺序**（与书写顺序不同）：

```
1. FROM / JOIN     → 笛卡尔积 + ON 过滤
2. WHERE           → 行过滤（不能用聚合函数）
3. GROUP BY        → 分组
4. HAVING          → 组过滤（可以用聚合函数）
5. SELECT          → 投影、计算表达式
6. DISTINCT        → 去重
7. ORDER BY        → 排序
8. LIMIT           → 取页
```

**关键差异**：

- `WHERE` 在 `GROUP BY` 之前 → 不能 `WHERE COUNT(*) > 5`；要用 `HAVING`。
- `SELECT` 在 `ORDER BY` 之前 → `SELECT name AS n FROM ... ORDER BY n` 是合法的。

### 3. JOIN 的 4 种类型

```sql
-- INNER JOIN：两边都匹配才返回
SELECT u.name, o.amount
FROM users u INNER JOIN orders o ON u.id = o.user_id;

-- LEFT JOIN：左表全保留，右表缺失填 NULL
SELECT u.name, o.amount
FROM users u LEFT JOIN orders o ON u.id = o.user_id;

-- RIGHT JOIN：右表全保留（实战少用，颠倒 LEFT 即可）

-- FULL OUTER JOIN：两边都保留（MySQL 不直接支持，用 UNION 实现）
```

**实战易错**：

```sql
-- ❌ 用 LEFT JOIN 后再在 WHERE 里过滤右表，等同于 INNER JOIN
SELECT u.name FROM users u LEFT JOIN orders o ON u.id = o.user_id
WHERE o.status = 'paid';     -- o.status 为 NULL 的行被滤掉了

-- ✅ 过滤条件放 ON 子句
SELECT u.name FROM users u LEFT JOIN orders o
  ON u.id = o.user_id AND o.status = 'paid';
```

### 4. 索引基础

**B+ 树索引**（MySQL InnoDB 默认）：

```
       [50]
      /    \
    [25]   [75]
   /  |     |  \
 [10 20] [30 40] [60 70] [80 90]   ← 叶子节点双向链表
```

特点：
- 范围查询快（叶子节点链表）
- 等值查询 O(log n)
- 排序友好（顺序遍历叶子）

**单列 vs 联合索引**：

```sql
-- 单列索引
CREATE INDEX idx_email ON users(email);

-- 联合索引（最左前缀原则）
CREATE INDEX idx_status_created ON orders(status, created_at);
-- 能命中：(status), (status, created_at)
-- 不能命中：(created_at), (created_at, status)
```

**索引失效的典型场景**：

```sql
WHERE YEAR(created_at) = 2024     -- ❌ 列上用函数
WHERE name LIKE '%alice%'          -- ❌ 前缀模糊
WHERE id + 1 = 100                 -- ❌ 列上做运算
WHERE phone = 13800138000          -- ❌ 隐式类型转换（phone 是 VARCHAR）

-- 改：
WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01'
WHERE name LIKE 'alice%'
WHERE id = 99
WHERE phone = '13800138000'
```

### 5. 聚合 + 分组

```sql
-- 每个用户的订单总额，按金额倒序，取 Top 10
SELECT user_id, COUNT(*) AS cnt, SUM(amount) AS total
FROM orders
WHERE created_at >= '2024-01-01'
GROUP BY user_id
HAVING total > 1000
ORDER BY total DESC
LIMIT 10;
```

**ONLY_FULL_GROUP_BY 模式**（MySQL 5.7+ 默认）：`SELECT` 里非聚合列必须全部出现在 `GROUP BY` 里，否则报错。这是好事——避免"任意取一行"的歧义。

### 6. EXPLAIN 看执行计划

```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 100 AND status = 'paid';
```

关键列：

| 列 | 含义 | 关注点 |
|----|-----|------|
| `type` | 访问类型 | `system` > `const` > `eq_ref` > `ref` > `range` > `index` > `ALL`；**`ALL` 是全表扫描，要消灭** |
| `key` | 实际用的索引 | NULL 就是没走索引 |
| `rows` | 估算扫描行数 | 越小越好 |
| `Extra` | 额外信息 | `Using filesort` / `Using temporary` 要警惕 |

## 三、工作原理（MySQL InnoDB）

| 维度 | 要点 |
|---|---|
| 入口 | Connector → Parser → Optimizer → Executor → Storage Engine |
| 配置 | `innodb_buffer_pool_size`（缓存数据/索引）= 物理内存 60-80% |
| 执行 | Optimizer 基于统计信息（`ANALYZE TABLE`）选索引 |
| 边界 | 慢查询（`long_query_time`）、死锁（`SHOW ENGINE INNODB STATUS`）、Buffer Pool 命中率 |
| 验证 | `EXPLAIN` + `EXPLAIN ANALYZE`（MySQL 8.0+ 真实执行） |

## 四、项目使用场景

- **第 27 章建模**：本章是工具，27 章是设计——表怎么拆、字段选什么类型。
- **第 28 章 MyBatis**：动态 SQL、`<foreach>` 批量、`<choose>` 条件分支。
- **第 33-36 章 API**：分页查询用 `LIMIT offset, size`，但深分页要改 keyset。
- **第 58 章性能**：慢查询日志 + EXPLAIN + 索引优化是标配套路。

## 五、常见问题与坑

| 问题 | 后果 | 处理方式 |
|---|---|---|
| 列上加函数 | 索引失效 | 把函数移到右边 |
| `SELECT *` | 覆盖索引失效、网络浪费 | 明确列表 |
| 深分页 `LIMIT 100000, 10` | 扫描 10 万 + 10 行 | 改 keyset：`WHERE id > last_id LIMIT 10` |
| 大表 `COUNT(*)` | 全表扫描 | 维护计数表 / 用估算（`information_schema.tables.table_rows`） |
| 隐式类型转换 | 索引失效 + 偶发 bug | 类型严格对齐 |
| `IS NULL` / `!=` | 一般索引失效 | 改成 `WHERE col = '' OR col IS NULL` 拆开；或用 `NOT EXISTS` |
| 联合索引顺序错 | 用不上索引 | 选择性高的列在前；最左前缀 |
| 没事务又改了数据 | 部分成功状态 | 用事务 + 唯一约束 + UPSERT |

## 六、面试高频问题

1. SQL 的逻辑执行顺序是什么？为什么 `WHERE` 里不能用聚合函数？
2. `INNER JOIN` 和 `LEFT JOIN` 的区别？为什么在 LEFT JOIN 后的 WHERE 里写右表条件会变成 INNER JOIN？
3. B+ 树相比 B 树 / 红黑树为什么更适合数据库索引？
4. 联合索引的"最左前缀"是什么意思？`(a,b,c)` 能命中哪些查询？
5. 索引失效的常见原因有哪些？
6. `EXPLAIN` 里的 `type=ALL` 和 `Using filesort` 各是什么含义？
7. 深分页为什么慢？怎么优化？
