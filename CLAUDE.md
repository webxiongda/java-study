# java-study-workbench

自建的 Java 学习工作台 + 面试备战平台。后端 Spring Boot 3.3.5（Java 21，MySQL + Flyway + Spring Security/JWT），前端 React 19 + Vite。

由 `~/.claude/CLAUDE.md` 全局配置统一管理。本文件仅覆盖项目特有内容。

## 项目特有覆盖

- superpowers 协作流程详见全局配置「Java + Spring Boot 项目」章节
- 当前正在推进的工作见 `docs/superpowers/plans/` 下日期最新的 plan
- 面试备战模块总体规划见 `~/.claude/plans/java-quiet-parnas.md`

## 关键约束

- 后端启动：`DB_PASSWORD= JWT_SECRET=<至少32位> mvn spring-boot:run`（本地 MySQL root 无密码，JWT 非 local profile 会强校验）
- JPA `ddl-auto: none`（schema 由 Flyway 权威管理，`@Lob String` 与 MySQL 校验不兼容）
- Commit 前确认所有改动都已落 plan 复选框，未在 plan 中的改动需先回 brainstorming
