# Chapter 47 Redis 进阶 - 实操 Demo

## Demo 目标

跑通 4 个生产级模式：
1. **穿透**：Bloom Filter（简化版用 Set）+ 空值缓存。
2. **击穿**：Redisson 互斥锁，回源只一次。
3. **雪崩**：随机 TTL 偏移。
4. **分布式锁**：Redisson `RLock` + 看门狗自动续期；Lua 原子释放。

## 前置条件

- 46 章已接入 Spring Data Redis。
- Redis 7+。

## 增量依赖

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>${redisson.version}</version>
</dependency>
```

## 1. 缓存穿透 + 击穿 + 雪崩（一处全部解决）

```java
@Service
@RequiredArgsConstructor
public class ArticleCacheService {
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ArticleMapper mapper;
    private final ObjectMapper json;

    private static final Duration NULL_TTL = Duration.ofMinutes(2);
    private static final Duration HIT_TTL  = Duration.ofMinutes(30);
    private static final String  NULL_VAL  = "__NULL__";

    public ArticleDTO getDetail(Long id) throws Exception {
        String key = "article:detail:" + id;
        String cached = redis.opsForValue().get(key);

        // 命中正常值
        if (cached != null && !NULL_VAL.equals(cached))
            return json.readValue(cached, ArticleDTO.class);
        // 命中空值哨兵：拦截穿透
        if (NULL_VAL.equals(cached)) return null;

        // miss → 防击穿：用 Redisson 分布式锁，回源串行化
        RLock lock = redisson.getLock("lock:article:" + id);
        boolean got = lock.tryLock(3, 10, TimeUnit.SECONDS);
        if (!got) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "load busy");
        try {
            // double-check：拿到锁后再查一次缓存
            cached = redis.opsForValue().get(key);
            if (cached != null) return NULL_VAL.equals(cached) ? null
                                : json.readValue(cached, ArticleDTO.class);

            ArticleDTO db = mapper.selectDetail(id);
            if (db == null) {
                redis.opsForValue().set(key, NULL_VAL, NULL_TTL);   // 防穿透
                return null;
            }
            // 防雪崩：基础 TTL ± 随机 5 分钟
            long jitter = ThreadLocalRandom.current().nextLong(-300, 300);
            redis.opsForValue().set(key, json.writeValueAsString(db),
                HIT_TTL.plusSeconds(jitter));
            return db;
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

**写场景的缓存清理（Cache-Aside）**：

```java
@Transactional
public void update(ArticleUpdateRequest req) {
    mapper.update(req);                                  // 1. 先写 DB
    redis.delete("article:detail:" + req.id());          // 2. 后删缓存（不要更新）
}
```

## 2. Redisson 分布式锁（业务场景）

```java
@Service
@RequiredArgsConstructor
public class StockService {
    private final RedissonClient redisson;
    private final StockMapper mapper;

    public void deduct(Long itemId, int qty) throws InterruptedException {
        RLock lock = redisson.getLock("stock:" + itemId);
        if (!lock.tryLock(5, TimeUnit.SECONDS)) {  // leaseTime 不传 → 看门狗自动续期 30s
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "busy");
        }
        try {
            int left = mapper.getStock(itemId);
            if (left < qty) throw new BusinessException(ErrorCode.BIZ, "库存不足");
            mapper.deduct(itemId, qty);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

## 3. 原生 SET NX + Lua 释放（不依赖 Redisson 的版本）

```java
public boolean tryLock(String key, String token, long ttlSec) {
    Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(ttlSec));
    return Boolean.TRUE.equals(ok);
}

private static final String UNLOCK_LUA =
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "  return redis.call('del', KEYS[1]) " +
    "else return 0 end";

public boolean unlock(String key, String token) {
    Long r = redis.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                           List.of(key), token);
    return Long.valueOf(1L).equals(r);
}
```

**为什么必须 Lua**：`if get == token then del` 三步在网络间隙里可能被别人抢到锁，Lua 在 Redis 单线程内原子执行。

## 4. 排行榜（ZSET）

```java
public void addLike(Long articleId) {
    redis.opsForZSet().incrementScore("rank:articles", articleId.toString(), 1);
}
public List<TypedTuple<String>> top10() {
    return new ArrayList<>(redis.opsForZSet().reverseRangeWithScores("rank:articles", 0, 9));
}
```

## 运行与验证

| 场景 | 验证方式 | 期望 |
|---|---|---|
| 穿透 | 查 id=999999（DB 不存在）100 次 | DB 只查 1 次，后续走空值缓存 |
| 击穿 | 删 key + 用 ab/wrk 同 id 100 并发 | DB 只查 1 次（锁串行） |
| 雪崩 | 批量预热 1000 条同时 TTL=30min，查 `TTL key` | TTL 分散在 1500-2100 秒 |
| 分布式锁 | 两个客户端同时 deduct，故意 sleep | 串行化，最终库存正确 |
| Lua 释放 | A 持锁，B 故意 unlock A 的 key | 返回 0，未删 |

## 常见坑

- 用 `RedisTemplate` 而非 `StringRedisTemplate` → 默认 JdkSerializationRedisSerializer，key 是乱码二进制。 配 `Jackson2JsonRedisSerializer`。
- 锁的 `leaseTime` 设小了 + 业务长 → 看门狗到不了，锁过期被别人抢。 用 Redisson 不传 leaseTime 即可自动续期。
- 不 `isHeldByCurrentThread` 就 unlock → 释放别人的锁。

## 提交

```bash
git commit -m "chapter 47: redis penetration/breakdown/avalanche + redisson rlock"
```
