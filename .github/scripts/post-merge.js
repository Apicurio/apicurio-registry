// Post-merge verification (post-merge.yml, #10380)
//
// Confirms a failed Verify run on a protected branch before anyone is told
// about it, and keeps one tracking issue open while the branch is broken:
//
//   failure, first attempt    re-run the failed jobs, say nothing; also
//                             after a pass or cancellation (a re-run by hand)
//   failure, after a failed   confirmed: open the tracking issue (important
//   attempt                   Slack), or note it on the open one
//   success                   close the tracking issue (general Slack); a
//                             re-run that passed with no issue open is a
//                             flake (general Slack)
//   cancelled, skipped, ...   ignored: a newer push superseded the run
//
// A retry runs in a concurrency group of its own commit (verify.yaml), so it
// can finish after the run of a newer commit. Every transition therefore
// compares commits and ignores a result older than the one it would replace,
// or one it can't order against it (see compare).
//
// Slack is sent by the workflow, from the outputs this sets: level, text and
// link. See "Post-merge verification" in .github/workflows/README.md.

const VERIFY_PATH = '.github/workflows/verify.yaml';
const VERIFY_WORKFLOW_FILE = 'verify.yaml';

// Branches whose push runs are confirmed and tracked.
const BRANCHES = ['main'];

const LABEL = 'ci/broken';
const LABEL_COLOR = 'b60205';
const LABEL_DESCRIPTION = 'Tracking issue: a branch is broken after merge (post-merge.yml)';
const MAINTAINERS_TEAM = '@Apicurio/maintainers';

// failure and timed_out are retried first; startup_failure means the workflow
// itself is broken and can't be re-run, so it is a confirmed break at once.
const RETRIED = new Set(['failure', 'timed_out']);
const BROKEN = new Set(['failure', 'timed_out', 'startup_failure']);

// Verify Result only aggregates the jobs that actually failed.
const AGGREGATE_JOBS = new Set(['Verify Result']);
const MAX_JOBS_NAMED = 5;

const STATE_RE = /<!-- post-merge-state (\{.*?\}) -->/;

function createApi(github, owner, repo) {
  return {
    // False when the run is already being re-run (409), e.g. by hand.
    reRunFailedJobs: async (runId) => {
      try {
        await github.rest.actions.reRunWorkflowFailedJobs({ owner, repo, run_id: runId });
        return true;
      } catch (e) {
        if (e.status !== 409) throw e;
        return false;
      }
    },

    attemptConclusion: async (runId, attempt) => {
      const { data } = await github.rest.actions.getWorkflowRunAttempt({
        owner, repo, run_id: runId, attempt_number: attempt,
      });
      return data.conclusion;
    },

    listAttemptJobs: async (runId, attempt) => github.paginate(
      github.rest.actions.listJobsForWorkflowRunAttempt,
      { owner, repo, run_id: runId, attempt_number: attempt, per_page: 100 },
    ),

    // compare/{base}...{head}: 'ahead' means head is newer than base. Null
    // when GitHub can't compare them (a commit it doesn't have). Null and
    // 'diverged' (only after a force push) don't order the commits, so the
    // handlers never close or advance an issue on them.
    compare: async (base, head) => {
      try {
        const { data } = await github.rest.repos.compareCommitsWithBasehead({
          owner, repo, basehead: `${base}...${head}`, per_page: 1,
        });
        return data.status;
      } catch (e) {
        if (e.status === 404 || e.status === 422) return null;
        throw e;
      }
    },

    // The commit of the most recent successful push run, by file rather than
    // name: a PR can add a workflow of its own called "Verify".
    newestGreenSha: async (branch) => {
      const { data } = await github.rest.actions.listWorkflowRuns({
        owner, repo, workflow_id: VERIFY_WORKFLOW_FILE,
        branch, event: 'push', status: 'success', per_page: 1,
      });
      return data.workflow_runs[0]?.head_sha || null;
    },

    listTrackingIssues: async () => github.paginate(github.rest.issues.listForRepo, {
      owner, repo, labels: LABEL, state: 'open', per_page: 100,
    }),

    createTrackingIssue: async (title, body) => {
      try {
        await github.rest.issues.getLabel({ owner, repo, name: LABEL });
      } catch (e) {
        if (e.status !== 404) throw e;
        await github.rest.issues.createLabel({
          owner, repo, name: LABEL, color: LABEL_COLOR, description: LABEL_DESCRIPTION,
        });
      }
      const { data } = await github.rest.issues.create({ owner, repo, title, body, labels: [LABEL] });
      return data;
    },

    updateIssueBody: async (number, body) => {
      await github.rest.issues.update({ owner, repo, issue_number: number, body });
    },

    comment: async (number, body) => {
      await github.rest.issues.createComment({ owner, repo, issue_number: number, body });
    },

    closeIssue: async (number) => {
      await github.rest.issues.update({
        owner, repo, issue_number: number, state: 'closed', state_reason: 'completed',
      });
    },
  };
}

// ---------------------------------------------------------------------------
// Tracking issue
// ---------------------------------------------------------------------------

function readState(body) {
  const match = STATE_RE.exec(body || '');
  if (!match) return null;
  try {
    return JSON.parse(match[1]);
  } catch {
    return null;
  }
}

function writeState(body, state) {
  return body.replace(STATE_RE, stateComment(state));
}

function stateComment(state) {
  return `<!-- post-merge-state ${JSON.stringify(state)} -->`;
}

async function findTrackingIssue(api, branch) {
  for (const issue of await api.listTrackingIssues()) {
    const state = readState(issue.body);
    if (state && state.branch === branch) return { issue, state };
  }
  return null;
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

function short(sha) {
  return sha.slice(0, 7);
}

function commitLink(ctx, sha) {
  return `[\`${short(sha)}\`](${ctx.serverUrl}/${ctx.owner}/${ctx.repo}/commit/${sha})`;
}

function duration(fromIso, now) {
  const minutes = Math.max(0, Math.round((now - new Date(fromIso)) / 60000));
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}m`;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

function jobList(names, code) {
  if (names.length === 0) return 'no job named';
  const wrap = code ? n => `\`${n}\`` : n => n;
  const shown = names.slice(0, MAX_JOBS_NAMED).map(wrap).join(', ');
  const rest = names.length - MAX_JOBS_NAMED;
  return rest > 0 ? `${shown} and ${rest} more` : shown;
}

// A startup failure is confirmed without a re-run (see BROKEN) and ran no jobs.
function isStartupFailure(run) {
  return run.conclusion === 'startup_failure';
}

// "Verify failed at X, and again when re-run", or that it could not start.
function failureText(run, commit) {
  return isStartupFailure(run)
    ? `Verify could not start at ${commit} (startup failure)`
    : `Verify failed at ${commit}, and again when re-run`;
}

// Whether the attempt before this one failed. Checked on the run, not its
// jobs: a job that times out ends as cancelled, so the only failed job can be
// Verify Result, which failedJobs leaves out.
async function previousAttemptFailed(api, run) {
  return run.run_attempt > 1
    && RETRIED.has(await api.attemptConclusion(run.id, run.run_attempt - 1));
}

async function failedJobs(api, run, attempt) {
  const jobs = await api.listAttemptJobs(run.id, attempt);
  return jobs
    .filter(j => BROKEN.has(j.conclusion) && !AGGREGATE_JOBS.has(j.name))
    .map(j => j.name);
}

function issueBody(ctx, run, jobs, greenSha, state) {
  const changes = greenSha
    ? `[changes since the last green commit, ${short(greenSha)}](${ctx.serverUrl}/${ctx.owner}/${ctx.repo}/compare/${greenSha}...${run.head_sha})`
    : 'no earlier green commit found';
  return [
    `\`${run.head_branch}\` is broken: ${failureText(run, commitLink(ctx, run.head_sha))}.`,
    '',
    '| | |',
    '|---|---|',
    `| Failing run | [Verify #${run.run_number}](${run.html_url}) |`,
    `| Failed jobs | ${isStartupFailure(run) ? 'none, the run did not start' : jobList(jobs, true)} |`,
    `| Suspects | ${changes} |`,
    `| Broken since | ${state.since.replace('T', ' ').replace(/:\d\d(\.\d+)?Z$/, ' UTC')} |`,
    '',
    `${MAINTAINERS_TEAM}: please find the cause, then fix it or revert it. This issue closes itself when Verify passes on \`${run.head_branch}\` again.`,
    '',
    stateComment(state),
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

// Whether this completed workflow_run is a push run of this repository's
// Verify on a tracked branch. workflow_run's branches filter matches a PR's
// head branch name too, so a fork's "main" would pass it.
function isTrackedPushRun(run, ctx) {
  return run.event === 'push'
    && run.path === VERIFY_PATH
    && run.head_repository?.full_name === `${ctx.owner}/${ctx.repo}`
    && BRANCHES.includes(run.head_branch);
}

async function onVerifyRun(api, ctx, run, now, log) {
  if (!isTrackedPushRun(run, ctx)) {
    log.info(`Not a tracked push run of Verify (${run.event} on ${run.head_branch}, ${run.path}); ignoring`);
    return null;
  }
  log.info(`Verify #${run.run_number} attempt ${run.run_attempt} on ${run.head_branch} at ${run.head_sha}: ${run.conclusion}`);

  if (RETRIED.has(run.conclusion) && !await previousAttemptFailed(api, run)) {
    log.info(await api.reRunFailedJobs(run.id)
      ? 'Re-running the failed jobs to confirm the failure'
      : 'Already being re-run');
    return null;
  }
  if (BROKEN.has(run.conclusion)) return onRed(api, ctx, run, now, log);
  if (run.conclusion === 'success') return onGreen(api, ctx, run, now, log);
  log.info('Neither passed nor failed; ignoring');
  return null;
}

async function onRed(api, ctx, run, now, log) {
  const branch = run.head_branch;
  const sha = run.head_sha;
  const jobs = isStartupFailure(run) ? [] : await failedJobs(api, run, run.run_attempt);
  const tracked = await findTrackingIssue(api, branch);

  if (tracked) {
    const { issue, state } = tracked;
    const status = await api.compare(state.lastRed, sha);
    if (status === 'behind' || status === 'identical') {
      log.info(`#${issue.number} already tracks ${short(state.lastRed)}, which is not older than this commit; ignoring`);
      return null;
    }
    if (status !== 'ahead') {
      log.warning(`Can't order ${short(sha)} against ${short(state.lastRed)}, which #${issue.number} tracks (${status}); leaving the issue as it is`);
      return null;
    }
    const next = { ...state, lastRed: sha };
    await api.updateIssueBody(issue.number, writeState(issue.body, next));
    const detail = isStartupFailure(run) ? 'could not start' : `failed in ${jobList(jobs, true)}`;
    await api.comment(issue.number,
      `Still failing at ${commitLink(ctx, sha)}: [Verify #${run.run_number}](${run.html_url}) ${detail}.`);
    log.info(`Noted on #${issue.number}`);
    return null;
  }

  // Reported unless a newer commit is known to have passed: when the two can't
  // be ordered, a false alarm costs less than a missed break.
  const greenSha = await api.newestGreenSha(branch);
  if (greenSha && await api.compare(sha, greenSha) === 'ahead') {
    log.info(`${short(greenSha)} has passed since; this result is stale`);
    return null;
  }

  const state = { branch, lastRed: sha, since: now.toISOString() };
  const issue = await api.createTrackingIssue(`\`${branch}\` is broken`, issueBody(ctx, run, jobs, greenSha, state));
  log.info(`Opened #${issue.number}`);
  return {
    level: 'important',
    text: `:rotating_light: ${branch} is broken: ${failureText(run, short(sha))}${isStartupFailure(run) ? '' : ` (${jobList(jobs, false)})`}. Tracking issue: #${issue.number}`,
    link: issue.html_url,
  };
}

async function onGreen(api, ctx, run, now, log) {
  const branch = run.head_branch;
  const sha = run.head_sha;
  const tracked = await findTrackingIssue(api, branch);

  if (tracked) {
    const { issue, state } = tracked;
    const status = await api.compare(state.lastRed, sha);
    if (status === 'behind') {
      log.info(`#${issue.number} tracks ${short(state.lastRed)}, which is newer than this commit; still broken`);
      return null;
    }
    if (status !== 'ahead' && status !== 'identical') {
      log.warning(`Can't order ${short(sha)} against ${short(state.lastRed)}, which #${issue.number} tracks (${status}); leaving it open`);
      return null;
    }
    const broken = duration(state.since, now);
    await api.comment(issue.number,
      `Verify passed at ${commitLink(ctx, sha)}: [Verify #${run.run_number}](${run.html_url}). \`${branch}\` was broken for ${broken}.`);
    await api.closeIssue(issue.number);
    log.info(`Closed #${issue.number}`);
    return {
      level: 'general',
      text: `:white_check_mark: ${branch} is green again at ${short(sha)}, after ${broken} (#${issue.number})`,
      link: run.html_url,
    };
  }

  // A re-run of a run that was cancelled, not one that failed, is no flake.
  if (await previousAttemptFailed(api, run)) {
    const jobs = await failedJobs(api, run, run.run_attempt - 1);
    log.info(`Passed on re-run; attempt ${run.run_attempt - 1} failed in ${jobs.join(', ')}`);
    return {
      level: 'general',
      text: `:warning: Verify on ${branch} failed at ${short(sha)} and passed when re-run, so probably flaky: ${jobList(jobs, false)}`,
      link: run.html_url,
    };
  }
  return null;
}

// Entry point for post-merge.yml: a completed Verify run.
async function handle({ github, context, core }) {
  const { owner, repo } = context.repo;
  const ctx = { owner, repo, serverUrl: context.serverUrl || 'https://github.com' };
  const api = createApi(github, owner, repo);
  const now = new Date();

  const message = await onVerifyRun(api, ctx, context.payload.workflow_run, now, core);

  core.setOutput('level', message?.level || '');
  core.setOutput('text', message?.text || '');
  core.setOutput('link', message?.link || '');
}

module.exports = {
  handle,
  readState,
  duration,
  LABEL,
};
