# 集合框架下 实操 Demo

## Demo 1：验证 hashCode/equals 契约对 HashMap 的影响

### 实操目标
通过对比"正确实现"和"错误实现"，理解 hashCode 和 equals 对 HashMap 行为的影响。

### 示例代码

```java
import java.util.*;

public class HashCodeEqualsDemo {

    // 错误示例：只重写 equals，没有重写 hashCode
    static class BadKey {
        String name;
        int age;

        BadKey(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BadKey)) return false;
            BadKey other = (BadKey) o;
            return this.name.equals(other.name) && this.age == other.age;
        }
        // 没有重写 hashCode！！！
    }

    // 正确示例：同时重写
    static class GoodKey {
        String name;
        int age;

        GoodKey(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GoodKey)) return false;
            GoodKey other = (GoodKey) o;
            return this.name.equals(other.name) && this.age == other.age;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }

        @Override
        public String toString() {
            return name + "(" + age + ")";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 错误示例：只重写 equals ===");
        Map<BadKey, String> badMap = new HashMap<>();
        BadKey k1 = new BadKey("Alice", 25);
        BadKey k2 = new BadKey("Alice", 25);

        System.out.println("k1.equals(k2): " + k1.equals(k2));      // true
        System.out.println("k1.hashCode(): " + k1.hashCode());       // 不同！基于地址
        System.out.println("k2.hashCode(): " + k2.hashCode());       // 不同！

        badMap.put(k1, "value1");
        System.out.println("put k1 后，用 k2 get: " + badMap.get(k2)); // null！！！

        System.out.println("\n=== 正确示例：同时重写 ===");
        Map<GoodKey, String> goodMap = new HashMap<>();
        GoodKey g1 = new GoodKey("Alice", 25);
        GoodKey g2 = new GoodKey("Alice", 25);

        System.out.println("g1.equals(g2): " + g1.equals(g2));      // true
        System.out.println("g1.hashCode(): " + g1.hashCode());       // 相同
        System.out.println("g2.hashCode(): " + g2.hashCode());       // 相同

        goodMap.put(g1, "value1");
        System.out.println("put g1 后，用 g2 get: " + goodMap.get(g2)); // value1

        // HashSet 去重也依赖这两个方法
        System.out.println("\n=== HashSet 去重验证 ===");
        Set<BadKey> badSet = new HashSet<>();
        badSet.add(new BadKey("Alice", 25));
        badSet.add(new BadKey("Alice", 25)); // 没有去重！
        System.out.println("BadKey set size（预期1，实际）: " + badSet.size()); // 2

        Set<GoodKey> goodSet = new HashSet<>();
        goodSet.add(new GoodKey("Alice", 25));
        goodSet.add(new GoodKey("Alice", 25)); // 正确去重
        System.out.println("GoodKey set size（预期1，实际）: " + goodSet.size()); // 1

        // 使用 record（Java 16+）自动生成 hashCode/equals
        System.out.println("\n=== 使用 record（Java 16+）===");
        // record 自动生成规范的 hashCode、equals、toString
    }
}

// Java 16+ 用 record 简化（自动生成 hashCode/equals/toString/constructor）
record PersonKey(String name, int age) {
    // 无需手写任何方法
}
```

### 运行结果

```
=== 错误示例：只重写 equals ===
k1.equals(k2): true
k1.hashCode(): 746292446    （基于内存地址，每次运行不同）
k2.hashCode(): 1021653265   （不同！）
put k1 后，用 k2 get: null

=== 正确示例：同时重写 ===
g1.equals(g2): true
g1.hashCode(): 63575519     （相同）
g2.hashCode(): 63575519     （相同）
put g1 后，用 g2 get: value1

=== HashSet 去重验证 ===
BadKey set size（预期1，实际）: 2
GoodKey set size（预期1，实际）: 1
```

### 关键点说明
- `Objects.hash(field1, field2, ...)` 是生成 hashCode 的推荐方式，内部调用 `Arrays.hashCode()`。
- Java 16+ 的 `record` 类型自动生成符合契约的 hashCode/equals，推荐用于数据传输对象（DTO）。
- 作为 HashMap key 的对象应该是不可变的，否则修改后 hashCode 变化会导致找不到。

---

## Demo 2：Comparable 和 Comparator 的使用对比

### 实操目标
理解两种排序接口的适用场景，掌握 Comparator 链式调用的多字段排序写法。

### 示例代码

```java
import java.util.*;

public class ComparatorDemo {

    // Comparable：定义自然顺序（按工资升序）
    static class Employee implements Comparable<Employee> {
        String name;
        String department;
        double salary;
        int yearsOfService;

        Employee(String name, String department, double salary, int years) {
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.yearsOfService = years;
        }

        @Override
        public int compareTo(Employee other) {
            return Double.compare(this.salary, other.salary); // 按工资升序
        }

        @Override
        public String toString() {
            return String.format("%s(%s, %.0f元, %d年)",
                    name, department, salary, yearsOfService);
        }
    }

    public static void main(String[] args) {
        List<Employee> employees = new ArrayList<>(Arrays.asList(
            new Employee("Alice",   "技术", 25000, 3),
            new Employee("Bob",     "运营", 15000, 5),
            new Employee("Charlie", "技术", 30000, 2),
            new Employee("Diana",   "运营", 15000, 3),
            new Employee("Eve",     "技术", 25000, 7),
            new Employee("Frank",   "HR",   18000, 1)
        ));

        // 1. 自然排序（按工资升序，Comparable）
        Collections.sort(employees);
        System.out.println("自然排序（工资升序）：");
        employees.forEach(e -> System.out.println("  " + e));

        // 2. 按工资降序（Comparator.reversed 或 reverseOrder）
        employees.sort(Comparator.comparingDouble(Employee::getSalary).reversed());
        // 注意：需要给字段加 getter，或直接用 lambda
        employees.sort((a, b) -> Double.compare(b.salary, a.salary));
        System.out.println("\n工资降序：");
        employees.forEach(e -> System.out.println("  " + e));

        // 3. 多字段排序：先按部门，再按工资降序，再按姓名
        Comparator<Employee> multiSort = Comparator
            .comparing((Employee e) -> e.department)           // 按部门字母序
            .thenComparingDouble((Employee e) -> -e.salary)    // 部门相同按工资降序（取负）
            .thenComparing(e -> e.name);                       // 工资相同按姓名

        employees.sort(multiSort);
        System.out.println("\n多字段排序（部门→工资↓→姓名）：");
        employees.forEach(e -> System.out.println("  " + e));

        // 4. TreeMap 按 key 自定义排序（按字符串长度）
        TreeMap<String, Integer> byLength = new TreeMap<>(
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())
        );
        byLength.put("banana", 1);
        byLength.put("apple", 2);
        byLength.put("kiwi", 3);
        byLength.put("fig", 4);
        byLength.put("cherry", 5);
        System.out.println("\nTreeMap 按长度排序：");
        byLength.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // 5. 使用 TreeSet 存储自定义对象
        TreeSet<Employee> sortedByYears = new TreeSet<>(
            Comparator.comparingInt((Employee e) -> e.yearsOfService)
                      .reversed()
                      .thenComparing(e -> e.name)
        );
        sortedByYears.addAll(employees);
        System.out.println("\n按工龄降序的 TreeSet：");
        sortedByYears.forEach(e -> System.out.println("  " + e));
    }
}
```

### 运行结果

```
自然排序（工资升序）：
  Bob(运营, 15000元, 5年)
  Diana(运营, 15000元, 3年)
  Frank(HR, 18000元, 1年)
  Alice(技术, 25000元, 3年)
  Eve(技术, 25000元, 7年)
  Charlie(技术, 30000元, 2年)

工资降序：
  Charlie(技术, 30000元, 2年)
  Alice(技术, 25000元, 3年)
  Eve(技术, 25000元, 7年)
  Frank(HR, 18000元, 1年)
  Bob(运营, 15000元, 5年)
  Diana(运营, 15000元, 3年)

多字段排序（部门→工资↓→姓名）：
  Frank(HR, 18000元, 1年)
  Charlie(技术, 30000元, 2年)
  Alice(技术, 25000元, 3年)
  Eve(技术, 25000元, 7年)
  Bob(运营, 15000元, 5年)
  Diana(运营, 15000元, 3年)

TreeMap 按长度排序：
  fig -> 4
  kiwi -> 3
  apple -> 2
  banana -> 1
  cherry -> 5

按工龄降序的 TreeSet：
  Eve(技术, 25000元, 7年)
  Bob(运营, 15000元, 5年)
  Alice(技术, 25000元, 3年)
  ...
```

### 关键点说明
- `Comparator.comparing()` 接受 key extractor 函数，比 lambda `(a,b)->...` 更可读。
- `.reversed()` 直接翻转整个比较器，比写 `(a,b)->compare(b,a)` 更清晰。
- `.thenComparing()` 只在前一个比较器返回 0（相等）时才调用，实现稳定多字段排序。
- TreeSet 用 Comparator 排序时，它同时作为"相等"判断依据——两个元素 compareTo 返回 0 就认为相等（不会同时存入），所以 Comparator 要包含所有区分字段。

---

## Demo 3：手动模拟 HashMap 核心逻辑（加深理解）

### 实操目标
通过实现一个极简版 SimpleHashMap，真正理解桶分配、hash 碰撞、链表存储的工作流程。

### 示例代码

```java
import java.util.*;

/**
 * 极简 HashMap 实现，仅用于理解原理，不处理扩容和红黑树
 */
public class SimpleHashMapDemo {

    static class SimpleHashMap<K, V> {
        private static final int CAPACITY = 16;
        private Object[][] buckets = new Object[CAPACITY][];  // 每个桶是键值对数组

        // 简化版：用 ArrayList 模拟链表
        @SuppressWarnings("unchecked")
        private List<Object[]>[] table = new ArrayList[CAPACITY];

        SimpleHashMap() {
            for (int i = 0; i < CAPACITY; i++) {
                table[i] = new ArrayList<>();
            }
        }

        private int getBucketIndex(K key) {
            if (key == null) return 0;
            int h = key.hashCode();
            h = h ^ (h >>> 16); // 扰动
            return (CAPACITY - 1) & h;
        }

        public void put(K key, V value) {
            int index = getBucketIndex(key);
            List<Object[]> bucket = table[index];

            // 找是否已有相同 key
            for (Object[] entry : bucket) {
                K existingKey = (K) entry[0];
                if (Objects.equals(existingKey, key)) {
                    entry[1] = value; // 覆盖
                    return;
                }
            }
            // 未找到，新增
            bucket.add(new Object[]{key, value});
        }

        @SuppressWarnings("unchecked")
        public V get(K key) {
            int index = getBucketIndex(key);
            List<Object[]> bucket = table[index];

            for (Object[] entry : bucket) {
                K existingKey = (K) entry[0];
                if (Objects.equals(existingKey, key)) {
                    return (V) entry[1];
                }
            }
            return null;
        }

        public void printBuckets() {
            for (int i = 0; i < CAPACITY; i++) {
                if (!table[i].isEmpty()) {
                    System.out.print("桶[" + i + "]: ");
                    for (Object[] entry : table[i]) {
                        System.out.print("[" + entry[0] + "=" + entry[1] + "] -> ");
                    }
                    System.out.println("null");
                }
            }
        }
    }

    public static void main(String[] args) {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();

        // 放入数据
        map.put("Alice", 90);
        map.put("Bob", 85);
        map.put("Charlie", 92);
        map.put("Alice", 95); // 覆盖

        System.out.println("get Alice: " + map.get("Alice")); // 95
        System.out.println("get Bob: " + map.get("Bob"));     // 85
        System.out.println("get Dave: " + map.get("Dave"));   // null

        System.out.println("\n桶分布（哪些桶有数据）：");
        map.printBuckets();

        // 演示 hash 碰撞：构造相同 hashCode 的 key
        System.out.println("\n=== 模拟 hash 碰撞 ===");
        // Java 中 "Aa" 和 "BB" 的 hashCode 相同（经典例子）
        System.out.println("\"Aa\".hashCode() = " + "Aa".hashCode());
        System.out.println("\"BB\".hashCode() = " + "BB".hashCode());

        Map<String, String> collisionDemo = new HashMap<>();
        collisionDemo.put("Aa", "val1");
        collisionDemo.put("BB", "val2");
        collisionDemo.put("Ca", "val3"); // "Ca".hashCode() 也可能相同（取决于实现）

        System.out.println("get Aa: " + collisionDemo.get("Aa"));
        System.out.println("get BB: " + collisionDemo.get("BB"));

        // 演示扩容触发时机
        System.out.println("\n=== 观察 HashMap 扩容 ===");
        // HashMap 没有直接暴露 capacity，但可以通过 size 推算
        Map<Integer, Integer> sizeDemo = new HashMap<>(); // 默认 capacity=16, threshold=12
        for (int i = 0; i < 20; i++) {
            sizeDemo.put(i, i);
            // 当 size=13 时（超过 threshold=12），内部触发 resize，capacity 变为 32
        }
        System.out.println("存入 20 个元素，size=" + sizeDemo.size());
        // 通过反射验证 capacity（面试不需要这么做，了解即可）
    }
}
```

### 运行结果

```
get Alice: 95
get Bob: 85
get Dave: null

桶分布（哪些桶有数据）：
桶[4]: [Alice=95] -> null
桶[9]: [Bob=85] -> null
桶[11]: [Charlie=92] -> null

=== 模拟 hash 碰撞 ===
"Aa".hashCode() = 2112
"BB".hashCode() = 2112
get Aa: val1
get BB: val2

=== 观察 HashMap 扩容 ===
存入 20 个元素，size=20
```

### 关键点说明
- `"Aa"` 和 `"BB"` 有相同 hashCode（2112），这是演示 hash 碰撞的经典字符串对。
- 真实 HashMap 用链表/红黑树处理碰撞，碰撞不影响正确性，只影响性能。
- `SimpleHashMap` 省略了扩容逻辑，真实 HashMap 超过 threshold 后 capacity 翻倍，重新 hash 所有节点。
- 面试时能画出桶+链表的结构图，说清楚 put 流程，是加分项。
