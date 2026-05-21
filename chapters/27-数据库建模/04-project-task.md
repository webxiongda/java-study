# Chapter 27 数据库建模 - 项目任务

## 任务概述

把博客项目升级到 v2 数据模型：从「3 张拍脑袋表」改成「6 张规范表 + ER 图 + 字段注释 + 索引理由 + Flyway 管理」。

## 业务背景

v1 表是 25 章为了跑 SQL 临时凑的，缺：审计列 / 软删除 / 标签 / 评论楼 / 命名规范。这一章正式把它做成可以发给团队 review 的「v2 schema」。

## 任务拆解

### Step 1：画 ER 图

工具任选 dbdiagram.io / Mermaid / drawio。包含 6 张表：

```
user 1 ── n post n ── n tag
        │           │
        └── n comment  post_tag
```

输出到 `docs/er.png` 或 `docs/er.mmd`（Mermaid）。

### Step 2：写 v2 DDL

按 02-demo 第二节的完整 DDL 写到 `db/migration/V2__schema.sql`，要求：

- 每张表有 `COMMENT='中文表说明'`
- 每列加 `COMMENT`
- 所有字符串列 `NOT NULL DEFAULT ''`
- 所有表带 `created_at / updated_at / is_deleted`

### Step 3：写迁移脚本

把 25 章的 v1 数据迁过来：

```sql
-- V3__migrate_v1.sql
INSERT INTO user (id, email, nickname, password, created_at)
SELECT id, email, name, '$2a$10$xxx', created_at FROM users;

INSERT INTO post (id, user_id, user_name, title, status, created_at)
SELECT p.id, p.user_id, u.name, p.title, p.status, p.created_at
FROM posts p JOIN users u ON u.id = p.user_id;
-- ...
```

跑完用 `SELECT COUNT(*)` 三次对账（旧、新、差）。

### Step 4：索引理由文档

`docs/index-rationale.md`，每个索引写一行：

| 索引 | 服务的查询 | 区分度 | 写代价 |
|------|----------|-------|-------|
| `idx_user_status_time` | 用户主页发布列表 | 高 | 中 |

### Step 5：Flyway 接入

在 `pom.xml` 加：

```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

`application.yml`：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

启动应用，看日志「Successfully applied 3 migrations」。

### Step 6：线上加字段演练

往 `post` 加 `cover_url`：

```sql
-- V4__add_post_cover.sql
ALTER TABLE post ADD COLUMN cover_url VARCHAR(200) NOT NULL DEFAULT '',
ALGORITHM=INPLACE, LOCK=NONE;
```

启动应用，确认 Flyway 自动执行；用 `SHOW PROCESSLIST` 观察是否锁表。

## 交付物

- [ ] `docs/er.png` 或 `docs/er.mmd`
- [ ] `db/migration/V2__schema.sql`
- [ ] `db/migration/V3__migrate_v1.sql`
- [ ] `db/migration/V4__add_post_cover.sql`
- [ ] `docs/index-rationale.md`
- [ ] Spring Boot 启动日志截图（Flyway 成功）

## 验收清单

| 项 | 标准 |
|----|------|
| 字段规范 | 全部 `NOT NULL DEFAULT`，时间用 `DATETIME(3)`，金额用 `DECIMAL` |
| 命名规范 | snake_case；索引 `idx_` / `uk_` 前缀 |
| 软删除 | 所有业务 SELECT 都带 `is_deleted=0` |
| Flyway | 应用启动自动 migrate；version 表正确 |
| 数据对账 | 迁移后总行数与 v1 一致 |

## 扩展挑战

1. **审计触发器**：写 `AFTER UPDATE` 触发器把 user 表的变更写入 `user_audit`。
2. **JSON 索引**：给 `post.meta`（JSON）加生成列 + 索引，证明 EXPLAIN 走索引。
3. **ER 自动出图**：写脚本调 `mysqldump --no-data` + dbml-cli 自动生成 ER 图（CI 跑）。
4. **回滚演练**：在 Flyway 里用 `U2__rollback.sql`（社区版需手写），证明你的 DDL 可逆。
