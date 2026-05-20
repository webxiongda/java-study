# Chapter 03 - 方法与调试 | 项目任务

---

## 业务背景

你被分配到学校的教务系统开发组，本次任务是实现一个**成绩计算工具** `GradeCalculator`。

该工具将被教师端调用，输入一组学生成绩，输出平均分、最高分、最低分以及对应的等级评定。系统需要同时支持整数成绩（百分制）和浮点成绩（存在半分情况），因此要求通过**方法重载**来处理两种输入类型。

---

## 任务要求

实现 `GradeCalculator` 类，包含以下方法：

### 核心方法

```java
public class GradeCalculator {

    // 1. 计算整数数组的平均分（重载版本一）
    public static double average(int[] scores) { ... }

    // 2. 计算浮点数组的平均分（重载版本二）
    public static double average(double[] scores) { ... }

    // 3. 找出整数数组中的最高分
    public static int max(int[] scores) { ... }

    // 4. 找出整数数组中的最低分
    public static int min(int[] scores) { ... }

    // 5. 根据平均分返回字母等级
    // A: 90-100, B: 80-89, C: 70-79, D: 60-69, F: <60
    public static String letterGrade(double avg) { ... }

    // main 方法：演示完整功能
    public static void main(String[] args) { ... }
}
```

### 输出示例

```
===== 成绩报告 =====
学生成绩：[85, 92, 78, 96, 88, 73, 65, 90]
平均分：83.38
最高分：96
最低分：65
等级评定：B

浮点成绩：[88.5, 91.0, 76.5, 82.0]
平均分：84.50
等级评定：B
```

---

## 参考实现

```java
public class GradeCalculator {

    /**
     * 计算整数数组的平均分
     * @param scores 整数成绩数组，不能为空
     * @return 平均分（保留两位小数精度）
     */
    public static double average(int[] scores) {
        if (scores == null || scores.length == 0) {
            throw new IllegalArgumentException("成绩数组不能为空");
        }
        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        return (double) sum / scores.length;
    }

    /**
     * 计算浮点数组的平均分（重载）
     * @param scores 浮点成绩数组，不能为空
     * @return 平均分
     */
    public static double average(double[] scores) {
        if (scores == null || scores.length == 0) {
            throw new IllegalArgumentException("成绩数组不能为空");
        }
        double sum = 0;
        for (double score : scores) {
            sum += score;
        }
        return sum / scores.length;
    }

    /**
     * 找出整数数组中的最高分
     * @param scores 整数成绩数组，不能为空
     * @return 最高分
     */
    public static int max(int[] scores) {
        if (scores == null || scores.length == 0) {
            throw new IllegalArgumentException("成绩数组不能为空");
        }
        int maxScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
            }
        }
        return maxScore;
    }

    /**
     * 找出整数数组中的最低分
     * @param scores 整数成绩数组，不能为空
     * @return 最低分
     */
    public static int min(int[] scores) {
        if (scores == null || scores.length == 0) {
            throw new IllegalArgumentException("成绩数组不能为空");
        }
        int minScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] < minScore) {
                minScore = scores[i];
            }
        }
        return minScore;
    }

    /**
     * 根据平均分返回字母等级
     * A: 90-100, B: 80-89, C: 70-79, D: 60-69, F: 60以下
     * @param avg 平均分
     * @return 字母等级 "A"/"B"/"C"/"D"/"F"
     */
    public static String letterGrade(double avg) {
        if (avg >= 90) return "A";
        if (avg >= 80) return "B";
        if (avg >= 70) return "C";
        if (avg >= 60) return "D";
        return "F";
    }

    public static void main(String[] args) {
        // 整数成绩测试
        int[] scores = {85, 92, 78, 96, 88, 73, 65, 90};
        double avg = average(scores);

        System.out.println("===== 成绩报告 =====");
        System.out.print("学生成绩：[");
        for (int i = 0; i < scores.length; i++) {
            System.out.print(scores[i]);
            if (i < scores.length - 1) System.out.print(", ");
        }
        System.out.println("]");
        System.out.printf("平均分：%.2f%n", avg);
        System.out.println("最高分：" + max(scores));
        System.out.println("最低分：" + min(scores));
        System.out.println("等级评定：" + letterGrade(avg));

        System.out.println();

        // 浮点成绩测试（重载方法）
        double[] floatScores = {88.5, 91.0, 76.5, 82.0};
        double floatAvg = average(floatScores);
        System.out.println("浮点成绩：[88.5, 91.0, 76.5, 82.0]");
        System.out.printf("平均分：%.2f%n", floatAvg);
        System.out.println("等级评定：" + letterGrade(floatAvg));
    }
}
```

---

## 验收标准（Checklist）

- [ ] `average(int[])` 和 `average(double[])` 均正确实现，结果与手动计算一致
- [ ] `max` 和 `min` 方法在只有一个元素时、所有元素相同时均能正确返回
- [ ] `letterGrade` 方法边界值处理正确（如 `90.0` 返回 `"A"`，`89.9` 返回 `"B"`，`0.0` 返回 `"F"`）
- [ ] `main` 方法能正常运行，输出格式清晰，平均分保留两位小数

---

## 常见坑

**坑 1：整数除法截断**

```java
// 错误写法
public static double average(int[] scores) {
    int sum = 0;
    for (int s : scores) sum += s;
    return sum / scores.length;  // 两个 int 相除，结果是 int，小数被截断
}

// 正确写法：强制类型转换
return (double) sum / scores.length;
```

**坑 2：`max` / `min` 初始值设置错误**

```java
// 错误写法：初始值设为 0，当所有成绩都大于 0 时 min 会返回错误的 0
int minScore = 0;

// 正确写法：用数组第一个元素作为初始值
int minScore = scores[0];
```

**坑 3：`letterGrade` 边界判断顺序**

```java
// 错误写法：先判断低分区间，会导致逻辑短路
if (avg >= 60) return "D";   // avg=95 也会命中这里，直接返回 D！
if (avg >= 70) return "C";
// ...

// 正确写法：从高分到低分依次判断
if (avg >= 90) return "A";
if (avg >= 80) return "B";
if (avg >= 70) return "C";
if (avg >= 60) return "D";
return "F";
```
