# Lambda 与函数式接口 理论文档

## 核心概念

### 什么是 Lambda 表达式

Lambda 表达式是 Java 8 引入的重要特性，本质上是**匿名函数**——没有名字、可以传递的一段代码。它让 Java 能以更简洁的方式表达"行为"，而不只是"数据"。

**语法结构：**
```
(参数列表) -> { 方法体 }
```

**简化规则：**
- 只有一个参数时，括号可省略
- 方法体只有一行时，花括号和 return 可省略
- 参数类型可由编译器推断，通常省略

```java
// 传统匿名内部类写法
Runnable r1 = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hello");
    }
};

// Lambda 等价写法
Runnable r2 = () -> System.out.println("Hello");

// 带参数
Comparator<String> comp1 = (String a, String b) -> { return a.compareTo(b); };
// 简化版
Comparator<String> comp2 = (a, b) -> a.compareTo(b);
```

### 什么是函数式接口

**函数式接口**是只有一个抽象方法的接口。Lambda 表达式的类型就是函数式接口，编译器通过"目标类型推断"确定 Lambda 对应哪个抽象方法。

`@FunctionalInterface` 注解用于声明函数式接口，编译器会强制检查——如果接口有多个抽象方法，编译报错。

```java
@FunctionalInterface
public interface Greeter {
    String greet(String name);  // 唯一的抽象方法
    
    // default 方法不影响函数式接口的性质
    default void printGreeting(String name) {
        System.out.println(greet(name));
    }
}

// 使用 Lambda 实现
Greeter g = name -> "Hello, " + name + "!";
System.out.println(g.greet("Java"));  // Hello, Java!
```

### 四大内置函数式接口

Java 在 `java.util.function` 包下提供了最常用的通用函数式接口：

#### 1. Supplier<T> —— 供给型（无参有返回）

```java
// 抽象方法：T get()
// 用途：延迟计算、工厂方法、懒加载

Supplier<String> supplier = () -> "Hello World";
System.out.println(supplier.get());  // Hello World

Supplier<List<String>> listSupplier = ArrayList::new;
List<String> list = listSupplier.get();

// 实际场景：Optional.orElseGet() 接收 Supplier
String result = Optional.ofNullable(null)
    .orElseGet(() -> "默认值");
```

#### 2. Consumer<T> —— 消费型（有参无返回）

```java
// 抽象方法：void accept(T t)
// 用途：遍历、打印、保存

Consumer<String> printer = s -> System.out.println(">> " + s);
printer.accept("Java 21");  // >> Java 21

// andThen 组合：先执行当前，再执行参数
Consumer<String> upperAndPrint = 
    ((Consumer<String>) s -> System.out.print(s.toUpperCase()))
    .andThen(s -> System.out.println(" (done)"));
upperAndPrint.accept("hello");  // HELLO (done)

// forEach 接收 Consumer
List.of("A", "B", "C").forEach(s -> System.out.println(s));
```

#### 3. Function<T, R> —— 转换型（有参有返回）

```java
// 抽象方法：R apply(T t)
// 用途：数据转换、映射

Function<String, Integer> toLength = String::length;
System.out.println(toLength.apply("Hello"));  // 5

// andThen：先执行当前，再执行参数（管道）
Function<String, Integer> toLengthTimes2 = toLength.andThen(n -> n * 2);
System.out.println(toLengthTimes2.apply("Hello"));  // 10

// compose：先执行参数，再执行当前（反向管道）
Function<Integer, String> toStr = n -> "len=" + n;
Function<String, String> pipeline = toStr.compose(toLength);
System.out.println(pipeline.apply("Hello"));  // len=5

// BiFunction<T, U, R>：两个入参
BiFunction<String, Integer, String> repeat = (s, n) -> s.repeat(n);
System.out.println(repeat.apply("abc", 3));  // abcabcabc
```

#### 4. Predicate<T> —— 判断型（有参返回 boolean）

```java
// 抽象方法：boolean test(T t)
// 用途：过滤、条件判断

Predicate<String> notEmpty = s -> !s.isEmpty();
System.out.println(notEmpty.test("Hello"));   // true
System.out.println(notEmpty.test(""));        // false

// and / or / negate 组合
Predicate<Integer> isEven = n -> n % 2 == 0;
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> isEvenAndPositive = isEven.and(isPositive);
System.out.println(isEvenAndPositive.test(4));   // true
System.out.println(isEvenAndPositive.test(-2));  // false

// filter 接收 Predicate
List<String> names = List.of("Alice", "Bob", "", "Charlie");
names.stream()
     .filter(notEmpty)
     .forEach(System.out::println);  // Alice Bob Charlie
```

### 方法引用

方法引用是 Lambda 的一种简写形式，当 Lambda 只是直接调用某个现有方法时可以使用。

**四种语法：**

```java
// 1. 静态方法引用：类名::静态方法名
Function<String, Integer> parse = Integer::parseInt;
// 等价于：s -> Integer.parseInt(s)

// 2. 实例方法引用（特定实例）：实例::方法名
String prefix = "Hello";
Predicate<String> startsWith = prefix::startsWith;
// 等价于：s -> prefix.startsWith(s) —— 注意这里 prefix 是调用者

// 3. 实例方法引用（任意实例）：类名::实例方法名
Function<String, String> toUpper = String::toUpperCase;
// 等价于：s -> s.toUpperCase()  —— 第一个参数作为方法调用者

// 4. 构造器引用：类名::new
Supplier<ArrayList<String>> listFactory = ArrayList::new;
// 等价于：() -> new ArrayList<>()

Function<String, StringBuilder> sbFactory = StringBuilder::new;
// 等价于：s -> new StringBuilder(s)
```

**方法引用实战示例：**

```java
List<String> names = Arrays.asList("Charlie", "Alice", "Bob");

// 静态方法引用
names.stream()
     .map(String::toUpperCase)           // 任意实例方法引用
     .sorted(String::compareTo)          // 任意实例方法引用
     .forEach(System.out::println);      // 特定实例方法引用（System.out 是实例）
```

### 接口默认方法（Default Method）

Java 8 允许接口定义带实现的方法（default method），解决了接口演化困境——在不破坏已有实现类的前提下为接口新增功能。

```java
public interface Printable {
    void print(String content);
    
    // 默认方法，实现类可以不重写
    default void printWithBorder(String content) {
        System.out.println("=== " + content + " ===");
        print(content);
    }
    
    // 静态方法（接口静态方法，只能通过接口名调用）
    static Printable consolePrinter() {
        return s -> System.out.println(s);
    }
}

// 多接口继承时，默认方法冲突需要显式解决
interface A {
    default void hello() { System.out.println("A"); }
}
interface B {
    default void hello() { System.out.println("B"); }
}
class C implements A, B {
    @Override
    public void hello() {
        A.super.hello();  // 显式选择 A 的实现
    }
}
```

---

## 使用场景

| 场景 | Lambda 应用 |
|------|-------------|
| 集合遍历 | `list.forEach(item -> ...)` |
| 集合排序 | `list.sort((a, b) -> ...)` |
| Stream 操作 | `.filter(x -> ...).map(x -> ...)` |
| 线程创建 | `new Thread(() -> {...})` |
| 事件监听 | `button.addActionListener(e -> ...)` |
| 延迟计算 | `Supplier<T>` 包装耗时操作 |
| 策略模式 | 用 Lambda 替代 Strategy 实现类 |

---

## 工作原理

### Lambda 的底层实现

Lambda 不是匿名内部类的语法糖，它的底层使用了 **invokedynamic** 字节码指令（JVM 的动态调用机制）：

1. **编译期**：编译器将 Lambda 体编译为当前类中的一个**私有静态方法**（或实例方法）
2. **首次调用**：通过 `invokedynamic` 调用 `LambdaMetafactory.metafactory()`，动态生成一个实现目标函数式接口的类
3. **后续调用**：使用缓存的实现，性能接近直接方法调用

**为什么不用匿名内部类：**
- 匿名内部类每次创建都会生成一个新的 `.class` 文件，加载开销大
- Lambda 通过 `invokedynamic` 延迟绑定，JVM 可以更灵活地优化

### 变量捕获

Lambda 可以捕获外部变量，但被捕获的局部变量必须是 **effectively final**（实际上不可变，不需要显式 final 关键字）：

```java
String prefix = "Hello";
// prefix = "Hi";  // 如果有这行，下面的 Lambda 会编译报错

Function<String, String> greet = name -> prefix + ", " + name;

// 实例变量和静态变量可以自由读写，不受 effectively final 限制
```

---

## 常见坑与易错点

### 坑 1：Lambda 捕获的变量不能被修改

```java
// 错误示例
int count = 0;
list.forEach(item -> {
    count++;  // 编译错误：Variable used in lambda expression should be effectively final
});

// 正确做法 1：用 AtomicInteger
AtomicInteger count = new AtomicInteger(0);
list.forEach(item -> count.incrementAndGet());

// 正确做法 2：用 Stream.count() 或 reduce
long count = list.stream().count();
```

### 坑 2：方法引用类型混淆

```java
// 错误：误用静态方法引用语法来引用实例方法
String s = "Hello";
// Function<String, String> f = s::toUpperCase;  // 这是"特定实例"方法引用，OK
// 但如果想对"任意字符串"调用 toUpperCase：
Function<String, String> f = String::toUpperCase;  // 正确：任意实例方法引用
// 而不是：
// Function<String, String> f2 = s::toUpperCase;  // 只对 s 这一个实例有效

// 下面这个更容易混淆：
// BiFunction 还是 Function？
// String::compareTo 的类型是：
BiFunction<String, String, Integer> bi = String::compareTo;  // 正确
// 因为 compareTo(String other) 有一个参数，加上隐式的 this，共两个入参
```

### 坑 3：接口默认方法的菱形继承问题

```java
interface Flyable {
    default void fly() { System.out.println("Flying"); }
}
interface Swimmable {
    default void fly() { System.out.println("Flying in water?"); }
}

// 错误：不处理冲突会编译失败
// class Duck implements Flyable, Swimmable {}  // 编译错误

// 正确做法：必须重写冲突的方法
class Duck implements Flyable, Swimmable {
    @Override
    public void fly() {
        Flyable.super.fly();  // 选择 Flyable 的实现
    }
}
```

### 坑 4：@FunctionalInterface 注解被忽略

```java
// 没有 @FunctionalInterface 注解时，接口有多个抽象方法不会在接口定义处报错
// 但赋值 Lambda 时报错，错误信息不直观

// 最佳实践：凡是设计为函数式接口的，都加 @FunctionalInterface
@FunctionalInterface
interface MyAction {
    void execute();
    // void cancel();  // 加了这行立刻在接口定义处报错，定位问题更快
}
```

### 坑 5：Consumer 的 andThen 执行顺序与 Function 的 compose 容易搞混

```java
Function<Integer, Integer> times2 = x -> x * 2;
Function<Integer, Integer> plus3  = x -> x + 3;

// andThen：先 times2，再 plus3
// times2.andThen(plus3).apply(5) = (5*2)+3 = 13
System.out.println(times2.andThen(plus3).apply(5));  // 13

// compose：先 plus3，再 times2（顺序相反！）
// times2.compose(plus3).apply(5) = (5+3)*2 = 16
System.out.println(times2.compose(plus3).apply(5));  // 16
```

---

## 面试高频问题

### Q1：Lambda 和匿名内部类有什么区别？

**答：** 有三个核心区别：

1. **this 的指向不同**：匿名内部类中 `this` 指向匿名类实例本身；Lambda 中 `this` 指向外层类的实例。这是最重要的区别。

2. **底层实现不同**：匿名内部类在编译期生成独立的 `.class` 文件；Lambda 使用 `invokedynamic` 指令，在运行时动态生成实现类，不产生额外的 class 文件，加载更轻量。

3. **状态不同**：匿名内部类可以有实例变量；Lambda 本身是无状态的（捕获的外部变量必须 effectively final）。

4. **适用范围不同**：Lambda 只能用于函数式接口（单抽象方法）；匿名内部类可以实现任意接口或抽象类。

### Q2：什么是函数式接口？Java 为什么要引入它？

**答：** 函数式接口是只有一个抽象方法的接口（可以有多个 default/static 方法）。用 `@FunctionalInterface` 注解标注，编译器会进行强制校验。

引入原因：Java 是强类型语言，不能直接传递函数。函数式接口作为 Lambda 的"目标类型"，让 Java 能够以类型安全的方式传递行为。这是实现函数式编程范式的基础，配合 Stream API 使集合操作的代码大幅简化。

### Q3：四大函数式接口分别在哪些场景使用？Stream API 是如何使用它们的？

**答：**
- `Supplier<T>`：无参有返回，用于延迟创建对象、工厂模式。Stream 中 `Stream.generate(Supplier)` 使用它。
- `Consumer<T>`：有参无返回，用于遍历消费。Stream 中 `forEach(Consumer)` 使用它。
- `Function<T,R>`：有参有返回，用于数据转换映射。Stream 中 `map(Function)` 使用它。
- `Predicate<T>`：有参返回 boolean，用于条件过滤。Stream 中 `filter(Predicate)` 使用它。

### Q4：方法引用有哪几种类型？各自的使用条件是什么？

**答：** 四种类型：

1. **静态方法引用** `类::静态方法`：Lambda 参数全部传给静态方法，如 `Integer::parseInt` 等价于 `s -> Integer.parseInt(s)`

2. **特定实例的方法引用** `实例::实例方法`：Lambda 参数传给该特定实例的方法，如 `System.out::println`，每次调用都在 `System.out` 这个对象上执行

3. **任意实例的方法引用** `类::实例方法`：Lambda 的第一个参数作为方法的调用者，如 `String::toUpperCase` 等价于 `s -> s.toUpperCase()`

4. **构造器引用** `类::new`：调用构造器，如 `ArrayList::new` 等价于 `() -> new ArrayList<>()`

使用条件：Lambda 体只是直接调用对应方法，参数顺序和个数匹配，不需要做额外处理时才能使用方法引用。

### Q5：接口 default 方法的设计意图是什么？它打破了哪些设计原则？如何处理多继承冲突？

**答：** 设计意图是**接口演化（Interface Evolution）**：在不破坏已有实现类的前提下，为接口添加新功能。Java 8 在 `Collection` 接口中新增了 `forEach`、`stream` 等默认方法，如果没有 default 机制，所有实现 `Collection` 的类都需要修改，这在 Java 生态中是不现实的。

它一定程度上引入了多继承的复杂性（虽然只是行为继承，不是状态继承）。Java 的解决规则是：
1. **类优先**：类的实现（包括从父类继承来的）优先于接口的 default 方法
2. **子接口优先**：更具体的接口的 default 方法覆盖父接口的 default 方法
3. **显式选择**：两个接口有冲突的 default 方法，实现类必须显式重写，可通过 `InterfaceName.super.method()` 选择特定接口的实现

### Q6：Lambda 能访问哪些外部变量？为什么局部变量必须是 effectively final？

**答：** Lambda 可以访问：实例变量、静态变量（无限制），以及外部作用域的局部变量（必须 effectively final）。

局部变量必须 effectively final 的原因：局部变量存在于栈上，当方法返回后栈帧被销毁，但 Lambda 可能在方法返回后才执行（如放入线程池异步执行）。JVM 通过复制局部变量值到 Lambda 中来解决这个问题，但如果变量可变，复制的值就可能与原始值不一致，导致数据不一致问题，所以强制要求 effectively final 来避免这种歧义。
