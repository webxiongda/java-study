# Chapter 30 里程碑：博客 DAO 层 - 理论篇

## 一、学习定位

第 21-29 章学的是「单项技能」：Maven、JUnit、日志、JDBC、SQL、MyBatis。这一章是**第一个里程碑**：把它们拧成一个能跑、能测、能 review 的完整 DAO 层，作为博客 v1 的底座。

- 优先级：L0（必须达成）
- 预计投入：6 小时
- 阶段产出：可独立运行的 `blog-dao` 模块，覆盖率 ≥ 70%，所有核心查询 EXPLAIN 通过

## 二、核心概念

### 1. DAO 层的边界

DAO（Data Access Object）= **唯一**和数据库交互的层。

```
Controller → Service → DAO → DB
                       ↑ 只有这一层会用 Mapper
```

**铁律**：

- Service 不直接拼 SQL、不直接拿 Connection。
- Controller 不直接调 Mapper。
- 任何 SQL 都在 `*Mapper.xml`，禁止 Service 里 `@Select` 注解大段 SQL。
- DAO 返回**实体或基本类型**，不返回 ResultSet / Map。

### 2. 分层架构与依赖方向

```
┌──────────────────┐
│   web (REST)     │ Controller / DTO
├──────────────────┤
│   service        │ 业务编排、@Transactional
├──────────────────┤
│   dao            │ Mapper 接口 + XML + Entity
├──────────────────┤
│   infrastructure │ 数据源、缓存、MQ
└──────────────────┘
```

**依赖方向只能向下**：web → service → dao → infra。**禁止反向**（dao 调 service）。

### 3. Entity / DTO / VO 三者区别

| 层 | 类名后缀 | 谁用 | 字段 |
|---|---------|------|------|
| Entity | `User` | DAO 与 DB 表一一对应 | 含 `id, createdAt, isDeleted` 等 DB 字段 |
| DTO | `UserCreateReq` | Service 入参 / Controller 接收 | 业务需要的子集 |
| VO | `UserVO` | Controller 返回前端 | 含格式化字段，剔除敏感（password） |

**反例**：把 Entity 直接返回前端 → 暴露 `password` / `is_deleted` / 内部状态字段。

### 4. 事务的归属

**`@Transactional` 必须打在 Service 方法上**，不能打在 DAO / Controller。

```java
@Service
public class PostService {
    @Transactional
    public Long publish(Long userId, PostCreateReq req) {
        Long id = postMapper.insert(...);      // 这两步要么全成功
        postTagMapper.batchInsert(id, tagIds); // 要么一起回滚
        return id;
    }
}
```

**为什么不打 DAO**：

- 单个 DAO 方法是原子的（一条 SQL），加事务没意义。
- 跨多个 DAO 的协调发生在 Service。

### 5. 异常的处理与传播

| 层 | 抛 | 接 |
|---|----|-----|
| DAO | MyBatis 抛 `DataAccessException`（运行时） | 不接，让它冒上去 |
| Service | 业务异常如 `PostNotFoundException` | 不接，让它冒上去 |
| Controller | 都不接，交给全局异常处理器 | `@RestControllerAdvice` 转 JSON |

**铁律**：DAO 不写 `try-catch`。捕获后吞掉，业务就丢失了"事务失败要回滚"的信号。

### 6. 测试金字塔在 DAO 层的落点

- **单元测试**（多）：Service 用 Mockito mock 掉 Mapper，跑业务逻辑分支。
- **集成测试**（中）：Mapper 用 `@SpringBootTest + @Transactional`（自动回滚）连真实 H2 / MySQL，验证 SQL 真的能跑。
- **端到端**（少）：起完整应用 + Testcontainers MySQL，验证 Controller → Service → DAO 链路。

DAO 集成测试必须用真实数据库，不能 mock——SQL 错了 mock 是发现不了的。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `mvn test` 跑全部测试；Flyway 启动时建表 |
| 配置 | `application-test.yml` 用 Testcontainers MySQL，端口随机 |
| 执行 | `@Transactional` 在 Service 包装；事务边界 = HTTP 请求 |
| 边界 | DAO 单测覆盖 ≥ 70%；EXPLAIN 全部 ≥ ref |
| 验证 | CI 跑 `mvn verify`，含 SonarQube 静态扫描 |

## 四、阶段产出对照（里程碑验收）

| 模块 | 必须达成 |
|------|---------|
| `entity/` | 5 个实体类（User/Post/Tag/PostTag/Comment）|
| `dao/` | 5 个 Mapper 接口，每个 ≥ 6 个方法 |
| `mapper/*.xml` | 全部 `#{}`，无 `${}` 滥用 |
| `service/` | 至少 3 个 Service（User/Post/Comment）|
| 单测 | JaCoCo 覆盖率 ≥ 70%，关键方法 ≥ 90% |
| 集成测试 | 每个 Mapper ≥ 5 个测试用例 |
| 性能 | 8 个核心查询 EXPLAIN `type` 全 ≥ ref |
| 文档 | README 含「如何跑、如何测、如何加表」三段 |

## 五、常见坑

| 现象 | 原因 | 修法 |
|-----|------|------|
| `@Transactional` 不生效 | 类内 self call | 注入自己 / 抽到另一个 Service |
| 集成测试相互污染 | 没用 `@Transactional` | 测试方法上加，自动回滚 |
| Mapper xml 找不到 | mapper-locations 配置错 | `classpath*:mapper/**/*.xml` |
| Service 直接 catch SQLException | 误吞事务回滚信号 | DAO 不接、Service 不接 |
| Entity 直接返回前端 | 泄漏 password / is_deleted | 转 VO |
| 集成测试用 H2 | 部分 MySQL 语法 H2 不支持 | 用 Testcontainers MySQL |

## 六、面试高频问题

1. 你的项目分几层？依赖方向？
2. Entity / DTO / VO 各自的职责？
3. `@Transactional` 应该打在哪一层？为什么？
4. 类内方法互调 `@Transactional` 为什么失效？
5. DAO 层要不要 catch 异常？
6. Mapper 集成测试用 H2 还是真实 MySQL？为什么？
7. 怎么保证 SQL 都走索引？
