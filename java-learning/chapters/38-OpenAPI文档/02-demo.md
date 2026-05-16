# Chapter 38 OpenAPI文档 - 实操 Demo

## Demo 目标

完成一个围绕 **生成 Swagger 接口文档** 的最小可运行练习。Demo 不追求功能多，而是要求能运行、能解释、能扩展。

## 前置条件

- JDK 21 可用，能执行 java -version。
- 项目已用 Git 管理，每次练习前确认工作区状态。
- 使用 IntelliJ IDEA 或 VS Code，代码统一 UTF-8。
- 准备 MySQL 或 Docker MySQL；没有数据库时先写 SQL 文件和伪实现。
- 准备 Spring Boot 项目骨架，包名建议使用 com.example.blog。

## 实操步骤

1. 创建本章练习分支或目录，名称包含 chapter-38。
2. 根据“springdoc、接口分组、请求示例、调试”列出 3 个必须验证的行为。
3. 先写最小代码让主流程跑通，再补充异常、边界和日志。
4. 把运行命令、输入样例、输出结果写进本章笔记。
5. 最后用一句话总结：OpenAPI文档 在博客项目中承担什么责任。

## 示例代码

~~~java
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleService articleService;

    @GetMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(articleService.getDetail(id));
    }
}
~~~

## 运行与验证

| 检查项 | 验证方式 |
|---|---|
| 主流程可运行 | 使用命令行、JUnit、HTTP 请求或 SQL 客户端执行一次完整流程 |
| 错误场景可观察 | 故意传入非法参数或断开依赖，确认异常信息可理解 |
| 输出可复现 | README 中记录命令、请求、响应或控制台输出 |
| 代码可维护 | 类名、方法名、包结构能表达职责，没有把所有逻辑塞进一个方法 |

## 建议提交信息

~~~bash
git add .
git commit -m "chapter 38: OpenAPI文档 demo"
~~~
