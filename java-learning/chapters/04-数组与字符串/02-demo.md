# Chapter 04 - 数组与字符串 | Demo 演示

---

## Demo 1：数组基本操作

> 目标：掌握数组声明、遍历、排序、复制等核心操作，熟悉 Arrays 工具类的常用方法。

```java
import java.util.Arrays;

public class ArrayBasicDemo {

    public static void main(String[] args) {

        // ===== 1. 声明与初始化 =====
        int[] scores = {88, 45, 92, 67, 78, 55, 99, 73};
        System.out.println("原始数组：" + Arrays.toString(scores));
        // 输出：原始数组：[88, 45, 92, 67, 78, 55, 99, 73]

        System.out.println("数组长度：" + scores.length);  // 8

        // ===== 2. 遍历 —— 普通 for 循环 =====
        System.out.print("手动遍历：");
        for (int i = 0; i < scores.length; i++) {
            System.out.print(scores[i] + " ");
        }
        System.out.println();

        // ===== 3. 遍历 —— 增强 for =====
        int total = 0;
        for (int score : scores) {
            total += score;
        }
        System.out.printf("总分：%d，平均分：%.2f%n", total, (double) total / scores.length);

        // ===== 4. Arrays.sort 排序（原地修改） =====
        Arrays.sort(scores);
        System.out.println("排序后：" + Arrays.toString(scores));
        // 输出：排序后：[45, 55, 67, 73, 78, 88, 92, 99]

        // ===== 5. 排序后查找最值 =====
        System.out.println("最低分：" + scores[0]);
        System.out.println("最高分：" + scores[scores.length - 1]);

        // ===== 6. Arrays.copyOf —— 复制（不影响原数组） =====
        int[] top3 = Arrays.copyOfRange(scores, scores.length - 3, scores.length);
        System.out.println("前三名：" + Arrays.toString(top3));
        // 输出：前三名：[88, 92, 99]

        int[] extended = Arrays.copyOf(scores, 10);  // 扩展到长度 10，多余位置填 0
        System.out.println("扩展后：" + Arrays.toString(extended));
        // 输出：扩展后：[45, 55, 67, 73, 78, 88, 92, 99, 0, 0]

        // ===== 7. Arrays.fill —— 填充 =====
        int[] zeros = new int[5];
        Arrays.fill(zeros, -1);
        System.out.println("填充后：" + Arrays.toString(zeros));
        // 输出：填充后：[-1, -1, -1, -1, -1]

        // ===== 8. Arrays.equals —— 内容比较 =====
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        System.out.println("a == b：" + (a == b));           // false（不同对象）
        System.out.println("Arrays.equals：" + Arrays.equals(a, b));  // true（内容相同）

        // ===== 9. 二维数组简单演示 =====
        int[][] matrix = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9}
        };
        System.out.println("\n矩阵输出：");
        for (int[] row : matrix) {
            System.out.println(Arrays.toString(row));
        }
    }
}
```

**运行输出：**
```
原始数组：[88, 45, 92, 67, 78, 55, 99, 73]
数组长度：8
手动遍历：88 45 92 67 78 55 99 73 
总分：597，平均分：74.63
排序后：[45, 55, 67, 73, 78, 88, 92, 99]
最低分：45
最高分：99
前三名：[88, 92, 99]
扩展后：[45, 55, 67, 73, 78, 88, 92, 99, 0, 0]
填充后：[-1, -1, -1, -1, -1]
a == b：false
Arrays.equals：true

矩阵输出：
[1, 2, 3]
[4, 5, 6]
[7, 8, 9]
```

---

## Demo 2：字符串常用方法综合练习

> 目标：在一个完整程序中演示 10 个以上 String 常用方法的实际用法。

```java
public class StringMethodDemo {

    public static void main(String[] args) {
        String text = "  Hello, Java World! Welcome to Java.  ";

        // 1. trim() —— 去除首尾空白
        String trimmed = text.trim();
        System.out.println("trim()       → [" + trimmed + "]");

        // 2. length() —— 获取长度
        System.out.println("length()     → " + trimmed.length());   // 37

        // 3. toUpperCase() / toLowerCase()
        System.out.println("toUpper()    → " + trimmed.toUpperCase());
        System.out.println("toLower()    → " + trimmed.toLowerCase());

        // 4. contains() —— 是否包含子串
        System.out.println("contains('Java')  → " + trimmed.contains("Java"));   // true
        System.out.println("contains('Python')→ " + trimmed.contains("Python")); // false

        // 5. startsWith() / endsWith()
        System.out.println("startsWith('Hello') → " + trimmed.startsWith("Hello")); // true
        System.out.println("endsWith('.')       → " + trimmed.endsWith("."));        // true

        // 6. indexOf() —— 查找子串第一次出现的位置
        System.out.println("indexOf('Java')    → " + trimmed.indexOf("Java"));      // 7
        System.out.println("indexOf('Python')  → " + trimmed.indexOf("Python"));    // -1（不存在）

        // 7. substring() —— 截取子串
        System.out.println("substring(7, 11) → " + trimmed.substring(7, 11));      // Java
        System.out.println("substring(7)     → " + trimmed.substring(7));           // 从下标7到末尾

        // 8. replace() —— 替换
        String replaced = trimmed.replace("Java", "Python");
        System.out.println("replace()    → " + replaced);

        // 9. split() —— 分割
        String csv = "apple,banana,cherry,date";
        String[] fruits = csv.split(",");
        System.out.print("split(',')   → ");
        for (String fruit : fruits) {
            System.out.print("[" + fruit + "] ");
        }
        System.out.println();

        // 10. charAt() —— 获取指定位置字符
        System.out.println("charAt(0)    → " + trimmed.charAt(0));   // H
        System.out.println("charAt(6)    → " + trimmed.charAt(6));   // ' '（空格）

        // 11. equals() vs equalsIgnoreCase()
        String s1 = "hello";
        String s2 = "HELLO";
        System.out.println("equals()           → " + s1.equals(s2));             // false
        System.out.println("equalsIgnoreCase() → " + s1.equalsIgnoreCase(s2));   // true

        // 12. compareTo() —— 字典序比较
        System.out.println("compareTo()：'apple' vs 'banana' → " + "apple".compareTo("banana")); // 负数
        System.out.println("compareTo()：'hello' vs 'hello'  → " + "hello".compareTo("hello"));  // 0

        // 13. isEmpty() / isBlank()（Java 11+）
        System.out.println("isEmpty()：\"\" → " + "".isEmpty());          // true
        System.out.println("isEmpty()：\" \" → " + " ".isEmpty());        // false（有空格）
        System.out.println("isBlank()：\"  \" → " + "  ".isBlank());      // true（全是空白）

        // ===== 综合应用：提取并格式化信息 =====
        System.out.println("\n===== 综合应用 =====");
        String email = "  User_Name@Example.COM  ";
        String cleanEmail = email.trim().toLowerCase();
        System.out.println("原始 email：[" + email + "]");
        System.out.println("清洗后：[" + cleanEmail + "]");

        String[] parts = cleanEmail.split("@");
        System.out.println("用户名：" + parts[0]);   // user_name
        System.out.println("域名：" + parts[1]);     // example.com

        boolean isGmail = cleanEmail.endsWith("@gmail.com");
        System.out.println("是否 Gmail：" + isGmail);
    }
}
```

---

## Demo 3：StringBuilder 性能对比

> 目标：通过真实的时间测量，直观感受 String + 拼接 vs StringBuilder 的性能差距。

```java
public class StringBuilderPerformanceDemo {

    static final int LOOP_COUNT = 10_000;  // 拼接次数

    // 方法 1：使用 String + 拼接（性能差）
    public static String concatWithString(int count) {
        String result = "";
        for (int i = 0; i < count; i++) {
            result += "a";   // 每次创建新 String 对象，O(n²) 时间复杂度
        }
        return result;
    }

    // 方法 2：使用 StringBuilder（性能好）
    public static String concatWithStringBuilder(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("a");  // 直接追加到内部 char[]，O(n) 时间复杂度
        }
        return sb.toString();
    }

    // 方法 3：使用 StringBuilder 并预设容量（性能最好）
    public static String concatWithStringBuilderCapacity(int count) {
        StringBuilder sb = new StringBuilder(count);  // 预设容量，避免扩容
        for (int i = 0; i < count; i++) {
            sb.append("a");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("拼接次数：" + LOOP_COUNT);
        System.out.println("=".repeat(45));

        // 测试 String +
        long start = System.currentTimeMillis();
        String r1 = concatWithString(LOOP_COUNT);
        long time1 = System.currentTimeMillis() - start;
        System.out.printf("String +          耗时：%5d ms，结果长度：%d%n", time1, r1.length());

        // 测试 StringBuilder
        start = System.currentTimeMillis();
        String r2 = concatWithStringBuilder(LOOP_COUNT);
        long time2 = System.currentTimeMillis() - start;
        System.out.printf("StringBuilder     耗时：%5d ms，结果长度：%d%n", time2, r2.length());

        // 测试带容量的 StringBuilder
        start = System.currentTimeMillis();
        String r3 = concatWithStringBuilderCapacity(LOOP_COUNT);
        long time3 = System.currentTimeMillis() - start;
        System.out.printf("SB(预设容量)      耗时：%5d ms，结果长度：%d%n", time3, r3.length());

        System.out.println("=".repeat(45));
        if (time1 > 0) {
            System.out.printf("StringBuilder 比 String+ 快约 %d 倍%n", time1 / Math.max(time2, 1));
        }

        // ===== StringBuilder 的常用操作 =====
        System.out.println("\n===== StringBuilder 常用方法演示 =====");
        StringBuilder sb = new StringBuilder("Hello");
        System.out.println("初始值：" + sb);

        sb.append(", World");
        System.out.println("append 后：" + sb);     // Hello, World

        sb.insert(5, "!!!");
        System.out.println("insert 后：" + sb);     // Hello!!!, World

        sb.delete(5, 8);
        System.out.println("delete 后：" + sb);     // Hello, World

        sb.reverse();
        System.out.println("reverse 后：" + sb);    // dlroW ,olleH

        sb.replace(0, 5, "WORLD");
        System.out.println("replace 后：" + sb);    // WORLD ,olleH

        System.out.println("length：" + sb.length());
        System.out.println("charAt(0)：" + sb.charAt(0));
        System.out.println("toString：" + sb.toString());
    }
}
```

**典型输出（实际时间因机器而异）：**
```
拼接次数：10000
=============================================
String +          耗时：  235 ms，结果长度：10000
StringBuilder     耗时：    1 ms，结果长度：10000
SB(预设容量)      耗时：    0 ms，结果长度：10000
=============================================
StringBuilder 比 String+ 快约 235 倍

===== StringBuilder 常用方法演示 =====
初始值：Hello
append 后：Hello, World
insert 后：Hello!!!, World
delete 后：Hello, World
reverse 后：dlroW ,olleH
replace 后：WORLD ,olleH
length：11
charAt(0)：W
toString：WORLD ,olleH
```

**性能差距分析：**

| 方法 | 时间复杂度 | 对象创建数 |
|------|-----------|-----------|
| `String +`（循环 n 次）| O(n²) | O(n) 个临时对象 |
| `StringBuilder` | O(n) | 1 个 StringBuilder 对象 |
| `StringBuilder`（预设容量）| O(n) | 1 个对象，无扩容 |

**结论：** 需要在循环内频繁拼接字符串时，必须使用 `StringBuilder`，性能差距在循环次数较大时会非常显著。
