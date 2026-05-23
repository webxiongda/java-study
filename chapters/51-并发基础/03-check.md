# Chapter 51 并发基础 - 自测与验收

## Q1 概念: 线程 6 状态各代表什么? 转换图能画出来吗?

```
        ┌────────┐
        │  NEW   │
        └───┬────┘
            │ start()
            ▼
        ┌────────────┐  yield/系统调度    ┌────────────┐
        │ RUNNABLE   │ ────────────────► │ RUNNABLE   │ (OS Ready)
        └─┬──────┬─┬─┘                   └────────────┘
          │      │ │
synchronized失败 │ │ Object.wait() / LockSupport.park() / join() (无时限)
          ▼      │ ▼
    ┌─────────┐  │ ┌─────────┐
    │ BLOCKED │  │ │ WAITING │
    └────┬────┘  │ └────┬────┘
         │       │      │ notify / notifyAll / unpark / join 结束
         │       │      ▼
         │       │ ┌──────────────┐
         │       └►│TIMED_WAITING │ sleep(t) / wait(t)
         │         └──────┬───────┘
         │  拿到锁         │ 时间到
         └────────────────┴──────► RUNNABLE
                                      │ run() 结束
                                      ▼
                                ┌────────────┐
                                │ TERMINATED │
                                └────────────┘
```

**重点考点**:
- `BLOCKED` 专指 synchronized 竞争失败
- `ReentrantLock.lock()` 阻塞时是 `WAITING`, **不是 BLOCKED**
- `sleep` 不释放锁, `wait` 释放锁
- `interrupt` 只能唤醒 sleep/wait/join, 唤不醒 `synchronized` 等待 (BLOCKED 是不可中断的)

---

## Q2 概念: volatile 解决什么, 不解决什么? 为什么 i++ 不安全?

**解决**:
1. 可见性: 写立即刷主存, 读直接读主存 (绕过 CPU cache line)
2. 有序性: 插入读/写内存屏障, 禁止指令重排越过 volatile 操作

**不解决**: **原子性**。 `i++` 等价:
```
1. int tmp = i;       // 读
2. tmp = tmp + 1;     // 算
3. i = tmp;           // 写
```
两个线程同时读到 5, 各自 +1 写回 6, 结果 6 而不是 7。

**修复方式**:
```java
private final AtomicInteger i = new AtomicInteger();
i.incrementAndGet();    // CAS 保证原子
// 或
private final LongAdder counter = new LongAdder();  // 高并发更优 (分段累加)
```

**经典坑题**: 单例双检锁 (DCL) 为什么 `instance` 必须 volatile?
```java
private static volatile Singleton INSTANCE;
public static Singleton get() {
    if (INSTANCE == null) {
        synchronized (Singleton.class) {
            if (INSTANCE == null) INSTANCE = new Singleton();  // ← 非原子
        }
    }
    return INSTANCE;
}
```
`new Singleton()` 分 3 步: 分配内存 / 调构造 / 引用赋值。 如果发生 (1)→(3)→(2) 重排, 别的线程 `INSTANCE != null` 但对象未构造完, 拿到半成品。 volatile 禁止此重排。

---

## Q3 代码审查: 找出错误并修复

```java
public class Worker {
    private boolean stop = false;

    public void run() {
        new Thread(() -> {
            while (!stop) {
                try {
                    Thread.sleep(1000);
                    fetch();
                } catch (InterruptedException e) {
                    // ignored
                }
            }
        }).run();
    }

    public void stop() { stop = true; }
}
```

**问题清单**:

1. `t.run()` → 在调用方线程同步执行, 应改 `.start()`
2. `stop` 没 volatile → 子线程可能永远看不到 `true`
3. `catch InterruptedException` 后 `// ignored` → 中断位被清除, 上层无法感知
4. 没有保存线程引用 → 无法 `interrupt()` 优雅停止
5. 调用 `stop` 方法时只改字段, 真正中断不了 sleep 中的线程
6. 字段命名 `stop` 跟方法同名, 编译能过但有歧义
7. 没有线程命名 → 出问题 jstack 看不出

**修复**:

```java
public class Worker {
    private volatile boolean running = true;
    private Thread thread;

    public synchronized void start() {
        thread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    fetch();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("worker exited");
        }, "data-fetcher");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }
}
```

---

## Q4 代码: 用 CompletableFuture 并发调三个外部接口, 任一失败立即返回

```java
public class ParallelFetch {
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    public Result fetchAll(String userId) {
        CompletableFuture<UserVO> u = CompletableFuture.supplyAsync(() -> userApi.get(userId), pool);
        CompletableFuture<OrderVO> o = CompletableFuture.supplyAsync(() -> orderApi.list(userId), pool);
        CompletableFuture<CartVO> c = CompletableFuture.supplyAsync(() -> cartApi.get(userId), pool);

        try {
            CompletableFuture.allOf(u, o, c).get(2, TimeUnit.SECONDS);
            return new Result(u.join(), o.join(), c.join());
        } catch (TimeoutException e) {
            u.cancel(true); o.cancel(true); c.cancel(true);
            throw new BizException(504, "downstream timeout");
        } catch (Exception e) {
            throw new BizException(502, "downstream error: " + e.getCause().getMessage());
        }
    }
}
```

**关键点**:
- 用独立 pool, 不要共享 ForkJoinPool.commonPool (CPU 密集会饿死)
- `allOf().get(timeout)` 而不是单个 future.get, 否则总耗时 = 最长 + 中间等待
- 超时后 `cancel(true)` 释放下游 (但 supplyAsync 任务可能仍在跑, cancel 只是不再收结果)
- 异常用 `BizException` 统一包装
- pool 大小用 IO 密集公式: 核数 * (1 + 等待/计算)

---

## Q5 综合: 排查 "CPU 100%, 接口超时" 的步骤

**线上故障 5 步法**:

```bash
# 1. 找出 CPU 高的进程
top -c
# 假设 java pid=12345

# 2. 找出该 java 进程里 CPU 高的线程
top -H -p 12345
# 找出几个 CPU 90%+ 的线程 pid (linux 是 LWP)

# 3. 把线程 pid 转 16 进制
printf "%x\n" 23456    # 例如 5ba0

# 4. dump 全部线程栈
jstack 12345 > stack.txt

# 5. 在 stack.txt 里搜 nid=0x5ba0
grep -A 30 "nid=0x5ba0" stack.txt
```

**常见根因**:

| 现象 | 根因 | 解法 |
|---|---|---|
| 多个线程 RUNNABLE 在某循环 | 死循环 / busy wait | 加 sleep / 用 BlockingQueue |
| 多个线程 BLOCKED 同一锁 | 锁粒度太大 | 缩小临界区 / 分段锁 |
| 多个线程 WAITING ConditionObject | 死锁 (jstack 末尾会标 Found Deadlock) | 调整加锁顺序 |
| GC 线程 100% | 内存泄漏 / Full GC | 看 gc.log, 堆 dump 分析 |
| ParallelGC 全部 RUNNABLE | 进入 STW | 扩容堆 / 换 G1/ZGC |

**面试讲法 (2 分钟)**:
> "我会先 top 看 CPU 进程, 再 top -H 看到底是 java 哪个线程吃 CPU, 然后把线程 ID 转 16 进制, jstack 出来 grep nid 找到对应栈。 常见根因要么是死循环 / busy wait, 要么是大锁导致大量 BLOCKED, 要么是 GC 风暴。 看到栈后能定位具体代码行, 再针对性改: 死循环改 BlockingQueue, 大锁拆细, GC 换 G1。"

---

## 通过标准

- [ ] 能画线程 6 状态转换图, 区分 BLOCKED / WAITING
- [ ] 能讲清 volatile 三件事 (可见性、有序性、不解决原子性)
- [ ] 能识别上面 Worker 类 7 个问题
- [ ] 能写 CompletableFuture 并发调用 + 超时取消
- [ ] 能讲完整 CPU 100% 排查流程, 在白纸上写命令
