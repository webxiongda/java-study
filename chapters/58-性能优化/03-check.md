# Chapter 58 性能优化 - 自测与验收

## Q1 概念: 为什么看 P99 不看平均 RT?

**平均的陷阱**:

```
1000 个请求: 990 个 10ms, 10 个 5000ms
平均 = 59.9ms        ← 看起来"还行"
P99 = 5000ms         ← 实际 1% 用户体验崩了
```

**用户感知的是分位线**: 你打开 100 个页面有 1 个卡死, 即使另外 99 个都很快, 你也会觉得这网站慢。

**生产标准**:
- P50 (中位数): 看正常体验
- P95 / P99: 看异常用户的体验 (尾延迟)
- P999: 关键金融 / 实时场景才看

**注意 P99 比平均高 10-100x 是正常的** (长尾分布)。 但如果差 1000x, 通常是: GC pause / 慢 SQL / 锁竞争 / 网络抖动。

---

## Q2 概念: EXPLAIN 4 个关键列 + 索引失效 5 种场景

**4 个列**:

| 列 | 健康值 | 异常 |
|---|---|---|
| `type` | const / eq_ref / ref / range | **ALL (全表扫)** / index (索引全扫) |
| `key` | 实际命中的索引名 | NULL = 没用索引 |
| `rows` | 越小越好 (估算扫描行数) | 上万 = 需要优化 |
| `Extra` | Using index (覆盖索引) | Using filesort (排序无索引) / Using temporary |

**索引失效 5 种**:

```sql
INDEX idx_name (name)

-- 1. 函数包裹
WHERE LOWER(name) = 'tom'              ✗ 失效
WHERE name = LOWER('Tom')              ✓

-- 2. 隐式类型转换 (name 是 varchar)
WHERE name = 123                       ✗ 失效 (MySQL 把 name 转 int)

-- 3. 前导通配符
WHERE name LIKE '%tom'                 ✗ 失效
WHERE name LIKE 'tom%'                 ✓

-- 4. 运算
WHERE id + 1 = 100                     ✗ 失效

-- 5. OR 任意一边没索引
WHERE name = 'tom' OR age = 18         ✗ (age 没索引则整体失效)
```

**最左前缀**: `INDEX (a, b, c)` 可被 `WHERE a` / `WHERE a, b` / `WHERE a, b, c` 命中, 但 `WHERE b` 或 `WHERE b, c` 不命中。

---

## Q3 实操题: 这段代码慢, 找出问题并优化

```java
@GetMapping("/api/posts")
public List<PostVO> list(@RequestParam(required=false) String keyword) {
    List<Post> posts = postRepo.findAll();    // (1)
    return posts.stream()
        .filter(p -> keyword == null || p.getTitle().contains(keyword))  // (2)
        .sorted(Comparator.comparing(Post::getCreatedAt).reversed())     // (3)
        .map(p -> {
            User author = userRepo.findById(p.getAuthorId()).orElseThrow();  // (4)
            Long commentCount = commentRepo.countByPostId(p.getId());        // (5)
            return new PostVO(p, author, commentCount);
        })
        .limit(20)        // (6)
        .toList();
}
```

**问题清单** (8 个):

| # | 问题 | 后果 | 优化 |
|---|---|---|---|
| 1 | `findAll()` 全表查 | 100 万行 → OOM | `Pageable` 分页 + WHERE 过滤 |
| 2 | 关键字 Java 端过滤 | 网络 + JVM 处理大量无效数据 | 改 `WHERE title LIKE '%kw%'` (或 ES 全文索引) |
| 3 | Java 端排序 | 大集合排序慢 | `ORDER BY created_at DESC` 走索引 |
| 4 | N+1 查 user | N 次 SQL | `IN (...)` 批量查 + Map |
| 5 | N+1 查 commentCount | N 次 SQL | `LEFT JOIN ... GROUP BY` 或单条 SQL 返聚合 |
| 6 | `limit(20)` 在 N+1 之后 | 已经查了 N 次, 白干 | 先 limit 再 enrich |
| 7 | 无缓存 | 热点反复查 DB | `@Cacheable` |
| 8 | 无 readOnly 事务 | 走写事务有开销 | `@Transactional(readOnly = true)` |

**优化后**:

```java
@Transactional(readOnly = true)
@Cacheable(value="post:list", key="#kw + ':' + #page", condition="#kw != null and #kw.length() >= 2")
public List<PostVO> list(String kw, int page) {
    var pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending());
    Page<Post> posts = (kw == null)
        ? postRepo.findAll(pageable)
        : postRepo.findByTitleContaining(kw, pageable);

    if (posts.isEmpty()) return List.of();

    Set<Long> userIds = posts.stream().map(Post::getAuthorId).collect(toSet());
    List<Long> postIds = posts.stream().map(Post::getId).toList();

    Map<Long, User> userMap = userRepo.findAllById(userIds).stream()
        .collect(toMap(User::getId, u -> u));
    Map<Long, Long> countMap = commentRepo.countByPostIdIn(postIds);   // 单 SQL 聚合

    return posts.stream()
        .map(p -> new PostVO(p, userMap.get(p.getAuthorId()), countMap.getOrDefault(p.getId(), 0L)))
        .toList();
}
```

配合:
- `CREATE INDEX idx_title ON posts(title);` 或全文索引
- `CREATE INDEX idx_post_id ON comments(post_id);`

---

## Q4 实操题: 怎么定位线上接口偶发慢?

**症状**: P50 30ms, P999 5000ms, 偶发, 复现不了。

**排查 7 步**:

```
1. 看监控趋势 (Grafana)
   - 慢的时候 CPU / 内存 / GC / Redis / DB / 网络 哪个异常?
   - 关联其他服务: 上游 / 下游同时慢吗?

2. 看 GC 日志
   - 慢的时刻是不是有 Full GC / Mixed GC 长停顿?
   - jstat -gc <pid> 1000
   - JFR 录制 5 分钟

3. 看慢 SQL 日志
   - tail -f slow.log | grep <慢时刻>
   - 是不是某条 SQL 在某些数据条件下走错索引?
     (如 WHERE status IN (...) 选择率太低)

4. 看应用日志
   - 慢请求是否打了 traceId? 关联前后所有日志
   - Sleuth / Skywalking 看分布式 trace

5. 看锁
   - jstack <pid>, 是不是 BLOCKED 在某锁上?
   - DB 死锁日志: SHOW ENGINE INNODB STATUS

6. 看资源饱和
   - top: CPU steal? (虚拟化资源被抢)
   - iostat: 磁盘 await?
   - 网络: tcpdump 看 retransmit?

7. 看下游
   - Redis 慢日志: SLOWLOG GET 10
   - 第三方 API: 是不是它偶发慢?
```

**常见根因**:
- **GC 停顿** (Old 区涨满, Mixed GC 200ms+)
- **慢 SQL** (某些参数下走错索引, 全表扫)
- **网络抖动** (跨可用区, DNS 抖动)
- **锁等待** (悲观锁排队)
- **缓存击穿** (某热 key 失效后大量打到 DB)
- **CPU 被抢** (Docker / k8s noisy neighbor)

---

## Q5 综合: 接口要从 QPS 100 提到 1000, 怎么做?

**分析路径 (一个 30 分钟讨论的框架)**:

```
1. 测当前瓶颈在哪
   wrk 压测 → 看 CPU/Mem/DB/Redis/网络饱和

2. 按瓶颈分类处理

   瓶颈 = CPU
   - 火焰图找热点 (Jackson? BCrypt? 反射?)
   - 缓存计算结果 (Caffeine)
   - 算法优化 (O(n²) → O(n log n))
   - 升级 CPU (临时方案)

   瓶颈 = DB
   - 慢 SQL 加索引
   - 加 Redis 缓存挡读 (90% 流量进缓存)
   - 读写分离 (主写, 从读)
   - 分库分表 (终极方案)

   瓶颈 = 网络
   - 合并请求 (1 个接口取多个数据)
   - 减少响应体大小 (Gzip / 字段裁剪 / Protobuf)
   - HTTP/2 多路复用

   瓶颈 = 线程
   - 加大线程池 (但要监控队列)
   - 异步化 (CompletableFuture / 虚拟线程)
   - 把同步 IO 改异步

3. 横向扩
   - 应用无状态 → k8s scale 10 实例
   - 配合负载均衡 (Nginx / k8s Service)
   - 监控自动扩缩 (HPA)

4. 验证收益
   - 同样压测脚本对比 P99 / QPS / 错误率
   - 灰度上线 (1% → 10% → 100%)
```

**面试讲法 (90 秒)**:

> "QPS 10x 不是一招的事, 是体系工程。 我会先压测定位瓶颈, 是 CPU 还是 DB。 假设是 DB, 第一步把热路径加 Redis 缓存挡掉 80% 读, P99 立刻降一档; 第二步给慢 SQL 加索引, 看 EXPLAIN 确认走对; 第三步读写分离, 读流量分给 3 个从库。 应用层如果 CPU 是瓶颈就横向扩 + 加 Caffeine 本地缓存。 最后所有改动一一压测验证, 灰度上线。 我之前博客 detail 接口就是这么从 QPS 142 提到 9200 的, 主要靠加索引 + N+1 优化 + 双层缓存 + 并行化。"

---

## 通过标准

- [ ] 能讲清 P50/P95/P99 区别 + 为什么不看平均
- [ ] 能解释 EXPLAIN 的 type/key/rows/Extra 含义
- [ ] 能识别索引失效的 5 种场景
- [ ] 能给定一段慢代码, 找出 N+1 / 全表扫 / 缓存缺失等问题
- [ ] 能讲偶发慢的 7 步排查流程
- [ ] 有一次完整的"压测 → 改 → 重压测"经历
