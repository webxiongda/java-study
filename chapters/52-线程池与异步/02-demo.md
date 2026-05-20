# Chapter 52 线程池与异步 - 实操 Demo

## Demo 目标

三个并列实验：
1. 自定义 `ThreadPoolExecutor` + 拒绝策略 + 监控指标。
2. `CompletableFuture` 编排两个 IO 调用并合并。
3. **JDK 21 虚拟线程**：用 10 万并发跑 IO 模拟，对比平台线程池吞吐 / 内存。

## 前置条件

- JDK 21。
- 基线 pom。

## 1. 自定义线程池 + Spring `@Async`

```java
@Configuration
@EnableAsync
public class ExecutorsConfig {

    @Bean("bizExecutor")
    public ThreadPoolTaskExecutor biz() {
        var t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        t.setMaxPoolSize(t.getCorePoolSize() * 2);
        t.setQueueCapacity(200);
        t.setKeepAliveSeconds(60);
        t.setThreadNamePrefix("biz-");
        t.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        t.setWaitForTasksToCompleteOnShutdown(true);
        t.setAwaitTerminationSeconds(30);
        t.initialize();
        return t;
    }
}

@Service
@RequiredArgsConstructor
public class NotifyService {
    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    @Async("bizExecutor")
    public CompletableFuture<Void> sendMail(String to) {
        log.info("send mail to {} on {}", to, Thread.currentThread().getName());
        // 模拟 IO
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        return CompletableFuture.completedFuture(null);
    }
}
```

## 2. CompletableFuture 编排

```java
@RestController
@RequiredArgsConstructor
public class HomeController {
    private final UserService userSvc;
    private final ProfileService profileSvc;
    @Qualifier("bizExecutor") private final Executor pool;

    @GetMapping("/home/{uid}")
    public ApiResponse<HomeDTO> home(@PathVariable Long uid) throws Exception {
        var u = CompletableFuture.supplyAsync(() -> userSvc.load(uid), pool);
        var p = CompletableFuture.supplyAsync(() -> profileSvc.load(uid), pool);

        HomeDTO dto = u.thenCombine(p, HomeDTO::of)
                       .orTimeout(2, TimeUnit.SECONDS)
                       .exceptionally(ex -> HomeDTO.empty())
                       .get();
        return ApiResponse.ok(dto);
    }
}
```

## 3. 虚拟线程 vs 平台线程对比

```java
public class VtBenchmark {
    static void simulateIO() {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {} // 假装 100ms IO
    }

    public static void main(String[] args) throws Exception {
        int N = 100_000;

        // 平台线程池：固定 200 个
        long t1 = bench("platform", N, Executors.newFixedThreadPool(200));
        // 虚拟线程：每个任务一根
        long t2 = bench("virtual",  N, Executors.newVirtualThreadPerTaskExecutor());

        System.out.printf("platform=%dms, virtual=%dms%n", t1, t2);
    }

    static long bench(String label, int n, ExecutorService es) throws Exception {
        long start = System.currentTimeMillis();
        try (es) {
            CountDownLatch latch = new CountDownLatch(n);
            for (int i = 0; i < n; i++)
                es.submit(() -> { simulateIO(); latch.countDown(); });
            latch.await();
        }
        return System.currentTimeMillis() - start;
    }
}
```

**预期结果**（M 系列 Mac，仅参考）：

```
platform = ~50_000ms（200 线程串行 IO，10w 任务 / 200 ≈ 500 轮 × 100ms）
virtual  = ~600ms    （10w VT 几乎并行）
```

## 4. 全局启用 VT（Spring Boot 3.2+）

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

效果：Tomcat 处理请求、`@Async` 默认 Executor、`@Scheduled` 都跑在 VT 上。 IO 重的接口吞吐明显提升。

## 5. `synchronized` 的 pin 陷阱（高频问）

```java
// ❌ 在 VT 里大块用 synchronized → 载体线程被 pin 住，等于退回平台线程
synchronized (lock) {
    httpCall();
}

// ✅ 改用 ReentrantLock，挂起 VT 而不 pin 载体
lock.lock();
try { httpCall(); } finally { lock.unlock(); }
```

JDK 21 加 `-Djdk.tracePinnedThreads=full` 启动参数可打印 pin 栈，定位优化点。

## 运行与验证

| 检查项 | 命令 / 期望 |
|---|---|
| 自定义池工作 | jstack 看线程名前缀 `biz-` |
| 拒绝策略生效 | 把队列设小 + 大量提交 → 看到 caller 自己跑 |
| CompletableFuture | 单接口耗时 ≈ max(u, p)，而不是 u+p |
| VT benchmark | virtual 比 platform 快 ≥ 50 倍 |
| VT pin 检测 | 启动 JVM 加 `-Djdk.tracePinnedThreads=short`，看是否有 pin 警告 |

## 常见坑

- 别在生产用 `Executors.newFixedThreadPool` / `newCachedThreadPool`。
- VT 不要复用 ThreadLocal 做缓存（量级太大），改 `ScopedValue`（JDK 21 预览）或显式参数。
- `@Async` 没指定名字时默认是 `taskExecutor` bean，看清楚自己注入的是哪个。

## 提交

```bash
git commit -m "chapter 52: custom thread pool + completable future + virtual threads"
```
