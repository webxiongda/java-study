# Chapter 55 里程碑 - 实操 Demo: 3 个必做实验

## 实验 1: 虚拟线程 vs 平台线程 (吞吐对比)

```java
public class VTBenchmark {
    static final int TASKS = 100_000;

    public static void main(String[] args) throws Exception {
        // 平台线程 (固定 200)
        try (var es = Executors.newFixedThreadPool(200)) {
            long start = System.currentTimeMillis();
            CountDownLatch done = new CountDownLatch(TASKS);
            for (int i = 0; i < TASKS; i++) {
                es.submit(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    done.countDown();
                });
            }
            done.await();
            System.out.println("Platform pool(200): " + (System.currentTimeMillis() - start) + "ms");
        }

        // 虚拟线程
        try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.currentTimeMillis();
            CountDownLatch done = new CountDownLatch(TASKS);
            for (int i = 0; i < TASKS; i++) {
                es.submit(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    done.countDown();
                });
            }
            done.await();
            System.out.println("Virtual threads: " + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
```

**预期输出**:
```
Platform pool(200): 50_100ms    ← 100000/200 * 100ms ≈ 50s
Virtual threads:    150ms       ← 全部并行执行, 仅 sleep + 调度开销
```

**结论**: 阻塞 IO 场景, 虚拟线程吞吐提升约 300x。

## 实验 2: 死锁复现 + jstack 排查

```java
public class DeadlockDemo {
    static final Object A = new Object();
    static final Object B = new Object();

    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> {
            synchronized (A) {
                sleep(100);
                synchronized (B) { System.out.println("t1 done"); }
            }
        }, "t1");

        Thread t2 = new Thread(() -> {
            synchronized (B) {
                sleep(100);
                synchronized (A) { System.out.println("t2 done"); }
            }
        }, "t2");

        t1.start(); t2.start(); t1.join();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
```

```bash
javac DeadlockDemo.java
java DeadlockDemo &
PID=$!
sleep 1
jstack $PID > deadlock.txt
grep -A 30 "Found one Java-level deadlock" deadlock.txt
```

**预期输出节选**:
```
Found one Java-level deadlock:
=============================
"t2": waiting to lock monitor 0x... which is held by "t1"
"t1": waiting to lock monitor 0x... which is held by "t2"
```

**修复**: 全部按 A → B 顺序加锁 (锁排序), 或用 `tryLock(timeout)` 退避。

## 实验 3: 堆 OOM + MAT 分析

```java
public class OomDemo {
    static class LeakObject {
        byte[] payload = new byte[1024 * 100];   // 100KB
        String trace = "request-" + UUID.randomUUID();
    }

    public static void main(String[] args) {
        Map<String, LeakObject> CACHE = new HashMap<>();
        int i = 0;
        while (true) {
            CACHE.put("k-" + i, new LeakObject());
            if (++i % 1000 == 0) System.out.println("cached " + i);
        }
    }
}
```

```bash
javac OomDemo.java
java -Xms64m -Xmx64m \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./ \
     OomDemo
```

OOM 后看 `*.hprof`:

1. 下载 [Eclipse MAT](https://eclipse.dev/mat/)
2. 打开 hprof → 自动建索引
3. **Leak Suspects** 直接指出 HashMap
4. **Dominator Tree** 按 Retained Heap 排序
5. **Path to GC Roots** 找静态字段链

**Dominator Tree 典型截图**:
```
Class                                  | Shallow | Retained
java.util.HashMap @ 0x...              | 64       | 58_000_000
  └─ HashMap$Node[] table              | 1024     | 57_999_876
       └─ HashMap$Node                 | 32       | 100_000
            └─ OomDemo$LeakObject      | 16       | 100_000
                 └─ byte[] payload     | 102400   | 102400
```

**生产对应**: 这里的 `HashMap` 可能是 PostCache / UserSession。 修复: Caffeine maximumSize, TTL, ThreadLocal 必 remove。

## 实验 4 (可选): GC 日志分析

```bash
docker compose up -d
wrk -t8 -c200 -d10m --latency http://localhost:8080/api/v1/posts/1

docker exec blog cat /var/log/blog/gc.log | grep "Pause" > gc-pauses.log
awk '/Pause Young/ {sum+=$NF; cnt++} END {print "YGC平均", sum/cnt, "ms, 次数", cnt}' gc-pauses.log
awk '/Pause Full/' gc-pauses.log
```

**健康基线 (2GB 堆 G1)**:
- Young GC: 0.2-1 次/秒
- Young GC P99: < 50ms
- Full GC: 0
- 堆稳态: 30-60%

不健康信号: YGC > 5/s (Eden 小) / YGC > 200ms (大对象 / 高存活率) / Full GC > 0 (泄漏)。

## 提交

```bash
git add chapters/55-里程碑：并发+JVM测验/experiments/
git commit -m "ch55: VT bench + deadlock + OOM dump + GC analysis"
```
