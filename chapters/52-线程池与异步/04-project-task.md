# Chapter 52 线程池与异步 - 项目任务

## 任务概述

给博客 API 配 **统一异步基础设施**:

1. 业务异步池 (邮件 / 推送 / 统计) + 文件处理池 (缩略图 / 转码) + 调度池 (定时任务) 三池分离
2. `@Async` 配置 + 全局异常处理
3. Micrometer 指标上报 (活跃 / 排队 / 完成数 / 拒绝数)
4. 优雅关闭
5. 试用 JDK 21 虚拟线程

## 业务背景

博客现有的异步需求: 评论后发邮件 / 推送、上传后生成缩略图、定时清理过期 token、定时聚合 PV 统计。 如果都共用同一个池 (或更糟, 共用 ForkJoinPool.commonPool), 一个慢任务 (比如 SMTP 超时) 会拖死整个异步链路。 必须按业务隔离。

## 任务拆解

### Step 1: 三池配置 (40 分钟)

```java
@Configuration
@EnableAsync
@EnableScheduling
public class ExecutorConfig {

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        return build("biz-", 4, 16, 500);
    }

    @Bean("fileExecutor")
    public Executor fileExecutor() {
        return build("file-", 2, 8, 100);
    }

    @Bean("scheduleExecutor")
    public ThreadPoolTaskScheduler scheduleExecutor() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("sched-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(30);
        s.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return s;
    }

    private Executor build(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(core);
        t.setMaxPoolSize(max);
        t.setQueueCapacity(queue);
        t.setKeepAliveSeconds(60);
        t.setThreadNamePrefix(prefix);
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        t.setWaitForTasksToCompleteOnShutdown(true);
        t.setAwaitTerminationSeconds(30);
        t.initialize();
        return t;
    }
}
```

### Step 2: 异步 Service (30 分钟)

```java
@Service
@RequiredArgsConstructor
public class CommentNotifyService {
    private final MailSender mailSender;

    @Async("bizExecutor")
    public CompletableFuture<Void> notifyAuthor(Long postAuthorId, String commenterName) {
        try {
            User author = userMapper.selectById(postAuthorId);
            mailSender.send(author.getEmail(), "您的文章收到新评论",
                commenterName + " 评论了您的文章");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("notify failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}

@Service
public class ThumbnailService {
    @Async("fileExecutor")
    public CompletableFuture<String> generate(String originalKey) {
        // 读 MinIO → Scalr 缩放 → 写回 MinIO
        ...
        return CompletableFuture.completedFuture(thumbKey);
    }
}
```

### Step 3: 全局异步异常处理 (15 分钟)

```java
@Configuration
public class AsyncExceptionConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("async error: method={} params={}", method.getName(), Arrays.toString(params), ex);
            Metrics.counter("async.error", "method", method.getName()).increment();
        };
    }
}
```

### Step 4: 监控指标 (30 分钟)

```java
@Component
@RequiredArgsConstructor
public class ExecutorMetrics {
    private final MeterRegistry registry;
    private final Map<String, Executor> executors;

    @PostConstruct
    public void register() {
        executors.forEach((name, exec) -> {
            if (exec instanceof ThreadPoolTaskExecutor t) {
                ThreadPoolExecutor tp = t.getThreadPoolExecutor();
                Gauge.builder("executor.active", tp, ThreadPoolExecutor::getActiveCount)
                     .tag("name", name).register(registry);
                Gauge.builder("executor.queue.size", tp, x -> x.getQueue().size())
                     .tag("name", name).register(registry);
                Gauge.builder("executor.queue.remaining", tp, x -> x.getQueue().remainingCapacity())
                     .tag("name", name).register(registry);
                Gauge.builder("executor.completed", tp, ThreadPoolExecutor::getCompletedTaskCount)
                     .tag("name", name).register(registry);
                Gauge.builder("executor.pool.size", tp, ThreadPoolExecutor::getPoolSize)
                     .tag("name", name).register(registry);
            }
        });
    }
}
```

### Step 5: 试用虚拟线程 (15 分钟)

`application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

写一个对比测试:
```java
@Test
void compareThroughput() throws Exception {
    // 1000 个模拟阻塞 IO 任务 (sleep 100ms)
    // 平台池 (16): 总耗时 ~7s
    // 虚拟线程: 总耗时 ~120ms
}
```

### Step 6: 测试 (30 分钟)

```java
@SpringBootTest
class CommentNotifyServiceTest {
    @Autowired CommentNotifyService svc;

    @Test
    void notify_async_returnsImmediately() throws Exception {
        long start = System.currentTimeMillis();
        CompletableFuture<Void> f = svc.notifyAuthor(1L, "alice");
        long cost = System.currentTimeMillis() - start;
        assertThat(cost).isLessThan(50);   // 应立即返回
        f.get(5, TimeUnit.SECONDS);
    }

    @Test
    void rejectedTask_callerRuns() throws Exception {
        // 用极小池 (1,1,1) 测拒绝策略, 提交 3 个任务
        // 验证调用线程也跑了任务
    }
}
```

## 交付物

- [ ] `ExecutorConfig` 三池分离 (biz / file / sched)
- [ ] `AsyncExceptionConfig` 全局异常
- [ ] `ExecutorMetrics` 上报 5 个指标
- [ ] `CommentNotifyService` / `ThumbnailService` 实际用 @Async
- [ ] application.yml 启用虚拟线程 + Grafana dashboard JSON
- [ ] 测试 ≥ 5 个 (异步生效 / 异常处理 / 拒绝策略 / 池大小 / 关闭等待)
- [ ] README "异步架构" + 三池容量决策依据
- [ ] git commit: `ch52: thread pool isolation + @Async + metrics + virtual threads`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 三池隔离 | jstack 能看到 biz-* / file-* / sched-* 三种命名 |
| @Async 生效 | Controller 调用立即返回, 任务在后台跑 |
| 拒绝策略 | 队列满后调用线程跑 (CallerRuns), 不抛异常 |
| 优雅关闭 | `kill -15` 后, 队列里任务跑完才退出 (最多 30s) |
| 异常处理 | 异步方法 throw 异常, 日志能看到 method + params |
| 指标 | Prometheus `/actuator/prometheus` 能看到 `executor_active{name="bizExecutor"}` |
| 虚拟线程 | 启用后, 1000 个阻塞 IO 任务总耗时 < 500ms (对比平台池 7s) |

## 扩展挑战

1. **Hystrix 风格的舱壁隔离**: 不同业务用不同池, 一个池打满不影响其他
2. **DynamicThreadPool**: 接 Apollo / Nacos 配置中心, 在线调整 core/max/queue 不重启
3. **TTL ThreadLocal**: 用阿里 transmittable-thread-local 透传 traceId 到异步任务
4. **CompletableFuture + Resilience4j**: 加熔断和重试
5. **JDK 21 StructuredConcurrency** (预览): 父子任务统一管理生命周期
