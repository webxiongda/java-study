# Chapter 22 单元测试 - 项目任务

## 任务概述

给 Chapter 21 搭起来的 `blog-api` 工程加单元测试，覆盖工具类和未来要写的 Service 层。**这是后续所有 DAO / Service 章节的"安全网"。**

## 业务背景

后端项目的核心交付物不是代码，而是**"经过测试证明能用的代码"**。从本章开始：
- 所有提交前必须 `mvn test` 通过。
- `target/site/jacoco/index.html` 覆盖率行覆盖 ≥ 60%（核心 Service ≥ 80%）。
- 后续每章新增 Controller / Service，都同步写 2-3 个测试。

## 任务拆解

### Step 1：加 JaCoCo 覆盖率插件

修改 `blog-api/pom.xml` 的 `<build><plugins>`：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Step 2：为 mini-utils 工具类补测试

至少覆盖：

- `StringUtils.isBlank` / `truncate` / `joinNonBlank`：用 `@ParameterizedTest` + `@NullAndEmptySource` 覆盖 null / 空 / 全空白 / 正常。
- `DateUtils.format` / `parse`：用固定 `Clock` 验证时区敏感场景。
- 至少一个会**抛异常**的方法：用 `assertThatThrownBy` 验证异常类型和消息。

### Step 3：写一个 mock 风格的 Service 测试样例

新建 `src/main/java/com/example/blog/service/HelloService.java`（即使是占位）：

```java
@Service
public class HelloService {
    private final HelloProperties props;
    public HelloService(HelloProperties props) { this.props = props; }
    public String greet(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        return props.getPrefix() + name + "!";
    }
}
```

对应 `HelloServiceTest`：用 `@Mock HelloProperties props` + `@InjectMocks HelloService service` 覆盖 3 条路径（正常 / 空名 / 自定义前缀）。

### Step 4：跑验证

```bash
cd blog-api
mvn clean verify                          # 跑所有测试 + 出覆盖率报告 + 触发 jacoco check
open target/site/jacoco/index.html        # 查看哪一行没覆盖
```

期望：`BUILD SUCCESS`，覆盖率 ≥ 60%，否则插件会让 build 失败。

### Step 5：故意写一个会失败的测试，看 Surefire 报告

在某个测试里临时改 `assertEquals(3, 1+1)`，跑 `mvn test`：

- 控制台会输出 `[ERROR] Tests run: X, Failures: 1`。
- `target/surefire-reports/<ClassName>.txt` 会有完整 trace。
- 修复后再跑一遍变绿。

## 交付物

- [ ] `pom.xml` 加上 jacoco 插件
- [ ] `src/test/java/.../util/*Test.java` 至少 3 个工具类测试，每个 ≥ 4 个 case
- [ ] `src/test/java/.../service/HelloServiceTest.java` 含 mock + AssertJ + 异常断言三种风格
- [ ] `mvn verify` 全绿，行覆盖率 ≥ 60%
- [ ] 提交一份 `docs/test-report.md`（或写在 README）：贴出 `target/site/jacoco/index.html` 截图、说明哪几行未覆盖、解释为什么没补

## 验收清单

| 验收项 | 标准 |
|-------|-----|
| 测试结构 | `src/test/java` 与 `src/main/java` 镜像，包路径一致 |
| 命名 | 测试类 `XxxTest`，方法用 `method_should_xxx_when_yyy` |
| 断言风格 | 全部用 AssertJ（统一）；异常用 `assertThatThrownBy` |
| 边界 | 每个方法至少 1 个 null / 空 / 异常路径 |
| 隔离 | `mvn test -Dtest='*Test'` 任意顺序运行结果一致 |
| 速度 | 总测试时间 < 5 秒（unit 不应该慢） |
| 覆盖率 | `mvn verify` 触发 jacoco check 通过 |

## 扩展挑战

1. **引入 Pitest 做变异测试**：覆盖率 80% 不等于 bug 少。Pitest 会故意把 `>` 改成 `>=` 看你测试能否发现，发现不了的"变异存活率"才是真实指标。
2. **写一个 `@Nested` 嵌套类**：把 `HelloServiceTest` 按场景分组（`GreetTests` / `ValidationTests`），用 `@DisplayName("中文场景描述")`，跑测试时输出可读的中文树。
3. **CI 集成**：在 `.github/workflows/ci.yml` 加 `mvn -B verify`，PR 不绿不让合并。
