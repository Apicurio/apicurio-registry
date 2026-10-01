// PR Validation Checks
//
// Validates submission quality and surfaces duplicate PRs. Deliberately
// independent of the lifecycle state machine in pr-lifecycle.js: validation
// runs on every PR regardless of lifecycle state. See .github/pr-lifecycle.yml
// for the shared config.
//
// Blocking checks (fail the GitHub check), each owned by whoever can fix it:
//   - issue link (author): the body references an issue this PR closes
//   - DCO sign-off (author): every non-merge commit carries a Signed-off-by trailer
//   - milestone (maintainer): the PR and every open issue it closes carry an
//     open milestone
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

// GitHub's own closing keywords.
const CLOSING_KEYWORD_PATTERN =
  /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+#(\d+)\b/gi;

// Only issues in this repository count (owner/repo checked in extractLinkedIssues).
const ISSUE_URL_PATTERN =
  /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+https?:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/issues\/(\d+)\b/gi;

// Who can act on a violation. The comment groups by it, because a check the
// author cannot clear otherwise reads as "the author broke something".
const AUTHOR = 'author';
const MAINTAINER = 'maintainer';

// The workflow file whose latest run carries a PR's "Validate PR" check.
const VALIDATION_WORKFLOW_FILE = 'pr-validation.yml';

// The base branches pr-validation.yml validates (its pull_request_target
// branches filter). Issue events can't be filtered by branch, so
// revalidateForIssue applies the same scope itself.
const VALIDATED_BASE_BRANCHES = ['main', '3.3.x'];

// How long revalidateForIssue waits for a validation run that is still going
// before re-running it: such a run may already have read the old milestone.
// A validation run normally takes well under a minute.
const IN_PROGRESS_POLL_MS = 10 * 1000;
const IN_PROGRESS_DEADLINE_MS = 3 * 60 * 1000;

// Comparing files against every open PR costs one API call per PR. Above this
// many open PRs we skip file-based duplicate detection rather than burn the
// rate limit; issue-based detection still runs and is the more precise signal.
const MAX_PRS_FOR_FILE_COMPARISON = 40;

// A single shared file (e.g. both PRs touch pom.xml) is common and rarely a
// real duplicate; require more overlap before flagging one.
const MIN_SHARED_FILES_FOR_DUPLICATE = 2;

function loadConfig() {
  const configPath = path.join(process.cwd(), '.github', 'pr-lifecycle.json');
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

function isExemptAuthor(config, username) {
  // Dependency bots open PRs that legitimately have no issue to close.
  return (config.auto_accept || []).includes(username);
}

/**
 * Markdown with fenced code blocks and code spans removed. A closing keyword
 * quoted in code is being discussed, not used: explaining why a PR no longer
 * says ``Fixes #7317`` must not link #7317 again.
 *
 * Follows the CommonMark rules that matter here rather than a regex: a fence
 * may be indented up to three spaces, is closed only by a fence of the same
 * character at least as long, and an unclosed fence runs to the end; a code
 * span ends at the next backtick run of exactly its length, may span lines,
 * and never crosses a blank line.
 */
function stripCode(markdown) {
  const kept = [];
  let fence = null;
  for (const line of markdown.split('\n')) {
    if (fence) {
      const close = /^ {0,3}(`{3,}|~{3,})[ \t]*$/.exec(line);
      if (close && close[1][0] === fence.char && close[1].length >= fence.length) fence = null;
      continue;
    }
    const open = /^ {0,3}(`{3,}|~{3,})(.*)$/.exec(line);
    // The info string of a backtick fence can't contain a backtick; that
    // line is inline code instead.
    if (open && !(open[1][0] === '`' && open[2].includes('`'))) {
      fence = { char: open[1][0], length: open[1].length };
      continue;
    }
    kept.push(line);
  }
  return kept.join('\n').split(/(\n[ \t]*\n)/).map(stripCodeSpans).join('');
}

// Code spans removed from one paragraph. A backtick run with no closing run
// of the same length is literal text.
function stripCodeSpans(paragraph) {
  const runLength = at => {
    let n = 0;
    while (paragraph[at + n] === '`') n++;
    return n;
  };
  let out = '';
  let i = 0;
  while (i < paragraph.length) {
    if (paragraph[i] !== '`') {
      out += paragraph[i++];
      continue;
    }
    const n = runLength(i);
    let j = i + n;
    let close = -1;
    while (j < paragraph.length) {
      if (paragraph[j] !== '`') {
        j++;
        continue;
      }
      const m = runLength(j);
      if (m === n) {
        close = j;
        break;
      }
      j += m;
    }
    if (close === -1) {
      out += paragraph.slice(i, i + n);
      i += n;
    } else {
      i = close + n;
    }
  }
  return out;
}

/**
 * Issue numbers this PR declares it closes. Only closing keywords count: a bare
 * "#123" is a reference, not a link, and GitHub will not close the issue on merge.
 */
function extractLinkedIssues(body, owner, repo) {
  const issues = new Set();
  const text = stripCode(body || '');

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

/**
 * A Signed-off-by trailer only counts if its email matches the commit's
 * author or committer email -- otherwise anyone could paste an arbitrary
 * trailer onto an unsigned commit and pass the DCO check.
 */
function hasSignOff(commit) {
  const { message, author, committer } = commit.commit;
  const ownEmails = new Set(
    [author && author.email, committer && committer.email]
      .filter(Boolean)
      .map(e => e.toLowerCase())
  );
  return message.split('\n').some(line => {
    const match = /^\s*Signed-off-by:\s*.+<(.+@.+)>\s*$/i.exec(line);
    return Boolean(match) && ownEmails.has(match[1].toLowerCase());
  });
}

function shortSha(sha) {
  return sha.substring(0, 8);
}

function firstLine(message) {
  return message.split('\n')[0];
}

function checkIssueLink(linkedIssues) {
  if (linkedIssues.size > 0) {
    return null;
  }
  return {
    name: 'Issue link',
    audience: AUTHOR,
    detail: 'The PR body does not link an issue. Add a closing keyword such as '
      + '`Closes #1234` (or `Fixes`/`Resolves`, or the full issue URL) so the issue '
      + 'closes when this merges.',
  };
}

/**
 * A merge commit has two or more parents, and carries no authored change of
 * its own. GitHub creates one server-side when the "Update branch" button (or
 * `gh pr update-branch`) brings a PR up to date; that commit never carries a
 * Signed-off-by trailer, and branch protection on main makes updating the
 * branch mandatory, so flagging it would make a required action break a
 * required check.
 *
 * Exempting merge commits cannot put unsigned content on main, because this
 * repository allows only squash and rebase merges: a merge commit on a PR
 * branch is discarded when the PR merges.
 */
function isMergeCommit(commit) {
  return (commit.parents || []).length > 1;
}

function checkDcoSignOff(commits) {
  const unsigned = commits.filter(c => !isMergeCommit(c) && !hasSignOff(c));
  if (unsigned.length === 0) {
    return null;
  }
  const list = unsigned
    .map(c => `  - \`${shortSha(c.sha)}\` ${firstLine(c.commit.message)}`)
    .join('\n');
  return {
    name: 'DCO sign-off',
    audience: AUTHOR,
    detail: `${unsigned.length} commit(s) are missing a \`Signed-off-by:\` trailer:\n\n${list}\n\n`
      + 'Sign off with `git commit -s`, or repair existing commits with '
      + '`git rebase --signoff upstream/main` and force-push.',
  };
}

/**
 * The issues this PR closes, fetched so their milestones can be checked. A
 * lookup that fails (deleted issue, a number that is really a PR in another
 * repo) is skipped rather than reported: the milestone check should not
 * invent a violation out of an API error.
 */
async function fetchLinkedIssues(github, owner, repo, linkedIssues, core) {
  const issues = [];
  for (const number of linkedIssues) {
    try {
      const { data } = await github.rest.issues.get({ owner, repo, issue_number: number });
      issues.push(data);
    } catch (e) {
      core.warning(`Could not load issue #${number} for the milestone check: ${e.message}`);
    }
  }
  return issues;
}

/**
 * Milestones drive the release notes (generated from the milestone's issues)
 * and make work findable after the fact, so both the PR and every open issue
 * it closes need one. A closed milestone is treated as missing — it belongs to
 * a release that already shipped, so nothing new can land in it.
 *
 * A closed linked issue is not checked. Its milestone records when its work
 * shipped, which is history: a follow-up PR to an issue fixed in 3.3.2 must not
 * require moving that issue to the current release. The PR's own milestone
 * places this work in the release notes.
 *
 * Setting a milestone needs triage permission, so this is the one blocking
 * check an outside contributor cannot clear themselves. The message says so.
 */
function checkMilestone(pr, issues) {
  const problems = [];

  const describe = (subject, milestone) => {
    if (!milestone) {
      problems.push(`- ${subject} has no milestone.`);
    } else if (milestone.state === 'closed') {
      problems.push(`- ${subject} is on milestone \`${milestone.title}\`, which is closed.`);
    }
  };

  describe('This PR', pr.milestone);
  for (const issue of issues) {
    if (issue.state === 'closed') continue;
    describe(`Issue #${issue.number}`, issue.milestone);
  }

  if (problems.length === 0) {
    return null;
  }
  return {
    name: 'Milestone',
    audience: MAINTAINER,
    detail: `${problems.join('\n')}\n\n`
      + 'Setting a milestone requires triage permission, so **a maintainer has to '
      + 'do this** — there is no action for the PR author here. The milestone '
      + 'determines which release notes this work appears in. The check re-runs '
      + 'on its own when the milestone is set, on this PR or on a linked issue.',
  };
}

async function findDuplicates(github, owner, repo, pr, linkedIssues, config, core) {
  const openPrs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', base: pr.base.ref, per_page: 100,
  });
  const others = openPrs.filter(p => p.number !== pr.number && !p.draft
    && !isExemptAuthor(config, p.user.login));

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
      if (shared.length >= MIN_SHARED_FILES_FOR_DUPLICATE) {
        byFile.push({ pr: other, files: shared });
      }
    }
  }

  return { byIssue, byFile };
}

function buildComment(pr, violations, duplicates) {
  const lines = [COMMENT_MARKER, '## PR validation', ''];

  if (violations.length === 0) {
    lines.push('All validation checks passed.', '');
  } else {
    lines.push(`This PR has ${violations.length} validation issue(s).`, '');
    for (const [audience, heading] of [[AUTHOR, 'For the author'], [MAINTAINER, 'For a maintainer']]) {
      const owned = violations.filter(v => v.audience === audience);
      if (owned.length === 0) continue;
      lines.push(`### ${heading}`, '');
      for (const violation of owned) {
        lines.push(`#### ${violation.name}`, '', violation.detail, '');
      }
    }
    lines.push('The check re-runs on its own when you push or edit the description, '
      + 'and when a maintainer sets a milestone on this PR or on an issue it closes. '
      + 'To re-run it by hand, comment `/retry`. This does not close your PR.', '');
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

async function validate({ github, context, core }) {
  const { owner, repo } = context.repo;
  // Read back rather than taken from the event: a re-run of this workflow
  // replays the original event, whose body and milestone may be stale, and a
  // re-run is how a milestone set on a linked issue reaches the check
  // (revalidateForIssue, and /retry in pr-lifecycle.js).
  const { data: pr } = await github.rest.pulls.get({
    owner, repo, pull_number: context.payload.pull_request.number,
  });
  const config = loadConfig();

  if (isExemptAuthor(config, pr.user.login)) {
    core.info(`Skipping validation for exempt author ${pr.user.login}`);
    return;
  }

  const commits = await github.paginate(github.rest.pulls.listCommits, {
    owner, repo, pull_number: pr.number, per_page: 100,
  });

  const linkedIssues = extractLinkedIssues(pr.body, owner, repo);
  const issues = await fetchLinkedIssues(github, owner, repo, linkedIssues, core);
  const violations = [
    checkIssueLink(linkedIssues),
    checkDcoSignOff(commits),
    checkMilestone(pr, issues),
  ].filter(Boolean);

  let duplicates = { byIssue: [], byFile: [] };
  try {
    duplicates = await findDuplicates(github, owner, repo, pr, linkedIssues, config, core);
  } catch (e) {
    core.warning(`Duplicate detection failed, continuing without it: ${e.message}`);
  }

  await upsertComment(github, owner, repo, pr.number,
    buildComment(pr, violations, duplicates), COMMENT_MARKER, core);
  await setFailedLabel(github, owner, repo, pr, violations.length > 0, core);

  try {
    await postDuplicateAdvisories(github, owner, repo, pr, duplicates, core);
  } catch (e) {
    core.warning(`Posting duplicate advisories failed: ${e.message}`);
  }

  if (violations.length > 0) {
    core.setFailed(`PR validation failed: ${violations.map(v => v.name).join(', ')}`);
  } else {
    core.info('PR validation passed');
  }
}

/**
 * A linked issue's milestone changed, or it was closed or reopened (closed
 * issues aren't milestone-checked). Re-runs the latest validation run of
 * every open PR that closes it.
 *
 * Validating here directly would update the comment and the label, but not
 * the PR's "Validate PR" check: that check belongs to the PR's own
 * pull_request_target run, and this run (an issues event) is attached to the
 * default branch. Re-running that run updates the check, and validate() reads
 * the current state. Only when there is no run to re-run, or GitHub won't
 * re-run it (older than 30 days), does this validate directly, so that at
 * least the comment and the label are right.
 */
async function revalidateForIssue({ github, context, core,
                                   pollMs = IN_PROGRESS_POLL_MS, deadlineMs = IN_PROGRESS_DEADLINE_MS }) {
  const { owner, repo } = context.repo;
  const issue = context.payload.issue;
  // A PR's own milestone reaches the check through pull_request_target
  // milestoned/demilestoned; the matching issues event is ignored.
  if (issue.pull_request) return;

  const openPrs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', per_page: 100,
  });
  const linking = openPrs.filter(p => VALIDATED_BASE_BRANCHES.includes(p.base.ref)
    && extractLinkedIssues(p.body, owner, repo).has(issue.number));
  core.info(`Issue #${issue.number} ${context.payload.action}; ${linking.length} open PR(s) close it`);

  for (const pr of linking) {
    const { data } = await github.rest.actions.listWorkflowRuns({
      owner, repo, workflow_id: VALIDATION_WORKFLOW_FILE, head_sha: pr.head.sha, per_page: 1,
    });
    let run = data.workflow_runs[0];
    // A run still going may have read the issue before this change. Let it
    // finish, then re-run it.
    for (let waited = 0; run && run.status !== 'completed' && waited < deadlineMs; waited += pollMs) {
      await new Promise(resolve => setTimeout(resolve, pollMs));
      run = (await github.rest.actions.getWorkflowRun({ owner, repo, run_id: run.id })).data;
    }
    if (run && run.status !== 'completed') {
      core.warning(`PR #${pr.number} validation run ${run.id} is still running after `
        + `${deadlineMs / 1000}s; not re-running it. /retry re-runs it once it has finished.`);
      continue;
    }
    if (run) {
      try {
        await github.rest.actions.reRunWorkflow({ owner, repo, run_id: run.id });
        core.info(`PR #${pr.number} re-ran validation run ${run.id}`);
        continue;
      } catch (e) {
        core.warning(`PR #${pr.number} could not re-run validation run ${run.id} (${e.message}); `
          + 'validating directly, which updates the comment and label but not the check');
      }
    }
    await validate({ github, context: { repo: context.repo, payload: { pull_request: pr } }, core });
  }
}

module.exports = {
  validate,
  revalidateForIssue,
  // exported for tests
  stripCode,
  extractLinkedIssues,
  hasSignOff,
  checkIssueLink,
  checkDcoSignOff,
  checkMilestone,
};
