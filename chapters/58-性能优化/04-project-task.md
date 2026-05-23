# Chapter 58 性能优化 - 项目任务

## 任务概述

给博客 API 做 **一次完整的性能优化闭环**:

1. 用 wrk 压测核心 5 个接口, 出基线报告
2. 看慢 SQL / 火焰图 / 监控, 定位 3 个以上瓶颈
3. 针对性优化 (索引 / 缓存 / N+1 / 异步)
4. 重压测对比, 出优化报告
5. 把优化过程写成 PERFORMANCE.md (面试可讲)

## 业务背景

简历上 "QPS 5000" / "P99 30ms" 这种数字, 没数据支撑 = 假。 这章把博客 API 的性能数据从 0 到 1 做出来, 既能优化系统, 也能写进简历。

## 任务拆解

### Step 1: 准备压测环境 (15 分钟)

```bash
# 装 wrk
brew install wrk     # mac
# 或 apt install wrk

# 启动应用 (生产 profile)
docker compose up -d
java -jar app.jar --spring.profiles.active=prod

# 准备测试数据 (10 万文章, 100 万评论, 1 万用户)
mysql blog < scripts/seed-perf.sql
```

### Step 2: 基线压测 (30 分钟)

5 个核心接口:

```bash
mkdir -p reports
SERVER=http://localhost:8080

# 1. 首页列表
wrk -t4 -c200 -d60s --latency $SERVER/api/v1/posts > reports/baseline-list.txt

# 2. 文章详情
wrk -t4 -c200 -d60s --latency $SERVER/api/v1/posts/1 > reports/baseline-detail.txt

# 3. 评论列表
wrk -t4 -c200 -d60s --latency $SERVER/api/v1/posts/1/comments > reports/baseline-comments.txt

# 4. 登录 (需要 POST + JSON body)
wrk -t4 -c100 -d30s -s scripts/login.lua $SERVER > reports/baseline-login.txt

# 5. 搜索
wrk -t4 -c100 -d30s $SERVER/api/v1/posts/search?kw=java > reports/baseline-search.txt
```

`scripts/login.lua`:
```lua
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"username":"alice","password":"alice123"}'
```

**输出表格** (写进 PERFORMANCE.md):

| 接口 | P50 | P99 | QPS | 错误率 |
|---|---|---|---|---|
| 列表 | 80ms | 312ms | 1245 | 0% |
| 详情 | 124ms | 612ms | 745 | 0% |
| 评论 | 45ms | 234ms | 1620 | 0% |
| 登录 | 234ms | 892ms | 320 | 0% |
| 搜索 | 312ms | 1240ms | 145 | 0% |

### Step 3: 定位瓶颈 (45 分钟)

**(a) 慢 SQL**

```bash
docker exec blog-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "
  SET GLOBAL slow_query_log=ON;
  SET GLOBAL long_query_time=0.05;
  SET GLOBAL slow_query_log_file='/var/log/mysql/slow.log';
"

# 重压一遍, 然后看
docker exec blog-mysql mysqldumpslow -s t -t 10 /var/log/mysql/slow.log > reports/slow-sql.txt
```

记录: 每条慢 SQL + EXPLAIN + 改进方案。

**(b) 应用层热点 (JFR)**

```bash
# 加启动参数
java -XX:StartFlightRecording=duration=60s,filename=reports/app.jfr -jar app.jar &
sleep 5

# 同时压测
wrk -t4 -c200 -d60s $SERVER/api/v1/posts/1

# 等 60s 后用 JMC 打开
open -a "JDK Mission Control" reports/app.jfr
```

看: CPU Hot Methods / Memory Allocation / GC Pauses。

**(c) 监控大盘**

打开 Grafana, 截图:
- CPU 使用率
- Heap / Young GC 频率
- Redis QPS / 命中率
- DB 连接池 / QPS
- 慢请求分布

### Step 4: 优化 (90 分钟)

按 ROI 排序, 选 5 个优化:

| 优先级 | 优化 | 预期收益 |
|---|---|---|
| 1 | comments 表加 `(post_id, created_at)` 索引 | 详情 P99 -200ms |
| 2 | 详情接口加 Caffeine + Redis 双层缓存 | P99 -80% |
| 3 | 列表查询解决 N+1 (一次取 author + count) | QPS 2x |
| 4 | 搜索引入 ES 替代 LIKE | 搜索 P99 -90% |
| 5 | 登录 BCrypt 异步化 (登录返回先, 审计异步) | 登录 QPS 3x |

每个优化:
1. 写代码
2. 单元测试通过
3. 单接口压测对比
4. 在 PERFORMANCE.md 记一行 (改前/改后/收益)

### Step 5: 重压测 + 出报告 (30 分钟)

重跑 Step 2 的 5 个压测, 命名 `optimized-*`。

`PERFORMANCE.md` 内容:

```markdown
# 博客 API 性能优化报告

## 环境
- 机器: M2 Pro 16C / 32GB
- JVM: OpenJDK 21, -Xms2g -Xmx2g, G1
- DB: MySQL 8.4, Docker, 4 核 4G
- 数据量: 10w 文章, 100w 评论, 1w 用户

## 优化前后对比

| 接口 | P99 (前) | P99 (后) | QPS (前) | QPS (后) | 收益 |
|---|---|---|---|---|---|
| 列表 | 312ms | 24ms | 1245 | 8500 | 13x QPS |
| 详情 | 612ms | 3ms | 745 | 12000 | 200x P99 |
| 评论 | 234ms | 18ms | 1620 | 9200 | 13x P99 |
| 登录 | 892ms | 120ms | 320 | 1100 | 3x QPS |
| 搜索 | 1240ms | 45ms | 145 | 3400 | 27x P99 |

## 关键优化项

### 1. 详情接口 N+1 + 双层缓存
**改前**: N+1 查 user + 评论无分页, P99 612ms
**改动**: 一次 IN 查 user + LIMIT 20 + Caffeine + Redis
**改后**: P99 3ms (200x 提升)
**代码**: src/main/java/.../PostService.java#getDetail

### 2. comments 表索引
**改前**: WHERE post_id 全表扫, 100w 行
**改动**: `CREATE INDEX idx_post_id_created ON comments(post_id, created_at)`
**改后**: 索引扫 200 行
**EXPLAIN**: type 从 ALL → ref, rows 从 100w → 200

### ... (其他 3 个)

## 副作用与风险
- 双层缓存增加内存 ~200MB
- ES 同步用 binlog 监听, 有 < 1s 延迟
- 异步 BCrypt 失败时审计可能缺失 (有降级)

## 后续 TODO
- 实测 P999 (本次只到 P99)
- 跨可用区压测
- 失败注入测试 (Chaos)
```

### Step 6: CI 接入压测 (可选, 15 分钟)

```yaml
# .github/workflows/perf.yml
name: Performance Test
on:
  schedule: [cron: '0 2 * * *']    # 每晚 2 点

jobs:
  bench:
    runs-on: ubuntu-latest
    services:
      mysql: { image: mysql:8.4, env: { MYSQL_ROOT_PASSWORD: root }, ports: [3306:3306] }
    steps:
      - uses: actions/checkout@v4
      - run: |
          docker run --rm --network host \
            -v $PWD/scripts:/scripts \
            williamyeh/wrk -t4 -c100 -d30s http://localhost:8080/api/v1/posts > result.txt
      - name: Compare with baseline
        run: |
          P99=$(grep "99%" result.txt | awk '{print $2}')
          # 比上周 P99 高 20% 报警
```

## 交付物

- [ ] `reports/baseline-*.txt` 基线压测 5 个
- [ ] `reports/optimized-*.txt` 优化后压测 5 个
- [ ] `reports/slow-sql.txt` 慢 SQL 列表
- [ ] `reports/app.jfr` JFR 录制 (60s)
- [ ] `reports/grafana-*.png` 监控截图 ≥ 3 张
- [ ] `PERFORMANCE.md` 完整报告 (上面格式)
- [ ] 至少 5 个优化, 每个都有 commit + 单接口压测对比
- [ ] git commit: `ch58: comprehensive perf optimization with report`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 数据真实 | 不是 100 行假数据, 是 10 万级 |
| 改一处压一次 | 不一锅端, 每个优化能独立证明收益 |
| EXPLAIN 截图 | 索引前后的 EXPLAIN 都贴出来 |
| 副作用思考 | 至少列 2 个"优化带来的代价" |
| 火焰图 | 至少分析一个热点方法 |
| 报告可写简历 | 能直接复制 2-3 句到简历 |

## 扩展挑战

1. **k6 / Gatling 压测脚本**: 替代 wrk, 支持复杂场景 (登录 → 创建 → 查询 → 删除链路)
2. **持续性能测试**: 每个 PR 跑微基准, P99 退化 > 10% 阻断
3. **JMH 微基准**: 自己代码里有热路径用 JMH 出数据 (如 Jackson 不同模块速度)
4. **Async Profiler 火焰图**: 出 SVG, 用 ctrl-f 找业务方法
5. **链路追踪 (Skywalking / Tempo)**: 看一个慢请求在 10 个服务里的耗时分布
6. **混沌工程**: ChaosMesh 注入网络延迟, 看降级是否生效
