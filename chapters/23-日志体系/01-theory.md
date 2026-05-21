# Chapter 23 日志体系 - 理论篇

## 一、学习定位

日志是后端线上排查问题的**唯一现场**。本章把 **SLF4J（门面） + Logback（实现） + MDC（链路）** 三件套讲透，后面所有章节都依赖它输出可观测信号。

- 优先级：L1 必须掌握
- 预计投入：2 小时
- 阶段产出：`blog-api` 接入结构化日志 + 按级别滚动归档 + 链路 traceId

## 二、核心概念

### 1. SLF4J 门面 vs Logback 实现

SLF4J（Simple Logging Facade for Java）是 API 规范，不做实际输出；Logback / Log4j2 是实现。代码里只 `import org.slf4j.*`，不绑定具体实现。

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    // 推荐用 Lombok：@Slf4j 自动生成上面这行
}
```

依赖图：你的代码 → `slf4j-api` → `logback-classic`（运行时）→ Logback 实现。Spring Boot 默认走这条路。

**为什么用门面**？换实现时（Logback ↔ Log4j2）只改 pom，不改代码。

### 2. 日志级别（TRACE < DEBUG < INFO < WARN < ERROR）

| 级别 | 何时用 | 生产是否开 |
|------|-------|-----------|
| TRACE | 极细粒度，循环里每次迭代 | 否 |
| DEBUG | 调试细节，参数、SQL | 默认否，排查时打开 |
| INFO | 关键业务节点：注册成功、订单创建 | ✅ |
| WARN | 可恢复异常：重试成功、降级触发 | ✅ |
| ERROR | 业务失败、异常未被捕获 | ✅ 且要告警 |

**规则**：低于阈值的日志**完全不打印**。生产设 INFO，DEBUG 信息消失。

### 3. 参数化日志（占位符）

```java
// ❌ 字符串拼接：即使日志没打也付出了拼接代价
log.debug("User " + userId + " logged in from " + ip);

// ✅ 占位符：只在级别匹配时才拼接
log.debug("User {} logged in from {}", userId, ip);

// ✅ 异常永远放最后一个参数（不占用 {} 槽位）
log.error("Failed to load user {}", userId, e);
```

### 4. MDC（Mapped Diagnostic Context）——链路 traceId

MDC 是 ThreadLocal 的 Map<String,String>，输出格式里用 `%X{key}` 取值。常用于把 traceId 写进每行日志：

```java
import org.slf4j.MDC;

@Component
public class TraceIdFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        String traceId = ((HttpServletRequest) req).getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();   // 必须清，否则线程池里下个请求复用脏数据
        }
    }
}
```

Logback pattern：`%-5level [%X{traceId}] %logger{36} - %msg%n` →
```
INFO  [a1b2c3d4] c.e.b.UserController - register success
```

### 5. logback-spring.xml 配置

放在 `src/main/resources/logback-spring.xml`（Spring Boot 优先于 logback.xml 加载，支持 `<springProfile>`）：

```xml
<configuration>
    <property name="LOG_PATH" value="logs"/>

    <!-- 控制台：开发用 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%X{traceId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 文件：按日期 + 大小滚动 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/app-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>             <!-- 留 30 天 -->
            <totalSizeCap>5GB</totalSizeCap>        <!-- 总大小封顶 -->
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level [%X{traceId:-}] %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- ERROR 单独一份文件 -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <!-- 同上的 rollingPolicy / encoder -->
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>

    <logger name="org.springframework" level="WARN"/>
    <logger name="com.example.blog" level="DEBUG"/>

    <!-- profile 分支 -->
    <springProfile name="prod">
        <root level="INFO"/>
    </springProfile>
</configuration>
```

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `LoggerFactory.getLogger(...)` 找到绑定的实现（Logback） |
| 配置 | 加载顺序：`logback-spring.xml` > `logback.xml` > `application.properties` 里的 `logging.*` |
| 执行 | 每次 `log.info(...)` 经过：过滤器（Level / MDC）→ Encoder（格式化）→ Appender（写出） |
| 边界 | MDC 泄漏；异步 Appender 缓冲区满；磁盘满；文件句柄泄漏 |
| 验证 | 用 `logback-classic.LoggerContext` 的 status listener；启动加 `-Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener` 看配置加载诊断 |

## 四、项目使用场景

- **第 33 章 REST**：Controller 入参出参用 `log.info` 记录关键节点。
- **第 34 章异常**：全局 ExceptionHandler 里用 `log.error(msg, e)` 记完整 stack。
- **第 35 章事务**：开启 `org.springframework.transaction: TRACE` 看事务边界。
- **第 41-44 章鉴权**：登录失败一定 WARN，频繁失败要告警。
- **第 56 章 Docker**：日志统一输出到 stdout，由 Docker / K8s 收集到 ELK。

## 五、常见问题与坑

| 问题 | 后果 | 处理方式 |
|---|---|---|
| 用 `+` 拼接日志 | 即使 DEBUG 关闭也付出 CPU | 用 `{}` 占位符 |
| 异常只 `log.error(e.getMessage())` | 看不到 stack trace | 把异常作为最后一个参数：`log.error("...", e)` |
| MDC 不清理 | 线程池下个任务读到上个请求的 traceId | `finally { MDC.clear(); }` |
| 同一份日志被打 2 次 | 找不到原因 | 子 logger 的 `additivity` 默认 true，向 root 传播；设 `additivity="false"` |
| 多个 SLF4J 实现共存 | 启动告警 + 输出乱 | `mvn dependency:tree` 找冲突，`<exclusion>` 多余的 |
| 生产开 DEBUG | 日志量爆炸，磁盘满 | 默认 INFO；DEBUG 用动态调整（actuator `/loggers` 端点） |
| 日志里打了密码 / token | 安全合规事故 | 做敏感字段脱敏，禁止直接 toString 整个 DTO |

## 六、面试高频问题

1. SLF4J 和 Logback 是什么关系？为什么不直接用 Logback API？
2. 日志级别从低到高？生产环境一般开到哪一级？
3. `log.debug("user=" + user)` 为什么不推荐？怎么改？
4. MDC 是干什么的？为什么用完一定要清？
5. 日志为什么要异步？异步 Appender 有什么风险？
6. 同样一条日志在两个文件里出现两次，可能的原因？
7. 你怎么排查"日志打了但磁盘上找不到文件"的情况？
