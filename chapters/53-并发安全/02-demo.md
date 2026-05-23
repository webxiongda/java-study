# Chapter 53 并发安全 - 实操 Demo

## Demo 目标

亲手对比并感受 6 种并发安全工具:
1. `i++` 直接共享 (错误示范)
2. `synchronized`
3. `AtomicInteger`
4. `LongAdder` (高竞争)
5. `ReentrantLock` 公平 vs 非公平
6. `ConcurrentHashMap` 替换 `HashMap`

## 一、计数器对比 (5 种实现)

```java
public class CounterBench {
    static final int THREADS = 16;
    static final int LOOP = 1_000_000;

    interface Counter { void inc(); long get(); }

    public static void main(String[] args) throws Exception {
        run("Unsafe (i++)", new Counter() {
            long c = 0;
            public void inc() { c++; }
            public long get() { return c; }
        });

        run("synchronized", new Counter() {
            long c = 0;
            public synchronized void inc() { c++; }
            public synchronized long get() { return c; }
        });

        run("AtomicLong", new Counter() {
            AtomicLong c = new AtomicLong();
            public void inc() { c.incrementAndGet(); }
            public long get() { return c.get(); }
        });

        run("LongAdder", new Counter() {
            LongAdder c = new LongAdder();
            public void inc() { c.increment(); }
            public long get() { return c.sum(); }
        });

        run("ReentrantLock", new Counter() {
            ReentrantLock lock = new ReentrantLock();
            long c = 0;
            public void inc() { lock.lock(); try { c++; } finally { lock.unlock(); } }
            public long get() { return c; }
        });
    }

    static void run(String name, Counter c) throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            es.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < LOOP; j++) c.inc();
                done.countDown();
            });
        }
        long t0 = System.currentTimeMillis();
        start.countDown();
        done.await();
        long cost = System.currentTimeMillis() - t0;
        es.shutdown();
        System.out.printf("%-15s expected=%d got=%d cost=%dms%n",
            name, (long) THREADS * LOOP, c.get(), cost);
    }
}
```

**典型输出 (16 核, M2)**:
```
Unsafe (i++)    expected=16000000 got=4521033   cost=82ms     ← 丢数据
synchronized    expected=16000000 got=16000000  cost=520ms
AtomicLong      expected=16000000 got=16000000  cost=340ms
LongAdder       expected=16000000 got=16000000  cost=110ms    ← 高竞争最快
ReentrantLock   expected=16000000 got=16000000  cost=480ms
```

**结论**:
- `i++` 丢约 70% → 证明非原子
- `LongAdder` 在高竞争场景秒杀 `AtomicLong` (分段累加, 最后 sum)

## 二、死锁演示 + jstack

```java
public class DeadlockDemo {
    static final Object A = new Object();
    static final Object B = new Object();

    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (A) { sleep(100); synchronized (B) { System.out.println("t1 ok"); } }
        }, "t1").start();
        new Thread(() -> {
            synchronized (B) { sleep(100); synchronized (A) { System.out.println("t2 ok"); } }
        }, "t2").start();
    }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
```

```bash
jps                                  # 找 pid
jstack <pid> | grep -A 20 "Found one Java-level deadlock"
```

预期输出末尾:
```
Found one Java-level deadlock:
=============================
"t1": waiting to lock monitor 0x... (object 0x... [B]),
  which is held by "t2"
"t2": waiting to lock monitor 0x... (object 0x... [A]),
  which is held by "t1"
```

## 三、ThreadLocal 串号坑

```java
public class TLPollutionDemo {
    static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);  // 只 2 个线程
        for (int i = 0; i < 5; i++) {
            int user = i;
            pool.submit(() -> {
                System.out.println(Thread.currentThread().getName()
                    + " before: " + USER.get());     // 看到上一次用户的数据!
                USER.set("user-" + user);
                // 漏写 USER.remove();
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

**输出示例**:
```
pool-1-thread-1 before: null
pool-1-thread-2 before: null
pool-1-thread-1 before: user-0     ← 串号! 应是 null
pool-1-thread-2 before: user-1
pool-1-thread-1 before: user-2
```

加上 `try { ... } finally { USER.remove(); }` 修复。

## 四、CAS + ABA + AtomicStampedReference

```java
public class AbaDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger ref = new AtomicInteger(1);

        new Thread(() -> {
            ref.compareAndSet(1, 2);
            ref.compareAndSet(2, 1);   // 改回去
        }).start();

        Thread.sleep(100);
        boolean ok = ref.compareAndSet(1, 3);
        System.out.println("普通 CAS 成功? " + ok);   // true, 没感知 ABA

        AtomicStampedReference<Integer> stamped = new AtomicStampedReference<>(1, 0);
        new Thread(() -> {
            stamped.compareAndSet(1, 2, 0, 1);
            stamped.compareAndSet(2, 1, 1, 2);
        }).start();
        Thread.sleep(100);
        boolean ok2 = stamped.compareAndSet(1, 3, 0, 1);   // 期望版本 0, 实际 2
        System.out.println("带版本 CAS 成功? " + ok2);    // false
    }
}
```

## 五、ConcurrentHashMap 的 computeIfAbsent

```java
ConcurrentHashMap<String, AtomicLong> stats = new ConcurrentHashMap<>();

// 错误: 非原子, 高并发下创建多个 AtomicLong
stats.putIfAbsent("pv", new AtomicLong());   // 每次 new 都对象浪费
stats.get("pv").incrementAndGet();

// 正确: computeIfAbsent 原子保证
stats.computeIfAbsent("pv", k -> new AtomicLong()).incrementAndGet();
```

## 六、运行 & 验证

```bash
javac CounterBench.java && java CounterBench       # 看 5 种实现耗时与正确性
javac DeadlockDemo.java && java DeadlockDemo &     # 起死锁
jstack $!                                          # 看到 Found one deadlock
javac TLPollutionDemo.java && java TLPollutionDemo
javac AbaDemo.java && java AbaDemo
```

## 七、提交

```bash
git add backend/src/test/java/concurrency/
git commit -m "ch53: thread-safety demos (counter bench / deadlock / tl-pollution / aba)"
```
