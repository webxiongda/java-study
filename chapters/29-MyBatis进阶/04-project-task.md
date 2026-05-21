# Chapter 29 MyBatis 进阶 - 项目任务

## 任务概述

把 28 章的博客 Mapper 升级到生产级：动态 SQL + 嵌套映射消除 N+1 + 自定义慢 SQL 插件 + 批量评论同步。

## 业务背景

28 章把 DAO 跑通了，但「搜索 / 列表 / 后台报表」类查询还在用拼字符串 + 多 SQL，性能拉胯。
这一章上动态 SQL、关联映射、自定义插件，把博客的查询性能推到一档新水平。

## 任务拆解

### Step 1：搜索接口走动态 SQL

`PostMapper.searchByConditions(req)`：

支持以下可选条件（任意组合）：

- `kw`：标题模糊
- `status`：发布状态
- `userId`：作者
- `tagIds`：标签 IN
- `startDate / endDate`：时间范围
- `sort`：白名单后的排序列

XML 用 `<where> + <if> + <foreach>`，禁止 `1=1` 和拼接 `${}`。

### Step 2：文章详情消除 N+1

接口：`GET /posts/{id}`，返回字段：
- 文章本身
- 作者昵称
- 标签列表（最多 5 个）
- 顶层评论数

**约束**：在 SQL 日志里观察这一次请求**只能发 ≤ 2 条 SQL**（1 条 JOIN posts+users+tags，1 条 COUNT comments）。

### Step 3：自定义慢 SQL 插件

按 03-check Q4 写 `SlowSqlInterceptor`，阈值 500ms。

- 注入 MDC traceId
- 输出 SQL + 参数 + 耗时
- 用 SLEEP() 制造慢 SQL，单测验证插件被触发

### Step 4：批量评论同步

写脚本：从外部 JSON 文件读 10 万条 comment 灌入 DB。

实现 3 个版本并 benchmark：

1. for + 单条 insert
2. `<foreach>` 单 SQL 多 VALUES
3. BATCH executor + `rewriteBatchedStatements=true`

写表格到 `docs/batch-benchmark.md`。

### Step 5：开 P6Spy 全链路看 SQL

把所有 SELECT/INSERT 经 P6Spy 打印出真实带参 SQL，验证：

- 动态 SQL 跳过空条件
- N+1 已消除
- 批量是真正合并成单 SQL

## 交付物

- [ ] `dao/PostMapper.java` + `mapper/PostMapper.xml`：动态搜索 SQL
- [ ] `dao/PostMapper.findDetail(id)` 带嵌套 resultMap
- [ ] `plugin/SlowSqlInterceptor.java`
- [ ] `script/CommentImporter.java`：3 版本批量插入
- [ ] `docs/batch-benchmark.md`：耗时对照表
- [ ] `docs/sql-log/`：P6Spy 截图

## 验收清单

| 项 | 标准 |
|----|------|
| 动态 SQL | 任意条件组合都正确，无 SQL 注入 |
| 无 N+1 | `/posts/{id}` 请求 ≤ 2 条 SQL |
| 慢 SQL 插件 | 单测覆盖 + 触发时 log 含 traceId |
| 批量性能 | 10 万行 < 5 秒（BATCH + rewriteBatched 版本）|
| 排序白名单 | 传非白名单值返回 400 |

## 扩展挑战

1. **PageHelper 集成**：换成 PageHelper + count 优化（用 `--without-count` 跳过 count 查询）。
2. **MyBatis-Plus 对比**：把 `PostMapper.searchByConditions` 改写成 LambdaQueryWrapper，对比可读性。
3. **数据权限插件**：写一个 Interceptor，根据当前登录用户的角色自动注入 `WHERE user_id=?`，防止越权查别人的草稿。
4. **二级缓存压测**：开 MyBatis 二级缓存 vs 关掉用 Spring Cache + Redis，QPS 对比。
