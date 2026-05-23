# Chapter 47 Redis 进阶 - 理论篇

## 一、学习定位

46 章已经把 Cache-Aside 和五种数据结构用起来。 本章深入到 Redis "面试必问" 区:

- 内存淘汰策略 (LRU / LFU / TTL / random / no-eviction)
- 持久化 (RDB / AOF / 混合)
- 高可用 (主从复制 / 哨兵 / Cluster 分片)
- 事务 + Lua 原子性
- 分布式锁的"正确实现" (Redlock 之争)
- 缓存一致性深入 (双删 / Canal / 版本号)
- 大 key / 热 key 问题

- 优先级: L3 高频, 是中级后端的分水岭
- 预计投入: 4 小时
- 阶段产出: 实现缓存保护方案 + 分布式锁 + 高可用配置

## 二、核心概念

### 1. 内存淘汰策略 (maxmemory-policy)

Redis 内存满了, 写新数据时怎么腾地方:

| 策略 | 行为 | 适用 |
|---|---|---|
| **noeviction** | 拒绝写, 返回错误 | 不能丢数据 (作 KV 主存) |
| **allkeys-lru** | 所有 key 中淘汰最少使用的 | 纯缓存, 默认推荐 |
| **allkeys-lfu** | 所有 key 中淘汰访问频率最低的 | 长尾访问明显的场景 |
| **volatile-lru** | 仅在带 TTL 的 key 中 LRU 淘汰 | 有些 key 必须永久保留 |
| **volatile-ttl** | 优先淘汰 TTL 最短的 | 大量短时缓存 |
| **allkeys-random** | 随机淘汰 | 极少用, 测试 |

**生产推荐**: 纯缓存用 `allkeys-lfu`, 混合 (缓存 + 必留数据) 用 `volatile-lru` + 必留数据不设 TTL。

### 2. 持久化: RDB vs AOF vs 混合

| 项 | RDB | AOF | 混合 (4.0+) |
|---|---|---|---|
| 存什么 | 内存快照 (二进制) | 每条写命令追加 | RDB 全量 + AOF 增量 |
| 恢复速度 | 快 (直接加载二进制) | 慢 (重放命令) | 快 |
| 数据丢失 | 可能丢分钟级 | 1 秒 / 不丢 / 不可控 | 几秒 |
| 文件大小 | 小 | 大 (rewrite 前) | 中 |
| CPU 开销 | fork 瞬时高 | fsync 持续 | 综合 |

**生产推荐**: `appendonly yes` + `aof-use-rdb-preamble yes`, 用混合持久化。`appendfsync everysec` (每秒 fsync, 最多丢 1 秒)。

### 3. 高可用拓扑

**单机**: 学习 / 内部工具用

**主从 + 哨兵**:
- 一主多从, 读从写主, 故障时哨兵自动选主
- 客户端连哨兵, 哨兵告知当前主节点
- 适用: 写吞吐能由单主扛住

**Cluster (分片)**:
- 16384 个 slot, 按 CRC16(key) 路由
- 横向扩展, 写也分散
- 跨 slot 操作受限 (multi-key 命令必须同 slot, 用 hash tag `{user:1}:posts`)
- 适用: 数据量 / 写 QPS 超过单机

### 4. Lua 脚本 (原子操作)

```lua
-- 限流脚本 (滑动窗口)
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
local count = redis.call('ZCARD', KEYS[1])
if count >= tonumber(ARGV[3]) then return 0 end
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1])
return 1
```

**为什么用 Lua?**
- Redis 单线程, Lua 脚本在一个 EVAL 内执行, **天然原子**
- 替代 MULTI/EXEC, 还能写条件分支
- 减少 RTT (一次 EVAL vs 多次 GET/SET)

**注意**: 长 Lua 阻塞主线程, 控制在 < 5ms。

### 5. 分布式锁的正确姿势

**最低标准**:

```
SET key uniqueToken NX PX ttl
```

- `NX`: 不存在才设
- `PX ttl`: 自动过期, 防死锁
- `uniqueToken`: 释放时校验所有者, 防误删 (用 Lua 原子做)

**Redlock (Redis 作者 antirez 提出)**:
- N (奇数) 个独立 Redis 节点, 客户端依次申请锁, 多数节点成功则视为获取
- 解决单节点宕机时锁丢失
- Martin Kleppmann 批评 Redlock 在 GC 暂停 / 时钟跳变下仍不可靠

**结论**:
- 单节点 SET NX PX 在 99% 场景够用
- 强一致需求 (转账) 别用 Redis 锁, 用 DB 行锁 / 乐观锁
- 用 Redisson 库实现, 自带看门狗 (watchdog) 续期

### 6. 缓存一致性 (深入)

| 策略 | 行为 | 一致性窗口 |
|---|---|---|
| **失效**: 更新 DB → 删缓存 | 简单, 推荐 | 秒级 |
| **更新**: 更新 DB → 更新缓存 | 并发下旧值覆盖新值 | 不推荐 |
| **延迟双删** | 删 → 更新 DB → 延迟再删 | 秒内 |
| **Canal 监听 binlog** | DB 变更 → 自动失效缓存 | 毫秒 |
| **版本号** | value 带 version, CAS 写入 | 强 |

### 7. 大 key / 热 key

**大 key 危害**:
- 单 key > 10KB (string) / > 5000 元素 (list/hash/set/zset)
- 阻塞主线程 (DEL / 过期)
- 网络 IO 集中
- 集群下倾斜分片

**检测**: `redis-cli --bigkeys` 或 `MEMORY USAGE key`

**处理**: 拆分 (按业务维度分桶) / 用 SCAN + HDEL 分批删 / `UNLINK` 异步删

**热 key 危害**: 单个 key QPS 极高 (热搜词), 单分片打满

**处理**: 多副本 (本地缓存 + Redis) / 客户端分流 (多个 redis 节点冗余 + 随机选)

### 8. 性能与运维

| 命令 | 用途 |
|---|---|
| `INFO` | 各项指标 (memory / clients / stats) |
| `CLIENT LIST` | 当前连接 |
| `SLOWLOG GET` | 慢命令 (> 10ms 默认) |
| `LATENCY DOCTOR` | 延迟诊断 |
| `MEMORY STATS` | 内存统计 |
| `DEBUG SLEEP n` | 模拟卡顿 (测试用) |

## 三、工作原理

| 维度 | 要点 | 你需要能说清 |
|---|---|---|
| 入口 | RedisTemplate / Lettuce / Redisson | 单连接 / 连接池 / Pipeline 区别 |
| 配置 | maxmemory / appendfsync / cluster-node-timeout | 各配置默认值 + 修改影响 |
| 执行 | 单线程 IO 多路复用 (6.x+ IO 多线程) | 为什么单线程能扛 10w QPS, 多线程加在哪一层 |
| 边界 | 阻塞主线程 / 集群跨 slot / 持久化 fork | 大 key / KEYS / 长 Lua / fork 阻塞何时触发 |
| 验证 | redis-benchmark / SLOWLOG / INFO | 看 ops/s、内存碎片率、AOF 重写时机 |

## 四、项目落地清单

接入到博客 API 后:

1. **maxmemory** 设定 + **allkeys-lfu** 配置, `application.yml` 不要写, 改 redis.conf
2. **持久化**: `appendonly yes` + `aof-use-rdb-preamble yes`
3. **大 key 监控**: `redis-cli --bigkeys` 每周跑, 超过阈值告警
4. **热 key 缓解**: PostCache 引入 Caffeine 本地一层
5. **分布式锁**: 用 Redisson (生产) 或 SET NX + Lua 释放 (轻量)
6. **限流脚本**: ZSET 滑动窗口 (见 ch45)
7. **故障切换**: 改 `spring.data.redis.sentinel.master` + nodes, 或用 Cluster

## 五、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| `KEYS *` 在生产 | 阻塞主线程几秒 | 用 SCAN |
| `DEL bigkey` 阻塞 | 主线程卡顿, 集群超时 | `UNLINK` 异步删 |
| 过期 key 集中 | 主线程瞬时清扫开销大 | TTL 加抖动 |
| AOF rewrite 时机不当 | 业务高峰 fork 卡顿 | rewrite 配置避开高峰 |
| 客户端不用连接池 | 频繁建连耗时 | Lettuce / Jedis 用连接池 |
| 用 Redis 做强一致 KV | 主从切换丢数据 | DB 才是真理, Redis 只做缓存 |
| Cluster 用 multi-key | CROSSSLOT 错误 | 同 hash tag `{user:1}:posts` |
| 锁 TTL 太短 + 业务慢 | 锁过期后其他线程拿到, 并发执行 | Redisson watchdog 续期 |

## 六、面试高频问题

1. Redis 内存淘汰策略有几种, 你会怎么选?
2. RDB vs AOF, 各自的恢复时间和数据丢失上限是多少?
3. Redis 单线程为什么这么快, 6.x 的多线程加在哪一层?
4. SET key val NX PX 1000 这条命令, 单独看够不够实现分布式锁? 哪里不够?
5. 缓存一致性, 你会用哪种策略, 为什么不用更新而是删除?
6. 一个 key 突然 QPS 飙升 100 倍, 怎么处理?
7. 大 key 怎么检测怎么治理?
8. Redis Cluster 16384 个 slot, 为什么是 16384?
9. Redlock 你怎么看, 生产用不用?
10. Redis 内存碎片率怎么看, 怎么治理?

## 七、对比表

### 主从 / 哨兵 / Cluster

| 维度 | 单机 | 主从 | 哨兵 | Cluster |
|---|---|---|---|---|
| 高可用 | 无 | 手动切换 | 自动选主 | 自动 |
| 写吞吐 | 单机 | 单机 (主) | 单机 (主) | 多节点 |
| 数据量 | 单机内存 | 单机内存 | 单机内存 | 累加 |
| 客户端复杂度 | 低 | 中 | 中 (连哨兵) | 高 (smart client) |
| 一致性 | 强 | 异步复制弱 | 异步复制弱 | 异步复制弱 |

### Redisson vs Spring Data Redis vs Jedis

| 库 | 优势 | 劣势 |
|---|---|---|
| **Jedis** | 简单 | 阻塞 IO, 多线程要加锁 |
| **Lettuce** (Spring Boot 默认) | 异步 / Netty / 线程安全 | 高级特性少 |
| **Redisson** | 分布式锁 / 队列 / 信号量 / RBucket | 包大、学习曲线 |
