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
  NEW: 'lifecycle/new',
  READY_FOR_REVIEW: 'lifecycle/ready-for-review',
  TESTED: 'lifecycle/tested',
  READY_TO_MERGE: 'lifecycle/ready-to-merge',
  // Set when the FULL verification suite passes for the current
  // HEAD. Two-tier CI: lifecycle/tested comes from the fast gate (ci.yaml's
  // Quick Verify job) during iteration; lifecycle/full-verified is the
  // pre-merge gate, applied once all full-suite workflows are green.
  FULL_VERIFIED: 'lifecycle/full-verified',
  WAITING_ON_AUTHOR: 'lifecycle/waiting-on-author',
  WAITING_ON_MAINTAINER: 'lifecycle/waiting-on-maintainer',
  STALE: 'lifecycle/stale',
  REVIEW_APPROVED: 'lifecycle/review-approved',
  DISABLED: 'orchestrator/disabled',
  AUTO_MERGE: 'orchestrator/auto-merge',
  REVIEW_SKIPPED: 'orchestrator/review-skipped',
  MERGE_REBASE: 'orchestrator/merge-rebase',
  // A merge was requested (via /merge or auto-merge) while the full
  // verification suite is still running; the merge proceeds when it passes.
  PENDING_MERGE: 'orchestrator/merge-pending',
};

const PRIMARY_STATES = [
  LABELS.NEW,
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
  [LABELS.NEW]:                  { color: COLORS.INFO, description: 'PR awaiting triage' },
  [LABELS.READY_FOR_REVIEW]:     { color: COLORS.INFO, description: 'Ready for review, fast CI gate runs on push' },
  [LABELS.TESTED]:               { color: COLORS.SUCCESS, description: 'Fast CI gate passed for current HEAD' },
  [LABELS.FULL_VERIFIED]:        { color: COLORS.SUCCESS, description: 'Full verification suite passed for current HEAD' },
  [LABELS.REVIEW_APPROVED]:      { color: COLORS.SUCCESS, description: 'PR has an approved review' },
  [LABELS.READY_TO_MERGE]:       { color: COLORS.INFO, description: 'Approved and fast-gated; full suite is the merge gate' },
  [LABELS.WAITING_ON_AUTHOR]:    { color: COLORS.ATTENTION_STRONG, description: 'Blocked on contributor action' },
  [LABELS.WAITING_ON_MAINTAINER]:{ color: COLORS.ATTENTION, description: 'Blocked on maintainer action' },
  [LABELS.STALE]:                { color: COLORS.INACTIVE, description: 'No activity for 4+ days (waiting on author) or 7+ days' },
  [LABELS.DISABLED]:             { color: COLORS.INACTIVE, description: 'PR excluded from lifecycle orchestrator' },
  [LABELS.AUTO_MERGE]:           { color: COLORS.INFO, description: 'Auto-merge enabled' },
  [LABELS.REVIEW_SKIPPED]:       { color: COLORS.INFO, description: 'Review requirement skipped by maintainer' },
  [LABELS.MERGE_REBASE]:         { color: COLORS.INFO, description: 'Branch auto-updated for merge, full suite re-running' },
  [LABELS.PENDING_MERGE]:        { color: COLORS.INFO, description: 'Merge queued, waiting for full verification' },
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

    mergePr: async (prNumber, method, commitTitle) => {
      await github.rest.pulls.merge({
        owner, repo, pull_number: prNumber,
        merge_method: method,
        commit_title: commitTitle,
      });
    },

    deleteBranch: async (branch) => {
      try {
        await github.rest.git.deleteRef({
          owner, repo, ref: `heads/${branch}`,
        });
      } catch (e) {
        if (e.status !== 422) throw e;
      }
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

    cancelWorkflowRun: async (runId) => {
      try {
        await github.rest.actions.cancelWorkflowRun({ owner, repo, run_id: runId });
      } catch (e) {
        if (e.status !== 409) throw e;
      }
    },

    reRunWorkflow: async (runId) => {
      try {
        await github.rest.actions.reRunWorkflow({ owner, repo, run_id: runId });
      } catch (e) {
        if (e.status !== 409) throw e;
      }
    },

    getWorkflowRun: async (runId) => {
      const { data } = await github.rest.actions.getWorkflowRun({
        owner, repo, run_id: runId,
      });
      return data;
    },

    approveWorkflowRun: async (runId) => {
      await github.request('POST /repos/{owner}/{repo}/actions/runs/{run_id}/approve', {
        owner, repo, run_id: runId,
      });
    },

    dispatchWorkflow: async (workflow, ref, inputs = {}) => {
      await github.rest.actions.createWorkflowDispatch({
        owner, repo, workflow_id: workflow, ref, inputs,
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

// Two-tier CI routing: the fast gate (ci.yaml's Quick Verify job, workflow
// name "CI") covers PR iteration; the full suite (ci.yaml's full tier plus the
// "Integration Tests", "Extra Tests" and "Verify" workflows) is the pre-merge
// gate and only meaningful once the PR is lifecycle/ready-to-merge. Retrigger
// and approval operations target the workflow files that matter for the PR's
// current state.
// The full suite is split across these top-level workflows; all of them must
// be green for a PR's head SHA before lifecycle/full-verified is applied.
// "CI" is dual-mode: it is also the fast gate during review.
const FAST_GATE_WORKFLOW = 'CI';
const FULL_SUITE_WORKFLOWS = ['CI', 'Integration Tests', 'Extra Tests', 'Verify'];
const FULL_SUITE_WORKFLOW_FILES = ['ci.yaml', 'integration-tests.yaml', 'extras.yaml', 'verify.yaml'];

// Aggregates the latest run of each full-suite workflow for a commit.
// Returns:
//   { status: 'pending' }  — some workflow has no run yet or is still running
//   { status: 'success' }  — every workflow's latest run succeeded
//   { status: 'failure' }  — at least one failed/was cancelled (fail fast)
async function getFullSuiteResult(github, owner, repo, headSha, eventName, core) {
  const { data } = await github.rest.actions.listWorkflowRunsForRepo({
    owner, repo, head_sha: headSha, event: eventName, per_page: 100,
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
      return { status: 'failure', failedRun: run };
    }
  }
  return { status: 'success' };
}

async function retriggerVerify(api, pr, core, { waitForRun = false } = {}) {
  // The workflow that matters depends on lifecycle state: the fast gate
  // (ci.yaml) during iteration, all four full-suite workflows at
  // ready-to-merge (verify.yaml was split into per-phase workflows).
  if (getLifecycleState(pr) === LABELS.READY_TO_MERGE) {
    let found = false;
    for (const workflow of FULL_SUITE_WORKFLOW_FILES) {
      found = (await retriggerWorkflowRun(api, pr, core, workflow, { waitForRun })) || found;
    }
    if (!found) {
      await triggerViaBranchUpdate(api, pr, core, 'the full suite');
    }
    return;
  }
  const found = await retriggerWorkflowRun(api, pr, core, 'ci.yaml', { waitForRun });
  if (!found) {
    await triggerViaBranchUpdate(api, pr, core, 'ci.yaml');
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
async function retriggerWorkflowRun(api, pr, core, workflow, { waitForRun = false } = {}) {
  let run = null;

  if (waitForRun) {
    for (let attempt = 0; attempt < 5; attempt++) {
      await new Promise(r => setTimeout(r, 3000));
      run = await api.findLatestVerifyRun(pr.head.sha, workflow);
      if (run) break;
    }
  } else {
    run = await api.findLatestVerifyRun(pr.head.sha, workflow);
  }

  if (!run) {
    return false;
  }

  // Fork PRs need workflow approval before they can run. Approve instead
  // of re-running — the Decide step fetches current labels from the API,
  // so the approved run will see the up-to-date lifecycle state.
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

  // Only re-run workflows that actually need it; a green sibling is left alone.
  if (run.conclusion === 'success') {
    core.info(`PR #${pr.number} ${workflow} run ${run.id} already green, not re-triggering`);
    return true;
  }

  if (run.status === 'in_progress' || run.status === 'queued') {
    core.info(`PR #${pr.number} cancelling in-progress ${workflow} run ${run.id}`);
    await api.cancelWorkflowRun(run.id);

    // Wait for the run to fully complete — gate jobs with `if: always()`
    // keep the run alive after cancellation.
    for (let attempt = 0; attempt < 20; attempt++) {
      await new Promise(r => setTimeout(r, 5000));
      const fresh = await api.getWorkflowRun(run.id);
      if (fresh.status === 'completed') break;
    }
  }

  // Allow label changes to propagate through GitHub's eventually-consistent API
  // before re-triggering, so the scope job sees the current labels.
  await new Promise(r => setTimeout(r, 5000));

  try {
    await api.reRunWorkflow(run.id);
    core.info(`PR #${pr.number} re-triggered ${workflow} run ${run.id}`);
  } catch (e) {
    core.warning(`PR #${pr.number} failed to re-trigger ${workflow} run ${run.id}: ${e.message}`);
  }
  return true;
}

// Approves all Verify workflow runs awaiting approval for a PR's head SHA.
// Called after lifecycle transitions that should enable CI (e.g. /accept)
// to catch label-triggered runs that race with retriggerVerify.
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

// Approves pending fork-PR runs for ALL tiers: the fast gate plus every
// full-suite workflow (ci.yaml doubles as both). Approving a run the current
// state will skip is harmless; missing one strands a fork PR on
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

async function performMerge(api, config, pr, core, { allowBranchUpdate = true } = {}) {
  const freshPr = await api.getPr(pr.number);
  if (!hasLabel(freshPr, LABELS.TESTED) || !hasLabel(freshPr, LABELS.READY_TO_MERGE)) {
    core.warning(`PR #${pr.number} merge aborted: state changed since merge was initiated`);
    return false;
  }

  // Two-tier CI: lifecycle/tested comes from the fast gate. The merge itself
  // additionally requires the full suite (lifecycle/full-verified) for this
  // HEAD. If it is still running, queue the merge — handleTestResult
  // completes it when the Verify workflow finishes green.
  if (!hasLabel(freshPr, LABELS.FULL_VERIFIED)) {
    if (!hasLabel(freshPr, LABELS.PENDING_MERGE)) {
      await api.addLabel(pr.number, LABELS.PENDING_MERGE);
      await api.postComment(pr.number,
        `Merge queued: waiting for the full verification suite to pass for ` +
        `commit ${freshPr.head.sha.substring(0, 7)}. The merge will proceed automatically.`
      );
    }
    core.info(`PR #${pr.number} merge deferred until full verification passes`);
    return false;
  }

  const strategy = config.merge?.strategy || 'rebase';
  try {
    await api.mergePr(pr.number, strategy, freshPr.title);
    if (config.merge?.delete_branch) {
      await api.deleteBranch(freshPr.head.ref);
    }
    core.info(`PR #${pr.number} merged using ${strategy}`);
    return true;
  } catch (e) {
    // Branch is behind but can be cleanly updated — rebase and retry.
    // Skip for permission errors (403 / "Resource not accessible") which
    // indicate a different problem (e.g. workflow file modifications).
    const isPermissionError = e.status === 403 || e.message?.includes('Resource not accessible');
    if (allowBranchUpdate && !isPermissionError) {
      const currentPr = await api.getPr(pr.number);
      if (currentPr.rebaseable) {
        try {
          await api.addLabel(pr.number, LABELS.MERGE_REBASE);
          await api.updateBranch(pr.number, currentPr.head.sha);
          await api.postComment(pr.number,
            `Merge could not proceed because the branch is behind \`${currentPr.base.ref}\`. ` +
            `The branch has been updated automatically. The full verification suite runs on ` +
            `the updated branch and the merge will proceed once it passes.`
          );
          core.info(`PR #${pr.number} branch updated for merge-rebase`);
          return false;
        } catch (updateErr) {
          await api.removeLabel(pr.number, LABELS.MERGE_REBASE);
          core.warning(`PR #${pr.number} branch update failed: ${updateErr.message}`);
        }
      }
    }

    const workflowHint = e.message?.includes('Resource not accessible')
      ? ' This may be because the PR modifies workflow files, which requires manual merge via the GitHub UI (the `workflow` token scope is not available to GitHub Actions).'
      : '';
    const conflictHint = freshPr.rebaseable === false
      ? ' The branch has conflicts with the base branch that need manual resolution.'
      : '';
    await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
    await api.removeLabel(pr.number, LABELS.TESTED);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.postComment(pr.number,
      `Merge failed: ${e.message}\n\n` +
      `Reverted to \`lifecycle/ready-for-review\`.${workflowHint}${conflictHint}` +
      (workflowHint || conflictHint ? '' : ` The branch may need to be rebased. Use \`/auto-merge\` to merge automatically once approved and tested.`)
    );
    core.error(`PR #${pr.number} merge failed: ${e.message}`);
    return false;
  }
}

async function checkAndTransitionToReady(api, pr, core, reviews) {
  if (!reviews) reviews = await api.getReviews(pr.number);
  const approved = isApproved(reviews);
  const reviewSkipped = hasLabel(pr, LABELS.REVIEW_SKIPPED);
  const tested = hasLabel(pr, LABELS.TESTED);
  const state = getLifecycleState(pr);

  if ((approved || reviewSkipped) && tested && state === LABELS.READY_FOR_REVIEW) {
    await api.setLifecycleState(pr, LABELS.READY_TO_MERGE);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);

    // Two-tier CI: the full suite is the pre-merge gate. The
    // label was applied by the orchestrator, which does NOT trigger a
    // labeled-event workflow run — kick the suite off explicitly. The PR
    // object held by callers is stale, so refetch for correct routing.
    const transitionedPr = await api.getPr(pr.number);
    await retriggerVerify(api, transitionedPr, core);

    if (hasLabel(pr, LABELS.AUTO_MERGE)) {
      core.info(`PR #${pr.number} auto-merge enabled, will merge`);
      return 'auto-merge';
    }

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
      `This PR is approved and has passed the fast CI gate. The full verification suite is now ` +
      `running as the final merge gate.${mentionSuffix} A maintainer can merge it with \`/merge\`, ` +
      `or enable auto-merge with \`/auto-merge\` — the merge completes once full verification passes.`
    );
    core.info(`PR #${pr.number} is ready to merge`);
    return 'ready-to-merge';
  }
  return null;
}

// ---------------------------------------------------------------------------
// Reconciler
// ---------------------------------------------------------------------------

// Cleans up PRs still carrying labels from before the WIP/smoke-test removal,
// so they don't get stuck with a stale lifecycle/wip label nobody recognizes.
const LEGACY_WIP_LABEL = 'lifecycle/wip';
const LEGACY_SMOKE_TESTED_LABEL = 'lifecycle/smoke-tested';
const LEGACY_TESTS_DISABLED_LABEL = 'orchestrator/tests-disabled';

async function migrateLegacyLabels(api, pr, core) {
  const labels = getLabelNames(pr);
  const hasLegacyWip = labels.includes(LEGACY_WIP_LABEL);
  if (labels.includes(LEGACY_SMOKE_TESTED_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_SMOKE_TESTED_LABEL);
  }
  if (labels.includes(LEGACY_TESTS_DISABLED_LABEL)) {
    await api.removeLabel(pr.number, LEGACY_TESTS_DISABLED_LABEL);
  }
  if (!hasLegacyWip) return false;

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
    `migrated to \`lifecycle/ready-for-review\` and the fast CI gate will run.`
  );
  await retriggerVerify(api, pr, core, { waitForRun: true });
  core.warning(`PR #${pr.number} migrated from legacy lifecycle/wip to ${LABELS.READY_FOR_REVIEW}`);
  return true;
}

async function reconcile(github, api, pr, core) {
  const config = loadConfig();

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

  // 1. No lifecycle label at all → initialize as new PR
  if (!state) {
    if (hasLabel(pr, LABELS.DISABLED)) return;

    if (isAutoAccepted(config, pr.user.login)) {
      await api.addLabel(pr.number, LABELS.READY_FOR_REVIEW);
      await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      await retriggerVerify(api, pr, core);
      core.warning(`PR #${pr.number} had no lifecycle label — initialized as ${LABELS.READY_FOR_REVIEW} (auto-accepted)`);
    } else {
      await api.addLabel(pr.number, LABELS.NEW);
      await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      core.warning(`PR #${pr.number} had no lifecycle label — initialized as lifecycle/new`);
    }

    await api.postComment(pr.number,
      `**Warning:** This PR was missing a lifecycle label, which indicates the ` +
      `PR lifecycle orchestrator may have failed during initial processing. ` +
      `The label has been restored automatically. If this PR was already accepted, ` +
      `a maintainer may need to re-run the appropriate command (e.g. \`/accept\`).`
    );
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
      await api.removeLabel(pr.number, LABELS.REVIEW_APPROVED);
      core.info(`PR #${pr.number} reconciler fixed waiting-on labels (changes requested)`);
    } else if (!hasChangesRequested && hasLabel(pr, LABELS.WAITING_ON_AUTHOR) && hasLabel(pr, LABELS.TESTED)) {
      // Only remove waiting-on-author if tests passed — otherwise the label
      // may have been set by a test failure, not a review.
      await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
      await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      core.info(`PR #${pr.number} reconciler fixed waiting-on labels (changes addressed)`);
    }

    if (approved) {
      await api.addLabel(pr.number, LABELS.REVIEW_APPROVED);
    }

    const result = await checkAndTransitionToReady(api, pr, core, reviews);
    if (result === 'auto-merge') {
      await performMerge(api, config, pr, core);
    } else if (result === 'ready-to-merge') {
      core.info(`PR #${pr.number} reconciler transitioned to ready-to-merge`);
    }
  }

  // 3. Ready-to-merge: verify the review is still valid (handles dismissals
  //    and changes-requested that arrived after the transition).
  if (state === LABELS.READY_TO_MERGE) {
    const reviews = await api.getReviews(pr.number);
    const approved = isApproved(reviews);
    const reviewSkipped = hasLabel(pr, LABELS.REVIEW_SKIPPED);

    if (!approved && !reviewSkipped) {
      await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
      await api.removeLabel(pr.number, LABELS.REVIEW_APPROVED);

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

// Puts a PR at the top of the lifecycle, fresh or after leaving draft.
// Drafts never reach here — they're ignored until marked ready for review.
async function initNewPr(github, owner, repo, api, config, pr, core) {
  if (!isAutoAccepted(config, pr.user.login)) {
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

  if (isAutoAccepted(config, pr.user.login)) {
    await api.addLabel(pr.number, LABELS.READY_FOR_REVIEW);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    // Trusted authors don't wait for review before the full suite runs:
    // review-skipped makes the PR eligible for ready-to-merge as soon as the
    // fast gate is green, and the full suite (still the merge gate) starts
    // immediately instead of after a maintainer review.
    await api.addLabel(pr.number, LABELS.REVIEW_SKIPPED);
    const maintainerHint = isMaintainer(config, pr.user.login)
      ? `\n\nReview is skipped; a maintainer can still use \`/merge\` to merge early ` +
        `(it will wait for the full suite) or \`/auto-merge\` to merge automatically.`
      : '';
    const forkHint = pr.head.repo?.full_name !== `${owner}/${repo}`
      ? `\n\n**Note (fork PR):** Review label updates may not apply automatically. ` +
        `A maintainer can use \`/retry\` after reviewing to update the labels.`
      : '';
    await api.postComment(pr.number,
      `PR auto-accepted (trusted author). The full verification suite starts immediately; ` +
      `review is not required before CI.` +
      maintainerHint + forkHint
    );
    core.info(`PR #${pr.number} auto-accepted for ${pr.user.login}, state=${LABELS.READY_FOR_REVIEW}, review skipped`);
    await retriggerVerify(api, pr, core, { waitForRun: true });
    return;
  }

  await api.addLabel(pr.number, LABELS.NEW);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  let message = config.welcome_message.replace(/\{author\}/g, pr.user.login);
  if (pr.head.repo?.full_name !== `${owner}/${repo}`) {
    message += `\n**Note (fork PR):** Review label updates may not apply automatically. ` +
      `A maintainer can use \`/retry\` after reviewing to update the labels.`;
  }
  await api.postComment(pr.number, message);
  core.info(`PR #${pr.number} opened, set to lifecycle/new`);
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

  // Orchestrator-initiated branch update for merge — preserve state.
  // Only honour for bot-triggered syncs; if a human pushes while the
  // label is set, abort the fast-merge flow and proceed normally.
  if (hasLabel(pr, LABELS.MERGE_REBASE)) {
    if (context.payload.sender?.login === BOT_LOGIN) {
      core.info(`PR #${pr.number} orchestrator-initiated branch update for merge, preserving state`);
      if (hasLabel(pr, LABELS.STALE)) {
        await api.removeLabel(pr.number, LABELS.STALE);
      }
      // lifecycle/full-verified is SHA-scoped in meaning: the new HEAD has
      // not been verified yet. The merge-rebase run re-adds it on success.
      if (hasLabel(pr, LABELS.FULL_VERIFIED)) {
        await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
      }
      return;
    }
    await api.removeLabel(pr.number, LABELS.MERGE_REBASE);
    core.info(`PR #${pr.number} human push during merge-rebase, aborting fast-merge`);
  }

  const rerunHints = [];

  if (hasLabel(pr, LABELS.TESTED)) {
    await api.removeLabel(pr.number, LABELS.TESTED);
    core.info(`PR #${pr.number} new push, removed lifecycle/tested`);
  }
  if (hasLabel(pr, LABELS.FULL_VERIFIED)) {
    await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
    core.info(`PR #${pr.number} new push, removed lifecycle/full-verified`);
  }
  if (hasLabel(pr, LABELS.PENDING_MERGE)) {
    await api.removeLabel(pr.number, LABELS.PENDING_MERGE);
    core.info(`PR #${pr.number} new push, removed orchestrator/merge-pending`);
  }
  // review-approved is not removed here — it is only removed when GitHub
  // dismisses the review (pull_request_review dismissed event), keeping
  // parity with GitHub's branch protection "dismiss stale reviews" setting.
  if (hasLabel(pr, LABELS.AUTO_MERGE)) {
    await api.removeLabel(pr.number, LABELS.AUTO_MERGE);
    rerunHints.push('Auto-merge has been disabled — use `/auto-merge` to re-enable after tests pass.');
    core.info(`PR #${pr.number} new push, removed orchestrator/auto-merge`);
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

  if (rerunHints.length > 0) {
    await api.postComment(pr.number,
      `New commits pushed. The test suite will re-run.\n\n` +
      rerunHints.map(h => `- ${h}`).join('\n')
    );
  }

  const freshPr = await api.getPr(pr.number);
  await reconcile(github, api, freshPr, core);

  // For fork PRs in a testable state, the synchronize event creates a new
  // run that needs approval. Wait for it to appear, then approve.
  const currentState = getLifecycleState(freshPr);
  if (currentState === LABELS.READY_FOR_REVIEW || currentState === LABELS.READY_TO_MERGE) {
    await new Promise(r => setTimeout(r, 5000));
    const approved = await approveAllPendingCiRuns(api, freshPr, core);
    if (approved > 0) {
      core.info(`PR #${pr.number} approved ${approved} pending CI run(s) after push`);
    }
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
    `Mark the PR as ready for review to re-enter the lifecycle at \`lifecycle/new\`. ` +
    `Note that a maintainer will need to \`/accept\` it again.`
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

  core.info(`PR #${pr.number} marked ready for review (was draft), entering lifecycle/new`);
  await initNewPr(github, owner, repo, api, config, pr, core);
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
    'accept': () => cmdAccept(api, config, core, pr, actor, maintainer, comment.id),
    'reject': () => cmdReject(api, config, core, pr, actor, maintainer, parsed.args, comment.id),
    'merge': () => cmdMerge(api, config, core, pr, actor, maintainer, comment.id),
    'auto-merge': () => cmdAutoMerge(api, config, core, pr, actor, maintainer, comment.id),
    'skip-review': () => cmdSkipReview(api, config, core, pr, actor, maintainer, comment.id),
    'unstale': () => cmdUnstale(api, config, core, pr, actor, isAuthor, maintainer, comment.id),
    'retry': () => cmdRetry(github, api, core, pr, actor, isAuthor, maintainer, comment.id),
  };

  const handler = handlers[parsed.command];
  if (handler) {
    await handler();
  }
}

// ---------------------------------------------------------------------------
// Command Handlers
// ---------------------------------------------------------------------------

async function cmdAccept(api, config, core, pr, actor, maintainer, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can accept PRs.`);
    return;
  }

  const state = getLifecycleState(pr);
  if (state !== LABELS.NEW) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot accept: PR is not in \`lifecycle/new\` state (current: \`${state || 'none'}\`).`
    );
    return;
  }

  await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.addReaction(commentId, '+1');
  await api.postComment(pr.number,
    `PR accepted by @${actor}. @${pr.user.login}, the full test suite will run now.\n\n` +
    `A maintainer can use \`/auto-merge\` to merge automatically once approved and tested.`
  );
  core.info(`PR #${pr.number} accepted by ${actor}`);
  await retriggerVerify(api, pr, core);

  // The label change above may trigger a new run that also needs
  // approval (fork PRs). Wait for GitHub to create it, then approve.
  await new Promise(r => setTimeout(r, 5000));
  await approveAllPendingCiRuns(api, pr, core);
}

async function cmdReject(api, config, core, pr, actor, maintainer, reason, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can reject PRs.`);
    return;
  }

  const state = getLifecycleState(pr);
  if (state !== LABELS.NEW) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot reject: PR is not in \`lifecycle/new\` state (current: \`${state || 'none'}\`).`
    );
    return;
  }

  const reasonText = reason ? `\n\nReason: ${reason}` : '';
  await api.postComment(pr.number,
    `PR rejected by @${actor}.${reasonText}\n\n` +
    `@${pr.user.login}, please address the feedback and reopen if appropriate.`
  );
  await api.setLifecycleState(pr, null);
  await api.closePr(pr.number);
  await api.addReaction(commentId, '+1');
  core.info(`PR #${pr.number} rejected by ${actor}`);
}

async function cmdMerge(api, config, core, pr, actor, maintainer, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can merge PRs.`);
    return;
  }

  const state = getLifecycleState(pr);
  if (state !== LABELS.READY_TO_MERGE) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot merge: PR is not in \`lifecycle/ready-to-merge\` state ` +
      `(current: \`${state || 'none'}\`). The PR must be both approved and tested.`
    );
    return;
  }

  const merged = await performMerge(api, config, pr, core);
  await api.addReaction(commentId, merged ? '+1' : '-1');
}

async function cmdAutoMerge(api, config, core, pr, actor, maintainer, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can enable auto-merge.`);
    return;
  }

  const state = getLifecycleState(pr);
  if (!state || state === LABELS.NEW) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot enable auto-merge: PR must be accepted first.`
    );
    return;
  }

  if (hasLabel(pr, LABELS.AUTO_MERGE)) {
    await api.removeLabel(pr.number, LABELS.AUTO_MERGE);
    await api.addReaction(commentId, '+1');
    await api.postComment(pr.number, `Auto-merge disabled by @${actor}.`);
    core.info(`PR #${pr.number} auto-merge disabled by ${actor}`);
    return;
  }

  await api.addLabel(pr.number, LABELS.AUTO_MERGE);
  await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.addReaction(commentId, '+1');

  const approved = isApproved(await api.getReviews(pr.number));
  const reviewSkipped = hasLabel(pr, LABELS.REVIEW_SKIPPED);
  const needsReview = !approved && !reviewSkipped;

  await api.postComment(pr.number,
    `Auto-merge enabled by @${actor}. This PR will be merged automatically ` +
    `when it reaches \`lifecycle/ready-to-merge\` state. Use \`/auto-merge\` again to disable.` +
    (needsReview ? `\n\n**Note:** A review or \`/skip-review\` is still required before auto-merge can proceed.` : '')
  );

  if (state === LABELS.READY_TO_MERGE) {
    await performMerge(api, config, pr, core);
  }

  core.info(`PR #${pr.number} auto-merge enabled by ${actor}`);
}

async function cmdSkipReview(api, config, core, pr, actor, maintainer, commentId) {
  if (!maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number, `@${actor} Only maintainers can skip the review requirement.`);
    return;
  }

  const state = getLifecycleState(pr);
  if (state !== LABELS.READY_FOR_REVIEW) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot skip review: PR must be in \`lifecycle/ready-for-review\` state ` +
      `(current: \`${state || 'none'}\`).`
    );
    return;
  }

  await api.addLabel(pr.number, LABELS.REVIEW_SKIPPED);
  await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.addReaction(commentId, '+1');

  const freshPr = await api.getPr(pr.number);
  if (hasLabel(freshPr, LABELS.TESTED)) {
    const result = await checkAndTransitionToReady(api, freshPr, core);
    if (result === 'auto-merge') {
      await performMerge(api, config, freshPr, core);
      await api.postComment(pr.number,
        `Review requirement skipped by @${actor}. PR was tested and has been auto-merged.`
      );
    } else if (result === 'ready-to-merge') {
      // checkAndTransitionToReady already posted its own comment
    }
  } else {
    await api.postComment(pr.number,
      `Review requirement skipped by @${actor}. The PR will move to \`lifecycle/ready-to-merge\` ` +
      `once tests pass.`
    );
  }
  core.info(`PR #${pr.number} review skipped by ${actor}`);
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

async function cmdRetry(github, api, core, pr, actor, isAuthor, maintainer, commentId) {
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

  // The workflows that matter depend on lifecycle state: the fast gate
  // (ci.yaml) during iteration, all four full-suite workflows at
  // ready-to-merge (verify.yaml was split into per-phase workflows).
  const atMergeGate = getLifecycleState(freshPr) === LABELS.READY_TO_MERGE;
  const workflowFiles = atMergeGate ? FULL_SUITE_WORKFLOW_FILES : ['ci.yaml'];
  const workflowDesc = atMergeGate ? 'full-suite' : 'ci.yaml';
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
    await retriggerVerify(api, freshPr, core);
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
    await retriggerVerify(api, freshPr, core);
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

// True when the PR should run the full suite immediately (not waiting for a maintainer
// to apply ready-to-merge): auto-accepted authors get orchestrator/review-skipped at open,
// so their PRs are full-suite eligible from ready-for-review.
function isFullSuiteState(pr, state) {
  return state === LABELS.READY_TO_MERGE
      || (state === LABELS.READY_FOR_REVIEW && hasLabel(pr, LABELS.REVIEW_SKIPPED));
}

// Dispatches the downstream workflows that consume the shared Build artifact from the
// CI workflow, so they do not build again. Runs under pull_request_target with the
// orchestrator's token.
async function dispatchDownstreamWorkflows(github, api, owner, repo, workflowRun, pr, core) {
  const ref = pr.head.ref;
  for (const workflow of ['integration-tests.yaml', 'extras.yaml']) {
    try {
      await api.dispatchWorkflow(workflow, ref, { 'pr-number': String(pr.number) });
      core.info(`PR #${pr.number} dispatched ${workflow} for ${workflowRun.head_sha.substring(0, 7)}`);
    } catch (e) {
      core.warning(`PR #${pr.number} failed to dispatch ${workflow}: ${e.message}`);
    }
  }
}

async function handleTestResult({ github, context, core }) {
  const workflowRun = context.payload.workflow_run;
  if (workflowRun.event !== 'pull_request') {
    core.info('Workflow run is not from a PR event, skipping');
    return;
  }

  // Two-tier CI routing:
  //  - "CI" (ci.yaml): dual-mode. During ready-for-review only its Quick
  //    Verify job runs and its result drives lifecycle/tested. At
  //    ready-to-merge it also runs the full unit tier and counts as one of
  //    the full-suite workflows.
  //  - "Integration Tests" / "Extra Tests" / "Verify": the rest of the full
  //    suite (verify.yaml was split into per-phase top-level workflows).
  //    lifecycle/full-verified is only applied once ALL of them are green
  //    for the PR's head SHA; their runs completed while a PR is still in
  //    ready-for-review are no-ops (Decide skips every job) and must NOT
  //    mark the PR tested.
  const isFastGate = workflowRun.name === FAST_GATE_WORKFLOW;
  if (!FULL_SUITE_WORKFLOWS.includes(workflowRun.name)) {
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
      core.info(`No open PR found for branch ${workflowRun.head_branch} / SHA ${workflowRun.head_sha}, skipping`);
      return;
    }
    core.info(`Resolved ${prRefs.length} PR(s) from head branch lookup (re-run fallback)`);
  }

  for (const prRef of prRefs) {
    const pr = await api.getPr(prRef.number);

    if (hasLabel(pr, LABELS.DISABLED)) continue;

    const state = getLifecycleState(pr);

    // The CI workflow is dual-mode: it serves fast-gate results during
    // ready-for-review and is part of the full-suite set at ready-to-merge.
    const asFastGate = isFastGate && state === LABELS.READY_FOR_REVIEW;

    // Merge-rebase flow (full suite only): branch was auto-updated;
    // proceed to merge once the full suite passes.
    if (!asFastGate && state === LABELS.READY_TO_MERGE && hasLabel(pr, LABELS.MERGE_REBASE)) {
      if (pr.head.sha !== workflowRun.head_sha) {
        core.info(`PR #${pr.number} merge-rebase SHA mismatch, skipping`);
        continue;
      }
      const suite = await getFullSuiteResult(github, owner, repo, workflowRun.head_sha, workflowRun.event, core);
      if (suite.status === 'pending') {
        core.info(`PR #${pr.number} merge-rebase: waiting for other full-suite workflows`);
        continue;
      }
      if (suite.status === 'success') {
        await api.removeLabel(pr.number, LABELS.MERGE_REBASE);
        // The full suite just passed for this (rebased) HEAD — record it so
        // performMerge's full-verified gate accepts the merge.
        await api.addLabel(pr.number, LABELS.FULL_VERIFIED);
        const config = loadConfig();
        const merged = await performMerge(api, config, pr, core, { allowBranchUpdate: false });
        if (!merged) {
          core.warning(`PR #${pr.number} merge-rebase: merge failed after branch update`);
        }
      } else {
        await api.removeLabel(pr.number, LABELS.MERGE_REBASE);
        await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
        if (hasLabel(pr, LABELS.PENDING_MERGE)) {
          await api.removeLabel(pr.number, LABELS.PENDING_MERGE);
        }
        await api.removeLabel(pr.number, LABELS.TESTED);
        await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
        await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        await api.postComment(pr.number,
          `The verification workflow failed after the branch update. ` +
          `Reverting to \`lifecycle/ready-for-review\` for a full test run.`
        );
      }
      await postDecisionSummary(github, owner, repo, workflowRun, pr.number, core);
      await postFlakyTestsSummary(github, owner, repo, workflowRun, pr.number, core);
      continue;
    }

    if (asFastGate) {
      // A late CI completion can belong to a full-tier run that raced a
      // failure revert. If any full-suite workflow already failed for this
      // SHA, the suite owns the result — do not promote the PR.
      const suite = await getFullSuiteResult(github, owner, repo, workflowRun.head_sha, workflowRun.event, core);
      if (suite.status === 'failure') {
        core.info(`PR #${pr.number} full-suite failure recorded for this SHA, skipping fast-gate result`);
        continue;
      }
    } else {
      // The full suite is the pre-merge gate. While a PR is in
      // ready-for-review these runs are no-ops (Decide skips every job),
      // so they must not mark the PR tested.
      if (state !== LABELS.READY_TO_MERGE) {
        core.info(`PR #${pr.number} not in ready-to-merge state, skipping full-suite result`);
        continue;
      }
    }

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
        const result = await checkAndTransitionToReady(api, freshPr, core);
        if (result === 'auto-merge') {
          const config = loadConfig();
          await performMerge(api, config, freshPr, core);
        } else if (!result) {
          await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        }
      } else if (workflowRun.conclusion === 'failure') {
        await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
        await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        await api.postComment(pr.number,
          `The fast CI gate failed for commit ${workflowRun.head_sha.substring(0, 7)}. ` +
          `@${pr.user.login}, please check the ` +
          `[workflow run](${workflowRun.html_url}) and push a fix.`
        );
        core.info(`PR #${pr.number} fast gate failed`);
      } else if (workflowRun.conclusion === 'cancelled') {
        await api.postComment(pr.number,
          `The fast CI gate was cancelled for commit ${workflowRun.head_sha.substring(0, 7)}. ` +
          `See the [workflow run](${workflowRun.html_url}). Use \`/retry\` to re-run.`
        );
        core.info(`PR #${pr.number} fast gate cancelled`);
      }
    } else {
      // Full suite at ready-to-merge: the pre-merge gate. All full-suite
      // workflows (CI, Integration Tests, Extra Tests, Verify) must be green
      // for this SHA; a failure or cancellation in any of them fails the suite.
      const suite = await getFullSuiteResult(github, owner, repo, workflowRun.head_sha, workflowRun.event, core);
      if (suite.status === 'pending') {
        core.info(`PR #${pr.number} waiting for other full-suite workflows`);
        continue;
      }
      if (suite.status === 'success') {
        await api.addLabel(pr.number, LABELS.FULL_VERIFIED);
        core.info(`PR #${pr.number} full verification passed, added lifecycle/full-verified`);

        if (hasLabel(pr, LABELS.PENDING_MERGE)) {
          await api.removeLabel(pr.number, LABELS.PENDING_MERGE);
          const config = loadConfig();
          const freshPr = await api.getPr(pr.number);
          const merged = await performMerge(api, config, freshPr, core, { allowBranchUpdate: true });
          if (!merged) {
            core.warning(`PR #${pr.number} pending merge did not complete after full verification`);
          }
        }
      } else {
        const failed = suite.failedRun;
        const verb = failed.conclusion === 'cancelled' ? 'was cancelled' : 'failed';
        await api.removeLabel(pr.number, LABELS.FULL_VERIFIED);
        await api.removeLabel(pr.number, LABELS.TESTED);
        if (hasLabel(pr, LABELS.PENDING_MERGE)) {
          await api.removeLabel(pr.number, LABELS.PENDING_MERGE);
        }
        await api.setLifecycleState(pr, LABELS.READY_FOR_REVIEW);
        await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
        await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
        await api.postComment(pr.number,
          `The full verification suite ${verb} for commit ${workflowRun.head_sha.substring(0, 7)} ` +
          `(${failed.name}: ${failed.html_url}). ` +
          `Reverting to \`lifecycle/ready-for-review\`. @${pr.user.login}, please check the ` +
          `workflow run and push a fix.`
        );
        core.info(`PR #${pr.number} full verification ${verb}, reverted to ready-for-review`);
      }
    }

    // Post or update the decision summary comment
    await postDecisionSummary(github, owner, repo, workflowRun, pr.number, core);
    await postFlakyTestsSummary(github, owner, repo, workflowRun, pr.number, core);

    const reconPr = await api.getPr(pr.number);
    await reconcile(github, api, reconPr, core);

    // The shared Build job lives in the CI workflow; when it completes for a PR
    // whose full suite should run, dispatch the downstream workflows that consume
    // its artifact instead of letting them build again. Runs under
    // pull_request_target with the orchestrator's token (ci.yaml's own GITHUB_TOKEN
    // cannot dispatch workflows from a pull_request trigger).
    if (isFastGate && workflowRun.conclusion === 'success' && isFullSuiteState(pr, state)) {
      await dispatchDownstreamWorkflows(github, api, owner, repo, workflowRun, pr, core);
    }
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
      ['Build', 'run-build', 'Build /'],
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
  handleComment,
  handleLabelChange,
  handleTestResult,
  handleStale,
  handleReconcile,
  reconcile,
  LABELS,
  FULL_SUITE_WORKFLOWS,
};
