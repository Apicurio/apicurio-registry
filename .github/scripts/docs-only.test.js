// Tests for docs-only.js — run with:
//   node --test .github/scripts/docs-only.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('child_process');
const path = require('path');

const docsOnly = require('./docs-only.js');

const SETTINGS = {
  paths: ['docs/**', 'docs-playbook/*.yml'],
  exclude: [
    'docs/pom.xml',
    'docs/config-generator/**',
    'docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.*',
    'docs/**/*.sh',
  ],
  review_required_paths: ['docs/modules/**/*security*'],
};

const file = (filename, extra = {}) => ({ filename, status: 'modified', ...extra });

test('a PR changing only docs pages is docs-only', () => {
  const files = [file('docs/modules/ROOT/pages/getting-started/assembly-intro.adoc'), file('docs/antora.yml')];
  assert.equal(docsOnly.isDocsOnly(files, SETTINGS), true);
});

test('a playbook change counts as docs', () => {
  assert.equal(docsOnly.isDocsOnly([file('docs-playbook/docs-playbook.yml')], SETTINGS), true);
});

test('a file outside the listed docs-playbook paths is not docs-only', () => {
  assert.equal(docsOnly.isDocsOnly([file('docs-playbook/_build-all.sh')], SETTINGS), false);
});

test('exclude ignores case', () => {
  assert.equal(docsOnly.isDocsOnly([file('docs/tools/Run.SH')], SETTINGS), false);
});

test('any file outside the docs paths makes the PR not docs-only', () => {
  const files = [file('docs/modules/ROOT/pages/a.adoc'), file('app/src/main/java/Foo.java')];
  assert.equal(docsOnly.isDocsOnly(files, SETTINGS), false);
});

test('excluded files under docs/ make the PR not docs-only', () => {
  for (const name of [
    'docs/pom.xml',
    'docs/config-generator/src/main/java/Gen.java',
    'docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc',
    'docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.template',
  ]) {
    assert.equal(docsOnly.isDocsOnly([file(name)], SETTINGS), false, name);
  }
});

test('a file renamed into docs/ from elsewhere is not docs-only', () => {
  const files = [file('docs/modules/ROOT/pages/moved.adoc', { status: 'renamed', previous_filename: 'README.md' })];
  assert.equal(docsOnly.isDocsOnly(files, SETTINGS), false);
});

test('a file renamed within docs/ is docs-only', () => {
  const files = [file('docs/modules/ROOT/pages/new.adoc', {
    status: 'renamed', previous_filename: 'docs/modules/ROOT/pages/old.adoc',
  })];
  assert.equal(docsOnly.isDocsOnly(files, SETTINGS), true);
});

test('an empty file list is not docs-only', () => {
  assert.equal(docsOnly.isDocsOnly([], SETTINGS), false);
});

test('a prefix match is not enough: docs-foo/ is not docs/', () => {
  assert.equal(docsOnly.isDocsOnly([file('docsfoo/a.adoc')], SETTINGS), false);
});

test('missing settings mean nothing is docs-only', () => {
  assert.equal(docsOnly.isDocsOnly([file('docs/a.adoc')], undefined), false);
  assert.equal(docsOnly.isDocsOnly([file('docs/a.adoc')], {}), false);
});

test('reviewRequiredFiles lists the files matching review_required_paths', () => {
  const files = [
    file('docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc'),
    file('docs/modules/ROOT/pages/getting-started/assembly-intro.adoc'),
  ];
  assert.deepEqual(docsOnly.reviewRequiredFiles(files, SETTINGS),
    ['docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc']);
});

test('reviewRequiredFiles checks the previous name of a renamed file too', () => {
  const files = [file('docs/modules/ROOT/pages/renamed.adoc', {
    status: 'renamed', previous_filename: 'docs/modules/ROOT/pages/old-security.adoc',
  })];
  assert.deepEqual(docsOnly.reviewRequiredFiles(files, SETTINGS), ['docs/modules/ROOT/pages/renamed.adoc']);
});

test('reviewRequiredFiles ignores case', () => {
  const files = [file('docs/modules/ROOT/pages/Configuring-Security.adoc')];
  assert.deepEqual(docsOnly.reviewRequiredFiles(files, SETTINGS), ['docs/modules/ROOT/pages/Configuring-Security.adoc']);
});

test('reviewRequiredFiles is empty without the setting', () => {
  assert.deepEqual(docsOnly.reviewRequiredFiles([file('docs/a-security.adoc')], { paths: ['docs/**'] }), []);
});

test('CLI prints docs-only=true for a docs-only file list', () => {
  const out = runCli(['docs/modules/ROOT/pages/a.adoc']);
  assert.equal(out, 'docs-only=true\n');
});

test('CLI prints docs-only=false for a mixed file list', () => {
  const out = runCli(['docs/modules/ROOT/pages/a.adoc', 'pom.xml']);
  assert.equal(out, 'docs-only=false\n');
});

test('CLI honours the previous name of a renamed file given as an object', () => {
  const out = runCli([{ filename: 'docs/modules/ROOT/pages/a.adoc', previous_filename: 'README.md' }]);
  assert.equal(out, 'docs-only=false\n');
});

function runCli(filenames) {
  const config = JSON.stringify({ docs_only: SETTINGS });
  const tmp = require('fs').mkdtempSync(path.join(require('os').tmpdir(), 'docs-only-'));
  const configPath = path.join(tmp, 'config.json');
  require('fs').writeFileSync(configPath, config);
  return execFileSync('node', [path.join(__dirname, 'docs-only.js'), configPath], {
    input: JSON.stringify(filenames), encoding: 'utf8',
  });
}

// The shipped configuration must classify the cases this feature exists for.
test('shipped pr-lifecycle.yml classifies representative PRs', t => {
  let config;
  try {
    config = JSON.parse(execFileSync('yq', ['-o', 'json', path.join(__dirname, '..', 'pr-lifecycle.yml')],
      { encoding: 'utf8' }));
  } catch (e) {
    if (process.env.CI) assert.fail('yq is required to check the shipped configuration');
    t.skip('yq is not installed');
    return;
  }
  const settings = config.docs_only;
  assert.equal(docsOnly.isDocsOnly([file('docs/modules/ROOT/pages/getting-started/assembly-intro.adoc')],
    settings), true);
  assert.equal(docsOnly.isDocsOnly([file('docs/rest-api/pom.xml')], settings), false);
  assert.equal(docsOnly.isDocsOnly(
    [file('docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc')], settings), false);
  assert.deepEqual(docsOnly.reviewRequiredFiles(
    [file('docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc')], settings),
  ['docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc']);
  assert.deepEqual(docsOnly.reviewRequiredFiles(
    [file('docs/modules/ROOT/pages/getting-started/assembly-intro.adoc')], settings), []);

  // docs-playbook/: the playbooks are docs, the build code is not.
  assert.equal(docsOnly.isDocsOnly([file('docs-playbook/docs-playbook.yml')], settings), true);
  assert.equal(docsOnly.isDocsOnly([file('docs-playbook/supplemental_ui/partials/header.hbs')], settings), true);
  for (const name of ['docs-playbook/_build-all.sh', 'docs-playbook/build.sh', 'docs-playbook/Dockerfile',
    'docs-playbook/package.json', 'docs-playbook/package-lock.json', 'docs/scripts/apicurio-export-registry-data.sh']) {
    assert.equal(docsOnly.isDocsOnly([file(name)], settings), false, name);
  }

  // Security-related files anywhere under docs/, in any case.
  for (const name of [
    'docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc',
    'docs/modules/ROOT/pages/getting-started/assembly-deploying-registry-secured-kafka.adoc',
    'docs/guides/2025/securing-apicurio-registry-with-microsoft-entra-id-external-tenants.md',
    'docs/guides/2025/securing-apicurio-registry-with-microsoft-entra-id-external-tenants/docker-compose.yaml',
    'docs/modules/ROOT/assets/attachments/apicurio-registry-dex-cr.yaml',
    'docs/modules/ROOT/partials/proc-configuring-RBAC.adoc',
    'docs/modules/ROOT/partials/proc-configuring-tls.adoc',
  ]) {
    assert.deepEqual(docsOnly.reviewRequiredFiles([file(name)], settings), [name], name);
  }
});
