# 方法与调试 理论文档

## 核心概念

### 方法的定义与结构

方法（Method）是封装一段特定逻辑、可被复用的代码块。Java 中所有方法都必须定义在类中。

```
[访问修饰符] [static] 返回类型 方法名([参数列表]) {
    // 方法体
    [return 表达式;]
}
```

```java
// 完整示例
public class MathUtils {

    // 无参数、无返回值
    public static void printSeparator() {
        System.out.println("================");
    }

    // 有参数、有返回值
    public static int add(int a, int b) {
        return a + b;
    }

    // 多参数、返回 double
    public static double average(int... nums) {  // 可变参数
        if (nums.length == 0) return 0;
        int sum = 0;
        for (int n : nums) sum += n;
        return (double) sum / nums.length;
    }
}
```

**各部分说明**：
- `public`：访问修饰符，`public` 任何地方可访问，`private` 只在本类可用，`protected` 同包或子类
- `static`：静态方法，可以通过类名直接调用，不需要创建对象
- 返回类型：方法返回值的类型，`void` 表示不返回任何值
- 方法名：驼峰命名，动词开头，如 `calculateTax`、`getUserById`
- 参数列表：调用时需要传入的数据，可以为空

---

### 参数传递：值传递

Java 中方法的参数传递**永远是值传递**，这是一个非常重要的概念。

**基本类型：传递值的副本**

```java
public static void doubleIt(int n) {
    n = n * 2;  // 只修改了副本，不影响原变量
}

int x = 10;
doubleIt(x);
System.out.println(x);  // 还是 10，x 没有变
```

**引用类型：传递引用的副本（不是对象本身）**

```java
public static void modifyArray(int[] arr) {
    arr[0] = 999;  // 通过引用副本修改了堆中的对象，原数组受影响
}

public static void replaceArray(int[] arr) {
    arr = new int[]{1, 2, 3};  // 只是改变了局部引用，原引用不受影响
}

int[] nums = {10, 20, 30};
modifyArray(nums);
System.out.println(nums[0]);   // 999（通过引用修改了内容）

replaceArray(nums);
System.out.println(nums[0]);   // 999（原引用没变，仍然指向原数组）
```

**理解要点**：传递引用类型时，传的是"引用的拷贝"（地址值的拷贝），不是对象本身。所以通过这个拷贝修改对象内部的属性/元素会生效，但让这个拷贝指向新对象不会影响原引用。

---

### 方法重载（Overloading）

同一个类中，方法名相同但参数列表不同（参数个数、类型、顺序不同），就构成方法重载。

```java
public class PrintHelper {

    public static void print(int value) {
        System.out.println("int: " + value);
    }

    public static void print(double value) {
        System.out.println("double: " + value);
    }

    public static void print(String value) {
        System.out.println("String: " + value);
    }

    public static void print(int a, int b) {
        System.out.println("两个int: " + a + ", " + b);
    }
}
```

**注意**：方法重载与返回值类型无关。只有返回值不同而方法名和参数完全相同的两个方法，不构成重载，编译器会报错。

---

### 递归

递归指方法调用自身。递归必须有：
1. **终止条件（Base Case）**：防止无限递归
2. **递归关系**：问题规模不断缩小，向终止条件靠近

```java
// 计算阶乘
public static long factorial(int n) {
    if (n <= 1) return 1;       // 终止条件
    return n * factorial(n - 1); // 递归调用，n 不断减小
}

// 斐波那契数列（简单递归，注意性能问题）
public static int fib(int n) {
    if (n <= 1) return n;       // fib(0)=0, fib(1)=1
    return fib(n - 1) + fib(n - 2);
}
```

**递归的执行栈**：每次调用 `factorial(5)` 都会压入一个新的栈帧，调用链为：
```
factorial(5) -> factorial(4) -> factorial(3) -> factorial(2) -> factorial(1) 返回1
                                                               ← 2*1=2
                                               ← 3*2=6
                               ← 4*6=24
               ← 5*24=120
```

---

### 方法的返回值

- `void` 方法可以用不带值的 `return` 提前结束
- 有返回值的方法，每条代码路径都必须有 `return`

```java
public static String classify(int score) {
    if (score >= 90) return "A";
    if (score >= 60) return "B";
    return "F";  // 必须有默认的 return，否则编译报错
}
```

---

### 可变参数（Varargs）

```java
public static int sum(int... numbers) {  // 可接受 0 个或多个 int 参数
    int total = 0;
    for (int n : numbers) total += n;
    return total;
}

// 调用方式
sum();           // 0 个参数，OK
sum(1, 2, 3);    // 3 个参数，OK
sum(new int[]{1, 2, 3, 4});  // 也可以传数组
```

可变参数必须是方法的最后一个参数，且每个方法只能有一个可变参数。

---

## 使用场景

- **方法抽取**：当某段逻辑出现超过 2 次，或一个方法超过 20 行，就应该考虑抽取
- **方法重载**：`System.out.println()` 就是重载的典型，支持传入 int、String、double 等各种类型
- **递归**：树/图的遍历、分治算法、文件夹递归遍历，但要注意栈溢出风险
- **值传递理解**：在 Spring Bean 的方法中传入对象时，修改对象属性会生效，但重新 new 一个对象赋给参数不会影响外部

---

## 工作原理

### 方法调用与栈帧

每次调用一个方法，JVM 都会在**栈（Stack）**上创建一个新的**栈帧（Stack Frame）**，栈帧中存储：
- 局部变量表（参数 + 方法内声明的变量）
- 操作数栈（计算中间值）
- 返回地址（方法执行完后回到哪里）

方法执行完毕，栈帧出栈，局部变量消失。

**StackOverflowError**：递归太深（或死递归），栈帧不断入栈，超出 JVM 栈的容量限制，就会抛出此错误。

```java
// 死递归，必然 StackOverflowError
public static void infinite() {
    infinite();  // 没有终止条件
}
```

---

## 常见坑与易错点

### 坑 1：以为 Java 是引用传递，修改参数对象后以为原对象一定改变

```java
public static void changeString(String s) {
    s = s + " World";  // String 是不可变对象！这里只是让 s 指向了新字符串
}

String str = "Hello";
changeString(str);
System.out.println(str);  // 还是 "Hello"，没变！
```

String 不可变 + 值传递双重作用，导致 `changeString` 对原变量毫无影响。

### 坑 2：递归缺少终止条件或条件写错

```java
// 错误：n == 0 应该是 n <= 1 或 n == 1
public static int factorial(int n) {
    if (n == 0) return 1;
    return n * factorial(n);  // 错误：应该是 factorial(n-1)，这里 n 没有减小！
}
// 调用 factorial(5) 会死循环 -> StackOverflowError
```

### 坑 3：方法重载时类型自动提升导致歧义

```java
public static void test(int a, double b) { System.out.println("int, double"); }
public static void test(double a, int b) { System.out.println("double, int"); }

test(1, 2);  // 编译报错：模糊！两个方法都能匹配（int 可提升为 double）
```

### 坑 4：void 方法中 return 的位置

```java
public static void printPositive(int n) {
    if (n <= 0) {
        System.out.println("非正数");
        return;  // 提前结束，后面的代码不执行
    }
    System.out.println("正数：" + n);
    // 不需要写 return，void 方法末尾自动 return
}
```

---

## 面试高频问题

### Q1：Java 是值传递还是引用传递？

**参考答案**：Java 只有值传递。对于基本类型，传递的是值的拷贝，方法内修改不影响原变量。对于引用类型，传递的是引用（内存地址）的拷贝，所以方法内通过该引用修改对象的内部状态会反映到原对象，但如果方法内将参数重新指向另一个新对象，对原引用没有影响。"传递引用的拷贝"不等于"引用传递"——引用传递意味着方法内能改变外部变量所指向的对象（如 C++ 的 `&`），Java 不支持这个。

### Q2：什么是方法重载？它与方法重写（Override）有什么区别？

**参考答案**：方法重载（Overloading）发生在同一个类中，方法名相同但参数列表不同（数量、类型、顺序），与返回值无关，是编译期多态（静态绑定）。方法重写（Overriding）发生在子类和父类之间，子类提供父类方法的不同实现，方法名、参数列表、返回类型都必须相同（返回类型可以是协变类型），是运行期多态（动态绑定，通过 `@Override` 注解标注）。简记：重载是"同一类，不同参"；重写是"子父类，同签名，不同体"。

### Q3：什么情况下会出现 StackOverflowError？

**参考答案**：`StackOverflowError` 发生在方法调用链过深时，导致 JVM 栈空间耗尽。最常见的原因是递归没有终止条件（死递归），或终止条件写错导致递归永远不结束，每次调用都会压入新的栈帧，最终溢出。另外，即使有终止条件但递归深度极大（如对百万级数据用递归），也可能溢出。解决方案：检查递归终止条件、将深递归改为迭代（循环）、或增大 JVM 栈空间（`-Xss` 参数）。

### Q4：可变参数（varargs）有什么使用限制？

**参考答案**：可变参数有以下限制：①必须是方法的最后一个参数；②每个方法只能有一个可变参数；③可变参数本质上是一个数组，调用时可以传 0 个或多个参数，也可以直接传数组。需要注意重载时的歧义：`void m(int... a)` 和 `void m(int a, int... b)` 在调用 `m(1)` 时两个都匹配，编译器会选择更具体的（后者），但复杂情况可能导致歧义编译错误。实际项目中，`String.format()` 和日志框架的 `log.info("msg {}", args...)` 都用了可变参数。

### Q5：如何优化一个存在重复计算的递归（以斐波那契为例）？

**参考答案**：朴素递归 `fib(n) = fib(n-1) + fib(n-2)` 的时间复杂度是 O(2^n)，因为大量子问题被重复计算（如计算 `fib(5)` 时，`fib(3)` 被计算了 2 次，`fib(2)` 被计算了 3 次）。优化方案：①**记忆化（Memoization）**：用 `HashMap` 或数组缓存已计算的结果，时间降至 O(n)；②**动态规划（迭代）**：用循环从底向上计算，只维护前两个值，时间 O(n)，空间 O(1)；③**矩阵快速幂**：时间降至 O(log n)，用于极大 n。面试中说出方案一和方案二即可，并分析各自的时间和空间复杂度。

### Q6：IDEA 中如何进行断点调试？介绍主要步骤和常用操作。

**参考答案**：①在代码行左侧单击添加断点（红点），或按 `Ctrl+F8` / `Command+F8`；②以 Debug 模式运行程序（`Shift+F9` 或点击小虫子图标）；③程序运行到断点处暂停，可以在 Variables 面板查看当前所有局部变量的值；④常用调试操作：`F8`（Step Over，执行当前行，不进入方法内部）、`F7`（Step Into，进入方法）、`Shift+F8`（Step Out，跳出当前方法）、`F9`（Resume，继续执行到下一个断点）；⑤在 Watches 面板可以添加表达式实时计算；⑥右键断点可以添加条件断点（只在满足特定条件时暂停，如 `i == 500`），用于调试循环中的特定迭代。
