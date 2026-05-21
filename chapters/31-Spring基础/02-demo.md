# Chapter 31 Spring 基础 - 实操 Demo

## Demo 目标

不引入 Spring Boot，纯 Spring Framework 6 跑最小 IoC + AOP demo，理解：容器启动 → Bean 注册注入 → AOP 切面生效 → 循环依赖报错修复。

## 前置

- JDK 21、Maven 3.9+

## 一、Maven 依赖

```xml
<properties><spring.version>6.1.14</spring.version></properties>
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>${spring.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-aspects</artifactId>
        <version>${spring.version}</version>
    </dependency>
</dependencies>
```

## 二、最小 IoC

```java
public interface Greeter { String hello(String name); }

@Service
public class GreeterImpl implements Greeter {
    public String hello(String name) { return "Hi, " + name; }
}

@Service @RequiredArgsConstructor
public class App {
    private final Greeter greeter;
    public void run() { System.out.println(greeter.hello("xiong")); }
}

@Configuration
@ComponentScan("com.example.demo")
@EnableAspectJAutoProxy
public class AppConfig {}

public class Main {
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            ctx.getBean(App.class).run();
        }
    }
}
```

输出：`Hi, xiong`

## 三、AOP 切面：方法计时

```java
@Aspect @Component @Slf4j
public class TimingAspect {
    @Around("execution(* com.example.demo..*.run(..))")
    public Object timing(ProceedingJoinPoint pjp) throws Throwable {
        long t = System.currentTimeMillis();
        try { return pjp.proceed(); }
        finally { log.info("{} cost={}ms", pjp.getSignature(), System.currentTimeMillis()-t); }
    }
}
```

重跑输出多一行：`INFO TimingAspect - void App.run() cost=3ms`

## 四、@Bean 注册三方对象

```java
@Configuration
@PropertySource("classpath:application.properties")
public class DbConfig {
    @Bean
    public HikariDataSource dataSource(@Value("${db.url}") String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        return ds;
    }
}
```

## 五、循环依赖报错

```java
@Service @RequiredArgsConstructor public class A { private final B b; }
@Service @RequiredArgsConstructor public class B { private final A a; }
// BeanCurrentlyInCreationException：构造器循环依赖无法解开
```

修法 1：`@Lazy`（最快）

```java
public A(@Lazy B b) { this.b = b; }
```

修法 2：改 setter 注入（让 Spring 三级缓存解开）

修法 3：重构 → 抽共同逻辑到 C，A/B 各依赖 C（治本）

## 六、自动装配冲突

```java
public interface PayService {}
@Service public class AliPay implements PayService {}
@Service public class WeChatPay implements PayService {}
// NoUniqueBeanDefinitionException
```

修法：`@Primary` 标默认；或 `@Qualifier("aliPay")` 精确指定。

## 七、看容器里的 Bean

```java
Arrays.stream(ctx.getBeanDefinitionNames()).forEach(System.out::println);
```

## 提交建议

```bash
git add demo/spring-basics/
git commit -m "chapter 31: pure spring IoC + AOP demo"
```
