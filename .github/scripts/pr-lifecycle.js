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
  WIP: 'lifecycle/wip',
  READY_FOR_REVIEW: 'lifecycle/ready-for-review',
  TESTED: 'lifecycle/tested',
  READY_TO_MERGE: 'lifecycle/ready-to-merge',
  WAITING_ON_AUTHOR: 'lifecycle/waiting-on-author',
  WAITING_ON_MAINTAINER: 'lifecycle/waiting-on-maintainer',
  STALE: 'lifecycle/stale',
  SMOKE_TESTED: 'lifecycle/smoke-tested',
  REVIEW_APPROVED: 'lifecycle/review-approved',
  DISABLED: 'orchestrator/disabled',
  AUTO_MERGE: 'orchestrator/auto-merge',
  TESTS_DISABLED: 'orchestrator/tests-disabled',
  REVIEW_SKIPPED: 'orchestrator/review-skipped',
};

const PRIMARY_STATES = [
  LABELS.NEW,
  LABELS.WIP,
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
  ATTENTION: 'F5A623',
  INACTIVE: 'CCCCCC',
};

const LABEL_DEFS = {
  [LABELS.NEW]:                  { color: COLORS.INFO, description: 'PR awaiting triage' },
  [LABELS.WIP]:                  { color: COLORS.INFO, description: 'Accepted, author working' },
  [LABELS.READY_FOR_REVIEW]:     { color: COLORS.INFO, description: 'Ready for review, full tests running' },
  [LABELS.SMOKE_TESTED]:         { color: COLORS.SUCCESS_LIGHT, description: 'Smoke tests passed for current HEAD' },
  [LABELS.TESTED]:               { color: COLORS.SUCCESS, description: 'Full test suite passed for current HEAD' },
  [LABELS.REVIEW_APPROVED]:      { color: COLORS.SUCCESS, description: 'PR has an approved review' },
  [LABELS.READY_TO_MERGE]:       { color: COLORS.INFO, description: 'Approved and tested, ready to merge' },
  [LABELS.WAITING_ON_AUTHOR]:    { color: COLORS.ATTENTION, description: 'Blocked on contributor action' },
  [LABELS.WAITING_ON_MAINTAINER]:{ color: COLORS.ATTENTION, description: 'Blocked on maintainer action' },
  [LABELS.STALE]:                { color: COLORS.INACTIVE, description: 'No activity for 7+ days' },
  [LABELS.DISABLED]:             { color: COLORS.INACTIVE, description: 'PR excluded from lifecycle orchestrator' },
  [LABELS.AUTO_MERGE]:           { color: COLORS.INFO, description: 'Auto-merge enabled' },
  [LABELS.TESTS_DISABLED]:       { color: COLORS.INFO, description: 'Smoke tests disabled for this PR' },
  [LABELS.REVIEW_SKIPPED]:       { color: COLORS.INFO, description: 'Review requirement skipped by maintainer' },
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

    findLatestVerifyRun: async (headSha) => {
      const { data } = await github.rest.actions.listWorkflowRuns({
        owner, repo, workflow_id: 'verify.yaml',
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
  };
}

async function retriggerVerify(api, pr, core, { waitForRun = false } = {}) {
  let run = null;

  if (waitForRun) {
    for (let attempt = 0; attempt < 5; attempt++) {
      await new Promise(r => setTimeout(r, 3000));
      run = await api.findLatestVerifyRun(pr.head.sha);
      if (run) break;
    }
  } else {
    run = await api.findLatestVerifyRun(pr.head.sha);
  }

  if (!run) {
    core.warning(`PR #${pr.number} no Verify run found for ${pr.head.sha}, skipping re-trigger`);
    return;
  }

  if (run.status === 'in_progress' || run.status === 'queued') {
    core.info(`PR #${pr.number} cancelling in-progress Verify run ${run.id}`);
    await api.cancelWorkflowRun(run.id);
    await new Promise(r => setTimeout(r, 3000));
  }

  try {
    await api.reRunWorkflow(run.id);
    core.info(`PR #${pr.number} re-triggered Verify run ${run.id}`);
  } catch (e) {
    core.warning(`PR #${pr.number} failed to re-trigger Verify run ${run.id}: ${e.message}`);
  }
}

function isApproved(reviews) {
  const latestByReviewer = new Map();
  for (const review of reviews) {
    if (review.state === 'APPROVED' || review.state === 'CHANGES_REQUESTED') {
      const existing = latestByReviewer.get(review.user.login);
      if (!existing || new Date(review.submitted_at) > new Date(existing.submitted_at)) {
        latestByReviewer.set(review.user.login, review);
      }
    }
  }
  const latest = Array.from(latestByReviewer.values());
  const hasApproval = latest.some(r => r.state === 'APPROVED');
  const hasChangesRequested = latest.some(r => r.state === 'CHANGES_REQUESTED');
  return hasApproval && !hasChangesRequested;
}

async function performMerge(api, config, pr, core) {
  const freshPr = await api.getPr(pr.number);
  if (!hasLabel(freshPr, LABELS.TESTED) || !hasLabel(freshPr, LABELS.READY_TO_MERGE)) {
    core.warning(`PR #${pr.number} merge aborted: state changed since merge was initiated`);
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
    await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
    await api.removeLabel(pr.number, LABELS.TESTED);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.postComment(pr.number,
      `Merge failed: ${e.message}\n\n` +
      `Reverted to \`lifecycle/ready-for-review\`. The branch may need to be rebased. ` +
      `Use \`/auto-merge\` to merge automatically once approved and tested.`
    );
    core.error(`PR #${pr.number} merge failed: ${e.message}`);
    return false;
  }
}

async function checkAndTransitionToReady(api, pr, core) {
  const approved = isApproved(await api.getReviews(pr.number));
  const reviewSkipped = hasLabel(pr, LABELS.REVIEW_SKIPPED);
  const tested = hasLabel(pr, LABELS.TESTED);
  const state = getLifecycleState(pr);

  if ((approved || reviewSkipped) && tested && state === LABELS.READY_FOR_REVIEW) {
    await api.setLifecycleState(pr, LABELS.READY_TO_MERGE);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);

    if (hasLabel(pr, LABELS.AUTO_MERGE)) {
      core.info(`PR #${pr.number} auto-merge enabled, will merge`);
      return 'auto-merge';
    }
    await api.postComment(pr.number,
      `This PR is approved and tested. A maintainer can merge it with \`/merge\`, ` +
      `or enable auto-merge with \`/auto-merge\`.`
    );
    core.info(`PR #${pr.number} is ready to merge`);
    return 'ready-to-merge';
  }
  return null;
}

// ---------------------------------------------------------------------------
// Event Handlers
// ---------------------------------------------------------------------------

async function handlePrOpened({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const config = loadConfig();

  if (isAutoAccepted(config, pr.user.login)) {
    const initialState = pr.draft ? LABELS.WIP : LABELS.READY_FOR_REVIEW;
    await api.addLabel(pr.number, initialState);
    if (pr.draft) {
      await api.postComment(pr.number,
        `PR auto-accepted (trusted author). Smoke tests will run on each push.\n\n` +
        `When ready, use \`/ready\` or mark as non-draft to run the full test suite, ` +
        `or \`/skip-review\` to skip the review requirement for small changes (tests are still required).`
      );
    } else {
      await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      await api.postComment(pr.number,
        `PR auto-accepted (trusted author). Full test suite will run.\n\n` +
        `Use \`/skip-review\` to skip the review requirement for small changes, ` +
        `or \`/auto-merge\` to merge automatically once approved and tested.`
      );
    }
    core.info(`PR #${pr.number} auto-accepted for ${pr.user.login}, state=${initialState}`);
    await retriggerVerify(api, pr, core, { waitForRun: true });
    return;
  }

  await api.addLabel(pr.number, LABELS.NEW);
  const message = config.welcome_message.replace(/\{author\}/g, pr.user.login);
  await api.postComment(pr.number, message);
  core.info(`PR #${pr.number} opened, set to lifecycle/new`);
}

async function handlePrSynchronize({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  if (hasLabel(pr, LABELS.TESTED)) {
    await api.removeLabel(pr.number, LABELS.TESTED);
    core.info(`PR #${pr.number} new push, removed lifecycle/tested`);
  }
  if (hasLabel(pr, LABELS.SMOKE_TESTED)) {
    await api.removeLabel(pr.number, LABELS.SMOKE_TESTED);
    core.info(`PR #${pr.number} new push, removed lifecycle/smoke-tested`);
  }
  if (hasLabel(pr, LABELS.REVIEW_APPROVED)) {
    await api.removeLabel(pr.number, LABELS.REVIEW_APPROVED);
    core.info(`PR #${pr.number} new push, removed lifecycle/review-approved`);
  }
  if (hasLabel(pr, LABELS.REVIEW_SKIPPED)) {
    await api.removeLabel(pr.number, LABELS.REVIEW_SKIPPED);
    core.info(`PR #${pr.number} new push, removed orchestrator/review-skipped`);
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
    await api.postComment(pr.number,
      `New commits pushed. Reverting to \`lifecycle/ready-for-review\` — ` +
      `the full test suite will re-run. Use \`/auto-merge\` to merge automatically once approved and tested.`
    );
    core.info(`PR #${pr.number} reverted from ready-to-merge to ready-for-review`);
  }
}

async function handlePrReadyForReview({ github, context, core }) {
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);
  const state = getLifecycleState(pr);

  if (state !== LABELS.WIP) {
    core.info(`PR #${pr.number} draft->ready but not in WIP state (${state}), skipping`);
    return;
  }

  const freshPr = await api.getPr(pr.number);
  if (!freshPr.requested_reviewers?.length && !freshPr.requested_teams?.length) {
    await api.postComment(pr.number,
      `Cannot transition to ready-for-review: no reviewer is assigned. ` +
      `Please ask a maintainer to assign a reviewer first, then use \`/ready\`.`
    );
    return;
  }

  await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
  await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.postComment(pr.number,
    `PR is now ready for review. The full test suite will run.\n\n` +
    `A maintainer can use \`/auto-merge\` to merge automatically once approved and tested.`
  );
  core.info(`PR #${pr.number} transitioned to ready-for-review (draft->ready)`);
  await retriggerVerify(api, pr, core);
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
    'ready': () => cmdReady(api, config, core, pr, actor, isAuthor, maintainer, comment.id),
    'merge': () => cmdMerge(api, config, core, pr, actor, maintainer, comment.id),
    'auto-merge': () => cmdAutoMerge(api, config, core, pr, actor, maintainer, comment.id),
    'skip-review': () => cmdSkipReview(api, config, core, pr, actor, maintainer, comment.id),
    'disable-tests': () => cmdDisableTests(api, core, pr, actor, isAuthor, maintainer, comment.id),
    'enable-tests': () => cmdEnableTests(api, core, pr, actor, isAuthor, maintainer, comment.id),
    'unstale': () => cmdUnstale(api, config, core, pr, actor, isAuthor, maintainer, comment.id),
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

  await api.setLifecycleState(pr, LABELS.WIP);
  await api.addReaction(commentId, '+1');
  await api.postComment(pr.number,
    `PR accepted by @${actor}. @${pr.user.login}, you can now work on your changes.\n\n` +
    `Smoke tests will run on each push. When ready, use \`/ready\` to request a full review.`
  );
  core.info(`PR #${pr.number} accepted by ${actor}`);
  await retriggerVerify(api, pr, core);
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

async function cmdReady(api, config, core, pr, actor, isAuthor, maintainer, commentId) {
  if (!isAuthor && !maintainer) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number,
      `@${actor} Only the PR author or a maintainer can mark a PR as ready.`
    );
    return;
  }

  const state = getLifecycleState(pr);
  if (state !== LABELS.WIP) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot mark as ready: PR is not in \`lifecycle/wip\` state (current: \`${state || 'none'}\`).`
    );
    return;
  }

  const freshPr = await api.getPr(pr.number);
  if (!freshPr.requested_reviewers?.length && !freshPr.requested_teams?.length) {
    await api.addReaction(commentId, '-1');
    await api.postComment(pr.number,
      `@${actor} Cannot mark as ready: no reviewer is assigned. ` +
      `Please ask a maintainer to assign a reviewer first.`
    );
    return;
  }

  await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
  await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
  await api.removeLabel(pr.number, LABELS.TESTS_DISABLED);
  await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
  await api.addReaction(commentId, '+1');
  await api.postComment(pr.number,
    `PR marked as ready for review. The full test suite will run.\n\n` +
    `A maintainer can use \`/auto-merge\` to merge automatically once approved and tested.`
  );
  core.info(`PR #${pr.number} marked ready by ${actor}`);
  await retriggerVerify(api, pr, core);
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
  await api.addReaction(commentId, '+1');
  await api.postComment(pr.number,
    `Auto-merge enabled by @${actor}. This PR will be merged automatically ` +
    `when it reaches \`lifecycle/ready-to-merge\` state. Use \`/auto-merge\` again to disable.`
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
  if (state !== LABELS.READY_FOR_REVIEW && state !== LABELS.WIP) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Cannot skip review: PR must be in \`lifecycle/wip\` or \`lifecycle/ready-for-review\` state ` +
      `(current: \`${state || 'none'}\`).`
    );
    return;
  }

  if (state === LABELS.WIP) {
    const freshPr = await api.getPr(pr.number);
    await api.setLifecycleState(freshPr, LABELS.READY_FOR_REVIEW);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.addLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await retriggerVerify(api, freshPr, core);
  }

  await api.addLabel(pr.number, LABELS.REVIEW_SKIPPED);
  await api.addReaction(commentId, '+1');

  const freshPr = await api.getPr(pr.number);
  if (hasLabel(freshPr, LABELS.TESTED)) {
    const result = await checkAndTransitionToReady(api, freshPr, core);
    if (result === 'auto-merge') {
      await performMerge(api, config, freshPr, core);
    }
    await api.postComment(pr.number,
      `Review requirement skipped by @${actor}. PR is tested and ready to merge.`
    );
  } else {
    await api.postComment(pr.number,
      `Review requirement skipped by @${actor}. The PR will move to \`lifecycle/ready-to-merge\` ` +
      `once tests pass.`
    );
  }
  core.info(`PR #${pr.number} review skipped by ${actor}`);
}

async function cmdDisableTests(api, core, pr, actor, isAuthor, maintainer, commentId) {
  if (!isAuthor && !maintainer) {
    await api.addReaction(commentId, '-1');
    return;
  }
  const state = getLifecycleState(pr);
  if (state !== LABELS.WIP) {
    await api.addReaction(commentId, 'confused');
    await api.postComment(pr.number,
      `@${actor} Tests can only be disabled in \`lifecycle/wip\` state.`
    );
    return;
  }
  await api.addLabel(pr.number, LABELS.TESTS_DISABLED);
  await api.addReaction(commentId, '+1');
  core.info(`PR #${pr.number} smoke tests disabled by ${actor}`);
}

async function cmdEnableTests(api, core, pr, actor, isAuthor, maintainer, commentId) {
  if (!isAuthor && !maintainer) {
    await api.addReaction(commentId, '-1');
    return;
  }
  const state = getLifecycleState(pr);
  if (state !== LABELS.WIP) {
    await api.addReaction(commentId, 'confused');
    return;
  }
  await api.removeLabel(pr.number, LABELS.TESTS_DISABLED);
  await api.addReaction(commentId, '+1');
  core.info(`PR #${pr.number} smoke tests re-enabled by ${actor}`);
  await retriggerVerify(api, pr, core);
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

// ---------------------------------------------------------------------------
// Review Handler
// ---------------------------------------------------------------------------

async function handleReview({ github, context, core }) {
  const review = context.payload.review;
  const pr = context.payload.pull_request;
  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  const state = getLifecycleState(pr);
  if (state !== LABELS.READY_FOR_REVIEW && state !== LABELS.READY_TO_MERGE) {
    core.info(`PR #${pr.number} review submitted but not in reviewable state (${state}), skipping`);
    return;
  }

  if (review.state === 'changes_requested') {
    await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
    await api.removeLabel(pr.number, LABELS.REVIEW_APPROVED);
    core.info(`PR #${pr.number} changes requested by ${review.user.login}`);
    return;
  }

  if (review.state === 'approved') {
    await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
    await api.addLabel(pr.number, LABELS.REVIEW_APPROVED);
    const freshPr = await api.getPr(pr.number);
    const result = await checkAndTransitionToReady(api, freshPr, core);
    if (result === 'auto-merge') {
      const config = loadConfig();
      await performMerge(api, config, freshPr, core);
    }
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

  if (label.name === LABELS.DISABLED) {
    const config = loadConfig();
    if (isMaintainer(config, actor)) {
      core.info(`PR #${pr.number} orchestrator ${action === 'labeled' ? 'disabled' : 'enabled'} by maintainer ${actor}`);
      return;
    }
    // Non-maintainer: fall through to label protection below
  }

  if (!CONTROL_LABELS.includes(label.name)) return;

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
  if (workflowRun.event !== 'pull_request' || !workflowRun.pull_requests?.length) {
    core.info('Workflow run is not from a PR, skipping');
    return;
  }

  const { owner, repo } = context.repo;
  const api = createApi(github, owner, repo);

  for (const prRef of workflowRun.pull_requests) {
    const pr = await api.getPr(prRef.number);

    if (hasLabel(pr, LABELS.DISABLED)) continue;

    const state = getLifecycleState(pr);
    if (state !== LABELS.READY_FOR_REVIEW && state !== LABELS.WIP) {
      core.info(`PR #${pr.number} not in ready-for-review or wip state, skipping test result`);
      continue;
    }

    if (pr.head.sha !== workflowRun.head_sha) {
      core.info(`PR #${pr.number} head SHA mismatch (PR: ${pr.head.sha}, run: ${workflowRun.head_sha}), skipping`);
      continue;
    }

    if (state === LABELS.WIP) {
      if (workflowRun.conclusion === 'success') {
        await api.addLabel(pr.number, LABELS.SMOKE_TESTED);
        core.info(`PR #${pr.number} smoke tests passed, added lifecycle/smoke-tested`);
      } else if (workflowRun.conclusion === 'failure') {
        await api.removeLabel(pr.number, LABELS.SMOKE_TESTED);
        core.info(`PR #${pr.number} smoke tests failed`);
      }
      continue;
    }

    if (workflowRun.conclusion === 'success') {
      await api.addLabel(pr.number, LABELS.TESTED);
      await api.removeLabel(pr.number, LABELS.SMOKE_TESTED);
      await api.removeLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
      core.info(`PR #${pr.number} tests passed, added lifecycle/tested`);

      const freshPr = await api.getPr(pr.number);
      const result = await checkAndTransitionToReady(api, freshPr, core);
      if (result === 'auto-merge') {
        const config = loadConfig();
        await performMerge(api, config, freshPr, core);
      }
    } else if (workflowRun.conclusion === 'failure') {
      await api.addLabel(pr.number, LABELS.WAITING_ON_AUTHOR);
      await api.removeLabel(pr.number, LABELS.WAITING_ON_MAINTAINER);
      await api.postComment(pr.number,
        `The test suite failed for commit ${workflowRun.head_sha.substring(0, 7)}. ` +
        `@${pr.user.login}, please check the ` +
        `[workflow run](${workflowRun.html_url}) and push a fix.`
      );
      core.info(`PR #${pr.number} tests failed`);
    }
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
  const now = new Date();

  const prs = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', per_page: 100,
  });

  for (const pr of prs) {
    if (hasLabel(pr, LABELS.DISABLED)) continue;

    const state = getLifecycleState(pr);
    if (!state) continue;
    if (state === LABELS.READY_TO_MERGE) continue;

    const updatedAt = new Date(pr.updated_at);
    const daysSinceUpdate = (now - updatedAt) / (1000 * 60 * 60 * 24);

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

      if (hasActivity) {
        await api.removeLabel(pr.number, LABELS.STALE);
        core.info(`PR #${pr.number} stale removed (activity detected)`);
      } else if (daysSinceStale >= (daysUntilClose - daysUntilStale)) {
        const closeMessage = (config.stale?.close_message || 'Closing due to inactivity.')
          .replace(/\{author\}/g, pr.user.login);
        await api.postComment(pr.number, closeMessage);
        await api.closePr(pr.number);
        core.info(`PR #${pr.number} closed due to extended inactivity`);
      }
    } else if (daysSinceUpdate >= daysUntilStale) {
      await api.addLabel(pr.number, LABELS.STALE);
      const staleMessage = (config.stale?.stale_message || 'This PR is stale.')
        .replace(/\{author\}/g, pr.user.login);
      await api.postComment(pr.number, staleMessage);
      core.info(`PR #${pr.number} marked as stale`);
    }
  }
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = {
  handlePrOpened,
  handlePrSynchronize,
  handlePrReadyForReview,
  handleComment,
  handleReview,
  handleLabelChange,
  handleTestResult,
  handleStale,
  LABELS,
};
