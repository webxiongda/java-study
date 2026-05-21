# Chapter 28 MyBatis 入门 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：`#{}` 和 `${}` 的本质区别？什么时候必须用 `${}`？

**参考答案：**

| 维度 | `#{name}` | `${name}` |
|------|----------|----------|
| 底层 | `PreparedStatement` + 占位符 `?` | 字符串拼接 |
| SQL 注入 | ✅ 防 | ❌ 必须自己白名单 |
| 性能 | 预编译可缓存执行计划 | 每次新 SQL |
| 引号 | MyBatis 自动加 | 你自己加 |
| 用途 | 99% 的参数 | 表名 / 列名 / `ORDER BY` 列名 |

**必须用 `${}` 的场景**（JDBC 占位符语法上不支持的位置）：

```xml
<!-- 动态表名（分表） -->
SELECT * FROM order_${month} WHERE id = #{id}

<!-- 动态列名 / 排序字段 -->
SELECT * FROM post ORDER BY ${sortColumn} ${sortDirection} LIMIT #{limit}
```

但 **`${}` 的值必须做白名单校验**：

```java
private static final Set<String> ALLOWED_SORT = Set.of("id", "created_at", "view_count");
if (!ALLOWED_SORT.contains(sortColumn)) {
    throw new IllegalArgumentException();
}
```

**关键点**：把"参数值"和"SQL 结构"区分开——参数值永远 `#{}`，SQL 结构（白名单后）才用 `${}`。

---

## Q2（概念）：MyBatis 的 Mapper 接口没有任何实现类，是怎么被调用的？

**参考答案：**

MyBatis 用 **JDK 动态代理** 给接口生成代理对象：

```
session.getMapper(UserMapper.class)
  → MapperProxyFactory.newInstance()
  → Proxy.newProxyInstance(loader, {UserMapper}, MapperProxy)
```

`MapperProxy.invoke(method, args)` 干 3 件事：

1. **找 MappedStatement**：用 `接口名.方法名` 拼 statementId（如 `com.x.UserMapper.findById`），在解析 XML 时就已注册到 Configuration。
2. **绑定参数**：根据方法签名（`@Param` / POJO / Map）打包成 ParamMap。
3. **委托给 SqlSession**：调 `sqlSession.selectOne/selectList/insert/...`。
4. SqlSession 把请求传给 `Executor → StatementHandler → ParameterHandler → ResultSetHandler` 最终走 JDBC。

```java
public class MapperProxy implements InvocationHandler {
    public Object invoke(Object proxy, Method method, Object[] args) {
        MapperMethod mm = methodCache.get(method);
        return mm.execute(sqlSession, args);   // 转换成 selectXxx/insert/...
    }
}
```

**关键点**：

- 接口本身只是占位（"协议"），实现是运行时生成的代理。
- statementId 命名是约定，所以 namespace 必须和接口全限定名一致。
- 这也是为什么 Mapper 接口里写 default 方法不会走 SQL（代理直接调用 default 实现）。

---

## Q3（实操）：以下 Mapper 有 6 处问题，找出并改正。

```java
public interface PostMapper {
    Post findOne(Long id, Integer status);
    List<Post> findByIds(List<Long> ids);
    int update(Post p);
    int deleteOlder(String date);
}
```

```xml
<mapper namespace="PostMapper">

    <select id="findOne" resultType="Post">
        SELECT * FROM post WHERE id = #{id} AND status = ${status}
    </select>

    <select id="findByIds" resultType="Post">
        SELECT * FROM post WHERE id IN (#{ids})
    </select>

    <update id="update">
        UPDATE post SET title = '${p.title}' WHERE id = #{p.id}
    </update>

    <delete id="deleteOlder">
        DELETE FROM post WHERE created_at < #{date}
    </delete>
</mapper>
```

**参考答案（改后版本）：**

```xml
<!-- ① namespace 写全限定名 -->
<mapper namespace="com.example.dao.PostMapper">

    <!-- ② resultType 用 typeAlias 或全限定名 -->
    <!-- ③ status 也用 #{}（防注入 + 用预编译） -->
    <select id="findOne" resultType="com.example.entity.Post">
        SELECT id, user_id AS userId, title, status, created_at AS createdAt
        FROM post WHERE id = #{id} AND status = #{status}
    </select>

    <!-- ④ List 参数必须 foreach；不能用 #{ids} -->
    <select id="findByIds" resultType="com.example.entity.Post">
        SELECT * FROM post
        WHERE id IN
        <foreach collection="ids" item="id" open="(" close=")" separator=",">
            #{id}
        </foreach>
    </select>

    <!-- ⑤ ${} 拼用户标题 → SQL 注入；改 #{} -->
    <update id="update">
        UPDATE post SET title = #{p.title}, updated_at = NOW() WHERE id = #{p.id}
    </update>

    <delete id="deleteOlder">
        DELETE FROM post WHERE created_at &lt; #{date}    <!-- ⑥ XML 里 < 必须转义 &lt; -->
    </delete>
</mapper>
```

接口侧补 `@Param`：

```java
Post findOne(@Param("id") Long id, @Param("status") Integer status);
List<Post> findByIds(@Param("ids") List<Long> ids);
int update(@Param("p") Post p);
int deleteOlder(@Param("date") String date);
```

**6 处问题汇总**：

1. namespace 没写全限定名 → 找不到 Mapper。
2. `resultType="Post"` 没注册 typeAlias。
3. `status = ${status}` SQL 注入 + 不走预编译。
4. `IN (#{ids})` 不能传 List，要 `<foreach>`。
5. `'${p.title}'` 用户标题 SQL 注入。
6. XML 里 `<` 必须转义成 `&lt;`。

---

## Q4（实操）：写 `CommentMapper` 的 3 个方法：`分页查询某文章评论`、`批量插入`、`按楼根 id 删除整楼`。要求带 `@Param`、`foreach`、动态 SQL。

**参考答案：**

接口：

```java
public interface CommentMapper {
    List<Comment> pageByPost(@Param("postId") Long postId,
                             @Param("lastId") Long lastId,
                             @Param("size") int size);

    int batchInsert(@Param("list") List<Comment> list);

    int deleteByRoot(@Param("postId") Long postId,
                     @Param("rootId") Long rootId);
}
```

XML：

```xml
<mapper namespace="com.example.dao.CommentMapper">

    <resultMap id="CommentMap" type="com.example.entity.Comment">
        <id     column="id"         property="id"/>
        <result column="post_id"    property="postId"/>
        <result column="user_id"    property="userId"/>
        <result column="parent_id"  property="parentId"/>
        <result column="root_id"    property="rootId"/>
        <result column="content"    property="content"/>
        <result column="created_at" property="createdAt"/>
    </resultMap>

    <!-- keyset 分页 -->
    <select id="pageByPost" resultMap="CommentMap">
        SELECT id, post_id, user_id, parent_id, root_id, content, created_at
        FROM comment
        WHERE post_id = #{postId} AND is_deleted = 0
        <if test="lastId != null and lastId > 0">
            AND id &lt; #{lastId}
        </if>
        ORDER BY id DESC
        LIMIT #{size}
    </select>

    <!-- 批量插入 -->
    <insert id="batchInsert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO comment(post_id, user_id, parent_id, root_id, content)
        VALUES
        <foreach collection="list" item="c" separator=",">
            (#{c.postId}, #{c.userId}, #{c.parentId}, #{c.rootId}, #{c.content})
        </foreach>
    </insert>

    <!-- 软删除整楼 -->
    <update id="deleteByRoot">
        UPDATE comment SET is_deleted = 1, updated_at = NOW(3)
        WHERE post_id = #{postId} AND root_id = #{rootId}
    </update>
</mapper>
```

**关键点**：

1. **keyset 分页**用 `<if>` 处理"首页 lastId=null"分支；XML 里 `<` 写 `&lt;`。
2. **批量 INSERT** 用单 SQL 多 VALUES，一次网络往返插 N 条；要打开 `rewriteBatchedStatements=true`。
3. **"删整楼" 用软删 UPDATE**，而非物理 DELETE，便于审计回滚。
4. `useGeneratedKeys` 让批量插入把自增 id 回填到每个对象。

---

## Q5（综合）：把以下 JDBC 代码改写成 MyBatis 版本（接口 + XML + Service 调用），并指出 MyBatis 版本相比 JDBC 解决了哪些痛点。

```java
public List<Post> searchByTitle(String kw, int page, int size) {
    String sql = "SELECT id, user_id, title, status, created_at " +
                 "FROM post WHERE status = 1 AND title LIKE ? " +
                 "ORDER BY created_at DESC LIMIT ?, ?";
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, "%" + kw + "%");
        ps.setInt(2, (page - 1) * size);
        ps.setInt(3, size);
        try (ResultSet rs = ps.executeQuery()) {
            List<Post> list = new ArrayList<>();
            while (rs.next()) {
                Post p = new Post();
                p.setId(rs.getLong("id"));
                p.setUserId(rs.getLong("user_id"));
                p.setTitle(rs.getString("title"));
                p.setStatus(rs.getInt("status"));
                p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                list.add(p);
            }
            return list;
        }
    } catch (SQLException e) { throw new RuntimeException(e); }
}
```

**参考答案：**

### Mapper 接口

```java
public interface PostMapper {
    List<Post> searchByTitle(@Param("kw") String kw,
                             @Param("offset") int offset,
                             @Param("size") int size);
}
```

### XML

```xml
<mapper namespace="com.example.dao.PostMapper">

    <select id="searchByTitle" resultType="com.example.entity.Post">
        SELECT id, user_id, title, status, created_at
        FROM post
        WHERE status = 1
          AND title LIKE CONCAT('%', #{kw}, '%')
        ORDER BY created_at DESC
        LIMIT #{offset}, #{size}
    </select>
</mapper>
```

### Service

```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostMapper postMapper;

    public List<Post> search(String kw, int page, int size) {
        if (kw == null || kw.isBlank()) return List.of();
        return postMapper.searchByTitle(kw, (page - 1) * size, size);
    }
}
```

### MyBatis 解决的痛点对照

| 痛点 | JDBC 版 | MyBatis 版 |
|-----|--------|-----------|
| 样板代码 | try-with-resources、setXxx、while next、ResultSet → 实体 30 行 | 1 个 select 标签 |
| 字段映射 | 手写 `setUserId(rs.getLong("user_id"))` | 自动驼峰映射 |
| 参数绑定 | 索引顺序写死，加列容易错位 | `#{name}` 按名字 |
| SQL 散落 | SQL 字符串拼在 Java 里 | 集中 XML，DBA 可 review |
| 异常 | 必须 catch SQLException | 框架封装成 `MyBatisException` (RuntimeException) |
| 测试 | 必须真实 DataSource | Mock Mapper 接口即可 |
| 日志 | 自己打 | MyBatis / P6Spy 自动打带参 SQL |

### 真实生产对照

- **美团**：MyBatis-Plus + 自研代码生成器，Mapper 接口 90% 是单表 CRUD 自动生成；复杂报表手写 XML。
- **饿了么**：MyBatis + 自研 ORM 中间件接 ShardingSphere，单 Mapper 透明分表分库。

**关键点**：

1. MyBatis 不是消灭 SQL，而是消灭样板。
2. **LIKE 拼接用 `CONCAT('%', #{kw}, '%')`**，不要 `'%${kw}%'`（SQL 注入）。
3. 真实工程会再叠一层 PageHelper 或自定义 RowBounds 做分页，不让 Service 算 offset。
4. 长期看，分页应升级 keyset，offset 在大表上有性能上限。
