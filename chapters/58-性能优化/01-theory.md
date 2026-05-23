# Chapter 58 性能优化 - 理论篇

## 一、学习定位

性能优化 = **测量 → 定位 → 改 → 再测量**。 凭感觉优化是最大的反模式。 本章给一套从指标到工具的完整方法论。

- 优先级: L1 (高级岗必考)
- 投入: 5 小时
- 产出: 博客 API 一次完整压测 + 优化 + 报告

## 二、核心指标

### 1. 4 个黄金指标 (Google SRE)

| 指标 | 含义 | 工具 |
|---|---|---|
| **Latency** | 请求耗时 (P50/P95/P99) | 看 P99, 不看平均 |
| **Traffic** | 请求量 QPS | Prometheus `http_requests_total` |
| **Errors** | 错误率 | 5xx / total |
| **Saturation** | 系统饱和度 (CPU/内存/连接池) | Grafana 看趋势 |

### 2. 为什么看 P99 不看平均

```
1000 个请求: 990 个 10ms, 10 个 5000ms
平均 = (990*10 + 10*5000) / 1000 = 59.9ms     ← "还行"
P99 = 5000ms                                    ← 1% 用户体验崩了
```

**用户感知的是分位线, 不是平均值。** 永远盯 P99 / P999。

### 3. 性能预算

```
端到端 200ms = 网关 10ms + Service 100ms + DB 50ms + 缓存 5ms + 序列化 35ms
```

每个环节有明确预算, 超预算就报警, 不是等用户投诉。

## 三、压测工具

### 1. wrk (HTTP 压测)

```bash
wrk -t8 -c200 -d60s --latency http://localhost:8080/api/v1/posts/1

# -t 线程数 (≈ CPU 核数)
# -c 并发连接数
# -d 持续时间
# --latency 输出 P99 分布
```

**输出关键**:
```
Latency Distribution
  50%   12.34ms
  90%   45.67ms
  99%  189.34ms       ← 主要看这个
Requests/sec: 8345.21  ← QPS
```

### 2. JMeter (复杂场景)

适合: 登录 → 拿 token → 多个接口串联的场景。 GUI 友好但资源占用大。

### 3. Gatling (代码即压测)

```scala
val scn = scenario("Blog API")
  .exec(http("get post").get("/api/v1/posts/1"))
  .pause(1)
  .exec(http("create comment").post("/api/v1/comments")...)

setUp(scn.inject(rampUsers(1000).during(60)))
```

### 4. JMH (微基准, JVM 内方法压测)

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class StringConcatBench {

    @Benchmark public String plus() {
        String s = "";
        for (int i = 0; i < 100; i++) s += i;
        return s;
    }

    @Benchmark public String builder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append(i);
        return sb.toString();
    }
}
// 跑: mvn package; java -jar target/benchmarks.jar
```

**坑**: 不用 JMH 直接 `System.currentTimeMillis()` 测 → JIT 优化 / Warmup / 死代码消除 都会让结果失真。

## 四、SQL 性能

### 1. 慢查询定位

```sql
-- 1. 开慢查询日志 (MySQL)
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;        -- 100ms 以上算慢
SET GLOBAL slow_query_log_file = '/var/log/mysql/slow.log';

-- 2. 看慢日志
tail -f /var/log/mysql/slow.log

-- 3. 分析工具
mysqldumpslow -s t -t 10 /var/log/mysql/slow.log     -- 按耗时排前 10
pt-query-digest /var/log/mysql/slow.log              -- Percona Toolkit (更详细)
```

### 2. EXPLAIN 关键列

```sql
EXPLAIN SELECT * FROM posts WHERE author_id = 1 ORDER BY created_at DESC LIMIT 10;
```

| 列 | 关注点 |
|---|---|
| `type` | system > const > eq_ref > ref > range > index > **ALL (全表扫, 危险)** |
| `key` | 实际用的索引, NULL = 没用索引 |
| `rows` | 扫描行数, 越小越好 |
| `Extra` | Using filesort (排序无索引), Using temporary (临时表), Using index (覆盖索引, 好) |

### 3. 索引设计原则

**最左前缀**:
```sql
INDEX idx_a_b_c (a, b, c)
WHERE a=1               ✓ 用到 (a)
WHERE a=1 AND b=2       ✓ 用到 (a, b)
WHERE a=1 AND c=3       ✓ 只用到 (a), c 跳过
WHERE b=2               ✗ 用不到
```

**覆盖索引**: 查询字段都在索引里, 不回表。
```sql
INDEX idx_author_created (author_id, created_at, id, title)
SELECT id, title FROM posts WHERE author_id = 1 ORDER BY created_at DESC;
-- 完全走索引, Extra: Using index
```

**索引失效场景**:
- `WHERE func(col) = x` (函数包裹)
- `WHERE col + 1 = 10` (运算)
- `WHERE col LIKE '%x'` (前导通配符)
- 隐式类型转换 (`col` 是 varchar, `WHERE col = 123`)
- OR 两边没都加索引

### 4. 分页优化

```sql
-- 慢 (后面页越来越慢)
SELECT * FROM posts ORDER BY id LIMIT 1000000, 10;
-- 扫了 100 万行才丢, 只取 10 行

-- 快 (游标分页)
SELECT * FROM posts WHERE id > 1000000 ORDER BY id LIMIT 10;
-- 直接定位, 取 10 行
```

### 5. N+1 问题

```java
// 触发 N+1
List<Post> posts = repo.findAll();           // 1 次查询
posts.forEach(p -> p.getComments().size());  // N 次查询 (每个 post 各一次)

// 解决: JOIN FETCH (JPA) 或一次查全
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments WHERE p.authorId = :uid")
List<Post> findWithComments(Long uid);
```

## 五、JVM 性能工具

### 1. JFR (Java Flight Recorder, JDK 内置)

```bash
# 录 60 秒
jcmd <pid> JFR.start duration=60s filename=app.jfr

# 用 JDK Mission Control 打开 app.jfr
# 看: CPU 热点 / 内存分配热点 / 锁竞争 / GC 行为
```

零成本 (生产可开), 比 jstack / jstat 强大百倍。

### 2. Arthas (阿里在线诊断)

```bash
java -jar arthas-boot.jar <pid>

# 在线看方法耗时
trace com.example.PostService getPost

# 在线改代码 (热更新)
jad PostService; mc Edited.java; redefine PostService.class

# 监控接口
monitor -c 5 com.example.PostController * '#cost > 100'
```

### 3. async-profiler (火焰图)

```bash
./profiler.sh -d 30 -f flame.html <pid>
open flame.html
# 横轴宽 = CPU 占用, 越宽越热
```

## 六、应用层优化套路

### 1. 缓存层级

```
浏览器缓存 (Cache-Control)
   ↓
CDN
   ↓
Nginx 缓存
   ↓
应用本地缓存 (Caffeine, < 1ms)
   ↓
Redis 缓存 (< 5ms)
   ↓
DB (50ms+)
```

每层挡住一部分, 流量逐级稀释。

### 2. 异步化

```java
// 同步: 600ms (3 个 200ms 串行)
A(); B(); C();

// 并行: 200ms
CompletableFuture.allOf(
    CompletableFuture.runAsync(this::A, pool),
    CompletableFuture.runAsync(this::B, pool),
    CompletableFuture.runAsync(this::C, pool)
).join();
```

### 3. 批量化

```java
// 慢 (1000 次单条插入)
items.forEach(repo::save);

// 快 (一次批量)
repo.saveAll(items);
// 或: INSERT INTO ... VALUES (..),(..),(..) 单 SQL
```

### 4. 减少序列化

```
JSON 序列化 ≈ 数据库查询耗时的 30-50%
→ Jackson afterburner module / 改用 Protobuf / 减少响应字段
```

### 5. 连接池调优

```
HikariCP maximumPoolSize 不是越大越好
推荐 = ((核心数 * 2) + 有效硬盘数)
普遍 10-20 够用, 上百反而因竞争变慢
```

## 七、Spring Boot 常见性能坑

| 坑 | 表现 | 处理 |
|---|---|---|
| 没加 `@Transactional(readOnly=true)` | 只读查询走可写事务, 开销大 | 显式加 readOnly |
| `findAll()` 返回上万行 | OOM 或慢 | 分页 / 流式 / `Pageable` |
| `@OneToMany` 默认 EAGER | 一查全表 | 改 LAZY + 按需 fetch |
| Jackson 每次反射 | CPU 高 | 加 afterburner 模块 |
| 日志 `logger.info("xxx" + a + b)` | 字符串拼接即使日志被禁也开销 | 用 `{}` 占位符 |
| `@Async` 没指定 Executor | 用默认 SimpleAsyncTaskExecutor (无线程池) | 自定义 Bean |
| RestTemplate 每次 new | 没连接复用 | 单例 + 连接池 |

## 八、压测 - 优化 - 验证流程

```
1. 基线测试 (压测当前版本, 记 P99 / QPS / 错误率)
2. 看监控 (CPU? Mem? DB? Redis? GC? Lock?)
3. 火焰图 / JFR 找热点
4. 改一处 (一次只改一个变量!)
5. 重压测对比 (要可重复)
6. 写入优化报告: 改前 → 改后 → 收益 → 副作用
```

**一次只改一个变量**: 一起改 5 处, 上线挂了不知道是哪处导致。

## 九、面试高频

1. 你怎么定位接口慢的? (压测 → 监控 → 火焰图 → 日志 → DB)
2. SQL 慢怎么排查? EXPLAIN 关键列?
3. 平均 RT 30ms 但用户说慢, 怎么办? (看 P99)
4. 缓存穿透 / 击穿 / 雪崩各是什么 + 处理? (Ch46)
5. JVM 调优你做过什么? (Ch54)
6. 怎么测一个方法的真实性能? (JMH)
7. 接口 QPS 从 100 提到 1000 你会怎么做?
8. 数据库连接池大小怎么设?
9. N+1 问题怎么避免?
10. 一次性能优化的真实案例?

## 十、Demo / Task

- Demo: 给一个慢接口 (返回 200 + 评论 + 用户), 一步步优化到 P99 20ms
- Task: 博客 API 完整压测 + 优化 + 报告
