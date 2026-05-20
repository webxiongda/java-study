# Chapter 03 - 方法与调试 | 知识检测

> 完成以下 5 道题，再对照参考答案自查。建议先独立作答，再看答案。

---

## Q1 概念题 — 值传递 vs 引用传递

**题目：** 阅读以下代码，分析输出结果并解释原因。

```java
public class PassTest {

    public static void changeInt(int x) {
        x = 100;
    }

    public static void changeArray(int[] arr) {
        arr[0] = 100;
    }

    public static void main(String[] args) {
        int num = 1;
        changeInt(num);
        System.out.println("num = " + num);        // 输出什么？

        int[] arr = {1, 2, 3};
        changeArray(arr);
        System.out.println("arr[0] = " + arr[0]);  // 输出什么？
    }
}
```

**问题：**
1. `num` 的输出是什么？为什么？
2. `arr[0]` 的输出是什么？为什么？
3. Java 是值传递还是引用传递？请用一句话概括。

---

**参考答案：**

1. `num = 1`。基本类型传参时，传递的是值的副本，方法内修改 `x` 不影响原变量 `num`。

2. `arr[0] = 100`。数组是引用类型，传参时传递的是引用（地址）的副本，两个引用指向同一个堆中的数组对象，所以通过 `arr[0] = 100` 修改的是堆中的数据，主方法中的 `arr` 能看到这个修改。

3. **Java 永远是值传递**，只不过引用类型传递的值是对象的引用（内存地址），因此通过引用可以修改堆中的对象内容，但无法让原引用变量指向新对象。

---

## Q2 概念题 — 方法重载规则

**题目：** 下列哪些方法组合构成合法的重载？哪些不合法？请逐一判断并说明原因。

```java
// 方法组合 A
int calculate(int a, int b)
double calculate(int a, int b)   // 仅返回值不同

// 方法组合 B
void print(String s)
void print(int n)                // 参数类型不同

// 方法组合 C
void show(int a, double b)
void show(double a, int b)       // 参数顺序不同

// 方法组合 D
void process(int a, int b)
void process(int x, int y)       // 仅参数名不同
```

---

**参考答案：**

| 组合 | 是否合法重载 | 原因 |
|------|------------|------|
| A | **不合法** | 仅返回值类型不同，参数列表完全相同，编译器无法区分，报编译错误 |
| B | **合法** | 参数类型不同（String vs int），构成重载 |
| C | **合法** | 参数类型顺序不同（int,double vs double,int），构成重载。但实际开发中不推荐，容易混淆 |
| D | **不合法** | 参数名不同不影响方法签名，本质上参数列表完全相同，编译报重复方法错误 |

**核心规则：** 重载由参数列表决定（类型、数量、顺序），与返回值类型和参数名无关。

---

## Q3 实操题 — 写一个递归方法

**题目：** 实现一个递归方法 `power(int base, int exp)`，计算 `base` 的 `exp` 次方（`exp >= 0`），不能使用 `Math.pow`。

要求：
1. 写出递归版本
2. 写出迭代版本
3. 验证：`power(2, 10)` 应返回 `1024`

---

**参考答案：**

```java
public class PowerDemo {

    // 递归版本
    public static long powerRecursive(int base, int exp) {
        // 终止条件：任何数的 0 次方等于 1
        if (exp == 0) {
            return 1;
        }
        return base * powerRecursive(base, exp - 1);
    }

    // 迭代版本
    public static long powerIterative(int base, int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println(powerRecursive(2, 10));   // 1024
        System.out.println(powerIterative(2, 10));   // 1024
        System.out.println(powerRecursive(3, 4));    // 81
        System.out.println(powerRecursive(5, 0));    // 1（任何数的 0 次方 = 1）
    }
}
```

**加分思路：** 快速幂算法（分治思想）可将时间复杂度从 O(n) 优化到 O(log n)：
```java
public static long fastPower(int base, int exp) {
    if (exp == 0) return 1;
    if (exp % 2 == 0) {
        long half = fastPower(base, exp / 2);
        return half * half;
    } else {
        return base * fastPower(base, exp - 1);
    }
}
```

---

## Q4 实操题 — 找出代码中的重载错误

**题目：** 下面的代码中存在 2 处与方法重载相关的错误，请找出并说明原因和修复方案。

```java
public class BuggyOverload {

    // 方法 1
    public static String format(int value) {
        return "整数：" + value;
    }

    // 方法 2（问题1在这里）
    public static double format(int value) {
        return value * 1.0;
    }

    // 方法 3
    public static String describe(String name, int age) {
        return name + " is " + age;
    }

    // 方法 4（问题2在这里）
    public static String describe(String username, int userAge) {
        return username + ", age: " + userAge;
    }

    public static void main(String[] args) {
        System.out.println(format(42));
        System.out.println(describe("Alice", 25));
    }
}
```

---

**参考答案：**

**错误 1（方法 1 和方法 2）：**
- 问题：两个 `format` 方法参数列表完全相同（都是 `int value`），仅返回值类型不同（`String` vs `double`）
- 原因：返回值类型不是重载的区分依据，编译器无法判断调用哪个
- 修复方案：修改其中一个方法的参数类型，例如将方法 2 改为 `format(double value)`

**错误 2（方法 3 和方法 4）：**
- 问题：两个 `describe` 方法参数列表完全相同（都是 `String, int`），仅参数变量名不同
- 原因：参数名不影响方法签名，本质上是两个完全相同的方法
- 修复方案：修改参数类型或数量，例如方法 4 改为 `describe(String name, int age, String city)`

---

## Q5 项目应用题 — 设计计算器类的方法签名

**题目：** 你要设计一个 `Calculator` 类，需要支持以下功能：
1. 整数加减乘除
2. 浮点数加减乘除
3. 对一个整数数组求和
4. 对一个 double 数组求平均值
5. 将角度（degree）转为弧度（radian）

请写出合理的方法签名（不需要实现方法体），要求：
- 运用方法重载
- 方法命名遵循驼峰命名法
- 参数和返回值类型合理

---

**参考答案：**

```java
public class Calculator {

    // ===== 整数四则运算 =====
    public static int add(int a, int b) { return 0; }
    public static int subtract(int a, int b) { return 0; }
    public static int multiply(int a, int b) { return 0; }
    public static double divide(int a, int b) { return 0; }   // 返回 double 避免整除截断

    // ===== 浮点数四则运算（重载：参数类型不同） =====
    public static double add(double a, double b) { return 0; }
    public static double subtract(double a, double b) { return 0; }
    public static double multiply(double a, double b) { return 0; }
    public static double divide(double a, double b) { return 0; }

    // ===== 数组求和（重载：参数数组类型不同） =====
    public static int sum(int[] numbers) { return 0; }
    public static double sum(double[] numbers) { return 0; }

    // ===== 数组平均值 =====
    public static double average(int[] numbers) { return 0; }
    public static double average(double[] numbers) { return 0; }

    // ===== 角度转弧度 =====
    public static double toRadian(double degree) { return 0; }
    // 也可提供整数参数的重载版本
    public static double toRadian(int degree) { return 0; }
}
```

**设计要点说明：**
- 整数除法返回 `double` 是为了避免 `5/2=2` 这样的精度丢失
- `sum` 和 `average` 针对 `int[]` 和 `double[]` 分别重载，体现了重载在实际场景的价值
- 角度转弧度接受 `int` 和 `double` 两种参数，方便调用方传入整数角度（如 90 度）
