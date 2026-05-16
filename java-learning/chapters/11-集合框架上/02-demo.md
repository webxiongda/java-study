# 集合框架上 实操 Demo

## Demo 1：List 基本操作与遍历方式对比

### 实操目标
掌握 ArrayList 的增删改查，理解三种遍历方式的区别，以及安全删除元素的写法。

### 示例代码

```java
import java.util.*;

public class ListDemo {
    public static void main(String[] args) {
        // 1. 创建和初始化
        List<String> fruits = new ArrayList<>(Arrays.asList("Apple", "Banana", "Cherry", "Date"));
        System.out.println("初始列表: " + fruits);

        // 2. 增加元素
        fruits.add("Elderberry");           // 尾部添加
        fruits.add(1, "Avocado");          // 指定位置插入
        System.out.println("添加后: " + fruits);

        // 3. 修改元素
        fruits.set(0, "Apricot");
        System.out.println("修改后: " + fruits);

        // 4. 删除元素
        fruits.remove("Date");              // 按值删除
        fruits.remove(0);                   // 按索引删除
        System.out.println("删除后: " + fruits);

        // 5. 查找
        System.out.println("包含 Banana: " + fruits.contains("Banana"));
        System.out.println("Banana 索引: " + fruits.indexOf("Banana"));

        // 6. 三种遍历方式
        System.out.println("\n--- 普通 for 循环 ---");
        for (int i = 0; i < fruits.size(); i++) {
            System.out.println(i + ": " + fruits.get(i));
        }

        System.out.println("\n--- 增强 for 循环 ---");
        for (String fruit : fruits) {
            System.out.println(fruit);
        }

        System.out.println("\n--- Iterator 遍历 ---");
        Iterator<String> it = fruits.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }

        System.out.println("\n--- Java 8 forEach ---");
        fruits.forEach(System.out::println);

        // 7. 安全删除：removeIf（推荐）
        List<String> names = new ArrayList<>(Arrays.asList("Alice", "Bob", "Charlie", "Brian"));
        names.removeIf(name -> name.startsWith("B"));
        System.out.println("\n删除 B 开头后: " + names); // [Alice, Charlie]

        // 8. 排序
        List<Integer> nums = new ArrayList<>(Arrays.asList(5, 2, 8, 1, 9, 3));
        nums.sort(Comparator.naturalOrder());
        System.out.println("升序: " + nums);
        nums.sort(Comparator.reverseOrder());
        System.out.println("降序: " + nums);

        // 9. 截取子列表（视图，修改会影响原列表）
        List<Integer> sub = nums.subList(1, 4);
        System.out.println("子列表(1-3): " + sub);

        // 10. List 与 Array 互转
        String[] arr = fruits.toArray(new String[0]);
        List<String> fromArr = Arrays.asList(arr);
        System.out.println("从数组转回: " + fromArr);
    }
}
```

### 运行结果

```
初始列表: [Apple, Banana, Cherry, Date]
添加后: [Apple, Avocado, Banana, Cherry, Date, Elderberry]
修改后: [Apricot, Avocado, Banana, Cherry, Date, Elderberry]
删除后: [Avocado, Banana, Cherry, Elderberry]
包含 Banana: true
Banana 索引: 1

--- 普通 for 循环 ---
0: Avocado
1: Banana
2: Cherry
3: Elderberry

--- 增强 for 循环 ---
Avocado
Banana
Cherry
Elderberry

--- Iterator 遍历 ---
Avocado
Banana
Cherry
Elderberry

--- Java 8 forEach ---
Avocado
Banana
Cherry
Elderberry

删除 B 开头后: [Alice, Charlie]
升序: [1, 2, 3, 5, 8, 9]
降序: [9, 8, 5, 3, 2, 1]
子列表(1-3): [8, 5, 3]
从数组转回: [Avocado, Banana, Cherry, Elderberry]
```

### 关键点说明
- `remove(int index)` 和 `remove(Object o)` 是两个重载，对 `List<Integer>` 调用 `remove(1)` 是按索引删除，要按值删除需用 `remove(Integer.valueOf(1))`。
- `subList()` 返回的是原列表的视图，对视图的修改会反映到原列表。
- 增强 for 循环中不能调用 `list.remove()`，否则抛 `ConcurrentModificationException`。

---

## Demo 2：Set 去重与 Map 统计词频

### 实操目标
理解 HashSet/TreeSet 的特性差异，并用 HashMap 实现词频统计这个经典面试题。

### 示例代码

```java
import java.util.*;

public class SetMapDemo {
    public static void main(String[] args) {
        // === Part 1: Set 对比 ===
        System.out.println("=== HashSet（无序去重）===");
        Set<String> hashSet = new HashSet<>();
        hashSet.add("banana");
        hashSet.add("apple");
        hashSet.add("cherry");
        hashSet.add("banana"); // 重复，不加入
        System.out.println("HashSet: " + hashSet); // 顺序不固定

        System.out.println("\n=== LinkedHashSet（保留插入顺序）===");
        Set<String> linkedSet = new LinkedHashSet<>();
        linkedSet.add("banana");
        linkedSet.add("apple");
        linkedSet.add("cherry");
        linkedSet.add("banana");
        System.out.println("LinkedHashSet: " + linkedSet); // [banana, apple, cherry]

        System.out.println("\n=== TreeSet（自动排序）===");
        Set<String> treeSet = new TreeSet<>();
        treeSet.add("banana");
        treeSet.add("apple");
        treeSet.add("cherry");
        treeSet.add("banana");
        System.out.println("TreeSet: " + treeSet); // [apple, banana, cherry]

        // Set 集合运算
        Set<Integer> setA = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        Set<Integer> setB = new HashSet<>(Arrays.asList(3, 4, 5, 6, 7));

        // 交集
        Set<Integer> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        System.out.println("\n交集: " + intersection); // [3, 4, 5]

        // 并集
        Set<Integer> union = new HashSet<>(setA);
        union.addAll(setB);
        System.out.println("并集: " + union); // [1, 2, 3, 4, 5, 6, 7]

        // 差集（A - B）
        Set<Integer> diff = new HashSet<>(setA);
        diff.removeAll(setB);
        System.out.println("差集 A-B: " + diff); // [1, 2]

        // === Part 2: HashMap 词频统计 ===
        System.out.println("\n=== HashMap 词频统计 ===");
        String text = "java is great java is fun java spring spring boot";
        String[] words = text.split(" ");

        Map<String, Integer> wordCount = new HashMap<>();
        for (String word : words) {
            // getOrDefault 简化了 if(containsKey) 判断
            wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
        }
        System.out.println("词频统计: " + wordCount);

        // 按词频降序排序
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(wordCount.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        System.out.println("\n按词频降序：");
        entries.forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

        // === Part 3: Map 其他实用操作 ===
        System.out.println("\n=== Map 实用操作 ===");
        Map<String, List<String>> groupMap = new HashMap<>();

        // computeIfAbsent：如果 key 不存在则初始化 value
        String[] students = {"Alice", "Bob", "Charlie", "Anna", "Brian", "Carl"};
        for (String name : students) {
            String firstLetter = String.valueOf(name.charAt(0));
            groupMap.computeIfAbsent(firstLetter, k -> new ArrayList<>()).add(name);
        }
        System.out.println("按首字母分组: " + groupMap);

        // merge：合并统计
        Map<String, Integer> scores = new HashMap<>();
        scores.merge("Alice", 10, Integer::sum); // Alice: 10
        scores.merge("Alice", 5, Integer::sum);  // Alice: 15
        scores.merge("Bob", 8, Integer::sum);    // Bob: 8
        System.out.println("合并后的分数: " + scores);
    }
}
```

### 运行结果

```
=== HashSet（无序去重）===
HashSet: [banana, cherry, apple]  （顺序不固定）

=== LinkedHashSet（保留插入顺序）===
LinkedHashSet: [banana, apple, cherry]

=== TreeSet（自动排序）===
TreeSet: [apple, banana, cherry]

交集: [3, 4, 5]
并集: [1, 2, 3, 4, 5, 6, 7]
差集 A-B: [1, 2]

=== HashMap 词频统计 ===
词频统计: {java=3, is=2, great=1, fun=1, spring=2, boot=1}

按词频降序：
  java: 3
  is: 2
  spring: 2
  great: 1
  fun: 1
  boot: 1

=== Map 实用操作 ===
按首字母分组: {A=[Alice, Anna], B=[Bob, Brian], C=[Charlie, Carl]}
合并后的分数: {Bob=8, Alice=15}
```

### 关键点说明
- `getOrDefault(key, defaultValue)` 比先 `containsKey` 再 `get` 更简洁高效。
- `computeIfAbsent` 非常适合"分组"场景，避免手动判断 key 是否存在再初始化。
- `merge` 用于累加场景，第三个参数是当 key 已存在时的合并函数。
- TreeSet 要对自定义对象排序，需要实现 `Comparable` 或在构造时传 `Comparator`。

---

## Demo 3：Collections 工具类与 Queue 实战

### 实操目标
掌握 Collections 常用工具方法，并用 Queue/Deque 模拟任务队列和栈操作。

### 示例代码

```java
import java.util.*;

public class CollectionsQueueDemo {
    public static void main(String[] args) {
        // === Part 1: Collections 工具类 ===
        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6, 5, 3));

        System.out.println("原始: " + nums);
        Collections.sort(nums);
        System.out.println("排序后: " + nums);

        int pos = Collections.binarySearch(nums, 5); // 必须先排序
        System.out.println("5 的索引: " + pos);

        System.out.println("最大值: " + Collections.max(nums));
        System.out.println("最小值: " + Collections.min(nums));
        System.out.println("5 出现次数: " + Collections.frequency(nums, 5));

        Collections.reverse(nums);
        System.out.println("反转: " + nums);

        Collections.fill(new ArrayList<>(Arrays.asList(1, 2, 3)), 0);

        // 不可修改视图
        List<Integer> immutable = Collections.unmodifiableList(nums);
        try {
            immutable.add(99);
        } catch (UnsupportedOperationException e) {
            System.out.println("不可修改列表：" + e.getClass().getSimpleName());
        }

        // === Part 2: Queue 模拟任务队列 ===
        System.out.println("\n=== 任务队列模拟 ===");
        Queue<String> taskQueue = new LinkedList<>();
        taskQueue.offer("发送邮件");
        taskQueue.offer("生成报表");
        taskQueue.offer("备份数据");

        System.out.println("队列大小: " + taskQueue.size());
        System.out.println("队首任务(不移除): " + taskQueue.peek());

        while (!taskQueue.isEmpty()) {
            String task = taskQueue.poll();
            System.out.println("执行任务: " + task);
        }

        // === Part 3: Deque 模拟浏览器历史（后退功能）===
        System.out.println("\n=== 浏览器历史模拟 ===");
        Deque<String> history = new ArrayDeque<>();
        String[] pages = {"home", "products", "detail", "cart", "checkout"};

        for (String page : pages) {
            history.push(page); // 压入栈顶
            System.out.println("访问: " + page);
        }

        System.out.println("\n当前页面: " + history.peek());
        System.out.println("点击后退:");
        for (int i = 0; i < 3; i++) {
            System.out.println("  返回到: " + history.pop());
        }
        System.out.println("现在在: " + history.peek());

        // === Part 4: PriorityQueue 优先级队列 ===
        System.out.println("\n=== 优先级队列（最小堆）===");
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        pq.offer(5);
        pq.offer(1);
        pq.offer(3);
        pq.offer(2);
        pq.offer(4);

        System.out.print("出队顺序（最小优先）: ");
        while (!pq.isEmpty()) {
            System.out.print(pq.poll() + " ");
        }
        System.out.println();

        // 自定义优先级：按字符串长度
        PriorityQueue<String> strPQ = new PriorityQueue<>(Comparator.comparingInt(String::length));
        strPQ.offer("Java");
        strPQ.offer("Go");
        strPQ.offer("Python");
        strPQ.offer("C");

        System.out.print("按长度出队: ");
        while (!strPQ.isEmpty()) {
            System.out.print(strPQ.poll() + " ");
        }
    }
}
```

### 运行结果

```
原始: [3, 1, 4, 1, 5, 9, 2, 6, 5, 3]
排序后: [1, 1, 2, 3, 3, 4, 5, 5, 6, 9]
5 的索引: 6
最大值: 9
最小值: 1
5 出现次数: 2
反转: [9, 6, 5, 5, 4, 3, 3, 2, 1, 1]
不可修改列表：UnsupportedOperationException

=== 任务队列模拟 ===
队列大小: 3
队首任务(不移除): 发送邮件
执行任务: 发送邮件
执行任务: 生成报表
执行任务: 备份数据

=== 浏览器历史模拟 ===
访问: home
访问: products
访问: detail
访问: cart
访问: checkout

当前页面: checkout
点击后退:
  返回到: checkout
  返回到: cart
  返回到: detail
现在在: products

=== 优先级队列（最小堆）===
出队顺序（最小优先）: 1 2 3 4 5

按长度出队: C Go Java Python
```

### 关键点说明
- `Queue` 推荐用 `offer/poll/peek`，不用 `add/remove/element`，因为前者失败返回 false/null，后者抛异常。
- `ArrayDeque` 作为栈比 `Stack` 类更推荐（Stack 继承 Vector，有同步开销）。
- `PriorityQueue` 默认是最小堆，传 `Comparator.reverseOrder()` 变成最大堆。
- `Collections.binarySearch()` 要求列表已排序，否则结果不可预期。
