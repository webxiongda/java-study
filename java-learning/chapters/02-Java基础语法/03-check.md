# Java 基础语法 自测题

> 先独立作答，再看参考答案。对于代码题，建议在 IDEA 中运行验证。

## 题目

### Q1（概念）：Java 的 8 种基本类型分别是什么？各占几个字节？哪种类型不能参与 switch 语句（JDK 21 以前）？

### Q2（概念）：下面代码的输出是什么？请解释原因。

```java
int a = 5;
int b = a++;
int c = ++a;
System.out.println("a=" + a + " b=" + b + " c=" + c);
```

### Q3（实操）：下面代码有什么问题？请修复。

```java
public class CalcDemo {
    public static void main(String[] args) {
        int x = 10;
        int y = 3;
        double result = x / y;
        System.out.println(result);  // 期望输出 3.333...，实际是？

        byte small = 200;  // 有问题吗？

        long time = System.currentTimeMillis();  // 有问题吗？
    }
}
```

### Q4（实操）：编写一个程序，使用 Scanner 读取用户输入的整数 n，打印 1 到 n 中所有不能被 3 整除但能被 5 整除的数，如果没有满足条件的数则打印"无符合条件的数"。

### Q5（项目应用）：你在开发一个电商系统的优惠券模块，需要实现一个方法来计算最终支付金额。规则如下：
- 原价 `originalPrice`（double）
- 折扣率 `discountRate`（double，范围 0.0~1.0，1.0 表示不打折）
- 满减金额 `rebate`（double，满 100 减 20，不满不减）
- 最终价格不能低于 1 元
- 使用 Scanner 从控制台读取这三个参数，计算并输出最终价格（保留 2 位小数）

---

## 参考答案

### A1：

8 种基本类型：

| 类型 | 字节 |
|------|------|
| byte | 1 |
| short | 2 |
| int | 4 |
| long | 8 |
| float | 4 |
| double | 8 |
| char | 2 |
| boolean | 规范未定义（实际通常 1 字节） |

`boolean` 不能用于 switch 语句（JDK 21 以前）。switch 支持的类型有：`byte`、`short`、`char`、`int`（及其包装类）、`String`（JDK 7+）、枚举（enum）。`long`、`float`、`double` 也不能直接用于 switch。

---

### A2：

输出：`a=7 b=5 c=7`

执行过程：
1. `int a = 5`：a = 5
2. `int b = a++`：先将 a 的值（5）赋给 b，然后 a 自增变为 6；所以 b = 5，a = 6
3. `int c = ++a`：先将 a 自增变为 7，再将新值赋给 c；所以 c = 7，a = 7
4. 输出：`a=7 b=5 c=7`

---

### A3：

**问题 1：整数除法陷阱**
```java
double result = x / y;  // x 和 y 都是 int，先做整数除法 10/3=3，再赋值给 double
// 实际输出 3.0，而非 3.3333...
```
修复：
```java
double result = (double) x / y;  // 或 x * 1.0 / y 或 x / (double) y
```

**问题 2：byte 超出范围**
```java
byte small = 200;  // 编译错误！byte 范围是 -128~127，200 超出范围
// 修复：
byte small = (byte) 200;  // 强转（结果为 -56，可能不是你想要的）
// 或者直接用 int/short
```

**问题 3：long 接收 currentTimeMillis**
```java
long time = System.currentTimeMillis();  // 这个没问题，是正确的！
// System.currentTimeMillis() 返回值就是 long 类型
```

---

### A4：

```java
import java.util.Scanner;

public class DivisibleCheck {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入整数 n：");
        int n = scanner.nextInt();

        boolean found = false;
        StringBuilder result = new StringBuilder();

        for (int i = 1; i <= n; i++) {
            // 不能被 3 整除 且 能被 5 整除
            if (i % 3 != 0 && i % 5 == 0) {
                result.append(i).append(" ");
                found = true;
            }
        }

        if (found) {
            System.out.println("符合条件的数：" + result.toString().trim());
        } else {
            System.out.println("无符合条件的数");
        }

        scanner.close();
    }
}
```

测试：n=30 时，输出 `符合条件的数：5 10 20 25`（15、30 能被 3 整除，排除）

---

### A5：

```java
import java.util.Scanner;

public class CouponCalculator {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("请输入原价：");
        double originalPrice = scanner.nextDouble();

        System.out.print("请输入折扣率（0.0~1.0，1.0为不打折）：");
        double discountRate = scanner.nextDouble();

        System.out.print("请输入满减金额（满100减多少）：");
        double rebate = scanner.nextDouble();

        // 参数校验
        if (originalPrice <= 0 || discountRate < 0 || discountRate > 1 || rebate < 0) {
            System.out.println("参数无效，请检查输入");
            scanner.close();
            return;
        }

        // 第一步：应用折扣
        double price = originalPrice * discountRate;

        // 第二步：满减（满 100 才减）
        if (price >= 100) {
            price -= rebate;
        }

        // 第三步：最低 1 元
        if (price < 1.0) {
            price = 1.0;
        }

        System.out.printf("原价：%.2f 元%n", originalPrice);
        System.out.printf("折后价（折扣率 %.0f%%）：%.2f 元%n", discountRate * 100, originalPrice * discountRate);
        System.out.printf("最终支付：%.2f 元%n", price);

        scanner.close();
    }
}
```

示例输出（输入 原价150，折扣0.8，满减20）：
```
原价：150.00 元
折后价（折扣率 80%）：120.00 元
最终支付：100.00 元
```
