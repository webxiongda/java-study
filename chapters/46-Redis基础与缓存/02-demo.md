# Chapter 46 Redis 基础与缓存 - 实操 Demo

## Demo 目标

给博客 API 加 **Cache-Aside 缓存层**: 文章详情 / 热门排行 / 计数 (PV/收藏)。能演示:

- 缓存命中 (DB 不查)
- 缓存未命中 → 回源 → 回填 → 二次访问命中
- 缓存击穿 (热点 key 过期) 用互斥锁
- 缓存穿透 (查不存在 id) 用空对象缓存
- 缓存雪崩 (大量同时过期) 随机 TTL 抖动
- 计数器: incr / zset 排行

## 前置条件

- Redis 7 (`docker run -d -p 6379:6379 redis:7-alpine`)
- 前几章 (42 / 43 / 44) 已具备 Post 表 + Service

## 增量依赖

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

## 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

## 一、RedisTemplate 配置 (JSON 序列化)

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(new StringRedisSerializer());
        t.setHashKeySerializer(new StringRedisSerializer());

        ObjectMapper om = new ObjectMapper();
        om.activateDefaultTyping(om.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        om.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(om);

        t.setValueSerializer(json);
        t.setHashValueSerializer(json);
        return t;
    }
}
```

## 二、Cache-Aside (文章详情)

```java
@Service
@RequiredArgsConstructor
public class PostCacheService {

    private final RedisTemplate<String, Object> redis;
    private final PostMapper postMapper;

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofMinutes(1);
    private static final String KEY = "post:detail:%d";
    private static final PostVO NULL_OBJ = new PostVO(-1L, null, null, null, null);

    public PostVO getById(Long id) {
        String key = KEY.formatted(id);

        // 1. 读缓存
        Object cached = redis.opsForValue().get(key);
        if (NULL_OBJ.equals(cached)) return null;       // 穿透防护
        if (cached != null) return (PostVO) cached;

        // 2. 回源
        PostVO db = postMapper.selectVOById(id);
        if (db == null) {
            redis.opsForValue().set(key, NULL_OBJ, NULL_TTL);
            return null;
        }

        // 3. 回填 + 随机抖动防雪崩
        long ttl = TTL.toSeconds() + ThreadLocalRandom.current().nextInt(60);
        redis.opsForValue().set(key, db, Duration.ofSeconds(ttl));
        return db;
    }

    public void evict(Long id) {
        redis.delete(KEY.formatted(id));
    }
}
```

写操作 (update / delete) 后调 `evict`:

```java
@Transactional
public void update(Long id, PostUpdateReq req) {
    postMapper.update(id, req);
    cache.evict(id);    // 删缓存而不是写缓存, 避免并发不一致
}
```

## 三、击穿防护: 互斥锁

```java
public PostVO getByIdWithMutex(Long id) {
    String key = KEY.formatted(id);
    Object cached = redis.opsForValue().get(key);
    if (cached != null) return cached == NULL_OBJ ? null : (PostVO) cached;

    String lock = "lock:post:" + id;
    Boolean got = redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(10));
    if (!Boolean.TRUE.equals(got)) {
        // 没拿到锁, 短暂等待后重试一次缓存
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return getByIdWithMutex(id);
    }
    try {
        // 二次检查 (double-check)
        cached = redis.opsForValue().get(key);
        if (cached != null) return cached == NULL_OBJ ? null : (PostVO) cached;

        PostVO db = postMapper.selectVOById(id);
        redis.opsForValue().set(key, db == null ? NULL_OBJ : db,
            db == null ? NULL_TTL : TTL);
        return db;
    } finally {
        redis.delete(lock);
    }
}
```

## 四、计数器: 浏览量 PV

```java
public Long incrViewCount(Long postId) {
    return redis.opsForValue().increment("post:pv:" + postId);
}

public Long getViewCount(Long postId) {
    Object v = redis.opsForValue().get("post:pv:" + postId);
    return v == null ? 0L : Long.parseLong(v.toString());
}
```

定时任务每分钟把 Redis 计数 flush 回 DB:

```java
@Scheduled(fixedDelay = 60_000)
public void flushViewCount() {
    Set<String> keys = redis.keys("post:pv:*");
    for (String k : keys) {
        Long id = Long.parseLong(k.substring("post:pv:".length()));
        Long v = (Long) redis.opsForValue().get(k);
        if (v != null && v > 0) {
            postMapper.addViewCount(id, v);
            redis.opsForValue().decrement(k, v);    // 仅扣已 flush 的部分
        }
    }
}
```

> 注: `keys` 在生产应换成 `scan`, 避免阻塞 Redis。

## 五、排行: ZSET 热门文章

```java
public void recordRead(Long postId) {
    String key = "post:hot:" + LocalDate.now();   // 按天分桶
    redis.opsForZSet().incrementScore(key, postId.toString(), 1);
    redis.expire(key, Duration.ofDays(3));
}

public List<PostVO> topN(int n) {
    String key = "post:hot:" + LocalDate.now();
    Set<Object> ids = redis.opsForZSet().reverseRange(key, 0, n - 1);
    if (ids == null || ids.isEmpty()) return List.of();
    return ids.stream()
        .map(o -> getById(Long.parseLong(o.toString())))
        .filter(Objects::nonNull)
        .toList();
}
```

## 六、分布式锁 (SET NX + Lua)

```java
public boolean tryLock(String key, String token, Duration ttl) {
    Boolean ok = redis.opsForValue().setIfAbsent("lock:" + key, token, ttl);
    return Boolean.TRUE.equals(ok);
}

private static final String UNLOCK_LUA = """
    if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('del', KEYS[1])
    else
        return 0
    end
    """;

public void unlock(String key, String token) {
    redis.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
        List.of("lock:" + key), token);
}
```

## 运行与验证

```bash
# 1. 起 Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. 启动服务
mvn spring-boot:run

# 3. 命中验证
curl localhost:8080/api/v1/posts/1            # 首次, log "miss"
curl localhost:8080/api/v1/posts/1            # 二次, log "hit"

# 4. 看 Redis
redis-cli
> KEYS post:detail:*
> TTL post:detail:1
> GET post:detail:1

# 5. 击穿测试 (压测)
for i in $(seq 1 100); do curl localhost:8080/api/v1/posts/1 & done
# 看后端日志 "miss" 应该只出现一次

# 6. PV 测试
for i in $(seq 1 5); do curl localhost:8080/api/v1/posts/1; done
redis-cli INCR debug && redis-cli GET post:pv:1   # 应为 5

# 7. 排行
curl localhost:8080/api/v1/posts/hot
```

## 错误场景

| 场景 | 期望 |
|---|---|
| Redis 宕机 | Service 降级 (catch RedisConnectionFailureException, 走 DB) |
| 缓存反序列化失败 | log warn + 删除该 key + 走 DB + 下次重新填 |
| 并发更新 | 删缓存 (不是写缓存), 让后续读触发 miss 回源 |
| 大对象 | 不缓存 > 1MB 的 value, 拆分或不缓存 |

## 常见坑

- 缓存与 DB 不一致: 推荐 **"更新 DB → 删缓存"** (cache-aside), 不要"更新 DB → 写缓存"。后者并发下数据陈旧
- 用 `KEYS *` 扫描 → 阻塞主线程。生产用 `SCAN`
- 大批量同时过期 → 雪崩。TTL 加 ±30s 随机
- 缓存空对象 TTL 太长 → DB 真有数据时迟迟读不到。 1-5 分钟即可
- 序列化用 JDK 默认 → 改类后旧 value 反序列化失败。 用 JSON

## 提交

```bash
git add .
git commit -m "ch46: Redis cache-aside + counter + hot rank + distributed lock"
```
