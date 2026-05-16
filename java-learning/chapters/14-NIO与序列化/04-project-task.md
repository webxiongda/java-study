# Chapter 14 - NIO 与序列化：项目任务

## 业务背景

你正在为一个 Java 应用开发**配置管理工具**。应用启动时需要从 `app.properties` 读取配置（数据库地址、端口、开关等），运行时支持动态修改配置并持久化，也支持在配置文件被外部修改后重新加载。

要求使用现代 NIO API（`Path` + `Files`），不使用旧版 `File` + 流组合。

---

## 任务说明

实现 `ConfigManager` 类：

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class ConfigManager {

    private final Path configPath;
    private Properties props;

    public ConfigManager(Path configPath) throws IOException {
        this.configPath = configPath;
        this.props = new Properties();
        reload();  // 构造时立即加载
    }

    /**
     * 获取字符串配置值
     * @param key         配置键
     * @param defaultValue 键不存在时返回的默认值
     */
    public String getString(String key, String defaultValue) { ... }

    /**
     * 获取整数配置值
     * 若值不存在或解析失败，返回 defaultValue
     */
    public int getInt(String key, int defaultValue) { ... }

    /**
     * 获取布尔配置值
     * "true"（不区分大小写）→ true，其余 → false
     * 键不存在时返回 defaultValue
     */
    public boolean getBoolean(String key, boolean defaultValue) { ... }

    /**
     * 设置（新增或修改）一个配置项（仅修改内存中的 props，不自动持久化）
     */
    public void set(String key, String value) { ... }

    /**
     * 将当前 props 写回 configPath 文件（覆盖原文件）
     * 使用 UTF-8 编码，注释为 "Saved by ConfigManager"
     * @throws IOException 写入失败时抛出
     */
    public void save() throws IOException { ... }

    /**
     * 重新从文件加载配置（丢弃内存中未保存的修改）
     * 若文件不存在，内部 props 置为空（不抛异常）
     * @throws IOException 文件存在但读取失败时抛出
     */
    public void reload() throws IOException { ... }
}
```

### 编写 main 方法验证

```java
public class ConfigManagerDemo {
    public static void main(String[] args) throws Exception {
        // 准备初始配置文件
        Path configPath = Paths.get("app.properties");
        Files.writeString(configPath, String.join("\n",
            "# 应用配置",
            "app.name=MyApp",
            "server.port=8080",
            "debug.enabled=true",
            "max.retry=3"
        ), StandardCharsets.UTF_8);

        ConfigManager config = new ConfigManager(configPath);

        // 读取各类型配置
        System.out.println("app.name    = " + config.getString("app.name", "unknown"));
        System.out.println("server.port = " + config.getInt("server.port", 80));
        System.out.println("debug       = " + config.getBoolean("debug.enabled", false));
        System.out.println("max.retry   = " + config.getInt("max.retry", 1));
        System.out.println("不存在的key  = " + config.getString("not.exist", "default"));

        // 修改配置（内存中）
        config.set("server.port", "9090");
        config.set("new.feature", "enabled");

        System.out.println("\n修改后 server.port（内存）= "
            + config.getInt("server.port", 80));

        // 保存到文件
        config.save();

        // reload 重新加载，验证持久化成功
        config.reload();
        System.out.println("reload 后 server.port = "
            + config.getInt("server.port", 80));  // 应为 9090
        System.out.println("reload 后 new.feature = "
            + config.getString("new.feature", "none"));  // 应为 "enabled"

        // getInt 解析失败时使用默认值
        config.set("bad.int", "not_a_number");
        System.out.println("bad.int = " + config.getInt("bad.int", -1));  // -1
    }
}
```

---

## 验收标准

1. **NIO API 正确使用**：`reload()` 和 `save()` 均使用 `Path` + `Files.newInputStream` / `Files.newOutputStream`，不使用旧版 `new FileInputStream(file)` 写法。
2. **编码统一 UTF-8**：`reload()` 和 `save()` 均通过 `InputStreamReader/OutputStreamWriter` 指定 `StandardCharsets.UTF_8`，支持中文 value。
3. **getInt / getBoolean 健壮性**：解析失败（如 value 为 `"abc"` 时调用 `getInt`）不抛出异常，返回指定默认值。
4. **文件不存在时 reload 不抛异常**：`reload()` 应检查 `Files.exists(configPath)`，文件不存在时将 props 重置为空而非抛 IOException。

---

## 常见坑

### 坑 1：save() 用 Files.writeString 写 Properties，格式错误

```java
// 错误：手动把 props 转字符串，格式不符合 .properties 规范
String content = props.toString();  // 输出 {key=value, ...}，不是合法 properties 格式
Files.writeString(configPath, content, StandardCharsets.UTF_8);
```

必须使用 `props.store(writer, comment)` 来序列化，它会输出合法的 `key=value` 格式，并正确转义特殊字符（如 `=`、`:`、`\n` 等）：

```java
try (OutputStream os = Files.newOutputStream(configPath);
     Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
    props.store(writer, "Saved by ConfigManager");
}
```

---

### 坑 2：getInt 没有捕获 NumberFormatException

```java
// 错误：若 value 是 "abc"，直接抛出 NumberFormatException，而非返回默认值
public int getInt(String key, int defaultValue) {
    String value = props.getProperty(key);
    return Integer.parseInt(value);  // 未处理 null 和格式异常
}

// 正确：
public int getInt(String key, int defaultValue) {
    String value = props.getProperty(key);
    if (value == null) return defaultValue;
    try {
        return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
        return defaultValue;  // 解析失败，静默返回默认值
    }
}
```
