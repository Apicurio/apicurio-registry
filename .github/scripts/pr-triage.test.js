// Tests for pr-triage.js — run with: node --test .github/scripts/
const { test } = require('node:test');
const assert = require('node:assert/strict');

const triage = require('./pr-triage.js');

// ---------------------------------------------------------------------------
// Fixture builders
// ---------------------------------------------------------------------------

function makePr(overrides = {}) {
  return {
    number: 42,
    title: 'fix(storage): handle null artifact refs',
    body: 'Fixes #100',
    additions: 50,
    deletions: 10,
    labels: [],
    user: { login: 'junior-dev' },
    head: { sha: 'abcdef1234567890' },
    base: { repo: { owner: { login: 'Apicurio' }, name: 'apicurio-registry' } },
    ...overrides,
  };
}

function makeCommit(message, parents = 1) {
  return {
    sha: 'c0ffee1234567890',
    parents: Array(parents).fill({ sha: 'p' }),
    commit: { message },
  };
}

function signedCommit() {
  return makeCommit('fix(storage): handle nulls\n\nSigned-off-by: Junior Dev <jr@example.com>');
}

function makeFile(filename, patch, overrides = {}) {
  return { filename, patch, status: 'modified', additions: 1, deletions: 0, ...overrides };
}

function makeIssue(number, authorLogin, commentAuthors = []) {
  return {
    issue: { number, user: { login: authorLogin } },
    comments: commentAuthors.map(login => ({ user: { login } })),
  };
}

// A data object that produces zero findings.
function cleanData(overrides = {}) {
  return {
    pr: makePr(),
    commits: [signedCommit()],
    files: [
      makeFile('app/src/main/java/io/apicurio/registry/storage/Foo.java',
        '+import java.util.List;\n+    doWork();'),
      makeFile('app/src/test/java/io/apicurio/registry/storage/FooTest.java',
        '+    assertEquals(3, counter);'),
    ],
    linkedIssues: [makeIssue(100, 'someone', ['carlesarnal'])],
    openPrs: [],
    config: { ...triage.DEFAULTS },
    maintainers: ['carlesarnal', 'EricWittmann'],
    ...overrides,
  };
}

function findingIds(data) {
  return triage.runChecks(data).map(f => f.id);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

test('addedLines extracts only added lines, without +++ header', () => {
  const patch = '@@ -1,2 +1,3 @@\n context\n+added one\n-removed\n+++ not this\n+added two';
  assert.deepEqual(triage.addedLines(patch), ['added one', 'added two']);
});

test('addedLines handles missing patch (binary/large files)', () => {
  assert.deepEqual(triage.addedLines(undefined), []);
});

test('extractLinkedIssueNumbers finds short refs, URLs, and dedups', () => {
  const body = 'Fixes #123 and relates to #456.\n' +
    'See https://github.com/Apicurio/apicurio-registry/issues/123 too.';
  assert.deepEqual(
    triage.extractLinkedIssueNumbers(body, 'Apicurio', 'apicurio-registry'),
    [123, 456]);
});

test('extractLinkedIssueNumbers handles null body', () => {
  assert.deepEqual(triage.extractLinkedIssueNumbers(null, 'o', 'r'), []);
});

// ---------------------------------------------------------------------------
// Clean PR → green
// ---------------------------------------------------------------------------

test('clean PR produces no findings and a green verdict', () => {
  const findings = triage.runChecks(cleanData());
  assert.deepEqual(findings, []);
  assert.equal(triage.computeVerdict(findings), 'green');
});

// ---------------------------------------------------------------------------
// Red checks
// ---------------------------------------------------------------------------

test('commit without Signed-off-by is a red dco-missing finding', () => {
  const findings = triage.runChecks(cleanData({ commits: [makeCommit('fix: no signoff')] }));
  const dco = findings.find(f => f.id === 'dco-missing');
  assert.ok(dco, 'dco-missing finding expected');
  assert.equal(dco.severity, 'red');
  assert.ok(dco.detail.includes('Signed-off-by'));
  assert.ok(dco.detail.includes('c0ffee1'), 'detail names the offending sha');
});

test('merge commits are exempt from DCO', () => {
  const ids = findingIds(cleanData({
    commits: [signedCommit(), makeCommit('Merge branch main', 2)],
  }));
  assert.ok(!ids.includes('dco-missing'));
});

test('missing linked issue is red when required', () => {
  const ids = findingIds(cleanData({ pr: makePr({ body: 'trust me' }), linkedIssues: [] }));
  assert.ok(ids.includes('no-linked-issue'));
});

test('missing linked issue is ignored when not required', () => {
  const data = cleanData({ pr: makePr({ body: '' }), linkedIssues: [] });
  data.config.require_linked_issue = false;
  assert.ok(!findingIds(data).includes('no-linked-issue'));
});

test('linked issue without maintainer comment is red', () => {
  const findings = triage.runChecks(cleanData({
    linkedIssues: [makeIssue(100, 'someone', ['other-user'])],
  }));
  const f = findings.find(x => x.id === 'issue-not-approved');
  assert.ok(f, 'issue-not-approved finding expected');
  assert.equal(f.severity, 'red');
  assert.ok(f.detail.includes('#100'), 'detail names the issue');
});

test('issue authored by a maintainer counts as approved', () => {
  const ids = findingIds(cleanData({
    linkedIssues: [makeIssue(100, 'EricWittmann', [])],
  }));
  assert.ok(!ids.includes('issue-not-approved'));
});

test('files under target/ or binaries are red', () => {
  for (const name of ['app/target/generated/Foo.java', 'lib/thing.jar']) {
    const data = cleanData();
    data.files.push(makeFile(name, undefined));
    assert.ok(findingIds(data).includes('generated-files'), name);
  }
});

// ---------------------------------------------------------------------------
// Yellow checks
// ---------------------------------------------------------------------------

test('main code without test changes is yellow tests-missing', () => {
  const data = cleanData();
  data.files = [makeFile('app/src/main/java/Foo.java', '+    newCodePath();')];
  assert.ok(findingIds(data).includes('tests-missing'));
});

test('integration-tests changes count as tests', () => {
  const data = cleanData();
  data.files = [
    makeFile('app/src/main/java/Foo.java', '+    newCodePath();'),
    makeFile('integration-tests/src/test/java/FooIT.java', '+    check();'),
  ];
  assert.ok(!findingIds(data).includes('tests-missing'));
});

test('star import added is yellow', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/Bar.java', '+import java.util.*;'));
  assert.ok(findingIds(data).includes('star-imports'));
});

test('config property outside allowed prefixes is yellow', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/Cfg.java',
    '+    @ConfigProperty(name = "myfeature.enabled", defaultValue = "false")'));
  assert.ok(findingIds(data).includes('config-prefix'));
});

test('apicurio.* and quarkus.* config properties are allowed', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/Cfg.java',
    '+    @ConfigProperty(name = "apicurio.rest.enabled")\n' +
    '+    @ConfigProperty(name = "quarkus.http.port")'));
  assert.ok(!findingIds(data).includes('config-prefix'));
});

test('license header in new file is yellow', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/New.java',
    '+ * Licensed under the Apache License, Version 2.0', { status: 'added' }));
  assert.ok(findingIds(data).includes('license-headers'));
});

test('license header in pre-existing file is not flagged', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/Old.java',
    '+ * Licensed under the Apache License, Version 2.0', { status: 'modified' }));
  assert.ok(!findingIds(data).includes('license-headers'));
});

test('toLowerCase without Locale is yellow; with Locale is fine', () => {
  const bad = cleanData();
  bad.files.push(makeFile('app/src/main/java/A.java', '+    s.toLowerCase();'));
  assert.ok(findingIds(bad).includes('locale-missing'));

  const good = cleanData();
  good.files.push(makeFile('app/src/main/java/A.java', '+    s.toLowerCase(Locale.ROOT);'));
  assert.ok(!findingIds(good).includes('locale-missing'));
});

test('synchronized in Mutiny file is yellow; in plain file is not', () => {
  const bad = cleanData();
  bad.files.push(makeFile('app/src/main/java/R.java',
    '+import io.smallrye.mutiny.Uni;\n+    synchronized (lock) {'));
  assert.ok(findingIds(bad).includes('synchronized-reactive'));

  const good = cleanData();
  good.files.push(makeFile('app/src/main/java/P.java', '+    synchronized (lock) {'));
  assert.ok(!findingIds(good).includes('synchronized-reactive'));
});

test('tab characters in java file is yellow', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/T.java', '+\tdoWork();'));
  assert.ok(findingIds(data).includes('tab-characters'));
});

test('inline FQN in code is yellow, but import lines and comments are fine', () => {
  const bad = cleanData();
  bad.files.push(makeFile('app/src/main/java/F.java',
    '+    throw new java.util.concurrent.TimeoutException();'));
  assert.ok(findingIds(bad).includes('inline-fqn'));

  const good = cleanData();
  good.files.push(makeFile('app/src/main/java/F.java',
    '+import java.util.concurrent.TimeoutException;\n+// see java.util.concurrent.TimeoutException'));
  assert.ok(!findingIds(good).includes('inline-fqn'));
});

test('non-conventional PR title is yellow and quoted in the detail', () => {
  const findings = triage.runChecks(cleanData({ pr: makePr({ title: 'Fixed the bug' }) }));
  const f = findings.find(x => x.id === 'title-format');
  assert.ok(f, 'title-format finding expected');
  assert.equal(f.severity, 'yellow');
  assert.ok(f.detail.includes('Fixed the bug'));
});

test('conventional titles with scope, without scope, and breaking marker pass', () => {
  for (const title of ['feat(ui): add search', 'fix: null check', 'refactor(storage/sql)!: split dao']) {
    const ids = findingIds(cleanData({ pr: makePr({ title }) }));
    assert.ok(!ids.includes('title-format'), title);
  }
});

test('diff above max_diff_lines is yellow', () => {
  const ids = findingIds(cleanData({ pr: makePr({ additions: 4000, deletions: 100 }) }));
  assert.ok(ids.includes('oversized'));
});

test('another open PR referencing the same issue is yellow', () => {
  const ids = findingIds(cleanData({
    openPrs: [{ number: 41, body: 'Fixes #100' }, { number: 42, body: 'Fixes #100' }],
  }));
  assert.ok(ids.includes('overlapping-pr'));
});

test('overlap detection also matches full issue URLs', () => {
  const ids = findingIds(cleanData({
    openPrs: [{ number: 41, body: 'See https://github.com/Apicurio/apicurio-registry/issues/100' }],
  }));
  assert.ok(ids.includes('overlapping-pr'));
});

test('own PR and unrelated PRs do not count as overlap', () => {
  const ids = findingIds(cleanData({
    openPrs: [{ number: 42, body: 'Fixes #100' }, { number: 43, body: 'Fixes #999' }],
  }));
  assert.ok(!ids.includes('overlapping-pr'));
});

// ---------------------------------------------------------------------------
// Verdict aggregation and report rendering
// ---------------------------------------------------------------------------

test('verdict: red beats yellow beats green', () => {
  assert.equal(triage.computeVerdict([]), 'green');
  assert.equal(triage.computeVerdict([{ severity: 'yellow' }]), 'yellow');
  assert.equal(triage.computeVerdict([{ severity: 'yellow' }, { severity: 'red' }]), 'red');
});

test('report contains marker, verdict, findings, and head sha', () => {
  const findings = [
    { id: 'dco-missing', severity: 'red', title: 'Missing DCO sign-off', detail: 'sign your commits' },
    { id: 'star-imports', severity: 'yellow', title: 'Star imports added', detail: 'be explicit' },
  ];
  const body = triage.renderReport({ verdict: 'red', findings, pr: makePr() });
  assert.ok(body.includes(triage.MARKER));
  assert.ok(body.includes('RED'));
  assert.ok(body.includes('Blocking'));
  assert.ok(body.includes('Missing DCO sign-off'));
  assert.ok(body.includes('Should fix'));
  assert.ok(body.includes('abcdef1'));
});

// ---------------------------------------------------------------------------
// Markdown injection hardening (report runs under pull_request_target)
// ---------------------------------------------------------------------------

test('code() strips backticks and newlines and truncates', () => {
  assert.equal(triage.code('safe.java'), 'safe.java');
  assert.equal(triage.code('evil`![x](https://evil)`\nrest'), 'evil![x](https://evil)rest');
  assert.equal(triage.code(null), '');
  assert.equal(triage.code('a'.repeat(500)).length, 200);
});

test('malicious PR title cannot break out of the report code span', () => {
  const title = 'pwn` — [click me](https://evil.example) `@maintainers do /accept';
  const findings = triage.runChecks(cleanData({ pr: makePr({ title }) }));
  const f = findings.find(x => x.id === 'title-format');
  assert.ok(f, 'weird title should fail conventional format');
  assert.ok(!f.detail.includes('`ick'), 'sanity');
  assert.ok(!f.detail.includes('pwn`'), 'backticks must be stripped from the title');
  assert.ok(f.detail.includes('pwn — [click me](https://evil.example) @maintainers do /accept'));
});

test('malicious filename with backticks is sanitized in finding details', () => {
  const data = cleanData();
  data.files.push(makeFile('app/src/main/java/evil`](x)`.java', '+import java.util.*;'));
  const f = triage.runChecks(data).find(x => x.id === 'star-imports');
  assert.ok(f, 'star-imports finding expected');
  assert.ok(!f.detail.includes('evil`'), 'backticks must be stripped from filenames');
});

test('extractLinkedIssueNumbers escapes regex metacharacters in owner/repo', () => {
  // A repo name with a dot must not act as a regex wildcard.
  const body = 'https://github.com/o/a.b/issues/7 and https://github.com/o/aXb/issues/8';
  assert.deepEqual(triage.extractLinkedIssueNumbers(body, 'o', 'a.b'), [7]);
});

// ---------------------------------------------------------------------------
// Side effects with a fake github client
// ---------------------------------------------------------------------------

function fakeGithub({ labels = [], comments = [] } = {}) {
  const calls = { addLabels: [], removeLabel: [], createLabel: [], createComment: [], updateComment: [] };
  return {
    calls,
    // Mirrors octokit paginate's optional mapFn signature, including done().
    paginate: async (fn, args, mapFn) => {
      const r = await fn(args);
      const data = r.data ?? r;
      return mapFn ? mapFn({ data }, () => {}) : data;
    },
    rest: {
      issues: {
        getLabel: async ({ name }) => {
          if (labels.includes(name)) return { data: { name } };
          const err = new Error('not found'); err.status = 404; throw err;
        },
        createLabel: async (args) => { calls.createLabel.push(args); return { data: {} }; },
        addLabels: async (args) => { calls.addLabels.push(args); return { data: {} }; },
        removeLabel: async (args) => { calls.removeLabel.push(args); return { data: {} }; },
        listComments: async () => ({ data: comments }),
        createComment: async (args) => { calls.createComment.push(args); return { data: {} }; },
        updateComment: async (args) => { calls.updateComment.push(args); return { data: {} }; },
      },
    },
  };
}

test('applyTriageLabel swaps to the verdict label and creates it if missing', async () => {
  const gh = fakeGithub({ labels: [] });
  const pr = makePr({ labels: [{ name: 'triage/red' }] });
  await triage.applyTriageLabel(gh, 'o', 'r', pr, 'green');
  assert.equal(gh.calls.createLabel[0].name, 'triage/green');
  assert.equal(gh.calls.removeLabel[0].name, 'triage/red');
  assert.deepEqual(gh.calls.addLabels[0].labels, ['triage/green']);
});

test('applyTriageLabel is idempotent when label already applied', async () => {
  const gh = fakeGithub({ labels: ['triage/green'] });
  const pr = makePr({ labels: [{ name: 'triage/green' }] });
  await triage.applyTriageLabel(gh, 'o', 'r', pr, 'green');
  assert.equal(gh.calls.addLabels.length, 0);
  assert.equal(gh.calls.removeLabel.length, 0);
});

test('upsertReportComment creates on first run, updates thereafter', async () => {
  const fresh = fakeGithub({ comments: [{ id: 1, body: 'welcome!' }] });
  await triage.upsertReportComment(fresh, 'o', 'r', 42, `${triage.MARKER}\nreport`);
  assert.equal(fresh.calls.createComment.length, 1);

  const existing = fakeGithub({
    comments: [{ id: 1, body: 'welcome!' }, { id: 2, body: `${triage.MARKER}\nold report` }],
  });
  await triage.upsertReportComment(existing, 'o', 'r', 42, `${triage.MARKER}\nnew report`);
  assert.equal(existing.calls.createComment.length, 0);
  assert.equal(existing.calls.updateComment[0].comment_id, 2);
});
