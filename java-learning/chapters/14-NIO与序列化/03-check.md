# Chapter 14 - NIO 与序列化：自测题

> 完成 Demo 练习后，独立作答以下 5 道题，再对照答案检验掌握程度。

---

## Q1（概念）：Path 和 File 有什么区别？什么情况下应该用 Path？

---

### 参考答案

**主要区别：**

| 维度 | java.io.File | java.nio.file.Path |
|------|-------------|-------------------|
| 引入版本 | Java 1.0 | Java 7（NIO.2） |
| 职责 | 路径 + 文件操作混在一起 | 路径表示（纯粹）；文件操作由 Files 完成 |
| 错误反馈 | 方法返回 false，难以知道失败原因 | 抛出具体的 IOException 子类 |
| API 丰富度 | 有限（delete、list 等基础操作） | Files 工具类提供丰富的一行式 API |
| 链式操作 | 不支持 | 支持（resolve、relativize、normalize） |
| 与 File 互转 | — | `path.toFile()` / `file.toPath()` |

**什么时候用 Path：**
- 新项目、新代码中优先使用 `Path` + `Files`。
- 需要路径拼接（`path.resolve("sub/file.txt")`）时 Path 更优雅。
- 与 Java 8+ Stream API 配合（`Files.walk()`、`Files.lines()`）时必须用 Path。
- 旧代码库里有 File，可通过 `file.toPath()` 桥接到新 API。

**什么时候还需要 File：**
- 部分旧版 API 只接受 File 参数（如 Jackson 的 `mapper.writeValue(File, ...)`），可用 `path.toFile()` 转换。

---

## Q2（概念）：以下代码有什么问题？

```java
Properties props = new Properties();
try (FileInputStream fis = new FileInputStream("config.properties")) {
    props.load(fis);
}
String value = props.getProperty("app.name");
System.out.println(value.toUpperCase());
```

---

### 参考答案

**问题 1：可能的 NullPointerException**

`props.getProperty("app.name")` 在 key 不存在时返回 `null`，而 `null.toUpperCase()` 会抛出 `NullPointerException`。

**修复：**
```java
String value = props.getProperty("app.name", "UNKNOWN");  // 提供默认值
System.out.println(value.toUpperCase());

// 或者：
String value = props.getProperty("app.name");
if (value != null) {
    System.out.println(value.toUpperCase());
}
```

**问题 2：编码风险**

`FileInputStream` 传给 `props.load()` 时，Properties 默认用 ISO-8859-1 解码。如果 properties 文件是 UTF-8 编码且含中文 value，会出现乱码。

**修复：**
```java
try (Reader reader = new InputStreamReader(
        new FileInputStream("config.properties"), StandardCharsets.UTF_8)) {
    props.load(reader);
}
```

---

## Q3（实操）：用 Files 工具类实现以下功能

**要求：** 写一个方法 `mergeFiles(List<Path> sources, Path dest)`，将多个源文件的内容合并写入目标文件（每个文件内容之间加一行分隔线 `---`）。

---

### 参考答案

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class FileMerger {

    /**
     * 将多个源文件合并到目标文件
     * 文件间用分隔线 "---" 隔开
     */
    public static void mergeFiles(List<Path> sources, Path dest) throws IOException {
        // 确保目标文件的父目录存在
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < sources.size(); i++) {
            Path src = sources.get(i);

            if (!Files.exists(src)) {
                System.err.println("警告：文件不存在，跳过：" + src);
                continue;
            }

            // 读取源文件（readString 简洁，适合中小文件）
            String content = Files.readString(src, StandardCharsets.UTF_8);
            sb.append(content);

            // 文件间加分隔线（最后一个文件后不加）
            if (i < sources.size() - 1) {
                if (!content.endsWith("\n")) {
                    sb.append("\n");
                }
                sb.append("---\n");
            }
        }

        // 一次性写入目标文件
        Files.writeString(dest, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("合并完成 → " + dest.toAbsolutePath());
    }

    public static void main(String[] args) throws IOException {
        // 准备测试文件
        Files.writeString(Paths.get("file1.txt"), "文件1第一行\n文件1第二行\n", StandardCharsets.UTF_8);
        Files.writeString(Paths.get("file2.txt"), "文件2内容", StandardCharsets.UTF_8);
        Files.writeString(Paths.get("file3.txt"), "文件3内容\n", StandardCharsets.UTF_8);

        mergeFiles(
            List.of(Paths.get("file1.txt"), Paths.get("file2.txt"), Paths.get("file3.txt")),
            Paths.get("merged.txt")
        );

        // 验证
        System.out.println(Files.readString(Paths.get("merged.txt"), StandardCharsets.UTF_8));
    }
}
```

**预期 merged.txt 内容：**
```
文件1第一行
文件1第二行
---
文件2内容
---
文件3内容
```

---

## Q4（实操）：分析 Jackson 反序列化时会发生什么？

```java
ObjectMapper mapper = new ObjectMapper();

// 场景1
String json1 = "{\"name\":\"Alice\",\"age\":30,\"extra\":\"unknown\"}";
User user1 = mapper.readValue(json1, User.class);

// 场景2
String json2 = "{\"name\":\"Bob\"}";  // 缺少 age 字段
User user2 = mapper.readValue(json2, User.class);

// 场景3（User 没有无参构造方法）
// 假设 User 只有 public User(String name, int age)
String json3 = "{\"name\":\"Carol\",\"age\":28}";
User user3 = mapper.readValue(json3, User.class);  // 会发生什么？
```

**问题：** 三个场景分别会发生什么？

---

### 参考答案

**场景1：JSON 有多余字段**

默认情况下，Jackson 会**忽略未知字段**（`extra` 字段不在 User 类中），反序列化成功，`user1.name = "Alice"`，`user1.age = 30`。

若想在有未知字段时抛出异常（严格模式）：
```java
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
```

**场景2：JSON 缺少字段**

Jackson 会使用字段的**默认值**：`int` 类型默认为 `0`，`String` 类型默认为 `null`。
因此 `user2.name = "Bob"`，`user2.age = 0`（不是 null，也不报错）。

**场景3：没有无参构造方法**

Jackson 默认需要**无参构造方法**来创建对象实例，然后通过 setter 或直接反射设置字段值。如果没有无参构造方法，会抛出：
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
No suitable constructor found for type [User]
```

**修复方案：**
```java
// 方案1：添加无参构造方法（最简单）
public User() {}

// 方案2：使用 @JsonCreator 注解指定构造方法
@JsonCreator
public User(@JsonProperty("name") String name, @JsonProperty("age") int age) {
    this.name = name;
    this.age = age;
}
```

---

## Q5（综合）：实现一个简单的 JSON 配置读写工具

**题目：** 不使用 Properties 格式，改用 JSON 格式存储配置（config.json），实现：
- `loadConfig(Path)`：从 JSON 文件加载为 `Map<String, String>`
- `saveConfig(Map<String, String>, Path)`：将 Map 保存为 JSON 文件

---

### 参考答案

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class JsonConfigDemo {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);  // 格式化输出

    /**
     * 从 JSON 文件加载配置
     */
    public static Map<String, String> loadConfig(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            return new LinkedHashMap<>();  // 文件不存在，返回空 Map
        }
        // TypeReference 是泛型擦除问题的解决方案，告诉 Jackson 目标类型是 Map<String,String>
        return MAPPER.readValue(configPath.toFile(),
            new TypeReference<LinkedHashMap<String, String>>() {});
    }

    /**
     * 将配置保存为 JSON 文件
     */
    public static void saveConfig(Map<String, String> config, Path configPath)
            throws IOException {
        // 确保父目录存在
        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }
        MAPPER.writeValue(configPath.toFile(), config);
    }

    public static void main(String[] args) throws IOException {
        Path configPath = Paths.get("config.json");

        // 1. 创建并保存配置
        Map<String, String> config = new LinkedHashMap<>();
        config.put("app.name", "MyApp");
        config.put("server.port", "8080");
        config.put("debug.enabled", "true");
        saveConfig(config, configPath);
        System.out.println("保存后的 config.json：");
        System.out.println(Files.readString(configPath));

        // 2. 加载配置
        Map<String, String> loaded = loadConfig(configPath);
        System.out.println("app.name    = " + loaded.get("app.name"));
        System.out.println("server.port = " + Integer.parseInt(loaded.get("server.port")));

        // 3. 修改并重新保存
        loaded.put("server.port", "9090");
        loaded.put("new.key", "new value");
        saveConfig(loaded, configPath);

        System.out.println("\n修改后 server.port = "
            + loadConfig(configPath).get("server.port"));
    }
}
```

**JSON vs Properties 对比：**

| 维度 | Properties 格式 | JSON 格式 |
|------|---------------|-----------|
| 可读性 | 好（行格式） | 好（结构化） |
| 嵌套支持 | 不支持（只能平铺） | 支持（对象/数组） |
| 类型支持 | 只有 String | 支持 number/boolean/null |
| Java 内置支持 | 是（Properties 类） | 否（需要 Jackson/Gson） |
| 适用场景 | 简单 key-value 配置 | 复杂结构化配置 |
