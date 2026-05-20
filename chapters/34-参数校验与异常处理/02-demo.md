# Chapter 34 参数校验与异常处理 - 实操 Demo

## Demo 目标

跑通：客户端发一个非法 JSON → 自动校验失败 → 全局异常处理器 → 返回 `{code: 10001, message: "title:不能为空"}`。

## 前置条件

- 基线 pom（已含 `spring-boot-starter-web` + `spring-boot-starter-validation`，见 [[chapters/21-Maven工程化/02-demo]]）。
- JDK 21。

## 增量依赖

无（`spring-boot-starter-validation` 已在基线里）。

## 完整示例代码

### ApiResponse + ErrorCode + BusinessException

```java
// com/example/blog/common/ApiResponse.java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data)              { return new ApiResponse<>(0, "ok", data); }
    public static <T> ApiResponse<T> fail(int code, String m){ return new ApiResponse<>(code, m, null); }
}

// com/example/blog/common/ErrorCode.java
public enum ErrorCode {
    PARAM_INVALID(10001, "参数错误"),
    UNAUTHORIZED(20001, "未登录"),
    ARTICLE_NOT_FOUND(40001, "文章不存在"),
    SYSTEM_ERROR(50000, "系统异常");

    public final int code;
    public final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
}

// com/example/blog/common/BusinessException.java
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(ErrorCode e) { super(e.message); this.code = e.code; }
    public BusinessException(ErrorCode e, String detail) { super(detail); this.code = e.code; }
    public int getCode() { return code; }
}
```

### DTO（含 jakarta.validation）

```java
// com/example/blog/article/CreateArticleRequest.java
import jakarta.validation.constraints.*;

public record CreateArticleRequest(
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    String title,

    @NotBlank(message = "内容不能为空")
    @Size(max = 50_000, message = "内容最长 5 万字")
    String content,

    @Pattern(regexp = "draft|published", message = "状态只能是 draft 或 published")
    String status
) {}
```

### Controller

```java
// com/example/blog/article/ArticleController.java
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/articles")
@Validated  // 关键：让 @PathVariable / @RequestParam 上的校验注解生效
public class ArticleController {

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CreateArticleRequest req) {
        // 校验失败不会进到这里
        return ApiResponse.ok(1L);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable @Min(value = 1, message = "id 必须 ≥ 1") Long id) {
        if (id == 999L) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        return ApiResponse.ok(Map.of("id", id, "title", "demo"));
    }
}
```

### 全局异常处理

```java
// com/example/blog/common/GlobalExceptionHandler.java
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid @RequestBody 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleBody(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
            .collect(Collectors.joining(";"));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.code, msg);
    }

    /** @Validated 类 + @PathVariable / @RequestParam 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<?> handleParam(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
            .map(cv -> cv.getPropertyPath() + ":" + cv.getMessage())
            .collect(Collectors.joining(";"));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.code, msg);
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleAll(Exception e) {
        log.error("unhandled", e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.code, ErrorCode.SYSTEM_ERROR.message);
    }
}
```

## 运行与验证

启动后用 curl 验证：

| 请求 | 预期响应 |
|---|---|
| `curl -X POST :8080/api/articles -H "Content-Type: application/json" -d '{}'` | `{"code":10001,"message":"title:标题不能为空;content:内容不能为空", ...}` |
| `curl :8080/api/articles/0` | `{"code":10001,"message":"id:id 必须 ≥ 1", ...}` |
| `curl :8080/api/articles/999` | `{"code":40001,"message":"文章不存在", ...}` |
| `curl :8080/api/articles/1` | `{"code":0,"message":"ok","data":{...}}` |

## 常见坑

- 校验注解全部不生效 → 检查包名是否 `jakarta.validation.constraints.*`。
- Path/Param 校验不生效 → 类上是否加 `@Validated`。
- 异常被吞 → `@ExceptionHandler(Exception.class)` 必须 `log.error(..., e)` 留栈。

## 建议提交

```bash
git commit -m "chapter 34: jakarta validation + global exception handler"
```
