# Chapter 19 注解与反射 - Demo 示例

## Demo 1：自定义注解 @Validate + 反射实现字段校验

**场景**：仿照 Bean Validation（JSR-303）的思路，用自定义注解标记校验规则，用反射读取注解并执行校验。

```java
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// ===========================
// Step 1：定义 @Validate 注解
// ===========================
@Retention(RetentionPolicy.RUNTIME)   // 必须！否则运行时读不到
@Target(ElementType.FIELD)            // 作用于字段
@Documented
@interface Validate {
    int minLength() default 0;
    int maxLength() default Integer.MAX_VALUE;
    boolean required() default true;
    String message() default "";       // 自定义错误消息，空则自动生成
}

// ===========================
// Step 2：被校验的数据模型
// ===========================
class UserForm {
    @Validate(required = true, minLength = 2, maxLength = 20, message = "用户名长度必须在2-20之间")
    private String username;

    @Validate(required = true, minLength = 6, maxLength = 50)
    private String password;

    @Validate(required = false, maxLength = 100)  // 可以为空，但有值时不能超过100
    private String email;

    private Integer age;  // 无注解，不校验

    public UserForm(String username, String password, String email, Integer age) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.age = age;
    }

    @Override
    public String toString() {
        return "UserForm{username=" + username + ", password=***, email=" + email + "}";
    }
}

// ===========================
// Step 3：校验器（反射读取注解）
// ===========================
class Validator {

    /**
     * 校验对象，返回所有错误信息列表
     */
    public static List<String> validate(Object obj) {
        List<String> errors = new ArrayList<>();
        Class<?> clazz = obj.getClass();

        // 遍历所有声明的字段（含 private）
        for (Field field : clazz.getDeclaredFields()) {
            // 检查字段是否有 @Validate 注解
            if (!field.isAnnotationPresent(Validate.class)) {
                continue;  // 无注解，跳过
            }

            field.setAccessible(true);  // 允许访问 private 字段

            Validate validate = field.getAnnotation(Validate.class);
            String fieldName = field.getName();
            Object value = null;

            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                errors.add(fieldName + ": 无法读取字段值");
                continue;
            }

            // 校验 required
            if (validate.required() && (value == null || value.toString().isBlank())) {
                String msg = validate.message().isEmpty()
                    ? fieldName + " 不能为空"
                    : validate.message();
                errors.add(msg);
                continue;  // required 校验失败，后续长度校验无意义
            }

            // 校验长度（仅对 String 有效）
            if (value instanceof String str && !str.isBlank()) {
                int len = str.length();
                if (len < validate.minLength()) {
                    String msg = validate.message().isEmpty()
                        ? fieldName + " 长度不能小于 " + validate.minLength()
                        : validate.message();
                    errors.add(msg);
                }
                if (len > validate.maxLength()) {
                    String msg = validate.message().isEmpty()
                        ? fieldName + " 长度不能超过 " + validate.maxLength()
                        : validate.message();
                    errors.add(msg);
                }
            }
        }

        return errors;
    }
}

// ===========================
// Step 4：测试
// ===========================
public class ValidateDemo {
    public static void main(String[] args) {
        System.out.println("=== 测试1：合法用户 ===");
        UserForm validUser = new UserForm("Alice", "pass123456", "alice@example.com", 25);
        List<String> errors1 = Validator.validate(validUser);
        if (errors1.isEmpty()) {
            System.out.println("校验通过！");
        } else {
            errors1.forEach(e -> System.out.println("错误：" + e));
        }

        System.out.println("\n=== 测试2：用户名太短，密码为空 ===");
        UserForm invalidUser = new UserForm("A", "", null, 25);
        List<String> errors2 = Validator.validate(invalidUser);
        errors2.forEach(e -> System.out.println("错误：" + e));

        System.out.println("\n=== 测试3：邮箱为空（允许），密码太短 ===");
        UserForm user3 = new UserForm("Bob", "12345", null, 30);
        List<String> errors3 = Validator.validate(user3);
        errors3.forEach(e -> System.out.println("错误：" + e));
    }
}
```

**运行输出：**
```
=== 测试1：合法用户 ===
校验通过！

=== 测试2：用户名太短，密码为空 ===
错误：用户名长度必须在2-20之间
错误：password 不能为空

=== 测试3：邮箱为空（允许），密码太短 ===
错误：password 长度不能小于 6
```

---

## Demo 2：用反射实现对象转 Map 工具

**场景**：序列化框架的核心功能之一——将任意对象的字段提取为 `Map<String, Object>`，供 JSON 序列化、日志输出等使用。

```java
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ObjectToMapDemo {

    // ===========================
    // 工具方法：对象 -> Map
    // ===========================
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) return new HashMap<>();

        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = obj.getClass();

        // getDeclaredFields()：获取本类所有字段（含 private，不含父类）
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);  // 关键：允许访问 private
            try {
                String fieldName = field.getName();
                Object fieldValue = field.get(obj);
                map.put(fieldName, fieldValue);
            } catch (IllegalAccessException e) {
                // 实际框架中会记录日志
                System.err.println("无法读取字段：" + field.getName());
            }
        }

        return map;
    }

    // ===========================
    // 扩展：支持父类字段
    // ===========================
    public static Map<String, Object> toMapWithSuper(Object obj) {
        if (obj == null) return new HashMap<>();

        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = obj.getClass();

        // 遍历当前类及所有父类（直到 Object）
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (map.containsKey(field.getName())) continue; // 子类优先，跳过父类同名字段
                field.setAccessible(true);
                try {
                    map.put(field.getName(), field.get(obj));
                } catch (IllegalAccessException ignored) {}
            }
            clazz = clazz.getSuperclass();
        }

        return map;
    }

    // ===========================
    // 测试数据类
    // ===========================
    static class Product {
        private Long id;
        private String name;
        private double price;
        private boolean available;

        public Product(Long id, String name, double price, boolean available) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.available = available;
        }
    }

    static class BaseEntity {
        private Long createTime = System.currentTimeMillis();
        private String createdBy = "system";
    }

    static class Order extends BaseEntity {
        private Long orderId;
        private String status;

        public Order(Long orderId, String status) {
            this.orderId = orderId;
            this.status = status;
        }
    }

    // ===========================
    // 测试
    // ===========================
    public static void main(String[] args) {
        System.out.println("=== Product 转 Map ===");
        Product p = new Product(1L, "iPhone 15", 5999.0, true);
        Map<String, Object> productMap = toMap(p);
        productMap.forEach((k, v) -> System.out.printf("  %-12s = %s%n", k, v));

        System.out.println("\n=== Order 转 Map（含父类字段）===");
        Order order = new Order(101L, "PAID");
        Map<String, Object> orderMap = toMapWithSuper(order);
        orderMap.forEach((k, v) -> System.out.printf("  %-12s = %s%n", k, v));

        System.out.println("\n=== 验证：读取 private 字段 ===");
        // 如果不 setAccessible(true)，访问 private 字段会抛 IllegalAccessException
        try {
            Field nameField = Product.class.getDeclaredField("name");
            // 不调用 setAccessible
            // nameField.get(p);  // 会抛 IllegalAccessException
            nameField.setAccessible(true);
            System.out.println("name（反射读取）= " + nameField.get(p));
        } catch (Exception e) {
            System.out.println("错误：" + e.getMessage());
        }
    }
}
```

**运行输出：**
```
=== Product 转 Map ===
  id           = 1
  name         = iPhone 15
  price        = 5999.0
  available    = true

=== Order 转 Map（含父类字段）===
  orderId      = 101
  status       = PAID
  createTime   = 1715846400000
  createdBy    = system

=== 验证：读取 private 字段 ===
name（反射读取）= iPhone 15
```

---

## Demo 3：用反射调用私有方法（模拟框架调用场景）

**场景**：某些框架（如测试框架、序列化框架）需要调用对象的私有方法，例如初始化回调、`@PostConstruct`。

```java
import java.lang.annotation.*;
import java.lang.reflect.Method;

// 自定义注解：标记初始化方法（模拟 @PostConstruct）
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface PostInit {
    String description() default "初始化方法";
}

// 业务类：有私有初始化方法
class DatabaseService {
    private String url;
    private boolean connected = false;

    public DatabaseService(String url) {
        this.url = url;
        // 注意：真实场景下 @PostConstruct 由框架调用，这里模拟
    }

    // 私有初始化方法（模拟框架通过反射调用）
    @PostInit(description = "建立数据库连接")
    private void init() {
        System.out.println("正在连接数据库：" + url);
        this.connected = true;
        System.out.println("数据库连接成功！");
    }

    // 私有校验方法
    @PostInit(description = "校验配置")
    private void validateConfig() {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("数据库 URL 不能为空");
        }
        System.out.println("配置校验通过：" + url);
    }

    public boolean isConnected() { return connected; }
    public String getUrl() { return url; }
}

// 简单的框架初始化器：扫描并调用 @PostInit 方法
class FrameworkInitializer {

    public static void initialize(Object obj) {
        Class<?> clazz = obj.getClass();
        System.out.println("初始化 Bean：" + clazz.getSimpleName());

        // 遍历所有方法，找 @PostInit
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostInit.class)) {
                PostInit anno = method.getAnnotation(PostInit.class);
                System.out.println("发现初始化方法：" + method.getName()
                    + "（" + anno.description() + "）");

                method.setAccessible(true);  // 允许调用 private 方法
                try {
                    method.invoke(obj);       // 调用方法，obj 是实例，无参方法传空
                } catch (Exception e) {
                    // 反射调用异常包装在 InvocationTargetException 中
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.err.println("初始化失败：" + cause.getMessage());
                }
            }
        }
        System.out.println("Bean 初始化完成！");
    }
}

// 测试
public class PrivateMethodDemo {
    public static void main(String[] args) {
        System.out.println("=== 场景1：正常初始化 ===");
        DatabaseService service = new DatabaseService("jdbc:mysql://localhost:3306/mydb");
        FrameworkInitializer.initialize(service);
        System.out.println("连接状态：" + service.isConnected());

        System.out.println("\n=== 场景2：配置错误的初始化 ===");
        DatabaseService badService = new DatabaseService("");
        FrameworkInitializer.initialize(badService);

        System.out.println("\n=== 场景3：直接用反射调用指定私有方法 ===");
        try {
            DatabaseService svc = new DatabaseService("jdbc:postgresql://localhost/test");
            Method initMethod = DatabaseService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(svc);
            System.out.println("直接调用结果 - 连接状态：" + svc.isConnected());
        } catch (Exception e) {
            System.out.println("调用失败：" + e.getMessage());
        }
    }
}
```

**运行输出：**
```
=== 场景1：正常初始化 ===
初始化 Bean：DatabaseService
发现初始化方法：validateConfig（校验配置）
配置校验通过：jdbc:mysql://localhost:3306/mydb
发现初始化方法：init（建立数据库连接）
正在连接数据库：jdbc:mysql://localhost:3306/mydb
数据库连接成功！
Bean 初始化完成！
连接状态：true

=== 场景2：配置错误的初始化 ===
初始化 Bean：DatabaseService
发现初始化方法：validateConfig（校验配置）
初始化失败：数据库 URL 不能为空
...

=== 场景3：直接用反射调用指定私有方法 ===
正在连接数据库：jdbc:postgresql://localhost/test
数据库连接成功！
直接调用结果 - 连接状态：true
```

**关键要点：**
- `method.invoke(obj, args...)` - 实例方法传对象实例；`static` 方法传 `null`
- 反射调用时若方法内抛异常，会被包装为 `InvocationTargetException`，需用 `getCause()` 获取原始异常
- `getDeclaredMethods()` 不保证顺序，如果初始化方法有顺序依赖，需要额外处理（如增加 `order` 属性）
