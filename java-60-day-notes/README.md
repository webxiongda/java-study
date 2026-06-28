# Java 60 天手敲笔记

这个文件夹只做一件事：把仓库里的 60 个 Java 学习章节映射成 60 天手敲笔记。

你每天打开对应的 `days/day-XX-*.md`，把自己当天理解的内容、代码验证、问题和复盘写进去。这里不追求写得漂亮，重点是你自己一行一行敲、每天能留下学习痕迹。

## 目录

- `60-day-map.md`：60 天和 `chapters/` 的映射表
- `days/`：每天一份手敲笔记
- `template.md`：以后新增或重置笔记时用的模板

## 每天怎么写

1. 打开当天文件，比如 `days/day-01-环境搭建与Java初印象.md`
2. 对照里面的“对应章节”去看 `chapters/xx`
3. 在“我手敲的笔记”里用自己的话写，不要复制原文
4. 在“代码验证”里记录你实际敲过的代码、命令或运行结果
5. 在“今日问题”里写不懂的点，第二天优先补

## 启动学习网站

只看前端：

```bash
npm run client
```

完整启动前后端：

```bash
npm run db:up
DB_PASSWORD=java_study_root JWT_SECRET=java-study-local-secret-1234567890 npm run dev
```

