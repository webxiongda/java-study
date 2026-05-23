# Chapter 54 JVM 基础 - 自测与验收

## Q1 概念: JVM 运行时数据区有哪些, 哪些是线程私有 / 共享, 各自 OOM 表现?

```
线程私有:
  - 程序计数器 PC      (不会 OOM)
  - 虚拟机栈           StackOverflowError (递归过深) / OOM (申请新栈失败)
  - 本地方法栈         同上

线程共享:
  - 堆 Heap            OutOfMemoryError: Java heap space
    ├─ Young (Eden+S0+S1)
    └─ Old
  - Metaspace (堆外)   OutOfMemoryError: Metaspace
  - 字符串常量池       OOM: String intern (JDK 7+ 在堆里)
  - 直接内存 (Direct)  OutOfMemoryError: Direct buffer memory
```

| OOM 类型 | 错误消息 | 常见原因 | 排查 |
|---|---|---|---|
| 堆 | `Java heap space` | 内存泄漏 / 大对象 | heap dump + MAT |
| Metaspace | `Metaspace` | CGLIB / 反射生成类无上限 | 限上限 + 看类加载日志 |
| StackOverflow | `StackOverflowError` | 递归 / 调用链过深 | 看栈, 拆递归 |
| 直接内存 | `Direct buffer memory` | Netty / NIO 用 DirectBuffer 不释放 | 看 `-XX:MaxDirectMemorySize` |
| GC 限制 | `GC overhead limit exceeded` | GC 99% 时间但只回收 < 2% | 大对象 / 内存紧 |

---

## Q2 概念: G1 vs ZGC vs Generational ZGC, JDK 21 怎么选?

| GC | 设计目标 | 停顿 | 适用堆大小 | JDK 21 推荐场景 |
|---|---|---|---|---|
| **Parallel** | 吞吐优先 | 较大 (秒级) | 1-4GB | 离线批处理 |
| **G1** (默认) | 平衡 | 100-500ms | 2-32GB | 通用 Web (默认选它) |
| **ZGC** | 超低延迟 | < 1ms | TB 级 | 金融 / 实时交易 / 游戏服务器 |
| **Generational ZGC** (JDK 21 新) | 低延迟 + 高吞吐 | < 1ms | TB 级 | 21+ 推荐, 比 ZGC 吞吐高 |
| **Shenandoah** | 低延迟 | < 10ms | 大堆 | RedHat 生态 |
| **Serial** | 简单 | 大 | < 1GB | Lambda / 小容器 |

**JDK 21 默认推荐**:
- ≤ 4GB 堆: G1 (`-XX:+UseG1GC`, 默认)
- ≥ 32GB 堆 / P99 < 10ms: `-XX:+UseZGC -XX:+ZGenerational`
- 不要再用 CMS (JDK 14 已删)
- 不要用 `-XX:+UseConcMarkSweepGC` (已删)

**重点考点**: G1 是 **分区 Region**, 不是传统的 Eden/S0/S1 物理分代; ZGC 用 **染色指针** + 读屏障实现并发标记/迁移。

---

## Q3 实操: 写出博客 API 在 4 核 4GB 容器中的 JVM 参数

```bash
java \
  -Xms2g -Xmx2g \                           # 初始最大相等, 避免动态扩容触发 GC
  -Xss512k \                                # 栈 512K, 平衡线程数和递归
  -XX:MetaspaceSize=128m \
  -XX:MaxMetaspaceSize=256m \               # 防 CGLIB 失控
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \                # G1 期望最大停顿
  -XX:+HeapDumpOnOutOfMemoryError \         # OOM 自动 dump
  -XX:HeapDumpPath=/var/log/blog/ \
  -XX:+ExitOnOutOfMemoryError \             # OOM 后退出, 让 k8s 重启
  -Xlog:gc*:file=/var/log/blog/gc.log:t,uptime:filecount=10,filesize=50M \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar blog-api.jar
```

**参数解释 (面试讲法)**:

| 参数 | 为什么 |
|---|---|
| Xms = Xmx | 防扩堆 STW; 容器化环境固定内存上限 |
| 堆 = 总内存 50% | 留 50% 给 Metaspace / Direct / 线程栈 / 系统 |
| MetaspaceSize 上限 | 防 Spring / MyBatis 生成类无限增长 OOM |
| HeapDumpOnOOM | 没 dump 等于盲排查 |
| ExitOnOOM | 容器化下宁可死也别带病运行 |
| `-Xlog:gc*` | JDK 9+ 统一日志, 取代 `-XX:+PrintGCDetails` |

**容器化补充**:
- JDK 10+ 自动识别 cgroup, 不用 `-XX:+UseContainerSupport` (默认开)
- 但显式设 `-Xmx` 仍是最佳实践 (cgroup 默认 1/4 内存)

---

## Q4 排查题: 线上 Java 进程 CPU 100%, 接口 503, 怎么定位?

**5 步排查法 (在白板能写下来)**:

```bash
# 1. 确认 java 进程及 CPU
top -c
# 假设 PID = 12345, CPU 800% (8 核满)

# 2. 找出该进程内 CPU 高的线程
top -H -p 12345
# LWP 列里找几个 > 90% 的, 假设 23456

# 3. 转 16 进制 (jstack 用 nid hex)
printf "%x\n" 23456
# 5ba0

# 4. dump 全部线程栈
jstack 12345 > /tmp/stack.txt
# 或 jstack -F (强制, STW)

# 5. grep nid 找栈
grep -A 30 "nid=0x5ba0" /tmp/stack.txt
```

**根因分类与对策**:

| 栈特征 | 根因 | 修复 |
|---|---|---|
| 业务代码循环 | 死循环 / busy wait | 加 sleep / 用 BlockingQueue 阻塞 |
| `java.util.HashMap.put` 链表死循环 | JDK 7 + 多线程 put | 换 ConcurrentHashMap |
| `GC Thread` | GC 风暴, 大对象 / 内存泄漏 | 看 gc.log + heap dump |
| `RUNNABLE Pattern.compile` | 正则回溯灾难 | 改正则或加超时 |
| `JIT C2 CompilerThread` | 代码触发 JIT 频繁编译 | 一般短暂, 长持续看具体类 |
| 大量 `BLOCKED` | 锁竞争 | 缩小临界区 / 换 ConcurrentHashMap |

**Arthas 一键版** (生产推荐):
```bash
java -jar arthas-boot.jar 12345
> thread -n 5                # 前 5 个 CPU 高的线程栈
> dashboard                  # 实时大屏: GC / 线程 / 内存
> profiler start --duration 60 --file /tmp/p.html   # 火焰图
```

---

## Q5 综合: 一个接口压测时 P99 突然飙到 2 秒, 怎么排查?

**排查矩阵**:

| 时间维度 | 看什么 | 工具 |
|---|---|---|
| **正在发生** | CPU / 线程数 / GC 频率 | top, jstat, jstack |
| **最近 1 小时** | 慢日志 / GC 日志 / 应用日志 | tail gc.log / app.log |
| **历史趋势** | Prometheus QPS / P99 / heap / GC time | Grafana |

**典型可能性 + 验证**:

1. **GC 长停顿**
   ```bash
   jstat -gcutil <pid> 1000      # 看 YGC YGCT FGC FGCT, 列上下波动
   tail -f gc.log | grep Pause   # 看具体停顿时长
   ```
   → 修: 调大堆 / 换 ZGC / 找内存泄漏

2. **数据库慢查询**
   - 看慢日志 `mysql.slow_log`
   - `EXPLAIN` 看是否走索引
   - → 加索引 / 优化 SQL

3. **下游服务慢**
   - Skywalking / Pinpoint / Arthas `trace` 看每段耗时
   ```bash
   > trace com.javastudy.PostService getDetail '#cost > 500'
   ```
   → 加缓存 / 异步化

4. **锁竞争**
   - jstack 看大量 BLOCKED
   → 缩小锁粒度

5. **线程池打满**
   - `executor.queue.size` 接近上限
   → 扩容 / CallerRuns / 限流

6. **Full GC 频率**
   - `jstat -gc <pid> 1000` 看 FGC 列单位时间增量
   → heap dump 找泄漏

**面试 2 分钟讲法**:
> "我会先看 Grafana 三件套: QPS / P99 / 错误率, 确认是单接口慢还是全局慢。 全局慢看 GC (gc.log + jstat), 单接口慢用 Arthas trace 定位到具体方法。 排在前几位的根因: GC 风暴 (内存泄漏)、慢 SQL (没索引)、下游超时、锁竞争、线程池满。 每种都有对应的工具能 10 分钟内定位。"

---

## 通过标准

- [ ] 能默写 JVM 运行时数据区 + 5 种 OOM 表现
- [ ] 能讲 G1 / ZGC / Generational ZGC 区别 + JDK 21 选型
- [ ] 能写完整生产 JVM 启动参数 + 解释每个
- [ ] 能讲完整 CPU 100% 5 步排查
- [ ] 能讲 P99 突增的 6 种排查方向 + 工具
