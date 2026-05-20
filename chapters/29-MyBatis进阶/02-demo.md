# Chapter 29 MyBatis 进阶 - 实操 Demo

## Demo 目标

四件套：PageHelper 分页 + 关联查询（避免 N+1） + 批量插入 + SQL 日志对比。

## 前置条件

- 28 章已能跑通基本 CRUD。
- 表：在 28 章 `article` 基础上加 `user`、`tag`、`article_tag`。

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  nickname VARCHAR(50)
);
CREATE TABLE tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE
);
CREATE TABLE article_tag (
  article_id BIGINT NOT NULL,
  tag_id     BIGINT NOT NULL,
  PRIMARY KEY (article_id, tag_id),
  KEY idx_tag (tag_id)
);
```

## 增量依赖

```xml
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>${pagehelper.version}</version>
</dependency>
```

## 1. PageHelper 分页

```yaml
pagehelper:
  helper-dialect: mysql
  reasonable: true            # pageNum < 1 自动 1，> totalPages 自动末页
  support-methods-arguments: false
```

```java
public PageInfo<Article> pageList(ArticleQuery q, int pageNum, int pageSize) {
    PageHelper.startPage(pageNum, pageSize);    // 紧跟下一行 SELECT
    List<Article> list = mapper.search(q);      // 自动加 LIMIT + 同事务 COUNT
    return new PageInfo<>(list);
}
```

**控制器返回**：

```java
@GetMapping("/api/articles")
public ApiResponse<PageDTO<ArticleDTO>> list(ArticleQuery q,
                                             @RequestParam(defaultValue = "1")  int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
    PageInfo<Article> p = service.pageList(q, pageNum, pageSize);
    return ApiResponse.ok(PageDTO.of(p, ArticleDTO::from));
}

public record PageDTO<T>(long total, int pageNum, int pageSize, List<T> records) {
    public static <S, T> PageDTO<T> of(PageInfo<S> p, Function<S, T> f) {
        return new PageDTO<>(p.getTotal(), p.getPageNum(), p.getPageSize(),
            p.getList().stream().map(f).toList());
    }
}
```

## 2. N+1 对比

### ❌ 反例（N+1）

```java
List<Article> articles = mapper.search(q);                 // 1 次
articles.forEach(a -> {
    a.setAuthor(userMapper.selectById(a.getAuthorId()));   // N 次
});
```

SQL 日志会看到 `SELECT * FROM user WHERE id = 1; SELECT * FROM user WHERE id = 2; ...`，10 条文章查 11 次 SQL。

### ✅ 方案 A：JOIN（嵌套 resultMap）

```xml
<resultMap id="ArticleWithAuthor" type="ArticleWithAuthorDTO">
    <id property="id" column="a_id"/>
    <result property="title" column="a_title"/>
    <association property="author" javaType="UserDTO">
        <id property="id" column="u_id"/>
        <result property="nickname" column="u_nickname"/>
    </association>
</resultMap>

<select id="searchWithAuthor" resultMap="ArticleWithAuthor">
    SELECT a.id a_id, a.title a_title, u.id u_id, u.nickname u_nickname
    FROM article a JOIN user u ON a.author_id = u.id
    WHERE a.status = 'PUBLISHED'
    ORDER BY a.created_at DESC
</select>
```

### ✅ 方案 B：两次查询 + 内存拼接

```java
List<Article> articles = mapper.search(q);
Set<Long> uids = articles.stream().map(Article::getAuthorId).collect(toSet());
Map<Long, User> userMap = userMapper.selectByIds(uids).stream()
    .collect(toMap(User::getId, Function.identity()));
articles.forEach(a -> a.setAuthor(userMap.get(a.getAuthorId())));
```

> 取舍：A 简单但当 user 字段多时浪费带宽；B 适合多表 / 跨库。

## 3. 一对多（文章 + 标签列表）

```xml
<resultMap id="ArticleWithTags" type="ArticleDetailDTO">
    <id property="id" column="a_id"/>
    <result property="title" column="a_title"/>
    <collection property="tags" ofType="TagDTO">
        <id property="id" column="t_id"/>
        <result property="name" column="t_name"/>
    </collection>
</resultMap>

<select id="detailWithTags" resultMap="ArticleWithTags">
    SELECT a.id a_id, a.title a_title, t.id t_id, t.name t_name
    FROM article a
    LEFT JOIN article_tag at ON at.article_id = a.id
    LEFT JOIN tag t          ON t.id = at.tag_id
    WHERE a.id = #{id}
</select>
```

## 4. 批量插入（性能差异显著）

```xml
<insert id="batchInsert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO article(title, content, author_id, status, view_count, created_at, updated_at)
    VALUES
    <foreach collection="list" item="a" separator=",">
        (#{a.title}, #{a.content}, #{a.authorId}, #{a.status}, 0, NOW(), NOW())
    </foreach>
</insert>
```

```java
@Transactional
public void importBatch(List<Article> list) {
    // 单条循环：1000 条 ≈ 5s
    // list.forEach(mapper::insert);

    // 批量 foreach：1000 条 ≈ 0.2s
    Lists.partition(list, 500).forEach(mapper::batchInsert);
}
```

> JDBC URL 加 `rewriteBatchedStatements=true` 让 `ExecutorType.BATCH` 也合并发送。

## 5. SQL 日志（性能定位关键）

`application-dev.yml`：

```yaml
logging:
  level:
    com.example.blog.mapper: DEBUG       # 打印每条 SQL + 参数 + 耗时
```

或开启 P6Spy（更易读）：

```xml
<dependency>
    <groupId>com.github.gavlyukovskiy</groupId>
    <artifactId>p6spy-spring-boot-starter</artifactId>
    <version>1.9.2</version>
</dependency>
```

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| 分页 SQL | 日志含 `LIMIT 0, 10` + 单独的 `SELECT COUNT(0)` |
| N+1 重现 | 反例代码下日志出现 N 条相同模板的 SELECT user |
| JOIN 合并 | `searchWithAuthor` 日志只有 1 条 SQL |
| 一对多映射 | 一个 article 多个 tags，list size 正确 |
| 批量插入 | 1000 条 < 500ms |

## 常见坑

- `PageHelper.startPage` 后没有紧跟 SELECT（中间有别的查询）→ 分页失效或错位。
- `<foreach>` 批量过大（> 5000）触发 `max_allowed_packet` → 分批。
- 嵌套 resultMap 的列名冲突（多表都有 id）→ 必须起别名 `a_id` / `u_id`。
- 误以为 `LEFT JOIN` 一对多会返回多行 Article 对象 → MyBatis 按 `<id>` 列去重合并，所以 `<id>` 必填。
- 分页 + ORDER BY 字段无索引 → DB 全表扫描 + filesort。 上 `idx_status_created`。

## 提交

```bash
git commit -m "chapter 29: pagehelper + n+1 + batch insert"
```
