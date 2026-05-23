# Chapter 51 并发基础 - 实操 Demo

## Demo 目标

亲手体验 5 个并发基础: 线程创建 / 中断 / 状态转换 / volatile 可见性 / wait-notify 生产消费。

## 一、Runnable / Callable / 虚拟线程

```java
public class CreateThreadDemo {
    public static void main(String[] args) throws Exception {
        // 1. Runnable
        Thread t1 = new Thread(() -> System.out.println("runnable on " + Thread.currentThread()));
        t1.start();
        t1.join();

        // 2. Callable + Future
        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<Integer> f = es.submit(() -> {
            Thread.sleep(100);
            return 42;
        });
        System.out.println("callable result = " + f.get());
        es.shutdown();

        // 3. 虚拟线程 (JDK 21)
        Thread.startVirtualThread(() -> System.out.println("virtual " + Thread.currentThread()))
              .join();
    }
}
```

运行预期: 三种创建方式都打印, 虚拟线程名形如 `VirtualThread[#XX]/runnable@ForkJoinPool-1-worker-1`。

## 二、合作式中断 (永远不要用 stop)

```java
public class InterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    System.out.println("working...");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    System.out.println("got interrupt, exit");
                    Thread.currentThread().interrupt();   // 重置中断位, 跳出 while
                }
            }
        });
        worker.start();
        Thread.sleep(2000);
        worker.interrupt();
        worker.join();
        System.out.println("worker done");
    }
}
```

**故意做错示范**: 删掉 `Thread.currentThread().interrupt();` → 线程永远不退出 (sleep 抛 InterruptedException 时清除了中断位)。

## 三、volatile 可见性

```java
public class VolatileDemo {
    private static boolean STOP = false;        // 改成 volatile 试对比
    // private static volatile boolean STOP = false;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            long c = 0;
            while (!STOP) c++;
            System.out.println("reader exit, count=" + c);
        });
        reader.start();
        Thread.sleep(1000);
        STOP = true;
        System.out.println("main set STOP = true");
        reader.join(3000);
        if (reader.isAlive()) System.out.println("reader STILL ALIVE -- 可见性问题!");
    }
}
```

**预期**:
- 不加 volatile (JIT 优化 + 缓存): reader 永远跑不出来 (大概率)
- 加 volatile: reader 1 秒后正常退出

## 四、wait / notify 经典生产消费

```java
public class WaitNotifyDemo {
    static final Queue<Integer> Q = new LinkedList<>();
    static final int CAP = 5;

    public static void main(String[] args) {
        new Thread(WaitNotifyDemo::produce, "P").start();
        new Thread(WaitNotifyDemo::consume, "C").start();
    }

    static void produce() {
        try {
            for (int i = 0; ; i++) {
                synchronized (Q) {
                    while (Q.size() >= CAP) Q.wait();
                    Q.offer(i);
                    System.out.println("produced " + i);
                    Q.notifyAll();
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void consume() {
        try {
            while (true) {
                synchronized (Q) {
                    while (Q.isEmpty()) Q.wait();
                    Integer item = Q.poll();
                    System.out.println("consumed " + item);
                    Q.notifyAll();
                }
                Thread.sleep(300);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

**坑点**: 必须 `while (Q.isEmpty())` 不能 `if`, 因为虚假唤醒 (spurious wakeup) 会让 wait 在没有 notify 的情况下返回。

## 五、线程状态可视化

```java
public class StateDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        Thread t = new Thread(() -> {
            synchronized (lock) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }, "worker");

        System.out.println("before start: " + t.getState());   // NEW
        t.start();
        Thread.sleep(100);
        System.out.println("after sleep: " + t.getState());    // TIMED_WAITING

        Thread blocker = new Thread(() -> {
            synchronized (lock) {}
        }, "blocker");
        blocker.start();
        Thread.sleep(100);
        System.out.println("blocker: " + blocker.getState());  // BLOCKED

        t.join();
        System.out.println("after join: " + t.getState());     // TERMINATED
    }
}
```

## 六、运行与验证

```bash
javac CreateThreadDemo.java && java CreateThreadDemo
javac InterruptDemo.java   && java InterruptDemo
javac VolatileDemo.java    && java -server VolatileDemo   # -server 模式更容易复现可见性问题
javac WaitNotifyDemo.java  && java WaitNotifyDemo
javac StateDemo.java       && java StateDemo
```

## 七、常见坑

| 坑 | 现象 | 修复 |
|---|---|---|
| 调 t.run() 不是 t.start() | 在主线程同步执行 | 必须 start |
| catch InterruptedException 后吞 | 父线程没法中断 | `Thread.currentThread().interrupt()` |
| 标志位不加 volatile | 别的线程改了看不到 | 加 volatile, 或用 AtomicBoolean |
| wait 用 if | 虚假唤醒导致非法状态 | 永远 while |
| 在不持有 monitor 时调 wait/notify | IllegalMonitorStateException | 必须 synchronized 块内 |

## 八、提交

```bash
git add backend/src/test/java/concurrency/
git commit -m "ch51: concurrency basics demos (interrupt/volatile/wait-notify/states)"
```
