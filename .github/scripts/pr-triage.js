// PR Triage — deterministic quality splitter for external contributions.
//
// Runs machine-checkable items from the Contributor Checklist (CLAUDE.md)
// against PR metadata and patch text fetched via the GitHub API. Produces a
// verdict (green/yellow/red), a triage/* label, and an upserted report comment.
//
// SECURITY: this module analyzes patch TEXT and metadata only. It must never
// execute, check out, or evaluate PR head code — it runs under
// pull_request_target with write permissions.

const MARKER = '<!-- pr-triage-report -->';

const TRIAGE_LABELS = {
  GREEN: 'triage/green',
  YELLOW: 'triage/yellow',
  RED: 'triage/red',
};

const TRIAGE_LABEL_DEFS = {
  [TRIAGE_LABELS.GREEN]:  { color: '5BB85B', description: 'Automated triage passed — ready for maintainer acceptance' },
  [TRIAGE_LABELS.YELLOW]: { color: 'F7BF6A', description: 'Automated triage found issues the author should fix' },
  [TRIAGE_LABELS.RED]:    { color: 'E8836B', description: 'Automated triage found blocking problems' },
};

const DEFAULTS = {
  enabled: true,
  require_linked_issue: true,
  max_diff_lines: 3000,
  max_files: 300,
};

// ---------------------------------------------------------------------------
// Patch parsing helpers (unified diff text from the GitHub API)
// ---------------------------------------------------------------------------

// Returns the added lines of a file patch (without the leading '+').
function addedLines(patch) {
  if (!patch) return [];
  return patch
    .split('\n')
    .filter(l => l.startsWith('+') && !l.startsWith('+++'))
    .map(l => l.slice(1));
}

// Sanitizes attacker-controlled text (titles, filenames, patch fragments) for
// embedding in a markdown code span: strips backticks so the span cannot be
// broken out of, drops newlines, and truncates.
function code(s) {
  return String(s ?? '').replace(/[`\r\n]/g, '').slice(0, 200);
}

function isJavaFile(file) {
  return file.filename.endsWith('.java');
}

function isMainCode(file) {
  return file.filename.includes('src/main/') && isJavaFile(file);
}

function isTestCode(file) {
  return (file.filename.includes('src/test/') || file.filename.includes('/it/') ||
          file.filename.includes('integration-tests/')) && isJavaFile(file);
}

// ---------------------------------------------------------------------------
// Issue reference extraction
// ---------------------------------------------------------------------------

function extractLinkedIssueNumbers(body, owner, repo) {
  if (!body) return [];
  const numbers = new Set();
  const shortRefs = body.matchAll(/(?:^|[\s(:,])#(\d{1,6})\b/g);
  for (const m of shortRefs) numbers.add(parseInt(m[1], 10));
  const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const urlRe = new RegExp(
    `github\\.com/${esc(owner)}/${esc(repo)}/issues/(\\d{1,6})\\b`, 'gi');
  for (const m of body.matchAll(urlRe)) numbers.add(parseInt(m[1], 10));
  return [...numbers];
}

// Target-repo coordinates from the PR object (base side — never the fork).
function prRepoCoords(pr) {
  return { owner: pr.base.repo.owner.login, repo: pr.base.repo.name };
}

// ---------------------------------------------------------------------------
// Checks — each returns a finding object or null.
// data: { pr, commits, files, linkedIssues, openPrs, config, maintainers }
// finding: { id, severity: 'red'|'yellow', title, detail }
// ---------------------------------------------------------------------------

function checkDco({ commits }) {
  const missing = commits
    .filter(c => (c.parents || []).length <= 1)
    .filter(c => !/^Signed-off-by: .+ <.+@.+>/m.test(c.commit.message));
  if (missing.length === 0) return null;
  const shas = missing.slice(0, 5).map(c => `\`${c.sha.slice(0, 7)}\``).join(', ');
  return {
    id: 'dco-missing',
    severity: 'red',
    title: 'Missing DCO sign-off',
    detail: `${missing.length} commit(s) lack a \`Signed-off-by\` trailer (${shas}). ` +
      `Amend with \`git commit --amend -s\` (or \`git rebase --signoff main\`) and force-push.`,
  };
}

function checkLinkedIssue({ pr, linkedIssues, config }) {
  if (!(config.require_linked_issue ?? DEFAULTS.require_linked_issue)) return null;
  if (linkedIssues.length > 0) return null;
  return {
    id: 'no-linked-issue',
    severity: 'red',
    title: 'No linked issue',
    detail: 'The PR description does not reference an issue. Every contribution needs a ' +
      'linked issue with maintainer approval before implementation — edit the description ' +
      'to add `Fixes #<issue>` and use `/triage` to re-check.',
  };
}

function checkIssueApproval({ linkedIssues, maintainers }) {
  if (linkedIssues.length === 0) return null;
  const approved = linkedIssues.some(issue =>
    maintainers.includes(issue.issue.user.login) ||
    issue.comments.some(c => maintainers.includes(c.user.login)));
  if (approved) return null;
  const refs = linkedIssues.map(i => `#${i.issue.number}`).join(', ');
  return {
    id: 'issue-not-approved',
    severity: 'red',
    title: 'Linked issue has no maintainer approval',
    detail: `Issue(s) ${refs} were not opened by a project maintainer and have no comment from one. ` +
      'Feature/fix requests need maintainer sign-off before implementation — ' +
      'otherwise the work may be rejected regardless of quality.',
  };
}

function checkGeneratedFiles({ files }) {
  const bad = files.filter(f =>
    /(^|\/)target\//.test(f.filename) ||
    /\.(jar|class|war|ear|zip|exe|dll|so|dylib)$/.test(f.filename));
  if (bad.length === 0) return null;
  const names = bad.slice(0, 5).map(f => `\`${code(f.filename)}\``).join(', ');
  return {
    id: 'generated-files',
    severity: 'red',
    title: 'Generated or binary files in diff',
    detail: `${bad.length} file(s) under \`target/\` or binary artifacts: ${names}. ` +
      'These are build outputs — remove them from the PR.',
  };
}

function checkTestsPresent({ files }) {
  const mainChanged = files.filter(f => isMainCode(f) && f.additions > 0);
  if (mainChanged.length === 0) return null;
  const testChanged = files.some(f => isTestCode(f));
  if (testChanged) return null;
  return {
    id: 'tests-missing',
    severity: 'yellow',
    title: 'No test changes for production code',
    detail: `${mainChanged.length} file(s) under \`src/main\` changed but no test files were ` +
      'touched. Every new code path needs tests — missing tests are an automatic rejection.',
  };
}

function checkStarImports({ files }) {
  const hits = [];
  for (const f of files.filter(isJavaFile)) {
    for (const line of addedLines(f.patch)) {
      if (/^\s*import\s+(static\s+)?[\w.]+\.\*\s*;/.test(line)) {
        hits.push(f.filename);
        break;
      }
    }
  }
  if (hits.length === 0) return null;
  return {
    id: 'star-imports',
    severity: 'yellow',
    title: 'Star imports added',
    detail: `Wildcard imports in: ${hits.slice(0, 5).map(f => `\`${code(f)}\``).join(', ')}. ` +
      'Use explicit imports (project convention, staged checkstyle rule).',
  };
}

const ALLOWED_CONFIG_PREFIXES = ['apicurio.', 'registry.', 'quarkus.', 'mp.', 'smallrye.', 'kafka.'];

function checkConfigPrefix({ files }) {
  const hits = [];
  for (const f of files.filter(isJavaFile)) {
    for (const line of addedLines(f.patch)) {
      const m = line.match(/@ConfigProperty\s*\(\s*name\s*=\s*"([^"]+)"/);
      if (m && !ALLOWED_CONFIG_PREFIXES.some(p => m[1].startsWith(p))) {
        hits.push(`\`${code(m[1])}\``);
      }
    }
  }
  if (hits.length === 0) return null;
  return {
    id: 'config-prefix',
    severity: 'yellow',
    title: 'Config property outside the apicurio.* namespace',
    detail: `New config properties ${hits.slice(0, 5).join(', ')} do not use the \`apicurio.*\` ` +
      'prefix. Registry properties must follow `.claude/rules/config-properties.md` and carry ' +
      '`@Info` in the `app` module.',
  };
}

function checkLicenseHeaders({ files }) {
  const hits = files
    .filter(f => f.status === 'added' && isJavaFile(f))
    .filter(f => addedLines(f.patch).some(l => /Licensed under the Apache License/i.test(l)))
    .map(f => f.filename);
  if (hits.length === 0) return null;
  return {
    id: 'license-headers',
    severity: 'yellow',
    title: 'Per-file license headers added',
    detail: `New file(s) ${hits.slice(0, 3).map(f => `\`${code(f)}\``).join(', ')} carry an Apache ` +
      'license header. This project does not use per-file headers — the repository-root ' +
      '`LICENSE` governs. Remove them.',
  };
}

function checkLocale({ files }) {
  const hits = [];
  for (const f of files.filter(isJavaFile)) {
    for (const line of addedLines(f.patch)) {
      if (/\.to(Upper|Lower)Case\(\s*\)/.test(line)) {
        hits.push(f.filename);
        break;
      }
    }
  }
  if (hits.length === 0) return null;
  return {
    id: 'locale-missing',
    severity: 'yellow',
    title: 'Case conversion without Locale',
    detail: `\`.toUpperCase()\`/\`.toLowerCase()\` without a \`Locale\` argument in ` +
      `${hits.slice(0, 5).map(f => `\`${code(f)}\``).join(', ')}. Use \`Locale.ROOT\`.`,
  };
}

function checkSynchronizedReactive({ files }) {
  const hits = [];
  for (const f of files.filter(isJavaFile)) {
    if (!f.patch) continue;
    const reactive = /io\.smallrye\.mutiny|Uni<|Multi</.test(f.patch);
    if (!reactive) continue;
    if (addedLines(f.patch).some(l => /\bsynchronized\b/.test(l))) {
      hits.push(f.filename);
    }
  }
  if (hits.length === 0) return null;
  return {
    id: 'synchronized-reactive',
    severity: 'yellow',
    title: 'synchronized in reactive code path',
    detail: `\`synchronized\` added in Mutiny-using file(s): ` +
      `${hits.slice(0, 3).map(f => `\`${code(f)}\``).join(', ')}. Use \`AtomicReference\` + CAS or ` +
      'framework-provided mechanisms — never block reactive threads.',
  };
}

function checkTabs({ files }) {
  const hits = [];
  for (const f of files.filter(isJavaFile)) {
    if (addedLines(f.patch).some(l => l.includes('\t'))) hits.push(f.filename);
  }
  if (hits.length === 0) return null;
  return {
    id: 'tab-characters',
    severity: 'yellow',
    title: 'Tab characters added',
    detail: `Tabs in ${hits.slice(0, 5).map(f => `\`${code(f)}\``).join(', ')}. ` +
      'The build enforces spaces (checkstyle error tier). Convert to 4-space indentation.',
  };
}

// Inline fully-qualified names in code are a common LLM artifact — the
// checklist requires them to be imports instead.
function checkInlineFqn({ files }) {
  const hits = [];
  // Bounded quantifiers: patch text is attacker-controlled.
  const fqnRe = /(?:^|[\s(={,])(java\.(?:util|io|nio|time|net)\.(?:[a-z]{1,40}\.){0,10}[A-Z]\w{0,80})/;
  for (const f of files.filter(isJavaFile)) {
    for (const line of addedLines(f.patch)) {
      if (/^\s*import\s/.test(line) || /^\s*\/\/|^\s*\*/.test(line)) continue;
      const m = line.match(fqnRe);
      if (m) {
        hits.push(`\`${code(m[1])}\` in \`${code(f.filename)}\``);
        break;
      }
    }
  }
  if (hits.length === 0) return null;
  return {
    id: 'inline-fqn',
    severity: 'yellow',
    title: 'Inline fully-qualified names',
    detail: `${hits.slice(0, 3).join(', ')}. Fully qualified names must be imports — ` +
      'inline FQNs are a common generated-code artifact and get flagged in review.',
  };
}

function checkTitleFormat({ pr }) {
  const conventional = /^(feat|fix|chore|docs|ci|test|refactor)(\([\w\-./]{1,60}\))?!?: .+/;
  if (conventional.test(pr.title)) return null;
  return {
    id: 'title-format',
    severity: 'yellow',
    title: 'PR title is not a Conventional Commit',
    detail: `\`${code(pr.title)}\` — expected \`type(scope): description\` with type one of ` +
      '`feat|fix|chore|docs|ci|test|refactor` (see CONTRIBUTING.md).',
  };
}

function checkDiffSize({ pr, config }) {
  const maxLines = config.max_diff_lines ?? DEFAULTS.max_diff_lines;
  const total = (pr.additions || 0) + (pr.deletions || 0);
  if (total <= maxLines) return null;
  return {
    id: 'oversized',
    severity: 'yellow',
    title: 'Very large diff',
    detail: `${total} changed lines (threshold ${maxLines}). Consider splitting — ` +
      'large drive-by PRs are hard to review and often stall.',
  };
}

function checkOverlappingPrs({ pr, linkedIssues, openPrs }) {
  if (linkedIssues.length === 0) return null;
  const issueNumbers = linkedIssues.map(i => i.issue.number);
  const { owner, repo } = prRepoCoords(pr);
  const overlapping = openPrs.filter(p => {
    if (p.number === pr.number || !p.body) return false;
    // Symmetric with how this PR's own links are extracted (short refs + URLs).
    const theirs = extractLinkedIssueNumbers(p.body, owner, repo);
    return issueNumbers.some(n => theirs.includes(n));
  });
  if (overlapping.length === 0) return null;
  const refs = overlapping.slice(0, 3).map(p => `#${p.number}`).join(', ');
  return {
    id: 'overlapping-pr',
    severity: 'yellow',
    title: 'Another open PR references the same issue',
    detail: `Open PR(s) ${refs} reference the same issue. Duplicate work gets the later ` +
      'PR closed — coordinate on the issue before continuing.',
  };
}

const CHECKS = [
  checkDco,
  checkLinkedIssue,
  checkIssueApproval,
  checkGeneratedFiles,
  checkTestsPresent,
  checkStarImports,
  checkConfigPrefix,
  checkLicenseHeaders,
  checkLocale,
  checkSynchronizedReactive,
  checkTabs,
  checkInlineFqn,
  checkTitleFormat,
  checkDiffSize,
  checkOverlappingPrs,
];

// ---------------------------------------------------------------------------
// Aggregation and rendering
// ---------------------------------------------------------------------------

function runChecks(data) {
  const findings = [];
  for (const check of CHECKS) {
    const finding = check(data);
    if (finding) findings.push(finding);
  }
  return findings;
}

function computeVerdict(findings) {
  if (findings.some(f => f.severity === 'red')) return 'red';
  if (findings.some(f => f.severity === 'yellow')) return 'yellow';
  return 'green';
}

function renderReport({ verdict, findings, pr }) {
  const icon = { green: '🟢', yellow: '🟡', red: '🔴' }[verdict];
  const headline = {
    green: 'All automated triage checks passed. A maintainer can accept this PR with `/accept`.',
    yellow: 'Triage found issues that will come up in review. Fixing them now speeds up acceptance.',
    red: 'Triage found blocking problems. Please fix them — the PR will not be triaged by a ' +
      'maintainer until the blocking findings are resolved. It will be closed automatically ' +
      'if there is no activity.',
  }[verdict];

  let body = `${MARKER}\n## ${icon} Automated triage: ${verdict.toUpperCase()}\n\n${headline}\n`;

  const sections = [
    ['red', '### 🚫 Blocking'],
    ['yellow', '### ⚠️ Should fix'],
  ];
  for (const [severity, heading] of sections) {
    const items = findings.filter(f => f.severity === severity);
    if (items.length === 0) continue;
    body += `\n${heading}\n\n`;
    for (const f of items) {
      body += `- **${f.title}** — ${f.detail}\n`;
    }
  }

  body += `\n---\n*Checks derive from the [Contributor Checklist](https://github.com/${pr.base.repo.owner.login}/${pr.base.repo.name}/blob/main/CLAUDE.md). ` +
    `The report refreshes on every push, or on demand with \`/triage\`. ` +
    `Head: \`${code(pr.head.sha).slice(0, 7)}\`.*\n`;
  return body;
}

// ---------------------------------------------------------------------------
// Data collection (GitHub API only — never touches PR code)
// ---------------------------------------------------------------------------

async function collectTriageData(github, owner, repo, pr, config, maintainers) {
  const maxFiles = config.max_files ?? DEFAULTS.max_files;

  const commits = await github.paginate(github.rest.pulls.listCommits, {
    owner, repo, pull_number: pr.number, per_page: 100,
  });

  // Early-exit pagination: stop fetching pages once we have enough files —
  // no point walking hundreds of pages of an oversized PR just to slice them.
  let fileCount = 0;
  let files = await github.paginate(github.rest.pulls.listFiles, {
    owner, repo, pull_number: pr.number, per_page: 100,
  }, (response, done) => {
    fileCount += response.data.length;
    if (fileCount >= maxFiles) done();
    return response.data;
  });
  if (files.length > maxFiles) files = files.slice(0, maxFiles);

  const issueNumbers = extractLinkedIssueNumbers(pr.body, owner, repo).slice(0, 3);
  const linkedIssues = [];
  for (const number of issueNumbers) {
    try {
      const { data: issue } = await github.rest.issues.get({ owner, repo, issue_number: number });
      if (issue.pull_request) continue; // reference was to a PR, not an issue
      const comments = await github.paginate(github.rest.issues.listComments, {
        owner, repo, issue_number: number, per_page: 100,
      });
      linkedIssues.push({ issue, comments });
    } catch (e) {
      if (e.status !== 404) throw e;
    }
  }

  let openPrs = [];
  if (linkedIssues.length > 0) {
    openPrs = await github.paginate(github.rest.pulls.list, {
      owner, repo, state: 'open', per_page: 100,
    });
  }

  return { pr, commits, files, linkedIssues, openPrs, config, maintainers };
}

// ---------------------------------------------------------------------------
// Label + comment side effects
// ---------------------------------------------------------------------------

async function ensureTriageLabel(github, owner, repo, name) {
  const def = TRIAGE_LABEL_DEFS[name];
  try {
    await github.rest.issues.getLabel({ owner, repo, name });
  } catch (e) {
    if (e.status === 404) {
      await github.rest.issues.createLabel({ owner, repo, name, ...def });
    } else {
      throw e;
    }
  }
}

async function applyTriageLabel(github, owner, repo, pr, verdict) {
  const target = {
    green: TRIAGE_LABELS.GREEN,
    yellow: TRIAGE_LABELS.YELLOW,
    red: TRIAGE_LABELS.RED,
  }[verdict];
  await ensureTriageLabel(github, owner, repo, target);
  const current = (pr.labels || []).map(l => l.name);
  for (const label of Object.values(TRIAGE_LABELS)) {
    if (label !== target && current.includes(label)) {
      // Best-effort: a failed removal must not prevent applying the correct label.
      await github.rest.issues.removeLabel({ owner, repo, issue_number: pr.number, name: label })
        .catch(() => {});
    }
  }
  if (!current.includes(target)) {
    await github.rest.issues.addLabels({ owner, repo, issue_number: pr.number, labels: [target] });
  }
}

async function upsertReportComment(github, owner, repo, prNumber, body) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: prNumber, per_page: 100,
  });
  const existing = comments.find(c => c.body && c.body.includes(MARKER));
  if (existing) {
    await github.rest.issues.updateComment({ owner, repo, comment_id: existing.id, body });
  } else {
    await github.rest.issues.createComment({ owner, repo, issue_number: prNumber, body });
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

// Runs triage end to end: collect → check → label → report.
// Returns { verdict, findings } or null when triage is disabled.
// Callers decide lifecycle consequences (waiting-on-* labels).
async function runTriage({ github, owner, repo, pr, config, core }) {
  const triageConfig = { ...DEFAULTS, ...(config.triage || {}) };
  if (!triageConfig.enabled) return null;

  const maintainers = config.maintainers || [];
  const data = await collectTriageData(github, owner, repo, pr, triageConfig, maintainers);
  const findings = runChecks(data);
  const verdict = computeVerdict(findings);

  await applyTriageLabel(github, owner, repo, pr, verdict);
  await upsertReportComment(github, owner, repo, pr.number, renderReport({ verdict, findings, pr }));

  core.info(`PR #${pr.number} triage verdict=${verdict} findings=[${findings.map(f => f.id).join(', ')}]`);
  return { verdict, findings };
}

module.exports = {
  runTriage,
  runChecks,
  computeVerdict,
  renderReport,
  extractLinkedIssueNumbers,
  addedLines,
  code,
  applyTriageLabel,
  upsertReportComment,
  TRIAGE_LABELS,
  TRIAGE_LABEL_DEFS,
  MARKER,
  DEFAULTS,
};
