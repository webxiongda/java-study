# Chapter 53 并发安全 - 自测与验收

> 严格按照 `docs/superpowers/specs/2026-05-25-check-template.md` 模板。
> 覆盖率自检:`node scripts/check-coverage.mjs '^53-'`

---

### Q1 [L2·对比·章节内测] synchronized vs ReentrantLock 详细对比

**考点**: synchronized, ReentrantLock, AQS
**参考答案**:

| 维度 | synchronized | ReentrantLock |
|---|---|---|
| 实现 | JVM 内置, monitorenter/exit 字节码 | JUC, 基于 AQS |
| 释放 | 自动 (块结束或异常) | 手动 `unlock()`, 必须 finally |
| 公平性 | 仅非公平 | 可选 `new ReentrantLock(true)` |
| 可中断 | 不可 (BLOCKED 不响应中断) | `lockInterruptibly()` |
| 超时 | 不可 | `tryLock(t, unit)` |
| 条件 | wait/notify 单一 | 多 `Condition`, 精准唤醒 |
| 锁信息 | 看不到 | `isHeldByCurrentThread()` 等 API |
| 性能 | JDK 6+ 偏向/轻量级优化, 与 RLock 接近 | 高竞争略优 |

**选型**:
- 默认 `synchronized` (代码简单, 自动释放, JIT 友好)
- 需要 **超时 / 中断 / 公平 / 多条件** → `ReentrantLock`
- 读多写少 → `ReentrantReadWriteLock` 或 `StampedLock`
- 分布式 → Redisson `RLock` (Ch47)

**JDK 21 提醒**: 虚拟线程内的 `synchronized` 会 **pin 载体线程** (JDK 24 改善), 高并发场景换 `ReentrantLock` 避免 pin。

**🔥追问**: AQS 的 state 字段在 ReentrantLock 中怎么用?为什么 Condition 必须配合 ReentrantLock?
**关联**: interview-bank.md#sync-vs-reentrantlock

---

### Q2 [L2·概念·章节内测] CAS 三大问题与解决

**考点**: CAS, ABA, LongAdder, AtomicStampedReference
**参考答案**:

**1. ABA**

值 A→B→A, CAS 无法感知中间变化。

```java
AtomicInteger v = new AtomicInteger(1);
v.compareAndSet(1, 2);
v.compareAndSet(2, 1);  // 改回 1
v.compareAndSet(1, 3);  // 成功, 但中间发生过变化
```

**解决**: `AtomicStampedReference` 加版本号。

```java
AtomicStampedReference<Integer> r = new AtomicStampedReference<>(1, 0);
r.compareAndSet(1, 3, 0, 1);   // 期望值 + 期望版本
```

**2. 自旋开销大**

高竞争下 CAS 失败重试, CPU 空转。

**解决**:
- `LongAdder` 分段 (Cell 数组), 多个线程各自累加, sum 时合并
- 锁分段 / 读写分离

**3. 只能保证单变量**

CAS 一次只能改一个 long/int/ref。

**解决**:
- 多变量打包到一个对象, 用 `AtomicReference<Pair>`
- 或者只能加锁

**LongAdder vs AtomicLong 选择**:
- 低竞争 (≤ 8 线程): AtomicLong 更省内存 (一个 long)
- 高竞争 (统计 PV / QPS): LongAdder 完爆 (实测可快 3-10 倍)
- 需要精确实时值 (库存): AtomicLong (LongAdder 的 sum 不是强一致快照)

**🔥追问**: LongAdder 的 sum() 为什么不是强一致的?Cell 数组怎么扩容?
**关联**: interview-bank.md#volatile-semantics (CAS + volatile 配合实现无锁)

---

### Q3 [L3·Debug·面试高频] 找出 UserCache + TraceContext 的并发问题

**考点**: HashMap 线程不安全, ThreadLocal 泄漏, 缓存设计
**参考答案**:

```java
public class UserCache {
    private static HashMap<Long, User> CACHE = new HashMap<>();

    public static User get(Long id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        User u = loadFromDb(id);
        CACHE.put(id, u);
        return u;
    }

    public static void clear() { CACHE.clear(); }
}

public class TraceContext {
    public static ThreadLocal<String> TRACE = new ThreadLocal<>();
    public static void set(String id) { TRACE.set(id); }
}
```

**问题清单**:

1. `HashMap` 多线程 `put` → JDK 7 链表成环死循环, JDK 8 不成环但仍丢数据 → 改 `ConcurrentHashMap`
2. `containsKey + get + put` 三步非原子, 仍可能多次回源 → 改 `computeIfAbsent`
3. `CACHE` 是 `public static`, 任意代码可清空 → 私有 + 提供 API
4. 缓存无上限, 容易 OOM → 用 Caffeine
5. `clear()` 没并发保护 (虽然 ConcurrentHashMap 的 clear 是安全的, 但语义上其他线程的迭代可能见到中间态)
6. `TRACE` ThreadLocal 暴露为 public → 任意代码可篡改
7. `set` 后没人 `remove` → 线程池下串号 + 内存泄漏
8. 没有 traceId 出栈方法

**修复**:

```java
public class UserCache {
    private static final Cache<Long, User> CACHE = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    public static User get(Long id) {
        return CACHE.get(id, UserCache::loadFromDb);
    }
}

public class TraceContext {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    public static void set(String id) { TRACE.set(id); }
    public static String get() { return TRACE.get(); }
    public static void clear() { TRACE.remove(); }

    public static <T> T with(String id, Supplier<T> action) {
        set(id);
        try { return action.get(); }
        finally { clear(); }
    }
}
```

**🔥追问**: ThreadLocalMap 的 Entry 用的是弱引用 key + 强引用 value, 为什么这样设计?为什么仍可能泄漏?
**关联**: interview-bank.md#hashmap-thread-unsafe, interview-bank.md#threadlocal-leak

---

### Q4 [L2·代码编写·章节内测] 用 ReentrantLock + Condition 实现有界缓冲区

**考点**: ReentrantLock, Condition, 虚假唤醒, signal vs signalAll
**参考答案**:

```java
public class BoundedBuffer<T> {
    private final Object[] items;
    private int head, tail, count;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBuffer(int cap) { items = new Object[cap]; }

    public void put(T x) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == items.length) notFull.await();
            items[tail] = x;
            if (++tail == items.length) tail = 0;
            count++;
            notEmpty.signal();
        } finally { lock.unlock(); }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) notEmpty.await();
            T x = (T) items[head];
            items[head] = null;
            if (++head == items.length) head = 0;
            count--;
            notFull.signal();
            return x;
        } finally { lock.unlock(); }
    }
}
```

**关键点**:
- 两个 Condition 分别管 "非空" 和 "非满", 避免 `notifyAll` 的惊群
- `while` 不是 `if` (防虚假唤醒)
- `lockInterruptibly` 而不是 `lock`, 支持中断
- `signal` 而不是 `signalAll` (只唤醒一个等待者就够)
- finally 保证 unlock
- 实际生产用 `ArrayBlockingQueue`, 不要造轮子

**🔥追问**: 为什么必须 while 不能 if?Condition 的 signal 怎么知道唤醒哪个线程?

---

### Q5 [L3·场景设计·面试高频] 秒杀系统怎么保证库存不超卖?

**考点**: AtomicInteger, 分布式锁, Redis Lua, MQ 削峰
**参考答案**:

**问题描述**: 商品库存 10 件, 1000 人同时下单, 不能超卖 (卖出去 11 件)。

**单机内方案 (库存在内存里)**:

```java
AtomicInteger stock = new AtomicInteger(10);

public boolean tryDeduct() {
    while (true) {
        int cur = stock.get();
        if (cur <= 0) return false;
        if (stock.compareAndSet(cur, cur - 1)) return true;
    }
}
```

CAS 自旋, 0 等待。 比 synchronized 快, 但只能单机。

**分布式 (库存在 DB)**:

方案 A: 数据库行锁 (悲观)
```sql
UPDATE goods SET stock = stock - 1 WHERE id = ? AND stock > 0;
-- 返回 affected_rows = 1 表示扣成功; = 0 表示库存不足
```
依赖 DB 行锁 + WHERE 条件原子性。 简单, 性能瓶颈在 DB。

方案 B: 乐观锁 (version)
```sql
UPDATE goods SET stock = stock - 1, version = version + 1
WHERE id = ? AND version = ?;
```
失败重试。 DB 压力小, 但高竞争重试多。

方案 C: Redis 原子扣减 (推荐)
```lua
-- DECR.lua
local s = redis.call('GET', KEYS[1])
if not s or tonumber(s) <= 0 then return -1 end
return redis.call('DECR', KEYS[1])
```
- 启动时把库存预热到 Redis
- 接口走 Redis 扣减 (Lua 原子)
- 异步落 DB (削峰)
- DB 用乐观锁兜底

方案 D: 分布式锁 (Redisson RLock)
```java
distLock.withLock("seckill:" + goodsId, ..., () -> {
    int stock = mapper.selectStock(goodsId);
    if (stock <= 0) throw new BizException("库存不足");
    mapper.deduct(goodsId);
});
```
能保证正确但性能低 (锁串行)。

**生产组合方案**:

```
前置限流 (网关 / 漏桶)
     ↓
Redis Lua 原子扣库存 (95% 流量挡掉)
     ↓
MQ 异步下单 (削峰, 削到 DB 写入承受范围)
     ↓
DB 乐观锁兜底 (双保险)
```

**面试 2 分钟讲法**:
> "单机用 AtomicInteger 的 CAS, 不需要锁。 分布式必须落到统一存储: DB 行锁 (UPDATE WHERE stock > 0) 简单但慢; Redis Lua 扣减最优, 配合 MQ 异步落库削峰。 我会做 4 层防超卖: 网关限流挡 95%, Redis Lua 原子扣 (库存预热), MQ 削峰, DB 乐观锁兜底。 库存数据回收用定时对账。"

**🔥追问**: Redis Lua 扣减成功但 DB 异步落库失败, 怎么对账?MQ 重复消费怎么幂等?
**关联**: interview-bank.md#distributed-lock-redisson, interview-bank.md#design-rate-limiter

---

## 通过标准

- [ ] 能默写 synchronized 与 ReentrantLock 8 维度对比
- [ ] 能讲清 CAS 三大问题 + 各自解决方案
- [ ] 能识别上面 UserCache + TraceContext 至少 6 个问题
- [ ] 能现场实现 BoundedBuffer (ReentrantLock + 2 Condition)
- [ ] 能讲秒杀防超卖 4 层方案
