// PR Validation Checks
//
// Validates submission quality and surfaces duplicate PRs. Deliberately
// independent of the lifecycle state machine in pr-lifecycle.js: validation
// runs on every PR regardless of lifecycle state, and a failure here never
// blocks /accept. See .github/pr-lifecycle.yml for the shared config.
//
// Blocking checks (fail the GitHub check):
//   - issue link: the body references an issue this PR closes
//   - DCO sign-off: every commit carries a Signed-off-by trailer
//
// Advisory only (reported, never blocking):
//   - duplicate PRs, matched by linked issue or by overlapping files

const fs = require('fs');
const path = require('path');

const VALIDATION_FAILED_LABEL = 'lifecycle/validation-failed';
const LABEL_COLOR = 'E8836B';
const LABEL_DESCRIPTION = 'PR validation checks failed';

// Marks the single comment this script owns, so each run edits it instead of
// posting a new one.
const COMMENT_MARKER = '<!-- pr-validation -->';
const ADVISORY_MARKER = '<!-- pr-validation:duplicate -->';

const BOT_LOGIN = 'github-actions[bot]';

// "Closes #123", "fixed #123", etc. GitHub's own closing keywords.
const CLOSING_KEYWORD_PATTERN =
  /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+#(\d+)\b/gi;

// Same, but written as a full URL. Only issues in this repository count.
const ISSUE_URL_PATTERN =
  /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+https?:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/issues\/(\d+)\b/gi;

// Comparing files against every open PR costs one API call per PR. Above this
// many open PRs we skip file-based duplicate detection rather than burn the
// rate limit; issue-based detection still runs and is the more precise signal.
const MAX_PRS_FOR_FILE_COMPARISON = 40;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function loadConfig() {
  const configPath = path.join(process.cwd(), '.github', 'pr-lifecycle.json');
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

function isExemptAuthor(config, username) {
  // Dependency bots open PRs that legitimately have no issue to close.
  return (config.auto_accept || []).includes(username);
}

/**
 * Issue numbers this PR declares it closes. Only closing keywords count: a bare
 * "#123" is a reference, not a link, and GitHub will not close the issue on merge.
 */
function extractLinkedIssues(body, owner, repo) {
  const issues = new Set();
  const text = body || '';

  for (const match of text.matchAll(CLOSING_KEYWORD_PATTERN)) {
    issues.add(Number(match[1]));
  }
  for (const match of text.matchAll(ISSUE_URL_PATTERN)) {
    if (match[1].toLowerCase() === owner.toLowerCase()
        && match[2].toLowerCase() === repo.toLowerCase()) {
      issues.add(Number(match[3]));
    }
  }
  return issues;
}

function hasSignOff(message) {
  return message.split('\n').some(line => /^\s*Signed-off-by:\s*.+<.+@.+>\s*$/i.test(line));
}

function shortSha(sha) {
  return sha.substring(0, 8);
}

function firstLine(message) {
  return message.split('\n')[0];
}

// ---------------------------------------------------------------------------
// Checks
// ---------------------------------------------------------------------------

function checkIssueLink(linkedIssues) {
  if (linkedIssues.size > 0) {
    return null;
  }
  return {
    name: 'Issue link',
    detail: 'The PR body does not link an issue. Add a closing keyword such as '
      + '`Closes #1234` (or `Fixes`/`Resolves`, or the full issue URL) so the issue '
      + 'closes when this merges.',
  };
}

function checkDcoSignOff(commits) {
  const unsigned = commits.filter(c => !hasSignOff(c.commit.message));
  if (unsigned.length === 0) {
    return null;
  }
  const list = unsigned
    .map(c => `  - \`${shortSha(c.sha)}\` ${firstLine(c.commit.message)}`)
    .join('\n');
  return {
    name: 'DCO sign-off',
    detail: `${unsigned.length} commit(s) are missing a \`Signed-off-by:\` trailer:\n\n${list}\n\n`
      + 'Sign off with `git commit -s`, or repair existing commits with '
      + '`git rebase --signoff upstream/main` and force-push.',
  };
}

// ---------------------------------------------------------------------------
// Duplicate detection
// ---------------------------------------------------------------------------

async function findDuplicates(github, owner, repo, pr, linkedIssues, core) {
  const openPrs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', per_page: 100,
  });
  const others = openPrs.filter(p => p.number !== pr.number && !p.draft);

  const byIssue = [];
  for (const other of others) {
    const otherIssues = extractLinkedIssues(other.body, owner, repo);
    const shared = [...linkedIssues].filter(n => otherIssues.has(n));
    if (shared.length > 0) {
      byIssue.push({ pr: other, issues: shared });
    }
  }

  const byFile = [];
  if (others.length > MAX_PRS_FOR_FILE_COMPARISON) {
    core.info(`Skipping file-based duplicate detection: ${others.length} open PRs `
      + `exceeds the ${MAX_PRS_FOR_FILE_COMPARISON} threshold`);
  } else {
    const ourFiles = new Set((await github.paginate(github.rest.pulls.listFiles, {
      owner, repo, pull_number: pr.number, per_page: 100,
    })).map(f => f.filename));

    const alreadyReported = new Set(byIssue.map(d => d.pr.number));
    for (const other of others) {
      if (alreadyReported.has(other.number)) continue;
      const otherFiles = await github.paginate(github.rest.pulls.listFiles, {
        owner, repo, pull_number: other.number, per_page: 100,
      });
      const shared = otherFiles.map(f => f.filename).filter(f => ourFiles.has(f));
      if (shared.length > 0) {
        byFile.push({ pr: other, files: shared });
      }
    }
  }

  return { byIssue, byFile };
}

// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------

function buildComment(pr, violations, duplicates) {
  const lines = [COMMENT_MARKER, '## PR validation', ''];

  if (violations.length === 0) {
    lines.push('All validation checks passed.', '');
  } else {
    lines.push(`This PR has ${violations.length} validation issue(s):`, '');
    for (const violation of violations) {
      lines.push(`### ${violation.name}`, '', violation.detail, '');
    }
    lines.push('The check updates automatically when you push. This does not '
      + 'close your PR, and a maintainer can still accept it.', '');
  }

  const { byIssue, byFile } = duplicates;
  if (byIssue.length > 0 || byFile.length > 0) {
    lines.push('### Possible duplicate PRs', '',
      'These are advisory only and do not affect the check result.', '');
    for (const dup of byIssue) {
      lines.push(`- #${dup.pr.number} by @${dup.pr.user.login} also links `
        + `${dup.issues.map(n => `#${n}`).join(', ')}`);
    }
    for (const dup of byFile) {
      const preview = dup.files.slice(0, 5).map(f => `\`${f}\``).join(', ');
      const more = dup.files.length > 5 ? ` (+${dup.files.length - 5} more)` : '';
      lines.push(`- #${dup.pr.number} by @${dup.pr.user.login} also changes ${preview}${more}`);
    }
    lines.push('');
  }

  return lines.join('\n');
}

async function upsertComment(github, owner, repo, prNumber, body, marker, core) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: prNumber, per_page: 100,
  });
  const existing = comments.find(
    c => c.user.login === BOT_LOGIN && c.body && c.body.includes(marker)
  );

  if (existing) {
    if (existing.body.trim() === body.trim()) {
      core.info(`PR #${prNumber} comment unchanged, skipping update`);
      return;
    }
    await github.rest.issues.updateComment({
      owner, repo, comment_id: existing.id, body,
    });
    core.info(`PR #${prNumber} updated validation comment`);
  } else {
    await github.rest.issues.createComment({
      owner, repo, issue_number: prNumber, body,
    });
    core.info(`PR #${prNumber} posted validation comment`);
  }
}

async function ensureLabel(github, owner, repo, core) {
  try {
    await github.rest.issues.getLabel({ owner, repo, name: VALIDATION_FAILED_LABEL });
  } catch (e) {
    if (e.status !== 404) throw e;
    await github.rest.issues.createLabel({
      owner, repo, name: VALIDATION_FAILED_LABEL,
      color: LABEL_COLOR, description: LABEL_DESCRIPTION,
    });
    core.info(`Created label ${VALIDATION_FAILED_LABEL}`);
  }
}

async function setFailedLabel(github, owner, repo, pr, failed, core) {
  const hasLabel = (pr.labels || []).some(l => l.name === VALIDATION_FAILED_LABEL);

  if (failed && !hasLabel) {
    await ensureLabel(github, owner, repo, core);
    await github.rest.issues.addLabels({
      owner, repo, issue_number: pr.number, labels: [VALIDATION_FAILED_LABEL],
    });
  } else if (!failed && hasLabel) {
    await github.rest.issues.removeLabel({
      owner, repo, issue_number: pr.number, name: VALIDATION_FAILED_LABEL,
    }).catch(e => { if (e.status !== 404) throw e; });
  }
}

/**
 * Tell the other side of an overlap that it exists. Keyed by this PR's number so
 * repeated pushes edit one comment instead of stacking new ones.
 */
async function postDuplicateAdvisories(github, owner, repo, pr, duplicates, core) {
  const all = [...duplicates.byIssue, ...duplicates.byFile];
  for (const dup of all) {
    const marker = `${ADVISORY_MARKER}<!-- from:${pr.number} -->`;
    const reason = dup.issues
      ? `links the same issue (${dup.issues.map(n => `#${n}`).join(', ')})`
      : `changes ${dup.files.length} of the same file(s)`;
    const body = [
      marker,
      `**Possible duplicate:** #${pr.number} by @${pr.user.login} ${reason}.`,
      '',
      'This is advisory only. If the overlap is intentional, ignore this comment.',
    ].join('\n');
    await upsertComment(github, owner, repo, dup.pr.number, body, marker, core);
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

async function validate({ github, context, core }) {
  const { owner, repo } = context.repo;
  const pr = context.payload.pull_request;
  const config = loadConfig();

  if (isExemptAuthor(config, pr.user.login)) {
    core.info(`Skipping validation for exempt author ${pr.user.login}`);
    return;
  }

  const commits = await github.paginate(github.rest.pulls.listCommits, {
    owner, repo, pull_number: pr.number, per_page: 100,
  });

  const linkedIssues = extractLinkedIssues(pr.body, owner, repo);
  const violations = [
    checkIssueLink(linkedIssues),
    checkDcoSignOff(commits),
  ].filter(Boolean);

  const duplicates = await findDuplicates(github, owner, repo, pr, linkedIssues, core);

  await upsertComment(github, owner, repo, pr.number,
    buildComment(pr, violations, duplicates), COMMENT_MARKER, core);
  await setFailedLabel(github, owner, repo, pr, violations.length > 0, core);
  await postDuplicateAdvisories(github, owner, repo, pr, duplicates, core);

  if (violations.length > 0) {
    core.setFailed(`PR validation failed: ${violations.map(v => v.name).join(', ')}`);
  } else {
    core.info('PR validation passed');
  }
}

module.exports = {
  validate,
  // exported for tests
  extractLinkedIssues,
  hasSignOff,
  checkIssueLink,
  checkDcoSignOff,
};
