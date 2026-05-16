# Chapter 19 注解与反射 - 自测题

## Q1（概念）：`@Retention` 的三个值分别在什么阶段保留注解？各有什么典型用途？

**参考答案：**

| 值 | 保留阶段 | 运行时能否反射读取 | 典型注解 |
|----|---------|----------------|---------|
| `SOURCE` | 源码（.java 文件）| 否 | `@Override`、`@SuppressWarnings`、Lombok 的 `@Data` |
| `CLASS` | 字节码（.class 文件，默认值） | 否 | 字节码增强工具（ASM/Byte Buddy）使用 |
| `RUNTIME` | 运行时（JVM 内存中）| 是 | Spring `@Component`、自定义校验注解 |

```java
// SOURCE 示例：@Override 只在编译期有意义，编译后丢弃
@Retention(RetentionPolicy.SOURCE)
public @interface Override {}

// RUNTIME 示例：Spring 注解需要运行时读取
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
    String value() default "";
}

// 验证：忘记设置 RUNTIME 时的后果
@Target(ElementType.FIELD)
// 没有 @Retention，默认 CLASS
public @interface MyField {}

class Test {
    @MyField
    private String name;
}

Field field = Test.class.getDeclaredField("name");
field.isAnnotationPresent(MyField.class); // false！CLASS 级别运行时读不到
```

**结论**：自定义注解如果需要在框架/工具中通过反射处理，必须显式声明 `@Retention(RetentionPolicy.RUNTIME)`，否则注解在运行时不可见。

---

## Q2（概念）：`getDeclaredField` vs `getField`、`getDeclaredMethod` vs `getMethod` 的区别？

**参考答案：**

| 方法 | 访问级别 | 查找范围 | 用途 |
|------|---------|---------|------|
| `getDeclaredField(name)` | 任意（含 private） | 仅本类 | 反射框架访问私有字段 |
| `getField(name)` | 仅 public | 本类 + 父类 + 接口 | 访问 public API 字段 |
| `getDeclaredMethod(name, paramTypes)` | 任意（含 private） | 仅本类 | 框架调用私有方法 |
| `getMethod(name, paramTypes)` | 仅 public | 本类 + 父类 + 接口 | 访问 public 方法 |

```java
class Base {
    public String publicField = "base-public";
    private String privateField = "base-private";
}

class Child extends Base {
    private String childPrivate = "child-private";
}

Child child = new Child();

// getDeclaredField：只找 Child 自己的，找不到父类字段
Child.class.getDeclaredField("childPrivate");  // OK
Child.class.getDeclaredField("publicField");   // NoSuchFieldException！在父类，不在 Child

// getField：找所有 public，包含父类
Child.class.getField("publicField");           // OK，找到父类的
Child.class.getField("childPrivate");          // NoSuchFieldException！不是 public

// 要访问父类的 private：需要 getSuperclass()
Base.class.getDeclaredField("privateField");   // OK
```

**实践技巧**：框架中遍历对象所有字段（含父类）的正确做法：
```java
List<Field> allFields = new ArrayList<>();
Class<?> clazz = obj.getClass();
while (clazz != null && clazz != Object.class) {
    allFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
    clazz = clazz.getSuperclass();
}
```

---

## Q3（实操）：找出以下反射代码的问题并改正

**有问题的代码：**
```java
// 代码1
@Target(ElementType.FIELD)
public @interface MyAnno { String value(); }  // 缺少 @Retention

class MyClass {
    @MyAnno("test")
    private String field;
}
// 运行时：
Field f = MyClass.class.getDeclaredField("field");
boolean hasAnno = f.isAnnotationPresent(MyAnno.class); // 期望 true

// 代码2
class Service {
    private void secretMethod(String param) {
        System.out.println("secret: " + param);
    }
}
Service svc = new Service();
Method m = Service.class.getDeclaredMethod("secretMethod", String.class);
m.invoke(svc, "hello");  // 缺少关键步骤

// 代码3
class Config {
    private static String instance;
}
Field f = Config.class.getDeclaredField("instance");
f.setAccessible(true);
String val = (String) f.get(null);  // 期望读取静态字段，但好像有问题？
```

**参考答案：**

**问题1：缺少 `@Retention(RetentionPolicy.RUNTIME)`，运行时读不到注解**
```java
// 改正：
@Retention(RetentionPolicy.RUNTIME)   // 添加这行！
@Target(ElementType.FIELD)
public @interface MyAnno { String value(); }

// 改正后：hasAnno 为 true
```

**问题2：调用私有方法前缺少 `setAccessible(true)`**
```java
// 改正：
Method m = Service.class.getDeclaredMethod("secretMethod", String.class);
m.setAccessible(true);   // 必须添加！否则抛 IllegalAccessException
m.invoke(svc, "hello");  // 现在正常调用
```

**问题3：代码3其实是正确的！** `f.get(null)` 读取静态字段时传 `null` 是正确写法（静态字段不属于任何实例）：
```java
Field f = Config.class.getDeclaredField("instance");
f.setAccessible(true);
String val = (String) f.get(null);   // 正确！静态字段传 null

// 错误理解：有人以为要传类实例，其实不用
// 类比：静态方法 invoke 也传 null：
// method.invoke(null, args);  // 调用静态方法
```

---

## Q4（实操）：用反射实现一个通用的对象打印方法

要求：接收任意对象，输出格式为 `ClassName{field1=value1, field2=value2, ...}`，类似 `toString()`。

**参考答案：**

```java
import java.lang.reflect.Field;
import java.util.StringJoiner;

public class ReflectiveToString {

    public static String toString(Object obj) {
        if (obj == null) return "null";

        Class<?> clazz = obj.getClass();
        StringJoiner joiner = new StringJoiner(", ",
            clazz.getSimpleName() + "{", "}");

        // 遍历本类和所有父类的字段
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    // 密码类字段脱敏
                    String displayValue;
                    if (field.getName().toLowerCase().contains("password")
                            || field.getName().toLowerCase().contains("secret")) {
                        displayValue = "***";
                    } else {
                        displayValue = String.valueOf(value);
                    }
                    joiner.add(field.getName() + "=" + displayValue);
                } catch (IllegalAccessException ignored) {}
            }
            current = current.getSuperclass();
        }

        return joiner.toString();
    }

    // 测试
    static class User {
        private Long id;
        private String username;
        private String password;

        public User(Long id, String username, String password) {
            this.id = id;
            this.username = username;
            this.password = password;
        }
    }

    public static void main(String[] args) {
        User user = new User(1L, "Alice", "secret123");
        System.out.println(toString(user));
        // 输出：User{id=1, username=Alice, password=***}

        System.out.println(toString(null));
        // 输出：null
    }
}
```

**关键点：**
- 用 `StringJoiner` 优雅处理分隔符
- 敏感字段（password/secret）自动脱敏
- 递归处理父类字段

---

## Q5（综合）：模拟 Spring @Component 扫描注册

**题目**：给定以下代码，用反射模拟 Spring 的 Bean 注册逻辑：扫描指定类列表，找到带 `@Component` 注解的类，创建实例并注册到 "容器"（`Map<String, Object>`）中。

```java
// 已有注解和类：
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Component {
    String value() default "";  // Bean 名称，默认用类名首字母小写
}

@Component("userService")
class UserService {
    public void save() { System.out.println("UserService.save()"); }
}

@Component
class OrderService {
    public void process() { System.out.println("OrderService.process()"); }
}

class HelperUtil {  // 无 @Component，不注册
    public void help() { }
}
```

**参考答案：**

```java
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class SimpleContainer {

    private Map<String, Object> beans = new HashMap<>();

    /**
     * 注册：扫描类列表，找 @Component 创建实例
     */
    public void scan(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(Component.class)) {
                continue;  // 无 @Component，跳过
            }

            Component anno = clazz.getAnnotation(Component.class);

            // 确定 Bean 名称：注解指定 > 类名首字母小写
            String beanName = anno.value().isEmpty()
                ? Character.toLowerCase(clazz.getSimpleName().charAt(0))
                    + clazz.getSimpleName().substring(1)
                : anno.value();

            // 用反射创建实例（要求有无参构造器）
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance();
                beans.put(beanName, instance);
                System.out.println("注册 Bean：" + beanName + " -> " + clazz.getName());
            } catch (Exception e) {
                System.err.println("创建 Bean 失败：" + clazz.getName() + " - " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        return (T) beans.get(name);
    }

    public static void main(String[] args) {
        SimpleContainer container = new SimpleContainer();
        container.scan(UserService.class, OrderService.class, HelperUtil.class);

        System.out.println("\n=== 从容器获取 Bean 并调用 ===");
        UserService userSvc = container.getBean("userService");
        if (userSvc != null) userSvc.save();

        OrderService orderSvc = container.getBean("orderService");  // 默认名：类名首字母小写
        if (orderSvc != null) orderSvc.process();

        Object helper = container.getBean("helperUtil");
        System.out.println("helperUtil = " + helper);  // null，未注册
    }
}
```

**运行输出：**
```
注册 Bean：userService -> UserService
注册 Bean：orderService -> OrderService
（HelperUtil 无 @Component，跳过）

=== 从容器获取 Bean 并调用 ===
UserService.save()
OrderService.process()
helperUtil = null
```

**这正是 Spring IOC 容器的简化版核心逻辑！** 真实 Spring 还会处理：依赖注入（`@Autowired`）、作用域（单例/原型）、循环依赖、AOP 代理等。
