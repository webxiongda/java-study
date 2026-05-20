# Chapter 48 消息队列 - 实操 Demo

## Demo 目标

RabbitMQ 全套生产模式：业务交换机 + 死信队列 + 重试 + 幂等消费。 场景：**评论通知**（用户评论 → 异步发邮件 + 推送）。

## 前置条件

- 本地 RabbitMQ：`docker run -d --name rmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management`
- 管理台 `http://localhost:15672` （guest/guest）

## 增量依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

## 1. `application.yml`

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated      # 生产者确认
    publisher-returns: true                 # 不可路由时回调
    listener:
      simple:
        acknowledge-mode: manual            # 手动 ack
        prefetch: 10
        retry:
          enabled: true
          initial-interval: 1s
          max-attempts: 3
          multiplier: 2
```

## 2. 拓扑（业务 + 死信）

```java
@Configuration
public class MqTopology {

    public static final String EX_NOTIFY      = "blog.notify.exchange";
    public static final String Q_COMMENT      = "blog.notify.comment.queue";
    public static final String RK_COMMENT     = "comment.created";

    public static final String EX_DLX         = "blog.notify.dlx";
    public static final String Q_DLQ          = "blog.notify.dlq";

    @Bean DirectExchange notifyExchange() { return new DirectExchange(EX_NOTIFY, true, false); }
    @Bean DirectExchange dlx()            { return new DirectExchange(EX_DLX, true, false); }

    @Bean
    Queue commentQueue() {
        return QueueBuilder.durable(Q_COMMENT)
            .withArgument("x-dead-letter-exchange",    EX_DLX)
            .withArgument("x-dead-letter-routing-key", "comment.dead")
            .withArgument("x-message-ttl", 60_000)     // 单条 TTL 60s
            .build();
    }

    @Bean Queue dlq() { return QueueBuilder.durable(Q_DLQ).build(); }

    @Bean Binding bindComment() {
        return BindingBuilder.bind(commentQueue()).to(notifyExchange()).with(RK_COMMENT);
    }
    @Bean Binding bindDlq() {
        return BindingBuilder.bind(dlq()).to(dlx()).with("comment.dead");
    }
}
```

## 3. 生产者（带确认 + 唯一 messageId）

```java
@Service
@RequiredArgsConstructor
public class CommentPublisher {
    private static final Logger log = LoggerFactory.getLogger(CommentPublisher.class);
    private final RabbitTemplate rabbit;

    @PostConstruct
    void init() {
        rabbit.setConfirmCallback((corr, ack, cause) -> {
            if (!ack) log.error("publish nack id={} cause={}", corr != null ? corr.getId() : null, cause);
        });
        rabbit.setReturnsCallback(r ->
            log.error("unroutable msg key={} replyText={}", r.getRoutingKey(), r.getReplyText()));
    }

    public void publishCommentCreated(CommentEvent evt) {
        String mid = UUID.randomUUID().toString();
        MessageProperties props = new MessageProperties();
        props.setMessageId(mid);             // 用于消费端幂等
        props.setContentType("application/json");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        Message msg = rabbit.getMessageConverter()
            .toMessage(evt, props);
        rabbit.send(MqTopology.EX_NOTIFY, MqTopology.RK_COMMENT, msg,
            new CorrelationData(mid));
    }
}

public record CommentEvent(Long commentId, Long articleId, Long authorId, String content) {}
```

业务侧：

```java
@Transactional
public void addComment(CommentRequest req) {
    Comment c = commentMapper.insert(...);
    // ⚠️ 事务里发 MQ：consumer 可能在事务未提交时拿到事件 → 查不到 DB。
    // 用 TransactionSynchronization 在 afterCommit 发，或走事务消息表。
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            publisher.publishCommentCreated(new CommentEvent(c.getId(), c.getArticleId(), c.getAuthorId(), c.getContent()));
        }
    });
}
```

## 4. 消费者（幂等 + 手动 ack + 重试退避）

幂等键放 Redis：

```java
@Component
@RequiredArgsConstructor
public class CommentListener {
    private static final Logger log = LoggerFactory.getLogger(CommentListener.class);
    private final StringRedisTemplate redis;
    private final MailService mail;

    @RabbitListener(queues = MqTopology.Q_COMMENT)
    public void onMessage(Message msg, Channel ch,
                          @Payload CommentEvent evt) throws IOException {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String mid = msg.getMessageProperties().getMessageId();
        String dedupKey = "mq:dedup:comment:" + mid;

        // SETNX 幂等：同一 messageId 处理过则 ack 跳过
        Boolean first = redis.opsForValue()
            .setIfAbsent(dedupKey, "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(first)) {
            log.warn("duplicate messageId={}, skip", mid);
            ch.basicAck(tag, false);
            return;
        }

        try {
            mail.sendCommentNotice(evt);
            ch.basicAck(tag, false);
        } catch (RetryableException e) {
            // 重试由 Spring AMQP 拦截（spring.rabbitmq.listener.simple.retry）
            redis.delete(dedupKey);                  // 让重试不被幂等挡住
            throw e;
        } catch (Exception e) {
            log.error("permanent failure mid={}", mid, e);
            ch.basicNack(tag, false, false);         // 不重回队列 → 进 DLQ
        }
    }

    @RabbitListener(queues = MqTopology.Q_DLQ)
    public void onDead(Message msg) {
        log.error("DEAD: {}", new String(msg.getBody(), StandardCharsets.UTF_8));
        // 人工介入 / 告警 / 落库待修复
    }
}
```

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| 正常消费 | 调评论接口 → 控制台收到 mail 日志 |
| 幂等 | 管理台手动 publish 同 messageId 两次 → 只处理 1 次 |
| 重试 | 模拟瞬时异常 → 日志看到 1s/2s/4s 重试 3 次 |
| 死信 | 最终失败 → 消息进入 `blog.notify.dlq`，管理台可见 |
| 持久化 | 重启 RMQ 后 DLQ 消息仍在 |

## 常见坑

- 事务内发 MQ → 事务回滚但消息已发。 必须 `afterCommit` 或事务消息表。
- 自动 ack + 抛异常 → 消息直接丢。 用 `acknowledge-mode: manual`。
- 没设 DLX → 消息满了或一直 nack 重投，CPU 打满。
- `prefetch` 设大 + 慢消费者 → 单 consumer 占了全部消息，其他空转。 一般 10-50。
- 幂等键过短或没设过期 → Redis 持续膨胀。 用 messageId + 业务键，TTL 1-7 天。

## 提交

```bash
git commit -m "chapter 48: rabbitmq dlq + idempotent consumer + retry"
```
