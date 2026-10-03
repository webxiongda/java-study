#!/bin/bash
# java-study 免登录版部署脚本（在 ECS 上执行）
#
# 由 GitHub Actions 通过 `aliyun ecs RunCommand` 下发，或本地 ca-run.sh 兜底执行。
# 幂等：可重复运行；每次替换源码前自动备份，旧 jar 一并留存以便回滚。
#
# 关键事实（探查所得，改脚本前先读）：
#   - 环境变量在 /etc/java-study/java-study.env（systemd EnvironmentFile），站点目录没有 .env
#   - 生产用 H2（DB_URL 指向 data/java-study.mv.db），与 MySQL 无关
#   - CONTENT_ROOT 指向 /www/wwwroot/java-study/content，该目录必须保留
#   - 服务器自带 mvn 3.6.2 太旧（compiler-plugin 3.13 要求 >=3.6.3），已装 3.9.9 到 /usr/local/bin/mvn
set -eo pipefail

SITE_DIR='/www/wwwroot/java-study'
ENVF='/etc/java-study/java-study.env'
BACKEND_PORT=18083
MVN=/usr/local/bin/mvn   # 3.9.9；不要用 /usr/bin/mvn（3.6.2 会构建失败）

echo "=== 0. 前置检查 ==="
[ -x "$MVN" ] || { echo "❌ $MVN 不存在，需先安装 Maven 3.9+"; exit 1; }
"$MVN" -version 2>&1 | head -1
set -a; . "$ENVF"; set +a
echo "DB_URL=${DB_URL:0:55}..."
echo "JWT_SECRET 长度: ${#JWT_SECRET}"

echo "=== 1. 下载源码 ==="
TAR=/tmp/_java-study.tar.gz
rm -f "$TAR"
http=$(curl -sSL -o "$TAR" -w '%{http_code}' --max-time 300 \
  "https://codeload.github.com/webxiongda/java-study/tar.gz/refs/heads/main")
[ "$http" = "200" ] || { echo "❌ 下载失败 HTTP=$http"; exit 1; }
tar -tzf "$TAR" >/dev/null && echo "tar OK ($(du -h $TAR | cut -f1))"

echo "=== 2. 备份并替换源码（保留 data/ content/ node_modules/）==="
BAK="${SITE_DIR}-bak-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BAK"
cd "$SITE_DIR"
cp -a backend/app.jar "$BAK/app.jar" 2>/dev/null || true
for item in backend/src src index.html package.json package-lock.json tsconfig.app.json tsconfig.json tsconfig.node.json public; do
  [ -e "$item" ] && cp -a "$item" "$BAK/" 2>/dev/null || true
done
echo "已备份 -> $BAK"

KEEP=/tmp/_java_keep
rm -rf "$KEEP"; mkdir -p "$KEEP"
for keep in data content node_modules; do
  [ -e "$keep" ] && mv "$keep" "$KEEP/$keep" || true
done
rm -rf backend src dist public index.html chapters
tar -xzf "$TAR" --strip-components=1
rm -f "$TAR"
for keep in data content node_modules; do
  [ -e "$KEEP/$keep" ] && mv "$KEEP/$keep" ./ || true
done
rm -rf "$KEEP"

echo "AutoLoginService: $([ -f backend/src/main/java/com/javastudy/service/AutoLoginService.java ] && echo YES || echo '❌ 缺失')"
echo "content 保留: $([ -d content ] && echo "YES ($(find content -type f | wc -l) 文件)" || echo '❌ 缺失')"
echo "data 保留:    $([ -f data/java-study.mv.db ] && echo YES || echo '❌ 缺失')"

echo "=== 3. 构建前端 ==="
export PATH=/usr/local/bin:$PATH
[ -d node_modules ] || npm install --no-audit --no-fund 2>&1 | tail -3
npm run build 2>&1 | tail -8

echo "=== 4. 构建后端 jar ==="
cd "$SITE_DIR/backend"
[ -f app.jar ] && mv app.jar "app.jar.old-$(date +%s)" || true
export DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET
"$MVN" -B clean package -DskipTests 2>&1 | grep -E "ERROR|BUILD|Building jar" | tail -10
JAR=$(ls -t target/*.jar 2>/dev/null | head -1)
[ -n "$JAR" ] || { echo "❌ 未生成 jar"; exit 1; }
cp "$JAR" app.jar
echo "app.jar: $(du -h app.jar | cut -f1)"
unzip -l app.jar 2>/dev/null | grep -q AutoLoginService \
  && echo "✅ AutoLoginService 已打包" \
  || { echo "❌ jar 内无 AutoLoginService，中止（不重启服务）"; exit 1; }

echo "=== 5. 重启 systemd ==="
systemctl restart java-study.service
for i in $(seq 1 20); do
  sleep 5
  code=$(curl -s -m 4 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${BACKEND_PORT}/api/health" 2>/dev/null || echo 000)
  echo "  探测 ${i}: $code"
  [ "$code" = "200" ] && break
  [ "$i" = "20" ] && { echo "❌ 20 次探测仍未就绪"; journalctl -u java-study.service --since "3 min ago" --no-pager | tail -20; exit 1; }
done

echo "=== 6. 免登录验证（无 Authorization 头）==="
echo -n "health   : "; curl -s -m 8 "http://127.0.0.1:${BACKEND_PORT}/api/health"; echo ""
echo -n "auth/me  : "; curl -s -m 8 "http://127.0.0.1:${BACKEND_PORT}/api/auth/me"; echo ""
for p in summary chapters interview/categories; do
  printf "  %-20s %s\n" "$p:" "$(curl -s -m 8 -o /dev/null -w '%{http_code}' "http://127.0.0.1:${BACKEND_PORT}/api/$p")"
done
echo "chapters 条数: $(curl -s -m 8 "http://127.0.0.1:${BACKEND_PORT}/api/chapters" | grep -o '"no"' | wc -l)"

# 回滚提示（不自动执行，避免误回滚）
echo ""
echo "如需回滚： systemctl stop java-study.service && cp '$BAK/app.jar' '$SITE_DIR/backend/app.jar' && systemctl start java-study.service"
echo "=== JAVA-STUDY DEPLOY DONE ==="