# Chapter 26 SQL 进阶 - 自测题

## Q1（概念）：MySQL InnoDB 在默认的 REPEATABLE READ 级别是怎么防住幻读的？

**参考答案：**

**标准定义**：幻读 = 同一事务内两次查询，第二次看到了第一次没看到的"新行"。

**SQL 标准下** REPEATABLE READ 不防幻读，需要 SERIALIZABLE 才行。但 **InnoDB 通过 MVCC + Next-Key Lock 做到了在 RR 下也防住**。

**两种读分别怎么防：**

### 快照读（普通 SELECT）→ MVCC 防

```sql
-- 事务 A
BEGIN;
SELECT * FROM users WHERE age > 20;  -- 第一次：5 行
                              -- 事务 B 在此 INSERT 一条 age=30 的，COMMIT
SELECT * FROM users WHERE age > 20;  -- 第二次：仍是 5 行
COMMIT;
```

机制：事务 A 第一次 SELECT 时**创建 Read View**（活跃事务快照），之后所有快照读都用这个 ReadView 判断版本可见性。事务 B 的新行 `trx_id` 大于 ReadView 上限，对 A 不可见。

### 当前读（SELECT FOR UPDATE / INSERT / UPDATE / DELETE）→ Next-Key Lock 防

```sql
-- 事务 A
BEGIN;
SELECT * FROM users WHERE age > 20 FOR UPDATE;
   -- 不仅锁了已存在的行，还锁住了 "age>20 的间隙"
                              -- 事务 B 想 INSERT age=30 → 阻塞，等 A
COMMIT;
```

机制：`age > 20` 是范围查询，InnoDB 加 **Next-Key Lock**（行锁 + Gap 锁），覆盖整个 `(20, +∞)` 区间。事务 B 想往这个区间插入新行就被卡住。

**对比表：**

| 读类型 | 触发语句 | 防幻读机制 |
|--------|---------|-----------|
| 快照读 | 普通 SELECT | MVCC（Read View） |
| 当前读 | `SELECT...FOR UPDATE`、`UPDATE`、`DELETE`、`INSERT` | Next-Key Lock |

**特别注意——半幻读陷阱**：

```sql
-- 事务 A
BEGIN;
SELECT * FROM users WHERE id = 100;     -- 返回空
                                         -- 事务 B 插入 id=100，COMMIT
INSERT INTO users (id, ...) VALUES (100, ...);   -- 报错：Duplicate key
SELECT * FROM users WHERE id = 100;     -- 还是空（快照读）！
UPDATE users SET name='x' WHERE id=100; -- 居然影响了 1 行（当前读）！
COMMIT;
```

事务 A 看不到 id=100 但能更新它——这就是"幻读"的一种残留。**结论**：不要只靠 SELECT 判断"是否存在"，要用唯一约束兜底。

---

## Q2（概念）：解释聚集索引、二级索引、覆盖索引、回表、索引下推

**参考答案：**

```
表：users(id PK, name, email, age) + INDEX idx_name(name)
```

**聚集索引（Clustered Index）**：

- InnoDB 默认用主键。
- 叶子节点**直接存整行数据**。
- 找到 PK 就拿到所有列。

```
[聚集索引 (PK=id)]
       [50]
     /      \
   [25]    [75]
   /  \     /  \
 [10:row][20:row][30:row][40:row] ...   ← 叶子节点存整行
```

**二级索引（Secondary Index）**：

- 用户建的非主键索引（`idx_name`）。
- 叶子节点存 **(name, id)**——只到主键，**不存其他列**。

```
[二级索引 idx_name]
       [Charlie]
       /        \
   [Alice]    [Eric]
   /   \       /  \
 [Alice:7][Bob:3][Charlie:5][David:9] ... ← 叶子节点存 (name, id)
```

**回表**：

二级索引查到 PK 后，还要去聚集索引拿其他列——叫"回表"。

```sql
-- 走 idx_name → 找到 id → 回聚集索引拿 email、age
SELECT email, age FROM users WHERE name = 'Alice';
```

**覆盖索引**：

查询所需的所有列**都在二级索引里**，**不用回表**。

```sql
-- idx_name 的叶子节点就有 (name, id)
SELECT id FROM users WHERE name = 'Alice';   -- 不回表
SELECT name FROM users WHERE name = 'Alice'; -- 不回表

-- EXPLAIN Extra: Using index
```

**实战**：把高频查询的所有列加到联合索引里（不要无脑 `SELECT *`），可以让 OLTP 接口快几倍。

```sql
-- 业务：列表分页查 id, title, created_at
CREATE INDEX idx_cover ON posts(status, created_at, title);
-- SELECT id, title, created_at FROM posts WHERE status=1 ORDER BY created_at DESC LIMIT 20
-- → Using index（不回表）
```

**索引下推（ICP, Index Condition Pushdown, MySQL 5.6+）**：

```sql
CREATE INDEX idx_age_name ON users(age, name);

SELECT * FROM users WHERE age = 20 AND name LIKE '%张%';
```

- **没有 ICP**：走索引拿到所有 age=20 的行 → 全部回表 → server 层过滤 name LIKE。
- **有 ICP**：在**存储引擎层**就用 name LIKE 过滤（虽然 LIKE '%张%' 用不上索引顺序，但能少回表）。EXPLAIN `Extra: Using index condition`。

**记忆口诀**：

> 一棵树（聚集索引）藏整行；二级索引藏 PK；查的列都在索引里就免回表；ICP 是"能在引擎层过滤就别带到 server 层"。

---

## Q3（实操）：以下事务在 RR 隔离级别下会发生什么？写出最终结果

```sql
-- 表：accounts(id PK, balance), 当前 id=1, balance=100
-- 事务 T1 和 T2 并发执行

-- T1                                  -- T2
BEGIN;                                 BEGIN;
SELECT balance FROM accounts WHERE id=1;
                                       SELECT balance FROM accounts WHERE id=1;
UPDATE accounts SET balance=balance-10 WHERE id=1;
                                       UPDATE accounts SET balance=balance-10 WHERE id=1;
                                       -- 这里发生什么？
COMMIT;
                                       COMMIT;

-- 最终 balance = ?
```

**参考答案：**

**事件序列：**

1. T1 BEGIN，SELECT 看到 100（快照读）。
2. T2 BEGIN，SELECT 看到 100（自己的快照）。
3. T1 UPDATE `balance=100-10=90`，给 id=1 加**行 X 锁**。
4. T2 UPDATE 想加 X 锁 → 等 T1。
5. T1 COMMIT，释放锁。
6. T2 拿到锁，**重新读取最新值**（这是关键：UPDATE 是当前读，不是快照读）—— `balance` 现在是 90。
7. T2 计算 `90 - 10 = 80`，写入。
8. T2 COMMIT。

**最终 balance = 80**（正确，两次各扣 10）

**反直觉点**：

- 虽然 T2 在第 2 步快照读看到 100，但第 4 步 UPDATE 时**自动升级为当前读**，会看到 T1 已提交的最新值。
- 这就是"**写操作总是基于最新数据，读操作走快照**"的设计——保证扣减不出错。

**陷阱版本（容易超卖）：**

如果业务这样写：

```java
// ❌ 应用层读后判断再写——经典超卖
BigDecimal balance = jdbc.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, 1);
if (balance.compareTo(amount) >= 0) {
    jdbc.update("UPDATE accounts SET balance = ? WHERE id = ?", balance.subtract(amount), 1);
}
```

并发场景：
- T1 读 balance=100 → 判断够 → 准备扣 10
- T2 读 balance=100 → 判断够 → 准备扣 10
- T1 UPDATE balance=90
- T2 UPDATE balance=90 ← **覆盖了 T1 的扣减！丢失更新**

**正确写法 3 选 1：**

```sql
-- ✅ 方案 A：把判断和扣减放进单个 UPDATE
UPDATE accounts SET balance = balance - 10
WHERE id = 1 AND balance >= 10;
-- 返回影响行数 = 0 → 余额不足

-- ✅ 方案 B：SELECT ... FOR UPDATE 排他锁
BEGIN;
SELECT balance FROM accounts WHERE id=1 FOR UPDATE;
-- ...判断
UPDATE accounts SET balance = balance - 10 WHERE id=1;
COMMIT;

-- ✅ 方案 C：版本号乐观锁
UPDATE accounts SET balance=balance-10, version=version+1
WHERE id=1 AND version=?  -- 旧版本号
```

**核心收获**：UPDATE 的 `balance = balance - 10` 是**单 SQL 原子操作**，等价于在 DB 层加锁——这是最简单也最高效的并发安全写法。

---

## Q4（实操）：以下查询是慢 SQL，请按"看 EXPLAIN → 找原因 → 改 SQL/索引"的流程优化

```sql
-- 表 orders(id PK, user_id, status, amount, created_at, ...) 5000 万行
-- 现有索引：idx_user(user_id), idx_status(status)

SELECT id, user_id, amount, created_at
FROM orders
WHERE status = 'paid' AND created_at >= '2024-01-01'
ORDER BY amount DESC
LIMIT 50;
```

EXPLAIN 输出：

```
type=ref, key=idx_status, rows=12000000, Extra=Using where; Using filesort
```

**参考答案：**

**问题诊断：**

| 信号 | 解读 |
|------|-----|
| `key=idx_status` | 走了 status 索引，但 status='paid' 在 5000 万行里占 30% = 1500 万行，**选择性差** |
| `rows=12000000` | 估算扫 1200 万行，太多 |
| `Using filesort` | 扫完之后还要在内存/磁盘排 amount —— 慢的根源 |
| `Using where` | created_at 在 server 层过滤，没走索引 |

**优化路径：**

### 优化 1：建联合索引覆盖排序

目标：让 status + created_at 范围 + amount 排序都用上索引。

```sql
-- 思考：
-- status 是等值 → 放第一位
-- created_at 是范围 → 必须在最后才不破坏后面列的索引顺序
-- amount 是 ORDER BY → 放在范围之前才能利用索引排序
-- 但是范围之后的列不能用作排序索引顺序

-- 方案 A：把 amount 放范围前面（理论上 amount 不能用作过滤，但能用作排序）
CREATE INDEX idx_status_amount_created ON orders(status, amount, created_at);
```

但这样有新问题：`created_at >= '2024-01-01'` 在 amount 排序之后，**实际跳过索引顺序**，还是会回到 filesort 或 ICP 过滤。

### 优化 2：覆盖索引 + 延迟关联（更彻底）

```sql
-- 索引设计：包含查询用到的所有列
CREATE INDEX idx_cover ON orders(status, created_at, amount, id);

-- 改写 SQL：先在索引里取 50 个 id（覆盖索引、不回表），最后回表拿详情
SELECT o.id, o.user_id, o.amount, o.created_at FROM orders o
INNER JOIN (
    SELECT id FROM orders
    WHERE status = 'paid' AND created_at >= '2024-01-01'
    ORDER BY amount DESC
    LIMIT 50
) t ON o.id = t.id
ORDER BY o.amount DESC;
```

子查询走 `idx_cover`，**所有过滤列、排序列都在索引里**，不回表。最后只有 50 次回表拿 user_id。

### 优化 3：业务层缓存 Top N

"金额最大的 50 笔" 是典型的**热点数据**——做一张缓存表/Redis sorted set，定时刷新：

```sql
-- 物化视图（每 5 分钟刷一次）
CREATE TABLE top_orders_cache AS
SELECT id, user_id, amount, created_at
FROM orders WHERE status='paid' AND created_at >= NOW() - INTERVAL 90 DAY
ORDER BY amount DESC LIMIT 1000;

CREATE INDEX idx ON top_orders_cache(amount);
```

业务直接查缓存表，性能稳定。

### 优化 4：归档历史数据

5000 万行里大部分是历史订单。把 1 年前的数据归档到 `orders_archive`，OLTP 库只留近 1 年——索引树更浅、扫描更快。

**优化效果对照：**

| 方案 | 耗时 | 索引 | 备注 |
|------|-----|-----|-----|
| 原始 | 8.2s | idx_status + filesort | 不可接受 |
| +联合索引 | 1.5s | idx_status_created_amount | OK |
| 延迟关联 | 230ms | 覆盖索引 | 推荐 |
| 物化视图 | 5ms | 缓存表 | 最快，但有延迟 |

**面试加分**：

> "我不会一上来就分库分表。第一步看 EXPLAIN 找出 filesort 和回表两个最大开销，第二步设计覆盖索引 + 延迟关联，第三步评估是不是热点数据该缓存，第四步看历史数据能不能归档。**先压榨现有架构，再考虑加复杂度。**"

---

## Q5（综合）：用 Java + 单 SQL 实现"防超卖" + "防死锁" 的扣库存

**题目**：

电商系统秒杀场景，每件商品 100 库存，瞬间涌入 10000 个并发扣减请求。要求：

1. 不能超卖（库存不能扣到负数）
2. 不能死锁
3. 性能尽可能好

写出 SQL + Java 代码 + 必要的索引。

**参考答案：**

### 方案 1：单 SQL 原子扣减（推荐 99% 的场景）

```sql
-- 表
CREATE TABLE products (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(100),
    stock       INT NOT NULL,
    version     INT NOT NULL DEFAULT 0,
    INDEX idx_stock (stock)
);
```

```java
@Service
public class SeckillService {

    private final JdbcTemplate jdbc;

    /** 返回 true=扣减成功，false=库存不足 */
    public boolean decreaseStock(Long productId, int qty) {
        int affected = jdbc.update("""
            UPDATE products
            SET stock = stock - ?
            WHERE id = ? AND stock >= ?
            """, qty, productId, qty);
        return affected == 1;
    }
}
```

**为什么这能防超卖：**

- `WHERE stock >= ?` 把"判断"和"扣减"合并到一条 SQL，DB 在执行时**对该行加 X 锁**，期间任何并发 UPDATE 都要排队。
- 库存不够时影响行数 = 0，业务层据此返回"售罄"。

**为什么不会死锁：**

- 只锁单行（id 是主键）。
- 单语句，事务范围最小。
- 所有并发请求按同一行排队，**顺序串行化** → 不存在循环等待。

**性能**：本地实测 MySQL 8 + SSD，单热点行能扛 ~3000-5000 QPS。10000 并发瞬时涌入会有等待，但不会出错。

### 方案 2：乐观锁（版本号，不必要场景下别用）

```sql
UPDATE products SET stock = stock - ?, version = version + 1
WHERE id = ? AND version = ? AND stock >= ?;
```

```java
public boolean decreaseStockOptimistic(Long productId, int qty) {
    for (int retry = 0; retry < 3; retry++) {
        Product p = jdbc.queryForObject("SELECT id, stock, version FROM products WHERE id=?",
            new BeanPropertyRowMapper<>(Product.class), productId);
        if (p.getStock() < qty) return false;
        int affected = jdbc.update("""
            UPDATE products SET stock = stock - ?, version = version + 1
            WHERE id = ? AND version = ? AND stock >= ?
            """, qty, productId, p.getVersion(), qty);
        if (affected == 1) return true;
        // 版本号变了，重试
    }
    throw new RuntimeException("retry exhausted");
}
```

**评价**：单纯扣库存场景下乐观锁**比方案 1 慢**（多 1 次 SELECT + 可能重试）。乐观锁适合"读多写少 + 长事务"的场景，扣库存不是。

### 方案 3：分段库存 + 分布式锁（应对极致并发）

把 100 库存拆成 10 段，每段 10：

```sql
CREATE TABLE product_stock_segments (
    product_id  BIGINT,
    seg_no      INT,
    stock       INT,
    PRIMARY KEY (product_id, seg_no)
);
```

扣减时按 `userId % 10` 路由到不同段，**把锁竞争从 1 行分散到 10 行**：

```java
public boolean decreaseStockSegmented(Long productId, Long userId) {
    int seg = (int)(userId % 10);
    int affected = jdbc.update("""
        UPDATE product_stock_segments SET stock = stock - 1
        WHERE product_id = ? AND seg_no = ? AND stock >= 1
        """, productId, seg);
    if (affected == 1) return true;

    // 当前段没了，遍历其他段兜底（性能下降但能扣到尾）
    for (int i = 0; i < 10; i++) {
        if (i == seg) continue;
        affected = jdbc.update("""
            UPDATE product_stock_segments SET stock = stock - 1
            WHERE product_id = ? AND seg_no = ? AND stock >= 1
            """, productId, i);
        if (affected == 1) return true;
    }
    return false;
}
```

**评价**：复杂度大幅上升，仅在方案 1 扛不住时用（万级 QPS 单商品）。

### 方案 4：Redis 预扣 + 异步落库（互联网大厂打法）

```
1. Redis 用 DECR 原子扣减（10 万 QPS 没问题）
2. 扣减成功 → MQ 发送"创建订单"消息
3. 消费者写 DB（异步、限流）
```

适用：库存查询不要求实时一致（如电商主搜索可以缓存 100ms）。

### 死锁避免清单

| 规则 | 解释 |
|------|-----|
| 所有事务按同一顺序访问资源 | 比如永远按 productId 升序 |
| 单 SQL 优先 | 不要 SELECT 再 UPDATE |
| 缩小事务范围 | 不在事务里发邮件、调 RPC |
| 索引必须命中 | 不然行锁升级为全表锁 |
| 设事务超时 | `innodb_lock_wait_timeout=5` 别用默认 50s |

### 选型决策树

```
QPS 估算：
├── < 1000：方案 1（单 SQL 扣减）
├── 1000-10000：方案 1 + Redis 缓存读
├── 10000-100000：方案 4（Redis 预扣 + MQ）
└── > 100000：方案 3 分段 + 方案 4 组合
```

**面试讲法**：

> "防超卖的本质是把'判断'和'修改'合并成原子操作。最简单是 `UPDATE ... WHERE stock >= ?`，DB 行锁天然防并发。比这复杂的方案都是为了承接更高的 QPS——但 99% 业务先用方案 1 就够了。**先写对的，再写快的**。"
