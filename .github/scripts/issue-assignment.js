// Issue Assignment Handler
//
// Automatically assigns or unassigns contributors to issues when they comment with trigger commands.
// Commands: /assign-me, /unassign-me (aliases: /claim, /unassign)

const MAX_ASSIGNED_ISSUES = 3;

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
    await handleAssign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, currentAssignees, assigneeLogins);
  } else if (unassignMatch) {
    await handleUnassign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, assigneeLogins);
  }
}

async function handleAssign(github, core, owner, repo, issueNumber, commenterOriginal, commenter, currentAssignees, assigneeLogins) {
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
};

