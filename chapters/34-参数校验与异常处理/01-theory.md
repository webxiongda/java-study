# Chapter 34 参数校验与异常处理 - 理论

> 前置：[[33-RESTAPI设计]] [[32-SpringBoot入门]]
> 后续：[[35-分层架构与事务]]
> 优先级：L1  预计：4 小时

## 1. 为什么需要这一章

线上接口接收的入参 100% 是不可信的：必填没传、长度超限、格式错。如果在 Service 里写一堆 `if (xxx == null) throw new RuntimeException("...")`，业务代码会被淹没；前端拿到的错误信息也五花八门。本章解决：**校验集中到注解 + 异常集中到一个 `@RestControllerAdvice`**，前端拿到的永远是统一结构 `{code, message, data}`。

## 2. 核心概念

### 2.1 jakarta.validation（重要）

Spring Boot 3.x 全面切换到 jakarta 命名空间，**不要再 import `javax.validation.*`**。 详见 [[glossary#jakarta vs javax]]。

| 常用注解 | 作用 | 备注 |
|---|---|---|
| `@NotNull` | 不为 null | 包装类用 |
| `@NotBlank` | 非 null + 非空白字符串 | 仅 `String` |
| `@NotEmpty` | 非 null + 非空 | 集合 / 数组 / String |
| `@Size(min=, max=)` | 长度 / 大小 | String / 集合 |
| `@Min` / `@Max` | 数值上下限 | |
| `@Email` | 邮箱格式 | |
| `@Pattern(regexp=)` | 正则 | |
| `@Valid` | 触发**嵌套**对象的校验 | 加在参数或字段上 |
| `@Validated` | Spring 增强版，支持分组（`groups`） | 加在类 / 方法上 |

### 2.2 校验触发点

- `@RequestBody` 入参前加 `@Valid` → 触发 Bean 校验，失败抛 `MethodArgumentNotValidException`。
- `@RequestParam` / `@PathVariable` 上直接写 `@Min`、`@NotBlank` 等 → 失败抛 `ConstraintViolationException`（需要类上加 `@Validated`）。
- 嵌套对象字段上加 `@Valid` 才会递归校验。

### 2.3 统一响应体 ApiResponse

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "ok", data); }
    public static <T> ApiResponse<T> fail(int code, String message) { return new ApiResponse<>(code, message, null); }
}
```

### 2.4 错误码设计

- 不要用 HTTP 状态码当业务码（HTTP 200 ≠ 业务成功）。
- 分层：`1xxxx` 参数 / `2xxxx` 认证 / `3xxxx` 权限 / `4xxxx` 业务 / `5xxxx` 系统。
- 用枚举 `ErrorCode` 集中管理，禁止硬编码字符串。

### 2.5 全局异常处理

`@RestControllerAdvice` + `@ExceptionHandler` 把所有异常翻译成 `ApiResponse`：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
            .collect(Collectors.joining(";"));
        return ApiResponse.fail(10001, msg);
    }
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleAll(Exception e) {
        log.error("unhandled", e);
        return ApiResponse.fail(50000, "系统异常");
    }
}
```

## 3. 工作机制

```
Client → DispatcherServlet
       → HandlerMethodArgumentResolver（解析 @RequestBody）
       → Bean Validation（jakarta.validation.Validator，Hibernate Validator 是默认实现）
       → 校验失败 → 抛 MethodArgumentNotValidException → @RestControllerAdvice 捕获
       → 校验通过 → Controller → Service
```

## 4. 常见坑

- **坑 1**：用了 `javax.validation.*` → Spring Boot 3 完全不识别，注解无效。改 jakarta。
- **坑 2**：忘加 `@Valid` → 校验注解全部失效。
- **坑 3**：`@Validated`（类级）+ `@RequestParam @NotBlank` 才能校验路径/查询参数；漏了类上的 `@Validated` 不生效。
- **坑 4**：分组校验：创建用 `Create.class` 组，更新用 `Update.class` 组，避免 PUT 时强制要求 id 必传。
- **坑 5**：异常处理类不要 catch 后吞掉 → 必须 `log.error` 留栈。
- **坑 6**：自定义异常优先继承 `RuntimeException`，避免污染方法签名。

## 5. 与项目衔接

博客 API 里：登录入参（用户名/密码格式）、发文（标题长度 1-100、内容非空）、分页参数（page ≥ 1，size ≤ 50）全部走 `@Valid`；业务错误用 `BusinessException(ErrorCode.ARTICLE_NOT_FOUND)`，由全局处理器翻译成统一 `ApiResponse`。
