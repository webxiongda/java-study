# Java 基础语法 实操 Demo

## Demo 1：8 种基本类型与类型转换

### 实操目标
掌握 8 种基本类型的声明、赋值规则，理解自动类型转换和强制类型转换的行为。

### 示例代码

```java
public class PrimitiveTypesDemo {
    public static void main(String[] args) {
        // ====== 1. 整数类型 ======
        byte b = 127;           // byte 最大值
        short s = 32767;        // short 最大值
        int i = 2_147_483_647;  // int 最大值（下划线分隔提升可读性）
        long l = 9_223_372_036_854_775_807L; // long 必须加 L

        System.out.println("byte  max: " + b);
        System.out.println("short max: " + s);
        System.out.println("int   max: " + i);
        System.out.println("long  max: " + l);

        // 整数溢出演示
        byte overflow = (byte) 128;  // 128 超出 byte 范围（最大 127）
        System.out.println("byte 溢出: " + overflow);  // -128（环绕）

        // ====== 2. 浮点类型 ======
        float f = 3.14f;   // float 必须加 f
        double d = 3.141592653589793;

        System.out.println("float : " + f);
        System.out.println("double: " + d);

        // 浮点数精度问题
        System.out.println("0.1 + 0.2 = " + (0.1 + 0.2)); // 0.30000000000000004
        System.out.println("1.0 - 0.9 = " + (1.0 - 0.9)); // 0.09999999999999998

        // ====== 3. 字符类型 ======
        char c1 = 'A';
        char c2 = '中';       // Java char 是 Unicode，可存中文
        char c3 = 65;         // 也可以用 ASCII/Unicode 码值
        System.out.println("char: " + c1 + " " + c2 + " " + c3);  // A 中 A
        System.out.println("char + int: " + (c1 + 1));  // 66（char 参与运算自动提升为 int）

        // ====== 4. 布尔类型 ======
        boolean flag = true;
        boolean isEmpty = "".equals(""); // true
        System.out.println("boolean: " + flag + ", isEmpty: " + isEmpty);

        // ====== 5. 自动类型转换 ======
        int intVal = 100;
        long longVal = intVal;     // int -> long，自动，无损
        double doubleVal = intVal; // int -> double，自动，无损
        System.out.println("int->long: " + longVal + ", int->double: " + doubleVal);

        // ====== 6. 强制类型转换 ======
        double pi = 3.14159;
        int truncated = (int) pi;  // 截断小数，不是四舍五入
        System.out.println("double->int (强转截断): " + truncated);  // 3

        long bigNum = 10_000_000_000L;
        int smallNum = (int) bigNum;  // 数据溢出，不可预期
        System.out.println("long->int (溢出): " + smallNum);  // 1410065408

        // ====== 7. 整数除法陷阱 ======
        int a = 7, n = 2;
        double wrong = a / n;            // 先做整数除法得 3，再赋值，结果 3.0
        double correct = (double) a / n; // 先强转 a 为 double，再做浮点除法
        System.out.println("整数除法陷阱: " + wrong);   // 3.0
        System.out.println("正确浮点除法: " + correct); // 3.5
    }
}
```

### 运行结果

```
byte  max: 127
short max: 32767
int   max: 2147483647
long  max: 9223372036854775807
byte 溢出: -128
float : 3.14
double: 3.141592653589793
0.1 + 0.2 = 0.30000000000000004
1.0 - 0.9 = 0.09999999999999998
char: A 中 A
char + int: 66
boolean: true, isEmpty: true
int->long: 100, int->double: 100.0
double->int (强转截断): 3
long->int (溢出): 1410065408
整数除法陷阱: 3.0
正确浮点除法: 3.5
```

### 关键点说明

- `long` 字面量必须加 `L`，`float` 字面量必须加 `f`，否则编译器默认当作 `int` 或 `double`
- 强制类型转换是截断而非四舍五入，`(int) 3.9 = 3`
- `char` 类型参与算术运算时自动提升为 `int`
- 金融计算不要用 `double`，应用 `BigDecimal("0.1")`

---

## Demo 2：流程控制综合练习 — 学生成绩分级

### 实操目标
综合运用 if-else、switch、for、while、Scanner，实现一个实际场景的控制流逻辑。

### 示例代码

```java
import java.util.Scanner;

public class GradeSystem {

    // 根据分数返回等级
    public static String getGrade(int score) {
        if (score < 0 || score > 100) {
            return "无效分数";
        }
        // JDK 14+ switch 表达式（老版本可改为 if-else）
        return switch (score / 10) {
            case 10, 9 -> "A（优秀）";
            case 8 -> "B（良好）";
            case 7 -> "C（中等）";
            case 6 -> "D（及格）";
            default -> "F（不及格）";
        };
    }

    // 计算平均分
    public static double calcAverage(int[] scores) {
        if (scores.length == 0) return 0;
        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        return (double) sum / scores.length;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== 学生成绩管理系统 ===");
        System.out.print("请输入学生人数：");
        int count = scanner.nextInt();

        int[] scores = new int[count];
        int highest = Integer.MIN_VALUE;  // 记录最高分
        int lowest = Integer.MAX_VALUE;   // 记录最低分

        // 输入成绩
        for (int i = 0; i < count; i++) {
            System.out.print("请输入第 " + (i + 1) + " 个学生的成绩（0-100）：");
            int score = scanner.nextInt();

            // 输入校验（循环直到输入合法）
            while (score < 0 || score > 100) {
                System.out.print("分数无效，请重新输入（0-100）：");
                score = scanner.nextInt();
            }

            scores[i] = score;

            // 更新最高/最低分
            if (score > highest) highest = score;
            if (score < lowest) lowest = score;
        }

        // 统计各等级人数
        int countA = 0, countB = 0, countC = 0, countD = 0, countF = 0;
        for (int score : scores) {
            String grade = getGrade(score);
            if (grade.startsWith("A")) countA++;
            else if (grade.startsWith("B")) countB++;
            else if (grade.startsWith("C")) countC++;
            else if (grade.startsWith("D")) countD++;
            else countF++;
        }

        // 打印统计报告
        System.out.println("\n=== 成绩统计报告 ===");
        System.out.printf("平均分：%.2f%n", calcAverage(scores));
        System.out.println("最高分：" + highest + " (" + getGrade(highest) + ")");
        System.out.println("最低分：" + lowest + " (" + getGrade(lowest) + ")");
        System.out.println("A（优秀）：" + countA + " 人");
        System.out.println("B（良好）：" + countB + " 人");
        System.out.println("C（中等）：" + countC + " 人");
        System.out.println("D（及格）：" + countD + " 人");
        System.out.println("F（不及格）：" + countF + " 人");

        // 打印每位学生的分数和等级
        System.out.println("\n=== 逐人成绩 ===");
        for (int i = 0; i < scores.length; i++) {
            System.out.printf("学生 %02d：%3d 分  %s%n", i + 1, scores[i], getGrade(scores[i]));
        }

        scanner.close();
    }
}
```

### 运行结果（示例输入：3 名学生，成绩 95、72、55）

```
=== 学生成绩管理系统 ===
请输入学生人数：3
请输入第 1 个学生的成绩（0-100）：95
请输入第 2 个学生的成绩（0-100）：72
请输入第 3 个学生的成绩（0-100）：55

=== 成绩统计报告 ===
平均分：74.00
最高分：95 (A（优秀）)
最低分：55 (F（不及格）)
A（优秀）：1 人
B（良好）：0 人
C（中等）：1 人
D（及格）：0 人
F（不及格）：1 人

=== 逐人成绩 ===
学生 01： 95 分  A（优秀）
学生 02： 72 分  C（中等）
学生 03： 55 分  F（不及格）
```

### 关键点说明

- `Integer.MIN_VALUE` 和 `Integer.MAX_VALUE` 是 int 的边界常量，用于初始化最大/最小值时很有用
- `System.out.printf("%.2f", ...)` 格式化输出，保留 2 位小数
- `%n` 是平台无关的换行符，比 `\n` 更规范
- `while` 内部做输入校验，实现"输入合法才退出"的效果
- `score / 10` 将 0-100 的分数映射到 0-10 的区间，简化 switch 分支

---

## Demo 3：运算符与控制流边界测试

### 实操目标
验证逻辑运算符的短路特性、自增自减的细节行为，以及 break/continue 的效果。

### 示例代码

```java
public class OperatorDemo {
    public static void main(String[] args) {
        // ====== 短路运算 ======
        System.out.println("=== 短路运算 ===");
        int x = 0;
        // && 短路：x==0 为 false，后面的 10/x 不执行，不会除零异常
        if (x != 0 && 10 / x > 1) {
            System.out.println("不会到这里");
        } else {
            System.out.println("&&短路，安全避开了除零");
        }

        // || 短路：第一个条件为 true，后面不执行
        int count = 0;
        if (true || (++count > 0)) {
            System.out.println("||短路，count 仍然是：" + count); // count = 0，++count 未执行
        }

        // ====== 自增自减 ======
        System.out.println("\n=== 自增自减 ===");
        int a = 5;
        int b = a++;  // b=5，然后 a 变为 6
        int c = ++a;  // a 先变为 7，c=7
        System.out.println("a=" + a + ", b=" + b + ", c=" + c);  // a=7, b=5, c=7

        // ====== break 和 continue ======
        System.out.println("\n=== break 和 continue ===");
        System.out.print("continue 跳过 3 和 7：");
        for (int i = 0; i <= 10; i++) {
            if (i == 3 || i == 7) continue;
            if (i == 9) break;
            System.out.print(i + " ");
        }
        System.out.println();  // 换行

        // ====== 嵌套循环 + 带标签的 break ======
        System.out.println("\n=== 带标签的 break（跳出外层循环）===");
        outer:
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                if (i + j == 7) {
                    System.out.println("找到 i=" + i + " j=" + j + "，i+j=7，跳出所有循环");
                    break outer;  // 跳出外层循环
                }
            }
        }

        // ====== 三元运算符 ======
        System.out.println("\n=== 三元运算符 ===");
        int score = 75;
        String result = (score >= 60) ? "及格" : "不及格";
        System.out.println("分数 " + score + "：" + result);

        // ====== 位运算（面试常考）======
        System.out.println("\n=== 位运算 ===");
        int num = 8;
        System.out.println("8 << 1 = " + (num << 1));  // 左移1位 = 乘以2 = 16
        System.out.println("8 >> 1 = " + (num >> 1));  // 右移1位 = 除以2 = 4
        System.out.println("判断奇偶：" + num + " & 1 = " + (num & 1));  // 0 为偶数
        System.out.println("判断奇偶：7 & 1 = " + (7 & 1));              // 1 为奇数
    }
}
```

### 运行结果

```
=== 短路运算 ===
&&短路，安全避开了除零
||短路，count 仍然是：0

=== 自增自减 ===
a=7, b=5, c=7

=== break 和 continue ===
continue 跳过 3 和 7：0 1 2 4 5 6 8 

=== 带标签的 break（跳出外层循环）===
找到 i=2 j=5，i+j=7，跳出所有循环

=== 三元运算符 ===
分数 75：及格

=== 位运算 ===
8 << 1 = 16
8 >> 1 = 4
判断奇偶：8 & 1 = 0
判断奇偶：7 & 1 = 1
```

### 关键点说明

- `&&` 和 `||` 的短路特性不只是性能优化，更是一种安全防护（防止空指针、除零等）
- `n & 1` 判断奇偶比 `n % 2 == 0` 更高效（位运算直接操作二进制）
- `n << 1` 等价于 `n * 2`，`n >> 1` 等价于 `n / 2`，位移运算在处理权限标志、哈希函数时很常见
- 带标签的 `break` 是合法的 Java 语法，但过度使用会降低可读性，优先考虑重构为方法
