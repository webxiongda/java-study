# Chapter 30 里程碑：博客 DAO 层 - 项目任务

> 前置：[[21-Maven工程化]] → [[29-MyBatis进阶]]

## 任务概述

把第 21-29 章学到的全部技术拧成一个**可独立运行、可测试、可演示**的 `blog-dao` 模块。这是博客项目的第一个交付里程碑。

## 业务背景

未来的 Service / Controller 都要建立在这层之上。这一章定下的代码风格、测试方式、命名规范，会影响后面 30 章。所以宁可慢一点，把基线做扎实。

## 任务拆解

### Step 1：项目脚手架

- Maven 多模块：`blog/{blog-common, blog-dao, blog-web}`
- `blog-common`：通用工具类（`Result`、`BusinessException`、`PageQuery`）
- `blog-dao`：本章重点
- 版本：JDK 21、Spring Boot 3.3.5、MyBatis 3.0.3、Flyway、Testcontainers

### Step 2：实体与映射

5 个 Entity（与 27 章 v2 schema 完全对齐）：

```java
User { id, email, nickname, password, status, createdAt, updatedAt, isDeleted }
Post { id, userId, userName, title, summary, content, status, viewCount, ... }
Tag, PostTag, Comment
```

Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`。

### Step 3：Mapper 接口

每个 Mapper 至少 6 方法：

- `findById(id)`
- `insert(entity)` （`useGeneratedKeys`）
- `update(entity)` （按 id）
- `softDelete(id)`
- `pageXxx(query)` （keyset 分页）
- `batchInsert(list)` （foreach）

加 `PostMapper.searchByConditions(req)` 动态 SQL（29 章）。

### Step 4：Service

3 个 Service：

- `UserService`：register（带密码加密占位） / banUser
- `PostService`：publish / unpublish / search / detail（带 tag 列表）
- `CommentService`：addComment / deleteThread / pageByPost

每个方法都要：
- `@Transactional`
- 业务规则用 `BusinessException` 而非返回 null

### Step 5：测试金字塔

```
test/
├── unit/              # Mockito mock Mapper，跑 Service 业务分支
│   ├── UserServiceTest.java
│   ├── PostServiceTest.java
│   └── CommentServiceTest.java
└── integration/       # @SpringBootTest + Testcontainers
    ├── UserMapperIT.java
    ├── PostMapperIT.java
    ├── PostServiceIT.java
    └── ...
```

每个 Mapper 集成测试 ≥ 5 用例；每个 Service 单测 ≥ 5 用例。

### Step 6：JaCoCo gate

加 JaCoCo 插件，覆盖率 < 70% 则 `mvn verify` 失败。

### Step 7：EXPLAIN 报告

写 `db/explain.sql`，跑 8 个核心查询：

1. 用户登录 `findByEmail`
2. 用户主页文章列表
3. 首页最新文章（keyset）
4. 按 tag 检索
5. 文章详情（含作者、tag）
6. 文章评论分页
7. 用户评论数统计
8. 后台搜索

每个查询附 EXPLAIN 输出，全部要求 `type ≥ ref`，无 `Using filesort`。

写到 `docs/explain.md`。

### Step 8：README

写 `blog-dao/README.md`：

- 如何起 MySQL（docker-compose 一句）
- 如何跑测试（`mvn verify`）
- 如何加新表（Flyway V8__xxx.sql + 新 Mapper + 测试模板）
- 如何看慢 SQL（开启 P6Spy / 慢日志）

## 交付物

- [ ] `blog/pom.xml`（parent）+ 3 子模块
- [ ] `blog-dao/src/main/`：5 entity + 5 Mapper + 3 Service + 5 XML
- [ ] `blog-dao/src/test/`：≥ 40 个测试，覆盖率 ≥ 70%
- [ ] `db/migration/V*.sql`（27 章已有 + 本章新增）
- [ ] `docs/explain.md`：8 查询 EXPLAIN 截图
- [ ] `blog-dao/README.md`
- [ ] CI 配置（GitHub Actions）跑 `mvn verify`

## 验收清单

| 项 | 标准 |
|----|------|
| 模块独立 | `cd blog-dao && mvn verify` 全绿 |
| 覆盖率 | LINE ≥ 70%（JaCoCo gate 强制） |
| 8 查询 | EXPLAIN `type` 全部 ≥ ref，无 `ALL`、无 `filesort` |
| 命名规范 | 实体 PascalCase；Mapper `XxxMapper`；测试 `XxxTest`/`XxxIT` |
| 无 SQL 注入风险 | grep `${` 应只命中白名单后的列名 |
| 事务正确 | self call 修复；业务异常正确回滚 |
| 文档 | README 三段齐全 |

## 扩展挑战

1. **本地 docker-compose 一键起**：`docker-compose up` 起 MySQL + Adminer，开发体验拉满。
2. **CI 集成测试加速**：Testcontainers reuse + Maven 并行 `-T 4`。
3. **数据权限 Mapper 拦截器**：根据当前用户角色自动注入 `WHERE user_id=?`。
4. **慢 SQL 大盘**：把 29 章的 SlowSqlInterceptor 改成 Micrometer Counter，接入 Prometheus + Grafana。
5. **MyBatis-Plus 替代实验**：选一个模块整改成 MP，对比代码行数 / 性能。
