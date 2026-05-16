# Chapter 13 - IO 基础：项目任务

## 业务背景

你正在为一个 Java 应用开发**日志记录工具**。应用在运行过程中需要将重要事件写入日志文件，运维人员需要能够查看全部日志、统计日志行数，也需要在必要时清空日志。

要求日志格式统一（含时间戳），文件编码为 UTF-8，追加写入不覆盖历史，且每次操作都要正确关闭文件流。

---

## 任务说明

实现 `FileLogger` 类，支持以下 4 个操作：

```java
public class FileLogger {

    private final String filePath;

    public FileLogger(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 追加一条日志到文件末尾
     * 格式：[yyyy-MM-dd HH:mm:ss] message\n
     * 例如：[2024-03-15 14:30:22] 用户登录成功
     *
     * @param message 日志内容
     * @throws IOException 写入失败时抛出
     */
    public void append(String message) throws IOException { ... }

    /**
     * 读取并返回日志文件的全部内容（每行作为一个字符串）
     * 若文件不存在，返回空列表
     *
     * @return 日志行列表
     * @throws IOException 读取失败时抛出
     */
    public List<String> readAll() throws IOException { ... }

    /**
     * 清空日志文件（文件保留，内容清空）
     *
     * @throws IOException 操作失败时抛出
     */
    public void clear() throws IOException { ... }

    /**
     * 统计日志文件的行数
     * 若文件不存在，返回 0
     *
     * @return 日志行数
     * @throws IOException 读取失败时抛出
     */
    public int countLines() throws IOException { ... }
}
```

### 时间戳格式要求

使用 `java.time.LocalDateTime` 和 `java.time.format.DateTimeFormatter`：

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String timestamp = LocalDateTime.now().format(formatter);
String logLine = "[" + timestamp + "] " + message;
```

### 编写 main 方法验证

```java
public class FileLoggerDemo {
    public static void main(String[] args) throws IOException {
        FileLogger logger = new FileLogger("app.log");

        // 1. 清空（确保初始状态干净）
        logger.clear();
        System.out.println("初始行数：" + logger.countLines()); // 0

        // 2. 追加日志
        logger.append("应用启动");
        Thread.sleep(1000); // 让时间戳有差异（仅演示用）
        logger.append("用户 Alice 登录");
        logger.append("查询订单列表，共 42 条");
        logger.append("用户 Alice 退出");

        // 3. 读取全部日志
        System.out.println("\n=== 全部日志 ===");
        logger.readAll().forEach(System.out::println);

        // 4. 统计行数
        System.out.println("\n当前日志行数：" + logger.countLines()); // 4

        // 5. 再次追加，验证追加不覆盖
        logger.append("应用关闭");
        System.out.println("追加后行数：" + logger.countLines()); // 5

        // 6. 清空
        logger.clear();
        System.out.println("清空后行数：" + logger.countLines()); // 0
    }
}
```

---

## 验收标准

1. **追加语义正确**：多次调用 `append()` 后，`readAll()` 返回的列表条数与追加次数一致，不会覆盖历史记录。
2. **时间戳格式准确**：每行日志开头为 `[yyyy-MM-dd HH:mm:ss]`，格式严格匹配（含方括号和空格）。
3. **编码统一 UTF-8**：日志文件中包含中文时，用文本编辑器（设置 UTF-8）打开不乱码。
4. **流安全关闭**：所有文件操作使用 try-with-resources，即使发生 IOException 也不会泄漏文件句柄。

---

## 常见坑

### 坑 1：FileWriter 默认覆盖，忘记开启追加模式

```java
// 错误：每次调用 append() 都会清空文件，只保留最后一条日志
FileWriter fw = new FileWriter(filePath);

// 正确：第二个参数 true 表示追加
new FileOutputStream(filePath, true)
```

一定要在构造 `FileOutputStream`（或 `FileWriter`）时传入 `true`，否则每次 `append()` 调用都会覆盖文件，日志全部丢失。

---

### 坑 2：clear() 的实现方式错误

```java
// 方式1：删除文件（clear 后 countLines 返回 0 正确，但文件不存在）
new File(filePath).delete();  // 文件被彻底删掉，不符合"文件保留"要求

// 方式2：用 FileWriter 覆盖模式写入空字符串（推荐）
try (FileWriter fw = new FileWriter(filePath)) {
    // 不写任何内容，FileWriter 会将文件截断为 0 字节
}

// 方式3：用 FileOutputStream 覆盖模式（等价）
try (FileOutputStream fos = new FileOutputStream(filePath)) {
    // 空写，文件被清空
}
```

需求是"文件保留，内容清空"，应选方式 2 或 3。

---

### 坑 3：readAll() 和 countLines() 对文件不存在的处理

```java
// 错误：文件不存在时，FileInputStream 抛出 FileNotFoundException
public List<String> readAll() throws IOException {
    try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(filePath), ...))) {
        // ...
    }
    // 若文件不存在，上面直接抛异常，调用方得到异常而不是空列表
}

// 正确：先检查文件是否存在
public List<String> readAll() throws IOException {
    File file = new File(filePath);
    if (!file.exists()) {
        return new ArrayList<>();  // 文件不存在，返回空列表
    }
    // 正常读取逻辑
}
```

`countLines()` 同理，文件不存在时应返回 0 而非抛异常。
