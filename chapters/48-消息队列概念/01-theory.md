# Chapter 48 消息队列概念 - 理论篇

## 一、学习定位

消息队列 (MQ) 是后端项目从"单体同步"走向"分布式异步"的关键。学到本章, 你能解释:

- 为什么有些操作要异步
- RabbitMQ / Kafka / RocketMQ 各自定位
- "至少一次 / 至多一次 / 精确一次" 语义如何实现
- 重试 / 死信 / 延迟队列 / 顺序消息
- 生产可靠性: 消息丢失 / 重复消费 / 积压

- 优先级: L2 项目常用 + L3 面试高频
- 预计投入: 4 小时
- 阶段产出: 评论通知异步化 + 死信队列处理失败 + 幂等消费者

## 二、核心概念

### 1. 异步解耦 (Why MQ)

**同步调用的问题**:

```
用户发评论 → [写 DB → 发邮件 → 发推送 → 增积分 → 触发反垄断检查]
            ←─ 全部完成才返回 (5 秒)
```

任何一步挂了, 用户失败。 邮件服务慢, 整个接口慢。

**异步**:

```
用户发评论 → [写 DB + 发 MQ 消息] → 返回 (50ms)
                  │
                  ├─→ 邮件消费者
                  ├─→ 推送消费者
                  ├─→ 积分消费者
                  └─→ 反垄断消费者
```

**MQ 提供四种能力**:

1. **解耦**: Producer 不需要知道 Consumer 是谁、几个
2. **异步**: Producer 不等 Consumer 完成
3. **削峰**: Producer 瞬时 1w QPS, Consumer 按自己速度处理
4. **广播**: 一条消息多个消费方都能收到

### 2. 三大主流 MQ 对比

| 维度 | RabbitMQ | Kafka | RocketMQ |
|---|---|---|---|
| 定位 | 通用消息队列 | 流处理 / 日志管道 | 业务消息 (阿里出品) |
| 协议 | AMQP 0.9.1 | 自有二进制 | 自有 |
| 吞吐 | 1w-10w QPS | 100w+ QPS | 10w QPS |
| 延迟 | < 1ms | 5-10ms | < 1ms |
| 顺序消息 | 单队列保证 | 单分区保证 | 单队列保证 |
| 延迟消息 | 插件 (rabbitmq_delayed_message_exchange) | 业务自己做 | 原生支持 (固定级别) |
| 事务 | 弱 | 较强 (事务消息) | 强 (事务消息) |
| 死信 | 原生 (x-dead-letter-exchange) | 业务做 | 原生 |
| 学习曲线 | 中 (Exchange/Binding/Queue) | 中 (Topic/Partition/Offset) | 中 (Topic/Tag) |
| 适用 | 业务异步、低延迟 | 日志、监控、数据管道 | 电商、订单、长事务 |

**博客项目用什么?**
- 评论通知 / 邮件 / 推送 → **RabbitMQ** (灵活、延迟小、支持复杂路由)
- 用户行为日志 / 点击流 → **Kafka** (高吞吐, 下游接 Flink)
- 订单支付 (假设有付费功能) → **RocketMQ** (事务消息)

### 3. RabbitMQ 模型

```
Producer ──→ Exchange ──(routing key + binding)──→ Queue ──→ Consumer
```

**四种 Exchange**:

| Exchange | 路由规则 |
|---|---|
| `direct` | routing key 完全匹配 binding key |
| `fanout` | 广播, 忽略 routing key |
| `topic` | routing key 通配匹配 (`order.*` / `*.created`) |
| `headers` | 按 header 匹配 (少用) |

**典型路由设计**:

```
Exchange: notification (topic)
├─ binding "email.*" → email.queue
├─ binding "push.*"  → push.queue
└─ binding "*.urgent" → urgent.queue

Producer 发 routing key:
  email.welcome    → email.queue
  push.comment     → push.queue
  email.urgent     → email.queue + urgent.queue
```

### 4. 消息可靠性: 三个环节

#### Producer 端

- **publisher confirm**: Broker 收到后 ack 给 Producer。 没收到 ack 重发
- **return callback**: routing key 找不到 queue 时回调 (avoid 黑洞)
- **持久化**: 消息 deliveryMode=2, 不然 Broker 重启丢

```java
@Bean
public RabbitTemplate template(ConnectionFactory cf) {
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setConfirmCallback((data, ack, cause) -> {
        if (!ack) log.error("Message lost: {}", cause);
    });
    t.setReturnsCallback(returned -> log.error("No queue for: {}", returned));
    t.setMandatory(true);
    return t;
}
```

#### Broker 端

- 队列声明 `durable=true`
- 消息 `MessageProperties.PERSISTENT_TEXT_PLAIN`
- 集群: 镜像队列 / Quorum Queue (RabbitMQ 3.8+)

#### Consumer 端

- **手动 ack** (`AcknowledgeMode.MANUAL`): 业务处理完才 ack, 异常不 ack → 重投
- **prefetch**: 一次最多拿几条 (默认 250, 太多会一个 Consumer 抢光)
- **死信**: 重试 N 次仍失败 → 进死信队列 (DLX), 人工或专门 worker 处理

#### 消息语义

| 语义 | 含义 | 实现 |
|---|---|---|
| **At most once** | 至多一次, 可能丢 | 不需要 ack, 性能最高 |
| **At least once** | 至少一次, 可能重 | 手动 ack + 重投 (大多 MQ 默认) |
| **Exactly once** | 精确一次 | 极难, 业务层做幂等 + 事务消息 |

**实战策略**: At least once + 消费者幂等 = 业务层 Exactly once。

### 5. 幂等消费

消费者必须能处理"重复消息":

```java
@RabbitListener(queues = "comment.notify")
public void onComment(NotifyMsg msg, Channel channel, @Header(DELIVERY_TAG) long tag)
        throws IOException {
    String idKey = "msg:processed:" + msg.getMsgId();
    Boolean fresh = redis.opsForValue().setIfAbsent(idKey, "1", Duration.ofDays(7));
    if (!Boolean.TRUE.equals(fresh)) {
        // 已处理过, 直接 ack
        channel.basicAck(tag, false);
        return;
    }
    try {
        emailService.send(msg);
        channel.basicAck(tag, false);
    } catch (Exception e) {
        // 业务异常, 删幂等键, 让下次重新处理
        redis.delete(idKey);
        channel.basicNack(tag, false, true);
    }
}
```

**msgId 来源**:
- Producer 生成 UUID 塞进 MessageProperties.messageId
- 或业务唯一键 (订单号 / 评论 id)

### 6. 重试与死信

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000
          multiplier: 2
          max-interval: 10000
        default-requeue-rejected: false   # 失败后不重入原队列, 进 DLX
```

死信交换机配置:

```java
@Bean
public Queue commentQueue() {
    return QueueBuilder.durable("comment.notify")
        .withArgument("x-dead-letter-exchange", "notification.dlx")
        .withArgument("x-dead-letter-routing-key", "comment.dead")
        .build();
}

@Bean
public Queue commentDeadQueue() {
    return QueueBuilder.durable("comment.dead").build();
}
```

死信场景:
- 消费失败超过 max-attempts
- 消息 TTL 过期
- 队列满

### 7. 延迟消息

**RabbitMQ 三种实现**:

1. **延迟插件** (rabbitmq_delayed_message_exchange): 最简单, 单条消息独立 TTL
2. **TTL + DLX**: 消息进队列 A 设置 TTL, 过期进 DLX 队列 B, B 真正消费
3. **业务定时表**: 自己写 schedule

```java
rabbit.convertAndSend("delayed.exchange", "comment.delayed", msg, m -> {
    m.getMessageProperties().setDelayLong(60_000L);   // 60 秒后投递
    return m;
});
```

**典型用途**: 订单 30 分钟未支付自动取消、文章定时发布、缓存延迟双删。

### 8. 顺序消息

**为什么会乱序?**

```
Producer ──→ Queue [m1, m2, m3]
                       │
                       ├─→ Consumer A 拉 m1 (慢)
                       └─→ Consumer B 拉 m2 (快, 先完成)
```

**保证顺序**:
- 单队列 + 单消费者 + prefetch=1 (吞吐低)
- 按业务 key hash 路由到不同队列, 单队列单消费者 (e.g. 按 userId)
- Kafka: 按 key 分到同一 partition, 单 partition 顺序保证

### 9. 消息积压

**症状**: Queue 深度 100w+, 消费跟不上。

**原因**:
- 消费者太少
- 消费者代码慢 (DB 慢, 外部 API 慢)
- 消费者全部挂

**应急**:
1. **扩容消费者** (k8s 加 pod / docker scale)
2. **临时跳过非关键** (catch 后 ack, 走兜底)
3. **降级**: 把消息搬到容量更大的队列, 后续慢慢消化
4. **修代码**: 慢查询 / N+1 等

### 10. 事务消息 (RocketMQ 特色)

```
1. 发"半消息" (对消费者不可见)
2. 执行本地事务 (写 DB)
3. 提交 / 回滚半消息
4. 如长时间没确认, Broker 回查 Producer
```

解决"DB 提交了但 MQ 没发" / "MQ 发了但 DB 失败"。

RabbitMQ 没原生事务消息, 业务层用"本地消息表"模式:

```
1. 业务事务里同时写 DB 数据 + 写 outbox 表
2. 单独的 outbox-poller 异步发 MQ + 删 outbox
```

## 三、工作原理

| 维度 | 要点 | 你需要能说清 |
|---|---|---|
| 入口 | Producer 调 RabbitTemplate.convertAndSend / KafkaTemplate.send | API、批量、Pipeline |
| 配置 | virtual-host / exchange / queue / binding / prefetch / retry | 各组件作用 |
| 执行 | Producer → Exchange → routing → Queue → Consumer ack | 每一步 ack / nack / requeue 行为 |
| 边界 | Broker 宕机 / 网络分区 / Consumer 阻塞 | 持久化 / 集群 / 死信 / 限流 |
| 验证 | RabbitMQ 管理台 / Kafka kafka-consumer-groups / 业务幂等表 | 看队列深度、消费速度、重试次数 |

## 四、项目落地清单

博客 API 的 MQ 接入:

1. **评论通知**: 评论入库后发 MQ → email/push/积分 三个消费者并行
2. **缓存延迟双删**: update 后发延迟 1s 消息 → 消费者删缓存
3. **文章定时发布**: 创建时设状态 SCHEDULED, 发延迟消息 → 到点改 PUBLISHED
4. **审计日志**: 所有写操作发到 audit queue, 单独服务消费写审计表
5. **死信处理**: dead queue 有专门 worker, 重试 3 次仍失败的发钉钉告警

## 五、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| 自动 ack | 业务异常消息丢 | 手动 ack |
| 没设 prefetch | 一个 Consumer 抢光所有消息 | prefetch=10-50 |
| 队列 / 消息没持久化 | Broker 重启全丢 | durable=true + persistent |
| 消息没 confirm | Broker 没收到也不知道 | 开 publisher confirm |
| 没幂等 | 重复消费导致重复发邮件 / 重复扣款 | Redis 幂等键 / 业务唯一索引 |
| 异常后无脑 requeue | 死循环消费同一条 | retry + DLX |
| 没设 DLX | 失败消息丢 | 配 x-dead-letter-exchange |
| 大消息直接进 MQ | Broker 内存 / 网络压力 | MQ 只发 id, 数据存对象存储 |
| 顺序消息用多消费者 | 乱序 | 单消费者 / hash 路由 |
| 积压不监控 | 业务断流不知道 | 队列深度告警 |

## 六、面试高频问题

1. 为什么用消息队列? 没有它会怎样?
2. RabbitMQ / Kafka / RocketMQ 三选一, 你选哪个, 为什么?
3. 怎么保证消息不丢失? 三个环节分别讲
4. 重复消费怎么办?
5. 消息积压 100w 你怎么应急?
6. 消费失败的消息怎么处理? 死信队列怎么用?
7. 延迟消息有几种实现?
8. 顺序消息怎么保证?
9. 事务消息和本地消息表的区别?
10. Exchange 四种, 各自的应用场景?

## 七、对比: 同步 vs 异步 决策

| 业务特征 | 推荐 |
|---|---|
| 用户必须立即看到结果 | 同步 |
| 多个下游, 任意一个慢 | 异步 |
| 下游不可靠 (第三方 API) | 异步 + 重试 |
| 高并发突发 | 异步 + MQ 削峰 |
| 强一致 (转账) | 同步 (或事务消息) |
| 大量小事件 (日志 / 行为) | 异步 + Kafka |
