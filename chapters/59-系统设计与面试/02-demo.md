# Chapter 59 系统设计 - 实操 Demo

## Demo 目标

3 道高频系统设计题，按「需求 → 容量估算 → 接口 → 存储 → 关键算法 → 扩展」六段式写完。

## 通用方法论（4S）

| 步骤 | 关注点 |
|---|---|
| **Scenario** | 用户量 / QPS / 数据量 / 读写比 / 延迟要求 |
| **Service** | 拆服务 / 模块 / 接口 |
| **Storage** | 选 DB（MySQL/MongoDB/ES/Redis）、表结构、索引、分库分表 |
| **Scale** | 缓存 / 队列 / 限流 / 降级 / 一致性 |

---

## 题 1：分布式限流系统

### Scenario
- 全站 10w QPS，需要按 接口 + 用户 / IP 维度限流。
- 多副本部署，必须分布式一致。
- 突发流量允许小幅放行（令牌桶 vs 漏桶）。

### Service
- 限流网关（Spring Cloud Gateway / Nginx + Lua / 应用过滤器）。
- 配置中心动态调整规则。

### 三种算法对比

| 算法 | 特点 | 适合 |
|---|---|---|
| 固定窗口 | 实现最简，临界双倍流量问题 | 内部低频接口 |
| 滑动窗口（log） | 平滑，存所有请求时间戳，内存大 | 日志风控 |
| **令牌桶** | 允许突发，匀速补充 | API 限流（多数） |
| 漏桶 | 严格匀速，请求排队 | 强匀速场景（账单生成） |

### 关键算法：Redis Lua 令牌桶（原子）

```lua
-- KEYS[1] = bucket key
-- ARGV[1] = rate (tokens/sec), ARGV[2] = capacity
-- ARGV[3] = now (sec, float), ARGV[4] = requested
local key = KEYS[1]
local rate, cap, now, req = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
local d = redis.call('HMGET', key, 't', 'ts')
local tokens = tonumber(d[1]) or cap
local ts     = tonumber(d[2]) or now
tokens = math.min(cap, tokens + (now - ts) * rate)
local ok = 0
if tokens >= req then tokens = tokens - req; ok = 1 end
redis.call('HMSET', key, 't', tokens, 'ts', now)
redis.call('EXPIRE', key, math.ceil(cap / rate) * 2)
return ok
```

### 接口与存储
- 限流配置：`rate_limit_rule(api, dim, rate, capacity)` 存 MySQL，启动加载到本地缓存 + Redis pub/sub 动态刷新。
- 单 key Redis QPS 极限 ~ 10w；不够时 hash 分片 `key:userId mod 16`。

### Scale
- 本地令牌桶（基于 Bucket4j）做一层「降级」：Redis 宕机自动退化到节点内限流，避免全站雪崩。
- 热点用户 → 主动隔离队列 / 拒绝。

---

## 题 2：短链服务

### Scenario
- 长 URL → 6-7 位短码（base62）。
- 写：1w/s，读：10w/s（短链一般读多写少，1:100）。
- 短码不可猜测（防爬遍历），可自定义。
- 30 天过期 + 长期保留两种策略。

### 短码生成 3 方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| **发号器 + base62** | 顺序、易扩、可保证不重复 | 序号可被逆推 |
| MD5(url) 截断 6 位 | 简单 | 碰撞需重试，长 URL 同短码 |
| 雪花 ID 转 base62 | 分布式无中心 | 短码偏长（11 位） |

推荐：**发号器（Redis INCR 或号段服务）+ base62**，对 ID 做一次反转 / 异或，提升不可预测性。

```java
private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
public String encode(long id) {
    StringBuilder sb = new StringBuilder();
    while (id > 0) {
        sb.append(CHARS.charAt((int)(id % 62)));
        id /= 62;
    }
    return sb.reverse().toString();
}

public long nextId() {
    return redis.opsForValue().increment("short:seq", 1);    // 高 QPS 用号段法
}

public String shorten(String longUrl, Long uid) {
    long id = nextId();
    String code = encode(id ^ 0x5A5A5A5AL);                  // 简单混淆
    mapper.insert(new ShortLink(id, code, longUrl, uid, now()));
    redis.opsForValue().set("s:" + code, longUrl, Duration.ofDays(30));
    return "https://s.xx/" + code;
}
```

### 存储
- MySQL：`short_link(id PK, code UNIQUE, long_url, owner_id, created_at, expire_at)`
- Redis 热缓存：`s:{code} -> longUrl`，30d TTL。
- 冷数据：归档表。

### 跳转 path
```
GET /:code
  → Redis 命中 → 302 + 异步打点
  → miss → DB 查 → 写回 Redis（防穿透：不存在写 NULL 哨兵）
```

### Scale
- 防刷：同 IP 限流；点击数走 Kafka 异步聚合到大数据，不阻塞跳转。
- CDN 边缘缓存：但短链通常太分散，命中率低；可对头部短链做边缘 KV。
- 自定义短码：先 `SETNX code = id`，冲突拒绝。

---

## 题 3：登录系统（细节版）

### Scenario
- 用户 1000w，DAU 100w，登录峰值 5k QPS。
- 多端：Web / App / 小程序。
- 安全：防撞库、防穷举、防 token 泄露。

### 存储
```sql
user(id, username, email, phone, password_hash, status, created_at)
login_log(id, uid, ip, ua, ok, ts)             -- 风控用
refresh_token(jti PK, uid, expire_at, revoked)  -- 也可纯 Redis
```

### 登录 flow

```
POST /api/auth/login {username, password, captcha?}
  ├─ 校验图形/滑块验证码（连续失败 3 次后强制）
  ├─ 限流（IP + username 双维度，5次/分钟）
  ├─ 取 user → BCrypt.matches(pwd, hash)
  ├─ 失败 → login_log + 计数；连续 N 次 → 锁定 30 分钟
  ├─ 成功 → 签 Access(15m) + Refresh(7d)，存 jti 到 Redis
  └─ 返回 {accessToken, refreshToken, expiresIn}
```

### 关键决策

| 问题 | 选择 | 理由 |
|---|---|---|
| 密码哈希 | **BCrypt cost=12** | 慢哈希，cost 可随硬件升级 |
| Token 类型 | Access(JWT, 15m) + Refresh(opaque, 7d) | Access 无状态扩展；Refresh 可主动作废 |
| 多端登录 | 每个端独立 Refresh，互不影响 | 一端登出不影响别处 |
| 主动登出 | Access 加 Redis 黑名单（TTL = remaining） | JWT 无法撤销，黑名单弥补 |
| 修改密码 | 把该 uid 之前的所有 jti 加黑 | 安全合规要求 |

### 撞库防护
- 验证码 / 滑块；失败 → 锁定；登录异常地区 → 二次验证；密码字典检测。
- 接口下放 IP 维度令牌桶（题 1）。

### Scale
- 用户表分库分表 by uid mod 16（千万级）。
- BCrypt 是 CPU 密集 → 登录服务独立扩，避免影响主业务。
- Refresh / 黑名单 Redis 集群分片。

---

## 验证（自测）

| 题 | 60 秒能复述？ | 能画时序图？ | 能讲清 3 个 trade-off？ |
|---|---|---|---|
| 限流 | ⬜ | ⬜ | 算法选型 / 单机vs分布式 / 降级 |
| 短链 | ⬜ | ⬜ | 发号器vs哈希 / 缓存策略 / 自定义码 |
| 登录 | ⬜ | ⬜ | Token方案 / 主动登出 / 撞库防护 |

## 提交

```bash
git commit -m "chapter 59: system design - rate limit / short url / login"
```
