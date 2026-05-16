# Chapter 10 - 综合测验（覆盖 Chapter 01-09）

> 本测验难度较高，覆盖前九章核心知识点。建议独立作答后再对照答案。

---

## Q1（代码阅读）：输出什么？

**题目**：仔细阅读以下代码，不运行，判断输出。

```java
public class MysteryClass {

    private int value;

    public MysteryClass(int value) {
        this.value = value;
    }

    public MysteryClass add(int n) {
        this.value += n;
        return this;
    }

    @Override
    public String toString() {
        return "MysteryClass(" + value + ")";
    }

    public static void main(String[] args) {
        // 部分 A
        MysteryClass a = new MysteryClass(10);
        MysteryClass b = a;
        b.add(5);
        System.out.println(a);  // 输出1

        // 部分 B
        MysteryClass c = new MysteryClass(10);
        MysteryClass d = new MysteryClass(10);
        System.out.println(c == d);       // 输出2
        System.out.println(c.equals(d));  // 输出3

        // 部分 C：方法链
        MysteryClass e = new MysteryClass(0);
        e.add(1).add(2).add(3);
        System.out.println(e);  // 输出4

        // 部分 D：多态
        Object obj = new MysteryClass(99);
        System.out.println(obj instanceof MysteryClass);  // 输出5
        MysteryClass f = (MysteryClass) obj;
        System.out.println(f.value);                      // 输出6（注意访问权限）
    }
}
```

**参考答案**：

```
MysteryClass(15)    // 输出1
false               // 输出2
false               // 输出3
MysteryClass(6)     // 输出4
true                // 输出5
99                  // 输出6
```

**详细解析**：

**输出1**：`b = a` 是引用赋值，`a` 和 `b` 指向同一对象。`b.add(5)` 修改了对象的 `value`，`a.value` 也变为 15。

**输出2**：`c` 和 `d` 是两个不同的对象（`new` 创建了不同的内存地址），`==` 比较引用地址，结果为 `false`。

**输出3**：`MysteryClass` 没有重写 `equals()`，继承自 `Object` 的默认实现也是比较引用地址，等同于 `==`，结果为 `false`。（如果重写了 `equals()` 比较 `value` 字段，结果才是 `true`。）

**输出4**：`add()` 方法返回 `this`，支持方法链。`e.add(1)` → e.value=1，`.add(2)` → e.value=3，`.add(3)` → e.value=6，最终输出 `MysteryClass(6)`。

**输出5**：`obj` 的运行时类型是 `MysteryClass`，`instanceof MysteryClass` 为 `true`。

**输出6**：`f.value` 访问 `private` 字段，但 `main` 方法在 `MysteryClass` 类内部，所以可以访问 `private` 成员（私有是类级别的，不是对象级别的）。输出 `99`。

---

## Q2（综合：继承 + 多态 + 方法重写）：输出什么？

**题目**：这是一道经典多态陷阱题。

```java
class Animal {
    String name = "动物";

    public String speak() {
        return name + " 说话";
    }

    public void show() {
        System.out.println(this.speak());    // 注意：this.speak() 是多态调用
        System.out.println(this.name);       // 注意：字段不多态
    }
}

class Dog extends Animal {
    String name = "狗";  // 注意：隐藏父类的 name 字段

    @Override
    public String speak() {
        return name + " 汪汪";
    }
}

public class PolymorphismTest {
    public static void main(String[] args) {
        Animal a = new Dog();
        System.out.println(a.speak());   // 行1
        System.out.println(a.name);      // 行2
        a.show();                        // 行3
    }
}
```

**参考答案**：

```
狗 汪汪
动物
狗 汪汪
动物
```

**详细解析**：

**行1 `a.speak()`**：`a` 的运行时类型是 `Dog`，方法调用走多态，调用 `Dog.speak()`，`Dog.name` 是 `"狗"`，输出 `"狗 汪汪"`。

**行2 `a.name`**：**字段访问不是多态的！** 字段的访问由编译期类型决定，`a` 的编译期类型是 `Animal`，所以访问的是 `Animal.name = "动物"`。

**行3 `a.show()`**：`show()` 在 `Animal` 中定义，`Dog` 未重写，所以调用的是 `Animal.show()`。在 `show()` 内部：
- `this.speak()`：`this` 的运行时类型是 `Dog`，方法多态调用 `Dog.speak()`，输出 `"狗 汪汪"`
- `this.name`：`show()` 在 `Animal` 类中，字段访问看的是声明位置的类，访问 `Animal.name = "动物"`

**关键规则**：方法调用是多态的（运行时类型决定），字段访问是静态的（编译期类型决定）。

---

## Q3（综合：泛型 + 异常 + 集合）：找出所有 Bug

**题目**：以下是一个带缓存的学生查找器，存在多处 Bug。找出所有 Bug（至少4个），说明原因，并给出修复版本。

```java
import java.util.*;

public class StudentCache {

    // Bug 1 区域
    private Map cache = new HashMap();

    public Student findOrLoad(int id) {
        // Bug 2 区域
        Student cached = (Student) cache.get(id);
        if (cached != null) {
            return cached;
        }

        // Bug 3 区域
        Student student = loadFromDb(id);
        cache.put(id, student);
        return student;
    }

    public List<Student> findAll(int[] ids) {
        List result = new ArrayList();
        for (int id : ids) {
            try {
                result.add(findOrLoad(id));
            } catch (Exception e) {
                // Bug 4 区域
            }
        }
        return result;
    }

    // 模拟数据库加载（ID 为 0 时返回 null，ID 为负数时抛异常）
    private Student loadFromDb(int id) {
        if (id < 0) throw new IllegalArgumentException("ID不能为负：" + id);
        if (id == 0) return null;
        return new Student(id, "Student" + id, 20, 85.0);
    }

    // Bug 5 区域
    public void updateCache(int id, Student student) {
        cache.put(id, student);
        System.out.println("缓存已更新");
    }
}
```

**参考答案**：

**Bug 1：使用原始类型 `Map`（没有泛型参数）**
- 原因：`private Map cache` 是原始类型，失去类型安全，编译器不检查键值类型。
- 修复：`private Map<Integer, Student> cache = new HashMap<>();`

**Bug 2：不必要的强转，且可能 ClassCastException**
- 原因：由于 Bug 1 的原始类型，`cache.get(id)` 返回 `Object`，需要强转，有风险。
- 修复（配合 Bug 1 修复后）：`Student cached = cache.get(id);` 无需强转。

**Bug 3：`loadFromDb(id)` 可能返回 `null`（id=0 时），直接 put 进 cache 后取出还是 null，但 findOrLoad 会以为"找不到缓存"再次查数据库，形成无限循环（缓存穿透）。**
- 修复：加 null 检查，或在 id=0 时抛异常。
```java
if (id <= 0) throw new IllegalArgumentException("ID必须大于0");
Student student = loadFromDb(id);
cache.put(id, student);
return student;
```

**Bug 4：`catch (Exception e) {}` 静默吞掉所有异常**
- 原因：当某个 id 不合法时，异常被吞掉，调用方不知道哪些 id 失败，`result` 中缺少对应数据但无任何提示。
- 修复：至少记录日志，或将失败的 id 单独收集。
```java
} catch (IllegalArgumentException e) {
    System.err.println("跳过非法 ID=" + id + "：" + e.getMessage());
} catch (Exception e) {
    System.err.println("加载 ID=" + id + " 失败：" + e.getMessage());
}
```

**Bug 5：`findAll` 的 `List result` 也是原始类型**
- 原因：与 Bug 1 相同，`List result` 丢失泛型。
- 修复：`List<Student> result = new ArrayList<>();`

**修复后的完整版**：
```java
import java.util.*;

public class StudentCache {

    private final Map<Integer, Student> cache = new HashMap<>();

    public Student findOrLoad(int id) {
        if (id <= 0) throw new IllegalArgumentException("ID必须大于0");

        Student cached = cache.get(id);
        if (cached != null) {
            return cached;
        }

        Student student = loadFromDb(id);
        cache.put(id, student);
        return student;
    }

    public List<Student> findAll(int[] ids) {
        List<Student> result = new ArrayList<>();
        for (int id : ids) {
            try {
                result.add(findOrLoad(id));
            } catch (IllegalArgumentException e) {
                System.err.println("跳过非法 ID=" + id + "：" + e.getMessage());
            }
        }
        return result;
    }

    private Student loadFromDb(int id) {
        return new Student(id, "Student" + id, 20, 85.0);
    }

    public void updateCache(int id, Student student) {
        cache.put(id, student);
        System.out.println("缓存已更新，id=" + id);
    }
}
```

---

## Q4（综合：枚举 + 访问控制 + 设计）：设计问题分析

**题目**：阅读以下代码并回答问题。

```java
public class OrderProcessor {

    public static final int STATUS_CREATED = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_SHIPPED = 3;

    public void process(int status) {
        if (status == 1) {
            System.out.println("处理新订单");
        } else if (status == 2) {
            System.out.println("处理已付款订单");
        } else if (status == 3) {
            System.out.println("处理已发货订单");
        } else {
            System.out.println("未知状态：" + status);
        }
    }

    // 调用方
    public static void main(String[] args) {
        OrderProcessor p = new OrderProcessor();
        p.process(1);
        p.process(99);  // 传入了不存在的状态，但编译器不会报错
        p.process(STATUS_PAID);
    }
}
```

**问题**：
1. 这段代码有什么设计问题？
2. 如何用枚举重构，使编译器能发现非法状态？

**参考答案**：

**设计问题**：

1. **类型不安全**：`process(int status)` 接受任意整数，`p.process(99)` 这种非法调用编译器不报错，只有运行时才发现走了 `else` 分支。
2. **魔法数字散落各处**：调用方要记住 `1=创建`、`2=付款` 这些约定，容易出错，新人维护困难。
3. **扩展困难**：新增状态需要修改 `if-else` 链，违反开闭原则（对扩展开放，对修改关闭）。
4. **switch/if-else 无法穷举检查**：编译器不知道所有合法的状态值，无法警告漏处理的情况。

**枚举重构**：

```java
public enum OrderStatus {
    CREATED (1, "已创建"),
    PAID    (2, "已付款"),
    SHIPPED (3, "已发货");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }
}

public class OrderProcessor {

    // 重构后：参数类型改为 OrderStatus，非法状态在编译期就报错
    public void process(OrderStatus status) {
        switch (status) {
            case CREATED  -> System.out.println("处理新订单");
            case PAID     -> System.out.println("处理已付款订单");
            case SHIPPED  -> System.out.println("处理已发货订单");
            // Java 14+ switch 表达式中，编译器会检查所有枚举值是否都被处理（穷举性）
        }
    }

    public static void main(String[] args) {
        OrderProcessor p = new OrderProcessor();
        p.process(OrderStatus.CREATED);
        p.process(OrderStatus.PAID);
        // p.process(99);    // 编译错误！类型不匹配
        // p.process(null);  // 运行时 NPE，但至少类型是安全的
    }
}
```

**改进效果**：非法状态在编译期报错，常量意义清晰，switch 可穷举检查，新增状态只需在枚举中添加，处理方法会被编译器提示更新。

---

## Q5（综合：全章节代码设计）：完成以下需求

**题目**：不需要写完整代码，用伪代码或骨架代码描述设计，重点考察综合运用能力。

**需求**：实现一个 `GradeCalculator` 类，功能如下：
- 接收一个 `Map<String, List<Double>>`（学生姓名 → 该学生的多次成绩）
- 计算每位学生的平均分，过滤掉平均分低于 60 的学生
- 将通过的学生按平均分从高到低排序
- 返回 `List<String>` 包含格式化后的字符串（如 `"Alice: 87.50"`）
- 如果输入 map 为 null 或空，返回空列表
- 如果某个学生的成绩列表为 null 或空，跳过该学生

**参考答案**（用 Stream API 和传统写法各一版）：

```java
import java.util.*;

public class GradeCalculator {

    // ===== 传统写法（适合 Chapter 10 阶段）=====
    public static List<String> calculate(Map<String, List<Double>> studentScores) {
        // 边界检查
        if (studentScores == null || studentScores.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 1：计算每个学生的平均分（跳过无效数据）
        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : studentScores.entrySet()) {
            String name = entry.getKey();
            List<Double> scores = entry.getValue();

            if (scores == null || scores.isEmpty()) {
                continue;  // 跳过无成绩的学生
            }

            double sum = 0;
            for (double score : scores) {
                sum += score;
            }
            double avg = sum / scores.size();
            averages.put(name, avg);
        }

        // Step 2：过滤低于 60 分的学生
        List<Map.Entry<String, Double>> passing = new ArrayList<>();
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            if (entry.getValue() >= 60) {
                passing.add(entry);
            }
        }

        // Step 3：按平均分降序排序
        passing.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Step 4：格式化输出
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : passing) {
            result.add(String.format("%s: %.2f", entry.getKey(), entry.getValue()));
        }

        return result;
    }

    // ===== 使用演示 =====
    public static void main(String[] args) {
        Map<String, List<Double>> data = new HashMap<>();
        data.put("Alice",   Arrays.asList(90.0, 85.0, 88.0));  // 平均 87.67
        data.put("Bob",     Arrays.asList(55.0, 60.0, 50.0));  // 平均 55.0，低于60，过滤
        data.put("Carol",   Arrays.asList(78.0, 92.0, 80.0));  // 平均 83.33
        data.put("David",   new ArrayList<>());                  // 空列表，跳过
        data.put("Eve",     Arrays.asList(95.0, 98.0));         // 平均 96.5

        List<String> result = calculate(data);
        result.forEach(System.out::println);
        // 预期输出（降序）：
        // Eve: 96.50
        // Alice: 87.67
        // Carol: 83.33
    }
}
```

**评分要点**（自我检查）：
- [ ] 正确处理 null/空 map 边界
- [ ] 正确跳过 null/空成绩列表的学生
- [ ] 平均分计算正确
- [ ] 过滤条件 `>= 60`（边界包含）
- [ ] 降序排序（高分在前）
- [ ] 格式化字符串使用 `%.2f`（保留两位小数）
