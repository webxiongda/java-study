# Chapter 37 Spring Data JPA 可选线 - 项目任务

## 任务概述

用 Spring Data JPA 实现 `Tag` 模块（与 MyBatis 共存），对比代码量 / 性能，产出一份选型决策报告。

## 业务背景

博客项目主 ORM 是 MyBatis。但这不妨碍拿一个简单的模块做 JPA 对比实验，了解"如果用 JPA 会怎样"。

## 任务拆解

### Step 1：引入 JPA + 写 Entity

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
```

写 `TagEntity`（@Entity / @Table / @Id / @GeneratedValue / @Column）。

### Step 2：Repository 基础 CRUD

`interface TagRepository extends JpaRepository<TagEntity, Long>`，实现：

- `findById` / `findAll`（JPA 自带）
- `findByName(String)`（方法名解析）
- `search(String kw)`（@Query）

### Step 3：Service + Controller

- `POST /jpa/tags` 创建
- `GET /jpa/tags?kw=xxx` 搜索
- `GET /jpa/tags` 全部
- `DELETE /jpa/tags/{id}` 删除

MyBatis 的 Tag 接口仍然保留，新开 `/jpa/` 前缀做隔离，不冲突。

### Step 4：对比实验

写 `docs/jpa-vs-mybatis.md`，包括：

| 对比项 | MyBatis | JPA |
|-------|---------|-----|
| "创建标签" 代码行数 | xx | xx |
| "搜索标签" SQL 数 | 手写 1 行 | 方法名 1 行 |
| 启动时间 | 无影响 | ±1 秒（Hibernate 实体扫描）|
| 单测方式 | @MybatisTest + Testcontainers | @DataJpaTest + Testcontainers |

### Step 5：N+1 演示 + 修复

写一个 JOIN 查询取 Post + author，跑 `show-sql: true`，截图 N+1 日志。加 `@EntityGraph` 修复，截图 1 条 JOIN 日志。

### Step 6：乐观锁演示

在 `PostEntity` 加 `@Version`，写一个集成测试：两个线程并发 `UPDATE`，验证第二个抛 `OptimisticLockException`。

## 交付物

- [ ] `entity/TagEntity.java`
- [ ] `dao/TagRepository.java`
- [ ] `service/TagJpaService.java`
- [ ] `controller/TagJpaController.java`
- [ ] `docs/jpa-vs-mybatis.md`
- [ ] N+1 发生 + 修复截图各一张

## 验收清单

| 项 | 标准 |
|----|------|
| 4 个接口可 curl | `/jpa/tags` 前缀全部正常 |
| show-sql 无 N+1 | 列表查询日志中只有 1 条 SELECT |
| 对比文档有数据 | 代码行数 / SQL 数 / 启动时间有实测数值 |
| 与 MyBatis 共存 | MyBatis 接口仍然 100% 通过 |

## 扩展挑战

1. **Specification 动态查询**：用 `JpaSpecificationExecutor` 实现标签的多条件动态查询（名称/时间/状态），对比 MyBatis `<where>`。
2. **软删除**：JPA 的 `@SQLDelete` 注解实现逻辑删除，验证 `deleteById` 执行的是 UPDATE。
3. **分页**：`Page<TagEntity>` 返回 `Pageable`，看 JPA 的 COUNT 查询效率。
4. **复杂 JOIN**：用 JPA 写 "取某用户的所有文章 + 标签"（多对多），证明 JPA 能拼出复杂 SQL。
