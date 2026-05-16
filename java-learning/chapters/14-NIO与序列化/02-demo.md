# Chapter 14 - NIO 与序列化：代码演示

> 三个 Demo 分别展示 NIO Files 工具类、Properties 配置文件读写、Jackson JSON 序列化。

---

## Demo 1：用 Files 工具类进行文件读写（对比旧版 IO 写法）

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

public class NioFilesDemo {

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("nio_demo");
        Files.createDirectories(dataDir);  // 创建目录（若已存在不报错）

        // ==================== 写文件 ====================
        writeDemo(dataDir);

        // ==================== 读文件 ====================
        readDemo(dataDir);

        // ==================== 复制文件 ====================
        copyDemo(dataDir);

        System.out.println("\n=== Demo 1 完成 ===");
    }

    static void writeDemo(Path dir) throws IOException {
        Path filePath = dir.resolve("notes.txt");  // 等价于 dir + "/notes.txt"

        // ----- NIO 写法（简洁） -----
        // 写字符串（Java 11+）
        Files.writeString(filePath, "第一行内容\n", StandardCharsets.UTF_8);
        // 追加内容
        Files.writeString(filePath, "第二行内容\n", StandardCharsets.UTF_8,
                          StandardOpenOption.APPEND);

        // 写多行（自动加换行符）
        List<String> lines = Arrays.asList("第三行", "第四行", "第五行");
        Files.write(filePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);

        System.out.println("NIO 写入完成：" + filePath.toAbsolutePath());

        // ----- 对比：旧版 IO 写法（繁琐）-----
        /*
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(filePath.toFile(), true),
                    StandardCharsets.UTF_8))) {
            bw.write("内容");
            bw.newLine();
        }
        */
    }

    static void readDemo(Path dir) throws IOException {
        Path filePath = dir.resolve("notes.txt");

        System.out.println("\n=== 读取文件 ===");

        // ----- NIO 读法1：readString（全部内容，Java 11+）-----
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        System.out.println("readString 结果：\n" + content);

        // ----- NIO 读法2：readAllLines（返回 List<String>）-----
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        System.out.println("共 " + lines.size() + " 行");
        lines.forEach(line -> System.out.println("  > " + line));

        // ----- NIO 读法3：lines() 惰性流（大文件推荐）-----
        System.out.println("\n用 Stream 处理（过滤含'三'的行）：");
        try (var stream = Files.lines(filePath, StandardCharsets.UTF_8)) {
            stream.filter(l -> l.contains("三"))
                  .forEach(System.out::println);
        }

        // ----- 对比：旧版 IO 读法 -----
        /*
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(filePath.toFile()),
                    StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
        */
    }

    static void copyDemo(Path dir) throws IOException {
        Path src  = dir.resolve("notes.txt");
        Path dest = dir.resolve("notes_backup.txt");

        // NIO 复制（一行搞定）
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("\n复制完成：" + src.getFileName() + " → " + dest.getFileName());

        // 验证：两个文件大小相同
        System.out.println("原文件大小：" + Files.size(src) + " 字节");
        System.out.println("副本大小：  " + Files.size(dest) + " 字节");

        // 移动/重命名演示
        Path renamed = dir.resolve("notes_backup_renamed.txt");
        Files.move(dest, renamed, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("重命名后：" + renamed.getFileName());

        // 删除
        Files.deleteIfExists(renamed);
        System.out.println("删除成功，exists=" + Files.exists(renamed));

        // 遍历目录
        System.out.println("\n=== 遍历 " + dir + " 目录 ===");
        try (var paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .forEach(p -> System.out.println("  " + p));
        }
    }
}
```

**NIO vs 旧版 IO 对比小结：**

| 操作 | 旧版 IO | NIO（Files 工具类） |
|------|--------|---------------------|
| 读全部内容 | 手动 BufferedReader 循环 | `Files.readString()` / `Files.readAllLines()` |
| 写内容 | 手动 BufferedWriter | `Files.writeString()` / `Files.write()` |
| 复制文件 | 手动读写循环 | `Files.copy()` |
| 移动/重命名 | `File.renameTo()`（可能失败） | `Files.move()`（更可靠） |
| 遍历目录 | 递归 `File.listFiles()` | `Files.walk()` |

---

## Demo 2：Properties 文件读写（读取配置、修改、保存）

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class PropertiesDemo {

    static final Path CONFIG_PATH = Paths.get("app.properties");

    public static void main(String[] args) throws IOException {
        // ==================== 创建初始配置文件 ====================
        createInitialConfig();

        // ==================== 读取配置 ====================
        Properties props = loadConfig();

        System.out.println("=== 读取配置 ===");
        System.out.println("app.name    = " + props.getProperty("app.name"));
        System.out.println("server.port = " + props.getProperty("server.port"));
        System.out.println("debug       = " + props.getProperty("debug.enabled"));
        System.out.println("不存在的key  = " + props.getProperty("not.exist"));              // null
        System.out.println("有默认值的key = " + props.getProperty("not.exist", "default")); // "default"

        // ==================== 类型转换 ====================
        System.out.println("\n=== 类型转换 ===");
        int port = Integer.parseInt(props.getProperty("server.port", "80"));
        boolean debug = Boolean.parseBoolean(props.getProperty("debug.enabled", "false"));
        int maxConn = Integer.parseInt(props.getProperty("max.connections", "10"));
        System.out.println("port (int)    = " + port);
        System.out.println("debug (bool)  = " + debug);
        System.out.println("maxConn (int) = " + maxConn);

        // ==================== 修改并保存 ====================
        System.out.println("\n=== 修改配置 ===");
        props.setProperty("server.port", "9090");
        props.setProperty("debug.enabled", "false");
        props.setProperty("new.feature.enabled", "true");  // 新增 key
        saveConfig(props);

        // ==================== 重新读取验证 ====================
        Properties reloaded = loadConfig();
        System.out.println("修改后 server.port = " + reloaded.getProperty("server.port"));   // 9090
        System.out.println("新增的key          = " + reloaded.getProperty("new.feature.enabled")); // true

        // 打印 properties 文件内容
        System.out.println("\n=== 当前 app.properties 内容 ===");
        System.out.println(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8));
    }

    /**
     * 创建初始配置文件（演示用，实际项目中配置文件已存在）
     */
    static void createInitialConfig() throws IOException {
        String content = String.join("\n",
            "# 应用配置文件",
            "app.name=MyJavaApp",
            "app.version=1.0.0",
            "server.port=8080",
            "debug.enabled=true",
            "max.connections=100"
        );
        Files.writeString(CONFIG_PATH, content, StandardCharsets.UTF_8);
        System.out.println("初始配置文件已创建：" + CONFIG_PATH.toAbsolutePath());
    }

    /**
     * 加载配置文件
     */
    static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        // 用 InputStreamReader 指定编码，支持 UTF-8 的中文 key/value
        try (InputStream is = Files.newInputStream(CONFIG_PATH);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    /**
     * 保存配置文件
     */
    static void saveConfig(Properties props) throws IOException {
        try (OutputStream os = Files.newOutputStream(CONFIG_PATH);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            props.store(writer, "Updated by PropertiesDemo");
            System.out.println("配置已保存到：" + CONFIG_PATH.toAbsolutePath());
        }
    }
}
```

**注意事项：**
- `props.store()` 写出的文件中，key 的顺序是**不确定的**（Properties 内部是 Hashtable），每次保存顺序可能不同。
- `props.store()` 会自动在文件顶部加时间戳注释（如 `#Sat Mar 15 14:30:00 CST 2024`），这是正常行为。
- 若原配置文件有格式化的注释（如分组注释），`store()` 后会丢失（只保留你传入的那一行注释）。

---

## Demo 3：Jackson 序列化/反序列化（概念演示）

> 前提：项目中已添加 jackson-databind 依赖（见 01-theory.md）。

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class JacksonDemo {

    // ---- 用于序列化的 POJO（必须有无参构造方法和 getter/setter）----
    static class User {
        private String name;
        private int age;
        private String email;

        // 无参构造（Jackson 反序列化时需要）
        public User() {}

        public User(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        // getter & setter（Jackson 通过 getter 读取字段值）
        public String getName() { return name; }
        public int getAge() { return age; }
        public String getEmail() { return email; }
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
        public void setEmail(String email) { this.email = email; }

        @Override
        public String toString() {
            return "User{name=" + name + ", age=" + age + ", email=" + email + "}";
        }
    }

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // 开启格式化输出（便于阅读）
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // ==================== 对象 → JSON 字符串 ====================
        User user = new User("Alice", 30, "alice@example.com");
        String json = mapper.writeValueAsString(user);
        System.out.println("=== 对象 → JSON ===");
        System.out.println(json);
        // 输出：
        // {
        //   "name" : "Alice",
        //   "age" : 30,
        //   "email" : "alice@example.com"
        // }

        // ==================== JSON 字符串 → 对象 ====================
        String inputJson = "{\"name\":\"Bob\",\"age\":25,\"email\":\"bob@example.com\"}";
        User bob = mapper.readValue(inputJson, User.class);
        System.out.println("\n=== JSON → 对象 ===");
        System.out.println(bob);  // User{name=Bob, age=25, email=bob@example.com}

        // ==================== 对象 → JSON 文件 ====================
        mapper.writeValue(Paths.get("user.json").toFile(), user);
        System.out.println("\n=== 序列化到文件 user.json ===");

        // ==================== JSON 文件 → 对象 ====================
        User fromFile = mapper.readValue(Paths.get("user.json").toFile(), User.class);
        System.out.println("=== 从文件反序列化 ===");
        System.out.println(fromFile);

        // ==================== List<User> 序列化 ====================
        List<User> users = Arrays.asList(
            new User("Carol", 28, "carol@example.com"),
            new User("Dave", 35, "dave@example.com")
        );
        String usersJson = mapper.writeValueAsString(users);
        System.out.println("\n=== List 序列化 ===");
        System.out.println(usersJson);

        // ==================== JSON 数组 → List<User> ====================
        List<User> parsedUsers = mapper.readValue(usersJson,
            mapper.getTypeFactory().constructCollectionType(List.class, User.class));
        System.out.println("\n=== JSON 数组反序列化 ===");
        parsedUsers.forEach(u -> System.out.println("  " + u));

        // ==================== 与 NIO 结合：序列化到 Path ====================
        // writeValue 接受 File，先把 Path 转 File
        mapper.writeValue(Paths.get("users.json").toFile(), users);
        System.out.println("\n序列化到 users.json 完成");
    }
}
```

**Jackson 常见注解（了解）：**

```java
public class Order {
    @JsonProperty("order_id")    // JSON key 与字段名不同时使用
    private String orderId;

    @JsonIgnore                  // 序列化时忽略此字段（如密码）
    private String password;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")  // 日期格式化
    private LocalDateTime createdAt;
}
```

**Gson 等价写法（对比）：**

```java
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

Gson gson = new GsonBuilder().setPrettyPrinting().create();

// 对象 → JSON
String json = gson.toJson(user);

// JSON → 对象
User user = gson.fromJson(json, User.class);

// 与 Files 结合写文件
Files.writeString(Paths.get("user.json"), gson.toJson(user), StandardCharsets.UTF_8);
```
