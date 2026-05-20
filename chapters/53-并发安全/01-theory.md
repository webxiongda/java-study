# Chapter 53 并发安全 - 理论

> 前置：[[51-并发基础]] → [[52-线程池与异步]]
> 后续：[[54-JVM基础]] → [[55-里程碑：并发+JVM测验]]
> 优先级：L3 面试高频  预计：5 小时

## 1. 为什么需要这一章

51 章讲了线程和 JMM，52 章讲了池化。一旦多个线程访问共享数据，就出现「同时写丢一次」「读到中间态」之类的 bug。 这一章把面试里高频的「锁、原子、并发容器、ThreadLocal」一次说透。

## 2. synchronized 的 4 种用法 + 锁升级

```java
synchronized (this)        { ... }   // 锁当前实例
synchronized (Cls.class)   { ... }   // 锁 Class 对象（所有实例共享）
synchronized void m()      { ... }   // 等价 synchronized(this)
static synchronized void m(){ ... }  // 等价 synchronized(Cls.class)
```

**锁升级（JDK 6+，对象头 Mark Word）**：

```
无锁 → 偏向锁 → 轻量级锁（CAS 自旋） → 重量级锁（OS mutex）
```

- 偏向锁：单线程访问。 JDK 15 起默认禁用（`-XX:+UseBiasedLocking` 才开）。
- 轻量级锁：少量线程交替，CAS 抢 Mark Word。
- 重量级锁：竞争激烈，进入 ObjectMonitor，挂起线程。 上下文切换成本高。
- **不可降级**：升上去就回不来。

> 编译成字节码是 `monitorenter` / `monitorexit`；同步方法靠方法 flag `ACC_SYNCHRONIZED`。

## 3. ReentrantLock vs synchronized

| 维度 | synchronized | ReentrantLock |
|---|---|---|
| 语法 | 关键字，自动释放 | API，必须 `try{} finally { lock.unlock(); }` |
| 公平性 | 非公平 | 可选 `new ReentrantLock(true)` |
| 可中断 | 不可 | `lockInterruptibly()` |
| 超时 | 不可 | `tryLock(t, unit)` |
| 条件队列 | 单个（wait/notify） | 多个 `Condition`，可精准唤醒 |
| 性能 | JDK 6+ 已接近 | 高竞争略好 |

```java
ReentrantLock lock = new ReentrantLock();
lock.lock();
try { ... } finally { lock.unlock(); }   // 必须 finally
```

**选型**：能用 `synchronized` 就用，需要超时 / 中断 / 多条件再上 `ReentrantLock`。

## 4. AQS（AbstractQueuedSynchronizer）

JUC 大部分锁的底层：`ReentrantLock` / `Semaphore` / `CountDownLatch` / `ReentrantReadWriteLock`。

**3 个关键**：

- `state`（volatile int）：表示锁状态。 ReentrantLock 用它存重入次数。
- **CLH 变种 FIFO 队列**：抢锁失败的线程包装成 Node 入队，前驱 unpark 后继。
- 模板方法：子类重写 `tryAcquire` / `tryRelease`，AQS 管排队与唤醒。

```java
// ReentrantLock 非公平获取（简化）
final boolean nonfairTryAcquire(int acquires) {
    int c = getState();
    if (c == 0 && compareAndSetState(0, acquires)) {
        setExclusiveOwnerThread(current);
        return true;
    } else if (current == getExclusiveOwnerThread()) {
        setState(c + acquires);   // 重入
        return true;
    }
    return false;
}
```

> 面试到这层就够了。 真要展开是一节课。

## 5. CAS 与原子类

**CAS = Compare And Swap**：CPU 指令 `cmpxchg`，「期望值 = V 时把 V 改成 N」原子完成。

```java
AtomicInteger n = new AtomicInteger(0);
n.incrementAndGet();   // 内部 do { v = get(); } while (!compareAndSet(v, v+1));
```

**3 个问题**：

1. **ABA**：值 A→B→A，CAS 以为没变。 解决：`AtomicStampedReference` 加版本号。
2. **自旋开销**：高竞争下 CPU 空转。 解决：JDK 8+ `LongAdder`（分段累加，最后求和）替 `AtomicLong`。
3. **只能保证单变量**：多个变量得用锁或 `AtomicReference` 包对象。

| 类 | 场景 |
|---|---|
| `AtomicInteger` / `AtomicLong` | 单变量计数 |
| `LongAdder` / `LongAccumulator` | 高竞争计数（统计 PV、QPS） |
| `AtomicReference` | 引用 CAS（无锁栈 / 无锁队列） |
| `AtomicStampedReference` | 防 ABA |
| `AtomicIntegerFieldUpdater` | 给已有字段加原子能力，省空间 |

## 6. ConcurrentHashMap（JDK 1.7 vs 1.8）

| | JDK 1.7 | JDK 1.8 |
|---|---|---|
| 结构 | Segment 分段（默认 16 段） | `Node[] + 链表/红黑树` |
| 锁粒度 | 段级 ReentrantLock | 桶头 `synchronized` + CAS |
| 扩容 | 各段独立 | 多线程协助迁移（`transfer`） |
| 计数 | 段 size 累加 | `CounterCell` 类似 LongAdder |

**1.8 写入流程**（简化）：

```
1. 数组未初始化 → CAS 初始化
2. 目标桶为空 → CAS 放入新 Node
3. 检测到 MOVED(-1) → 协助扩容
4. 否则 synchronized(桶头) → 链表/树插入
5. 链长 ≥ 8 且数组 ≥ 64 → 树化
```

**常见坑**：

- `get` 无锁，靠 volatile 数组 + Node.val 保证可见性。
- `size()` 是估算，不要在判等中用。 用 `mappingCount()`（long）。
- key/value 不允许 null。 想区分「没这 key」用 `containsKey`。

## 7. CopyOnWriteArrayList / ArrayList 不安全

- `ArrayList` 多线程 `add` 会丢数据、抛 `ArrayIndexOutOfBoundsException`。
- 同步包装 `Collections.synchronizedList`：所有方法都 `synchronized(mutex)`，迭代要手动加锁。
- **COW**：写时复制（`Arrays.copyOf`），写慢、读无锁。 适合**读极多写极少**（监听器列表、配置缓存）。
- 不适合大数组、写频繁，否则 GC 压力大。

## 8. 死锁四要件 + 排查

四要件（缺一不可）：互斥 / 持有并等待 / 不可剥夺 / 循环等待。

**典型死锁**：

```java
Thread t1 = new Thread(() -> { synchronized(A){ sleep(100); synchronized(B){} } });
Thread t2 = new Thread(() -> { synchronized(B){ sleep(100); synchronized(A){} } });
```

**排查**：

```bash
jps                          # 找 pid
jstack <pid> | grep -A 20 deadlock
# 或用 Arthas：thread -b
```

**避免**：

- 锁排序：所有线程按同一顺序拿锁。
- `tryLock(timeout)` 超时退避。
- 减少锁粒度，能用并发容器 / 原子类就别加锁。

## 9. ThreadLocal

**原理**：每个 `Thread` 内部有 `ThreadLocalMap`，key 是 `ThreadLocal` 弱引用，value 是任意对象。 读写都只在自己线程，无需同步。

**典型场景**：

- 上下文传递：用户身份、traceId、租户。
- 线程不安全对象的隔离：`SimpleDateFormat`、`SecureRandom`。

```java
private static final ThreadLocal<String> TRACE = new ThreadLocal<>();
TRACE.set(traceId);
try { ... } finally { TRACE.remove(); }   // 必须 remove
```

**内存泄漏**：

```
ThreadLocalMap.Entry: WeakReference<ThreadLocal>  →  Object value(强引用)
```

- 线程池线程长生命周期。
- `ThreadLocal` 被 GC 后，Entry.key=null，但 value 仍被 `Thread → Map → Entry` 强引用。
- 用完不 `remove()` → value 永驻 → 内存泄漏 + 串号（下个请求拿到上次的数据）。

> **铁律**：线程池里用 ThreadLocal 一定 `try/finally remove()`。

**`InheritableThreadLocal`**：子线程能拿到父线程的值；但**线程池里失效**（线程是预先创建的）。 跨线程传递用阿里 `TransmittableThreadLocal` 或 `MdcTaskDecorator`。

## 10. volatile 复盘（细节见 51 章）

- ✅ 可见性 + 禁止重排
- ❌ 原子性
- 经典用法：**单例双检锁**

```java
public class Singleton {
    private static volatile Singleton INSTANCE;     // volatile 必须
    public static Singleton getInstance() {
        if (INSTANCE == null) {
            synchronized (Singleton.class) {
                if (INSTANCE == null) INSTANCE = new Singleton();
            }
        }
        return INSTANCE;
    }
}
```

> 不加 volatile 的隐患：`new Singleton()` 是「分配 + 初始化 + 赋引用」3 步，可能重排成「分配 + 赋引用 + 初始化」，另一线程读到未初始化对象。

## 11. 项目里的「并发安全」清单

| 场景 | 推荐方案 |
|---|---|
| 计数 / 限流计数 | `LongAdder` / `AtomicLong` |
| 本地缓存 | `ConcurrentHashMap` + `computeIfAbsent`（注意 1.8 锁桶不能在 lambda 里再写同 map） |
| 监听器 / 配置 / 字典 | `CopyOnWriteArrayList` |
| 跨线程上下文 | `MDC` / `TransmittableThreadLocal` + `MdcTaskDecorator` |
| 一次性事件 | `CountDownLatch` |
| 资源池 / 限并发 | `Semaphore` |
| 分布式锁 | Redisson `RLock`（详见 47 章）|
| 单例 | 静态内部类 / 枚举 / 双检 volatile |

## 12. 常见坑

- `HashMap` 多线程 `put` → JDK 7 链表成环死循环；JDK 8 不会成环但仍丢数据。 用 `ConcurrentHashMap`。
- `i++` 加 `volatile` 还是不安全，得 `AtomicInteger` 或加锁。
- `synchronized(new Object())` 每次新对象 = 没锁。
- `synchronized(Integer)` 装箱缓存（-128~127）外是新对象，看似锁住其实没锁。
- 锁住 String 字面量 → 整个 JVM 共享，竞争失控。
- ThreadLocal 不 `remove()`，线程池场景串号。
- 双检锁不写 volatile → 偶发 NPE。

## 13. 面试高频

1. synchronized 锁升级过程？为什么不可降级？
2. ReentrantLock 比 synchronized 多了什么能力？
3. AQS 怎么实现的？state 是什么？
4. CAS 三大问题，怎么解决？
5. ConcurrentHashMap 1.7 / 1.8 区别？
6. ThreadLocal 内存泄漏成因，线程池场景怎么避免？
7. 双检锁为什么必须 volatile？
8. 死锁排查工具，怎么避免？
9. LongAdder 比 AtomicLong 快在哪？
10. CopyOnWrite 适合什么场景，不适合什么场景？

更多 → [[interview-bank|面试题库]] 并发区。
