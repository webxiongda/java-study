# Chapter 23 日志体系 - 自测题

## Q1（概念）：SLF4J 和 Logback 是什么关系？为什么 Spring Boot 默认要走 SLF4J？

**参考答案：**

| 角色 | 包 | 职责 |
|------|---|------|
| 门面（Facade） | `slf4j-api` | 定义 `Logger`、`LoggerFactory` 接口，**不做任何输出** |
| 实现（Backend） | `logback-classic`、`log4j-core`、`java.util.logging` | 真正负责格式化、过滤、写文件 |
| 桥接（Bridge） | `jcl-over-slf4j`、`log4j-over-slf4j`、`jul-to-slf4j` | 把其他日志框架的调用劫持到 SLF4J |

依赖图（Spring Boot）：

```
你的代码
   ↓ org.slf4j.Logger
slf4j-api
   ↓ (StaticLoggerBinder)
logback-classic ← logback-core
```

**为什么用门面**：

1. **可替换**：未来想从 Logback 换 Log4j2，只改 pom（去掉 `spring-boot-starter-logging` + 加 `spring-boot-starter-log4j2`），代码 0 改动。
2. **统一**：第三方库可能用 JCL、JUL、Log4j 1.x，桥接器把它们全部收口到 SLF4J，最终走同一个 Logback 配置——不然会"半夜一个文件，白天一个文件"。
3. **性能**：占位符 `log.debug("x={}", x)` 在级别未开启时不做参数 `toString()`，比 `log.debug("x=" + x)` 节省 CPU。

**反例：直接用 Logback API**

```java
// ❌ 绑死 Logback
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
```

未来换实现就是改全工程的 import。

**结论**：**代码层永远只 import `org.slf4j.*`，运行时由 pom 决定实现**。这是 12-factor app "logs" 原则的落地姿势。

---

## Q2（概念）：MDC 是干什么的？为什么用完一定要清？给一个不清理会出 bug 的具体场景。

**参考答案：**

**MDC（Mapped Diagnostic Context）**：本质是 `ThreadLocal<Map<String,String>>`，让一个线程在多次 `log.xxx()` 之间共享一组上下文 key-value。最常见的用法是**链路追踪 traceId**。

```java
// 在 Filter / Interceptor 入口写入
MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
MDC.put("userId", String.valueOf(currentUser.getId()));

// 业务代码无感知
log.info("query started");          // 输出会自动带 [traceId][userId]
userMapper.selectById(1L);
log.info("query finished");

// 出口必须清
MDC.clear();
```

Logback pattern：`%-5level [%X{traceId:-NA}] [%X{userId:-anon}] %msg%n`

**为什么必须清——Tomcat 线程池复用陷阱：**

Tomcat 默认 200 个线程的池，请求结束后线程**不死，回池等下个请求**。

```
请求 A 进来 → 线程 #42 → MDC.put(traceId="aaa") → 业务 → 返回
   （没 clear！）
请求 B 进来 → 线程 #42 复用 → traceId 还是 "aaa"！
   → 整个 B 请求的日志全打着 A 的 traceId
   → 线上排查时，根据 traceId 搜出的日志混着两个请求
```

更严重的：

```
请求 A：管理员操作 → MDC.put(role="ADMIN")
请求 B：普通用户操作（同一个线程）→ 没设 role，但 MDC 还是 ADMIN
→ 日志里普通用户被记成 ADMIN，**安全审计错误归因**
```

**正确写法**：永远在 finally 里清

```java
public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
    MDC.put("traceId", extractOrGenerateTraceId(req));
    try {
        chain.doFilter(req, resp);
    } finally {
        MDC.clear();   // ← 关键
    }
}
```

**进阶坑：异步线程**

`@Async` / `CompletableFuture.supplyAsync` 切到新线程时，MDC 会丢失（新线程的 ThreadLocal 是空的）。解决：

```java
Map<String,String> snapshot = MDC.getCopyOfContextMap();
CompletableFuture.supplyAsync(() -> {
    MDC.setContextMap(snapshot);
    try {
        return doWork();
    } finally {
        MDC.clear();
    }
}, executor);
```

Spring 提供 `TaskDecorator` 可在 `ThreadPoolTaskExecutor` 里自动做这个 copy（生产推荐）。

---

## Q3（实操）：以下日志代码有 6 个问题，逐个找出并改正

```java
@Service
public class UserService {
    Logger logger = Logger.getLogger("UserService");

    public User register(String email, String password) {
        logger.info("register called: email=" + email + ", password=" + password);
        try {
            User u = userMapper.findByEmail(email);
            if (u != null) {
                logger.warning("email already exists");
                throw new DuplicateEmailException();
            }
            User created = userMapper.insert(new User(email, password));
            logger.info("register success");
            return created;
        } catch (Exception e) {
            logger.error("error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

**参考答案：**

**问题 1：用了错误的 Logger 类（`java.util.logging.Logger`）**

```java
// ❌
import java.util.logging.Logger;
Logger logger = Logger.getLogger("UserService");

// ✅
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger log = LoggerFactory.getLogger(UserService.class);
// 或用 Lombok：@Slf4j
```

`getLogger(class)` 拿到的 logger 名是全限定类名（`com.example.UserService`），可以按包配级别；用字符串 `"UserService"` 则无法。

**问题 2：日志里直接打了密码**

```java
// ❌ 严重合规问题
log.info("register called: email=" + email + ", password=" + password);

// ✅ 脱敏
log.info("register called: email={}", email);     // 永远不打密码、token、身份证
```

**问题 3：用字符串拼接，开销永远存在**

```java
// ❌ 即使日志被关闭，"+" 也已经执行
log.info("register called: email=" + email);

// ✅ 占位符，级别匹配时才拼
log.info("register called: email={}", email);
```

**问题 4：`logger.warning` 是 JUL API，SLF4J 应该用 `warn`**

```java
// ✅
log.warn("email already exists: {}", email);
```

**问题 5：异常只记 message，丢了 stack trace**

```java
// ❌ 找不到根因
log.error("error: " + e.getMessage());

// ✅ 把异常作为最后一个参数（不占 {} 槽位）
log.error("register failed for email={}", email, e);
```

**问题 6：捕获 Exception → 重新包装成 RuntimeException 把根因 e 丢了？**

实际 `new RuntimeException(e)` 保留了 cause，没丢。但**不应该 catch 所有 Exception**（吞掉了 NullPointerException、IllegalArgumentException 等真正 bug），且这里 `DuplicateEmailException` 是业务异常，应该让它向上传不要被 catch。

```java
// ✅ 改成只让真正的"业务异常"通过，其他原样上抛
public User register(String email, String password) {
    log.info("register called: email={}", email);
    User existing = userMapper.findByEmail(email);
    if (existing != null) {
        log.warn("email already exists: {}", email);
        throw new DuplicateEmailException(email);
    }
    User created = userMapper.insert(new User(email, hash(password)));
    log.info("register success: id={}, email={}", created.getId(), email);
    return created;
    // 业务里"无需感知"的异常（IO、SQL）让 @ControllerAdvice 统一接管 + 记日志
}
```

**完整修正后版本：**

```java
@Slf4j
@Service
public class UserService {
    private final UserMapper userMapper;

    public User register(String email, String password) {
        log.info("register called: email={}", email);

        if (userMapper.findByEmail(email) != null) {
            log.warn("email already exists: {}", email);
            throw new DuplicateEmailException(email);
        }
        User created = userMapper.insert(new User(email, hash(password)));
        log.info("register success: id={}, email={}", created.getId(), email);
        return created;
    }
}
```

---

## Q4（实操）：写一个 Servlet Filter，给每个 HTTP 请求加 `traceId`，并在响应头里返回，便于前端和后端联调对照。

**参考答案：**

```java
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)   // 越小越早执行，要保证在业务 Filter 之前
public class TraceIdFilter implements Filter {

    private static final String HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // 1. 优先沿用上游传过来的 traceId（链路追踪场景），没有则生成
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 2. 写入 MDC，整个请求线程内所有日志都会自动带上
        MDC.put(MDC_KEY, traceId);

        // 3. 回写到响应头，前端 / Nginx 可以拿到
        response.setHeader(HEADER, traceId);

        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(MDC_KEY);   // 一定要清！比 MDC.clear() 更精准，不影响其他 key
        }
    }
}
```

`logback-spring.xml` 配套 pattern：

```xml
<pattern>%d{HH:mm:ss.SSS} %-5level [%X{traceId:-}] %logger{36} - %msg%n</pattern>
```

**验证步骤：**

```bash
# 启动后访问任意接口
curl -i http://localhost:8080/api/users/1

# 响应头里能看到
HTTP/1.1 200 OK
X-Trace-Id: a1b2c3d4e5f60718
...

# 服务端日志
10:30:01.234 INFO  [a1b2c3d4e5f60718] c.e.b.UserController - getUser id=1
10:30:01.250 INFO  [a1b2c3d4e5f60718] c.e.b.UserService    - load from DB
```

**生产强化：**

1. **配合 OpenTelemetry / Sleuth**：用规范的 W3C `traceparent` 头而不是自造 `X-Trace-Id`。
2. **异步线程透传**：配 `ThreadPoolTaskExecutor` + `TaskDecorator` 把 MDC 复制到子线程。
3. **不可信来源**：来自互联网的请求，**生成自己的 traceId**而不是直接信任 header（防伪造）。
4. **关联前端**：前端 JS 在每次请求时生成短 traceId 放进 header，再把响应里的 `X-Trace-Id` 打到 Sentry / 控制台——出现报错时直接拿 traceId 在 ELK 反查。

---

## Q5（综合）：你被分配排查一个线上问题——"用户反馈下单成功但订单查不到"，已知日志在 ELK 里。请给出排查的日志命令序列和检查项。

**参考答案：**

**前置假设**：

- 服务有 traceId（Q4 已配）；
- 日志已结构化输出，按级别拆 `app.log` / `error.log`；
- 业务日志命名规范：`order.create.start` / `order.create.success` / `order.create.fail`。

**排查步骤：**

### 第 1 步：根据用户提供的下单时间 + 用户 ID 锁定 traceId

```
# Kibana / ELK 查询
service:"order-service" AND userId:"12345" AND msg:"order.create.start"
   AND @timestamp:[2024-06-01T10:00:00 TO 2024-06-01T10:05:00]
```

拿到 `traceId=abc12345`。

### 第 2 步：用 traceId 拉出该请求的全链路日志

```
traceId:"abc12345"
```

按时间排序，看到：
```
10:01:23.001 INFO  [abc12345] OrderController  - order.create.start userId=12345
10:01:23.050 INFO  [abc12345] OrderService     - validate ok
10:01:23.100 INFO  [abc12345] OrderMapper      - INSERT INTO orders ...
10:01:23.110 INFO  [abc12345] OrderService     - order.create.success orderId=99
10:01:23.115 WARN  [abc12345] PaymentService   - payment timeout, will rollback
10:01:23.120 ERROR [abc12345] OrderService     - TransactionRolledBackException ...
```

**结论**：业务上"成功提示"先返回了用户，但后续支付超时触发**事务回滚**——`orders` 行被回滚，用户看到的"成功"是假的。

### 第 3 步：交叉验证数据库

```sql
SELECT id, status, created_at FROM orders WHERE user_id=12345 ORDER BY created_at DESC LIMIT 5;
-- 看不到 orderId=99，确认确实被回滚
```

### 第 4 步：找根因——是不是网络抖动？

```
traceId:"abc12345" AND logger:"PaymentService"
```

看到 `payment timeout after 3000ms`。再查支付下游：

```
service:"payment-service" AND traceId:"abc12345"
```

发现支付服务**没收到任何请求**——网络层面失败。结合监控指标确认那一秒钢厂的 packet loss。

### 第 5 步：根因分析 + 改进项

| 维度 | 现状 | 改进 |
|------|-----|------|
| 错误处理 | 给用户提示"成功"过早 | 把"返回成功"放到事务 commit 之后 |
| 重试 | 没重试 | 支付加 3 次重试 + 指数退避 |
| 用户体验 | 用户不知道支付失败 | 异步推送支付结果 |
| 监控 | 网络抖动没告警 | 加支付成功率告警阈值 |

### 排查命令清单（沉淀到 runbook）

```
# 1. 锁定 traceId
service:"<svc>" AND userId:"<uid>" AND msg:"<biz>.start" AND @timestamp:[...]

# 2. 拉全链路
traceId:"<traceId>"

# 3. 只看 ERROR / WARN
traceId:"<traceId>" AND level:(ERROR OR WARN)

# 4. 看 SQL（如果日志开了 MyBatis SQL 输出）
traceId:"<traceId>" AND logger:"*Mapper*"

# 5. 跨服务追踪
traceparent:"<traceparent>" 或者
traceId:"<traceId>" AND service:*

# 6. 数据库对账
SELECT * FROM <table> WHERE id=<bizId>;
```

**这道题考的不是 API 用法，而是「日志的设计是否足以支撑生产排查」**——traceId 透传、关键节点 INFO、失败 ERROR + stack、SQL 可观测、能跨服务串联。日志不是"写完就完"，而是**未来某个凌晨 3 点你能不能找到 root cause** 的契约。
