# Chapter 46 Redis 基础与缓存 - 自测

## Q1 (概念): Cache-Aside / Read-Through / Write-Through / Write-Behind 四种缓存模式对比

### 答案

| 模式 | 读流程 | 写流程 | 业务侵入 | 数据一致性 | 典型场景 |
|---|---|---|---|---|---|
| **Cache-Aside** (旁路) | App 先查缓存, miss 时回源 DB 并回填 | App 写 DB → 删 / 写缓存 | 高 (业务代码自己管) | 弱 (短暂不一致) | **最常用**, 博客 / 商品详情 |
| **Read-Through** | App 只查缓存, 缓存自己 miss 时去 DB 加载 | 同 Cache-Aside | 低 (透明) | 弱 | 缓存层封装好的库 (Caffeine) |
| **Write-Through** | 同 Read-Through | App 写缓存, 缓存同步写 DB | 低 | **强** | 对一致性要求高 + 写少 |
| **Write-Behind** (Write-Back) | 同上 | App 写缓存, 缓存**异步**批量刷 DB | 低 | 弱 + 有丢失风险 | 高写吞吐, 可容忍丢失 (PV 计数) |

**为什么 Cache-Aside 最常用?**

- 实现简单, 用 RedisTemplate 直接写
- DB 是真理 (source of truth), 缓存挂了不影响数据
- 缺点: 业务代码每处都得记得 "更新 DB → 删缓存"

**博客 API 选型**:
- 文章详情: Cache-Aside (业务侵入可接受)
- PV 计数: Write-Behind (Redis incr + 定时刷 DB)
- 会话: Write-Through 模拟 (写 DB 同步更新 Redis 黑名单)

### 常见坑

- 选 Cache-Aside 但用了"更新 DB → 写缓存" → 并发下旧值覆盖新值。**应该"更新 DB → 删缓存"**, 让下次读回填
- Write-Behind 没 graceful shutdown → 关机时缓存里的数据没刷回 DB → 丢

## Q2 (概念): 缓存穿透 / 击穿 / 雪崩三种问题区别和防护

### 答案

| 问题 | 现象 | 危害 | 防护 |
|---|---|---|---|
| **穿透** | 查询**根本不存在**的数据 (如 id=-1), 每次都打 DB | DB 被恶意刷 | 1. 空对象缓存 (短 TTL) <br> 2. 布隆过滤器 (BloomFilter) 前置 |
| **击穿** | 单个**热点** key 突然过期, 海量请求同时打 DB | DB 瞬时压力 | 1. 互斥锁 (setIfAbsent) <br> 2. 逻辑过期 (永不真过期, value 内带 expireAt) <br> 3. 热点不过期, 用 MQ 主动刷新 |
| **雪崩** | **大量** key 同时过期 (或 Redis 整体宕机) | DB 被全量打穿 | 1. TTL 随机抖动 (±30s) <br> 2. 多级缓存 (Caffeine + Redis) <br> 3. 限流降级 <br> 4. Redis 集群 (高可用) |

**示意区别**:
- 穿透: 数据**不存在** (key 不该有 value)
- 击穿: 数据存在, **一个**热点 key 过期了
- 雪崩: 数据存在, **大量** key 同时过期

**示例**:

```java
// 1. 穿透防护: 空对象缓存
PostVO p = postMapper.selectById(id);
redis.set(key, p == null ? NULL_OBJ : p, p == null ? 60s : 600s);

// 2. 击穿防护: 互斥锁
Boolean got = redis.setIfAbsent("lock:" + key, "1", 10s);
if (got) {
    // 只有一个线程回源
} else {
    Thread.sleep(50); return retry();
}

// 3. 雪崩防护: TTL 抖动
long ttl = 600 + ThreadLocalRandom.current().nextInt(60);
redis.set(key, value, Duration.ofSeconds(ttl));
```

### 常见坑

- 用 `id IN (1,2,3,4)` 批量查时一个 id 不存在 → 整个批次都打了 DB 但只有部分 miss, 难调试
- 布隆过滤器误判率没估算 → 误判率 1% 时仍有 1% 穿透
- 锁忘了释放或锁 TTL 太长 → 业务异常时其他请求长时间阻塞

## Q3 (代码改错): 找出下面缓存代码的问题

```java
@Service
public class PostService {
    @Autowired private RedisTemplate<String, Object> redis;
    @Autowired private PostMapper mapper;

    public PostVO get(Long id) {
        String key = "post:" + id;
        PostVO cached = (PostVO) redis.opsForValue().get(key);
        if (cached != null) return cached;
        PostVO db = mapper.selectVOById(id);
        redis.opsForValue().set(key, db, Duration.ofHours(1));
        return db;
    }

    public void update(Long id, PostUpdateReq req) {
        PostVO updated = mapper.update(id, req);
        redis.opsForValue().set("post:" + id, updated, Duration.ofHours(1));   // 更新缓存
    }

    public void delete(Long id) {
        mapper.delete(id);
        redis.delete("post:" + id);
    }

    @Scheduled(fixedRate = 1000)
    public void refreshHotCache() {
        Set<String> keys = redis.keys("post:*");
        for (String k : keys) {
            // refresh ...
        }
    }
}
```

### 答案

**问题清单**:

1. **缓存穿透**: `db == null` 时未缓存空对象, 攻击者用不存在 id 反复打 DB
2. **缓存雪崩**: TTL 固定 1 小时, 服务重启后批量 set, 1 小时后集体过期
3. **击穿无锁**: 热点 key 过期瞬间, 100 个并发都打 DB
4. **更新策略错**: `update` 写缓存 → 并发下旧值覆盖新值, 应**删缓存**
5. **`@Scheduled` 用 `keys`**: 阻塞 Redis 主线程, 数据量大时会卡顿
6. **`@Autowired` 字段注入**: Spring 推荐构造注入

**修复**:

```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final RedisTemplate<String, Object> redis;
    private final PostMapper mapper;

    private static final String KEY = "post:%d";
    private static final Object NULL_OBJ = new Object();
    private static final Duration NULL_TTL = Duration.ofMinutes(1);
    private static final Duration BASE_TTL = Duration.ofHours(1);

    public PostVO get(Long id) {
        String key = KEY.formatted(id);
        Object cached = redis.opsForValue().get(key);
        if (cached == NULL_OBJ) return null;
        if (cached != null) return (PostVO) cached;

        // 互斥锁防击穿
        String lock = "lock:" + key;
        Boolean got = redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(got)) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return get(id);
        }
        try {
            // double-check
            cached = redis.opsForValue().get(key);
            if (cached == NULL_OBJ) return null;
            if (cached != null) return (PostVO) cached;

            PostVO db = mapper.selectVOById(id);
            // 雪崩抖动 + 穿透防护
            long ttl = (db == null ? NULL_TTL : BASE_TTL).toSeconds()
                     + ThreadLocalRandom.current().nextInt(60);
            redis.opsForValue().set(key, db == null ? NULL_OBJ : db, Duration.ofSeconds(ttl));
            return db;
        } finally {
            redis.delete(lock);
        }
    }

    @Transactional
    public void update(Long id, PostUpdateReq req) {
        mapper.update(id, req);
        redis.delete(KEY.formatted(id));     // 删缓存而非写缓存
    }

    @Transactional
    public void delete(Long id) {
        mapper.delete(id);
        redis.delete(KEY.formatted(id));
    }

    @Scheduled(fixedRate = 60_000)
    public void refreshHotCache() {
        // 不要 keys * → 用 scan, 或者维护一个热门 id 集合
        ScanOptions opts = ScanOptions.scanOptions().match("post:*").count(100).build();
        try (Cursor<String> cursor = redis.scan(opts)) {
            cursor.forEachRemaining(k -> { /* refresh */ });
        }
    }
}
```

## Q4 (代码题): 实现 "更新 DB 后双删缓存 + 延迟队列" 处理强一致需求

### 背景

`更新 DB → 删缓存` 在极端并发下仍有不一致:

```
T1: 读, miss → 查 DB (旧值 A)
T2: 写 DB (新值 B) → 删缓存
T1: 把 A 写入缓存          ← 此时缓存是 A, DB 是 B
```

**双删 + 延迟删** 可缓解:

```
1. 删缓存
2. 更新 DB
3. 延迟 N 秒后再删一次
```

### 参考实现

```java
@Service
@RequiredArgsConstructor
public class PostCacheService {
    private final RedisTemplate<String, Object> redis;
    private final TaskScheduler scheduler;
    private final PostMapper mapper;

    public void update(Long id, PostUpdateReq req) {
        String key = "post:%d".formatted(id);
        redis.delete(key);                  // ① 先删一次
        mapper.update(id, req);             // ② 更新 DB
        scheduler.schedule(                 // ③ 1 秒后再删
            () -> redis.delete(key),
            Instant.now().plusSeconds(1)
        );
    }
}
```

**生产级方案: MQ 异步删**

```java
public void update(Long id, PostUpdateReq req) {
    String key = "post:%d".formatted(id);
    redis.delete(key);
    mapper.update(id, req);
    rabbit.convertAndSend("cache.delete.delay", key, m -> {
        m.getMessageProperties().setDelay(1000);   // RabbitMQ 延迟插件
        return m;
    });
}

@RabbitListener(queues = "cache.delete.delay")
public void delayedDelete(String key) {
    redis.delete(key);
}
```

**Canal binlog 监听**: 直接订阅 MySQL binlog, 业务无侵入。

**为什么不是分布式锁?**
- 锁影响吞吐
- 实际并发下"读到旧值"是可接受的, 几秒后自动收敛即可

### 决策表

| 一致性要求 | 方案 | 性能 | 复杂度 |
|---|---|---|---|
| 弱 (秒级容忍) | 删缓存 | ★★★★★ | ★ |
| 中 (秒内容忍) | 双删 + 延迟删 | ★★★★ | ★★ |
| 中 (异步) | MQ 延迟队列 | ★★★★ | ★★★ |
| 强 (近实时) | Canal 订阅 binlog | ★★★ | ★★★★ |
| 强 (同步) | 分布式锁 / 串行化 | ★★ | ★★★ |

## Q5 (综合题): 设计一个"热门文章排行"功能

### 需求

1. 实时统计每篇文章的"近 7 天阅读量"
2. 首页展示 Top 10, 5 秒刷新一次
3. 每天 0 点结算昨日榜单, 写入 `daily_hot` 表
4. 支撑 QPS 10w

### 答案

**方案: ZSET + 分桶**

```
key = "post:hot:2026-05-23"        # 每天一个 ZSET, score 是阅读量
score = 累加阅读次数
member = postId
```

#### 写入

```java
public void recordRead(Long postId) {
    String today = "post:hot:" + LocalDate.now();
    redis.opsForZSet().incrementScore(today, String.valueOf(postId), 1);
    redis.expire(today, Duration.ofDays(10));
}
```

QPS 10w 时 `ZINCRBY` 单实例 Redis 能扛 (单核 ~10w QPS)。 不够就分片 (按 postId hash 路由)。

#### Top 10 查询

```java
public List<PostVO> getTop10() {
    String today = "post:hot:" + LocalDate.now();
    Set<Object> ids = redis.opsForZSet().reverseRange(today, 0, 9);
    return ids.stream()
        .map(o -> Long.parseLong(o.toString()))
        .map(postCacheService::get)
        .filter(Objects::nonNull)
        .toList();
}
```

#### 5 秒刷新缓存 Top 10 结果

```java
@Cacheable(value = "hotTop10", key = "'today'")
public List<PostVO> getTop10Cached() { return getTop10(); }

// Caffeine TTL 5s, 避免每次请求都打 Redis ZSET
```

#### 近 7 天合并榜

`ZUNIONSTORE` 把 7 天合并:

```java
public List<PostVO> getTop10Last7d() {
    LocalDate today = LocalDate.now();
    List<String> keys = IntStream.range(0, 7)
        .mapToObj(i -> "post:hot:" + today.minusDays(i))
        .toList();
    String mergeKey = "post:hot:7d";
    redis.opsForZSet().unionAndStore(keys.get(0), keys.subList(1, 7), mergeKey);
    redis.expire(mergeKey, Duration.ofMinutes(10));
    Set<Object> ids = redis.opsForZSet().reverseRange(mergeKey, 0, 9);
    return loadByIds(ids);
}
```

#### 0 点结算

```java
@Scheduled(cron = "0 0 0 * * ?")
public void settle() {
    String yesterday = "post:hot:" + LocalDate.now().minusDays(1);
    Set<ZSetOperations.TypedTuple<Object>> tuples =
        redis.opsForZSet().reverseRangeWithScores(yesterday, 0, 99);
    // 批量写 daily_hot 表
    dailyHotMapper.batchInsert(toEntities(tuples));
}
```

#### 风险与对应

| 风险 | 对应 |
|---|---|
| 单 ZSET QPS 上限 | 分片: `post:hot:2026-05-23:shard0..shardN`, 查询时 union |
| Redis 宕机 | 写入降级: 先写本地内存队列, 异步重试; 读降级: 走 daily_hot |
| 刷流量作弊 | 同 user 1 天内对同一 post 仅计 1 次 (用 set 去重) |
| 数据丢失 | 定时持久化到 DB, RDB + AOF |
| 排行偏冷启动 | 新文章发布时初始加 10 分 (热度池) |

#### 面试 2 分钟讲法

> 热门榜核心是 ZSET, member 是 postId, score 是阅读量。我按天分桶, key 形如 `post:hot:2026-05-23`, 每次阅读 ZINCRBY +1。Top 10 用 ZREVRANGE。 近 7 天用 ZUNIONSTORE 合并。 0 点跑 cron 把昨日榜写 `daily_hot` 表持久化。前端 5 秒刷新结合 Caffeine 本地缓存抗住读 QPS。 风险点: 单 ZSET 上限 10w QPS, 真到这量级用分片 + union; 防作弊用 set 去重; Redis 挂了走 daily_hot 表降级。
