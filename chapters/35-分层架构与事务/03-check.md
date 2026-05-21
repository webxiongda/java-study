# Chapter 35 分层架构与事务 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：Spring 的 7 种事务传播行为分别是什么？工程里最常用的 3 种场景怎么选？

**参考答案：**

| 传播行为 | 有外层事务 | 无外层事务 |
|---------|----------|---------|
| `REQUIRED`（默认）| 加入外层事务 | 新建事务 |
| `REQUIRES_NEW` | 挂起外层，开新事务 | 新建事务 |
| `SUPPORTS` | 加入外层事务 | 以非事务方式执行 |
| `NOT_SUPPORTED` | 挂起外层，非事务 | 非事务 |
| `NEVER` | 抛异常 | 非事务 |
| `MANDATORY` | 加入外层事务 | 抛异常 |
| `NESTED` | 嵌套事务（SAVEPOINT）| 新建事务 |

**工程常用 3 种**：

| 场景 | 选择 | 理由 |
|------|------|------|
| 发文章（主流程）| `REQUIRED`（默认）| 所有步骤一起提交或一起回滚 |
| 审计日志 | `REQUIRES_NEW` | 主流程失败，审计日志仍要保留；主事务回滚不影响日志 |
| 可忽略的子操作（加 tag 失败不影响发文）| `NESTED` | 子操作可独立回滚，主事务不感知 |

**`REQUIRES_NEW` 实战注意**：

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(...) { ... }
```

调用时会**挂起**当前事务，也就是释放当前 DB 连接再拿新连接，连接池需要至少能支撑 `当前并发 × 2` 的连接数，否则死锁在连接池层面（A 持有连接 1 等连接 2，B 持有连接 2 等连接 1）。

**关键点**：`REQUIRED` 够用就别用 `REQUIRES_NEW`，后者引入连接数翻倍风险。

---

## Q2（概念）：什么是"长事务"？在博客项目里哪些典型写法会导致长事务？如何修复？

**参考答案：**

**长事务**：事务持有 DB 连接的时间过长（通常 > 1s 即为警戒线）。危害：

- 占用 DB 连接不释放（连接池耗尽 → 后续请求超时）。
- undo log 膨胀（InnoDB 长事务的 undo 不能清理）。
- 锁持有时间长（阻塞其他写操作）。

**3 种典型长事务**：

| 反例 | 修法 |
|------|------|
| 事务内调外部 HTTP / 短信 / 邮件服务 | 把 IO 移到事务外，事务提交后再发 |
| 事务内循环单条 INSERT（1 万次）| 改批量 insert，事务范围不变但执行快 |
| 事务内的 `SELECT ... FOR UPDATE` 加锁，然后调用第三方支付等待 3s | 先查询数据，移出事务，得到结果后再进短事务更新 |

**修法模板**：

```java
// ❌ 长事务（外部 IO 在事务内）
@Transactional
public void doIt(Req req) {
    userMapper.update(req);         // 持有锁
    smsService.send(req.phone());   // 外部 IO，可能 5s
    emailService.send(req.email()); // 再 5s
}

// ✅ 短事务 + 事务外 IO
public void doIt(Req req) {
    self.update(req);            // @Transactional 方法，1ms 提交
    smsService.send(...);        // 事务已释放，IO 不占 DB 连接
    emailService.send(...);
}

@Transactional
void update(Req req) { userMapper.update(req); }
```

**关键点**：事务的范围 = SQL 语句的集合；任何不是 SQL 的事情（HTTP / 文件 / 计算）都不应该在事务内。

---

## Q3（实操）：以下代码有 5 处事务问题，找出并改正。

```java
@Service
public class PaymentService {

    @Transactional
    private void deduct(Long userId, int amount) {           // ①
        accountMapper.deduct(userId, amount);
        throw new RuntimeException("模拟失败");
    }

    @Transactional
    public void pay(Long userId, int amount) {
        deduct(userId, amount);                              // ② self call
        orderMapper.markPaid(orderId);
    }

    @Transactional
    public List<Account> findAll() {                         // ③
        return accountMapper.findAll();
    }

    @Transactional(rollbackFor = IOException.class)          // ④
    public void transfer(Long from, Long to, int amount) {
        try {
            accountMapper.deduct(from, amount);
            accountMapper.add(to, amount);
        } catch (DataAccessException e) {
            log.error("transfer failed", e);                 // ⑤ 吞掉异常
        }
    }
}
```

**参考答案（改后）：**

```java
@Service
public class PaymentService {
    @Autowired PaymentService self;           // 注入代理

    // ① private → public（AOP 代理只增强 public）
    @Transactional
    public void deduct(Long userId, int amount) {
        accountMapper.deduct(userId, amount);
        throw new RuntimeException("模拟失败");
    }

    @Transactional
    public void pay(Long userId, int amount) {
        self.deduct(userId, amount);           // ② 通过代理调，事务正确传播
        orderMapper.markPaid(orderId);
    }

    // ③ 只读查询加 readOnly
    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountMapper.findAll();
    }

    // ④ rollbackFor 应覆盖 RuntimeException（已默认）+ 所有业务异常
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long from, Long to, int amount) {
        accountMapper.deduct(from, amount);
        accountMapper.add(to, amount);
        // ⑤ 不要 try-catch，让异常传播，Spring 自动回滚
        // catch 后只 log，不重抛 → Spring 看不到异常 → 不回滚 → 资金不一致
    }
}
```

**5 处问题**：

1. `private` 方法加 `@Transactional`，AOP 代理不增强 → 事务不生效。
2. `deduct(userId, amount)` self call → 走的是 `this` 不走代理 → 传播行为失效。
3. 查询方法缺 `readOnly = true`，性能损失。
4. `rollbackFor = IOException.class` 太窄，`RuntimeException`（如 `DataAccessException`）不在此列 → 数据不一致。
5. catch 后吞异常（只 log）→ Spring 看不到异常 → 事务提交而非回滚 → 资金转移后对端没加账。

---

## Q4（实操）：写"用户升级会员"Service，要求：扣款 → 升级状态 → 写审计日志三步，其中审计日志必须单独提交（主事务失败也要留记录），并写集成测试证明回滚行为。

**参考答案：**

```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final AccountMapper accountMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public void upgradeToPremium(Long userId, int price) {
        Account account = accountMapper.findByUserId(userId);
        if (account.getBalance() < price) {
            throw new InsufficientBalanceException("余额不足");
        }
        accountMapper.deduct(userId, price);          // step 1
        userMapper.setPremium(userId, true);          // step 2
        // step 3 在独立事务里提交，主事务 rollback 不影响审计
        auditLogService.log(userId, "UPGRADE_PREMIUM", price);
    }
}

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, int amount) {
        auditLogMapper.insert(AuditLog.builder()
            .userId(userId).action(action).amount(amount).build());
    }
}
```

**集成测试**：

```java
@SpringBootTest
class MemberServiceIT {

    @Autowired MemberService memberService;
    @Autowired AccountMapper accountMapper;
    @Autowired UserMapper userMapper;
    @Autowired AuditLogMapper auditLogMapper;

    @Test
    void upgrades_successfully() {
        // given：初始余额 500
        Long uid = setupUser(500);

        // when
        memberService.upgradeToPremium(uid, 199);

        // then
        assertThat(accountMapper.balance(uid)).isEqualTo(301);
        assertThat(userMapper.isPremium(uid)).isTrue();
        assertThat(auditLogMapper.countByUser(uid)).isEqualTo(1);
    }

    @Test
    void rolls_back_on_insufficient_balance_but_audit_remains() {
        // given：余额不足
        Long uid = setupUser(50);
        long auditBefore = auditLogMapper.countByUser(uid);

        // when
        assertThatThrownBy(() -> memberService.upgradeToPremium(uid, 199))
            .isInstanceOf(InsufficientBalanceException.class);

        // then：主事务回滚
        assertThat(accountMapper.balance(uid)).isEqualTo(50);     // 余额不变
        assertThat(userMapper.isPremium(uid)).isFalse();           // 状态不变

        // 审计日志已提交（REQUIRES_NEW 独立提交）
        // ⚠️ 此测试用例不加 @Transactional，不能自动回滚，需清理
        assertThat(auditLogMapper.countByUser(uid)).isEqualTo(auditBefore + 1);
    }
}
```

**关键点**：

1. `REQUIRES_NEW` 审计事务挂起主事务 → 先提交 → 主事务再 rollback 不影响它。
2. 集成测试"审计留存"那条 **不能加 `@Transactional`**（否则整个测试在同一个事务里，`REQUIRES_NEW` 被嵌入无法真正独立提交）。
3. 连接池需保证 `maximumPoolSize ≥ 并发数 × 2`（`REQUIRES_NEW` 同时需要 2 个连接）。

---

## Q5（综合）：博客发布文章的流程是：写 post → 绑 tag → 更新 user.post_count → 发 MQ 消息通知粉丝。请设计事务边界，分析各步失败时的影响，并给出生产级解决方案。

**参考答案：**

### 一、步骤分析

| 步骤 | 可以回滚？ | 幂等？ |
|------|----------|-------|
| INSERT post | ✅ | 否（多次 INSERT 多条）|
| INSERT post_tag | ✅ | 否 |
| UPDATE user.post_count | ✅ | 否（累加）|
| 发 MQ 消息 | ❌（消息发了就没收回）| 否 |

### 二、错误做法（全在一个大事务）

```java
@Transactional
public void publish(...) {
    postMapper.insert(p);
    postTagMapper.batchInsert(...);
    userMapper.incrementPostCount(userId);
    mqProducer.sendPublishEvent(p.getId());  // ❌ MQ 在事务里发，可能事务回滚但消息已发
}
```

**问题**：MQ 发出了但 DB 事务 rollback → 粉丝收到通知，但文章查不到。

### 三、生产级方案（本地消息表 / 事务性发件箱）

```java
// 步骤 1：主事务里写 post + tag + count + 消息记录（事务性发件箱）
@Transactional
public Long publish(Long userId, PostCreateReq req) {
    postMapper.insert(p);
    postTagMapper.batchInsert(...);
    userMapper.incrementPostCount(userId);
    // 不直接发 MQ，而是写一条"待发送"消息记录
    outboxMapper.insert(OutboxMessage.of("POST_PUBLISHED", p.getId()));
    return p.getId();
}

// 步骤 2：定时任务 / CDC 轮询 outbox 表，发 MQ，成功后标记已发
@Scheduled(fixedDelay = 1000)
public void relayOutbox() {
    List<OutboxMessage> msgs = outboxMapper.findPending();
    for (OutboxMessage m : msgs) {
        mqProducer.send(m);          // 幂等消费者去重
        outboxMapper.markSent(m.id());
    }
}
```

**保证**：主事务失败 → outbox 也 rollback → MQ 不发；主事务成功 → outbox 落地 → 最终一定会发出去。

### 四、各步失败影响分析

| 哪步失败 | 影响 | 方案 |
|---------|------|------|
| INSERT post 失败 | 整体回滚，无副作用 | `@Transactional` 默认 |
| INSERT post_tag 失败 | 整体回滚，无副作用 | 同上 |
| UPDATE post_count 失败 | 整体回滚，无副作用 | 同上 |
| 事务提交失败（极少）| post / tag 都没写进去 | 重试 + 幂等接口 |
| outbox relay 失败 | MQ 延迟发送，最终一致 | 重试 + 幂等消费者 |

### 五、简化方案（可接受最终一致）

```java
@Transactional
public Long publish(...) { /* post + tag + count */ }

// 事务提交后发 MQ（Spring 事件）
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onPublished(PostPublishedEvent event) {
    mqProducer.send(...);   // 事务已提交再发，DB 一定有数据
    // 但如果 MQ 发送失败，没有重试机制 → 最终一致性弱
}
```

适合：通知可丢、延迟可接受的场景（如粉丝通知 vs 库存扣减）。

**关键点**：

1. **DB 事务 ≠ MQ 事务**，两者无法用一个 `@Transactional` 一起原子化。
2. **本地消息表（事务性发件箱）**是最可靠的"DB + MQ 原子性"方案，已被 Debezium / Canal 发展为 CDC 模式。
3. **`@TransactionalEventListener(AFTER_COMMIT)`** 是轻量方案，但无持久化保证；适合非关键通知。
4. **user.post_count 可移出事务**：定时任务 `SELECT COUNT(*) FROM post WHERE user_id=?` 刷新比实时累加更准确（避免并发写入累加丢失）。
