# 03-check.md 模板规范

> 适用范围:`chapters/**/03-check.md` 的所有章节自测题
> 配套脚本:`scripts/check-coverage.mjs` 自动校验"理论+demo 覆盖率"
> 反向链接:`interview-bank.md` 已为 35 道题加锚点(`<a id="kebab-slug"></a>`)

## 字段规范

每道题严格 5 字段:

```markdown
### Q{n} [L{1-3}·{类型}·{用途}] 题干

**考点**: 一行,3 个以内关键词,必须与本章 01-theory.md 的 H2/H3 或 02-demo.md 的类/方法名对齐
**参考答案**: 正文(代码题给完整可运行片段)
**🔥追问**: 选填;L2+ 必填,贴近 interview-bank 的大厂追问风格
**关联**: interview-bank.md#kebab-slug (若该题对应 bank 已有条目,无则省略)
```

### 难度

| 标签 | 含义 | 比例 |
|---|---|---|
| L1 | 概念背诵,30s 内答出 | Q1-Q2 |
| L2 | 原理 + 场景,需要 2-5 分钟讲清 | Q3-Q4 |
| L3 | 源码 + 系统设计,面试加分题 | Q5(或里程碑章) |

### 类型

`概念` / `代码阅读` / `代码编写` / `Debug` / `对比` / `场景设计`

### 用途

`章节内测`(保证学了) / `面试高频`(对标 bank) / `项目实战`(里程碑章)

### 题量分布

- **普通章**:Q1-Q2 L1/章节内测、Q3-Q4 L2/章节内测、Q5 L2-L3/面试高频
- **里程碑章**(10/15/20/30/40/50/55):Q1-Q3 综合 L2、Q4 Debug L2、Q5 L3 系统设计

### 必带的章末 checklist

```markdown
## 通过标准

- [ ] 能默写 {本章核心知识点 1}
- [ ] 能讲清 {核心知识点 2 的 why}
- [ ] 能识别 {Q3 Debug 题的 N 个问题}
- [ ] 能现场实现 {Q4 编码题}
- [ ] 能讲 {Q5 场景题的方案}
```

## 完整示例(参考 chapters/53-并发安全/03-check.md 改造后版本)

````markdown
### Q1 [L2·对比·章节内测] synchronized vs ReentrantLock 详细对比

**考点**: synchronized, ReentrantLock, AQS
**参考答案**:

| 维度 | synchronized | ReentrantLock |
|---|---|---|
| 实现 | JVM 内置 | AQS |
| 释放 | 自动 | 手动 finally unlock |
| ... | ... | ... |

**🔥追问**: 虚拟线程内的 synchronized 为什么会 pin 载体线程?
**关联**: interview-bank.md#sync-vs-reentrantlock
````

## 覆盖率校验

每改完一章立刻跑:

```bash
node scripts/check-coverage.mjs '^53-'
```

**通过门槛**:理论覆盖率 ≥ 80%、demo 实体引用率 ≥ 70%。不达标则补题。

## 改造前后对照(以 ch02 Q3 为例)

**改造前**:

```markdown
### Q3（实操）：下面代码有什么问题？请修复。
```

**改造后**:

```markdown
### Q3 [L2·Debug·章节内测] 下面代码隐藏 3 个陷阱,找出并修复

**考点**: 整数除法精度, byte 越界, 浮点精度
**参考答案**: ...
**🔥追问**: 为什么 `0.1 + 0.2 != 0.3`?用什么类型存金额?
**关联**: (基础题,bank 无对应)
```

## 后续 52 章作者协作约定

1. **先跑基线**:`node scripts/check-coverage.mjs '^XX-'` 看本章漏题清单
2. **照模板写 5 题**:Q1-Q5 必带 5 字段,Q5 必关联 bank(若无对应条目则在 bank 补一条 + 加锚点)
3. **再跑校验**:覆盖率达标即可提 PR
4. **复习 ch53** 作为标准范本
