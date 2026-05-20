# 术语表 Glossary

> 在各章节文档里出现术语时，用 Obsidian 双链 `[[glossary#术语名]]` 指向此处。新增术语保持「一句话定义 + 出现章节 + 易错点」三段格式。

## Java SE

### Autoboxing / 自动装箱
基本类型与对应包装类的隐式转换。 出现在 `[[02-Java基础语法]]` / `[[11-集合框架上]]`。坑：`Integer i = 1000; Integer j = 1000; i == j` 为 `false`（缓存 -128~127）。

### equals / hashCode 契约
两个对象 `equals` 相等则 `hashCode` 必须相等；反之不一定。 出现在 `[[12-集合框架下]]`。坑：HashMap key 重写 equals 必须同时重写 hashCode。

### PECS（Producer Extends, Consumer Super）
泛型通配符助记：生产用 `? extends T`（只读），消费用 `? super T`（只写）。 出现在 `[[09-枚举与泛型入门]]` / `[[11-集合框架上]]`。

### checked vs unchecked Exception
`Exception`（编译期必须处理） vs `RuntimeException`（不强制）。 出现在 `[[08-异常处理]]`。 业务异常优先继承 `RuntimeException`。

### Records（JDK 14+ 正式）
不可变数据载体 `public record Point(int x, int y) {}`，自动生成 ctor / 访问器 / equals / hashCode / toString。 出现在 `[[05-面向对象入门]]` / DTO 场景。

### Sealed Classes（JDK 17+）
`sealed` + `permits` 限定继承范围，配合 Pattern Matching 实现穷尽分支。

### Virtual Threads（JDK 21）
`Thread.ofVirtual().start(r)` / `Executors.newVirtualThreadPerTaskExecutor()`。 适合 IO 密集；CPU 密集仍用平台线程池。出现在 `[[52-线程池与异步]]`。

## 集合 & 并发

### fail-fast vs fail-safe
迭代时检测结构修改抛 `ConcurrentModificationException`（ArrayList/HashMap） vs 不抛但可能读到旧数据（`CopyOnWriteArrayList`、`ConcurrentHashMap`）。

### Happens-Before
JMM 规则：保证写操作对后续读可见的顺序约定（volatile 写 → volatile 读、unlock → lock 等）。

### CAS / ABA
`Compare-And-Swap` 无锁原子操作；ABA 用 `AtomicStampedReference` 加版本号解决。

## Spring / Web

### jakarta vs javax
Spring Boot 3.x 全面切到 `jakarta.*` 命名空间（`jakarta.servlet`、`jakarta.validation`、`jakarta.persistence`）。 不要再写 `javax.validation.constraints.NotBlank`。

### IoC / DI
控制反转 / 依赖注入。Bean 由容器管理生命周期，依赖通过构造器（推荐）/ setter / 字段注入。 出现在 `[[31-Spring基础]]`。

### Bean 生命周期
实例化 → 属性填充 → `Aware` 回调 → `BeanPostProcessor#before` → `@PostConstruct` / `InitializingBean#afterPropertiesSet` → `BeanPostProcessor#after` → 使用 → `@PreDestroy` / `DisposableBean#destroy`。

### SecurityFilterChain（Security 6.x）
取代过时的 `WebSecurityConfigurerAdapter`，用 `@Bean SecurityFilterChain` + Lambda DSL：`http.authorizeHttpRequests(a -> a.anyRequest().authenticated())`。 出现在 `[[44-SpringSecurity入门]]`。

### CSRF
跨站请求伪造。表单/Cookie 鉴权必须开 CSRF；纯 JWT/Header 鉴权可关闭。

### CORS
跨域资源共享。后端用 `CorsConfigurationSource` Bean + Security 的 `http.cors(Customizer.withDefaults())`。

### `@Transactional` 自调用失效
同类内 `this.foo()` 调本类 `@Transactional bar()`，AOP 代理不生效。解决：注入自己的代理 bean 或拆类。出现在 `[[35-分层架构与事务]]`。

### Propagation 传播级别
`REQUIRED`（默认，有则加入）/ `REQUIRES_NEW`（挂起外层，新开）/ `NESTED`（保存点）/ `SUPPORTS` / `MANDATORY` / `NOT_SUPPORTED` / `NEVER`。

## 数据库 / MyBatis

### N+1 问题
一次主查询 + N 次关联查询。 解决：`<resultMap>` 嵌套 join 或 `<collection select=>` + 批量。

### PreparedStatement vs Statement
前者参数占位 `?`，防 SQL 注入 + 可缓存执行计划；MyBatis 的 `#{}` 走 PreparedStatement，`${}` 是字符串拼接（危险）。

## Redis

### Cache-Aside
读：查缓存 miss → 查 DB → 回填。 写：先写 DB → 删缓存（不要更新缓存，防并发脏写）。

### 缓存三大问题
- **穿透**：查不存在的 key → 空值缓存 + Bloom Filter。
- **击穿**：热点 key 失效瞬间打 DB → 互斥锁 / 永不过期 + 异步刷新。
- **雪崩**：大批 key 同时失效 → 随机过期偏移 + 多级缓存。

### 分布式锁
`SET key val NX PX 30000` + 唯一 value 验证 + Lua 释放，或直接用 Redisson `RLock`（看门狗自动续期、可重入）。

## JVM（JDK 21 视角）

### G1（默认 GC）
分区（Region）+ 部分回收 + 可预测停顿目标 `-XX:MaxGCPauseMillis=200`。

### ZGC
亚毫秒级停顿，可管理 TB 级堆，`-XX:+UseZGC`。 JDK 21 已生产可用（含分代 ZGC `-XX:+ZGenerational`）。

### 内存区域
线程私有：程序计数器、虚拟机栈、本地方法栈。 共享：堆（含新生代/老年代）、方法区（JDK 8+ 实为 Metaspace，使用本地内存）。

### 类加载器双亲委派
Bootstrap → Platform（原 Ext）→ App → 自定义。 打破委派：JDBC `ServiceLoader`、Tomcat WebappClassLoader。

## 工具 / 流程

### STAR 法则
Situation / Task / Action / Result。 简历项目和面试讲述统一用这个结构。
