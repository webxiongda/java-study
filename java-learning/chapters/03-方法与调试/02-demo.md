# Chapter 03 - 方法与调试 | Demo 演示

---

## Demo 1：方法定义与重载

> 目标：理解方法签名、重载规则，掌握同名不同参数的方法定义方式。

```java
public class MethodOverloadDemo {

    // 重载方法 1：两个 int 相加
    public static int add(int a, int b) {
        System.out.println("调用：add(int, int)");
        return a + b;
    }

    // 重载方法 2：三个 int 相加
    public static int add(int a, int b, int c) {
        System.out.println("调用：add(int, int, int)");
        return a + b + c;
    }

    // 重载方法 3：两个 double 相加
    public static double add(double a, double b) {
        System.out.println("调用：add(double, double)");
        return a + b;
    }

    public static void main(String[] args) {
        System.out.println(add(1, 2));          // 调用方法1，输出 3
        System.out.println(add(1, 2, 3));       // 调用方法2，输出 6
        System.out.println(add(1.5, 2.5));      // 调用方法3，输出 4.0

        // 注意：编译器根据参数类型和数量自动选择匹配的重载版本
        System.out.println(add(1, 2.0));        // int 自动提升为 double，调用方法3
    }
}
```

**运行输出：**
```
调用：add(int, int)
3
调用：add(int, int, int)
6
调用：add(double, double)
4.0
调用：add(double, double)
3.0
```

**重点笔记：**
- 重载（Overload）= 同一个类中，方法名相同，参数列表不同（类型、数量、顺序）
- 返回值类型不同 **不能** 构成重载
- 编译器在编译期就决定调用哪个版本（静态绑定）

---

## Demo 2：递归方法

> 目标：理解递归的两个要素（递归终止条件 + 递归调用），并与迭代方法对比。

### 2-1 阶乘（n!）

```java
public class RecursionDemo {

    // ===== 递归版本 =====
    public static long factorialRecursive(int n) {
        // 终止条件（Base Case）：一定要有，否则无限递归导致 StackOverflowError
        if (n <= 1) {
            return 1;
        }
        // 递归调用：问题规模缩小
        return n * factorialRecursive(n - 1);
    }

    // ===== 迭代版本（推荐实际使用） =====
    public static long factorialIterative(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    public static void main(String[] args) {
        int n = 6;
        System.out.println("递归版 6! = " + factorialRecursive(n)); // 720
        System.out.println("迭代版 6! = " + factorialIterative(n)); // 720
    }
}
```

### 2-2 斐波那契数列

```java
public class FibonacciDemo {

    // 递归版（直观但性能差，存在大量重复计算）
    public static long fibRecursive(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        return fibRecursive(n - 1) + fibRecursive(n - 2);
    }

    // 迭代版（推荐：时间 O(n)，空间 O(1)）
    public static long fibIterative(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        long prev = 0, curr = 1;
        for (int i = 2; i <= n; i++) {
            long next = prev + curr;
            prev = curr;
            curr = next;
        }
        return curr;
    }

    public static void main(String[] args) {
        System.out.println("=== 斐波那契数列前 10 项 ===");
        for (int i = 0; i < 10; i++) {
            System.out.print(fibIterative(i) + " ");
        }
        System.out.println();
        // 输出：0 1 1 2 3 5 8 13 21 34

        // 性能对比：n=40 时递归版明显慢
        long start = System.currentTimeMillis();
        fibRecursive(40);
        System.out.println("递归版 fib(40) 耗时：" + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        fibIterative(40);
        System.out.println("迭代版 fib(40) 耗时：" + (System.currentTimeMillis() - start) + "ms");
    }
}
```

**递归 vs 迭代 对比：**

| 维度 | 递归 | 迭代 |
|------|------|------|
| 代码可读性 | 高（贴近数学定义） | 一般 |
| 性能 | 较差（函数调用开销，可能栈溢出） | 较好 |
| 适用场景 | 树/图遍历、分治算法 | 简单循环计算 |

---

## Demo 3：IDEA 断点调试步骤

### 调试步骤说明

1. **设置断点**：在代码行号左侧单击，出现红色圆点即为断点
2. **以 Debug 模式运行**：点击工具栏的虫子图标（或快捷键 `Shift+F9`），而非普通运行
3. **程序暂停在断点处**：当前行高亮显示，程序暂停执行
4. **查看变量值**：
   - 底部 "Variables" 面板显示当前作用域所有变量的值
   - 鼠标悬停在变量名上可快速查看
5. **单步调试**：
   - `F8`（Step Over）：执行当前行，跳过方法内部，移动到下一行
   - `F7`（Step Into）：进入当前行调用的方法内部
   - `Shift+F8`（Step Out）：从当前方法跳出，回到调用处
   - `F9`（Resume）：继续执行到下一个断点或程序结束
6. **条件断点**：右键断点 → 填写条件表达式（如 `i == 5`），只有满足条件时才暂停
7. **结束调试**：点击红色方块停止，或 `Ctrl+F2`

### 含 Bug 的代码 — 供调试练习

下面这段代码本来要计算数组所有元素之和，但运行结果不对。请用断点调试找出 bug。

```java
public class DebugPractice {

    // 计算数组元素之和（含 bug，请调试找出问题）
    public static int sumArray(int[] arr) {
        int sum = 0;
        // bug 在哪里？仔细检查循环边界
        for (int i = 0; i <= arr.length; i++) {   // <-- 这行有问题
            sum += arr[i];
        }
        return sum;
    }

    // 计算平均值（也含 bug）
    public static double average(int[] arr) {
        int total = 0;
        for (int i = 0; i < arr.length; i++) {
            total += arr[i];
        }
        // bug：整数除法会截断小数
        return total / arr.length;  // <-- 这行有问题
    }

    public static void main(String[] args) {
        int[] scores = {85, 92, 78, 96, 88};

        // 预期 sum = 439，预期 average = 87.8
        System.out.println("总和：" + sumArray(scores));
        System.out.println("平均：" + average(scores));
    }
}
```

**调试任务：**
1. 在 `sumArray` 方法的 `for` 循环体内设置断点，观察每次循环时 `i` 和 `sum` 的值
2. 找出 `i <= arr.length` 导致的 `ArrayIndexOutOfBoundsException`，修复为 `i < arr.length`
3. 在 `average` 方法的 `return` 行设置断点，观察 `total / arr.length` 的结果为何是整数
4. 修复方法：将除法改为 `(double) total / arr.length`

**修复后正确代码：**
```java
public static int sumArray(int[] arr) {
    int sum = 0;
    for (int i = 0; i < arr.length; i++) {  // 修复：< 而非 <=
        sum += arr[i];
    }
    return sum;
}

public static double average(int[] arr) {
    int total = 0;
    for (int i = 0; i < arr.length; i++) {
        total += arr[i];
    }
    return (double) total / arr.length;  // 修复：强制类型转换
}
```
