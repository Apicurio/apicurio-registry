// Docs check: compares the Antora logs of a PR's docs and of its base.
//
// verify-docs.yaml builds the docs twice with Antora's JSON log format, once
// for the PR head and once for the merge base, then runs:
//   node docs-check.js <base.log> <head.log>
// Exits 1 when the head has an error or warning (broken xref, missing include,
// unknown attribute, ...) that the base does not have, and annotates each one.
// Comparing with the base rather than failing on every warning lets the check
// hold the line on new problems while the existing ones (literal {placeholder}
// text in code samples, mostly) are cleaned up separately.

const fs = require('fs');

// Antora's levels at or above warn. fatal stops the build.
const REPORTED_LEVELS = new Set(['warn', 'error', 'fatal']);

/**
 * The warn-or-worse entries of an Antora JSON log, keyed by what identifies a
 * problem across two checkouts: level, file path relative to the worktree, and
 * message. Line numbers are left out on purpose: an edit above a warning moves
 * it without making it new.
 *
 * @param {string} text the log, one JSON object per line
 * @returns {Array<{level: string, file: string, msg: string}>}
 */
function parseLog(text) {
  const entries = [];
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    let data;
    try {
      data = JSON.parse(line);
    } catch (e) {
      // Antora prints a crash as plain text; that is a failure too.
      entries.push({ level: 'error', file: '', msg: line.trim() });
      continue;
    }
    if (!REPORTED_LEVELS.has(data.level)) continue;
    entries.push({ level: data.level, file: relativePath(data), msg: data.msg || '' });
  }
  return entries;
}

function relativePath(data) {
  const file = data.file && data.file.path;
  if (!file) return '';
  const root = data.source && data.source.worktree;
  return root && file.startsWith(`${root}/`) ? file.slice(root.length + 1) : file;
}

const keyOf = e => `${e.level}\u0000${e.file}\u0000${e.msg}`;

/**
 * Splits the head's entries into those the base also has (counting
 * occurrences, so one more copy of an existing warning is new) and those it
 * does not.
 *
 * @returns {{added: Array, existing: Array}}
 */
function compare(baseEntries, headEntries) {
  const remaining = new Map();
  for (const e of baseEntries) remaining.set(keyOf(e), (remaining.get(keyOf(e)) || 0) + 1);
  const added = [];
  const existing = [];
  for (const e of headEntries) {
    const left = remaining.get(keyOf(e)) || 0;
    if (left > 0) {
      remaining.set(keyOf(e), left - 1);
      existing.push(e);
    } else {
      added.push(e);
    }
  }
  return { added, existing };
}

function annotate(e) {
  const where = e.file ? ` file=${e.file}` : '';
  return `::error${where}::${e.msg} (Antora ${e.level})`;
}

function main(argv) {
  const base = parseLog(fs.readFileSync(argv[2], 'utf8'));
  const head = parseLog(fs.readFileSync(argv[3], 'utf8'));
  const { added, existing } = compare(base, head);
  console.log(`Docs check: ${existing.length} problem(s) already on the base branch, ${added.length} new.`);
  if (added.length === 0) {
    console.log('Docs check passed: no new errors or warnings.');
    return 0;
  }
  for (const e of added) console.log(annotate(e));
  console.log('Fix the problems above. Errors are broken xrefs or includes; warnings are usually an '
    + 'attribute reference with a typo, or a literal {placeholder} that needs escaping as \\{placeholder}.');
  return 1;
}

if (require.main === module) {
  process.exitCode = main(process.argv);
}

module.exports = { parseLog, compare };
