// Unit tests for the pure functions in pr-validation.js. Run with:
//   node --test .github/scripts/pr-validation.test.js

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const {
  validate,
  extractLinkedIssues,
  hasSignOff,
  checkIssueLink,
  checkDcoSignOff,
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

test('hasSignOff: message with a trailing Signed-off-by trailer', () => {
  assert.equal(hasSignOff('fix(core): thing\n\nSigned-off-by: Jane Doe <jane@example.com>'), true);
});

test('hasSignOff: message without a trailer', () => {
  assert.equal(hasSignOff('fix(core): thing'), false);
});

test('hasSignOff: trailer line with leading whitespace still counts', () => {
  assert.equal(hasSignOff('fix(core): thing\n   Signed-off-by: Jane Doe <jane@example.com>'), true);
});

test('hasSignOff: trailer missing an email is not a valid sign-off', () => {
  assert.equal(hasSignOff('fix(core): thing\n\nSigned-off-by: Jane Doe'), false);
});

test('checkIssueLink: returns null when at least one issue is linked', () => {
  assert.equal(checkIssueLink(new Set([123])), null);
});

test('checkIssueLink: returns a violation named "Issue link" when nothing is linked', () => {
  const violation = checkIssueLink(new Set());
  assert.equal(violation.name, 'Issue link');
});

test('checkDcoSignOff: returns null when every commit is signed off', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', commit: { message: 'fix: a\n\nSigned-off-by: A <a@example.com>' } },
    { sha: 'bbbbbbbb2222', commit: { message: 'fix: b\n\nSigned-off-by: B <b@example.com>' } },
  ];
  assert.equal(checkDcoSignOff(commits), null);
});

test('checkDcoSignOff: flags the exact unsigned commits and reports their count', () => {
  const commits = [
    { sha: 'aaaaaaaa1111', commit: { message: 'fix: a\n\nSigned-off-by: A <a@example.com>' } },
    { sha: 'bbbbbbbb2222', commit: { message: 'fix: b' } },
  ];
  const violation = checkDcoSignOff(commits);
  assert.equal(violation.name, 'DCO sign-off');
  assert.match(violation.detail, /1 commit\(s\)/);
  assert.match(violation.detail, /`bbbbbbbb`/);
  assert.doesNotMatch(violation.detail, /`aaaaaaaa`/);
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
  { sha, commit: { message: `${subject}\n\nSigned-off-by: Dev <dev@example.com>` } }
);
const UNSIGNED_COMMIT = (sha, subject) => ({ sha, commit: { message: subject } });

/**
 * Fake octokit client covering only the calls pr-validation.js makes.
 * `commitsByPr` and `filesByPr` are keyed by PR number; `openPrs` seeds
 * github.rest.pulls.list. Every write call is recorded onto `calls` so
 * tests can assert on exact arguments.
 */
function createFakeGithub({ commitsByPr = {}, openPrs = [], filesByPr = {} } = {}) {
  const calls = { createdComments: [], updatedComments: [], addedLabels: [], removedLabels: [] };
  const comments = [];
  const knownLabels = new Set();
  let nextCommentId = 1;

  const github = {
    paginate: async (fn, params) => (await fn(params)).data,
    rest: {
      pulls: {
        listCommits: async ({ pull_number }) => ({ data: commitsByPr[pull_number] || [] }),
        list: async () => ({ data: openPrs }),
        listFiles: async ({ pull_number }) => (
          { data: (filesByPr[pull_number] || []).map(filename => ({ filename })) }
        ),
      },
      issues: {
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
  let failedMessage = null;
  return {
    core: {
      info: msg => info.push(msg),
      setFailed: msg => { failedMessage = msg; },
    },
    info,
    getFailed: () => failedMessage,
  };
}

function makeContext(pr) {
  return { repo: { owner: OWNER, repo: REPO }, payload: { pull_request: pr } };
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
