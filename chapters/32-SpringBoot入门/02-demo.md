# Chapter 32 Spring Boot 入门 - 实操 Demo

## Demo 目标

从 0 到可 curl 的 Boot 应用：`/health` Actuator + 1 个 POST 接口 + 自动配置报告 + Fat JAR 打包。

## 前置

- JDK 21、Maven 3.9+、curl

## 一、start.spring.io 生成骨架

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.3.5 \
  -d groupId=com.example \
  -d artifactId=blog-boot \
  -d javaVersion=21 \
  -d dependencies=web,actuator,lombok \
  -o blog-boot.zip
unzip blog-boot.zip && cd blog-boot
```

## 二、主类

```java
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

## 三、最小 REST 接口

```java
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public List<PostVO> list(@RequestParam(defaultValue = "0") Long lastId,
                             @RequestParam(defaultValue = "10") int size) {
        return postService.page(lastId, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostVO create(@RequestBody @Valid PostCreateReq req) {
        return postService.create(req);
    }
}
```

## 四、配置

`application.yml`：

```yaml
server:
  port: 8080

management:
  endpoints.web.exposure.include: health,info,metrics
  endpoint.health.show-details: when-authorized

spring:
  application:
    name: blog-api
  profiles:
    active: dev
```

`application-dev.yml`：

```yaml
logging:
  level:
    com.example: DEBUG
    org.springframework.web: DEBUG
```

## 五、启动

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/blog-boot-0.0.1-SNAPSHOT.jar
```

期望日志：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
Started BlogApplication in 2.4 seconds (JVM running for 2.9)
```

## 六、验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# 创建文章
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"Hi Boot","summary":"test","content":"body","publish":true}'
# {"id":1,"title":"Hi Boot",...}
```

## 七、自动配置报告

```bash
java -jar target/blog-boot-0.0.1-SNAPSHOT.jar --debug 2>&1 | grep -A3 "DataSourceAutoConfiguration"
```

输出：

```
DataSourceAutoConfiguration matched:
  - @ConditionalOnClass found required class 'javax.sql.DataSource' (OnClassCondition)
  ...
MyBatisAutoConfiguration matched:
  - @ConditionalOnClass found required classes 'org.mybatis.spring.SqlSessionFactory' ...
```

## 八、关掉某个自动配置

```yaml
spring:
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

或注解：

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
```

## 九、自定义 HealthIndicator

```java
@Component
public class DbHealthIndicator implements HealthIndicator {
    @Autowired DataSource ds;

    @Override
    public Health health() {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT 1");
             var rs = ps.executeQuery()) {
            rs.next();
            return Health.up().withDetail("ping", "ok").build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

访问 `/actuator/health` 看到：

```json
{"status":"UP","components":{"db":{"status":"UP","details":{"ping":"ok"}}}}
```

## 十、失败场景：包路径错误

```java
// 错：主类在子包
com.example.blog.config.BlogApplication   // ❌ controller/ 扫不到
```

报错：`No mapping for GET /api/posts`

修法：把主类移到 `com.example.blog.BlogApplication`（最顶层）。

## 提交建议

```bash
git add src/ pom.xml application.yml
git commit -m "chapter 32: boot skeleton + actuator + first REST endpoint"
```
