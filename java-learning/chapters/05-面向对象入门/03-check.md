# Chapter 05 - 面向对象入门 | 知识检测

> 完成以下 5 道题，再对照参考答案自查。建议先独立作答，再看答案。

---

## Q1 概念题 — 封装与访问控制

**题目：** 下面的 `Temperature` 类存在封装设计问题，请找出所有问题并给出改进方案。

```java
public class Temperature {
    public double celsius;      // 摄氏度
    public double fahrenheit;   // 华氏度（冗余字段）

    public Temperature(double celsius) {
        this.celsius = celsius;
        this.fahrenheit = celsius * 9 / 5 + 32;
    }

    // 直接修改摄氏度时，华氏度没有同步更新
    public void setCelsius(double celsius) {
        this.celsius = celsius;
        // 忘记更新 fahrenheit！
    }
}
```

---

**参考答案：**

**问题 1：** `celsius` 和 `fahrenheit` 都是 `public`，外部代码可以直接修改，导致两者不同步（如 `t.celsius = 100` 而 `fahrenheit` 还是旧值）。

**问题 2：** `fahrenheit` 是冗余字段，它可以由 `celsius` 计算得出，没必要单独存储。每次同步都容易遗漏。

**改进方案：**

```java
public class Temperature {
    private double celsius;  // 只存一个基准值

    public Temperature(double celsius) {
        this.celsius = celsius;
    }

    public double getCelsius() {
        return celsius;
    }

    public void setCelsius(double celsius) {
        if (celsius < -273.15) {
            throw new IllegalArgumentException("温度不能低于绝对零度 -273.15°C");
        }
        this.celsius = celsius;
    }

    // 华氏度通过计算获得，永远与 celsius 同步
    public double getFahrenheit() {
        return celsius * 9.0 / 5.0 + 32;
    }

    // 也可以用华氏度设置
    public void setFahrenheit(double fahrenheit) {
        setCelsius((fahrenheit - 32) * 5.0 / 9.0);
    }

    @Override
    public String toString() {
        return String.format("%.2f°C / %.2f°F", celsius, getFahrenheit());
    }
}
```

**核心原则：** 不要存储可以计算得出的冗余字段；通过 setter 控制字段修改的合法性。

---

## Q2 概念题 — static 成员

**题目：** 判断以下代码的输出，并解释每个 `System.out.println` 的结果。

```java
public class Counter {
    private static int count = 0;
    private int id;

    public Counter() {
        count++;
        id = count;
    }

    public static int getCount() { return count; }
    public int getId() { return id; }

    public static void main(String[] args) {
        System.out.println("A: " + Counter.getCount());  // 第A行

        Counter c1 = new Counter();
        Counter c2 = new Counter();
        Counter c3 = new Counter();

        System.out.println("B: " + Counter.getCount());  // 第B行
        System.out.println("C: " + c1.getId());          // 第C行
        System.out.println("D: " + c2.getId());          // 第D行
        System.out.println("E: " + c3.getId());          // 第E行

        // 思考题：下面两行输出什么？
        System.out.println("F: " + c1.getCount());       // 第F行（用对象调用 static 方法）
        System.out.println("G: " + Counter.count);       // 第G行
    }
}
```

---

**参考答案：**

| 行 | 输出 | 原因 |
|----|------|------|
| A | `0` | 还没有创建任何 Counter 对象，count 为初始值 0 |
| B | `3` | 创建了 3 个对象，每次构造器执行 `count++` |
| C | `1` | c1 是第一个创建的，此时 count 刚变为 1，`id = count = 1` |
| D | `2` | c2 是第二个，id = 2 |
| E | `3` | c3 是第三个，id = 3 |
| F | `3` | 虽然用对象引用调用 static 方法，实际效果与 `Counter.getCount()` 相同，输出 3。但这种写法不推荐，IDEA 会发出警告 |
| G | **编译错误** | `count` 是 `private`，类外部（此处 main 是在 Counter 类内，所以能访问，输出 3）。若在其他类中访问则编译错误 |

**注意：** 第 G 行，因为 `main` 方法在 `Counter` 类内部，所以可以访问 `private` 的 `count`，输出 `3`。

---

## Q3 实操题 — 设计一个 Rectangle 类

**题目：** 设计一个 `Rectangle`（矩形）类，要求：
1. 包含 `width` 和 `height` 两个私有字段（单位：厘米）
2. 提供全参构造器和无参构造器（默认 1x1）
3. 提供 getter/setter，setter 中要校验宽/高必须大于 0
4. 提供 `area()`（面积）和 `perimeter()`（周长）方法
5. 提供 `isSquare()` 方法判断是否为正方形
6. 重写 `toString()` 方法

---

**参考答案：**

```java
public class Rectangle {
    private double width;
    private double height;

    // 全参构造器
    public Rectangle(double width, double height) {
        setWidth(width);    // 复用 setter 的校验逻辑
        setHeight(height);
    }

    // 无参构造器：默认 1x1
    public Rectangle() {
        this(1.0, 1.0);
    }

    // Getters
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    // Setters with validation
    public void setWidth(double width) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0，给定：" + width);
        this.width = width;
    }

    public void setHeight(double height) {
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0，给定：" + height);
        this.height = height;
    }

    // 业务方法
    public double area() {
        return width * height;
    }

    public double perimeter() {
        return 2 * (width + height);
    }

    public boolean isSquare() {
        return Double.compare(width, height) == 0;  // 用 Double.compare 避免浮点精度问题
    }

    @Override
    public String toString() {
        return String.format("Rectangle{width=%.2f, height=%.2f, area=%.2f, square=%s}",
                             width, height, area(), isSquare());
    }

    public static void main(String[] args) {
        Rectangle r1 = new Rectangle(4.0, 3.0);
        System.out.println(r1);               // Rectangle{width=4.00, height=3.00, area=12.00, square=false}
        System.out.println("周长：" + r1.perimeter());  // 14.0

        Rectangle r2 = new Rectangle(5.0, 5.0);
        System.out.println(r2.isSquare());    // true

        Rectangle r3 = new Rectangle();
        System.out.println(r3);               // Rectangle{width=1.00, height=1.00, area=1.00, square=true}

        // 测试校验
        try {
            new Rectangle(-1, 3);
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常：" + e.getMessage());  // 宽度必须大于 0，给定：-1.0
        }
    }
}
```

---

## Q4 实操题 — 分析代码执行顺序

**题目：** 分析以下代码的输出顺序，并在横线上填写每行输出的内容。

```java
public class OrderTest {
    private static int x = initX();

    private static int initX() {
        System.out.println("_____");   // 第1行
        return 1;
    }

    static {
        System.out.println("_____");   // 第2行
    }

    private int y = initY();

    private int initY() {
        System.out.println("_____");   // 第3行（每次 new 对象都会打印）
        return 2;
    }

    {
        System.out.println("_____");   // 第4行
    }

    public OrderTest() {
        System.out.println("_____");   // 第5行
    }

    public static void main(String[] args) {
        System.out.println("=== main 开始 ===");
        new OrderTest();
        System.out.println("=== 第二个对象 ===");
        new OrderTest();
    }
}
```

---

**参考答案：**

```
=== main 开始 ===
静态字段 x 初始化          ← 第1行（类加载时，静态字段先于静态代码块）
静态代码块执行             ← 第2行
实例字段 y 初始化          ← 第3行
实例代码块执行             ← 第4行
构造器执行                ← 第5行
=== 第二个对象 ===
实例字段 y 初始化          ← 第3行（静态部分不再重复）
实例代码块执行             ← 第4行
构造器执行                ← 第5行
```

实际输出（用更具体的文字表述）：
```
=== main 开始 ===
静态字段 x 初始化
静态代码块执行
实例字段 y 初始化
实例代码块执行
构造器执行
=== 第二个对象 ===
实例字段 y 初始化
实例代码块执行
构造器执行
```

---

## Q5 综合题 — 完善一个类的设计

**题目：** 下面是一个不完整的 `Circle` 类，按要求补全代码。

```java
public class Circle {
    private static final double PI = 3.14159265358979;
    private static int objectCount = 0;  // 记录创建了多少个 Circle 对象

    private double radius;

    // TODO 1：写全参构造器，radius 必须 > 0，并维护 objectCount
    // TODO 2：写无参构造器，radius 默认为 1.0
    // TODO 3：写 getRadius() 和 setRadius()，setter 需要校验
    // TODO 4：写 area() 方法返回面积
    // TODO 5：写 circumference() 方法返回周长
    // TODO 6：写 static 方法 getObjectCount() 返回已创建的对象数量
    // TODO 7：重写 toString()
}
```

---

**参考答案：**

```java
public class Circle {
    private static final double PI = 3.14159265358979;
    private static int objectCount = 0;

    private double radius;

    // TODO 1：全参构造器
    public Circle(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("半径必须大于 0，给定：" + radius);
        }
        this.radius = radius;
        objectCount++;  // 每成功创建一个 Circle，计数 +1
    }

    // TODO 2：无参构造器，委托给全参构造器
    public Circle() {
        this(1.0);
    }

    // TODO 3：Getter 和 Setter
    public double getRadius() { return radius; }

    public void setRadius(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("半径必须大于 0，给定：" + radius);
        }
        this.radius = radius;
    }

    // TODO 4：面积 = π * r²
    public double area() {
        return PI * radius * radius;
    }

    // TODO 5：周长 = 2 * π * r
    public double circumference() {
        return 2 * PI * radius;
    }

    // TODO 6：静态方法，通过类名访问
    public static int getObjectCount() {
        return objectCount;
    }

    // TODO 7：toString
    @Override
    public String toString() {
        return String.format("Circle{radius=%.2f, area=%.2f, circumference=%.2f}",
                             radius, area(), circumference());
    }

    public static void main(String[] args) {
        System.out.println("已创建：" + Circle.getObjectCount());  // 0

        Circle c1 = new Circle(5.0);
        Circle c2 = new Circle(3.0);
        Circle c3 = new Circle();    // 默认半径 1.0

        System.out.println(c1);
        System.out.println(c2);
        System.out.println(c3);
        System.out.println("已创建：" + Circle.getObjectCount());  // 3

        c1.setRadius(10.0);
        System.out.println("修改后：" + c1);
    }
}
```
