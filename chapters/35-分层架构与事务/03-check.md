# Chapter 35 分层架构与事务 - 自测与验收

> 模板见 `docs/superpowers/specs/2026-05-25-check-template.md`
> 覆盖率自检:`node scripts/check-coverage.mjs '^35-'`

---

### Q1 [L1·概念·章节内测] Controller / Service / Repository 三层职责边界 + DTO ↔ DO 转换

**考点**: 三层职责, DTO ↔ DO 转换, 常见坑
**参考答案**:

```
Controller            Service                Repository (Mapper)
─────────────         ─────────────          ──────────────────
处理 HTTP             编排业务               访问数据库
参数绑定              事务边界               单表 CRUD / 聚合查询
入参校验              调用其他服务            不写业务判断
返回 DTO              转换 DO ↔ DTO          不依赖 Service
不写 SQL              不依赖 HTTP             只关心 SQL
```

**3 条铁律**:

1. **Controller 不能注入 Mapper**,必须经 Service。理由:跳过 Service 等于把业务规则写进 Controller,事务边界也错。
2. **Service 不返回 Entity**,统一返回 DTO/VO。理由:Entity 暴露内部字段(如 `password_hash`)、Lazy 字段会触发 N+1 / `LazyInitializationException`、字段重命名破坏 API 契约。
3. **事务边界在 Service 方法上**,不在 Controller 也不在 Mapper。

**DTO ↔ DO 转换**(推荐 MapStruct,手写时):

```java
public record ArticleDTO(Long id, String title, String authorName, LocalDateTime publishedAt) {
    public static ArticleDTO from(Article entity, User author) {
        return new ArticleDTO(entity.getId(), entity.getTitle(),
            author.getName(), entity.getPublishedAt());
    }
}
```

**常见坑**:

- Service 互相注入循环 → 通常职责拆错(用领域聚合或事件解耦)
- `@Transactional` 加在接口上而实现用 CGLIB → 注解可能不被识别。**加在实现类的具体方法上更安全**
- 事务里调远程接口 → 持锁太久,DB 连接耗尽(Q2 详述)

**🔥追问**:`@Transactional` 应该加在接口还是实现类?(答:实现类的 public 方法。Spring 文档明确推荐,避免 CGLIB 代理下注解可能失效;另外接口里的注解会被任何实现类继承,反而失去显式声明的价值)

**关联**: interview-bank.md#layered-architecture-rules

---

### Q2 [L2·概念·章节内测] 7 种事务传播行为 + 隔离级别 + readOnly = true 的作用

**考点**: 传播级别 (Propagation), 隔离级别 (Isolation), readOnly = true
**参考答案**:

**7 种传播级别**:

| 级别 | 有外层事务 | 无外层事务 | 典型场景 |
|---|---|---|---|
| `REQUIRED`(默认) | 加入 | 新建 | 99% 业务 |
| `REQUIRES_NEW` | 挂起外层 + 新建独立事务 | 新建 | 审计日志(外层失败也要留) |
| `NESTED` | 在外层内开 SAVEPOINT | 新建 | 子流程允许部分回滚(如可选的绑标签) |
| `SUPPORTS` | 加入 | 非事务 | 只读查询 |
| `MANDATORY` | 加入 | 抛异常 | 强约束内部方法 |
| `NOT_SUPPORTED` | 挂起当前事务 | 非事务 | 耗时操作避免长事务 |
| `NEVER` | 当前在事务中就抛异常 | 非事务 | 严格非事务路径 |

**工程常用 3 种**:`REQUIRED` 默认就够 / `REQUIRES_NEW` 审计日志 / `NESTED` 可独立回滚的子流程。

**`REQUIRES_NEW` 的坑**:挂起外层 = 释放当前 DB 连接 + 拿新连接。**连接池要 ≥ 并发数 × 2**,否则可能死锁(A 持 conn1 等 conn2,B 持 conn2 等 conn1)。

**隔离级别(Isolation)**:

| 级别 | 脏读 | 不可重复读 | 幻读 |
|---|---|---|---|
| READ_UNCOMMITTED | ✅ 会 | ✅ 会 | ✅ 会 |
| READ_COMMITTED | ❌ | ✅ 会 | ✅ 会 |
| REPEATABLE_READ(MySQL 默认) | ❌ | ❌ | ❌(InnoDB MVCC + 间隙锁解决了) |
| SERIALIZABLE | ❌ | ❌ | ❌(性能最差,几乎不用) |

**生产建议**:**用 DB 默认**,代码里不要瞎改。MySQL 用 REPEATABLE_READ,Oracle / PostgreSQL 用 READ_COMMITTED。改了之后调试问题非常困难。

**`readOnly = true`**:

```java
@Transactional(readOnly = true)
public ArticleDTO getDetail(Long id) { ... }
```

3 个好处:
1. 不开真正的写事务,**省 undo log** 生成
2. JPA 跳过 dirty check(对每次 entity 字段读后比对原值)
3. 读写分离场景下 ORM/driver 可走只读连接(读副本)

**所有列表 / 详情查询都应该加**。

**🔥追问**:`REPEATABLE_READ` 能完全防幻读吗?(答:InnoDB 在快照读用 MVCC、在当前读用间隙锁,两个机制结合解决了大部分幻读;但 `select ... for update` 与 `select` 混用仍可能出现"幻读"现象,严格说是 MySQL 实现下的妥协)

**关联**: interview-bank.md#transaction-propagation

---

### Q3 [L2·Debug·面试高频] @Transactional 6 种失效场景一次问完

**考点**: 4. 失效的 6 种情形, self call, 非 public, rollbackFor, AOP 代理, PostService, saveAndPublish
**参考答案**:

参考 02-demo.md 的 `PostService.saveAndPublish` self-call 反例。

| # | 失效场景 | 根因 | 修法 |
|---|---|---|---|
| 1 | **同类自调用**(self call) | `this.foo()` 走的不是代理对象,绕开 AOP 拦截器 | 注入自己的代理 bean(`@Autowired self`)或拆类 |
| 2 | **非 public 方法**加注解 | JDK 动态代理只代理接口的 public 方法;CGLIB 也不会增强 private/static | 改为 public(`final` 在 CGLIB 下也不行) |
| 3 | **抛 checked 异常未配 rollbackFor** | 默认只回滚 `RuntimeException` / `Error` | `@Transactional(rollbackFor = Exception.class)` 或异常继承 `RuntimeException` |
| 4 | **异常被 try-catch 吞掉** | catch 后不重抛,AOP 看不到异常,事务正常提交 | 不吞或在 catch 末尾 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` |
| 5 | **多数据源 + 默认 TransactionManager** | 默认 manager 不一定管你那个 DataSource,事务实际没开 | `@Transactional("orderTxManager")` 指定 manager |
| 6 | **Bean 没被 Spring 管理** | `new Service()` 出来的对象没代理 | 通过 `@Autowired` / `ApplicationContext.getBean` 获取 |

**最常考的反例代码**(改自 demo `PostService`):

```java
@Service
public class PostService {

    @Transactional
    public void saveAndPublish(Post p) {
        save(p);              // ❌ self call,事务注解失效
        publish(p.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Post p) { ... }
}
```

**修法 — 注入自身代理**:

```java
@Service
public class PostService {
    @Autowired PostService self;     // Spring 注入的是代理

    @Transactional
    public void saveAndPublish(Post p) {
        self.save(p);                 // ✅ 走代理,事务生效
        self.publish(p.getId());
    }
}
```

**最容易漏的"第 7 种"**:Spring 6.1 之前,**`@Transactional` 在 `@Async` 异步方法上,事务上下文不会传过去** — 因为 `@Async` 开了新线程,而事务靠 `ThreadLocal` 绑定。修法:确保两个注解的代理顺序对,或在异步方法内显式 `transactionTemplate.execute(...)`。

**🔥追问**:为什么 self call 失效?Spring 不能识别"自己调自己"再走代理吗?

**参考答案**:Spring AOP 是 **运行时代理模式**(JDK 动态代理 / CGLIB)。`this.foo()` 在字节码层就是 `invokevirtual this.foo` — JVM 不知道这个对象其实是代理对象包装的目标对象。Spring 在你 `@Autowired XxxService xxx` 时注入的是 **代理实例**,只有通过代理实例调方法,AOP 才能拦截。这是代理模式的固有限制,不是 Spring 的 bug。

**关联**: interview-bank.md#transactional-pitfalls

---

### Q4 [L2·代码编写·面试高频] 在博客发文 Demo 基础上,加只读详情 + 长事务修复 + 集成测试

**考点**: readOnly = true, 长事务陷阱, REQUIRES_NEW, detail, registerAndNotify, PostService
**参考答案**:

**(1)只读事务** — 配合 demo 的 `PostService.detail`:

```java
@Transactional(readOnly = true)
public PostDetailVO detail(Long id) {
    Post p = postMapper.findById(id);
    User author = userMapper.findById(p.getUserId());
    return PostDetailVO.from(p, author);   // 转 VO,不漏 Entity
}
```

**(2)长事务陷阱修复** — 参考 demo `registerAndNotify`:

```java
// ❌ 长事务:外部 IO 在事务内,DB 连接持有 6s+
@Transactional
public void registerAndNotify(RegisterReq req) {
    User user = userMapper.insert(...);
    smsService.sendVerify(user.getPhone());    // 可能 3s 超时
    mailService.sendWelcome(user.getEmail());  // 再 3s
}

// ✅ 短事务 + 事务外 IO
public void registerAndNotify(RegisterReq req) {
    Long userId = self.register(req);   // @Transactional,1ms 提交
    smsService.sendVerify(...);          // 此时 DB 连接已释放
    mailService.sendWelcome(...);
}

@Transactional
public Long register(RegisterReq req) {
    return userMapper.insert(...);
}
```

**长事务 3 大危害**:
1. 占用 DB 连接不释放(连接池耗尽 → 后续请求 timeout)
2. undo log 膨胀(InnoDB 长事务的 undo 不能 purge)
3. 锁持有时间长(阻塞其他写)

**铁律**:**事务范围 = SQL 语句的集合**。任何不是 SQL 的事情(HTTP / 文件 / 计算 / 短信)都不应在事务内。

**(3)集成测试** — 证明回滚 + REQUIRES_NEW 审计独立提交:

```java
@SpringBootTest
class PostServiceIT {
    @Autowired PostService postService;
    @Autowired PostMapper postMapper;
    @Autowired AuditLogMapper auditLogMapper;

    @Test
    @Transactional             // 测试结束自动回滚 — 测试间隔离
    void publish_and_tags_atomic() {
        Long id = postService.publish(1L, new PostCreateReq("t", "c", List.of(1L, 2L)));
        assertThat(postMapper.findById(id)).isNotNull();
    }

    @Test
    void publish_rolls_back_on_bad_tag() {
        long before = postMapper.countAll();
        long auditBefore = auditLogMapper.countAll();

        assertThatThrownBy(() ->
            postService.publish(1L, new PostCreateReq("t", "c", List.of(99999L))))
            .isInstanceOf(DataAccessException.class);

        // 主事务回滚 — post / post_tag 都没写
        assertThat(postMapper.countAll()).isEqualTo(before);

        // 但 REQUIRES_NEW 的 auditLog 已独立提交,留下记录
        assertThat(auditLogMapper.countAll()).isEqualTo(auditBefore + 1);
    }
}
```

**测试陷阱**:验证"REQUIRES_NEW 审计留存"的那条用例 **不能加 `@Transactional`**,否则整个测试包在一个事务里,内层 REQUIRES_NEW 在测试事务回滚时一并消失。

**🔥追问**:测试加 `@Transactional` 后,如何在测试期间真正提交去观察状态?(答:`@Commit` 或 `TestTransaction.flagForCommit() + end()`)

**关联**: interview-bank.md#readonly-transaction, interview-bank.md#long-transaction

---

### Q5 [L3·场景设计·面试高频] 设计"发文 → 绑 tag → 加积分 → 发 MQ 通知粉丝"的事务边界

**考点**: PostService, writeTag, 事务性发件箱, REQUIRES_NEW, NESTED, DB 事务 ≠ MQ 事务
**参考答案**:

**步骤分析**:

| 步骤 | DB 可回滚? | 幂等? | 失败可接受? |
|---|---|---|---|
| 1. INSERT post | ✅ | 否(多发多条) | ❌ 必须成 |
| 2. INSERT post_tag(批量) | ✅ | 否 | ⚠️ 部分失败可降级(`NESTED`) |
| 3. UPDATE user.post_count | ✅ | 否(累加) | ❌ 必须和 post 一致 |
| 4. 发 MQ 通知粉丝 | ❌(消息发了就没收回) | 消费者侧要做 | ⚠️ 可延迟 / 可丢 |

**错误做法**(MQ 在大事务里发):

```java
@Transactional
public void publish(...) {
    postMapper.insert(p);
    postTagMapper.batchInsert(...);
    userMapper.incrementPostCount(userId);
    mqProducer.sendPublishEvent(p.getId());   // ❌ MQ 已发但事务可能 rollback
}
```

→ 粉丝收到通知,但去查文章返回 404(回滚了)。

**生产级方案 A — 事务性发件箱(本地消息表)**:

```java
// 主事务:post + tag + count + 一条"待发"消息全部原子写
@Transactional(rollbackFor = Exception.class)
public Long publish(Long userId, PostCreateReq req) {
    postMapper.insert(p);
    self.writeTag(p.getId(), req.tagIds());     // ✅ 走代理调,事务生效
    userMapper.incrementPostCount(userId);
    outboxMapper.insert(OutboxMessage.of("POST_PUBLISHED", p.getId()));
    return p.getId();
}

@Transactional(propagation = Propagation.NESTED)
public void writeTag(Long postId, List<Long> tagIds) {
    try { postTagMapper.batchInsert(postId, tagIds); }
    catch (Exception e) { log.warn("tag insert failed, skip"); }
    // NESTED 让 tag 失败可独立回滚,不影响外层 post / count
}

// 独立 relay 服务:轮询 outbox 发 MQ,成功后标已发
@Scheduled(fixedDelay = 1000)
public void relayOutbox() {
    for (OutboxMessage m : outboxMapper.findPending(100)) {
        try {
            mqProducer.send(m);
            outboxMapper.markSent(m.id());
        } catch (Exception e) {
            log.error("relay fail, will retry", e);
        }
    }
}
```

**保证**:
- 主事务失败 → outbox 也回滚 → MQ 永远不会发
- 主事务成功 → outbox 落地 → relay 重试到成功为止
- 消费者侧做幂等(messageId 去重),最终一致

**生产级方案 B — `@TransactionalEventListener(AFTER_COMMIT)`(轻量版)**:

```java
@Transactional
public Long publish(...) {
    /* post + tag + count */
    applicationEventPublisher.publishEvent(new PostPublishedEvent(p.getId()));
    return p.getId();
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPublished(PostPublishedEvent e) {
    mqProducer.send(...);    // 事务确认提交后再发 — DB 一定有数据
    // ⚠️ MQ 失败没自动重试机制 — 适合可丢通知,不适合强一致场景
}
```

**两种方案选型**:

| 维度 | 发件箱 | AFTER_COMMIT 事件 |
|---|---|---|
| 可靠性 | 强(持久化 + relay 重试) | 弱(失败就丢) |
| 复杂度 | 高(要 outbox 表 + relay 任务) | 极低 |
| 延迟 | 1~10s(取决于 relay 周期) | 实时 |
| 适用 | 订单、支付、库存 | 通知、统计、日志 |
| 业内同类 | Debezium / Canal CDC | Spring 原生 |

**🔥追问**:`user.post_count` 实时累加会丢更新,怎么办?

**参考答案**:两种方案 —
- (a)定时任务全表 `SELECT COUNT(*) FROM post WHERE user_id=?` 刷新(适合统计指标,允许秒级延迟)
- (b)用 `UPDATE user SET post_count = post_count + 1 WHERE id=?`(原子加,不丢更新,但要求行锁串行,QPS 高时是瓶颈)
- (c)用 Redis `INCR` 计数,DB 异步落盘(最快,但 Redis 挂了可能丢)

**面试 2 分钟讲法**:

> "这种 DB + MQ 的混合操作,关键认知是:DB 事务和 MQ 事务无法用一个 @Transactional 原子化。生产首选事务性发件箱 — 主事务里写一条 outbox 记录,独立 relay 服务轮询发 MQ;主事务失败 outbox 一起回滚,主事务成功 relay 重试到成功为止,消费者侧做幂等。轻量场景用 `@TransactionalEventListener(AFTER_COMMIT)`,代码量小但 MQ 失败就丢。两个方案的核心都是把'发 MQ'移到事务边界之外,但用不同手段保证不丢消息。"

**关联**: interview-bank.md#db-mq-consistency

---

## 通过标准

- [ ] 能默写三层职责边界 + DTO/DO 转换的 3 条铁律
- [ ] 能默写 7 种传播级别 + 4 种隔离级别 + readOnly 的 3 个好处
- [ ] 能讲清 `@Transactional` 6 种失效场景及修法,尤其 self call
- [ ] 能写带 readOnly 的查询 / 把长事务里的 IO 移到事务外 / 验证回滚的集成测试
- [ ] 能设计 DB + MQ 混合操作的事务边界,讲清事务性发件箱 vs AFTER_COMMIT 事件的取舍
