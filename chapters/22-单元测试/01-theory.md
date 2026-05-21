# Chapter 22 单元测试 - 理论篇

## 一、学习定位

单元测试（Unit Test）是后端工程师的基本盘——没有测试的项目是"赌博工程"。本章打通 **JUnit 5** + **AssertJ** + **Mockito** 三件套，后续 35 章事务、44 章 Security、57 章测试策略都建立在这套基础上。

- 优先级：L1 必须掌握
- 预计投入：4 小时
- 阶段产出：给 Chapter 21 的 `blog-api` 加 ≥ 10 个测试用例，覆盖率 ≥ 60%

## 二、核心概念

### 1. JUnit 5 注解与生命周期

JUnit 5（jupiter）相比 JUnit 4 重写了 API，把 `@Before` / `@After` 改成更清晰的 `@BeforeEach` / `@BeforeAll`。

| 注解 | 作用 | 何时执行 |
|------|-----|---------|
| `@Test` | 标记测试方法 | 每个方法独立实例 |
| `@BeforeEach` | 每个测试前 | 重置 mock / 准备数据 |
| `@AfterEach` | 每个测试后 | 清理临时文件 |
| `@BeforeAll` | 整个类前 | 启动数据库 / 初始化全局资源（须是 `static`，或类标 `@TestInstance(PER_CLASS)`） |
| `@AfterAll` | 整个类后 | 关闭数据库 |
| `@DisplayName("...")` | 替换默认显示名 | 跑测试时输出中文/可读名称 |
| `@Disabled("reason")` | 临时跳过 | 待修复用例 |
| `@ParameterizedTest` | 参数化 | 配合 `@ValueSource`、`@CsvSource` 等 |
| `@Nested` | 内嵌测试类 | 按功能分组组织 |
| `@Tag("slow")` | 打标签 | `mvn test -Dgroups=slow` 选择执行 |

**关键规则**：每个 `@Test` 方法默认会**新建一个测试类实例**——所以不能用实例字段在方法间传状态（会被覆盖）。

### 2. AssertJ 流式断言

JUnit 自带 `Assertions.assertEquals(expected, actual)`，但**参数顺序容易写反**、且不支持链式。AssertJ 用 `assertThat(actual).xxx()` 解决：

```java
// JUnit 风格（参数顺序：期望，实际）
assertEquals(3, calculator.add(1, 2));
assertTrue(list.contains("foo"));

// AssertJ 风格（链式、可读）
assertThat(calculator.add(1, 2)).isEqualTo(3);
assertThat(list)
    .hasSize(3)
    .contains("foo", "bar")
    .doesNotContain("baz")
    .allMatch(s -> s.length() > 0);
```

常用 AssertJ API 速查：

| 场景 | 写法 |
|-----|-----|
| 数值范围 | `assertThat(x).isBetween(0, 100)` |
| 字符串模糊 | `assertThat(s).startsWith("user-").containsIgnoringCase("ID")` |
| 集合相等（顺序无关） | `assertThat(list).containsExactlyInAnyOrder("a","b","c")` |
| Map 内容 | `assertThat(map).containsEntry("k","v").hasSize(1)` |
| 异常断言 | `assertThatThrownBy(() -> ...).isInstanceOf(X.class).hasMessageContaining("...")` |
| 对象字段 | `assertThat(user).hasFieldOrPropertyWithValue("name", "Alice")` |
| 对象比较忽略某字段 | `assertThat(actual).usingRecursiveComparison().ignoringFields("id","createdAt").isEqualTo(expected)` |
| Optional | `assertThat(opt).isPresent().hasValue("x")` |

### 3. Mockito：行为模拟

测 `Service` 时通常不希望真连数据库——用 Mockito **打桩 DAO 的返回值**，专注测业务逻辑。

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;          // 被打桩的依赖

    @InjectMocks
    private UserService userService;        // 被测对象，自动注入 mock

    @Test
    void getUser_should_return_dto_when_exists() {
        // given：定义打桩行为
        User mockUser = new User(1L, "Alice");
        when(userMapper.selectById(1L)).thenReturn(mockUser);

        // when：调用被测方法
        UserDTO dto = userService.getUser(1L);

        // then：验证返回值 + 验证 mock 被调用
        assertThat(dto.getName()).isEqualTo("Alice");
        verify(userMapper, times(1)).selectById(1L);
        verifyNoMoreInteractions(userMapper);
    }
}
```

**核心 API**：

| API | 含义 |
|-----|-----|
| `when(x).thenReturn(v)` | 返回固定值 |
| `when(x).thenThrow(e)` | 抛异常 |
| `when(x).thenAnswer(inv -> ...)` | 动态返回值（拿参数动态计算） |
| `verify(mock).method(args)` | 验证调用次数 / 参数 |
| `verify(mock, never()).method()` | 验证从未调用 |
| `ArgumentCaptor` | 抓取传给 mock 的参数，深度断言 |
| `@Mock` / `@InjectMocks` | 简化创建 |
| `MockedStatic` | 模拟静态方法（如 `LocalDateTime.now()`） |

### 4. 测试分层（金字塔模型）

```
            /\
           /UI\          ← 端到端测试（少：~5%）
          /----\
         / 集成 \         ← 跨组件 / 真实 DB / HTTP（中：~15%）
        /--------\
       /  单元测试 \       ← 单类单方法（多：~80%）
      /-----------\
```

单元测试规则：
- **快**：单个用例 < 100ms，否则不是 unit。
- **独立**：互不依赖，任意顺序都能跑。
- **可重复**：100 次结果一致，不依赖时间/网络。
- **自验证**：assert 失败即失败，不靠人眼看 log。

集成测试用 `@SpringBootTest` 或 Testcontainers（57 章详讲）。

### 5. 测试命名与 AAA 结构

**命名约定**（推荐 Roy Osherove 风格）：

```
methodName_should_expectedBehavior_when_condition
```

```java
@Test void divide_should_throwException_when_divisorIsZero() { ... }
@Test void getUser_should_returnNull_when_idDoesNotExist() { ... }
@Test void register_should_persistUser_when_emailNotTaken() { ... }
```

**AAA 结构**（Arrange-Act-Assert，又名 Given-When-Then）：

```java
@Test
void register_should_throw_when_emailAlreadyExists() {
    // Arrange / Given
    when(userMapper.countByEmail("a@x.com")).thenReturn(1);

    // Act / When
    Throwable thrown = catchThrowable(() -> userService.register("a@x.com", "pwd"));

    // Assert / Then
    assertThat(thrown)
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessageContaining("a@x.com");
}
```

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `mvn test` → maven-surefire-plugin → fork JVM → 用 JUnit 5 launcher 发现并执行 `@Test` 方法 |
| 配置 | `src/test/resources/` 下的配置文件优先于 `src/main/resources/`；可加 `application-test.properties` |
| 执行 | 每个 `@Test` 方法默认新建类实例；并发由 `junit-platform.properties` 配置 `junit.jupiter.execution.parallel.enabled=true` |
| 边界 | `@BeforeAll` 必须 static；`@Mock` 必须配合 `@ExtendWith(MockitoExtension.class)`；测 final 类需要 `mockito-inline` |
| 验证 | `mvn test` 输出 + `target/surefire-reports/` 下的 XML/TXT 报告；覆盖率用 JaCoCo（`mvn jacoco:report`） |

## 四、项目使用场景

- **第 25-29 章 SQL/MyBatis**：用 H2 内存库 + `@MybatisTest` 切片测试。
- **第 33 章 REST**：`MockMvc` 在不启动 Tomcat 的前提下测 Controller。
- **第 35 章事务**：用 `@Transactional` + `@Rollback` 让测试自动回滚，不污染库。
- **第 42 章 JWT**：mock 时钟（`Clock`）测 token 过期。
- **第 52 章并发**：Awaitility 等异步条件、`CountDownLatch` 触发并发。
- **CI 流水线**：`mvn -B verify` → 失败即阻断合并。

## 五、常见问题与坑

| 问题 | 后果 | 处理方式 |
|---|---|---|
| `mvn test` 不跑测试类 | 用例没跑就当通过 | 类名/方法名必须 `Test` 结尾或 `Test` 开头；类必须 public（JUnit 5 实际支持 package-private，但 Surefire 默认配置只扫 `*Test.java`） |
| 用例之间互相影响 | 顺序变了就挂 | 别用实例字段当状态；用 `@BeforeEach` 重置 |
| `verify` 报 `wanted but not invoked` | mock 设了但代码没调 | 看打桩参数与实际调用是否一致（`any()` vs 具体值） |
| 测试代码引到生产代码 | 编译失败或污染生产逻辑 | 严格分 `src/main` 和 `src/test`；不要把 mock 工具放到 main |
| `@Mock` 没注入 | `NullPointerException` | 漏写 `@ExtendWith(MockitoExtension.class)` |
| 时间相关测试不稳定 | 半夜 0 点跑就挂 | 注入 `Clock` 而不是直接调 `LocalDateTime.now()`；测试里 mock Clock |
| 测试用真数据库 | 慢、不可重复 | unit 用 mock；integration 用 Testcontainers + 每次重建 schema |
| 覆盖率达标但 bug 还是漏 | 假指标 | 看 mutation testing（Pitest）；覆盖率只是必要不充分 |

## 六、面试高频问题

1. JUnit 5 比 JUnit 4 改进了什么？为什么 `@BeforeEach` 不需要是 static？
2. `@Mock` / `@Spy` / `@InjectMocks` 三者的区别？什么时候用 Spy？
3. AssertJ 相比 JUnit 自带断言的优势是什么？举一个易错对比。
4. 单元测试和集成测试的边界是什么？`@SpringBootTest` 是单元测试吗？
5. 测试金字塔的三层比例为什么是 80/15/5？如果反过来会怎样？
6. 你怎么测试一段调用了 `LocalDateTime.now()` 的代码？
7. 覆盖率到 90% 但还是漏 bug，可能的原因是什么？
