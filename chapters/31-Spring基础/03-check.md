# Chapter 31 Spring 基础 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：IoC 和 DI 是什么关系？Spring 实现 DI 的三种方式各有什么优劣？

**参考答案：**

**关系**：IoC（控制反转）是思想，DI（依赖注入）是 IoC 的一种实现方式。"控制权"从对象自己 new 依赖 → 转移给容器注入。

| 注入方式 | 示例 | 优点 | 缺点 |
|---------|------|------|------|
| 构造器 | `public Svc(Mapper m) {...}` | final/不可变；依赖一目了然；单测直接 new；容器启动就报错 | 循环依赖构造器不好解 |
| Setter | `@Autowired void setX(X x)` | 可选依赖；可循环依赖 | 依赖隐式；字段非 final |
| 字段 | `@Autowired X x` | 代码最短 | 无法单测（不能 new）；依赖隐藏；final 加不了 |

**面试常考陷阱**：

```java
// 字段注入无法这样单测
new PostService();      // NPE！mapper 没注入
// 构造器可以
new PostService(mockMapper);  // ✅
```

**关键点**：团队规范一律构造器注入 + Lombok `@RequiredArgsConstructor`，既省代码又能单测。

---

## Q2（概念）：Spring AOP 的底层用了什么代理？两种代理怎么选？AOP 什么时候会失效？

**参考答案：**

**两种代理**：

| 维度 | JDK 动态代理 | CGLIB |
|------|------------|-------|
| 条件 | 目标类必须实现接口 | 无接口也行（子类代理） |
| 原理 | `java.lang.reflect.Proxy` | 运行时字节码生成子类 |
| 性能 | 略慢（反射） | Spring 5+ 已优化，基本相当 |
| 限制 | 只能代理接口方法 | final 类/方法无法代理 |

**Spring 的选择逻辑**：

- `@EnableAspectJAutoProxy(proxyTargetClass=false)`（默认）：有接口用 JDK，没接口用 CGLIB。
- Spring Boot 默认 `proxyTargetClass=true`：统一用 CGLIB。

**AOP 失效的 6 种场景**：

| 场景 | 原因 | 修法 |
|------|------|------|
| self call | 类内调用绕过代理 | 注入自身 / 抽到另一个 Bean |
| private 方法 | 代理无法覆盖 | 改 public |
| final 类/方法（CGLIB）| 无法生成子类 | 去掉 final |
| 非 Spring Bean | 没代理 | 加 `@Component` |
| 构造器里调用 | Bean 还未完全初始化 | 改 `@PostConstruct` |
| 同类多切面执行顺序 | 未指定 `@Order` | 加 `@Order(n)` |

**结论**：`@Transactional`、`@Cacheable`、`@Async` 本质都是 AOP，所有失效场景通用。

---

## Q3（实操）：以下代码有 5 处 Spring 反模式，请找出并改正。

```java
@RestController
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private static TagService tagService;   // ①

    @GetMapping("/posts")
    public List<Post> list() {
        return postService.findAll();       // 返回 Entity
    }

    @GetMapping("/posts/{id}")
    public Post get(@PathVariable Long id) {
        try {
            return postService.findById(id);
        } catch (Exception e) {
            return null;                    // ②
        }
    }
}

@Service
public class PostService {
    private final PostMapper mapper;
    private final TagService tagService;   // ③ 假设 TagService 也注入 PostService

    @Autowired
    public PostService(PostMapper mapper, TagService tagService) {
        this.mapper = mapper;
        this.tagService = tagService;
    }

    @Transactional
    private void save(Post p) {            // ④
        mapper.insert(p);
    }

    public Post publish(Post p) {
        save(p);                           // ⑤ self call
        return p;
    }
}
```

**参考答案：**

**① static 字段注入**

`static` 字段不归 Spring 管，注入无效（字段是类级别，而 Spring 按实例注入）。

```java
// 删掉 static，改为实例字段构造器注入
private final TagService tagService;
```

**② catch 后返回 null**

调用方拿到 null 会 NPE，且隐藏了真实错误。

```java
// 删掉 try-catch，让全局异常处理器处理
// GlobalExceptionHandler @ExceptionHandler(PostNotFoundException.class) → 404
```

**③ 循环依赖**

`PostService` 依赖 `TagService`，`TagService` 也依赖 `PostService` → 构造器循环依赖报错。

修法：重构，抽出共同操作到第三个 Service，或改成事件驱动解耦。

**④ `@Transactional` 打在 private 方法**

AOP 代理不能增强 private 方法，事务不生效。

```java
@Transactional
public void save(Post p) { mapper.insert(p); }   // 改 public
```

**⑤ self call 绕过代理**

`publish` 里直接调 `this.save(p)`，走的是原始对象而非代理，事务注解失效。

```java
@Service
public class PostService {
    @Autowired PostService self;   // 注入代理
    public Post publish(Post p) {
        self.save(p);              // 走代理，事务生效
        return p;
    }
}
```

或直接把 `save` 逻辑合并进 `publish`，避免多余的内部调用。

---

## Q4（实操）：写一个 AOP 切面，拦截所有 `@Repository` 注解类的方法，打印"进入 DAO"+"执行耗时"，且当耗时 > 100ms 时额外打 WARN 日志。

**参考答案：**

```java
@Aspect
@Component
@Slf4j
public class DaoLoggingAspect {

    @Around("within(@org.springframework.stereotype.Repository *)")
    public Object logDao(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        log.debug("[DAO ENTER] {}", method);

        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 100) {
                log.warn("[DAO SLOW] {} cost={}ms", method, cost);
            } else {
                log.debug("[DAO EXIT] {} cost={}ms", method, cost);
            }
        }
    }
}
```

**切入点表达式说明**：

| 写法 | 含义 |
|------|------|
| `execution(* com.example.dao..*.*(..))` | 包路径下所有方法 |
| `within(@Repository *)` | 所有带 `@Repository` 注解的类 |
| `@annotation(Audited)` | 带指定注解的方法 |
| `bean(userService)` | 特定 Bean 名 |

**单测**：

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class, DaoLoggingAspect.class})
class DaoLoggingAspectTest {
    @Autowired FakeRepo fakeRepo;
    @Test void slow_method_logs_warn(@Captor ArgumentCaptor<String> cap) {
        fakeRepo.slowOp();  // SLEEP 200ms
        // 验证 logger 收到 WARN 级别日志
    }

    @Repository
    static class FakeRepo {
        public void slowOp() throws Exception { Thread.sleep(200); }
    }
}
```

**关键点**：

1. `try-finally` 确保切面不会吞异常。
2. `pjp.proceed()` 的返回值必须 return，否则被代理的方法无返回值。
3. **不要把 System.currentTimeMillis 的计算放在 catch 里**，异常路径也要统计耗时。
4. 生产里 DAO 层改用 29 章的 `SlowSqlInterceptor`（MyBatis 层），准确性更好（含 JDBC 时间）。

---

## Q5（综合）：你接手一个老项目，发现 Service 里到处是 `new` 关键字（`new MailSender()`, `new SmsService()`），单测写不了，发邮件短信的配置硬编码在 Java 文件里。请设计一个迁移方案，把这个项目纳入 Spring IoC 管理。

**参考答案：**

### 一、现状问题诊断

```java
@Service
public class NotifyService {
    private final MailSender mail = new MailSenderImpl("smtp.xxx.com", "admin", "pw123"); // ❌
    private final SmsService sms = new SmsServiceImpl("key123");                         // ❌

    public void sendWelcome(String email) {
        mail.send(email, "欢迎注册");
        sms.send(email, "验证码 1234");
    }
}
```

**问题**：

1. 配置硬编码，换环境要改代码。
2. 无法单测（`new` 了真实依赖）。
3. 每次 `NotifyService` 实例化都 new 一套昂贵对象。

### 二、迁移步骤

**Step 1：抽接口**

```java
public interface MailSender { void send(String to, String subject); }
public interface SmsService { void send(String phone, String msg); }
```

**Step 2：实现类注册为 Bean**

```java
@Configuration
@PropertySource("classpath:application.properties")
public class NotifyConfig {
    @Bean
    public MailSender mailSender(
        @Value("${mail.host}") String host,
        @Value("${mail.user}") String user,
        @Value("${mail.pass}") String pass) {
        return new MailSenderImpl(host, user, pass);
    }

    @Bean
    public SmsService smsService(@Value("${sms.key}") String key) {
        return new SmsServiceImpl(key);
    }
}
```

**Step 3：Service 改构造器注入**

```java
@Service @RequiredArgsConstructor
public class NotifyService {
    private final MailSender mail;
    private final SmsService sms;

    public void sendWelcome(String email) {
        mail.send(email, "欢迎注册");
        sms.send(email, "验证码");
    }
}
```

**Step 4：配置提到 properties（按 Profile 区分）**

```properties
# application-dev.properties
mail.host=smtp.mailtrap.io   # 开发沙箱
sms.key=test-key

# application-prod.properties
mail.host=smtp.xxx.com
sms.key=prod-key
```

**Step 5：单测 mock 依赖**

```java
@ExtendWith(MockitoExtension.class)
class NotifyServiceTest {
    @Mock MailSender mail;
    @Mock SmsService sms;
    @InjectMocks NotifyService svc;

    @Test
    void sendWelcome_calls_mail_and_sms() {
        svc.sendWelcome("a@x.com");
        verify(mail).send("a@x.com", "欢迎注册");
        verify(sms).send("a@x.com", "验证码");
    }
}
```

### 三、迁移风险控制

- **灰度**：先把测试环境迁，跑 1 周观察。
- **Key 保密**：prod 密码通过环境变量/Vault 注入，不进 Git：`-Dmail.pass=${MAIL_PASS}`。
- **回滚**：保留原来的 `new` 版本一周，确认无问题再删。

### 四、最终收益

| 指标 | 迁移前 | 迁移后 |
|------|-------|-------|
| 单测可写 | ❌ | ✅ mock 注入 |
| 配置可切 | ❌ | ✅ Profile |
| 对象复用 | ❌ 每次 new | ✅ 单例 |
| 三方库替换 | 改 Java 文件 | 改 `@Bean` 实现 |

**关键点**：迁移步骤的顺序是「先抽接口 → 再注册 Bean → 再改调用方」，确保每步可独立验证、可回滚。
