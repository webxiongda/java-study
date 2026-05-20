# 面试题库 Interview Bank

> 各章节 `03-check.md` 末尾的「面试高频题」汇总于此。L1 必须答得出，L2 项目相关，L3 加分题。
> 标注：⭐ 高频；⭐⭐ 必考；🔥 大厂常考追问。

---

## Java 基础（章节 02-09）

### ⭐⭐ `==` 和 `equals` 的区别？
- `==`：基本类型比值，引用类型比地址。
- `equals`：`Object` 默认就是 `==`；`String` / 包装类重写为值比较。
- 🔥 追问：重写 `equals` 必须重写 `hashCode` 吗？为什么？（HashMap 契约）

### ⭐⭐ String / StringBuilder / StringBuffer 选哪个？
- 不变 → `String`；单线程拼接 → `StringBuilder`；多线程 → `StringBuffer`。
- 🔥 追问：`String s = "a" + "b" + i;` 编译后是几次拼接？（编译器优化为 StringBuilder）

### ⭐ checked vs unchecked，业务异常该选谁？
- 业务异常用 `RuntimeException` 子类，避免污染方法签名 + 配合全局异常处理统一返回。

### ⭐⭐ 多态的运行时绑定怎么实现的？
- JVM 通过对象头的类型指针 → klass 元数据 → vtable 索引调用实际方法（`invokevirtual` 字节码）。

### ⭐ 泛型擦除是什么？带来什么坑？
- 编译期检查，运行时擦除为 `Object`（或边界）。 坑：`List<String>` 和 `List<Integer>` 运行时同一个 Class；不能 `new T()`；不能用基本类型；`instanceof T` 不行。

---

## 集合（章节 11-12）

### ⭐⭐⭐ HashMap 底层结构？JDK 8 改了什么？
- 数组 + 链表 + 红黑树（链表长度 ≥ 8 且数组 ≥ 64 时树化，< 6 退化）。
- JDK 8 头插改尾插（解决并发扩容成环）。
- 🔥 追问：负载因子为什么是 0.75？初始容量为什么是 16？扩容机制？

### ⭐⭐ HashMap 为什么线程不安全？怎么选并发 Map？
- 并发 put 可能丢数据 / 扩容死循环（JDK 7）。
- 用 `ConcurrentHashMap`（分段锁 → JDK 8 改 CAS + synchronized 桶级别锁）。

### ⭐ ArrayList 扩容机制？为什么是 1.5 倍？
- `oldCapacity + (oldCapacity >> 1)`，权衡内存浪费 vs 拷贝次数。

### ⭐ ConcurrentHashMap 在 JDK 8 后怎么实现的？
- 取消 Segment，桶级 CAS（首节点空时）+ synchronized（非空时），扩容支持多线程协助。

---

## 并发（章节 51-53）

### ⭐⭐⭐ synchronized 与 ReentrantLock 区别？
- 关键字 vs API；自动释放 vs 手动 `unlock` 在 finally；不可中断 vs 可中断；非公平 vs 可选公平；不可超时 vs `tryLock(timeout)`；条件变量 `Condition` 灵活。

### ⭐⭐⭐ volatile 解决什么问题？为什么不能保证原子性？
- 可见性（写立即刷主存 + 失效其他线程缓存）+ 禁止指令重排（内存屏障）。
- 不保证原子性：`i++` 是读改写三步。

### ⭐⭐⭐ 线程池 7 个核心参数？拒绝策略有哪些？
- `corePoolSize / maximumPoolSize / keepAliveTime / unit / workQueue / threadFactory / handler`。
- 拒绝策略：`Abort`（默认抛异常）/ `CallerRuns`（调用线程跑）/ `Discard`（丢弃）/ `DiscardOldest`（丢最老）。
- 🔥 追问：CPU 密集和 IO 密集线程数怎么定？（CPU 核数 / 2*核数）

### ⭐⭐ 虚拟线程（JDK 21）和平台线程区别？什么场景用？
- 虚拟线程由 JVM 调度，挂起代价极低，单 JVM 百万级。
- IO 密集（DB、HTTP 调用）首选；CPU 密集没收益。
- 注意 `synchronized` 仍会 pin 平台线程（JDK 21 仍有，JDK 24+ 改善）。

### ⭐⭐ ThreadLocal 为什么会内存泄漏？
- `ThreadLocalMap` 的 key 是弱引用，value 是强引用，线程不结束 value 不回收。 用完 `remove()`。

---

## JVM（章节 54）

### ⭐⭐⭐ JVM 内存区域？OOM 可能发生在哪些区？
- 堆 / 栈 / 方法区（Metaspace）/ 程序计数器 / 本地方法栈。
- OOM：堆（最常见）/ Metaspace（动态类多）/ 栈（递归过深 `StackOverflowError`）/ 直接内存（NIO）。

### ⭐⭐ 类加载过程？双亲委派？
- 加载 → 验证 → 准备 → 解析 → 初始化。
- 子加载器先委托父加载器，避免核心类被覆盖。

### ⭐⭐ JDK 21 默认 GC 是什么？ZGC 适合什么场景？
- 默认 G1。 ZGC 适合大堆 + 低延迟（亚毫秒停顿）。

### ⭐ 如何排查线上 CPU 100% / OOM？
- `top -Hp pid` 找线程 → `printf "%x\n" tid` 转 16 进制 → `jstack pid | grep tid`。
- OOM：`-XX:+HeapDumpOnOutOfMemoryError` + MAT 分析 hprof。

---

## Spring / Spring Boot（章节 31-39）

### ⭐⭐⭐ Spring Bean 生命周期？
见 `[[glossary#Bean 生命周期]]`。

### ⭐⭐ `@Transactional` 什么情况下会失效？
1. 同类自调用（绕过代理）；2. 非 public 方法（默认）；3. 抛 checked 异常未配 `rollbackFor`；4. 异常被 catch 吞掉；5. 数据库引擎不支持事务（MyISAM）；6. 多数据源未配事务管理器。

### ⭐⭐ Spring Boot 自动配置原理？
`@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 读 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x 后）→ 按 `@ConditionalOn*` 筛选。

### ⭐ Spring 循环依赖怎么解？
三级缓存：`singletonObjects` / `earlySingletonObjects` / `singletonFactories`。 构造器注入循环 + `@Async` Bean 仍会失败。

---

## 数据库 / MyBatis（章节 24-29）

### ⭐⭐⭐ 索引失效场景？
1. 函数 / 表达式包裹列；2. 隐式类型转换；3. `LIKE '%xxx'` 前模糊；4. `OR` 一边没索引；5. 不符合最左前缀；6. 优化器估算全表更快。

### ⭐⭐ 事务隔离级别 + MySQL 默认？
读未提交 / 读已提交 / 可重复读（MySQL 默认）/ 串行化。 解决：脏读 / 不可重复读 / 幻读。 MySQL RR 通过 MVCC + Next-Key Lock 防大部分幻读。

### ⭐⭐ MyBatis `#{}` 和 `${}` 区别？
`#{}` 预编译参数（安全）；`${}` 字符串拼接（用于动态表名 / 列名，否则 SQL 注入）。

### ⭐ N+1 怎么解？
`<resultMap>` 嵌套 join 一次查；或 `<collection>` + 批量 `IN`。

---

## Redis（章节 46-47）

### ⭐⭐⭐ 缓存穿透 / 击穿 / 雪崩区别 + 解法？
见 `[[glossary#缓存三大问题]]`。

### ⭐⭐ 分布式锁怎么实现？Redisson 比手写 `SET NX` 好在哪？
- 手写：`SET key uuid NX PX 30000` + Lua 删除（验 value）。
- Redisson：看门狗自动续期、可重入、读写锁、公平锁、Redlock 多节点。

### ⭐ Redis 数据持久化？
RDB（快照）+ AOF（命令日志，`appendfsync everysec` 平衡性能与丢数据风险）。

---

## 认证 / 安全（章节 42-45）

### ⭐⭐⭐ JWT 怎么设计 Refresh 机制？
- Access Token 短期（15min）+ Refresh Token 长期（7d，存 DB/Redis）；Access 过期用 Refresh 换；登出 / 改密码 → Refresh 进黑名单。

### ⭐⭐ Spring Security 6.x 配置和 5.x 差异？
5.x 继承 `WebSecurityConfigurerAdapter`；6.x 直接 `@Bean SecurityFilterChain` + Lambda DSL，更显式，方便组合多 FilterChain。

### ⭐⭐ CSRF 什么场景需要开？
基于 Cookie/Session 的鉴权必须开。 纯 Header（JWT/`Authorization`）鉴权可关。

---

## 系统设计（章节 59）

### ⭐⭐ 设计一个限流？
- 计数器（最简，临界尖刺）/ 滑动窗口（精准）/ 漏桶（恒定速率）/ 令牌桶（允许突发，推荐）。
- 单机：`Bucket4j`；分布式：Redis + Lua 脚本原子扣减。

### ⭐⭐ 设计一个短链系统？
- 发号器（雪花算法 / 数据库号段）→ Base62 编码 → 短码。
- 读多写少：长链查 Redis 命中直接 302；miss 查 DB 回填。
- 容量估算：日活 × 短链率 × 365 × 5 年 = 总条数。

### ⭐ 设计一个排行榜？
Redis `ZSET`（score = 分数 + 时间反转作 tie-break）；分片榜：哈希分桶 + 定时合并。

---

## 使用建议

- 每周从中抽 5-10 题做「2 分钟口头答」演练。
- 答不出来的题 → 抄到 `[[mistakes]]`，加入 `[[review-plan]]` 间隔复习。
- 项目章节（30 / 40 / 50）做完后，把项目能讲清的题目打 ✅。
