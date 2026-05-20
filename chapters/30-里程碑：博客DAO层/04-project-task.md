# Chapter 30 里程碑：博客 DAO 层 - 项目任务

> 前置：[[21-Maven工程化]] → [[29-MyBatis进阶]]
> 这一站完成后，下一阶段（31-40）才接 Spring Boot Web。

## 任务概述

用 MyBatis 实现博客系统的数据访问层 v1：表设计 + Mapper + 单元测试，**不依赖 Spring Boot Web**，保证 DAO 可独立验证。

## 量化验收

### 一、数据库

| # | 项 | 标准 |
|---|---|---|
| 1 | 表数量 | ≥ 5：user / article / tag / article_tag / comment |
| 2 | 主键 | 全部 BIGINT AUTO_INCREMENT |
| 3 | 索引 | 至少 3 个有意义的二级索引（author+status / tag / created_at） |
| 4 | 字符集 | utf8mb4 + utf8mb4_unicode_ci（支持 emoji） |
| 5 | DDL 文件 | `sql/init/001-schema.sql` 可重复执行（含 `DROP IF EXISTS`） |
| 6 | 种子数据 | `sql/init/002-seed.sql` 至少 20 条文章 + 5 用户 + 10 标签 |

### 二、Mapper

| # | 项 | 标准 |
|---|---|---|
| 7 | Mapper 接口 | 每张表一个，方法名见名知意（`selectById` / `insert` / `updateSelective` / `deleteById` / `search`） |
| 8 | XML resultMap | 至少 2 个嵌套（article + author，article + tags） |
| 9 | 动态 SQL | `<where>` + `<if>` + `<foreach>` 至少各用一次 |
| 10 | 主键回填 | insert 后 `getId()` 非空 |
| 11 | 枚举映射 | status 字段用 `EnumTypeHandler` |
| 12 | 防注入 | 排序字段用白名单，禁止 `${userInput}` |

### 三、测试

| # | 项 | 标准 |
|---|---|---|
| 13 | 单测 | 每个 Mapper ≥ 3 个测试（insert / select / search） |
| 14 | 测试隔离 | 用 `@Transactional` + `@Rollback`（Spring Test）或 Testcontainers |
| 15 | 覆盖 | jacoco mapper 包 ≥ 80% |

### 四、运行验证

| # | 项 | 标准 |
|---|---|---|
| 16 | 启动 | `mvn test -Dtest=ArticleMapperTest` 全绿 |
| 17 | SQL 日志 | 控制台可见每条执行 SQL（log-impl: stdout） |
| 18 | 性能 | 列表分页 1w 条数据 < 100ms（带索引） |

## 提交

```bash
git tag dao-v1
git push origin dao-v1
```

## 复盘问题

1. 列出所有表的字段 + 索引 + 关系图。
2. `<resultMap>` 嵌套和两次查询拼接，分别在什么场景用？
3. 哪个查询如果数据涨到 100w 行会变慢？怎么优化？
4. 你最满意的一个 SQL 是哪个？为什么？
