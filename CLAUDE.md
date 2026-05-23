# java-study

自建的 Java 学习工作台 + 面试备战平台。后端 Spring Boot 3.3.5（Java 21，MySQL + Flyway + Spring Security/JWT），前端 React 19 + Vite，内容为 60 章学习闭环 + 面试题库（`interview-bank.md` 与各章 `03-check.md` 的 Q5）。最终目标是**通过 Java 后端开发岗位面试**。

## 协作流程（强制走 superpowers）

本仓库由 Claude Code 与 Codex CLI 两端共同维护，所有非琐碎任务一律走 [obra/superpowers](https://github.com/obra/superpowers) 方法论。任意一端开始工作前先按以下顺序激活技能：

1. **`brainstorming` / `writing-plans`** — 把意图整理为 spec 与 plan，落到 `docs/superpowers/`
2. **`executing-plans`** 或 **`subagent-driven-development`** — 按 plan 的 `- [ ]` 任务推进
3. **`test-driven-development`** — 写代码前先写测试（红 → 绿 → 重构）
4. **`verification-before-completion`** — 收尾前必须用真实环境验证，不许只看类型/编译通过就声称完成
5. **`systematic-debugging`** — 遇到问题先查根因，不要绕过

辅助：`dispatching-parallel-agents`（并行任务）、`using-git-worktrees`（隔离实验）、`requesting-code-review` / `receiving-code-review`（PR 评审）、`finishing-a-development-branch`（合分支前的收尾清单）。

## 文档目录约定

| 路径 | 内容 |
|---|---|
| `docs/superpowers/specs/` | brainstorming 产出的 design / spec，命名 `YYYY-MM-DD-<topic>-design.md` |
| `docs/superpowers/plans/` | writing-plans 产出的可执行 plan，命名 `YYYY-MM-DD-<topic>.md`，任务用 `- [ ]` 复选框 |
| `chapters/` | 学习章节内容（不要在这里写实现代码） |
| `backend/` | Spring Boot 后端 |
| `src/` | React 前端 |

新启动一项工作时，**先在 `docs/superpowers/plans/` 里查有没有未完成的 plan**，有就续作，没有再走 brainstorming。

## 当前正在推进的工作

参考 `docs/superpowers/plans/` 下日期最新的 plan。面试备战模块（5 个 Phase）的总体规划见 `/Users/xiongkuobiao/.claude/plans/java-quiet-parnas.md`，Phase 1 已完成。

## 关键约束

- 后端启动：`DB_PASSWORD= JWT_SECRET=<至少32位> mvn spring-boot:run`（本地 MySQL root 无密码，JWT 非 local profile 会强校验）
- JPA `ddl-auto: none`（schema 由 Flyway 权威管理，`@Lob String` 与 MySQL 校验不兼容）
- Commit 前确认所有改动都已落 plan 复选框，未在 plan 中的改动需先回 brainstorming
