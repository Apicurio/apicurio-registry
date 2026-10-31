// Docs-only PR classification.
//
// The single place that decides whether a PR changes only documentation, read
// by Quick Check (to skip the Java and UI builds), PR validation (milestone
// exempt, issue link advisory) and docs auto-approval. The definition lives in
// the docs_only section of .github/pr-lifecycle.yml.
//
// Usable as a module, or as a CLI from a workflow:
//   node docs-only.js <pr-lifecycle.json>  < files.json
// where files.json is a JSON array of file names or of pulls.listFiles
// entries; prints docs-only=true|false in $GITHUB_OUTPUT format.

const fs = require('fs');
const { globToRegExp } = require('./pr-reviewer-assignment.js');

/**
 * Every name a changed file is known by. A renamed file changes its previous
 * location too, so a file moved into docs/ from elsewhere is not docs-only.
 */
function namesOf(file) {
  return [file.filename, file.previous_filename].filter(Boolean);
}

/**
 * A predicate matching any of the globs. ignoreCase is for the lists that
 * guard against something (exclude, review_required_paths): a file named
 * Security.adoc or run.SH must not slip past them on its case.
 */
function matcher(globs, { ignoreCase = false } = {}) {
  const patterns = (globs || []).map(globToRegExp).map(p => (ignoreCase ? new RegExp(p.source, 'i') : p));
  return name => patterns.some(p => p.test(name));
}

/**
 * True when the PR has at least one file and every name of every file matches
 * settings.paths and none matches settings.exclude. Missing settings classify
 * nothing as docs-only, so removing the config section switches the fast path
 * off rather than on.
 *
 * @param {Array<{filename: string, previous_filename?: string}>} files the PR's files
 * @param {{paths?: string[], exclude?: string[]}} settings the docs_only config section
 * @returns {boolean}
 */
function isDocsOnly(files, settings) {
  if (!settings || !(settings.paths || []).length || !files.length) return false;
  const included = matcher(settings.paths);
  const excluded = matcher(settings.exclude, { ignoreCase: true });
  return files.every(f => namesOf(f).every(n => included(n) && !excluded(n)));
}

/**
 * The files (by current name) that need a human review even in a docs-only PR:
 * those whose current or previous name matches settings.review_required_paths,
 * ignoring case.
 *
 * @param {Array<{filename: string, previous_filename?: string}>} files the PR's files
 * @param {{review_required_paths?: string[]}} settings the docs_only config section
 * @returns {string[]}
 */
function reviewRequiredFiles(files, settings) {
  const required = matcher(settings && settings.review_required_paths, { ignoreCase: true });
  return files.filter(f => namesOf(f).some(required)).map(f => f.filename);
}

function main(argv) {
  const config = JSON.parse(fs.readFileSync(argv[2], 'utf8'));
  // File names, or file objects as pulls.listFiles returns them (which keep
  // the previous name of a renamed file).
  const entries = JSON.parse(fs.readFileSync(0, 'utf8'));
  const files = entries.map(e => (typeof e === 'string' ? { filename: e } : e));
  process.stdout.write(`docs-only=${isDocsOnly(files, config.docs_only)}\n`);
}

if (require.main === module) {
  main(process.argv);
}

module.exports = { isDocsOnly, reviewRequiredFiles };
