# Chapter 48 消息队列概念 - 自测

## Q1 (概念): RabbitMQ vs Kafka vs RocketMQ, 各自定位和选型

### 答案

| 维度 | RabbitMQ | Kafka | RocketMQ |
|---|---|---|---|
| 语言 | Erlang | Scala / Java | Java |
| 协议 | AMQP | 自有二进制 | 自有 |
| 吞吐 | 1-10w QPS | 100w+ QPS | 10-100w QPS |
| 延迟 | < 1ms | 5-10ms | < 1ms |
| 主要数据结构 | Exchange + Queue | Topic + Partition | Topic + MessageQueue |
| 顺序保证 | 单队列 | 单 partition | 单 queue |
| 延迟消息 | 插件 | 业务实现 | 原生 (固定级别) |
| 事务消息 | 弱 | 较强 | 强 (半消息回查) |
| 死信 | 原生 | 业务实现 | 原生 |
| 消息回溯 | 不支持 | 支持 (按 offset 重读) | 支持 |
| 适用场景 | **业务异步** | **流处理** | **业务消息 + 强一致** |

**选型决策树**:

```
需要回溯历史消息? ─Yes→ Kafka / RocketMQ
        │
        No
        ↓
QPS > 50w? ─Yes→ Kafka
        │
        No
        ↓
强一致 (事务消息)? ─Yes→ RocketMQ
        │
        No
        ↓
RabbitMQ (默认)
```

**博客 API 的选型**:
- 评论通知: RabbitMQ (灵活 + 死信 + 延迟)
- 用户行为日志: Kafka (高吞吐, 接 Flink 实时分析)
- 订单支付 (假设): RocketMQ (事务消息)

**为什么不一律用 Kafka?**
- Kafka 的 partition 模型不适合"一对多广播 + 路由灵活"场景
- Kafka 没有原生死信 / 优先级 / 延迟队列
- Kafka 客户端复杂 (rebalance / offset 管理)

### 常见坑

- 用 Kafka 做业务 MQ, 被 rebalance + offset 提交搞死
- 用 RabbitMQ 做日志管道, 单队列吞吐顶不住

## Q2 (概念): 消息可靠投递的三个环节, 各自怎么保证

### 答案

#### 环节一: Producer → Broker

**风险**: 网络抖动, Broker 没收到。

**保证**:
- **publisher confirm** (RabbitMQ): Broker 写入持久化后 ack 给 Producer
- **acks=all** (Kafka): 所有 ISR 副本写入才 ack
- **return callback**: 找不到队列时回调, 避免黑洞

```java
@Bean
public RabbitTemplate template(CachingConnectionFactory cf) {
    cf.setPublisherConfirmType(PUBLISHER_CONFIRMS);
    cf.setPublisherReturns(true);
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setMandatory(true);
    t.setConfirmCallback((data, ack, cause) -> {
        if (!ack) log.error("NOT ACKED: id={}, cause={}", data.getId(), cause);
    });
    t.setReturnsCallback(r -> log.error("RETURNED: routingKey={}", r.getRoutingKey()));
    return t;
}
```

> 进阶: **本地消息表 (outbox)** 模式 - 业务事务里同时写 DB + outbox 表, 单独 poller 发 MQ, 解决"DB 提交但 MQ 没发"。

#### 环节二: Broker 内部

**风险**: Broker 重启 / 宕机, 内存中消息丢。

**保证**:
- 队列 **durable=true**
- 消息 **persistent** (deliveryMode=2)
- Broker 集群 (RabbitMQ Quorum Queue / Kafka 多副本)

> 即使持久化也不绝对: 写入 page cache 但还没 fsync 时宕机, 1 秒内的消息可能丢。 接受这个权衡, 否则吞吐崩塌。

#### 环节三: Broker → Consumer

**风险**: Consumer 拿到消息后 crash, 业务没完成。

**保证**:
- **手动 ack** (`acknowledge-mode: manual`): 业务完成才 ack
- 业务异常 → nack + requeue, 或 nack + 进死信
- Consumer 重启时 Broker 会把"未 ack" 重新投递

```java
@RabbitListener(queues = "comment.notify")
public void onMsg(NotifyMsg msg, Channel channel, @Header(DELIVERY_TAG) long tag)
        throws IOException {
    try {
        emailService.send(msg);
        channel.basicAck(tag, false);
    } catch (BusinessException e) {
        channel.basicNack(tag, false, false);   // 业务异常进死信
    } catch (Exception e) {
        channel.basicNack(tag, false, true);    // 网络异常重投
    }
}
```

#### 综合: At least once + 幂等 = 业务 Exactly once

三个环节都保证后, 消息**最多重复, 不会丢失**。 消费者必须幂等 (Redis 幂等键或业务唯一索引) 才能保证业务正确。

### 常见坑

- 开了 publisher confirm 但没回调处理 → 仍然丢
- 设了 persistent 但队列没 durable → 重启丢
- 自动 ack + 业务异常 → 消息丢

## Q3 (代码改错): 找出下面消费者的问题

```java
@Service
public class CommentNotifier {

    @Autowired private EmailService email;
    @Autowired private PushService push;
    @Autowired private CommentMapper mapper;

    @RabbitListener(queues = "comment.notify")
    public void onComment(String json) {
        NotifyMsg msg = JSON.parseObject(json, NotifyMsg.class);
        email.send(msg.getEmail(), "新评论", msg.getContent());
        push.send(msg.getUserId(), msg.getContent());
        mapper.markNotified(msg.getCommentId());
    }
}
```

`application.yml`:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto
```

### 答案

**问题清单**:

1. **自动 ack**: 一进监听器就 ack, 任何一步异常 → 消息丢
2. **没幂等**: 重投 / 重试时会重复发邮件、重复发推送、重复 update DB
3. **多步骤无原子性**: email 成功, push 失败 → 重试后 email 又发一次
4. **异常不分类**: 网络抖动应重试, 业务错误 (邮箱格式错误) 应进死信
5. **没 try-catch**: 异常抛到框架, 框架根据 `default-requeue-rejected=true` 无限重投 → 死循环
6. **同步阻塞**: email + push 都同步, 一个慢全慢

**修复**:

```java
@Service
@RequiredArgsConstructor
public class CommentNotifier {

    private final EmailService email;
    private final PushService push;
    private final CommentMapper mapper;
    private final StringRedisTemplate redis;

    @RabbitListener(queues = "comment.notify", concurrency = "5-10")
    public void onComment(NotifyMsg msg, Channel channel, @Header(DELIVERY_TAG) long tag)
            throws IOException {
        String idKey = "msg:processed:" + msg.getMsgId();
        Boolean fresh = redis.opsForValue().setIfAbsent(idKey, "1", Duration.ofDays(7));
        if (!Boolean.TRUE.equals(fresh)) {
            channel.basicAck(tag, false);
            return;
        }
        try {
            email.send(msg.getEmail(), "新评论", msg.getContent());
            push.send(msg.getUserId(), msg.getContent());
            mapper.markNotified(msg.getCommentId());
            channel.basicAck(tag, false);
        } catch (RecoverableException e) {
            redis.delete(idKey);
            channel.basicNack(tag, false, true);
        } catch (Exception e) {
            log.error("Send notify failed permanently: {}", msg, e);
            channel.basicNack(tag, false, false);
        }
    }
}
```

`application.yml`:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000
          multiplier: 2
        default-requeue-rejected: false
```

更彻底拆分: 路由消费者把 1 条消息拆成 N 条子消息, 各下游独立队列独立 consumer。

## Q4 (代码题): 用 RabbitMQ 实现 "30 分钟未支付订单自动取消"

### 方案对比

| 方案 | 优点 | 缺点 |
|---|---|---|
| **定时扫表** | 简单 | 周期最少 1 分钟, 时效差; 大表慢 |
| **Redis 过期事件** | 实时 | Redis 过期事件不可靠 (大量 key 过期时丢) |
| **MQ 延迟消息** | 实时 + 可靠 + 解耦 | 需要插件 / TTL+DLX |
| **专用调度** (Quartz / xxl-job) | 强 | 重 |

### 参考实现 (RabbitMQ TTL + DLX)

#### 配置

```java
@Configuration
public class OrderMqConfig {

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable("order.cancel").build();
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return ExchangeBuilder.directExchange("order.cancel.exchange").durable(true).build();
    }

    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
            .to(orderCancelExchange()).with("order.cancel");
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable("order.delay")
            .withArgument("x-dead-letter-exchange", "order.cancel.exchange")
            .withArgument("x-dead-letter-routing-key", "order.cancel")
            .build();
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return ExchangeBuilder.directExchange("order.delay.exchange").durable(true).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
            .to(orderDelayExchange()).with("order.delay");
    }
}
```

#### Producer (下单时发延迟消息)

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final RabbitTemplate rabbit;
    private final OrderMapper mapper;

    @Transactional
    public Long create(OrderCreateReq req) {
        OrderDO o = new OrderDO();
        o.setUserId(req.userId());
        o.setStatus("PENDING");
        o.setCreatedAt(LocalDateTime.now());
        mapper.insert(o);

        rabbit.convertAndSend("order.delay.exchange", "order.delay", o.getId(), m -> {
            m.getMessageProperties().setExpiration(String.valueOf(30 * 60 * 1000));
            return m;
        });
        return o.getId();
    }
}
```

#### Consumer

```java
@RabbitListener(queues = "order.cancel")
public void onTimeout(Long orderId, Channel ch, @Header(DELIVERY_TAG) long tag)
        throws IOException {
    try {
        OrderDO o = mapper.selectById(orderId);
        if (o == null) { ch.basicAck(tag, false); return; }
        if (!"PENDING".equals(o.getStatus())) {
            ch.basicAck(tag, false);
            return;
        }
        mapper.updateStatus(orderId, "CANCELLED");
        log.info("Order {} auto cancelled", orderId);
        ch.basicAck(tag, false);
    } catch (Exception e) {
        log.error("cancel order {} failed", orderId, e);
        ch.basicNack(tag, false, true);
    }
}
```

#### 注意

1. **TTL+DLX 的"队头阻塞"问题**: 队头消息没过期, 后面的消息也得等。 解决: 用 **rabbitmq_delayed_message_exchange** 插件, 每条消息独立计时。

2. **幂等**: 消费者必须判断"已支付"状态, 不能盲目取消。

3. **测试**:

```java
@Test
void unpaidOrderShouldBeCancelledAfterTtl() throws Exception {
    Long id = orderService.create(...);
    Thread.sleep(6_000);    // 测试用 5 秒 TTL
    OrderDO o = mapper.selectById(id);
    assertEquals("CANCELLED", o.getStatus());
}

@Test
void paidOrderShouldNotBeCancelled() throws Exception {
    Long id = orderService.create(...);
    orderService.pay(id);
    Thread.sleep(6_000);
    OrderDO o = mapper.selectById(id);
    assertEquals("PAID", o.getStatus());
}
```

## Q5 (综合题): 设计博客评论"异步通知" + 消息积压应急

### 需求

1. 用户评论后, 异步发送邮件 / 站内推送 / 增加积分 / 触发审核
2. 各下游独立失败重试, 互不影响
3. 死信处理 (重试 3 次仍失败的进人工队列)
4. 突发流量 (1w QPS) 时不丢消息, 消费者按节奏处理
5. 消息积压 50w 条时的应急方案

### 答案

#### 架构图

```
User
  │ POST /comment
  ▼
CommentController
  │
  ├─ DB.insert(comment)
  └─ outbox.insert(...) (同事务)
        │
        ▼ poller 异步发 MQ
        ▼ Exchange: notification (topic)
        ├─ binding "comment.created" → email.queue → EmailConsumer
        ├─ binding "comment.created" → push.queue  → PushConsumer
        ├─ binding "comment.created" → score.queue → ScoreConsumer
        └─ binding "comment.created" → audit.queue → AuditConsumer
```

#### 出口侧 (outbox)

```java
@Transactional
public Long create(CommentCreateReq req, Long uid) {
    CommentDO c = new CommentDO(...);
    mapper.insert(c);

    OutboxDO outbox = new OutboxDO(
        UUID.randomUUID().toString(),
        "comment.created",
        toJson(new CommentCreatedEvent(c.getId(), uid, c.getPostId())),
        "PENDING"
    );
    outboxMapper.insert(outbox);
    return c.getId();
}

@Scheduled(fixedDelay = 1000)
public void publishOutbox() {
    List<OutboxDO> pending = outboxMapper.selectPending(100);
    for (OutboxDO o : pending) {
        try {
            rabbit.convertAndSend("notification", o.getRoutingKey(), o.getPayload(),
                m -> { m.getMessageProperties().setMessageId(o.getMsgId()); return m; });
            outboxMapper.markSent(o.getId());
        } catch (Exception e) {
            log.error("Send outbox failed: {}", o, e);
        }
    }
}
```

> 出口表 (outbox pattern) 保证"DB 提交 + 消息发送"原子, 是分布式系统的标准做法。

#### 消费侧

```java
@RabbitListener(queues = "email.queue", concurrency = "10-50")
public void onEmail(CommentCreatedEvent evt, Channel ch, @Header(DELIVERY_TAG) long tag,
                     @Header(MESSAGE_ID) String msgId) throws IOException {
    String idKey = "processed:email:" + msgId;
    if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(idKey, "1", Duration.ofDays(7)))) {
        ch.basicAck(tag, false);
        return;
    }
    try {
        UserDO author = userMapper.selectByPostId(evt.postId());
        emailService.send(author.getEmail(), "您的文章有新评论", evt.toContent());
        ch.basicAck(tag, false);
    } catch (RecoverableException e) {
        redis.delete(idKey);
        ch.basicNack(tag, false, true);
    } catch (Exception e) {
        log.error("Send email permanently failed", e);
        ch.basicNack(tag, false, false);
    }
}
```

#### 死信队列

```java
@RabbitListener(queues = "email.dead")
public void onDead(CommentCreatedEvent evt, @Header(MESSAGE_ID) String msgId) {
    log.warn("EMAIL DEAD LETTER: {}", evt);
    deadLetterTable.insert(...);
    dingTalk.alert("邮件死信: " + msgId);
}
```

#### 消息积压 50w 应急

**1. 评估**

```bash
http://rabbit:15672/#/queues
# 看 email.queue 深度 = 50w, ready = 50w, unacked = 200
# 看 consumer count, message rates
```

**2. 扩容 Consumer**

```bash
kubectl scale deployment email-consumer --replicas=20
```

**3. 提高单消费者并发**

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 50
        max-concurrency: 100
        prefetch: 50
```

**4. 临时降级**

```java
@RabbitListener(queues = "email.queue")
public void onEmail(NotifyMsg msg, ...) {
    if (degradationConfig.isEmailDegraded() && !userService.isVip(msg.getUserId())) {
        ch.basicAck(tag, false);    // 跳过非 VIP
        return;
    }
    // normal
}
```

**5. 排查根因**: 慢查询 / 第三方 API throttle / JVM GC / 网络

**6. 复盘**: 加监控 + 自动扩容 + 评估迁移 Kafka

#### 面试 2 分钟讲法

> 评论通知用 RabbitMQ topic exchange, 业务侧用 outbox 模式保证 "DB + MQ 发送" 原子。 消费侧每个下游独立队列独立 consumer, 失败互不影响。 关键: 幂等 (Redis 7 天去重)、手动 ack、死信收集失败消息进人工兜底。 突发流量靠 MQ 削峰, Consumer concurrency 按 CPU 配。 真要 50w 积压, 应急: k8s 扩 Consumer pod 到 20, 同时按业务降级 (邮件只给 VIP, 推送做聚合), 然后查代码慢点。
