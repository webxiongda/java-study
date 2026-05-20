# Chapter 35 分层架构与事务 - 理论

> 前置：[[34-参数校验与异常处理]] [[28-MyBatis入门]]
> 后续：[[36-SpringBoot+MyBatis]]
> 优先级：L1  预计：4 小时

## 1. 为什么需要这一章

写到几百行的 Controller 之后你会发现：协议（HTTP）、业务（"发文要扣积分"）、存储（SQL）耦在一起，改一处影响一片，测试也写不了。分层就是**强制把这三种关注点分开**。事务则是保证「业务半步」的原子性——发文 + 扣积分要么都成，要么都不成。

## 2. 三层职责（边界要记死）

```
Controller       Service          Repository (Mapper)
─────────────    ─────────────    ────────────────────
处理 HTTP        编排业务         访问数据库
参数绑定         事务边界         单表 CRUD / 聚合查询
入参校验         调用其他服务     不写业务判断
返回 DTO         转换 DO ↔ DTO    不依赖 Service
不写 SQL         不依赖 HTTP      只关心 SQL
```

- Controller 不能注入 Mapper，**必须**经过 Service。
- Service 不返回 Entity，统一返回 DTO/Vo（防止 lazy 序列化坑）。
- 事务边界在 **Service 层方法**上，不在 Controller 也不在 Mapper。

## 3. `@Transactional` 工作机制

Spring AOP 在调用 `@Transactional` 方法前后织入「开事务 → 业务 → 提交 / 回滚」。 默认只在抛出 `RuntimeException` 或 `Error` 时回滚。

### 传播级别（Propagation）

| 级别 | 行为 | 典型场景 |
|---|---|---|
| `REQUIRED`（默认） | 有事务则加入，无则新开 | 99% 业务 |
| `REQUIRES_NEW` | 挂起外层，新开独立事务 | 写日志/审计，无论外层失败都要记 |
| `NESTED` | 在外层事务内开 SAVEPOINT | 子流程允许部分回滚 |
| `SUPPORTS` | 有则加入，无则非事务运行 | 只读查询 |
| `MANDATORY` | 必须在事务中，否则抛异常 | 强约束的内部方法 |
| `NOT_SUPPORTED` | 挂起当前事务 | 耗时操作，避免长事务 |
| `NEVER` | 当前在事务中就抛异常 | 严格非事务路径 |

### 隔离级别（Isolation）

通常用 DB 默认（MySQL 是 `REPEATABLE_READ`），代码里不要瞎改。 详见 [[26-SQL进阶]]。

### `readOnly = true`

```java
@Transactional(readOnly = true)
public ArticleDTO getDetail(Long id) { ... }
```

- 不开真正的写事务，省 undo log。
- 部分 DB driver / ORM 会跳过 dirty check（JPA）或选择只读连接（读写分离）。
- 列表/详情查询统一加上。

## 4. 失效的 6 种情形（高频面试，详见 [[interview-bank]]）

1. **同类自调用**：`this.foo()` 调本类 `@Transactional bar()`，绕开 AOP 代理。 修复：注入自己的代理 bean，或拆类。
2. **非 public 方法**：JDK 动态代理默认只代理 public。
3. **checked 异常未配 `rollbackFor`**：默认只回滚 RuntimeException。 业务异常要么继承 RuntimeException，要么 `@Transactional(rollbackFor = Exception.class)`。
4. **异常被 catch 吞了**：catch 后没 rethrow，AOP 看不到异常，事务正常提交。
5. **多数据源未配 TransactionManager**：默认 manager 不一定管你那个 dataSource。
6. **Bean 没被 Spring 管理**：`new Service()` 出来的对象没有代理。

## 5. 业务异常 vs 系统异常

- **业务异常**（`BusinessException`）：用户输入或业务规则导致，可预期，返回 4xx 业务码。
- **系统异常**（`RuntimeException` 未捕获）：bug / 网络 / DB 挂掉，返回 5xxxx。

事务回滚策略：业务异常通常需要回滚（操作失败）；但有时业务异常发生时，**前面的写入也要保留**（比如「先记一条审计再抛业务异常」），这时审计写入必须用 `REQUIRES_NEW`。

## 6. DTO ↔ DO 转换

不要让 Entity 直接返回给前端：

- 暴露内部字段（如 `password_hash`）。
- Lazy 字段触发 N+1。
- 字段重命名后 API 也变，破坏契约。

用 MapStruct（推荐）或手写 `ArticleDTO.from(Article)` 静态方法。

## 7. 常见坑

- Service 互相注入循环 → 通常说明职责拆错；用领域聚合 / 事件解耦。
- 事务里调用远程接口 → 持锁太久导致 DB 连接耗尽。 远程调用应放到事务外（结果回填到本地后再开事务）。
- `@Transactional` 加在接口上、实现用 CGLIB → 注解可能不被识别，建议加在实现类的具体方法上。

## 8. 与项目衔接

博客 API 「发布文章」用例：

```
Controller.create(req)
  → ArticleService.publish(req)            // @Transactional
       ├── articleMapper.insert(article)
       ├── tagService.bindTags(articleId, tagIds)
       └── userPointService.add(userId, 10)   // 同事务
  → ApiResponse.ok(articleId)
```

任一步抛 RuntimeException → 全部回滚，数据库不会出现「文章存在但积分没加」的不一致。
