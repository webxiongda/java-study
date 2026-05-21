# Chapter 34 参数校验与异常处理 - 项目任务

## 任务概述

给博客 API 套上"防弹衣"：Bean Validation 阻止脏数据进 Service；全局异常处理器把任何错误转成结构化 JSON；自定义注解处理业务相关格式。

## 业务背景

32-33 章的接口还没有校验——传空 title、负 id、非法邮箱全都进了 Service，随机触发 NPE 或 DB 报错。这一章做完后，所有边界都在边界上拦截，Service 里不再做防御性 null 判断。

## 任务拆解

### Step 1：引入 Validation Starter

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Step 2：给所有 Request DTO 加注解

- `PostCreateReq.title`：`@NotBlank @Size(max=200)`
- `PostCreateReq.content`：`@NotBlank`
- `CommentCreateReq.content`：`@NotBlank @Size(max=1000)`
- `RegisterReq`：`@Email`、`@PhoneNumber`（自定义）、密码强度 Pattern
- 分页参数：`@Min(0) lastId`、`@Min(1) @Max(50) size`

### Step 3：全局异常处理器

写 `GlobalExceptionHandler`（`@RestControllerAdvice`），处理：

| 异常 | HTTP 状态 | 响应体 |
|------|---------|--------|
| `MethodArgumentNotValidException` | 400 | `{code:400, msg:"field: xxx", data:null}` |
| `ConstraintViolationException` | 400 | 同上（query param 校验） |
| `NotFoundException` | 404 | 404 |
| `ForbiddenException` | 403 | 403 |
| `BusinessException` | 422 | 422 |
| `Exception` | 500 | 通用提示，错误 log |

### Step 4：自定义 `@PhoneNumber` 注解

按 03-check Q4 实现，配单测（合法 + 非法 + null）。

### Step 5：注册接口校验

新增 `POST /api/v1/auth/register`：

- 邮箱/手机二选一（类级别自定义注解 `@AtLeastOneContact`）
- 密码 8 位含大小写数字
- 密码确认一致（Service 层校验）
- 邀请码可选，有就校验（Service 查库，不存在抛 404）

### Step 6：单测

```java
@WebMvcTest(PostController.class)
class PostControllerTest {
    @MockBean PostService postService;

    @Test void create_should_return_400_when_title_blank() throws Exception {
        mvc.perform(post("/api/v1/posts")
              .contentType(APPLICATION_JSON)
              .content("{\"title\":\"\",\"content\":\"body\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(400));
    }
}
```

每个校验规则写 1 个 `@Test`，全部断言 HTTP status 正确。

## 交付物

- [ ] 所有 Request DTO 加完校验注解
- [ ] `GlobalExceptionHandler.java`（覆盖 6 类异常）
- [ ] `@PhoneNumber` 注解 + Validator + 单测
- [ ] `@AtLeastOneContact` 类级别注解
- [ ] `POST /api/v1/auth/register` 完整校验
- [ ] `PostControllerTest.java`：≥ 8 个单测（校验 + 成功路径）

## 验收清单

| 项 | 标准 |
|----|------|
| 空 title → 400 | `{"code":400,"msg":"title: title 不能为空"}` |
| 不存在 id → 404 | `{"code":404,"msg":"post not found: 999"}` |
| Service 异常 → 500 | `{"code":500,"msg":"系统繁忙"}`（不泄漏堆栈）|
| 单测全绿 | `mvn test`，`@WebMvcTest` 快速验证 |
| 无防御性 null 判断 | grep `!= null` 在 Service 层应趋近于零 |

## 扩展挑战

1. **国际化错误消息**：把校验错误 message 放 `messages.properties`，实现中英文切换。
2. **Problem Details（RFC 9457）**：Spring 6 的 `ProblemDetail`，返回标准化错误体（带 `type` / `title` / `instance`）。
3. **参数篡改检测**：自定义过滤器检测请求体里有无 HTML 标签（XSS 防护），过滤或拒绝。
4. **接口限流**：`@RateLimit(perMinute=30)` 自定义注解 + AOP + Redis 计数，超限返回 429。
