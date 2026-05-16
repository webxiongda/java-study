# Chapter 14 - NIO 与序列化：理论知识

## 学习目标

- 理解 `java.nio.file.Path` 与旧版 `File` 类的区别
- 掌握 `Files` 工具类的核心方法
- 学会用 `Properties` 读写配置文件
- 理解 JSON 序列化的必要性及 Jackson/Gson 的基本用法
- 了解 Java 原生序列化的问题和替代方案

---

## 1. java.nio.file.Path（NIO.2 文件 API）

### 1.1 Path vs File 的对比

| 维度 | java.io.File（旧） | java.nio.file.Path（新，Java 7+） |
|------|-------------------|----------------------------------|
| 引入版本 | Java 1.0 | Java 7（NIO.2） |
| 表示含义 | 文件或目录的路径 | 路径（更纯粹，不含操作方法） |
| 文件操作 | 方法分散在 File 类中 | 集中在 Files 工具类 |
| 错误反馈 | 方法返回 boolean，错误信息少 | 抛出具体的 IOException 子类 |
| 链式操作 | 不支持 | 支持（`path.resolve().normalize()`） |
| 跨平台 | 需要手动处理分隔符 | 自动处理 |
| 与 File 互转 | — | `file.toPath()` / `path.toFile()` |

### 1.2 创建 Path

```java
// 方式1：Paths.get()（最常用）
Path path = Paths.get("/Users/alice/data/test.txt");
Path path = Paths.get("/Users/alice", "data", "test.txt");  // 多段拼接

// 方式2：Path.of()（Java 11+）
Path path = Path.of("/Users/alice/data/test.txt");

// 方式3：File 转 Path
File file = new File("/Users/alice/data/test.txt");
Path path = file.toPath();
```

### 1.3 Path 常用方法

```java
Path path = Paths.get("/Users/alice/data/test.txt");

path.getFileName()          // test.txt（最后一段）
path.getParent()            // /Users/alice/data
path.getRoot()              // /（根路径）
path.toString()             // 转为字符串
path.toAbsolutePath()       // 转为绝对路径
path.normalize()            // 去掉 . 和 ..（规范化）
path.resolve("other.txt")   // 拼接路径：/Users/alice/data/other.txt
path.relativize(otherPath)  // 计算相对路径
```

---

## 2. Files 工具类

`java.nio.file.Files` 是 NIO 的核心工具类，集中了几乎所有文件操作，不再需要像旧版 IO 那样手动创建流对象。

### 2.1 读取文件

```java
// 读取全部字节
byte[] bytes = Files.readAllBytes(path);

// 读取全部行（小文件用，全部加载到内存）
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

// 读取全部内容为字符串（Java 11+）
String content = Files.readString(path, StandardCharsets.UTF_8);

// 惰性加载的行流（大文件推荐，逐行处理，记得关闭）
try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
    stream.filter(line -> line.contains("ERROR"))
          .forEach(System.out::println);
}
```

### 2.2 写入文件

```java
// 写字符串（Java 11+，默认覆盖）
Files.writeString(path, "内容", StandardCharsets.UTF_8);

// 写字符串并追加
Files.writeString(path, "追加内容", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

// 写字节数组
Files.write(path, bytes);

// 写多行（每行自动加换行符）
List<String> lines = Arrays.asList("第一行", "第二行", "第三行");
Files.write(path, lines, StandardCharsets.UTF_8);

// 创建文件并写入（若文件已存在则失败）
Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
```

### 2.3 文件系统操作

```java
// 判断
Files.exists(path)                  // 是否存在
Files.isDirectory(path)             // 是否是目录
Files.isRegularFile(path)           // 是否是普通文件

// 复制（默认若目标存在会失败）
Files.copy(src, dest);
Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);  // 覆盖目标

// 移动/重命名
Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);

// 删除
Files.delete(path);         // 不存在时抛 NoSuchFileException
Files.deleteIfExists(path); // 不存在时静默忽略

// 创建目录
Files.createDirectory(path);   // 单层（父目录必须存在）
Files.createDirectories(path); // 多层（自动创建中间目录，推荐）

// 获取文件大小
long size = Files.size(path);

// 遍历（Java 8+，返回惰性 Stream）
try (Stream<Path> paths = Files.walk(rootPath)) {
    paths.filter(p -> p.toString().endsWith(".java"))
         .forEach(System.out::println);
}
```

---

## 3. Properties 文件读写

`Properties` 是 Java 内置的配置文件处理类，对应 `.properties` 格式（key=value 形式）。

### 3.1 配置文件格式

```properties
# app.properties 示例
app.name=MyApp
app.version=1.0.0
server.port=8080
debug.enabled=true
max.connections=100
```

### 3.2 读取配置文件

```java
Properties props = new Properties();

// 旧版写法（File + 流）
try (InputStream is = new FileInputStream("app.properties")) {
    props.load(is);  // 解析 key=value 格式
}

// NIO 写法（推荐）
try (InputStream is = Files.newInputStream(Paths.get("app.properties"))) {
    props.load(is);
}

// 读取值
String name    = props.getProperty("app.name");           // "MyApp"
String port    = props.getProperty("server.port", "80");  // 有默认值
String missing = props.getProperty("not.exist");          // null
```

### 3.3 修改并保存配置

```java
// 修改/新增
props.setProperty("server.port", "9090");
props.setProperty("new.key", "new.value");

// 保存回文件（store 第二参数是注释，写入文件顶部）
try (OutputStream os = Files.newOutputStream(Paths.get("app.properties"))) {
    props.store(os, "Updated by ConfigManager");
}
```

---

## 4. JSON 序列化

### 4.1 为什么不用 Java 原生 Serializable？

Java 提供了 `java.io.Serializable` 接口用于对象序列化（将对象转为字节流存储或传输），但实际项目中**几乎不使用**，原因：

| 问题 | 说明 |
|------|------|
| 可读性差 | 生成的是二进制格式，无法直接阅读和调试 |
| 跨语言不兼容 | 只有 Java 能读，无法与 Python/Go/JavaScript 等系统交互 |
| 版本兼容性脆弱 | 类结构变化（增删字段）可能导致反序列化失败（serialVersionUID 问题） |
| 安全漏洞 | 反序列化是历史上 Java 安全漏洞的重灾区（可执行任意代码） |
| 性能较差 | 比主流 JSON/Protobuf 库慢 |

### 4.2 Jackson 简介

Jackson 是 Java 生态中最流行的 JSON 序列化库，Spring Boot 默认集成。

**Maven 依赖：**
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

**核心 API：**
```java
ObjectMapper mapper = new ObjectMapper();

// 对象 → JSON 字符串
String json = mapper.writeValueAsString(object);

// 对象 → 格式化 JSON（易读）
String prettyJson = mapper.writerWithDefaultPrettyPrinter()
                          .writeValueAsString(object);

// JSON 字符串 → 对象
User user = mapper.readValue(json, User.class);

// JSON 字符串 → List
List<User> users = mapper.readValue(jsonArray,
    mapper.getTypeFactory().constructCollectionType(List.class, User.class));

// 对象 → 文件
mapper.writeValue(new File("user.json"), object);

// 文件 → 对象
User user = mapper.readValue(new File("user.json"), User.class);
```

### 4.3 Gson 简介

Gson 是 Google 开发的 JSON 库，API 更简洁。

**Maven 依赖：**
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

```java
Gson gson = new Gson();

String json  = gson.toJson(object);        // 对象 → JSON
User user    = gson.fromJson(json, User.class);  // JSON → 对象
```

**Jackson vs Gson 选择：**
- Spring Boot 项目：Jackson（已内置，无需额外依赖）
- 简单工具类/非 Spring 项目：Gson（API 更简单）

---

## 常见坑

### 坑 1：Files.writeString 默认覆盖原文件

```java
// 危险：第二次调用会覆盖第一次写入的内容！
Files.writeString(path, "第一条日志\n", StandardCharsets.UTF_8);
Files.writeString(path, "第二条日志\n", StandardCharsets.UTF_8);
// 文件只有"第二条日志"

// 正确：追加模式
Files.writeString(path, "第二条日志\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
```

---

### 坑 2：Path 不能直接用字符串作为方法参数

```java
// 错误：Files 的方法接受 Path，不接受 String
Files.readAllLines("app.properties");  // 编译错误

// 正确：先转为 Path
Files.readAllLines(Paths.get("app.properties"), StandardCharsets.UTF_8);
// 或 Java 11+：
Files.readAllLines(Path.of("app.properties"), StandardCharsets.UTF_8);
```

---

### 坑 3：Serializable 的 serialVersionUID 问题

```java
// 如果不声明 serialVersionUID，JVM 会自动生成
// 一旦类结构改变（增删字段），自动生成的 UID 变化，之前序列化的数据就无法反序列化
public class User implements Serializable {
    // 没有 serialVersionUID ← 危险！
    private String name;
}

// 正确：显式声明 serialVersionUID
public class User implements Serializable {
    private static final long serialVersionUID = 1L;  // 手动管理版本
    private String name;
}
```

即使显式声明了 serialVersionUID，类结构变化（如改变字段类型）仍可能导致反序列化异常，这也是项目中用 JSON 替代原生序列化的重要原因。

---

## 面试高频问题

### Q1：NIO 和传统 IO 的区别？

**答：**
- **传统 IO（java.io）：** 面向流（Stream-oriented），同步阻塞（Blocking），每次操作等待完成才返回；`File` 类操作方法错误信息少（返回 boolean）。
- **NIO（java.nio，Java 4）：** 面向缓冲区（Buffer-oriented），支持非阻塞 IO，通过 Channel + Buffer + Selector 实现高并发网络编程。
- **NIO.2（java.nio.file，Java 7）：** 专注于文件系统操作，引入 `Path` / `Files` / `FileSystem`，是传统 `File` 类的现代替代方案，与网络 NIO 是两件事。
- 日常文件操作：用 NIO.2（`Path` + `Files`）代替旧版 IO；高并发网络编程：用 NIO（Netty 框架封装了底层复杂性）。

---

### Q2：为什么不推荐用 Java 原生序列化（Serializable）？

**答：**
1. **可读性差：** 二进制格式，无法人工调试。
2. **跨语言不兼容：** 只有 Java 能反序列化，无法与微服务中的其他语言互通。
3. **版本脆弱：** 类结构变化可能导致历史数据无法读取。
4. **安全风险：** 反序列化漏洞（如 CC 链攻击）是 Java 历史上最严重的安全问题之一，攻击者可通过构造恶意序列化数据执行任意代码。
5. **性能差：** 比 JSON（Jackson/Gson）或二进制协议（Protobuf）慢。
- **推荐替代：** 数据存储/传输用 JSON（Jackson/Gson），高性能场景用 Protobuf。

---

### Q3：如何用 Jackson 序列化 Java 对象到 JSON 文件？

**答：**
```java
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();

// 对象序列化到文件
User user = new User("Alice", 30);
mapper.writeValue(Paths.get("user.json").toFile(), user);
// 文件内容：{"name":"Alice","age":30}

// 从文件反序列化
User loaded = mapper.readValue(Paths.get("user.json").toFile(), User.class);
System.out.println(loaded.getName()); // Alice
```

---

### Q4：`Files.readAllLines` 和 `Files.lines` 有什么区别？

**答：**
| | `Files.readAllLines` | `Files.lines` |
|--|--|--|
| 返回类型 | `List<String>` | `Stream<String>` |
| 加载方式 | 一次性全部读入内存 | 惰性加载（按需读取） |
| 适用场景 | 小文件（几十 MB 以内） | 大文件（按需处理，节省内存） |
| 关闭方式 | 自动 | 需要 try-with-resources 关闭 Stream |

大文件首选 `Files.lines()`，但必须用 try-with-resources 确保底层文件句柄关闭。

---

### Q5：Properties 的 `load()` 和 `store()` 分别做什么？支持哪些格式？

**答：**
- **`load(InputStream)`：** 从输入流解析 `.properties` 文件（`key=value` 或 `key: value` 格式），将键值对加载到 Properties 对象中。`#` 和 `!` 开头的行是注释，会被忽略。
- **`store(OutputStream, String)`：** 将当前所有键值对以 `key=value` 格式写入输出流，第二参数是写入文件顶部的注释。
- **注意：** Properties 只支持 String 类型的键和值；需要整数/布尔值时，需要手动用 `Integer.parseInt()` 等方法转换。
- **中文：** `load`/`store` 默认用 ISO-8859-1 编码处理中文（会转为 Unicode 转义）；`loadFromXML`/`storeToXML` 支持 UTF-8；也可用 `load(new InputStreamReader(is, UTF_8))` 直接处理 UTF-8 的 properties 文件。
