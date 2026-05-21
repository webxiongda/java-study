# Chapter 37 Spring Data JPA 可选线 - 理论篇

## 一、学习定位

这条线是 "MyBatis 的对照实验"——你不一定用它，但需要知道它解决什么问题、和 MyBatis 比差在哪。

- 优先级：L2（可选线，读完理解即可，不要求在博客项目里替换 MyBatis）
- 预计投入：2 小时
- 阶段产出：一篇 MyBatis vs JPA 对比笔记（`docs/jpa-vs-mybatis.md`）

## 二、核心概念

### 1. JPA 是什么

JPA（Jakarta Persistence API）是 Java 官方的 ORM 标准，Hibernate 是其最流行的实现。Spring Data JPA 在此基础上封装了一层。

```
Entity → JPA 注解 → Hibernate 生成 SQL → JDBC → DB
     ↑
Spring Data JPA：@Repository 接口，方法名自动解析 SQL
```

### 2. MyBatis vs JPA 核心对比

| 维度 | MyBatis（半自动） | JPA/Hibernate（全自动） |
|------|-----------------|----------------------|
| SQL 控制 | 手写 SQL，精确控制 | 框架自动生成，有时不符合预期 |
| 学习曲线 | XML + SQL 基础即可 | Entity 关系映射，一坑一学 |
| 复杂查询 | 手写 SQL，灵活 | @Query / Specification，边界痛 |
| N+1 问题 | 自己控制 | 容易触发，@EntityGraph 修复 |
| 缓存 | 一级 + 可控二级 | 一 + 二 + 查询缓存，但也容易脏 |
| 性能调优 | 直接改 SQL | 要读 Hibernate 日志看生成了什么 |
| 国内主流 | 80% 互联网公司 | 少数，如部分 spring-data-jpa 新项目 |

### 3. Entity 注解

```java
@Entity
@Table(name = "user")
@Data @NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Post> posts;
}
```

| 注解 | 含义 |
|------|------|
| `@Entity` | 声明这是 JPA 实体 |
| `@Id` | 主键 |
| `@GeneratedValue` | 主键生成策略（IDENTITY/SEQUENCE/AUTO）|
| `@Column` | 列名、约束、类型 |
| `@OneToMany` / `@ManyToOne` | 关联关系 |
| `@Transient` | 不映射到表的字段 |

### 4. Repository 接口

```java
// 基础 CRUD
public interface UserRepository extends JpaRepository<User, Long> {
    // 方法名解析查询
    Optional<User> findByEmail(String email);

    List<User> findByStatusOrderByCreatedAtDesc(int status);

    // @Query 自定义
    @Query("SELECT u FROM User u WHERE u.email LIKE CONCAT('%', :kw, '%')")
    List<User> search(@Param("kw") String kw);

    // 本地 SQL（绕过 JPA 缓存）
    @Query(value = "SELECT * FROM user WHERE DATE(created_at) = :date",
           nativeQuery = true)
    List<User> findByDate(@Param("date") LocalDate date);
}
```

**方法名解析**：`findByStatusOrderByCreatedAtDesc` → Hibernate 自动生成 `WHERE status=? ORDER BY created_at DESC`。

**利弊**：简单查询极快；但条件超过 3-4 个时方法名过长，反而不如 `@Query` 清晰。

### 5. 关联关系与 N+1

```java
@Entity
public class Post {
    @ManyToOne(fetch = FetchType.LAZY)   // 默认 EAGER，一定要改 LAZY
    @JoinColumn(name = "user_id")
    private User author;
}
```

**N+1 演示**：

```java
// 取 100 条 post → 每条访问 post.author → 100 额外 SQL
List<Post> posts = postRepository.findAll();
for (Post p : posts) System.out.println(p.getAuthor().getEmail());
// SQL: 1 SELECT post + 100 SELECT user
```

**修复**：

```java
// 方式 1：@EntityGraph 提前告警
@EntityGraph(attributePaths = "author")
@Query("SELECT p FROM Post p")
List<Post> findAllWithAuthor();
// 生成 1 条 LEFT JOIN

// 方式 2：@Query 用 JOIN FETCH 显式声明
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthor2();
```

### 6. 事务

Spring Data JPA 的 `JpaRepository` 方法自带 `@Transactional(readOnly=true)`；写操作（`save` / `delete`）自动在事务里执行。手写的 Service 方法加 `@Transactional` 覆盖即可。

### 7. 乐观锁

```java
@Entity
public class Post {
    @Version
    private Integer version;  // 每次 UPDATE SET version = version + 1 WHERE version = old
}
```

并发更新时，版本不匹配抛 `OptimisticLockException`。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `@EnableJpaRepositories("com.example.blog.dao")`（自动）|
| 配置 | `spring.jpa.hibernate.ddl-auto: update`（开发启用，生产用 Flyway）|
| 执行 | 方法名解析 → `QueryCreator` → SQL → JDBC |
| 边界 | N+1、EAGER 拖慢、懒加载在事务外 Exception、ddl-auto 删表 |
| 验证 | 开 `spring.jpa.show-sql=true + spring.jpa.properties.hibernate.format_sql=true` |

## 四、在博客项目里的定位（对比线）

博客项目走了 MyBatis 路线，但你可以做一个对照实验：把 `Tag` 表用 Spring Data JPA 管理，建立 `TagJpaRepository`，在 Service 里同仓库共存。

| 操作 | MyBatis | JPA |
|-----|---------|-----|
| 单表 CRUD | BaseMapper | JpaRepository.save/findById/deleteById |
| 条件查询 | 手写 `<where>` | `findByName(String)` 方法名解析 |
| 复杂 JOIN | 手写 SQL | `@Query` 或 `Specification` |
| 分页 | PageHelper / keyset | `Pageable` 参数 |

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| LazyInitializationException | 懒加载字段在事务外读取 | `@Transactional` 包围或 `JOIN FETCH` |
| N+1 全公司投诉 | EAGER 默认或没加 JOIN FETCH | `@EntityGraph` 或改 FetchType.LAZY |
| DDL 自动删表 | `ddl-auto=create` 覆盖 prod | 生产用 `validate` 或 `none` |
| 方法名太长解析错 | `findByXAndYOrZ` 超过 4 个条件 | 改用 `@Query` |
| 乐观锁频繁报错 | 高并发写入同一条记录 | 看实际冲突率，太高则改悲观锁 |

## 六、面试高频问题

1. JPA 和 MyBatis 各自的优缺点？（必考）
2. N+1 怎么产生的？怎么修？（必考）
3. LAZY 和 EAGER 有什么区别？为什么应该用 LAZY？（必考）
4. `@Version` 乐观锁怎么工作？
5. `ddl-auto` 各值的含义？生产上用哪几个？
6. `@EntityGraph` 的作用？
7. JPA 的一级缓存和二级缓存区别？
