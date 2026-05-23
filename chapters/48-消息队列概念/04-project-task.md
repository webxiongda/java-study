# Chapter 48 消息队列概念 - 项目任务

## 任务概述

把博客评论通知改成 **MQ 异步**: 评论入库后 → 一条消息扇出到 4 个消费者 (邮件 / 站内推送 / 加积分 / 触发审核), 各自独立失败重试, 死信进人工处理队列。 同时实现 "30 分钟未审核评论自动隐藏" 的延迟消息场景。

## 业务背景

47 章前评论接口是同步的: 评论入库 → 发邮件 → 发推送 → 加积分 → 调审核, 5 个调用串行, 第三方 API 慢一下整个接口超时。

引入 RabbitMQ 后:
- 用户响应时间从 500ms 降到 50ms
- 邮件 / 推送服务挂掉不影响下单
- 各下游独立扩缩容
- 突发流量 (热门文章瞬间 500 条评论) 不再压垮邮件服务

## 任务拆解

### Step 1: 起 RabbitMQ + 接入 (30 分钟)

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin \
  rabbitmq:3.13-management
```

访问 http://localhost:15672 (admin/admin)。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
spring:
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: 5672
    username: admin
    password: admin
    publisher-confirm-type: correlated
    publisher-returns: true
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

### Step 2: 拓扑配置 (40 分钟)

```java
@Configuration
public class CommentMqConfig {

    public static final String EXCHANGE = "comment.exchange";
    public static final String DLX = "comment.dlx";

    @Bean
    public TopicExchange exchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange dlx() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    private Queue makeQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", name + ".dead")
            .build();
    }

    @Bean public Queue emailQueue() { return makeQueue("comment.email"); }
    @Bean public Queue pushQueue()  { return makeQueue("comment.push"); }
    @Bean public Queue scoreQueue() { return makeQueue("comment.score"); }
    @Bean public Queue auditQueue() { return makeQueue("comment.audit"); }

    @Bean public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(exchange()).with("comment.created");
    }
    @Bean public Binding pushBinding() {
        return BindingBuilder.bind(pushQueue()).to(exchange()).with("comment.created");
    }
    @Bean public Binding scoreBinding() {
        return BindingBuilder.bind(scoreQueue()).to(exchange()).with("comment.created");
    }
    @Bean public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(exchange()).with("comment.created");
    }

    @Bean public Queue emailDead() { return QueueBuilder.durable("comment.email.dead").build(); }
    @Bean public Binding emailDeadBinding() {
        return BindingBuilder.bind(emailDead()).to(dlx()).with("comment.email.dead");
    }
    // ... 其他死信队列
}
```

启动后访问 http://localhost:15672, 看 Queues / Exchanges 都已就绪。

### Step 3: Producer + Outbox (45 分钟)

```sql
CREATE TABLE outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msg_id VARCHAR(64) NOT NULL UNIQUE,
  routing_key VARCHAR(100) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  sent_at DATETIME,
  INDEX idx_status (status)
);
```

```java
@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentMapper cm;
    private final OutboxMapper om;

    @Transactional
    public Long create(CommentCreateReq req, Long uid) {
        CommentDO c = new CommentDO(null, req.postId(), uid, req.content(),
                                     "PENDING_REVIEW", LocalDateTime.now());
        cm.insert(c);
        om.insert(new OutboxDO(
            null,
            UUID.randomUUID().toString(),
            "comment.created",
            JSON.toJSONString(new CommentCreatedEvent(c.getId(), uid, c.getPostId())),
            "PENDING",
            LocalDateTime.now(), null));
        return c.getId();
    }
}

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxMapper om;
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        List<OutboxDO> pending = om.selectPending(100);
        for (OutboxDO o : pending) {
            try {
                rabbit.convertAndSend(CommentMqConfig.EXCHANGE, o.getRoutingKey(), o.getPayload(),
                    m -> { m.getMessageProperties().setMessageId(o.getMsgId()); return m; });
                om.markSent(o.getId());
            } catch (Exception e) {
                log.error("Send outbox {} failed", o.getId(), e);
            }
        }
    }
}
```

### Step 4: 四个 Consumer (60 分钟)

```java
@Component
@RequiredArgsConstructor
public class EmailConsumer {
    private final EmailService email;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final StringRedisTemplate redis;

    @RabbitListener(queues = "comment.email", concurrency = "5-20")
    public void onMsg(String payload, Channel ch,
                       @Header(DELIVERY_TAG) long tag,
                       @Header(MESSAGE_ID) String msgId) throws IOException {
        String idKey = "p:email:" + msgId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(idKey, "1", Duration.ofDays(7)))) {
            ch.basicAck(tag, false); return;
        }
        try {
            CommentCreatedEvent evt = JSON.parseObject(payload, CommentCreatedEvent.class);
            UserDO author = userMapper.selectByPostId(evt.postId());
            if (author == null || author.getEmail() == null) {
                ch.basicAck(tag, false); return;
            }
            email.send(author.getEmail(), "您的文章有新评论",
                "评论 ID: " + evt.commentId());
            ch.basicAck(tag, false);
        } catch (RecoverableException e) {
            redis.delete(idKey);
            ch.basicNack(tag, false, true);
        } catch (Exception e) {
            log.error("Send email permanently failed", e);
            ch.basicNack(tag, false, false);
        }
    }
}

// 同样实现 PushConsumer / ScoreConsumer / AuditConsumer
```

### Step 5: 死信处理 (30 分钟)

```java
@Component
public class DeadLetterHandler {

    @RabbitListener(queues = "comment.email.dead")
    public void onEmailDead(String payload, @Header(MESSAGE_ID) String msgId) {
        log.warn("EMAIL DEAD: msgId={}, payload={}", msgId, payload);
        deadLetterTable.insert(...);
        dingTalk.alert("评论邮件死信: " + msgId);
    }

    // push / score / audit 各自的死信
}
```

### Step 6: 延迟消息 - 30 分钟未审核自动隐藏 (45 分钟)

```java
@Configuration
public class CommentDelayConfig {

    @Bean
    public Queue auditTimeoutDelayQueue() {
        return QueueBuilder.durable("comment.audit.timeout.delay")
            .withArgument("x-dead-letter-exchange", "comment.exchange")
            .withArgument("x-dead-letter-routing-key", "comment.audit.timeout")
            .build();
    }

    @Bean
    public Queue auditTimeoutQueue() {
        return QueueBuilder.durable("comment.audit.timeout").build();
    }

    @Bean
    public Binding auditTimeoutBinding() {
        return BindingBuilder.bind(auditTimeoutQueue())
            .to(commentExchange()).with("comment.audit.timeout");
    }
}
```

```java
// 创建评论时也发延迟消息
public Long create(CommentCreateReq req, Long uid) {
    CommentDO c = new CommentDO(...);
    cm.insert(c);

    // 普通通知 outbox
    om.insert(...);

    // 延迟消息: 30 分钟后审核兜底
    rabbit.convertAndSend("", "comment.audit.timeout.delay", c.getId(), m -> {
        m.getMessageProperties().setExpiration(String.valueOf(30 * 60 * 1000));
        return m;
    });
    return c.getId();
}

@RabbitListener(queues = "comment.audit.timeout")
public void onAuditTimeout(Long commentId, Channel ch, @Header(DELIVERY_TAG) long tag)
        throws IOException {
    try {
        CommentDO c = cm.selectById(commentId);
        if (c == null || !"PENDING_REVIEW".equals(c.getStatus())) {
            ch.basicAck(tag, false); return;
        }
        // 30 分钟还没审核, 自动隐藏
        cm.updateStatus(commentId, "HIDDEN");
        log.warn("Comment {} auto hidden due to audit timeout", commentId);
        ch.basicAck(tag, false);
    } catch (Exception e) {
        ch.basicNack(tag, false, true);
    }
}
```

### Step 7: 测试 (45 分钟)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommentNotifyIT {

    @Container static RabbitMQContainer rabbit =
        new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @MockBean EmailService email;

    @Test
    void commentTriggersEmail() {
        commentService.create(new CommentCreateReq(1L, "hello"), 42L);
        Awaitility.await().atMost(5, SECONDS).untilAsserted(() ->
            verify(email).send(anyString(), eq("您的文章有新评论"), anyString()));
    }

    @Test
    void duplicateMessageProcessedOnce() {
        // 手动 publish 两条相同 msgId, 验证 email.send 只调一次
    }

    @Test
    void permanentFailureGoesToDeadLetter() {
        // mock email.send 抛 InvalidEmailException, 验证死信队列有消息
    }
}
```

### Step 8: 监控与告警 (30 分钟)

- Actuator 暴露 rabbit metrics
- Prometheus 抓 `rabbitmq_queue_messages` (队列深度)
- Grafana 面板: 4 个业务队列深度 + 死信队列计数
- 告警规则: 队列深度 > 1w 持续 5 分钟 → 钉钉
- 告警规则: 死信队列任意消息 → 立即钉钉

## 交付物

- [ ] RabbitMQ 拓扑: 1 个 topic exchange + 4 个业务队列 + 4 个死信队列
- [ ] `CommentService` + outbox 表
- [ ] `OutboxPublisher` 定时发 MQ
- [ ] 4 个 Consumer (email / push / score / audit), 含幂等 + 手动 ack + 异常分类
- [ ] 死信处理器, 写死信表 + 钉钉告警
- [ ] 30 分钟延迟消息 (TTL + DLX) 实现审核超时隐藏
- [ ] Testcontainers 集成测试 ≥ 4 个
- [ ] Prometheus + Grafana 监控面板
- [ ] README 加 "消息流转图" + "积压应急预案"
- [ ] git commit: `ch48: comment async notify with rabbitmq + outbox + delayed`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 异步生效 | curl POST /api/v1/comments 应在 100ms 内返回, 邮件 / 推送等异步发出 |
| 扇出正确 | 一次评论后, 4 个队列各收到 1 条消息 |
| 幂等 | 同 msgId 重发 10 次, email.send 只被调用 1 次 |
| 持久化 | RabbitMQ 重启后队列仍在, 未消费消息不丢 |
| 死信工作 | mock email.send 抛 InvalidEmailException, 重试 3 次后进死信队列 |
| 延迟消息 | 创建评论 30 秒后 (测试用) 状态变 HIDDEN (前提: 未审核) |
| 已审核不被覆盖 | 创建后 5 秒内手动通过审核, 30 秒后状态保持 APPROVED |
| Outbox 一致 | 业务事务回滚后, outbox 表无对应记录, MQ 没发出 |
| 监控告警 | 强行积压 1.1w 条消息, 告警触发 |

## 扩展挑战

1. **Kafka 行为日志**: 用户的点赞 / 浏览 / 收藏发到 Kafka, 下游 Flink 实时计算热度榜。 体验 Kafka partition / consumer group / offset 管理。
2. **本地事务消息表 + Canal**: 替代 outbox + poller, 用 Canal 监听 outbox 表 INSERT 实时发 MQ, 延迟从秒级到毫秒级。
3. **消息追踪 / Trace**: 在 message headers 里塞 TraceId, 从 Producer 到 Consumer 全链路日志可关联。
4. **流量回放**: 把 7 天内消息持久化到 S3, 故障时可重放任意时间段消息。
5. **Sidecar 改造**: 把 MQ 客户端抽到 sidecar 进程 (Envoy / Dapr), 业务代码与 MQ 解耦。
6. **延迟插件**: 装 `rabbitmq_delayed_message_exchange` 插件替代 TTL+DLX, 解决队头阻塞。
