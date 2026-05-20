# Chapter 54 JVM 基础（JDK 21 视角） - 理论

> 前置：[[53-并发安全]]
> 后续：[[55-里程碑：并发+JVM测验]]
> 优先级：L3 面试高频  预计：4 小时

## 1. 为什么需要这一章

线上 CPU 100% / OOM / GC 长停顿这三类故障，全部都跟 JVM 有关。 八股需要会答，更重要的是能定位与调优。 本章以 **JDK 21** 为基准——不要再背 PermGen / CMS。

## 2. 运行时内存区域

```
线程私有：
  ┌────────────────────────┐
  │ PC 寄存器（极小）       │   // 当前字节码行号
  │ Java 虚拟机栈           │   // 每个方法一个栈帧（局部变量、操作数栈）
  │ 本地方法栈              │   // native 方法
  └────────────────────────┘

线程共享：
  ┌────────────────────────┐
  │ 堆 Heap                │   // 对象实例，GC 主战场
  │   ├─ Young（Eden+S0+S1）│
  │   └─ Old                │
  │ Metaspace（堆外）       │   // 类元数据，JDK 8+ 用本地内存（取代 PermGen）
  │ 字符串常量池（堆内）     │   // JDK 7+ 移到堆
  │ 堆外 / Direct Memory   │   // NIO ByteBuffer.allocateDirect
  └────────────────────────┘
```

**OOM 可能发生在**：
- 堆（`java.lang.OutOfMemoryError: Java heap space`）— 最常见。
- Metaspace（`Metaspace`）— 动态生成类太多（CGLIB、反射框架）。
- 栈（`StackOverflowError`）— 递归无界。
- 直接内存（`Direct buffer memory`）— Netty / NIO 用得多。
- GC 时间过长（`GC overhead limit exceeded`）— GC 占 CPU > 98% 但只回收 < 2%。

## 3. 对象创建与逃逸分析

```
new Article(...)
  ↓
1. 类加载检查（如果没加载过 → 加载、验证、准备、解析、初始化）
2. 分配内存（指针碰撞 or 空闲列表，CAS 或 TLAB）
3. 零值初始化
4. 设置对象头（Mark Word + 类型指针 + 数组长度）
5. 执行 <init> 构造方法
```

**TLAB**（Thread Local Allocation Buffer）：每个线程在 Eden 里独占一小块，分配走指针碰撞无锁，极快。

**逃逸分析**：JIT 判断对象是否「逃出方法」，不逃出时可栈上分配 / 同步消除 / 标量替换。 JDK 8+ 默认开启。

## 4. GC 概念

### 4.1 可达性分析

GC Roots：虚拟机栈中引用、静态字段引用、常量引用、JNI 引用、活跃线程 等。 从 Roots 不可达的对象会被回收。

### 4.2 分代假说

- 大部分对象「朝生夕灭」→ 放新生代，频繁回收。
- 熬过几次 GC 的对象大概率长寿 → 晋升老年代。
- **分代收集**因此高效。 G1 / Parallel 都是分代；ZGC 在 JDK 21 也支持分代（`-XX:+ZGenerational`）。

### 4.3 三色标记 + 并发标记

现代 GC（G1、ZGC、Shenandoah）的核心算法：用白 / 灰 / 黑标记对象，**与应用线程并发执行**，仅需短暂 STW 同步。

## 5. JDK 21 的 GC 选择（重要更新）

| GC | 启用 | 特点 | 适用场景 |
|---|---|---|---|
| **G1**（默认） | `-XX:+UseG1GC` | 分区（Region）+ 可预测停顿 `-XX:MaxGCPauseMillis=200` | 默认就用，2-32 GB 堆通用 |
| **ZGC** | `-XX:+UseZGC` | 亚毫秒停顿，可管理 TB 级堆，并发回收 | 低延迟要求（金融、游戏） |
| **Generational ZGC**（JDK 21 新） | `-XX:+UseZGC -XX:+ZGenerational` | ZGC + 分代，吞吐和延迟都更好 | 21 推荐 |
| Shenandoah | `-XX:+UseShenandoahGC` | OpenJDK 自带，理念类似 ZGC | 同 ZGC |
| Parallel | `-XX:+UseParallelGC` | 吞吐优先，停顿大 | 离线批处理 |
| Serial | `-XX:+UseSerialGC` | 单线程，简单 | 容器小堆 / Lambda 函数 |

**注意**：CMS 在 JDK 14 已删除，Parallel Old 是默认搭配。 不要再讲 CMS。

### 工程建议

- ≤ 4 核 + ≤ 4 GB 堆：默认 G1 就够。
- 32 GB 以上 / P99 要求 < 10ms：上 Generational ZGC。
- 容器里给堆留 50-70%（其余给 Metaspace / 直接内存 / 线程栈）。

## 6. 关键参数（线上必背 10 个）

| 参数 | 作用 | 典型值 |
|---|---|---|
| `-Xms` / `-Xmx` | 堆初始/最大，**两者设成相等**避免扩缩 | `-Xms4g -Xmx4g` |
| `-Xss` | 单线程栈大小 | `-Xss512k` |
| `-XX:MetaspaceSize` / `MaxMetaspaceSize` | Metaspace 初始 / 上限 | 256m / 512m |
| `-XX:+UseG1GC` / `+UseZGC` | 选 GC | 见上表 |
| `-XX:MaxGCPauseMillis` | G1 期望停顿 | 200 |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 自动 dump | 必加 |
| `-XX:HeapDumpPath` | dump 路径 | `/var/log/app/` |
| `-Xlog:gc*:file=/var/log/gc.log:t,uptime:filecount=10,filesize=50M` | GC 日志（JDK 9+ 统一 `-Xlog`） | 必加 |
| `-XX:+ExitOnOutOfMemoryError` | OOM 直接退出，让 k8s 重启 | 容器化推荐 |
| `-XX:ActiveProcessorCount` | 容器内显式声明 CPU 数 | 看实际 cpu limit |

## 7. 类加载与双亲委派

```
Bootstrap ClassLoader     // 加载 $JAVA_HOME/lib/rt.jar、java.base
   │
Platform ClassLoader      // 原 Ext，JDK 9+ 改名
   │
App / System ClassLoader  // classpath
   │
自定义 ClassLoader        // Tomcat WebappClassLoader、OSGi
```

**双亲委派**：子加载器先委托父加载器去加载，避免核心类（如 java.lang.String）被覆盖。

**打破委派**：
- JDBC 的 `ServiceLoader`：父加载器要用子类（驱动）→ Thread Context ClassLoader。
- Tomcat：每个 WebApp 独立 ClassLoader，互不可见。
- JDK 9+ 模块化的 Layer ClassLoader。

## 8. 排查思路（背下来）

### CPU 100%

```bash
top -Hp <pid>                              # 找最热线程 tid（10 进制）
printf "%x\n" <tid>                         # 转 16 进制
jstack <pid> | grep <hex-tid> -A 30        # 看栈
```

### OOM

```bash
# 启动时已加 -XX:+HeapDumpOnOutOfMemoryError
# OOM 后用 MAT / Eclipse Memory Analyzer 打开 hprof
# 看 Dominator Tree 找最大保留对象
jmap -dump:live,format=b,file=heap.hprof <pid>   # 主动 dump（线上谨慎，会 STW）
```

### GC 长停顿

```bash
# 看 gc.log（启动时配 -Xlog:gc*）
# 关注：Pause Young (Normal) (G1 Evacuation Pause) 时间、Full GC 频率
# 工具：GCViewer / GCEasy
```

### 类加载问题

```bash
java -verbose:class                    # 看哪个 jar 加载了某个类
jcmd <pid> VM.classloader_stats
```

## 9. 常见坑

- 用 `-Xms != -Xmx` → 高峰扩堆触发 Full GC。
- 容器里不设 `-Xmx` → JDK 10+ 默认按 cgroup memory 算（取 1/4），但还是建议显式。
- Metaspace 不设上限 → 反射 / CGLIB 生成大量类时无限增长。
- 用 `System.gc()` → 在 G1 / ZGC 下可能触发 Full GC，禁用 `-XX:+DisableExplicitGC` 或在代码里删掉。
- 误以为 GC 越多越好 → GC 频率 + 停顿 + 吞吐三者权衡。

## 10. 与项目衔接

部署博客 API 的 JVM 启动参数（建议）：

```bash
java \
  -Xms2g -Xmx2g -Xss512k \
  -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/blog/ \
  -Xlog:gc*:file=/var/log/blog/gc.log:t,uptime:filecount=10,filesize=50M \
  -jar blog-api.jar
```
