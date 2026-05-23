# Chapter 57 测试策略 - 自测与验收

## Q1 概念: 测试金字塔三层 + 比例 + 反模式?

| 层 | 占比 | 速度 | 工具 |
|---|---|---|---|
| 单元 (Unit) | 70% | < 100ms | JUnit5 + Mockito |
| 集成 (Integration) | 25% | 1-5s | SpringBootTest + Testcontainers |
| E2E | 5% | 5-30s | MockMvc / RestAssured / Cypress |

**反模式**:
- **冰淇淋甜筒**: 顶部一坨 E2E, 底部没单元 → 跑一次 30 分钟, 改任何东西都炸
- **沙漏**: 单元多 + E2E 多, 但没集成 → 单元都过但联调出问题
- **倒金字塔**: 全 E2E → 慢, 失败定位难, CI 卡死

**为什么单元最多**: 单元跑得快 → 反馈快 → 重构敢做; 单元定位准 → 出错知道哪个方法。

---

## Q2 概念: `@Mock` `@MockBean` `@SpyBean` 区别?

| 注解 | 类型 | 容器 | 用途 |
|---|---|---|---|
| `@Mock` | Mockito | 不进 Spring | 纯单元测试 (`@ExtendWith(MockitoExtension.class)`) |
| `@MockBean` | Spring Boot | 替换 Spring Bean | `@SpringBootTest` / `@WebMvcTest` 里把某 Bean 替换为 mock |
| `@SpyBean` | Spring Boot | 包装 Spring Bean | 真实调用但能 verify, 也能 stub 部分方法 |
| `@InjectMocks` | Mockito | - | 自动把 `@Mock` 注入被测对象 |

```java
// 纯单测 (快)
@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository repo;
    @InjectMocks PostService service;
}

// Web 切片 (中)
@WebMvcTest(PostController.class)
class PostControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PostService service;   // Controller 依赖的 Service 被 mock 掉
}
```

**坑**: 不要在 `@SpringBootTest` 里大量用 `@MockBean`, 每次都重建 ApplicationContext → 测试龟速。 一个 `@MockBean` ≈ 重启一次 Spring。

---

## Q3 代码题: 给下面的 Service 写完整测试

```java
@Service
public class CouponService {
    @Autowired CouponRepository repo;
    @Autowired UserService userService;
    @Autowired MeterRegistry metrics;

    public Coupon claim(Long userId, String code) {
        Coupon c = repo.findByCode(code)
            .orElseThrow(() -> new CouponNotFoundException(code));
        if (c.getRemain() <= 0) throw new CouponSoldOutException(code);
        if (!userService.canClaim(userId, code)) {
            throw new DuplicateClaimException(userId, code);
        }
        c.setRemain(c.getRemain() - 1);
        repo.save(c);
        metrics.counter("coupon.claim", "code", code).increment();
        return c;
    }
}
```

**参考实现**:

```java
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository repo;
    @Mock UserService userService;
    @Spy MeterRegistry metrics = new SimpleMeterRegistry();
    @InjectMocks CouponService service;

    @Test
    @DisplayName("正常领取")
    void shouldClaim_whenValid() {
        var c = Coupon.builder().code("X").remain(10).build();
        when(repo.findByCode("X")).thenReturn(Optional.of(c));
        when(userService.canClaim(1L, "X")).thenReturn(true);
        when(repo.save(any())).thenReturn(c);

        Coupon result = service.claim(1L, "X");

        assertThat(result.getRemain()).isEqualTo(9);
        verify(repo).save(c);
        assertThat(metrics.counter("coupon.claim", "code", "X").count()).isEqualTo(1.0);
    }

    @Test
    void shouldThrow_whenCouponNotFound() {
        when(repo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(1L, "X"))
            .isInstanceOf(CouponNotFoundException.class);

        verifyNoInteractions(userService);
        verify(repo, never()).save(any());
    }

    @Test
    void shouldThrow_whenSoldOut() {
        var c = Coupon.builder().code("X").remain(0).build();
        when(repo.findByCode("X")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.claim(1L, "X"))
            .isInstanceOf(CouponSoldOutException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void shouldThrow_whenDuplicate() {
        var c = Coupon.builder().code("X").remain(10).build();
        when(repo.findByCode("X")).thenReturn(Optional.of(c));
        when(userService.canClaim(1L, "X")).thenReturn(false);

        assertThatThrownBy(() -> service.claim(1L, "X"))
            .isInstanceOf(DuplicateClaimException.class);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("remain 临界值 1 时也能领, 之后归 0")
    void shouldClaim_whenRemainExactlyOne() {
        var c = Coupon.builder().code("X").remain(1).build();
        when(repo.findByCode("X")).thenReturn(Optional.of(c));
        when(userService.canClaim(1L, "X")).thenReturn(true);
        when(repo.save(any())).thenReturn(c);

        Coupon result = service.claim(1L, "X");

        assertThat(result.getRemain()).isZero();
    }
}
```

**点评**: 4 个分支 (正常 / 不存在 / 卖完 / 重复) + 1 个边界 (remain=1)。 用 `verify(..., never())` 验证 **没发生** 的调用 (关键 — 防止"卖完了还扣库存"这种 bug)。

---

## Q4 代码题: 写一个集成测试用 Testcontainers, 测试事务回滚

```java
@SpringBootTest
@Testcontainers
class TransferIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("bank").withUsername("t").withPassword("t");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired TransferService service;
    @Autowired AccountRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        repo.save(new Account(1L, "Alice", new BigDecimal("100")));
        repo.save(new Account(2L, "Bob", new BigDecimal("50")));
    }

    @Test
    void shouldRollback_whenTargetFrozen() {
        // 把 Bob 账户设为冻结
        Account bob = repo.findById(2L).orElseThrow();
        bob.setFrozen(true);
        repo.save(bob);

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("30")))
            .isInstanceOf(AccountFrozenException.class);

        // 关键: Alice 钱不能被扣
        assertThat(repo.findById(1L).orElseThrow().getBalance())
            .isEqualByComparingTo("100");
        assertThat(repo.findById(2L).orElseThrow().getBalance())
            .isEqualByComparingTo("50");
    }

    @Test
    void shouldTransfer_andCommit() {
        service.transfer(1L, 2L, new BigDecimal("30"));

        assertThat(repo.findById(1L).orElseThrow().getBalance())
            .isEqualByComparingTo("70");
        assertThat(repo.findById(2L).orElseThrow().getBalance())
            .isEqualByComparingTo("80");
    }
}
```

**为什么不用 `@Transactional` 测试方法**: 转账测试要验证 **真实 commit 的事务行为**, 如果 test 方法上加 `@Transactional`, 整个测试在事务里跑, 最后会被框架回滚 — 你测的根本不是真实事务行为。 用 `@BeforeEach deleteAll` 手动清理。

---

## Q5 综合: 一个项目从无测试到完整测试体系, 怎么落地?

**阶段 1: 立基础设施 (1 天)**

```bash
# 1. pom 加依赖: spring-boot-starter-test + testcontainers
# 2. JaCoCo 接入, 配置覆盖率阈值 (起步 30%)
# 3. CI 强制跑 mvn test, 失败阻断合并
# 4. 写 5 个示范测试 (各种典型场景), 团队 review 形成共识
```

**阶段 2: 补关键路径 (1-2 周)**

```
优先级排序:
1. 涉及钱的 (支付/退款/优惠券)      - 必须 100% 覆盖, 含边界
2. 涉及权限的 (登录/RBAC)            - 必须有失败用例
3. 主流程 (创建文章/评论/点赞)        - 集成测试覆盖
4. 工具类 / 纯函数                  - 单元测试 100%
5. CRUD 简单方法                    - 抽样 + 边界
```

**阶段 3: 重构 + 提升 (持续)**

```
- 每个 bug 修复必须先写一个失败的测试 (回归测试)
- 覆盖率阈值: 30% → 50% → 60% → 70% (季度提升)
- 慢测试 (> 5s) 标 @Tag("slow"), 夜间跑
- 引入 PIT mutation testing, 找出 "看起来覆盖了但没断言" 的方法
- 集成测试用 Testcontainers, 不用 H2
```

**阶段 4: 工程化 (上线后)**

```
- CI 拆 fast (单元) + slow (集成) 两阶段, PR 只跑 fast
- 测试报告自动评论到 PR (Codecov / SonarQube)
- 合并主干前 fast + slow + 静态扫描全过
- 生产 bug 倒推 → "为什么这个测试没覆盖到?" 写 RCA
```

**反面教训**:

> 我之前一个项目, 上来就立 80% 覆盖率, 大家为了刷数字写大量 `assertNotNull(result)` 的水测试, 一年后 PIT score 才 12% — 改任何代码测试都不挂。 后来改成: 覆盖率阈值低 (50%) + PIT score > 60% + 关键路径强制集成测试, 真正卡掉了 bug。

**面试讲法 (90 秒)**:

> "测试金字塔我会按 7:2:1 分配。 单元用 Mockito + JUnit5 + AssertJ, controller 用 `@WebMvcTest` + MockMvc 切片测, 集成用 `@SpringBootTest` + Testcontainers 起真 MySQL/Redis。 覆盖率用 JaCoCo, 但更看 PIT mutation score 防止水测试。 CI 拆快慢两阶段, 慢测试夜间跑。 关键路径 (钱/权限) 必须有正向 + 反向 + 边界三类用例。 我之前博客项目从 0 覆盖到 65% 用了两周, P1 bug 数下降 70%。"

---

## 通过标准

- [ ] 能讲清测试金字塔比例 + 反模式 + 选择理由
- [ ] 区分 `@Mock` `@MockBean` `@SpyBean` 使用场景
- [ ] 能给 Service 写正向 + 反向 + 边界完整测试套
- [ ] 能用 Testcontainers 写真实 DB 集成测试
- [ ] 能讲从 0 到完整测试体系的落地路径
