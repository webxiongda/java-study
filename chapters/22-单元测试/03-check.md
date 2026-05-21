# Chapter 22 单元测试 - 自测题

## Q1（概念）：`@Mock`、`@Spy`、`@InjectMocks` 三者的区别？什么场景用 Spy？

**参考答案：**

| 注解 | 实质 | 默认方法行为 | 典型用途 |
|------|-----|------------|---------|
| `@Mock` | 全空壳对象 | 所有方法返回 null / 0 / 空集合 | 替换协作者（DAO、外部服务） |
| `@Spy` | 真实对象的"半身像" | 默认调用真实方法，只 stub 你指定的部分 | 测试遗留代码、想保留部分真实行为 |
| `@InjectMocks` | 被测对象本体 | Mockito 自动把 `@Mock`/`@Spy` 字段注入到它的构造器/setter/字段 | 被测主体（Service / Component） |

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock UserMapper userMapper;           // 全 mock
    @Spy  Calculator calculator = new Calculator();   // spy 必须给真实实例
    @InjectMocks OrderService orderService;            // 被测对象

    @Test
    void demo() {
        // @Mock：必须 stub，否则返回 null
        when(userMapper.selectById(1L)).thenReturn(new User(1L, "Alice"));

        // @Spy：默认走真实方法
        int real = calculator.add(2, 3);   // 真实返回 5
        // 但可以选择 stub 部分方法：
        doReturn(99).when(calculator).add(2, 3);
        int faked = calculator.add(2, 3);  // 现在返回 99

        // 注意 spy 的坑：不能用 when().thenReturn()，要用 doReturn().when()
        // when(calculator.divide(10, 0)).thenReturn(0);   // ❌ 会真的执行 divide → 抛异常
        // doReturn(0).when(calculator).divide(10, 0);     // ✅ 安全
    }
}
```

**Spy 的场景**：
- **遗留代码**：一个 1000 行的老类，新逻辑只 stub 其中 1 个方法，其他保持原样。
- **部分 mock**：被测对象自己调自己的 protected 方法，想验证某个内部步骤。

**优先级**：能用 `@Mock` 就别用 `@Spy`。Spy 容易出"半真半假"的陷阱（调用了不该被调的真实方法，副作用泄漏）。

---

## Q2（概念）：写一段调用了 `LocalDateTime.now()` 的业务代码，怎么测试它的"过期判断"逻辑？

**参考答案：**

**❌ 直接调 `LocalDateTime.now()` 的不可测代码：**

```java
public class TokenService {
    public boolean isExpired(Token token) {
        return token.getExpireAt().isBefore(LocalDateTime.now());   // 测试时无法控制 "now"
    }
}
```

**测试时为了模拟"过期" / "未过期"，要么 sleep 等真的过期（慢且不稳定），要么用 `MockedStatic` 强行 mock 静态方法（耦合实现）。**

**✅ 推荐方案：注入 `Clock` 抽象**

```java
public class TokenService {
    private final Clock clock;   // 通过构造器注入

    public TokenService(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(Token token) {
        return token.getExpireAt().isBefore(LocalDateTime.now(clock));   // 用注入的 clock
    }
}
```

生产环境：

```java
@Bean
Clock clock() {
    return Clock.systemDefaultZone();
}
```

测试：

```java
@Test
void isExpired_should_returnTrue_when_pastExpireAt() {
    // Arrange：把时钟拨到 2030-01-01 10:00:00
    Clock fixed = Clock.fixed(
        Instant.parse("2030-01-01T10:00:00Z"),
        ZoneOffset.UTC
    );
    TokenService service = new TokenService(fixed);
    Token token = new Token(LocalDateTime.parse("2030-01-01T09:00:00"));   // 1 小时前已过期

    // Act + Assert
    assertThat(service.isExpired(token)).isTrue();
}

@Test
void isExpired_should_returnFalse_when_futureExpireAt() {
    Clock fixed = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);
    TokenService service = new TokenService(fixed);
    Token token = new Token(LocalDateTime.parse("2030-01-01T11:00:00"));   // 1 小时后才过期

    assertThat(service.isExpired(token)).isFalse();
}
```

**核心思想**：把"对外界的依赖"（时间、随机数、UUID、文件系统）抽象成接口/对象注入进来，测试时替换为可控版本。这就是**依赖倒置（DIP）**在测试上的体现。

**进阶方案**：用 `MutableClock`（自定义实现），让单个测试里可以"快进时钟"：

```java
class MutableClock extends Clock {
    private Instant current;
    @Override public Instant instant() { return current; }
    public void advance(Duration d) { current = current.plus(d); }
    // ...
}
```

---

## Q3（实操）：下面这段测试代码有 5 个问题，找出并改正。

```java
public class UserServiceTest {

    UserService userService = new UserService();
    List<User> savedUsers = new ArrayList<>();

    @BeforeAll
    void setUp() {
        savedUsers.add(new User(1L, "Alice"));
    }

    @Test
    public void test1() {
        User u = userService.getById(1L);
        assertEquals(u.getName(), "Alice");
    }

    @Test
    public void test2() throws Exception {
        try {
            userService.divide(10, 0);
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    public void test3() {
        savedUsers.add(new User(2L, "Bob"));
        assertEquals(2, savedUsers.size());
    }
}
```

**参考答案：**

**问题 1：类没有 `@ExtendWith(MockitoExtension.class)`，且 `userService` 没有用 `@InjectMocks`**

如果 `UserService` 依赖 `UserMapper`，直接 `new` 出来 mapper 是 null，跑 `test1` 会 NPE。

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserMapper userMapper;
    @InjectMocks UserService userService;
    // ...
}
```

**问题 2：`@BeforeAll` 方法不是 `static`，会直接报错**

JUnit 5 默认 `@BeforeAll` 必须 static（因为类还没实例化）。两种修法：

```java
// 方案 A：加 static
@BeforeAll
static void setUp() { ... }

// 方案 B：让整个类共享一个实例
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest {
    @BeforeAll
    void setUp() { ... }   // 不用 static
}
```

但这里实际想要的是"每个测试前重置"，应该用 `@BeforeEach`。

**问题 3：测试方法名 `test1`、`test2` 无信息量**

```java
// 改成自描述命名：
@Test void getById_should_return_user_when_idExists() { ... }
@Test void divide_should_throw_when_divisorIsZero() { ... }
@Test void savedUsers_should_grow_when_userAdded() { ... }
```

**问题 4：`assertEquals` 参数顺序反了**

JUnit 5 的签名是 `assertEquals(expected, actual)`，写反虽然结果一样但报错信息会误导。

```java
assertEquals("Alice", u.getName());        // 正确顺序
// 或者用 AssertJ：
assertThat(u.getName()).isEqualTo("Alice");
```

**问题 5：异常断言写法粗糙**

`catch(Exception e) {}` 会吞掉**所有**异常，包括 NullPointerException 之类的真正 bug。

```java
// AssertJ 推荐写法
assertThatThrownBy(() -> userService.divide(10, 0))
    .isInstanceOf(ArithmeticException.class)
    .hasMessageContaining("/ by zero");

// 或者 JUnit 5：
ArithmeticException e = assertThrows(
    ArithmeticException.class,
    () -> userService.divide(10, 0)
);
assertThat(e.getMessage()).contains("/ by zero");
```

**附加问题（隐性）**：`test3` 修改了 `savedUsers` 字段，会污染其他测试的状态。要么改 `@BeforeEach` 每次重置，要么改用局部变量。

---

## Q4（实操）：用参数化测试 `@ParameterizedTest` 测试一个邮箱校验方法

**题目**：写一个 `EmailValidator.isValid(String email)` 方法的测试，用 `@ParameterizedTest` 覆盖：3 个合法邮箱、5 个非法邮箱、null、空串。要求一次性把 10 个用例写完。

**参考答案：**

被测方法：

```java
public class EmailValidator {
    private static final Pattern PATTERN =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static boolean isValid(String email) {
        return email != null && !email.isBlank() && PATTERN.matcher(email).matches();
    }
}
```

测试：

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.assertThat;

class EmailValidatorTest {

    /* ---------- 合法邮箱 ---------- */
    @ParameterizedTest(name = "[{index}] {0} should be valid")
    @ValueSource(strings = {
        "alice@example.com",
        "user.name+tag@sub.domain.co.uk",
        "x@y.io"
    })
    void should_acceptValidEmails(String email) {
        assertThat(EmailValidator.isValid(email))
            .as("expected '%s' to be valid", email)
            .isTrue();
    }

    /* ---------- 非法邮箱 ---------- */
    @ParameterizedTest(name = "[{index}] {0} should be invalid")
    @ValueSource(strings = {
        "no-at-sign.com",          // 缺 @
        "@no-local.com",           // 缺 local part
        "user@",                   // 缺 domain
        "user@.com",               // domain 以 . 开头
        "user@domain"              // 缺顶级域
    })
    void should_rejectInvalidEmails(String email) {
        assertThat(EmailValidator.isValid(email)).isFalse();
    }

    /* ---------- null / 空 ---------- */
    @ParameterizedTest
    @NullAndEmptySource         // 自动注入 null 和 ""
    @ValueSource(strings = {"   ", "\t", "\n"})    // 再加几个全空白
    void should_rejectNullOrBlank(String email) {
        assertThat(EmailValidator.isValid(email)).isFalse();
    }
}
```

**关键点：**

- `@ValueSource` 支持 `strings`、`ints`、`longs`、`doubles` 等基础类型。
- `@NullSource` / `@EmptySource` / `@NullAndEmptySource` 是为这两个"老大难"边界值定制的。
- 想传"对象"或多个参数，用 `@CsvSource`：

```java
@ParameterizedTest
@CsvSource({
    "1, 2, 3",
    "10, 20, 30",
    "-5, 5, 0"
})
void add_should_sum(int a, int b, int expected) {
    assertThat(calculator.add(a, b)).isEqualTo(expected);
}
```

- 大量数据放外部文件：`@CsvFileSource(resources = "/test-data.csv")`。
- 自定义对象：`@MethodSource("provideTestCases")` + 一个返回 `Stream<Arguments>` 的静态方法。

**写参数化的好处**：原本 10 个 `@Test` 方法塞进 3 个参数化方法，**新增用例只改数据不改逻辑**，且报错时 `[index] arguments` 明确指出哪条数据挂了。

---

## Q5（综合）：用 Mockito + ArgumentCaptor 测一个发邮件 Service

**题目**：

```java
@Service
public class RegistrationService {
    private final UserMapper userMapper;
    private final EmailSender emailSender;

    public RegistrationService(UserMapper userMapper, EmailSender emailSender) {
        this.userMapper = userMapper;
        this.emailSender = emailSender;
    }

    public void register(String email, String password) {
        if (userMapper.countByEmail(email) > 0) {
            throw new DuplicateEmailException(email);
        }
        User user = new User(null, email, hash(password), LocalDateTime.now());
        userMapper.insert(user);
        emailSender.send(email, "Welcome", "Hi " + email + ", your account is ready!");
    }

    private String hash(String pwd) { return "hashed-" + pwd; }
}
```

要求 3 个测试用例：
1. 正常注册：验证插入和发邮件都被调用，**断言邮件标题和内容正确**。
2. 邮箱重复：验证抛异常，**且 insert 和 send 都没被调用**。
3. 发邮件失败（`EmailSender.send` 抛异常）：验证插入已发生，异常向上传播。

**参考答案：**

```java
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserMapper userMapper;
    @Mock EmailSender emailSender;
    @InjectMocks RegistrationService service;

    /* ========== 用例 1：正常注册 ========== */
    @Test
    void register_should_insertAndSendEmail_when_emailIsFree() {
        // Given
        when(userMapper.countByEmail("alice@x.com")).thenReturn(0);

        // When
        service.register("alice@x.com", "pwd123");

        // Then —— 验证 insert 被调用，且参数正确
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@x.com");
        assertThat(saved.getPassword()).isEqualTo("hashed-pwd123");
        assertThat(saved.getCreatedAt()).isNotNull();

        // 验证邮件内容
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("alice@x.com"), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("Welcome");
        assertThat(bodyCaptor.getValue())
            .startsWith("Hi alice@x.com")
            .contains("your account is ready");
    }

    /* ========== 用例 2：邮箱重复 ========== */
    @Test
    void register_should_throw_when_emailAlreadyExists() {
        // Given
        when(userMapper.countByEmail("alice@x.com")).thenReturn(1);

        // When + Then
        assertThatThrownBy(() -> service.register("alice@x.com", "pwd"))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("alice@x.com");

        // 关键：验证副作用没发生
        verify(userMapper, never()).insert(any());
        verify(emailSender, never()).send(any(), any(), any());
    }

    /* ========== 用例 3：发邮件失败 ========== */
    @Test
    void register_should_persistUser_even_when_emailSenderFails() {
        // Given
        when(userMapper.countByEmail("bob@x.com")).thenReturn(0);
        doThrow(new RuntimeException("SMTP timeout"))
            .when(emailSender).send(any(), any(), any());

        // When + Then
        assertThatThrownBy(() -> service.register("bob@x.com", "pwd"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("SMTP");

        // 验证用户已经被插入（业务上可能要求事务回滚，但当前实现没事务）
        verify(userMapper).insert(any(User.class));
        verify(emailSender).send(eq("bob@x.com"), anyString(), anyString());
    }
}
```

**学到的关键技巧：**

1. **`ArgumentCaptor`** —— 验证传给 mock 的参数。当参数是复杂对象时，比 `eq(...)` 灵活得多（不用重写 equals）。
2. **`verify(mock, never()).method(any())`** —— "证明它没发生"，比"找不到它的痕迹"严谨。
3. **`doThrow(...).when(mock).voidMethod(...)`** —— 给返回 void 的方法打桩抛异常的唯一写法（`when().thenThrow()` 编译不过）。
4. **`eq()` 与 captor 必须配套** —— `verify(emailSender).send(eq("..."), captor.capture(), captor2.capture())` 中如果第一个参数用裸值（不包 `eq`），Mockito 会报 `InvalidUseOfMatchersException`。规则：**要么全用 matcher，要么全用裸值**。

**面试加分点**：用例 3 暴露了业务设计问题——发邮件失败时用户已经入库，下次重试注册会撞重复。**生产代码应该把发邮件改成异步队列**（第 48 章），让"注册"和"通知"两件事解耦。这种"从测试用例反推设计缺陷"的能力是高级工程师的标志。
