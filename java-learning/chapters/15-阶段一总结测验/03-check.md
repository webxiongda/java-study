# Chapter 15 - 阶段一总结：综合测验（10 题）

> 覆盖 Chapter 01-14 全部知识点，含代码改错题和设计题。建议限时 60 分钟独立作答。

---

## Q1（代码改错）：找出并修复以下代码中的所有问题

```java
public class BankAccount {
    public double balance;
    public String owner;

    BankAccount(String owner, double initialBalance) {
        owner = owner;              // 问题A
        this.balance = initialBalance;
    }

    public void withdraw(double amount) {
        balance = balance - amount; // 问题B（业务逻辑缺失）
    }

    public boolean equals(BankAccount other) { // 问题C
        return this.owner.equals(other.owner);
    }
}
```

---

### 答案与解析

**问题 A：`owner = owner` 没有赋值给字段**

```java
// 错误：将参数赋给参数自身，字段 this.owner 始终为 null
owner = owner;

// 修复：
this.owner = owner;
```

**问题 B：withdraw 没有校验余额是否充足**

```java
// 错误：可以透支，balance 变为负数
balance = balance - amount;

// 修复：
public void withdraw(double amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("取款金额必须大于 0");
    }
    if (amount > balance) {
        throw new IllegalStateException("余额不足，当前余额：" + balance);
    }
    balance -= amount;
}
```

**问题 C：`equals` 方法签名错误，未重写 Object 的 equals**

```java
// 错误：参数类型是 BankAccount，不是 Object，这是方法重载而非重写
// Object 的 equals(Object o) 未被覆盖，== 比较的仍是内存地址
public boolean equals(BankAccount other) { ... }

// 修复：
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BankAccount)) return false;
    BankAccount other = (BankAccount) o;
    return Objects.equals(this.owner, other.owner);
}

@Override
public int hashCode() {
    return Objects.hash(owner);  // 同时重写 hashCode！
}
```

**附加问题：字段应该是 private**
```java
private double balance;   // 不应 public，防止外部直接修改
private String owner;
```

---

## Q2（概念分析）：以下代码输出什么？解释原因。

```java
public class StringTest {
    public static void main(String[] args) {
        String s1 = "hello";
        String s2 = "hello";
        String s3 = new String("hello");
        String s4 = s3.intern();

        System.out.println(s1 == s2);       // (1)
        System.out.println(s1 == s3);       // (2)
        System.out.println(s1 == s4);       // (3)
        System.out.println(s1.equals(s3));  // (4)

        String s5 = "he" + "llo";           // 编译期常量折叠
        String s6 = "he";
        String s7 = s6 + "llo";             // 运行时拼接

        System.out.println(s1 == s5);       // (5)
        System.out.println(s1 == s7);       // (6)
    }
}
```

---

### 答案与解析

```
true   ← (1)
false  ← (2)
true   ← (3)
true   ← (4)
true   ← (5)
false  ← (6)
```

| 行 | 结果 | 原因 |
|----|------|------|
| (1) | true | s1、s2 都是字符串字面量，JVM 字符串常量池中同一个对象 |
| (2) | false | `new String("hello")` 在堆上创建新对象，不是常量池中的对象 |
| (3) | true | `intern()` 返回常量池中的引用，与 s1 相同 |
| (4) | true | `equals()` 比较内容，内容都是 "hello" |
| (5) | true | `"he" + "llo"` 是两个字面量相加，**编译期折叠**为 "hello"，直接指向常量池 |
| (6) | false | `s6 + "llo"` 中 s6 是变量，**运行时**拼接，等价于 `new StringBuilder(s6).append("llo").toString()`，在堆上创建新对象 |

---

## Q3（设计题）：设计一个线程安全的计数器

**要求：**
- 实现 `Counter` 类，支持 `increment()`、`decrement()`、`getCount()` 三个方法
- 使用 `synchronized` 保证线程安全（不需要真正运行多线程，但实现要正确）
- 计数值不能低于 0（`decrement` 时若已为 0 则忽略）
- 用 `AtomicInteger` 实现相同功能（两种方案对比）

---

### 参考答案

**方案1：synchronized**

```java
public class Counter {
    private int count = 0;

    public synchronized void increment() {
        count++;
    }

    public synchronized void decrement() {
        if (count > 0) {
            count--;
        }
    }

    public synchronized int getCount() {
        return count;
    }
}
```

**方案2：AtomicInteger（更高效，无锁 CAS）**

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }

    public void decrement() {
        count.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public int getCount() {
        return count.get();
    }
}
```

**两者对比：**
- `synchronized`：悲观锁，同一时刻只有一个线程能执行，适合竞争激烈场景
- `AtomicInteger`：乐观锁（CAS），无阻塞，适合竞争不激烈的场景，性能通常更好

---

## Q4（集合综合）：分析以下 Map 使用场景，选择合适的 Map 实现

**场景描述：**

1. 电商平台的**商品 ID → 商品详情**映射，高并发读写，不需要排序。
2. 统计一篇文章中**每个单词出现的次数**，最终按字母序输出结果。
3. 实现一个**浏览历史记录**，保存最近访问的 URL（按访问顺序，最多保留 10 条）。
4. 银行系统**账号 → 余额**，对并发线程安全有严格要求。

**问题：** 四个场景分别应该用哪种 Map？说明理由。

---

### 参考答案

**场景1 → HashMap**

理由：不需要顺序，HashMap 的 get/put 均摊 O(1)，性能最好。（如果高并发读写，应用 ConcurrentHashMap，但题目是基础集合范围内选 HashMap）

**场景2 → TreeMap**

理由：TreeMap 按 key 自然顺序（字母序）维护排序，遍历时自动得到排序结果，无需额外排序步骤。HashMap 需要先统计再单独排序。

```java
TreeMap<String, Integer> wordCount = new TreeMap<>();
// 遍历文章单词，统计...
// 直接遍历 TreeMap 即可按字母序输出
```

**场景3 → LinkedHashMap（开启 accessOrder 模式）**

理由：LinkedHashMap 支持按**访问顺序**维护，且重写 `removeEldestEntry` 可以实现固定容量的 LRU 缓存（最近最少使用）。

```java
Map<String, String> history = new LinkedHashMap<String, String>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > 10;  // 超过 10 条自动移除最旧的
    }
};
```

**场景4 → ConcurrentHashMap**

理由：`HashMap` 线程不安全，并发写会导致死循环（Java 7）或数据不一致。`Hashtable` 用 `synchronized` 锁全表，性能差。`ConcurrentHashMap` 使用分段锁（Java 8+ 改为 CAS + synchronized 锁单个桶），高并发下兼顾安全与性能。

---

## Q5（IO 综合）：代码改错

以下代码尝试读取文件所有行并统计包含关键字 "ERROR" 的行数，找出所有问题并修复。

```java
public class LogAnalyzer {
    public static int countErrors(String filePath) {
        int count = 0;
        FileReader fr = new FileReader(filePath);         // 问题1
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();                      // 问题2
        while (line != null) {
            if (line.contains("ERROR")) count++;
            line = br.readLine();
        }
        return count;
        // 问题3：流没有关闭
    }
}
```

---

### 参考答案

**问题1：`FileReader` 构造抛出受检异常 `FileNotFoundException`，必须处理**

`FileReader(String)` 声明抛 `FileNotFoundException`（受检异常），不在 try 块中或不声明 `throws` 会编译错误。

**问题2：`readLine()` 也抛 `IOException`，同样需要处理**

**问题3：流未关闭，资源泄漏**

**修复后的完整代码：**

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class LogAnalyzer {

    public static int countErrors(String filePath) throws IOException {
        int count = 0;

        // 修复：使用 try-with-resources 自动关闭，指定 UTF-8 编码
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("ERROR")) {
                    count++;
                }
            }
        }
        // br 自动关闭，无论是否抛出异常

        return count;
    }

    // 更简洁的 NIO 写法（Java 8+）：
    public static long countErrorsNio(String filePath) throws IOException {
        try (var lines = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
            return lines.filter(line -> line.contains("ERROR")).count();
        }
    }
}
```

---

## Q6（泛型）：以下代码能编译通过吗？分析原因。

```java
import java.util.*;

public class GenericTest {

    public static double sum(List<? extends Number> list) {  // (A)
        double total = 0;
        for (Number n : list) {
            total += n.doubleValue();
        }
        return total;
    }

    public static void addNumbers(List<? super Integer> list) {  // (B)
        list.add(1);
        list.add(2);
        list.add(3);
    }

    public static void main(String[] args) {
        List<Integer> ints = Arrays.asList(1, 2, 3);
        List<Double>  dbls = Arrays.asList(1.5, 2.5, 3.5);

        System.out.println(sum(ints));  // (1)
        System.out.println(sum(dbls));  // (2)

        List<Number> nums = new ArrayList<>();
        addNumbers(nums);               // (3)
        System.out.println(nums);       // (4)

        // List<String> strs = new ArrayList<>();
        // addNumbers(strs);            // (5) 能通过吗？
    }
}
```

---

### 答案与解析

**(1) `sum(ints)` → 6.0** — 可以编译，`Integer` 是 `Number` 的子类，符合 `? extends Number`。

**(2) `sum(dbls)` → 7.5** — 可以编译，`Double` 也是 `Number` 的子类，同样符合。

**(3) `addNumbers(nums)` → 可以编译**，`List<Number>` 中 `Number` 是 `Integer` 的父类，符合 `? super Integer`。

**(4) 输出 `[1, 2, 3]`** — 三个 Integer 被添加到 nums 中。

**(5) `addNumbers(strs)` — 编译错误**

`List<String>` 中 `String` 不是 `Integer` 的父类（也不是 `Integer` 的祖先类），不符合 `? super Integer`，编译器拒绝。

**记忆技巧：PECS 原则（Producer Extends, Consumer Super）**
- `? extends T`：只能读（Producer），不能写（因为不知道具体子类型，无法安全写入）
- `? super T`：只能写（Consumer），读出来只能当 Object 用

---

## Q7（异常设计）：设计一个用户注册服务的异常体系

**背景：** 用户注册时可能出现以下错误：
- 邮箱格式不合法
- 邮箱已被注册（需要业务层感知）
- 数据库连接失败（底层错误）

**要求：** 设计自定义异常类，并在 `UserService.register()` 方法中正确使用。

---

### 参考答案

```java
// 基类：所有业务异常的父类（继承 RuntimeException，无需强制 try-catch）
public class BusinessException extends RuntimeException {
    private final int errorCode;

    public BusinessException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() { return errorCode; }
}

// 具体业务异常
public class InvalidEmailException extends BusinessException {
    public InvalidEmailException(String email) {
        super(4001, "邮箱格式不合法：" + email);
    }
}

public class EmailAlreadyExistsException extends BusinessException {
    public EmailAlreadyExistsException(String email) {
        super(4002, "邮箱已被注册：" + email);
    }
}

// 系统级异常（数据库等基础设施问题）
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);  // 保留原始异常（cause），便于排查
    }
}

// 使用
public class UserService {
    public void register(String email, String password) {
        // 校验格式
        if (!email.contains("@")) {
            throw new InvalidEmailException(email);
        }

        // 检查重复（假设调用 DAO）
        try {
            if (userDao.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }
            userDao.save(new User(email, password));
        } catch (SQLException e) {
            // 将底层异常包装为业务异常，避免泄漏实现细节
            throw new DataAccessException("注册用户时数据库出错", e);
        }
    }
}
```

**设计原则：**
1. 自定义异常继承 `RuntimeException`（非受检），避免强制 try-catch 污染业务代码
2. 提供有意义的错误码和消息，便于前端处理和日志排查
3. 包装底层异常时保留 `cause`（原始异常），不要丢失堆栈信息

---

## Q8（综合应用）：分析以下代码的性能问题并优化

```java
public class DataProcessor {

    // 原始版本：从大量数据中找出所有包含特定关键字的条目
    public List<String> findByKeyword(List<String> data, String keyword) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).indexOf(keyword) >= 0) {
                result.add(data.get(i));
            }
        }
        return result;
    }

    // 字符串拼接版本：将结果拼成一个字符串
    public String joinResults(List<String> items) {
        String result = "";
        for (String item : items) {
            result += item + ", ";  // 性能问题在哪？
        }
        return result;
    }
}
```

---

### 参考答案

**`findByKeyword` 的问题：**

`data.get(i)` 调用了两次（一次判断，一次添加）。对 `ArrayList` 来说是 O(1) 所以无大问题，但仍不优雅。更好的写法：

```java
public List<String> findByKeyword(List<String> data, String keyword) {
    List<String> result = new ArrayList<>();
    for (String item : data) {              // for-each 更简洁
        if (item.contains(keyword)) {       // contains 比 indexOf >= 0 更清晰
            result.add(item);
        }
    }
    return result;
    
    // Java 8+ 更简洁：
    // return data.stream()
    //            .filter(s -> s.contains(keyword))
    //            .collect(Collectors.toList());
}
```

**`joinResults` 的严重性能问题：**

```java
result += item + ", ";
```

`String` 是不可变的，每次 `+=` 都会创建新的 `String` 对象：
- n 个元素 → 创建 O(n²) 个临时字符串对象
- 100 个元素：约创建 5000 个字符串对象
- 10000 个元素：约创建 50,000,000 个字符串对象

**优化方案：**

```java
public String joinResults(List<String> items) {
    if (items == null || items.isEmpty()) return "";

    // 方案1：StringBuilder（手动）
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
        sb.append(items.get(i));
        if (i < items.size() - 1) {
            sb.append(", ");
        }
    }
    return sb.toString();

    // 方案2：String.join（Java 8+，推荐）
    // return String.join(", ", items);

    // 方案3：Stream + Collectors.joining
    // return items.stream().collect(Collectors.joining(", "));
}
```

**性能对比（1000 个元素）：**
- 原版 `+=`：耗时约 50ms，创建约 50 万个临时对象
- `StringBuilder`：耗时约 0.1ms，几乎无额外对象创建

---

## Q9（IO + 集合综合）：以下代码实现了读取配置文件并缓存的功能，找出问题。

```java
public class ConfigCache {
    private static Map<String, String> cache = new HashMap<>();

    public static String get(String key) throws IOException {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Properties props = new Properties();
        FileInputStream fis = new FileInputStream("config.properties");
        props.load(fis);
        // 注意：fis 未关闭

        for (String k : props.stringPropertyNames()) {
            cache.put(k, props.getProperty(k));
        }

        return cache.get(key);  // 可能返回 null
    }
}
```

---

### 参考答案

**问题1：FileInputStream 未关闭**

若不关闭，每次调用 `get()` 都会占用一个文件句柄，时间长了导致"Too many open files"错误。

**问题2：返回值可能为 null，调用方容易 NPE**

若 key 不在配置文件中，`cache.get(key)` 返回 `null`，调用方使用时容易 NullPointerException。

**问题3：并发场景下 HashMap 不安全**

若多线程同时调用 `get()`，`HashMap` 的并发写可能导致数据丢失。

**修复后：**

```java
public class ConfigCache {
    // 使用 ConcurrentHashMap 保证并发安全
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private static synchronized void loadIfNeeded() throws IOException {
        if (loaded) return;
        Properties props = new Properties();
        // try-with-resources 确保关闭
        try (InputStream is = Files.newInputStream(Paths.get("config.properties"));
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        for (String k : props.stringPropertyNames()) {
            cache.put(k, props.getProperty(k));
        }
        loaded = true;
    }

    public static String get(String key) throws IOException {
        loadIfNeeded();
        return cache.getOrDefault(key, "");  // 不存在返回空字符串而非 null
    }

    public static String get(String key, String defaultValue) throws IOException {
        loadIfNeeded();
        return cache.getOrDefault(key, defaultValue);
    }
}
```

---

## Q10（综合设计题）：设计一个简单的学生成绩管理系统

**要求：**

1. `Student` 类：字段 id（String）、name（String）、score（double）；按 id 实现 equals/hashCode；实现 `Comparable<Student>`（按成绩降序）。
2. `GradeManager` 类：
   - `addStudent(Student s)`：添加学生（同 id 的视为更新）
   - `getTopN(int n)`：返回成绩最高的 N 个学生
   - `getAverage()`：返回所有学生的平均分
   - `getByScoreRange(double min, double max)`：返回成绩在 [min, max] 范围内的学生列表（按成绩降序）

**写出完整实现（含 main 方法验证）。**

---

### 参考答案

```java
import java.util.*;
import java.util.stream.*;

public class GradeSystem {

    static class Student implements Comparable<Student> {
        private final String id;
        private final String name;
        private final double score;

        public Student(String id, String name, double score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }

        public String getId()    { return id; }
        public String getName()  { return name; }
        public double getScore() { return score; }

        @Override
        public int compareTo(Student other) {
            // 按成绩降序（大的在前）
            return Double.compare(other.score, this.score);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Student)) return false;
            return Objects.equals(id, ((Student) o).id);
        }

        @Override
        public int hashCode() { return Objects.hash(id); }

        @Override
        public String toString() {
            return String.format("Student{id='%s', name='%s', score=%.1f}", id, name, score);
        }
    }

    static class GradeManager {
        // 用 HashMap 存储，key 是 id，方便按 id 查找/更新
        private final Map<String, Student> students = new HashMap<>();

        public void addStudent(Student s) {
            students.put(s.getId(), s);  // 同 id 自动覆盖旧记录
        }

        public List<Student> getTopN(int n) {
            return students.values().stream()
                .sorted()               // 调用 Student.compareTo（成绩降序）
                .limit(n)
                .collect(Collectors.toList());
        }

        public double getAverage() {
            if (students.isEmpty()) return 0;
            return students.values().stream()
                .mapToDouble(Student::getScore)
                .average()
                .orElse(0);
        }

        public List<Student> getByScoreRange(double min, double max) {
            return students.values().stream()
                .filter(s -> s.getScore() >= min && s.getScore() <= max)
                .sorted()               // 按成绩降序
                .collect(Collectors.toList());
        }
    }

    public static void main(String[] args) {
        GradeManager manager = new GradeManager();

        manager.addStudent(new Student("S001", "张三", 88));
        manager.addStudent(new Student("S002", "李四", 95));
        manager.addStudent(new Student("S003", "王五", 72));
        manager.addStudent(new Student("S004", "赵六", 88));
        manager.addStudent(new Student("S005", "陈七", 60));

        // 更新 S003 的成绩（同 id，覆盖）
        manager.addStudent(new Student("S003", "王五", 85));

        System.out.println("=== Top 3 ===");
        manager.getTopN(3).forEach(System.out::println);

        System.out.printf("%n平均分：%.2f%n", manager.getAverage());

        System.out.println("\n=== 成绩 80-90 的学生 ===");
        manager.getByScoreRange(80, 90).forEach(System.out::println);
    }
}
```

**预期输出：**
```
=== Top 3 ===
Student{id='S002', name='李四', score=95.0}
Student{id='S001', name='张三', score=88.0}
Student{id='S004', name='赵六', score=88.0}

平均分：81.60

=== 成绩 80-90 的学生 ===
Student{id='S001', name='张三', score=88.0}
Student{id='S004', name='赵六', score=88.0}
Student{id='S003', name='王五', score=85.0}
```
