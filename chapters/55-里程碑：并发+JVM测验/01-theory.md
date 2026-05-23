# Chapter 55 里程碑：并发 + JVM 测验 - 理论篇

## 一、学习定位

51-54 的能力 **闭卷测试 + 实战复盘**。 不教新东西, 把高频面试题打通, 把易错点固化。

- 优先级: L1 (这是面试通关守门员)
- 投入: 4 小时 (1h 答题 + 1h 实验 + 1h 复盘 + 1h 输出)
- 产出: 并发 / JVM 个人面试答案集 + 3 个实验代码 + 1 份 GC 日志分析

## 二、知识地图回顾

```
                    Java 后端面试 (并发 + JVM)
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
     并发原理 (51)          线程池 (52)          并发安全 (53)
   ├ JMM / volatile       ├ ThreadPoolExecutor    ├ synchronized
   ├ happens-before       ├ 7 参数                 ├ ReentrantLock
   ├ 中断                  ├ 4 拒绝策略             ├ AQS
   └ 线程状态              ├ 队列选型               ├ CAS / AtomicXxx
                          ├ CompletableFuture     ├ ConcurrentHashMap
                          ├ 虚拟线程 (JDK 21)      ├ ThreadLocal
                          └ @Async / 监控          └ 死锁排查
                              │
                          JVM (54)
                  ├ 运行时数据区
                  ├ GC (G1 / ZGC / Generational ZGC)
                  ├ 类加载 双亲委派
                  ├ 故障排查 (CPU 100% / OOM / GC 长停顿)
                  └ 生产 JVM 参数
```

## 三、20 题速查 (面试到这层够用)

### 并发 10 题

| # | 题 | 关键词 |
|---|---|---|
| 1 | synchronized vs ReentrantLock | 公平 / 中断 / 超时 / 多 Condition / API |
| 2 | volatile 解决什么? 为什么不保证原子性? | 可见性 + 有序性, 不保证 i++ 原子 |
| 3 | AQS 是什么? 哪些工具基于它? | state + CLH FIFO 队列, Lock/Semaphore/CDL/RW |
| 4 | ThreadLocal 原理 + 内存泄漏 | ThreadLocalMap, key 弱引用 value 强引用 |
| 5 | CAS 三大问题 | ABA / 自旋 / 单变量 |
| 6 | ConcurrentHashMap 1.7 vs 1.8 | Segment vs CAS+synchronized 桶头 |
| 7 | 线程池 7 参数 + 调度顺序 | core → queue → max → reject |
| 8 | 4 种拒绝策略 | Abort / CallerRuns / Discard / DiscardOldest |
| 9 | 虚拟线程 vs 平台线程 | 数量 / 阻塞 / 适用场景 |
| 10 | 虚拟线程的 pin 问题 | synchronized 大块 pin 载体, ReentrantLock OK |

### JVM 10 题

| # | 题 | 关键词 |
|---|---|---|
| 11 | 运行时数据区 | PC/栈/本地栈 私有, 堆/Metaspace 共享 |
| 12 | 5 种 OOM | Heap / Metaspace / SOF / Direct / GC overhead |
| 13 | GC Roots 5 类 | 栈引用 / 静态 / 常量 / JNI / 活线程 |
| 14 | G1 vs ZGC vs Generational ZGC | 默认 G1, 大堆 ZGC, JDK21 推荐 Generational ZGC |
| 15 | 双亲委派 + 3 个打破 | JDBC / Tomcat / OSGi |
| 16 | CPU 100% 排查 | top -Hp → printf %x → jstack | grep nid |
| 17 | OOM dump + 分析 | HeapDumpOnOOM + MAT Dominator Tree |
| 18 | -Xms = -Xmx 原因 | 防扩堆 STW |
| 19 | 容器 JVM 内存 | cgroup 自动识别, 但仍显式 Xmx, 留 50% 给非堆 |
| 20 | 逃逸分析 / TLAB / 标量替换 | JIT 优化, 默认开启 |

## 四、错题分类

| 错因 | 占比 (经验值) | 解决 |
|---|---|---|
| 概念混淆 | 30% | 重读理论 + 写笔记 |
| 细节遗忘 | 25% | 卡片记忆 (Anki) |
| 没动手过 | 25% | 做手写实验 |
| 过时八股 | 10% | 验证 JDK 21 实情 (CMS 已删, 偏向锁默认禁) |
| 没看源码 | 10% | 读 1 次 AQS / ConcurrentHashMap put 流程 |

## 五、本里程碑的产出

```
chapters/55-.../
├── answers.md            ← 20 题闭卷答案 + 自我评分
├── experiments/
│   ├── VTBenchmark.java  ← 虚拟线程 vs 平台线程对比
│   ├── DeadlockDemo.java ← 死锁复现 + jstack 输出
│   └── OomDemo.java      ← OOM + MAT dominator 截图
├── gc-analysis.md        ← 博客 API 压测 10 分钟 GC 报告
└── interview-notes.md    ← 错题本 + 易混淆点
```

## 六、过关标准

- [ ] 20 题答对 ≥ 16
- [ ] 3 个手写实验都跑通
- [ ] GC 日志能解读 (Young GC / Pause / 堆占用曲线)
- [ ] 错题本至少 10 条, 每条写"错因 + 正确点"
- [ ] 能给同事讲 30 分钟并发 + JVM, 不卡壳
