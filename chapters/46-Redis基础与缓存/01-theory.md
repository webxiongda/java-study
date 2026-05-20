# Chapter 46 Redis 基础与缓存 - 理论

> 前置：[[45-Web安全防护]]
> 后续：[[47-Redis进阶]] → [[48-消息队列概念]]
> 优先级：L1 必须掌握  预计：4 小时

## 1. Redis 是什么

- 内存型 KV 数据库，单实例 QPS 10w+。
- 单线程网络模型（IO 多路复用 epoll），命令原子。 6.0 起多线程只用于网络读写。
- 主流用法：**缓存、分布式锁、排行榜、计数、队列、限流**。

## 2. 五种基础数据结构

| 类型 | 命令 | 项目场景 |
|---|---|---|
| String | `SET/GET/INCR/SETEX/SETNX` | 缓存、计数、分布式锁、token |
| Hash | `HSET/HGET/HGETALL/HINCRBY` | 对象（user:1 → {name,age}），节省内存 |
| List | `LPUSH/RPOP/LRANGE/BRPOP` | 队列、最新动态 |
| Set | `SADD/SISMEMBER/SINTER` | 去重、好友交集 |
| ZSet | `ZADD/ZRANGE/ZINCRBY` | 排行榜、延时队列、按分数分页 |

**进阶（47 章细讲）**：Bitmap（签到）/ HyperLogLog（UV 估算）/ Geo / Stream（消息队列）。

## 3. 过期策略与内存淘汰

**过期机制（两种叠加）**：

- **惰性删除**：读时检查 expire，过期才删。 省 CPU，但占内存。
- **定期删除**：每 100ms 抽样部分 db，删过期 key。 防止内存堆积。

**内存淘汰策略**（`maxmemory-policy`，超过 `maxmemory` 触发）：

| 策略 | 行为 |
|---|---|
| `noeviction` | 写报错（默认） |
| `allkeys-lru` ⭐ | 所有 key 中淘汰最近最少使用 |
| `allkeys-lfu` | 所有 key 中淘汰最少访问频率 |
| `volatile-lru` | 只在设了 expire 的 key 中 LRU |
| `volatile-ttl` | 只淘汰快过期的 |
| `allkeys-random` / `volatile-random` | 随机 |

> 缓存场景一律 `allkeys-lru` 或 `allkeys-lfu`，**别用默认** noeviction。

## 4. Cache-Aside（旁路缓存，最常用）

```
读：
  1. 查 Redis
  2. 命中 → 返回
  3. 未命中 → 查 DB → 写 Redis（设 TTL）→ 返回

写：
  1. 写 DB
  2. 删 Redis（不是更新！）
```

**为什么是「删」不是「更新」**：

- 写后立即更新可能写入旧值（A 读旧，B 写新 + 更新缓存，A 再用旧值覆盖缓存）。
- 删除让下次读重新从 DB 加载，简单可靠。
- 「懒加载」省下从未被读到的 key 的写缓存开销。

## 5. 缓存一致性

完美一致不可能（DB 和缓存是两个系统）。 业界默认「**最终一致**」，常见方案：

| 方案 | 一致性 | 复杂度 | 适用 |
|---|---|---|---|
| Cache-Aside（先写库后删缓存） | 弱 | ⭐ | 一般业务 |
| 双删（删 → 写库 → 延时再删） | 中 | ⭐⭐ | 容忍短窗 |
| 订阅 binlog（Canal）异步刷缓存 | 较强 | ⭐⭐⭐ | 大型系统 |
| 强一致（分布式事务） | 强 | ⭐⭐⭐⭐ | 极少用，性能差 |

**项目实操**：博客系统选 Cache-Aside + 短 TTL（5-30 min），允许短窗口不一致。

## 6. 三大缓存问题（必背，47 章给代码）

### 缓存穿透（查不存在的 key）

攻击者疯狂查不存在的 id，每次都打 DB。

**解法**：

- 缓存空值（`null` + 短 TTL 60s）。
- **布隆过滤器**（Bloom Filter）：DB 加载时所有合法 key 预放入，查前先过 BF。

### 缓存击穿（热点 key 过期瞬间）

某个超热 key（首页热门文章）TTL 到期，瞬间 1w 请求全部穿透到 DB。

**解法**：

- **互斥锁**：第一个进来的请求拿分布式锁回源，其他等待重试。
- **逻辑过期**：缓存永不真过期，value 内含 `expireAt`，过期后异步刷新，老数据照常返回。
- 热点 key 设置永不过期。

### 缓存雪崩（大量 key 同时过期）

启动时批量预热，所有缓存 TTL 一致 → 同一刻集体过期 → DB 瞬时压垮。

**解法**：

- TTL **加随机抖动**：`base + random(0, 5min)`。
- 多级缓存：本地（Caffeine）+ Redis。
- 服务降级 + 限流，扛住穿透流量。

## 7. 缓存命名规范

```
{业务前缀}:{实体}:{id}[:{子维度}]

article:detail:1001              # 文章详情
article:hot:list                 # 热门文章列表
user:profile:42
user:permissions:42              # 权限集
auth:blacklist:jwt:<jti>         # JWT 黑名单
rate:limit:login:<ip>            # 限流计数
lock:order:create:<orderId>      # 分布式锁
```

**约定**：

- 用 `:` 分层，便于 `keys article:*`（生产用 `scan`，不要 `keys`）查找。
- 见名知意：能从 key 反推业务。
- 别用中文 / 空格 / `?*`。

## 8. Spring Boot 集成（细节见 47 章）

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PWD:}
      lettuce:
        pool: { max-active: 8, max-idle: 8, min-idle: 0 }
      timeout: 2s
```

**两种用法**：

- 编程式：`RedisTemplate` / `StringRedisTemplate`（推荐 String 序列化，便于排查）。
- 声明式：`@Cacheable` / `@CacheEvict` / `@CachePut`（语法糖，复杂场景仍要手写）。

## 9. 项目场景对照

| 场景 | 结构 / 用法 |
|---|---|
| 文章详情缓存 | `article:detail:{id}` String，TTL 30min |
| 热门文章 Top10 | ZSet `article:hot`，分数=浏览数 |
| 点赞 / 浏览数 | `INCR article:views:{id}`，定时落库 |
| 登录限流 | `rate:limit:login:{ip}` String + EXPIRE 15min |
| 验证码 | `code:sms:{phone}` String + EXPIRE 5min |
| 在线用户 | Set `online:users` |
| 分布式锁 | `SET k v NX EX 30` 或 Redisson |
| JWT 黑名单 | `auth:blacklist:{jti}` String + TTL=剩余过期时间 |

## 10. 常见坑

- 用 `keys *` → 单线程阻塞，生产用 `SCAN`。
- 大 key：单 value > 10KB 或 List/Hash 元素 > 10w，影响其他命令。 拆分。
- 热 key：QPS 集中在单 key → 单实例打爆。 本地缓存兜底。
- 缓存与 DB 写顺序：先删缓存后写库，并发下可能读旧值回填缓存。 应**先写库后删缓存**。
- 序列化用 JDK 默认 → 类路径变更后反序列化失败。 用 JSON。
- TTL 不设 → 内存爆。 所有缓存 key **必须有 TTL**。
- 缓存空值不设 TTL → 异常数据永驻。

## 11. 面试高频

1. Redis 为什么快？单线程为什么不慢？
2. 五种数据结构 + 一个真实场景。
3. 过期策略 + 内存淘汰策略，怎么选？
4. Cache-Aside 为什么删缓存而不是更新？
5. 缓存穿透 / 击穿 / 雪崩 区别和方案？
6. 缓存与 DB 一致性怎么保证？
7. 大 key / 热 key 怎么发现和处理？
8. Redis 持久化 RDB / AOF 区别？（→ 47）

更多 → [[interview-bank|面试题库]] 缓存区。
