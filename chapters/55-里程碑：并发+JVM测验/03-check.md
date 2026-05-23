# Chapter 55 里程碑 - 20 题闭卷测验

> 闭卷, 限时 60 分钟。 答完用本页参考答案对照。 ≥ 16 题通过。

## 并发部分 (10 题)

### Q1. synchronized 和 ReentrantLock 的 5 个差异?

参考: 释放方式 (自动 vs finally) / 公平性 / 可中断 / 超时 / 多 Condition / API 灵活度。 JDK 6+ 后性能接近, 默认 synchronized; 需要超时/中断/多条件再上 RLock。

### Q2. volatile 解决什么? 为什么不能保证 i++ 原子性?

参考: 可见性 + 禁止重排; 不保证原子性。 i++ = 读 + 算 + 写 三步, 两线程同时读到 5, 各 +1 写 6, 丢一次。

### Q3. AQS 是什么? 基于它实现了哪些工具?

参考: AbstractQueuedSynchronizer, 用 `volatile int state` + CLH 变种 FIFO 等待队列。 子类重写 tryAcquire/tryRelease。 基于它: ReentrantLock / ReentrantReadWriteLock / Semaphore / CountDownLatch / FutureTask。

### Q4. ThreadLocal 原理 + 内存泄漏成因?

参考: 每个 Thread 内部有 ThreadLocalMap, key 是 ThreadLocal **弱引用**, value 是强引用。 ThreadLocal 被 GC 后 key=null 但 value 仍被 Thread → Map → Entry 强引用。 线程池长生命周期 + 不 remove → 泄漏 + 串号。 修复: 用完必 `remove()`。

### Q5. CAS 三大问题?

参考:
1. ABA (用 AtomicStampedReference 版本号)
2. 自旋开销 (用 LongAdder 分段)
3. 只能保证单变量 (用 AtomicReference 包对象 / 加锁)

### Q6. ConcurrentHashMap 1.7 vs 1.8?

参考:
- 1.7: Segment (默认 16) + ReentrantLock, 段级锁
- 1.8: Node 数组 + CAS + `synchronized(桶头)`, 桶级锁; 链表 ≥ 8 且数组 ≥ 64 树化
- 1.8 size 用 CounterCell (类 LongAdder)
- get 始终无锁

### Q7. ThreadPoolExecutor 7 参数 + 任务调度顺序?

参考: core/max/keepAlive(unit)/queue/factory/handler。 调度: < core 新建核心 → 进队列 → 队列满 + < max 新建非核心 → 拒绝。 **先入队再扩**, 不是先扩。

### Q8. 4 种拒绝策略, 你选哪种?

参考: AbortPolicy(默认抛异常) / CallerRunsPolicy(调用线程跑, **反向限流, 最常用**) / DiscardPolicy(静默丢) / DiscardOldestPolicy(丢最老再入)。 业务通常 CallerRuns。

### Q9. 虚拟线程 vs 平台线程 3 点差异?

参考:
1. 数量: 百万 vs 千 (栈按需 vs 1MB 固定)
2. 阻塞: VT 挂起释放载体 vs PT 占着 OS 线程
3. 适用: IO 密集 vs CPU 密集

### Q10. synchronized 在虚拟线程下的 pin 问题?

参考: JDK 21 的 synchronized 临界区里, 虚拟线程会 **pin 载体线程** (不能让出载体跑别的 VT), 高并发场景失去虚拟线程优势。 JDK 24+ 改善。 推荐用 ReentrantLock 替换 synchronized 大块。

---

## JVM 部分 (10 题)

### Q11. JVM 运行时数据区?

参考: 程序计数器 / 虚拟机栈 / 本地方法栈 (私有); 堆 / Metaspace (共享)。 字符串常量池 JDK 7+ 在堆。

### Q12. 5 种 OOM 场景?

参考:
- `Java heap space`: 内存泄漏, 缓存无上限
- `Metaspace`: CGLIB / 反射生成类无限增长
- `StackOverflowError`: 递归无界
- `Direct buffer memory`: Netty / NIO DirectBuffer 不释放
- `GC overhead limit exceeded`: GC 99% 时间但只回收 < 2%

### Q13. GC Roots 5 类?

参考: 虚拟机栈引用 / 本地方法栈 (JNI) / 方法区静态字段 / 方法区常量 / 活线程。

### Q14. JDK 21 选哪个 GC?

参考:
- ≤ 4GB 堆: G1 (默认)
- ≥ 32GB / P99 < 10ms: **Generational ZGC** (`-XX:+UseZGC -XX:+ZGenerational`)
- 离线批: Parallel
- CMS 已删 (JDK 14), 不要再讲

### Q15. 双亲委派 + 3 个打破场景?

参考:
- Bootstrap → Platform → App → 自定义
- 打破: JDBC (DriverManager + ServiceLoader + ThreadContextClassLoader) / Tomcat (每 WebApp 独立 ClassLoader) / OSGi

### Q16. CPU 100% 三板斧?

```bash
top -Hp <pid>                  # 找最热线程 LWP
printf "%x\n" <LWP>            # 转 16 进制
jstack <pid> | grep nid=0x.. -A 30
```

### Q17. OOM 后怎么 dump + 看哪些?

参考:
- 启动加 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...`
- 用 Eclipse MAT 打开 hprof
- 看 **Leak Suspects** / **Dominator Tree** (Retained Heap 排序) / **Path to GC Roots** (引用链)
- 主动: `jmap -dump:live,format=b,file=h.hprof <pid>` (有 STW)

### Q18. -Xms 为什么应该等于 -Xmx?

参考: 堆动态扩缩容触发 GC + STW; 容器化固定上限避免与 cgroup 冲突。 生产固定相等。

### Q19. 容器里 JVM 内存怎么设?

参考: JDK 10+ 自动识别 cgroup (默认堆 1/4), 但 **显式 -Xmx 仍是最佳实践**。 堆给容器 50-70%, 留剩余给 Metaspace / 直接内存 / 线程栈 / 系统。 例 4GB → -Xmx2g。

### Q20. 逃逸分析 / TLAB / 标量替换?

参考:
- TLAB: 每线程在 Eden 独占一小块, 分配无锁
- 逃逸分析: JIT 判断对象是否逃出方法
- 标量替换: 未逃逸对象拆成基本类型栈上分配, 无需 GC
- JDK 8+ 默认开启

---

## 自我评分

| 范围 | 分数 |
|---|---|
| 答对题数 | __ / 20 |
| 实验 1 (VT bench) | 完成 / 未完成 |
| 实验 2 (死锁) | 完成 / 未完成 |
| 实验 3 (OOM dump) | 完成 / 未完成 |
| GC 日志分析 | 完成 / 未完成 |

**通过标准**: ≥ 16 题对 + 3 个实验完成 + GC 分析

---

## 错题本格式

```
## Q__ 错误
**我的答案**: ...
**正确答案**: ...
**错因**: 概念混淆 / 细节遗忘 / 没动手 / 过时
**修复**: 重读 chapters/__/01-theory.md 第 __ 节, 写一次 demo
```

---

## 通过标准

- [ ] 20 题闭卷答对 ≥ 16
- [ ] 3 个实验都跑通并保存输出
- [ ] GC 日志至少分析 1 次, 能识别 Young GC / Full GC / 停顿曲线
- [ ] 错题本 ≥ 4 条, 每条有"错因 + 修复路径"
- [ ] 能在白板默写 CPU 100% 三板斧命令
