# Chapter 50 里程碑：博客APIv2 - 实操 Demo

## Demo 目标

把 41-49 章的能力 **拼装成一个完整的运行系统**:
- 用户 注册 → 登录 → 上传头像 → 发文章 (带图) → 评论 → 看排行榜
- 全链路 200, 涉及 MySQL / Redis / RabbitMQ / MinIO 四个中间件
- Docker Compose 一键起

## 前置条件

- JDK 21, Maven 3.9+
- Docker + Docker Compose
- 41-49 章的代码都已经合并到 `backend/`

## 一、docker-compose.yml (中间件全家桶)

```yaml
version: "3.8"

services:
  mysql:
    image: mysql:8.4
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: blog
    volumes: ["./data/mysql:/var/lib/mysql"]

  redis:
    image: redis:7.4-alpine
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672","15672:15672"]
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin123

  minio:
    image: minio/minio:latest
    ports: ["9000:9000","9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123
    command: server /data --console-address ":9001"
    volumes: ["./data/minio:/data"]
```

```bash
docker compose up -d
```

## 二、application.yml (整合配置)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    username: admin
    password: admin123
    publisher-confirm-type: correlated
    listener:
      simple:
        acknowledge-mode: manual
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB

storage:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket: blog

jwt:
  secret: ${JWT_SECRET:change-me-32-chars-min-change-me}
  expire-minutes: 60

logging:
  level:
    com.javastudy: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

## 三、端到端联调脚本

```bash
# 0. 启动
mvn -pl backend spring-boot:run

# 1. 注册
curl -s -X POST localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Passw0rd!","email":"alice@a.com"}'

# 2. 登录, 拿 token
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Passw0rd!"}' | jq -r .data.token)
echo $TOKEN

# 3. 上传头像
curl -s -X POST localhost:8080/api/v1/upload/avatar \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@./avatar.png

# 4. 发文章 (带 XSS 测试)
curl -s -X POST localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Hello","content":"<p>hi</p><script>alert(1)</script>"}'

# 期望 content 净化后只剩 <p>hi</p>

# 5. 看文章 (第一次走 DB, 第二次走 Redis)
curl -s localhost:8080/api/v1/posts/1
curl -s localhost:8080/api/v1/posts/1   # log: cache hit

# 6. 评论 (异步通知)
curl -s -X POST localhost:8080/api/v1/posts/1/comments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"first!"}'

# RabbitMQ 控制台 (localhost:15672) 看 comment.notify 队列消息流过

# 7. 热门排行
curl -s localhost:8080/api/v1/posts/hot

# 8. 限流测试 (Ch45 装的 5 次/分钟)
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" -d '{"username":"x","password":"x"}'
done
# 后 5 个应返回 429
```

## 四、关键整合代码

### SecurityConfig (Ch44) 与 RateLimitFilter (Ch45) 协作

```java
@Bean
SecurityFilterChain chain(HttpSecurity http, JwtFilter jwt, RateLimitFilter rl) throws Exception {
    return http
        .csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/v1/auth/**","/api/v1/posts","/api/v1/posts/**").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/v1/posts").authenticated()
            .anyRequest().authenticated())
        .addFilterBefore(rl, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .headers(h -> h
            .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
            .frameOptions(f -> f.deny()))
        .build();
}
```

### PostService 整合缓存 + Jsoup + MQ

```java
@Transactional
public PostVO create(PostCreateReq req, Long uid) {
    String safe = Jsoup.clean(req.getContent(), Safelist.basicWithImages());
    Post post = Post.builder().userId(uid).title(req.getTitle()).content(safe).build();
    mapper.insert(post);
    outbox.save("post.created", Map.of("id", post.getId(), "uid", uid));
    return postCache.toVO(post);
}

public PostVO getDetail(Long id) {
    return postCache.get(id, () -> {
        Post p = mapper.selectById(id);
        if (p == null) throw new BizException(404, "post not found");
        return PostVO.of(p);
    });
}
```

## 五、验证清单

| 项 | 命令 | 预期 |
|---|---|---|
| 启动 | `docker compose up -d && mvn spring-boot:run` | 4 容器 healthy, 应用 actuator/health = UP |
| 注册登录 | 上面脚本 1-2 | 返回 JWT |
| 上传 | 上面脚本 3 | MinIO 控制台 (9001) 看到对象 |
| XSS 防护 | 上面脚本 4 | DB content 不含 `<script>` |
| 缓存 | 重复 GET | 第二次 log "cache hit" |
| 异步 | 评论 | RabbitMQ 队列消息消费成功 |
| 限流 | 循环 10 次 | 后 5 次 429 |
| 故障降级 | `docker stop redis`, 再 GET | 仍 200, 走 DB |

## 六、提交

```bash
git add backend docker-compose.yml
git commit -m "ch50: blog API v2 integration (auth + cache + security + upload + mq)"
git tag v2.0
```
