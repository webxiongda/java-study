# Chapter 27 数据库建模 - 实操 Demo

## Demo 目标

把博客业务从草图 → ER → DDL 完整走一遍，包含：

- 5 张核心表 + 1 张中间表 + 1 张审计表
- 字段类型理由 + 索引理由
- 一次"线上加字段"演练（含 pt-online-schema-change）

## 前置

- MySQL 8.x
- Chapter 25 / 26 建立的 `blog` 库（已有 users、posts、comments）

## 一、业务需求收敛

| 用例 | 涉及实体 | 关键查询 |
|-----|---------|---------|
| 注册登录 | user | by email |
| 发文 / 列表 | post, user | by user_id + status |
| 评论楼中楼 | comment, user, post | by post_id, 按时间排序 |
| 标签筛选 | post, tag, post_tag | by tag → posts |
| 全文搜索 | post.title / content | LIKE 或 FULLTEXT |

## 二、完整 DDL

```sql
USE blog;
DROP TABLE IF EXISTS comment, post_tag, post, tag, user_audit, user;

-- 用户
CREATE TABLE user (
    id          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT     COMMENT '主键',
    email       VARCHAR(120)      NOT NULL                    COMMENT '登录邮箱',
    nickname    VARCHAR(32)       NOT NULL                    COMMENT '昵称',
    password    CHAR(60)          NOT NULL                    COMMENT 'bcrypt hash',
    status      TINYINT           NOT NULL DEFAULT 1          COMMENT '1=active 0=banned',
    created_at  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted  TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 标签
CREATE TABLE tag (
    id          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)       NOT NULL,
    created_at  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';

-- 文章
CREATE TABLE post (
    id          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED   NOT NULL,
    user_name   VARCHAR(32)       NOT NULL DEFAULT ''         COMMENT '反范式：列表页直接展示',
    title       VARCHAR(200)      NOT NULL,
    summary     VARCHAR(500)      NOT NULL DEFAULT '',
    content     MEDIUMTEXT,
    status      TINYINT           NOT NULL DEFAULT 0          COMMENT '0=draft 1=published',
    view_count  INT UNSIGNED      NOT NULL DEFAULT 0,
    created_at  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted  TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status_time (user_id, status, created_at),
    KEY idx_status_view (status, view_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章';

-- 文章-标签 多对多
CREATE TABLE post_tag (
    post_id  BIGINT UNSIGNED NOT NULL,
    tag_id   BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id, tag_id),
    KEY idx_tag_post (tag_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关系';

-- 评论：邻接列表 + 冗余 root_id（取整楼用）
CREATE TABLE comment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id     BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '0=顶层',
    root_id     BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '楼根 id，顶层=自己',
    content     VARCHAR(1000)   NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted  TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_post_root_time (post_id, root_id, created_at),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

-- 审计：谁改了 user 的什么
CREATE TABLE user_audit (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    op          VARCHAR(16)     NOT NULL  COMMENT 'INSERT/UPDATE/DELETE',
    old_value   JSON,
    new_value   JSON,
    operator_id BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户变更审计';
```

## 三、索引理由备注（写到 PR 描述里）

| 索引 | 服务的查询 | 区分度 |
|------|----------|-------|
| `uk_email` | 登录 `WHERE email=?` | 完全唯一 |
| `idx_user_status_time` | 用户主页 `WHERE user_id=? AND status=1 ORDER BY created_at DESC` | 高 |
| `idx_status_view` | 热门列表 `WHERE status=1 ORDER BY view_count DESC` | 中 |
| `(post_id, tag_id)` PK | 加标签去重 | 完全唯一 |
| `idx_tag_post` | 按标签找文章 | 反向 join 用 |
| `idx_post_root_time` | 取整楼 `WHERE post_id=? AND root_id=? ORDER BY created_at` | 高 |

## 四、查询验证

```sql
EXPLAIN SELECT id, title FROM post
WHERE user_id = 7 AND status = 1
ORDER BY created_at DESC LIMIT 10;
-- 期望：type=ref, key=idx_user_status_time, Extra=Using where; Backward index scan
```

```sql
-- 取一整楼评论
EXPLAIN SELECT * FROM comment
WHERE post_id = 100 AND root_id = 500
ORDER BY created_at;
-- 期望：type=ref, key=idx_post_root_time
```

## 五、线上加字段演练

需求：给 post 加 `cover_url VARCHAR(200)`。

### 错误做法（线上禁用）

```sql
ALTER TABLE post ADD COLUMN cover_url VARCHAR(200);
-- 大表会卡几分钟，期间 DML 全部排队
```

### 正确做法（Online DDL）

```sql
ALTER TABLE post
ADD COLUMN cover_url VARCHAR(200) NOT NULL DEFAULT '',
ALGORITHM=INPLACE, LOCK=NONE;
-- MySQL 8 支持，几乎不阻塞写
```

### 更稳的做法（超大表）

```bash
pt-online-schema-change \
  --alter "ADD COLUMN cover_url VARCHAR(200) NOT NULL DEFAULT ''" \
  D=blog,t=post --execute
# 原理：建影子表 → 触发器同步 → rename swap
```

## 六、失败场景

### 6.1 字符串列允许 NULL → 索引基数变怪

```sql
CREATE TABLE bad (email VARCHAR(100));   -- 默认允许 NULL
INSERT INTO bad VALUES (NULL), (NULL), ('a@x');
SELECT COUNT(*) FROM bad WHERE email != 'a@x';   -- 答案 0，不是 2 ！
-- 因为 NULL != 任何值都是 NULL
```

### 6.2 JSON 列直接 WHERE → 全表扫

```sql
SELECT * FROM post WHERE JSON_EXTRACT(meta, '$.lang') = 'zh';
-- type=ALL
```

修法：生成列 + 索引

```sql
ALTER TABLE post
ADD COLUMN lang VARCHAR(8) GENERATED ALWAYS AS (JSON_UNQUOTE(meta->'$.lang')) STORED,
ADD INDEX idx_lang (lang);
```

## 提交建议

```bash
git add db/migration/V27__schema.sql docs/er.png docs/index-rationale.md
git commit -m "chapter 27: blog v2 schema + ER + index rationale"
```
