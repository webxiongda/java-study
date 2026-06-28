# 面试题库 Interview Bank

> 各章节 `03-check.md` 末尾的「面试高频题」汇总于此。L1 必须答得出，L2 项目相关，L3 加分题。
> 标注：⭐ 高频；⭐⭐ 必考；🔥 大厂常考追问。
> 每题前的 `<a id="...">` 锚点供章节 `03-check.md` 的 `**关联**:` 字段反向引用。

---

## Java 基础（章节 02-09）

<a id="eq-vs-equals"></a>
### ⭐⭐ `==` 和 `equals` 的区别？
- `==`：基本类型比值，引用类型比地址。
- `equals`：`Object` 默认就是 `==`；`String` / 包装类重写为值比较。
- 🔥 追问：重写 `equals` 必须重写 `hashCode` 吗？为什么？（HashMap 契约）

<a id="string-builder-buffer"></a>
### ⭐⭐ String / StringBuilder / StringBuffer 选哪个？
- 不变 → `String`；单线程拼接 → `StringBuilder`；多线程 → `StringBuffer`。
- 🔥 追问：`String s = "a" + "b" + i;` 编译后是几次拼接？（编译器优化为 StringBuilder）

<a id="checked-vs-unchecked"></a>
### ⭐ checked vs unchecked，业务异常该选谁？
- 业务异常用 `RuntimeException` 子类，避免污染方法签名 + 配合全局异常处理统一返回。

<a id="polymorphism-dispatch"></a>
### ⭐⭐ 多态的运行时绑定怎么实现的？
- JVM 通过对象头的类型指针 → klass 元数据 → vtable 索引调用实际方法（`invokevirtual` 字节码）。

<a id="generics-erasure"></a>
### ⭐ 泛型擦除是什么？带来什么坑？
- 编译期检查，运行时擦除为 `Object`（或边界）。 坑：`List<String>` 和 `List<Integer>` 运行时同一个 Class；不能 `new T()`；不能用基本类型；`instanceof T` 不行。

---

## 集合（章节 11-12）

<a id="hashmap-bottom-structure"></a>
### ⭐⭐⭐ HashMap 底层结构？JDK 8 改了什么？
- 数组 + 链表 + 红黑树（链表长度 ≥ 8 且数组 ≥ 64 时树化，< 6 退化）。
- JDK 8 头插改尾插（解决并发扩容成环）。
- 🔥 追问：负载因子为什么是 0.75？初始容量为什么是 16？扩容机制？

<a id="hashmap-thread-unsafe"></a>
### ⭐⭐ HashMap 为什么线程不安全？怎么选并发 Map？
- 并发 put 可能丢数据 / 扩容死循环（JDK 7）。
- 用 `ConcurrentHashMap`（分段锁 → JDK 8 改 CAS + synchronized 桶级别锁）。

<a id="arraylist-resize"></a>
### ⭐ ArrayList 扩容机制？为什么是 1.5 倍？
- `oldCapacity + (oldCapacity >> 1)`，权衡内存浪费 vs 拷贝次数。

<a id="chm-jdk8"></a>
### ⭐ ConcurrentHashMap 在 JDK 8 后怎么实现的？
- 取消 Segment，桶级 CAS（首节点空时）+ synchronized（非空时），扩容支持多线程协助。

---

## 并发（章节 51-53）

<a id="sync-vs-reentrantlock"></a>
### ⭐⭐⭐ synchronized 与 ReentrantLock 区别？
- 关键字 vs API；自动释放 vs 手动 `unlock` 在 finally；不可中断 vs 可中断；非公平 vs 可选公平；不可超时 vs `tryLock(timeout)`；条件变量 `Condition` 灵活。

<a id="volatile-semantics"></a>
### ⭐⭐⭐ volatile 解决什么问题？为什么不能保证原子性？
- 可见性（写立即刷主存 + 失效其他线程缓存）+ 禁止指令重排（内存屏障）。
- 不保证原子性：`i++` 是读改写三步。

<a id="threadpool-7-params"></a>
### ⭐⭐⭐ 线程池 7 个核心参数？拒绝策略有哪些？
- `corePoolSize / maximumPoolSize / keepAliveTime / unit / workQueue / threadFactory / handler`。
- 拒绝策略：`Abort`（默认抛异常）/ `CallerRuns`（调用线程跑）/ `Discard`（丢弃）/ `DiscardOldest`（丢最老）。
- 🔥 追问：CPU 密集和 IO 密集线程数怎么定？（CPU 核数 / 2*核数）

<a id="virtual-thread"></a>
### ⭐⭐ 虚拟线程（JDK 21）和平台线程区别？什么场景用？
- 虚拟线程由 JVM 调度，挂起代价极低，单 JVM 百万级。
- IO 密集（DB、HTTP 调用）首选；CPU 密集没收益。
- 注意 `synchronized` 仍会 pin 平台线程（JDK 21 仍有，JDK 24+ 改善）。

<a id="threadlocal-leak"></a>
### ⭐⭐ ThreadLocal 为什么会内存泄漏？
- `ThreadLocalMap` 的 key 是弱引用，value 是强引用，线程不结束 value 不回收。 用完 `remove()`。

---

## JVM（章节 54）

<a id="jvm-memory-oom"></a>
### ⭐⭐⭐ JVM 内存区域？OOM 可能发生在哪些区？
- 堆 / 栈 / 方法区（Metaspace）/ 程序计数器 / 本地方法栈。
- OOM：堆（最常见）/ Metaspace（动态类多）/ 栈（递归过深 `StackOverflowError`）/ 直接内存（NIO）。

<a id="classloader-parent-delegation"></a>
### ⭐⭐ 类加载过程？双亲委派？
- 加载 → 验证 → 准备 → 解析 → 初始化。
- 子加载器先委托父加载器，避免核心类被覆盖。

<a id="jdk21-default-gc"></a>
### ⭐⭐ JDK 21 默认 GC 是什么？ZGC 适合什么场景？
- 默认 G1。 ZGC 适合大堆 + 低延迟（亚毫秒停顿）。

<a id="troubleshoot-cpu-oom"></a>
### ⭐ 如何排查线上 CPU 100% / OOM？
- `top -Hp pid` 找线程 → `printf "%x\n" tid` 转 16 进制 → `jstack pid | grep tid`。
- OOM：`-XX:+HeapDumpOnOutOfMemoryError` + MAT 分析 hprof。

---

## Spring / Spring Boot（章节 31-39）

<a id="bean-lifecycle"></a>
### ⭐⭐⭐ Spring Bean 生命周期？
见 `[[glossary#Bean 生命周期]]`。

<a id="transactional-fail-cases"></a>
### ⭐⭐ `@Transactional` 什么情况下会失效？
1. 同类自调用（绕过代理）；2. 非 public 方法（默认）；3. 抛 checked 异常未配 `rollbackFor`；4. 异常被 catch 吞掉；5. 数据库引擎不支持事务（MyISAM）；6. 多数据源未配事务管理器。

<a id="springboot-autoconfig"></a>
### ⭐⭐ Spring Boot 自动配置原理？
`@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 读 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x 后）→ 按 `@ConditionalOn*` 筛选。

<a id="circular-dependency"></a>
### ⭐ Spring 循环依赖怎么解？
三级缓存：`singletonObjects` / `earlySingletonObjects` / `singletonFactories`。 构造器注入循环 + `@Async` Bean 仍会失败。

---

## 数据库 / MyBatis（章节 24-29）

<a id="index-fail-cases"></a>
### ⭐⭐⭐ 索引失效场景？
1. 函数 / 表达式包裹列；2. 隐式类型转换；3. `LIKE '%xxx'` 前模糊；4. `OR` 一边没索引；5. 不符合最左前缀；6. 优化器估算全表更快。

<a id="transaction-isolation"></a>
### ⭐⭐ 事务隔离级别 + MySQL 默认？
读未提交 / 读已提交 / 可重复读（MySQL 默认）/ 串行化。 解决：脏读 / 不可重复读 / 幻读。 MySQL RR 通过 MVCC + Next-Key Lock 防大部分幻读。

<a id="mybatis-hash-vs-dollar"></a>
### ⭐⭐ MyBatis `#{}` 和 `${}` 区别？
`#{}` 预编译参数（安全）；`${}` 字符串拼接（用于动态表名 / 列名，否则 SQL 注入）。

<a id="n-plus-1"></a>
### ⭐ N+1 怎么解？
`<resultMap>` 嵌套 join 一次查；或 `<collection>` + 批量 `IN`。

---

## Redis（章节 46-47）

<a id="cache-three-problems"></a>
### ⭐⭐⭐ 缓存穿透 / 击穿 / 雪崩区别 + 解法？
见 `[[glossary#缓存三大问题]]`。

<a id="distributed-lock-redisson"></a>
### ⭐⭐ 分布式锁怎么实现？Redisson 比手写 `SET NX` 好在哪？
- 手写：`SET key uuid NX PX 30000` + Lua 删除（验 value）。
- Redisson：看门狗自动续期、可重入、读写锁、公平锁、Redlock 多节点。

<a id="redis-persistence"></a>
### ⭐ Redis 数据持久化？
RDB（快照）+ AOF（命令日志，`appendfsync everysec` 平衡性能与丢数据风险）。

---

## 认证 / 安全（章节 42-45）

<a id="jwt-refresh-design"></a>
### ⭐⭐⭐ JWT 怎么设计 Refresh 机制？
- Access Token 短期（15min）+ Refresh Token 长期（7d，存 DB/Redis）；Access 过期用 Refresh 换；登出 / 改密码 → Refresh 进黑名单。

<a id="spring-security-6-vs-5"></a>
### ⭐⭐ Spring Security 6.x 配置和 5.x 差异？
5.x 继承 `WebSecurityConfigurerAdapter`；6.x 直接 `@Bean SecurityFilterChain` + Lambda DSL，更显式，方便组合多 FilterChain。

<a id="csrf-when-enable"></a>
### ⭐⭐ CSRF 什么场景需要开？
基于 Cookie/Session 的鉴权必须开。 纯 Header（JWT/`Authorization`）鉴权可关。

---

## 系统设计（章节 59）

<a id="design-rate-limiter"></a>
### ⭐⭐ 设计一个限流？
- 计数器（最简，临界尖刺）/ 滑动窗口（精准）/ 漏桶（恒定速率）/ 令牌桶（允许突发，推荐）。
- 单机：`Bucket4j`；分布式：Redis + Lua 脚本原子扣减。

<a id="design-short-url"></a>
### ⭐⭐ 设计一个短链系统？
- 发号器（雪花算法 / 数据库号段）→ Base62 编码 → 短码。
- 读多写少：长链查 Redis 命中直接 302；miss 查 DB 回填。
- 容量估算：日活 × 短链率 × 365 × 5 年 = 总条数。

<a id="design-leaderboard"></a>
### ⭐ 设计一个排行榜？
Redis `ZSET`（score = 分数 + 时间反转作 tie-break）；分片榜：哈希分桶 + 定时合并。

---

## 新增锚点(第二批 ch17/35/44/46/54 章节关联引入)

> 这些条目是 2026-05-25 第二批改造时从章节自测题反向引入,先以一句话占位,后续可扩成完整题目。完整答案请回到对应章节 `03-check.md`。

### Stream / 集合操作(章节 17)

<a id="stream-groupingby-downstream"></a>
### ⭐ Collectors.groupingBy 多级分组 + 下游收集器怎么写?
- 二级: `groupingBy(Order::category, groupingBy(Order::status))`。
- 配下游 `counting()` / `summingInt()` / `mapping()` / `partitioningBy()`。详见 ch17 Q2。

<a id="stream-reduce-vs-collect"></a>
### ⭐⭐ reduce 三参版 vs collect 各适合什么场景?
- `reduce(identity, accumulator, combiner)` 用于纯函数式聚合;`combiner` 仅并行流生效。
- `collect` 用于可变累加器(builder),性能优于反复 new。详见 ch17 Q3。

<a id="stream-toMap-pitfall"></a>
### ⭐⭐ Stream toMap 容易踩哪些坑?
- 重复 key 抛 IllegalStateException → 必须传 `mergeFunction`。
- 默认返回 HashMap, 需 LinkedHashMap 保序时传第四个参数。详见 ch17 Q4。

<a id="stream-parallel-cautions"></a>
### ⭐⭐ parallel stream 什么时候用、什么时候不要用?
- 数据 ≥ 10k + 纯 CPU + 无锁 + 无副作用 4 条件全满足才用。
- 默认共享 ForkJoinPool.commonPool, Web 环境慎用。详见 ch17 Q5。

### Spring / 事务(章节 35)

<a id="layered-architecture-rules"></a>
### ⭐ Controller / Service / Repository 三层职责边界?
- Controller 只做参数校验 + DTO 转换;Service 持事务 + 业务规则;Repository 只 CRUD。
- 三条铁律:Controller 不引 Mapper、Service 不返 Entity、事务在 Service 层。详见 ch35 Q1。

<a id="transaction-propagation"></a>
### ⭐⭐⭐ @Transactional 7 种传播行为分别是?
- REQUIRED(默认)/ REQUIRES_NEW / NESTED / SUPPORTS / NOT_SUPPORTED / MANDATORY / NEVER。
- REQUIRES_NEW 用挂起当前事务的新事务;NESTED 用 savepoint 嵌套。详见 ch35 Q2。

<a id="transactional-pitfalls"></a>
### ⭐⭐ @Transactional 失效的 6 种场景 + 修复?(别名: transactional-fail-cases)
- 同类自调用 / 非 public / 未配 rollbackFor 抛 checked / catch 吞异常 / 多数据源 / 非 Spring Bean。详见 ch35 Q3。

<a id="readonly-transaction"></a>
### ⭐ readOnly = true 有什么收益?
- Hibernate 跳过 dirty check;MyBatis 提示 DataSource 路由到只读从库;long transaction 检测线放宽。详见 ch35 Q4。

<a id="long-transaction"></a>
### ⭐⭐ 长事务有哪些危害?怎么拆?
- 占连接、占行锁、回滚日志大、主从延迟。
- 拆法:循环内单独事务、异步化外发、读用 readOnly。详见 ch35 Q4。

<a id="db-mq-consistency"></a>
### ⭐⭐⭐ DB + MQ 双写一致性怎么做?
- 本地事务表(Outbox)+ 后台投递 + 幂等消费。
- 配 @TransactionalEventListener / NESTED 子事务写 outbox tag。详见 ch35 Q5。

### Spring Security(章节 44)

<a id="spring-security-filter-chain"></a>
### ⭐⭐⭐ Spring Security 过滤器链由哪些核心 Filter 组成?顺序为何重要?
- 15+ 个 Filter 按顺序: SecurityContextHolderFilter → AuthenticationFilter → AuthorizationFilter 等。
- 自定义 Filter 用 addFilterBefore/After 指定位置;顺序错会跳过认证或鉴权。详见 ch44 Q1。

<a id="auth-manager-vs-provider"></a>
### ⭐⭐ AuthenticationManager / ProviderManager / AuthenticationProvider / UserDetailsService 区别?
- Manager 是入口接口;ProviderManager 是默认实现,委托给一组 Provider。
- Provider 处理特定 Token 类型(DaoAuthenticationProvider 用 UserDetailsService 查库)。详见 ch44 Q2。

<a id="security-5x-to-6x-migration"></a>
### ⭐⭐ Spring Security 5.x → 6.x 升级的 6 个破坏性变更?(别名: spring-security-6-vs-5)
- 删 WebSecurityConfigurerAdapter / antMatchers→requestMatchers / authorizeRequests 弃用 / formLogin 链式改 Lambda / NoOpPasswordEncoder 删 / RequestCache 默认行为变更。详见 ch44 Q3。

<a id="401-vs-403"></a>
### ⭐ 401 vs 403 在 Spring Security 里分别由谁处理?
- 401(未认证)→ AuthenticationEntryPoint;403(已认证无权限)→ AccessDeniedHandler。统一返回 JSON 格式。详见 ch44 Q4。

<a id="sms-login-spring-security"></a>
### ⭐⭐ Spring Security 怎么扩展手机验证码登录?
- 自定义 SmsCodeAuthenticationToken + Provider + Filter 三件套,注册到 SecurityFilterChain。详见 ch44 Q4。

<a id="multi-chain-security-design"></a>
### ⭐⭐⭐ 同一应用要支持 Web / App / Partner 三套不同认证规则,怎么设计?
- 多 SecurityFilterChain + securityMatcher 分流 URL + @Order 控制优先级;Partner 走 HMAC,App 走 JWT,Web 走 Session。详见 ch44 Q5。

### Redis / 缓存(章节 46)

<a id="redis-why-fast"></a>
### ⭐⭐⭐ Redis 为什么快?单线程为何不慢?6.0 多线程改了什么?
- 纯内存 + IO 多路复用(epoll)+ 单线程无锁 + 高效结构。
- 6.0 多线程只优化 read/parse/write 网络阶段,命令执行仍单线程。详见 ch46 Q1。

<a id="redis-eviction-policy"></a>
### ⭐⭐ Redis 8 种 maxmemory-policy 怎么选?LRU 是真 LRU 吗?
- 缓存场景一律 allkeys-lru 或 allkeys-lfu;noeviction 默认值生产禁用。
- Redis LRU 是抽样近似(默认采样 5 个),不是真双向链表。详见 ch46 Q2。

<a id="cache-pitfalls"></a>
### ⭐⭐⭐ 缓存穿透 / 击穿 / 雪崩区别 + 防护?(别名: cache-three-problems)
- 穿透: 空对象 / 布隆过滤器;击穿: 互斥锁 / 逻辑过期;雪崩: TTL 抖动 / 多级缓存。详见 ch46 Q3。

<a id="redis-zset-leaderboard"></a>
### ⭐⭐ Redis ZSet 实现排行榜的完整方案?(别名: design-leaderboard)
- key 按时间分桶 + ZINCRBY 计数 + ZREVRANGE Top N + ZUNIONSTORE 跨天合并 + 定时落库。详见 ch46 Q4。

<a id="multi-level-cache"></a>
### ⭐⭐⭐ 突发热点 10w QPS 怎么撑?三级缓存(Caffeine + Redis + DB)方案细节?
- L1 Caffeine 5s TTL 抗单 key 热点;L2 Redis 集群 + 互斥锁防击穿;写时 pub/sub 广播失效 L1。详见 ch46 Q5。

### JVM(章节 54)

<a id="jvm-runtime-area"></a>
### ⭐⭐⭐ JVM 运行时数据区有哪些?5 种 OOM 表现?(别名: jvm-memory-oom)
- 私有: PC / 虚拟机栈 / 本地方法栈;共享: 堆 / Metaspace / 常量池 / DirectMemory。
- OOM: heap / Metaspace / StackOverflow / DirectBuffer / GC overhead。详见 ch54 Q1。

<a id="jvm-gc-selection"></a>
### ⭐⭐⭐ JDK 21 G1 / ZGC / Generational ZGC 怎么选?(别名: jdk21-default-gc)
- ≤ 32G 默认 G1;TB 级 + P99 < 10ms 用 Generational ZGC;不要再讲 CMS。详见 ch54 Q2。

<a id="jvm-prod-params"></a>
### ⭐⭐ 写一份生产级 JVM 启动参数 + 解释为什么?
- Xms=Xmx 防扩堆 / MaxMetaspaceSize 防失控 / HeapDumpOnOOM 必加 / ExitOnOOM 容器化推荐。详见 ch54 Q3。

<a id="cpu-100-debug"></a>
### ⭐⭐⭐ 线上 CPU 100% 五步排查?(别名: troubleshoot-cpu-oom)
- top -Hp pid → printf "%x" tid → jstack > stack.txt → grep nid=0xXXX 找栈。
- Arthas 一键: thread -n 5 / profiler start。详见 ch54 Q4。

<a id="p99-spike-debug"></a>
### ⭐⭐ 接口 P99 突然飙到 2 秒,6 种典型根因 + 验证命令?
- GC 长停顿 / 慢 SQL / 下游慢 / 锁竞争 / 线程池满 / Full GC 频率。每种都有对应 jstat/jstack/Arthas 命令。详见 ch54 Q5。

---

## 使用建议

- 每周从中抽 5-10 题做「2 分钟口头答」演练。
- 答不出来的题 → 抄到 `[[mistakes]]`，加入 `[[review-plan]]` 间隔复习。
- 项目章节（30 / 40 / 50）做完后，把项目能讲清的题目打 ✅。

---

## 反向索引

> 由 `chapters/**/03-check.md` 的 `**关联**:` 字段反向汇总。Task 5 落地后填充。

<!-- AUTO-GENERATED-REVERSE-INDEX:START -->

> 由 8 个试点章节(ch02 / 12 / 17 / 35 / 44 / 46 / 53 / 54)的 `**关联**:` 字段反向汇总,生成于 2026-05-26。

### 集合(章节 11-12)

- [hashmap-bottom-structure](#hashmap-bottom-structure) → ch12 Q1, Q2, Q3
- [hashmap-thread-unsafe](#hashmap-thread-unsafe) → ch12 Q4, ch53 Q3
- [chm-jdk8](#chm-jdk8) → ch12 Q4

### Stream / 函数式(章节 17)

- [stream-groupingby-downstream](#stream-groupingby-downstream) → ch17 Q2
- [stream-reduce-vs-collect](#stream-reduce-vs-collect) → ch17 Q3
- [stream-toMap-pitfall](#stream-toMap-pitfall) → ch17 Q4
- [stream-parallel-cautions](#stream-parallel-cautions) → ch17 Q5

### 并发(章节 51-53)

- [sync-vs-reentrantlock](#sync-vs-reentrantlock) → ch53 Q1
- [volatile-semantics](#volatile-semantics) → ch53 Q2
- [threadlocal-leak](#threadlocal-leak) → ch53 Q3
- [distributed-lock-redisson](#distributed-lock-redisson) → ch53 Q5

### JVM(章节 54)

- [jvm-runtime-area](#jvm-runtime-area) / [jvm-memory-oom](#jvm-memory-oom) → ch54 Q1
- [jvm-gc-selection](#jvm-gc-selection) / [jdk21-default-gc](#jdk21-default-gc) → ch54 Q2
- [jvm-prod-params](#jvm-prod-params) → ch54 Q3
- [cpu-100-debug](#cpu-100-debug) / [troubleshoot-cpu-oom](#troubleshoot-cpu-oom) → ch54 Q4
- [p99-spike-debug](#p99-spike-debug) → ch54 Q5

### Spring / 事务(章节 31-39)

- [layered-architecture-rules](#layered-architecture-rules) → ch35 Q1
- [transaction-propagation](#transaction-propagation) → ch35 Q2
- [transactional-pitfalls](#transactional-pitfalls) / [transactional-fail-cases](#transactional-fail-cases) → ch35 Q3
- [readonly-transaction](#readonly-transaction) → ch35 Q4
- [long-transaction](#long-transaction) → ch35 Q4
- [db-mq-consistency](#db-mq-consistency) → ch35 Q5

### 认证 / 安全(章节 42-45)

- [spring-security-filter-chain](#spring-security-filter-chain) → ch44 Q1
- [auth-manager-vs-provider](#auth-manager-vs-provider) → ch44 Q2
- [security-5x-to-6x-migration](#security-5x-to-6x-migration) / [spring-security-6-vs-5](#spring-security-6-vs-5) → ch44 Q3
- [401-vs-403](#401-vs-403) → ch44 Q4
- [sms-login-spring-security](#sms-login-spring-security) → ch44 Q4
- [multi-chain-security-design](#multi-chain-security-design) → ch44 Q5

### Redis / 缓存(章节 46-47)

- [redis-why-fast](#redis-why-fast) → ch46 Q1
- [redis-eviction-policy](#redis-eviction-policy) → ch46 Q2
- [cache-pitfalls](#cache-pitfalls) / [cache-three-problems](#cache-three-problems) → ch46 Q3
- [redis-zset-leaderboard](#redis-zset-leaderboard) / [design-leaderboard](#design-leaderboard) → ch46 Q4
- [multi-level-cache](#multi-level-cache) → ch46 Q5

### 系统设计(章节 59)

- [design-rate-limiter](#design-rate-limiter) → ch53 Q5

### 暂无章节关联(后续 52 章试点目标)

> 这些 bank 锚点本期 8 章未引用,留给批量改造时认领。

- [eq-vs-equals](#eq-vs-equals)
- [string-builder-buffer](#string-builder-buffer)
- [checked-vs-unchecked](#checked-vs-unchecked)
- [polymorphism-dispatch](#polymorphism-dispatch)
- [generics-erasure](#generics-erasure)
- [arraylist-resize](#arraylist-resize)
- [threadpool-7-params](#threadpool-7-params)
- [virtual-thread](#virtual-thread)
- [classloader-parent-delegation](#classloader-parent-delegation)
- [bean-lifecycle](#bean-lifecycle)
- [springboot-autoconfig](#springboot-autoconfig)
- [circular-dependency](#circular-dependency)
- [index-fail-cases](#index-fail-cases)
- [transaction-isolation](#transaction-isolation)
- [mybatis-hash-vs-dollar](#mybatis-hash-vs-dollar)
- [n-plus-1](#n-plus-1)
- [redis-persistence](#redis-persistence)
- [jwt-refresh-design](#jwt-refresh-design)
- [csrf-when-enable](#csrf-when-enable)
- [design-short-url](#design-short-url)

<!-- AUTO-GENERATED-REVERSE-INDEX:END -->
