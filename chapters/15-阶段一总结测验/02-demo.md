# Chapter 15 - 阶段一总结：代码阅读题

> 以下 3 道代码阅读题，覆盖 OOP、异常、集合三大主题。先独立分析，再看解析。

---

## 代码题 1（OOP + 多态）：这段代码输出什么？

```java
class Animal {
    String name = "Animal";

    public void speak() {
        System.out.println("Animal speaks");
    }

    public String getName() {
        return name;
    }
}

class Dog extends Animal {
    String name = "Dog";  // 注意：字段隐藏，不是重写

    @Override
    public void speak() {
        System.out.println("Dog barks");
    }

    @Override
    public String getName() {
        return name;
    }
}

public class PolymorphismTest {
    public static void main(String[] args) {
        Animal a = new Dog();

        System.out.println(a.name);        // (1)
        System.out.println(a.getName());   // (2)
        a.speak();                         // (3)

        Dog d = (Dog) a;
        System.out.println(d.name);        // (4)
        System.out.println(d.getName());   // (5)
    }
}
```

**问题：** 每行输出什么？请先独立分析。

---

### 详细解析

**输出结果：**
```
Animal    ← (1)
Dog       ← (2)
Dog barks ← (3)
Dog       ← (4)
Dog       ← (5)
```

**逐行解析：**

**(1) `a.name` → `"Animal"`**

`a` 的声明类型是 `Animal`。Java 中**字段不支持多态（不存在"重写字段"）**，字段的访问在**编译期**就由声明类型决定。虽然 `a` 实际指向 `Dog` 对象，但通过 `Animal` 类型的引用访问 `name`，得到的是 `Animal` 的 `name` 字段。

这叫做**字段隐藏（Field Hiding）**：子类定义了同名字段，并未覆盖父类字段，两者在对象中同时存在。

**(2) `a.getName()` → `"Dog"`**

`getName()` 是方法，方法支持多态（动态绑定）。运行时 JVM 查看 `a` 实际指向的对象类型（`Dog`），调用 `Dog.getName()`，返回 `Dog` 的 `name` 字段，即 `"Dog"`。

**(3) `a.speak()` → `"Dog barks"`**

同上，方法动态绑定，调用 `Dog.speak()`。

**(4) `d.name` → `"Dog"`**

`d` 的声明类型是 `Dog`，访问 `name` 字段由 `Dog` 的声明类型决定，得到 `Dog.name = "Dog"`。

**(5) `d.getName()` → `"Dog"`**

`Dog.getName()` 返回 `Dog` 的 `name`，即 `"Dog"`。

**核心结论：**
- 方法调用：**运行时**决定（动态绑定，支持多态）
- 字段访问：**编译时**决定（由声明类型决定，不支持多态）
- 实际开发中字段都用 private，通过方法访问，因此这个问题不常出现但面试很爱考。

---

## 代码题 2（异常处理）：这段代码有什么问题？输出什么？

```java
public class ExceptionTest {

    public static int getValue() {
        try {
            System.out.println("try block");
            return 1;
        } catch (Exception e) {
            System.out.println("catch block");
            return 2;
        } finally {
            System.out.println("finally block");
            return 3;  // 注意这里
        }
    }

    public static String process(String input) {
        try {
            if (input == null) {
                throw new IllegalArgumentException("input 不能为 null");
            }
            return input.toUpperCase();
        } catch (NullPointerException e) {
            return "NPE caught";
        }
    }

    public static void main(String[] args) {
        // 场景1
        int result = getValue();
        System.out.println("result = " + result);

        // 场景2
        System.out.println(process(null));

        // 场景3
        try {
            int[] arr = new int[3];
            arr[5] = 10;
        } catch (Exception e) {
            System.out.println("caught: " + e.getClass().getSimpleName());
        } finally {
            System.out.println("cleanup done");
        }
    }
}
```

**问题：** 指出代码中的问题，并写出完整输出。

---

### 详细解析

**完整输出：**
```
try block
finally block
result = 3
IllegalArgumentException 未被捕获，程序抛出异常（见分析）
```

等等——第二个场景让程序崩溃了吗？先逐个分析：

**场景1：`getValue()` 的输出与返回值**

```
try block      ← try 块执行 println
finally block  ← finally 在 return 前执行
result = 3     ← finally 中的 return 3 覆盖了 try 中的 return 1
```

**问题所在：** `finally` 中有 `return 3`，这会**覆盖** `try` 块中的 `return 1`，最终返回 `3`。

这是一个非常严重的代码问题：**永远不要在 finally 块中使用 return**。原因：
- 它会吞掉 try 块中的返回值
- 更危险的是：它会**吞掉异常**（若 try 块抛了异常，finally 的 return 会让异常消失，方法正常返回，掩盖了错误）

**场景2：`process(null)` 的问题**

```java
if (input == null) {
    throw new IllegalArgumentException("input 不能为 null");
}
```

抛出的是 `IllegalArgumentException`（继承自 `RuntimeException`），但 catch 只捕获了 `NullPointerException`。`IllegalArgumentException` 不是 `NullPointerException`，catch 捕获失败，异常继续向上传播，最终导致程序崩溃。

正确的捕获方式（三选一）：
```java
catch (IllegalArgumentException e) { ... }  // 捕获正确的类型
catch (RuntimeException e) { ... }          // 捕获父类
catch (Exception e) { ... }                 // 捕获所有受检和非受检
```

**场景3：数组越界异常**

```
caught: ArrayIndexOutOfBoundsException  ← 被 catch (Exception e) 捕获
cleanup done                            ← finally 执行
```

`arr[5] = 10`：数组长度 3，访问下标 5，抛出 `ArrayIndexOutOfBoundsException`（继承自 RuntimeException，RuntimeException 继承自 Exception），被 `catch (Exception e)` 成功捕获。

**本题的实际完整输出：**
```
try block
finally block
result = 3
Exception in thread "main" java.lang.IllegalArgumentException: input 不能为 null
    at ExceptionTest.process(ExceptionTest.java:...)
    at ExceptionTest.main(ExceptionTest.java:...)
```

（场景2 抛出未被捕获的异常，程序终止，场景3 不会执行）

---

## 代码题 3（集合）：这段代码的输出是什么？分析存在的问题。

```java
import java.util.*;

public class CollectionTest {

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }

        @Override
        public String toString() { return "(" + x + "," + y + ")"; }
        // 注意：没有重写 equals 和 hashCode
    }

    public static void main(String[] args) {
        // 场景1：List 遍历删除
        List<String> list = new ArrayList<>(
            Arrays.asList("apple", "banana", "cherry", "date")
        );

        for (String s : list) {
            if (s.startsWith("b")) {
                list.remove(s);  // 问题？
            }
        }
        System.out.println("场景1结果：" + list);

        // 场景2：Point 放入 HashSet
        Set<Point> set = new HashSet<>();
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);

        set.add(p1);
        set.add(p2);
        System.out.println("场景2 set大小：" + set.size());
        System.out.println("p1.equals(p2)：" + p1.equals(p2));

        // 场景3：Collections 工具方法
        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));
        Collections.sort(nums);
        System.out.println("排序后：" + nums);

        int idx = Collections.binarySearch(nums, 5);
        System.out.println("5 的索引：" + idx);

        Collections.reverse(nums);
        System.out.println("反转后：" + nums);

        System.out.println("最大值：" + Collections.max(nums));
        System.out.println("最小值：" + Collections.min(nums));
    }
}
```

**问题：** 场景1会抛出什么异常？场景2的输出和你预期的一样吗？场景3的完整输出是什么？

---

### 详细解析

**场景1：在 for-each 中删除元素**

```java
for (String s : list) {
    if (s.startsWith("b")) {
        list.remove(s);  // ← 抛出 ConcurrentModificationException！
    }
}
```

for-each 编译后等价于 Iterator 遍历，`list.remove()` 修改了集合的 `modCount`，而 Iterator 在 `next()` 时检测到 `modCount` 不一致，抛出 `ConcurrentModificationException`。

**正确做法：**
```java
// 方式1：用 Iterator
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().startsWith("b")) {
        it.remove();  // 使用 Iterator 的 remove，安全
    }
}

// 方式2：Java 8+，removeIf
list.removeIf(s -> s.startsWith("b"));

// 场景1正确结果：[apple, cherry, date]
```

**场景2：Point 放入 HashSet**

```
场景2 set大小：2
p1.equals(p2)：false
```

`Point` 没有重写 `equals` 和 `hashCode`，继承 Object 的实现：
- `hashCode()`：基于内存地址，p1 和 p2 不同
- `equals()`：比较内存地址（等价于 `==`），p1 != p2

因此 HashSet 认为这是两个不同的对象，`set.size() = 2`，而不是期望的 1。

**修复：** 重写 equals 和 hashCode（基于 x 和 y 字段）。

**场景3：Collections 工具方法（正确输出）**

```
排序后：[1, 1, 2, 3, 4, 5, 6, 9]
5 的索引：5
反转后：[9, 6, 5, 4, 3, 2, 1, 1]
最大值：9
最小值：1
```

注意：`Collections.binarySearch` 要求**列表已排序**（本题已 sort），返回的是元素的索引。若有重复元素，返回哪个索引不确定，但一定是其中一个。

**场景3 不存在问题，输出正确。**

**本题综合结论：**
1. for-each 中不能直接删除集合元素，用 `Iterator.remove()` 或 `removeIf()`
2. 放入 HashSet / 作为 HashMap key 的类，必须正确实现 equals 和 hashCode
3. `binarySearch` 使用前必须保证列表已排序
