# Chapter 54 JVM 基础 - 实操 Demo

## Demo 目标

亲手制造 + 排查 5 类 JVM 问题:
1. 堆 OOM + heap dump 分析
2. Metaspace OOM
3. StackOverflow
4. CPU 100% (死循环) 定位
5. GC 日志分析

## 前置

- JDK 21
- 可选: 下载 [Eclipse MAT](https://eclipse.dev/mat/) 或 [Arthas](https://github.com/alibaba/arthas)

## 一、堆 OOM (最常见)

```java
public class HeapOomDemo {
    public static void main(String[] args) {
        List<byte[]> hold = new ArrayList<>();
        for (int i = 0; ; i++) {
            hold.add(new byte[1024 * 1024]);   // 每次 1MB
            System.out.println("allocated " + i + "MB");
        }
    }
}
```

```bash
javac HeapOomDemo.java
java -Xms64m -Xmx64m \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./ \
     HeapOomDemo
```

**预期**: 大约第 60-70 次循环时:
```
java.lang.OutOfMemoryError: Java heap space
Dumping heap to ./java_pid12345.hprof ...
```

用 MAT 打开 hprof → Dominator Tree → 看到 `ArrayList` 持有 60MB byte[]。

## 二、Metaspace OOM

```java
public class MetaspaceOomDemo {
    public static void main(String[] args) {
        long i = 0;
        while (true) {
            ByteBuddy bb = new ByteBuddy();
            Class<?> c = bb.subclass(Object.class)
                .name("Generated_" + i++)
                .make()
                .load(MetaspaceOomDemo.class.getClassLoader())
                .getLoaded();
            if (i % 1000 == 0) System.out.println("generated " + i);
        }
    }
}
```

```bash
java -XX:MaxMetaspaceSize=32m MetaspaceOomDemo
```

**预期**: `java.lang.OutOfMemoryError: Metaspace`

**结论**: 反射 / CGLIB / 动态代理框架 (Spring AOP, MyBatis Mapper proxy, Mockito) 会生成大量 class, 必须设 MaxMetaspaceSize 上限。

## 三、StackOverflow

```java
public class StackOverflowDemo {
    static int depth = 0;
    public static void main(String[] args) {
        try { recurse(); } catch (StackOverflowError e) {
            System.out.println("depth = " + depth);
        }
    }
    static void recurse() { depth++; recurse(); }
}
```

```bash
java -Xss256k StackOverflowDemo   # 输出 depth ≈ 2000
java -Xss1m StackOverflowDemo     # 输出 depth ≈ 8000
```

**结论**: 栈大小线性影响递归深度。 生产 `-Xss512k` 平衡线程数与递归。

## 四、CPU 100% 定位

```java
public class CpuBurnDemo {
    public static void main(String[] args) throws Exception {
        new Thread(() -> {
            while (true) Math.sqrt(Math.random());   // 死循环
        }, "BURNER").start();

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }, "IDLE").start();

        Thread.currentThread().join();
    }
}
```

```bash
java CpuBurnDemo &
PID=$!

# 1. 找 CPU 高的线程
top -H -p $PID

# 假设输出某行 LWP = 23456 占 99% CPU
printf "%x\n" 23456
# 输出 5ba0

# 2. dump 线程栈
jstack $PID > stack.txt

# 3. 找到对应栈
grep -A 30 "nid=0x5ba0" stack.txt
# 应看到 "BURNER" 线程在 Math.sqrt 死循环
```

或用 Arthas 一行命令:
```bash
java -jar arthas-boot.jar $PID
> thread -n 3        # 前 3 个 CPU 占用线程的栈
```

## 五、GC 日志分析

```java
public class GcDemo {
    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            byte[] b = new byte[1024 * 1024];   // 1MB
            if (i % 10 == 0) Thread.sleep(100);
        }
    }
}
```

```bash
java -Xms32m -Xmx128m \
     -XX:+UseG1GC \
     -Xlog:gc*:file=gc.log:t,uptime:filecount=5,filesize=10M \
     GcDemo
```

`gc.log` 看到:
```
[2026-05-23T10:01:23.123+0800][0.234s] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 32M->5M(128M) 5.234ms
[2026-05-23T10:01:23.250+0800][0.361s] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 38M->8M(128M) 4.123ms
...
```

**关键字段**:
- `Pause Young`: 年轻代 GC, 应 < 100ms
- `Pause Full`: 全堆 GC, 必须警惕
- `32M->5M(128M)`: GC 前/后/总堆
- `5.234ms`: STW 停顿时间

工具:
- [GCViewer](https://github.com/chewiebug/GCViewer) 本地
- [GCEasy.io](https://gceasy.io/) 在线上传 gc.log 分析

## 六、JVM 工具速查

| 工具 | 用途 | 示例 |
|---|---|---|
| `jps` | 列出 Java 进程 | `jps -l` |
| `jstat` | GC / 类加载 实时统计 | `jstat -gcutil <pid> 1000` 每秒一次 |
| `jstack` | 线程栈 | `jstack <pid>` |
| `jmap` | 堆 dump / 直方图 | `jmap -dump:live,format=b,file=h.hprof <pid>` |
| `jcmd` | 万能 | `jcmd <pid> VM.flags`, `jcmd <pid> GC.heap_info` |
| `jhsdb` | 类似 GDB | `jhsdb jmap --heap --pid <pid>` |
| `Arthas` | 在线诊断神器 | watch / trace / monitor / heapdump |
| `JFR` | 低开销飞行记录 | `jcmd <pid> JFR.start duration=60s filename=app.jfr` |
| `JMC` | JFR 可视化 | 打开 .jfr 看 hot methods / GC / locks |

## 七、提交

```bash
git add jvm-demos/
git commit -m "ch54: JVM demos (OOM/SOF/CPU-burn/GC-log)"
```
