# Chapter 31 Spring 基础 - 项目任务

## 任务概述

用纯 Spring Framework（无 Boot）搭一个迷你 IoC 容器，体验 Bean 注册、AOP、Profile 的原始形态，为 32 章的 Spring Boot 作对比基础。

## 业务背景

理解"Spring Boot 帮你做了什么"的最好方式，是先不用它。这个任务刻意不用 Boot，把所有配置手写出来，配好后再迁到 Boot 看哪些东西消失了。

## 任务拆解

### Step 1：纯 Spring 项目骨架

```bash
mkdir spring-raw && cd spring-raw
mvn archetype:generate -DarchetypeArtifactId=maven-archetype-quickstart
```

加 spring-context + spring-aspects 依赖（theory 里的版本）。

### Step 2：AppConfig + ComponentScan

写 `AppConfig`：

- `@ComponentScan("com.example")`
- `@EnableAspectJAutoProxy`
- `@PropertySource("classpath:application.properties")`

在 `Main` 里 `new AnnotationConfigApplicationContext(AppConfig.class)` 启动，打印所有 Bean 名。

### Step 3：Service + Mapper 骨架

写 3 个类（不用真实 DB）：

```
FakePostMapper（@Repository）→ PostService（@Service）→ PostController（@Component）
```

全部构造器注入，没有一个 `@Autowired` 字段注入。

写 1 个方法：`PostService.findAll() → mapper.list()`，在 Main 里调用，输出结果。

### Step 4：AOP 计时切面

写 `TimingAspect`，拦截 `within(@Repository *)` 的所有方法，打印耗时。

故意在 FakePostMapper 里 `Thread.sleep(50)` 验证切面是否打印出 `cost=50ms`。

### Step 5：Profile 区分配置

写 2 个 `@Profile` 的 DataSource Bean（`dev` / `prod`）：

- `dev`：连本地 H2（内存库）
- `prod`：连 MySQL

用 `-Dspring.profiles.active=dev` 启动，确认日志里是 `DevDataSource`。

### Step 6：循环依赖与自动装配冲突

故意制造：

1. A 构造器依赖 B、B 构造器依赖 A → 截图报错信息 → 用 `@Lazy` 修复。
2. 两个 `PayService` 实现 → 截图 `NoUniqueBeanDefinitionException` → 用 `@Primary` 修复。

把报错截图 + 修复代码写到 `docs/spring-pitfalls.md`。

## 交付物

- [ ] `src/main/java/com/example/`：AppConfig + 3 个 Bean + 1 个 Aspect
- [ ] `src/main/resources/application{,-dev,-prod}.properties`
- [ ] `Main.java`：启动并输出 Bean 名 + 方法调用结果
- [ ] `docs/spring-pitfalls.md`：2 个失败场景截图 + 修复

## 验收清单

| 项 | 标准 |
|----|------|
| 无字段注入 | grep `@Autowired` 应为空（构造器注入不用写）|
| AOP 生效 | 日志里出现 `cost=xxx ms` |
| Profile 生效 | `dev` 启动用 DevDataSource，`prod` 用 ProdDataSource |
| 循环依赖修复 | 启动正常，文档有报错截图 |
| 冲突修复 | 启动正常，文档有报错截图 |

## 扩展挑战

1. **手写迷你 IoC**：用 `Map<Class<?>, Object>` + 反射实现 50 行的简化 DI 容器，加深理解。
2. **BeanPostProcessor**：写一个 `@Log` 注解 + 对应 BPP，标注方法自动打出入参。
3. **Spring vs Boot 对比**：完成本章后迁到 Boot，记录「哪些配置消失了 / 哪些自动完成了」。
4. **@Conditional**：写一个 `@ConditionalOnProperty(name="feature.sms", havingValue="true")` 的 SmsService Bean，关闭时自动替换为 NoopSmsService。
