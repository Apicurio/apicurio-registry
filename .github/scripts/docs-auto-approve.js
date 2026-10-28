// Docs-only auto-approval.
//
// Runs from docs-auto-approve.yml when a Verify run completes. Approves a PR,
// as github-actions[bot], when a maintainer changed only documentation and the
// docs check passed, so the author can merge without waiting for a second
// person. Branch protection is unchanged: the approval is an ordinary review,
// dismissed by the next push (dismiss_stale_reviews), after which the next
// Verify run decides again.
//
// Runs in a privileged workflow_run context, so it never checks out or runs
// PR code: everything is read back through the API, and the PR's file list,
// not anything in the run, decides what the PR changes.

const { isDocsOnly, reviewRequiredFiles } = require('./docs-only.js');

const VERIFY_WORKFLOW_PATH = '.github/workflows/verify.yaml';
// The Docs Check caller job in verify.yaml; reusable-workflow jobs are named
// "<caller> / <job>".
const DOCS_CHECK_JOB_PREFIX = 'Docs Check /';
const VALIDATED_BASE_BRANCHES = ['main', '3.3.x'];
const BOT_LOGIN = 'github-actions[bot]';
const LIFECYCLE_WORKFLOW_FILE = 'pr-lifecycle.yml';

// GitHub's own committer for commits made in the web UI (edits, suggestion
// commits, "Update branch"); the author of such a commit is still checked.
const WEB_FLOW_LOGIN = 'web-flow';

const APPROVAL_MARKER = '<!-- docs-auto-approve -->';
const REVIEW_REQUIRED_MARKER = '<!-- docs-auto-approve:review-required -->';

const REVIEW_RESOLVED_COMMENT = [
  REVIEW_REQUIRED_MARKER,
  '~~**Docs-only PR, but a human review is required.**~~ Resolved: this PR no longer changes a file on the '
    + 'review-required list (`docs_only.review_required_paths` in `.github/pr-lifecycle.yml`).',
].join('\n');

const SETTING_HINT = 'Enable "Allow GitHub Actions to create and approve pull requests" under '
  + 'Settings > Actions > General > Workflow permissions.';

/**
 * The PR a Verify run belongs to. workflow_run lists it for same-repository
 * branches only; a fork PR is found through its head commit.
 */
async function findPrNumber(github, owner, repo, run) {
  if ((run.pull_requests || []).length > 0) return run.pull_requests[0].number;
  const { data } = await github.rest.repos.listPullRequestsAssociatedWithCommit({
    owner, repo, commit_sha: run.head_sha,
  });
  const open = data.find(p => p.state === 'open' && p.head.sha === run.head_sha);
  return open ? open.number : null;
}

async function docsCheckPassed(github, owner, repo, runId) {
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, {
    owner, repo, run_id: runId, per_page: 100,
  });
  const list = Array.isArray(jobs) ? jobs : jobs.jobs;
  const docsJobs = list.filter(j => j.name.startsWith(DOCS_CHECK_JOB_PREFIX));
  return docsJobs.length > 0 && docsJobs.every(j => j.conclusion === 'success');
}

/**
 * The commits on the PR that a maintainer did not both author and commit. The
 * PR author is not enough: a maintainer can open a PR from a branch that holds
 * someone else's commits, and the bot approval would then skip the second
 * person for content no maintainer wrote. A commit whose author or committer
 * is not linked to a GitHub account cannot be attributed, so it counts too.
 *
 * @returns {Promise<string[]>} the short SHAs of those commits
 */
async function commitsNotByMaintainers(github, owner, repo, prNumber, maintainers) {
  const commits = await github.paginate(github.rest.pulls.listCommits, {
    owner, repo, pull_number: prNumber, per_page: 100,
  });
  const isMaintainer = user => Boolean(user) && maintainers.includes(user.login);
  return commits
    .filter(c => !isMaintainer(c.author)
      || !(isMaintainer(c.committer) || (c.committer && c.committer.login === WEB_FLOW_LOGIN)))
    .map(c => c.sha.slice(0, 12));
}

async function alreadyApproved(github, owner, repo, prNumber, sha) {
  const reviews = await github.paginate(github.rest.pulls.listReviews, {
    owner, repo, pull_number: prNumber, per_page: 100,
  });
  return reviews.some(r => r.user && r.user.login === BOT_LOGIN && r.state === 'APPROVED' && r.commit_id === sha);
}

async function findBotComment(github, owner, repo, prNumber, marker) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: prNumber, per_page: 100,
  });
  return comments.find(c => c.user && c.user.login === BOT_LOGIN && (c.body || '').includes(marker));
}

async function upsertComment(github, owner, repo, prNumber, body, marker) {
  const existing = await findBotComment(github, owner, repo, prNumber, marker);
  if (existing) {
    await github.rest.issues.updateComment({ owner, repo, comment_id: existing.id, body });
  } else {
    await github.rest.issues.createComment({ owner, repo, issue_number: prNumber, body });
  }
}

/**
 * Rewrites an earlier review-required comment once the PR no longer changes a
 * review-required file, so that it does not contradict the approval. Leaves
 * the PR alone when there is no such comment or it already says so.
 */
async function resolveReviewRequiredComment(github, owner, repo, prNumber) {
  const existing = await findBotComment(github, owner, repo, prNumber, REVIEW_REQUIRED_MARKER);
  if (!existing || existing.body === REVIEW_RESOLVED_COMMENT) return;
  await github.rest.issues.updateComment({ owner, repo, comment_id: existing.id, body: REVIEW_RESOLVED_COMMENT });
}

function reviewRequiredComment(files) {
  return [
    REVIEW_REQUIRED_MARKER,
    '**Docs-only PR, but a human review is required.** These files are on the review-required list '
      + '(`docs_only.review_required_paths` in `.github/pr-lifecycle.yml`), so this PR was not approved '
      + 'automatically:',
    '',
    ...files.map(f => `- \`${f}\``),
  ].join('\n');
}

/**
 * Handles a completed Verify run: approves the PR when every condition holds,
 * and otherwise logs which one did not.
 *
 * @param {{github: object, context: object, core: object, config: object}} args
 *   config is .github/pr-lifecycle.yml as JSON
 */
async function handleVerifyCompleted({ github, context, core, config }) {
  const { owner, repo } = context.repo;
  const run = context.payload.workflow_run;
  const skip = reason => core.info(`Not approving: ${reason}`);

  if (run.path !== VERIFY_WORKFLOW_PATH) return skip(`run is from ${run.path}, not ${VERIFY_WORKFLOW_PATH}`);
  if (run.event !== 'pull_request') return skip(`run event is ${run.event}`);
  if (run.conclusion !== 'success') return skip(`Verify concluded ${run.conclusion}`);

  const prNumber = await findPrNumber(github, owner, repo, run);
  if (!prNumber) return skip(`no open PR has head ${run.head_sha}`);
  const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: prNumber });

  if (pr.state !== 'open' || pr.draft) return skip(`PR #${prNumber} is ${pr.draft ? 'a draft' : pr.state}`);
  if (!VALIDATED_BASE_BRANCHES.includes(pr.base.ref)) return skip(`PR #${prNumber} targets ${pr.base.ref}`);
  // The run checked an older commit; the run for the current head decides.
  if (pr.head.sha !== run.head_sha) return skip(`PR #${prNumber} head is ${pr.head.sha}, run checked ${run.head_sha}`);
  const maintainers = config.maintainers || [];
  if (!maintainers.includes(pr.user.login)) {
    return skip(`PR #${prNumber} author ${pr.user.login} is not a maintainer`);
  }
  const foreign = await commitsNotByMaintainers(github, owner, repo, prNumber, maintainers);
  if (foreign.length > 0) {
    return skip(`PR #${prNumber} has commits not authored and committed by a maintainer: ${foreign.join(', ')}`);
  }

  const files = await github.paginate(github.rest.pulls.listFiles, {
    owner, repo, pull_number: prNumber, per_page: 100,
  });
  if (!isDocsOnly(files, config.docs_only)) return skip(`PR #${prNumber} is not docs-only`);
  if (!await docsCheckPassed(github, owner, repo, run.id)) {
    return skip(`the Docs Check job did not pass in run ${run.id}`);
  }

  const sensitive = reviewRequiredFiles(files, config.docs_only);
  if (sensitive.length > 0) {
    await upsertComment(github, owner, repo, prNumber, reviewRequiredComment(sensitive), REVIEW_REQUIRED_MARKER);
    return skip(`PR #${prNumber} changes review-required files: ${sensitive.join(', ')}`);
  }
  await resolveReviewRequiredComment(github, owner, repo, prNumber);

  if (await alreadyApproved(github, owner, repo, prNumber, run.head_sha)) {
    return skip(`PR #${prNumber} is already approved at ${run.head_sha}`);
  }

  try {
    await github.rest.pulls.createReview({
      owner, repo, pull_number: prNumber, commit_id: run.head_sha, event: 'APPROVE',
      body: `${APPROVAL_MARKER}\nApproved automatically: a docs-only change by a maintainer, and the docs check `
        + `passed ([Verify run](${run.html_url})). Any new push dismisses this approval.`,
    });
  } catch (e) {
    core.setFailed(`Could not approve PR #${prNumber}: ${e.message}. ${SETTING_HINT}`);
    return undefined;
  }
  core.info(`Approved PR #${prNumber} at ${run.head_sha}`);

  // A review made with GITHUB_TOKEN starts no workflows, so the lifecycle
  // orchestrator would not see it until its next scheduled reconcile.
  // workflow_dispatch is the exception to that rule.
  const ref = (context.payload.repository && context.payload.repository.default_branch) || 'main';
  await github.rest.actions.createWorkflowDispatch({
    owner, repo, workflow_id: LIFECYCLE_WORKFLOW_FILE, ref, inputs: { pr_number: String(prNumber) },
  });
  return undefined;
}

module.exports = { handleVerifyCompleted, APPROVAL_MARKER, REVIEW_REQUIRED_MARKER, REVIEW_RESOLVED_COMMENT };
