# Chapter 53 并发安全 - 项目任务

## 任务概述

把博客 API 里 **隐藏的并发问题** 一次性治理:

1. PV / 点赞 计数: 从 HashMap + i++ 改成 LongAdder + ConcurrentHashMap
2. 用户上下文 (UserId / TraceId): 用 ThreadLocal 管理 + 严格 remove
3. 文章并发编辑冲突: 乐观锁 (version) + 友好错误
4. 评论防重: ConcurrentHashMap 去重 1 秒内重复提交
5. 排查工具脚本 (jstack / Arthas)

## 业务背景

代码 review 时发现的问题:
- `private static Map<Long, Long> pvMap = new HashMap<>()` (高 QPS 下丢数据)
- 拦截器把 userId 放 ThreadLocal, 但漏 remove → 线程池下串号
- 两个编辑者同时改一篇文章, 后保存覆盖前保存
- 用户连点提交评论, 出现重复

## 任务拆解

### Step 1: 并发安全的 PV 计数 (30 分钟)

```java
@Component
public class PostPvCounter {
    private final ConcurrentHashMap<Long, LongAdder> pv = new ConcurrentHashMap<>();

    public void incr(Long postId) {
        pv.computeIfAbsent(postId, k -> new LongAdder()).increment();
    }

    public long get(Long postId) {
        LongAdder a = pv.get(postId);
        return a == null ? 0 : a.sum();
    }

    /** 每 30 秒批量刷 DB, 防热点 key 高频 UPDATE */
    @Scheduled(fixedDelay = 30_000)
    public void flushToDb() {
        Map<Long, Long> snapshot = new HashMap<>();
        pv.forEach((k, v) -> {
            long s = v.sumThenReset();
            if (s > 0) snapshot.put(k, s);
        });
        if (!snapshot.isEmpty()) postMapper.batchIncrPv(snapshot);
    }
}
```

**关键设计**:
- `computeIfAbsent` 保证 LongAdder 只 new 一次 (原子)
- `LongAdder.sumThenReset` 取值并清零, 增量写 DB
- 批量刷盘, 单次 UPDATE 而不是 N 次

### Step 2: ThreadLocal 上下文统一 (30 分钟)

```java
public class UserContext {
    private static final ThreadLocal<UserContextDTO> CTX = new ThreadLocal<>();

    public static void set(UserContextDTO ctx) { CTX.set(ctx); }
    public static UserContextDTO get() { return CTX.get(); }
    public static Long getUserId() { return CTX.get() == null ? null : CTX.get().getUserId(); }
    public static void clear() { CTX.remove(); }
}

@Component
public class UserContextInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) {
        // 从 SecurityContext 拿 userId
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long uid) {
            UserContext.set(UserContextDTO.builder().userId(uid).traceId(genTraceId()).build());
            MDC.put("traceId", UserContext.get().getTraceId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object h, Exception ex) {
        UserContext.clear();       // 必须 remove
        MDC.clear();
    }
}
```

异步任务透传 (TTL):

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.5</version>
</dependency>
```

```java
@Bean("bizExecutor")
public Executor bizExecutor() {
    ThreadPoolTaskExecutor t = ...;
    t.initialize();
    return TtlExecutors.getTtlExecutorService(t.getThreadPoolExecutor());
}
```

### Step 3: 文章编辑乐观锁 (30 分钟)

```sql
ALTER TABLE post ADD COLUMN version INT NOT NULL DEFAULT 0;
```

```java
@Update("UPDATE post SET title=#{title}, content=#{content}, version=version+1 " +
        "WHERE id=#{id} AND version=#{version}")
int updateWithVersion(Post post);
```

```java
@Transactional
public PostVO update(Long id, PostUpdateReq req, int expectVersion) {
    int rows = postMapper.updateWithVersion(Post.builder()
        .id(id).title(req.getTitle()).content(req.getContent())
        .version(expectVersion).build());
    if (rows == 0) {
        throw new BizException(409, "文章已被他人修改, 请刷新后重试");
    }
    return postMapper.selectById(id).toVO();
}
```

前端编辑时返回 `version`, 提交时带回。

### Step 4: 评论防重 (20 分钟)

```java
@Component
public class CommentAntiDup {
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 1000;

    public boolean tryAccept(Long userId, Long postId, String content) {
        String key = userId + ":" + postId + ":" + DigestUtils.md5DigestAsHex(content.getBytes());
        long now = System.currentTimeMillis();
        Long last = recent.put(key, now);
        return last == null || now - last > WINDOW_MS;
    }

    @Scheduled(fixedDelay = 60_000)
    public void clean() {
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(e -> now - e.getValue() > 60_000);
    }
}
```

注意: 单机版。 多实例上 Redis SETNX (Ch46 已学)。

### Step 5: 排查工具 (20 分钟)

`scripts/diagnose.sh`:

```bash
#!/usr/bin/env bash
PID=$(jps | grep BlogApplication | awk '{print $1}')
echo "PID: $PID"

echo "=== Top 5 CPU threads ==="
top -H -p $PID -b -n 1 | head -25

echo "=== Deadlock check ==="
jstack $PID | grep -A 30 "Found one Java-level deadlock" || echo "no deadlock"

echo "=== Thread state summary ==="
jstack $PID | grep java.lang.Thread.State | sort | uniq -c | sort -rn
```

(可选) Arthas 一键:
```bash
java -jar arthas-boot.jar $PID
> thread -b           # 死锁
> thread -n 5         # CPU 前 5
> watch com.javastudy.service.PostService getDetail '{params, returnObj, throwExp}'
```

### Step 6: 集成测试 (30 分钟)

```java
@SpringBootTest
class ConcurrencySafetyTest {

    @Test
    void pvCounter_correctUnder1000Concurrent() throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(50);
        CountDownLatch done = new CountDownLatch(1000);
        for (int i = 0; i < 1000; i++) {
            es.submit(() -> {
                counter.incr(1L);
                done.countDown();
            });
        }
        done.await();
        assertEquals(1000, counter.get(1L));
        es.shutdown();
    }

    @Test
    void optimisticLock_concurrentEdit_oneWins() throws Exception {
        Post p = postMapper.insert(new Post("title","content",0));
        ExecutorService es = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> f1 = es.submit(() -> { start.await(); return postService.update(p.getId(), req("v1"), 0); });
        Future<?> f2 = es.submit(() -> { start.await(); return postService.update(p.getId(), req("v2"), 0); });

        start.countDown();

        int success = 0, conflict = 0;
        for (Future<?> f : List.of(f1, f2)) {
            try { f.get(); success++; }
            catch (ExecutionException e) {
                if (((BizException)e.getCause()).getCode() == 409) conflict++;
            }
        }
        assertEquals(1, success);
        assertEquals(1, conflict);
    }
}
```

## 交付物

- [ ] `PostPvCounter` (ConcurrentHashMap + LongAdder + 批量刷盘)
- [ ] `UserContext` + `UserContextInterceptor` (set/clear 严格管理)
- [ ] TTL 透传到异步线程
- [ ] `post.version` 字段 + 乐观锁更新
- [ ] `CommentAntiDup` 1 秒内防重
- [ ] `diagnose.sh` 一键排查脚本
- [ ] 测试 ≥ 5 个 (PV 并发 / 乐观锁冲突 / 防重 / TL 串号反例 / ConcurrentHashMap 正确性)
- [ ] git commit: `ch53: thread safety fixes (pv counter / threadlocal / optimistic lock)`

## 验收清单

| 验收项 | 标准 |
|---|---|
| PV 并发 | 1000 并发 incr, get() = 1000, 无丢失 |
| 批量刷盘 | 30s 内一次 UPDATE, 不是 N 次 |
| ThreadLocal | 反例 demo 看到串号; 修复后无串号 |
| TTL | 异步线程能拿到提交时的 userId |
| 乐观锁 | 两个线程同时改, 一个成功一个 409 |
| 防重 | 1 秒内同用户同内容评论, 第二次拒绝 |
| jstack | `diagnose.sh` 能识别死锁 (人造一个测试) |

## 扩展挑战

1. **StampedLock** 重写 PostPvCounter, 用乐观读提升读吞吐
2. **细粒度锁**: 用户级独立锁 (`ConcurrentHashMap<Long, ReentrantLock>`), 防同用户并发
3. **Caffeine 替换 ConcurrentHashMap**: 加 LRU 淘汰防内存泄漏
4. **基于 Redis 的分布式去重**: 用 SETNX + TTL 替代单机 Map
5. **JFR 录制**: 抓 60 秒并发执行, JMC 看锁竞争
