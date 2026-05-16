# Chapter 10 - 项目设计指南：学生管理系统 CLI

## 1. 项目目标

本项目是 Java SE 阶段的综合里程碑，目标是综合运用前九章所有知识点，实现一个可在命令行运行的学生管理系统。

### 功能列表

| 功能 | 描述 |
|------|------|
| 添加学生 | 输入姓名、年龄、成绩，添加到系统 |
| 查找学生（按 ID） | 根据系统分配的 ID 精确查找 |
| 查找学生（按姓名） | 根据姓名模糊查找，返回所有匹配结果 |
| 更新学生信息 | 根据 ID 修改姓名、年龄或成绩 |
| 删除学生 | 根据 ID 删除，不存在时给出明确提示 |
| 查看所有学生 | 列表展示所有学生信息 |
| 按成绩排序 | 支持升序/降序排列后展示 |
| 统计平均分 | 计算所有学生的平均成绩 |
| 成绩区间查找 | 查找成绩在指定范围内的学生 |
| CSV 持久化 | 启动时从文件读取，退出前保存到文件 |

---

## 2. 包结构设计

```
com.example.student/
├── model/
│   └── Student.java              # 数据模型：id/name/age/score
├── service/
│   └── StudentService.java       # 业务逻辑：增删改查、排序、统计
├── exception/
│   └── StudentNotFoundException.java  # 自定义异常
├── ui/
│   └── ConsoleUI.java            # 用户界面：菜单、输入处理
└── Main.java                     # 程序入口（只调用 ConsoleUI.start()）
```

**分层原则**：
- `model` 层：纯数据，无业务逻辑
- `service` 层：所有业务逻辑，不做任何 UI 操作（不打印菜单，不读取用户输入）
- `exception` 层：项目特有的异常类
- `ui` 层：与用户交互，调用 service，处理异常并展示友好信息

---

## 3. 各类职责说明

### Student（model 层）

```
职责：表示一个学生数据对象
字段：
  - int id            —— 唯一标识，由 StudentService 分配，从 1 开始自增
  - String name       —— 姓名，非空，长度 1-20
  - int age           —— 年龄，1-150
  - double score      —— 成绩，0.0-100.0
提供：构造器、所有字段的 getter/setter、toString()
不提供：任何业务逻辑（排序、校验由 service 层负责）
```

### StudentService（service 层）

```
职责：所有业务操作的唯一入口
内部存储：private List<Student> students = new ArrayList<>()
内部 ID 计数器：private int nextId = 1
主要方法：
  add(String name, int age, double score)      → Student
  findById(int id)                             → Student（找不到抛 StudentNotFoundException）
  findByName(String keyword)                   → List<Student>（空列表表示无结果）
  update(int id, String name, int age, double score) → void
  delete(int id)                               → void（找不到抛 StudentNotFoundException）
  getAll()                                     → List<Student>（只读副本）
  getAverage()                                 → double（无学生时返回 0.0）
  sortByScore(boolean ascending)               → List<Student>（排序后的副本，不修改原始列表）
  findByScoreRange(double min, double max)     → List<Student>
  loadFromCsv(String filePath)                 → void
  saveToCsv(String filePath)                   → void
```

### StudentNotFoundException（exception 层）

```
职责：表示按 ID 查找/操作不存在的学生时抛出的异常
继承：RuntimeException
字段：int studentId（找的是哪个 ID）
构造器：StudentNotFoundException(int studentId)
         自动生成消息："未找到 ID 为 X 的学生"
```

### ConsoleUI（ui 层）

```
职责：命令行交互，展示菜单，读取输入，调用 service，展示结果
依赖：StudentService（构造器注入）
主要方法：
  start()    —— 程序主循环，展示菜单，读取选择
  私有方法对应每个菜单项（handleAdd、handleFindById 等）
资源：Scanner（在 start() 结束时关闭）
```

---

## 4. 关键设计决策

### 为什么用 ArrayList 而不是数组？

- **动态扩容**：学生数量不固定，数组需要预先确定大小，ArrayList 自动扩容。
- **操作便利**：`list.remove(student)` 比数组移位操作简单得多。
- **泛型支持**：`List<Student>` 比 `Student[]` 提供更好的类型安全。
- **代价**：随机访问性能略低于数组（O(n) vs O(1) 按索引），但对于学生管理系统的数据量完全可以接受。

### 为什么用 RuntimeException（StudentNotFoundException）？

- 调用 `findById()` 时，找不到学生是可能的场景，但不是"可以恢复"的情况——调用方通常只能提示用户"ID 不存在"。
- 使用 `RuntimeException` 避免每个调用点都写 `try-catch` 或 `throws` 声明，让 `ConsoleUI` 层统一捕获并处理。
- 如果用 `checked` 异常（继承 `Exception`），`findById` 的调用链上所有方法都要声明 `throws StudentNotFoundException`，产生不必要的样板代码。

### 为什么 sortByScore 返回副本而不是原地排序？

- 原地排序会改变 `students` 列表的顺序，影响后续 ID 查找等操作（虽然本项目用 ID 查找不受影响，但这是更安全的设计）。
- 返回副本让调用方可以展示排序结果，同时原始数据保持原有顺序（插入顺序），符合"查询不改变状态"的原则。

### CSV 格式约定

```
文件名：students.csv
格式（每行一个学生）：id,name,age,score
示例：
1,Alice,20,92.5
2,Bob,21,78.0
3,Carol,19,88.5
```

注意：name 字段如果包含逗号，需要用引号包裹（简化版本可以限制姓名不含逗号）。

---

## 5. 代码质量检查清单

完成项目后，对照以下清单逐项检查：

### 封装与访问控制
- [ ] `Student` 的所有字段是 `private`，通过 getter/setter 访问
- [ ] `StudentService` 内部的 `students` 列表和 `nextId` 是 `private`
- [ ] `ConsoleUI` 的辅助方法（`handleAdd` 等）是 `private`

### 异常处理
- [ ] 所有可能的用户输入错误（非数字、成绩越界等）有 `try-catch` 保护，不崩溃
- [ ] `StudentNotFoundException` 在 `ConsoleUI` 层捕获并展示友好信息
- [ ] 没有空的 `catch` 块（`catch (Exception e) {}`）

### 输入校验
- [ ] 成绩必须是 0-100 的数字（非数字或越界时提示重新输入）
- [ ] 姓名不能为空
- [ ] 年龄是合理的正整数

### 泛型使用
- [ ] 所有集合声明都有泛型参数（`List<Student>` 而非 `List`）
- [ ] 没有不必要的强转和原始类型使用

### CSV 持久化
- [ ] 程序启动时调用 `loadFromCsv()`，文件不存在时正常启动（不报错）
- [ ] 用户选择退出时调用 `saveToCsv()`，保存当前数据

### 代码可读性
- [ ] 方法长度不超过 30 行（过长的方法拆分为多个私有方法）
- [ ] 每个类都有简短的 JavaDoc 说明职责
- [ ] 变量和方法名见名知义，无 `a`、`b`、`temp1` 等无意义名称
