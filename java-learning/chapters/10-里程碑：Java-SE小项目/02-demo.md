# Chapter 10 - 项目骨架代码

以下是学生管理系统的完整可运行骨架。标有 `// TODO` 的部分需要你在项目任务中补全。

---

## Student.java（model 层）

```java
package com.example.student.model;

/**
 * 学生数据模型
 * 职责：存储学生信息，提供 getter/setter，无业务逻辑
 */
public class Student {

    private int id;
    private String name;
    private int age;
    private double score;

    public Student(int id, String name, int age, double score) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.score = score;
    }

    // ===== Getters =====
    public int getId()       { return id; }
    public String getName()  { return name; }
    public int getAge()      { return age; }
    public double getScore() { return score; }

    // ===== Setters（id 不提供 setter，由 service 分配后不可改） =====
    public void setName(String name)   { this.name = name; }
    public void setAge(int age)        { this.age = age; }
    public void setScore(double score) { this.score = score; }

    @Override
    public String toString() {
        return String.format("Student{id=%d, name='%s', age=%d, score=%.1f}",
                id, name, age, score);
    }
}
```

---

## StudentNotFoundException.java（exception 层）

```java
package com.example.student.exception;

/**
 * 按 ID 查找不存在的学生时抛出
 */
public class StudentNotFoundException extends RuntimeException {

    private final int studentId;

    public StudentNotFoundException(int studentId) {
        super("未找到 ID 为 " + studentId + " 的学生");
        this.studentId = studentId;
    }

    public int getStudentId() {
        return studentId;
    }
}
```

---

## StudentService.java（service 层）

```java
package com.example.student.service;

import com.example.student.exception.StudentNotFoundException;
import com.example.student.model.Student;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生管理业务服务
 * 职责：所有业务操作的唯一入口，不做任何 UI 操作
 */
public class StudentService {

    private final List<Student> students = new ArrayList<>();
    private int nextId = 1;

    // ===== 增 =====

    /**
     * 添加新学生
     * @param name  姓名（调用方保证非空）
     * @param age   年龄（调用方保证合法）
     * @param score 成绩 0-100（调用方保证合法）
     * @return 新创建的学生对象
     */
    public Student add(String name, int age, double score) {
        Student student = new Student(nextId++, name, age, score);
        students.add(student);
        return student;
    }

    // ===== 查 =====

    /**
     * 按 ID 查找学生
     * @throws StudentNotFoundException 如果 ID 不存在
     */
    public Student findById(int id) {
        return students.stream()
                .filter(s -> s.getId() == id)
                .findFirst()
                .orElseThrow(() -> new StudentNotFoundException(id));
    }

    /**
     * 按姓名关键字查找（包含关系，不区分大小写）
     * @return 匹配的学生列表，无结果时返回空列表
     */
    public List<Student> findByName(String keyword) {
        // TODO: 实现按姓名模糊查找
        // 提示：用 stream + filter，检查 name 是否包含 keyword（忽略大小写）
        return new ArrayList<>();
    }

    /**
     * 获取所有学生（返回副本，防止外部修改内部列表）
     */
    public List<Student> getAll() {
        return new ArrayList<>(students);
    }

    // ===== 改 =====

    /**
     * 更新学生信息
     * @throws StudentNotFoundException 如果 ID 不存在
     */
    public void update(int id, String name, int age, double score) {
        Student student = findById(id);  // 不存在会抛异常
        student.setName(name);
        student.setAge(age);
        student.setScore(score);
    }

    // ===== 删 =====

    /**
     * 删除学生
     * @throws StudentNotFoundException 如果 ID 不存在
     */
    public void delete(int id) {
        Student student = findById(id);  // 不存在会抛异常
        students.remove(student);
    }

    // ===== 统计 =====

    /**
     * 计算平均成绩
     * @return 平均分，无学生时返回 0.0
     */
    public double getAverage() {
        if (students.isEmpty()) return 0.0;
        double sum = 0;
        for (Student s : students) {
            sum += s.getScore();
        }
        return sum / students.size();
    }

    /**
     * 按成绩排序后返回副本
     * @param ascending true=升序，false=降序
     */
    public List<Student> sortByScore(boolean ascending) {
        List<Student> sorted = new ArrayList<>(students);
        // TODO: 实现排序
        // 提示：用 Comparator.comparingDouble(Student::getScore)，升序或降序
        return sorted;
    }

    /**
     * 查找成绩在 [min, max] 范围内的学生
     * TODO: 实现此方法
     */
    public List<Student> findByScoreRange(double min, double max) {
        // TODO: 实现
        return new ArrayList<>();
    }

    // ===== CSV 持久化 =====

    /**
     * 从 CSV 文件加载学生数据
     * 文件格式：id,name,age,score（每行一个学生）
     * 文件不存在时静默忽略（正常启动）
     */
    public void loadFromCsv(String filePath) {
        // TODO: 实现
        // 提示：
        // 1. 用 Files.exists() 检查文件是否存在，不存在直接返回
        // 2. 用 try-with-resources + BufferedReader 逐行读取
        // 3. 用 String.split(",") 解析每行
        // 4. 解析后创建 Student，添加到 students 列表
        // 5. 更新 nextId（设置为当前最大 id + 1）
    }

    /**
     * 将学生数据保存到 CSV 文件
     */
    public void saveToCsv(String filePath) {
        // TODO: 实现
        // 提示：
        // 1. 用 try-with-resources + BufferedWriter 写文件
        // 2. 每个学生写一行：id + "," + name + "," + age + "," + score
    }
}
```

---

## ConsoleUI.java（ui 层）

```java
package com.example.student.ui;

import com.example.student.exception.StudentNotFoundException;
import com.example.student.model.Student;
import com.example.student.service.StudentService;

import java.util.List;
import java.util.Scanner;

/**
 * 命令行用户界面
 * 职责：展示菜单、读取用户输入、调用 service、展示结果、处理异常
 */
public class ConsoleUI {

    private static final String CSV_FILE = "students.csv";

    private final StudentService service;
    private final Scanner scanner;

    public ConsoleUI() {
        this.service = new StudentService();
        this.scanner = new Scanner(System.in);
    }

    /**
     * 程序主入口：加载数据、展示菜单、处理输入、保存退出
     */
    public void start() {
        // 启动时加载数据
        service.loadFromCsv(CSV_FILE);
        System.out.println("学生管理系统启动成功。");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("请输入选项：");

            switch (choice) {
                case 1  -> handleAdd();
                case 2  -> handleFindById();
                case 3  -> handleFindByName();
                case 4  -> handleUpdate();
                case 5  -> handleDelete();
                case 6  -> handleListAll();
                case 7  -> handleSortByScore();
                case 8  -> handleAverage();
                case 9  -> handleFindByScoreRange();
                case 0  -> running = false;
                default -> System.out.println("无效选项，请重新输入。");
            }
        }

        // 退出前保存数据
        service.saveToCsv(CSV_FILE);
        System.out.println("数据已保存，再见！");
        scanner.close();
    }

    private void printMenu() {
        System.out.println("\n========== 学生管理系统 ==========");
        System.out.println("1. 添加学生");
        System.out.println("2. 按 ID 查找学生");
        System.out.println("3. 按姓名查找学生");
        System.out.println("4. 更新学生信息");
        System.out.println("5. 删除学生");
        System.out.println("6. 查看所有学生");
        System.out.println("7. 按成绩排序");
        System.out.println("8. 查看平均分");
        System.out.println("9. 按成绩区间查找");
        System.out.println("0. 退出");
        System.out.println("==================================");
    }

    // ===== 菜单处理方法 =====

    private void handleAdd() {
        System.out.println("\n--- 添加学生 ---");
        String name = readNonBlankString("请输入姓名：");
        int age = readPositiveInt("请输入年龄：");
        double score = readScore("请输入成绩（0-100）：");

        Student student = service.add(name, age, score);
        System.out.println("添加成功：" + student);
    }

    private void handleFindById() {
        System.out.println("\n--- 按 ID 查找 ---");
        int id = readInt("请输入学生 ID：");
        try {
            Student student = service.findById(id);
            System.out.println("找到：" + student);
        } catch (StudentNotFoundException e) {
            System.out.println("提示：" + e.getMessage());
        }
    }

    private void handleFindByName() {
        System.out.println("\n--- 按姓名查找 ---");
        String keyword = readNonBlankString("请输入姓名关键字：");
        List<Student> results = service.findByName(keyword);
        if (results.isEmpty()) {
            System.out.println("未找到包含 \"" + keyword + "\" 的学生。");
        } else {
            System.out.println("找到 " + results.size() + " 条记录：");
            results.forEach(System.out::println);
        }
    }

    private void handleUpdate() {
        System.out.println("\n--- 更新学生信息 ---");
        int id = readInt("请输入要更新的学生 ID：");
        try {
            Student existing = service.findById(id);
            System.out.println("当前信息：" + existing);

            String name  = readNonBlankString("请输入新姓名（当前：" + existing.getName() + "）：");
            int age      = readPositiveInt("请输入新年龄（当前：" + existing.getAge() + "）：");
            double score = readScore("请输入新成绩（当前：" + existing.getScore() + "）：");

            service.update(id, name, age, score);
            System.out.println("更新成功！");
        } catch (StudentNotFoundException e) {
            System.out.println("提示：" + e.getMessage());
        }
    }

    private void handleDelete() {
        System.out.println("\n--- 删除学生 ---");
        int id = readInt("请输入要删除的学生 ID：");
        try {
            service.delete(id);
            System.out.println("删除成功，ID=" + id + " 的学生已移除。");
        } catch (StudentNotFoundException e) {
            System.out.println("提示：" + e.getMessage());
        }
    }

    private void handleListAll() {
        System.out.println("\n--- 所有学生 ---");
        List<Student> all = service.getAll();
        if (all.isEmpty()) {
            System.out.println("暂无学生数据。");
        } else {
            System.out.println("共 " + all.size() + " 名学生：");
            all.forEach(System.out::println);
        }
    }

    private void handleSortByScore() {
        System.out.println("\n--- 按成绩排序 ---");
        System.out.println("1. 升序（成绩低→高）");
        System.out.println("2. 降序（成绩高→低）");
        int choice = readInt("请选择：");
        boolean ascending = (choice == 1);

        List<Student> sorted = service.sortByScore(ascending);
        if (sorted.isEmpty()) {
            System.out.println("暂无学生数据。");
        } else {
            System.out.println("排序结果（" + (ascending ? "升序" : "降序") + "）：");
            sorted.forEach(System.out::println);
        }
    }

    private void handleAverage() {
        double avg = service.getAverage();
        System.out.printf("%n--- 平均成绩 ---%n");
        System.out.printf("所有学生平均成绩：%.2f 分%n", avg);
    }

    private void handleFindByScoreRange() {
        // TODO: 实现成绩区间查找的 UI 交互
        // 提示：读取 min 和 max，调用 service.findByScoreRange()，展示结果
        System.out.println("\n--- 按成绩区间查找 ---");
        System.out.println("（TODO：待实现）");
    }

    // ===== 输入辅助方法 =====

    /**
     * 读取一个整数，输入非法时提示重新输入
     */
    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("请输入有效的整数！");
            }
        }
    }

    /**
     * 读取一个正整数
     */
    private int readPositiveInt(String prompt) {
        while (true) {
            int value = readInt(prompt);
            if (value > 0) return value;
            System.out.println("请输入大于 0 的整数！");
        }
    }

    /**
     * 读取成绩（0.0-100.0）
     */
    private double readScore(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double score = Double.parseDouble(scanner.nextLine().trim());
                if (score >= 0 && score <= 100) return score;
                System.out.println("成绩必须在 0-100 之间！");
            } catch (NumberFormatException e) {
                System.out.println("请输入有效的数字！");
            }
        }
    }

    /**
     * 读取非空字符串
     */
    private String readNonBlankString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("输入不能为空！");
        }
    }
}
```

---

## Main.java（程序入口）

```java
package com.example.student;

import com.example.student.ui.ConsoleUI;

/**
 * 程序入口
 * 只做一件事：创建 UI 并启动
 */
public class Main {

    public static void main(String[] args) {
        new ConsoleUI().start();
    }
}
```

---

## 骨架运行说明

1. 将以上文件按包结构创建
2. 运行 `Main.main()`，程序可以启动并显示菜单
3. 功能 1（添加）、2（按ID查找）、4（更新）、5（删除）、6（查看全部）、8（平均分）已实现
4. **功能 3（按姓名查找）、7（排序）、9（区间查找）、CSV 持久化**标有 `// TODO`，留待在 `04-project-task.md` 中完成

**编译方式**（假设在项目根目录下）：
```bash
# 编译
javac -d out -sourcepath src src/com/example/student/Main.java

# 运行
java -cp out com.example.student.Main
```
