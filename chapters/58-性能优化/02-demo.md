# Chapter 58 性能优化 - 实操 Demo

## Demo 目标

把一个慢接口 (`GET /api/v1/posts/{id}/detail`, 返回文章 + 作者 + 前 20 评论) 从 P99 600ms 优化到 P99 30ms。 全程压测数据驱动。

## 初始版本 (慢)

```java
@GetMapping("/{id}/detail")
public PostDetail detail(@PathVariable Long id) {
    Post post = postRepo.findById(id).orElseThrow();
    User author = userRepo.findById(post.getAuthorId()).orElseThrow();
    List<Comment> comments = commentRepo.findByPostId(id);    // 没分页
    List<CommentVO> commentVos = comments.stream()
        .map(c -> {
            User cu = userRepo.findById(c.getUserId()).orElseThrow();  // N+1!
            return new CommentVO(c, cu);
        }).toList();
    return new PostDetail(post, author, commentVos);
}
```

## Step 1: 基线压测

```bash
wrk -t4 -c100 -d30s --latency http://localhost:8080/api/v1/posts/1/detail
```

输出 (典型):
```
Latency Distribution
   50%  234.56ms
   90%  489.23ms
   99%  612.45ms
Requests/sec:  142.34
```

**结论**: P99 612ms, QPS 142 — 慢。

## Step 2: 看慢日志 + EXPLAIN

```bash
tail -f /var/log/mysql/slow.log
```

发现:
```sql
# Query_time: 0.054
SELECT * FROM comments WHERE post_id = 1;    -- 没索引!
# Query_time: 0.012
SELECT * FROM users WHERE id = 5;            -- 重复 N 次
```

```sql
EXPLAIN SELECT * FROM comments WHERE post_id = 1;
-- type: ALL, rows: 50000, key: NULL    ← 全表扫!
```

## Step 3: 优化 1 - 加索引 (DB)

```sql
ALTER TABLE comments ADD INDEX idx_post_id_created (post_id, created_at);
```

EXPLAIN 后: `type: ref, rows: 200`. 单条评论查询 50ms → 1ms。

重压: P99 612ms → 280ms。

## Step 4: 优化 2 - 解决 N+1 (一次取所有 user)

```java
public PostDetail detail(Long id) {
    Post post = postRepo.findById(id).orElseThrow();
    User author = userRepo.findById(post.getAuthorId()).orElseThrow();

    // 改为分页 + 一次取所有 user
    List<Comment> comments = commentRepo
        .findByPostIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))
        .getContent();
    Set<Long> userIds = comments.stream()
        .map(Comment::getUserId).collect(toSet());
    Map<Long, User> userMap = userRepo.findAllById(userIds).stream()
        .collect(toMap(User::getId, u -> u));
    List<CommentVO> commentVos = comments.stream()
        .map(c -> new CommentVO(c, userMap.get(c.getUserId())))
        .toList();

    return new PostDetail(post, author, commentVos);
}
```

重压: P99 280ms → 80ms。

## Step 5: 优化 3 - Redis 缓存 (热数据)

```java
@Cacheable(cacheNames="post:detail", key="#id")
public PostDetail detail(Long id) {
    // 同上
}
```

application.yml:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 5m
```

第二次访问命中缓存, RT 从 80ms → 3ms。 冷启动还是 80ms。

重压 (warm up 后): P99 80ms → 12ms, QPS 142 → 5000+。

## Step 6: 优化 4 - 本地缓存 (避免 Redis 网络往返)

```java
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager(...) // L1 Caffeine
            // L2 Redis
    }
}
```

P99 12ms → 4ms。

## Step 7: 优化 5 - 并行化非依赖项

```java
public PostDetail detail(Long id) {
    CompletableFuture<Post> postF = supplyAsync(() -> postRepo.findById(id).orElseThrow(), pool);
    CompletableFuture<List<Comment>> commentsF = supplyAsync(
        () -> commentRepo.findByPostIdOrderByCreatedAtDesc(id, PageRequest.of(0,20)).getContent(),
        pool);

    Post post = postF.join();
    User author = userRepo.findById(post.getAuthorId()).orElseThrow();
    List<Comment> comments = commentsF.join();
    // ...
}
```

冷启动 P99 80ms → 50ms (post 和 comments 并行)。

## 优化报告 (最终)

| 阶段 | P50 | P99 | QPS | 错误率 | 改动 |
|---|---|---|---|---|---|
| 基线 | 234ms | 612ms | 142 | 0% | - |
| 加 comments 索引 | 124ms | 280ms | 320 | 0% | SQL |
| 解决 N+1 | 35ms | 80ms | 1100 | 0% | 代码 |
| Redis 缓存 | 4ms | 12ms | 5200 | 0% | 注解 |
| Caffeine L1 | 1ms | 4ms | 8500 | 0% | 注解 |
| 并行化 | 1ms | 3ms | 9200 | 0% | 代码 |

**总收益**: P99 612ms → 3ms (200x), QPS 142 → 9200 (65x)。

## Demo 2: JMH 微基准

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CollectionBench {

    List<Integer> data = IntStream.range(0, 1000).boxed().toList();

    @Benchmark public int forLoop() {
        int sum = 0;
        for (int i : data) sum += i;
        return sum;
    }

    @Benchmark public int stream() {
        return data.stream().mapToInt(Integer::intValue).sum();
    }

    @Benchmark public int parallelStream() {
        return data.parallelStream().mapToInt(Integer::intValue).sum();
    }
}
```

跑: `mvn clean package && java -jar target/benchmarks.jar`

预期输出:
```
forLoop          avgt   5  1234 ± 12 ns/op
stream           avgt   5  4523 ± 45 ns/op       ← 慢 4x
parallelStream   avgt   5  9821 ± 312 ns/op       ← 1000 元素并行反而更慢 (调度开销)
```

**结论**: 小集合用 for, 大集合 (> 10k) 才考虑 parallelStream。

## Demo 3: JFR 找 CPU 热点

```bash
# 启动应用时
java -XX:StartFlightRecording=duration=60s,filename=app.jfr -jar app.jar

# 同时压测
wrk -t4 -c100 -d60s http://localhost:8080/api/v1/posts/1

# 用 JMC 打开 app.jfr
open -a "JDK Mission Control" app.jfr
```

看 **CPU → Hot Methods**, 通常会发现:
- Jackson 序列化 (考虑 afterburner)
- BCrypt (考虑放业务异步)
- 反射 (考虑 MethodHandle 缓存)

## 提交

```bash
git add chapters/58-性能优化/
git commit -m "ch58: detail API optimization 612ms→3ms"
```
