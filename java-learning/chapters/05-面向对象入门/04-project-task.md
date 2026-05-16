# Chapter 05 - 面向对象入门 | 项目任务

---

## 业务背景

你正在为某学校开发**学生管理系统（Student Management System）** 的基础数据层。本次任务是设计并实现核心实体类 `Student`，它将作为整个系统的基石，后续的查询、排序、统计功能都依赖这个类。

设计要求体现本章所学的所有知识点：封装、构造器重载、`this`、`static` 成员、`toString`。

---

## 任务要求

实现 `Student` 类，包含以下所有内容：

### 字段设计

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 学号，格式如 "STU001"，只读（final）|
| `name` | `String` | 姓名，不能为空 |
| `age` | `int` | 年龄，1-150 |
| `score` | `double` | 成绩，0-100 |
| `totalStudents` | `static int` | 累计创建的学生对象总数 |

### 方法设计

```java
// 构造器
Student(String id, String name, int age, double score)   // 全参
Student(String id, String name)                          // 简化版：age=18, score=0

// Getter（所有字段）
String getId()
String getName()
int getAge()
double getScore()
static int getTotalStudents()

// Setter（id 不可修改，其余字段需校验）
void setName(String name)
void setAge(int age)
void setScore(double score)

// 业务方法
boolean isPass()           // 成绩 >= 60 视为及格
String getGrade()          // 返回等级：A/B/C/D/F（复用上一章 GradeCalculator 的逻辑）

// 重写方法
String toString()          // 格式化输出学生信息
```

---

## 参考实现

```java
public class Student {

    // ===== 静态字段：记录创建的学生总数 =====
    private static int totalStudents = 0;

    // ===== 实例字段 =====
    private final String id;     // final：学号一旦设定不可更改
    private String name;
    private int age;
    private double score;

    // ===== 全参构造器 =====
    public Student(String id, String name, int age, double score) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        this.id = id.trim();
        setName(name);    // 复用 setter 的校验逻辑
        setAge(age);
        setScore(score);
        totalStudents++;  // 成功创建后计数 +1
    }

    // ===== 简化构造器：age 默认 18，score 默认 0 =====
    public Student(String id, String name) {
        this(id, name, 18, 0.0);
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public double getScore() { return score; }

    // 静态 getter：用类名访问
    public static int getTotalStudents() { return totalStudents; }

    // ===== Setters with validation =====
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("学生姓名不能为空");
        }
        this.name = name.trim();
    }

    public void setAge(int age) {
        if (age < 1 || age > 150) {
            throw new IllegalArgumentException("年龄不合法：" + age + "，合法范围：1-150");
        }
        this.age = age;
    }

    public void setScore(double score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("成绩不合法：" + score + "，合法范围：0-100");
        }
        this.score = score;
    }

    // ===== 业务方法 =====

    /**
     * 判断是否及格（score >= 60）
     */
    public boolean isPass() {
        return score >= 60;
    }

    /**
     * 获取成绩等级
     * A: 90-100, B: 80-89, C: 70-79, D: 60-69, F: <60
     */
    public String getGrade() {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    /**
     * 重写 toString，格式化输出学生信息
     */
    @Override
    public String toString() {
        return String.format("Student{id='%s', name='%s', age=%d, score=%.1f, grade=%s, pass=%s}",
                             id, name, age, score, getGrade(), isPass() ? "是" : "否");
    }

    // ===== 主方法：演示 =====
    public static void main(String[] args) {
        System.out.println("===== 学生管理系统 - 实体类演示 =====\n");

        // 创建学生
        System.out.println("当前学生总数：" + Student.getTotalStudents());  // 0

        Student s1 = new Student("STU001", "Alice", 20, 92.5);
        Student s2 = new Student("STU002", "Bob", 19, 78.0);
        Student s3 = new Student("STU003", "Charlie", 21, 55.0);
        Student s4 = new Student("STU004", "Diana");  // 简化构造器

        System.out.println("创建 4 名学生后，总数：" + Student.getTotalStudents());  // 4
        System.out.println();

        // 输出所有学生信息
        System.out.println("学生列表：");
        Student[] students = {s1, s2, s3, s4};
        for (Student s : students) {
            System.out.println("  " + s);
        }
        System.out.println();

        // 使用业务方法
        System.out.println("及格情况：");
        for (Student s : students) {
            System.out.printf("  %s（%s）：%s%n",
                              s.getName(), s.getId(),
                              s.isPass() ? "及格" : "不及格");
        }
        System.out.println();

        // 更新成绩
        System.out.println("Diana 补考后成绩更新：");
        s4.setScore(65.0);
        System.out.println("  更新后：" + s4);
        System.out.println();

        // 测试校验
        System.out.println("===== 校验测试 =====");
        try {
            s1.setScore(110);  // 非法成绩
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常：" + e.getMessage());
        }

        try {
            new Student("", "TestUser");  // 空学号
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常：" + e.getMessage());
        }

        try {
            s2.setAge(-5);  // 非法年龄
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常：" + e.getMessage());
        }

        // 验证封装：id 不可修改（final 字段没有 setter）
        // s1.setId("NEW_ID");  // 编译错误！没有 setId 方法，也没有直接访问 id 字段的方式
        System.out.println("\n封装验证：id 字段无法从外部修改（没有 setId 方法）");
    }
}
```

**运行输出：**
```
===== 学生管理系统 - 实体类演示 =====

当前学生总数：0
创建 4 名学生后，总数：4

学生列表：
  Student{id='STU001', name='Alice', age=20, score=92.5, grade=A, pass=是}
  Student{id='STU002', name='Bob', age=19, score=78.0, grade=C, pass=是}
  Student{id='STU003', name='Charlie', age=21, score=55.0, grade=F, pass=否}
  Student{id='STU004', name='Diana', age=18, score=0.0, grade=F, pass=否}

及格情况：
  Alice（STU001）：及格
  Bob（STU002）：及格
  Charlie（STU003）：不及格
  Diana（STU004）：不及格

Diana 补考后成绩更新：
  更新后：Student{id='STU004', name='Diana', age=18, score=65.0, grade=D, pass=是}

===== 校验测试 =====
捕获异常：成绩不合法：110.0，合法范围：0-100
捕获异常：学号不能为空
捕获异常：年龄不合法：-5，合法范围：1-150

封装验证：id 字段无法从外部修改（没有 setId 方法）
```

---

## 验收标准（Checklist）

- [ ] `totalStudents` 在创建任意 Student 对象时正确自增，且只能通过 `Student.getTotalStudents()` 访问
- [ ] `id` 字段使用 `final` 修饰，不提供 setter，外部无法修改
- [ ] 所有 setter 均包含合法性校验，传入非法值时抛出 `IllegalArgumentException` 并有清晰的错误信息
- [ ] `isPass()` 和 `getGrade()` 结果与 `score` 字段始终保持一致
- [ ] `toString()` 输出格式清晰，包含所有关键字段，可以一眼看出学生状态

---

## 常见坑

**坑 1：构造器中直接赋值，跳过 setter 校验**

```java
// 错误：如果 score 传入 -1，这里不会抛出异常
public Student(String id, String name, int age, double score) {
    this.score = score;  // 直接赋值，不做校验
}

// 正确：在构造器中调用 setter，复用校验逻辑
public Student(String id, String name, int age, double score) {
    setScore(score);  // 如果 score 非法，setter 会抛出异常
}
```

**坑 2：static 字段用实例方法修改，多线程场景下会出现竞争条件**

```java
// 当前实现在单线程环境下是正确的
// 但在多线程环境（多个线程同时 new Student()）下，totalStudents++ 不是原子操作
// 多线程场景下应使用 AtomicInteger 或加锁（这是进阶内容）
```

当前任务是单线程场景，不需要处理，但了解这个隐患有助于后续学习多线程时有更深的理解。
