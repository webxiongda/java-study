# Chapter 54 JVM 基础 - 自测与验收

## Q1 [L1·概念·章节内测] 画出 JVM 运行时数据区,说明哪些线程私有 / 共享 + 每个区域可能的 OOM 表现

**考点**: 运行时内存区域 / OOM 类型 / 对象创建与逃逸分析
**关联**: interview-bank.md#jvm-runtime-area

### 参考答案

```
线程私有(每个线程独立):
  ┌──────────────────────────────┐
  │ 程序计数器 PC                │   不会 OOM(唯一不会 OOM 的区域)
  │ 虚拟机栈(栈帧/局部变量表)  │   StackOverflowError / OOM
  │ 本地方法栈(native 方法)    │   同上
  └──────────────────────────────┘

线程共享(GC 主战场):
  ┌──────────────────────────────┐
  │ 堆 Heap                      │   OOM: Java heap space
  │   ├─ Young(Eden + S0 + S1)  │
  │   └─ Old                     │
  │ Metaspace(堆外, JDK 8+)    │   OOM: Metaspace(取代 PermGen)
  │ 字符串常量池(JDK 7+ 在堆) │   OOM: String intern
  │ 直接内存 Direct Memory       │   OOM: Direct buffer memory
  └──────────────────────────────┘
```

**5 种 OOM 表现 + 触发原因**(对应 02-demo.md 各演示):

| OOM 类型 | 错误消息 | 触发原因 | demo 类 | 排查工具 |
|---|---|---|---|---|
| 堆 OOM | `Java heap space` | 内存泄漏 / 大对象 | `HeapOomDemo.main` | heap dump + MAT |
| Metaspace OOM | `Metaspace` | CGLIB / 反射 / 动态代理生成类无上限 | `MetaspaceOomDemo.main` | `jcmd VM.classloader_stats` |
| 栈溢出 | `StackOverflowError` | 递归过深 / 调用链过长 | `StackOverflowDemo.main` | jstack 看栈 |
| 直接内存 OOM | `Direct buffer memory` | Netty / NIO `ByteBuffer.allocateDirect` 不释放 | — | `-XX:MaxDirectMemorySize` |
| GC 限制 | `GC overhead limit exceeded` | GC 占 98% CPU 但只回收 < 2% | — | gc.log + heap dump |

**对象创建与逃逸分析(把握细节)**:

`new Article(...)` 的实际过程:

1. 类加载检查 → 没加载就走「加载 → 验证 → 准备 → 解析 → 初始化」
2. **TLAB**(Thread Local Allocation Buffer)分配:每线程在 Eden 独占一小块,指针碰撞无锁,极快
3. 零值初始化(int=0, ref=null)
4. 设置对象头(Mark Word + 类型指针 + 数组长度)
5. 执行 `<init>` 构造方法

**逃逸分析**:JIT 判断对象是否"逃出方法",不逃出可触发:
- **栈上分配**:对象直接在栈帧分配,方法退出就回收,不进 GC
- **同步消除**:无逃逸对象的 synchronized 直接去掉
- **标量替换**:把对象拆成原始字段,寄存器存放

JDK 8+ 默认开启 `-XX:+DoEscapeAnalysis`,生产**绝对不要关**。

**🔥追问**:JDK 8 之前 PermGen,8 之后 Metaspace,为什么改?
- PermGen 是堆的一部分,大小固定(`-XX:MaxPermSize`),很容易 OOM(JSP / CGLIB 场景)。
- Metaspace 用本地内存(堆外),默认无上限,**仅受机器内存限制**。
- 但默认无上限 = 失控风险,所以生产**必须**设 `-XX:MaxMetaspaceSize`,见 `MetaspaceOomDemo` 验证。

---

## Q2 [L2·对比·章节内测+面试高频] JDK 21 五种 GC 选型 + G1 / ZGC / Generational ZGC 算法核心区别

**考点**: 分代假说 / 可达性分析 / GC 选择 / 工程建议
**关联**: interview-bank.md#jvm-gc-selection

### 参考答案

**GC 算法基础(分代假说 + 可达性分析)**:

- **分代假说**:大部分对象"朝生夕灭"→ 放新生代频繁回收;熬过几次 GC 的长寿对象 → 晋升老年代。**分代收集因此高效**。
- **可达性分析**:从 **GC Roots**(虚拟机栈引用、静态字段、常量、JNI 引用、活跃线程)出发,不可达的对象会被回收。
- **三色标记**:白(未扫描)/ 灰(自己已扫描、引用未扫)/ 黑(自己和引用都扫完)+ 写屏障维护一致性,实现**与应用并发标记**,仅需短暂 STW。

**JDK 21 六种 GC 对照**:

| GC | 启用参数 | 设计目标 | 典型停顿 | 适用堆 | JDK 21 建议 |
|---|---|---|---|---|---|
| **Serial** | `-XX:+UseSerialGC` | 单线程,简单 | 大 | < 1GB | Lambda / 容器小堆 |
| **Parallel** | `-XX:+UseParallelGC` | 吞吐优先 | 秒级 | 1-4GB | 离线批处理 |
| **G1**(默认) | `-XX:+UseG1GC` | 平衡(分区 Region) | 100-500ms | 2-32GB | **通用 Web 默认** |
| **ZGC** | `-XX:+UseZGC` | 超低延迟,染色指针 + 读屏障 | < 1ms | TB 级 | 金融 / 实时交易 |
| **Generational ZGC**(JDK 21 新) | `-XX:+UseZGC -XX:+ZGenerational` | ZGC + 分代 | < 1ms | TB 级 | **21 推荐**,比 ZGC 吞吐更高 |
| **Shenandoah** | `-XX:+UseShenandoahGC` | 低延迟,Red Hat 系 | < 10ms | 大堆 | OpenJDK 替代 ZGC |

**❌ 不要再讲的**:CMS(JDK 14 已删)、PermGen、`-XX:+UseConcMarkSweepGC`(已删)。

**算法核心区别**:

| 维度 | G1 | ZGC | Generational ZGC |
|---|---|---|---|
| 分区 | Region(1-32MB) | Region(2MB) | 分代 + Region |
| 标记 | SATB + 三色 | 染色指针 + 读屏障 | 染色指针 + 分代 |
| 整理 | Region 内复制 + 跨 Region 整理 | 并发整理 | 并发整理 + 分代 |
| 停顿目标 | `MaxGCPauseMillis` 软目标 | 几乎无 STW | 同 ZGC 但吞吐更高 |
| 适用 | 2-32GB 通用 | TB 级 + 低延迟 | TB 级 + 低延迟 + 高吞吐 |

**工程建议(选型决策树)**:

- ≤ 4 核 + ≤ 4GB 堆 → 默认 G1 即可
- 32GB+ / P99 < 10ms → Generational ZGC
- 容器内堆 = 总内存 × 50-70%(留给 Metaspace / 直接内存 / 线程栈)
- JDK 10+ 自动识别 cgroup,但仍**显式**设 `-Xmx` 比默认 1/4 内存安全

**🔥追问**:为什么 ZGC 几乎无停顿?Read Barrier 不是有性能开销吗?
- **染色指针**:64 位指针里 4 bit 存对象状态(标记/重定位),CPU 取指时直接判断,**用空间换时间**。
- **读屏障**(Load Barrier):每次读对象引用时,硬件辅助插一条快速判断,99% 路径无开销,只有正在迁移的对象走慢路径。
- **代价**:吞吐比 G1 低 5-15%(因 barrier 开销),但 P99 延迟优势数十倍。Generational ZGC 通过分代减少了 barrier 触发频率,把吞吐损失降到 5% 以内。

**🔥追问**:G1 为什么有 Full GC?如何避免?
- G1 设计目标是"避免" Full GC,但**老年代被打满**且并发标记跟不上时退化为单线程 Full GC(灾难)。
- 触发条件:Mixed GC 回收速度跟不上分配 → 老年代占用持续上涨 → IHOP 阈值触发并发标记 → 失败退 Full GC。
- 避免:增大堆 / 调低 `-XX:InitiatingHeapOccupancyPercent`(默认 45%)/ 避免大对象 / 排查内存泄漏。

---

## Q3 [L2·代码编写·项目实战] 写出博客 API 生产部署的完整 JVM 启动参数,逐项解释为什么

**考点**: 关键参数 / GC 选择 / 工程建议
**关联**: interview-bank.md#jvm-prod-params

### 题干

环境:K8s 容器,4 核 4GB 内存上限,JDK 21,Spring Boot 3 + MyBatis 项目。
写出完整 `java` 启动命令,并逐参数解释。

### 参考答案

```bash
java \
  -Xms2g -Xmx2g \                                                # ① 堆初始 = 最大,避免动态扩容触发 GC
  -Xss512k \                                                      # ② 线程栈 512K,平衡线程数与递归深度
  -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m \             # ③ Metaspace 初始 + 上限,防 CGLIB/MyBatis 失控
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \                        # ④ G1 + 期望停顿,2-32GB 堆通用
  -XX:+HeapDumpOnOutOfMemoryError \                              # ⑤ OOM 自动 dump,没 dump 等于盲排查
  -XX:HeapDumpPath=/var/log/blog/ \
  -XX:+ExitOnOutOfMemoryError \                                  # ⑥ OOM 后退出,让 k8s 重启
  -Xlog:gc*:file=/var/log/blog/gc.log:t,uptime:filecount=10,filesize=50M \   # ⑦ JDK 9+ 统一日志
  -Dfile.encoding=UTF-8 \                                         # ⑧ 防中文乱码
  -Duser.timezone=Asia/Shanghai \                                # ⑨ 防时区问题
  -XX:ActiveProcessorCount=4 \                                   # ⑩ 显式声明 CPU 数,某些 K8s 版本识别不准
  -jar blog-api.jar
```

**为什么这些参数(面试讲法)**:

| 参数 | 为什么 |
|---|---|
| `Xms = Xmx = 2g` | 防止扩堆 STW;容器化环境固定内存上限。堆 = 总内存 50%,留 50% 给 Metaspace / Direct / 线程栈 / 系统页缓存 |
| `Xss 512k` | 默认 1MB 太大,4GB 内存下能起 ≈ 2000 线程;Spring Boot 默认 200 个 Tomcat 线程,512K 完全够 |
| `MaxMetaspaceSize 256m` | 防 Spring CGLIB / MyBatis Mapper 代理生成类无限增长 OOM,见 `MetaspaceOomDemo` |
| `UseG1GC` | 2-32GB 堆的默认推荐,平衡停顿与吞吐 |
| `MaxGCPauseMillis=200` | G1 软目标,告诉它每次 GC 尽量 < 200ms,实际可能更长 |
| `HeapDumpOnOutOfMemoryError` | 没 dump 等于盲排查;**必须加**,文件路径 mount 持久化卷 |
| `ExitOnOutOfMemoryError` | 容器化下宁可死也不带病运行,K8s readiness 失败后会自动重启 |
| `-Xlog:gc*` | JDK 9+ 统一日志,取代 `-XX:+PrintGCDetails`;切片 + 滚动 + 大小限制 |
| `ActiveProcessorCount` | K8s 8.0 之前对 CPU limit 识别有 bug,显式声明保险 |

**❌ 不要做的事**:

- 不要用 `-XX:+UseConcMarkSweepGC`(已删)
- 不要在代码里用 `System.gc()`(G1/ZGC 下可能触发 Full GC),用 `-XX:+DisableExplicitGC` 兜底
- 不要 `-Xms != -Xmx`,生产环境扩堆触发 Full GC 是大灾难
- 不要忘记设 `-Xmx`,JDK 10+ 默认按 cgroup memory 取 1/4(可能不准)

**🔥追问**:`-XX:ActiveProcessorCount` 不显式设会有什么后果?
- JDK 默认按 `Runtime.availableProcessors()` 决定 GC 并行线程数、ForkJoinPool 大小。
- K8s 设了 `cpu: 4` 但有些早期版本仍读到节点总核数(如 32),导致 GC 起 32 个并行线程在 4 核上抢占,**反而变慢**。
- JDK 17+ 已基本修复,但显式设 `ActiveProcessorCount=4` 是**最稳妥**做法。

**🔥追问**:为什么不直接用 ZGC?
- 4GB 堆完全在 G1 舒适区,ZGC 优势在 TB 级。
- ZGC 有 5-15% 吞吐损失(读屏障),小堆下不值得。
- 上 ZGC 的判断:**堆 ≥ 16GB 且 P99 要求 < 10ms**。

---

## Q4 [L2·Debug·面试高频] 线上 Java 进程 CPU 100%,接口 503,从发现到定位的完整 5 步排查 + 根因分类

**考点**: CPU 100% 排查 / 排查思路 / 类加载与双亲委派
**关联**: interview-bank.md#cpu-100-debug

### 题干

某天早高峰,Grafana 告警:博客服务 8 个 Pod 中有 1 个 CPU 持续 100%,接口返回 503。
写出完整 5 步排查脚本(命令级)+ 根因分类对照表 + Arthas 一键方案 + 类加载场景下如果是类加载死锁怎么办。

### 参考答案

**5 步排查法(白板能默写)**:

```bash
# ① 找到 java 进程 PID
top -c
# 或 jps -l 找当前用户的 Java 进程
# 假设 PID = 12345, CPU 800% (8 核满)

# ② 找出该进程内 CPU 高的线程(注意是 -H 显示线程)
top -H -p 12345
# LWP 列里挑 > 90% 的几个,假设 23456

# ③ 线程 ID 转 16 进制(jstack 输出的 nid 是 hex)
printf "%x\n" 23456
# 5ba0

# ④ dump 全部线程栈
jstack 12345 > /tmp/stack.txt
# 进程没响应时用 jstack -F 强制 dump(会 STW,慎用)

# ⑤ 在 stack.txt 里 grep nid 找对应栈
grep -A 30 "nid=0x5ba0" /tmp/stack.txt
```

**根因分类 + 对策**:

| 栈特征 | 根因 | 修复 |
|---|---|---|
| 业务代码 `while(true)` 无 sleep | 死循环 / busy wait | 加 sleep / BlockingQueue 阻塞 |
| `java.util.HashMap.put` 链表死循环 | JDK 7 + 多线程 put(JDK 8 已修但仍可能扩容卡死) | 换 `ConcurrentHashMap` |
| `GC Thread` 系列占满 | GC 风暴,大对象 / 内存泄漏 | 看 gc.log + heap dump + MAT |
| `RUNNABLE Pattern.compile` / `Matcher.find` | **正则回溯灾难**(ReDoS) | 改正则 / 加超时 / 用 RE2 |
| `JIT C2 CompilerThread` | 代码触发 JIT 频繁编译 | 一般短暂;持续看具体类 |
| 大量 `BLOCKED` 同一锁 | 锁竞争 | 缩小临界区 / 换 `ConcurrentHashMap` / 读写锁 |
| `ClassLoader.loadClass` 嵌套 | 类加载死锁(双亲委派下 A 加载触发 B,B 又触发 A) | 重构静态初始化 |

**Arthas 一键方案(生产强烈推荐)**:

```bash
java -jar arthas-boot.jar 12345
> thread -n 5                           # 前 5 个 CPU 占用线程的栈(已自动 grep 好)
> dashboard                             # 实时大屏: GC / 线程 / 内存
> profiler start --duration 60 --file /tmp/p.html   # 60 秒火焰图
> trace com.javastudy.PostService getDetail '#cost > 500'   # 方法级耗时追踪
> watch com.javastudy.PostService getDetail '{params, returnObj}' 'params[0] > 100'
```

**类加载问题排查(双亲委派相关)**:

- jstack 中看到大量线程在 `ClassLoader.loadClass` BLOCKED → 类加载死锁
- **双亲委派**:子加载器先委托父加载器加载,避免核心类被覆盖;但**循环依赖**初始化时会死锁
- 排查工具:`jcmd <pid> VM.classloader_stats` / `java -verbose:class` 看加载顺序
- 修复:**避免静态初始化里跨 ClassLoader 调用**;Tomcat 场景检查 WebappClassLoader 与 SharedClassLoader 是否有循环依赖

**演示验证**(对应 02-demo.md):

- `CpuBurnDemo.main`:启动 BURNER + IDLE 两线程,用上面 5 步能精确定位到 `Math.sqrt` 死循环
- `StackOverflowDemo.main`:同样的 jstack 能看到递归调用栈(几千层 `recurse`)

**🔥追问**:`top -Hp` 显示的 LWP(线程 ID)和 jstack 的 nid 关系?
- `LWP` 是 Linux 内核分配的轻量级进程 ID(10 进制)
- `nid` 是 jstack 输出里"native thread id"的简称(16 进制)
- 二者**指向同一个 OS 线程**,只是进制不同,所以要 `printf "%x\n"` 转换

**🔥追问**:为什么不直接用 `top -Hp` 看到的线程名定位?
- top 显示的"线程名"是 OS 进程名,JVM 没把 Java 线程名同步过去(`jstack` 能看到 Java 线程名)
- 所以一定要走 "LWP → hex → 在 jstack 输出里 grep nid" 这条路

---

## Q5 [L3·场景设计·面试高频] 一个接口压测时 P99 突然飙到 2 秒,从最快定位到最深根因的"排查矩阵" + 常见坑

**考点**: 排查思路 / 常见坑 / 与项目衔接
**关联**: interview-bank.md#p99-spike-debug

### 题干

博客 `/api/v1/posts/{id}` 接口正常 P99 < 50ms,压测 1w QPS 时突然 P99 飙到 2000ms。 给出系统化排查矩阵 + 6 种典型根因 + 验证命令 + 常见坑清单。

### 参考答案

**排查矩阵(三时间维度)**:

| 时间维度 | 看什么 | 工具 |
|---|---|---|
| **正在发生** | CPU / 线程数 / GC 频率 / 锁竞争 | `top -H` / `jstat -gcutil 1000` / `jstack` |
| **最近 1 小时** | 慢日志 / GC 日志 / 应用日志 | `tail -f gc.log` / `tail -f app.log` |
| **历史趋势** | Prometheus QPS / P99 / heap / GC time / 线程池 | Grafana 三件套 |

**6 种典型根因 + 验证命令**:

**1) GC 长停顿(最常见)**

```bash
jstat -gcutil <pid> 1000          # YGC YGCT FGC FGCT 列波动
tail -f gc.log | grep Pause       # 看具体停顿时长
grep "Pause Full" gc.log          # Full GC 是大杀手
```

修:调大堆 / 换 ZGC / heap dump 找内存泄漏。

**2) 数据库慢查询**

```bash
# MySQL 端
SHOW PROCESSLIST;
SELECT * FROM mysql.slow_log WHERE start_time > NOW() - INTERVAL 1 HOUR;
EXPLAIN SELECT ...;                # 看是否走索引
```

修:加索引 / 优化 SQL / 走 ReadReplica。

**3) 下游服务慢**

Arthas 单接口耗时拆解:

```bash
> trace com.javastudy.PostService getById '#cost > 500'
# 输出每段方法耗时,定位慢在 mapper.selectVOById 还是 redis.get
```

修:加缓存 / 异步化 / 超时降级。

**4) 锁竞争**

```bash
jstack <pid> | grep -A 30 BLOCKED | head -100
# 大量线程 BLOCKED 在同一 Monitor → 锁竞争
```

修:缩小临界区 / 换 `ConcurrentHashMap` / 读写锁。

**5) 线程池打满**

```bash
# Spring Boot Actuator
curl /actuator/metrics/executor.queue.size
curl /actuator/metrics/executor.active
```

队列接近上限 → 任务排队等待。修:扩容 / `CallerRunsPolicy` / 限流。

**6) Full GC 频率**

```bash
jstat -gc <pid> 1000              # FGC 列单位时间增量
# 1 分钟 FGC++ > 2 = 异常
jmap -dump:live,format=b,file=/tmp/h.hprof <pid>
# 用 MAT 打开 → Dominator Tree 找最大保留对象
```

修:找泄漏(常见:静态 Map / ThreadLocal 没 remove / 监听器没 unregister)。

**常见坑清单(`-Xlog`/类加载/Metaspace/工具用法)**:

- ❌ 用 `Xms != Xmx` → 高峰扩堆触发 Full GC
- ❌ 容器里不设 `-Xmx` → JDK 10+ 按 cgroup memory 取 1/4,小堆下频繁 GC
- ❌ Metaspace 不设上限 → 反射 / CGLIB 生成大量类时无限增长(见 `MetaspaceOomDemo`)
- ❌ 用 `System.gc()` → G1/ZGC 下可能触发 Full GC,用 `-XX:+DisableExplicitGC` 兜底
- ❌ 误以为 GC 越多越好 → GC 频率 + 停顿 + 吞吐三者权衡
- ❌ `jmap -dump` 在线生产用 → 会 STW 数秒,务必加 `:live` 且低峰执行
- ❌ JDK 9 之前的 `-XX:+PrintGCDetails` 在 JDK 9+ 已弃用 → 统一用 `-Xlog:gc*`

**与项目衔接(博客 API 调优实例)**:

- 容器 4C4G → `-Xms2g -Xmx2g -XX:+UseG1GC` 是稳态(对应 Q3)
- 上线前用 `GcDemo` / `HeapOomDemo` 在测试环境**主动验证**参数
- 监控:Prometheus 抓 `jvm_gc_pause_seconds` + `jvm_memory_used_bytes`,Grafana 出图
- 日志:gc.log 与业务 log 分离,gc.log 必须 rotate

**面试 2 分钟讲法**:

> "我会先看 Grafana 三件套:QPS / P99 / 错误率,确认是单接口慢还是全局慢。全局慢看 GC(gc.log + jstat),单接口慢用 Arthas trace 定位到具体方法。排在前几位的根因:GC 风暴(内存泄漏)、慢 SQL(没索引)、下游超时、锁竞争、线程池满。每种都有对应工具能 10 分钟内定位。Arthas 是生产救命神器,trace + watch + profiler 三招几乎覆盖所有场景。"

**🔥追问**:为什么 GC 日志里 `Pause Young (Normal) (G1 Evacuation Pause)` 这种 STW 也不会让接口直接挂?
- Young GC 通常 < 50ms,JVM **整体暂停**,但 Tomcat 接收的 TCP 包还在内核 socket buffer 里。
- GC 完后线程继续处理,**只增加端到端延迟,不丢请求**。
- 真正出问题是 Full GC 数秒级停顿 → 客户端先超时断连 → 服务还在 GC → 请求积压 → 雪崩。

---

## 通过标准

- [ ] 能默写 JVM 运行时数据区(私有 / 共享)+ 5 种 OOM 表现 + 对象创建 + 逃逸分析
- [ ] 能讲分代假说 + 可达性分析 + 三色标记
- [ ] 能讲 G1 / ZGC / Generational ZGC 算法区别 + JDK 21 选型决策树
- [ ] 能写完整生产 JVM 启动参数 + 逐项解释为什么
- [ ] 能默写 CPU 100% 5 步排查命令 + 根因分类表 + Arthas 一键替代
- [ ] 能讲 P99 突增的 6 种根因 + 每种的验证命令
- [ ] 能复述 02-demo.md 中 `HeapOomDemo` / `MetaspaceOomDemo` / `StackOverflowDemo` / `CpuBurnDemo` / `GcDemo` 各自演示什么 OOM 类型
- [ ] 能讲类加载与双亲委派的打破场景(JDBC ServiceLoader / Tomcat / JDK 9 模块)

---

## 与 01-theory / 02-demo 的映射回顾

| Q | 对应理论小节 | 对应 demo 代码块 |
|---|---|---|
| Q1 | 2. 运行时内存区域 / 3. 对象创建与逃逸分析 | 一、HeapOomDemo / 二、MetaspaceOomDemo / 三、StackOverflowDemo |
| Q2 | 4. GC 概念(可达性分析 / 分代假说)/ 5. JDK 21 GC 选择 + 工程建议 | — |
| Q3 | 5. JDK 21 GC 选择 / 6. 关键参数 / 10. 与项目衔接 | — |
| Q4 | 7. 类加载与双亲委派 / 8. 排查思路(CPU 100%) | 四、CpuBurnDemo / 三、StackOverflowDemo |
| Q5 | 8. 排查思路(OOM / GC) / 9. 常见坑 / 10. 与项目衔接 | 五、GcDemo / 一、HeapOomDemo / 六、JVM 工具速查 |

---

> **下一章**:[[55-里程碑:并发+JVM测验]] — 用本章 + 53 章知识做综合压测与故障演练
