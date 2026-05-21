# Chapter 32 Spring Boot 入门 - 理论篇

## 一、学习定位

31 章把 Spring 的原始面貌看清楚了；Boot 是在 Spring 上套了一层"自动化"——**自动配置 + Starter + 内嵌服务器 + Actuator**。理解 Boot = 理解"它帮你省了哪些配置，以及你怎么覆盖这些配置"。

- 优先级：L1
- 预计投入：4 小时
- 阶段产出：一个最小 Boot 应用 + `/health` Actuator + 一个 REST 接口可 curl

## 二、核心概念

### 1. Spring Boot 帮你做了什么

| Boot 之前（手写） | Boot 之后（自动） |
|----------------|----------------|
| `new AnnotationConfigApplicationContext()` | `SpringApplication.run()` |
| `@ComponentScan("com.example")` | 主类所在包自动扫描 |
| 手配 `DispatcherServlet` + Tomcat | 内嵌 Tomcat，启动即可用 |
| 手配 `DataSource` + `SqlSessionFactory` | 引入 starter，配 URL 就够 |
| 手写 `application.properties` 每个 key | Starter 提供带默认值的绑定 |
| 手配 Jackson / 参数校验 / Actuator | 自动配置 |

### 2. 核心注解

```java
@SpringBootApplication
= @SpringBootConfiguration    // 就是 @Configuration
+ @EnableAutoConfiguration    // 关键：加载自动配置
+ @ComponentScan              // 从主类包开始扫
```

**主类位置**：必须放在所有子包的上层，否则部分类扫不到。

```
com.example.blog/
├── BlogApplication.java     ← 主类（最顶层包）
├── controller/
├── service/
└── dao/
```

### 3. 自动配置原理

Boot 在 `spring-boot-autoconfigure.jar` 里预置了几百个 `@Configuration` 类：

```
DataSourceAutoConfiguration
MyBatisAutoConfiguration
JacksonAutoConfiguration
SpringMvcAutoConfiguration
...
```

触发条件：`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等。

```java
@Configuration
@ConditionalOnClass(HikariDataSource.class)     // classpath 有 HikariCP
@ConditionalOnMissingBean(DataSource.class)     // 你自己没配 DataSource
class DataSourceAutoConfiguration {
    @Bean DataSource dataSource() { /* 用 spring.datasource.* 属性初始化 */ }
}
```

**查看自动配置**：`--debug` 模式启动，看 `CONDITIONS EVALUATION REPORT`。

### 4. Starter

Starter 就是一组依赖 + 一个或多个自动配置。引入一个 starter 就等于"我要这个功能"。

| Starter | 带来什么 |
|---------|---------|
| `spring-boot-starter-web` | Tomcat + Spring MVC + Jackson |
| `spring-boot-starter-data-jpa` | Hibernate + DataSource + Tx |
| `mybatis-spring-boot-starter` | SqlSessionFactory + Mapper 扫描 |
| `spring-boot-starter-actuator` | 健康检查 + 指标 + Info |
| `spring-boot-starter-test` | JUnit 5 + Mockito + AssertJ |

### 5. 配置绑定

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost/blog
    username: root
    password: ${DB_PASSWORD}    # 环境变量注入

blog:
  upload:
    max-size: 5MB
    allowed-types: jpg,png,gif
```

**类型安全绑定**：

```java
@ConfigurationProperties(prefix = "blog.upload")
@Component
public record UploadProperties(String maxSize, String allowedTypes) {}
```

**配置优先级**（高覆盖低）：

```
命令行参数 > 环境变量 > application-{profile}.yml > application.yml > @PropertySource > 默认值
```

### 6. 内嵌 Tomcat 与打包

```bash
mvn package              # 生成 target/blog-0.0.1.jar
java -jar blog-0.0.1.jar  # 内嵌 Tomcat 启动，端口 8080
```

Fat JAR：把所有依赖打进一个 JAR，任何装了 JDK 的机器都能直接跑。

**换服务器**：去掉 Tomcat，加 Undertow：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion><groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-starter-tomcat</artifactId></exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

### 7. Actuator

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,env
  endpoint.health.show-details: always
```

| 端点 | 含义 |
|------|------|
| `/actuator/health` | 应用 + 组件健康状态 |
| `/actuator/info` | 版本、git commit 等 |
| `/actuator/metrics` | JVM、HTTP、DataSource 指标 |
| `/actuator/env` | 当前所有配置项（含来源）|
| `/actuator/beans` | 容器里所有 Bean |

**生产上 Actuator 必须鉴权**：见 44 章 Spring Security。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `SpringApplication.run()` → 环境准备 → 刷新 ApplicationContext → 内嵌服务器 |
| 配置 | `spring-boot-autoconfigure/META-INF/spring/...imports` 列出所有自动配置类 |
| 执行 | Condition 评估 → 有条件地注册 Bean → 启动 DispatcherServlet |
| 边界 | 自动配置冲突、classpath 版本冲突、配置覆盖顺序 |
| 验证 | `--debug` 启动看自动配置报告；`/actuator/beans` 看容器 |

## 四、在博客项目里的落点

- 主类 `BlogApplication` 在 `com.example.blog` 顶层包。
- 所有配置在 `application.yml`，Profile 区分 dev/test/prod。
- Actuator 只暴露 health + info 给负载均衡器探活（其余鉴权保护）。
- Fat JAR → Docker 镜像（57 章）。

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| Bean 找不到 | 主类不在最顶层包 | 移动主类或加 `@ComponentScan` |
| 多数据源配置冲突 | 自动配置和手动 Bean 冲突 | `@ConditionalOnMissingBean` 或加 `@Primary` |
| 端口冲突 8080 | 本地有别的服务 | `server.port=8081` |
| Fat JAR 太大 | 所有依赖打进去 | 分层打包（`layers.enabled=true`）|
| 环境变量被 log 暴露 | `/actuator/env` 未保护 | Security 拦截 Actuator |

## 六、面试高频问题

1. Spring Boot 自动配置的原理？`@ConditionalOnXxx` 如何工作？
2. Starter 是什么？自己写一个 Starter 需要哪些步骤？
3. `@SpringBootApplication` 由哪 3 个注解组成？
4. Spring Boot 的 Fat JAR 和普通 JAR 的区别？
5. `application.yml` 和 `application-prod.yml` 的关系？优先级如何？
6. Actuator 的 health 端点如何自定义 HealthIndicator？
7. 如何关掉某个自动配置？
