# Chapter 19 注解与反射 - 项目任务

## 业务背景

现代框架（Spring、MyBatis、Jackson）的核心能力之一是对象序列化：将 Java 对象转为 JSON、XML 等格式。本任务要求你从零实现一个简单的 JSON 序列化工具，通过注解控制序列化行为，通过反射读取对象字段，模拟 Jackson 的核心能力。

---

## 任务要求

### 任务1：定义 @JsonField 注解

```java
// 功能：指定序列化时的字段名（不指定则用字段名）
// 示例：@JsonField(name = "user_name") → JSON 中使用 "user_name" 作为 key
// 支持 ignore = true：序列化时忽略该字段（如密码字段）
```

需要实现的注解：
```java
// 补全注解定义
public @interface JsonField {
    String name() default "";       // 自定义 JSON key 名，空则用字段名
    boolean ignore() default false; // true 则序列化时跳过该字段
}
```

### 任务2：实现 SimpleJsonSerializer

```java
public class SimpleJsonSerializer {

    /**
     * 将对象序列化为 JSON 字符串（简单格式，不处理嵌套对象）
     * 支持的字段类型：String（加引号）、数字、boolean、null
     *
     * 示例输出：{"user_name":"Alice","age":25,"email":"alice@example.com"}
     */
    public static String toJson(Object obj) {
        // TODO：
        // 1. 处理 null → "null"
        // 2. 遍历 obj 所有 getDeclaredFields()
        // 3. 检查 @JsonField 注解：
        //    - ignore=true → 跳过
        //    - name 非空 → 使用注解指定名称
        //    - 否则 → 使用字段名
        // 4. 读取字段值（setAccessible(true)）
        // 5. 根据值类型决定是否加引号（String 加引号，数字和 boolean 不加）
        // 6. 拼接为 JSON 字符串
        return "{}"; // 替换此行
    }
}
```

### 任务3：测试用 User 类

```java
public class User {
    @JsonField(name = "user_name")
    private String username;

    private Integer age;

    @JsonField(name = "email_address")
    private String email;

    @JsonField(ignore = true)   // 密码不序列化
    private String password;

    private boolean active;

    // 无注解字段，直接用字段名
    private Double score;

    public User(String username, Integer age, String email,
                String password, boolean active, Double score) {
        this.username = username;
        this.age = age;
        this.email = email;
        this.password = password;
        this.active = active;
        this.score = score;
    }
}
```

期望的序列化结果（顺序可能不同）：
```json
{"user_name":"Alice","age":25,"email_address":"alice@example.com","active":true,"score":98.5}
```
注意：`password` 被 `ignore=true` 排除，不出现在 JSON 中。

---

## 完整骨架代码

```java
import java.lang.annotation.*;
import java.lang.reflect.Field;

// ============================================================
// 1. 注解定义
// ============================================================
@Retention(RetentionPolicy.RUNTIME)  // TODO：确认保留级别是否正确
@Target(ElementType.FIELD)
public @interface JsonField {
    String name() default "";
    boolean ignore() default false;
}

// ============================================================
// 2. 序列化器
// ============================================================
public class SimpleJsonSerializer {

    public static String toJson(Object obj) {
        if (obj == null) return "null";

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Field field : obj.getClass().getDeclaredFields()) {
            // TODO 1：检查是否有 @JsonField 且 ignore=true，若是则跳过
            // TODO 2：确定 JSON key（注解 name 非空用注解名，否则用字段名）
            // TODO 3：field.setAccessible(true)
            // TODO 4：field.get(obj) 读取值
            // TODO 5：根据值类型决定格式（String 用引号，数字/boolean/null 不加）
            // TODO 6：追加到 sb，注意逗号分隔（first 控制）

            // 伪代码提示：
            // if (!first) sb.append(",");
            // sb.append("\"").append(key).append("\":").append(formatValue(value));
            // first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    // 辅助方法：根据值类型格式化
    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeString((String) value) + "\"";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) return value.toString();
        // 其他类型：转字符串并加引号（简化处理，不处理嵌套对象）
        return "\"" + value + "\"";
    }

    // 辅助方法：转义 JSON 字符串中的特殊字符
    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

// ============================================================
// 3. 测试
// ============================================================
public class SerializerTest {
    public static void main(String[] args) {
        // 测试1：正常对象
        User user = new User("Alice", 25, "alice@example.com", "secret123", true, 98.5);
        String json = SimpleJsonSerializer.toJson(user);
        System.out.println("序列化结果：");
        System.out.println(json);
        System.out.println();

        // 验证 password 字段不出现
        System.out.println("是否包含 password：" + json.contains("password")); // false
        System.out.println("是否包含 user_name：" + json.contains("user_name")); // true
        System.out.println("是否包含 email_address：" + json.contains("email_address")); // true

        // 测试2：null 对象
        System.out.println("\nnull 对象：" + SimpleJsonSerializer.toJson(null));

        // 测试3：含特殊字符的字符串
        User userWithSpecial = new User("Bob\"s", 30, "bob@example.com", "pwd", false, 0.0);
        System.out.println("\n含引号的 JSON：" + SimpleJsonSerializer.toJson(userWithSpecial));
    }
}
```

---

## 参考答案

<details>
<summary>点击展开参考答案（建议先自行实现）</summary>

```java
// @JsonField 完整定义
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface JsonField {
    String name() default "";
    boolean ignore() default false;
}

// SimpleJsonSerializer.toJson 完整实现
public static String toJson(Object obj) {
    if (obj == null) return "null";

    StringBuilder sb = new StringBuilder("{");
    boolean first = true;

    for (Field field : obj.getClass().getDeclaredFields()) {
        // 检查 @JsonField
        if (field.isAnnotationPresent(JsonField.class)) {
            JsonField anno = field.getAnnotation(JsonField.class);
            if (anno.ignore()) continue;  // ignore=true，跳过
        }

        // 确定 key 名
        String key;
        if (field.isAnnotationPresent(JsonField.class)) {
            JsonField anno = field.getAnnotation(JsonField.class);
            key = anno.name().isEmpty() ? field.getName() : anno.name();
        } else {
            key = field.getName();
        }

        // 读取字段值
        field.setAccessible(true);
        Object value;
        try {
            value = field.get(obj);
        } catch (IllegalAccessException e) {
            continue;
        }

        // 拼接
        if (!first) sb.append(",");
        sb.append("\"").append(key).append("\":").append(formatValue(value));
        first = false;
    }

    sb.append("}");
    return sb.toString();
}
```

</details>

---

## 验收标准

1. **注解定义正确**：`@JsonField` 有 `@Retention(RetentionPolicy.RUNTIME)` 和 `@Target(ElementType.FIELD)`，两个属性均有默认值。
2. **ignore 功能**：`ignore=true` 的字段（如 `password`）不出现在 JSON 输出中。
3. **name 重命名功能**：`@JsonField(name="user_name")` 的字段在 JSON 中使用 `"user_name"` 作为 key，而非字段名 `username`。
4. **类型处理正确**：String 值加双引号并转义特殊字符；数字和 boolean 不加引号；null 输出字面量 `null`。

---

## 常见坑

**坑1：忘记给 `@JsonField` 添加 `@Retention(RetentionPolicy.RUNTIME)`**
```java
// 错误：不加 @Retention 则默认 CLASS，运行时 isAnnotationPresent 返回 false
@Target(ElementType.FIELD)
public @interface JsonField { ... }  // 缺少 @Retention!

// 后果：ignore 和 name 都不生效，password 也会出现在 JSON 中
// 改正：必须加 @Retention(RetentionPolicy.RUNTIME)
```

**坑2：访问 private 字段前忘记 `setAccessible(true)`**
```java
// 错误：
Object value = field.get(obj);  // IllegalAccessException！

// 改正：
field.setAccessible(true);      // 先设置可访问
Object value = field.get(obj);  // OK
```

**坑3：String 类型值中含有双引号或换行符时未转义，导致 JSON 格式错误**
```java
// 错误：直接拼接，不转义
String name = "Bob\"s";
sb.append("\"").append(name).append("\"");
// 结果："Bob"s"  → 非法 JSON！

// 改正：使用 escapeString 方法转义特殊字符
sb.append("\"").append(escapeString(name)).append("\"");
// 结果："Bob\"s"  → 合法 JSON
```
