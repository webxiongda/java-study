# Chapter 29 MyBatis 进阶 - 理论篇

## 一、学习定位

28 章会"调"MyBatis；29 章学会"用好"——**动态 SQL、关联映射、插件、二级缓存、批量、性能、代码生成**。

- 优先级：L1
- 预计投入：4 小时
- 阶段产出：博客 Mapper 全部用动态 SQL；写一个自定义分页插件

## 二、核心概念

### 1. 动态 SQL 五大标签

| 标签 | 作用 | 例 |
|------|------|----|
| `<if>` | 条件追加 | 可选筛选条件 |
| `<choose><when><otherwise>` | 互斥分支 | switch-case |
| `<where>` | 自动去掉首个 `AND/OR` | 多条件查询 |
| `<set>` | 自动去掉末尾 `,` | 部分字段更新 |
| `<foreach>` | 遍历集合 | `IN (...)` / 批量插入 |

```xml
<select id="search" resultType="Post">
    SELECT * FROM post
    <where>
        is_deleted = 0
        <if test="kw != null and kw != ''">
            AND title LIKE CONCAT('%', #{kw}, '%')
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
        <if test="tagIds != null and tagIds.size() > 0">
            AND id IN (
                SELECT post_id FROM post_tag WHERE tag_id IN
                <foreach collection="tagIds" item="t" open="(" close=")" separator=",">
                    #{t}
                </foreach>
            )
        </if>
    </where>
    ORDER BY id DESC LIMIT #{size}
</select>
```

**陷阱**：

- `<if test="status != null">` —— `status=0` 时也算"有值"；只有 `null` 才跳过。
- OGNL 表达式里 `''` 不等于 `' '`（含空格的字符串）。
- 数字类型的判 0：`<if test="count != null and count != 0">`。

### 2. 关联映射

**一对一（association）**：

```xml
<resultMap id="PostWithAuthor" type="Post">
    <id column="id" property="id"/>
    <result column="title" property="title"/>
    <association property="author" javaType="User">
        <id column="u_id" property="id"/>
        <result column="u_name" property="nickname"/>
    </association>
</resultMap>

<select id="findWithAuthor" resultMap="PostWithAuthor">
    SELECT p.id, p.title, u.id AS u_id, u.nickname AS u_name
    FROM post p JOIN user u ON p.user_id = u.id
    WHERE p.id = #{id}
</select>
```

**一对多（collection）**：

```xml
<resultMap id="PostWithComments" type="Post">
    <id column="p_id" property="id"/>
    <result column="title" property="title"/>
    <collection property="comments" ofType="Comment">
        <id column="c_id" property="id"/>
        <result column="content" property="content"/>
    </collection>
</resultMap>
```

**N+1 反例**：

```xml
<!-- 嵌套查询：取 1 个 post + 100 个 comment 会发出 1 + 100 = 101 条 SQL -->
<association property="author"
             select="com.x.UserMapper.findById" column="user_id"/>
```

**修法**：

1. **嵌套结果映射**（一次 JOIN）—— 上面 `PostWithAuthor` 写法。
2. **批量 IN**：在 Service 层 `findByUserIds(Set<Long>)` 一次取，业务侧手动拼装。

### 3. 二级缓存

```xml
<!-- 在 mapper 头部声明 -->
<cache eviction="LRU" flushInterval="60000" size="1024" readOnly="true"/>
```

**生效条件**：

- 整个 mapper namespace 内开启
- SqlSession `commit` 或 `close` 后才写入二级缓存
- `<select useCache="false">` 可单条关闭

**集群陷阱**：本地内存缓存，A 节点改了 B 节点看不到 → 用 Redis 后端：

```xml
<cache type="org.mybatis.caches.redis.RedisCache"/>
```

更建议：**关掉 MyBatis 二级缓存，业务层用 Spring Cache + Redis**，可控性更好。

### 4. 插件（Interceptor）

MyBatis 允许拦截 4 个接口：

| 接口 | 拦截点 | 典型用途 |
|-----|-------|---------|
| `Executor` | 增删改查执行 | 二级缓存、SQL 改写 |
| `StatementHandler` | 准备 / 执行 Statement | 分页插件、慢 SQL 告警 |
| `ParameterHandler` | 设置参数 | 加密 / 脱敏 |
| `ResultSetHandler` | 结果转换 | 解密 / 数据权限过滤 |

最常见：**PageHelper**（拦 `StatementHandler` 在 SQL 前后包 `SELECT COUNT(*)` + `LIMIT`）。

最常用自研：**SQL 慢日志告警**

```java
@Intercepts(@Signature(type = StatementHandler.class, method = "query",
                       args = {Statement.class, ResultHandler.class}))
public class SlowSqlPlugin implements Interceptor {
    public Object intercept(Invocation inv) throws Throwable {
        long t = System.currentTimeMillis();
        try { return inv.proceed(); }
        finally {
            long cost = System.currentTimeMillis() - t;
            if (cost > 500) log.warn("SLOW SQL {}ms : {}", cost, sqlOf(inv));
        }
    }
}
```

### 5. 批量操作

| 方式 | 一次 INSERT 1 万行耗时 | 注意 |
|------|----------------------|------|
| 循环 mapper.insert | 8 s（一次一往返） | ❌生产禁用 |
| `<foreach>` 拼 VALUES | 200 ms | SQL 长度受 max_allowed_packet 限制 |
| `ExecutorType.BATCH` | 600 ms | JDBC 批处理 |
| `ExecutorType.BATCH` + `rewriteBatchedStatements=true` | 150 ms | **推荐** |

```java
try (SqlSession s = factory.openSession(ExecutorType.BATCH)) {
    UserMapper m = s.getMapper(UserMapper.class);
    for (User u : list) m.insert(u);
    s.commit();
}
```

### 6. MyBatis-Plus 简介

国内最流行的 MyBatis 增强：

- `BaseMapper<T>`：自动 CRUD（`selectById/insert/updateById/deleteById/page`）
- 条件构造器：`new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1).orderByDesc(Post::getCreatedAt).last("LIMIT 10")`
- 主键策略、逻辑删除、乐观锁、自动填充、防全表更新拦截器
- 代码生成器：根据 DB 反向生成 entity / mapper / xml

**取舍**：CRUD 多 → 用 MP；强 SQL 控制 → 留原生 MyBatis。两者可同库共用。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `Configuration` 解析 `<plugins>` 注册 Interceptor 链 |
| 配置 | `mybatis.configuration.cache-enabled=true` 控全局二级缓存开关 |
| 执行 | Executor → 插件链 → StatementHandler → JDBC |
| 边界 | 缓存与事务的可见性、N+1、批处理大小 |
| 验证 | 开 SQL 日志 + P6Spy + 慢 SQL 插件 |

## 四、在博客项目里的落点

- 搜索 API：动态 SQL `<where> + <if>` 处理可选 kw / status / tagIds。
- 文章详情：用嵌套结果映射一次性 JOIN 出 author，避免 N+1。
- 评论批量同步：BATCH executor 批量入库。
- 慢 SQL 自研插件 + 接入 MDC：方便定位是哪个用户哪条请求触发的。

## 五、常见坑

| 现象 | 原因 | 修法 |
|-----|------|------|
| `<if test="status != ''">` 数字判 0 失效 | OGNL 数字不能 `!=''` | 用 `!= null and != 0` |
| 二级缓存集群脏读 | 各节点本地缓存 | 关二级缓存或用 Redis 后端 |
| N+1 列表慢 | 嵌套 `<association select=>` | 改嵌套结果映射或 IN 批量查 |
| 批量插入慢 | 没开 `rewriteBatchedStatements` | URL 加该参数 |
| 自定义插件全局生效太广 | 没加签名过滤 | `@Signature` 精确到方法 |
| MP `updateById` 漏字段 | 字段值是 null 默认不更新 | 改 strategy 或用 `update(wrapper)` |

## 六、面试高频问题

1. 动态 SQL `<where>` 和 `<set>` 帮你做了什么？
2. MyBatis 怎么避免 N+1？
3. 二级缓存为什么生产慎用？
4. 自定义 MyBatis 插件能拦截哪些点？拦截原理？
5. 批量插入有几种实现，效率排序？
6. MyBatis-Plus 的逻辑删除是怎么实现的？
7. `${}` 在哪些场景必须用？怎么防注入？
