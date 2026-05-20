# Lambda 与函数式接口 实操 Demo

## Demo 1：用 Lambda 重构排序和过滤逻辑

### 实操目标
演示 Lambda 如何替代匿名内部类，并结合四大函数式接口完成实际数据处理。

### 示例代码

```java
import java.util.*;
import java.util.function.*;

public class LambdaBasicsDemo {

    public static void main(String[] args) {
        // ==================== 对比：匿名内部类 vs Lambda ====================
        List<String> names = new ArrayList<>(Arrays.asList("Charlie", "Alice", "Bob", "David", "Eve"));

        // 传统匿名内部类写法
        names.sort(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareTo(b);
            }
        });
        System.out.println("匿名类排序: " + names);

        // Lambda 写法
        names.sort((a, b) -> a.compareTo(b));
        System.out.println("Lambda排序: " + names);

        // 方法引用写法（最简洁）
        names.sort(String::compareTo);
        System.out.println("方法引用排序: " + names);

        // ==================== Predicate 过滤 ====================
        Predicate<String> longName = s -> s.length() > 3;
        Predicate<String> startsWithA = s -> s.startsWith("A") || s.startsWith("C");
        Predicate<String> combined = longName.and(startsWithA);

        System.out.println("\n名字长度 > 3 且以A或C开头:");
        names.stream()
             .filter(combined)
             .forEach(System.out::println);  // Charlie

        // ==================== Function 转换链 ====================
        Function<String, String> trim = String::trim;
        Function<String, String> toUpper = String::toUpperCase;
        Function<String, Integer> toLength = String::length;

        // 组合管道：trim -> toUpper -> length
        Function<String, Integer> pipeline = trim.andThen(toUpper).andThen(toLength);
        System.out.println("\n' hello world '的处理结果: " + pipeline.apply("  hello world  "));

        // ==================== Consumer 组合 ====================
        Consumer<String> printName = s -> System.out.print("Name: " + s);
        Consumer<String> printLength = s -> System.out.println(" (length: " + s.length() + ")");
        Consumer<String> combined2 = printName.andThen(printLength);

        System.out.println("\n组合Consumer:");
        names.forEach(combined2);

        // ==================== Supplier 懒加载 ====================
        Supplier<List<String>> expensiveList = () -> {
            System.out.println("(正在创建耗时列表...)");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                result.add("Item-" + i);
            }
            return result;
        };

        System.out.println("\n定义了Supplier，但还没有执行");
        System.out.println("现在触发执行:");
        List<String> items = expensiveList.get();
        System.out.println("结果: " + items);
    }
}
```

### 运行结果

```
匿名类排序: [Alice, Bob, Charlie, David, Eve]
Lambda排序: [Alice, Bob, Charlie, David, Eve]
方法引用排序: [Alice, Bob, Charlie, David, Eve]

名字长度 > 3 且以A或C开头:
Alice
Charlie

' hello world '的处理结果: 11

组合Consumer:
Name: Alice (length: 5)
Name: Bob (length: 3)
Name: Charlie (length: 7)
Name: David (length: 5)
Name: Eve (length: 3)

定义了Supplier，但还没有执行
现在触发执行:
(正在创建耗时列表...)
结果: [Item-0, Item-1, Item-2, Item-3, Item-4]
```

### 关键点说明
- `Predicate.and()` 是短路与——第一个为 false 时第二个不执行
- `Function.andThen()` 是正序管道，`compose()` 是反序管道，容易混淆
- `Consumer.andThen()` 两个都会执行（不是短路的）
- `Supplier` 的核心价值是**延迟执行**，只有调用 `get()` 才真正执行

---

## Demo 2：方法引用的四种类型实战

### 实操目标
通过对比 Lambda 写法和方法引用写法，掌握四种方法引用的适用场景。

### 示例代码

```java
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class MethodReferenceDemo {

    // 静态方法（用于演示静态方法引用）
    public static String addPrefix(String s) {
        return "[INFO] " + s;
    }

    // 实例方法（用于演示特定实例方法引用）
    private String name;

    public MethodReferenceDemo(String name) {
        this.name = name;
    }

    public boolean nameStartsWith(String prefix) {
        return name.startsWith(prefix);
    }

    public static void main(String[] args) {
        // ==================== 1. 静态方法引用 ====================
        // Lambda 写法
        Function<String, String> f1 = s -> MethodReferenceDemo.addPrefix(s);
        // 方法引用写法
        Function<String, String> f2 = MethodReferenceDemo::addPrefix;

        System.out.println("静态方法引用:");
        List.of("start", "stop", "pause")
            .stream()
            .map(f2)
            .forEach(System.out::println);

        // ==================== 2. 特定实例的方法引用 ====================
        MethodReferenceDemo demo = new MethodReferenceDemo("Alice");
        // Lambda 写法
        Predicate<String> p1 = prefix -> demo.nameStartsWith(prefix);
        // 方法引用写法（demo 是特定实例）
        Predicate<String> p2 = demo::nameStartsWith;

        System.out.println("\n特定实例方法引用:");
        System.out.println("名字以A开头: " + p2.test("A"));  // true
        System.out.println("名字以B开头: " + p2.test("B"));  // false

        // System.out::println 也是特定实例方法引用（System.out 是具体实例）
        Consumer<String> print = System.out::println;
        print.accept("System.out::println 也是特定实例方法引用");

        // ==================== 3. 任意实例的方法引用 ====================
        // Lambda 写法
        Function<String, String> f3 = s -> s.toUpperCase();
        // 方法引用写法（第一个参数作为 this）
        Function<String, String> f4 = String::toUpperCase;

        // BiFunction：第一个参数作为 this，第二个参数作为入参
        BiFunction<String, String, Boolean> contains = String::contains;
        System.out.println("\n任意实例方法引用:");
        System.out.println("Hello 包含 ell: " + contains.apply("Hello", "ell"));

        // Comparator 场景
        List<String> words = new ArrayList<>(Arrays.asList("banana", "apple", "cherry"));
        words.sort(String::compareToIgnoreCase);  // 等价于 (a, b) -> a.compareToIgnoreCase(b)
        System.out.println("忽略大小写排序: " + words);

        // ==================== 4. 构造器引用 ====================
        // Supplier：无参构造器
        Supplier<ArrayList<String>> listFactory = ArrayList::new;
        ArrayList<String> newList = listFactory.get();

        // Function：有参构造器
        Function<String, StringBuilder> sbFactory = StringBuilder::new;
        StringBuilder sb = sbFactory.apply("initial content");
        System.out.println("\n构造器引用创建 StringBuilder: " + sb);

        // 与 Stream.collect 结合
        List<String> collected = Stream.of("x", "y", "z")
            .collect(Collectors.toCollection(ArrayList::new));
        System.out.println("构造器引用 + collect: " + collected);
    }
}
```

### 运行结果

```
静态方法引用:
[INFO] start
[INFO] stop
[INFO] pause

特定实例方法引用:
名字以A开头: true
名字以B开头: false
System.out::println 也是特定实例方法引用

任意实例方法引用:
Hello 包含 ell: true
忽略大小写排序: [apple, banana, cherry]

构造器引用创建 StringBuilder: initial content
构造器引用 + collect: [x, y, z]
```

### 关键点说明
- 方法引用是语法糖，不改变任何行为，只是写法更简洁
- 最容易混淆的是"任意实例方法引用"：`String::toUpperCase` 等价于 `s -> s.toUpperCase()`，第一个参数是 this
- 构造器引用 `ArrayList::new` 每次调用 `get()` 都创建一个新对象

---

## Demo 3：自定义函数式接口 + 策略模式

### 实操目标
自定义函数式接口，用 Lambda 实现策略模式（替代大量 if-else）。

### 示例代码

```java
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class StrategyPatternDemo {

    // 自定义函数式接口：价格计算策略
    @FunctionalInterface
    interface PricingStrategy {
        double calculate(double originalPrice);

        // 默认方法：在价格上再打折
        default PricingStrategy thenDiscount(double discountRate) {
            return price -> this.calculate(price) * discountRate;
        }
    }

    public static void main(String[] args) {
        // 定义各种定价策略
        PricingStrategy regular    = price -> price;                     // 原价
        PricingStrategy vip        = price -> price * 0.9;               // VIP 9折
        PricingStrategy superVip   = price -> price * 0.8;               // 超级VIP 8折
        PricingStrategy coupon     = price -> price - 20;                // 减20元券
        PricingStrategy flash      = price -> price * 0.5;               // 秒杀半价

        // 组合策略：VIP + 再打8折
        PricingStrategy vipWithExtra = vip.thenDiscount(0.8);

        // 策略注册表（替代 if-else 或 switch）
        Map<String, PricingStrategy> strategies = new HashMap<>();
        strategies.put("REGULAR",   regular);
        strategies.put("VIP",       vip);
        strategies.put("SUPER_VIP", superVip);
        strategies.put("COUPON",    coupon);
        strategies.put("FLASH",     flash);
        strategies.put("VIP_EXTRA", vipWithExtra);

        // 测试各种策略
        double originalPrice = 200.0;
        System.out.printf("原价: %.2f%n%n", originalPrice);

        strategies.forEach((name, strategy) -> {
            double finalPrice = strategy.calculate(originalPrice);
            System.out.printf("%-12s: %.2f 元%n", name, finalPrice);
        });

        // ==================== 通用处理器示例 ====================
        System.out.println("\n--- 处理管道示例 ---");
        // 用 Function 组合实现数据清洗管道
        Function<String, String> normalize =
            ((Function<String, String>) String::trim)
            .andThen(String::toLowerCase)
            .andThen(s -> s.replaceAll("\\s+", "_"));

        String[] rawInputs = {"  Hello World  ", " Java Lambda ", "  Stream API  "};
        for (String input : rawInputs) {
            System.out.printf("'%s' -> '%s'%n", input, normalize.apply(input));
        }
    }
}
```

### 运行结果

```
原价: 200.00

REGULAR     : 200.00 元
VIP         : 180.00 元
SUPER_VIP   : 160.00 元
COUPON      : 180.00 元
FLASH       : 100.00 元
VIP_EXTRA   : 144.00 元

--- 处理管道示例 ---
'  Hello World  ' -> 'hello_world'
' Java Lambda ' -> 'java_lambda'
'  Stream API  ' -> 'stream_api'
```

### 关键点说明
- `@FunctionalInterface` 的 default 方法可以返回 `this` 接口类型，实现**链式组合**
- 策略注册表（Map<String, 函数式接口>）是用 Lambda 消除 if-else 的经典模式
- Spring Boot 中大量使用类似模式（如 HandlerMapping 的策略选择）
