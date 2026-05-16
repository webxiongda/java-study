# 包访问控制与常用类 理论文档

## 核心概念

### 1. package 声明和 import

**package** 是 Java 的命名空间机制，用于组织类文件，避免命名冲突。

```java
// 文件第一行声明所属包
package com.company.project.service;

// 导入其他包的类
import java.util.List;
import java.util.ArrayList;

// 导入整个包（不推荐，会让人不清楚用了哪些类）
import java.util.*;

// 导入静态成员（用于频繁使用的工具方法）
import static java.lang.Math.PI;
import static java.lang.Math.sqrt;

public class UserService {
    public List<String> getUsers() {
        return new ArrayList<>();
    }
}
```

**包命名规范**：全部小写，通常以公司域名倒置开头：
- `com.alibaba.fastjson`
- `org.springframework.boot`
- `io.github.yourname.project`

**目录结构与包名对应**：包 `com.example.service` 对应磁盘路径 `com/example/service/`

### 2. 四种访问修饰符

| 修饰符 | 本类 | 同包 | 子类（跨包） | 其他类 |
|--------|------|------|------------|--------|
| `public` | 是 | 是 | 是 | 是 |
| `protected` | 是 | 是 | 是 | 否 |
| `default`（不写） | 是 | 是 | 否 | 否 |
| `private` | 是 | 否 | 否 | 否 |

```java
package com.example.model;

public class BankAccount {
    public String accountNumber;      // 任何地方都能访问
    protected double balance;         // 同包 + 子类可访问
    double interestRate;              // 同包可访问（default）
    private String password;          // 只有本类内部可访问

    public double getBalance() {      // 公开的 getter
        return balance;
    }

    private boolean verifyPassword(String input) {  // 私有方法
        return password.equals(input);
    }
}
```

**最佳实践**：
- 字段几乎都应该是 `private`，通过 getter/setter 暴露
- 对外 API 用 `public`
- 只给子类用的方法用 `protected`
- 包内工具方法用 `default`（不写修饰符）

### 3. Math 常用方法

`java.lang.Math` 是工具类，所有方法都是 `static`。

```java
// 绝对值
Math.abs(-5)       // 5
Math.abs(-3.14)    // 3.14

// 最大值/最小值
Math.max(10, 20)   // 20
Math.min(10, 20)   // 10

// 幂运算和开方
Math.pow(2, 10)    // 1024.0
Math.sqrt(16)      // 4.0
Math.cbrt(27)      // 3.0（立方根）

// 四舍五入/上取整/下取整
Math.round(3.6)    // 4（返回 long）
Math.ceil(3.1)     // 4.0（向上取整）
Math.floor(3.9)    // 3.0（向下取整）

// 常量
Math.PI            // 3.141592653589793
Math.E             // 2.718281828459045

// 随机数 [0.0, 1.0)
Math.random()

// 对数
Math.log(Math.E)   // 1.0（自然对数）
Math.log10(100)    // 2.0（以10为底）

// 三角函数（参数是弧度）
Math.sin(Math.PI / 2)  // 1.0
Math.cos(0)            // 1.0
```

### 4. Objects 工具类

`java.util.Objects`（注意是 Objects，不是 Object）提供了一系列处理对象的工具方法，最大优点是**空安全（null-safe）**。

```java
import java.util.Objects;

String name = null;

// 空判断
Objects.isNull(name)      // true
Objects.nonNull(name)     // false

// 空值检查，为 null 则抛 NullPointerException（带自定义消息）
String validated = Objects.requireNonNull(name, "name cannot be null");

// 空安全的 toString，为 null 时返回默认值
Objects.toString(name)            // "null"（字符串）
Objects.toString(name, "unknown") // "unknown"（自定义默认值）

// 空安全的 equals（避免 null.equals() 抛 NPE）
Objects.equals(null, "hello")   // false，不抛异常
Objects.equals("hi", "hello")   // false
Objects.equals("hi", "hi")      // true

// 空安全的 hashCode
Objects.hashCode(null)  // 0，不抛异常

// Objects.hash() 用于实现 hashCode 方法
@Override
public int hashCode() {
    return Objects.hash(id, name, email);  // 便捷组合多个字段
}
```

**实际用法示例**：

```java
public class User {
    private Long id;
    private String name;
    private String email;

    public User(Long id, String name, String email) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.email = email;  // email 允许为 null
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return Objects.equals(id, other.id) &&
               Objects.equals(name, other.name) &&
               Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" +
               Objects.toString(email, "N/A") + "'}";
    }
}
```

### 5. String.format 和格式化

```java
// %s 字符串，%d 整数，%f 浮点数，%n 换行（跨平台），%% 百分号
String msg = String.format("Hello, %s! You are %d years old.", "Alice", 25);
// "Hello, Alice! You are 25 years old."

// 控制小数位数
String price = String.format("Price: $%.2f", 9.999);
// "Price: $10.00"

// 控制宽度（右对齐，不足补空格）
String padded = String.format("|%10s|", "Java");
// "|      Java|"

// 左对齐（加 - 号）
String leftAligned = String.format("|%-10s|", "Java");
// "|Java      |"

// 整数前补零
String withZero = String.format("%05d", 42);
// "00042"

// Java 15+ 文本块中也可以用 formatted()
String template = """
    Name: %s
    Score: %.1f
    """.formatted("Bob", 95.5);

// System.out.printf 直接格式化输出（不需要先生成字符串）
System.out.printf("%-15s %5d%n", "Alice", 95);
System.out.printf("%-15s %5d%n", "Bob", 87);
```

### 6. 日期时间入门（LocalDate / LocalDateTime）

Java 8 引入了新的日期时间 API（`java.time` 包），解决了旧版 `java.util.Date` 和 `Calendar` 的诸多问题。

#### LocalDate（日期，不含时间）

```java
import java.time.LocalDate;
import java.time.Month;

// 获取今天的日期
LocalDate today = LocalDate.now();           // 2026-05-16
LocalDate specificDate = LocalDate.of(2026, 1, 15);      // 2026-01-15
LocalDate fromString = LocalDate.parse("2026-03-20");    // 解析字符串

// 读取字段
today.getYear()        // 2026
today.getMonth()       // MAY（枚举）
today.getMonthValue()  // 5
today.getDayOfMonth()  // 16
today.getDayOfWeek()   // SATURDAY（枚举）

// 日期计算（不可变对象，操作返回新对象）
LocalDate nextWeek = today.plusDays(7);
LocalDate lastMonth = today.minusMonths(1);
LocalDate nextYear = today.plusYears(1);

// 比较
today.isBefore(nextWeek)   // true
today.isAfter(nextWeek)    // false
today.isEqual(today)       // true

// 格式化
import java.time.format.DateTimeFormatter;
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
String formatted = today.format(fmt);  // "2026年05月16日"
```

#### LocalDateTime（日期 + 时间）

```java
import java.time.LocalDateTime;
import java.time.LocalTime;

// 获取当前日期时间
LocalDateTime now = LocalDateTime.now();    // 2026-05-16T14:30:00.123

// 创建
LocalDateTime dt = LocalDateTime.of(2026, 5, 16, 14, 30, 0);
LocalDateTime fromDate = LocalDate.now().atTime(9, 0);  // 今天 09:00

// 读取
now.toLocalDate()   // LocalDate 部分
now.toLocalTime()   // LocalTime 部分
now.getHour()       // 14
now.getMinute()     // 30

// 格式化
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String str = now.format(fmt);              // "2026-05-16 14:30:00"
LocalDateTime parsed = LocalDateTime.parse("2026-05-16 14:30:00", fmt);

// 时间差（Period 用于日期，Duration 用于时间）
import java.time.Period;
import java.time.Duration;

LocalDate birthday = LocalDate.of(2000, 3, 15);
Period age = Period.between(birthday, LocalDate.now());
System.out.println("Age: " + age.getYears() + " years");

LocalDateTime start = LocalDateTime.now();
LocalDateTime end = start.plusHours(2).plusMinutes(30);
Duration duration = Duration.between(start, end);
System.out.println("Duration: " + duration.toHours() + " hours");
```

---

## 使用场景

1. **包结构**：Spring Boot 项目通常按层分包：`controller`、`service`、`repository`（或 `mapper`）、`model`（或 `entity`、`dto`）
2. **访问控制**：entity 的字段用 `private` + getter/setter；Service 层内部工具方法用 `private`；需要给子类使用的模板方法用 `protected`
3. **Math**：坐标计算、数值处理、随机数生成
4. **Objects**：任何需要 `equals()`、`hashCode()`、`toString()` 的 POJO 类，以及防空指针校验
5. **String.format**：日志输出、报表生成、SQL 拼接（实际开发用参数化查询，不直接拼 SQL）
6. **LocalDate/LocalDateTime**：存储生日、订单时间、日志时间戳，替代旧版 `java.util.Date`

---

## 工作原理

### 访问控制的编译时检查

访问控制是**编译时**机制，编译器在生成字节码时检查访问权限。运行时通过反射可以绕过（`field.setAccessible(true)`），但这不是正常用法。

### LocalDate 的不可变性

`LocalDate`、`LocalDateTime` 都是**不可变对象（Immutable）**，所有"修改"操作都返回新对象，原对象不变。这让它们天然是线程安全的，和 `String` 设计哲学一致。

```java
LocalDate d = LocalDate.of(2026, 1, 1);
d.plusDays(10);   // 错误用法：返回值被丢弃，d 本身不变
d = d.plusDays(10); // 正确用法：接住返回值
```

### String.format 的内部实现

`String.format()` 底层使用 `Formatter` 类解析格式字符串，逐字符处理，性能比直接字符串拼接慢一些。大量格式化时可考虑 `StringBuilder` + 手动拼接，或者使用日志框架的参数化日志（`log.info("name={}", name)`）。

---

## 常见坑与易错点

### 坑1：default 访问级别和跨包子类

**错误理解**：很多人以为 `protected` 比 `default`（包级别）严格，其实相反。

```java
// 包 com.example.a
package com.example.a;
public class Parent {
    void defaultMethod() { }        // default：只有同包可访问
    protected void protectedMethod() { } // protected：同包 + 所有子类可访问
}

// 包 com.example.b
package com.example.b;
import com.example.a.Parent;

public class Child extends Parent {
    void test() {
        defaultMethod();    // 编译错误！不同包，default 不可访问
        protectedMethod();  // 正确！子类可访问 protected
    }
}
```

### 坑2：LocalDate 操作结果没有接收

**错误示例**：

```java
LocalDate birthday = LocalDate.of(2000, 3, 15);
birthday.plusYears(1);  // 这行毫无意义！LocalDate 是不可变的
System.out.println(birthday); // 仍然是 2000-03-15
```

**正确做法**：

```java
LocalDate nextBirthday = birthday.plusYears(1);
System.out.println(nextBirthday); // 2001-03-15
```

### 坑3：String.format 格式符类型不匹配

**错误示例**：

```java
int count = 42;
String s = String.format("Count: %f", count);  // 运行时异常！int 不能用 %f
```

**正确做法**：

```java
String s1 = String.format("Count: %d", count);      // 整数用 %d
String s2 = String.format("Count: %f", (double)count); // 强转为 double 再用 %f
```

格式符速查：`%s`（任意对象）、`%d`（整数）、`%f`（浮点）、`%b`（布尔）、`%c`（字符）、`%n`（换行）

### 坑4：Objects.equals 和 == 的区别

**错误示例**：

```java
String a = new String("hello");
String b = new String("hello");
System.out.println(a == b);             // false（不同对象）
System.out.println(a.equals(b));        // true，但 a 如果为 null 会 NPE
System.out.println(Objects.equals(a, b)); // true，null 安全
```

**最佳实践**：比较对象内容时永远用 `Objects.equals(a, b)` 或 `"literal".equals(variable)`，避免 NPE。

---

## 面试高频问题

### Q1：四种访问修饰符的区别是什么？protected 和 default 谁的范围更大？

**答**：Java 四种访问修饰符按可见范围从大到小：`public > protected > default（包级）> private`。

- `public`：任何地方都可以访问
- `protected`：同包 + 所有子类（包括不同包的子类）
- `default`（不写）：仅同包内可访问，跨包不行，即使是子类也不行
- `private`：仅本类内部

常见误区：很多人以为 `default` 比 `protected` 严格，实际上 `protected` 范围更大（多了跨包子类）。

**实践中**：字段尽量 `private`，对外方法用 `public`，子类模板方法用 `protected`，包内工具类用 `default`。

### Q2：为什么推荐使用 LocalDate 而不是 java.util.Date？

**答**：旧版 `java.util.Date` 有几个严重问题：
1. **可变性**：`Date` 是可变对象，容易在多线程环境出现 bug，或者通过 getter 拿到引用后被外部修改
2. **设计混乱**：`Date` 同时包含日期和时间，年份从 1900 开始偏移，月份从 0 开始（1月是0），非常反直觉
3. **线程不安全**：`SimpleDateFormat` 是有状态的，多线程共享会出问题

`java.time.LocalDate/LocalDateTime`（Java 8 引入，受 Joda-Time 启发）的优点：
- **不可变**：天然线程安全
- **清晰分离**：`LocalDate` 只有日期，`LocalTime` 只有时间，`LocalDateTime` 两者都有，`ZonedDateTime` 带时区
- **API 直觉**：年份就是年份，月份 1-12，方法名语义清晰（`plusDays`、`minusMonths`）
- **DateTimeFormatter** 线程安全，可以作为常量复用

### Q3：Math.random() 和 Random 类有什么区别？

**答**：
- `Math.random()` 内部实际上是调用了一个全局共享的 `Random` 实例，返回 `[0.0, 1.0)` 的 double
- `new Random()` 更灵活，可以设置种子（`new Random(42)` 每次运行产生相同序列，用于可复现的测试），并提供 `nextInt(bound)`、`nextLong()`、`nextBoolean()` 等方法
- Java 17+ 引入 `RandomGenerator` 接口和 `ThreadLocalRandom`（多线程场景高性能随机数）
- **安全场景**（如密码、token）必须用 `java.security.SecureRandom`，`Math.random()` 是伪随机数，不安全

### Q4：Objects.requireNonNull 有什么用？什么时候用它？

**答**：`Objects.requireNonNull(obj, "message")` 在对象为 null 时立即抛出 `NullPointerException`，附带自定义错误信息。

用途：**快速失败（fail-fast）原则**。在方法入口或构造器中对必须非空的参数进行校验，让 NPE 在"问题发生处"抛出，而不是在后续某个不相关的地方才崩溃，大幅降低排查难度。

```java
public UserService(UserRepository repo) {
    // 如果 repo 是 null，这里立即抛 NPE，错误信息明确
    this.repo = Objects.requireNonNull(repo, "UserRepository must not be null");
}
```

与直接写 `if (obj == null) throw new NullPointerException("...")` 等价，但更简洁。Spring 框架内部大量使用这种模式。

### Q5：Spring Boot 项目里包结构通常怎么组织？

**答**：Spring Boot 项目通常有两种主流包结构：

**按层分包**（Layer-first）：
```
com.example.myapp
├── controller    （HTTP 入口层）
├── service       （业务逻辑层）
├── repository    （数据访问层）
├── model/entity  （数据模型）
├── dto           （数据传输对象）
└── config        （配置类）
```

**按功能/模块分包**（Feature-first，推荐用于大型项目）：
```
com.example.myapp
├── user
│   ├── UserController
│   ├── UserService
│   ├── UserRepository
│   └── User
├── order
│   ├── OrderController
│   └── ...
└── common        （公共组件）
```

特点：按功能分包时同一业务的代码放在一起，内部可以使用 `default` 访问控制，对外只暴露 `public` 接口；按层分包更直观，适合小型项目。实际上很多项目混合使用两种方式。
