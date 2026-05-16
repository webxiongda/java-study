# Lambda 与函数式接口 自测题

> 先独立作答，再看参考答案。建议用纸笔写出答案后再对照。

## 题目

### Q1（概念）：下面代码能编译通过吗？如果不能，原因是什么，怎么修复？

```java
@FunctionalInterface
interface Transformer {
    String transform(String input);
    String transform(Integer input);  // 多了这行
}
```

---

### Q2（概念）：Lambda 中的 `this` 关键字指向哪里？和匿名内部类中的 `this` 有什么区别？请用代码举例说明。

---

### Q3（实操）：给定以下代码，用方法引用改写 Lambda（能改的全部改写），写出改写后的版本：

```java
List<String> words = Arrays.asList("hello", "world", "java");

words.stream()
     .filter(s -> s.length() > 4)
     .map(s -> s.toUpperCase())
     .sorted((a, b) -> a.compareTo(b))
     .forEach(s -> System.out.println(s));
```

---

### Q4（实操）：实现一个 `compose` 方法，接受两个 `Function<Integer, Integer>` 参数，返回一个新的 `Function<Integer, Integer>`，新函数的效果是：先执行第二个参数，再把结果传给第一个参数。

调用示例：
```java
Function<Integer, Integer> times2 = x -> x * 2;
Function<Integer, Integer> plus10 = x -> x + 10;
Function<Integer, Integer> result = myCompose(times2, plus10);
System.out.println(result.apply(5));  // 应该输出 30，因为先 5+10=15，再 15*2=30
```

---

### Q5（项目应用）：Spring Boot 的 `@RestController` 中经常需要对请求参数做校验和转换。用函数式接口设计一个通用的参数处理链，要求：
- 支持 trim 处理
- 支持非空校验（空时抛出 `IllegalArgumentException`）
- 支持自定义转换逻辑
- 支持链式调用

写出接口设计和一个简单的使用示例。

---

## 参考答案

### A1：

不能编译通过。原因：`@FunctionalInterface` 注解要求接口**只有一个抽象方法**，但 `Transformer` 接口有两个抽象方法（`transform(String)` 和 `transform(Integer)`），违反了函数式接口的定义，编译器会报错：

```
Multiple non-overriding abstract methods found in interface Transformer
```

修复方式：删除其中一个抽象方法，只保留一个。如果业务上确实需要两种 transform，可以用泛型：

```java
@FunctionalInterface
interface Transformer<T> {
    String transform(T input);
}

Transformer<String> strTransformer = String::toUpperCase;
Transformer<Integer> intTransformer = i -> "Number: " + i;
```

---

### A2：

Lambda 中的 `this` 指向**外层类的实例**（词法作用域中的 this），而匿名内部类中的 `this` 指向**匿名类自身的实例**。

```java
public class ThisDemo {
    private String name = "OuterClass";

    public void test() {
        // 匿名内部类
        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                // 这里 this 是匿名类的实例，不能访问外部类的 name
                System.out.println(this.getClass().getSimpleName()); // 类似 "ThisDemo$1"
                // 要访问外部类需要用：ThisDemo.this.name
                System.out.println(ThisDemo.this.name); // OuterClass
            }
        };

        // Lambda
        Runnable r2 = () -> {
            // 这里 this 就是 ThisDemo 的实例
            System.out.println(this.name); // OuterClass
            System.out.println(this.getClass().getSimpleName()); // ThisDemo
        };

        r1.run();
        r2.run();
    }
}
```

实际开发中这个区别在事件监听器里很重要——Lambda 版本中 `this` 可以直接访问外层类的方法和字段，不需要 `OuterClass.this` 前缀。

---

### A3：

```java
words.stream()
     .filter(s -> s.length() > 4)       // 无法改写（包含运算，不是单纯方法调用）
     .map(String::toUpperCase)           // 任意实例方法引用
     .sorted(String::compareTo)          // 任意实例方法引用（Comparator 版）
     .forEach(System.out::println);      // 特定实例方法引用
```

`s.length() > 4` 无法改写为方法引用，因为它包含了算术比较，不是简单的方法调用。

---

### A4：

```java
import java.util.function.Function;

public class ComposeDemo {

    // 手动实现 compose：先执行 g，再执行 f
    public static Function<Integer, Integer> myCompose(
            Function<Integer, Integer> f,
            Function<Integer, Integer> g) {
        return x -> f.apply(g.apply(x));
    }

    public static void main(String[] args) {
        Function<Integer, Integer> times2 = x -> x * 2;
        Function<Integer, Integer> plus10 = x -> x + 10;
        Function<Integer, Integer> result = myCompose(times2, plus10);
        System.out.println(result.apply(5));  // 30

        // 其实 Function 接口内置了 compose 方法，行为相同：
        Function<Integer, Integer> result2 = times2.compose(plus10);
        System.out.println(result2.apply(5));  // 30
    }
}
```

注意：`Function.compose(g)` 等价于 `x -> this.apply(g.apply(x))`，即先执行参数 g，再执行 this。这与 `andThen` 正好相反。

---

### A5：

```java
import java.util.function.Function;
import java.util.function.UnaryOperator;

// 设计一个参数处理链接口
@FunctionalInterface
interface ParamProcessor {
    String process(String input);

    // 链式组合：先执行当前处理器，再执行 next
    default ParamProcessor then(ParamProcessor next) {
        return input -> next.process(this.process(input));
    }

    // 内置常用处理器（静态工厂方法）
    static ParamProcessor trim() {
        return String::trim;
    }

    static ParamProcessor requireNonEmpty(String fieldName) {
        return input -> {
            if (input == null || input.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " 不能为空");
            }
            return input;
        };
    }

    static ParamProcessor transform(UnaryOperator<String> transformer) {
        return transformer::apply;
    }
}

// 使用示例
public class ParamProcessorDemo {
    public static void main(String[] args) {
        // 组合处理链：trim -> 非空校验 -> 转大写
        ParamProcessor usernameProcessor = ParamProcessor.trim()
            .then(ParamProcessor.requireNonEmpty("username"))
            .then(ParamProcessor.transform(String::toUpperCase));

        System.out.println(usernameProcessor.process("  alice  "));  // ALICE
        usernameProcessor.process("  ");  // 抛出 IllegalArgumentException: username 不能为空
    }
}
```

这个设计在 Spring Boot 项目中可以注入为 Bean，在 Controller 中复用，避免大量重复的参数校验代码。
