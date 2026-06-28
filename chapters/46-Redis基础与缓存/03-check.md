# Chapter 46 Redis 基础与缓存 - 自测

## Q1 [L1·概念·章节内测] Redis 五种基础数据结构 + 真实场景对应,并解释为什么 Redis 单线程也快

**考点**: 五种基础数据结构 / Redis 是什么 / 单线程网络模型
**关联**: interview-bank.md#redis-why-fast

### 参考答案

**1) 五种基础数据结构 + 命令 + 项目场景**:

| 类型 | 关键命令 | 项目场景 |
|---|---|---|
| **String** | `SET / GET / INCR / SETEX / SETNX` | 文章详情缓存 `article:detail:{id}`、PV 计数、JWT 黑名单、分布式锁 token |
| **Hash** | `HSET / HGET / HGETALL / HINCRBY` | 对象 `user:profile:42 → {name, age, email}`,字段独立读写比 JSON 省内存 |
| **List** | `LPUSH / RPOP / LRANGE / BRPOP` | 时间线流、简易消息队列(`BRPOP` 阻塞消费) |
| **Set** | `SADD / SISMEMBER / SINTER` | 文章标签去重、共同关注、限定每天每用户对同 post 只计 1 次 PV |
| **ZSet** | `ZADD / ZRANGE / ZINCRBY / ZREVRANGE` | 热门文章排行(`post:hot:{date}`,score=PV)、延时队列(score=触发时间) |

进阶(47 章):Bitmap(签到) / HyperLogLog(UV 估算) / Geo / Stream(消息队列)。

**2) Redis 为什么快**:

- **纯内存操作**:数据全在 RAM,不走磁盘。
- **单线程模型**:命令串行执行,**没有锁竞争 / 上下文切换**开销,命令原子。
- **IO 多路复用**:用 epoll(Linux) 一个线程监听上万连接的 socket 事件,IO 不阻塞。
- **高效数据结构**:SDS 字符串、ziplist / listpack / skiplist 针对场景优化。
- **协议简单**:RESP 文本协议,解析快。

**3) 为什么"单线程"不慢**:

- 瓶颈在 **网络 IO 和命令吞吐**,不是 CPU。单线程 + 多路复用单实例可达 **10w QPS**。
- 多线程要面对锁、内存可见性、上下文切换,在内存型 KV 场景反而**得不偿失**。
- Redis 6.0 引入的"多线程"**只用于网络读写**(parse / reply),命令执行仍是单线程。

**🔥追问**:Redis 6.0 后说支持多线程,那原来"单线程"的承诺还成立吗?
- 成立。Redis 6.0 多线程**只优化网络 IO 阶段**(read / parse / write),命令执行仍是**单线程串行**——所以 `INCR` 之类的命令依然原子,无需加锁。
- 默认仍是单线程模式,需要 `io-threads 4` 显式开启,适合 **大 value 网络回包** 成为瓶颈的场景(如返回 100KB 的 JSON)。

---

## Q2 [L2·对比·章节内测] 过期策略 vs 内存淘汰策略 — 区别是什么?常见 6 种 maxmemory-policy 怎么选?

**考点**: 过期策略与内存淘汰 / allkeys-lru / volatile-ttl
**关联**: interview-bank.md#redis-eviction-policy

### 参考答案

**两者完全不同**:

| 维度 | **过期策略**(expire) | **内存淘汰策略**(maxmemory-policy) |
|---|---|---|
| 触发 | 你给 key 设了 TTL,到期了 | Redis 内存达到 `maxmemory` 上限,**写入时**触发 |
| 范围 | 仅设过 expire 的 key | 全部 key(allkeys-*) 或仅设过 expire 的(volatile-*) |
| 目的 | 主动清理无用数据 | 防止 OOM,腾位置写新数据 |

**过期策略(两种叠加)**:

- **惰性删除**:读到一个 key 才检查 TTL,过期了就删。优点省 CPU,缺点过期 key 长时间占内存。
- **定期删除**:Redis 后台每 100ms 抽样部分 db,删一批过期 key。
- 单独用任何一种都有问题,**两者叠加** = 平衡 CPU 与内存。

**8 种 maxmemory-policy**:

| 策略 | 行为 | 适用 |
|---|---|---|
| `noeviction` | 写报错(默认) | ❌ **绝对不要在缓存场景用** |
| `allkeys-lru` ⭐ | 全部 key 中 LRU | **通用缓存首选** |
| `allkeys-lfu` | 全部 key 中 LFU(访问频率) | 长尾热点(电商商品) |
| `allkeys-random` | 全部随机 | 极少用,数据无热度差 |
| `volatile-lru` | 仅设了 expire 的 LRU | 缓存与持久数据混存 |
| `volatile-lfu` | 同上 LFU | 同上 |
| `volatile-ttl` | 优先淘汰快过期的 | 缓存生命周期可预测 |
| `volatile-random` | 仅过期 key 随机 | 罕见 |

**项目实操**:

- 博客 API 缓存层 → `maxmemory 2gb` + `maxmemory-policy allkeys-lru`
- 千万别用默认 `noeviction`,会让"删缓存写新缓存"的写流程报错。

**🔥追问**:LFU 和 LRU 的差异?Redis 的 LRU 是真的 LRU 吗?
- **LRU 看时间**(最久没访问),**LFU 看频率**(访问次数最少)。商品类长尾访问选 LFU 更准。
- Redis **不是真 LRU**(真 LRU 需要双向链表),而是 **抽样近似 LRU**:从 `maxmemory-samples`(默认 5) 个 key 里挑最久没用的淘汰。提高 samples 更接近真实 LRU,代价是 CPU 略涨。

---

## Q3 [L2·代码改错·章节内测+面试高频] 找出下面缓存代码的 6 个问题并修复(防穿透/击穿/雪崩 + Cache-Aside)

**考点**: Cache-Aside / 三大缓存问题 / RedisConfig / getById / evict
**关联**: interview-bank.md#cache-pitfalls

### 题干

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
        redis.opsForValue().set("post:" + id, updated, Duration.ofHours(1));
    }

    public void delete(Long id) {
        mapper.delete(id);
        redis.delete("post:" + id);
    }

    @Scheduled(fixedRate = 1000)
    public void refreshHotCache() {
        Set<String> keys = redis.keys("post:*");
        for (String k : keys) { /* refresh */ }
    }
}
```

### 参考答案

**6 个问题**:

1. **缓存穿透**:`db == null` 时不缓存空对象,攻击者用不存在 id 反复打 DB
2. **缓存雪崩**:TTL 固定 1 小时,服务重启批量预热 → 1 小时后**集体过期**
3. **缓存击穿**:热点 key 过期瞬间,N 个并发都打 DB(无互斥锁)
4. **更新策略错**:Cache-Aside 应是"更新 DB → **删**缓存",当前是"写缓存" → 并发下 A 读旧 + B 写新 + A 用旧值覆盖
5. **`keys` 阻塞**:Redis 单线程,`keys post:*` 在百万级 key 时会卡住整个实例
6. **字段注入**:`@Autowired` 字段注入不利于测试和不可变,应改构造注入

**修复(对照 02-demo.md 的 RedisConfig + getById + evict + getByIdWithMutex)**:

```java
// 1) RedisConfig 使用 JSON 序列化,避免 JDK 反序列化版本兼容问题
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(new StringRedisSerializer());
        ObjectMapper om = new ObjectMapper();
        om.activateDefaultTyping(om.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL);
        om.registerModule(new JavaTimeModule());
        t.setValueSerializer(new GenericJackson2JsonRedisSerializer(om));
        return t;
    }
}

@Service
@RequiredArgsConstructor                   // ✅ 构造注入
public class PostService {
    private final RedisTemplate<String, Object> redis;
    private final PostMapper mapper;

    private static final String KEY = "post:detail:%d";
    private static final Object NULL_OBJ = new Object();
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    public PostVO getById(Long id) {                          // ✅ 对应 demo 的 getById
        String key = KEY.formatted(id);
        Object cached = redis.opsForValue().get(key);
        if (cached == NULL_OBJ) return null;                   // ✅ 穿透防护命中
        if (cached != null) return (PostVO) cached;

        // ✅ 击穿防护: 互斥锁 (即 demo 中的 getByIdWithMutex)
        String lock = "lock:" + key;
        Boolean got = redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(got)) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return getById(id);
        }
        try {
            cached = redis.opsForValue().get(key);             // double-check
            if (cached == NULL_OBJ) return null;
            if (cached != null) return (PostVO) cached;

            PostVO db = mapper.selectVOById(id);
            // ✅ 雪崩防护: TTL 抖动 + ✅ 穿透防护: 空对象短 TTL
            long ttl = (db == null ? NULL_TTL : TTL).toSeconds()
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
        evict(id);                                              // ✅ 删缓存而非写缓存
    }

    @Transactional
    public void delete(Long id) {
        mapper.delete(id);
        evict(id);
    }

    public void evict(Long id) {                                // ✅ 对应 demo 的 evict
        redis.delete(KEY.formatted(id));
    }

    @Scheduled(fixedRate = 60_000)
    public void refreshHotCache() {
        // ✅ SCAN 替代 KEYS,不阻塞 Redis
        ScanOptions opts = ScanOptions.scanOptions().match("post:detail:*").count(100).build();
        try (Cursor<String> cursor = redis.scan(opts)) {
            cursor.forEachRemaining(k -> { /* refresh */ });
        }
    }
}
```

**🔥追问**:为什么"先更新 DB → 删缓存"而不是"先删缓存 → 更新 DB"?
- "先删缓存 → 更新 DB" 在并发下风险更大:
  - T1 删缓存
  - T2 读 miss → 查 DB(旧值)→ 写缓存(旧值)
  - T1 更新 DB → 此时缓存是旧值,DB 是新值,**不一致窗口直到 TTL 过期**
- "先更新 DB → 删缓存" 的不一致窗口短得多(只在删缓存动作之间),且通过"延迟双删"可进一步消除。

---

## Q4 [L2·代码编写·章节内测+面试高频] 用 ZSet 实现"博客文章热门排行 Top10"(对应 demo 中的 incrViewCount + ZSet 排行)

**考点**: ZSet 排行 / incrViewCount / 缓存命名规范
**关联**: interview-bank.md#redis-zset-leaderboard

### 题干

实现:

1. 每次访问文章,把 PV +1(同时写 String 计数器 + ZSet 当日榜)
2. 查"今日热门 Top10"
3. 查"近 7 天热门 Top10"
4. 0 点结算昨日榜单写入 `daily_hot` 表

要求:命名遵循章内"缓存命名规范"小节,key 形如 `{业务}:{实体}:{id}` 或 `{业务}:{维度}:{时间}`。

### 参考答案

```java
@Service
@RequiredArgsConstructor
public class PostStatsService {
    private final RedisTemplate<String, Object> redis;
    private final DailyHotMapper dailyHotMapper;
    private final PostMapper postMapper;

    // ===== 1. PV 计数: String 计数器 + ZSet 当日榜双写 =====
    public Long incrViewCount(Long postId) {                  // ✅ 对应 demo 的 incrViewCount
        // ① String 计数(精确总 PV)—— 缓存命名规范: 业务:实体:id
        String pvKey = "post:pv:" + postId;
        Long pv = redis.opsForValue().increment(pvKey);

        // ② ZSet 当日榜(用于排行)—— 业务:维度:时间
        String hotKey = "post:hot:" + LocalDate.now();
        redis.opsForZSet().incrementScore(hotKey, postId.toString(), 1);
        redis.expire(hotKey, Duration.ofDays(10));            // 保留 10 天用于近 7 天合并

        return pv;
    }

    // ===== 2. 今日 Top10 =====
    public List<PostVO> topToday() {
        String key = "post:hot:" + LocalDate.now();
        Set<Object> ids = redis.opsForZSet().reverseRange(key, 0, 9);
        return loadByIds(ids);
    }

    // ===== 3. 近 7 天 Top10(ZUNIONSTORE 合并) =====
    public List<PostVO> topLast7d() {
        LocalDate today = LocalDate.now();
        List<String> keys = IntStream.range(0, 7)
            .mapToObj(i -> "post:hot:" + today.minusDays(i))
            .toList();
        String mergeKey = "post:hot:7d";
        redis.opsForZSet().unionAndStore(keys.get(0), keys.subList(1, 7), mergeKey);
        redis.expire(mergeKey, Duration.ofMinutes(5));        // 短 TTL,5 分钟内复用合并结果
        Set<Object> ids = redis.opsForZSet().reverseRange(mergeKey, 0, 9);
        return loadByIds(ids);
    }

    // ===== 4. 0 点结算昨日榜 =====
    @Scheduled(cron = "0 5 0 * * ?")                          // 0:05 给前一天榜留余量
    public void settleYesterday() {
        String key = "post:hot:" + LocalDate.now().minusDays(1);
        Set<ZSetOperations.TypedTuple<Object>> tuples =
            redis.opsForZSet().reverseRangeWithScores(key, 0, 99);
        if (tuples == null) return;
        List<DailyHot> rows = tuples.stream()
            .map(t -> new DailyHot(
                Long.parseLong(t.getValue().toString()),
                t.getScore().longValue(),
                LocalDate.now().minusDays(1)))
            .toList();
        dailyHotMapper.batchInsert(rows);
    }

    private List<PostVO> loadByIds(Set<Object> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
            .map(o -> postMapper.selectVOById(Long.parseLong(o.toString())))
            .filter(Objects::nonNull)
            .toList();
    }
}
```

**关键考点**:

- **缓存命名规范**:`{业务前缀}:{实体}:{id}[:{子维度}]`,如 `post:pv:1`、`post:hot:2026-05-23` — 见名知意,便于 SCAN 匹配。
- **ZINCRBY 原子性**:多线程并发计数无需加锁。
- **ZUNIONSTORE 合并 7 天榜**:O(N×M) 操作,只在用户查询时算一次,结果缓存 5 分钟。
- **不要查询时 ZRANGE 全表**:`reverseRange(0, 9)` 只取 Top10,O(log N + 10)。

**🔥追问**:每次阅读都 INCRBY,刷数据怎么办?
- 在写入前加"用户+文章" 去重 Set:`SADD post:pv:dedup:{date}:{postId} {userId}`,TTL=1 天。
- 用 `SADD` 返回值判断是否首次(0 = 已加过,1 = 新增),首次才 `INCR`。
- 或者用 HyperLogLog `PFADD` 估算 UV(节省内存,1% 误差)。

---

## Q5 [L3·场景设计·面试高频] 设计博客 API 三级缓存方案,处理"突发热点文章 10w QPS"

**考点**: Cache-Aside / 缓存一致性 / 击穿防护 / 缓存命名规范
**关联**: interview-bank.md#multi-level-cache

### 题干

需求:博客系统,日常 QPS 5000,某文章被微博大 V 转发,瞬时 10w QPS 集中在一篇文章。
约束:

- 不能让 DB 倒
- 文章详情容忍 5 秒内不一致
- Redis 单实例上限假设 8w QPS
- 服务 4 核 8GB × 6 实例

### 参考答案

**方案:Caffeine(本地)+ Redis(集中)+ DB,三级缓存**:

```
client → [Caffeine L1] miss → [Redis L2] miss → [DB] → 回填 Redis → 回填 Caffeine
                       ↑                ↑                  ↑
                       5s TTL           10min+随机抖动    源数据
```

**1) L1: 本地 Caffeine(对抗单 key 热点)**

```java
@Bean
public Cache<String, PostVO> postLocalCache() {
    return Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(Duration.ofSeconds(5))   // 5 秒 → 容忍不一致窗口
        .recordStats()
        .build();
}
```

- 6 实例 × 单实例本地 hit 8000 QPS → 4.8w QPS **不打 Redis**。
- 容量 1000 个 key 足够覆盖热门文章池。

**2) L2: Redis 集群 + 互斥锁(击穿防护)**

```java
public PostVO getById(Long id) {
    String key = "post:detail:" + id;
    return localCache.get(id.toString(), k -> loadFromRedis(id));
}

private PostVO loadFromRedis(Long id) {
    String key = "post:detail:" + id;
    Object cached = redis.opsForValue().get(key);
    if (cached == NULL_OBJ) return null;
    if (cached != null) return (PostVO) cached;

    // 互斥锁防止 6 实例同时打 DB
    String lock = "lock:" + key;
    if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(3)))) {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return loadFromRedis(id);
    }
    try {
        PostVO db = postMapper.selectVOById(id);
        long ttl = 600 + ThreadLocalRandom.current().nextInt(60);   // 雪崩防护
        redis.opsForValue().set(key, db == null ? NULL_OBJ : db, Duration.ofSeconds(ttl));
        return db;
    } finally {
        redis.delete(lock);
    }
}
```

**3) 写操作同步两级缓存**

```java
@Transactional
public void update(Long id, PostUpdateReq req) {
    postMapper.update(id, req);
    redis.delete("post:detail:" + id);                       // 删 L2
    redisPubSub.convertAndSend("cache:evict", id.toString()); // 广播
}

// 各实例订阅,清自己的 L1
@EventListener
public void onEvict(String idStr) {
    localCache.invalidate(idStr);
}
```

**4) 容量与 QPS 估算**

| 层 | QPS | 命中后耗时 | 抗压策略 |
|---|---|---|---|
| L1 Caffeine | 10w 全打 → 95% 命中 = 9.5w | < 1μs | 6 实例分担,单实例 1.6w QPS,纯 CPU |
| L2 Redis | 剩 5000 → 99% 命中 = 4950 | ~1ms | 单实例 8w 上限完全够 |
| DB | 剩 50 / 秒 | ~5ms | 互斥锁 + 主从,完全顶住 |

**5) 风险与对应**

| 风险 | 对应 |
|---|---|
| L1 不一致 5 秒 | 业务允许;评论计数等关键数据走 Redis 直读 |
| Caffeine 内存爆 | `maximumSize` 限上限,LRU 自动淘汰 |
| Redis 集群崩 | 服务降级:返回 DB + 限流 + 加 Sentinel 报警 |
| 删 L1 广播丢失 | 5 秒 TTL 兜底,最多陈旧 5 秒 |

**🔥追问**:为什么不用分布式锁让所有实例只有一个回源?
- 分布式锁会让其他 5 实例 **同步等待**,延迟劣化(等锁可能数十 ms)。
- 当前方案让每实例**独立用 Redis 互斥锁**,最差情况 6 实例打 6 次 DB(单 ms),DB 完全扛得住。
- 分布式锁应留给 **写场景**(防超卖、防重复消费),读场景没必要。

**🔥追问**:怎么发现"热点文章"?
- Redis 命令 `--hotkeys`(基于 LFU 采样)。
- 在 `incrViewCount` 时若 PV 增速过快(> 1000/s)主动写入"热点池",触发 L1 预加载。
- 网关层(Nginx + Lua / Apisix)统计 URL 维度 QPS,Top 10 自动推送热点服务。

---

## 通过标准

- [ ] 能默写五种基础数据结构 + 每种 2 个项目场景
- [ ] 能讲清"过期策略"和"内存淘汰策略"的区别 + 为什么默认 noeviction 不能用
- [ ] 能找出 Q3 代码 6 个问题并写出修复
- [ ] 能用 ZSet + 命名规范实现热门排行,并知道刷量去重方案
- [ ] 能讲清三级缓存方案下 10w QPS 的流量分布,知道为什么不用分布式锁
- [ ] 能复述 02-demo.md 中 `PostCacheService` 的 `getById` / `evict` / `getByIdWithMutex` / `incrViewCount` / `recordRead` / `topN` / `flushViewCount` / `getViewCount` / `tryLock` / `unlock` 各自职责
- [ ] 能写出 Spring Boot 集成 Redis 的 application.yml(host / port / pool / timeout)
- [ ] 能对照 01-theory.md 的"项目场景对照"表举出 5 个 key 形态(article / user / auth / rate / lock)
- [ ] 能列出"常见坑"6 条以上(keys 阻塞 / 大 key / 热 key / TTL 缺失 / JDK 序列化 / 空对象 TTL 过长)

---

## 与 01-theory / 02-demo 的映射回顾

> 自测前请确认能把每道 Q 关联到下表对应的理论小节与 demo 代码块,做不到时回去再看一遍。

| Q | 对应理论小节 | 对应 demo 代码块 |
|---|---|---|
| Q1 | 1. Redis 是什么 / 2. 五种基础数据结构 | — |
| Q2 | 3. 过期策略与内存淘汰 | — |
| Q3 | 4. Cache-Aside / 6. 三大缓存问题 / 10. 常见坑 | 一、RedisConfig / 二、PostCacheService.getById + evict / 三、getByIdWithMutex |
| Q4 | 7. 缓存命名规范 / 9. 项目场景对照 | 四、incrViewCount + getViewCount + flushViewCount / 五、recordRead + topN |
| Q5 | 5. 缓存一致性 / 8. Spring Boot 集成 | PostCacheService 全套 + 六、tryLock + unlock |

---

> **下一章**:[[47-Redis进阶]] — RDB / AOF / 主从复制 / Redisson 分布式锁 / 集群模式
