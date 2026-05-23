# Chapter 52 线程池与异步 - 自测与验收

## Q1 概念: ThreadPoolExecutor 7 参数 + 任务调度流程

```java
new ThreadPoolExecutor(
    corePoolSize,      // 核心线程数 (常驻)
    maximumPoolSize,   // 最大线程数 (峰值)
    keepAliveTime, unit, // 非核心线程空闲超时
    workQueue,         // 任务队列
    threadFactory,     // 线程工厂 (给线程命名很重要)
    handler            // 拒绝策略
);
```

**调度顺序**:
```
1. 线程数 < core → 新建核心线程跑
2. 线程数 ≥ core → 进队列
3. 队列满 + 线程数 < max → 新建非核心线程跑
4. 队列满 + 线程数 = max → 走 RejectedExecutionHandler
```

**4 种拒绝策略**:
| 策略 | 行为 | 适用 |
|---|---|---|
| AbortPolicy | 抛 RejectedExecutionException (默认) | 调用方需感知 |
| CallerRunsPolicy | 调用线程自己跑 | **反向限流, 最常用** |
| DiscardPolicy | 静默丢 | 日志类 |
| DiscardOldestPolicy | 丢最老的, 再尝试入队 | 偏好新任务 |

**经典错答**: 以为先扩到 max 再入队 → 错。 真实是 **先入队再扩**, 这就是为什么 `LinkedBlockingQueue` 不传容量会让 max 形同虚设 (队列无限大永远不会触发扩容)。

---

## Q2 概念: Executors 的 4 个工厂方法为什么生产禁用?

| 方法 | 内部实现 | 风险 |
|---|---|---|
| `newFixedThreadPool(n)` | core=max=n, **无界 LinkedBlockingQueue** | 任务堆积 → 堆 OOM |
| `newSingleThreadExecutor()` | 同上, n=1 | 同上 |
| `newCachedThreadPool()` | core=0, max=**Integer.MAX_VALUE**, SynchronousQueue | 任务暴涨 → 线程暴涨 → OS OOM |
| `newScheduledThreadPool(n)` | DelayedWorkQueue (无界) | 同 Fixed |

**生产正确做法**:
```java
new ThreadPoolExecutor(
    core, max,
    60, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(1000),                       // 有界
    new ThreadFactoryBuilder().setNameFormat("biz-%d").build(), // 命名
    new ThreadPoolExecutor.CallerRunsPolicy()             // 反向限流
);
```

或者用 Spring `ThreadPoolTaskExecutor` + Bean 注入。

---

## Q3 代码题: 写一个生产可用的 @Async 配置

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(cores * 2);
        t.setMaxPoolSize(cores * 4);
        t.setQueueCapacity(500);
        t.setKeepAliveSeconds(60);
        t.setThreadNamePrefix("biz-async-");
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        t.setWaitForTasksToCompleteOnShutdown(true);
        t.setAwaitTerminationSeconds(30);
        t.initialize();
        return t;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("async error in {} args={}", method.getName(), Arrays.toString(params), ex);
    }
}

@Service
public class MailService {
    @Async("bizExecutor")
    public CompletableFuture<Void> sendAsync(String to, String body) {
        smtp.send(to, body);
        return CompletableFuture.completedFuture(null);
    }
}
```

**关键点**:
- `setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(30)` → 优雅关闭
- `CallerRunsPolicy` → 队列满时调用线程自己执行, 起到反压作用
- `@Async` 方法返回 `CompletableFuture<T>` 而不是 `Future<T>`, 才能用 `thenApply` 等链式 API
- `getAsyncUncaughtExceptionHandler` 处理 void 返回的 @Async 异常 (有返回值会进 future)
- **@Async 同类调用不生效** (走的不是代理), 必须从另一个 Bean 调

---

## Q4 代码: CompletableFuture 编排 (并行查 + 任一失败降级)

需求: 文章详情页要查 文章 / 作者 / 评论 三个数据, 并行查, 评论挂了不影响主流程显示空列表。

```java
@Service
@RequiredArgsConstructor
public class PostDetailService {
    private final ExecutorService pool = Executors.newFixedThreadPool(16,
        Thread.ofPlatform().name("detail-", 0).factory());

    public PostDetailVO get(Long postId) {
        CompletableFuture<PostVO> postF = CompletableFuture
            .supplyAsync(() -> postMapper.selectById(postId), pool);

        CompletableFuture<UserVO> authorF = postF
            .thenApplyAsync(p -> userMapper.selectById(p.getUserId()), pool);

        CompletableFuture<List<CommentVO>> commentF = CompletableFuture
            .supplyAsync(() -> commentMapper.latest(postId, 5), pool)
            .exceptionally(ex -> {
                log.warn("comment fetch failed, fallback to empty", ex);
                return List.of();
            });

        try {
            CompletableFuture.allOf(postF, authorF, commentF)
                .orTimeout(1, TimeUnit.SECONDS)
                .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException)
                throw new BizException(504, "聚合超时");
            throw new BizException(500, "聚合失败");
        }

        return new PostDetailVO(postF.join(), authorF.join(), commentF.join());
    }
}
```

**易错点**:
- 不传 Executor → 用 `ForkJoinPool.commonPool` → 被其他业务污染
- `exceptionally` 只能处理"上游"异常, 用 `handle` 同时处理结果和异常
- `orTimeout` (JDK 9+) 比自己 schedule 取消更优雅
- `allOf().join()` 不返回结果, 各 future 自己 `.join()` 取
- `cancel(true)` 只是不接收结果, 任务可能仍在跑 (要业务方支持中断)

---

## Q5 综合: 虚拟线程 vs 线程池, 怎么选?

**对比**:

| 维度 | 平台线程池 | 虚拟线程 (JDK 21+) |
|---|---|---|
| 数量上限 | 千级 (每线程 ~1MB 栈) | 百万级 (按需栈) |
| 调度 | OS 内核 | JVM ForkJoinPool 载体线程 |
| 创建开销 | 高 (毫秒级) | 低 (微秒级) |
| 阻塞 IO | 占着 OS 线程 | 挂起, 载体线程被释放跑别的 |
| 适用 | CPU 密集 | IO 密集 (DB / HTTP / 文件) |
| ThreadLocal | 高效 | 多了 VT 多线性涨内存 |
| synchronized | OK | **pin 载体** (JDK 21 仍有问题, JDK 24+ 改善), 推荐 ReentrantLock |

**选型**:
- **Web 接口处理 (IO 密集)**: VT, `spring.threads.virtual.enabled=true` 全局开启
- **CPU 密集计算 (压缩 / 编码)**: 平台池, 大小 = 核数 (+ 1)
- **混合负载**: 进 API 用 VT, CPU 子任务 submit 到独立平台池

**Spring Boot 3.2 + JDK 21 全局虚拟线程**:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

启用后 Tomcat / @Async / @Scheduled 默认全跑在 VT 上。

**陷阱**:
- 业务代码大量 `synchronized` (比如老的 DBCP 连接池) → VT 退化为平台线程并发
- ThreadLocal 滥用 (比如全链路 trace) → 上百万 VT 时 ThreadLocal 内存暴涨
- VT 不能真正"取消" → 长任务仍要业务方实现中断逻辑

**面试讲法 2 分钟**:
> "JDK 21 之前, Spring 用平台线程池, Tomcat 默认 200 个, 一个请求占一个线程。 接外部 HTTP 时, 线程阻塞在 socket read, 池小了请求排队。 JDK 21 引入虚拟线程, 一个 VT 阻塞时载体线程会被复用去跑其他 VT, 阻塞型 IO 几乎没有上限了。 我现在的新项目直接 `spring.threads.virtual.enabled=true`, IO 密集场景吞吐能提 3-5 倍。 但要注意, CPU 密集任务还是要用平台池 (核数 + 1), 否则上下文切换反而拖慢; 老代码里的 synchronized 大块要换成 ReentrantLock, 不然 VT 会 pin 在载体线程上失去优势。"

---

## 通过标准

- [ ] 能默写 ThreadPoolExecutor 7 参数 + 任务调度流程图
- [ ] 能讲清 4 个 Executors 工厂方法的具体风险
- [ ] 能现场写生产可用的 @Async 配置 (含拒绝策略 / 命名 / 关闭等待)
- [ ] 能写 CompletableFuture 并行 + 超时 + 局部降级
- [ ] 能讲虚拟线程的适用场景与陷阱
