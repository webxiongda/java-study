# Chapter 47 Redis 进阶 - 自测

## Q1 (概念): Redis 内存淘汰策略有哪些, 各自适用什么场景, 你的项目怎么选

### 答案

| 策略 | 行为 | 适用 |
|---|---|---|
| noeviction | 满了就报错 | KV 主存, 不能丢数据 |
| allkeys-lru | 所有 key LRU | 纯缓存, 通用 |
| allkeys-lfu | 所有 key LFU (频率) | 长尾访问明显 (头部少数 key 长期热) |
| volatile-lru | 仅 TTL key LRU | 混合存储 (永久 key + 缓存) |
| volatile-ttl | 优先淘汰 TTL 短的 | 大量短时缓存 |
| volatile-random | TTL key 随机淘汰 | 测试 |
| allkeys-random | 全部随机 | 测试 |

**博客 API 怎么选?**

- 所有 key 都有 TTL (Cache-Aside 缓存层, 不放永久数据)
- 头部 100 篇文章占总 QPS 80% → 用 **allkeys-lfu**, 让最热的文章一直驻留

**LRU vs LFU 关键差异**:
- LRU: "最近访问过的留下", 适合临时热度变化场景
- LFU: "频率高的留下", 适合"少数 key 长期热"的场景 (符合 80/20 法则)

**配置**:

```conf
# redis.conf
maxmemory 4gb
maxmemory-policy allkeys-lfu
```

> Redis 用近似 LRU/LFU (不是真的全局排序), 默认采样 5 个 key, 可调 `maxmemory-samples 10` 提高精度。

### 常见坑

- `noeviction` + 持续写入 → 写入报错而非淘汰旧的, 业务方没处理 → 接口报 500
- LFU 计数器初始 5, 新 key 容易被淘汰 → 注意预热

## Q2 (概念): Redis 单线程为什么这么快, 6.x 的"多线程"加在哪一层

### 答案

**Redis 4.x / 5.x 单线程的"快"来源**:

1. **内存操作**: 数据全在内存, 访问 ns 级
2. **IO 多路复用**: epoll / kqueue, 一个线程监听上千连接, 不像传统每连接一线程
3. **无锁数据结构**: 单线程不需要加锁, ZSET / HASH 都是裸操作
4. **优化协议**: RESP 文本协议简单, 解析快
5. **避免上下文切换**: 单线程没有 CPU 切换开销
6. **批处理**: Pipeline / MULTI 把多个命令合并一次 RTT

**单线程的限制**:
- 单 CPU 核心瓶颈, 大 key / 长 Lua 阻塞所有连接
- 多核机器浪费

**Redis 6.x 多线程加在哪?**

只在 **网络 IO** 层多线程化:

```
[client conn1] ─┐                       ┌─ command exec (单线程)
[client conn2] ─┼─ read multi-thread ──→│   (这一层仍单线程, 保证无锁)
[client conn3] ─┘                       └─ write multi-thread (回复)
```

- `io-threads-do-reads yes` + `io-threads 4`
- **命令执行仍是单线程**, 所以 SET / GET / ZADD 的并发模型没变
- 适用网络是瓶颈的场景 (10G+ 网卡 + 大量小连接)

**Redis 7.x 进一步**: 写命令也开始支持多线程 (实验性), 但默认关闭。

**面试回答要点**:
> Redis 单线程是"命令执行单线程", 不是"全进程单线程"。 持久化用 fork 子进程, 慢操作用 BIO 后台线程, 6.x 网络读写多线程化。核心数据操作之所以单线程, 是为了避免数据结构加锁的开销, 内存操作快到加锁反而成了瓶颈。

### 常见坑

- 以为 6.x 启用 IO 多线程后能扛"大 key 操作" → 错。大 key 仍卡住命令执行线程
- 期待多线程线性扩展 QPS → 命令执行单线程, 上限还是 ~15w QPS 单实例

## Q3 (代码改错): 找出下面分布式锁的问题

```java
@Service
public class OrderService {
    @Autowired private StringRedisTemplate redis;

    public void place(Long userId) {
        String key = "lock:order:" + userId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1");      // ① 没 TTL
        if (!Boolean.TRUE.equals(ok)) {
            throw new BizException("正在处理");
        }
        try {
            doBusiness();                                              // ② 业务慢
        } finally {
            redis.delete(key);                                          // ③ 直接 del
        }
    }

    private void doBusiness() throws Exception {
        Thread.sleep(30_000);   // 可能 30 秒
    }
}
```

### 答案

**问题**:

1. **没 TTL → 死锁**: 业务异常 / JVM crash → `delete` 永远不执行 → 这个 user 再也无法下单
2. **业务超时 → 锁被别人拿走**: 如果加了 TTL=10s, 业务跑了 30s, 第 10s 后另一个请求拿到了锁, 第 30s 当前线程 `delete` 删了别人的锁
3. **直接 `del` 无所有者校验**: 同问题 2, 可能删别人的锁
4. **`setIfAbsent("1")` token 不唯一**: 任何并发线程都可能误删

**修复 (SET NX PX + Lua 释放)**:

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final StringRedisTemplate redis;

    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end""";
    private static final DefaultRedisScript<Long> UNLOCK =
        new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    public void place(Long userId) {
        String key = "lock:order:" + userId;
        String token = UUID.randomUUID().toString();
        Boolean got = redis.opsForValue()
            .setIfAbsent(key, token, Duration.ofSeconds(30));   // 业务上限的 1.5 倍
        if (!Boolean.TRUE.equals(got)) {
            throw new BizException("正在处理");
        }
        try {
            doBusiness();
        } finally {
            redis.execute(UNLOCK, List.of(key), token);
        }
    }
}
```

**进一步: Redisson 看门狗** (业务真的可能跑很久):

```java
@Autowired private RedissonClient redisson;

public void place(Long userId) {
    RLock lock = redisson.getLock("lock:order:" + userId);
    if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {   // 等 0 秒, 持有 30 秒
        throw new BizException("正在处理");
    }
    try {
        doBusiness();
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```

Redisson 在持锁期间每 (ttl/3) 秒自动续期, 业务结束或 JVM 退出才彻底释放。

### 常见坑

- `setIfAbsent(key, val, ttl)` 不是原子 → 错, Spring Data Redis 这个方法走的是 `SET key val NX PX ttl`, 是原子的
- 用 `expire(key, ttl)` 单独设 TTL → 不原子, NX 成功后还没 expire 时进程挂掉 → 死锁

## Q4 (代码题): 用 Redisson 实现 "评论限频: 同一用户对同一文章 5 秒内只能评论一次"

### 参考实现

```java
@Service
@RequiredArgsConstructor
public class CommentService {

    private final RedissonClient redisson;
    private final CommentMapper mapper;

    public Long create(Long userId, Long postId, String content) {
        String key = "comment:cooldown:%d:%d".formatted(userId, postId);
        RBucket<Boolean> bucket = redisson.getBucket(key);
        boolean ok = bucket.setIfAbsent(true, Duration.ofSeconds(5));
        if (!ok) {
            throw new TooManyRequestsException("评论过于频繁, 请 5 秒后再试");
        }
        // 业务
        return mapper.insert(new CommentDO(null, userId, postId, content, LocalDateTime.now()));
    }
}
```

**单测**:

```java
@Test
void shouldRejectSecondCommentWithin5s() {
    commentService.create(1L, 100L, "first");
    assertThrows(TooManyRequestsException.class,
        () -> commentService.create(1L, 100L, "second"));
}

@Test
void shouldAllowAfter5s() throws InterruptedException {
    commentService.create(1L, 100L, "first");
    Thread.sleep(5_100);
    assertDoesNotThrow(() -> commentService.create(1L, 100L, "second"));
}
```

**为什么用 `setIfAbsent` + TTL 而不是 `setIfPresent` 或 `incr`?**

- `setIfAbsent(true, 5s)`: 不存在时设置, 5 秒后自动消失。原子, 简单。是"冷却"的标准实现
- `incr` + 判断: 也行, 但要每次 expire, 操作更多
- 不要用先 `get` 后 `set`: 并发下不原子

**进阶: 滑动窗口**

如果是"5 秒内最多 3 条评论":

```java
public Long create(Long userId, Long postId, String content) {
    String key = "comment:rate:%d:%d".formatted(userId, postId);
    long now = System.currentTimeMillis();
    long window = 5_000;
    int limit = 3;

    RScoredSortedSet<String> zset = redisson.getScoredSortedSet(key);
    zset.removeRangeByScore(0, now - window);
    long count = zset.size();
    if (count >= limit) throw new TooManyRequestsException("评论过于频繁");
    zset.add(now, UUID.randomUUID().toString());
    zset.expire(Duration.ofMillis(window));

    return mapper.insert(...);
}
```

> 注: 上述非原子, 多步操作并发下有误差。 严格要求用 Lua 包装。

## Q5 (综合题): Redis 在生产中 CPU 突然 100%, 你的排查思路

### 答案

**第一步: 确认现象**

```bash
redis-cli INFO cpu
redis-cli INFO commandstats          # 看哪类命令占用多
redis-cli --latency-history          # 实时延迟
redis-cli SLOWLOG GET 20             # 慢命令
top -p $(pgrep redis)                # 进程 CPU
```

**第二步: 按可能性排序排查**

#### 1. 大 key 操作

```bash
redis-cli --bigkeys                  # 扫描全库找大 key
redis-cli MEMORY USAGE <key>         # 单 key 内存
```

如有 list/zset/hash 元素 > 1w, DEL / KEYS 操作就会卡。

**处理**:
- `UNLINK` 替代 `DEL` (异步释放)
- 拆分大 key (按业务维度分桶, 如 `user:1:posts:page1`)

#### 2. KEYS / FLUSHALL 阻塞

```bash
redis-cli SLOWLOG GET
# 看是否有 KEYS / FLUSHALL / HGETALL big_hash
```

**处理**: 改用 SCAN / HSCAN

#### 3. 长 Lua

```bash
redis-cli SLOWLOG GET 10
# 看是否有 EVAL / EVALSHA 在慢命令
```

**处理**: 拆短 Lua, 避免循环遍历大集合

#### 4. 持久化 fork 阻塞

```bash
redis-cli INFO persistence
# rdb_last_bgsave_status / rdb_last_bgsave_time_sec
# aof_last_rewrite_time_sec
```

如果 fork 时间长 (> 1s), 主线程在 fork 期间被阻塞。

**处理**:
- 关掉 `save ...`, 改用纯 AOF
- AOF rewrite 时机改到低峰期: `auto-aof-rewrite-percentage`
- 用大内存机器时关闭 Linux Transparent Huge Pages

#### 5. 网络饱和

```bash
sar -n DEV 1                         # 网卡流量
redis-cli INFO clients               # 当前连接数
redis-cli INFO stats                 # total_commands_processed
```

QPS 突增, 网卡打满 → IO 等待 → CPU 上升。

**处理**:
- Pipeline 合并请求减少 RTT
- 客户端加本地缓存
- 加 slave 分流读

#### 6. 热 key

```bash
redis-cli --hotkeys                  # 6.x+ 支持
```

某个 key QPS 占总 QPS 50%+ → 单分片打满。

**处理**:
- 客户端 Caffeine 本地缓存
- 热 key 副本 (10 个 key 存同样内容, 客户端随机读)
- 业务拆分

#### 7. 客户端连接风暴

```bash
redis-cli CLIENT LIST | wc -l
```

突然 1w+ 连接 → 客户端连接池配错 / 没复用。

**处理**:
- 检查 Lettuce/Jedis 池配置
- `maxclients` 调高
- 限流入口

### 应急处置

如果生产已经在着火:
1. **加 slave 分流读**, 降主节点压力
2. **临时禁用昂贵接口** (如 /search), 走限流降级
3. **重启高 QPS 的客户端实例** 让连接重置
4. **如果是大 key 操作**, 杀掉对应客户端: `CLIENT KILL ID xxx`

### 复盘要点

- `INFO commandstats` 看哪类命令突增了
- `MONITOR` 抓 1 秒命令样本, 不要长开 (太重)
- 引入 Redis Exporter + Prometheus, 历史 QPS / 内存 / fork 时间长期可观测
- 加上 SLOWLOG 阈值告警 (slowlog-log-slower-than 5000)
