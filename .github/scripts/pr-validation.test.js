// Unit tests for the pure functions in pr-validation.js. Run with:
//   node --test .github/scripts/pr-validation.test.js

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const {
  validate,
  revalidateForIssue,
  stripCode,
  extractLinkedIssues,
  hasSignOff,
  checkIssueLink,
  checkDcoSignOff,
  checkMilestone,
} = require('./pr-validation.js');

const OWNER = 'Apicurio';
const REPO = 'apicurio-registry';
const VALIDATION_FAILED_LABEL = 'lifecycle/validation-failed';

test('extractLinkedIssues: closing keyword', () => {
  const issues = extractLinkedIssues('Closes #123', OWNER, REPO);
  assert.deepEqual([...issues], [123]);
});

test('extractLinkedIssues: multiple keywords in one body', () => {
  const issues = extractLinkedIssues('Fixes #45 and Resolves #67', OWNER, REPO);
  assert.deepEqual([...issues].sort(), [45, 67]);
});

test('extractLinkedIssues: keyword matching is case-insensitive', () => {
  const issues = extractLinkedIssues('FIXED #12', OWNER, REPO);
  assert.deepEqual([...issues], [12]);
});

test('extractLinkedIssues: bare "#123" without a closing keyword is not linked', () => {
  const issues = extractLinkedIssues('See #123 for background.', OWNER, REPO);
  assert.deepEqual([...issues], []);
});

test('extractLinkedIssues: full issue URL for this repo', () => {
  const body = 'Closes https://github.com/Apicurio/apicurio-registry/issues/999';
  const issues = extractLinkedIssues(body, OWNER, REPO);
  assert.deepEqual([...issues], [999]);
});

test('extractLinkedIssues: full issue URL for a different repo does not count', () => {
  const body = 'Closes https://github.com/other/repo/issues/999';
  const issues = extractLinkedIssues(body, OWNER, REPO);
  assert.deepEqual([...issues], []);
});

test('extractLinkedIssues: owner/repo match is case-insensitive', () => {
  const body = 'Closes https://github.com/apicurio/APICURIO-REGISTRY/issues/7';
  const issues = extractLinkedIssues(body, OWNER, REPO);
  assert.deepEqual([...issues], [7]);
});

test('extractLinkedIssues: keyword and URL for the same issue dedupe', () => {
  const body = 'Closes #7\n\nAlso see https://github.com/Apicurio/apicurio-registry/issues/7';
  const issues = extractLinkedIssues(body, OWNER, REPO);
  assert.deepEqual([...issues], [7]);
});

test('extractLinkedIssues: null body yields an empty set', () => {
  const issues = extractLinkedIssues(null, OWNER, REPO);
  assert.deepEqual([...issues], []);
});

// hasSignOff() takes a commit API object: { commit: { message, author, committer } }.
const commitWith = (message, authorEmail, committerEmail = authorEmail) => ({
  commit: {
    message,
    author: authorEmail ? { email: authorEmail } : null,
    committer: committerEmail ? { email: committerEmail } : null,
  },
});

test('hasSignOff: trailer email matches the commit author', () => {
  const commit = commitWith(
    'fix(core): thing\n\nSigned-off-by: Jane Doe <jane@example.com>', 'jane@example.com'
  );
  assert.equal(hasSignOff(commit), true);
});

test('hasSignOff: message without a trailer', () => {
  const commit = commitWith('fix(core): thing', 'jane@example.com');
  assert.equal(hasSignOff(commit), false);
});

test('hasSignOff: trailer line with leading whitespace still counts', () => {
  const commit = commitWith(
    'fix(core): thing\n   Signed-off-by: Jane Doe <jane@example.com>', 'jane@example.com'
  );
  assert.equal(hasSignOff(commit), true);
});

test('hasSignOff: trailer missing an email is not a valid sign-off', () => {
  const commit = commitWith('fix(core): thing\n\nSigned-off-by: Jane Doe', 'jane@example.com');
  assert.equal(hasSignOff(commit), false);
});

test('hasSignOff: trailer email matching the committer (not the author) still counts', () => {
  const commit = commitWith(
    'fix(core): thing\n\nSigned-off-by: Jane Doe <jane@example.com>', 'author@example.com', 'jane@example.com'
  );
  assert.equal(hasSignOff(commit), true);
});

test('hasSignOff: a forged trailer with an unrelated email does not count', () => {
  const commit = commitWith(
    'fix(core): thing\n\nSigned-off-by: Someone Else <someone-else@example.com>', 'jane@example.com'
  );
  assert.equal(hasSignOff(commit), false);
});

test('hasSignOff: trailer email match is case-insensitive', () => {
  const commit = commitWith(
    'fix(core): thing\n\nSigned-off-by: Jane Doe <JANE@EXAMPLE.COM>', 'jane@example.com'
  );
  assert.equal(hasSignOff(commit), true);
});

test('checkIssueLink: returns null when at least one issue is linked', () => {
  assert.equal(checkIssueLink(new Set([123])), null);
});

test('checkIssueLink: returns a violation named "Issue link" when nothing is linked', () => {
  const violation = checkIssueLink(new Set());
  assert.equal(violation.name, 'Issue link');
});

test('checkDcoSignOff: returns null when every commit is signed off by its own author', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', ...commitWith('fix: a\n\nSigned-off-by: A <a@example.com>', 'a@example.com') },
    { sha: 'bbbbbbbb2222', ...commitWith('fix: b\n\nSigned-off-by: B <b@example.com>', 'b@example.com') },
  ];
  assert.equal(checkDcoSignOff(commits), null);
});

test('checkDcoSignOff: flags the exact unsigned commits and reports their count', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', ...commitWith('fix: a\n\nSigned-off-by: A <a@example.com>', 'a@example.com') },
    { sha: 'bbbbbbbb2222', ...commitWith('fix: b', 'b@example.com') },
  ];
  const violation = checkDcoSignOff(commits);
  assert.equal(violation.name, 'DCO sign-off');
  assert.match(violation.detail, /1 commit\(s\)/);
  assert.match(violation.detail, /`bbbbbbbb`/);
  assert.doesNotMatch(violation.detail, /`aaaaaaaa`/);
});

test('checkDcoSignOff: a trailer copied from a different commit does not satisfy the check', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', ...commitWith('fix: a\n\nSigned-off-by: A <a@example.com>', 'b@example.com') },
  ];
  const violation = checkDcoSignOff(commits);
  assert.notEqual(violation, null);
  assert.match(violation.detail, /`aaaaaaaa`/);
});

test('checkDcoSignOff: an unsigned merge commit is exempt', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', ...commitWith('fix: a\n\nSigned-off-by: A <a@example.com>', 'a@example.com') },
    {
      sha: 'bbbbbbbb2222',
      parents: [{ sha: 'aaaaaaaa1111' }, { sha: 'cccccccc3333' }],
      ...commitWith("Merge branch 'main' into fix/thing", 'a@example.com'),
    },
  ];
  assert.equal(checkDcoSignOff(commits), null);
});

test('checkDcoSignOff: the merge exemption does not cover single-parent commits', () => {
  const commits = [
    { sha: 'bbbbbbbb2222', parents: [{ sha: 'aaaaaaaa1111' }], ...commitWith('fix: b', 'b@example.com') },
  ];
  const violation = checkDcoSignOff(commits);
  assert.equal(violation.name, 'DCO sign-off');
  assert.match(violation.detail, /1 commit\(s\)/);
  assert.match(violation.detail, /`bbbbbbbb`/);
});

// ---------------------------------------------------------------------------
// validate(): mocked github/core, no network access.
//
// loadConfig() reads .github/pr-lifecycle.json from disk via fs.readFileSync;
// each test stubs that call with node:test's built-in mock (auto-restored
// when the test ends) instead of depending on a real file on disk.
// ---------------------------------------------------------------------------

function stubConfig(t, config) {
  t.mock.method(fs, 'readFileSync', () => JSON.stringify(config));
}

const SIGNED_COMMIT = (sha, subject) => (
  { sha, ...commitWith(`${subject}\n\nSigned-off-by: Dev <dev@example.com>`, 'dev@example.com') }
);
const UNSIGNED_COMMIT = (sha, subject) => ({ sha, ...commitWith(subject, 'dev@example.com') });

// Most tests are not about milestones, so PRs and linked issues carry an open
// one by default and the milestone check stays quiet. Tests that do care pass
// `milestone: null` on the PR fixture or seed `issuesByNumber`.
const OPEN_MILESTONE = { title: '3.4.0', state: 'open' };

// validate() reads the PR back with pulls.get rather than trusting the event
// payload. makeContext() registers the PR it builds here, and the fake's
// pulls.get serves it.
const CURRENT_PRS = new Map();
const CLOSED_MILESTONE = { title: '3.3.3', state: 'closed' };

/**
 * Fake octokit client covering only the calls pr-validation.js makes.
 * `commitsByPr` and `filesByPr` are keyed by PR number; `openPrs` seeds
 * github.rest.pulls.list; `issuesByNumber` seeds github.rest.issues.get for
 * the milestone check. Every write call is recorded onto `calls` so tests can
 * assert on exact arguments.
 */
function createFakeGithub({ commitsByPr = {}, openPrs = [], filesByPr = {},
                            issuesByNumber = {}, validationRunsBySha = {},
                            reRunError = null } = {}) {
  const calls = {
    createdComments: [], updatedComments: [], addedLabels: [], removedLabels: [], listParams: [],
    runLookups: [], reRuns: [],
  };
  const comments = [];
  const knownLabels = new Set();
  let nextCommentId = 1;

  const github = {
    paginate: async (fn, params) => (await fn(params)).data,
    rest: {
      pulls: {
        get: async ({ pull_number }) => ({ data: CURRENT_PRS.get(pull_number) }),
        listCommits: async ({ pull_number }) => ({ data: commitsByPr[pull_number] || [] }),
        list: async ({ base }) => {
          calls.listParams.push({ base });
          return { data: withDefaultBase(openPrs).filter(p => base === undefined || p.base.ref === base) };
        },
        listFiles: async ({ pull_number }) => (
          { data: (filesByPr[pull_number] || []).map(filename => ({ filename })) }
        ),
      },
      actions: {
        listWorkflowRuns: async ({ workflow_id, head_sha }) => {
          calls.runLookups.push({ workflow_id, head_sha });
          return { data: { workflow_runs: validationRunsBySha[head_sha] || [] } };
        },
        reRunWorkflow: async ({ run_id }) => {
          if (reRunError) throw reRunError;
          calls.reRuns.push(run_id);
        },
      },
      issues: {
        get: async ({ issue_number }) => {
          const issue = issuesByNumber[issue_number];
          if (issue instanceof Error) throw issue;
          return { data: issue ?? { number: issue_number, milestone: OPEN_MILESTONE } };
        },
        listComments: async ({ issue_number }) => (
          { data: comments.filter(c => c.issue_number === issue_number) }
        ),
        createComment: async ({ issue_number, body }) => {
          const comment = { id: nextCommentId++, issue_number, body, user: { login: 'github-actions[bot]' } };
          comments.push(comment);
          calls.createdComments.push(comment);
          return { data: comment };
        },
        updateComment: async ({ comment_id, body }) => {
          const comment = comments.find(c => c.id === comment_id);
          comment.body = body;
          calls.updatedComments.push(comment);
          return { data: comment };
        },
        getLabel: async ({ name }) => {
          if (!knownLabels.has(name)) {
            const notFound = new Error('Not Found');
            notFound.status = 404;
            throw notFound;
          }
          return { data: { name } };
        },
        createLabel: async ({ name }) => {
          knownLabels.add(name);
          return { data: { name } };
        },
        addLabels: async ({ issue_number, labels }) => {
          calls.addedLabels.push({ issue_number, labels });
          return { data: [] };
        },
        removeLabel: async ({ issue_number, name }) => {
          calls.removedLabels.push({ issue_number, name });
          return { data: [] };
        },
      },
    },
  };
  return { github, calls };
}

function createFakeCore() {
  const info = [];
  const warnings = [];
  let failedMessage = null;
  return {
    core: {
      info: msg => info.push(msg),
      warning: msg => warnings.push(msg),
      setFailed: msg => { failedMessage = msg; },
    },
    info,
    warnings,
    getFailed: () => failedMessage,
  };
}

// Real pull_request payloads always carry base.ref; fixtures that do not care
// which branch they target inherit main so every test does not have to say so.
function withDefaultBase(prs) {
  return prs.map(p => ({ base: { ref: 'main' }, ...p }));
}

function makeContext(pr) {
  const [withBase] = withDefaultBase([pr]);
  // Same idea as base.ref above: default to a milestoned PR so tests that are
  // not about milestones do not all have to say so.
  const current = { milestone: OPEN_MILESTONE, ...withBase };
  CURRENT_PRS.set(current.number, current);
  return {
    repo: { owner: OWNER, repo: REPO },
    payload: { pull_request: current },
  };
}

test('validate(): unsigned commit fails the check, labels the PR, and posts one comment', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a'), UNSIGNED_COMMIT('bbbbbbbb2222', 'fix: b')] },
  });
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.match(getFailed(), /DCO sign-off/);
  assert.equal(calls.createdComments.length, 1);
  assert.match(calls.createdComments[0].body, /DCO sign-off/);
  assert.deepEqual(calls.addedLabels, [{ issue_number: 1, labels: [VALIDATION_FAILED_LABEL] }]);
  assert.deepEqual(calls.removedLabels, []);
});

test('validate(): clean PR passes and removes a stale failure label', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = {
    number: 1, user: { login: 'contributor' }, body: 'Closes #42',
    labels: [{ name: VALIDATION_FAILED_LABEL }], draft: false,
  };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
  });
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.equal(getFailed(), null);
  assert.equal(calls.createdComments[0].body.includes('All validation checks passed.'), true);
  assert.deepEqual(calls.removedLabels, [{ issue_number: 1, name: VALIDATION_FAILED_LABEL }]);
  assert.deepEqual(calls.addedLabels, []);
});

test('validate(): a second PR linking the same issue gets an advisory comment', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const otherPr = { number: 2, user: { login: 'other-dev' }, body: 'Fixes #42', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [otherPr],
    filesByPr: { 1: ['a.js'], 2: ['b.js'] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const advisory = calls.createdComments.find(c => c.issue_number === 2);
  assert.notEqual(advisory, undefined);
  assert.match(advisory.body, /links the same issue \(#42\)/);
  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.match(ownComment.body, /#2 by @other-dev also links #42/);
});

test('validate(): a PR targeting another branch is not a duplicate', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  // Same issue, same files, but a backport to a release branch: intentional, not a duplicate.
  const backport = {
    number: 2, user: { login: 'other-dev' }, body: 'Fixes #42', labels: [], draft: false,
    base: { ref: '2.6.x' },
  };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [backport],
    filesByPr: { 1: ['a.js', 'b.js'], 2: ['a.js', 'b.js'] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.deepEqual(calls.listParams, [{ base: 'main' }]);
  assert.equal(calls.createdComments.some(c => c.issue_number === 2), false);
  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.match(ownComment.body, /All validation checks passed./);
});

test('validate(): two shared files trigger a file-based duplicate advisory', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #1', labels: [], draft: false };
  const otherPr = { number: 2, user: { login: 'other-dev' }, body: 'Closes #2', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [otherPr],
    filesByPr: { 1: ['a.js', 'b.js', 'c.js'], 2: ['a.js', 'b.js', 'd.js'] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.match(ownComment.body, /#2 by @other-dev also changes `a\.js`, `b\.js`/);
});

test('validate(): a single shared file is not enough to trigger a duplicate advisory', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #1', labels: [], draft: false };
  const otherPr = { number: 2, user: { login: 'other-dev' }, body: 'Closes #2', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [otherPr],
    filesByPr: { 1: ['pom.xml'], 2: ['pom.xml'] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.doesNotMatch(ownComment.body, /Possible duplicate/);
  assert.equal(calls.createdComments.length, 1);
});

test('validate(): more than 40 open PRs skips file-based detection but issue-based still runs', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const otherPr = { number: 2, user: { login: 'other-dev' }, body: 'Fixes #42', labels: [], draft: false };
  const filler = Array.from({ length: 40 }, (_, i) => (
    { number: 100 + i, user: { login: `filler-${i}` }, body: '', labels: [], draft: false }
  ));
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [otherPr, ...filler],
    filesByPr: { 1: ['a.js', 'b.js'], 2: ['a.js', 'b.js'] },
  });
  const { core, info } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.match(ownComment.body, /#2 by @other-dev also links #42/);
  assert.doesNotMatch(ownComment.body, /also changes/);
  assert.equal(info.some(m => m.includes('Skipping file-based duplicate detection')), true);
});

test('validate(): exempt authors are skipped entirely, no API writes happen', async (t) => {
  stubConfig(t, { auto_accept: ['dependabot[bot]'] });
  const pr = { number: 1, user: { login: 'dependabot[bot]' }, body: '', labels: [], draft: false };
  const { github, calls } = createFakeGithub();
  const { core, info, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.equal(getFailed(), null);
  assert.deepEqual(calls.createdComments, []);
  assert.deepEqual(calls.addedLabels, []);
  assert.deepEqual(info, ['Skipping validation for exempt author dependabot[bot]']);
});

test('validate(): an exempt/bot author is excluded from duplicate comparison', async (t) => {
  stubConfig(t, { auto_accept: ['dependabot[bot]'] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #1', labels: [], draft: false };
  const botPr = { number: 2, user: { login: 'dependabot[bot]' }, body: '', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    openPrs: [botPr],
    filesByPr: { 1: ['pom.xml', 'pom2.xml'], 2: ['pom.xml', 'pom2.xml'] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const ownComment = calls.createdComments.find(c => c.issue_number === 1);
  assert.doesNotMatch(ownComment.body, /Possible duplicate/);
});

test('validate(): a duplicate-detection failure does not fail a clean PR', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
  });
  github.rest.pulls.list = async () => { throw new Error('API rate limit exceeded'); };
  const { core, warnings, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.equal(getFailed(), null);
  assert.equal(calls.createdComments[0].body.includes('All validation checks passed.'), true);
  assert.equal(warnings.some(m => m.includes('Duplicate detection failed')), true);
});

test('validate(): a duplicate-detection failure still reports a real violation', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: '', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [UNSIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
  });
  github.rest.pulls.list = async () => { throw new Error('API rate limit exceeded'); };
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.match(getFailed(), /Issue link/);
  assert.match(getFailed(), /DCO sign-off/);
  assert.match(calls.createdComments[0].body, /Issue link/);
});

// ---------------------------------------------------------------------------
// Milestone check
//
// Both the PR and every issue it closes need an open milestone: the release
// notes are generated from a milestone's issues, and the PR's own milestone
// keeps the work findable by search. Unlike the other blocking checks this
// one is not the author's to fix, which is why the message says who must act.
// ---------------------------------------------------------------------------

test('checkMilestone(): passes when the PR and its issues are milestoned', () => {
  const violation = checkMilestone(
    { milestone: OPEN_MILESTONE },
    [{ number: 42, milestone: OPEN_MILESTONE }]
  );
  assert.equal(violation, null);
});

test('checkMilestone(): reports a PR with no milestone', () => {
  const violation = checkMilestone({ milestone: null }, []);
  assert.match(violation.detail, /This PR has no milestone/);
  assert.match(violation.detail, /a maintainer has to do this/);
});

test('checkMilestone(): a closed milestone counts as missing', () => {
  const violation = checkMilestone({ milestone: CLOSED_MILESTONE }, []);
  assert.match(violation.detail, /`3\.3\.3`, which is closed/);
});

test('checkMilestone(): reports each unmilestoned issue separately', () => {
  const violation = checkMilestone({ milestone: OPEN_MILESTONE }, [
    { number: 42, milestone: null },
    { number: 43, milestone: CLOSED_MILESTONE },
    { number: 44, milestone: OPEN_MILESTONE },
  ]);
  assert.match(violation.detail, /Issue #42 has no milestone/);
  assert.match(violation.detail, /Issue #43 is on milestone/);
  assert.doesNotMatch(violation.detail, /Issue #44/);
});

test('validate(): an unmilestoned PR fails the check and is labelled', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = {
    number: 1, user: { login: 'contributor' }, body: 'Closes #42',
    labels: [], draft: false, milestone: null,
  };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
  });
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.match(getFailed(), /Milestone/);
  assert.deepEqual(calls.addedLabels, [{ issue_number: 1, labels: ['lifecycle/validation-failed'] }]);
  assert.match(calls.createdComments[0].body, /This PR has no milestone/);
});

test('validate(): a milestoned PR closing an unmilestoned issue still fails', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    issuesByNumber: { 42: { number: 42, milestone: null } },
  });
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.match(getFailed(), /Milestone/);
  assert.match(calls.createdComments[0].body, /Issue #42 has no milestone/);
});

test('validate(): an issue that cannot be fetched is warned about, not failed', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  const { github } = createFakeGithub({
    commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] },
    issuesByNumber: { 42: new Error('Not Found') },
  });
  const { core, warnings, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.equal(getFailed(), null);
  assert.ok(warnings.some(w => /Could not load issue #42/.test(w)));
});

test('validate(): exempt bot authors skip the milestone check entirely', async (t) => {
  stubConfig(t, { auto_accept: ['renovate[bot]'] });
  const pr = {
    number: 1, user: { login: 'renovate[bot]' }, body: '',
    labels: [], draft: false, milestone: null,
  };
  const { github, calls } = createFakeGithub({});
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.equal(getFailed(), null);
  assert.deepEqual(calls.addedLabels, []);
  assert.deepEqual(calls.createdComments, []);
});

// ---------------------------------------------------------------------------
// Closing keywords quoted in code don't link (#10101): explaining why a PR no
// longer says `Fixes #7317` must not link #7317 again.
// ---------------------------------------------------------------------------

test('extractLinkedIssues: a closing keyword in inline code is not a link', () => {
  const issues = extractLinkedIssues('Closes #10100\n\nWas linked as `Fixes #7317`, see above.', OWNER, REPO);
  assert.deepEqual([...issues], [10100]);
});

test('extractLinkedIssues: a closing keyword in a fenced block (``` or ~~~) is not a link', () => {
  const body = 'Closes #1\n\n```\nFixes #2\n```\n\n~~~text\nResolves #3\n~~~\n\nResolves #4';
  assert.deepEqual([...extractLinkedIssues(body, OWNER, REPO)].sort(), [1, 4]);
});

test('extractLinkedIssues: a double-backtick span is code too', () => {
  assert.deepEqual([...extractLinkedIssues('Closes #5, not ``Fixes #6``', OWNER, REPO)], [5]);
});

test('stripCode: leaves prose alone', () => {
  assert.equal(stripCode('Closes #1 and Fixes #2'), 'Closes #1 and Fixes #2');
});

// ---------------------------------------------------------------------------
// A closed linked issue's milestone is history (#10101)
// ---------------------------------------------------------------------------

test('checkMilestone(): a closed linked issue on a closed milestone is not a violation', () => {
  const violation = checkMilestone({ milestone: OPEN_MILESTONE }, [
    { number: 7317, state: 'closed', milestone: CLOSED_MILESTONE },
    { number: 7318, state: 'closed', milestone: null },
  ]);
  assert.equal(violation, null);
});

test('checkMilestone(): an open linked issue on a closed milestone is still a violation', () => {
  const violation = checkMilestone({ milestone: OPEN_MILESTONE }, [
    { number: 42, state: 'open', milestone: CLOSED_MILESTONE },
  ]);
  assert.match(violation.detail, /Issue #42 is on milestone `3\.3\.3`, which is closed/);
});

test('checkMilestone(): the PR itself still needs an open milestone when its issues are closed', () => {
  const violation = checkMilestone({ milestone: CLOSED_MILESTONE }, [
    { number: 7317, state: 'closed', milestone: CLOSED_MILESTONE },
  ]);
  assert.match(violation.detail, /This PR is on milestone `3\.3\.3`/);
  assert.doesNotMatch(violation.detail, /#7317/);
});

// ---------------------------------------------------------------------------
// The comment says who has to act (#10179)
// ---------------------------------------------------------------------------

test('validate(): violations are grouped by who can fix them, author first', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false, milestone: null };
  const { github, calls } = createFakeGithub({
    commitsByPr: { 1: [UNSIGNED_COMMIT('bbbbbbbb2222', 'fix: b')] },
  });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  const body = calls.createdComments[0].body;
  const author = body.indexOf('### For the author');
  const dco = body.indexOf('#### DCO sign-off');
  const maintainer = body.indexOf('### For a maintainer');
  const milestone = body.indexOf('#### Milestone');
  assert.ok(author >= 0 && dco > author && maintainer > dco && milestone > maintainer,
    'author section (with DCO) comes first, then the maintainer section (with Milestone)');
  assert.match(body, /comment `\/retry`/);
});

test('validate(): a PR blocked only on a maintainer has no author section', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const pr = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false, milestone: null };
  const { github, calls } = createFakeGithub({ commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] } });
  const { core } = createFakeCore();

  await validate({ github, context: makeContext(pr), core });

  assert.doesNotMatch(calls.createdComments[0].body, /For the author/);
  assert.match(calls.createdComments[0].body, /### For a maintainer/);
});

test('validate(): reads the PR back instead of trusting a stale event payload', async (t) => {
  // A re-run replays the original event: here it still says "no milestone",
  // but a maintainer has set one since.
  stubConfig(t, { auto_accept: [] });
  const current = { number: 1, user: { login: 'contributor' }, body: 'Closes #42', labels: [], draft: false };
  makeContext(current);
  const stale = { repo: { owner: OWNER, repo: REPO }, payload: { pull_request: { ...current, milestone: null } } };
  const { github } = createFakeGithub({ commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] } });
  const { core, getFailed } = createFakeCore();

  await validate({ github, context: stale, core });

  assert.equal(getFailed(), null);
});

// ---------------------------------------------------------------------------
// A linked issue's milestone changing re-runs the validation of the PRs that
// close it (#10101)
// ---------------------------------------------------------------------------

function issueEvent(number, { pullRequest = false } = {}) {
  const issue = { number };
  if (pullRequest) issue.pull_request = {};
  return { repo: { owner: OWNER, repo: REPO }, payload: { issue } };
}

const linkingPr = (number, body, sha) => ({
  number, body, head: { sha }, user: { login: 'contributor' }, labels: [], draft: false,
});

test('revalidateForIssue(): re-runs the latest validation run of each open PR closing the issue', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const { github, calls } = createFakeGithub({
    openPrs: [
      linkingPr(1, 'Closes #42', 'sha1'),
      linkingPr(2, 'Fixes #99', 'sha2'),
      linkingPr(3, 'Mentions `Closes #42` in code only', 'sha3'),
      linkingPr(4, 'Resolves #42', 'sha4'),
    ],
    validationRunsBySha: {
      sha1: [{ id: 101, status: 'completed', conclusion: 'failure' }],
      sha4: [{ id: 104, status: 'completed', conclusion: 'success' }],
    },
  });
  const { core } = createFakeCore();

  await revalidateForIssue({ github, context: issueEvent(42), core });

  assert.deepEqual(calls.runLookups, [
    { workflow_id: 'pr-validation.yml', head_sha: 'sha1' },
    { workflow_id: 'pr-validation.yml', head_sha: 'sha4' },
  ]);
  assert.deepEqual(calls.reRuns, [101, 104]);
  assert.deepEqual(calls.createdComments, [], 're-runs post their own comments');
});

test('revalidateForIssue(): a validation run still in progress is left alone', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const { github, calls } = createFakeGithub({
    openPrs: [linkingPr(1, 'Closes #42', 'sha1')],
    validationRunsBySha: { sha1: [{ id: 101, status: 'in_progress', conclusion: null }] },
  });
  const { core } = createFakeCore();

  await revalidateForIssue({ github, context: issueEvent(42), core });

  assert.deepEqual(calls.reRuns, []);
  assert.deepEqual(calls.createdComments, []);
});

test('revalidateForIssue(): without a run to re-run, validates directly so the comment and label are right', async (t) => {
  stubConfig(t, { auto_accept: [] });
  for (const [label, options] of [
    ['no run', {}],
    ['re-run refused (older than 30 days)', {
      validationRunsBySha: { sha1: [{ id: 101, status: 'completed', conclusion: 'failure' }] },
      reRunError: Object.assign(new Error('Unable to re-run this workflow run'), { status: 403 }),
    }],
  ]) {
    const pr = { ...linkingPr(1, 'Closes #42', 'sha1'), milestone: OPEN_MILESTONE };
    makeContext(pr);
    const { github, calls } = createFakeGithub({
      openPrs: [pr], commitsByPr: { 1: [SIGNED_COMMIT('aaaaaaaa1111', 'fix: a')] }, ...options,
    });
    const { core } = createFakeCore();

    await revalidateForIssue({ github, context: issueEvent(42), core });

    assert.equal(calls.createdComments.length, 1, label);
    assert.match(calls.createdComments[0].body, /All validation checks passed/, label);
  }
});

test('revalidateForIssue(): a milestone change on a PR (also an issues event) is ignored', async (t) => {
  stubConfig(t, { auto_accept: [] });
  const { github, calls } = createFakeGithub({
    openPrs: [linkingPr(1, 'Closes #42', 'sha1')],
    validationRunsBySha: { sha1: [{ id: 101, status: 'completed', conclusion: 'failure' }] },
  });
  const { core } = createFakeCore();

  await revalidateForIssue({ github, context: issueEvent(42, { pullRequest: true }), core });

  assert.deepEqual(calls.listParams, []);
  assert.deepEqual(calls.reRuns, []);
});

