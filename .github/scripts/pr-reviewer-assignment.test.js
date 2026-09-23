// Tests for pr-reviewer-assignment.js — run with:
//   node --test .github/scripts/pr-reviewer-assignment.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('child_process');
const path = require('path');

const ra = require('./pr-reviewer-assignment.js');

const NOW = new Date('2026-09-23T12:00:00Z');
const daysAgo = n => new Date(NOW - n * 24 * 60 * 60 * 1000).toISOString();
const hoursAgo = n => new Date(NOW - n * 60 * 60 * 1000).toISOString();

const SETTINGS = {
  weights: { ownership: 0.55, interest: 0.30, issue_author: 0.15 },
  ownership: {
    max_files: 30, commits_per_file: 10, since_months: 12, recent_months: 3,
    recent_multiplier: 2, ignore_paths: ['**/package-lock.json', 'go-sdk/pkg/**'],
  },
  fairness: { window_days: 30, recent_hours: 48, recent_multiplier: 2, swap_ratio: 1.5, swap_min_gap: 3 },
  bot_rotation: { reviewers: ['alice', 'bob'] },
  reviewers: {
    alice: { interest: { 'area/*': 0.4, 'area/ui/*': 1.0 } },
    bob: { interest: { 'area/*': 0.4 } },
    carol: { interest: { 'area/documentation': 1.0 } },
  },
};

const CONFIG = {
  maintainers: ['alice', 'bob', 'carol', 'dave'],
  auto_accept: ['renovate[bot]'],
  reviewer_assignment: SETTINGS,
};

// ---------------------------------------------------------------------------
// Mock GitHub
// ---------------------------------------------------------------------------

function makeWorld({
  pr = {}, files = [], commits = {}, closingAuthors = [], assignments = {},
  comments = [], fail = {},
} = {}) {
  const calls = { assigned: [], comments: [], listCommits: [], loadQueries: 0 };
  const thePr = {
    number: 42, state: 'open', draft: false, user: { login: 'contributor' },
    labels: [], assignees: [], requested_reviewers: [], requested_teams: [], ...pr,
  };
  const github = {
    rest: {
      pulls: { get: async () => ({ data: thePr }), listFiles: 'listFiles' },
      issues: {
        listComments: 'listComments',
        addAssignees: async ({ assignees }) => { calls.assigned.push(...assignees); },
        createComment: async ({ body }) => { calls.comments.push(body); },
      },
      repos: {
        listCommits: async ({ path: p }) => {
          calls.listCommits.push(p);
          if (fail.commits?.includes(p)) throw new Error('boom');
          return {
            data: (commits[p] || []).map(([login, date]) => ({
              author: login ? { login } : null, commit: { author: { date } },
            })),
          };
        },
      },
    },
    paginate: async fn => {
      if (fn === 'listFiles') return files;
      if (fn === 'listComments') return comments.map(body => ({ body }));
      return [];
    },
    graphql: async (query, vars) => {
      if (query.includes('closingIssuesReferences')) {
        if (fail.closing) throw new Error('boom');
        return { repository: { pullRequest: { closingIssuesReferences: {
          nodes: closingAuthors.map((login, i) => ({ number: 100 + i, author: { login } })),
        } } } };
      }
      calls.loadQueries++;
      if (fail.loads) throw new Error('boom');
      const out = {};
      for (const [key, q] of Object.entries(vars)) {
        const login = /assignee:(\S+)/.exec(q)[1];
        out[`u${key.slice(1)}`] = {
          nodes: (assignments[login] || []).map((a, i) => ({
            number: a.number ?? 1000 + i,
            author: { __typename: a.bot ? 'Bot' : 'User', login: a.author ?? (a.bot ? 'renovate' : 'someone') },
            timelineItems: { nodes: [{ createdAt: a.at, assignee: { login } }] },
          })),
        };
      }
      return out;
    },
  };
  const logs = { info: [], warning: [] };
  const core = { info: m => logs.info.push(m), warning: m => logs.warning.push(m) };
  const context = { repo: { owner: 'Apicurio', repo: 'apicurio-registry' } };
  return { github, core, context, calls, logs };
}

function run(w, extra = {}) {
  return ra.assignReviewer({
    github: w.github, context: w.context, core: w.core, prNumber: 42,
    config: CONFIG, now: NOW, ...extra,
  });
}

// ---------------------------------------------------------------------------
// Interest
// ---------------------------------------------------------------------------

test('interestWeight: an exact pattern matches only that label', () => {
  const p = { 'area/ui': 0.7 };
  assert.equal(ra.interestWeight(p, 'area/ui'), 0.7);
  assert.equal(ra.interestWeight(p, 'area/ui/editors'), undefined);
});

test('interestWeight: a subtree pattern matches the label itself and everything under it', () => {
  const p = { 'area/storage/*': 0.8 };
  assert.equal(ra.interestWeight(p, 'area/storage'), 0.8);
  assert.equal(ra.interestWeight(p, 'area/storage/sql'), 0.8);
});

test('interestWeight: a subtree is by segment, not by string prefix', () => {
  assert.equal(ra.interestWeight({ 'area/ui/*': 1 }, 'area/uix'), undefined);
});

test('interestWeight: area/* matches every area label', () => {
  assert.equal(ra.interestWeight({ 'area/*': 0.4 }, 'area/rest/ccompat'), 0.4);
});

test('interestWeight: the deepest matching pattern wins', () => {
  const p = { 'area/*': 0.4, 'area/ui/*': 1.0 };
  assert.equal(ra.interestWeight(p, 'area/ui/editors'), 1.0);
  assert.equal(ra.interestWeight(p, 'area/rest'), 0.4);
});

test('interestWeight: an exact pattern beats a subtree pattern for the same label', () => {
  const p = { 'area/ui/*': 1.0, 'area/ui': 0.2 };
  assert.equal(ra.interestWeight(p, 'area/ui'), 0.2);
  assert.equal(ra.interestWeight(p, 'area/ui/editors'), 1.0);
});

test('interestWeight: 0 carves a label out of a broader pattern', () => {
  const p = { 'area/*': 0.4, 'area/ui/editors': 0 };
  assert.equal(ra.interestWeight(p, 'area/ui/editors'), 0);
  assert.equal(ra.interestWeight(p, 'area/ui'), 0.4);
});

test('interestWeight: matching is case-insensitive, like GitHub labels', () => {
  assert.equal(ra.interestWeight({ 'area/CLI': 1 }, 'area/cli'), 1);
  assert.equal(ra.interestWeight({ 'area/ci/*': 1 }, 'area/CI/automation'), 1);
});

test('mostSpecificLabels: an ancestor of another label is dropped, siblings are kept', () => {
  assert.deepEqual(
    ra.mostSpecificLabels(['area/storage', 'area/storage/sql', 'area/storage/kafkasql', 'area/ui']),
    ['area/storage/sql', 'area/storage/kafkasql', 'area/ui']);
});

test('mostSpecificLabels: non-area labels and duplicates are ignored', () => {
  assert.deepEqual(
    ra.mostSpecificLabels(['lifecycle/ready-for-review', 'area/ui', 'area/ui']),
    ['area/ui']);
});

test('interestScore: the highest weight among the labels, so extra labels cannot dilute it', () => {
  const p = { 'area/ui/*': 1.0, 'area/rest': 0.5 };
  assert.equal(ra.interestScore(p, ['area/ui/editors', 'area/rest', 'area/CLI']), 1.0);
  assert.equal(ra.interestScore(p, ['area/rest', 'area/CLI']), 0.5);
});

test('interestScore: 0 for a PR without area labels or without a matching one', () => {
  assert.equal(ra.interestScore({ 'area/*': 1 }, []), 0);
  assert.equal(ra.interestScore({ 'area/ui': 1 }, ['area/CLI']), 0);
});

test('primaryLabel: the label the classifier scored highest', () => {
  const scores = { 'area/ui': 0.52, 'area/documentation': 0.41, 'area/rest': 0.60 };
  assert.equal(ra.primaryLabel(['area/documentation', 'area/ui'], scores), 'area/ui');
});

test('primaryLabel: labels without a score are ignored, and none scored means none', () => {
  assert.equal(ra.primaryLabel(['area/ui', 'area/CLI'], { 'area/CLI': 0.3 }), 'area/CLI');
  assert.equal(ra.primaryLabel(['area/ui'], {}), null);
  assert.equal(ra.primaryLabel([], { 'area/ui': 0.9 }), null);
});

test('primaryLabel: an exact tie goes alphabetically, so reruns agree', () => {
  assert.equal(ra.primaryLabel(['area/ui', 'area/CLI'], { 'area/ui': 0.4, 'area/CLI': 0.4 }), 'area/CLI');
});

// ---------------------------------------------------------------------------
// Ownership
// ---------------------------------------------------------------------------

test('globToRegExp: **/ matches at any depth including the root', () => {
  const re = ra.globToRegExp('**/package-lock.json');
  assert.ok(re.test('package-lock.json'));
  assert.ok(re.test('ui/ui-app/package-lock.json'));
  assert.ok(!re.test('ui/package-lock.json.bak'));
});

test('globToRegExp: a trailing ** covers a whole directory, by segment', () => {
  const re = ra.globToRegExp('go-sdk/pkg/**');
  assert.ok(re.test('go-sdk/pkg/registryclient-v3/models/foo.go'));
  assert.ok(!re.test('go-sdk/pkgx/foo.go'));
});

test('selectOwnershipFiles: added and ignored files are skipped, largest change first, capped', () => {
  const files = [
    { filename: 'a.java', status: 'modified', changes: 5 },
    { filename: 'b.java', status: 'added', changes: 500 },
    { filename: 'ui/package-lock.json', status: 'modified', changes: 900 },
    { filename: 'c.java', status: 'modified', changes: 50 },
    { filename: 'd.java', status: 'removed', changes: 20 },
  ];
  const selected = ra.selectOwnershipFiles(files, { ...SETTINGS.ownership, max_files: 2 });
  assert.deepEqual(selected, ['c.java', 'd.java']);
});

test('selectOwnershipFiles: a renamed file is looked up under its previous name', () => {
  const files = [{ filename: 'new/A.java', previous_filename: 'old/A.java', status: 'renamed', changes: 3 }];
  assert.deepEqual(ra.selectOwnershipFiles(files, SETTINGS.ownership), ['old/A.java']);
});

test('ownershipFromCommits: recent commits count recent_multiplier times', () => {
  const scores = ra.ownershipFromCommits(
    [[['alice', daysAgo(10)], ['bob', daysAgo(200)]].map(([login, date]) => ({ login, date }))],
    ['alice', 'bob'], SETTINGS.ownership, NOW);
  assert.equal(scores.get('alice'), 2 / 3);
  assert.equal(scores.get('bob'), 1 / 3);
});

test('ownershipFromCommits: each file weighs the same however long its history', () => {
  const hot = Array.from({ length: 10 }, () => ({ login: 'alice', date: daysAgo(200) }));
  const quiet = [{ login: 'bob', date: daysAgo(200) }];
  const scores = ra.ownershipFromCommits([hot, quiet], ['alice', 'bob'], SETTINGS.ownership, NOW);
  assert.equal(scores.get('alice'), 0.5);
  assert.equal(scores.get('bob'), 0.5);
});

test('ownershipFromCommits: a contributor-only file lowers everyone, a bot-only file is ignored', () => {
  const owned = [{ login: 'alice', date: daysAgo(200) }];
  const contributorOnly = [{ login: 'someone', date: daysAgo(200) }];
  const botOnly = [{ login: 'renovate[bot]', date: daysAgo(1) }];
  const scores = ra.ownershipFromCommits([owned, contributorOnly, botOnly], ['alice'], SETTINGS.ownership, NOW);
  assert.equal(scores.get('alice'), 0.5);
});

test('ownershipFromCommits: logins match case-insensitively and deleted accounts are skipped', () => {
  const scores = ra.ownershipFromCommits(
    [[{ login: 'Alice', date: daysAgo(200) }, { login: undefined, date: daysAgo(1) }]],
    ['alice'], SETTINGS.ownership, NOW);
  assert.equal(scores.get('alice'), 1);
});

test('ownershipFromCommits: no history at all scores 0, not NaN', () => {
  const scores = ra.ownershipFromCommits([], ['alice'], SETTINGS.ownership, NOW);
  assert.equal(scores.get('alice'), 0);
});

// ---------------------------------------------------------------------------
// Ranking and comment
// ---------------------------------------------------------------------------

function signals({
  interest = {}, ownership = {}, issueAuthors = [], load = {}, labelled = true, primary = 'area/rest',
}) {
  return {
    interest: new Map(Object.entries(interest)),
    ownership: new Map(Object.entries(ownership)),
    issueAuthors,
    load: new Map(Object.entries(load)),
    labelled,
    primary,
  };
}

test('rankCandidates: the score is the weighted sum of the three signals', () => {
  const r = ra.rankCandidates(['alice'],
    signals({ interest: { alice: 0.8 }, ownership: { alice: 0.4 }, issueAuthors: ['alice'] }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.winner.parts.ownership.toFixed(4), '0.2200');
  assert.equal(r.winner.parts.interest.toFixed(4), '0.2400');
  assert.equal(r.winner.parts.issue_author, 0.15);
  assert.equal(r.winner.score.toFixed(4), '0.6100');
});

test('rankCandidates: load is not part of the score', () => {
  const busy = ra.rankCandidates(['alice'], signals({ interest: { alice: 0.4 }, load: { alice: 50 } }), SETTINGS);
  const idle = ra.rankCandidates(['alice'], signals({ interest: { alice: 0.4 } }), SETTINGS);
  assert.equal(busy.winner.score, idle.winner.score);
});

test('rankCandidates: a reviewer with no link to the PR is not considered, and is listed as such', () => {
  const r = ra.rankCandidates(['alice', 'carol'],
    signals({ interest: { alice: 0.4 }, load: { alice: 50 } }), SETTINGS);
  assert.deepEqual(r.ranked.map(x => x.login), ['alice']);
  assert.deepEqual(r.notConsidered, ['carol']);
  assert.equal(r.fallback, false);
});

test('rankCandidates: any one of interest, ownership or issue authorship is enough', () => {
  const r = ra.rankCandidates(['alice', 'bob', 'carol'],
    signals({ interest: { alice: 0.4 }, ownership: { bob: 0.1 }, issueAuthors: ['carol'] }), SETTINGS);
  assert.deepEqual(r.ranked.map(x => x.login).sort(), ['alice', 'bob', 'carol']);
});

test('rankCandidates: when nobody has a link, everyone is considered and load decides', () => {
  const r = ra.rankCandidates(['alice', 'bob'], signals({ load: { alice: 4, bob: 1 } }), SETTINGS);
  assert.equal(r.fallback, true);
  assert.deepEqual(r.notConsidered, []);
  assert.equal(r.winner.login, 'bob');
});

test('rankCandidates: equal scores go to the less loaded, then alphabetically', () => {
  const byName = ra.rankCandidates(['bob', 'alice'], signals({ interest: { alice: 0.4, bob: 0.4 } }), SETTINGS);
  assert.deepEqual(byName.ranked.map(x => x.login), ['alice', 'bob']);
  const byLoad = ra.rankCandidates(['alice', 'bob'],
    signals({ interest: { alice: 0.4, bob: 0.4 }, load: { alice: 2 } }), SETTINGS);
  assert.equal(byLoad.winner.login, 'bob');
});

// alice owns the code and wins on score; bob is the runner-up.
const OVERLOADED = { ownership: { alice: 1, bob: 0.2 }, load: { alice: 9, bob: 6 } };

test('rankCandidates: an overloaded winner hands the PR to a runner-up interested in the primary area', () => {
  // bob's area/* covers the primary area area/rest.
  const r = ra.rankCandidates(['alice', 'bob'], signals(OVERLOADED), SETTINGS);
  assert.equal(r.winner.login, 'bob');
  assert.equal(r.runnerUp.login, 'alice');
  assert.deepEqual([r.swap.from.login, r.swap.to.login, r.swap.applied], ['alice', 'bob', true]);
});

test('rankCandidates: no swap to a runner-up with no interest in the primary area', () => {
  // carol only covers area/documentation; the PR is mainly area/ui.
  const r = ra.rankCandidates(['alice', 'carol'], signals({
    ownership: { alice: 1, carol: 0.2 }, load: { alice: 9, carol: 1 }, primary: 'area/ui',
  }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.runnerUp.login, 'carol');
  assert.equal(r.swap.applied, false);
});

test('rankCandidates: interest in a secondary label does not make a runner-up eligible for the swap', () => {
  // The case the primary area exists for: a UI PR that also touched docs.
  const r = ra.rankCandidates(['alice', 'carol'], signals({
    interest: { alice: 1, carol: 1 }, ownership: { alice: 1 }, load: { alice: 65, carol: 14 },
    primary: 'area/ui',
  }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.swap.applied, false);
});

test('rankCandidates: a PR without area labels can be swapped to any runner-up', () => {
  const r = ra.rankCandidates(['alice', 'carol'], signals({
    ownership: { alice: 1, carol: 0.2 }, load: { alice: 9, carol: 1 }, labelled: false, primary: null,
  }), SETTINGS);
  assert.equal(r.winner.login, 'carol');
  assert.equal(r.swap.applied, true);
});

test('rankCandidates: a labelled PR with no known primary area is not swapped', () => {
  const r = ra.rankCandidates(['alice', 'bob'], signals({ ...OVERLOADED, primary: null }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.swap.applied, false);
});

test('rankCandidates: no swap on a large ratio with a small gap', () => {
  const r = ra.rankCandidates(['alice', 'bob'],
    signals({ ownership: { alice: 1, bob: 0.2 }, load: { alice: 2, bob: 1 } }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.swap, null);
});

test('rankCandidates: no swap on a large gap with a small ratio', () => {
  const r = ra.rankCandidates(['alice', 'bob'],
    signals({ ownership: { alice: 1, bob: 0.2 }, load: { alice: 44, bob: 40 } }), SETTINGS);
  assert.equal(r.winner.login, 'alice');
  assert.equal(r.swap, null);
});

const LINKS = {
  tuning: 'https://github.com/o/r/blob/main/.github/PR_LIFECYCLE.md#adjusting-which-prs-are-assigned-to-you',
  run: 'https://github.com/o/r/actions/runs/7',
};

test('formatComment: one visible line, and the reasoning collapsed below it', () => {
  const r = ra.rankCandidates(['alice', 'bob', 'carol'],
    signals({ interest: { alice: 1, bob: 0.4 }, ownership: { alice: 1 }, issueAuthors: ['bob'] }), SETTINGS);
  const body = ra.formatComment(r, SETTINGS, {
    suggestOnly: false, labels: ['area/ui/editors', 'area/rest'], primary: 'area/ui/editors', links: LINKS,
  });
  assert.equal(body, [
    ra.COMMENT_MARKER,
    'Auto-assigned to @alice.',
    '',
    '<details>',
    '<summary>Why this reviewer</summary>',
    '',
    '| Reviewer | Score | Ownership ×0.55 | Interest ×0.30 | Issue author ×0.15 | Load (30d) |',
    '|---|--:|--:|--:|:-:|--:|',
    '| **`alice`** | 0.85 | 1.00 | 1.00 |  | 0 |',
    '| `bob` | 0.27 | 0.00 | 0.40 | yes | 0 |',
    '',
    'Areas: `area/ui/editors` (primary), `area/rest`',
    'Not considered (no area interest, recent commits or linked issue): `carol`',
    '',
    `[How to adjust assignment](${LINKS.tuning}) · [Run log](${LINKS.run})`,
    '',
    '</details>',
  ].join('\n'));
});

test('formatComment: a swap is explained, with both loads', () => {
  const r = ra.rankCandidates(['alice', 'bob'], signals(OVERLOADED), SETTINGS);
  const body = ra.formatComment(r, SETTINGS, { suggestOnly: false, labels: ['area/rest'], primary: 'area/rest' });
  assert.match(body, /^Auto-assigned to @bob\.$/m);
  assert.match(body, /^\| \*\*`bob`\*\* \|/m);
  assert.match(body, /^Swapped: `alice` scored highest but has 9 assignments in the last 30 days against 6, so the runner-up got it\.$/m);
});

test('formatComment: a blocked swap says why the busy winner kept the PR', () => {
  const r = ra.rankCandidates(['alice', 'carol'], signals({
    ownership: { alice: 1, carol: 0.2 }, load: { alice: 9, carol: 1 }, primary: 'area/ui',
  }), SETTINGS);
  const body = ra.formatComment(r, SETTINGS, { suggestOnly: false, labels: ['area/ui'], primary: 'area/ui' });
  assert.match(body, /^Not swapped: `alice` has 9 assignments in the last 30 days against 1, but `carol` has no interest in the primary area\.$/m);
});

test('formatComment: a suggestion mentions nobody and says it was not assigned', () => {
  const r = ra.rankCandidates(['alice', 'bob'], signals({ interest: { alice: 1, bob: 0.4 } }), SETTINGS);
  const body = ra.formatComment(r, SETTINGS, { suggestOnly: true, labels: ['area/ui'], primary: 'area/ui' });
  assert.match(body, /^Suggested reviewer: `alice`\. Not assigned: maintainers choose their own reviewer\.$/m);
  assert.doesNotMatch(body, /@/);
});

test('formatComment: the fallback is called out, and a PR without labels says so', () => {
  const r = ra.rankCandidates(['alice'], signals({ labelled: false, primary: null }), SETTINGS);
  const body = ra.formatComment(r, SETTINGS, { suggestOnly: false, labels: [], primary: null });
  assert.match(body, /^Areas: none$/m);
  assert.match(body, /so everyone was considered\.$/m);
  assert.doesNotMatch(body, /Not considered/);
});

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

test('readSettings: no section means assignment is off', () => {
  assert.equal(ra.readSettings({ maintainers: [] }), null);
});

test('readSettings: a missing key fails loudly, naming the key', () => {
  const broken = structuredClone(SETTINGS);
  delete broken.weights.issue_author;
  delete broken.ownership.ignore_paths;
  assert.throws(() => ra.readSettings({ reviewer_assignment: broken }),
    /missing: weights\.issue_author, ownership\.ignore_paths/);
});

test('readClassification: selected labels minus suppressed ones, and the scores', t => {
  const fs = require('fs');
  const os = require('os');
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'ra-')), 'c.json');
  fs.writeFileSync(file, JSON.stringify({
    area_labels_selected: ['area/documentation', 'area/ui'],
    area_labels_suppressed: ['area/ui'],
    area_label_scores: { 'area/documentation': 0.41, 'area/ui': 0.52, bogus: 'x' },
  }));
  assert.deepEqual(ra.readClassification(file, makeWorld().core), {
    labels: ['area/documentation'],
    scores: { 'area/documentation': 0.41, 'area/ui': 0.52 },
  });
});

test('readClassification: a missing or broken file is empty, not fatal', () => {
  const fs = require('fs');
  const os = require('os');
  const w = makeWorld();
  const broken = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'ra-')), 'c.json');
  fs.writeFileSync(broken, '{not json');
  assert.deepEqual(ra.readClassification('/nonexistent/c.json', w.core), { labels: [], scores: {} });
  assert.deepEqual(ra.readClassification(broken, w.core), { labels: [], scores: {} });
  assert.equal(w.logs.warning.length, 1);
});

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

const UI_PR = {
  pr: { labels: [{ name: 'area/ui' }, { name: 'area/ui/editors' }] },
  files: [{ filename: 'ui/Editor.tsx', status: 'modified', changes: 10 }],
  commits: { 'ui/Editor.tsx': [['alice', daysAgo(10)]] },
};

function classificationFile(content) {
  const fs = require('fs');
  const os = require('os');
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'ra-')), 'c.json');
  fs.writeFileSync(file, JSON.stringify(content));
  return file;
}

test('assignReviewer: a contributor PR is assigned and the reasoning is posted', async () => {
  const w = makeWorld(UI_PR);
  w.context.runId = 7;
  const out = await run(w);

  assert.equal(out.winner, 'alice');
  assert.deepEqual(w.calls.assigned, ['alice']);
  assert.equal(w.calls.comments.length, 1);
  const body = w.calls.comments[0];
  assert.ok(body.startsWith(`${ra.COMMENT_MARKER}\nAuto-assigned to @alice.\n`));
  assert.match(body, /^\| \*\*`alice`\*\* \| 0\.85 \| 1\.00 \| 1\.00 \| {2}\| 0 \|$/m);
  assert.match(body, /^\| `bob` \| 0\.12 \| 0\.00 \| 0\.40 \| {2}\| 0 \|$/m);
  assert.match(body, /^Areas: `area\/ui\/editors`$/m);
  assert.match(body, /^Not considered \(no area interest, recent commits or linked issue\): `carol`$/m);
  assert.match(body, /\(https:\/\/github\.com\/Apicurio\/apicurio-registry\/blob\/main\/\.github\/PR_LIFECYCLE\.md#adjusting-which-prs-are-assigned-to-you\)/);
  assert.match(body, /\[Run log\]\(https:\/\/github\.com\/Apicurio\/apicurio-registry\/actions\/runs\/7\)/);
});

test('assignReviewer: a reviewer who opened the linked issue gets the issue-author signal', async () => {
  const w = makeWorld({ ...UI_PR, closingAuthors: ['carol'] });
  const out = await run(w);

  const carol = out.result.ranked.find(r => r.login === 'carol');
  assert.equal(carol.parts.issue_author, 0.15);
});

test('assignReviewer: labels and the primary area come from the classification output too', async () => {
  const file = classificationFile({
    area_labels_selected: ['area/documentation', 'area/ui'],
    area_labels_suppressed: ['area/ui'],
    area_label_scores: { 'area/documentation': 0.5, 'area/ui': 0.9 },
  });
  const w = makeWorld();
  const out = await run(w, { classificationPath: file });

  // area/ui was suppressed, so the only area is documentation, and it is primary.
  assert.equal(out.winner, 'carol');
  assert.equal(out.result.ranked.find(r => r.login === 'carol').interest, 1);
  assert.match(out.body, /^Areas: `area\/documentation` \(primary\)$/m);
});

test('assignReviewer: an overloaded winner is swapped for a runner-up in the primary area', async () => {
  const w = makeWorld({
    ...UI_PR,
    assignments: { alice: Array.from({ length: 6 }, () => ({ at: daysAgo(5) })), bob: [{ at: daysAgo(5) }] },
  });
  const out = await run(w, {
    classificationPath: classificationFile({ area_label_scores: { 'area/ui/editors': 0.6 } }),
  });

  // bob's area/* covers area/ui/editors; 6 >= 1.5 x 1 and 6 - 1 >= 3.
  assert.equal(out.winner, 'bob');
  assert.deepEqual(w.calls.assigned, ['bob']);
  assert.match(out.body, /^Swapped: `alice` scored highest but has 6 assignments/m);
});

test('assignReviewer: a PR that already has an assignee is left alone', async () => {
  const w = makeWorld({ ...UI_PR, pr: { ...UI_PR.pr, assignees: [{ login: 'bob' }] } });
  assert.equal(await run(w), null);
  assert.deepEqual(w.calls.assigned, []);
  assert.deepEqual(w.calls.comments, []);
});

test('assignReviewer: an earlier assignment comment stops a second run', async () => {
  const w = makeWorld({ ...UI_PR, comments: [`${ra.COMMENT_MARKER}\nAuto-assigned to @alice.`] });
  assert.equal(await run(w), null);
  assert.deepEqual(w.calls.assigned, []);
  assert.deepEqual(w.calls.comments, []);
});

test('assignReviewer: drafts and closed PRs are skipped', async () => {
  for (const pr of [{ draft: true }, { state: 'closed' }]) {
    const w = makeWorld({ ...UI_PR, pr });
    assert.equal(await run(w), null);
    assert.deepEqual(w.calls.assigned, []);
  }
});

test('assignReviewer: a maintainer PR gets a suggestion, not an assignment, and never its author', async () => {
  const w = makeWorld({ ...UI_PR, pr: { ...UI_PR.pr, user: { login: 'alice' } } });
  const out = await run(w);

  assert.equal(out.mode, 'suggest');
  assert.deepEqual(w.calls.assigned, []);
  assert.equal(w.calls.comments.length, 1);
  assert.match(w.calls.comments[0], /^Suggested reviewer: `bob`\./m);
  assert.ok(!out.result.ranked.some(r => r.login === 'alice'));
});

test('assignReviewer: a maintainer PR that already requested a review gets no suggestion', async () => {
  const w = makeWorld({ ...UI_PR, pr: { user: { login: 'alice' }, requested_reviewers: [{ login: 'bob' }] } });
  assert.equal(await run(w), null);
  assert.deepEqual(w.calls.comments, []);
});

test('assignReviewer: a dependency bot PR rotates to the least bot-loaded, with no comment', async () => {
  const w = makeWorld({
    pr: { user: { login: 'renovate[bot]' } },
    assignments: {
      alice: [{ bot: true, at: daysAgo(3) }, { bot: true, at: daysAgo(4) }],
      bob: [{ bot: true, at: daysAgo(5) }, { at: daysAgo(1) }, { at: daysAgo(2) }],
    },
  });
  const out = await run(w);

  // bob has more PRs in total, but only one bot PR: contributor PRs do not
  // count against the bot rotation.
  assert.equal(out.winner, 'bob');
  assert.deepEqual(w.calls.assigned, ['bob']);
  assert.deepEqual(w.calls.comments, []);
  assert.deepEqual(w.calls.listCommits, []);
});

test('assignReviewer: a bot rotation tie goes to whoever had a bot PR longest ago', async () => {
  const w = makeWorld({
    pr: { user: { login: 'renovate[bot]' } },
    assignments: { alice: [{ bot: true, at: daysAgo(10) }], bob: [{ bot: true, at: daysAgo(2) }] },
  });
  assert.equal((await run(w)).winner, 'alice');
});

test('assignReviewer: bot PRs do not count toward contributor load', async () => {
  const w = makeWorld({
    ...UI_PR,
    commits: {},
    assignments: { alice: Array.from({ length: 20 }, () => ({ bot: true, at: daysAgo(5) })) },
  });
  const out = await run(w);
  assert.equal(out.result.ranked.find(r => r.login === 'alice').load, 0);
});

test('assignReviewer: load counts recent assignments double and ignores old ones and self-assignment', async () => {
  const w = makeWorld({
    ...UI_PR,
    assignments: {
      alice: [
        { at: hoursAgo(5) },                 // 2
        { at: daysAgo(10) },                 // 1
        { at: daysAgo(40) },                 // outside the window
        { at: daysAgo(3), author: 'alice' }, // her own PR
      ],
    },
  });
  const out = await run(w);
  assert.equal(out.result.ranked.find(r => r.login === 'alice').load, 3);
});

test('assignReviewer: a dry run decides but writes nothing', async () => {
  const w = makeWorld(UI_PR);
  const out = await run(w, { dryRun: true });

  assert.equal(out.winner, 'alice');
  assert.deepEqual(w.calls.assigned, []);
  assert.deepEqual(w.calls.comments, []);
  assert.ok(w.logs.info.some(m => m.startsWith('[DRY RUN] would post:')));
});

test('assignReviewer: failed lookups degrade to no signal instead of failing', async () => {
  const w = makeWorld({ ...UI_PR, fail: { commits: ['ui/Editor.tsx'], closing: true, loads: true } });
  const out = await run(w);

  // Interest alone still decides.
  assert.equal(out.winner, 'alice');
  assert.equal(out.result.winner.ownership, 0);
  assert.equal(w.logs.warning.length, 3);
});

test('assignReviewer: a bot rotation without loads assigns nobody rather than always the same person', async () => {
  const w = makeWorld({ pr: { user: { login: 'renovate[bot]' } }, fail: { loads: true } });
  assert.equal(await run(w), null);
  assert.deepEqual(w.calls.assigned, []);
});

test('assignReviewer: a configured reviewer who is not a maintainer is ignored', async () => {
  const config = structuredClone(CONFIG);
  config.reviewer_assignment.reviewers.mallory = { interest: { 'area/*': 1 } };
  const w = makeWorld(UI_PR);
  const out = await run(w, { config });

  assert.ok(!out.result.ranked.some(r => r.login === 'mallory'));
  assert.ok(!out.result.notConsidered.includes('mallory'));
  assert.ok(w.logs.warning.some(m => m.includes('mallory')));
});

// ---------------------------------------------------------------------------
// The shipped configuration
// ---------------------------------------------------------------------------

// The YAML files are read through yq, which the workflows already use to turn
// pr-lifecycle.yml into JSON — no YAML parser needs installing for Node.
function readYaml(t, file) {
  try {
    return JSON.parse(execFileSync('yq', ['-o', 'json', file], { encoding: 'utf8' }));
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
    if (process.env.CI) assert.fail('yq is required to check the shipped configuration');
    t.skip('yq is not installed');
    return null;
  }
}

function shipped(t) {
  const lifecycle = readYaml(t, path.join(__dirname, '..', 'pr-lifecycle.yml'));
  const labels = readYaml(t, path.join(__dirname, 'label-classification', 'label-descriptions.yml'));
  if (!lifecycle || !labels) return null;
  return { config: lifecycle, settings: lifecycle.reviewer_assignment, areaLabels: Object.keys(labels.area_labels.labels) };
}

test('shipped config: reviewer_assignment is complete', t => {
  const s = shipped(t);
  if (!s) return;
  assert.ok(ra.readSettings(s.config));
});

test('shipped config: the signal weights add up to 1', t => {
  const s = shipped(t);
  if (!s) return;
  const sum = Object.values(s.settings.weights).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(sum - 1) < 1e-9, `weights add up to ${sum}`);
});

test('shipped config: every reviewer and bot rotation reviewer is a maintainer', t => {
  const s = shipped(t);
  if (!s) return;
  for (const login of [...Object.keys(s.settings.reviewers), ...s.settings.bot_rotation.reviewers]) {
    assert.ok(s.config.maintainers.includes(login), `${login} is not in maintainers`);
  }
});

test('shipped config: every interest pattern names a configured label and a 0-1 weight', t => {
  const s = shipped(t);
  if (!s) return;
  const known = new Set(s.areaLabels.map(l => l.toLowerCase()));
  for (const [login, reviewer] of Object.entries(s.settings.reviewers)) {
    for (const [pattern, weight] of Object.entries(reviewer.interest)) {
      const base = pattern.endsWith('/*') ? pattern.slice(0, -2) : pattern;
      assert.ok(base === 'area' || known.has(base.toLowerCase()),
        `${login}: ${pattern} does not match any configured label`);
      assert.ok(weight >= 0 && weight <= 1, `${login}: ${pattern} has weight ${weight}`);
    }
  }
});

test('shipped config: every area label has at least one interested reviewer', t => {
  const s = shipped(t);
  if (!s) return;
  const uncovered = s.areaLabels.filter(label => !Object.values(s.settings.reviewers)
    .some(r => (ra.interestWeight(r.interest, label) ?? 0) > 0));
  assert.deepEqual(uncovered, []);
});
