# Chapter 36 Spring Boot + MyBatis - 项目任务

## 任务概述

把博客 API 的数据层从"裸 Boot + MyBatis"升级到"Boot + MyBatis-Plus + 读写分离 + @MybatisTest 单测"。

## 业务背景

28-30 章的 Mapper 都是手写 XML，功能 OK 但重复代码多。这一章引入 MP 处理单表 CRUD，复杂 SQL 保留 XML，并把读操作接入只读副本（或测试 slave 模拟）。

## 任务拆解

### Step 1：引入 MyBatis-Plus

替换 `mybatis-spring-boot-starter` 为 `mybatis-plus-spring-boot3-starter`，启动验证老 Mapper 仍正常。

### Step 2：Entity 加 MP 注解

给 `Post` / `User` / `Tag` / `Comment` 加：

- `@TableName`
- `@TableId(type=IdType.AUTO)`
- `@TableLogic` 字段（is_deleted）
- `@TableField(fill=FieldFill.INSERT)` createdAt / `INSERT_UPDATE` updatedAt

写 `MetaObjectHandler` 实现自动填充时间戳。

### Step 3：Mapper 继承 BaseMapper

新建 `TagMapper extends BaseMapper<Tag>`（原来的手写 Mapper XML 也保留），验证两套 Mapper 共存。

### Step 4：分页插件

```java
@Bean
public MybatisPlusInterceptor mpInterceptor() {
    var i = new MybatisPlusInterceptor();
    i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    i.addInnerInterceptor(new BlockAttackInnerInterceptor());
    return i;
}
```

用 `IPage<Post>` 实现后台管理分页（允许 OFFSET），前台 Feed 仍用 keyset XML。

### Step 5：读写分离（简版）

配 2 个数据源（开发环境可以 master / slave 指向同一个 MySQL，模拟配置）：

- `find*` / `list*` / `count*` 方法 → slave
- `insert` / `update` / `delete` → master

AOP 自动路由（03-check Q4 方案），单测：

```java
@Test
void read_goes_to_slave() {
    postMapper.findAll();
    assertThat(DataSourceHolder.get()).isEqualTo("SLAVE");
}
```

### Step 6：@MybatisTest 单测

不用 `@SpringBootTest`（慢），用 `@MybatisTest + @AutoConfigureTestDatabase(replace=NONE) + Testcontainers`：

每个 Mapper ≥ 5 个测试用例，验证 MP 生成的 CRUD + 自定义 XML 方法都正确。

## 交付物

- [ ] `entity/` 加完 MP 注解
- [ ] `TagMapper extends BaseMapper<Tag>`
- [ ] `MetaObjectHandler.java`（自动填充时间）
- [ ] `MybatisPlusInterceptor` Bean 配置
- [ ] AOP 读写分离路由
- [ ] `@MybatisTest` 单测（≥ 20 用例）
- [ ] `docs/mybatis-vs-mp.md`：XML 手写 vs MP 对比（代码量 / 性能 / 可读性）

## 验收清单

| 项 | 标准 |
|----|------|
| 老 Mapper 不报错 | 所有原有集成测试通过 |
| MP CRUD 可用 | `selectById/insert/updateById` 正常 |
| 逻辑删除 | `deleteById` 实际执行 UPDATE is_deleted=1 |
| 自动填充 | insert 后 createdAt / updatedAt 非空 |
| 防全表更新 | 无 WHERE 的 update 被 BlockAttack 拦截 |
| 读写路由 | find* 走 slave 配置 |

## 扩展挑战

1. **Mapper 自动生成**：用 MP 代码生成器 `FastAutoGenerator` 反向从 DB 生成所有 Mapper，对比人工写的差异。
2. **动态 TableName**：`@InterceptorIgnore + DynamicTableNameInnerInterceptor` 实现按月分表（`post_202501` / `post_202502`）。
3. **乐观锁**：`@Version int version` 字段 + `OptimisticLockerInnerInterceptor`，并发 update 验证只有一个成功。
4. **数据权限**：自定义 `DataPermissionHandler`，用户只能查自己的文章（WHERE user_id = current_user_id 自动注入）。
