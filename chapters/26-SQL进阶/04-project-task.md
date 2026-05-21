# Chapter 26 SQL 进阶 - 项目任务

## 任务概述

为博客库做一次**完整的慢 SQL 治理 + 索引设计 review**，产出一份可以发给团队 leader 的报告。

## 业务背景

`posts` 已经 100 万行、`comments` 已经 1000 万行，业务方反馈：

- 首页"最新文章"分页第 1000 页打开要 6 秒。
- 后台"按用户筛选评论"经常超时。
- 偶尔出现 `Lock wait timeout` 报错。

你的任务：定位 → 优化 → 写报告 + 死锁治理 + 索引精简。

## 任务拆解

### Step 1：开慢日志，跑一遍真实流量

```sql
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.1;
```

跑 25 章项目里的 8 个核心查询 + 故意构造 3 个慢 SQL（深分页、函数失效、LIKE 前模糊）。

跑完用 `pt-query-digest` 汇总到 `docs/slow-report.txt`，列出 Top 5。

### Step 2：每个 Top 5 慢 SQL 做 EXPLAIN ANALYZE 前后对比

对每条慢 SQL 输出：

| SQL | 原 EXPLAIN | 优化手段 | 新 EXPLAIN | 耗时变化 |
|-----|-----------|---------|-----------|---------|
| SELECT … WHERE YEAR(created_at)=2024 | type=ALL rows=1M | 改成范围 | type=range rows=27K | 4800 ms → 28 ms |

写到 `docs/sql-optimize.md`。

### Step 3：索引精简

```sql
-- 列出当前所有索引
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'blog'
GROUP BY TABLE_NAME, INDEX_NAME;

-- 找重复/冗余索引
pt-duplicate-key-checker --host=localhost --user=root --ask-pass
```

写出至少 1 个"该删的索引"、1 个"该加的索引"，附理由。

### Step 4：死锁复现 + 治理

照 02-demo 第三节复现一次死锁，把 `SHOW ENGINE INNODB STATUS` 的 `LATEST DETECTED DEADLOCK` 段贴到 `docs/deadlock-case.md`。

给出**3 条**预防规则（按 id 升序、缩短事务、加合适索引），每条配一行代码示例。

### Step 5：覆盖索引 + keyset 分页改造

把"首页最新文章分页" API 改成：

```sql
-- 老
SELECT id, title, summary FROM posts WHERE status=1 ORDER BY id DESC LIMIT 100000,10;

-- 新
SELECT id, title, summary FROM posts WHERE status=1 AND id < :lastId ORDER BY id DESC LIMIT 10;
```

加联合索引 `(status, id, title, summary)` 让它走覆盖索引 + keyset。EXPLAIN 应该是 `type=range, Extra=Using index`。

## 交付物

- [ ] `docs/slow-report.txt`：pt-query-digest 汇总
- [ ] `docs/sql-optimize.md`：Top 5 慢 SQL 前后 EXPLAIN + 耗时对比
- [ ] `docs/index-review.md`：当前索引清单 + 增删建议
- [ ] `docs/deadlock-case.md`：死锁日志 + 预防规则
- [ ] `db/migration/V26__optimize_index.sql`：实际执行的 DDL

## 验收清单

| 项 | 标准 |
|----|------|
| Top 5 慢 SQL 全部消失 | 再跑一轮，pt-query-digest 头部换人 |
| 首页分页 P99 | < 50 ms（不论翻到第几页） |
| 死锁可解释 | 报告里写清环路 + 谁被回滚 + 为什么 |
| 索引数 | 单表二级索引 ≤ 6 |
| 没有 Using filesort/temporary | 8 个核心查询 EXPLAIN 通过 |

## 扩展挑战

1. **MVCC 实验**：开 2 个会话，分别在 RC / RR 下跑同样的 `SELECT → 对方 UPDATE → 再 SELECT`，截图证明 RR 看到的是旧值。
2. **Online DDL**：用 `ALGORITHM=INPLACE, LOCK=NONE` 加索引，观察对在线写入的影响（用 `sysbench` 同时压一下）。
3. **物化视图**：把 Top 3 的统计 SQL（如"每用户评论数"）做成定时任务写到 `user_stat` 表，OLTP 不再实时聚合。
4. **EXPLAIN 自动化**：写一段 CI 脚本，遍历 `src/main/resources/mapper/*.xml` 抽出 SQL → 自动 `EXPLAIN` → 出现 `type=ALL` 就让 CI 红。
