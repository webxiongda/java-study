# Chapter 51 并发基础 - 理论

> 前置：[[19-注解与反射]]（不直接依赖，但需要类 / 方法基础）
> 后续：[[52-线程池与异步]] → [[53-并发安全]] → [[54-JVM基础]]
> 优先级：L3 面试高频  预计：4 小时

## 1. 为什么需要这一章

线程是 JVM 调度的最小单位，几乎所有线上事故（CPU 飙高、接口挂起、数据错乱）都和线程相关。 八股要会答，更重要的是写代码能避坑。 这一章只讲「基础」——线程怎么起、怎么停、怎么协作。 池化和锁分别放 52 / 53。

## 2. 三种线程创建方式

```java
// 1. 继承 Thread（不推荐，强耦合）
new Thread() { public void run() { ... } }.start();

// 2. Runnable（无返回值）
new Thread(() -> doWork()).start();

// 3. Callable + Future（有返回值 / 异常）
ExecutorService es = Executors.newSingleThreadExecutor();
Future<Integer> f = es.submit(() -> compute());
int r = f.get();   // 阻塞等待

// 4. JDK 21：虚拟线程（详见 52 章）
Thread.startVirtualThread(() -> doWork());
```

> 生产**永远用线程池**，不要裸 `new Thread`。

## 3. 线程的 6 个状态

```
NEW          // 已创建未 start
RUNNABLE     // 可运行（含 OS 视角的 ready / running）
BLOCKED      // 等待进入 synchronized 临界区
WAITING      // wait() / join() / LockSupport.park()，无限等
TIMED_WAITING // sleep(t) / wait(t)
TERMINATED   // run 结束
```

> `BLOCKED` 仅指 `synchronized` 竞争失败的等待；用 `ReentrantLock` 失败属于 `WAITING`。 这是面试坑题。

## 4. start vs run 区别

| 调用 | 行为 |
|---|---|
| `t.start()` | JVM 创建 OS 线程，由其执行 `run()` |
| `t.run()` | 在**当前线程**里同步执行，等于普通方法调用 |

## 5. 中断协作机制

Java 没有「强行 kill 线程」的安全方式。 `Thread.stop()` 已废弃（会破坏锁状态）。 正确做法是**合作式中断**：

```java
Thread t = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            doWork();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // sleep / wait / join 抛 InterruptedException 时会清除中断位，
            // 必须显式恢复，外层循环才能感知
            Thread.currentThread().interrupt();
            break;
        }
    }
});
t.start();
t.interrupt();   // 请求中断
```

**3 个关键 API**：
- `interrupt()`：设中断位（不中止执行）。
- `isInterrupted()`：查标志，不清除。
- `interrupted()`（静态）：查标志，**清除**。

## 6. 线程协作

### join

```java
Thread loader = new Thread(this::loadData);
loader.start();
loader.join();           // 当前线程等 loader 跑完
loader.join(3000);       // 最多等 3 秒
```

### sleep vs wait

| | `Thread.sleep` | `Object.wait` |
|---|---|---|
| 调用条件 | 任何地方 | 必须持有该对象的 monitor |
| 是否释放锁 | 否 | 是 |
| 唤醒 | 时间到 / `interrupt` | `notify` / `notifyAll` / 时间到 |
| 归属 | Thread 静态方法 | Object 实例方法 |

### wait / notify 经典生产消费

```java
synchronized (queue) {
    while (queue.isEmpty()) queue.wait();   // 必须 while 不是 if（虚假唤醒）
    item = queue.poll();
    queue.notifyAll();
}
```

> 工程里几乎不用 wait/notify，改用 `BlockingQueue` / `Lock + Condition`。

## 7. JMM（Java 内存模型）三大问题

| 问题 | 含义 | 例 |
|---|---|---|
| **原子性** | 操作不可分割 | `i++` 是 3 步（读、加、写） |
| **可见性** | 一个线程的写对另一个线程立即可见 | 不加 volatile 的标志位可能永远读旧值 |
| **有序性** | 程序顺序 vs 实际执行顺序（重排序） | 单例双检锁要 volatile |

### volatile 解决什么

```java
private volatile boolean stop = false;   // 写线程改了，读线程能立刻看到
```

- ✅ 可见性：写立刻刷主内存，读直接从主内存。
- ✅ 有序性：插入读 / 写屏障，禁止重排越过 volatile 操作。
- ❌ 原子性：`volatile int i; i++` 仍不安全。

### happens-before（必背 4 条）

1. 程序次序：同一线程内 A 写在 B 前，A happens-before B。
2. 锁规则：unlock 先于后续 lock。
3. volatile 规则：写先于后续读。
4. 传递性：A → B && B → C → A → C。

## 8. synchronized 简介（详见 53 章）

```java
synchronized (this) { ... }   // 锁实例
synchronized (Cls.class) { ... }   // 锁类
static synchronized void m() { ... }  // 等价锁 Cls.class
synchronized void m() { ... }         // 锁 this
```

- 可重入：同线程可多次进入。
- JDK 6 后有偏向 → 轻量 → 重量级锁升级。
- 编译为 `monitorenter` / `monitorexit` 字节码。

## 9. 项目场景：何时该用并发

| 场景 | 选择 |
|---|---|
| Web 请求处理 | 框架自带线程池，业务不要再起线程 |
| IO 重的 / 等待外部服务 | 虚拟线程（52）或 `CompletableFuture` 并行 |
| 批量 + 独立任务 | `ExecutorService` + Future / parallelStream |
| 异步通知 / 解耦 | `@Async` 或消息队列（48 章），别在请求线程做 |
| 定时任务 | `@Scheduled` + 配独立池，不要复用 web 池 |

## 10. 常见坑

- `new Thread().start()` 每请求一个 → OOM 或上下文切换雪崩。
- `ThreadLocal` 用完不 remove → 线程池场景下数据串号、内存泄漏。
- 共享变量不加同步只标 `volatile` → 解决了可见性，没解决原子性。
- 用 `Thread.sleep` 实现「定时」→ 精度差、不可中断。 用 `ScheduledExecutorService`。
- `InterruptedException` 直接吞掉 → 上层永远无法中断。 必须 `Thread.currentThread().interrupt()` 重置。

## 11. 面试高频

1. start 和 run 区别？
2. 线程 6 状态 / 状态转换图。
3. volatile 解决了什么？为什么不能保证原子性？
4. wait / sleep 5 点差异。
5. 怎么优雅停止一个线程？
6. ThreadLocal 工作原理 + 内存泄漏成因。
7. happens-before 4 条规则。
8. 线程池 vs 虚拟线程怎么选？（→ 52）

更多 → [[interview-bank|面试题库]] 并发区。
