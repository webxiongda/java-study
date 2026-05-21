# Chapter 26 SQL 进阶 - 理论篇

## 一、学习定位

延续第 25 章的"会写"，本章上升到"会调优 + 会避坑"：**事务隔离、锁、索引深度、EXPLAIN、慢 SQL 治理**。这是从"业务开发"到"DBA-friendly 工程师"的分水岭。

- 优先级：L1
- 预计投入：4 小时
- 阶段产出：完成博客库的索引设计 + 1 份慢 SQL 治理报告

## 二、核心概念

### 1. 事务的 ACID 与隔离级别

| 性质 | 含义 | 谁来保证 |
|------|-----|---------|
| Atomicity 原子性 | 全成功或全回滚 | undo log |
| Consistency 一致性 | 约束不被破坏 | 业务 + 约束 |
| Isolation 隔离性 | 并发事务互不打扰 | MVCC + 锁 |
| Durability 持久性 | commit 后断电不丢 | redo log + fsync |

**4 个隔离级别 与 3 个并发问题：**

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|---------|------|----------|------|
| READ UNCOMMITTED | ⚠️可能 | ⚠️ | ⚠️ |
| READ COMMITTED（Oracle 默认） | ✅防 | ⚠️ | ⚠️ |
| **REPEATABLE READ**（MySQL 默认） | ✅ | ✅ | ⚠️ 标准下可能；**InnoDB 用 Next-Key Lock 防住** |
| SERIALIZABLE | ✅ | ✅ | ✅ | （性能差） |

### 2. MVCC：多版本并发控制

InnoDB 用 MVCC 让"读不阻塞写、写不阻塞读"：

- 每行隐藏列：`trx_id`（最近修改事务）、`roll_ptr`（指向 undo log 旧版本）。
- 事务开始时拿一个**Read View**（活跃事务列表快照）。
- 读时根据 Read View 找"对自己可见的版本"。

```
事务 A：BEGIN → SELECT name FROM users WHERE id=1;   -- 看到 "Alice"
事务 B：UPDATE users SET name='Bob' WHERE id=1; COMMIT;
事务 A：SELECT name FROM users WHERE id=1;           -- 仍看到 "Alice"（RR 级别）
```

**关键**：

- RR 级别下，Read View 在**事务第一次 SELECT 时创建**，整个事务复用。
- RC 级别下，**每次 SELECT 都新建 Read View**——所以会出现不可重复读。

### 3. 锁

**按粒度**：

- **行锁**（InnoDB 默认）：只锁命中的行
- **表锁**（MyISAM 默认）：锁整张表
- **Gap 锁**：锁两行之间的"间隙"，防幻读
- **Next-Key Lock**：行锁 + Gap 锁的组合

**按用途**：

| 锁 | 语法 | 用途 |
|----|-----|-----|
| 共享锁 S | `SELECT ... LOCK IN SHARE MODE` | 读时阻止他人改 |
| 排他锁 X | `SELECT ... FOR UPDATE` | 写前占坑 |
| 意向锁 IS/IX | 自动 | 表级标记下面有行级 S/X |
| Record Lock | 自动 | 命中索引的行 |
| Gap Lock | 自动 | 范围扫描时锁间隙 |

**重要规则**：

- 行锁加在**索引**上，不在行本身。
- 没有索引时，行锁退化为表锁。
- `UPDATE` / `DELETE` / `SELECT ... FOR UPDATE` 加 X 锁。
- `SELECT` 普通查询不加锁（走 MVCC 快照读）。

### 4. 死锁

```
事务 A：UPDATE users SET name='x' WHERE id = 1;   -- 锁 id=1
事务 B：UPDATE users SET name='y' WHERE id = 2;   -- 锁 id=2
事务 A：UPDATE users SET name='z' WHERE id = 2;   -- 等 B
事务 B：UPDATE users SET name='w' WHERE id = 1;   -- 等 A
→ InnoDB 检测到环路，回滚其中一个（一般是 undo 量小的）
```

**预防**：

- 所有事务**按相同顺序**访问资源（比如永远按 id 升序）。
- 加索引避免锁升级到全表。
- 减小事务范围（不要在事务里调外部 API）。

### 5. 索引深入

**聚集索引（Clustered Index）**：叶子节点直接存整行数据。InnoDB 用主键作聚集索引。

**二级索引（Secondary Index）**：叶子节点存"主键值"，查询要"回表"。

```
查 user_id=100：
  → 走 idx_user_id（二级索引）找到 主键 PK
  → 拿 PK 回 聚集索引 拿整行
```

**覆盖索引**：查询需要的所有列都在索引里，不用回表。

```sql
CREATE INDEX idx ON users(name);

-- 不用回表（id 是 PK，二级索引叶子自带 PK）
SELECT id, name FROM users WHERE name = 'Alice';
-- EXPLAIN Extra: Using index

-- 要回表
SELECT id, name, email FROM users WHERE name = 'Alice';
-- 因为 email 不在索引里
```

**索引下推（ICP, MySQL 5.6+）**：

```sql
-- 索引 (age, name)
SELECT * FROM users WHERE age = 20 AND name LIKE '%张%';
-- 老版本：走 age=20 拿到一堆行 → 回表 → 在 server 层用 name LIKE 过滤
-- ICP：在索引层就用 name LIKE 过滤掉不匹配的，减少回表次数
-- EXPLAIN Extra: Using index condition
```

### 6. EXPLAIN 深读

```sql
EXPLAIN SELECT u.name, o.amount
FROM users u INNER JOIN orders o ON u.id = o.user_id
WHERE u.status = 1 AND o.created_at > '2024-01-01'
ORDER BY o.amount DESC LIMIT 10;
```

输出列详解：

| 列 | 例值 | 含义 |
|----|-----|-----|
| `id` | 1, 2 | 查询块的序号，越大越早执行 |
| `select_type` | SIMPLE / SUBQUERY / DERIVED | 子查询类型 |
| `table` | u, o | 涉及表 |
| `type` | ref, range, ALL | 访问方法（重要） |
| `possible_keys` | idx_status | 可能用的索引 |
| `key` | idx_user_created | 实际用的 |
| `key_len` | 4 | 用了索引几个字节（看联合索引用到几列） |
| `ref` | const, u.id | 哪个常量/列匹配索引 |
| `rows` | 1000 | 估算扫描行数 |
| `filtered` | 10.0 | 过滤后剩余比例（%） |
| `Extra` | Using where; Using index | 关键信号 |

**type 优劣**（从快到慢）：

```
system > const > eq_ref > ref > range > index > ALL
```

**Extra 红灯**：

- `Using filesort` —— 需要内存/磁盘排序（想办法用索引排序）
- `Using temporary` —— 用了临时表（GROUP BY / UNION 容易触发）
- `Using join buffer (Block Nested Loop)` —— JOIN 没走索引

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | Optimizer 根据统计信息选索引；可用 `FORCE INDEX(idx)` 强制 |
| 配置 | `innodb_buffer_pool_size`、`tmp_table_size`、`sort_buffer_size` |
| 执行 | 索引扫描 → 回表 → 排序 → 分页 |
| 边界 | 大事务 → undo / redo 膨胀；幻读；死锁；统计信息过期 |
| 验证 | `EXPLAIN ANALYZE`（MySQL 8）、`SHOW ENGINE INNODB STATUS`、慢日志 |

## 四、项目使用场景

- **博客分页**：keyset 分页避免深 OFFSET（25 章 Q5 详）。
- **抢购扣库存**：`SELECT ... FOR UPDATE` + 单 SQL 原子更新避免超卖。
- **统计报表**：聚合 → 物化视图或定时任务，别在 OLTP 库上跑。
- **关键 SQL CI**：所有新 SQL 用 `pt-query-digest` 审查、`EXPLAIN` 在 PR 里跑。

## 五、常见问题与坑

| 问题 | 后果 | 处理 |
|---|---|---|
| 长事务 | 阻塞其他写、undo 膨胀 | 拆分事务、不在事务里调外部 API |
| 无索引锁全表 | 大量阻塞 | 任何 UPDATE/DELETE 必须有索引命中 |
| RR 下幻读边界没考虑 | 唯一性约束失效 | 用唯一索引兜底，业务别只依赖 SELECT 后判断 |
| 自增 ID 跳号 | 不影响功能但被业务误解 | 别用 AUTO_INCREMENT 当业务"连续编号" |
| 索引过多 | 写慢 + 占空间 | 单表 ≤ 5-6 个二级索引；定期 `pt-duplicate-key-checker` |
| 大字段 in 索引 | 索引树高 | varchar 长字段用前缀索引（`KEY (col(20))`） |
| Optimizer 选错索引 | 走全表扫描 | `ANALYZE TABLE` 刷新统计；或 `FORCE INDEX` |

## 六、面试高频问题

1. MySQL 默认隔离级别是什么？InnoDB 在这个级别是怎么防住幻读的？
2. MVCC 的工作原理？为什么"读不阻塞写"？
3. 聚集索引和二级索引的区别？什么是回表？
4. 覆盖索引是什么？怎么写 SQL 让它发生？
5. `SELECT ... FOR UPDATE` 加什么锁？没命中索引会怎样？
6. 死锁是怎么产生的？怎么避免？
7. EXPLAIN 里 `type=ALL`、`Extra=Using filesort` 各意味着什么？
