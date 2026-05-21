# Chapter 27 数据库建模 - 理论篇

## 一、学习定位

会写 SQL 不等于会建表。本章把"业务需求 → 表"的过程标准化：**范式 + 反范式 + 字段类型 + 索引策略 + 命名规范 + 演进策略**。

- 优先级：L1
- 预计投入：3 小时
- 阶段产出：博客 v2 数据模型 ER 图 + DDL 脚本（含字段注释、索引理由）

## 二、核心概念

### 1. 三范式 + 反范式

| 范式 | 定义 | 例子 |
|-----|------|------|
| 1NF | 每列原子，不再可拆 | `address` 拆成 province/city/street |
| 2NF | 非主键列完全依赖主键（消除部分依赖） | 联合主键 (order_id, item_id)，价格不该只依赖 item_id |
| 3NF | 非主键列不传递依赖 | `user` 表不该存 `dept_name`，只存 `dept_id` |

**反范式**：为了读性能，故意冗余。

```sql
-- 范式：post 没有 user_name，每次 JOIN users 拿
-- 反范式：post.user_name 冗余一份，列表页直接展示
```

> **何时反范式**：列表 / 详情高频读、被冗余字段几乎不变（如用户名、商品名）。

### 2. 主键策略

| 方案 | 优点 | 缺点 |
|-----|------|------|
| `AUTO_INCREMENT BIGINT` | 简单、聚集索引顺序写入快 | 单库自增、迁移合并难、暴露规模 |
| UUID v4 | 全球唯一、可前端生成 | 36 字节、随机分布导致页分裂 |
| 雪花 ID（Snowflake） | 64bit、趋势递增、含时间戳 | 时钟回拨问题 |
| 业务键（如 email） | 语义清晰 | 变更难、不能 NULL、容易泄露 |

**博客项目推荐**：`BIGINT AUTO_INCREMENT` 做主键 + 业务唯一键加 `UNIQUE KEY` 兜底。

### 3. 字段类型选择

| 业务含义 | 选型 | 反例 |
|---------|------|------|
| 主键 / 外键 | `BIGINT UNSIGNED` | `INT`（21 亿就爆） |
| 状态枚举 | `TINYINT` | `VARCHAR('active')` |
| 金额 | `DECIMAL(15,2)` | `DOUBLE`（精度丢失） |
| 时间 | `DATETIME(3)` | `VARCHAR` / `TIMESTAMP`（2038 问题） |
| 短文本 | `VARCHAR(N)` 精确估算 N | `TEXT`（不能默认值、内联差） |
| 长文本 | `TEXT` / `MEDIUMTEXT` | `VARCHAR(65535)` |
| 布尔 | `TINYINT(1)` | `CHAR('Y'/'N')` |
| JSON 半结构 | `JSON` | `TEXT` 自行解析 |

**陷阱**：所有字符串列必须显式 `NOT NULL DEFAULT ''`，否则 NULL 会让索引、聚合函数行为变怪。

### 4. 索引策略

**加索引前先回答 3 个问题**：

1. 这个列的查询频率 / 总查询数 > 10%？
2. 区分度 (`COUNT(DISTINCT col) / COUNT(*)`) > 0.1？
3. 联合索引是否能复用已有索引的最左前缀？

**典型组合**：

```sql
-- 用户主页：WHERE user_id=? AND status=1 ORDER BY created_at DESC
KEY idx_user_status_time (user_id, status, created_at)

-- 后台搜索：WHERE title LIKE 'xxx%'  （可以走索引）
KEY idx_title (title(50))

-- 唯一约束：邮箱
UNIQUE KEY uk_email (email)
```

**禁忌**：

- 在性别 (`gender`) 这类区分度极低的列单建索引。
- 给所有列各建一个索引（写放大 + 空间浪费）。
- 给 `TEXT` / `JSON` 直接建索引（必须用前缀或生成列）。

### 5. 命名规范

| 对象 | 模式 | 例 |
|-----|------|----|
| 表 | `snake_case`，单数 | `user`、`post_tag` |
| 列 | `snake_case` | `user_id`、`created_at` |
| 主键 | `id` | — |
| 外键 | `<table>_id` | `user_id` |
| 索引 | `idx_<列>` / `uk_<列>` | `idx_user_status`、`uk_email` |
| 布尔列 | `is_xxx` / `has_xxx` | `is_deleted` |
| 时间戳 | `xxx_at` | `created_at`、`updated_at`、`deleted_at` |

### 6. 软删除 + 审计字段

每张业务表标配 5 列：

```sql
created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
created_by  BIGINT      NOT NULL DEFAULT 0,
updated_by  BIGINT      NOT NULL DEFAULT 0,
is_deleted  TINYINT     NOT NULL DEFAULT 0
```

软删除：业务 SELECT 永远带 `WHERE is_deleted = 0`，避免误删用户数据。

### 7. 多对多 / 一对多 / 树状结构

- **一对多**：`comment.post_id` 外键即可。
- **多对多**：中间表 `post_tag(post_id, tag_id)`，组合主键 + 反向索引。
- **树状（评论楼中楼）**：
  - 邻接列表：`parent_id`（简单但递归慢）
  - 路径枚举：`path = '/1/4/9/'`（查子树快）
  - 闭包表：父子所有祖先关系存独立表（最灵活、写放大）

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | 业务用例 → ER 图 → DDL；用 dbdiagram.io / DBeaver ER 出图 |
| 配置 | InnoDB + utf8mb4 + utf8mb4_0900_ai_ci |
| 执行 | DDL 走 migration 工具（Flyway / Liquibase），永不手工执行 |
| 边界 | 大表 ALTER 用 pt-online-schema-change；金额字段永远 DECIMAL |
| 验证 | Code review 时盯 DDL：字段类型 / NOT NULL / 索引 / 默认值 |

## 四、在博客项目里的落点

- `db/migration/V1__init.sql`：5 张核心表，所有字段含注释。
- 所有 DAO 入口都基于 `is_deleted = 0`，删除操作改为 UPDATE。
- `posts.tags` 用中间表 `post_tag`，不要在 post 表里塞 `VARCHAR('java,spring')`。
- `comments` 用邻接列表 + 冗余 `root_id`，单 SQL 取整楼。

## 五、常见坑

| 现象 | 原因 | 修法 |
|-----|------|------|
| 加字段卡死全表 | 在线 ALTER 持锁 | pt-online-schema-change / gh-ost |
| 联合索引顺序错 | 区分度大的放后面 | 最左前缀，等值列放前，范围列放后 |
| TEXT 列做 WHERE | 全表扫 | 加生成列 + 索引 |
| 金额用 FLOAT | 0.1+0.2 ≠ 0.3 | 一律 DECIMAL(15,2) |
| 时区错乱 | TIMESTAMP 受会话时区影响 | 业务用 DATETIME 存 UTC，应用层格式化 |
| UUID 主键写慢 | 随机插入触发页分裂 | 用雪花 ID 或自增 |
| 没有审计字段 | 出问题查不到谁改的 | 默认 5 列审计 |

## 六、面试高频问题

1. 三范式是什么？何时反范式？
2. 主键用自增 vs UUID vs 雪花，怎么选？
3. 联合索引的最左前缀原则是什么？为什么？
4. 软删除 vs 物理删除，业务怎么选？
5. 评论树用什么结构存？怎么取整棵树？
6. 在线表怎么改字段不影响业务？
7. JSON 列在 MySQL 里能不能加索引？
