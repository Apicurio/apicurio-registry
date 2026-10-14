// Tests for post-merge.js — run with: node --test .github/scripts/post-merge.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');

const postMerge = require('./post-merge.js');

// ---------------------------------------------------------------------------
// Stateful mock GitHub API, driven through the module's real createApi
// ---------------------------------------------------------------------------

const OWNER = 'Apicurio';
const REPO = 'apicurio-registry';
const HOUR = 3600 * 1000;

// Commits c1..c9, oldest first: compare() orders them by position.
const HISTORY = ['c1', 'c2', 'c3', 'c4', 'c5', 'c6', 'c7', 'c8', 'c9'].map(c => c.padEnd(40, '0'));
const [C1, C2, C3, C4, C5, C6] = HISTORY;

function httpError(status) {
  return Object.assign(new Error(`HTTP ${status}`), { status });
}

function makeWorld({ issues = [], labelExists = true, greens = [], jobs = {}, reRunConflict = false } = {}) {
  const calls = { reRuns: [], comments: [], createdLabels: [], outputs: {}, logs: [] };
  let labelPresent = labelExists;
  let nextIssue = 500;

  const github = {
    paginate: async (fn, params) => (await fn(params)).data,
    rest: {
      actions: {
        reRunWorkflowFailedJobs: async ({ run_id }) => {
          if (reRunConflict) throw httpError(409);
          calls.reRuns.push(run_id);
        },
        listJobsForWorkflowRunAttempt: async ({ run_id, attempt_number }) => ({
          data: jobs[`${run_id}:${attempt_number}`] || [],
        }),
        listWorkflowRuns: async ({ workflow_id, event, status }) => {
          assert.equal(workflow_id, 'verify.yaml');
          assert.equal(event, 'push');
          assert.equal(status, 'success');
          return { data: { workflow_runs: greens.map(head_sha => ({ head_sha })) } };
        },
      },
      repos: {
        compareCommitsWithBasehead: async ({ basehead }) => {
          const [base, head] = basehead.split('...').map(sha => HISTORY.indexOf(sha));
          if (base < 0 || head < 0) throw httpError(404);
          const status = head > base ? 'ahead' : head < base ? 'behind' : 'identical';
          return { data: { status } };
        },
      },
      issues: {
        listForRepo: async ({ labels, state }) => ({
          data: issues.filter(i => i.state === state && i.labels.includes(labels)),
        }),
        getLabel: async () => {
          if (!labelPresent) throw httpError(404);
          return { data: {} };
        },
        createLabel: async ({ name, color }) => {
          labelPresent = true;
          calls.createdLabels.push({ name, color });
        },
        create: async ({ title, body, labels }) => {
          const issue = {
            number: nextIssue++, title, body, labels, state: 'open',
            html_url: `https://github.com/${OWNER}/${REPO}/issues/${nextIssue - 1}`,
          };
          issues.push(issue);
          return { data: issue };
        },
        update: async ({ issue_number, body, state, state_reason }) => {
          const issue = issues.find(i => i.number === issue_number);
          if (body !== undefined) issue.body = body;
          if (state !== undefined) Object.assign(issue, { state, state_reason });
        },
        createComment: async ({ issue_number, body }) => calls.comments.push({ issue_number, body }),
      },
    },
  };

  const core = {
    info: msg => calls.logs.push(msg),
    setOutput: (name, value) => { calls.outputs[name] = value; },
  };

  return { github, core, calls, issues };
}

function verifyRun(overrides = {}) {
  return {
    id: 1000,
    run_number: 42,
    run_attempt: 1,
    event: 'push',
    path: '.github/workflows/verify.yaml',
    head_branch: 'main',
    head_sha: C3,
    head_repository: { full_name: `${OWNER}/${REPO}` },
    html_url: `https://github.com/${OWNER}/${REPO}/actions/runs/1000`,
    conclusion: 'failure',
    ...overrides,
  };
}

async function onRun(world, run) {
  await postMerge.handle({
    github: world.github,
    core: world.core,
    context: {
      eventName: 'workflow_run',
      repo: { owner: OWNER, repo: REPO },
      serverUrl: 'https://github.com',
      payload: { workflow_run: run },
    },
  });
  return world.calls.outputs;
}

async function onSchedule(world) {
  await postMerge.handle({
    github: world.github,
    core: world.core,
    context: { eventName: 'schedule', repo: { owner: OWNER, repo: REPO }, serverUrl: 'https://github.com', payload: {} },
  });
  return world.calls.outputs;
}

function trackingIssue({ number = 77, branch = 'main', firstRed = C2, lastRed = C2, since, reminded = false } = {}) {
  const state = { branch, firstRed, lastRed, since: since || new Date(Date.now() - HOUR).toISOString(), reminded };
  return {
    number,
    title: `\`${branch}\` is broken`,
    body: `text\n\n<!-- post-merge-state ${JSON.stringify(state)} -->`,
    labels: [postMerge.LABEL],
    state: 'open',
    html_url: `https://github.com/${OWNER}/${REPO}/issues/${number}`,
  };
}

const NOTHING = { level: '', text: '', link: '' };

// ---------------------------------------------------------------------------
// Which runs count
// ---------------------------------------------------------------------------

test('ignores runs that are not push runs of this repository\'s Verify on main', async () => {
  for (const overrides of [
    { event: 'pull_request' },
    { head_branch: '3.3.x' },
    { head_branch: 'feature' },
    { head_repository: { full_name: 'someone/apicurio-registry' } },
    { path: '.github/workflows/my-verify.yaml' },
  ]) {
    const w = makeWorld();
    assert.deepEqual(await onRun(w, verifyRun(overrides)), NOTHING, JSON.stringify(overrides));
    assert.deepEqual(w.calls.reRuns, []);
    assert.equal(w.issues.length, 0);
  }
});

test('cancelled and skipped runs are ignored', async () => {
  for (const conclusion of ['cancelled', 'skipped', 'neutral']) {
    const w = makeWorld({ issues: [trackingIssue()] });
    assert.deepEqual(await onRun(w, verifyRun({ conclusion, run_attempt: 2 })), NOTHING);
    assert.deepEqual(w.calls.reRuns, []);
    assert.equal(w.calls.comments.length, 0);
    assert.equal(w.issues[0].state, 'open');
  }
});

// ---------------------------------------------------------------------------
// First failure: re-run, silently
// ---------------------------------------------------------------------------

test('a failed first attempt is re-run without a message', async () => {
  for (const conclusion of ['failure', 'timed_out']) {
    const w = makeWorld();
    assert.deepEqual(await onRun(w, verifyRun({ conclusion })), NOTHING);
    assert.deepEqual(w.calls.reRuns, [1000]);
    assert.equal(w.issues.length, 0);
  }
});

test('a run already being re-run is left alone', async () => {
  const w = makeWorld({ reRunConflict: true });
  assert.deepEqual(await onRun(w, verifyRun()), NOTHING);
  assert.ok(w.calls.logs.includes('Already being re-run'));
});

// ---------------------------------------------------------------------------
// Confirmed failure
// ---------------------------------------------------------------------------

test('a failed re-run opens the tracking issue and posts to the important channel', async () => {
  const w = makeWorld({
    labelExists: false,
    greens: [C1],
    jobs: {
      '1000:2': [
        { name: 'Unit Tests (3)', conclusion: 'failure' },
        { name: 'Integration Tests (h2)', conclusion: 'timed_out' },
        { name: 'Build', conclusion: 'success' },
        { name: 'Verify Result', conclusion: 'failure' },
      ],
    },
  });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));

  assert.equal(w.issues.length, 1);
  const issue = w.issues[0];
  assert.equal(issue.title, '`main` is broken');
  assert.deepEqual(issue.labels, ['ci/broken']);
  assert.deepEqual(w.calls.createdLabels, [{ name: 'ci/broken', color: 'b60205' }]);
  assert.match(issue.body, /@Apicurio\/maintainers/);
  assert.match(issue.body, /`Unit Tests \(3\)`, `Integration Tests \(h2\)` \|/);
  assert.doesNotMatch(issue.body, /Verify Result/);
  assert.ok(issue.body.includes(`/compare/${C1}...${C3}`));
  assert.ok(issue.body.includes('[Verify #42](https://github.com/Apicurio/apicurio-registry/actions/runs/1000)'));
  const state = postMerge.readState(issue.body);
  assert.equal(state.branch, 'main');
  assert.equal(state.firstRed, C3);
  assert.equal(state.lastRed, C3);
  assert.equal(state.reminded, false);

  assert.equal(out.level, 'important');
  assert.equal(out.text,
    ':rotating_light: main is broken: Verify failed at c300000, and again when re-run (Unit Tests (3), Integration Tests (h2)). Tracking issue: #500');
  assert.equal(out.link, issue.html_url);
  assert.deepEqual(w.calls.reRuns, []);
});

test('the label is not re-created when it exists', async () => {
  const w = makeWorld({ labelExists: true });
  await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.deepEqual(w.calls.createdLabels, []);
  assert.equal(w.issues.length, 1);
});

test('a startup failure is confirmed at once, since it cannot be re-run', async () => {
  const w = makeWorld();
  const out = await onRun(w, verifyRun({ conclusion: 'startup_failure' }));
  assert.deepEqual(w.calls.reRuns, []);
  assert.equal(w.issues.length, 1);
  assert.match(w.issues[0].body, /\| Failed jobs \| no job named \|/);
  assert.equal(out.level, 'important');
});

test('with no green run on record the issue says so instead of linking the changes', async () => {
  const w = makeWorld({ greens: [] });
  await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.match(w.issues[0].body, /\| Suspects \| no earlier green commit found \|/);
});

test('a confirmed failure older than the newest green commit is stale', async () => {
  const w = makeWorld({ greens: [C4] });
  assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: C3 })), NOTHING);
  assert.equal(w.issues.length, 0);
});

test('a newer confirmed failure is noted on the open issue, without a message', async () => {
  const w = makeWorld({
    issues: [trackingIssue({ firstRed: C2, lastRed: C2 })],
    jobs: { '1000:2': [{ name: 'Unit Tests (1)', conclusion: 'failure' }] },
  });
  assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: C4 })), NOTHING);

  assert.equal(w.issues.length, 1);
  const state = postMerge.readState(w.issues[0].body);
  assert.equal(state.firstRed, C2);
  assert.equal(state.lastRed, C4);
  assert.equal(w.calls.comments.length, 1);
  assert.equal(w.calls.comments[0].issue_number, 77);
  assert.match(w.calls.comments[0].body, /^Still failing at \[`c400000`\]/);
  assert.match(w.calls.comments[0].body, /`Unit Tests \(1\)`/);
});

test('an older or the same confirmed failure leaves the open issue alone', async () => {
  for (const sha of [C2, C3]) {
    const w = makeWorld({ issues: [trackingIssue({ firstRed: C3, lastRed: C3 })] });
    assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: sha })), NOTHING);
    assert.equal(w.calls.comments.length, 0);
    assert.equal(postMerge.readState(w.issues[0].body).lastRed, C3);
  }
});

test('a tracking issue for another branch, or a labelled issue without state, is not this branch\'s', async () => {
  const other = trackingIssue({ number: 80, branch: '3.3.x' });
  const unrelated = { ...trackingIssue({ number: 81 }), body: 'someone added the label by hand' };
  const w = makeWorld({ issues: [other, unrelated] });
  await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.equal(w.issues.length, 3);
  assert.equal(w.issues[2].number, 500);
});

test('more than five failed jobs are summarised', async () => {
  const names = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
  const w = makeWorld({ jobs: { '1000:2': names.map(name => ({ name, conclusion: 'failure' })) } });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.match(out.text, /\(A, B, C, D, E and 2 more\)/);
  assert.match(w.issues[0].body, /`A`, `B`, `C`, `D`, `E` and 2 more/);
});

// ---------------------------------------------------------------------------
// Success
// ---------------------------------------------------------------------------

test('a newer green run closes the issue and posts to the general channel', async () => {
  const since = new Date(Date.now() - (5 * HOUR + 12 * 60 * 1000)).toISOString();
  const w = makeWorld({ issues: [trackingIssue({ firstRed: C2, lastRed: C3, since })] });
  const out = await onRun(w, verifyRun({ conclusion: 'success', head_sha: C4 }));

  assert.equal(w.issues[0].state, 'closed');
  assert.equal(w.issues[0].state_reason, 'completed');
  assert.equal(w.calls.comments.length, 1);
  assert.match(w.calls.comments[0].body, /^Verify passed at \[`c400000`\].*`main` was broken for 5h 12m\.$/);
  assert.equal(out.level, 'general');
  assert.equal(out.text, ':white_check_mark: main is green again at c400000, after 5h 12m (#77)');
  assert.equal(out.link, 'https://github.com/Apicurio/apicurio-registry/actions/runs/1000');
});

test('the failing commit passing on a later re-run closes the issue too', async () => {
  const w = makeWorld({ issues: [trackingIssue({ lastRed: C3 })] });
  const out = await onRun(w, verifyRun({ conclusion: 'success', head_sha: C3, run_attempt: 3 }));
  assert.equal(w.issues[0].state, 'closed');
  assert.equal(out.level, 'general');
});

test('a green run older than the tracked failure leaves the issue open', async () => {
  const w = makeWorld({ issues: [trackingIssue({ lastRed: C4 })] });
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'success', head_sha: C3, run_attempt: 2 })), NOTHING);
  assert.equal(w.issues[0].state, 'open');
  assert.equal(w.calls.comments.length, 0);
});

test('a re-run that passes with no issue open is reported as a flake', async () => {
  const w = makeWorld({
    jobs: {
      '1000:1': [
        { name: 'Unit Tests (2)', conclusion: 'failure' },
        { name: 'Verify Result', conclusion: 'failure' },
        { name: 'Build', conclusion: 'success' },
      ],
    },
  });
  const out = await onRun(w, verifyRun({ conclusion: 'success', run_attempt: 2 }));
  assert.equal(out.level, 'general');
  assert.equal(out.text, ':warning: Verify on main failed at c300000 and passed when re-run, so probably flaky: Unit Tests (2)');
  assert.equal(w.issues.length, 0);
});

test('a re-run of a cancelled run that passes is not a flake', async () => {
  const w = makeWorld({ jobs: { '1000:1': [{ name: 'Unit Tests (2)', conclusion: 'cancelled' }] } });
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'success', run_attempt: 2 })), NOTHING);
});

test('a first attempt that passes with no issue open says nothing', async () => {
  const w = makeWorld();
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'success' })), NOTHING);
  assert.equal(w.calls.comments.length, 0);
});

// ---------------------------------------------------------------------------
// Reminder
// ---------------------------------------------------------------------------

test('an issue open for a day gets one reminder', async () => {
  const since = new Date(Date.now() - 25 * HOUR).toISOString();
  const w = makeWorld({ issues: [trackingIssue({ firstRed: C2, since })] });
  const out = await onSchedule(w);

  assert.equal(out.level, 'important');
  assert.equal(out.text, ':rotating_light: main has been broken for 25h, since c200000 (#77)');
  assert.equal(out.link, 'https://github.com/Apicurio/apicurio-registry/issues/77');
  assert.equal(w.calls.comments.length, 1);
  assert.match(w.calls.comments[0].body, /^@Apicurio\/maintainers: `main` has been broken for 25h, since \[`c200000`\]/);
  assert.equal(postMerge.readState(w.issues[0].body).reminded, true);

  w.calls.outputs = {};
  assert.deepEqual(await onSchedule(w), NOTHING);
  assert.equal(w.calls.comments.length, 1);
});

test('no reminder before a day, or with no issue open', async () => {
  const young = makeWorld({ issues: [trackingIssue({ since: new Date(Date.now() - 23 * HOUR).toISOString() })] });
  assert.deepEqual(await onSchedule(young), NOTHING);
  assert.equal(young.calls.comments.length, 0);

  assert.deepEqual(await onSchedule(makeWorld()), NOTHING);
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

test('readState tolerates a missing or broken state comment', () => {
  assert.equal(postMerge.readState('no state here'), null);
  assert.equal(postMerge.readState('<!-- post-merge-state {broken -->'), null);
  assert.equal(postMerge.readState(null), null);
  assert.deepEqual(postMerge.readState('x <!-- post-merge-state {"branch":"main"} --> y'), { branch: 'main' });
});

test('duration rounds to minutes and drops a zero part', () => {
  const now = new Date('2026-10-14T12:00:00Z');
  assert.equal(postMerge.duration('2026-10-14T11:59:40Z', now), '0m');
  assert.equal(postMerge.duration('2026-10-14T11:15:00Z', now), '45m');
  assert.equal(postMerge.duration('2026-10-14T09:00:00Z', now), '3h');
  assert.equal(postMerge.duration('2026-10-13T10:30:00Z', now), '25h 30m');
});
