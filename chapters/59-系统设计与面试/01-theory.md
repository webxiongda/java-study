# Chapter 59 系统设计与面试 - 理论篇

## 一、学习定位

系统设计题是 P6+ 面试核心环节。 不考你会用什么框架, 考你 **能不能在 45 分钟内把一个不确定问题拆解 → 估算 → 设计 → 评估**。

- 优先级: L1 (高级岗必考)
- 投入: 6 小时
- 产出: 5 道经典题完整设计稿

## 二、面试评分维度 (面试官视角)

| 维度 | 加分点 | 扣分点 |
|---|---|---|
| 需求澄清 | 先问 QPS / 数据量 / 一致性要求 | 一上来就画架构图 |
| 容量估算 | 给具体数字 (写 1w QPS / 数据 100G) | 拍脑袋 "差不多 100 万" |
| 高层架构 | 画清各模块职责 + 数据流 | 一个 box 包含所有 |
| 数据建模 | 表结构 + 索引 + 主从 / 分片 | 完全不提数据库 |
| 关键决策 | 解释 trade-off (CP vs AP, 强 vs 最终一致) | "用 Redis 就行了" |
| 失败处理 | 主从切换 / 限流降级 / 重试幂等 | 全程假设永不失败 |
| 进化能力 | "如果 QPS 10x 怎么改" 有答案 | "等出问题再说" |

## 三、通用方法论 RACE

```
R (Requirements)  - 5 分钟问需求 + 估算容量
A (Architecture)  - 10 分钟画高层架构
C (Components)    - 15 分钟设计核心模块 (DB / Cache / API)
E (Evolution)     - 10 分钟讨论瓶颈 + 扩展
```

### 1. Requirements 黄金 5 问

```
1. QPS / DAU 多少? 峰值 vs 平均? 读写比?
2. 数据量? 1 年增长?
3. 一致性要求? (强 / 最终)
4. 延迟要求? (P99 多少?)
5. 必做 vs 可做功能? (拍板范围)
```

### 2. 容量估算

```
DAU 1000w → 假设每人日均 10 个请求 → 总 1 亿 QPD
1 亿 / 86400 ≈ 1200 QPS 平均
峰值 = 平均 * 3-5 = 5000 QPS
读写比 100:1 → 写 50 QPS, 读 5000 QPS

存储:
每条记录 1KB * 1 亿/天 = 100GB/天 → 1 年 36TB
→ 分库分表 / 冷热分离

带宽:
5000 QPS * 10KB/请求 = 50MB/s = 400Mbps
→ CDN / 压缩
```

## 四、5 个经典题

### 1. 短链系统 (TinyURL)

**需求**: 长 URL → 6 字符短链, 重定向 302, 100 亿存量, 1w QPS 写, 10w QPS 读。

**架构**:
```
Client → CDN → API Gateway → ShortenService
                                ↓
                  ID Generator (Snowflake)
                                ↓
                  Base62(id) → 6 字符 (62^6 = 568 亿够用)
                                ↓
                  Write: MySQL (long_url, short_code)
                  Read: Redis (LFU 缓存, 命中率 99%+)
```

**关键决策**:
- **生成策略**: ID 生成 + base62 编码 (不用 hash, 因为冲突难处理)
- **存储**: 短码作为 primary key, 单 row 256B, 100 亿 = 2.5TB → 分库分表 (短码哈希)
- **缓存**: 热 URL 用 Redis, 冷的直接 DB; TTL 用 LFU 而非 LRU (短链有热点)
- **写少读多**: 主从, 读全走从库
- **去重**: 同一长 URL 同一用户返回同一短链 (查 long_url 反向索引)

**易错**:
- 生成短码不能 hash (碰撞难处理), 也不能纯自增 (可枚举猜测)
- 重定向用 302 (临时, 可改) 还是 301 (永久, 浏览器缓存)? → 302 利于统计
- 防爬虫: 限频率 + 验证码

### 2. 秒杀系统

**需求**: 1w 库存, 100w 人抢, 1 秒内, 不超卖, 不少卖。

**架构**:
```
Client (按钮置灰) → CDN (静态资源) → Nginx (限流)
   ↓
API Gateway (令牌桶限频)
   ↓
SeckillService
   ├─ 1. 黑名单校验 (Redis SET)
   ├─ 2. 是否参与过 (Redis SET dedupe)
   ├─ 3. 库存预扣 (Redis Lua 原子 DECR)
   ├─ 4. MQ 异步 (生成订单)
   └─ 5. 返回排队中, 客户端轮询
                ↓
        OrderService (消费 MQ)
                ↓
        MySQL (库存最终 UPDATE WHERE stock > 0)
```

**关键决策**:
- **削峰**: MQ (Kafka / RocketMQ) 把 100w 瞬时削成 1k QPS 入 DB
- **防超卖**: Redis Lua 原子扣减 (单线程) + DB 乐观锁 (`UPDATE ... WHERE stock > 0`)
- **防少卖**: MQ 至少一次 + DB 幂等键 (用户 ID + 商品 ID)
- **防黄牛**: 同一用户限 1 单 + IP 限频 + 风控
- **静态化**: 商品详情走 CDN, 不进应用
- **服务隔离**: 秒杀独立部署, 出问题不影响主站

**易错**:
- 库存直接 DB UPDATE → DB 扛不住 100w QPS
- Lua 脚本里 DECR 后忘记判断 < 0 (会变负)
- 没幂等 → MQ 重试导致多扣

### 3. 朋友圈 / Feed 流

**需求**: 1 亿用户, 平均 200 好友, 发 / 看朋友圈。

**架构 - 两种模式**:

**Push (推模式 / Fan-out on Write)**:
- 我发一条 → 写入所有 200 个好友的收件箱 (Redis SortedSet, score=时间)
- 好友打开 → 读自己的收件箱 (毫秒)
- **优**: 读快
- **缺**: 大 V (1000w 粉丝) 一条 → 写 1000w 次 (写放大)

**Pull (拉模式 / Fan-out on Read)**:
- 我发一条 → 只写自己的发件箱
- 好友打开 → 查所有好友的发件箱 → 归并
- **优**: 写省
- **缺**: 读慢

**实际**: **混合模式 (推拉结合)**
- 普通用户: Push (绝大多数)
- 大 V (粉丝 > 10w): Pull (只写自己, 粉丝来时拉)
- 在线粉丝优先 Push (体验), 离线 Pull (省资源)

**存储**:
- 收件箱: Redis ZSET, 限大小 (近 500 条)
- 历史: HBase / TiDB 按 (user_id, time) 分片

### 4. 排行榜 (实时)

**需求**: 100 万用户实时积分, 查 Top 100 + 我的排名。

**Redis SortedSet 是天然选择**:
```bash
ZADD leaderboard <score> <user_id>          # 写
ZREVRANGE leaderboard 0 99 WITHSCORES       # Top 100
ZREVRANK leaderboard <user_id>              # 我的排名
ZINCRBY leaderboard 1 <user_id>             # 加分
```

**100 万元素**:
- 内存: 100w * (16B key + 8B score) ≈ 24MB
- ZRANGE 100 个: O(log N + 100) ≈ 30us
- 单 Redis 实例足够

**分片** (超大规模):
- 按 score 区间分片 (0-1000 / 1000-10000 / ...)
- 查 Top 100 时归并各片头部

**持久化**:
- Redis AOF + 每日全量备份到 MySQL
- 重启时从 MySQL 重建

**易错**:
- 用 MySQL `ORDER BY score LIMIT` → 慢 (大表排序)
- 不做持久化 → Redis 挂数据丢

### 5. 登录系统 (单点 SSO + 多端)

**需求**: 1 亿用户, 多端登录 (Web/iOS/Android), 单点登出, 安全。

**架构**:
```
Client → API Gateway → AuthService
                          ↓
                   1. 校验账密 (BCrypt)
                   2. 生成 JWT (access 15min) + RefreshToken (7d)
                   3. RefreshToken 写 Redis (含 device_id)
                   4. 返回 access + refresh
                          ↓
                   下次请求带 access → Gateway 校验 JWT 签名 (无状态)
                          ↓
                   access 过期 → 用 refresh 换 → 校验 Redis 中是否还有
```

**关键决策**:
- **JWT vs Session**: JWT 无状态适合多端, Session 集中校验适合单端
- **双 token**: access 短 (15 分钟) + refresh 长 (7 天), 平衡安全与体验
- **登出**: JWT 无法主动失效 → refresh 进黑名单 + access 短期自然过期
- **多端互踢**: refresh token 关联 device_id, 同 device 新登录踢老
- **密码安全**: BCrypt (cost 10) + 慢登录 (失败 5 次锁 5 分钟)
- **2FA**: TOTP (Google Authenticator) / SMS

**易错**:
- access token 过长 (1 天) → 用户被盗后无法及时止损
- refresh token 也用 JWT (不能 revoke) → 改用 opaque + Redis
- 没有限流 → 撞库攻击
- BCrypt cost 太低 (5) → 不安全; 太高 (15) → 登录慢

## 五、面试技巧

### 1. 不要假装懂

```
面试官: 用 Cassandra 怎么样?
错: "可以可以" (然后开始胡说)
对: "我没用过 Cassandra 生产环境, 我的理解是它擅长高写入的宽列存储, 类似的话我会想到 HBase 或 ScyllaDB, 如果你想我深入讨论我们可以拿一个我用过的对比"
```

### 2. 主动提风险

```
"这个方案有个明显的问题: 大 V 写放大。 我会在二期用混合模式解决, 一期接受这个限制。"
```

主动提 → 你考虑过 = 高级。 等面试官问 → 你没想 = 中级。

### 3. 画图节省话

不要全程说话, 在白板 / 纸上画:
- 各服务 box
- 数据流箭头 (实线同步, 虚线异步)
- 数据库 / 缓存 / MQ 用不同形状

### 4. 经典 trade-off 表

| 选择 A | 选择 B | trade-off |
|---|---|---|
| SQL | NoSQL | 事务/Join vs 弹性/规模 |
| 强一致 | 最终一致 | 正确性 vs 可用性/性能 |
| Push | Pull | 写多读少 vs 读多写少 |
| 同步 | 异步 (MQ) | 简单/RT vs 削峰/解耦 |
| 单库 | 分库分表 | 简单 vs 规模 (复杂度大涨) |
| JWT | Session | 无状态/多端 vs 可撤销 |

## 六、自检清单

设计完一题问自己:
- [ ] QPS / 容量算清了吗?
- [ ] 数据库选型 + 表 + 索引 + 分片说了吗?
- [ ] 缓存什么 (key / value / TTL / 淘汰) 说了吗?
- [ ] 单点故障在哪? 怎么 HA?
- [ ] 一致性怎么保证? 用了哪种方案?
- [ ] 限流 / 降级 / 重试 / 幂等 提了吗?
- [ ] 监控 / 报警 / 灰度 提了吗?
- [ ] 一句话总结这个方案的 trade-off?

## 七、Demo / 任务

- Demo: 已有 demo 写短链 + 排行榜
- Task: 5 道系统设计题 (短链/秒杀/Feed/排行榜/登录) 各写完整设计稿
