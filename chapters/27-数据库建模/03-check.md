# Chapter 27 数据库建模 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，每题都附**参考答案**。

---

## Q1（概念）：三范式分别要求什么？什么时候应该反范式？请举博客项目中的例子。

**参考答案：**

| 范式 | 要求 | 违反例 | 整改 |
|-----|------|--------|------|
| 1NF | 列原子，不可再拆 | `address='北京-海淀-学院路'` | 拆 `province / city / street` |
| 2NF | 非主键完全依赖主键（消除部分依赖，针对联合主键） | `order_item(order_id, item_id, item_name)`，`item_name` 只依赖 `item_id` | 抽出 `item` 表 |
| 3NF | 非主键不传递依赖 | `user(id, dept_id, dept_name)`，`dept_name` 依赖 `dept_id` | `dept_name` 放 `dept` 表 |

**反范式时机**：

1. **高频读 / 低频写**：列表页要展示作者昵称，每次 JOIN users 性能差 → 在 `post.user_name` 冗余一份。
2. **冗余字段几乎不变**：用户改昵称是低频事件，可接受异步同步过去。
3. **聚合可预计算**：`post.comment_count` 冗余存评论数，避免每次 `COUNT(*)`。

**反范式代价**：要写**同步机制**——MQ / 定时任务 / 触发器，并保证一致性容忍度（最终一致即可）。

**结论**：先 3NF 建模，再针对真实慢查询有据反范式，不要一开始就拍脑袋冗余。

---

## Q2（概念）：博客的 `comment` 表既要支持"评论某条文章"又要支持"回复某条评论"，请对比 3 种存储方案。

**参考答案：**

| 方案 | 结构 | 查整楼 | 查祖先 | 增删 | 适合 |
|-----|------|-------|-------|------|------|
| 邻接列表 `parent_id` | 只存父 id | 需要 CTE 递归 | 递归向上 | 简单 | 树浅、不取整楼 |
| 邻接列表 + `root_id` 冗余 | 加楼根 id | `WHERE post_id=? AND root_id=?` 1 条 SQL | 仍需递归 | 简单 | **博客评论首选** |
| 路径枚举 `path='/1/4/9/'` | varchar 存路径 | `WHERE path LIKE '/1/4/%'` | 切字符串 | 修改父亲难 | 论坛分类树 |
| 闭包表 | 单独表存所有祖先关系 | JOIN 闭包 | JOIN 闭包 | 写放大 N 倍 | 组织架构、需任意子树查询 |

**为什么博客选「邻接列表 + root_id」**：

- 业务只关心"某文章下整楼按时间排序"，`(post_id, root_id, created_at)` 索引一击命中。
- 顶层评论 `root_id = id`（插入后 UPDATE 一次或用触发器）。
- 楼中楼最大深度通常 ≤ 5 层，前端拿到扁平列表自己拼树。

**关键点**：永远在**真实查询模式**下选树形结构，不要"为了优雅"上闭包表。

---

## Q3（实操）：下面这张表里有 8 个建模/字段类型问题，找出并改正。

```sql
CREATE TABLE Order (
    id        INT,
    UserID    int,
    amount    FLOAT,
    status    VARCHAR(20),
    items     TEXT,
    isPaid    CHAR(1),
    created   TIMESTAMP,
    createdBy VARCHAR(100),
    KEY (status)
);
```

**参考答案（改后版本）：**

```sql
CREATE TABLE `order` (                                  -- ① 加反引号（order 是关键字）；表名小写
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, -- ② INT 容量 21 亿不够；缺主键声明
    user_id       BIGINT UNSIGNED NOT NULL,                -- ③ 命名 snake_case；类型对齐主键
    amount        DECIMAL(15, 2)  NOT NULL DEFAULT 0,      -- ④ FLOAT 丢精度，金额必 DECIMAL
    status        TINYINT         NOT NULL DEFAULT 0,      -- ⑤ 枚举用 TINYINT，省 19 字节
    item_ids      JSON,                                    -- ⑥ TEXT 半结构 → JSON 列
    is_paid       TINYINT(1)      NOT NULL DEFAULT 0,      -- ⑦ 布尔用 TINYINT(1)；命名 is_xxx
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),  -- ⑧ TIMESTAMP 有 2038 问题、时区坑
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,      -- ⑨ 操作人存 id 不存字符串
    is_deleted    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_paid (user_id, is_paid)                   -- ⑩ status 单列区分度低，去掉；按真实查询建联合索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';
```

**问题清单**：

1. 表名 `Order` 是保留字 + 大小写混用。
2. `id INT` 容量不够，缺主键约束。
3. `UserID` 命名风格错乱。
4. `amount FLOAT` 精度丢失。
5. `status VARCHAR(20)` 浪费空间且不利于索引。
6. `items TEXT` 半结构应用 JSON。
7. `isPaid CHAR(1)` 反模式。
8. `created TIMESTAMP` 2038/时区问题。
9. `createdBy VARCHAR` 应存外键 id。
10. `KEY(status)` 区分度低、单列无意义，且缺少时间审计列、软删除列。

---

## Q4（实操）：用 DDL 完成下面需求——"用户可以收藏文章，需要查询某用户收藏过哪些文章 + 某文章被多少人收藏 + 取消收藏"。

**参考答案：**

```sql
CREATE TABLE post_favorite (
    user_id    BIGINT UNSIGNED NOT NULL,
    post_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, post_id),           -- ① 天然去重，一人一文最多 1 条
    KEY idx_post_user (post_id, user_id)      -- ② 反向查"某文章被谁收藏"
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏';

-- 反范式聚合（高频读）
ALTER TABLE post ADD COLUMN favorite_count INT UNSIGNED NOT NULL DEFAULT 0;
```

3 个核心查询：

```sql
-- 我收藏的文章（按时间倒序）
SELECT p.id, p.title, f.created_at
FROM post_favorite f
JOIN post p ON p.id = f.post_id
WHERE f.user_id = :uid AND p.is_deleted = 0
ORDER BY f.created_at DESC
LIMIT 20;
-- 走 PK 前缀 (user_id) 索引

-- 某文章被多少人收藏（实时算）
SELECT COUNT(*) FROM post_favorite WHERE post_id = :pid;
-- 走 idx_post_user

-- 取消收藏（幂等）
DELETE FROM post_favorite WHERE user_id=:uid AND post_id=:pid;
UPDATE post SET favorite_count = favorite_count - 1 WHERE id=:pid AND favorite_count > 0;
```

**关键点**：

1. **组合主键 = 关系唯一性 + 主索引**，无需额外的 `id` 列（节省空间 + 写更快）。
2. **反向索引** `(post_id, user_id)` 让"某文章被谁收藏"也能走索引。
3. **冗余 favorite_count** 让列表页不用每条都 `COUNT(*)`；增 / 删收藏时 +1 / -1。
4. **幂等 DELETE** 无论收藏与否都安全。

---

## Q5（综合）：博客上线 1 年后，`comment` 表已经 5000 万行，业务方提出：
- 用户主页要展示「我发过的所有评论」分页
- 文章详情要展示「整楼评论」按时间排序
- 后台要做「按关键词搜评论」

请设计：表结构调整 / 索引 / 拆分策略，并写出关键 SQL。

**参考答案：**

### 一、表结构调整

```sql
ALTER TABLE comment
  ADD COLUMN root_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER parent_id;
-- 顶层评论 root_id = id；楼中楼 root_id = 楼根 id
-- 历史数据用一次性脚本回填
```

### 二、索引

```sql
ALTER TABLE comment
  ADD INDEX idx_user_time (user_id, created_at),         -- 用户主页
  ADD INDEX idx_post_root_time (post_id, root_id, created_at);  -- 整楼按时间
```

| 查询 | 索引 | EXPLAIN type |
|-----|------|-------------|
| 用户主页 | `idx_user_time` | ref |
| 整楼 | `idx_post_root_time` | ref + 索引排序 |

### 三、拆分策略（5000 万 → 难维护）

**纵向拆**：把 `content` 大列拆到 `comment_content` 副表，主表 keep 元数据，索引 / 列表查询不读 content，IO 显著降低。

**横向拆**（按 `post_id` 哈希分 16 库）：

```
comment_00, comment_01, ..., comment_15
路由：shard = post_id % 16
```

- 用户维度查询要广播 → 在 ES 同步一份"用户 → 评论 id 列表"。
- 选 ShardingSphere / MyCAT / 自研 router。

### 四、全文搜索

不要在 MySQL 上 `LIKE '%xxx%'`。同步到 **Elasticsearch**：

```
MySQL binlog → Canal / Debezium → Kafka → ES（comment-index）
```

后台搜评论走 ES，业务读详情仍走 MySQL。

### 五、关键 SQL

```sql
-- 我的评论分页（keyset）
SELECT id, post_id, content, created_at
FROM comment
WHERE user_id = :uid AND is_deleted = 0 AND id < :lastId
ORDER BY id DESC LIMIT 20;

-- 文章整楼
SELECT id, parent_id, user_id, content, created_at
FROM comment
WHERE post_id = :pid AND is_deleted = 0
ORDER BY root_id, created_at;   -- 楼内按楼根聚合 + 时间排序
```

### 六、生产对照（真实公司做法）

- B 站评论：MySQL 分片 64 库 + TiDB 兼顾 + ES 搜索 + Redis 缓存热门楼。
- 知乎评论：MySQL + 自研路由 + ES + 缓存 root 楼 JSON。

**关键点**：

1. 5000 万行还在 MySQL 单表能撑（索引选对 + 拆 content 副表）的范围。
2. 真正决定是否分片的是 **写入 QPS** 和 **B+ 树高度**（> 4 层就慢）。
3. 全文搜索不在 MySQL 里硬抗，丢给 ES。
4. 任何分片必先回答：路由键是谁、跨片查询怎么办、扩容怎么做（一致性哈希 / 双写迁移）。
