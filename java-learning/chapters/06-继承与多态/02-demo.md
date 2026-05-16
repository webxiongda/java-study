# 继承与多态 实操 Demo

## Demo 1：动物园多态演示

### 实操目标
演示继承、方法重写、多态向上转型，以及如何用父类引用统一处理不同子类对象。

### 示例代码

```java
package demo06;

// 父类：抽象动物
abstract class Animal {
    private String name;
    private int age;

    public Animal(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    // 抽象方法：子类必须实现
    public abstract String sound();

    // 普通方法：子类继承，可重写
    public String describe() {
        return name + " (age " + age + ") says: " + sound();
    }
}

// 子类：狗
class Dog extends Animal {
    private String breed;

    public Dog(String name, int age, String breed) {
        super(name, age);
        this.breed = breed;
    }

    @Override
    public String sound() {
        return "Woof!";
    }

    @Override
    public String describe() {
        return super.describe() + " [Breed: " + breed + "]";
    }

    public void fetch() {
        System.out.println(getName() + " fetches the ball!");
    }
}

// 子类：猫
class Cat extends Animal {
    private boolean isIndoor;

    public Cat(String name, int age, boolean isIndoor) {
        super(name, age);
        this.isIndoor = isIndoor;
    }

    @Override
    public String sound() {
        return "Meow!";
    }

    @Override
    public String describe() {
        String type = isIndoor ? "indoor" : "outdoor";
        return super.describe() + " [" + type + " cat]";
    }
}

// 子类：鸟
class Bird extends Animal {
    private boolean canFly;

    public Bird(String name, int age, boolean canFly) {
        super(name, age);
        this.canFly = canFly;
    }

    @Override
    public String sound() {
        return "Tweet!";
    }

    @Override
    public String describe() {
        String flyInfo = canFly ? "can fly" : "cannot fly";
        return super.describe() + " [" + flyInfo + "]";
    }
}

// 主类
public class AnimalZoo {
    public static void main(String[] args) {
        // 多态：父类引用指向子类对象（向上转型）
        Animal[] animals = {
            new Dog("Rex", 3, "Labrador"),
            new Cat("Whiskers", 5, true),
            new Bird("Tweety", 2, true),
            new Dog("Buddy", 1, "Poodle")
        };

        System.out.println("=== Zoo Animals ===");
        for (Animal animal : animals) {
            // 运行时多态：调用各自重写的 describe() 方法
            System.out.println(animal.describe());
        }

        System.out.println("\n=== Dog Special Actions ===");
        for (Animal animal : animals) {
            // 向下转型（先用 instanceof 检查）
            if (animal instanceof Dog dog) {  // Java 16+ pattern matching
                dog.fetch();  // 调用 Dog 特有方法
            }
        }
    }
}
```

### 运行结果

```
=== Zoo Animals ===
Rex (age 3) says: Woof! [Breed: Labrador]
Whiskers (age 5) says: Meow! [indoor cat]
Tweety (age 2) says: Tweet! [can fly]
Buddy (age 1) says: Woof! [Breed: Poodle]

=== Dog Special Actions ===
Rex fetches the ball!
Buddy fetches the ball!
```

### 关键点说明

1. `Animal[] animals` 存放不同子类对象，这是多态的典型用法
2. `animal.describe()` 在运行时根据对象实际类型调用对应方法，体现动态分派
3. `super.describe()` 在子类中复用父类逻辑，避免代码重复
4. `instanceof Dog dog` 是 Java 16 的 Pattern Matching 写法，比传统的检查+强转更简洁

---

## Demo 2：接口多实现 + 组合使用

### 实操目标
演示一个类实现多个接口，以及接口 default 方法的使用，体现接口作为行为规范的设计思想。

### 示例代码

```java
package demo06;

// 接口：可飞行
interface Flyable {
    void fly();

    // default 方法：提供默认实现，实现类可选择重写
    default String getFlightType() {
        return "standard flight";
    }
}

// 接口：可游泳
interface Swimmable {
    void swim();

    default String getSwimStyle() {
        return "freestyle";
    }
}

// 接口：可奔跑
interface Runnable {  // 注意：实际开发中不要和 java.lang.Runnable 重名
    void run();
}

// 鸭子：会飞、会游泳、会跑
class Duck implements Flyable, Swimmable, Runnable {
    private String name;

    public Duck(String name) {
        this.name = name;
    }

    @Override
    public void fly() {
        System.out.println(name + " flaps wings and flies!");
    }

    @Override
    public void swim() {
        System.out.println(name + " paddles through water.");
    }

    @Override
    public void run() {
        System.out.println(name + " waddles along.");
    }

    // 重写 default 方法
    @Override
    public String getFlightType() {
        return "low altitude flight";
    }
}

// 鱼：只会游泳
class Fish implements Swimmable {
    private String name;

    public Fish(String name) {
        this.name = name;
    }

    @Override
    public void swim() {
        System.out.println(name + " glides through water smoothly.");
    }

    @Override
    public String getSwimStyle() {
        return "serpentine";
    }
}

// 工具方法：接受接口类型参数，体现面向接口编程
class ActivityCenter {
    public static void makeItFly(Flyable f) {
        f.fly();
        System.out.println("  Flight type: " + f.getFlightType());
    }

    public static void makeItSwim(Swimmable s) {
        s.swim();
        System.out.println("  Swim style: " + s.getSwimStyle());
    }
}

public class InterfaceDemo {
    public static void main(String[] args) {
        Duck duck = new Duck("Donald");
        Fish fish = new Fish("Nemo");

        System.out.println("=== Flying ===");
        ActivityCenter.makeItFly(duck);  // Duck 作为 Flyable 传入

        System.out.println("\n=== Swimming ===");
        ActivityCenter.makeItSwim(duck); // Duck 作为 Swimmable 传入
        ActivityCenter.makeItSwim(fish); // Fish 作为 Swimmable 传入

        System.out.println("\n=== Interface Type Checking ===");
        Object[] objects = { duck, fish, "a string", 42 };
        for (Object obj : objects) {
            System.out.print(obj.getClass().getSimpleName() + ": ");
            if (obj instanceof Flyable) System.out.print("can fly ");
            if (obj instanceof Swimmable) System.out.print("can swim ");
            if (obj instanceof Runnable) System.out.print("can run ");
            System.out.println();
        }
    }
}
```

### 运行结果

```
=== Flying ===
Donald flaps wings and flies!
  Flight type: low altitude flight

=== Swimming ===
Donald paddles through water.
  Swim style: freestyle
Nemo glides through water smoothly.
  Swim style: serpentine

=== Interface Type Checking ===
Duck: can fly can swim can run 
Fish: can swim 
String: 
Integer: 
```

### 关键点说明

1. `Duck implements Flyable, Swimmable, Runnable` — 一个类实现多个接口，解决了 Java 单继承的限制
2. `makeItFly(Flyable f)` — 参数类型是接口，调用方可以传入任何实现该接口的对象，这是"面向接口编程"的核心
3. `getFlightType()` 是 `default` 方法，`Duck` 选择重写，`Fish` 没有实现该接口所以无需关心

---

## Demo 3：模板方法模式（抽象类经典用法）

### 实操目标
演示抽象类如何通过模板方法模式固定算法骨架，让子类填充具体步骤，是实际开发中非常常见的设计模式。

### 示例代码

```java
package demo06;

// 抽象类：报告生成器模板
abstract class ReportGenerator {
    // 模板方法：定义生成报告的步骤（用 final 防止子类改变骨架）
    public final String generateReport(String title, String[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatHeader(title));      // 步骤1：格式化标题
        sb.append(formatBody(data));         // 步骤2：格式化正文（子类实现）
        sb.append(formatFooter());           // 步骤3：格式化页脚
        return sb.toString();
    }

    // 子类必须实现的步骤
    protected abstract String formatBody(String[] data);

    // 有默认实现，子类可选择重写
    protected String formatHeader(String title) {
        return "=== " + title + " ===\n";
    }

    protected String formatFooter() {
        return "=== End of Report ===\n";
    }
}

// CSV 格式报告
class CsvReport extends ReportGenerator {
    @Override
    protected String formatBody(String[] data) {
        return String.join(",", data) + "\n";
    }
}

// HTML 格式报告
class HtmlReport extends ReportGenerator {
    @Override
    protected String formatHeader(String title) {
        return "<html><head><title>" + title + "</title></head><body>\n<h1>" + title + "</h1>\n";
    }

    @Override
    protected String formatBody(String[] data) {
        StringBuilder sb = new StringBuilder("<ul>\n");
        for (String item : data) {
            sb.append("  <li>").append(item).append("</li>\n");
        }
        sb.append("</ul>\n");
        return sb.toString();
    }

    @Override
    protected String formatFooter() {
        return "</body></html>\n";
    }
}

// Markdown 格式报告
class MarkdownReport extends ReportGenerator {
    @Override
    protected String formatHeader(String title) {
        return "# " + title + "\n\n";
    }

    @Override
    protected String formatBody(String[] data) {
        StringBuilder sb = new StringBuilder();
        for (String item : data) {
            sb.append("- ").append(item).append("\n");
        }
        return sb.toString();
    }

    @Override
    protected String formatFooter() {
        return "\n---\n";
    }
}

public class TemplateMethodDemo {
    public static void main(String[] args) {
        String[] students = {"Alice - 95", "Bob - 87", "Charlie - 92"};

        // 多态：同一套接口，不同格式输出
        ReportGenerator[] generators = {
            new CsvReport(),
            new HtmlReport(),
            new MarkdownReport()
        };

        String[] names = {"CSV", "HTML", "Markdown"};

        for (int i = 0; i < generators.length; i++) {
            System.out.println("--- " + names[i] + " Report ---");
            System.out.println(generators[i].generateReport("Student Scores", students));
        }
    }
}
```

### 运行结果

```
--- CSV Report ---
=== Student Scores ===
Alice - 95,Bob - 87,Charlie - 92
=== End of Report ===

--- HTML Report ---
<html><head><title>Student Scores</title></head><body>
<h1>Student Scores</h1>
<ul>
  <li>Alice - 95</li>
  <li>Bob - 87</li>
  <li>Charlie - 92</li>
</ul>
</body></html>

--- Markdown Report ---
# Student Scores

- Alice - 95
- Bob - 87
- Charlie - 92

---
```

### 关键点说明

1. `generateReport()` 用 `final` 修饰，子类不能修改算法骨架，只能定制步骤
2. `formatBody()` 是 `abstract`，子类必须实现；`formatHeader/Footer` 有默认实现，子类可选择重写
3. 这就是**模板方法模式（Template Method Pattern）**，是 Spring 框架中大量使用的设计模式（如 `JdbcTemplate`）
