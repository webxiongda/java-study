# Chapter 52 线程池与异步 - 理论

> 前置：[[51-并发基础]]
> 后续：[[53-并发安全]]
> 优先级：L3 面试高频  预计：4 小时

## 1. 为什么需要这一章

裸 `new Thread().start()` 在生产里是禁止的——OOM、上下文切换、资源不可控。 线程池统一管理生命周期、复用线程、限制并发数。 JDK 21 又多了**虚拟线程**，IO 密集场景下「无限线程」的开发体验加上低开销的运行时表现。

## 2. ThreadPoolExecutor 7 大参数（背下来）

```java
new ThreadPoolExecutor(
    int corePoolSize,                 // 核心线程数：常驻
    int maximumPoolSize,              // 最大线程数：峰值
    long keepAliveTime, TimeUnit unit,// 非核心线程空闲存活时间
    BlockingQueue<Runnable> workQueue,// 任务队列
    ThreadFactory threadFactory,      // 线程工厂（给线程命名很重要）
    RejectedExecutionHandler handler  // 拒绝策略
);
```

### 任务调度顺序（高频面试）

```
提交任务
   ├─ 当前线程数 < corePoolSize → 直接新建核心线程跑
   ├─ 否则 → 入 workQueue
   ├─ 队列满 + 当前线程数 < maximumPoolSize → 新建非核心线程跑
   └─ 队列满 + 线程数已达 max → 执行 RejectedExecutionHandler
```

⚠️ 注意：`Executors.newCachedThreadPool()` 用的 `SynchronousQueue` 永远不入队，所以会一直创建线程到 `Integer.MAX_VALUE` —— **生产禁用**，会 OOM。

### 四种拒绝策略

| 策略 | 行为 | 适用 |
|---|---|---|
| `AbortPolicy`（默认） | 抛 `RejectedExecutionException` | 调用方需要感知 |
| `CallerRunsPolicy` | 在提交线程里直接执行 | 反向限流，最稳妥 |
| `DiscardPolicy` | 静默丢弃 | 日志类可忽略 |
| `DiscardOldestPolicy` | 丢队列最老的，再尝试提交 | 偏好新任务 |

### 核心数怎么算

- CPU 密集：`N = CPU 核数 + 1`（少一次上下文切换的损耗）。
- IO 密集：`N = CPU 核数 × (1 + 平均等待 / 平均计算)`，工程上常取 `2 × 核数` 起步，再压测调。
- 不要硬编码 8 / 16，用 `Runtime.getRuntime().availableProcessors()`。

## 3. 队列怎么选

| Queue | 行为 | 适用 |
|---|---|---|
| `ArrayBlockingQueue(n)` | 有界，FIFO | 推荐，配合 max > core 触发扩容 |
| `LinkedBlockingQueue()` | 无界（默认 `Integer.MAX_VALUE`） | ⚠️ 会让 max 形同虚设 |
| `LinkedBlockingQueue(n)` | 有界 | OK |
| `SynchronousQueue` | 不存储，直接交付 | 配合 max=INF 用于短任务（cached 池） |
| `PriorityBlockingQueue` | 按优先级 | 任务有优先级 |

**最常见的坑**：默认 `Executors.newFixedThreadPool` 用无界 `LinkedBlockingQueue` → 任务堆积 → 堆 OOM。

## 4. CompletableFuture 编排

```java
CompletableFuture<User>    u = CompletableFuture.supplyAsync(() -> userSvc.load(uid), pool);
CompletableFuture<Profile> p = CompletableFuture.supplyAsync(() -> profileSvc.load(uid), pool);

CompletableFuture<HomeDTO> home = u.thenCombine(p, HomeDTO::of)
    .exceptionally(ex -> { log.error("load home", ex); return HomeDTO.empty(); })
    .orTimeout(2, TimeUnit.SECONDS);
```

关键 API：`thenApply / thenCompose / thenCombine / allOf / anyOf / exceptionally / orTimeout`。

**坑**：不传 `Executor` 时用的是 `ForkJoinPool.commonPool()`，被全局共享，慢任务会污染其他业务。 **永远显式传自己的池**。

## 5. JDK 21 虚拟线程（Virtual Threads）

```java
// 创建
Thread.startVirtualThread(() -> { ... });

// 作为线程池接口
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handleRequest());
}
```

### 和平台线程的关键差异

| 维度 | 平台线程（PT） | 虚拟线程（VT） |
|---|---|---|
| 数量上限 | 千级（每个 ~1MB 栈） | 百万级（按需分配栈） |
| 调度 | 操作系统 | JVM（ForkJoinPool 载体线程） |
| 创建开销 | 高 | 极低（μs 级） |
| 阻塞 IO | 占着 OS 线程 | 挂起，载体线程被释放跑别的 VT |
| 适合 | CPU 密集 | IO 密集（DB、HTTP、文件） |

### 什么时候用 VT

- IO 多、并发高：DB 调用、外部 HTTP、文件读写。
- **不需要**自己 tuning 池大小 —— 每个请求一个 VT。

### 什么时候**不**用

- CPU 密集计算 → 用平台线程池 + 等于 CPU 核数。
- 大量 `synchronized` 临界区 → JDK 21 仍会「pin」载体线程（JDK 24+ 改善），优先用 `ReentrantLock`。
- ThreadLocal 滥用 → VT 多了 ThreadLocal 内存也线性涨。

## 6. Spring `@Async`

```java
@EnableAsync
@SpringBootApplication
public class App { }

@Configuration
public class AsyncConfig {
    @Bean("bizExecutor")
    public Executor biz() {
        var t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(4);
        t.setMaxPoolSize(16);
        t.setQueueCapacity(200);
        t.setThreadNamePrefix("biz-");
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        t.initialize();
        return t;
    }
}

@Service
public class MailService {
    @Async("bizExecutor")
    public CompletableFuture<Void> send(String to) { /* ... */ return CompletableFuture.completedFuture(null); }
}
```

JDK 21 + Spring Boot 3.2+ 可以全局开启虚拟线程：

```yaml
spring:
  threads:
    virtual:
      enabled: true   # Tomcat + @Async 都跑在 VT 上
```

## 7. 监控（很多人遗漏）

```java
ThreadPoolExecutor tp = ...;
// 周期性打：活跃数、队列长度、完成任务数
Metrics.gauge("pool.active",  tp, ThreadPoolExecutor::getActiveCount);
Metrics.gauge("pool.queue",   tp, t -> t.getQueue().size());
Metrics.gauge("pool.completed", tp, ThreadPoolExecutor::getCompletedTaskCount);
```

Spring Boot 用 Micrometer 自动暴露到 `/actuator/metrics`，对接 Prometheus + Grafana。

## 8. 常见坑

- 不命名线程 → OOM dump 里全是 `pool-3-thread-12`，定位不到业务。
- `Executors.newFixed` / `newCached` 默认无界队列或无界线程 → 直接 OOM。
- `@Async` 不指定 executor → 默认 SimpleAsyncTaskExecutor，每次新建线程。
- VT 池里加 `synchronized` 大块代码 → 退化为平台线程级别并发。
- 主线程不 `shutdown()` 池 → JVM 不退出。
