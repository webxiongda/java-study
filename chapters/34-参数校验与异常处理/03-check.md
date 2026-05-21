# Chapter 34 参数校验与异常处理 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：Bean Validation 中 `@Valid` 和 `@Validated` 有什么区别？分组校验如何实现？

**参考答案：**

| 维度 | `@Valid` | `@Validated` |
|------|----------|-------------|
| 来源 | `jakarta.validation`（标准） | Spring 扩展 |
| 分组 | ❌ 不支持 | ✅ `@Validated(GroupA.class)` |
| 嵌套校验 | ✅ 触发嵌套对象校验 | ✅ |
| 适用位置 | 方法参数 / 字段 | 类 / 方法参数 |

**分组校验**：

```java
// 定义分组接口
public interface Create {}
public interface Update {}

// DTO 上标注分组
public record PostReq(
    @NotBlank(groups = Create.class) String title,    // 创建必填
    @Positive(groups = {Create.class, Update.class}) Long id  // 更新需要
) {}

// Controller 指定分组
@PostMapping
public Result<PostVO> create(@Validated(Create.class) @RequestBody PostReq req) {}

@PutMapping("/{id}")
public Result<PostVO> update(@Validated(Update.class) @RequestBody PostReq req) {}
```

**什么时候用分组**：同一 DTO 创建和更新时规则不同。更简单的做法是直接写两个 DTO（`CreateReq` / `UpdateReq`），避免分组复杂度。

**关键点**：`@Valid` 够用时就用 `@Valid`，分组校验需求时才上 `@Validated`。

---

## Q2（概念）：`@RestControllerAdvice` 的工作原理是什么？为什么不应该在 Controller 里 `try-catch` 业务异常？

**参考答案：**

**工作原理**：

`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`，是一个 AOP 切面，拦截所有 `@Controller` 抛出的异常，根据 `@ExceptionHandler` 匹配后处理。

```
请求 → DispatcherServlet → Handler → 抛出异常
↓
ExceptionHandlerExceptionResolver → @RestControllerAdvice → 返回 JSON
```

**匹配规则**：

- 先找最具体的异常类型（子类 > 父类）。
- 同一异常有多个 Handler，取最近的（本 Controller > Advice）。

**为什么不在 Controller catch**：

```java
// ❌ 每个 Controller 都要写
@GetMapping("/{id}")
public Result<PostVO> get(@PathVariable Long id) {
    try {
        return Result.ok(postService.detail(id));
    } catch (PostNotFoundException e) {
        return Result.error(404, e.getMessage());
    } catch (Exception e) {
        return Result.error(500, "系统繁忙");
    }
}

// ✅ Controller 只写业务，异常交给全局处理器
@GetMapping("/{id}")
public Result<PostVO> get(@PathVariable Long id) {
    return Result.ok(postService.detail(id));   // 简洁
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PostNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFound(PostNotFoundException e) {
        return Result.error(404, e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleAll(Exception e) {
        log.error("unexpected error", e);
        return Result.error(500, "系统繁忙");
    }
}
```

**优点**：

1. Controller 代码精简，只关心 happy path。
2. 异常处理逻辑集中，一处改动全生效。
3. 日志也集中在一处打，不会散落在各个 catch 里。

---

## Q3（实操）：以下代码有 6 处校验 / 异常处理问题，找出并改正。

```java
@RestController
public class PostController {

    @PostMapping("/api/posts")
    public Result<PostVO> create(@RequestBody PostCreateReq req) {
        if (req.getTitle() == null || req.getTitle().isEmpty()) {
            return Result.error(400, "title 不能为空");   // ①
        }
        if (req.getTitle().length() > 200) {
            return Result.error(400, "title 过长");       // ②
        }
        try {
            PostVO vo = postService.create(req);
            return Result.ok(vo);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());     // ③④
        }
    }

    @GetMapping("/api/posts/{id}")
    public PostVO get(@PathVariable Long id) {            // ⑤
        PostVO vo = postService.detail(id);
        if (vo == null) return null;                      // ⑥
        return vo;
    }
}
```

**参考答案（改后）：**

```java
// DTO 上加校验注解（解决 ① ②）
public record PostCreateReq(
    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 不超过 200 字")
    String title,
    @Size(max = 500) String summary,
    @NotBlank String content,
    boolean publish
) {}

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ① ② 靠 @Valid 自动校验，不手写 if-null
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostVO> create(@Valid @RequestBody PostCreateReq req) {
        return Result.ok(postService.create(req));
        // ③ ④ 异常交给全局 @RestControllerAdvice，不 try-catch
    }

    // ⑤ 返回 Result<PostVO> 而非裸 Entity/VO
    @GetMapping("/{id}")
    public Result<PostVO> get(@PathVariable Long id) {
        return Result.ok(postService.detail(id));   // ⑥ Service 抛 NotFoundException，不返回 null
    }
}

// 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFound(NotFoundException ex) {
        return Result.error(404, ex.getMessage());
    }
}
```

**6 处问题**：

1. 手写 null 判断 → 改 `@NotBlank`。
2. 手写长度判断 → 改 `@Size(max=200)`。
3. `try-catch Exception` catch 太宽 → 全局处理器做，Controller 不 catch。
4. `e.getMessage()` 直接返回给用户 → 可能泄漏内部信息，记 log，返回通用提示。
5. 返回裸 `PostVO` → 应包 `Result<PostVO>`。
6. `return null` → Service 层抛 `NotFoundException`，全局处理器返 404。

---

## Q4（实操）：写自定义注解 `@PhoneNumber`，校验中国大陆手机号格式（1 开头，11 位，第 2 位 3-9）。并写单测验证合法与非法值。

**参考答案：**

```java
// 注解定义
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {
    String message() default "手机号格式不正确";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 校验器实现
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
    private static final Pattern PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;   // null 留给 @NotNull 处理
        return PATTERN.matcher(value).matches();
    }
}

// 使用
public record RegisterReq(
    @NotBlank String email,
    @PhoneNumber String phone    // 可选，填了就校验
) {}
```

**单测**：

```java
class PhoneNumberValidatorTest {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    record Req(@PhoneNumber String phone) {}

    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "18900000001", "19900000001"})
    void valid_phones(String phone) {
        assertThat(validator.validate(new Req(phone))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "1380013800",  "138001380001", "023-12345678"})
    void invalid_phones(String phone) {
        assertThat(validator.validate(new Req(phone))).isNotEmpty();
    }

    @Test
    void null_passes_validation() {
        assertThat(validator.validate(new Req(null))).isEmpty();
    }
}
```

**关键点**：

1. `isValid` 对 `null` 返回 `true`，null 的责任交给 `@NotNull`（单一职责）。
2. 正则 `^1[3-9]\\d{9}$`：`^$` 锚定，`[3-9]` 第 2 位约束，`\\d{9}` 后 9 位数字，共 11 位。
3. `@ParameterizedTest @ValueSource` 简洁覆盖多个合法/非法样本。
4. 不要在 `isValid` 里抛异常，返回 `false` 即可，框架会把 `message` 包成 violation。

---

## Q5（综合）：博客注册接口要求：邮箱 / 手机二选一（至少一个）+ 密码强度（8位、大小写、数字）+ 重复密码一致 + 邀请码可选但填了必须合法。请设计校验方案，并处理"邀请码不存在"的业务异常。

**参考答案：**

### 一、DTO 设计

```java
@AtLeastOneContact    // 自定义类级别注解，邮箱/手机二选一
public record RegisterReq(
    @Email @Size(max=120)
    String email,

    @PhoneNumber
    String phone,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "密码需 8 位以上，含大小写字母和数字"
    )
    String password,

    @NotBlank
    String passwordConfirm,

    @Size(min=6, max=6)
    String inviteCode
) {}
```

### 二、类级别注解（邮箱/手机二选一）

```java
@Documented
@Constraint(validatedBy = AtLeastOneContactValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AtLeastOneContact {
    String message() default "邮箱或手机号至少填一个";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class AtLeastOneContactValidator
    implements ConstraintValidator<AtLeastOneContact, RegisterReq> {
    @Override
    public boolean isValid(RegisterReq r, ConstraintValidatorContext ctx) {
        return (r.email() != null && !r.email().isBlank())
            || (r.phone() != null && !r.phone().isBlank());
    }
}
```

### 三、密码一致性（Service 层验证，不在 Bean Validation）

```java
@Service
public class AuthService {
    @Transactional
    public UserVO register(RegisterReq req) {
        // 业务规则：密码一致性
        if (!req.password().equals(req.passwordConfirm())) {
            throw new ValidationException("两次密码不一致");
        }
        // 邀请码业务逻辑
        if (req.inviteCode() != null && !req.inviteCode().isBlank()) {
            InviteCode code = inviteCodeService.findValid(req.inviteCode())
                .orElseThrow(() -> new NotFoundException("邀请码无效或已使用"));
            inviteCodeService.markUsed(code.id());
        }
        // 创建用户...
    }
}
```

### 四、异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Bean Validation 失败 → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        return Result.error(400, String.join("; ", errors));
    }

    // 业务校验失败 → 422 Unprocessable Entity
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result<?> handleBizValidation(ValidationException ex) {
        return Result.error(422, ex.getMessage());
    }

    // 业务资源不存在 → 404
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFound(NotFoundException ex) {
        return Result.error(404, ex.getMessage());
    }
}
```

### 五、curl 验证

```bash
# 邮箱/手机都不填 → 400
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"password":"Hello1234","passwordConfirm":"Hello1234"}'
# {"code":400,"msg":"邮箱或手机号至少填一个"}

# 密码不一致 → 422
curl -X POST ... \
  -d '{"email":"a@x.com","password":"Hello1234","passwordConfirm":"Hello12345"}'
# {"code":422,"msg":"两次密码不一致"}

# 邀请码无效 → 404
curl -X POST ... \
  -d '{"email":"a@x.com","password":"Hello1234","passwordConfirm":"Hello1234","inviteCode":"BADCOD"}'
# {"code":404,"msg":"邀请码无效或已使用"}
```

**关键点**：

1. **Bean Validation 处理格式问题**（`@Email` / `@Pattern` / 自定义 `@PhoneNumber`）。
2. **Service 处理业务规则**（密码一致性 / 邀请码逻辑），不往 DTO 里堆 / ConstraintValidator 里查库。
3. **HTTP 状态码分层**：400=格式错，422=业务不合法，404=资源不存在，500=未知错误。
4. **密码强度用正则**：lookahead `(?=.*[a-z])` 断言"后面必须有小写"，不消耗字符，依次断言大小写和数字。
