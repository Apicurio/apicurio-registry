// Tests for docs-check.js — run with:
//   node --test .github/scripts/docs-check.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const check = require('./docs-check.js');

const BASE_ROOT = '/work/base';
const HEAD_ROOT = '/work/head';

const entry = (root, level, msg, file) => JSON.stringify({
  level, msg, name: 'asciidoctor',
  ...(file ? { file: { path: `${root}/${file}` }, source: { worktree: root } } : {}),
});

const PAGE = 'docs/modules/ROOT/pages/a.adoc';

test('parseLog keys entries by level, path relative to the worktree, and message', () => {
  const log = [entry(HEAD_ROOT, 'warn', 'skipping reference to missing attribute: prefix', PAGE), ''].join('\n');
  assert.deepEqual(check.parseLog(log), [
    { level: 'warn', file: PAGE, msg: 'skipping reference to missing attribute: prefix' },
  ]);
});

test('parseLog keeps messages that have no file', () => {
  const log = entry(HEAD_ROOT, 'error', 'ui bundle not found');
  assert.deepEqual(check.parseLog(log), [{ level: 'error', file: '', msg: 'ui bundle not found' }]);
});

test('parseLog reports non-JSON lines (e.g. a crash) as errors', () => {
  assert.deepEqual(check.parseLog('TypeError: boom'), [{ level: 'error', file: '', msg: 'TypeError: boom' }]);
});

test('parseLog ignores info and debug entries', () => {
  assert.deepEqual(check.parseLog(entry(HEAD_ROOT, 'info', 'hello', PAGE)), []);
});

test('a warning already on the base is not new', () => {
  const base = check.parseLog(entry(BASE_ROOT, 'warn', 'missing attribute: prefix', PAGE));
  const head = check.parseLog(entry(HEAD_ROOT, 'warn', 'missing attribute: prefix', PAGE));
  assert.deepEqual(check.compare(base, head), { added: [], existing: head });
});

test('a warning the base does not have is new', () => {
  const head = check.parseLog(entry(HEAD_ROOT, 'warn', 'missing attribute: registyr', PAGE));
  assert.deepEqual(check.compare([], head).added, head);
});

test('one more occurrence of an existing warning is new', () => {
  const one = entry(BASE_ROOT, 'warn', 'missing attribute: prefix', PAGE);
  const base = check.parseLog(one);
  const head = check.parseLog([one, one].join('\n'));
  const result = check.compare(base, head);
  assert.equal(result.added.length, 1);
  assert.equal(result.existing.length, 1);
});

test('a broken xref is new when the base has none', () => {
  const head = check.parseLog(entry(HEAD_ROOT, 'error', 'target of xref not found: nope.adoc', PAGE));
  assert.deepEqual(check.compare([], head).added, head);
});

test('fixing warnings is never a failure', () => {
  const base = check.parseLog(entry(BASE_ROOT, 'warn', 'missing attribute: prefix', PAGE));
  assert.deepEqual(check.compare(base, []), { added: [], existing: [] });
});

test('CLI exits 1 and annotates new problems', () => {
  const { status, stdout } = runCli(
    entry(BASE_ROOT, 'warn', 'missing attribute: prefix', PAGE),
    [entry(HEAD_ROOT, 'warn', 'missing attribute: prefix', PAGE),
      entry(HEAD_ROOT, 'error', 'target of xref not found: nope.adoc', PAGE)].join('\n'));
  assert.equal(status, 1);
  assert.match(stdout, /::error file=docs\/modules\/ROOT\/pages\/a\.adoc::target of xref not found: nope\.adoc/);
  assert.doesNotMatch(stdout, /::error[^\n]*missing attribute: prefix/);
});

test('CLI exits 0 when nothing is new', () => {
  const line = entry(BASE_ROOT, 'warn', 'missing attribute: prefix', PAGE);
  const { status, stdout } = runCli(line, line.replace(BASE_ROOT, HEAD_ROOT).replace(BASE_ROOT, HEAD_ROOT));
  assert.equal(status, 0);
  assert.match(stdout, /no new errors or warnings/);
});

function runCli(baseLog, headLog) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'docs-check-'));
  fs.writeFileSync(path.join(dir, 'base.log'), baseLog);
  fs.writeFileSync(path.join(dir, 'head.log'), headLog);
  try {
    const stdout = execFileSync('node', [path.join(__dirname, 'docs-check.js'),
      path.join(dir, 'base.log'), path.join(dir, 'head.log')], { encoding: 'utf8' });
    return { status: 0, stdout };
  } catch (e) {
    return { status: e.status, stdout: e.stdout };
  }
}
