# Chapter 19 注解与反射 - 理论篇

## 一、注解（Annotation）

### 1.1 注解的本质

注解是 Java 5 引入的特性，**本质上是一种特殊的接口**，用 `@interface` 声明，编译器将其编译为实现了 `java.lang.annotation.Annotation` 的接口。

注解本身不包含任何业务逻辑，它的作用是**为代码元素（类、方法、字段等）附加元数据（Metadata）**，供编译器、框架或运行时工具读取并做相应处理。

```
注解 → 元数据标记
框架（反射读取注解）→ 根据注解执行特定逻辑
```

### 1.2 内置注解

| 注解 | 作用 |
|------|------|
| `@Override` | 标记方法重写父类，编译器检查方法签名是否匹配 |
| `@Deprecated` | 标记已过时的 API，使用时编译器发出警告 |
| `@SuppressWarnings` | 抑制特定编译器警告（如 `"unchecked"`、`"rawtypes"`）|
| `@FunctionalInterface` | 标记函数式接口，编译器检查只有一个抽象方法 |

```java
public class InnerAnnotations {

    @Deprecated(since = "2.0", forRemoval = true)
    public void oldMethod() { ... }

    @Override
    public String toString() { return "Example"; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void rawTypeMethod() {
        List list = new ArrayList(); // 使用原始类型不报警告
    }
}
```

### 1.3 元注解（注解的注解）

元注解用于修饰自定义注解的注解：

| 元注解 | 说明 |
|--------|------|
| `@Retention` | 指定注解的生命周期 |
| `@Target` | 指定注解可以用在哪些代码元素上 |
| `@Documented` | 注解信息包含在 JavaDoc 中 |
| `@Inherited` | 子类可继承父类的注解（仅对类级别注解有效） |

**@Retention 的三个值：**

| 值 | 说明 | 典型场景 |
|----|------|---------|
| `RetentionPolicy.SOURCE` | 仅保留在源码，编译后丢弃 | `@Override`、`@SuppressWarnings` |
| `RetentionPolicy.CLASS` | 保留到 class 文件，JVM 加载时丢弃（默认） | 字节码工具使用 |
| `RetentionPolicy.RUNTIME` | 保留到运行时，可通过反射读取 | Spring 框架注解、自定义校验注解 |

**@Target 常用值：**

```java
@Target({
    ElementType.TYPE,            // 类、接口、枚举
    ElementType.FIELD,           // 字段
    ElementType.METHOD,          // 方法
    ElementType.PARAMETER,       // 方法参数
    ElementType.CONSTRUCTOR,     // 构造器
    ElementType.LOCAL_VARIABLE,  // 局部变量
    ElementType.ANNOTATION_TYPE  // 注解类型（即元注解）
})
```

### 1.4 自定义注解

```java
import java.lang.annotation.*;

// 定义注解
@Retention(RetentionPolicy.RUNTIME)   // 运行时可读
@Target(ElementType.FIELD)            // 用于字段
@Documented
public @interface Validate {
    int minLength() default 0;         // 属性（方法形式），可有默认值
    int maxLength() default Integer.MAX_VALUE;
    boolean required() default true;
    String message() default "校验失败";  // 自定义错误消息
}
```

**使用自定义注解：**
```java
public class User {
    @Validate(required = true, minLength = 2, maxLength = 20)
    private String username;

    @Validate(required = false, maxLength = 100)
    private String email;

    private Integer age; // 无注解，不校验
}
```

**注解属性规则：**
- 属性类型只能是：基本类型、String、Class、枚举、注解、以上类型的数组
- 有且只有一个属性且名为 `value` 时，可省略属性名：`@Anno("val")` 等价于 `@Anno(value = "val")`
- 没有属性的注解称为**标记注解**（Marker Annotation），如 `@Override`

---

## 二、反射（Reflection）

### 2.1 反射的用途

反射允许程序在**运行时**检查和操作类的结构（字段、方法、构造器），而不需要在编译期知道具体类型。

主要用途：
- **框架开发**：Spring IOC、MyBatis ORM、JUnit 测试框架
- **序列化/反序列化**：JSON 库（Jackson、Gson）读取对象字段
- **依赖注入**：根据注解自动注入依赖
- **动态代理**：AOP 切面实现

### 2.2 获取 Class 对象的三种方式

```java
// 方式1：通过类名（编译时就知道类型）
Class<String> clazz1 = String.class;

// 方式2：通过对象实例（运行时获取）
String str = "hello";
Class<?> clazz2 = str.getClass();

// 方式3：通过全限定类名字符串（最灵活，框架常用）
Class<?> clazz3 = Class.forName("java.lang.String");

// 三种方式得到的是同一个 Class 对象（类加载器保证唯一）
System.out.println(clazz1 == clazz2); // true
System.out.println(clazz2 == clazz3); // true
```

### 2.3 反射核心 API

**获取字段：**
```java
Class<?> clazz = User.class;

// getFields()         → 获取所有 public 字段（含父类）
// getDeclaredFields() → 获取本类声明的所有字段（含 private）
Field[] fields = clazz.getDeclaredFields();
for (Field field : fields) {
    System.out.println(field.getName() + " : " + field.getType().getSimpleName());
}

// 获取指定字段并读取/修改值
Field nameField = clazz.getDeclaredField("username");
nameField.setAccessible(true);         // 关键！访问 private 字段必须调用
User user = new User("Alice", "alice@example.com");
String name = (String) nameField.get(user);  // 读取
nameField.set(user, "Bob");                  // 修改
```

**获取方法：**
```java
// getMethods()          → 获取所有 public 方法（含父类和接口）
// getDeclaredMethods()  → 获取本类声明的所有方法（含 private）
Method[] methods = clazz.getDeclaredMethods();

// 获取指定方法（方法名 + 参数类型）
Method setName = clazz.getDeclaredMethod("setUsername", String.class);
setName.setAccessible(true);
setName.invoke(user, "Charlie");    // 调用方法，等价于 user.setUsername("Charlie")
```

**获取构造器并创建对象：**
```java
// 方式1：无参构造（类必须有 public 无参构造器）
User newUser = clazz.getDeclaredConstructor().newInstance();

// 方式2：有参构造
Constructor<?> constructor = clazz.getDeclaredConstructor(String.class, String.class);
User userWithArgs = (User) constructor.newInstance("Dave", "dave@example.com");
```

**读取注解：**
```java
// 读取字段上的注解
Field field = clazz.getDeclaredField("username");
if (field.isAnnotationPresent(Validate.class)) {
    Validate validate = field.getAnnotation(Validate.class);
    System.out.println("minLength: " + validate.minLength());
    System.out.println("required: " + validate.required());
}

// 读取类上的注解
if (clazz.isAnnotationPresent(Service.class)) {
    Service service = clazz.getAnnotation(Service.class);
}
```

### 2.4 Spring 用反射实现 IOC 的原理

Spring 框架大量使用反射，以 `@Autowired` 注入为例：

```
1. Spring 启动时，扫描 classpath 下所有 @Component/@Service 等注解标记的类
2. 用 Class.forName 加载类，getDeclaredConstructor().newInstance() 创建对象（Bean）
3. 对每个 Bean 的字段，检查是否有 @Autowired 注解
4. 若有，根据字段类型从容器中找到对应 Bean
5. 用 field.setAccessible(true) + field.set(bean, dependency) 注入依赖
```

简化版模拟：
```java
// 伪代码：Spring IOC 注入的核心逻辑
for (Field field : bean.getClass().getDeclaredFields()) {
    if (field.isAnnotationPresent(Autowired.class)) {
        Object dependency = getBean(field.getType());  // 从容器找
        field.setAccessible(true);
        field.set(bean, dependency);                    // 反射注入
    }
}
```

### 2.5 反射的性能影响与适用场景

**性能开销来源：**
1. 方法/字段查找（按名字搜索，比直接调用慢）
2. 类型检查和权限检查（`setAccessible` 有安全成本）
3. JIT 优化难度更高（反射调用难以内联）

**适用场景：**
- 框架/库开发（有性能预算，灵活性更重要）
- 工具类（序列化、对象映射等）
- 测试代码（访问私有方法进行单元测试）
- 插件化架构（运行时加载未知类）

**不适用场景：**
- 高性能热点代码（每秒百万次调用的业务逻辑）
- 简单的业务代码（直接调用即可，无需反射）

---

## 三、常见坑

**坑1：忘记调用 `setAccessible(true)` 访问私有成员**
```java
Field nameField = User.class.getDeclaredField("username");
// 错误：直接访问 private 字段会抛 IllegalAccessException
nameField.get(user); // 抛异常！

// 正确：先设置可访问
nameField.setAccessible(true);
nameField.get(user); // OK
```

**坑2：`@Retention` 不设 RUNTIME 导致运行时读不到注解**
```java
// 错误：默认 RetentionPolicy.CLASS，运行时注解已丢失
@Target(ElementType.FIELD)
public @interface MyAnno { ... }  // 未指定 Retention

// 运行时读取返回 false：
field.isAnnotationPresent(MyAnno.class); // false！注解已被丢弃

// 正确：必须显式指定 RUNTIME
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MyAnno { ... }
```

**坑3：反射创建对象时没有无参构造器**
```java
// 错误场景：User 只有有参构造器
public class User {
    public User(String name) { ... }
    // 没有无参构造器！
}

// 反射调用无参构造会抛 NoSuchMethodException
User user = User.class.getDeclaredConstructor().newInstance(); // 异常！

// 正确：指定正确的参数类型
Constructor<User> c = User.class.getDeclaredConstructor(String.class);
User user = c.newInstance("Alice");
```

---

## 四、面试高频问题

**Q1：反射是什么，有什么用？**

A：反射是 Java 的一种机制，允许程序在运行时检查和操作类的结构（字段、方法、构造器、注解等），而无需在编译期知道具体类型。主要用途包括：框架开发（Spring IOC/AOP）、序列化工具（Jackson/Gson）、ORM 映射（MyBatis）、测试框架（JUnit）等。核心 API 包括 `Class`、`Field`、`Method`、`Constructor`，关键操作是 `setAccessible(true)`。

**Q2：Spring 是如何用反射实现 IOC 的？**

A：Spring IOC 的核心流程：① 扫描 classpath，找到带 `@Component`/`@Service` 等注解的类；② 通过反射（`Class.forName` + `newInstance`）创建 Bean 对象放入容器；③ 对 Bean 的字段/方法扫描 `@Autowired`，找到依赖的类型；④ 从容器中取出对应 Bean，用 `field.setAccessible(true) + field.set(bean, dep)` 完成依赖注入。整个过程依赖注解（`@Autowired`）+ 反射（读取注解、创建对象、设置字段）。

**Q3：`@Retention` 三个值的区别？**

A：`SOURCE` - 只保留在源码中，编译后被丢弃，用于编译器检查（如 `@Override`）；`CLASS` - 保留到 .class 文件，JVM 加载时丢弃，这是默认值，用于字节码工具处理；`RUNTIME` - 保留到运行时，可通过反射读取，大多数框架注解（如 Spring 的 `@Component`、自定义校验注解）都用这个级别。

**Q4：`getDeclaredField` 和 `getField` 的区别？**

A：`getField(name)` 只能获取 `public` 字段，且会递归查找父类；`getDeclaredField(name)` 可以获取本类声明的任意访问级别（`private`/`protected`/`public`/包级）的字段，但不包含父类。要访问 `private` 字段，需要使用 `getDeclaredField` + `setAccessible(true)`。类似地，方法也分 `getMethod`/`getDeclaredMethod`，构造器分 `getConstructor`/`getDeclaredConstructor`。

**Q5：反射会有性能问题吗？如何优化？**

A：有，主要体现在：权限检查、类型检查和 JIT 难以优化内联。优化手段：① 缓存 `Field`/`Method` 对象，避免重复 `getDeclaredField` 查找（这是最重要的优化）；② 调用一次 `setAccessible(true)` 后缓存复用；③ 热点路径考虑用 `MethodHandles`（Java 7+）替代反射，速度接近直接调用；④ 高频场景考虑代码生成（如 Lombok 在编译期生成代码，完全避免运行时反射）。
