# Chapter 23 日志体系 - 实操 Demo

## Demo 目标

接入生产级日志：SLF4J + Logback + `logback-spring.xml`（分环境）+ MDC 注入 `traceId` + 滚动归档。

## 前置条件

- 基线 pom（21 章）：`spring-boot-starter-web` 默认已带 logback + slf4j。
- 应用基础包：`com.example.blog`。

## 增量依赖

无。

## 1. `logback-spring.xml`（放在 `src/main/resources/`）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <property name="LOG_DIR" value="${LOG_DIR:-./logs}"/>
    <property name="APP" value="blog-api"/>
    <property name="PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"/>

    <!-- 控制台：开发用 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- 业务日志滚动文件 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/${APP}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/${APP}-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>5GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- 错误日志单独一份，便于巡检 -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/${APP}-error.log</file>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/${APP}-error-%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>60</maxHistory>
        </rollingPolicy>
        <encoder><pattern>${PATTERN}</pattern></encoder>
    </appender>

    <!-- 分环境 -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.example.blog" level="DEBUG"/>
        <logger name="org.mybatis" level="DEBUG"/>
    </springProfile>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="FILE"/>
            <appender-ref ref="ERROR_FILE"/>
        </root>
        <logger name="com.example.blog" level="INFO"/>
    </springProfile>

</configuration>
```

## 2. MDC `traceId` 过滤器

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final String TRACE_ID = "traceId";
    private static final String HEADER   = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String tid = req.getHeader(HEADER);
        if (tid == null || tid.isBlank()) tid = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, tid);
        resp.setHeader(HEADER, tid);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

> 异步 / `@Async` 场景下 MDC 不会自动传递，需配 `TaskDecorator`：

```java
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable r) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (ctx != null) MDC.setContextMap(ctx);
                r.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
// ThreadPoolTaskExecutor#setTaskDecorator(new MdcTaskDecorator())
```

## 3. 业务代码中使用

```java
@Service
public class ArticleService {
    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    public ArticleDTO getDetail(Long id) {
        log.info("query article id={}", id);     // 占位符，别用字符串拼接
        try {
            // ...
        } catch (Exception e) {
            log.error("query article failed id={}", id, e);  // 异常对象放最后
            throw e;
        }
        return null;
    }
}
```

## 4. `application.yml` 配合

```yaml
spring:
  profiles:
    active: dev
logging:
  config: classpath:logback-spring.xml
  file:
    path: ./logs              # 同时被 ${LOG_DIR} 引用
```

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| traceId 注入 | `curl -v http://localhost:8080/api/articles/1`，response header 含 `X-Trace-Id`；控制台日志行中 `[xxxxxx]` 与之相同 |
| 链路串联 | 同一请求多条日志的 traceId 一致 |
| 分环境 | `--spring.profiles.active=prod` 启动，控制台无输出，`./logs/blog-api.log` 有内容 |
| 滚动归档 | 把 maxFileSize 改 1MB 压测，看到 `.log.gz` 归档文件 |
| ERROR 分流 | 故意抛异常，`blog-api-error.log` 有 stack，主日志也有 |

## 常见坑

- 用 `log.info("id=" + id)` 字符串拼接 → 即使 INFO 关闭也会算 toString。 必须 `log.info("id={}", id)`。
- 异常打 `log.error("msg: " + e.getMessage())` → 丢栈。 正确：`log.error("msg", e)`。
- 文件名用 `logback.xml` → Spring 在 bean 初始化完成前就读了，`<springProfile>` 失效。 必须 `logback-spring.xml`。
- 不清 MDC → 线程池里下一个请求带上别人的 traceId。 必须 `finally { MDC.remove }`。
- 生产打 DEBUG → 磁盘秒满。 `com.example.blog` 用 INFO，关键路径才 DEBUG。

## 提交

```bash
git commit -m "chapter 23: logback-spring.xml + mdc traceId filter"
```
