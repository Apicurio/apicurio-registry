// Issue Monitoring
//
// Scheduled scan for stale assigned issues and unassigned issues needing
// triage. See .github/issue-monitoring.yml for config (thresholds, triage
// windows, message templates).

const fs = require('fs');
const path = require('path');

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const MS_PER_DAY = 24 * 60 * 60 * 1000;

const STALE_ASSIGNED_LABEL_DEF = {
  color: 'F7BF6A',
  description: 'Assigned issue inactive for 14+ days with no linked PR',
};

const NEEDS_TRIAGE_LABEL_DEF = {
  color: 'D4C5F9',
  description: 'Unassigned issue with no maintainer activity, needs triage',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function loadConfig() {
  const configPath = path.join(process.cwd(), '.github', 'issue-monitoring.json');
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

function hasLabel(issue, label) {
  return (issue.labels || []).map(l => (typeof l === 'string' ? l : l.name)).includes(label);
}

function formatMentions(logins) {
  return logins.map(login => `@${login}`).join(' ');
}

function renderTemplate(template, vars) {
  let result = template;
  for (const [key, value] of Object.entries(vars)) {
    result = result.replace(new RegExp(`\\{${key}\\}`, 'g'), value);
  }
  return result;
}

function createApi(github, owner, repo, core, dryRun) {
  const ensuredLabels = new Set();

  async function ensureLabel(name, def) {
    if (ensuredLabels.has(name)) return;
    try {
      const { data: existing } = await github.rest.issues.getLabel({ owner, repo, name });
      if (existing.color !== def.color || existing.description !== def.description) {
        await github.rest.issues.updateLabel({ owner, repo, name, color: def.color, description: def.description });
      }
    } catch (e) {
      if (e.status === 404) {
        await github.rest.issues.createLabel({ owner, repo, name, color: def.color, description: def.description });
      } else {
        throw e;
      }
    }
    ensuredLabels.add(name);
  }

  return {
    listAssignedOpenIssues: async () => {
      const items = await github.paginate(github.rest.issues.listForRepo, {
        owner, repo, state: 'open', assignee: '*', per_page: 100,
      });
      return items.filter(i => !i.pull_request);
    },

    listUnassignedOpenIssues: async () => {
      const items = await github.paginate(github.rest.issues.listForRepo, {
        owner, repo, state: 'open', assignee: 'none', per_page: 100,
      });
      return items.filter(i => !i.pull_request);
    },

    getTimeline: async (issueNumber) => {
      return github.paginate(github.rest.issues.listEventsForTimeline, {
        owner, repo, issue_number: issueNumber, per_page: 100,
      });
    },

    getComments: async (issueNumber) => {
      return github.paginate(github.rest.issues.listComments, {
        owner, repo, issue_number: issueNumber, per_page: 100,
      });
    },

    addLabel: async (issueNumber, label, def) => {
      if (dryRun) {
        core.info(`[dry-run] issue #${issueNumber}: would add label '${label}'`);
        return;
      }
      await ensureLabel(label, def);
      await github.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: [label] });
    },

    removeLabel: async (issueNumber, label) => {
      if (dryRun) {
        core.info(`[dry-run] issue #${issueNumber}: would remove label '${label}'`);
        return;
      }
      try {
        await github.rest.issues.removeLabel({ owner, repo, issue_number: issueNumber, name: label });
      } catch (e) {
        if (e.status !== 404) throw e;
      }
    },

    postComment: async (issueNumber, body) => {
      if (dryRun) {
        core.info(`[dry-run] issue #${issueNumber}: would post comment:\n${body}`);
        return;
      }
      await github.rest.issues.createComment({ owner, repo, issue_number: issueNumber, body });
    },

    removeAssignees: async (issueNumber, assignees) => {
      if (dryRun) {
        core.info(`[dry-run] issue #${issueNumber}: would remove assignees [${assignees.join(', ')}]`);
        return;
      }
      await github.rest.issues.removeAssignees({ owner, repo, issue_number: issueNumber, assignees });
    },
  };
}

// ---------------------------------------------------------------------------
// Assigned-Issue Stale Scan
// ---------------------------------------------------------------------------

// Multiple assignees are treated as a single clock: the start time is the
// latest 'assigned' timeline event among the issue's *current* assignees
// (so reassignment restarts the clock), and the clock resets when any
// current assignee comments after that point.
async function processAssignedIssue(api, config, issue, core) {
  const assignees = (issue.assignees && issue.assignees.length ? issue.assignees : [])
    .map(a => a.login);
  if (!assignees.length) return;

  const timeline = await api.getTimeline(issue.number);

  const assignedEvents = timeline.filter(
    e => e.event === 'assigned' && e.assignee && assignees.includes(e.assignee.login)
  );
  if (!assignedEvents.length) {
    core.warning(`Issue #${issue.number} has assignees but no matching 'assigned' timeline event found, skipping`);
    return;
  }
  const assignedAt = new Date(Math.max(...assignedEvents.map(e => new Date(e.created_at).getTime())));

  const staleLabel = config.assigned_stale.stale_label;

  // A PR that references this issue (via #number/URL mention, or the
  // "Link a pull request" UI) after the current assignment counts as progress.
  const hasLinkedPr = timeline.some(e => {
    if (new Date(e.created_at) <= assignedAt) return false;
    if (e.event === 'cross-referenced') return !!e.source?.issue?.pull_request;
    if (e.event === 'connected') return true;
    return false;
  });

  if (hasLinkedPr) {
    if (hasLabel(issue, staleLabel)) {
      await api.removeLabel(issue.number, staleLabel);
      core.info(`Issue #${issue.number} has a linked PR, removed ${staleLabel}`);
    }
    return;
  }

  const comments = await api.getComments(issue.number);
  const assigneeComments = comments.filter(
    c => assignees.includes(c.user?.login) && new Date(c.created_at) > assignedAt
  );
  const latestAssigneeCommentAt = assigneeComments.length
    ? new Date(Math.max(...assigneeComments.map(c => new Date(c.created_at).getTime())))
    : null;

  const clockStart = latestAssigneeCommentAt && latestAssigneeCommentAt > assignedAt
    ? latestAssigneeCommentAt
    : assignedAt;
  const daysSinceClockStart = (Date.now() - clockStart.getTime()) / MS_PER_DAY;

  const daysUntilPing = config.assigned_stale.days_until_ping;
  const daysUntilUnassign = config.assigned_stale.days_until_unassign;

  if (hasLabel(issue, staleLabel)) {
    const staleLabelEvent = timeline.filter(e => e.event === 'labeled' && e.label?.name === staleLabel).pop();
    const staleLabelAt = staleLabelEvent ? new Date(staleLabelEvent.created_at) : null;

    if (staleLabelAt && latestAssigneeCommentAt && latestAssigneeCommentAt > staleLabelAt) {
      await api.removeLabel(issue.number, staleLabel);
      core.info(`Issue #${issue.number} stale clock reset, assignee commented after ping`);
      return;
    }

    if (daysSinceClockStart >= daysUntilUnassign) {
      const body = renderTemplate(config.assigned_stale.unassign_message, {
        assignee: formatMentions(assignees),
        days: daysUntilUnassign,
      });
      await api.removeAssignees(issue.number, assignees);
      await api.removeLabel(issue.number, staleLabel);
      await api.postComment(issue.number, body);
      core.info(`Issue #${issue.number} auto-unassigned after ${daysUntilUnassign} days of inactivity`);
    } else {
      core.info(`Issue #${issue.number} already pinged, ${Math.floor(daysSinceClockStart)}d since clock start`);
    }
    return;
  }

  if (daysSinceClockStart >= daysUntilPing) {
    const body = renderTemplate(config.assigned_stale.ping_message, {
      assignee: formatMentions(assignees),
      days: daysUntilPing,
      days_until_unassign_remaining: daysUntilUnassign - daysUntilPing,
    });
    await api.addLabel(issue.number, staleLabel, STALE_ASSIGNED_LABEL_DEF);
    await api.postComment(issue.number, body);
    core.info(`Issue #${issue.number} pinged assignee(s) after ${daysUntilPing} days of inactivity`);
  }
}

async function handleAssignedStaleScan(api, config, core) {
  const issues = await api.listAssignedOpenIssues();
  for (const issue of issues) {
    try {
      await processAssignedIssue(api, config, issue, core);
    } catch (err) {
      core.warning(`Issue #${issue.number} assigned-stale scan failed: ${err.message}`);
    }
  }
}

// ---------------------------------------------------------------------------
// Unassigned-Issue Triage Scan
// ---------------------------------------------------------------------------

// Triage window is picked from config.triage.windows (first label match wins)
// or config.triage.default_days if no listed label is present. The clock
// starts at the issue's creation and is pushed forward by the most recent
// maintainer comment or maintainer-driven label change — non-maintainer
// activity (e.g. other users commenting) does not reset it, since the point
// is to catch issues a maintainer hasn't looked at yet.
function triageWindowDays(config, issue) {
  const issueLabels = (issue.labels || []).map(l => (typeof l === 'string' ? l : l.name));
  const window = (config.triage.windows || []).find(w => w.labels.some(l => issueLabels.includes(l)));
  return window ? window.days : config.triage.default_days;
}

async function processUnassignedIssue(api, config, issue, core) {
  const triageLabel = config.triage.label;
  if (hasLabel(issue, triageLabel)) return;

  const maintainers = config.maintainers || [];

  const [timeline, comments] = await Promise.all([
    api.getTimeline(issue.number),
    api.getComments(issue.number),
  ]);

  const maintainerActivityTimes = [
    ...comments
      .filter(c => maintainers.includes(c.user?.login))
      .map(c => new Date(c.created_at).getTime()),
    ...timeline
      .filter(e => (e.event === 'labeled' || e.event === 'unlabeled') && maintainers.includes(e.actor?.login))
      .map(e => new Date(e.created_at).getTime()),
  ];

  const clockStart = maintainerActivityTimes.length
    ? new Date(Math.max(...maintainerActivityTimes))
    : new Date(issue.created_at);
  const daysSinceClockStart = (Date.now() - clockStart.getTime()) / MS_PER_DAY;

  const windowDays = triageWindowDays(config, issue);
  if (daysSinceClockStart >= windowDays) {
    await api.addLabel(issue.number, triageLabel, NEEDS_TRIAGE_LABEL_DEF);
    core.info(`Issue #${issue.number} flagged ${triageLabel} after ${Math.floor(daysSinceClockStart)}d with no maintainer activity (window: ${windowDays}d)`);
  }
}

async function handleUnassignedTriageScan(api, config, core) {
  const issues = await api.listUnassignedOpenIssues();
  for (const issue of issues) {
    try {
      await processUnassignedIssue(api, config, issue, core);
    } catch (err) {
      core.warning(`Issue #${issue.number} triage scan failed: ${err.message}`);
    }
  }
}

// ---------------------------------------------------------------------------
// Entry Point
// ---------------------------------------------------------------------------

async function handleScan({ github, context, core }) {
  const { owner, repo } = context.repo;
  // workflow_dispatch boolean inputs arrive as the string 'true'/'false';
  // schedule-triggered runs have no inputs at all, so default to false.
  const dryRun = context.payload.inputs?.dry_run === 'true';
  const api = createApi(github, owner, repo, core, dryRun);
  const config = loadConfig();

  core.info(`Issue monitoring scan starting (dry_run=${dryRun})`);
  await handleAssignedStaleScan(api, config, core);
  await handleUnassignedTriageScan(api, config, core);
}

module.exports = {
  handleScan,
};
