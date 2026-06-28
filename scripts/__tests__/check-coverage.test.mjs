import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  extractTheoryPoints,
  extractDemoEntities,
  extractCheckCorpus,
  computeCoverage,
} from '../check-coverage.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fx = (name) => join(__dirname, 'fixtures', name);

test('extractTheoryPoints: returns meaningful H2/H3 titles, skips H1 and meta titles', () => {
  const md = `# title\n\n## 核心概念\n\n## 流程控制\n\n## 短路求值\n\n### 一、xx\n\n### 1. 为什么需要这一章\n\n### HashMap 底层结构\n`;
  // "核心概念" / "为什么需要这一章" 元词被过滤; "一、xx" 剥编号后剩 "xx" 不足 3 字被过滤
  assert.deepEqual(extractTheoryPoints(md), ['流程控制', '短路求值', 'HashMap 底层结构']);
});

test('extractDemoEntities: extracts class and public methods from java blocks only', () => {
  const md = '```java\npublic class A {\n  public void foo() {}\n}\n```\n\n```text\nclass B {}\n```\n';
  const entities = extractDemoEntities(md);
  const names = entities.map((e) => e.name).sort();
  assert.deepEqual(names, ['A', 'foo']);
});

test('extractCheckCorpus: lowercased full document', () => {
  const md = '### Q1 解释 FlowDemo?\n\n**考点**: 流程控制\n**参考答案**: 见 ABC';
  const corpus = extractCheckCorpus(md);
  assert.ok(corpus.includes('流程控制'));
  assert.ok(corpus.includes('flowdemo'));
  assert.ok(corpus.includes('abc'));
});

test('computeCoverage: matches theory point by any substantive token, not full string', async () => {
  // 直接验证 computeCoverage 对带前缀 + 多 token 标题的处理
  const result = await computeCoverage({
    theoryPath: fx('multitoken-theory.md'),
    demoPath: fx('demo.md'),
    checkPath: fx('multitoken-check.md'),
  });
  // theory: ["2. synchronized 的 4 种用法 + 锁升级", "HashMap 底层结构"]
  // check 提到 "synchronized" 和 "HashMap" → 两个都覆盖
  assert.equal(result.theoryTotal, 2);
  assert.equal(result.theoryHit, 2);
});

test('computeCoverage: with fixtures returns hit counts', async () => {
  const result = await computeCoverage({
    theoryPath: fx('theory.md'),
    demoPath: fx('demo.md'),
    checkPath: fx('check.md'),
  });
  assert.equal(result.theoryTotal, 3);
  assert.equal(result.theoryHit, 1, '只有「流程控制」被命中');
  assert.deepEqual(result.missingTheoryPoints.sort(), ['浮点精度', '短路求值']);

  assert.equal(result.demoTotal, 4); // FlowDemo, main, PrecisionDemo, add
  assert.equal(result.demoHit, 1, '只有 FlowDemo 在 Q2 题干里出现');
  assert.ok(result.missingDemoEntities.includes('PrecisionDemo'));
});
