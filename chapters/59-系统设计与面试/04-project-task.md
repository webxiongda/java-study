# Chapter 59 系统设计与面试 - 项目任务

## 任务概述

写 **5 道经典系统设计题的完整设计文档**, 每题都要能在面试 45 分钟内讲完:

1. 短链系统 (TinyURL)
2. 秒杀系统
3. 朋友圈 / Feed 流
4. 实时排行榜
5. 登录系统 (SSO + 多端)

每题都要包含: 需求澄清 + 容量估算 + 架构图 + 数据模型 + 关键决策 + 失败处理 + 演进方向。

## 业务背景

P6+ 后端面试系统设计题占 30-50% 权重。 不准备 → 现场卡壳。 准备 5 道经典题足以应对 80% 的变体 (秒杀 ≈ 红包 / 抢购, Feed ≈ 微博 / 抖音推荐, 排行榜 ≈ 游戏战力榜)。

## 任务拆解

### Step 1: 模板准备 (15 分钟)

`design/_template.md`:

```markdown
# <题目> 系统设计

## 1. 需求澄清

### 功能性需求
- [ ] 必做 1
- [ ] 必做 2
- [ ] 可做 1 (二期)

### 非功能性需求
- 用户量 / DAU
- QPS (平均 / 峰值)
- 数据量 (现存 / 增长)
- 一致性 (强 / 最终)
- 延迟 (P99 < ?ms)
- 可用性 (99.9% / 99.99%)

## 2. 容量估算

- QPS:
- 存储:
- 带宽:
- 内存:

## 3. 高层架构

(画图: 客户端 → CDN → Gateway → Service → Cache / DB / MQ)

## 4. 数据模型

(表结构 / Redis Key / MQ Topic / 索引 / 分片)

## 5. API 设计

| 接口 | 参数 | 返回 |
|---|---|---|
| POST /xxx | | |

## 6. 关键决策

| 选择 | 选 A 还是 B | 原因 |
|---|---|---|
| SQL vs NoSQL | | |
| 强一致 vs 最终 | | |

## 7. 失败处理

- 主从切换:
- 限流降级:
- 重试幂等:

## 8. 演进方向

- 当前规模:
- 10x 怎么改:
- 100x 怎么改:

## 9. 一句话总结 (面试结尾)

"这个设计核心是 ___ 解决 ___ 问题, 用 ___ 削峰, ___ 保证一致, 主要 trade-off 是 ___"
```

### Step 2: 短链系统 (45 分钟)

`design/01-tinyurl.md`:

**重点**:
- 短码生成: Snowflake + Base62 (不用 hash)
- 100 亿存量 → 分库分表 (短码哈希 256 库)
- 读缓存: Redis LFU (热 URL 命中率 99%)
- 302 vs 301: 选 302 (利于统计)
- 防爬: 限流 + 验证码

### Step 3: 秒杀系统 (60 分钟)

`design/02-seckill.md`:

**重点**:
- 限流: Nginx + Gateway + 业务层 3 道
- 库存: Redis Lua 原子扣 + DB 乐观锁兜底
- 削峰: MQ (Kafka, 10 分区)
- 防黄牛: 用户限购 1 单 + IP 限频 + 风控
- 静态化: 商品详情 CDN, 不进应用
- 服务隔离: 秒杀独立部署, 出问题不影响主站

### Step 4: 朋友圈 / Feed (60 分钟)

`design/03-feed.md`:

**重点**:
- Push (写扩散) vs Pull (读扩散) vs **混合**
- 大 V 阈值 1w 粉丝
- 收件箱 Redis ZSET 限 500
- 历史存 TiDB / HBase 按 (user, time) 分片
- 在线粉丝 Push, 离线 Pull (省资源)

### Step 5: 排行榜 (30 分钟)

`design/04-leaderboard.md`:

**重点**:
- Redis SortedSet (ZSET) 是天然选择
- ZINCRBY 加分 / ZREVRANGE 取 Top / ZREVRANK 查我排名
- 多榜单按 key 区分: `leaderboard:2026-05-23`
- 持久化: AOF + 每日全量到 MySQL
- 100w 用户 = 24MB, 单实例足够
- 易错: 用 ZADD 覆盖 (不是 ZINCRBY) → 并发 bug

### Step 6: 登录系统 (45 分钟)

`design/05-login.md`:

**重点**:
- JWT (access 15min) + Refresh Token (7d, opaque, 进 Redis)
- 单点登出: 把 refresh 拉黑 + access 短期自然过期
- 多端互踢: refresh 关联 device_id
- BCrypt cost 10
- 防撞库: 失败 5 次锁 5 分钟 + IP 限频
- 2FA: TOTP (Google Authenticator)
- SSO: OAuth2 / OIDC

### Step 7: 模拟面试 + 录音 (60 分钟)

```
找一个搭子或自录:
1. 随机选一题
2. 限时 45 分钟讲完
3. 录音 + 回听
4. 自评:
   - 需求问够了吗?
   - 容量算了吗?
   - 架构画清了吗?
   - 关键决策讲了 trade-off 吗?
   - 失败 / 演进 有覆盖吗?
   - 卡壳次数?
```

## 交付物

- [ ] `design/_template.md` 通用模板
- [ ] `design/01-tinyurl.md` ~ `design/05-login.md` 五题完整设计稿
- [ ] 每题包含架构图 (markdown 文字图 or 截图)
- [ ] 每题包含容量估算具体数字
- [ ] 每题包含 trade-off 表格
- [ ] 至少 1 题完成模拟面试录音 + 自评笔记
- [ ] `INTERVIEW-PITCH.md` 5 题各 90 秒电梯讲法
- [ ] git commit: `ch59: 5 system design playbooks`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 设计稿完整 | 包含模板 9 节, 每节都有内容 |
| 容量有具体数字 | 不是"很多用户", 是"DAU 1000w QPS 5000" |
| 架构有图 | 能看出数据流方向 |
| trade-off 清晰 | 每个关键决策有 2 个备选 + 选择原因 |
| 失败覆盖 | 主从切换 / 限流 / 幂等 都提了 |
| 演进路径 | 10x / 100x 怎么改, 不是"再加机器" |
| 90 秒能讲完概要 | 每题在 INTERVIEW-PITCH.md 有 < 200 字总结 |

## 扩展挑战

1. **加 5 题**: 即时通讯 IM / 推荐系统 / 在线编辑器 (类似 Google Doc) / 网约车 / 视频流
2. **画真图**: 用 draw.io / Excalidraw 画架构, 不只是 ASCII
3. **代码原型**: 任选 1 题 (推荐短链或排行榜) 写实际可跑代码 (Spring Boot, 200 行)
4. **真模拟面试**: 上 Pramp / Interviewing.io, 跟陌生人对练
5. **行业案例**: 找 Twitter / 微信 / 抖音的公开技术分享 (架构演进 PPT), 对比自己的设计
6. **白板讲解视频**: 录一段自己讲短链或秒杀的视频, 5 分钟, 能听不能听
