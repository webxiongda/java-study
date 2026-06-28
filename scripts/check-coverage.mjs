#!/usr/bin/env node
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const HEADING_RE = /^(#{2,})\s+(.+?)\s*$/gm;
const FENCE_RE = /```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g;
const CLASS_RE = /\b(?:class|interface|enum|record)\s+([A-Z]\w*)/g;
const METHOD_RE = /\b(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s+)?[\w<>\[\],?\s.]+?\s+([a-z]\w*)\s*\(/g;
const Q_HEAD_RE = /^###\s+Q\d+[^\n]*$/gm;
const KAODIAN_RE = /^\*\*考点\*\*[：:]\s*(.+)$/gm;

// 过滤掉纯结构性 / 元信息标题,只保留"知识点"型标题
const META_TITLES = new Set([
  '核心概念', '学习定位', '学习目标', '为什么需要这一章', '总结', '小结',
  '一、学习定位', '二、核心概念', '一、核心概念', '二、学习定位',
  '题目', '参考答案', '通过标准', '一、概念回顾', '二、实战题',
]);
const STRUCTURAL_PREFIX_RE = /^[一二三四五六七八九十]+[、.\s]+|^\d+(\.\d+)*[、.\s]+/;

function isMeaningfulTitle(title) {
  if (META_TITLES.has(title)) return false;
  // 去掉编号前缀后剩余文本若太短 (≤2 字),视为元标题
  const stripped = title.replace(STRUCTURAL_PREFIX_RE, '').trim();
  if (stripped.length < 3) return false;
  if (META_TITLES.has(stripped)) return false;
  return true;
}

export function extractTheoryPoints(md) {
  const titles = [];
  for (const m of md.matchAll(HEADING_RE)) {
    const title = m[2].trim();
    if (title && isMeaningfulTitle(title)) titles.push(title);
  }
  return titles;
}

function stripStructuralPrefix(s) {
  return s.replace(STRUCTURAL_PREFIX_RE, '').trim();
}

// 停用词:中文虚词 / 通用连接词
const STOPWORDS = new Set([
  '的', '是', '在', '与', '和', '或', '及', '等', '从', '到', '为', '把',
  'vs', 'and', 'or', 'the', 'a', 'an', 'of', 'to', 'is',
]);

// 把"2. synchronized 的 4 种用法 + 锁升级" → ["synchronized", "种用法", "锁升级"]
export function tokenize(title) {
  const stripped = stripStructuralPrefix(title);
  const raw = stripped.split(/[\s,，、+\-/\\()（）【】\[\]：:?？.。!！"'`*]+/);
  const tokens = [];
  for (const t of raw) {
    if (!t) continue;
    if (/^\d+$/.test(t)) continue; // 纯数字
    const lc = t.toLowerCase();
    if (STOPWORDS.has(lc)) continue;
    // 中文 token 需 ≥ 2 字, 英文 ≥ 3 字
    const isAscii = /^[\x00-\x7f]+$/.test(t);
    if (isAscii && t.length < 3) continue;
    if (!isAscii && t.length < 2) continue;
    tokens.push(lc);
  }
  return tokens;
}

export function isTheoryPointCovered(title, corpus) {
  // 整串命中(剥编号后)优先
  const stripped = stripStructuralPrefix(title).toLowerCase();
  if (stripped && corpus.includes(stripped)) return true;
  // 否则任一关键 token 命中即覆盖
  const tokens = tokenize(title);
  if (tokens.length === 0) return false;
  return tokens.some((t) => corpus.includes(t));
}

export function extractDemoEntities(md) {
  const entities = [];
  const seen = new Set();
  for (const m of md.matchAll(FENCE_RE)) {
    const lang = m[1].toLowerCase();
    if (lang !== 'java') continue;
    const code = m[2];
    for (const cm of code.matchAll(CLASS_RE)) {
      const name = cm[1];
      const key = `class:${name}`;
      if (!seen.has(key)) {
        seen.add(key);
        entities.push({ kind: 'class', name });
      }
    }
    for (const mm of code.matchAll(METHOD_RE)) {
      const name = mm[1];
      if (['if', 'for', 'while', 'switch', 'return', 'new'].includes(name)) continue;
      const key = `method:${name}`;
      if (!seen.has(key)) {
        seen.add(key);
        entities.push({ kind: 'method', name });
      }
    }
  }
  return entities;
}

export function extractCheckCorpus(md) {
  // 整个文档 lowercase 作为语料 (题干 + 考点 + 答案 + 代码示例都纳入)
  return md.toLowerCase();
}

export async function computeCoverage({ theoryPath, demoPath, checkPath }) {
  const [theoryMd, demoMd, checkMd] = await Promise.all([
    readFile(theoryPath, 'utf8'),
    readFile(demoPath, 'utf8'),
    readFile(checkPath, 'utf8'),
  ]);

  const theoryPoints = extractTheoryPoints(theoryMd);
  const demoEntities = extractDemoEntities(demoMd);
  const corpus = extractCheckCorpus(checkMd);

  const missingTheoryPoints = [];
  let theoryHit = 0;
  for (const point of theoryPoints) {
    if (isTheoryPointCovered(point, corpus)) theoryHit++;
    else missingTheoryPoints.push(point);
  }

  const missingDemoEntities = [];
  let demoHit = 0;
  for (const ent of demoEntities) {
    if (corpus.includes(ent.name.toLowerCase())) demoHit++;
    else missingDemoEntities.push(ent.name);
  }

  return {
    theoryTotal: theoryPoints.length,
    theoryHit,
    missingTheoryPoints,
    demoTotal: demoEntities.length,
    demoHit,
    missingDemoEntities,
  };
}

async function listChapters(chaptersDir) {
  const entries = await readdir(chaptersDir, { withFileTypes: true });
  return entries
    .filter((e) => e.isDirectory() && /^\d{2}-/.test(e.name))
    .map((e) => e.name)
    .sort();
}

function pct(hit, total) {
  if (total === 0) return '—';
  return `${Math.round((hit / total) * 100)}%`;
}

async function main(argv) {
  const repoRoot = fileURLToPath(new URL('..', import.meta.url));
  const chaptersDir = join(repoRoot, 'chapters');
  const reportPath = join(repoRoot, 'docs/superpowers/specs/2026-05-25-check-coverage-report.md');

  const filterArg = argv[0];
  let chapters = await listChapters(chaptersDir);
  if (filterArg) {
    const re = new RegExp(filterArg);
    chapters = chapters.filter((c) => re.test(c));
  }

  const rows = [];
  let totalTheoryHit = 0, totalTheoryTotal = 0, totalDemoHit = 0, totalDemoTotal = 0;
  for (const ch of chapters) {
    const dir = join(chaptersDir, ch);
    const theoryPath = join(dir, '01-theory.md');
    const demoPath = join(dir, '02-demo.md');
    const checkPath = join(dir, '03-check.md');
    if (!existsSync(theoryPath) || !existsSync(checkPath)) {
      rows.push({ ch, skipped: '缺 theory 或 check' });
      continue;
    }
    const demoExists = existsSync(demoPath);
    const result = await computeCoverage({
      theoryPath,
      demoPath: demoExists ? demoPath : theoryPath, // fallback: no demo, count zero entities
      checkPath,
    });
    if (!demoExists) {
      result.demoTotal = 0;
      result.demoHit = 0;
      result.missingDemoEntities = [];
    }
    rows.push({ ch, ...result });
    totalTheoryHit += result.theoryHit;
    totalTheoryTotal += result.theoryTotal;
    totalDemoHit += result.demoHit;
    totalDemoTotal += result.demoTotal;
  }

  const lines = [];
  lines.push('# 自测题覆盖率报告');
  lines.push('');
  lines.push(`> 生成时间: ${new Date().toISOString()}`);
  lines.push(`> 章节数: ${rows.length}`);
  lines.push(`> 总覆盖率: 理论 ${pct(totalTheoryHit, totalTheoryTotal)} (${totalTheoryHit}/${totalTheoryTotal}), demo ${pct(totalDemoHit, totalDemoTotal)} (${totalDemoHit}/${totalDemoTotal})`);
  lines.push('');
  lines.push('| 章节 | 理论覆盖 | demo 覆盖 | 漏问理论点 | 漏引 demo 实体 |');
  lines.push('|---|---|---|---|---|');
  for (const r of rows) {
    if (r.skipped) {
      lines.push(`| ${r.ch} | — | — | — | ${r.skipped} |`);
      continue;
    }
    const missTheory = r.missingTheoryPoints.slice(0, 5).join(' / ') + (r.missingTheoryPoints.length > 5 ? ` …(+${r.missingTheoryPoints.length - 5})` : '');
    const missDemo = r.missingDemoEntities.slice(0, 5).join(' / ') + (r.missingDemoEntities.length > 5 ? ` …(+${r.missingDemoEntities.length - 5})` : '');
    lines.push(`| ${r.ch} | ${pct(r.theoryHit, r.theoryTotal)} (${r.theoryHit}/${r.theoryTotal}) | ${pct(r.demoHit, r.demoTotal)} (${r.demoHit}/${r.demoTotal}) | ${missTheory || '—'} | ${missDemo || '—'} |`);
  }
  lines.push('');

  const report = lines.join('\n');
  await writeFile(reportPath, report, 'utf8');
  console.log(report);
  console.log(`\n报告已写入: ${reportPath}`);
}

const isMain = import.meta.url === `file://${process.argv[1]}`;
if (isMain) {
  main(process.argv.slice(2)).catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
