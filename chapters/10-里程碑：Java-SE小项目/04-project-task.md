# Chapter 10 - 项目任务：完成学生管理系统

> 前置：[[09-枚举与泛型入门]]
> 后续：[[11-集合框架上]] → 后续 24 章把 CSV 持久化换成 [[24-JDBC入门|JDBC]]，31 章再迁移到 [[31-Spring核心|Spring Boot]]

## 任务说明

在 `02-demo.md` 骨架代码的基础上，完成所有标注 `// TODO` 的功能，并额外增加三项增强功能。这是 Java SE 阶段的最终综合项目，请独立完成。

---

## 第一部分：补全骨架中的 TODO

### TODO 1：`StudentService.findByName()`

按姓名关键字模糊查找（不区分大小写），返回所有包含该关键字的学生列表。

```
输入：keyword = "ali"
行为：找出所有 name 包含 "ali"（忽略大小写）的学生
输出：[Student{id=1, name='Alice', ...}]（如果 Alice 存在）
```

提示：使用 `for` 循环遍历 `students`，用 `String.toLowerCase()` + `String.contains()` 判断。

### TODO 2：`StudentService.sortByScore(boolean ascending)`

返回按成绩排序的学生列表副本，不修改原始 `students` 列表。

```
ascending=true  → 成绩从低到高（升序）
ascending=false → 成绩从高到低（降序）
```

提示：`Collections.sort(list, comparator)` 或 `list.sort(comparator)`，结合 `Comparator.comparingDouble(Student::getScore)`。

### TODO 3：`StudentService.findByScoreRange(double min, double max)`

返回成绩在 `[min, max]`（闭区间）范围内的所有学生列表。

### TODO 4：`StudentService.loadFromCsv(String filePath)`

从 CSV 文件读取学生数据，文件不存在时静默忽略。

CSV 格式（每行一个学生）：
```
id,name,age,score
1,Alice,20,92.5
2,Bob,21,78.0
```

注意：读取完所有数据后，将 `nextId` 设置为当前最大 id + 1，确保后续添加的学生 id 不重复。

### TODO 5：`StudentService.saveToCsv(String filePath)`

将当前所有学生数据写入 CSV 文件。如果文件已存在则覆盖。

### TODO 6：`ConsoleUI.handleFindByScoreRange()`

实现 UI 交互：读取 min 和 max，调用 service，展示结果。需要校验 `min <= max`。

---

## 第二部分：增强功能

### 增强 1：输入验证强化

当前 `readScore()` 只校验了范围，需要确保：
- 成绩输入非数字时提示"请输入有效的数字"并重新输入（当前骨架已实现，验证是否正确）
- `readPositiveInt()` 对年龄要增加上限校验（年龄不超过 150）
- 姓名不能超过 20 个字符

### 增强 2：CSV 持久化

确保以下场景都能正常工作：
- 首次运行（没有 `students.csv`）：正常启动，提示"数据文件不存在，已创建新库"
- 有历史数据：启动时加载，显示"已从文件加载 X 名学生"
- 正常退出（选 0）：保存数据，显示"已保存 X 名学生到文件"
- 程序异常退出：数据可能不会保存（这是已知限制，可以用 `Runtime.getRuntime().addShutdownHook()` 改进，了解即可）

### 增强 3：`findByScoreRange` 方法

在 `StudentService` 中实现，在 `ConsoleUI` 中完成对应的 UI 交互（菜单选项 9）。

---

## 参考结构

```
project/
├── src/
│   └── com/example/student/
│       ├── model/Student.java
│       ├── service/StudentService.java
│       ├── exception/StudentNotFoundException.java
│       ├── ui/ConsoleUI.java
│       └── Main.java
├── students.csv          ← 运行后自动生成
└── README.md（可选）
```

---

## 验收标准

1. **功能完整性**：所有 10 个菜单选项都能正常使用，无 TODO 残留；程序不会因用户输入非法字符（如在"输入 ID"时输入字母）而崩溃。

2. **CSV 持久化正确**：退出程序后重新启动，之前添加/修改/删除的学生数据完整保留；ID 自增不重复（重启后新添加的学生 ID 接续上次，不从 1 重新开始）。

3. **异常处理完整**：所有 `StudentNotFoundException` 在 UI 层捕获并展示友好信息（如"未找到 ID 为 X 的学生，请确认 ID 是否正确"）；所有用户输入类异常（`NumberFormatException`）有 try-catch 保护，提示重新输入。

4. **代码符合设计规范**：service 层没有任何 `Scanner`、`System.out.println` 等 UI 操作；ui 层没有直接操作 `students` 列表；`Student` 类没有业务逻辑方法。

5. **边界场景正常**：成绩区间查找 `min > max` 时给出提示；无学生时查看所有/平均分/排序都有友好提示（"暂无学生数据"）；删除不存在的学生时给出提示而非崩溃。

---

## 进入下一阶段

完成本章后，下面这些「未来要改」的部分先在心里记一笔：

| 当前实现 | 后续替代 | 章节 |
|---|---|---|
| CSV 文件持久化 | MySQL + JDBC | [[24-JDBC入门]] |
| 手写 SQL 拼接 | MyBatis Mapper | [[28-MyBatis入门]] |
| `ConsoleUI` 入口 | Spring Boot Web + REST | [[31-Spring核心]] / [[36-SpringBoot+MyBatis]] |
| `StudentService.findByName` | jakarta.validation + 分页 | [[34-参数校验与异常处理]] / [[29-MyBatis进阶]] |
| 异常打印到控制台 | 统一 `ApiResponse` + Logback | [[23-日志体系]] / [[34-参数校验与异常处理]] |

> 这是阶段一的「能跑」交付物。 阶段二要把它升级成「能放到面试官面前」的真后端项目。


## 常见坑

**坑 1：CSV 读取后 nextId 没有更新，导致 ID 重复**

加载 CSV 后，`nextId` 默认还是 1。如果文件里已有 id=1、2、3 的学生，新添加的学生 id 也会从 1 开始，导致 id 冲突。

```java
// 错误：加载后忘记更新 nextId
public void loadFromCsv(String filePath) {
    // ... 读取并添加 student 到 students 列表
    // 忘记了：
    // nextId = students.stream().mapToInt(Student::getId).max().orElse(0) + 1;
}

// 正确：加载完成后计算最大 id，更新 nextId
public void loadFromCsv(String filePath) {
    // ... 读取
    if (!students.isEmpty()) {
        int maxId = 0;
        for (Student s : students) {
            if (s.getId() > maxId) maxId = s.getId();
        }
        nextId = maxId + 1;
    }
}
```

**坑 2：CSV 解析时没有 trim()，空格导致解析失败**

如果 CSV 文件中某行有多余空格（如 `1, Alice, 20, 92.5`），`Integer.parseInt(" Alice")` 会抛 `NumberFormatException`。

```java
// 错误：直接 split 不处理空格
String[] parts = line.split(",");
int id = Integer.parseInt(parts[0]);        // 如果有空格会报错
String name = parts[1];                     // 含前置空格

// 正确：每个字段都 trim()
String[] parts = line.split(",");
int id       = Integer.parseInt(parts[0].trim());
String name  = parts[1].trim();
int age      = Integer.parseInt(parts[2].trim());
double score = Double.parseDouble(parts[3].trim());
```

**坑 3：sortByScore 直接对内部 students 排序，破坏原始顺序**

```java
// 错误：直接对 students 排序，永久改变内部列表顺序
public List<Student> sortByScore(boolean ascending) {
    students.sort(Comparator.comparingDouble(Student::getScore));  // 修改了 students！
    return students;  // 返回的是内部引用，外部修改会影响内部
}

// 正确：创建副本，对副本排序，返回副本
public List<Student> sortByScore(boolean ascending) {
    List<Student> sorted = new ArrayList<>(students);  // 副本
    Comparator<Student> comparator = Comparator.comparingDouble(Student::getScore);
    if (!ascending) {
        comparator = comparator.reversed();
    }
    sorted.sort(comparator);
    return sorted;  // 返回副本，不影响内部 students
}
```
