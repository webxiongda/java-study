# Chapter 09 - 枚举与泛型入门：核心理论

## 1. 枚举（enum）基本语法

```java
// 最简单的枚举
public enum Direction {
    NORTH, SOUTH, EAST, WEST
}

// 使用
Direction d = Direction.NORTH;
System.out.println(d);        // 输出：NORTH
System.out.println(d.name()); // 输出：NORTH
System.out.println(d.ordinal()); // 输出：0（从0开始的序号）
```

---

## 2. enum 的本质：它是一个类

Java 的 `enum` 在编译后实际上是一个继承自 `java.lang.Enum` 的 `final` 类，每个枚举常量是该类的一个 `public static final` 实例。

```java
// 你写的
public enum Status { ACTIVE, INACTIVE }

// 编译器生成的等价代码（简化）
public final class Status extends Enum<Status> {
    public static final Status ACTIVE   = new Status("ACTIVE", 0);
    public static final Status INACTIVE = new Status("INACTIVE", 1);

    private Status(String name, int ordinal) {
        super(name, ordinal);
    }
    // values()、valueOf() 等方法由编译器自动生成
}
```

**关键推论**：
- `enum` **不能被实例化**（`new Status()` 编译报错）
- `enum` **不能被继承**（隐式 final），也不能继承其他类（已经继承了 Enum）
- `enum` **可以实现接口**
- `enum` **可以有字段、方法、构造器**

---

## 3. enum 的字段、方法和构造器

枚举可以携带数据，非常适合表示有额外属性的常量：

```java
public enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS  (4.869e+24, 6.0518e6),
    EARTH  (5.976e+24, 6.37814e6);

    private final double mass;    // 质量（千克）
    private final double radius;  // 半径（米）

    // 枚举构造器必须是 private（或包私有），不能是 public
    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
    }

    // 普通方法
    public double surfaceGravity() {
        final double G = 6.67300E-11;
        return G * mass / (radius * radius);
    }

    public double getMass() { return mass; }
    public double getRadius() { return radius; }
}
```

---

## 4. 枚举在 switch 中的使用

```java
public enum OrderStatus { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED }

// Java 传统 switch
OrderStatus status = OrderStatus.PAID;
switch (status) {
    case CREATED:
        System.out.println("订单已创建，等待付款");
        break;
    case PAID:
        System.out.println("已付款，准备发货");
        break;
    case SHIPPED:
        System.out.println("已发货，等待收货");
        break;
    default:
        System.out.println("其他状态：" + status);
}

// Java 14+ switch 表达式（更简洁）
String message = switch (status) {
    case CREATED   -> "订单已创建，等待付款";
    case PAID      -> "已付款，准备发货";
    case SHIPPED   -> "已发货，等待收货";
    case DELIVERED -> "已收货";
    case CANCELLED -> "已取消";
};
```

---

## 5. enum 的内置方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `name()` | 返回枚举常量名称（字符串） | `Direction.NORTH.name()` → `"NORTH"` |
| `ordinal()` | 返回枚举常量的序号（从0开始） | `Direction.NORTH.ordinal()` → `0` |
| `toString()` | 默认与 name() 相同，可重写 | `Direction.NORTH.toString()` → `"NORTH"` |
| `values()` | 静态方法，返回所有枚举常量数组 | `Direction.values()` → `[NORTH, SOUTH, EAST, WEST]` |
| `valueOf(String)` | 静态方法，根据名称返回枚举常量 | `Direction.valueOf("NORTH")` → `Direction.NORTH` |
| `compareTo(E)` | 按 ordinal 排序比较 | |
| `equals(Object)` | 枚举可以直接用 `==` 比较（推荐） | `status == OrderStatus.PAID` |

**注意**：枚举比较用 `==`，不用 `equals()`（虽然 equals 也能工作，但 `==` 更直观且编译器可以检查类型）。

---

## 6. 泛型类

泛型允许类和方法在定义时不指定具体类型，在使用时再指定，实现代码复用并保证类型安全。

```java
// 不用泛型：只能存 Object，取出时需要强转，不安全
List list = new ArrayList();
list.add("hello");
String s = (String) list.get(0);  // 需要强转，运行时可能 ClassCastException

// 使用泛型：指定类型，编译时检查，无需强转
List<String> list2 = new ArrayList<>();
list2.add("hello");
String s2 = list2.get(0);  // 直接取，类型安全

// 定义泛型类
public class Box<T> {          // T 是类型参数，可以是任意名称，惯例：T=Type, E=Element, K=Key, V=Value
    private T value;

    public Box(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}

// 使用泛型类
Box<String>  stringBox  = new Box<>("Hello");
Box<Integer> integerBox = new Box<>(42);
Box<Double>  doubleBox  = new Box<>(3.14);
```

---

## 7. 泛型方法

泛型方法可以独立于类的泛型参数声明自己的类型参数：

```java
public class Utils {

    // 泛型方法：<T> 声明在返回类型之前
    public static <T> T identity(T value) {
        return value;
    }

    // 泛型方法：交换数组中两个元素
    public static <T> void swap(T[] arr, int i, int j) {
        T temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    // 泛型方法：找列表中的最大值（T 必须实现 Comparable）
    public static <T extends Comparable<T>> T max(List<T> list) {
        if (list.isEmpty()) throw new IllegalArgumentException("列表为空");
        T max = list.get(0);
        for (T item : list) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }
}

// 调用（大多数情况下类型可以由编译器推断）
String result = Utils.identity("hello");
Integer num   = Utils.identity(42);

String[] names = {"banana", "apple", "cherry"};
Utils.swap(names, 0, 1);  // ["apple", "banana", "cherry"]
```

---

## 8. 通配符 `?`

当方法只需要"读取"泛型容器中的内容，而不关心具体类型时，使用通配符。

### 无界通配符 `?`

```java
// 接受任意类型的 List，但只能读取（读出的是 Object）
public static void printList(List<?> list) {
    for (Object item : list) {
        System.out.println(item);
    }
}

printList(new ArrayList<String>());   // OK
printList(new ArrayList<Integer>());  // OK
printList(new ArrayList<Double>());   // OK
```

### 上界通配符 `? extends T`（读数据，PECS 中的 Producer）

```java
// 只接受 Number 及其子类（Integer、Double 等）的 List
// 可以安全读取（读出的是 Number），但不能添加元素
public static double sumList(List<? extends Number> list) {
    double sum = 0;
    for (Number n : list) {
        sum += n.doubleValue();  // 可以读，读出的是 Number
    }
    // list.add(1.0);  // 编译错误！不知道实际类型是 List<Integer> 还是 List<Double>
    return sum;
}

sumList(new ArrayList<Integer>());  // OK
sumList(new ArrayList<Double>());   // OK
```

### 下界通配符 `? super T`（写数据，PECS 中的 Consumer）

```java
// 只接受 Integer 或 Integer 父类（Number、Object）的 List
// 可以安全添加 Integer 元素，但读取时只能得到 Object
public static void addNumbers(List<? super Integer> list) {
    list.add(1);    // 可以写 Integer（因为 list 能容纳 Integer）
    list.add(2);
    list.add(3);
    // Integer x = list.get(0);  // 编译错误！只能 Object x = list.get(0)
}

addNumbers(new ArrayList<Integer>());  // OK
addNumbers(new ArrayList<Number>());   // OK
addNumbers(new ArrayList<Object>());   // OK
```

### PECS 原则记忆法

> **P**roducer **E**xtends, **C**onsumer **S**uper

- 如果容器是"生产者"（你从中**读取**数据）→ 用 `? extends T`
- 如果容器是"消费者"（你向其**写入**数据）→ 用 `? super T`
- 如果既读又写 → 用具体类型 `T`，不用通配符

---

## 9. 类型擦除（Type Erasure）

Java 泛型是"伪泛型"——泛型信息只在编译期存在，编译后的字节码中泛型类型信息被擦除，替换为原始类型（通常是 `Object` 或类型上界）。

```java
// 源码
List<String>  listString  = new ArrayList<>();
List<Integer> listInteger = new ArrayList<>();

// 编译后（字节码层面，等价于）
List listString  = new ArrayList();
List listInteger = new ArrayList();

// 运行时无法区分两者
System.out.println(listString.getClass() == listInteger.getClass()); // true！
System.out.println(listString instanceof List);    // true
// System.out.println(listString instanceof List<String>);  // 编译错误！
```

**类型擦除的实际影响**：
1. 运行时无法获取泛型类型参数（`T.class` 非法）
2. 不能创建泛型类型的数组（`new T[10]` 非法）
3. 不能用 `instanceof` 检查泛型类型（`obj instanceof List<String>` 非法）

---

## 常见坑

**坑 1：不能直接创建泛型数组**

```java
public class Container<T> {
    // 错误：不能直接创建泛型数组
    private T[] items = new T[10];  // 编译错误！

    // 正确方案一：用 Object 数组，取出时强转
    private Object[] items = new Object[10];
    public T get(int i) {
        return (T) items[i];  // 有编译警告，但可以用
    }

    // 正确方案二：改用 List
    private List<T> items = new ArrayList<>();
}
```

**坑 2：类型擦除导致方法重载冲突**

```java
public class Processor {
    // 编译错误！擦除后两个方法签名相同，都是 process(List)
    public void process(List<String>  list) { ... }
    public void process(List<Integer> list) { ... }
}
```

**坑 3：enum 不能用 new 实例化**

```java
// 编译错误！
Direction d = new Direction();  // enum 的构造器是 private，且不允许外部实例化

// 正确：直接使用枚举常量
Direction d = Direction.NORTH;

// 或通过 valueOf 从字符串获取
Direction d2 = Direction.valueOf("NORTH");
// 注意：valueOf 对不存在的名称抛 IllegalArgumentException
```

---

## 面试高频问题

**Q1：enum 和常量类（定义一堆 public static final 的类）有什么区别？**

答：
1. **类型安全**：enum 是独立类型，编译器能检查；常量类的值只是 int/String，可以传入任意值。
2. **switch 支持**：enum 可以直接在 switch 中使用，常量类的值（如 int）也能用但没有类型检查。
3. **可以有行为**：enum 可以定义方法，常量类的常量是纯数据。
4. **单例保证**：每个 enum 常量是全局唯一的实例，可以安全用 `==` 比较；常量类的 String 值要用 `equals()`。
5. **序列化安全**：enum 序列化/反序列化保证同一个实例；普通对象反序列化会创建新实例。

**Q2：什么是类型擦除？它带来哪些限制？**

答：类型擦除是 Java 泛型的实现机制——编译器在生成字节码时移除所有泛型类型信息，将 `List<String>` 和 `List<Integer>` 都变成 `List`，泛型类型参数替换为 `Object`（或类型上界）。带来的限制：1）运行时无法判断泛型类型（`instanceof List<String>` 非法）；2）不能创建泛型类型数组（`new T[]` 非法）；3）不能用泛型类型做 `catch`；4）泛型参数不同但擦除后相同的方法不能重载。

**Q3：`? extends T` 和 `? super T` 怎么选？**

答：遵循 PECS 原则：
- **Producer Extends**：如果该集合是数据的"生产者"（你从中读取数据），用 `? extends T`。例如 `List<? extends Number>` 用于求和，你只读不写。
- **Consumer Super**：如果该集合是数据的"消费者"（你往里写数据），用 `? super T`。例如 `List<? super Integer>` 用于填充 Integer，你只写不读。
- 记忆技巧：extends 限定了上界，读出来至少是 T 类型；super 限定了下界，T 类型可以安全写入。

**Q4：为什么不能创建泛型数组？**

答：因为类型擦除。数组在运行时会检查元素类型（`ArrayStoreException`），而泛型的类型信息在运行时已被擦除，无法做这个检查。如果允许创建泛型数组，可能在编译期骗过类型系统，在运行时产生难以排查的 ClassCastException。Java 为了保持类型安全，直接禁止创建泛型数组。替代方案是使用 `List<T>`。

**Q5：枚举能实现接口吗？能继承类吗？**

答：**能实现接口，不能继承类**。枚举隐式继承 `java.lang.Enum`，由于 Java 不支持多重继承，所以不能再继承其他类。但枚举可以实现接口，并且每个枚举常量可以有不同的接口实现（通过匿名类体覆盖方法）：

```java
public interface Describable {
    String describe();
}

public enum Season implements Describable {
    SPRING {
        @Override
        public String describe() { return "万物复苏"; }
    },
    SUMMER {
        @Override
        public String describe() { return "骄阳似火"; }
    };
}
```
