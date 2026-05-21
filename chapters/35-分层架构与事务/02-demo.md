# Chapter 35 分层架构与事务 - 实操 Demo

## Demo 目标

在博客项目里实验 3 个核心场景：

1. `@Transactional` 传播行为：`REQUIRED` vs `REQUIRES_NEW`。
2. self call 导致事务失效 + 修法。
3. 分布式场景：两步操作（扣库存 + 写日志）如何保证一致性。

## 前置

- Blog 项目已有 UserMapper / PostMapper / PostTagMapper
- 已引入 spring-boot-starter-data-jpa 或 mybatis（事务原理相同）

## 一、基础事务：发布文章 + 绑定标签原子操作

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {
    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, PostCreateReq req) {
        Post p = Post.builder()
            .userId(userId).title(req.title()).content(req.content()).status(1).build();

        postMapper.insert(p);                                     // 步骤 1

        if (!req.tagIds().isEmpty()) {
            postTagMapper.batchInsert(p.getId(), req.tagIds());   // 步骤 2
        }

        auditLogService.log(userId, "publish", p.getId());       // 步骤 3（见下）

        return p.getId();
    }
}
```

## 二、传播行为演示

### `REQUIRES_NEW`：审计日志独立提交

```java
@Service
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 新事务，独立提交
    public void log(Long userId, String action, Long targetId) {
        auditLogMapper.insert(AuditLog.builder()
            .userId(userId).action(action).targetId(targetId).build());
    }
}
```

**效果**：

- `PostService.publish` 的大事务 rollback → post / post_tag 回滚 ✅。
- `AuditLogService.log` 已提交 → 审计日志**留下** ✅（需要留存失败记录）。

### `NESTED`：子事务可独立回滚但父提交时一起提交

```java
@Transactional(propagation = Propagation.NESTED)
public void writeTag(Long postId, Long tagId) {
    try { postTagMapper.insert(postId, tagId); }
    catch (Exception e) { log.warn("tag insert failed, skip"); } // 子事务回滚，不影响外层
}
```

> ⚠️ MySQL InnoDB 支持 NESTED（用 SAVEPOINT 实现），但并非所有 ORM/DB 支持。

## 三、self call 失效演示

```java
@Service
public class PostService {
    @Transactional
    public void saveAndPublish(Post p) {
        save(p);          // ❌ self call，事务注解失效
        publish(p.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Post p) { ... }
}
```

**验证**：在 `save` 里 throw，观察 `saveAndPublish` 是否整体回滚（答案：不会，self call 事务不生效）。

**修法**：注入自身代理

```java
@Service
public class PostService {
    @Autowired PostService self;   // Spring 注入代理

    @Transactional
    public void saveAndPublish(Post p) {
        self.save(p);       // ✅ 走代理，事务生效
        self.publish(p.getId());
    }
}
```

## 四、只读事务优化

```java
@Transactional(readOnly = true)   // 提示 DB：这是读请求
public PostDetailVO detail(Long id) {
    Post p = postMapper.findById(id);
    ...
}
```

**效果**：InnoDB 跳过 undo log 生成，MyBatis 也可用 read replica。

## 五、长事务陷阱演示

```java
// ❌ 长事务：在事务里调外部 API
@Transactional
public void registerAndNotify(RegisterReq req) {
    User user = userMapper.insert(...);
    smsService.sendVerify(user.getPhone());   // 可能 3s 超时 → 连接一直持有
    mailService.sendWelcome(user.getEmail()); // 再 3s
}

// ✅ 外部 IO 移到事务外
public void registerAndNotify(RegisterReq req) {
    Long userId = self.register(req);   // 短事务
    smsService.sendVerify(...);         // 事务已提交，在外面发
    mailService.sendWelcome(...);
}

@Transactional
public Long register(RegisterReq req) { return userMapper.insert(...); }
```

## 六、测试事务回滚

```java
@SpringBootTest
class PostServiceIT {
    @Autowired PostService postService;
    @Autowired PostMapper postMapper;

    @Test
    @Transactional              // 测试结束自动回滚
    void publish_and_tags_atomic() {
        Long id = postService.publish(1L, new PostCreateReq("t","s","c",true,List.of(1L,2L)));
        assertThat(postMapper.findById(id)).isNotNull();
    }

    @Test
    void publish_rolls_back_on_bad_tag() {
        long before = postMapper.countAll();
        assertThatThrownBy(() ->
            postService.publish(1L, new PostCreateReq("t","s","c",true,List.of(99999L))))
            .isInstanceOf(DataAccessException.class);
        assertThat(postMapper.countAll()).isEqualTo(before);   // 整体回滚
    }
}
```

## 提交建议

```bash
git add src/main/java/com/example/blog/service/
git commit -m "chapter 35: transaction propagation demo + self-call fix"
```
