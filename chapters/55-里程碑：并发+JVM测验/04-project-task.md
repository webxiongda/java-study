# Chapter 55 里程碑：并发 + JVM 测验 - 项目任务

> 前置：[[51-并发基础]] → [[54-JVM基础]]
> 这是知识沉淀型里程碑，不是写代码，而是把高频面试题打通。

## 任务概述

完成 20 道高频面试题的「答对 ≥ 16」+ 3 个手写实验 + 1 次 GC 日志分析。

## 量化验收

### 一、面试题（共 20，答对 ≥ 16）

> 题目来自 [[interview-bank|面试题库]] 并发/JVM 区。 闭卷 30 分钟。

并发 10 题：
1. `synchronized` 与 `ReentrantLock` 5 个差异
2. `volatile` 解决什么问题？为什么不能保证原子性
3. AQS 是什么？基于它实现了哪些工具
4. ThreadLocal 的原理 + 内存泄漏成因
5. CAS 三大问题（ABA / 自旋 CPU / 只能单变量）
6. `ConcurrentHashMap` 1.7 vs 1.8 区别
7. 线程池 7 大参数 + 任务调度顺序
8. 4 种拒绝策略，选哪一种
9. 虚拟线程 vs 平台线程 3 点差异
10. `synchronized` 在虚拟线程下的 pin 问题

JVM 10 题：
11. 运行时内存区域 5 块
12. 5 种 OOM 各自典型场景
13. 可达性分析的 GC Roots 5 类
14. G1 vs ZGC vs Generational ZGC（JDK 21）
15. 类加载双亲委派 + 3 个打破场景
16. CPU 100% 三板斧（top -Hp / jstack / 栈分析）
17. OOM 后如何 dump + 看哪些点
18. `-Xms` 必须等于 `-Xmx` 的原因
19. 容器里 JVM 内存怎么设
20. 逃逸分析 / TLAB / 标量替换

### 二、3 个手写实验

| # | 实验 | 验证 |
|---|---|---|
| 1 | 虚拟线程 vs 200 平台线程跑 10w IO 任务 | virtual ≥ 50x 快 |
| 2 | 死锁复现 + jstack 找出 | jstack 输出含 "Found one Java-level deadlock" |
| 3 | 故意 OOM + MAT 分析 hprof | 能在 Dominator Tree 找到泄露根 |

### 三、GC 日志分析

跑博客 API 持续压测 10 分钟，分析 `gc.log`：

| 指标 | 标准 |
|---|---|
| Young GC 频率 | < 1 次/秒 |
| Young GC 停顿 | P99 < 50ms |
| Full GC 次数 | 0 |
| Heap 利用率 | 稳态 < 70% |

## 提交产物

```
chapters/55-里程碑：并发+JVM测验/
├── answers.md           ← 20 题的自答（标对错）
├── experiments/
│   ├── vt-benchmark.java
│   ├── deadlock-demo.java
│   └── oom-demo.java
└── gc-analysis.md       ← 截图 + 分析
```

## 复盘问题

1. 错的几题，根因是「概念不清」还是「细节遗忘」？
2. JDK 21 视角下，哪些八股已经过时？
3. 你能讲清楚 ZGC 的着色指针 + 读屏障吗？讲不清就再补。
