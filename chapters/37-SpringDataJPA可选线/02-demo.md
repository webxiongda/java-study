# Chapter 37 Spring Data JPA 可选线 - 实操 Demo

## Demo 目标

用 Spring Data JPA 代替 MyBatis 做一个博客的 `Tag` 模块全 CRUD，对比两个 ORM 的代码量和心智负担。

## 前置

- 32 章 Boot 应用可运行
- MySQL blog 数据库已有 tag 表

## 一、引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

配置：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate          # 验证实体和表结构一致，不改表
    show-sql: true                # 看 Hibernate 生成的 SQL
    properties:
      hibernate.format_sql: true
```

## 二、Entity

```java
@Entity
@Table(name = "tag")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    @Builder.Default private Integer isDeleted = 0;
}
```

## 三、Repository

```java
public interface TagRepository extends JpaRepository<TagEntity, Long> {
    Optional<TagEntity> findByName(String name);
    List<TagEntity> findByNameContaining(String kw);
}
```

## 四、Service

```java
@Service @RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;

    public TagEntity create(String name) {
        if (tagRepository.findByName(name).isPresent()) {
            throw new BusinessException("标签已存在: " + name);
        }
        return tagRepository.save(TagEntity.builder().name(name).createdAt(LocalDateTime.now()).build());
    }

    public List<TagEntity> search(String kw) {
        return tagRepository.findByNameContaining(kw);
    }

    @Transactional
    public void delete(Long id) {
        tagRepository.findById(id).orElseThrow(() -> new NotFoundException("tag not found"));
        tagRepository.deleteById(id);
    }
}
```

## 五、验证

```bash
curl -X POST http://localhost:8080/api/v1/tags \
  -H "Content-Type: application/json" \
  -d '{"name":"spring"}'
# {"id":1,"name":"spring","createdAt":"..."}

curl http://localhost:8080/api/v1/tags?kw=spr
```

## 六、N+1 演示

```java
@Entity
public class PostEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity author;
}

// 控制器
@GetMapping("/posts/nplus1")
public List<PostVO> nplus1() {
    List<PostEntity> posts = postRepository.findAll();
    for (PostEntity p : posts) {
        System.out.println(p.getAuthor().getEmail());  // 每个触发一条 SELECT
    }
}
```

日志输出：

```
Hibernate: select p1_0.id,p1_0.title from post p1_0
Hibernate: select u1_0.id,u1_0.email from user u1_0 where u1_0.id=?
Hibernate: select u1_0.id,u1_0.email from user u1_0 where u1_0.id=?
...
```

修法：加 `@EntityGraph("Post.author")`。

## 七、失败场景：LazyInitializationException

```java
@GetMapping("/posts/{id}")
public PostDTO get(@PathVariable Long id) {
    PostEntity p = postRepository.findById(id).orElseThrow();
    // 事务已结束时（Controller 层）访问懒加载字段
    return new PostDTO(p.getId(), p.getAuthor().getEmail());  // ❌ lazy 初始化异常
}
```

修法：Service 层 @Transactional 内提前获取，或 `JOIN FETCH`。

## 提交建议

```bash
git add src/main/java/com/example/blog/jpa/
git commit -m "chapter 37: JPA tag module + N+1 demo"
```
