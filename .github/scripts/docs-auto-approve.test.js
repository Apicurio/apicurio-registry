// Tests for docs-auto-approve.js — run with:
//   node --test .github/scripts/docs-auto-approve.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');

const { handleVerifyCompleted, APPROVAL_MARKER, REVIEW_REQUIRED_MARKER } = require('./docs-auto-approve.js');

const OWNER = 'Apicurio';
const REPO = 'apicurio-registry';
const SHA = 'abc1234def5678';

const CONFIG = {
  maintainers: ['eameh', 'alice'],
  docs_only: {
    paths: ['docs/**'],
    exclude: ['docs/pom.xml'],
    review_required_paths: ['docs/modules/**/*security*'],
  },
};

const PAGE = 'docs/modules/ROOT/pages/getting-started/assembly-intro.adoc';
const SECURITY_PAGE = 'docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc';

function makeRun(overrides = {}) {
  return {
    id: 99, event: 'pull_request', conclusion: 'success', head_sha: SHA,
    path: '.github/workflows/verify.yaml', pull_requests: [{ number: 7 }],
    html_url: 'https://example.test/run/99', ...overrides,
  };
}

function makePr(overrides = {}) {
  return {
    number: 7, state: 'open', draft: false, user: { login: 'eameh' },
    base: { ref: 'main' }, head: { sha: SHA }, ...overrides,
  };
}

function makeCommit({ sha = SHA, author = 'eameh', committer = 'eameh' } = {}) {
  return {
    sha,
    author: author ? { login: author } : null,
    committer: committer ? { login: committer } : null,
  };
}

function setup({ run = makeRun(), pr = makePr(), files = [PAGE], reviews = [],
                 jobs = [{ name: 'Docs Check / Antora', conclusion: 'success' }],
                 reviewError = null, comments = [], prsForSha = null,
                 commits = [makeCommit()] } = {}) {
  const calls = { reviews: [], comments: [], updatedComments: [], dispatches: [] };
  const github = {
    paginate: async (fn, params) => (await fn(params)).data,
    rest: {
      pulls: {
        get: async () => ({ data: pr }),
        listFiles: async () => ({ data: files.map(f => (typeof f === 'string' ? { filename: f } : f)) }),
        listReviews: async () => ({ data: reviews }),
        listCommits: async () => ({ data: commits }),
        createReview: async params => {
          if (reviewError) throw reviewError;
          calls.reviews.push(params);
          return { data: {} };
        },
      },
      repos: {
        listPullRequestsAssociatedWithCommit: async () => ({ data: prsForSha || [] }),
      },
      actions: {
        listJobsForWorkflowRun: async () => ({ data: { jobs } }),
        createWorkflowDispatch: async params => {
          calls.dispatches.push(params);
          return { data: {} };
        },
      },
      issues: {
        listComments: async () => ({ data: comments }),
        createComment: async params => {
          calls.comments.push(params);
          return { data: {} };
        },
        updateComment: async params => {
          calls.updatedComments.push(params);
          return { data: {} };
        },
      },
    },
  };
  const log = { info: [], failed: null };
  const core = {
    info: m => log.info.push(m),
    warning: m => log.info.push(m),
    setFailed: m => { log.failed = m; },
    summary: { addRaw() { return this; }, write: async () => {} },
  };
  const context = { repo: { owner: OWNER, repo: REPO }, payload: { workflow_run: run } };
  return { github, core, context, calls, log };
}

const run = env => handleVerifyCompleted({ ...env, config: CONFIG });

test('approves a maintainer docs-only PR whose Verify run and docs check passed', async () => {
  const env = setup();
  await run(env);
  assert.equal(env.calls.reviews.length, 1);
  const review = env.calls.reviews[0];
  assert.equal(review.event, 'APPROVE');
  assert.equal(review.commit_id, SHA);
  assert.equal(review.pull_number, 7);
  assert.ok(review.body.includes(APPROVAL_MARKER));
  assert.equal(env.log.failed, null);
});

test('asks the lifecycle orchestrator to reconcile after approving', async () => {
  const env = setup();
  await run(env);
  assert.deepEqual(env.calls.dispatches, [{
    owner: OWNER, repo: REPO, workflow_id: 'pr-lifecycle.yml', ref: 'main', inputs: { pr_number: '7' },
  }]);
});

for (const [name, overrides] of [
  ['the run failed', { run: makeRun({ conclusion: 'failure' }) }],
  ['the run is not a pull_request run', { run: makeRun({ event: 'push' }) }],
  ['the run is not verify.yaml', { run: makeRun({ path: '.github/workflows/other.yaml' }) }],
  ['the author is not a maintainer', { pr: makePr({ user: { login: 'contributor' } }) }],
  ['the PR is a draft', { pr: makePr({ draft: true }) }],
  ['the PR is closed', { pr: makePr({ state: 'closed' }) }],
  ['the PR targets an unvalidated branch', { pr: makePr({ base: { ref: 'feature' } }) }],
  ['the PR has moved on to a newer commit', { pr: makePr({ head: { sha: 'ffff0000' } }) }],
  ['the PR is not docs-only', { files: [PAGE, 'app/src/main/java/Foo.java'] }],
  ['the PR touches an excluded docs file', { files: ['docs/pom.xml'] }],
  ['the docs check was skipped', { jobs: [{ name: 'Docs Check / Antora', conclusion: 'skipped' }] }],
  ['the docs check did not run at all', { jobs: [] }],
  ['a commit was authored by a non-maintainer', { commits: [makeCommit(), makeCommit({ author: 'contributor' })] }],
  ['a commit was committed by a non-maintainer', { commits: [makeCommit({ committer: 'contributor' })] }],
  ['a commit author has no GitHub account', { commits: [makeCommit({ author: null })] }],
]) {
  test(`does not approve when ${name}`, async () => {
    const env = setup(overrides);
    await run(env);
    assert.deepEqual(env.calls.reviews, []);
    assert.deepEqual(env.calls.comments, []);
    assert.equal(env.log.failed, null);
  });
}

test('approves commits by several maintainers, and web-flow commits of a maintainer', async () => {
  const env = setup({ commits: [makeCommit(), makeCommit({ author: 'alice' }),
    makeCommit({ author: 'alice', committer: 'web-flow' })] });
  await run(env);
  assert.equal(env.calls.reviews.length, 1);
});

test('names the commits that are not by a maintainer', async () => {
  const env = setup({ commits: [makeCommit({ sha: '0123456789abcdef', author: 'contributor' })] });
  await run(env);
  assert.deepEqual(env.calls.reviews, []);
  assert.ok(env.log.info.includes(
    'Not approving: PR #7 has commits not authored and committed by a maintainer: 0123456789ab'));
});

test('does not approve twice for the same commit', async () => {
  const env = setup({ reviews: [{ state: 'APPROVED', commit_id: SHA, user: { login: 'github-actions[bot]' } }] });
  await run(env);
  assert.deepEqual(env.calls.reviews, []);
});

test('approves again after a push dismissed the earlier approval', async () => {
  const env = setup({ reviews: [{ state: 'DISMISSED', commit_id: 'old', user: { login: 'github-actions[bot]' } }] });
  await run(env);
  assert.equal(env.calls.reviews.length, 1);
});

test('a sensitive file blocks approval and explains why in one comment', async () => {
  const env = setup({ files: [PAGE, SECURITY_PAGE] });
  await run(env);
  assert.deepEqual(env.calls.reviews, []);
  assert.equal(env.calls.comments.length, 1);
  const { body } = env.calls.comments[0];
  assert.ok(body.includes(REVIEW_REQUIRED_MARKER));
  assert.ok(body.includes(`\`${SECURITY_PAGE}\``));
  assert.ok(!body.includes(`\`${PAGE}\``));
});

test('the sensitive-file comment is updated rather than repeated', async () => {
  const existing = { id: 5, body: `${REVIEW_REQUIRED_MARKER}\nold`, user: { login: 'github-actions[bot]' } };
  const env = setup({ files: [SECURITY_PAGE], comments: [existing] });
  await run(env);
  assert.deepEqual(env.calls.comments, []);
  assert.equal(env.calls.updatedComments.length, 1);
  assert.equal(env.calls.updatedComments[0].comment_id, 5);
});

test('finds the PR by commit when the run payload lists none (fork PRs)', async () => {
  const env = setup({ run: makeRun({ pull_requests: [] }), prsForSha: [makePr()] });
  await run(env);
  assert.equal(env.calls.reviews.length, 1);
});

test('a refused approval fails the job with the setting to change', async () => {
  const error = Object.assign(new Error('GitHub Actions is not permitted to approve pull requests.'), { status: 422 });
  const env = setup({ reviewError: error });
  await run(env);
  assert.match(env.log.failed, /Allow GitHub Actions to create and approve pull requests/);
});
