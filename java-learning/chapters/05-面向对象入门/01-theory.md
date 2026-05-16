# Chapter 05 - 面向对象入门 | 理论知识

---

## 一、类和对象的关系

**类（Class）** 是对象的蓝图/模板，描述了同一类事物的属性（字段）和行为（方法）。  
**对象（Object）** 是类的实例，是真正存在于内存中的个体。

```
类 ──── 就像 ────> 汽车设计图纸
对象 ── 就像 ────> 一辆具体的汽车
```

```java
// 定义类：描述"学生"这个模板
public class Student {
    String name;   // 属性（字段）
    int age;

    void study() { // 行为（方法）
        System.out.println(name + " is studying");
    }
}

// 使用类创建对象（实例化）
Student s1 = new Student();   // new 关键字在堆上创建对象
s1.name = "Alice";
s1.age = 20;
s1.study();   // Alice is studying
```

---

## 二、构造器

构造器（Constructor）是与类名相同、没有返回值类型的特殊方法，在 `new` 对象时自动调用，用于初始化对象的状态。

### 2.1 无参构造器

```java
public class Person {
    String name;
    int age;

    // 无参构造器：不接受任何参数
    public Person() {
        this.name = "未知";
        this.age = 0;
    }
}
```

**注意：** 如果你不写任何构造器，Java 编译器会自动添加一个什么都不做的默认无参构造器。但一旦你手动定义了任何构造器（包括有参构造器），编译器就不再自动添加无参构造器。

### 2.2 有参构造器

```java
public class Person {
    String name;
    int age;

    public Person(String name, int age) {
        this.name = name;   // this.name 是字段，name 是参数
        this.age = age;
    }
}
```

### 2.3 构造器重载

一个类可以有多个构造器，参数列表不同即可（与方法重载规则相同）。

```java
public class Person {
    String name;
    int age;
    String email;

    // 无参构造器
    public Person() {
        this("未知", 0);  // 调用双参构造器，this() 必须是第一行
    }

    // 双参构造器
    public Person(String name, int age) {
        this(name, age, "");  // 调用三参构造器
    }

    // 全参构造器
    public Person(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }
}
```

---

## 三、封装（Encapsulation）

封装是面向对象的核心原则之一：**将数据（字段）隐藏在类内部，通过公开的方法（getter/setter）提供受控的访问接口。**

好处：
- 数据保护：防止外部代码直接修改字段为非法值
- 灵活性：可以在 setter 中添加验证逻辑，而不影响外部调用代码
- 可维护性：内部实现改变时，外部接口不需要变

```java
public class BankAccount {
    // private 字段：外部无法直接访问
    private double balance;
    private String accountId;

    public BankAccount(String accountId, double initialBalance) {
        this.accountId = accountId;
        this.balance = initialBalance >= 0 ? initialBalance : 0;
    }

    // Getter：只读访问
    public double getBalance() {
        return balance;
    }

    public String getAccountId() {
        return accountId;
    }

    // 没有 setBalance：不允许直接修改余额，必须通过 deposit/withdraw 方法
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("存款金额必须大于 0");
        balance += amount;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) return false;
        balance -= amount;
        return true;
    }
}
```

---

## 四、this 关键字

`this` 是当前对象的引用，有三种主要用途：

### 4.1 区分字段和参数（同名时消除歧义）

```java
public void setName(String name) {
    this.name = name;  // this.name 是字段，name 是参数
}
```

### 4.2 在方法中传递当前对象

```java
public Person getThis() {
    return this;   // 返回当前对象本身
}
```

### 4.3 在构造器中调用另一个构造器（`this()`）

```java
public Person() {
    this("默认", 0);  // 调用有参构造器，必须是构造器第一行
}
```

---

## 五、static 成员

`static` 修饰的成员属于**类本身**，而不是某个具体对象，所有对象共享同一份 static 成员。

### 5.1 静态字段

```java
public class Counter {
    private static int count = 0;  // 所有对象共享的计数器
    private int id;

    public Counter() {
        count++;          // 每创建一个对象，类级别计数器 +1
        this.id = count;
    }

    public static int getCount() { return count; }
    public int getId() { return id; }
}

Counter c1 = new Counter();   // count = 1，c1.id = 1
Counter c2 = new Counter();   // count = 2，c2.id = 2
System.out.println(Counter.getCount());  // 2（用类名访问 static 方法）
```

### 5.2 静态方法

```java
public class MathUtils {
    // static 方法不依赖对象状态，可直接通过类名调用
    public static double circleArea(double radius) {
        return Math.PI * radius * radius;
    }
}

double area = MathUtils.circleArea(5.0);  // 无需创建对象
```

**static 方法的限制：**
- 不能直接访问实例字段（需要先创建对象）
- 不能使用 `this` 关键字

### 5.3 静态代码块 vs 实例代码块

```java
public class InitDemo {
    static int staticVar;
    int instanceVar;

    // 静态代码块：类加载时执行一次，最先执行
    static {
        staticVar = 10;
        System.out.println("静态代码块执行");
    }

    // 实例代码块：每次 new 对象时执行，在构造器之前执行
    {
        instanceVar = 100;
        System.out.println("实例代码块执行");
    }

    public InitDemo() {
        System.out.println("构造器执行");
    }
}
// 执行 new InitDemo() 时，顺序：
// 1. 静态代码块（首次加载类时）
// 2. 实例代码块
// 3. 构造器
```

---

## 六、对象内存模型

理解 Java 中对象的内存布局对于理解引用传递、垃圾回收至关重要。

```
栈（Stack）                    堆（Heap）
┌─────────────┐               ┌─────────────────┐
│ main 栈帧   │               │  Person 对象      │
│  p1 ───────►│──────────────►│  name: "Alice"   │
│  p2 ───────►│──┐            │  age: 20         │
└─────────────┘  │            └─────────────────┘
                 │            ┌─────────────────┐
                 └───────────►│  Person 对象      │
                              │  name: "Bob"     │
                              │  age: 25         │
                              └─────────────────┘
```

- **栈（Stack）**：存储基本类型变量的值，以及引用类型变量的引用（地址）。每个方法调用有自己的栈帧，方法结束后栈帧销毁。
- **堆（Heap）**：存储所有 `new` 出来的对象。对象不再被引用后，由垃圾回收器（GC）回收。
- **方法区/元空间**：存储类的字节码、static 变量、字符串常量池。

---

## 七、常见坑

**坑 1：忘记写无参构造器导致编译错误**

```java
public class Person {
    String name;
    // 定义了有参构造器后，编译器不再自动添加无参构造器
    public Person(String name) {
        this.name = name;
    }
}

// 调用处想用无参构造器：
Person p = new Person();  // 编译错误！没有无参构造器
```

**解决方案：** 手动添加无参构造器，或使用 `Person p = new Person("default")` 调用有参版本。

**坑 2：static 方法中访问实例变量**

```java
public class MyClass {
    private int value = 10;

    // 错误：static 方法没有 this，无法访问实例变量
    public static void printValue() {
        System.out.println(value);  // 编译错误！
    }

    // 正确：实例方法可以访问实例变量
    public void printValue() {
        System.out.println(this.value);
    }
}
```

**坑 3：this() 调用不在构造器第一行**

```java
public Person(String name) {
    System.out.println("creating person");
    this("default", 0);  // 编译错误！this() 必须是构造器的第一条语句
}
```

---

## 八、面试高频问题

**Q1：构造器可以被继承吗？可以被重写（Override）吗？**

答：构造器不能被继承，也不能被重写。子类可以通过 `super()` 调用父类的构造器，但这不是继承。每个类都必须有自己的构造器，子类的构造器中如果没有显式调用 `super()`，编译器会自动在第一行插入 `super()`（调用父类的无参构造器）。

**Q2：static 变量和 instance 变量（实例变量）的区别？**

答：static 变量（类变量）属于类，存储在方法区，所有对象共享同一份，通过类名访问（如 `Person.count`）；实例变量属于对象，存储在堆中，每个对象有独立的副本，通过对象引用访问（如 `p.name`）。static 变量在类加载时初始化，实例变量在 new 对象时初始化。

**Q3：this 和 super 的区别？**

答：`this` 是当前对象的引用，用于访问当前类的成员或调用当前类的其他构造器（`this()`）；`super` 是父类部分的引用，用于访问父类的成员或调用父类的构造器（`super()`）。两者都只能在非 static 方法或构造器中使用，且 `this()` 和 `super()` 都必须是构造器的第一条语句（因此不能同时出现）。

**Q4：封装的意义是什么？private 字段一定需要 getter/setter 吗？**

答：封装的核心是"隐藏实现细节，暴露必要接口"。private 字段不一定需要同时提供 getter 和 setter：如果字段只读，只提供 getter；如果字段的修改需要特殊逻辑（如余额修改必须通过 deposit/withdraw），则不提供 setter 而是提供业务方法。盲目为所有字段生成 getter/setter 实际上破坏了封装性。

**Q5：对象在内存中是怎么存储的？**

答：用 `new` 创建的对象存储在堆（Heap）上，包含对象的所有实例字段。对象的引用（地址）存储在栈（Stack）上的局部变量或另一个对象的字段中。static 字段存储在方法区（Java 8 以前）或堆中（Java 8+ 的元空间变化）。当对象没有任何引用指向它时，垃圾回收器（GC）会在某个时机回收该对象占用的堆内存。
