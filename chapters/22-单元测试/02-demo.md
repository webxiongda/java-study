# Chapter 22 单元测试 - 实操 Demo

## Demo 目标

JUnit 5 + AssertJ 完整套餐：基础断言、参数化、`@BeforeEach`、异常断言、生命周期、命名规约。

## 前置条件

基线 pom 已含 `spring-boot-starter-test`（自动引入 JUnit 5 + Mockito + AssertJ + Spring Test）。

## 增量依赖

无。基线已就绪。

## 完整示例代码

被测类：

```java
public class Calculator {
    public int add(int a, int b) { return a + b; }
    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("/ by zero");
        return a / b;
    }
}
```

### 1. 基本断言 + 命名

```java
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;

@DisplayName("Calculator 单元测试")
class CalculatorTest {

    private Calculator sut;   // System Under Test

    @BeforeEach
    void setUp() { sut = new Calculator(); }

    @Test
    @DisplayName("add: 正数 + 正数 = 正数")
    void add_positiveNumbers_returnsSum() {
        assertThat(sut.add(2, 3)).isEqualTo(5);
    }

    @Test
    @DisplayName("divide: 除零抛 ArithmeticException")
    void divide_byZero_throws() {
        assertThatThrownBy(() -> sut.divide(10, 0))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("by zero");
    }
}
```

命名约定：`<method>_<scenario>_<expected>()`，配 `@DisplayName` 写中文场景。

### 2. 参数化测试（`@ParameterizedTest`）

```java
@ParameterizedTest(name = "add({0}, {1}) = {2}")
@CsvSource({
    " 1,  2,  3",
    "-1, -2, -3",
    " 0,  0,  0",
    "10, -3,  7"
})
void add_variousInputs(int a, int b, int expected) {
    assertThat(sut.add(a, b)).isEqualTo(expected);
}

@ParameterizedTest
@ValueSource(strings = {"", " ", "\t", "\n"})
void isBlank_blankStrings_returnsTrue(String s) {
    assertThat(s.isBlank()).isTrue();
}
```

### 3. 集合断言（AssertJ 链式风格）

```java
@Test
void listAssertions() {
    List<String> names = List.of("alice", "bob", "carol");
    assertThat(names)
        .hasSize(3)
        .contains("bob")
        .doesNotContain("dave")
        .startsWith("alice")
        .anyMatch(s -> s.length() == 3);
}
```

### 4. Mockito 配合 Spring（Service 层测试）

```java
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {
    @Mock ArticleMapper mapper;
    @InjectMocks ArticleService sut;

    @Test
    void getDetail_existingId_returnsDto() {
        when(mapper.selectById(1L)).thenReturn(new Article(1L, "hi"));
        ArticleDTO dto = sut.getDetail(1L);
        assertThat(dto.title()).isEqualTo("hi");
        verify(mapper).selectById(1L);
    }

    @Test
    void getDetail_missingId_throwsBusinessException() {
        when(mapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> sut.getDetail(999L))
            .isInstanceOf(BusinessException.class);
    }
}
```

### 5. MockMvc（Controller 层切片测试）

```java
@WebMvcTest(ArticleController.class)
class ArticleControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ArticleService service;

    @Test
    void post_invalidBody_returns400Body() throws Exception {
        mvc.perform(post("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
           .andExpect(status().isOk())   // 我们用统一响应，HTTP 200 + 业务 code
           .andExpect(jsonPath("$.code").value(10001));
    }
}
```

## 运行与验证

| 命令 | 期望 |
|---|---|
| `mvn test` | 全绿，输出 `Tests run: N, Failures: 0` |
| `mvn surefire-report:report` | `target/site/surefire-report.html` 可看 |
| 覆盖率（可选）：加 `jacoco-maven-plugin` 后 `mvn verify` | `target/site/jacoco/index.html` |

## 命名 / 组织约定

- 测试包路径镜像 `src/main` 包结构。
- 文件名：`{Class}Test.java` 单元；`{Class}IT.java` 集成。
- 一个 `@Test` 只验证一个行为。
- 不要在测试里写 `if/for`——出现就拆 `@ParameterizedTest`。

## 常见坑

- 用了 JUnit 4 的 `@Before` / `org.junit.Test` → JUnit 5 不识别，注解全失效。 必须 `org.junit.jupiter.api.*`。
- `@MockBean` 仅在 Spring Test 切片下生效；纯单元用 `@Mock`。
- 测试之间共享静态变量 → 测试顺序敏感。 `@BeforeEach` 重置。

## 提交

```bash
git commit -m "chapter 22: junit5 + assertj + mockito test suite"
```
