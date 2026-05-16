# 继承与多态 理论文档

## 核心概念

### 1. 继承（extends）

继承是 Java OOP 三大特性之一，允许子类复用父类的字段和方法，同时可以扩展或重写。

```java
public class Animal {
    String name;
    int age;

    public void eat() {
        System.out.println(name + " is eating");
    }
}

public class Dog extends Animal {
    String breed;

    public void bark() {
        System.out.println(name + " barks!"); // 可直接访问父类字段
    }
}
```

Java **只支持单继承**（一个类只能 extends 一个父类），但可以通过接口实现多继承的效果。

### 2. super 关键字

`super` 用于在子类中引用父类的成员（字段、方法、构造器）。

```java
public class Animal {
    String name;

    public Animal(String name) {
        this.name = name;
    }

    public void describe() {
        System.out.println("I am " + name);
    }
}

public class Dog extends Animal {
    String breed;

    public Dog(String name, String breed) {
        super(name);          // 调用父类构造器，必须放在第一行
        this.breed = breed;
    }

    @Override
    public void describe() {
        super.describe();     // 调用父类方法
        System.out.println("Breed: " + breed);
    }
}
```

**关键规则**：
- `super()` 调用父类构造器，必须是子类构造器的**第一条语句**
- 如果父类没有无参构造器，子类构造器必须显式调用 `super(参数)`

### 3. 方法重写（@Override）

子类重新实现父类的方法，叫方法重写（Override）。

```java
public class Animal {
    public String sound() {
        return "...";
    }
}

public class Cat extends Animal {
    @Override
    public String sound() {
        return "Meow";
    }
}

public class Dog extends Animal {
    @Override
    public String sound() {
        return "Woof";
    }
}
```

**重写规则**：
- 方法名、参数列表必须完全相同
- 返回值类型可以是父类返回值的子类型（协变返回）
- 访问修饰符不能比父类更严格（父类 `public`，子类不能改成 `private`）
- 不能抛出比父类更宽泛的受检异常

`@Override` 注解不是必须的，但**强烈建议加上**，编译器会帮你检查是否真的重写了父类方法。

### 4. final 类和方法

```java
// final 类：不能被继承
public final class String { ... }  // JDK 中的 String 就是 final 的

// final 方法：不能被重写
public class Template {
    public final void process() {  // 子类不能重写这个方法
        step1();
        step2();
        step3();
    }
}

// final 字段：必须在声明时或构造器中赋值，之后不能修改
public class Circle {
    private final double PI = 3.14159;
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;  // 构造器中赋值
    }
}
```

### 5. 抽象类（abstract）

抽象类不能被实例化，通常用于定义模板。

```java
public abstract class Shape {
    String color;

    public Shape(String color) {
        this.color = color;
    }

    // 抽象方法：没有方法体，子类必须实现
    public abstract double area();

    // 普通方法：子类继承后可以直接用
    public void printInfo() {
        System.out.println("Shape color: " + color + ", area: " + area());
    }
}

public class Circle extends Shape {
    double radius;

    public Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

public class Rectangle extends Shape {
    double width, height;

    public Rectangle(String color, double width, double height) {
        super(color);
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }
}
```

**抽象类特点**：
- 用 `abstract` 修饰的类
- 可以包含抽象方法（没有方法体）和普通方法
- 不能实例化（`new Shape()` 会编译错误）
- 子类必须实现所有抽象方法，除非子类也是抽象类

### 6. 接口（interface）

接口是一种更纯粹的抽象，定义行为规范。

```java
public interface Flyable {
    // 接口中的方法默认是 public abstract
    void fly();

    // Java 8+ 支持默认方法
    default void land() {
        System.out.println("Landing...");
    }

    // Java 8+ 支持静态方法
    static void info() {
        System.out.println("Flyable interface");
    }
}

public interface Swimmable {
    void swim();
}

// 一个类可以实现多个接口
public class Duck extends Animal implements Flyable, Swimmable {
    @Override
    public void fly() {
        System.out.println("Duck is flying");
    }

    @Override
    public void swim() {
        System.out.println("Duck is swimming");
    }
}
```

**接口 vs 抽象类**：

| 对比点 | 接口 | 抽象类 |
|--------|------|--------|
| 继承方式 | implements（可多个） | extends（只能一个） |
| 字段 | 只能是 public static final | 任意 |
| 构造器 | 无 | 有 |
| 方法 | 默认 public abstract，可有 default/static | 任意 |
| 用途 | 定义行为规范 | 定义共同模板 |

### 7. 多态

多态是同一个行为对不同对象有不同表现。核心是**父类引用指向子类对象**。

```java
Animal animal1 = new Dog("Rex", "Labrador");
Animal animal2 = new Cat("Whiskers");

// 调用的是各自重写的方法，而不是 Animal 的方法
System.out.println(animal1.sound()); // Woof
System.out.println(animal2.sound()); // Meow
```

**向上转型（Upcasting）**：子类对象赋值给父类引用，自动进行，安全。

```java
Dog dog = new Dog("Rex", "Labrador");
Animal animal = dog;   // 向上转型，自动，安全
```

**向下转型（Downcasting）**：父类引用强制转换回子类类型，需要显式转换，可能抛 `ClassCastException`。

```java
Animal animal = new Dog("Rex", "Labrador");
Dog dog = (Dog) animal;       // 向下转型，需要强制转换
dog.bark();                   // 现在可以调用 Dog 特有的方法了
```

**instanceof 检查**：

```java
Animal animal = new Dog("Rex", "Labrador");

if (animal instanceof Dog) {
    Dog dog = (Dog) animal;
    dog.bark();
}

// Java 16+ 支持 Pattern Matching，更简洁
if (animal instanceof Dog dog) {
    dog.bark();  // 直接使用，无需强制转换
}
```

---

## 使用场景

1. **继承**：多个类有共同字段和行为时，抽取到父类。例如：`Employee`、`Manager`、`Intern` 都继承 `Person`。

2. **抽象类**：有公共模板逻辑，但某些步骤需要子类自己实现。例如：不同格式的报表生成器都继承 `ReportGenerator`，但 `generateContent()` 由各子类实现。

3. **接口**：定义行为契约，不关心具体实现细节。例如：`Comparable`、`Serializable`、`Runnable` 都是接口。

4. **多态**：编写通用代码处理不同类型对象。例如：`List<Animal>` 中存放各种动物，统一调用 `sound()` 方法。

---

## 工作原理

### 动态分派（Dynamic Dispatch）

Java 方法调用在运行时根据对象的**实际类型**（而非引用类型）决定调用哪个方法，这就是动态分派。

```java
Animal animal = new Dog("Rex", "Labrador");
animal.sound();  // 调用的是 Dog.sound()，不是 Animal.sound()
```

**底层机制**：JVM 通过**虚方法表（vtable）**实现动态分派：
- 每个类都有一个 vtable，存储该类所有虚方法的指针
- 子类的 vtable 从父类继承，重写的方法会替换对应位置的指针
- 运行时，JVM 根据对象实际类型找到对应的 vtable，再找到对应方法并调用

**静态方法不参与多态**：

```java
public class Parent {
    public static void staticMethod() {
        System.out.println("Parent static");
    }
    public void instanceMethod() {
        System.out.println("Parent instance");
    }
}

public class Child extends Parent {
    public static void staticMethod() {  // 这是隐藏（hiding），不是重写
        System.out.println("Child static");
    }
    @Override
    public void instanceMethod() {
        System.out.println("Child instance");
    }
}

Parent p = new Child();
p.staticMethod();    // 输出 "Parent static"  —— 静态方法按引用类型调用
p.instanceMethod();  // 输出 "Child instance" —— 实例方法按实际类型调用
```

### 接口的实现原理

接口方法通过**接口方法表（itable）**实现，与 vtable 类似，但因为一个类可以实现多个接口，所以查找过程略有不同（多一步接口查找），性能与 vtable 相当。

---

## 常见坑与易错点

### 坑1：忘记调用 super() 导致父类字段未初始化

**错误示例**：

```java
public class Animal {
    String name;

    public Animal(String name) {
        this.name = name;
    }
}

public class Dog extends Animal {
    String breed;

    // 错误：没有构造器，编译器自动生成无参构造器
    // 但父类没有无参构造器，编译报错！
    public Dog(String breed) {
        this.breed = breed;  // 编译错误：没有调用 super(name)
    }
}
```

**正确做法**：

```java
public class Dog extends Animal {
    String breed;

    public Dog(String name, String breed) {
        super(name);       // 必须在第一行
        this.breed = breed;
    }
}
```

### 坑2：向下转型不检查类型，运行时 ClassCastException

**错误示例**：

```java
Animal animal = new Cat("Whiskers");
Dog dog = (Dog) animal;  // 编译通过，但运行时抛 ClassCastException！
dog.bark();
```

**正确做法**：

```java
Animal animal = new Cat("Whiskers");
if (animal instanceof Dog dog) {  // Java 16+ pattern matching
    dog.bark();
} else {
    System.out.println("Not a dog");
}
```

### 坑3：静态方法不能被重写（只能被隐藏）

**错误示例**：

```java
public class Parent {
    public static void print() { System.out.println("Parent"); }
}

public class Child extends Parent {
    // 以为这是重写，其实是隐藏
    public static void print() { System.out.println("Child"); }
}

Parent p = new Child();
p.print();  // 输出 "Parent"，不是 "Child"，因为静态方法不多态
```

**正确理解**：静态方法属于类，不属于实例，不参与多态。想要多态效果，必须用实例方法。

### 坑4：接口 default 方法冲突

**错误示例**：

```java
interface A {
    default void hello() { System.out.println("A"); }
}

interface B {
    default void hello() { System.out.println("B"); }
}

// 编译错误：类 C 继承了两个相同签名的 default 方法，必须重写
class C implements A, B { }
```

**正确做法**：

```java
class C implements A, B {
    @Override
    public void hello() {
        A.super.hello();  // 显式指定调用哪个接口的 default 方法
    }
}
```

### 坑5：抽象类的子类忘记实现所有抽象方法

**错误示例**：

```java
public abstract class Shape {
    public abstract double area();
    public abstract double perimeter();  // 忘记实现这个
}

public class Circle extends Shape {  // 编译错误！没有实现 perimeter()
    double radius;

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
    // 缺少 perimeter() 的实现
}
```

**正确做法**：要么实现所有抽象方法，要么把 Circle 也声明为 abstract。

---

## 面试高频问题

### Q1：Java 中的多态是什么？有哪几种形式？

**答**：多态是 OOP 的核心特性之一，指同一个接口或方法对不同对象有不同的行为表现。Java 中多态主要有两种形式：

**编译时多态**（静态多态）：方法重载（Overloading），在编译时根据参数类型确定调用哪个方法。

**运行时多态**（动态多态）：方法重写（Overriding）+ 父类引用指向子类对象，在运行时根据对象实际类型决定调用哪个方法。

多态的三个必要条件：继承（或实现接口）、方法重写、父类引用指向子类对象。底层通过 JVM 的虚方法表（vtable）实现动态分派。

### Q2：抽象类和接口的区别是什么？分别在什么场景下使用？

**答**：

**区别**：
- 接口用 `interface` 定义，只能 `implements`，一个类可以实现多个接口；抽象类用 `abstract class` 定义，只能 `extends`，一个类只能继承一个
- 接口的字段默认是 `public static final`（常量）；抽象类的字段没有限制
- 接口不能有构造器；抽象类可以有构造器
- Java 8+ 接口支持 `default` 和 `static` 方法；Java 9+ 支持 `private` 方法

**使用场景**：
- **接口**：定义行为契约，关注"能做什么"，适合跨继承层次的能力声明，如 `Comparable`、`Serializable`、`Runnable`
- **抽象类**：定义共同模板，关注"是什么"，适合有公共逻辑代码需要复用的场景，如模板方法模式

### Q3：方法重写（Override）和方法重载（Overload）的区别？

**答**：

| 对比 | 重写（Override） | 重载（Overload） |
|------|-----------------|-----------------|
| 发生位置 | 子类和父类之间 | 同一个类内部 |
| 方法名 | 相同 | 相同 |
| 参数列表 | 必须相同 | 必须不同 |
| 返回值 | 相同或协变 | 可以不同 |
| 多态类型 | 运行时多态 | 编译时多态 |
| @Override | 推荐加 | 不适用 |

重写是实现运行时多态的关键机制，重载只是同名方法的不同参数版本，编译时就确定了调用哪个。

### Q4：super 和 this 有什么区别？

**答**：
- `this` 引用当前对象本身，`super` 引用父类部分
- `this()` 调用本类其他构造器，`super()` 调用父类构造器，两者都必须放在构造器第一行，因此不能同时出现
- `this.field` 访问当前对象字段，`super.field` 访问父类字段（当子类有同名字段时区分）
- `this.method()` 调用当前对象方法（会触发多态），`super.method()` 直接调用父类方法（不触发多态）

### Q5：final 关键字在继承中有哪些用途？

**答**：
- `final class`：该类不能被继承，例如 JDK 中的 `String`、`Integer`。这样做可以防止行为被篡改，保证安全性和不可变性
- `final method`：该方法不能被子类重写，常用于模板方法模式中固定算法骨架，同时 JVM 可以做内联优化
- `final field`：字段一旦赋值不能修改，必须在声明时或构造器中初始化，常与 `static` 组合定义常量（`public static final`）

JVM 层面，`final` 还有内存语义：对 `final` 字段的写操作，对所有读线程可见（happens-before 保证），是实现不可变对象的基础。

### Q6：instanceof 和强制转型的关系是什么？Java 16 有什么改进？

**答**：
`instanceof` 用于在运行时检查对象是否是某个类型的实例（包括父类和接口），在向下转型之前应该先用它做安全检查，避免 `ClassCastException`。

传统写法需要两步：检查 + 强转：
```java
if (animal instanceof Dog) {
    Dog dog = (Dog) animal;
    dog.bark();
}
```

Java 16 引入**模式匹配（Pattern Matching for instanceof）**，将检查和转型合并为一步：
```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

编译器保证在 `if` 块内 `dog` 一定是 `Dog` 类型，更安全也更简洁。这是 Java 向模式匹配语言特性演进的一步，后续 `switch` 表达式也支持类似的模式匹配（Java 21 正式稳定）。
