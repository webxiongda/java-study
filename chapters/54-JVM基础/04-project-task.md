# Chapter 54 JVM 基础 - 项目任务

## 任务概述

把博客 API **生产化部署**:

1. 制定 JVM 启动参数 + 写进 Dockerfile
2. 接 Micrometer + Prometheus + Grafana, 暴露 JVM 指标
3. 配 GC 日志 + heap dump 自动归档
4. 准备 3 个故障演练 (OOM / CPU 高 / GC 长停顿) 的排查脚本
5. 写一份"线上故障 SOP" 文档

## 业务背景

之前章节都在写业务, 没人关心 JVM 怎么调。 真上生产, 没监控、没 GC 日志、没 OOM dump 就是裸奔: 出故障只能瞎猜。 本章把可观测和排查能力做到位。

## 任务拆解

### Step 1: Dockerfile + JVM 参数 (30 分钟)

```dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/blog-api.jar app.jar

ENV JAVA_OPTS="-Xms2g -Xmx2g -Xss512k \
  -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/blog/ \
  -XX:+ExitOnOutOfMemoryError \
  -Xlog:gc*:file=/var/log/blog/gc.log:t,uptime:filecount=10,filesize=50M \
  -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

EXPOSE 8080
VOLUME ["/var/log/blog"]

ENTRYPOINT exec java $JAVA_OPTS -jar /app/app.jar
```

`docker-compose.yml`:
```yaml
services:
  blog:
    build: .
    ports: ["8080:8080"]
    volumes:
      - ./logs:/var/log/blog
    deploy:
      resources:
        limits:
          memory: 3G
          cpus: '2.0'
```

### Step 2: Micrometer + Prometheus (30 分钟)

`pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

`application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: blog-api
      env: ${ENV:dev}
```

JVM 指标自动暴露 (无需写代码):
- `jvm_memory_used_bytes{area="heap"}`
- `jvm_gc_pause_seconds_count`
- `jvm_threads_states_threads{state="runnable"}`
- `jvm_classes_loaded_classes`
- `process_cpu_usage`

### Step 3: docker-compose 加 Prometheus + Grafana (30 分钟)

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - ./grafana-provisioning:/etc/grafana/provisioning
```

`prometheus.yml`:
```yaml
scrape_configs:
  - job_name: blog-api
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets: ["blog:8080"]
```

Grafana 直接导入官方 JVM 面板 ID `4701` (Micrometer JVM Dashboard)。

### Step 4: heap dump 归档 (15 分钟)

OOM 后 `/var/log/blog/` 会出现 `.hprof`。 加定时任务上传 S3 + 通知:

```bash
#!/usr/bin/env bash
# scripts/heap-dump-uploader.sh
for f in /var/log/blog/*.hprof; do
  if [ -f "$f" ]; then
    aws s3 cp "$f" "s3://blog-jvm-dumps/$(hostname)/$(date +%F)/"
    curl -X POST $ALERT_WEBHOOK -d "{\"text\":\"heap dump uploaded: $f\"}"
    rm "$f"
  fi
done
```

`cron`: `*/5 * * * * /app/scripts/heap-dump-uploader.sh`

### Step 5: 故障演练脚本 (30 分钟)

`scripts/chaos/oom.sh`:
```bash
#!/usr/bin/env bash
# 通过 actuator 触发内存占用 (需先在代码里加 /admin/leak 接口, 不上线)
curl -X POST localhost:8080/admin/leak?size=2gb
sleep 30
ls /var/log/blog/*.hprof    # 应能看到 dump 文件
```

`scripts/chaos/cpu-burn.sh`:
```bash
#!/usr/bin/env bash
curl -X POST localhost:8080/admin/cpu-burn?threads=8
sleep 5
PID=$(docker inspect --format '{{.State.Pid}}' blog)
top -H -p $PID -b -n 1 | head
jstack $PID > /tmp/burn.stack
grep -B 2 "RUNNABLE" /tmp/burn.stack | head -50
```

`scripts/diagnose.sh` (前面 Ch53 已经写过, 这里复用):
```bash
#!/usr/bin/env bash
PID=$1
echo "=== Thread state ==="
jstack $PID | grep "java.lang.Thread.State" | sort | uniq -c

echo "=== GC summary (last 100 lines) ==="
docker exec blog tail -100 /var/log/blog/gc.log | grep -E "Pause|Full"

echo "=== Heap ==="
jcmd $PID GC.heap_info

echo "=== Memory areas ==="
jcmd $PID VM.native_memory summary 2>/dev/null || echo "需要 -XX:NativeMemoryTracking=summary"
```

### Step 6: SOP 文档 (30 分钟)

`docs/INCIDENT-SOP.md`:

```markdown
# 线上故障 SOP

## 0. 接到告警 (Grafana / PagerDuty)

1. 确认范围: 单实例 / 单接口 / 全局?
2. 看 Grafana JVM 面板 + 业务面板
3. 决定是 **回滚** 还是 **诊断**

## 1. 接口 503 (后端不响应)

```bash
# 进容器
docker exec -it blog bash
# 1. 健康检查
curl localhost:8080/actuator/health
# 2. CPU
top -H -p 1
# 3. 线程
jstack 1 > /tmp/s.txt
grep "java.lang.Thread.State" /tmp/s.txt | sort | uniq -c
```

## 2. OOM

- heap dump 自动归档到 S3 (5min cron)
- 用 MAT 分析: Dominator Tree 找最大保留对象
- 常见: 缓存无上限 / ThreadLocal 不 remove / Mapper 缓存

## 3. P99 突增

按 Q5 排查矩阵: GC / DB / 下游 / 锁 / 线程池, 每项有 1 个 quick check 命令.
```

## 交付物

- [ ] `Dockerfile` 带完整 JVM 参数
- [ ] `docker-compose.yml` 含 blog + prometheus + grafana
- [ ] `prometheus.yml` + Grafana JVM 面板
- [ ] `heap-dump-uploader.sh` cron 脚本
- [ ] `scripts/chaos/oom.sh` `cpu-burn.sh` `diagnose.sh`
- [ ] `docs/INCIDENT-SOP.md` 线上故障手册
- [ ] git commit: `ch54: production JVM tuning + monitoring + chaos drills`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 启动参数 | `docker exec blog jcmd 1 VM.flags` 能看到 G1GC / Xmx2g 等 |
| Prometheus | `http://localhost:9090/targets` blog UP, JVM 指标可查 |
| Grafana | JVM 面板能看到 heap / GC / threads / classes |
| GC 日志 | `tail logs/gc.log` 有 G1 Pause 日志 |
| OOM 自动 dump | 触发 OOM 后 `logs/*.hprof` 出现, 5 分钟内上传 |
| CPU 排查 | chaos 触发后, `diagnose.sh` 能找出 BURNER 线程 |
| SOP | 同事看 SOP 能 30 分钟内完成 P99 突增的初步排查 |

## 扩展挑战

1. **JFR 持续录制**: 启动 `-XX:StartFlightRecording=duration=60s,filename=app.jfr,settings=profile`
2. **Pyroscope / Parca** 持续 profile, 看哪段代码最耗 CPU
3. **GC 切到 Generational ZGC** (`-XX:+UseZGC -XX:+ZGenerational`), 对比 P99 GC 停顿
4. **NMT (Native Memory Tracking)** 跟踪堆外内存
5. **OOMKilled 兜底**: k8s liveness 探针 + ExitOnOutOfMemoryError + Pod 重启自动告警
