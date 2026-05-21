# Chapter 25 SQL 基础 - 项目任务

## 任务概述

为博客项目设计 5 张核心表 + 灌测试数据 + 用 EXPLAIN 验证 8 个关键查询走索引。

## 任务拆解

### Step 1：表结构

至少 5 张表：`users`、`posts`、`tags`、`post_tags`（多对多）、`comments`。每张表都要：
- 主键 BIGINT AUTO_INCREMENT
- `created_at` / `updated_at` 双时间戳
- 有合适的 UNIQUE 约束（如 email、tag.name）

### Step 2：灌数据

至少：1 万用户、10 万 post、100 万 comment、500 tag。可用 SQL 循环 / mysqlslap / Python Faker。

### Step 3：8 个核心查询

要支撑的业务：

1. 用户登录：`WHERE email = ?`
2. 发布列表分页：`WHERE status = 1 ORDER BY created_at DESC LIMIT ...`
3. 用户主页文章列表：`WHERE user_id = ? AND status = 1 ORDER BY created_at DESC`
4. 热门文章：`WHERE status = 1 ORDER BY view_count DESC LIMIT 10`
5. 按 tag 检索：`WHERE EXISTS (SELECT 1 FROM post_tags ...)`
6. 用户评论数：`SELECT user_id, COUNT(*) FROM comments GROUP BY user_id`
7. 我的文章下的所有评论：`WHERE post_id IN (...)`
8. 最近一周新增数：`WHERE created_at >= NOW() - INTERVAL 7 DAY`

每个查询都：
- 写出对应索引
- `EXPLAIN` 截图 / 文本贴上
- 标注 `type / key / rows / Extra`

### Step 4：性能对比

随便挑 3 个查询：
- 不加索引跑一次（DROP INDEX），记录耗时。
- 加索引再跑一次。
- 写成 markdown 表格放 `docs/sql-perf.md`。

### Step 5：写一条"反面案例"

故意写一个明知会全表扫描的查询（如 `WHERE YEAR(created_at) = 2024`），EXPLAIN 出来 `type=ALL`，然后给出改写方案。

## 交付物

- [ ] `db/schema.sql`：5 张表 + 所有索引
- [ ] `db/seed.sql` 或 `scripts/seed.py`：灌至少 100 万行
- [ ] `docs/sql-cheatsheet.md`：8 个查询 + EXPLAIN 输出
- [ ] `docs/sql-perf.md`：3 组前后对比 + 提速倍数
- [ ] 1 个反面案例 + 改写

## 验收清单

| 项 | 标准 |
|----|------|
| 表结构合理 | 主键、外键约束 / 索引、NOT NULL、UNIQUE 完整 |
| 灌数据真实 | 用户分布有长尾、时间跨度 ≥ 1 年 |
| 查询命中索引 | 8 个核心查询 `type` 全部 ≥ `ref`，无 `ALL` |
| 无 filesort | ORDER BY 列必须能利用索引顺序 |
| 字段类型严格 | 没有"varchar 存 ID"这类隐式转换坑 |

## 扩展挑战

1. **慢查询日志**：开 `slow_query_log` + `long_query_time=0.1`，故意跑一个慢 SQL，看日志格式，再用 `pt-query-digest` 汇总。
2. **覆盖索引演示**：选一个查询，把 `SELECT *` 改成 `SELECT id, title`（都在索引里），EXPLAIN `Extra` 列出现 `Using index` → 不用回表，更快。
3. **explain analyze**：MySQL 8 的 `EXPLAIN ANALYZE` 显示真实执行时间和迭代器树，跟普通 EXPLAIN 对比写在文档里。
