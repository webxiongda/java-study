# Chapter 26 SQL 进阶 - 实操 Demo

## Demo 目标

在第 25 章的 `blog` 库上做四件事：

1. 打开**慢查询日志** + `pt-query-digest` 汇总。
2. 用 `EXPLAIN ANALYZE` 对一个真实慢 SQL 做"前 → 后"优化对比（type=ALL → ref，耗时 5s → 30ms）。
3. 复现**死锁**并读 `SHOW ENGINE INNODB STATUS`。
4. 复现 **RR 下不可重复读 vs 防住的幻读**。

## 前置

- Chapter 25 已建 `users / posts / comments` 三张表，已灌 1 万 / 10 万 / 100 万行。
- MySQL 8.x；客户端：`mysql -uroot -p blog`。
- 可选：`brew install percona-toolkit`（安装 `pt-query-digest`）。

## 一、打开慢查询日志

```sql
-- 临时打开（重启失效）
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.1;          -- 0.1s 以上记录
SET GLOBAL log_queries_not_using_indexes = 'ON';
SHOW VARIABLES LIKE 'slow_query_log_file'; -- 看路径
```

跑几个慢 SQL：

```sql
SELECT * FROM posts WHERE YEAR(created_at) = 2024;            -- 函数失效
SELECT * FROM comments WHERE content LIKE '%spam%';           -- 前模糊
SELECT * FROM posts ORDER BY view_count DESC LIMIT 100000,10; -- 深分页
```

用 `pt-query-digest` 汇总：

```bash
pt-query-digest /var/lib/mysql/your-host-slow.log > slow-report.txt
head -80 slow-report.txt
```

期望输出（节选）：

```
# Profile
# Rank Query ID           Response time  Calls R/Call V/M
# ==== ================== ============== ===== ====== =====
#    1 0xA1B2C3D...           42.3s 60%      3 14.10  0.12  SELECT posts
#    2 0xE5F6...               12.1s 17%      5  2.42  0.05  SELECT comments
```

> **结论**：60% 的慢查询时间花在那个 `YEAR(created_at)` 上。

## 二、EXPLAIN ANALYZE 优化对比

### 反面：函数包裹列 → type=ALL

```sql
EXPLAIN ANALYZE
SELECT id, title FROM posts WHERE YEAR(created_at) = 2024;
```

```
-> Filter: (year(posts.created_at) = 2024)  (cost=10245 rows=100000) (actual=4823.5..4823.6 rows=27834 loops=1)
    -> Table scan on posts  (cost=10245 rows=100000) (actual=0.12..3812.4 rows=100000 loops=1)
```

耗时 **4.8 s**，全表扫 10 万行。

### 改写：保持列裸出，用范围

```sql
EXPLAIN ANALYZE
SELECT id, title FROM posts
WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01';
```

```
-> Index range scan on posts using idx_created_at  (cost=8423 rows=27834) (actual=0.18..28.4 rows=27834 loops=1)
```

耗时 **28 ms**——加速 **170 倍**。

### 反面 2：深分页

```sql
-- 慢
EXPLAIN ANALYZE
SELECT id, title, view_count FROM posts
WHERE status = 1 ORDER BY id DESC LIMIT 100000, 10;
-- actual=3520ms  扫了 100010 行
```

### 改写：keyset（书签式）分页

```sql
-- 上一页最后一条的 id = 234567
EXPLAIN ANALYZE
SELECT id, title, view_count FROM posts
WHERE status = 1 AND id < 234567
ORDER BY id DESC LIMIT 10;
-- actual=2ms  扫 10 行
```

> **结论**：深分页用 keyset，避免 `OFFSET` 把前面 10 万行全扫一遍。

## 三、死锁复现

终端 A：

```sql
BEGIN;
UPDATE posts SET view_count = view_count + 1 WHERE id = 1;
-- 不要 commit
```

终端 B：

```sql
BEGIN;
UPDATE posts SET view_count = view_count + 1 WHERE id = 2;
UPDATE posts SET view_count = view_count + 1 WHERE id = 1;  -- 阻塞，等 A
```

终端 A：

```sql
UPDATE posts SET view_count = view_count + 1 WHERE id = 2;
-- ERROR 1213 (40001): Deadlock found when trying to get lock;
--                     try restarting transaction
```

看死锁日志：

```sql
SHOW ENGINE INNODB STATUS\G
-- 搜索 "LATEST DETECTED DEADLOCK"
```

会看到完整的环路 + 哪个事务被回滚（一般 undo 量小的）。

**根因**：A、B 拿锁顺序相反。修法：所有事务**按 id 升序**访问。

## 四、RR 下不可重复读 vs 幻读

### 4.1 RR 防住不可重复读

终端 A：

```sql
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;
SELECT name FROM users WHERE id = 1;   -- Alice
```

终端 B：

```sql
UPDATE users SET name = 'Bob' WHERE id = 1; COMMIT;
```

终端 A：

```sql
SELECT name FROM users WHERE id = 1;   -- 仍然 Alice ✅（Read View 锁死）
COMMIT;
SELECT name FROM users WHERE id = 1;   -- Bob
```

### 4.2 半幻读陷阱（RR 也救不了）

终端 A：

```sql
BEGIN;
SELECT * FROM posts WHERE user_id = 999;   -- 空
```

终端 B：

```sql
INSERT INTO posts(user_id, title, status) VALUES (999, 'New', 1); COMMIT;
```

终端 A：

```sql
SELECT * FROM posts WHERE user_id = 999;        -- 仍然空（快照读）
UPDATE posts SET title='X' WHERE user_id = 999;  -- 影响 1 行！
SELECT * FROM posts WHERE user_id = 999;        -- 突然冒出 1 行（当前读）
```

> **结论**：RR 下"快照读"不能防"当前读"撞到的幻行。要绝对防住，加 `SELECT ... FOR UPDATE`（触发 Next-Key Lock）。

## 五、覆盖索引演示

```sql
ALTER TABLE posts ADD INDEX idx_status_view (status, view_count);

EXPLAIN SELECT id, status, view_count FROM posts
WHERE status = 1 ORDER BY view_count DESC LIMIT 10;
-- Extra: Using index    （不回表）
```

vs

```sql
EXPLAIN SELECT id, title FROM posts
WHERE status = 1 ORDER BY view_count DESC LIMIT 10;
-- Extra: Using index condition; Using filesort（title 不在索引，回表 + 排序）
```

## 六、失败场景：索引选错

```sql
-- 当 status=1 占 80% 时
EXPLAIN SELECT * FROM posts WHERE status = 1 AND user_id = 7;
-- 可能 key=idx_status（区分度低，rows 估很大）
```

修法：

```sql
ANALYZE TABLE posts;                        -- 刷新统计
-- 或强制
SELECT * FROM posts FORCE INDEX(idx_user_id)
WHERE status = 1 AND user_id = 7;
```

## 提交建议

```bash
git add docs/slow-report.txt docs/explain-before-after.md
git commit -m "chapter 26: slow log + EXPLAIN ANALYZE optimization + deadlock demo"
```
