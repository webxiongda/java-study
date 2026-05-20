# Chapter 04 - 数组与字符串 | 理论知识

---

## 一、一维数组

### 1.1 声明与初始化

数组是一种固定长度、存储同类型元素的线性数据结构，声明后长度不可变。

```java
// 声明方式（推荐第一种）
int[] nums;
int nums2[];   // C 风格，不推荐

// 动态初始化：只指定长度，元素用默认值填充（int 默认 0，String 默认 null，boolean 默认 false）
int[] arr = new int[5];

// 静态初始化：声明时直接赋值，长度由元素数量决定
int[] scores = {90, 85, 78, 92, 88};
int[] scores2 = new int[]{90, 85, 78, 92, 88};  // 等价写法
```

### 1.2 遍历方式

```java
int[] arr = {10, 20, 30, 40, 50};

// 方式 1：普通 for 循环（可访问下标）
for (int i = 0; i < arr.length; i++) {
    System.out.println(arr[i]);
}

// 方式 2：增强 for（forEach，简洁但无法获取下标）
for (int num : arr) {
    System.out.println(num);
}
```

### 1.3 Arrays 工具类

`java.util.Arrays` 提供了操作数组的常用静态方法：

```java
import java.util.Arrays;

int[] arr = {5, 3, 1, 4, 2};

Arrays.sort(arr);                         // 原地排序（升序）
System.out.println(Arrays.toString(arr)); // 转字符串输出：[1, 2, 3, 4, 5]

int[] copy = Arrays.copyOf(arr, 3);       // 复制前 3 个元素：[1, 2, 3]
int[] copy2 = Arrays.copyOfRange(arr, 1, 4); // 复制下标 1~3：[2, 3, 4]

Arrays.fill(arr, 0);                      // 将所有元素填充为 0

boolean equal = Arrays.equals(arr, copy); // 按内容比较两个数组是否相等
```

---

## 二、二维数组

```java
// 声明与初始化
int[][] matrix = new int[3][4];   // 3 行 4 列
int[][] grid = {
    {1, 2, 3},
    {4, 5, 6},
    {7, 8, 9}
};

// 遍历（嵌套 for 循环）
for (int i = 0; i < grid.length; i++) {
    for (int j = 0; j < grid[i].length; j++) {
        System.out.print(grid[i][j] + " ");
    }
    System.out.println();
}

// Java 支持不规则二维数组（每行长度可不同）
int[][] jagged = new int[3][];
jagged[0] = new int[2];
jagged[1] = new int[4];
jagged[2] = new int[1];
```

---

## 三、String 不可变性

String 是 **不可变（Immutable）** 的引用类型。一旦创建，字符串内容不可更改。

```java
String s = "hello";
s = s + " world";   // 并非修改原字符串，而是创建了一个新的字符串对象
                    // 原 "hello" 对象仍在内存中，等待 GC 回收
```

**为什么 String 设计为不可变？**
1. **线程安全**：多线程共享同一 String 对象时无需同步
2. **字符串常量池**：相同字面量可以复用，节省内存
3. **缓存 hashCode**：hashCode 只需计算一次，提升 HashMap 等容器性能
4. **安全性**：作为参数传入时不担心被调用方修改

---

## 四、String 常量池

```java
String s1 = "hello";          // 存储在常量池
String s2 = "hello";          // 复用常量池中的同一对象
String s3 = new String("hello"); // 强制在堆中新建对象

System.out.println(s1 == s2);      // true（同一个常量池对象）
System.out.println(s1 == s3);      // false（s3 是堆中的新对象）
System.out.println(s1.equals(s3)); // true（内容相同）
```

**规律：** 字面量赋值会先在常量池中查找，存在则复用；`new String()` 则绕过常量池，直接在堆中创建。

---

## 五、String 常用方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `length()` | 返回字符串长度 | `"hello".length()` → `5` |
| `charAt(int index)` | 返回指定下标的字符 | `"hello".charAt(1)` → `'e'` |
| `substring(int begin)` | 从 begin 截取到末尾 | `"hello".substring(2)` → `"llo"` |
| `substring(int begin, int end)` | 截取 [begin, end) | `"hello".substring(1,3)` → `"el"` |
| `indexOf(String s)` | 返回子串首次出现的下标，不存在返回 -1 | `"hello".indexOf("ll")` → `2` |
| `split(String regex)` | 按正则分割，返回字符串数组 | `"a,b,c".split(",")` → `["a","b","c"]` |
| `replace(CharSequence old, CharSequence new)` | 替换所有匹配 | `"aabbcc".replace("bb","XX")` → `"aaXXcc"` |
| `trim()` | 去除首尾空白字符 | `" hi ".trim()` → `"hi"` |
| `toUpperCase()` | 转大写 | `"hello".toUpperCase()` → `"HELLO"` |
| `toLowerCase()` | 转小写 | `"HELLO".toLowerCase()` → `"hello"` |
| `contains(CharSequence s)` | 是否包含子串 | `"hello".contains("ell")` → `true` |
| `startsWith(String prefix)` | 是否以指定前缀开头 | `"hello".startsWith("he")` → `true` |
| `endsWith(String suffix)` | 是否以指定后缀结尾 | `"hello".endsWith("lo")` → `true` |
| `equals(Object o)` | 内容比较（区分大小写） | `"Hi".equals("hi")` → `false` |
| `equalsIgnoreCase(String s)` | 内容比较（忽略大小写） | `"Hi".equalsIgnoreCase("hi")` → `true` |
| `compareTo(String s)` | 字典序比较，返回差值 | `"apple".compareTo("banana")` → 负数 |
| `isEmpty()` | 是否长度为 0 | `"".isEmpty()` → `true` |
| `isBlank()` | 是否全为空白（Java 11+） | `"  ".isBlank()` → `true` |

---

## 六、String vs StringBuilder vs StringBuffer

| 特性 | String | StringBuilder | StringBuffer |
|------|--------|--------------|--------------|
| 是否可变 | 不可变 | 可变 | 可变 |
| 线程安全 | 安全（不可变） | 不安全 | 安全（方法加 synchronized） |
| 性能 | 拼接慢（每次创建新对象） | 最快 | 比 StringBuilder 慢（加锁开销） |
| 适用场景 | 字符串不需要频繁修改 | 单线程频繁拼接/修改 | 多线程共享的可变字符串 |

```java
// StringBuilder 常用方法
StringBuilder sb = new StringBuilder();
sb.append("Hello");      // 追加
sb.append(", ");
sb.append("World");
sb.insert(5, "!");       // 在下标 5 处插入
sb.delete(5, 6);         // 删除下标 5（含）到 6（不含）
sb.reverse();            // 反转
sb.toString();           // 转为 String
```

---

## 七、字符串比较

```java
String a = "hello";
String b = new String("hello");

// == 比较的是引用（内存地址），不是内容
System.out.println(a == b);              // false

// equals 比较的是内容
System.out.println(a.equals(b));         // true

// compareTo 按字典序比较，相等返回 0，前者小返回负数，前者大返回正数
System.out.println("apple".compareTo("banana")); // 负数（a < b）
System.out.println("hello".compareTo("hello"));  // 0

// 注意：永远用 equals 比较字符串内容，永远不要用 ==
```

---

## 八、常见坑

**坑 1：用 `==` 比较字符串内容**

```java
// 错误
if (userInput == "yes") { ... }

// 正确：用 equals，且将常量放在前面避免空指针
if ("yes".equals(userInput)) { ... }
```

**坑 2：字符串拼接的性能问题**

```java
// 错误：在循环中用 + 拼接，每次都创建新对象，O(n²) 时间复杂度
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i;   // 糟糕！
}

// 正确：使用 StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);
}
String result = sb.toString();
```

**坑 3：数组越界（ArrayIndexOutOfBoundsException）**

```java
int[] arr = {1, 2, 3};
// 错误：下标从 0 开始，最大有效下标为 length - 1
System.out.println(arr[3]);   // 报错！

// 正确：遍历时用 i < arr.length，不要 <=
for (int i = 0; i < arr.length; i++) { ... }
```

---

## 九、面试高频问题

**Q1：String 为什么设计成不可变的？**

答：主要有四个原因。第一，线程安全，多线程可安全共享同一个 String 对象无需加锁；第二，可以利用字符串常量池复用对象，节省内存；第三，String 的 hashCode 可以缓存，作为 HashMap 的 key 时性能更好；第四，在网络传输、数据库连接等场景中，String 被用作参数，不可变可防止被意外修改。

**Q2：StringBuilder 和 StringBuffer 的区别？**

答：两者都是可变字符串类，底层用 `char[]` 存储。主要区别是线程安全性：`StringBuffer` 的方法都用 `synchronized` 修饰，是线程安全的，但有锁的开销，性能较低；`StringBuilder` 没有同步，线程不安全，但性能更高。单线程场景下优先使用 `StringBuilder`，多线程共享时才用 `StringBuffer`。

**Q3：String 的 `+` 操作底层是什么？**

答：编译器会将 `String + String` 优化为 `StringBuilder.append()` 调用，最后调用 `toString()` 返回新字符串。但在循环内部使用 `+` 时，每次迭代都会创建一个新的 `StringBuilder` 对象，导致大量对象创建。因此循环内部应手动创建一个 `StringBuilder` 并复用。

**Q4：`String s = new String("abc")` 创建了几个对象？**

答：最多创建 2 个对象。如果字符串常量池中已有 `"abc"`，则只在堆中创建 1 个 String 对象；如果常量池中没有，则先在常量池创建 `"abc"`，再在堆中创建 1 个 String 对象，共 2 个。

**Q5：数组和 ArrayList 的区别？**

答：数组长度固定，声明后不可变，可存储基本类型；ArrayList 是动态数组，长度可自动扩容（扩为原来的 1.5 倍），但只能存储引用类型（基本类型需要装箱）。数组访问速度更快，内存占用更小；ArrayList 更灵活，提供了丰富的操作方法。

**Q6：如何高效反转一个字符串？**

答：最简洁的方式是 `new StringBuilder(str).reverse().toString()`。如果不能用 StringBuilder，可以手动双指针对换：将字符串转为 `char[]`，用两个指针从两端向中间移动并交换字符，最后转回 String。时间复杂度 O(n)，空间 O(n)。
