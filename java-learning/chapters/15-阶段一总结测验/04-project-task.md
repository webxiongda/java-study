# Chapter 15 - 阶段一总结：综合项目任务

## 业务背景

你是一所学校的系统开发人员，教务处需要一个**成绩单处理工具**。每学期末，教务处会收到一份 CSV 格式的原始成绩文件，需要对其进行解析、统计、排序，并生成格式化的统计报告。

这道综合任务覆盖 **IO + 集合 + 字符串处理 + OOP** 四大主题，是阶段一知识的综合检验。

---

## 任务说明

### 数据说明

**输入文件：** `students.csv`

```
id,name,score
1001,张三,88
1002,李四,92
1003,王五,45
1004,赵六,95
1005,陈七,60
1006,周八,73
1007,吴九,38
1008,郑十,81
```

第一行是表头，之后每行是一个学生记录：`id,name,score`。

**输出文件：** `report.txt`（格式见验收标准第2条）

---

### 第一步：设计 Student 类

```java
public class Student {
    private int id;
    private String name;
    private double score;

    // 请实现：全参构造方法、getter、equals（按 id）、hashCode（按 id）
    // toString 格式：Student{id=1001, name='张三', score=88.0}

    // 提供便捷方法：判断是否及格（score >= 60）
    public boolean isPassed() { return score >= 60; }
}
```

### 第二步：实现 CsvParser（CSV 解析器）

```java
public class CsvParser {

    /**
     * 解析 CSV 文件，返回 Student 列表
     * - 跳过第一行（表头）
     * - 跳过格式不正确的行（打印警告，不抛异常）
     * - 使用 UTF-8 编码读取
     * @param filePath CSV 文件路径
     * @return 解析成功的 Student 列表
     */
    public static List<Student> parse(String filePath) throws IOException { ... }
}
```

### 第三步：实现 GradeStatistics（统计工具）

```java
public class GradeStatistics {

    private final List<Student> students;

    public GradeStatistics(List<Student> students) {
        this.students = new ArrayList<>(students);
    }

    /** 按成绩降序排序，返回新列表（不修改原列表）*/
    public List<Student> sortByScoreDesc() { ... }

    /** 返回所有不及格学生（score < 60），按成绩升序（分最低的在前）*/
    public List<Student> getFailedStudents() { ... }

    /** 计算平均分，保留 2 位小数 */
    public double getAverage() { ... }

    /** 返回最高分 */
    public double getMaxScore() { ... }

    /** 返回最低分 */
    public double getMinScore() { ... }

    /** 统计及格人数 */
    public long getPassCount() { ... }

    /** 统计不及格人数 */
    public long getFailCount() { ... }
}
```

### 第四步：实现 ReportWriter（报告生成器）

```java
public class ReportWriter {

    /**
     * 将统计结果写入 report.txt
     * 格式要求见下方示例
     */
    public static void write(String filePath,
                             List<Student> allSorted,
                             GradeStatistics stats) throws IOException { ... }
}
```

**report.txt 格式示例：**

```
===========================================
         学生成绩统计报告
===========================================
统计时间：2024-03-15 14:30:22
总人数：8
及格人数：6
不及格人数：2
及格率：75.00%
-------------------------------------------
平均分：71.50
最高分：95.00（赵六）
最低分：38.00（吴九）
===========================================
成绩排名（降序）：
 1. 赵六        95.00
 2. 李四        92.00
 3. 张三        88.00
 4. 郑十        81.00
 5. 周八        73.00
 6. 陈七        60.00
 7. 王五        45.00  [不及格]
 8. 吴九        38.00  [不及格]
===========================================
不及格学生名单：
  - 吴九 (38.00)
  - 王五 (45.00)
===========================================
```

### 第五步：编写 main 方法串联所有步骤

```java
public class GradeProcessor {

    public static void main(String[] args) {
        try {
            // 1. 解析 CSV
            List<Student> students = CsvParser.parse("students.csv");
            System.out.println("解析完成，共 " + students.size() + " 名学生");

            // 2. 创建统计工具
            GradeStatistics stats = new GradeStatistics(students);

            // 3. 排序
            List<Student> sorted = stats.sortByScoreDesc();

            // 4. 打印不及格学生
            List<Student> failed = stats.getFailedStudents();
            System.out.println("不及格学生：" + failed.size() + " 名");

            // 5. 生成报告
            ReportWriter.write("report.txt", sorted, stats);
            System.out.println("报告已生成：report.txt");

        } catch (IOException e) {
            System.err.println("处理失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

---

## 验收标准

1. **CSV 解析正确**：表头行被跳过，8 名学生全部正确解析；若 CSV 中有格式错误行（如缺少字段或 score 不是数字），打印警告并跳过，不影响其他行的解析。
2. **report.txt 格式符合规范**：包含所有统计数据（总人数、及格/不及格人数、及格率、平均分、最高/最低分）、成绩排名列表（不及格学生标注 `[不及格]`）、不及格学生名单（按成绩升序）。
3. **排序和过滤结果正确**：成绩排名降序，平均分/最高/最低分计算正确（可手算验证）。
4. **IO 操作安全**：所有文件读写使用 try-with-resources，指定 UTF-8 编码，流不会泄漏。
5. **异常处理合理**：CSV 解析中 `Integer.parseInt` 和 `Double.parseDouble` 的异常被捕获并打印警告（不让整个程序崩溃）；IO 异常向上传播，在 main 中统一处理。

---

## 常见坑

### 坑 1：`split(",")` 在字段内容含逗号时出错

```java
// 若 CSV 字段内有逗号（如：name = "Smith, John"），split 会拆错
String[] parts = line.split(",");

// 严格的 CSV 解析需要处理引号包裹的字段，但本任务数据格式简单，无此问题
// 若遇到此场景，使用 Apache Commons CSV 等专业库
```

另外，`split(",", -1)` 和 `split(",")` 有区别：默认 `split(",")` 会丢弃末尾的空字符串，`-1` 表示不丢弃。处理 CSV 末尾空字段时需注意。

---

### 坑 2：解析 score 时没有 trim()，导致 NumberFormatException

```java
// CSV 行：" 1001, 张三 , 88 "（字段前后有空格，现实中很常见）
String[] parts = line.split(",");
double score = Double.parseDouble(parts[2]);  // 报错：" 88 " 不是合法数字

// 修复：先 trim
double score = Double.parseDouble(parts[2].trim());
```

解析 CSV 时，对每个字段都调用 `.trim()` 是好习惯。

---

### 坑 3：report.txt 中的百分比和小数位格式

```java
// 随手写，格式不统一
System.out.println("及格率：" + passCount / totalCount * 100 + "%");  
// 问题：整数除法，8个人6个及格 → 6/8=0（整数除法！），输出 "0%"

// 修复1：确保浮点除法
double passRate = (double) passCount / totalCount * 100;

// 修复2：用 String.format 统一格式
String.format("及格率：%.2f%%", passRate)  // 注意 %% 才能输出一个 %

// 修复3：成绩对齐格式（右对齐，宽度固定）
String.format("%2d. %-10s %6.2f", rank, name, score)
```
