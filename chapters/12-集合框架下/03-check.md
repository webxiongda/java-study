# Chapter 12 - 集合框架下：自测题

> 完成本节 demo 后，独立作答以下 5 道题，再对照答案检验掌握程度。

---

## Q1（概念）：HashMap 的 put() 方法完整流程是什么？

**题目：** 请描述调用 `map.put(key, value)` 时，HashMap 内部的完整执行流程（含 hash 计算、位置确定、碰撞处理、扩容等）。

---

### 参考答案

**完整流程（Java 8+）：**

1. **计算 hash 值**
   ```
   hash = key.hashCode() ^ (key.hashCode() >>> 16)
   ```
   将高 16 位异或到低 16 位，目的是减少 hash 碰撞（"扰动函数"）。如果 key 为 null，hash 值为 0，存入 index=0 的桶。

2. **确定数组下标**
   ```
   index = hash & (capacity - 1)
   ```
   capacity 始终是 2 的幂，所以等价于对 capacity 取模，但位运算更快。

3. **判断桶是否为空**
   - 若 `table[index] == null`，直接创建新 Node 存入。

4. **桶不为空时处理碰撞**
   - 先判断第一个节点的 key 是否相等（`hash 相等 && (key == 存入的key || key.equals(存入的key))`）：
     - 若相等 → 更新 value，返回旧 value。
   - 若第一个节点是 **TreeNode**（红黑树节点）→ 调用红黑树的插入方法。
   - 否则遍历链表：
     - 找到 key 相同的节点 → 更新 value。
     - 到达链表末尾仍未找到 → 追加新节点到链表尾部。
     - 追加后检查链表长度是否 `>= 8`，若是且数组长度 `>= 64` → 链表转红黑树（treeifyBin）。

5. **插入后检查是否需要扩容**
   - `++size > threshold`（threshold = capacity × loadFactor，默认 0.75）时触发 **resize()**。
   - 扩容：新容量 = 旧容量 × 2，重新计算所有节点位置（rehash）。

**关键数字记忆：**
| 参数 | 默认值 |
|------|--------|
| 初始容量 | 16 |
| 负载因子 | 0.75 |
| 链表转树阈值 | 8（且数组长度>=64） |
| 树退化链表阈值 | 6 |

---

## Q2（概念）：equals() 和 hashCode() 为什么必须同时重写？

**题目：** 解释为什么重写 `equals()` 时必须同时重写 `hashCode()`。如果只重写 `equals()` 不重写 `hashCode()` 会发生什么？

---

### 参考答案

**必须同时重写的根本原因：**

Java 规范要求（Java SE 文档）：
> 如果两个对象 `equals()` 返回 true，那么它们的 `hashCode()` 必须相同。

HashMap / HashSet 的查找逻辑是：
1. 先用 `hashCode()` 定位桶
2. 再用 `equals()` 在桶内精确比较

如果两个"逻辑相等"的对象 hashCode 不同，步骤 1 就会定位到不同的桶，`equals()` 根本不会被调用。

**只重写 equals 不重写 hashCode 的后果：**

```java
Person p1 = new Person("Alice", 30);
Person p2 = new Person("Alice", 30);

System.out.println(p1.equals(p2));  // true（重写了 equals）

Map<Person, String> map = new HashMap<>();
map.put(p1, "工程师");

System.out.println(map.get(p2));    // null！！！
```

原因：
- p1 和 p2 的 `hashCode()` 继承自 Object，返回的是内存地址相关的值，两者不同。
- `map.get(p2)` 先计算 p2 的 hash，定位到不同的桶，永远找不到 p1 存入的值。

**同样的问题出现在 HashSet：**
```java
Set<Person> set = new HashSet<>();
set.add(p1);
System.out.println(set.contains(p2));  // false！
set.add(p2);  // 重复添加成功，set 中出现两个"相同"的对象
```

**结论：** equals 决定"谁相等"，hashCode 决定"去哪个桶找"，两者必须保持一致。

---

## Q3（实操）：分析只重写 equals 未重写 hashCode 的问题

**题目：** 以下 `Person` 类只重写了 `equals()`，未重写 `hashCode()`。分析将两个"相同"Person 放入 HashMap 后会出现什么问题，并给出修复方案。

```java
public class Person {
    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person)) return false;
        Person p = (Person) o;
        return age == p.age && Objects.equals(name, p.name);
    }
    // 没有重写 hashCode()
}
```

---

### 参考答案

**问题分析：**

```java
Person p1 = new Person("Alice", 30);
Person p2 = new Person("Alice", 30);

// 验证 equals
System.out.println(p1.equals(p2));       // true ✓

// 验证 hashCode
System.out.println(p1.hashCode());       // 例如：366712642（JVM内存地址相关）
System.out.println(p2.hashCode());       // 例如：1829164700（不同！）

Map<Person, String> map = new HashMap<>();
map.put(p1, "Alice的部门");

// 问题 1：get 返回 null
String dept = map.get(p2);
System.out.println(dept);               // null（找不到！）

// 问题 2：put 不去重
map.put(p2, "另一个值");
System.out.println(map.size());         // 2（应该是 1）

// 问题 3：HashSet 无法去重
Set<Person> set = new HashSet<>();
set.add(p1);
set.add(p2);
System.out.println(set.size());         // 2（应该是 1）
```

**根本原因：** p1 和 p2 的 hashCode 不同 → 存入不同的桶 → get 时找错了桶。

**修复方案：**

```java
@Override
public int hashCode() {
    // 方式1：用 Objects.hash()（推荐，简洁）
    return Objects.hash(name, age);

    // 方式2：手动计算（了解原理）
    // int result = name != null ? name.hashCode() : 0;
    // result = 31 * result + age;
    // return result;
}
```

修复后验证：
```java
Person p1 = new Person("Alice", 30);
Person p2 = new Person("Alice", 30);

System.out.println(p1.hashCode() == p2.hashCode());  // true ✓

Map<Person, String> map = new HashMap<>();
map.put(p1, "Alice的部门");
System.out.println(map.get(p2));  // "Alice的部门" ✓

Set<Person> set = new HashSet<>();
set.add(p1);
set.add(p2);
System.out.println(set.size());  // 1 ✓
```

**IDE 生成提示：** IntelliJ IDEA 可通过 `Alt+Insert` → `equals() and hashCode()` 自动生成，实际项目中推荐使用。

---

## Q4（实操）：用 Comparator 对 List<Employee> 排序

**题目：** 有如下 `Employee` 类，请用 `Comparator` 实现：按薪资降序排序，薪资相同时按姓名升序排序。写出完整可运行的代码。

```java
public class Employee {
    private String name;
    private double salary;
    // 构造方法、getter 省略
}
```

---

### 参考答案

```java
import java.util.*;

public class EmployeeSortDemo {

    static class Employee {
        private String name;
        private double salary;

        public Employee(String name, double salary) {
            this.name = name;
            this.salary = salary;
        }

        public String getName() { return name; }
        public double getSalary() { return salary; }

        @Override
        public String toString() {
            return name + "(" + salary + ")";
        }
    }

    public static void main(String[] args) {
        List<Employee> employees = new ArrayList<>(Arrays.asList(
            new Employee("张三", 15000),
            new Employee("李四", 20000),
            new Employee("王五", 15000),
            new Employee("赵六", 18000),
            new Employee("陈七", 20000)
        ));

        // 方式1：Comparator.comparing 链式写法（推荐）
        employees.sort(
            Comparator.comparingDouble(Employee::getSalary).reversed()
                      .thenComparing(Employee::getName)
        );

        System.out.println("排序结果：");
        employees.forEach(System.out::println);
        // 输出：
        // 陈七(20000.0)
        // 李四(20000.0)
        // 赵六(18000.0)
        // 王五(15000.0)
        // 张三(15000.0)

        // 方式2：匿名内部类（理解底层逻辑）
        employees.sort(new Comparator<Employee>() {
            @Override
            public int compare(Employee e1, Employee e2) {
                // 薪资降序：e2 - e1（注意正负含义）
                int salaryCompare = Double.compare(e2.getSalary(), e1.getSalary());
                if (salaryCompare != 0) {
                    return salaryCompare;  // 薪资不同，按薪资排
                }
                // 薪资相同，按姓名升序：e1 - e2
                return e1.getName().compareTo(e2.getName());
            }
        });

        // 方式3：Lambda 简化
        employees.sort((e1, e2) -> {
            int salaryCompare = Double.compare(e2.getSalary(), e1.getSalary());
            return salaryCompare != 0 ? salaryCompare
                                      : e1.getName().compareTo(e2.getName());
        });
    }
}
```

**关键点解释：**
- `reversed()` 将原来的升序变为降序。
- `thenComparing()` 在前一个比较器结果为 0 时生效（即薪资相同时启用）。
- `Double.compare(a, b)` 比直接相减更安全（避免精度问题导致返回值截断为 0）。

---

## Q5（项目应用）：单词频率统计功能设计

**题目：** 设计一个单词频率统计功能：给定一段英文文本，统计每个单词出现的次数，并按频率降序输出前 N 个高频词。用 HashMap 实现，分析时间复杂度。

---

### 参考答案

```java
import java.util.*;
import java.util.stream.*;

public class WordFrequencyCounter {

    /**
     * 统计单词频率
     * @param text 输入文本
     * @return 单词 -> 出现次数 的映射
     */
    public static Map<String, Integer> countFrequency(String text) {
        Map<String, Integer> freqMap = new HashMap<>();

        // 按非字母字符分割，转小写，过滤空串
        String[] words = text.toLowerCase().split("[^a-zA-Z]+");

        for (String word : words) {
            if (!word.isEmpty()) {
                // getOrDefault 简化计数逻辑
                freqMap.put(word, freqMap.getOrDefault(word, 0) + 1);

                // 等价写法（Java 8+）：
                // freqMap.merge(word, 1, Integer::sum);
            }
        }
        return freqMap;
    }

    /**
     * 获取出现频率最高的 topN 个单词
     */
    public static List<Map.Entry<String, Integer>> getTopN(
            Map<String, Integer> freqMap, int n) {
        return freqMap.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        String text = "to be or not to be that is the question " +
                      "whether tis nobler in the mind to suffer";

        Map<String, Integer> freq = countFrequency(text);

        System.out.println("=== 全部单词频率 ===");
        freq.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));

        System.out.println("\n=== Top 5 高频词 ===");
        getTopN(freq, 5).forEach(e ->
            System.out.println(e.getKey() + " -> " + e.getValue() + " 次"));
    }
}
```

**时间复杂度分析：**

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| 分割文本 | O(n) | n 为文本字符数 |
| 遍历单词并 put | O(w) | w 为单词数，HashMap put 均摊 O(1) |
| 排序获取 TopN | O(k log k) | k 为不重复单词数 |
| **整体** | **O(n + k log k)** | 瓶颈在排序 |

**空间复杂度：** O(k)，k 为不重复单词数。

**优化方向：**
- 若只需 TopN 而不需要全排序，可用 **最小堆（PriorityQueue）** 将排序降为 O(k log N)。
- 大规模文本（如 GB 级）需要考虑分片处理或 MapReduce。
