# Chapter 59 系统设计与面试 - 自测与验收

## Q1 概念: 拿到系统设计题第一件事干什么?

**绝不是画架构图, 是问需求 + 容量估算**。 RACE 方法:

```
R (Requirements) - 5 分钟  → 问需求 + 估算
A (Architecture) - 10 分钟 → 画高层架构
C (Components)   - 15 分钟 → DB / Cache / API 细节
E (Evolution)    - 10 分钟 → 瓶颈 + 扩展
```

**Requirements 必问 5 件事**:

1. QPS / DAU 多少? 峰值 vs 平均? 读写比?
2. 数据量? 1 年增长?
3. 一致性要求? (强 / 最终)
4. 延迟要求? (P99 多少?)
5. 必做 vs 可做功能?

**容量估算示例**:

```
"日活 1000w, 假设每人日均 10 个请求, 总 1 亿 QPD
   1 亿 / 86400 ≈ 1200 QPS (平均), 峰值 5000 QPS
   读写 100:1 → 写 50 QPS, 读 5000 QPS
   每条 1KB, 1 亿条/天 = 100GB/天 → 年 36TB
   → 需要分库分表, 用 Redis 挡 90% 读"
```

**为什么必须先算**: 5 QPS 和 50w QPS 是完全不同的设计 — 前者一个 MySQL 搞定, 后者需要分库分表 + 缓存 + MQ + CDN。

---

## Q2 概念: 短链系统怎么生成短码? 为什么不用 hash?

**3 种方案对比**:

| 方案 | 原理 | 问题 |
|---|---|---|
| Hash | MD5(url) 取前 6 位 base62 | **碰撞难处理**: 100 亿数据 6 字符 base62 = 568 亿, 生日攻击下 1000w 时 50% 碰撞 |
| 自增 ID + base62 | DB auto_increment, encode 成 6 字符 | **可枚举**: 攻击者递增 ID 能爬全站短链 |
| 雪花 ID + base62 / 提前批量生成 | Snowflake 64bit, 取 36bit base62 | **推荐**: 不可猜, 分布式不冲突, 高性能 |

**推荐方案**: 发号器 (Snowflake / Leaf) → 取低 36 位 → base62 编码 → 6 字符。

```java
public String shorten(String longUrl, Long userId) {
    // 同用户同 URL 复用
    String existing = repo.findByLongUrlAndUserId(longUrl, userId);
    if (existing != null) return existing;

    long id = idGenerator.next();
    String code = Base62.encode(id & 0xFFFFFFFFFL);    // 取低 36 位
    repo.insert(new ShortLink(code, longUrl, userId));
    return code;
}
```

**重定向 302 vs 301**:
- 301 永久: 浏览器缓存, 不再请求服务器 → **统计不准**
- 302 临时: 每次都请求服务器 → 可统计点击量 → **短链选 302**

---

## Q3 设计题: 秒杀系统 (1w 库存 / 100w 抢 / 1 秒)

### 需求澄清
- 1w 库存, 100w 用户 1 秒涌入
- 不超卖 (绝对) / 不少卖 (尽量)
- 公平: 不允许黄牛刷
- 完成后 5 秒内出订单

### 容量
- 峰值 100w QPS (静态资源 + 接口)
- DB 写 1w 次 (库存数 = 库存)
- Redis: 100w QPS 读 + 1w QPS 写

### 架构

```
[1] 用户端
    - 按钮 0.5 秒后才可点 (防多点)
    - 加密 token (前端拿不到接口 URL 提前)

[2] CDN
    - 商品详情 / 图片 / JS / CSS 全部静态化

[3] Nginx
    - 限流: limit_req zone=seckill burst=5 (单 IP)
    - 黑名单 (IP / UA)

[4] API Gateway
    - 令牌桶限频 (单用户 1 req/s)
    - JWT 校验

[5] SeckillService (无状态, 横向扩 100 实例)
    - Step 1: 是否在活动时间? (本地缓存)
    - Step 2: 用户是否在黑名单? (Redis SET)
    - Step 3: 用户是否已参与? (Redis SETNX user:product → 1)
    - Step 4: Redis Lua 原子扣减库存:
              local stock = redis.call('GET', KEYS[1])
              if tonumber(stock) <= 0 then return -1 end
              redis.call('DECR', KEYS[1])
              return 1
    - Step 5: 库存 > 0 → 投 MQ → 返回 "排队中"
              库存 ≤ 0 → 返回 "已抢光"

[6] MQ (Kafka, 10 分区)
    - 削峰: 100w 瞬时 → 1k QPS 入 DB

[7] OrderService (消费 MQ)
    - 幂等键: user_id + product_id
    - UPDATE stock SET stock=stock-1 WHERE id=? AND stock>0
    - 插订单
    - 推消息通知用户

[8] DB (MySQL 主从)
    - 主写, 从读
    - 库存表用乐观锁兜底
```

### 关键点

| 风险 | 处理 |
|---|---|
| 超卖 | Redis Lua 原子扣 + DB `WHERE stock > 0` 兜底 |
| 少卖 | MQ 至少一次 + 幂等 (重复消息只成功一次) |
| 缓存击穿 | 库存预热到 Redis, 不查 DB |
| 黄牛 | IP 限频 + 用户限购 1 单 + 风控接入 |
| 误点 | 客户端 0.5s 防抖 + 后端 SETNX 重入校验 |
| 服务雪崩 | 秒杀服务独立部署 + 限流 + 熔断 + 降级 |

### Trade-off
- 选 AP (库存放 Redis, 主从异步): 牺牲一点点超卖风险换性能 (主备 1ms 内, 概率极低)
- 库存如果是高价值 (如机票), 用 CP (Redis Cluster 同步 + 双写校验)

---

## Q4 设计题: 朋友圈 Feed (1 亿用户 / 平均 200 好友)

### 模式选择

| 模式 | 写 | 读 | 适合 |
|---|---|---|---|
| Push | 一发 → 写所有粉丝收件箱 (n 次) | 读自己收件箱 (1 次) | 普通用户 (粉丝 < 1w) |
| Pull | 写自己发件箱 (1 次) | 读所有关注的发件箱归并 (n 次) | 大 V (粉丝 > 1w, 避免写放大) |
| **混合** (推荐) | 普通 Push, 大 V Pull | 普通读收件箱, 加上大 V 的发件箱 | 1 亿规模 |

### 架构 (混合)

```
发动作:
  user 发条朋友圈
    ↓
  Post 写入 MySQL (永久存档)
    ↓
  判断 user 是否大 V:
    - 否: 写所有 follower 的收件箱 (Redis ZSET, score=time, 限 500)
    - 是: 只写自己的发件箱 (Redis LIST)

读动作:
  user 拉朋友圈
    ↓
  读自己的收件箱 ZRANGE 0 19 (普通用户的内容)
    ↓
  并发拉所有关注的大 V 的发件箱 LRANGE 0 19
    ↓
  归并 + 按时间倒序 + 翻页
```

### 存储估算

```
收件箱: 1 亿用户 × 500 条 × (16B + 8B) = 1.2TB Redis
→ Redis Cluster 16 节点, 每节点 100GB

历史 Post: 1 亿用户 × 平均 100 条/年 × 1KB = 10TB
→ TiDB / HBase 分片
```

### 关键点

- **粉丝阈值**: 大 V 阈值取 1w (粉丝大于 1w 切 Pull)
- **新人冷启动**: 关注后异步拉历史 200 条到收件箱
- **删除**: 软删, 收件箱靠 TTL 自然过期
- **缓存内容**: 收件箱只存 post_id, 详情按需从 DB 拉 + Caffeine

### Trade-off

最终一致: 大 V 发布后, 普通粉丝看到延迟几秒 (拉发件箱); 接受这个延迟换写放大问题。

---

## Q5 设计题: 排行榜 (100 万用户实时积分)

### 数据结构选型

| 方案 | Top N | 我的排名 | 加分 | 适用 |
|---|---|---|---|---|
| MySQL ORDER BY | O(N log N) | O(log N) | O(log N) | 数据小 (< 1w) |
| Redis SortedSet | O(log N + M) | O(log N) | O(log N) | **首选** |
| 分桶预计算 | O(1) | O(1) | O(1) | 超大规模 (> 10 亿) |

### 实现

```java
@Service
public class LeaderboardService {
    private final String KEY = "leaderboard:daily";
    private final RedisTemplate<String, String> redis;

    public void addScore(Long userId, double delta) {
        redis.opsForZSet().incrementScore(KEY, userId.toString(), delta);
    }

    public List<Rank> top(int n) {
        var set = redis.opsForZSet()
            .reverseRangeWithScores(KEY, 0, n - 1);
        return set.stream()
            .map(t -> new Rank(Long.parseLong(t.getValue()), t.getScore()))
            .toList();
    }

    public Long myRank(Long userId) {
        Long rank = redis.opsForZSet().reverseRank(KEY, userId.toString());
        return rank == null ? -1 : rank + 1;    // Redis 0-based
    }
}
```

### 容量

```
100w 用户 × (16B key + 8B score) ≈ 24MB
→ 单 Redis 实例够 (4GB 内存可放 100 个排行榜)
```

### 进阶

- **多榜单**: 日 / 周 / 月 / 总榜分 ZSET, key 用日期: `leaderboard:2026-05-23`
- **分页**: ZREVRANGE 0 19 / 20 39 ...
- **附近的人**: ZRANGEBYSCORE 当前分数 ±100
- **持久化**: AOF + 每日 BGSAVE + 定时备份到 MySQL
- **超大规模 (10 亿)**: 按 score 区间分多片 Redis, 归并 Top N

### 易错

- 用 MySQL ORDER BY → 100w 排序慢, 用户数翻倍直接挂
- 不持久化 → Redis 挂数据全丢
- 加分用 ZADD 而不是 ZINCRBY → 覆盖了原分数 (并发问题)

---

## 通过标准

- [ ] 能背 RACE 4 步 + Requirements 5 问
- [ ] 能估算 (QPS / 存储 / 带宽)
- [ ] 能讲短链生成方案对比
- [ ] 能讲秒杀的完整链路 (含限流 / 削峰 / 幂等)
- [ ] 能讲 Feed 流 Push/Pull/混合 trade-off
- [ ] 能用 Redis ZSET 设计排行榜
- [ ] 主动提风险 + trade-off, 不假装懂
