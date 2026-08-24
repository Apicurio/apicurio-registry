// PR Lifecycle Orchestrator
//
// State machine for PR lifecycle management. Labels drive state,
// comment commands drive transitions. See .github/pr-lifecycle.yml for config.

const fs = require('fs');
const path = require('path');

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const LABELS = {
  READY_FOR_REVIEW: 'lifecycle/ready-for-review',
  // Fast gate (quick-check.yaml) passed for the current HEAD.
  TESTED: 'lifecycle/tested',
  // Approved and tested; the full suite (verify.yaml) is the remaining
  // merge gate. Purely a status label — it does not gate anything itself.
  // What actually gates the full suite running is native: PR author
  // (maintainer/auto_accept run it immediately) or an approving review
  // (everyone else), evaluated directly by verify-decide.yaml.
  READY_TO_MERGE: 'lifecycle/ready-to-merge',
  // Set when the full verification suite passes for the current HEAD.
  FULL_VERIFIED: 'lifecycle/full-verified',
  WAITING_ON_AUTHOR: 'lifecycle/waiting-on-author',
  WAITING_ON_MAINTAINER: 'lifecycle/waiting-on-maintainer',
  STALE: 'lifecycle/stale',
  DISABLED: 'orchestrator/disabled',
};

const PRIMARY_STATES = [
  LABELS.READY_FOR_REVIEW,
  LABELS.READY_TO_MERGE,
];

const CONTROL_LABELS = Object.values(LABELS).filter(
  l => l.startsWith('lifecycle/') || l.startsWith('orchestrator/')
);

const COLORS = {
  INFO: 'A8D8F0',
  SUCCESS_LIGHT: 'B5E8B5',
  SUCCESS: '5BB85B',
  ATTENTION: 'F7BF6A',
  ATTENTION_STRONG: 'E8836B',
  INACTIVE: 'CCCCCC',
};

const LABEL_DEFS = {
  [LABELS.READY_FOR_REVIEW]:     { color: COLORS.INFO, description: 'In review; Quick Check gate runs on every push' },
  [LABELS.TESTED]:               { color: COLORS.SUCCESS, description: 'Quick Check gate passed for current HEAD' },
  [LABELS.FULL_VERIFIED]:        { color: COLORS.SUCCESS, description: 'Full verification suite passed for current HEAD' },
  [LABELS.READY_TO_MERGE]:       { color: COLORS.INFO, description: 'Approved and fast-gated; full suite is the remaining merge gate' },
  [LABELS.WAITING_ON_AUTHOR]:    { color: COLORS.ATTENTION_STRONG, description: 'Blocked on contributor action' },
  [LABELS.WAITING_ON_MAINTAINER]:{ color: COLORS.ATTENTION, description: 'Blocked on maintainer action' },
  [LABELS.STALE]:                { color: COLORS.INACTIVE, description: 'No activity for 4+ days (waiting on author) or 7+ days' },
  [LABELS.DISABLED]:             { color: COLORS.INACTIVE, description: 'PR excluded from lifecycle orchestrator' },
};

const BOT_LOGIN = 'github-actions[bot]';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function loadConfig() {
  const configPath = path.join(process.cwd(), '.github', 'pr-lifecycle.json');
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

function isMaintainer(config, username) {
  return config.maintainers.includes(username);
}

function isAutoAccepted(config, username) {
  return config.maintainers.includes(username) ||
    (config.auto_accept || []).includes(username);
}

function parseCommand(body) {
  for (const line of body.split('\n')) {
    const trimmed = line.trim();
    const match = trimmed.match(/^\/(\S+)(?:\s+(.*))?$/);
    if (match) {
      return { command: match[1], args: match[2] ? match[2].trim() : '' };
    }
  }
  return null;
}

function getLabelNames(pr) {
  return (pr.labels || []).map(l => l.name);
}

function hasLabel(pr, label) {
  return getLabelNames(pr).includes(label);
}

function getLifecycleState(pr) {
  const labels = getLabelNames(pr);
  for (const state of PRIMARY_STATES) {
    if (labels.includes(state)) return state;
  }
  return null;
}

function createApi(github, owner, repo) {
  const ensuredLabels = new Set();

  async function ensureLabel(name) {
    if (ensuredLabels.has(name)) return;
    const def = LABEL_DEFS[name];
    if (!def) return;
    try {
      const { data: existing } = await github.rest.issues.getLabel({ owner, repo, name });
      if (existing.color !== def.color || existing.description !== def.description) {
        await github.rest.issues.updateLabel({ owner, repo, name, color: def.color, description: def.description });
      }
    } catch (e) {
      if (e.status === 404) {
        await github.rest.issues.createLabel({ owner, repo, name, color: def.color, description: def.description });
      } else {
        throw e;
      }
    }
    ensuredLabels.add(name);
  }

  return {
    addLabel: async (prNumber, label) => {
      await ensureLabel(label);
      await github.rest.issues.addLabels({
        owner, repo, issue_number: prNumber, labels: [label],
      });
    },

    removeLabel: async (prNumber, label) => {
      try {
        await github.rest.issues.removeLabel({
          owner, repo, issue_number: prNumber, name: label,
        });
      } catch (e) {
        if (e.status !== 404) throw e;
      }
    },

    setLifecycleState: async (pr, newState) => {
      const labels = getLabelNames(pr);
      if (newState) {
        await ensureLabel(newState);
        await github.rest.issues.addLabels({
          owner, repo, issue_number: pr.number, labels: [newState],
        });
      }
      for (const state of PRIMARY_STATES) {
        if (state !== newState && labels.includes(state)) {
          await github.rest.issues.removeLabel({
            owner, repo, issue_number: pr.number, name: state,
          }).catch(e => { if (e.status !== 404) throw e; });
        }
      }
    },

    postComment: async (prNumber, body) => {
      await github.rest.issues.createComment({
        owner, repo, issue_number: prNumber, body,
      });
    },

    addReaction: async (commentId, reaction) => {
      await github.rest.reactions.createForIssueComment({
        owner, repo, comment_id: commentId, content: reaction,
      });
    },

    getPr: async (prNumber) => {
      const { data } = await github.rest.pulls.get({
        owner, repo, pull_number: prNumber,
      });
      return data;
    },

    getReviews: async (prNumber) => {
      return github.paginate(github.rest.pulls.listReviews, {
        owner, repo, pull_number: prNumber, per_page: 100,
      });
    },

    closePr: async (prNumber) => {
      await github.rest.pulls.update({
        owner, repo, pull_number: prNumber, state: 'closed',
      });
    },

    // Native GitHub auto-merge: GitHub itself merges the PR the moment its
    // required checks and required review are satisfied — no bot polling,
    // no rebase-retry, no pending-merge bookkeeping. Repo already has
    // "Automatically delete head branches" enabled, so branch cleanup is
    // native too.
    enableAutoMerge: async (prNumber, mergeMethod) => {
      const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: prNumber });
      await github.graphql(
        `mutation($id: ID!, $method: PullRequestMergeMethod!) {
          enablePullRequestAutoMerge(input: { pullRequestId: $id, mergeMethod: $method }) { clientMutationId }
        }`,
        { id: pr.node_id, method: mergeMethod.toUpperCase() }
      );
    },

    disableAutoMerge: async (prNumber) => {
      const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: prNumber });
      await github.graphql(
        `mutation($id: ID!) {
          disablePullRequestAutoMerge(input: { pullRequestId: $id }) { clientMutationId }
        }`,
        { id: pr.node_id }
      );
    },

    updateBranch: async (prNumber, expectedHeadSha) => {
      const params = { owner, repo, pull_number: prNumber };
      if (expectedHeadSha) params.expected_head_sha = expectedHeadSha;
      await github.rest.pulls.updateBranch(params);
    },

    findLatestVerifyRun: async (headSha, workflow = 'verify.yaml') => {
      const { data } = await github.rest.actions.listWorkflowRuns({
        owner, repo, workflow_id: workflow,
        head_sha: headSha, per_page: 1,
      });
      return data.workflow_runs[0] || null;
    },

    reRunWorkflow: async (runId) => {
      try {
        await github.rest.actions.reRunWorkflow({ owner, repo, run_id: runId });
      } catch (e) {
        if (e.status !== 409) throw e;
      }
    },

    approveWorkflowRun: async (runId) => {
      await github.request('POST /repos/{owner}/{repo}/actions/runs/{run_id}/approve', {
        owner, repo, run_id: runId,
      });
    },

    // GitHub represents runs awaiting fork PR approval as    // status=completed, conclusion=action_required. The API's status
    // query parameter accepts 'action_required' as a filter value even
    // though the run object itself stores it in the conclusion field.
    findPendingApprovalVerifyRuns: async (headSha, workflow = 'verify.yaml') => {
      const { data } = await github.rest.actions.listWorkflowRuns({
        owner, repo, workflow_id: workflow,
        head_sha: headSha, status: 'action_required',
        per_page: 10,
      });
      return data.workflow_runs;
    },
  };
}

// Two-tier CI routing: the fast gate (quick-check.yaml, workflow name "Quick
// Check") covers PR iteration; the full suite (verify.yaml — build, unit
// tests, CLI, SDKs, console plugin, integration tests, extra tests, operator
// tests, and on push, publishing) is the pre-merge gate. It runs on its own
// native triggers (push for trusted authors, review submission for everyone
// else) rather than a bot-applied label. verify.yaml has a single Decide
// job shared by every job in it, so there is exactly one workflow (and one
// answer to "is the full suite required") to track for full-verified —
// unlike the fast gate, which is intentionally a separate, independent
// workflow with no lifecycle awareness of its own.
const FAST_GATE_WORKFLOW = 'Quick Check';
const FULL_SUITE_WORKFLOWS = ['Verify'];
const FULL_SUITE_WORKFLOW_FILES = ['verify.yaml'];

// Aggregates the latest run of each full-suite workflow for a commit.
// Returns:
//   { status: 'pending' }  — some workflow has no run yet or is still running
//   { status: 'success' }  — every workflow's latest run succeeded
//   { status: 'failure' }  — at least one genuinely failed/was cancelled (fail fast)
async function getFullSuiteResult(github, owner, repo, headSha, core) {
  const { data } = await github.rest.actions.listWorkflowRunsForRepo({
    owner, repo, head_sha: headSha, per_page: 100,
  });
  const latest = new Map();
  for (const run of data.workflow_runs) {
    if (!FULL_SUITE_WORKFLOWS.includes(run.name)) continue;
    const prev = latest.get(run.name);
    if (!prev || new Date(run.created_at) > new Date(prev.created_at)) {
      latest.set(run.name, run);
    }
  }
  for (const name of FULL_SUITE_WORKFLOWS) {
    const run = latest.get(name);
    if (!run || run.status !== 'completed') {
      core.info(`Full suite for ${headSha}: ${name} ${run ? run.status : 'has no run yet'}, waiting`);
      return { status: 'pending' };
    }
  }
  for (const run of latest.values()) {
    if (run.conclusion !== 'success') {
      // verify.yaml's Gate job intentionally fails (not skips) while Decide
      // has not required the full suite yet (author isn't trusted and the
      // PR isn't approved), as the only way to make the required
      // branch-protection check honestly reflect "not satisfied yet"
      // instead of the false-pass a skipped required job would produce
      // (see verify.yaml's Gate for the full reasoning). That failure is
      // expected and must NOT be treated as a genuine full-suite failure
      // here — every job in the run other than Gate itself is either
      // success or skipped in that case, since Decide gated all of them on
      // the same not-yet-ready state. Only trust 'failure' when some other
      // job actually failed.
      const { data: { jobs } } = await github.rest.actions.listJobsForWorkflowRun({
        owner, repo, run_id: run.id, per_page: 100,
      });
      const realFailure = jobs.some(j => j.name !== 'Verification Gate' && j.conclusion === 'failure');
      if (!realFailure) {
        core.info(`Full suite for ${headSha}: ${run.name}'s only failure is its own not-ready-to-merge-yet check, treating as pending`);
        return { status: 'pending' };
      }
      return { status: 'failure', failedRun: run };
    }
  }
  return { status: 'success' };
}

// Used by /retry. Both verify.yaml and quick-check.yaml now trigger natively
// off PR events (push, review submission) — nothing needs to force a
// re-run purely because the bot changed a label — so this only ever
// re-runs a workflow that is actually stuck or failed.
async function retriggerVerify(api, pr, core, isTrustedAuthor) {
  // The full suite is the relevant workflow once approved+tested (about to
  // merge) or for a trusted author (it runs from the start for them);
  // otherwise the fast gate is what /retry should be looking at.
  const checkFullSuite = getLifecycleState(pr) === LABELS.READY_TO_MERGE || isTrustedAuthor;
  if (checkFullSuite) {
    let found = false;
    for (const workflow of FULL_SUITE_WORKFLOW_FILES) {
      found = (await retriggerWorkflowRun(api, pr, core, workflow)) || found;
    }
    if (!found) {
      await triggerViaBranchUpdate(api, pr, core, 'the full suite');
    }
    return;
  }
  const found = await retriggerWorkflowRun(api, pr, core, 'quick-check.yaml');
  if (!found) {
    await triggerViaBranchUpdate(api, pr, core, 'quick-check.yaml');
  }
}

async function triggerViaBranchUpdate(api, pr, core, workflow) {
  // No run exists for this SHA (e.g. PR predates the current
  // workflow, or the run was cleaned up). Update the PR branch to
  // trigger a fresh synchronize event and a new run.
  // handlePrSynchronize will approve the new run for fork PRs.
  core.warning(`PR #${pr.number} no ${workflow} run found for ${pr.head.sha}, attempting branch update`);
  try {
    await api.updateBranch(pr.number, pr.head.sha);
    core.info(`PR #${pr.number} branch updated to trigger fresh ${workflow} run`);
    return;
  } catch (e) {
    core.warning(`PR #${pr.number} branch update failed: ${e.message}`);
    await api.postComment(pr.number,
      `Could not trigger the test suite automatically — no existing workflow run ` +
      `was found and the branch could not be updated. @${pr.user.login}, please push ` +
      `a change to trigger CI.`
    );
    return;
  }
}

// Retriggers the latest run of a single workflow file for the PR's head SHA.
// Returns true when a run existed (regardless of what was done with it).
async function retriggerWorkflowRun(api, pr, core, workflow) {
  const run = await api.findLatestVerifyRun(pr.head.sha, workflow);

  if (!run) {
    return false;
  }

  // Fork PRs need workflow approval before they can run. Approve instead
  // of re-running — Decide re-evaluates live, so the approved run will see
  // current PR/review state.
  // GitHub represents these as status=completed, conclusion=action_required.
  if (run.conclusion === 'action_required') {
    try {
      await api.approveWorkflowRun(run.id);
      core.info(`PR #${pr.number} approved pending ${workflow} run ${run.id}`);
    } catch (e) {
      core.warning(`PR #${pr.number} failed to approve ${workflow} run ${run.id}: ${e.message}`);
    }
    return true;
  }

  // Only re-run workflows that actually need it; a green run is left alone.
  if (run.conclusion === 'success') {
    core.info(`PR #${pr.number} ${workflow} run ${run.id} already green, not re-triggering`);
    return true;
  }

  if (run.status === 'in_progress' || run.status === 'queued') {
    core.info(`PR #${pr.number} ${workflow} run ${run.id} already running, not re-triggering`);
    return true;
  }

  try {
    await api.reRunWorkflow(run.id);
    core.info(`PR #${pr.number} re-triggered ${workflow} run ${run.id}`);
  } catch (e) {
    core.warning(`PR #${pr.number} failed to re-trigger ${workflow} run ${run.id}: ${e.message}`);
  }
  return true;
}

// Approves all Verify workflow runs awaiting approval for a PR's head SHA.
// Called after events that should enable CI (e.g. a fresh push) to catch
// label-triggered or event-triggered runs that race with retriggerVerify.
async function approvePendingVerifyRuns(api, pr, core, workflow = 'verify.yaml') {
  try {
    const runs = await api.findPendingApprovalVerifyRuns(pr.head.sha, workflow);
    if (runs.length === 0) {
      core.info(`PR #${pr.number} no pending-approval ${workflow} runs found for ${pr.head.sha}`);
      return 0;
    }
    let approved = 0;
    for (const run of runs) {
      try {
        await api.approveWorkflowRun(run.id);
        approved++;
        core.info(`PR #${pr.number} approved pending ${workflow} run ${run.id}`);
      } catch (e) {
        core.warning(`PR #${pr.number} failed to approve ${workflow} run ${run.id}: ${e.message}`);
      }
    }
    return approved;
  } catch (e) {
    core.warning(`PR #${pr.number} failed to list pending ${workflow} runs: ${e.message}`);
    return 0;
  }
}

// Approves pending fork-PR runs for both tiers: the fast gate
// (quick-check.yaml) and the full suite (verify.yaml). Approving a run the
// current state will skip is harmless; missing one strands a fork PR on
// "action_required".
async function approveAllPendingCiRuns(api, pr, core) {
  let approved = 0;
  for (const workflow of FULL_SUITE_WORKFLOW_FILES) {
    approved += await approvePendingVerifyRuns(api, pr, core, workflow);
  }
  return approved;
}

function latestReviewsByReviewer(reviews) {
  const latestByReviewer = new Map();
  for (const review of reviews) {
    if (review.state === 'APPROVED' || review.state === 'CHANGES_REQUESTED') {
      const existing = latestByReviewer.get(review.user.login);
      if (!existing || new Date(review.submitted_at) > new Date(existing.submitted_at)) {
        latestByReviewer.set(review.user.login, review);
      }
    }
  }
  return Array.from(latestByReviewer.values());
}

function isApproved(reviews) {
  const latest = latestReviewsByReviewer(reviews);
  const hasApproval = latest.some(r => r.state === 'APPROVED');
  const hasChangesRequested = latest.some(r => r.state === 'CHANGES_REQUESTED');
  return hasApproval && !hasChangesRequested;
}

function hasLatestChangesRequested(reviews) {
  return latestReviewsByReviewer(reviews).some(r => r.state === 'CHANGES_REQUESTED');
}

// Enables (or, if already on, disables) native GitHub auto-merge for a PR.
// GitHub merges automatically once its own required checks and required
// review are satisfied — no bot polling, no rebase-retry, no
// pending-merge/merge-rebase bookkeeping needed.
async function setAutoMerge(api, config, pr, core) {
  const freshPr = await api.getPr(pr.number);
  if (freshPr.auto_merge) {
    await api.disableAutoMerge(pr.number);
    core.info(`PR #${pr.number} auto-merge disabled`);
    return 'disabled';
  }

  const strategy = config.merge?.strategy || 'rebase';
  try {
    await api.enableAutoMerge(pr.number, strategy);
    core.info(`PR #${pr.number} auto-merge enabled (${strategy})`);
    return 'enabled';
  } catch (e) {
    const workflowHint = e.message?.includes('Resource not accessible')
      ? ' This may be because the PR modifies workflow files, which requires a manual merge via the GitHub UI (the `workflow` token scope is not available to GitHub Actions).'
      : '';
    core.error(`PR #${pr.number} failed to enable auto-merge: ${e.message}`);
    await api.postComment(pr.number, `Could not enable auto-merge: ${e.message}${workflowHint}`);
    return 'error';
  }
}

// Promotes ready-for-review to ready-to-merge once approved and fast-gated.
// Purely a status transition now — it does not gate or trigger anything:
// the full suite already runs on its own native triggers (PR push for
// trusted authors, review submission for everyone else), and merging (if
// auto-merge was enabled via /merge) is entirely GitHub's own job from here.
async function checkAndTransitionToReady(api, pr, core, reviews) {
  if (!reviews) reviews = await api.getReviews(pr.number);
  const approved = isApproved(reviews);
  const tested = hasLabel(pr, LABELS.TESTED);
  const state = getLifecycleState(pr);

  if (approved && tested && state === LABELS.READY_FOR_REVIEW) {
    await api.setLifecycleState(pr, LABELS.READY_TO_MERGE);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);

    // Ping reviewers/requested reviewers so they know the PR is ready
    const reviewerLogins = [...new Set(reviews.map(r => r.user.login))];
    const requestedLogins = (pr.requested_reviewers || []).map(r => r.login);
    const allReviewers = [...new Set([...reviewerLogins, ...requestedLogins])];
    const mentions = allReviewers
      .filter(login => login !== pr.user.login)
      .map(login => `@${login}`)
      .join(' ');
    const mentionSuffix = mentions ? ` ${mentions}` : '';

    await api.postComment(pr.number,
      `This PR is approved and has passed the Quick Check gate. The full verification suite is the ` +
      `remaining merge gate.${mentionSuffix} A maintainer can merge it with \`/merge\` — it enables ` +
      `auto-merge, which completes once the full suite passes and the review is still valid.`
    );
    core.info(`PR #${pr.number} is ready to merge`);
    return true;
  }
  return false;
}

// ---------------------------------------------------------------------------
// Reconciler
// ---------------------------------------------------------------------------

// Cleans up PRs still carrying labels from before the WIP/smoke-test removal,
// so they don't get stuck with a stale lifecycle/wip label nobody recognizes.
const LEGACY_WIP_LABEL = 'lifecycle/wip';
const LEGACY_SMOKE_TESTED_LABEL = 'lifecycle/smoke-tested';
const LEGACY_TESTS_DISABLED_LABEL = 'orchestrator/tests-disabled';
// Retired when the full suite moved from a bot-label-gated trigger to a
// native one (maintainer/auto_accept authorship or an approving review):
// these no longer mean anything, they are just dropped.
const LEGACY_NEW_LABEL = 'lifecycle/new';
const LEGACY_REVIEW_APPROVED_LABEL = 'lifecycle/review-approved';
const LEGACY_REVIEW_SKIPPED_LABEL = 'orchestrator/review-skipped';
const LEGACY_PENDING_MERGE_LABEL = 'orchestrator/merge-pending';
const LEGACY_MERGE_REBASE_LABEL = 'orchestrator/merge-rebase';
// Retired in favor of native GitHub auto-merge (checked via pr.auto_merge).
const LEGACY_AUTO_MERGE_LABEL = 'orchestrator/auto-merge';
const SIMPLE_RETIRED_LABELS = [
  LEGACY_REVIEW_APPROVED_LABEL, LEGACY_REVIEW_SKIPPED_LABEL,
  LEGACY_PENDING_MERGE_LABEL, LEGACY_MERGE_REBASE_LABEL,
];

async function migrateLegacyLabels(api, pr, core) {
  const labels = getLabelNames(pr);
  let migrated = false;

  if (labels.includes(LEGACY_SMOKE_TESTED_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_SMOKE_TESTED_LABEL);
  }
  if (labels.includes(LEGACY_TESTS_DISABLED_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_TESTS_DISABLED_LABEL);
  }
  for (const label of SIMPLE_RETIRED_LABELS) {
    if (labels.includes(label)) {
      await api.removeLabel(pr.number, label);
      core.info(`PR #${pr.number} removed retired label ${label}`);
    }
  }
  if (labels.includes(LEGACY_AUTO_MERGE_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_AUTO_MERGE_LABEL);
    const config = loadConfig();
    await setAutoMerge(api, config, pr, core);
    core.info(`PR #${pr.number} migrated ${LEGACY_AUTO_MERGE_LABEL} to native auto-merge`);
  }
  if (labels.includes(LEGACY_NEW_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_NEW_LABEL);
    if (!pr.draft && !getLifecycleState(pr)) {
      await api.addLabel(pr.number, LABELS.READY_FOR_REVIEW);
      await api.postComment(pr.number,
        `**Lifecycle update:** the triage (\`lifecycle/new\` / \`/accept\`) stage has been removed — ` +
        `this PR now moves straight to \`lifecycle/ready-for-review\`.`
      );
      migrated = true;
    }
    core.info(`PR #${pr.number} migrated off retired ${LEGACY_NEW_LABEL}`);
  }

  const hasLegacyWip = labels.includes(LEGACY_WIP_LABEL);
  if (!hasLegacyWip) return migrated;

  await api.removeLabel(pr.number, LEGACY_WIP_LABEL);

  // Drafts are outside the lifecycle now — just drop the legacy label.
  if (pr.draft) {
    core.info(`PR #${pr.number} is a draft — removed legacy lifecycle/wip without promoting`);
    return true;
  }

  await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
  // A legacy WIP PR whose smoke tests failed still carries waiting-on-author;
  // clear it so the PR doesn't end up with both waiting-on-* labels until the
  // retriggered suite reports back.
  await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.postComment(pr.number,
    `**Lifecycle update:** the \`lifecycle/wip\` stage has been removed. This PR has been ` +
    `migrated to \`lifecycle/ready-for-review\` and the Quick Check gate will run.`
  );
  core.warning(`PR #${pr.number} migrated from legacy lifecycle/wip to ${LABELS.READY_FOR_REVIEW}`);
  return true;
}

async function reconcile(github, api, pr, core) {
  if (await migrateLegacyLabels(api, pr, core)) return;

  // The orchestrator ignores draft PRs entirely — nothing to reconcile,
  // whether or not the PR still carries lifecycle labels from before it was
  // converted back to draft. Stripping those labels is handled by
  // handlePrConvertedToDraft.
  if (pr.draft) {
    core.info(`PR #${pr.number} is a draft — skipping reconcile`);
    return;
  }

  const state = getLifecycleState(pr);

  // 1. No lifecycle label at all → recover into ready-for-review. There is
  //    no separate triage stage any more: Quick Check already runs for every
  //    PR on its own native trigger, and the full suite runs immediately for
  //    trusted authors or after an approving review — neither depends on
  //    this label, so recovering it is just a display fix.
  if (!state) {
    if (hasLabel(pr, LABELS.DISABLED)) return;

    await api.addLabel(pr.number, LABELS.READY_FOR_REVIEW);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.postComment(pr.number,
      `**Warning:** This PR was missing a lifecycle label, which indicates the ` +
      `PR lifecycle orchestrator may have failed during initial processing. ` +
      `The label has been restored automatically.`
    );
    core.warning(`PR #${pr.number} had no lifecycle label — recovered as ${LABELS.READY_FOR_REVIEW}`);
    return;
  }

  // 2. Ready-for-review: fix waiting-on-* labels based on review state,
  //    and check if it should transition to ready-to-merge
  if (state === LABELS.READY_FOR_REVIEW) {
    const reviews = await api.getReviews(pr.number);
    const approved = isApproved(reviews);
    const hasChangesRequested = !approved && hasLatestChangesRequested(reviews);

    if (hasChangesRequested) {
      await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
      await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      core.info(`PR #${pr.number} reconciler fixed waiting-on labels (changes requested)`);
    } else if (!hasChangesRequested && hasLabel(pr, LABELS.WAITING_ON_AUTHOR) && hasLabel(pr, LABELS.TESTED)) {
      // Only remove waiting-on-author if tests passed — otherwise the label
      // may have been set by a test failure, not a review.
      await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
      await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      core.info(`PR #${pr.number} reconciler fixed waiting-on labels (changes addressed)`);
    }

    const promoted = await checkAndTransitionToReady(api, pr, core, reviews);
    if (promoted) {
      core.info(`PR #${pr.number} reconciler transitioned to ready-to-merge`);
    }
  }

  // 3. Ready-to-merge: verify the review is still valid (handles dismissals
  //    and changes-requested that arrived after the transition).
  if (state === LABELS.READY_TO_MERGE) {
    const reviews = await api.getReviews(pr.number);
    const approved = isApproved(reviews);

    if (!approved) {
      await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);

      if (hasLatestChangesRequested(reviews)) {
        await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
        await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      } else {
        await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      }

      core.info(`PR #${pr.number} reconciler reverted from ready-to-merge (review no longer approved)`);
    }
  }
}

// ---------------------------------------------------------------------------
// Event Handlers
// ---------------------------------------------------------------------------

async function countOpenPrsByAuthor(github, owner, repo, author, excludePr) {
  const prs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', per_page: 100,
  });
  return prs.filter(p => p.user.login === author && p.number !== excludePr);
}

// Puts a PR at the top of the lifecycle, fresh or after leaving draft. Every
// non-draft PR goes straight to ready-for-review — there is no separate
// triage/accept stage; Quick Check already runs for every PR on its own
// native trigger (fork-PR workflow approval, if configured, is GitHub's own
// gate on whether a stranger's code runs at all), and the full suite runs
// immediately for trusted authors or after an approving review for everyone
// else — both decided natively by verify-decide.yaml, not by anything this
// function does.
// Drafts never reach here — they're ignored until marked ready for review.
async function initNewPr(github, owner, repo, api, config, pr, core) {
  const trusted = isAutoAccepted(config, pr.user.login);

  if (!trusted) {
    const existingPrs = await countOpenPrsByAuthor(github, owner, repo, pr.user.login, pr.number);
    const maxPrs = config.max_contributor_prs ?? 1;
    if (existingPrs.length >= maxPrs) {
      const prLinks = existingPrs.map(p => `#${p.number}`).join(', ');
      await api.postComment(pr.number,
        `Thanks for your contribution! However, you already have ${existingPrs.length > 1 ? 'open PRs' : 'an open PR'} ` +
        `(${prLinks}). To keep the review pipeline manageable, each ` +
        `contributor can have at most ${maxPrs} open PR(s) at a time.\n\n` +
        `Please complete or close your existing PR before opening a new one. ` +
        `This PR has been closed automatically.`
      );
      await api.closePr(pr.number);
      core.info(`PR #${pr.number} closed: ${pr.user.login} already has ${existingPrs.length} open PR(s)`);
      return;
    }
  }

  await api.addLabel(pr.number, LABELS.READY_FOR_REVIEW);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);

  const forkHint = pr.head.repo?.full_name !== `${owner}/${repo}`
    ? `\n\n**Note (fork PR):** label updates may not apply automatically. ` +
      `A maintainer can use \`/retry\` after reviewing to update them.`
    : '';

  if (trusted) {
    await api.postComment(pr.number,
      `Thanks for opening this PR! As a trusted author, the full verification suite starts ` +
      `immediately — it does not wait for a review. A maintainer's review is still required to merge.` +
      forkHint
    );
    core.info(`PR #${pr.number} opened by trusted author ${pr.user.login}, state=${LABELS.READY_FOR_REVIEW}`);
    return;
  }

  const message = config.welcome_message.replace(/\{author\}/g, pr.user.login) + forkHint;
  await api.postComment(pr.number, message);
  core.info(`PR #${pr.number} opened, state=${LABELS.READY_FOR_REVIEW}`);
}

async function handlePrOpened({ github, context, core }) {
  const pr = context.payload.pull_request;
  if (pr.draft) {
    core.info(`PR #${pr.number} opened as draft, orchestrator ignoring until marked ready for review`);
    return;
  }
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const config = loadConfig();
  await initNewPr(github, owner, repo, api, config, pr, core);
}

async function handlePrSynchronize({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  // Drafts are outside the lifecycle — pushes to a draft are not our business.
  if (pr.draft) {
    core.info(`PR #${pr.number} is a draft — ignoring push`);
    return;
  }

  if (hasLabel(pr, LABELS.TESTED)) {
    await api.removeLabel(pr.number, LABELS.TESTED);
    core.info(`PR #${pr.number} new push, removed lifecycle/tested`);
  }
  if (hasLabel(pr, LABELS.FULL_VERIFIED)) {
    await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
    core.info(`PR #${pr.number} new push, removed lifecycle/full-verified`);
  }
  if (hasLabel(pr, LABELS.STALE)) {
    await api.removeLabel(pr.number, LABELS.STALE);
    core.info(`PR #${pr.number} new push, removed lifecycle/stale`);
  }

  const state = getLifecycleState(pr);
  if (state === LABELS.READY_TO_MERGE) {
    const freshPr = await api.getPr(pr.number);
    await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    core.info(`PR #${pr.number} reverted from ready-to-merge to ready-for-review`);
  }

  const freshPr = await api.getPr(pr.number);
  await reconcile(github, api, freshPr, core);

  // For fork PRs, the synchronize event creates a new run that needs
  // approval. Wait for it to appear, then approve.
  await new Promise(r => setTimeout(r, 5000));
  const approved = await approveAllPendingCiRuns(api, freshPr, core);
  if (approved > 0) {
    core.info(`PR #${pr.number} approved ${approved} pending CI run(s) after push`);
  }
}

// Fires when a PR is converted back to draft. Drafts are outside the lifecycle
// (no labels, no CI), so strip every lifecycle label the PR accumulated —
// otherwise verify.yaml would keep seeing ready-for-review and running the full
// suite on every push to a draft.
async function handlePrConvertedToDraft({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  const toRemove = getLabelNames(pr).filter(l => l.startsWith('lifecycle/'));
  if (toRemove.length === 0) {
    core.info(`PR #${pr.number} converted to draft with no lifecycle labels, nothing to do`);
    return;
  }

  for (const label of toRemove) {
    await api.removeLabel(pr.number, label);
  }

  await api.postComment(pr.number,
    `Converted to draft — the orchestrator ignores draft PRs, so lifecycle labels ` +
    `have been removed and CI will not run on new pushes.\n\n` +
    `Mark the PR as ready for review to re-enter the lifecycle at \`lifecycle/ready-for-review\`.`
  );
  core.info(`PR #${pr.number} converted to draft, removed: ${toRemove.join(', ')}`);
}

// Fires when a draft PR goes ready — its first contact with the orchestrator,
// so it enters the lifecycle just like a freshly opened PR.
async function handlePrReadyForReview({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const config = loadConfig();

  if (getLifecycleState(pr)) {
    core.info(`PR #${pr.number} marked ready for review but already has a lifecycle label, skipping init`);
    return;
  }

  core.info(`PR #${pr.number} marked ready for review (was draft), entering the lifecycle`);
  await initNewPr(github, owner, repo, api, config, pr, core);
}

// Fires when a review is submitted. verify.yaml (the full suite) already
// reacts to this natively (pull_request_review: submitted) for non-trusted
// authors; this reconciles labels (waiting-on-*, ready-to-merge) right away
// instead of waiting for the next label-change event or the periodic sweep.
// Fires when a review is submitted. verify.yaml has its own native
// pull_request_review trigger (safe — GitHub gives it the same restricted,
// secret-less fork-PR token as pull_request, and GITHUB_REF/GITHUB_SHA
// already resolve to the PR's merge branch, same as pull_request), so it
// re-evaluates Decide and starts the full suite on its own the moment a
// review lands. This just keeps the display labels (waiting-on-*,
// ready-to-merge) in sync right away instead of waiting for the next
// label-change event or the periodic sweep.
async function handlePrReviewSubmitted({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  if (hasLabel(pr, LABELS.DISABLED) || pr.draft) return;

  const freshPr = await api.getPr(pr.number);
  await reconcile(github, api, freshPr, core);
}

async function handleComment({ github, context, core }) {
  const comment = context.payload.comment;
  const issue = context.payload.issue;

  if (!issue.pull_request) return;

  const parsed = parseCommand(comment.body);
  if (!parsed) return;

  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const config = loadConfig();
  const pr = await api.getPr(issue.number);
  const actor = comment.user.login;
  const maintainer = isMaintainer(config, actor);
  const isAuthor = actor === pr.user.login;

  const handlers = {
    'reject': () => cmdReject(api, config, core, pr, actor, maintainer, parsed.args, comment.id),
    'merge': () => cmdMerge(api, config, core, pr, actor, maintainer, comment.id),
    'unstale': () => cmdUnstale(api, config, core, pr, actor, isAuthor, maintainer, comment.id),
    'retry': () => cmdRetry(github, api, config, core, pr, actor, isAuthor, maintainer, comment.id),
  };

  const handler = handlers[parsed.command];
  if (handler) {
    await handler();
  }
}

// ---------------------------------------------------------------------------
// Command Handlers
// ---------------------------------------------------------------------------

// Maintainer moderation — closes a PR that should not be worked further,
// regardless of its current lifecycle state.
async function cmdReject(api, config, core, pr, actor, maintainer, reason, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can reject PRs.`);
    return;
  }

  const reasonText = reason ? `\n\nReason: ${reason}` : '';
  await api.postComment(pr.number,
    `PR rejected by @${actor}.${reasonText}\n\n` +
    `@${pr.user.login}, please address the feedback and reopen if appropriate.`
  );
  await api.closePr(pr.number);
  await api.addReaction(commentId, '+1');
  core.info(`PR #${pr.number} rejected by ${actor}`);
}

// Toggles native GitHub auto-merge. GitHub merges automatically once its own
// required checks and required review are satisfied — this does not require
// the PR to already be lifecycle/ready-to-merge; it just queues the intent.
async function cmdMerge(api, config, core, pr, actor, maintainer, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can merge PRs.`);
    return;
  }

  const result = await setAutoMerge(api, config, pr, core);
  await api.addReaction(commentId, result === 'error' ? '-1' : '+1');
  if (result === 'disabled') {
    await api.postComment(pr.number, `Auto-merge disabled by @${actor}.`);
  } else if (result === 'enabled') {
    await api.postComment(pr.number,
      `Auto-merge enabled by @${actor}. GitHub will merge this PR automatically once all ` +
      `required checks pass and it has an approving review. Use \`/merge\` again to disable.`
    );
  }
}

async function cmdUnstale(api, config, core, pr, actor, isAuthor, maintainer, commentId) {
  if (!isAuthor && !maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number,
      `@${actor} Only the PR author or a maintainer can remove the stale label.`
    );
    return;
  }
  if (!hasLabel(pr, LABELS.STALE)) {
    await api.addReaction(commentId, 'confused');
    return;
  }
  await api.removeLabel(pr.number, LABELS.STALE);
  await api.addReaction(commentId, '+1');
  core.info(`PR #${pr.number} unstaled by ${actor}`);
}

async function cmdRetry(github, api, config, core, pr, actor, isAuthor, maintainer, commentId) {
  if (!isAuthor && !maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number,
      `@${actor} Only the PR author or a maintainer can retry.`
    );
    return;
  }

  await api.addReaction(commentId, '+1');

  // Run the reconciler to fix any label inconsistencies
  const freshPr = await api.getPr(pr.number);
  await reconcile(github, api, freshPr, core);

  // The workflow that matters depends on lifecycle state: the fast gate
  // (quick-check.yaml) during iteration, the full suite (verify.yaml) at
  // ready-to-merge or for trusted authors (it runs from the start for them).
  const isTrustedAuthor = isAutoAccepted(config, freshPr.user.login);
  const atMergeGate = getLifecycleState(freshPr) === LABELS.READY_TO_MERGE || isTrustedAuthor;
  const workflowFiles = atMergeGate ? FULL_SUITE_WORKFLOW_FILES : ['quick-check.yaml'];
  const workflowDesc = atMergeGate ? 'full-suite' : 'quick-check.yaml';
  const latestRuns = [];
  for (const wf of workflowFiles) {
    const run = await api.findLatestVerifyRun(freshPr.head.sha, wf);
    if (run) latestRuns.push(run);
  }
  const latestRun = latestRuns.find(r => r.conclusion === 'action_required')
    || latestRuns.find(r => r.conclusion === 'failure' || r.conclusion === 'cancelled')
    || latestRuns.find(r => r.status !== 'completed')
    || latestRuns[0];
  if (latestRun && latestRun.conclusion === 'action_required') {
    const approved = await approveAllPendingCiRuns(api, freshPr, core);
    await api.postComment(pr.number,
      `Retrying: reconciled PR state and approved ${approved} pending CI ` +
      `workflow run${approved !== 1 ? 's' : ''} (fork PR approval).`
    );
    core.info(`PR #${pr.number} retry: reconciled + approved ${approved} pending run(s) by ${actor}`);
  } else if (latestRun && (latestRun.conclusion === 'failure' || latestRun.conclusion === 'cancelled')) {
    await api.postComment(pr.number,
      `Retrying: reconciled PR state and re-triggering the failed ${workflowDesc} workflow ` +
      `run(s) (previous run [${latestRun.conclusion}](${latestRun.html_url})).`
    );
    await retriggerVerify(api, freshPr, core, isTrustedAuthor);
    core.info(`PR #${pr.number} retry: reconciled + re-triggered ${workflowDesc} by ${actor}`);
  } else if (latestRun && latestRun.status !== 'completed') {
    await api.postComment(pr.number,
      `Retrying: reconciled PR state. The ${workflowDesc} workflow is already running.`
    );
    core.info(`PR #${pr.number} retry: reconciled (${workflowDesc} already running) by ${actor}`);
  } else if (!latestRun) {
    await api.postComment(pr.number,
      `Retrying: reconciled PR state. No ${workflowDesc} run found — attempting to trigger a fresh one.`
    );
    await retriggerVerify(api, freshPr, core, isTrustedAuthor);
    core.info(`PR #${pr.number} retry: reconciled + triggered fresh ${workflowDesc} by ${actor}`);
  } else {
    await api.postComment(pr.number,
      `Retrying: reconciled PR state. No failed ${workflowDesc} run to re-trigger.`
    );
    core.info(`PR #${pr.number} retry: reconciled (no failed run) by ${actor}`);
  }
}

// ---------------------------------------------------------------------------
// Label Protection
// ---------------------------------------------------------------------------

async function handleLabelChange({ github, context, core }) {
  const pr = context.payload.pull_request;
  const label = context.payload.label;
  const action = context.payload.action;
  const actor = context.payload.sender.login;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  if (actor === BOT_LOGIN) return;

  if (!CONTROL_LABELS.includes(label.name)) return;

  const MAINTAINER_EDITABLE = [
    LABELS.WAITING_ON_AUTHOR,
    LABELS.WAITING_ON_MAINTAINER,
    LABELS.STALE,
    LABELS.DISABLED,
  ];

  const config = loadConfig();
  if (isMaintainer(config, actor) && MAINTAINER_EDITABLE.includes(label.name)) {
    core.info(`PR #${pr.number} label ${action}: ${label.name} by maintainer ${actor} (allowed)`);
    return;
  }

  if (action === 'labeled') {
    await api.removeLabel(pr.number, label.name);
    await api.postComment(pr.number,
      `@${actor} The label \`${label.name}\` is managed by the PR lifecycle orchestrator ` +
      `and cannot be added manually. Use the appropriate slash command instead.`
    );
    core.info(`PR #${pr.number} reverted unauthorized label add: ${label.name} by ${actor}`);
  } else if (action === 'unlabeled') {
    await api.addLabel(pr.number, label.name);
    await api.postComment(pr.number,
      `@${actor} The label \`${label.name}\` is managed by the PR lifecycle orchestrator ` +
      `and cannot be removed manually. Use the appropriate slash command instead.`
    );
    core.info(`PR #${pr.number} reverted unauthorized label remove: ${label.name} by ${actor}`);
  }
}

// ---------------------------------------------------------------------------
// Test Result Handler
// ---------------------------------------------------------------------------

async function handleTestResult({ github, context, core }) {
  const workflowRun = context.payload.workflow_run;
  if (workflowRun.event !== 'pull_request') {
    core.info('Workflow run is not from a PR event, skipping');
    return;
  }

  // Two-tier CI routing:
  //  - "Quick Check" (quick-check.yaml): the fast gate. Its result drives
  //    lifecycle/tested while a PR is in ready-for-review. It has no
  //    lifecycle awareness of its own and is not part of the full suite —
  //    unlike before the quick-check.yaml/verify.yaml split, it no longer
  //    doubles as one of the full-suite workflows, so it is intentionally
  //    NOT in FULL_SUITE_WORKFLOWS.
  //  - "Verify": the full suite (build, unit tests, CLI, SDKs, console
  //    plugin, integration tests, extra tests, operator tests). One Decide
  //    job shared by every job in it, so lifecycle/full-verified is applied
  //    or reverted based on this single workflow's outcome. Its runs
  //    completing while a PR is still in ready-for-review are no-ops
  //    (Decide skips every job) and must NOT mark the PR tested.
  const isFastGate = workflowRun.name === FAST_GATE_WORKFLOW;
  if (!isFastGate && !FULL_SUITE_WORKFLOWS.includes(workflowRun.name)) {
    core.info(`Unhandled workflow ${workflowRun.name}, skipping`);
    return;
  }

  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  // Re-run attempts may lose the pull_requests array from the workflow_run
  // payload, and fork PRs always have an empty array. Fall back to
  // searching for PRs by the head SHA, using the head repository owner
  // (which differs from the base owner for fork PRs).
  let prRefs = workflowRun.pull_requests || [];
  if (!prRefs.length) {
    const headOwner = workflowRun.head_repository?.owner?.login || owner;
    const { data: prs } = await github.rest.pulls.list({
      owner, repo, state: 'open', head: `${headOwner}:${workflowRun.head_branch}`, per_page: 10,
    });
    prRefs = prs.filter(p => p.head.sha === workflowRun.head_sha);
    if (!prRefs.length) {
      // Last resort: scan all open PRs by head SHA (covers edge cases where
      // the head-branch filter above misses, e.g. a stale/renamed branch).
      const { data: openPrs } = await github.rest.pulls.list({
        owner, repo, state: 'open', per_page: 50,
      });
      prRefs = openPrs.filter(p => p.head.sha === workflowRun.head_sha);
    }
    if (!prRefs.length) {
      core.info(`No open PR found for branch ${workflowRun.head_branch} / SHA ${workflowRun.head_sha}, skipping`);
      return;
    }
    core.info(`Resolved ${prRefs.length} PR(s) from head branch lookup (re-run fallback)`);
  }

  for (const prRef of prRefs) {
    const pr = await api.getPr(prRef.number);

    if (hasLabel(pr, LABELS.DISABLED)) continue;

    const state = getLifecycleState(pr);

    // Quick Check's result only drives lifecycle/tested while the PR is in
    // ready-for-review; a Quick Check completion arriving after promotion
    // (handlePrSynchronize normally reverts to ready-for-review on every new
    // push before this can happen, but a completion for an already-promoted
    // SHA is possible) falls through to the full-suite branch below instead,
    // which is a harmless no-op re-check of verify.yaml's status.
    const asFastGate = isFastGate && state === LABELS.READY_FOR_REVIEW;

    if (asFastGate) {
      // A late Quick Check completion can belong to a full-tier run that
      // raced a failure revert. If the full suite (verify.yaml) already
      // failed for this SHA, it owns the result — do not promote the PR.
      const suite = await getFullSuiteResult(github, owner, repo, workflowRun.head_sha, core);
      if (suite.status === 'failure') {
        core.info(`PR #${pr.number} full-suite failure recorded for this SHA, skipping fast-gate result`);
        continue;
      }
    }
    // Unlike before, the full-suite branch below is NOT gated on
    // lifecycle/ready-to-merge: verify.yaml now triggers on its own native
    // events (push for trusted authors, review submission for everyone
    // else), so it can legitimately complete before the fast gate/review has
    // finished promoting the PR.

    if (pr.head.sha !== workflowRun.head_sha) {
      core.info(`PR #${pr.number} head SHA mismatch (PR: ${pr.head.sha}, run: ${workflowRun.head_sha}), skipping`);
      continue;
    }

    if (asFastGate) {
      if (workflowRun.conclusion === 'success') {
        await api.addLabel(pr.number, LABELS.TESTED);
        await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
        core.info(`PR #${pr.number} fast gate passed, added lifecycle/tested`);

        const freshPr = await api.getPr(pr.number);
        const promoted = await checkAndTransitionToReady(api, freshPr, core);
        if (!promoted) {
          await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        }
      } else if (workflowRun.conclusion === 'failure') {
        await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
        await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        await api.postComment(pr.number,
          `The Quick Check gate failed for commit ${workflowRun.head_sha.substring(0, 7)}. ` +
          `@${pr.user.login}, please check the ` +
          `[workflow run](${workflowRun.html_url}) and push a fix.`
        );
        core.info(`PR #${pr.number} fast gate failed`);
      } else if (workflowRun.conclusion === 'cancelled') {
        await api.postComment(pr.number,
          `The Quick Check gate was cancelled for commit ${workflowRun.head_sha.substring(0, 7)}. ` +
          `See the [workflow run](${workflowRun.html_url}). Use \`/retry\` to re-run.`
        );
        core.info(`PR #${pr.number} fast gate cancelled`);
      }
    } else {
      // Full suite: the pre-merge gate. verify.yaml must be green for this
      // SHA; a failure or cancellation fails the suite.
      const suite = await getFullSuiteResult(github, owner, repo, workflowRun.head_sha, core);
      if (suite.status === 'pending') {
        core.info(`PR #${pr.number} waiting for the full-suite workflow`);
        continue;
      }
      if (suite.status === 'success') {
        await api.addLabel(pr.number, LABELS.FULL_VERIFIED);
        core.info(`PR #${pr.number} full verification passed, added lifecycle/full-verified`);
      } else {
        const failed = suite.failedRun;
        const verb = failed.conclusion === 'cancelled' ? 'was cancelled' : 'failed';
        await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
        await api.postComment(pr.number,
          `The full verification suite ${verb} for commit ${workflowRun.head_sha.substring(0, 7)} ` +
          `(${failed.name}: ${failed.html_url}). @${pr.user.login}, please check the workflow run and push a fix.`
        );
        if (state === LABELS.READY_TO_MERGE) {
          await api.removeLabel(pr.number, LABELS.TESTED);
          await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
          await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
          await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
          core.info(`PR #${pr.number} full verification ${verb}, reverted to ready-for-review`);
        } else {
          core.info(`PR #${pr.number} full verification ${verb}`);
        }
      }
    }

    // Post or update the decision summary comment
    await postDecisionSummary(github, owner, repo, workflowRun, pr.number, core);
    await postFlakyTestsSummary(github, owner, repo, workflowRun, pr.number, core);

    const reconPr = await api.getPr(pr.number);
    await reconcile(github, api, reconPr, core);
  }
}

async function postDecisionSummary(github, owner, repo, workflowRun, prNumber, core) {
  const DECISION_KEYS = new Set([
    'lifecycle-ready', 'run-build', 'run-unit-tests',
    'run-integration', 'run-extras', 'run-sdk', 'run-cli',
    'run-operator', 'run-go-sdk-freshness',
  ]);
  const CHANGES_KEYS = new Set(['java', 'ui', 'integration', 'sdk', 'cli', 'operator', 'go-sdk-gen', 'ci']);
  const ALLOWED_VALUES = new Set(['true', 'false', 'skip']);
  const EXPECTED_FILES = new Set(['verify-decisions.json', 'verify-changes.json']);
  const MAX_ARTIFACT_BYTES = 10240;

  try {
    // ── Load decisions from artifact (hardened) ───────────────────────
    const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, {
      owner, repo, run_id: workflowRun.id, per_page: 100,
    });
    const artifact = artifacts.find(a => a.name === 'verify-decisions');
    if (!artifact) {
      core.info(`No verify-decisions artifact found for run ${workflowRun.id}, skipping summary`);
      return;
    }
    if (artifact.size_in_bytes > MAX_ARTIFACT_BYTES) {
      core.warning(`Decision artifact too large (${artifact.size_in_bytes} bytes), skipping`);
      return;
    }

    const { data: zip } = await github.rest.actions.downloadArtifact({
      owner, repo, artifact_id: artifact.id, archive_format: 'zip',
    });

    const os = require('os');
    const { execSync } = require('child_process');
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'verify-decisions-'));
    const zipPath = path.join(tmpDir, 'artifact.zip');
    fs.writeFileSync(zipPath, Buffer.from(zip));

    // Validate zip entries before extracting
    const listing = execSync(`unzip -l "${zipPath}"`, { encoding: 'utf8' });
    const entryLines = listing.split('\n').filter(l => l.includes('.json'));
    for (const line of entryLines) {
      const entry = line.trim().split(/\s+/).pop();
      if (entry.includes('..') || path.isAbsolute(entry)) {
        core.warning(`Suspicious path in artifact: ${entry}, skipping`);
        return;
      }
    }

    execSync(`unzip -o "${zipPath}" -d "${tmpDir}"`, { stdio: 'ignore' });

    // Validate extracted files
    const jsonFiles = fs.readdirSync(tmpDir).filter(f => f.endsWith('.json') && f !== 'artifact.zip');
    for (const f of jsonFiles) {
      if (!EXPECTED_FILES.has(f)) {
        core.warning(`Unexpected file in artifact: ${f}, skipping`);
        return;
      }
    }

    function parseAndValidate(filePath, allowedKeys) {
      if (!fs.existsSync(filePath)) return null;
      const raw = fs.readFileSync(filePath, 'utf8');
      if (raw.length > 1024) return null;
      const obj = JSON.parse(raw);
      if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) return null;
      for (const [key, value] of Object.entries(obj)) {
        if (!allowedKeys.has(key) || !ALLOWED_VALUES.has(String(value))) return null;
      }
      return obj;
    }

    const decisions = parseAndValidate(path.join(tmpDir, 'verify-decisions.json'), DECISION_KEYS);
    const changes = parseAndValidate(path.join(tmpDir, 'verify-changes.json'), CHANGES_KEYS);
    if (!decisions) {
      core.warning('Decision JSON missing or invalid, skipping summary');
      return;
    }

    // ── Fetch actual job results ──────────────────────────────────────
    const { data: { jobs } } = await github.rest.actions.listJobsForWorkflowRun({
      owner, repo, run_id: workflowRun.id, per_page: 100,
    });

    const jobResult = (namePrefix) => {
      const matching = jobs.filter(j => j.name.startsWith(namePrefix));
      if (!matching.length) return 'skipped';
      if (matching.some(j => j.conclusion === 'failure')) return 'failure';
      if (matching.every(j => j.conclusion === 'skipped')) return 'skipped';
      if (matching.every(j => j.conclusion === 'success' || j.conclusion === 'skipped')) return 'success';
      return 'mixed';
    };

    const icon = (planned, result) => {
      if (planned !== 'true') return '➖';
      if (result === 'success') return '🟢';
      if (result === 'failure') return '🔴';
      return '🟡';
    };

    const conclusion = workflowRun.conclusion === 'success' ? '✅ passed' : '❌ failed';

    const phases = [
      ['Lint and Validate', 'lifecycle-ready', 'Lint and Validate'],
      // build-java/build-ui are inlined directly in verify.yaml (not a
      // reusable "Build" caller job), so they are named "Build Java
      // Application (no tests)" / "Build UI Application" directly rather
      // than composing as "Build / ...".
      ['Build', 'run-build', 'Build '],
      ['Unit Tests', 'run-unit-tests', 'Unit Tests /'],
      ['Integration Tests', 'run-integration', 'Integration Tests /'],
      ['Extra Tests', 'run-extras', 'Extra Tests /'],
      ['SDK Verification', 'run-sdk', 'SDK Verification /'],
      ['CLI Verification', 'run-cli', 'CLI Verification'],
      ['Operator Tests', 'run-operator', 'Operator Tests /'],
    ];

    const rows = phases.map(([label, key, jobPrefix]) =>
      `| ${label} | ${icon(decisions[key], jobResult(jobPrefix))} |`
    );

    const bodyParts = [
      '<!-- verify-decide-summary -->',
      `**Verify — ${conclusion}** ([run](${workflowRun.html_url}))`,
      '',
      '| Phase | Status |',
      '|-------|--------|',
      ...rows,
    ];

    if (changes) {
      bodyParts.push(
        '',
        '<details><summary>Change detection</summary>',
        '',
        Object.entries(changes)
          .map(([k, v]) => `${k}: \`${v}\``)
          .join(', '),
        '</details>',
      );
    }

    const body = bodyParts.join('\n');

    // ── Minimize previous summaries and post a new comment ────────────
    const comments = await github.paginate(github.rest.issues.listComments, {
      owner, repo, issue_number: prNumber, per_page: 100,
    });
    const previous = comments.filter(c => c.body?.includes('<!-- verify-decide-summary -->'));
    for (const old of previous) {
      try {
        await github.graphql(`
          mutation($id: ID!) {
            minimizeComment(input: { subjectId: $id, classifier: OUTDATED }) {
              minimizedComment { isMinimized }
            }
          }
        `, { id: old.node_id });
      } catch (e) {
        core.warning(`Failed to minimize old summary comment ${old.id}: ${e.message}`);
      }
    }
    await github.rest.issues.createComment({ owner, repo, issue_number: prNumber, body });
    core.info(`PR #${prNumber} decision summary posted`);
  } catch (err) {
    core.warning(`Failed to post decision summary: ${err.message}`);
  }
}

async function postFlakyTestsSummary(github, owner, repo, workflowRun, prNumber, core) {
  try {
    const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, {
      owner, repo, run_id: workflowRun.id, per_page: 100,
    });
    
    const flakyArtifacts = artifacts.filter(a => a.name.startsWith('flaky-tests-'));
    if (!flakyArtifacts.length) {
      core.info(`No flaky-tests artifacts found for run ${workflowRun.id}`);
      await minimizePreviousFlakyComments(github, owner, repo, prNumber, core);
      return;
    }

    const os = require('os');
    const { execSync } = require('child_process');
    const allFlakyTests = [];

    for (const artifact of flakyArtifacts) {
      if (artifact.size_in_bytes > 102400) {
        core.warning(`Flaky test artifact ${artifact.name} is too large (${artifact.size_in_bytes} bytes), skipping`);
        continue;
      }

      const { data: zip } = await github.rest.actions.downloadArtifact({
        owner, repo, artifact_id: artifact.id, archive_format: 'zip',
      });

      const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'flaky-tests-'));
      try {
        const zipPath = path.join(tmpDir, 'artifact.zip');
        fs.writeFileSync(zipPath, Buffer.from(zip));

        const listing = execSync(`unzip -l "${zipPath}"`, { encoding: 'utf8' });
        const lines = listing.split('\n');
        let inEntries = false;
        const entries = [];
        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('---------')) {
            inEntries = !inEntries;
            continue;
          }
          if (inEntries) {
            const parts = trimmed.split(/\s+/);
            if (parts.length >= 4) {
              const entryName = parts.slice(3).join(' ');
              entries.push(entryName);
            }
          }
        }

        let valid = true;
        for (const entry of entries) {
          if (entry.includes('..') || path.isAbsolute(entry)) {
            core.warning(`Suspicious path in flaky-tests artifact zip: ${entry}, skipping`);
            valid = false;
            break;
          }
        }
        if (!valid) continue;

        execSync(`unzip -o "${zipPath}" -d "${tmpDir}"`, { stdio: 'ignore' });

        const jsonFiles = fs.readdirSync(tmpDir).filter(f => f.endsWith('.json') && f !== 'artifact.zip');
        for (const f of jsonFiles) {
          const filePath = path.join(tmpDir, f);
          try {
            const raw = fs.readFileSync(filePath, 'utf8');
            const tests = JSON.parse(raw);
            if (Array.isArray(tests)) {
              const source = artifact.name.substring('flaky-tests-'.length);
              for (const t of tests) {
                allFlakyTests.push({
                  source,
                  class: t.class || 'Unknown',
                  test: t.test || 'Unknown',
                  retries: t.retries || 0,
                  details: t.details || []
                });
              }
            }
          } catch (e) {
            core.warning(`Failed to parse json file ${f}: ${e.message}`);
          }
        }
      } finally {
        try {
          fs.rmSync(tmpDir, { recursive: true, force: true });
        } catch (e) {}
      }
    }

    if (allFlakyTests.length === 0) {
      core.info('No flaky tests detected in artifacts');
      await minimizePreviousFlakyComments(github, owner, repo, prNumber, core);
      return;
    }

    const bodyParts = [
      '<!-- verify-flaky-tests-summary -->',
      '### ⚠️ Flaky Test Retries Detected',
      'The following tests failed initially but passed upon retry in the verification run. These retries can mask performance degradation and consume extra CI resources.',
      '',
      '| Job / Shard | Test Class | Test Name | Retries |',
      '|-------------|------------|-----------|---------|',
    ];

    for (const t of allFlakyTests) {
      const className = t.class.split('.').pop();
      bodyParts.push(`| \`${t.source}\` | \`${className}\` | \`${t.test}\` | ${t.retries} |`);
    }

    bodyParts.push('', '> [!TIP]', '> Flaky tests should be investigated and fixed to maintain CI speed and reliability.');

    const body = bodyParts.join('\n');

    await minimizePreviousFlakyComments(github, owner, repo, prNumber, core);
    await github.rest.issues.createComment({ owner, repo, issue_number: prNumber, body });
    core.info(`PR #${prNumber} flaky tests summary posted`);
  } catch (err) {
    core.warning(`Failed to post flaky tests summary: ${err.message}`);
  }
}

async function minimizePreviousFlakyComments(github, owner, repo, prNumber, core) {
  try {
    const comments = await github.paginate(github.rest.issues.listComments, {
      owner, repo, issue_number: prNumber, per_page: 100,
    });
    const previous = comments.filter(c => c.body?.includes('<!-- verify-flaky-tests-summary -->'));
    for (const old of previous) {
      try {
        await github.graphql(`
          mutation($id: ID!) {
            minimizeComment(input: { subjectId: $id, classifier: OUTDATED }) {
              minimizedComment { isMinimized }
            }
          }
        `, { id: old.node_id });
      } catch (e) {
        core.warning(`Failed to minimize old flaky summary comment ${old.id}: ${e.message}`);
      }
    }
  } catch (err) {
    core.warning(`Failed to minimize old flaky summary comments: ${err.message}`);
  }
}

// ---------------------------------------------------------------------------
// Stale Detection
// ---------------------------------------------------------------------------

async function handleStale({ github, context, core }) {
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const config = loadConfig();
  const daysUntilStale = config.stale?.days_until_stale || 7;
  const daysUntilClose = config.stale?.days_until_close || 14;
  // PRs blocked on the author warn and close sooner (4/7 vs 7/14), with a
  // shorter 3-day post-warning grace — intentional, they're already stalled.
  const daysUntilStaleWaitingOnAuthor = config.stale?.days_until_stale_waiting_on_author || 4;
  const daysUntilCloseWaitingOnAuthor = config.stale?.days_until_close_waiting_on_author || 7;
  const now = new Date();

  const prs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', per_page: 100,
  });

  for (const pr of prs) {
    if (hasLabel(pr, LABELS.DISABLED)) continue;

    // Reconcile all PRs targeting main
    if (pr.base?.ref === 'main') {
      try {
        await reconcile(github, api, pr, core);
      } catch (err) {
        core.warning(`PR #${pr.number} reconcile failed: ${err.message}`);
      }
    }

    const state = getLifecycleState(pr);
    if (!state) continue;
    if (state === LABELS.READY_TO_MERGE) continue;

    const updatedAt = new Date(pr.updated_at);
    const daysSinceUpdate = (now - updatedAt) / (1000 * 60 * 60 * 24);

    const isWaitingOnAuthor = hasLabel(pr, LABELS.WAITING_ON_AUTHOR);
    const effectiveDaysUntilStale = isWaitingOnAuthor ? daysUntilStaleWaitingOnAuthor : daysUntilStale;
    const effectiveDaysUntilClose = isWaitingOnAuthor ? daysUntilCloseWaitingOnAuthor : daysUntilClose;

    if (hasLabel(pr, LABELS.STALE)) {
      const { data: events } = await github.rest.issues.listEventsForTimeline({
        owner, repo, issue_number: pr.number, per_page: 100,
      });

      const staleEvent = events
        .filter(e => e.event === 'labeled' && e.label?.name === LABELS.STALE)
        .pop();

      if (!staleEvent) continue;

      const staleSince = new Date(staleEvent.created_at);
      const daysSinceStale = (now - staleSince) / (1000 * 60 * 60 * 24);

      const hasActivity = events.some(e => {
        if (new Date(e.created_at) <= staleSince) return false;
        if (e.actor?.login === BOT_LOGIN || e.user?.login === BOT_LOGIN) return false;
        return e.event === 'commented' || e.event === 'committed' ||
               e.event === 'head_ref_force_pushed';
      });

      // pr is a snapshot from before this run, so a PR can't hit both the
      // "just went stale" and "already stale" branches in the same pass.
      if (hasActivity) {
        await api.removeLabel(pr.number, LABELS.STALE);
        core.info(`PR #${pr.number} stale removed (activity detected)`);
      } else if (daysSinceStale >= (effectiveDaysUntilClose - effectiveDaysUntilStale)) {
        const closeMessage = (config.stale?.close_message || 'Closing due to inactivity.')
          .replace(/\{author\}/g, pr.user.login);
        await api.postComment(pr.number, closeMessage);
        await api.closePr(pr.number);
        core.info(`PR #${pr.number} closed due to extended inactivity`);
      }
    } else if (daysSinceUpdate >= effectiveDaysUntilStale) {
      await api.addLabel(pr.number, LABELS.STALE);
      const graceDays = Math.max(0, effectiveDaysUntilClose - effectiveDaysUntilStale);
      const closeWindow = graceDays <= 0
        ? 'as soon as the next check'
        : `in ${graceDays} more day${graceDays === 1 ? '' : 's'}`;
      const staleMessage = (config.stale?.stale_message || 'This PR is stale.')
        .replace(/\{author\}/g, pr.user.login)
        .replace(/\{close_window\}/g, closeWindow);
      await api.postComment(pr.number, staleMessage);
      core.info(`PR #${pr.number} marked as stale`);
    }
  }

}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

async function handleReconcile({ github, context, core, prNumber }) {
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const pr = await api.getPr(prNumber);
  core.info(`Reconciling PR #${prNumber} via workflow_dispatch`);
  await reconcile(github, api, pr, core);
}

module.exports = {
  handlePrOpened,
  handlePrSynchronize,
  handlePrReadyForReview,
  handlePrConvertedToDraft,
  handlePrReviewSubmitted,
  handleComment,
  handleLabelChange,
  handleTestResult,
  handleStale,
  handleReconcile,
  reconcile,
  LABELS,
  FULL_SUITE_WORKFLOWS,
};
