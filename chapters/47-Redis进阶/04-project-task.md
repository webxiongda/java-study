# Chapter 47 Redis 进阶 - 项目任务

## 任务概述

把 46 章的"裸 Redis"升级到 **生产可运维**:

1. 接入 **Redisson** (替代手撸 Lua 锁), 用 RLock 重写分布式锁
2. **多级缓存**: Caffeine (本地) + Redis (远端), 抗热 key
3. **缓存预热**: 启动时把 Top 100 文章 warm-up 进缓存
4. **大 key / 热 key 监控**: 接入 Redis exporter + Prometheus
5. **故障演练**: stop Redis 服务, 验证降级链路
6. **Lua 限流脚本**: 滑动窗口, 替代单机 Bucket4j

## 业务背景

46 章用 RedisTemplate + 手撸 Lua, 在 demo 环境够用, 但生产场景:

- 业务慢 + 锁 TTL 短 → 锁过期被别人拿走, 数据错乱 → 需要 watchdog 续期 (Redisson)
- 热文章被点爆 → 单分片 Redis 打满 → 需要多级缓存
- 服务重启后 5 分钟缓存命中率才回升 → 需要预热
- 没监控 → 慢查询和大 key 完全靠运气发现

本章是把 Redis 从"能用"升级到"放心用"。

## 任务拆解

### Step 1: 接入 Redisson (45 分钟)

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.0</version>
</dependency>
```

`application.yml`:

```yaml
spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://${REDIS_HOST:localhost}:6379"
            connectionPoolSize: 16
            connectionMinimumIdleSize: 4
```

重写 46 章 `RedisLock`:

```java
@Component
@RequiredArgsConstructor
public class DistributedLock {
    private final RedissonClient redisson;

    public <T> T withLock(String key, Duration wait, Duration hold,
                          Supplier<T> action) {
        RLock lock = redisson.getLock("lock:" + key);
        try {
            if (!lock.tryLock(wait.toMillis(), hold.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BizException(409, "请稍后重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "锁中断");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

使用:

```java
return distLock.withLock("post:create:" + uid,
    Duration.ZERO, Duration.ofSeconds(30),
    () -> postService.create(req, uid));
```

### Step 2: 多级缓存 Caffeine + Redis (60 分钟)

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Service
@RequiredArgsConstructor
public class PostCacheService {

    private final RedisTemplate<String, Object> redis;
    private final PostMapper mapper;

    private final Cache<Long, PostVO> local = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .recordStats()
        .build();

    public PostVO get(Long id) {
        // L1: Caffeine
        PostVO v = local.getIfPresent(id);
        if (v != null) return v == NULL_OBJ ? null : v;

        // L2: Redis
        v = (PostVO) redis.opsForValue().get(key(id));
        if (v != null) {
            local.put(id, v);
            return v == NULL_OBJ ? null : v;
        }

        // L3: DB
        PostVO db = mapper.selectVOById(id);
        // 回填两层
        redis.opsForValue().set(key(id), db == null ? NULL_OBJ : db,
            jitterTtl(db));
        local.put(id, db == null ? NULL_OBJ : db);
        return db;
    }

    public void evict(Long id) {
        redis.delete(key(id));
        // 通过 Redis pub/sub 通知所有实例清本地
        redis.convertAndSend("cache:evict", "post:" + id);
    }

    @PostConstruct
    public void subscribeEvict() {
        // 监听 cache:evict 频道, 收到后 local.invalidate
    }
}
```

> 关键: 多实例部署时, L1 (本地) 失效要广播。 用 Redis pub/sub 通知, 或换成 Spring Cache + JetCache。

### Step 3: 缓存预热 (30 分钟)

```java
@Component
@RequiredArgsConstructor
public class CacheWarmer implements ApplicationRunner {
    private final PostMapper mapper;
    private final PostCacheService cache;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Warming up cache...");
        List<Long> hotIds = mapper.selectTopNIdsByViewCount(100);
        hotIds.parallelStream().forEach(cache::get);   // get 会自动回填
        log.info("Warmed up {} posts", hotIds.size());
    }
}
```

也可改成 Spring Event 触发, 或用单独的 `/admin/warmup` 接口手动触发。

### Step 4: Lua 限流 (30 分钟)

```java
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {
    private final StringRedisTemplate redis;
    private static final String LUA = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        local count = redis.call('ZCARD', key)
        if count >= limit then return 0 end
        redis.call('ZADD', key, now, now .. ':' .. math.random())
        redis.call('PEXPIRE', key, window)
        return 1
        """;
    private static final DefaultRedisScript<Long> SCRIPT =
        new DefaultRedisScript<>(LUA, Long.class);

    public boolean tryAcquire(String key, long windowMs, int limit) {
        Long r = redis.execute(SCRIPT, List.of(key),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(windowMs),
            String.valueOf(limit));
        return Long.valueOf(1L).equals(r);
    }
}
```

替代 45 章的 Bucket4j 单机版, 在多实例下也准确。

### Step 5: 大 key / 热 key 监控 (30 分钟)

#### 大 key 定时检测

```java
@Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
public void scanBigKeys() {
    // 用 redis-cli --bigkeys 不能 in-process, 改用 ScanOptions 逐个采样
    try (Cursor<String> cur = redis.scan(ScanOptions.scanOptions().count(100).build())) {
        while (cur.hasNext()) {
            String key = cur.next();
            Long size = redis.opsForValue().getOperations().execute(
                (RedisCallback<Long>) c -> c.serverCommands().memoryUsage(key.getBytes()));
            if (size != null && size > 1024 * 1024) {  // > 1MB
                log.warn("BIG KEY detected: {} = {} bytes", key, size);
                alertService.send("Redis big key: " + key);
            }
        }
    }
}
```

#### Prometheus exporter

```yaml
# docker-compose.yml
services:
  redis-exporter:
    image: oliver006/redis_exporter:v1.55.0
    ports: ["9121:9121"]
    environment:
      REDIS_ADDR: "redis://redis:6379"
```

Prometheus 抓 `redis-exporter:9121/metrics`, 关键指标:

- `redis_memory_used_bytes`
- `redis_commands_total{cmd="..."}`
- `redis_slowlog_length`
- `redis_keyspace_hits_total / misses_total` → 命中率
- `redis_connected_clients`

### Step 6: 故障演练 (30 分钟)

```bash
# 1. 起服务 + Redis
# 2. 验证 GET 走 Redis (log "cache hit")
# 3. docker stop redis
docker stop redis
# 4. 立即 GET, 应仍 200, log 有 WARN "Redis down, fallback to DB"
curl localhost:8080/api/v1/posts/1
# 5. 启动 redis
docker start redis
# 6. 等 30 秒 Lettuce 重连, 下次 GET 又走 Redis
```

代码层:

```java
public PostVO get(Long id) {
    try {
        return getWithRedis(id);
    } catch (RedisConnectionFailureException | QueryTimeoutException e) {
        log.warn("Redis unavailable, falling back to DB", e);
        return mapper.selectVOById(id);
    }
}
```

可选: 用 Resilience4j CircuitBreaker, 连续失败 5 次开熔断, 30 秒后试探恢复。

### Step 7: 测试 (30 分钟)

```java
@Test
void redissonLock_acquiredOnce() {
    RLock lock = redisson.getLock("test:1");
    assertTrue(lock.tryLock(0, 5, TimeUnit.SECONDS));
    assertFalse(lock.tryLock(0, 5, TimeUnit.SECONDS));   // 同一线程可重入, 但
    lock.unlock();
}

@Test
void rateLimit_window5_limit3() {
    String k = "test:rl";
    for (int i = 0; i < 3; i++) {
        assertTrue(limiter.tryAcquire(k, 5_000, 3));
    }
    assertFalse(limiter.tryAcquire(k, 5_000, 3));
}

@Test
void redisDown_fallbackToDb() {
    // 用 Testcontainers stop 容器, 再 get, 应返回 DB 数据
}
```

## 交付物

- [ ] `DistributedLock` (基于 Redisson 的可重入锁 + watchdog)
- [ ] `PostCacheService` 升级为 Caffeine + Redis 二级缓存
- [ ] 多实例本地缓存失效通过 Redis pub/sub 广播
- [ ] `CacheWarmer` 启动预热
- [ ] `RedisRateLimiter` Lua 滑动窗口
- [ ] 大 key 监控定时任务 + 告警
- [ ] Prometheus + redis-exporter 接入
- [ ] Redis 故障降级 (catch + fallback)
- [ ] 测试 ≥ 6 个
- [ ] README "缓存架构图" + "失败模式表"
- [ ] git commit: `ch47: redisson + multi-level cache + warmup + monitoring`

## 验收清单

| 验收项 | 标准 |
|---|---|
| Redisson 锁续期 | 持锁 60 秒 (业务 sleep), 锁不会过期被别人拿走 |
| L1 命中 | 同 id 连读 10 次, 后 9 次 redis-cli MONITOR 看不到 GET 命令 |
| L1 失效广播 | 实例 A 更新, 实例 B 的本地缓存在 1 秒内失效 |
| 预热 | 服务启动 30 秒内, Top 100 缓存命中率 > 95% |
| Lua 限流 | 跨实例验证: 2 个实例分别打 3 次, 总 6 次中后 3 次被拒 |
| 大 key 告警 | 故意 SADD 1w 元素, 凌晨任务发出 alert |
| 监控指标 | Prometheus `/targets` 看 redis-exporter UP, Grafana 面板可见命中率 |
| 故障降级 | stop redis 后接口仍 200, log 含 WARN; start redis 30 秒内恢复 |

## 扩展挑战

1. **Redis Cluster 接入**: 把 Redisson 切到 Cluster 模式 (`clusterServersConfig`), 体验跨 slot 限制和 hash tag 使用。
2. **JetCache 替换手撸多级缓存**: 接 JetCache (阿里), `@Cached(name="post", expire=600)` 注解化, 自动二级 + 一致性。
3. **Canal 监听 MySQL binlog**: 业务零侵入, DB 更新自动失效 Redis 缓存。
4. **Hot key 自动识别**: `redis-cli --hotkeys` 集成进监控, 自动给热 key 加本地缓存。
5. **基于 Redis Stream 的可靠延迟队列**: 替代 `TaskScheduler`, 实现延迟双删 / 重试。
6. **Redis 主从 + 哨兵部署**: 用 docker-compose 起 1 主 2 从 3 哨兵, 模拟主节点宕机自动选主。
