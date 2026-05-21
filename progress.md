# Java 学习进度

> **使用提示**：每章 `01-theory.md` / `02-demo.md` 等文件可在顶部加 frontmatter（见末尾示例）。 装 Dataview 插件后，下面的两段查询块会自动汇总进度，无需手工维护表格。

## 当前状态

- 当前章节：01-环境搭建与 Java 初印象
- 开始日期：2026-05-21
- 完成章节数：0 / 60

## 自动汇总（需 Dataview 插件）

### 完成率（按阶段）

````dataview
TABLE
  length(rows) as 章数,
  length(filter(rows, (r) => r.status = "done")) as 已完成,
  round(length(filter(rows, (r) => r.status = "done")) / length(rows) * 100, 1) + "%" as 完成率
FROM "chapters"
WHERE file.name = "01-theory" AND stage
GROUP BY stage
SORT stage ASC
````

### 当前在学（最近 5 章）

````dataview
TABLE WITHOUT ID
  file.link as 章节,
  status as 状态,
  priority as 优先级,
  started as 开始,
  finished as 完成
FROM "chapters"
WHERE file.name = "01-theory" AND (status = "doing" OR status = "todo")
SORT chapter ASC
LIMIT 5
````

### 里程碑

````dataview
TABLE WITHOUT ID
  file.link as 里程碑,
  status as 状态,
  finished as 完成时间
FROM "chapters"
WHERE file.name = "04-project-task" AND milestone = true
SORT chapter ASC
````

### Frontmatter 模板（粘贴到 `01-theory.md` 顶部）

```yaml
---
chapter: 12
stage: "stage-1"            # stage-1 / stage-2 / stage-3
priority: L3                # L1 / L2 / L3
status: doing               # todo / doing / done
started: 2026-05-15
finished:
milestone: false
---
```

## 章节进度

| # | 章节 | 优先级 | 理论 | Demo | 验收 | 项目任务 | 复习 | 完成日期 |
|---|---|---|---|---|---|---|---|---|
| 01 | 环境搭建与 Java 初印象 | L1 | ✅ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 02 | Java 基础语法 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 03 | 方法与调试 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 04 | 数组与字符串 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 05 | 面向对象入门 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 06 | 继承与多态 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 07 | 包、访问控制与常用类 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 08 | 异常处理 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 09 | 枚举与泛型入门 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 10 | 里程碑：Java SE 小项目 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 11 | 集合框架上 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 12 | 集合框架下 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 13 | IO 基础 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 14 | NIO 与序列化 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 15 | 阶段一总结测验 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 16 | Lambda 与函数式接口 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 17 | Stream API | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 18 | Optional 与时间 API | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 19 | 注解与反射 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 20 | 里程碑：Java 工具库 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 21 | Maven 工程化 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 22 | 单元测试 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 23 | 日志体系 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 24 | JDBC 入门 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 25 | SQL 基础 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 26 | SQL 进阶 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 27 | 数据库建模 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 28 | MyBatis 入门 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 29 | MyBatis 进阶 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 30 | 里程碑：博客 DAO 层 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 31 | Spring 基础 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 32 | Spring Boot 入门 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 33 | REST API 设计 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 34 | 参数校验与异常处理 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 35 | 分层架构与事务 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 36 | Spring Boot + MyBatis | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 37 | Spring Data JPA 可选线 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 38 | OpenAPI 文档 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 39 | 配置与环境 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 40 | 里程碑：博客 API v1 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 41 | 认证基础 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 42 | JWT 实战 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 43 | 权限控制 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 44 | Spring Security 入门 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 45 | Web 安全防护 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 46 | Redis 基础与缓存 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 47 | Redis 进阶 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 48 | 消息队列概念 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 49 | 文件上传 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 50 | 里程碑：博客 API v2 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 51 | 并发基础 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 52 | 线程池与异步 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 53 | 并发安全 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 54 | JVM 基础 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 55 | 里程碑：并发 + JVM 测验 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 56 | Docker 部署 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 57 | 测试策略 | L2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 58 | 性能优化 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 59 | 系统设计与面试 | L3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
| 60 | 总结与进阶路线 | L1 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |
