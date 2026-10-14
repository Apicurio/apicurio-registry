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

// attempts: the conclusion of earlier attempts, by `run:attempt`; failure
// unless given, so attempt 2 of a failed run is a confirmation.
// prs: the PR each commit was merged in, by sha, as { number, mergedBy };
// a commit not listed was pushed directly. compareTotal overrides how many
// commits compare reports, for a range wider than HISTORY.
function makeWorld({
  issues = [], labelExists = true, greens = [], jobs = {}, attempts = {}, prs = {}, compareTotal, reRunConflict = false,
} = {}) {
  const calls = { reRuns: [], comments: [], createdLabels: [], outputs: {}, logs: [], warnings: [] };
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
        getWorkflowRunAttempt: async ({ run_id, attempt_number }) => ({
          data: { conclusion: attempts[`${run_id}:${attempt_number}`] || 'failure' },
        }),
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
          const commits = HISTORY.slice(base + 1, head + 1).map(sha => ({ sha }));
          return { data: { status, commits, total_commits: compareTotal ?? commits.length } };
        },
        // Listed first: an open PR whose branch has the commit (it merged
        // main), and one merged into another branch. Neither is the suspect.
        listPullRequestsAssociatedWithCommit: async ({ commit_sha }) => ({
          data: [
            { number: 9001, merged_at: null, base: { ref: 'main' } },
            { number: 9002, merged_at: '2026-10-14T00:00:00Z', base: { ref: 'feature' } },
            ...prs[commit_sha] ? [{ number: prs[commit_sha].number, merged_at: '2026-10-14T00:00:00Z', base: { ref: 'main' } }] : [],
          ],
        }),
      },
      pulls: {
        get: async ({ pull_number }) => {
          const pr = Object.values(prs).find(p => p.number === pull_number);
          return {
            data: {
              number: pr.number,
              html_url: `https://github.com/${OWNER}/${REPO}/pull/${pr.number}`,
              merged_by: pr.mergedBy ? { login: pr.mergedBy } : null,
            },
          };
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
    warning: msg => calls.warnings.push(msg),
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

function trackingIssue({ number = 77, branch = 'main', lastRed = C2, since } = {}) {
  const state = { branch, lastRed, since: since || new Date(Date.now() - HOUR).toISOString() };
  return {
    number,
    title: `\`${branch}\` is broken`,
    body: `text\n\n<!-- post-merge-state ${JSON.stringify(state)} -->`,
    labels: [postMerge.LABEL],
    state: 'open',
    html_url: `https://github.com/${OWNER}/${REPO}/issues/${number}`,
  };
}

const NOTHING = { level: '', text: '', link: '', mention: '' };

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

test('a failure after a pass or a cancellation, re-run by hand, is re-run before it counts', async () => {
  for (const previous of ['success', 'cancelled']) {
    const w = makeWorld({ attempts: { '1000:1': previous } });
    assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2 })), NOTHING, previous);
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
  assert.equal(state.lastRed, C3);

  assert.equal(out.level, 'important');
  assert.equal(out.text,
    ':rotating_light: main is broken: Verify failed at c300000, and again when re-run (Unit Tests (3), Integration Tests (h2)). '
    + `Suspects: https://github.com/Apicurio/apicurio-registry/commit/${C3} pushed directly, `
    + `https://github.com/Apicurio/apicurio-registry/commit/${C2} pushed directly. Tracking issue: #500`);
  assert.equal(out.link, issue.html_url);
  assert.deepEqual(w.calls.reRuns, []);
});

test('the label is not re-created when it exists', async () => {
  const w = makeWorld({ labelExists: true });
  await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.deepEqual(w.calls.createdLabels, []);
  assert.equal(w.issues.length, 1);
});

test('a startup failure is confirmed at once, since it cannot be re-run, and says it could not start', async () => {
  const w = makeWorld();
  const out = await onRun(w, verifyRun({ conclusion: 'startup_failure' }));
  assert.deepEqual(w.calls.reRuns, []);
  assert.equal(w.issues.length, 1);
  assert.match(w.issues[0].body, /^`main` is broken: Verify could not start at \[`c300000`\]\(.*\) \(startup failure\)\.\n/);
  assert.match(w.issues[0].body, /\| Failed jobs \| none, the run did not start \|/);
  assert.equal(out.level, 'important');
  assert.match(out.text, /^:rotating_light: main is broken: Verify could not start at c300000 \(startup failure\)\. Suspects: /);

  w.calls.outputs = {};
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'startup_failure', head_sha: C4 })), NOTHING);
  assert.match(w.calls.comments[0].body, /^Still failing at \[`c400000`\].*\) could not start\.$/);
});

// ---------------------------------------------------------------------------
// Suspects
// ---------------------------------------------------------------------------

test('a single suspect PR is listed on the issue and its merger is mentioned', async () => {
  const w = makeWorld({ greens: [C2], prs: { [C3]: { number: 103, mergedBy: 'alice' } } });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));

  assert.equal(out.mention, 'alice');
  assert.match(out.text, / Suspects: https:\/\/github\.com\/Apicurio\/apicurio-registry\/pull\/103 merged by alice\. Tracking issue: #500$/);
  assert.match(w.issues[0].body, /\| Suspect \| Merged by \|\n\|---\|---\|\n\| #103 \| @alice \|\n\n/);
});

test('with several suspects the merger of the most is mentioned first, a tie going to the newest merge', async () => {
  const w = makeWorld({
    greens: [C1],
    prs: {
      [C2]: { number: 102, mergedBy: 'dave' },
      [C3]: { number: 103, mergedBy: 'alice' },
      [C5]: { number: 105, mergedBy: 'alice' },
      [C6]: { number: 106, mergedBy: 'carol' },
    },
  });
  const out = await onRun(w, verifyRun({ run_attempt: 2, head_sha: C6 }));

  assert.equal(out.mention, 'alice,carol,dave');
  const pull = n => `https://github.com/Apicurio/apicurio-registry/pull/${n}`;
  assert.ok(out.text.includes(`Suspects: ${pull(106)} merged by carol, ${pull(105)} merged by alice, `
    + `https://github.com/Apicurio/apicurio-registry/commit/${C4} pushed directly, ${pull(103)} merged by alice, ${pull(102)} merged by dave. `));
  assert.ok(w.issues[0].body.includes([
    '| #106 | @carol |',
    '| #105 | @alice |',
    `| [\`c400000\`](https://github.com/Apicurio/apicurio-registry/commit/${C4}), pushed directly | |`,
    '| #103 | @alice |',
    '| #102 | @dave |',
  ].join('\n')));
});

test('with no earlier green run the issue says so, and only the failing commit is a suspect', async () => {
  const w = makeWorld({ greens: [], prs: { [C3]: { number: 103, mergedBy: 'alice' } } });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.equal(out.mention, 'alice');
  assert.match(w.issues[0].body, /\| Changes \| no earlier green commit found \|/);
  assert.match(w.issues[0].body, /\|---\|---\|\n\| #103 \| @alice \|\n\n/);
});

test('too many changes since the last green commit name nobody', async () => {
  const w = makeWorld({ greens: [C1], compareTotal: 101, prs: { [C3]: { number: 103, mergedBy: 'alice' } } });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.equal(out.mention, '');
  assert.match(out.text, /Too many changes since the last green commit to name suspects \(over 100\)\./);
  assert.match(w.issues[0].body, /More than 100 changes since the last green commit; too many to name suspects\./);
});

test('a confirmed failure older than the newest green commit is stale', async () => {
  const w = makeWorld({ greens: [C4] });
  assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: C3 })), NOTHING);
  assert.equal(w.issues.length, 0);
});

test('a newer confirmed failure is noted on the open issue, without a message', async () => {
  const w = makeWorld({
    issues: [trackingIssue({ lastRed: C2 })],
    jobs: { '1000:2': [{ name: 'Unit Tests (1)', conclusion: 'failure' }] },
  });
  assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: C4 })), NOTHING);

  assert.equal(w.issues.length, 1);
  const state = postMerge.readState(w.issues[0].body);
  assert.equal(state.lastRed, C4);
  assert.equal(w.calls.comments.length, 1);
  assert.equal(w.calls.comments[0].issue_number, 77);
  assert.match(w.calls.comments[0].body, /^Still failing at \[`c400000`\]/);
  assert.match(w.calls.comments[0].body, /`Unit Tests \(1\)`/);
});

test('an older or the same confirmed failure leaves the open issue alone', async () => {
  for (const sha of [C2, C3]) {
    const w = makeWorld({ issues: [trackingIssue({ lastRed: C3 })] });
    assert.deepEqual(await onRun(w, verifyRun({ run_attempt: 2, head_sha: sha })), NOTHING);
    assert.equal(w.calls.comments.length, 0);
    assert.equal(postMerge.readState(w.issues[0].body).lastRed, C3);
  }
});

test('a result that can\'t be ordered against the tracked failure leaves the issue as it is', async () => {
  const unknown = 'f'.repeat(40);
  for (const conclusion of ['failure', 'success']) {
    const w = makeWorld({ issues: [trackingIssue({ lastRed: C3 })] });
    assert.deepEqual(await onRun(w, verifyRun({ conclusion, run_attempt: 2, head_sha: unknown })), NOTHING, conclusion);
    assert.equal(w.issues[0].state, 'open');
    assert.equal(w.calls.comments.length, 0);
    assert.equal(postMerge.readState(w.issues[0].body).lastRed, C3);
    assert.equal(w.calls.warnings.length, 1);
  }
});

test('a confirmed failure that can\'t be ordered against the newest green commit is still reported', async () => {
  const w = makeWorld({ greens: ['f'.repeat(40)], prs: { [C3]: { number: 103, mergedBy: 'alice' } } });
  const out = await onRun(w, verifyRun({ run_attempt: 2 }));
  assert.equal(w.issues.length, 1);
  assert.equal(out.level, 'important');
  assert.equal(out.mention, 'alice');
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
  const w = makeWorld({ issues: [trackingIssue({ lastRed: C3, since })] });
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
  const w = makeWorld({
    attempts: { '1000:1': 'cancelled' },
    jobs: { '1000:1': [{ name: 'Unit Tests (2)', conclusion: 'cancelled' }] },
  });
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'success', run_attempt: 2 })), NOTHING);
});

test('a first attempt that passes with no issue open says nothing', async () => {
  const w = makeWorld();
  assert.deepEqual(await onRun(w, verifyRun({ conclusion: 'success' })), NOTHING);
  assert.equal(w.calls.comments.length, 0);
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
