# Chapter 28 MyBatis 入门 - 实操 Demo

## Demo 目标

完整接入 MyBatis：注解 Mapper + XML `resultMap` + 动态 SQL + 类型处理器（枚举）。

## 前置条件

- 基线 pom（21 章）+ MySQL 8。
- DB：`blog`，表 `article`：

```sql
CREATE TABLE article (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL,
  content      LONGTEXT,
  author_id    BIGINT NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/ARCHIVED
  view_count   INT NOT NULL DEFAULT 0,
  created_at   DATETIME NOT NULL,
  updated_at   DATETIME NOT NULL,
  KEY idx_author_status (author_id, status)
);
```

## 增量依赖

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>${mybatis-spring-boot.version}</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

## 1. `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.blog.domain
  configuration:
    map-underscore-to-camel-case: true   # author_id → authorId
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 控制台打 SQL（仅开发）
```

## 2. 实体 + 枚举

```java
public enum ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }

@Data @NoArgsConstructor @AllArgsConstructor
public class Article {
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private ArticleStatus status;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## 3. Mapper 接口（注解 + XML 混用）

```java
@Mapper
public interface ArticleMapper {

    // 简单查询用注解
    @Select("SELECT view_count FROM article WHERE id = #{id}")
    Integer selectViewCount(@Param("id") Long id);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementView(@Param("id") Long id);

    // 复杂查询走 XML
    Article selectById(@Param("id") Long id);

    List<Article> search(ArticleQuery q);

    int insert(Article a);

    int updateSelective(Article a);

    int deleteById(@Param("id") Long id);
}
```

## 4. XML `resultMap` + 动态 SQL

`src/main/resources/mapper/ArticleMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.blog.mapper.ArticleMapper">

    <resultMap id="ArticleMap" type="com.example.blog.domain.Article">
        <id property="id" column="id"/>
        <result property="title" column="title"/>
        <result property="content" column="content"/>
        <result property="authorId" column="author_id"/>
        <result property="status" column="status"
                javaType="com.example.blog.domain.ArticleStatus"
                typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
        <result property="viewCount" column="view_count"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <sql id="cols">id, title, content, author_id, status, view_count, created_at, updated_at</sql>

    <select id="selectById" resultMap="ArticleMap">
        SELECT <include refid="cols"/> FROM article WHERE id = #{id}
    </select>

    <select id="search" resultMap="ArticleMap">
        SELECT <include refid="cols"/> FROM article
        <where>
            <if test="authorId != null">     AND author_id = #{authorId}</if>
            <if test="status != null">       AND status   = #{status}</if>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="ids != null and ids.size() > 0">
                AND id IN
                <foreach collection="ids" item="i" open="(" close=")" separator=",">#{i}</foreach>
            </if>
        </where>
        ORDER BY created_at DESC
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO article(title, content, author_id, status, view_count, created_at, updated_at)
        VALUES (#{title}, #{content}, #{authorId}, #{status}, #{viewCount}, #{createdAt}, #{updatedAt})
    </insert>

    <update id="updateSelective">
        UPDATE article
        <set>
            <if test="title != null">title       = #{title},</if>
            <if test="content != null">content   = #{content},</if>
            <if test="status != null">status     = #{status},</if>
            updated_at = NOW()
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteById">DELETE FROM article WHERE id = #{id}</delete>
</mapper>
```

## 5. 查询条件对象

```java
@Data
public class ArticleQuery {
    private Long authorId;
    private ArticleStatus status;
    private String keyword;
    private List<Long> ids;
}
```

## 6. 用法

```java
@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleMapper mapper;

    public Long create(String title, Long authorId) {
        Article a = new Article();
        a.setTitle(title);
        a.setAuthorId(authorId);
        a.setStatus(ArticleStatus.DRAFT);
        a.setViewCount(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        mapper.insert(a);
        return a.getId();    // 主键回填
    }

    public List<Article> publishedOf(Long authorId) {
        ArticleQuery q = new ArticleQuery();
        q.setAuthorId(authorId);
        q.setStatus(ArticleStatus.PUBLISHED);
        return mapper.search(q);
    }
}
```

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| 主键回填 | insert 后 `a.getId()` 非空 |
| 驼峰映射 | `author_id` → `authorId` 自动转换 |
| 枚举存取 | DB 存 `PUBLISHED` 字符串，Java 取出是 `ArticleStatus.PUBLISHED` |
| 动态 SQL | 只传 keyword，看打印的 SQL 不含 `author_id =` 子句 |
| IN 查询 | `ids=[1,2,3]`，SQL 为 `IN (?, ?, ?)` |

## 常见坑

- 没启用 `map-underscore-to-camel-case` → `authorId` 全 null。
- 用 `${}` 而非 `#{}` → SQL 注入。 只有表名 / 排序列才用 `${}`。
- XML 文件未被打包：检查 `src/main/resources/mapper/` 路径与 `mapper-locations` 一致。
- `<if test="status != null">` 里写成 `=='PUBLISHED'` → OGNL 中字符串比较要 `.toString().equals(...)`，不如传枚举对象。
- 把 `Article` 当 DTO 直接返给前端 → 字段泄露（content/author_id）。 必须经 DTO 转换。

## 提交

```bash
git commit -m "chapter 28: mybatis crud + xml resultMap + dynamic sql"
```
