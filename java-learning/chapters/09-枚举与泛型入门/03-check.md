# Chapter 09 - 自测题

## Q1（概念）：以下关于枚举的说法，哪些正确？

**题目**：判断以下每条说法是否正确，并解释原因。

A. `enum` 可以继承另一个 `enum`  
B. `enum` 可以实现接口  
C. `enum` 的构造器可以是 `public`  
D. 枚举比较应该用 `equals()` 而不是 `==`  
E. `values()` 方法每次调用都返回同一个数组对象  

**参考答案**：

- **A：错误**。枚举隐式继承 `java.lang.Enum`，由于 Java 单继承限制，不能再继承其他枚举或类。且枚举类是 `final` 的，也不能被继承。

- **B：正确**。枚举可以实现接口，甚至每个枚举常量可以有不同的接口实现（通过枚举常量体内覆盖方法）。

- **C：错误**。枚举的构造器只能是 `private` 或包私有（默认），不能是 `public` 或 `protected`。因为枚举实例只能在枚举类内部声明，外部不能也不应该调用构造器。

- **D：错误**。枚举比较应该用 `==`，不需要用 `equals()`。每个枚举常量在 JVM 中是唯一实例，`==` 既高效又安全。`equals()` 也能工作，但编译器无法检查类型，且 `==` 是惯例写法。

- **E：错误**。每次调用 `values()` 都会返回一个新的数组副本，以防止外部修改枚举常量列表。这意味着在循环中频繁调用 `values()` 有轻微的性能开销，建议缓存到局部变量或常量中。

---

## Q2（代码阅读）：输出什么？

**题目**：不运行代码，判断输出结果。

```java
public enum Color {
    RED, GREEN, BLUE;

    static {
        System.out.println("Color 枚举类加载");
    }
}

public class EnumTest {
    public static void main(String[] args) {
        System.out.println("main 开始");
        Color c1 = Color.RED;
        Color c2 = Color.valueOf("RED");
        Color c3 = Color.values()[0];

        System.out.println(c1 == c2);
        System.out.println(c2 == c3);
        System.out.println(c1.ordinal());
        System.out.println(Color.GREEN.compareTo(Color.RED));
        System.out.println("main 结束");
    }
}
```

**参考答案**：

```
main 开始
Color 枚举类加载
true
true
0
1
main 结束
```

**逐行解释**：

1. `"main 开始"` — main 方法入口打印
2. `"Color 枚举类加载"` — 第一次访问 `Color.RED` 触发枚举类的类加载，静态块执行
3. `c1 == c2` → `true` — `Color.valueOf("RED")` 返回的就是 `Color.RED` 这个唯一实例
4. `c2 == c3` → `true` — `Color.values()[0]` 也是 `Color.RED` 这个实例（但注意是新数组的第0个元素）
5. `c1.ordinal()` → `0` — RED 是第一个声明的，ordinal 为 0
6. `Color.GREEN.compareTo(Color.RED)` → `1` — compareTo 比较 ordinal 差值：GREEN.ordinal(1) - RED.ordinal(0) = 1
7. `"main 结束"` — main 方法结束

---

## Q3（实操）：泛型方法

**题目**：实现以下三个泛型方法，不能用 `Object` 强转：

1. `first(List<T> list)`：返回列表第一个元素，列表为空时抛 `NoSuchElementException`
2. `contains(T[] arr, T target)`：判断数组是否包含 target（用 `equals` 比较）
3. `zip(List<A> as, List<B> bs)`：将两个等长列表合并为 `List<Pair<A,B>>`，长度不等时抛 `IllegalArgumentException`

**参考答案**：

```java
import java.util.*;

public class GenericMethods {

    /**
     * 返回列表的第一个元素
     */
    public static <T> T first(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new NoSuchElementException("列表为空，无法获取第一个元素");
        }
        return list.get(0);
    }

    /**
     * 判断数组是否包含指定元素
     */
    public static <T> boolean contains(T[] arr, T target) {
        if (arr == null) return false;
        for (T item : arr) {
            if (target == null ? item == null : target.equals(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将两个列表"拉链"合并为 Pair 列表
     */
    public static <A, B> List<Pair<A, B>> zip(List<A> as, List<B> bs) {
        if (as.size() != bs.size()) {
            throw new IllegalArgumentException(
                "两个列表长度不等：" + as.size() + " vs " + bs.size());
        }
        List<Pair<A, B>> result = new ArrayList<>();
        for (int i = 0; i < as.size(); i++) {
            result.add(new Pair<>(as.get(i), bs.get(i)));
        }
        return result;
    }

    public static void main(String[] args) {
        // 测试 first
        List<String> names = Arrays.asList("Alice", "Bob", "Carol");
        System.out.println(first(names));  // Alice

        // 测试 contains
        Integer[] nums = {1, 2, 3, 4, 5};
        System.out.println(contains(nums, 3));   // true
        System.out.println(contains(nums, 10));  // false

        // 测试 zip
        List<String>  keys   = Arrays.asList("a", "b", "c");
        List<Integer> values = Arrays.asList(1, 2, 3);
        List<Pair<String, Integer>> zipped = zip(keys, values);
        zipped.forEach(System.out::println);  // (a, 1)  (b, 2)  (c, 3)
    }
}
```

---

## Q4（概念）：类型擦除导致的编译错误

**题目**：以下哪行代码会导致编译错误？请一一指出并解释原因，然后给出正确的替代写法。

```java
import java.util.*;

public class TypeErasureTest {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("hello");

        // 行A
        if (list instanceof List<String>) { }

        // 行B
        if (list instanceof List) { }

        // 行C
        List<String>[] arr = new ArrayList<String>[10];

        // 行D
        List<?>[] arr2 = new ArrayList<?>[10];
    }

    // 行E
    public static <T> T[] createArray(int size) {
        return new T[size];
    }

    // 行F
    public <T> void test(List<T> list1, List<T> list2) { }
    public <T> void test(List<Integer> list) { }  // 与上面重载
}
```

**参考答案**：

- **行A：编译错误**。`instanceof` 不能检查泛型类型参数，因为运行时类型信息已被擦除，`List<String>` 和 `List<Integer>` 运行时都是 `List`。替代写法：`if (list instanceof List<?>) {}` 或直接 `if (list instanceof List) {}`

- **行B：正确**。不检查泛型参数的 `instanceof` 没有问题，只检查是否是 List 类型。

- **行C：编译错误**。不能创建泛型类型的数组。类型擦除后无法保证数组元素类型安全。替代写法：`List<String>[] arr = new ArrayList[10];`（有编译警告但可用）或改用 `List<List<String>>` 等集合。

- **行D：正确**。通配符 `?` 的数组可以创建（因为 `?` 本身就表示未知类型，不涉及具体泛型检查）。

- **行E：编译错误**。`new T[size]` 非法，T 在运行时已被擦除，无法创建。替代写法：
  ```java
  @SuppressWarnings("unchecked")
  public static <T> T[] createArray(Class<T> type, int size) {
      return (T[]) java.lang.reflect.Array.newInstance(type, size);
  }
  ```

- **行F：编译错误**。`test(List<T>)` 和 `test(List<Integer>)` 在类型擦除后签名相同（都是 `test(List)`），不构成有效重载。替代方案：改为不同的方法名。

---

## Q5（项目应用）：枚举 + 泛型组合设计

**题目**：设计一个通用的 `Result<T>` 类，用于统一表示操作结果（成功或失败）。要求：

1. 包含一个 `Status` 枚举（SUCCESS、FAILURE、PARTIAL）
2. `Result<T>` 类包含：状态、数据（成功时有值）、错误信息（失败时有值）
3. 提供静态工厂方法 `success(T data)`、`failure(String message)`
4. 提供 `isSuccess()`、`getData()`（失败时抛异常）、`getMessage()` 方法

**参考答案**：

```java
/**
 * 操作状态枚举
 */
public enum Status {
    SUCCESS("成功"),
    FAILURE("失败"),
    PARTIAL("部分成功");

    private final String description;

    Status(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}

/**
 * 通用操作结果包装类
 * @param <T> 成功时数据的类型
 */
public class Result<T> {

    private final Status status;
    private final T data;            // 成功时有值，失败时为 null
    private final String message;    // 失败时有值，成功时为 null（或可设为成功消息）

    // 私有构造器：通过工厂方法创建
    private Result(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    // ===== 工厂方法 =====

    public static <T> Result<T> success(T data) {
        return new Result<>(Status.SUCCESS, data, null);
    }

    public static <T> Result<T> failure(String message) {
        return new Result<>(Status.FAILURE, null, message);
    }

    public static <T> Result<T> partial(T data, String message) {
        return new Result<>(Status.PARTIAL, data, message);
    }

    // ===== 查询方法 =====

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public T getData() {
        if (!isSuccess() && status != Status.PARTIAL) {
            throw new IllegalStateException("操作未成功，无法获取数据。错误：" + message);
        }
        return data;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("Result{status=%s, data=%s, message='%s'}",
                status.name(), data, message);
    }
}

// ===== 使用演示 =====

public class ResultDemo {

    public static Result<String> findUser(int id) {
        if (id <= 0) {
            return Result.failure("用户ID必须大于0");
        }
        if (id == 999) {
            return Result.failure("用户不存在，id=" + id);
        }
        return Result.success("User#" + id);
    }

    public static void main(String[] args) {
        // 成功场景
        Result<String> r1 = findUser(1);
        System.out.println(r1.isSuccess());  // true
        System.out.println(r1.getData());    // User#1

        // 失败场景
        Result<String> r2 = findUser(999);
        System.out.println(r2.isSuccess());   // false
        System.out.println(r2.getMessage());  // 用户不存在，id=999
        try {
            r2.getData();  // 抛出 IllegalStateException
        } catch (IllegalStateException e) {
            System.out.println("预期异常：" + e.getMessage());
        }

        // 枚举 switch
        switch (r2.getStatus()) {
            case SUCCESS -> System.out.println("处理成功数据");
            case FAILURE -> System.out.println("记录失败日志：" + r2.getMessage());
            case PARTIAL -> System.out.println("部分成功处理");
        }
    }
}
```
