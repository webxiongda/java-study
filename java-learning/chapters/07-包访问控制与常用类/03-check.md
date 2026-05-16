# Chapter 07 - 自测题

## Q1（概念）：四种访问修饰符的作用范围是什么？

**题目**：请说明 Java 中 `private`、`（默认/包私有）`、`protected`、`public` 四种访问修饰符的可见范围，并用表格对比。

**参考答案**：

| 修饰符 | 同一类 | 同一包 | 子类（不同包） | 其他包 |
|--------|--------|--------|--------------|--------|
| `private` | ✅ | ❌ | ❌ | ❌ |
| 默认（无修饰符） | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

**要点说明**：
- `private`：封装的核心，仅在声明它的类内部可见，常用于字段和内部实现方法。
- 默认（包私有）：不写任何修饰符时生效，适合包内协作的辅助类或方法。
- `protected`：在继承场景中开放给子类，同时对同包类也可见，适合设计为"可被子类扩展"的方法。
- `public`：完全开放，API 的公开接口使用此修饰符。

**设计原则**：始终遵循最小权限原则——能用 `private` 就不用 `protected`，能用 `protected` 就不用 `public`。

---

## Q2（概念）：Math.round(2.5) 和 Math.round(-2.5) 分别返回什么，为什么？

**题目**：不运行代码，判断以下两个表达式的返回值并解释原因：
```java
Math.round(2.5)
Math.round(-2.5)
```

**参考答案**：

- `Math.round(2.5)` 返回 **3**
- `Math.round(-2.5)` 返回 **-2**

**原因**：

`Math.round(x)` 的实现等价于 `(long) Math.floor(x + 0.5)`，即先加 0.5，再向下取整（floor）。

- `Math.round(2.5)` → `floor(2.5 + 0.5)` = `floor(3.0)` = **3**
- `Math.round(-2.5)` → `floor(-2.5 + 0.5)` = `floor(-2.0)` = **-2**

这是 Java 采用的"半数向正无穷方向舍入"（half-up toward positive infinity）策略，与数学上的"四舍五入"对正数一致，但对负数行为不同——`-2.5` 舍入结果是 `-2` 而不是 `-3`。

**对比**：如果需要标准的"银行家舍入"（half-even），应使用 `BigDecimal.setScale(0, RoundingMode.HALF_EVEN)`。

---

## Q3（实操）：写一个 DateUtils 工具类

**题目**：写一个 `DateUtils` 工具类，包含一个将 `LocalDate` 格式化为 `"yyyy-MM-dd"` 字符串的静态方法。要求：该类不可被实例化。

**参考答案**：

```java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    // 私有构造器，防止实例化
    private DateUtils() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    // 定义格式化器（DateTimeFormatter 线程安全，可作为常量复用）
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 将 LocalDate 格式化为 "yyyy-MM-dd" 字符串
     *
     * @param date 待格式化的日期，不能为 null
     * @return 格式化后的字符串，如 "2025-07-20"
     * @throws IllegalArgumentException 如果 date 为 null
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date 不能为 null");
        }
        return date.format(DATE_FORMATTER);
    }
}
```

**使用示例**：
```java
System.out.println(DateUtils.formatDate(LocalDate.now()));
// 输出类似：2025-07-20

System.out.println(DateUtils.formatDate(LocalDate.of(2000, 1, 1)));
// 输出：2000-01-01
```

**关键点**：
1. `private DateUtils()` 阻止 `new DateUtils()`。
2. `DateTimeFormatter` 是线程安全的，声明为 `static final` 常量复用，避免每次调用重复创建。
3. 做 null 检查，防御性编程。

---

## Q4（实操）：以下代码有什么访问控制问题？

**题目**：阅读以下代码，找出所有访问控制问题并说明如何修复。

```java
// 文件：com/example/service/UserService.java
package com.example.service;

public class UserService {
    public String username;           // 问题 1
    public String password;           // 问题 2
    String dbUrl = "jdbc:mysql://..."; // 问题 3

    public void login(String user, String pwd) {
        System.out.println("Connecting to: " + dbUrl);
        // ... 业务逻辑
    }

    public String buildSql(String user) {  // 问题 4
        return "SELECT * FROM users WHERE name='" + user + "'";
    }
}

// 文件：com/example/ui/LoginController.java
package com.example.ui;

import com.example.service.UserService;

public class LoginController {
    public void handleLogin() {
        UserService service = new UserService();
        service.password = "123456";   // 直接赋值密码
        service.login("admin", "123456");
    }
}
```

**参考答案**：

**问题 1：`public String username`**
- 缺陷：字段直接暴露，外部可随意读写，破坏封装性。
- 修复：改为 `private String username`，通过 getter/setter 访问。

**问题 2：`public String password`**
- 缺陷：密码字段绝对不能公开！外部可直接读取明文密码。
- 修复：改为 `private String password`，且 setter 中应对密码加密后存储，不提供 getter。

**问题 3：`String dbUrl = "jdbc:mysql://..."`（默认包私有）**
- 缺陷：数据库连接 URL 是内部配置，虽然包私有比 public 好一些，但仍可能被同包其他类访问。
- 修复：改为 `private static final String DB_URL = "jdbc:mysql://..."`，同时应从配置文件读取而非硬编码。

**问题 4：`public String buildSql(String user)`**
- 缺陷：这是内部实现方法，不应该暴露为 public；同时存在 SQL 注入风险。
- 修复：改为 `private String buildSql(String user)`，并使用 PreparedStatement 替代字符串拼接。

**修复后的关键结构**：
```java
public class UserService {
    private String username;
    private String password;
    private static final String DB_URL = "jdbc:mysql://...";

    private String buildSql(String user) { ... }
    public void login(String user, String pwd) { ... }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
```

---

## Q5（项目应用）：设计字符串工具类

**题目**：设计一个 `StringUtils` 工具类，提供以下三个静态方法，并说明每个方法的访问修饰符选择理由：
1. `isBlank(String s)`：判断字符串是否为 null 或仅含空白字符
2. `truncate(String s, int maxLen)`：截断超长字符串，超出部分用 `"..."` 代替
3. `capitalize(String s)`：将字符串首字母大写

**参考答案**：

```java
public class StringUtils {

    // 私有构造器：工具类不应被实例化
    private StringUtils() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 判断字符串是否为空白（null、空串、仅含空格均视为空白）
     * 访问修饰符选择：public —— 这是对外提供的核心功能，需要项目各处调用
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 截断超长字符串，超出 maxLen 个字符时截断并追加 "..."
     * 访问修饰符选择：public —— 对外功能，调用方需要控制显示长度
     *
     * @param s      原始字符串
     * @param maxLen 最大允许长度（不含 "..."）
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (maxLen <= 0) throw new IllegalArgumentException("maxLen 必须大于 0");
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 首字母大写，其余字母保持不变
     * 访问修饰符选择：public —— 格式化工具，供外部使用
     */
    public static String capitalize(String s) {
        if (isBlank(s)) return s;   // 复用私有逻辑（实际上 isBlank 是 public，这里展示内部复用）
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // 如果有只在工具类内部使用的辅助方法，应声明为 private
    // 例如：
    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
```

**访问修饰符选择理由总结**：

| 元素 | 修饰符 | 理由 |
|------|--------|------|
| 构造器 | `private` | 工具类不允许实例化 |
| `isBlank` | `public static` | 对外功能，全项目复用 |
| `truncate` | `public static` | 对外功能，全项目复用 |
| `capitalize` | `public static` | 对外功能，全项目复用 |
| 内部辅助方法 | `private static` | 实现细节，不对外暴露 |

**设计原则**：工具类对外暴露的方法用 `public static`，内部实现细节用 `private static`，类本身加 `private` 构造器 + 可选 `final` 修饰符防止继承。
