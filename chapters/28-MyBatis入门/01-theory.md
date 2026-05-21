# Chapter 28 MyBatis 入门 - 理论篇

## 一、学习定位

JDBC 写多了你就发现：90% 的代码是「拿连接 → 设参数 → 取结果 → 释放」的样板。MyBatis 就是把这些样板抽走，让你只关心 SQL 本身。

- 优先级：L1
- 预计投入：4 小时
- 阶段产出：博客 DAO 全部用 MyBatis 重写，单测覆盖 ≥ 70%

## 二、核心概念

### 1. ORM 的两条路线：全自动 vs 半自动

| 维度 | 全自动（JPA / Hibernate） | 半自动（MyBatis） |
|------|-------------------------|------------------|
| SQL 来源 | 框架根据实体自动生成 | 你自己写 |
| 学习曲线 | 上手快，深入难（关联、N+1） | 上手慢，但 SQL 完全可控 |
| 性能调优 | 经常要 hack 框架 | 直接改 SQL |
| 国内主流 | 偏少 | **互联网公司 80% 选它** |
| 适用 | CRUD 简单 / 模型稳定 | 复杂报表 / 强 SQL 控制 |

> **结论**：业务复杂、SQL 性能敏感 → 选 MyBatis。

### 2. 核心组件

```
SqlSessionFactoryBuilder → SqlSessionFactory → SqlSession → Mapper
       (建造者)             (单例，整个应用)     (短期，每请求)  (代理)
```

| 组件 | 作用 | 生命周期 |
|-----|------|---------|
| `SqlSessionFactoryBuilder` | 解析 mybatis-config.xml | 方法局部 |
| `SqlSessionFactory` | 持有配置、数据源 | 应用单例 |
| `SqlSession` | 一次 DB 会话（拿连接 / 事务 / 缓存） | 请求范围 |
| `Mapper` 接口 | 用 JDK 动态代理把方法调用 → SQL | 跟 SqlSession 共生 |

### 3. Mapper 接口 + XML 映射

**接口**：

```java
public interface UserMapper {
    User findById(Long id);
    List<User> findByStatus(@Param("status") int status,
                           @Param("limit") int limit);
    int insert(User u);
}
```

**XML**：

```xml
<mapper namespace="com.example.dao.UserMapper">
    <select id="findById" resultType="com.example.entity.User">
        SELECT id, email, name, status, created_at AS createdAt
        FROM user WHERE id = #{id}
    </select>

    <select id="findByStatus" resultType="com.example.entity.User">
        SELECT id, email, name FROM user
        WHERE status = #{status}
        LIMIT #{limit}
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user(email, name, status) VALUES(#{email}, #{name}, #{status})
    </insert>
</mapper>
```

**关键约定**：

- `namespace` = Mapper 接口的全限定名。
- `<select id="">` = 接口方法名。
- 参数名通过 `@Param` 暴露到 XML。
- 返回字段名 = 实体属性名（不一致用 `<resultMap>` 或 SQL `AS`）。

### 4. `#{}` vs `${}`

| 写法 | 处理方式 | SQL 注入 | 用途 |
|------|---------|---------|------|
| `#{name}` | PreparedStatement 占位符 `?` | ✅安全 | 99% 的场景 |
| `${name}`  | 字符串拼接 | ❌危险 | 表名 / 列名等不能用 `?` 的地方 |

```xml
<!-- 危险 -->
<select id="bad">SELECT * FROM user WHERE name = '${name}'</select>
<!-- 用户传 ' OR 1=1 --  → 拖库 -->

<!-- 安全 -->
<select id="good">SELECT * FROM user WHERE name = #{name}</select>
```

### 5. ResultMap：解决"列名 ≠ 字段名"

```xml
<resultMap id="UserMap" type="com.example.entity.User">
    <id     column="id"          property="id"/>
    <result column="email"       property="email"/>
    <result column="created_at"  property="createdAt"/>
    <association property="dept" javaType="Department">
        <id     column="dept_id"   property="id"/>
        <result column="dept_name" property="name"/>
    </association>
</resultMap>

<select id="findWithDept" resultMap="UserMap">
    SELECT u.*, d.id AS dept_id, d.name AS dept_name
    FROM user u LEFT JOIN dept d ON u.dept_id = d.id
    WHERE u.id = #{id}
</select>
```

> 实际项目常用「驼峰下划线自动映射」省掉大部分 resultMap：
> `<setting name="mapUnderscoreToCamelCase" value="true"/>`

### 6. 事务

MyBatis 自己不管事务，只是把 `Connection.commit/rollback` 暴露出来：

```java
try (SqlSession session = factory.openSession()) {  // autoCommit=false
    UserMapper m = session.getMapper(UserMapper.class);
    m.insert(u1);
    m.insert(u2);
    session.commit();
}
```

**整合 Spring 后**：`@Transactional` 接管 → MyBatis 自动复用线程绑定的 Connection。

### 7. 缓存

| 缓存 | 范围 | 默认 | 注意 |
|-----|------|------|------|
| 一级缓存 | SqlSession 内 | 开 | 同一会话同 SQL 同参数命中 |
| 二级缓存 | namespace 跨会话 | 关 | 分布式环境慎用，易脏读 |

一级缓存的"坑"：A 事务里 `SELECT id=1` 拿到 Alice → B 改 → A 再 `SELECT id=1` 仍是 Alice（被缓存了）。生产建议在 Spring 整合后用「`@Transactional` 默认 RR + 短事务」回避。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `SqlSessionFactoryBuilder.build(InputStream)` |
| 配置 | `mybatis-config.xml`（数据源、settings、typeAliases）+ 各 mapper XML |
| 执行 | 动态代理拦截 Mapper 调用 → `MappedStatement` → 参数处理 → `StatementHandler` → JDBC |
| 边界 | SQL 注入（用 `#{}`）、缓存坑、N+1 |
| 验证 | 打开 SQL 日志：`<setting name="logImpl" value="STDOUT_LOGGING"/>` |

## 四、在博客项目里的落点

- DAO 层：`UserMapper` / `PostMapper` / `CommentMapper`，XML 放 `resources/mapper/`。
- 配置：`mybatis-config.xml` + `application.yml` 里 `mybatis.mapper-locations`。
- 事务：Service 层用 Spring `@Transactional`，禁止在 DAO 层手动 `session.commit()`。
- 日志：开 P6Spy 看真实带参 SQL，开发期排查必备。

## 五、常见坑

| 现象 | 原因 | 修法 |
|-----|------|------|
| `Parameter 'xxx' not found` | 多参数没加 `@Param` | 多参一律 `@Param` |
| 查询返回字段全 null | 列名驼峰不匹配 | 开 `mapUnderscoreToCamelCase` 或 SQL 加 AS |
| in 查询报语法错 | 用 `#{ids}` 把 list 当字符串 | 用 `<foreach>` |
| SQL 注入 | 用了 `${}` 拼用户输入 | 改 `#{}` |
| 一级缓存脏读 | 长事务里数据被外部改 | 缩短事务 / 用 `flushCache="true"` |
| 二级缓存集群不一致 | 各节点本地 cache | 关掉或用 Redis 后端 |

## 六、面试高频问题

1. MyBatis 和 Hibernate 的本质区别？
2. `#{}` 和 `${}` 的区别？什么时候必须用 `${}`？
3. Mapper 接口没写实现类，是怎么被调用的？
4. 一级缓存什么时候会失效？
5. resultType 和 resultMap 的区别？
6. MyBatis 怎么处理多对一 / 一对多？
7. 怎么在 MyBatis 里防 SQL 注入？
