# Chapter 51 并发基础 - 项目任务

## 任务概述

给博客 API 接入 **并发场景**:

1. 文章详情聚合接口: 并发调 文章 / 作者 / 评论 / 点赞 4 个查询, 总耗时 = max 而不是 sum
2. 优雅停机: 应用关闭时, 后台任务收到中断, 完成手头工作再退出
3. 演示 volatile 可见性问题 (写一个反例 + 用 volatile 修复)
4. 用 BlockingQueue 重写一个简易事件队列

## 业务背景

文章详情页要展示: 正文 + 作者信息 + 最新 5 条评论 + 点赞数。 串行查 4 次, 每次 30ms → 总 120ms; 并行只要 30ms。 这是并发最直接的收益。 同时博客有定时清理 / 索引重建等后台任务, 应用关闭时要优雅退出, 不能丢任务。

## 任务拆解

### Step 1: 文章详情聚合并发化 (40 分钟)

```java
@Service
@RequiredArgsConstructor
public class PostDetailAggregator {
    private final PostService postService;
    private final UserService userService;
    private final CommentService commentService;
    private final LikeService likeService;

    private final ExecutorService pool = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "post-aggregator-" + threadIdx.getAndIncrement());
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger threadIdx = new AtomicInteger();

    public PostDetailVO aggregate(Long postId) {
        long start = System.currentTimeMillis();

        CompletableFuture<PostVO> post = CompletableFuture.supplyAsync(
            () -> postService.getDetail(postId), pool);
        CompletableFuture<UserVO> author = post.thenApplyAsync(
            p -> userService.getById(p.getUserId()), pool);
        CompletableFuture<List<CommentVO>> comments = CompletableFuture.supplyAsync(
            () -> commentService.latestForPost(postId, 5), pool);
        CompletableFuture<Long> likes = CompletableFuture.supplyAsync(
            () -> likeService.countForPost(postId), pool);

        try {
            CompletableFuture.allOf(post, author, comments, likes).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("aggregate timeout, postId={}", postId);
            throw new BizException(504, "聚合超时");
        } catch (Exception e) {
            throw new BizException(502, "聚合失败: " + e.getMessage());
        }

        long cost = System.currentTimeMillis() - start;
        log.info("aggregate postId={} cost={}ms", postId, cost);
        return PostDetailVO.of(post.join(), author.join(), comments.join(), likes.join());
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

### Step 2: 优雅停机 (30 分钟)

`application.yml`:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

后台任务示例:

```java
@Component
@RequiredArgsConstructor
public class IndexRebuilder {
    private volatile boolean running = true;
    private Thread worker;
    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>(1000);

    @PostConstruct
    public void start() {
        worker = new Thread(this::loop, "index-rebuilder");
        worker.start();
    }

    private void loop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Long postId = queue.poll(1, TimeUnit.SECONDS);
                if (postId != null) doRebuild(postId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("rebuild failed", e);
            }
        }
        log.info("index-rebuilder exited gracefully");
    }

    public void enqueue(Long postId) {
        if (!queue.offer(postId)) log.warn("index queue full, drop {}", postId);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        running = false;
        worker.interrupt();
        worker.join(5000);
    }
}
```

### Step 3: volatile 反例 + 修复 (15 分钟)

写一个 `VolatilityTest` 类放 `src/test/java`, 演示不加 volatile 时 reader 可能跑死循环。 用 `@Test(timeout=3000)` 断言加了 volatile 后能在 1 秒内退出。

### Step 4: 集成测试 (30 分钟)

```java
@SpringBootTest
class PostDetailAggregatorTest {
    @Autowired PostDetailAggregator agg;
    @MockBean PostService postService;
    @MockBean UserService userService;
    @MockBean CommentService commentService;
    @MockBean LikeService likeService;

    @Test
    void aggregate_parallel_fasterThanSerial() {
        when(postService.getDetail(1L)).thenAnswer(i -> { Thread.sleep(100); return new PostVO(); });
        when(userService.getById(any())).thenAnswer(i -> { Thread.sleep(100); return new UserVO(); });
        when(commentService.latestForPost(1L, 5)).thenAnswer(i -> { Thread.sleep(100); return List.of(); });
        when(likeService.countForPost(1L)).thenAnswer(i -> { Thread.sleep(100); return 0L; });

        long start = System.currentTimeMillis();
        agg.aggregate(1L);
        long cost = System.currentTimeMillis() - start;
        assertThat(cost).isLessThan(300);  // 串行需要 400, 并行应 < 300 (含调度开销)
    }

    @Test
    void aggregate_timeout() {
        when(postService.getDetail(1L)).thenAnswer(i -> { Thread.sleep(2000); return new PostVO(); });
        // 其他 mock 都正常
        assertThrows(BizException.class, () -> agg.aggregate(1L));
    }
}
```

## 交付物

- [ ] `PostDetailAggregator` (并发聚合 + 超时 + 线程命名 + 优雅关闭)
- [ ] `IndexRebuilder` (BlockingQueue + volatile + 中断响应)
- [ ] `VolatilityTest` 反例 + 修复对比
- [ ] `application.yml` 开启 graceful shutdown
- [ ] 集成测试 ≥ 4 个 (并行性能 / 超时 / 关闭等待 / 队列满)
- [ ] git commit: `ch51: parallel aggregator + graceful shutdown + volatile demo`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 并发收益 | 4 个 100ms 的查询, 总耗时 < 300ms |
| 超时 | 任一下游 > 1s, 接口返回 504 |
| 优雅关闭 | `kill -15` 应用进程, 日志显示 "index-rebuilder exited gracefully", 不丢任务 |
| 中断响应 | IndexRebuilder 收到 interrupt 后 ≤ 1s 退出 |
| 队列满保护 | 队列 offer 失败时 log warn, 不阻塞调用方 |
| volatile | 反例: reader 死循环; 加 volatile 后 1s 内退出 |
| 线程命名 | jstack 输出能看到 `post-aggregator-1` `index-rebuilder` |

## 扩展挑战

1. 用 **虚拟线程** (JDK 21 `Thread.ofVirtual().factory()`) 替换 FixedThreadPool, 对比 IO 密集场景的吞吐
2. 接 `CompletableFuture.handle` 实现 "局部失败降级" (评论查不到不影响主流程)
3. 加 Micrometer 计数: `aggregator.cost.seconds` Histogram, Grafana 看 P99
4. 用 `ForkJoinPool` 实现文章 Markdown 渲染的并行解析 (CPU 密集场景)
