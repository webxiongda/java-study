# Chapter 29 MyBatis 进阶 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：MyBatis 的 N+1 问题是什么？请用博客场景说明 3 种修法，并对比利弊。

**参考答案：**

**N+1 现象**：取 1 张列表（N 行）→ 每行再发 1 条 SQL 取关联数据 → 共 1 + N 条 SQL。

博客示例：列表展示「文章 + 作者昵称」

```xml
<!-- 错：嵌套 select → N+1 -->
<resultMap id="m" type="Post">
    <id column="id" property="id"/>
    <association property="author"
                 select="UserMapper.findById" column="user_id"/>
</resultMap>
<select id="list" resultMap="m">
    SELECT * FROM post WHERE status=1 LIMIT 100
</select>
<!-- 实际发出：1 条 list + 100 条 findById -->
```

### 修法 1：嵌套结果映射（一次 JOIN）

```xml
<resultMap id="m" type="Post">
    <id column="id" property="id"/>
    <association property="author" javaType="User">
        <id column="u_id" property="id"/>
        <result column="nickname" property="nickname"/>
    </association>
</resultMap>
<select id="list" resultMap="m">
    SELECT p.*, u.id AS u_id, u.nickname
    FROM post p LEFT JOIN user u ON u.id = p.user_id
    WHERE p.status=1 LIMIT 100
</select>
```

- 优：1 条 SQL 搞定
- 缺：返回字段重复（每行带 user 字段），JOIN 大表慎用

### 修法 2：业务层批量 IN

```java
List<Post> posts = postMapper.list();
Set<Long> uids = posts.stream().map(Post::getUserId).collect(toSet());
Map<Long,User> userMap = userMapper.findByIds(uids).stream()
    .collect(toMap(User::getId, u->u));
posts.forEach(p -> p.setAuthor(userMap.get(p.getUserId())));
```

- 优：2 条 SQL；user 表压力小
- 缺：业务侧多写代码

### 修法 3：反范式冗余

直接在 `post` 表加 `user_name` 列，写入文章时一并写入。

```xml
<select id="list" resultType="Post">
    SELECT id, title, user_id, user_name FROM post WHERE status=1 LIMIT 100
</select>
```

- 优：1 条 SQL、最快
- 缺：用户改昵称要同步（MQ / 定时 / 触发器）

| 方案 | SQL 数 | 数据新鲜度 | 写代价 | 适合 |
|------|-------|----------|-------|------|
| 嵌套结果映射 | 1 | 实时 | 0 | 中小表 |
| 业务层批量 IN | 2 | 实时 | 0 | 大列表、关联多对一 |
| 反范式冗余 | 1 | 最终一致 | 写时同步 | 超高频读、字段稳定 |

**关键点**：N+1 不是 MyBatis 的锅，是写法的锅。`<association select=>` 嵌套查询要警惕。

---

## Q2（概念）：MyBatis 的二级缓存默认关闭，开了为什么生产慎用？请举具体反例。

**参考答案：**

**机制**：二级缓存以 namespace 为单位、跨 SqlSession 缓存查询结果。命中条件：同 namespace + 同 statementId + 同参数。

**为什么慎用**：

### 反例 1：跨表更新感知不到

```xml
<!-- UserMapper.xml -->
<cache/>
<select id="findById" resultType="User">SELECT * FROM user WHERE id=#{id}</select>

<!-- OrderMapper.xml -->
<update id="setUserVip">UPDATE user SET vip=1 WHERE id=#{id}</update>
```

`UserMapper.findById(1)` 缓存 vip=0 → `OrderMapper.setUserVip(1)` 改成 1 → 再 `UserMapper.findById(1)` 仍返回 vip=0（**OrderMapper namespace 不会刷 UserMapper 缓存**）。

### 反例 2：集群环境本地缓存不一致

A 节点缓存 user=Alice → B 节点改成 Bob → A 仍返回 Alice 直到 60s 过期。

### 反例 3：缓存对象被业务层修改

`readOnly=false`（默认）时 MyBatis 返回的是反序列化后的实例，业务里 `user.setName("X")` 会改到缓存里 → 别人下一次拿到脏数据。

### 反例 4：分页查询 `count + list` 缓存粒度不一致

count 缓存 1000，list 因参数不同各自缓存，写入新数据后 count 滞后。

### 推荐做法

- **关掉 MyBatis 二级缓存**：`mybatis.configuration.cache-enabled=false`。
- 业务层用 **Spring Cache + Redis**：精确控制 key、过期、失效（写时主动 `@CacheEvict`）。
- 实在要用，开 `readOnly=true` 防止业务侧改对象，且只缓存"低频写"namespace。

**关键点**：缓存的复杂度永远在「失效」上，不在「命中」上。把缓存放到能精确控制失效的位置（业务 / Redis），别留在 ORM 框架里。

---

## Q3（实操）：以下动态 SQL 有 5 处坑，找出并改正。

```xml
<select id="search" resultType="Post">
    SELECT * FROM post WHERE 1=1
    <if test="kw != ''">
        AND title LIKE '%${kw}%'
    </if>
    <if test="status != ''">
        AND status = #{status}
    </if>
    <if test="tagIds != null">
        AND id IN
        <foreach collection="tagIds" item="t" separator=",">
            #{t}
        </foreach>
    </if>
    <if test="endDate != null">
        AND created_at < #{endDate}
    </if>
    ORDER BY ${sort}
</select>
```

**参考答案（改后版本）：**

```xml
<select id="search" resultType="com.example.entity.Post">
    SELECT id, user_id, title, status, created_at FROM post
    <where>                                                   <!-- ① 用 <where> 代替 1=1 -->
        is_deleted = 0
        <if test="kw != null and kw != ''">                  <!-- ② 字符串先判 null -->
            AND title LIKE CONCAT('%', #{kw}, '%')           <!-- ③ ${kw} 注入风险 → #{} + CONCAT -->
        </if>
        <if test="status != null">                            <!-- ④ status=0 是合法值，不能 !='' -->
            AND status = #{status}
        </if>
        <if test="tagIds != null and tagIds.size() > 0">     <!-- ⑤ 空集合会渲染 IN ()，SQL 错 -->
            AND id IN
            <foreach collection="tagIds" item="t" open="(" close=")" separator=",">
                #{t}                                          <!-- ⑥ foreach 缺 open/close 括号 -->
            </foreach>
        </if>
        <if test="endDate != null">
            AND created_at &lt; #{endDate}                    <!-- ⑦ < 必须转义 -->
        </if>
    </where>
    <if test="sort != null">
        ORDER BY ${sort}                                      <!-- ⑧ 必须接白名单校验 -->
    </if>
</select>
```

Java 侧：

```java
private static final Set<String> ALLOWED_SORT =
    Set.of("id DESC", "created_at DESC", "view_count DESC");

public List<Post> search(SearchReq req) {
    if (req.getSort() != null && !ALLOWED_SORT.contains(req.getSort())) {
        throw new IllegalArgumentException("invalid sort");
    }
    return postMapper.search(req);
}
```

**5（+）处坑汇总**：

1. `WHERE 1=1` 是历史包袱，应改 `<where>`。
2. `kw != ''` 漏判 null（OGNL 中 `null != ''` 为 true，会进入分支并 NPE）。
3. `'%${kw}%'` SQL 注入。
4. `status != ''` 把合法 0 排除掉。
5. 空 `tagIds` 渲染出 `IN ()` 语法错。
6. `<foreach>` 缺 open/close 括号。
7. `<` 没转义。
8. `ORDER BY ${sort}` 必须白名单校验。

---

## Q4（实操）：写一个自定义 MyBatis 插件，拦截所有 SELECT，当执行时间 > 500ms 时打 warn 日志，并把 SQL + 参数 + 耗时 + 当前 traceId 一并输出。

**参考答案：**

```java
@Slf4j
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query",
               args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update",
               args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final long THRESHOLD_MS = 500;

    @Override
    public Object intercept(Invocation inv) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return inv.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > THRESHOLD_MS) {
                StatementHandler sh = (StatementHandler) inv.getTarget();
                BoundSql boundSql = sh.getBoundSql();
                String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
                Object params = boundSql.getParameterObject();
                String traceId = MDC.get("traceId");

                log.warn("SLOW_SQL traceId={} cost={}ms sql=[{}] params={}",
                         traceId, cost, sql, params);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
```

Spring Boot 自动注册（实现 `Interceptor` 且 `@Component`，starter 会自动加到 Configuration）。手动注册：

```java
@Configuration
public class MyBatisConfig {
    @Bean
    public ConfigurationCustomizer cfg(SlowSqlInterceptor slow) {
        return cfg -> cfg.addInterceptor(slow);
    }
}
```

**关键点**：

1. `@Signature` 精确签名 → 只拦截 SELECT/UPDATE 的入口，避免性能损耗。
2. **耗时统计放 try-finally**，异常 SQL 也要打日志。
3. **从 MDC 拿 traceId**：把慢 SQL 和请求链路打通（Chapter 23 已埋）。
4. 真实生产更进一步：累计到 Prometheus → Grafana 告警；或丢到 Kafka 给慢 SQL 治理平台。

**单测**：

```java
@SpringBootTest
class SlowSqlInterceptorTest {
    @Autowired UserMapper m;
    @Autowired Appender mockAppender; // logback 测试用

    @Test void prints_warn_when_slow() {
        // 给 Mapper 加 SLEEP(0.6) 制造慢 SQL
        m.sleepSelect();
        verify(mockAppender).doAppend(argThat(e -> e.getMessage().contains("SLOW_SQL")));
    }
}
```

---

## Q5（综合）：博客上线后发现「评论同步任务」每天跑 1 小时——每 5 秒插入 200 条到 `comment` 表，跑了一晚上。用 MyBatis 把这个性能优化到 5 分钟以内，并解释每一步为什么有效。

**参考答案：**

### 基线版本（差）

```java
@Service @RequiredArgsConstructor
public class CommentSyncBad {
    private final CommentMapper mapper;

    @Transactional
    public void sync(List<Comment> list) {
        for (Comment c : list) mapper.insert(c);    // 一次一往返
    }
}
```

20 万条耗时约 1h（每条 18ms 含网络 + 解析）。

### 第 1 步：单 SQL 多 VALUES

```xml
<insert id="batchInsert">
    INSERT INTO comment(post_id, user_id, parent_id, root_id, content)
    VALUES
    <foreach collection="list" item="c" separator=",">
        (#{c.postId}, #{c.userId}, #{c.parentId}, #{c.rootId}, #{c.content})
    </foreach>
</insert>
```

每次 200 条拼一条 SQL → 1000 次网络往返 → **约 8 min**。

**为什么有效**：减少网络往返 + 让 MySQL 一次解析复用执行计划。

### 第 2 步：开 rewriteBatchedStatements + BATCH executor

```yaml
spring.datasource.url: jdbc:mysql://...?rewriteBatchedStatements=true
```

```java
try (SqlSession s = factory.openSession(ExecutorType.BATCH)) {
    CommentMapper m = s.getMapper(CommentMapper.class);
    for (Comment c : list) m.insert(c);
    s.commit();
}
```

驱动会把 N 个 `INSERT` 合并成 `INSERT ... VALUES (..),(..),...` 一次发 → **约 4 min**。

### 第 3 步：分批 + 单独事务

```java
public void sync(List<Comment> all) {
    Lists.partition(all, 1000).forEach(this::batchInsertInTx);
}
@Transactional
public void batchInsertInTx(List<Comment> batch) { mapper.batchInsert(batch); }
```

**为什么**：

- 一个大事务 → undo log 暴涨、回滚慢、阻塞别人。
- 1000/批 → 单条 SQL 长度 < max_allowed_packet（默认 4 MB）。

### 第 4 步：去掉无关索引（写期）

如果是冷启动一次性灌历史数据：

```sql
ALTER TABLE comment DROP INDEX idx_user;
-- 灌完
ALTER TABLE comment ADD INDEX idx_user(user_id);
```

每多一个二级索引，每次 INSERT 多一次 B+ 树维护。临时去掉，全量灌完后再加 → **约 3 min**。

### 第 5 步：并发分片

按 post_id % N 拆 N 个分片，N 个线程各自 BATCH 插入 → **约 1 min**（受 MySQL 写 IOPS 限制）。

### 性能总览

| 版本 | 耗时 | 提速 |
|------|------|------|
| 单条 insert | 60 min | 1x |
| `<foreach>` | 8 min | 7.5x |
| + rewriteBatched | 4 min | 15x |
| + 分批事务 | 3.5 min | 17x |
| + 临时去索引 | 3 min | 20x |
| + 并发分片 4 线程 | 1 min | 60x |

### 真实生产对照

- 美团评论灌历史用 `LOAD DATA INFILE` 直接吃文件，单线程 100 万行 < 30 秒（绕过 SQL 层）。
- 高并发实时写入：MQ 削峰 + 批量消费 + ack 后落库，避免短时间冲击。

**关键点**：

1. **批量 = 减少网络往返 + 减少 SQL 解析**，是数量级的优化。
2. **事务粒度** 决定 undo 大小，分批提交是惯用手法。
3. **写期临时去索引** 适用于一次性灌数据，不适合在线服务。
4. **并发分片** 永远要看下游写 IOPS 上限，再多线程也救不了 IO 瓶颈。
