# Chapter 46 Redis 基础与缓存 - 项目任务

## 任务概述

给博客 API 接入 **Redis 缓存层**, 覆盖 4 个场景:

1. **文章详情** Cache-Aside (含穿透 / 击穿 / 雪崩防护)
2. **PV 计数** Write-Behind (Redis incr + 定时 flush DB)
3. **热门榜** ZSET 实时统计 + Top 10
4. **分布式锁** SET NX + Lua, 用于幂等控制

要求每个场景都有: 实现 + 单测 + curl 验证 + 失败场景演示。

## 业务背景

文章详情接口在 42 章测试时 100 QPS 已经让 MySQL CPU 飙到 70%。 上 Redis 后:
- 缓存命中率 95%+ 时, DB 压力下降到原来的 5%
- P99 从 80ms 下降到 5ms (Redis 内存查) 
- 热门榜不再用 `ORDER BY view_count` 全表扫, 直接 ZREVRANGE
- PV 不再每次 +1 写 DB (40w QPS 不可能), 走 Redis 缓冲

这是后端工程化的入门门槛, 几乎所有面试都会问"你的缓存策略是什么"。

## 任务拆解

### Step 1: Redis 接入 + RedisTemplate 配置 (30 分钟)

`pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

`application.yml`:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      timeout: 2000ms
      lettuce:
        pool: { max-active: 16, max-idle: 8, min-idle: 4 }
```

`RedisConfig` 配 JSON 序列化 (见 02-demo.md 一节)。

启动后 `RedisTemplate<String,Object>` 可注入, 用 `redis-cli ping` 验证连通。

### Step 2: 文章详情 Cache-Aside (60 分钟)

`PostCacheService.getById(id)`:

- 读缓存
- miss → 互斥锁 (setIfAbsent) → 二次检查 → 回源 DB → 回填 (TTL 抖动) → 释放锁
- DB null → 写空对象 (短 TTL) 防穿透

`PostService.update / delete` 调用 `cache.evict(id)` 删缓存 (不写缓存)。

代码参考 02-demo.md 第二、三节。

### Step 3: PV 计数 (45 分钟)

#### 读阶段 + 异步写

```java
@GetMapping("/api/v1/posts/{id}")
public PostVO get(@PathVariable Long id) {
    PostVO p = postCache.getById(id);
    if (p != null) viewCounter.incr(id);    // 异步加
    return p;
}
```

```java
@Component
@RequiredArgsConstructor
public class ViewCounter {
    private final RedisTemplate<String, Object> redis;
    public void incr(Long postId) {
        redis.opsForValue().increment("post:pv:" + postId);
    }
}
```

#### 定时刷盘

```java
@Scheduled(fixedDelay = 60_000)
public void flush() {
    ScanOptions opts = ScanOptions.scanOptions().match("post:pv:*").count(100).build();
    try (Cursor<String> cur = redis.scan(opts)) {
        while (cur.hasNext()) {
            String k = cur.next();
            Long postId = Long.parseLong(k.substring("post:pv:".length()));
            Long v = (Long) redis.opsForValue().get(k);
            if (v != null && v > 0) {
                postMapper.addViewCount(postId, v);
                redis.opsForValue().decrement(k, v);
            }
        }
    }
}
```

> 注: 不要直接 `delete`, 否则 flush 期间新增的 PV 会丢。`decrement(k, v)` 只扣已写入的部分。

### Step 4: 热门榜 ZSET (45 分钟)

```java
@Component
@RequiredArgsConstructor
public class HotRank {
    private final RedisTemplate<String, Object> redis;

    public void record(Long postId) {
        String key = "post:hot:" + LocalDate.now();
        redis.opsForZSet().incrementScore(key, postId.toString(), 1);
        redis.expire(key, Duration.ofDays(10));
    }

    public List<Long> top(int n) {
        Set<Object> s = redis.opsForZSet()
            .reverseRange("post:hot:" + LocalDate.now(), 0, n - 1);
        return s == null ? List.of() : s.stream()
            .map(o -> Long.parseLong(o.toString())).toList();
    }
}
```

`GET /api/v1/posts/hot` 返回 Top 10 + 内容 (走 PostCacheService.getById 拉详情)。

### Step 5: 分布式锁 (30 分钟)

```java
@Component
@RequiredArgsConstructor
public class RedisLock {
    private final RedisTemplate<String, Object> redis;
    private static final String UNLOCK = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1]) else return 0 end""";

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent("lock:" + key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        redis.execute(new DefaultRedisScript<>(UNLOCK, Long.class),
            List.of("lock:" + key), token);
    }
}
```

应用: "文章发布幂等"

```java
@PostMapping("/api/v1/posts")
public Long create(@RequestBody PostCreateReq req, Authentication auth) {
    Long uid = (Long) auth.getPrincipal();
    String idempotencyKey = "post:create:" + uid + ":" + req.title().hashCode();
    String token = lock.tryLock(idempotencyKey, Duration.ofSeconds(5));
    if (token == null) throw new BizException(409, "请勿重复提交");
    try {
        return postService.create(req, uid);
    } finally {
        lock.unlock(idempotencyKey, token);
    }
}
```

### Step 6: 测试 (45 分钟)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PostCacheIT {
    @Container static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void cacheHitsOnSecondRead() {
        // first hit DB
        // second hit cache (mock DB to throw / count invocations)
    }

    @Test
    void cachePenetrationProtected() {
        // get(999999) twice, mapper.selectById should be called only once
    }

    @Test
    void writeThenReadIsConsistent() {
        // create -> read (hit DB, cache)
        // update -> evict
        // read again -> hit DB (refilled)
    }
}
```

### Step 7: 降级 (30 分钟)

Redis 挂了不能让接口挂:

```java
public PostVO getById(Long id) {
    try {
        return getFromCache(id);
    } catch (RedisConnectionFailureException e) {
        log.warn("Redis down, fallback to DB for post {}", id);
        return postMapper.selectVOById(id);
    }
}
```

可选: 加 `CircuitBreaker` (Resilience4j), 连续失败 N 次后整段绕过 Redis 一段时间。

## 交付物

- [ ] `RedisConfig` Bean (JSON 序列化)
- [ ] `PostCacheService` (Cache-Aside + 互斥锁 + 空对象 + TTL 抖动)
- [ ] `ViewCounter` + 定时刷盘任务
- [ ] `HotRank` + `GET /api/v1/posts/hot`
- [ ] `RedisLock` + 创建文章幂等防重复
- [ ] Redis 降级处理 (try-catch + fallback)
- [ ] Testcontainers 集成测试 ≥ 4 个
- [ ] README 加"缓存策略"段落, 含 4 张图 / 表
- [ ] git commit: `ch46: redis cache-aside + counter + hot rank + lock`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 缓存命中 | 同一 id 连续 GET 两次, 第二次 redis-cli MONITOR 看到 GET 命令, 不应有 SQL |
| 缓存穿透 | 请求不存在 id 100 次, DB selectById 只被调用一次 (因空对象) |
| 缓存击穿 | 删 hot key 后并发 100 请求, DB selectById 只调一次 (因互斥锁) |
| 写后失效 | update 后 GET, 缓存里的 key 已被删 |
| TTL 抖动 | redis-cli TTL post:detail:* 看一组 key, TTL 散列在 600-660 秒 |
| PV 异步 | 100 次 GET 后 redis post:pv:1 = 100; 60s 后 DB view_count += 100, redis = 0 |
| 热门榜 | 10 个文章不同次数 GET 后, /api/v1/posts/hot 按 GET 次数倒序 |
| 幂等 | 同 user 5 秒内提交相同 title 两次 → 409 |
| 降级 | docker stop redis → 接口仍 200 (走 DB), log 有 WARN |

## 扩展挑战

1. **多级缓存**: Caffeine (本地) + Redis (远端), 用 `CompositeCacheManager` 组合, 减少 Redis QPS。
2. **缓存预热**: 启动后扫 `daily_hot` 表的 Top 100 文章, 批量 warm-up 写入 Redis。
3. **延迟双删**: `update` 时立即删 + RabbitMQ 延迟队列 1 秒后再删, 应对极端并发不一致。
4. **Canal 订阅 binlog**: 接 Canal, MySQL 任何 UPDATE 都自动失效对应缓存, 业务零侵入。
5. **基于 RedLock 的分布式锁**: 用 Redisson 的 RedLock 替代 SET NX, 在多 Redis 节点下也安全。
6. **限流 + 缓存联动**: 命中缓存的请求不计入限流配额, 因为不消耗 DB 资源。
