// Issue Assignment Handler
//
// Automatically assigns or unassigns contributors to issues when they comment with trigger commands.
// Commands: /assign-me, /unassign-me (aliases: /claim, /unassign)

const fs = require('fs');
const path = require('path');

const MAX_ASSIGNED_ISSUES = 3;

// Issues carrying one of these labels are maintainer-only and not open to
// self-assignment. Matched case-insensitively against the issue's labels.
// The refusal message names the label rather than the area, so adding a label
// here is all it takes to extend the policy.
const MAINTAINER_ONLY_LABELS = ['area/CI'];

// Team pinged in the rejection message when a contributor wants an exception.
const MAINTAINERS_TEAM = '@Apicurio/maintainers';

const KNOWN_BOTS = new Set([
  'renovate[bot]',
  'dependabot[bot]',
  'apicurio-ci',
  'github-actions[bot]',
  'sonarqubecloud[bot]',
  'sonarqubecloud',
  'sonarcloud[bot]',
  'sonarcloud',
  'codecov[bot]',
  'codecov',
]);

/**
 * Maintainer logins, from the single source of truth shared with the PR lifecycle
 * orchestrator (.github/pr-lifecycle.yml, converted to JSON by the workflow).
 * A missing or unreadable file is not fatal: it only means nobody is treated as a
 * maintainer, so the maintainer-only check applies to everyone.
 */
function loadMaintainers(core) {
  const configPath = path.join(process.cwd(), '.github', 'pr-lifecycle.json');
  try {
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    return (config.maintainers || []).map(m => m.toLowerCase());
  } catch (error) {
    core.warning(`Could not read maintainers from ${configPath}: ${error.message}. Treating all commenters as non-maintainers.`);
    return [];
  }
}

/**
 * Returns the maintainer-only label present on the issue (original casing), or null.
 */
function findMaintainerOnlyLabel(issue) {
  const labelNames = (issue.labels || []).map(l => (typeof l === 'string' ? l : l.name)).filter(Boolean);
  return labelNames.find(
    name => MAINTAINER_ONLY_LABELS.some(restricted => restricted.toLowerCase() === name.toLowerCase())
  ) || null;
}

/**
 * Main handler for issue comment events.
 */
async function handleIssueComment({ github, context, core }) {
  const payload = context.payload;
  const issue = payload.issue;
  const comment = payload.comment;

  // Ignore pull requests (issue comments trigger for PRs as well)
  if (issue.pull_request) {
    core.info('Comment is on a pull request, skipping issue assignment handler.');
    return;
  }

  // Check commenter
  const commenterOriginal = comment.user.login;
  const commenter = commenterOriginal.toLowerCase();
  const commenterType = comment.user.type;

  if (commenterType === 'Bot' || commenter.endsWith('[bot]') || KNOWN_BOTS.has(commenter)) {
    core.info(`Ignoring comment from bot user: ${commenterOriginal}`);
    return;
  }

  const commentBody = (comment.body || '').trim();
  const firstLine = commentBody.split('\n')[0].trim();

  // Match commands using whitespace/line-end boundaries to prevent hyphenated matches (e.g. /assign-foo)
  const assignMatch = /^\/(assign-me|claim)(?:\s|$)/i.test(firstLine);
  const unassignMatch = /^\/(unassign-me|unassign)(?:\s|$)/i.test(firstLine);

  if (!assignMatch && !unassignMatch) {
    core.info('Comment does not start with an issue assignment command, skipping.');
    return;
  }

  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const issueNumber = issue.number;

  // Fetch live issue data to avoid stale payload snapshot race conditions
  let freshIssue;
  try {
    const response = await github.rest.issues.get({
      owner,
      repo,
      issue_number: issueNumber,
    });
    freshIssue = response.data;
  } catch (error) {
    core.warning(`Could not fetch live issue #${issueNumber}: ${error.message}. Falling back to payload issue data.`);
    freshIssue = issue;
  }

  // Verify issue is open
  if (freshIssue.state !== 'open') {
    core.info(`Issue #${issueNumber} is closed. Cannot modify assignment.`);
    await postComment(github, core, owner, repo, issueNumber, `@${commenterOriginal} Issue #${issueNumber} is closed and cannot be assigned.`);
    return;
  }

  const currentAssignees = freshIssue.assignees || [];
  const assigneeLogins = currentAssignees.map(a => a.login.toLowerCase());

  if (assignMatch) {
    await handleAssign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, currentAssignees, assigneeLogins, freshIssue);
  } else if (unassignMatch) {
    await handleUnassign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, assigneeLogins);
  }
}

async function handleAssign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, currentAssignees, assigneeLogins, issue) {
  // Maintainer-only areas are not open to self-assignment. Maintainers themselves
  // are exempt — the policy is that they do this work, not that the issue cannot
  // be claimed at all.
  const restrictedLabel = findMaintainerOnlyLabel(issue);
  const maintainers = restrictedLabel ? loadMaintainers(core) : [];
  if (restrictedLabel && !maintainers.includes(commenter)) {
    core.info(`Issue #${issueNumber} carries the maintainer-only label "${restrictedLabel}"; refusing self-assignment for ${commenterOriginal}.`);
    // Point at one person when the issue was reported by a maintainer — they can
    // act on the request themselves. Otherwise the team is the only useful target.
    const author = issue.user && issue.user.login;
    const pingList = author && maintainers.includes(author.toLowerCase())
      ? `@${author}`
      : MAINTAINERS_TEAM;
    await postComment(
      github,
      core,
      owner,
      repo,
      issueNumber,
      `@${commenterOriginal} Thanks for offering to help! This issue is labelled \`${restrictedLabel}\`, which marks work that is handled by the maintainers, ` +
      `so it is not available for self-assignment. We would really appreciate it if you picked a different issue — ` +
      `there are plenty that are open for contribution.\n\n` +
      `Labels are applied automatically, so if you think \`${restrictedLabel}\` does not really fit this issue, or you would like an exception, ` +
      `you can say so in a comment and ping ${pingList}. A maintainer can remove the label or assign the issue to you directly.`
    );
    return;
  }

  // If already assigned
  if (assigneeLogins.length > 0) {
    if (assigneeLogins.includes(commenter)) {
      core.info(`User ${commenterOriginal} is already assigned to issue #${issueNumber}.`);
      await postComment(github, core, owner, repo, issueNumber, `@${commenterOriginal} You are already assigned to this issue.`);
    } else {
      const mentionList = currentAssignees.map(a => `@${a.login}`).join(', ');
      core.info(`Issue #${issueNumber} is already assigned to ${mentionList}.`);
      await postComment(github, core, owner, repo, issueNumber, `This issue is already claimed by ${mentionList}.`);
    }
    return;
  }

  // Check contributor's open assigned issues limit using pagination to prevent truncation past 100 items
  let openAssignedIssues;
  try {
    openAssignedIssues = await github.paginate(github.rest.issues.listForRepo, {
      owner,
      repo,
      assignee: commenterOriginal,
      state: 'open',
      per_page: 100,
    });
  } catch (error) {
    core.error(`Failed to fetch open issues for user ${commenterOriginal}: ${error.message}`);
    throw error;
  }

  // Filter out PRs (GitHub API returns PRs in issues endpoint)
  const actualOpenIssues = openAssignedIssues.filter(i => !i.pull_request);

  if (actualOpenIssues.length >= MAX_ASSIGNED_ISSUES) {
    core.info(`User ${commenterOriginal} reached max open issues limit (${actualOpenIssues.length}/${MAX_ASSIGNED_ISSUES}).`);
    await postComment(
      github,
      core,
      owner,
      repo,
      issueNumber,
      `@${commenterOriginal} You currently have ${actualOpenIssues.length} open issue(s) assigned to you. ` +
      `Contributors are limited to ${MAX_ASSIGNED_ISSUES} open issues at a time. ` +
      `Please complete or unassign yourself from existing issues before claiming a new one.`
    );
    return;
  }

  // Assign contributor
  core.info(`Assigning ${commenterOriginal} to issue #${issueNumber}.`);
  let updatedIssue;
  try {
    const response = await github.rest.issues.addAssignees({
      owner,
      repo,
      issue_number: issueNumber,
      assignees: [commenterOriginal],
    });
    updatedIssue = response.data;
  } catch (error) {
    core.warning(`Failed to assign ${commenterOriginal} to issue #${issueNumber}: ${error.message}`);
    await postComment(
      github,
      core,
      owner,
      repo,
      issueNumber,
      `@${commenterOriginal} Thank you for volunteering! However, an error occurred while assigning this issue. A maintainer will need to review and assign this issue to you manually.`
    );
    return;
  }

  // Verify contributor was actually assigned (external non-collaborator users cannot be assigned directly by GH API)
  const isAssigned = (updatedIssue.assignees || []).some(
    a => a.login.toLowerCase() === commenter
  );

  if (!isAssigned) {
    core.warning(`User ${commenterOriginal} could not be assigned to issue #${issueNumber}. User may lack collaborator permissions.`);
    await postComment(
      github,
      core,
      owner,
      repo,
      issueNumber,
      `@${commenterOriginal} Thank you for volunteering! However, GitHub repository permissions require collaborator access to assign issues directly. A maintainer will need to review and assign this issue to you manually.`
    );
    return;
  }

  await postComment(
    github,
    core,
    owner,
    repo,
    issueNumber,
    `@${commenterOriginal} Thanks for volunteering! This issue has been assigned to you. Happy coding!`
  );
}

async function handleUnassign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, assigneeLogins) {
  if (!assigneeLogins.includes(commenter)) {
    core.info(`User ${commenterOriginal} attempted to unassign from issue #${issueNumber} but is not assigned.`);
    await postComment(github, core, owner, repo, issueNumber, `@${commenterOriginal} You are not currently assigned to this issue.`);
    return;
  }

  core.info(`Unassigning ${commenterOriginal} from issue #${issueNumber}.`);
  try {
    await github.rest.issues.removeAssignees({
      owner,
      repo,
      issue_number: issueNumber,
      assignees: [commenterOriginal],
    });
  } catch (error) {
    core.warning(`Failed to unassign ${commenterOriginal} from issue #${issueNumber}: ${error.message}`);
    await postComment(
      github,
      core,
      owner,
      repo,
      issueNumber,
      `@${commenterOriginal} Could not unassign you from this issue due to an error. A maintainer can update the assignment manually.`
    );
    return;
  }

  await postComment(github, core, owner, repo, issueNumber, `@${commenterOriginal} You have been unassigned from this issue.`);
}

async function postComment(github, core, owner, repo, issueNumber, body) {
  try {
    await github.rest.issues.createComment({
      owner,
      repo,
      issue_number: issueNumber,
      body,
    });
  } catch (error) {
    core.warning(`Failed to post comment on issue #${issueNumber}: ${error.message}`);
  }
}

module.exports = {
  handleIssueComment,
  MAINTAINER_ONLY_LABELS,
  MAX_ASSIGNED_ISSUES,
};

