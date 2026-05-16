# Chapter 09 - 代码演示

## Demo 1：OrderStatus 枚举

完整的订单状态枚举：含状态码、描述、构造器、自定义方法，在 switch 中使用。

```java
/**
 * 订单状态枚举
 * 演示：枚举字段、构造器、方法、switch 用法
 */
public enum OrderStatus {

    CREATED  (1001, "待付款"),
    PAID     (1002, "已付款"),
    SHIPPED  (1003, "已发货"),
    DELIVERED(1004, "已签收"),
    CANCELLED(1005, "已取消"),
    REFUNDING(1006, "退款中");

    // 枚举字段（final，不可变）
    private final int code;
    private final String description;

    // 枚举构造器：必须是 private（或省略，默认也是 private）
    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    // Getter
    public int getCode() { return code; }
    public String getDescription() { return description; }

    /**
     * 判断是否是终态（无法再流转的状态）
     */
    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /**
     * 根据状态码查找枚举（valueOf 只能按名称查，这里按 code 查）
     * @throws IllegalArgumentException 如果 code 不对应任何状态
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的订单状态码：" + code);
    }

    @Override
    public String toString() {
        return String.format("OrderStatus{code=%d, name='%s', description='%s'}",
                code, name(), description);
    }
}

// ===== 主程序演示 =====

public class OrderStatusDemo {

    public static void main(String[] args) {
        // 基本使用
        OrderStatus status = OrderStatus.PAID;
        System.out.println("状态名称：" + status.name());           // PAID
        System.out.println("状态码：" + status.getCode());          // 1002
        System.out.println("状态描述：" + status.getDescription()); // 已付款
        System.out.println("序号：" + status.ordinal());            // 1
        System.out.println("是否终态：" + status.isFinal());        // false

        System.out.println("\n=== 遍历所有状态 ===");
        for (OrderStatus s : OrderStatus.values()) {
            System.out.println(s);
        }

        System.out.println("\n=== 根据 code 查找 ===");
        OrderStatus found = OrderStatus.fromCode(1003);
        System.out.println("找到：" + found.getDescription());  // 已发货

        System.out.println("\n=== switch 用法 ===");
        processOrder(OrderStatus.SHIPPED);
        processOrder(OrderStatus.DELIVERED);
        processOrder(OrderStatus.CANCELLED);

        // 枚举比较：用 == 而不是 equals
        System.out.println("\n=== 枚举比较 ===");
        System.out.println(status == OrderStatus.PAID);    // true
        System.out.println(status.equals(OrderStatus.PAID)); // 也是 true，但推荐用 ==

        // valueOf 使用
        OrderStatus fromString = OrderStatus.valueOf("SHIPPED");
        System.out.println("valueOf 结果：" + fromString);
    }

    private static void processOrder(OrderStatus status) {
        String action = switch (status) {
            case CREATED   -> "提醒用户付款";
            case PAID      -> "通知仓库备货";
            case SHIPPED   -> "发送物流通知短信";
            case DELIVERED -> "发送好评提醒，订单完成";
            case CANCELLED -> "退款处理";
            case REFUNDING -> "等待退款审批";
        };
        System.out.printf("[%s] %s%n", status.getDescription(), action);
    }
}
```

**输出示例**：
```
状态名称：PAID
状态码：1002
状态描述：已付款
序号：1
是否终态：false

=== 遍历所有状态 ===
OrderStatus{code=1001, name='CREATED', description='待付款'}
OrderStatus{code=1002, name='PAID', description='已付款'}
...

=== switch 用法 ===
[已发货] 发送物流通知短信
[已签收] 发送好评提醒，订单完成
[已取消] 退款处理
```

---

## Demo 2：泛型 Pair 类和泛型方法

```java
/**
 * 泛型类：表示一对值（键-值对）
 * 演示：多类型参数、泛型方法
 *
 * @param <K> 第一个值的类型（Key）
 * @param <V> 第二个值的类型（Value）
 */
public class Pair<K, V> {

    private K first;
    private V second;

    public Pair(K first, V second) {
        this.first = first;
        this.second = second;
    }

    public K getFirst()  { return first; }
    public V getSecond() { return second; }

    public void setFirst(K first)   { this.first = first; }
    public void setSecond(V second) { this.second = second; }

    /**
     * 返回一个"翻转"的 Pair（把 first 和 second 对调）
     * 注意这是实例方法，使用类的泛型参数 K、V
     */
    public Pair<V, K> swap() {
        return new Pair<>(second, first);
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    // ===== 静态泛型工厂方法 =====

    /**
     * 静态泛型方法：创建 Pair，编译器可推断类型
     * 比构造器更方便：Pair.of("name", 100) 比 new Pair<String, Integer>("name", 100) 简洁
     */
    public static <K, V> Pair<K, V> of(K first, V second) {
        return new Pair<>(first, second);
    }
}

/**
 * 独立的泛型工具方法演示
 */
public class GenericUtils {

    /**
     * 泛型方法：找数组最大值
     * <T extends Comparable<T>> 表示 T 必须实现 Comparable 接口（有比较能力）
     */
    public static <T extends Comparable<T>> T max(T[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("数组不能为空");
        }
        T max = arr[0];
        for (T item : arr) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }

    /**
     * 泛型方法：将可变参数打包成 List
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) {
            list.add(item);
        }
        return list;
    }

    /**
     * 泛型方法：交换 Pair 的两个元素（返回新的 Pair）
     */
    public static <A, B> Pair<B, A> swapPair(Pair<A, B> pair) {
        return Pair.of(pair.getSecond(), pair.getFirst());
    }
}

// ===== 主程序演示 =====

public class PairDemo {

    public static void main(String[] args) {
        // 创建不同类型的 Pair
        Pair<String, Integer> nameAge = Pair.of("Alice", 28);
        Pair<String, Double>  product = Pair.of("iPhone", 7999.0);
        Pair<Integer, Boolean> flag   = Pair.of(200, true);

        System.out.println("nameAge = " + nameAge);  // (Alice, 28)
        System.out.println("product = " + product);  // (iPhone, 7999.0)
        System.out.println("flag    = " + flag);      // (200, true)

        // 访问元素（类型安全，无需强转）
        String  name = nameAge.getFirst();
        Integer age  = nameAge.getSecond();
        System.out.println(name + " 今年 " + age + " 岁");

        // 翻转
        Pair<Integer, String> swapped = nameAge.swap();
        System.out.println("翻转后：" + swapped);  // (28, Alice)

        // 泛型静态方法
        System.out.println("\n=== 泛型方法演示 ===");
        Integer[] nums  = {3, 1, 4, 1, 5, 9, 2, 6};
        String[]  words = {"banana", "apple", "cherry"};
        System.out.println("最大数字：" + GenericUtils.max(nums));   // 9
        System.out.println("最大字符串：" + GenericUtils.max(words)); // cherry（字典序）

        List<String> list = GenericUtils.listOf("a", "b", "c");
        System.out.println("listOf 结果：" + list);  // [a, b, c]
    }
}
```

---

## Demo 3：通配符使用场景对比

```java
import java.util.*;

public class WildcardDemo {

    public static void main(String[] args) {
        List<Integer> integers = Arrays.asList(1, 2, 3, 4, 5);
        List<Double>  doubles  = Arrays.asList(1.1, 2.2, 3.3);
        List<String>  strings  = Arrays.asList("a", "b", "c");

        // ===========================================
        // 无界通配符 List<?>
        // ===========================================
        System.out.println("=== 无界通配符 List<?> ===");
        printAll(integers);  // OK
        printAll(doubles);   // OK
        printAll(strings);   // OK

        // ===========================================
        // 上界通配符 ? extends Number（读场景）
        // ===========================================
        System.out.println("\n=== 上界通配符 ? extends Number（求和）===");
        System.out.println("整数列表之和：" + sum(integers));  // 15.0
        System.out.println("浮点列表之和：" + sum(doubles));   // 6.6
        // sum(strings);  // 编译错误！String 不是 Number 的子类

        // ===========================================
        // 下界通配符 ? super Integer（写场景）
        // ===========================================
        System.out.println("\n=== 下界通配符 ? super Integer（填充数据）===");

        List<Integer> intList    = new ArrayList<>();
        List<Number>  numList    = new ArrayList<>();
        List<Object>  objectList = new ArrayList<>();

        fill(intList);     // OK：List<Integer> super Integer 成立
        fill(numList);     // OK：List<Number>  super Integer 成立
        fill(objectList);  // OK：List<Object>  super Integer 成立

        System.out.println("intList：" + intList);     // [1, 2, 3]
        System.out.println("numList：" + numList);     // [1, 2, 3]
        System.out.println("objectList：" + objectList); // [1, 2, 3]

        // ===========================================
        // 对比：extends 不能写，super 读到的是 Object
        // ===========================================
        System.out.println("\n=== 关键区别演示 ===");
        demonstrateDifference();
    }

    // ===== 方法定义 =====

    // 无界通配符：接受任意 List，只读（读出来是 Object）
    public static void printAll(List<?> list) {
        for (Object item : list) {
            System.out.print(item + " ");
        }
        System.out.println();
        // list.add("something");  // 编译错误！不知道实际类型，不能添加
    }

    // 上界通配符：接受 Number 及子类的 List，可读（读出来是 Number），不能写
    public static double sum(List<? extends Number> list) {
        double total = 0;
        for (Number n : list) {      // 读出的元素类型是 Number
            total += n.doubleValue();
        }
        // list.add(1.0);  // 编译错误！
        // 原因：如果 list 实际是 List<Integer>，你往里加 Double 就炸了
        // 编译器无法知道实际类型，所以禁止写入（null 除外）
        return total;
    }

    // 下界通配符：接受 Integer 或父类的 List，可写 Integer，读出的是 Object
    public static void fill(List<? super Integer> list) {
        list.add(1);  // 可以写 Integer
        list.add(2);
        list.add(3);
        // Integer x = list.get(0);  // 编译错误！只知道是 Integer 或父类，不确定具体类型
        Object x = list.get(0);       // 只能用 Object 接收
    }

    // 演示 extends 和 super 的核心区别
    public static void demonstrateDifference() {
        List<Integer>  intList = new ArrayList<>(Arrays.asList(1, 2, 3));

        // extends：读 OK，写 NO
        List<? extends Number> producer = intList;
        Number n = producer.get(0);  // 读：OK，得到 Number
        // producer.add(4);          // 写：编译错误！

        // super：写 OK，读得 Object
        List<? super Integer> consumer = intList;
        consumer.add(4);             // 写：OK
        Object obj = consumer.get(0); // 读：只能得到 Object
        // Integer i = consumer.get(0); // 编译错误！

        System.out.println("extends 读到的：" + n);    // 1
        System.out.println("super  写入后：" + intList); // [1, 2, 3, 4]
    }
}
```

**通配符选择总结**：

```
你要做什么？          用什么？          记忆
─────────────────────────────────────────────
只读集合元素           ? extends T    Producer Extends
只写集合元素           ? super T      Consumer Super
既读又写              具体类型 T      两者都需要，不用通配符
不关心元素类型          ?             完全不关心
```
