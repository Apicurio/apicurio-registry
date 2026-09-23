// PR Reviewer Assignment
//
// Picks a reviewer when a PR enters the lifecycle (opened, or a draft marked
// ready for review). Runs as a step of the classify-pr job in classify.yml,
// right after the PR has been given its area/* labels, because one of the
// signals reads them. See #9005, and `reviewer_assignment` in
// .github/pr-lifecycle.yml for the configuration.
//
// Three kinds of PR, three behaviours:
//   - contributor PRs are scored, assigned to the winner, and the reasoning is
//     posted as a comment (collapsed, so the contributor sees one line) so the
//     knobs can be calibrated in the open;
//   - maintainer PRs are scored and the result is posted as a suggestion only —
//     maintainers pick their own reviewer;
//   - dependency bot PRs (auto_accept) rotate evenly among a small pool. For a
//     version bump, ownership, interest and issue authorship carry no signal,
//     and scoring them would hand nearly all of them to whoever edits pom.xml
//     most.
//
// Three signals, each scored 0-1 and combined with configured weights:
//   - ownership:    who recently committed to the files the PR touches;
//   - interest:     how much each reviewer wants PRs in the PR's area labels;
//   - issue author: whether the reviewer opened an issue this PR closes.
// A reviewer needs at least one of them to be considered at all.
//
// Load — PRs assigned in the last 30 days — is not a scored signal. It breaks
// exact ties, and it drives the swap: an overloaded winner hands the PR to the
// runner-up, provided the runner-up wants PRs in the PR's primary area. A
// weighted fairness signal was measured alongside the swap and changed nothing
// (#9005); load relative to how many PRs someone was eligible for, rather than
// raw counts, is the follow-up that would (#10247).

const fs = require('fs');
const path = require('path');

const COMMENT_MARKER = '<!-- pr-reviewer-assignment -->';

const KNOWN_BOTS = new Set(['apicurio-ci', 'github-actions']);

const DAY_MS = 24 * 60 * 60 * 1000;
const HOUR_MS = 60 * 60 * 1000;

// Every key is required: the numbers live in pr-lifecycle.yml and nowhere
// else, so there is no second set of defaults in here to drift from them.
const REQUIRED_SETTINGS = {
  weights: ['ownership', 'interest', 'issue_author'],
  ownership: ['max_files', 'commits_per_file', 'since_months', 'recent_months',
    'recent_multiplier', 'ignore_paths'],
  fairness: ['window_days', 'recent_hours', 'recent_multiplier', 'swap_ratio', 'swap_min_gap'],
  bot_rotation: ['reviewers'],
};

// ---------------------------------------------------------------------------
// Config and identities
// ---------------------------------------------------------------------------

function loadConfig() {
  const configPath = path.join(process.cwd(), '.github', 'pr-lifecycle.json');
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

/**
 * The validated `reviewer_assignment` section, or null when it is absent
 * (which disables assignment). A present-but-incomplete section throws: a
 * missing weight silently scoring as 0 is exactly the kind of mistake that
 * would go unnoticed for weeks.
 */
function readSettings(config) {
  const settings = config.reviewer_assignment;
  if (!settings) return null;
  const missing = [];
  for (const [section, keys] of Object.entries(REQUIRED_SETTINGS)) {
    for (const key of keys) {
      if (settings[section]?.[key] === undefined) missing.push(`${section}.${key}`);
    }
  }
  if (!settings.reviewers || typeof settings.reviewers !== 'object') missing.push('reviewers');
  if (missing.length) {
    throw new Error(`reviewer_assignment is missing: ${missing.join(', ')}`);
  }
  return settings;
}

// GraphQL reports a GitHub App as "renovate", REST as "renovate[bot]".
function normalizeLogin(login) {
  return (login || '').replace(/\[bot\]$/i, '').toLowerCase();
}

function sameLogin(a, b) {
  return normalizeLogin(a) === normalizeLogin(b);
}

function isBotLogin(login) {
  return /\[bot\]$/i.test(login || '') || KNOWN_BOTS.has(normalizeLogin(login));
}

function isMaintainer(config, login) {
  return (config.maintainers || []).some(m => sameLogin(m, login));
}

// auto_accept holds maintainers' trusted identities as well as bots; only the
// non-maintainer entries are dependency bots.
function isDependencyBot(config, login) {
  return !isMaintainer(config, login)
    && (config.auto_accept || []).some(a => sameLogin(a, login));
}

// ---------------------------------------------------------------------------
// Interest
// ---------------------------------------------------------------------------

/**
 * The weight the most specific matching pattern gives `label`, or undefined
 * when no pattern matches.
 *
 *   area/ui     matches only area/ui
 *   area/ui/*   matches area/ui and every label nested under it
 *   area/*      matches every area label
 *
 * Deeper patterns beat shallower ones, and an exact pattern beats a subtree
 * pattern for the same label — so `area/*: 0.4` with `area/ui/editors: 0`
 * means "everything except the editors".
 */
function interestWeight(patterns, label) {
  const target = label.toLowerCase();
  let best;
  let bestRank = -1;
  for (const [pattern, weight] of Object.entries(patterns || {})) {
    const p = pattern.toLowerCase();
    let rank = -1;
    if (p.endsWith('/*')) {
      const base = p.slice(0, -2);
      if (target === base || target.startsWith(`${base}/`)) rank = base.split('/').length * 2;
    } else if (target === p) {
      rank = p.split('/').length * 2 + 1;
    }
    if (rank > bestRank) {
      bestRank = rank;
      best = weight;
    }
  }
  return best;
}

/**
 * The PR's area labels minus any that another of its labels nests under.
 * Ancestors are implied by their children — area/storage adds nothing once
 * area/storage/sql is there — and counting them would double-weight a family.
 */
function mostSpecificLabels(labels) {
  const area = [...new Set(labels.filter(l => l.toLowerCase().startsWith('area/')))];
  return area.filter(label => !area.some(other =>
    other.toLowerCase().startsWith(`${label.toLowerCase()}/`)));
}

/**
 * The reviewer's highest weight among the PR's most specific labels; 0
 * without labels.
 *
 * The highest, not the average: PRs carry 2-3 labels on average and several
 * are incidental (area/QE for touching a test, area/build for touching a
 * pom), and averaging let every one of them dilute a specialist out of their
 * own area. Measured on 334 contributor PRs, averaging routed 8 of 17
 * documentation PRs and 12 of 19 CLI PRs to the specialist; the highest
 * weight routes 14 and 16. The noise that the average was absorbing is
 * handled by the swap looking only at the primary area instead.
 */
function interestScore(patterns, labels) {
  if (!labels.length) return 0;
  return Math.max(...labels.map(label => interestWeight(patterns, label) ?? 0));
}

/**
 * The label the classifier was most confident about, among the PR's most
 * specific labels — what the PR is mainly about. Null when none of them has
 * a classifier score (the classification step failed, or wrote no output).
 */
function primaryLabel(labels, scores) {
  const scored = labels.filter(l => typeof scores?.[l] === 'number');
  if (!scored.length) return null;
  return scored.sort((a, b) => scores[b] - scores[a] || a.localeCompare(b))[0];
}

// ---------------------------------------------------------------------------
// Ownership
// ---------------------------------------------------------------------------

function globToRegExp(glob) {
  let source = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      if (glob[i + 2] === '/') {
        source += '(?:.*/)?';
        i += 2;
      } else {
        source += '.*';
        i += 1;
      }
    } else if (c === '*') {
      source += '[^/]*';
    } else if (c === '?') {
      source += '[^/]';
    } else {
      source += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${source}$`);
}

/**
 * The paths whose history is worth a commits lookup, largest change first.
 * Added files have no history; ignored paths (lockfiles, generated code) have
 * plenty, but it says nothing about who understands the change. A renamed
 * file's history lives under its previous name.
 */
function selectOwnershipFiles(files, ownership) {
  const ignored = (ownership.ignore_paths || []).map(globToRegExp);
  return files
    .filter(f => f.status !== 'added' && !ignored.some(re => re.test(f.filename)))
    .sort((a, b) => (b.changes || 0) - (a.changes || 0) || a.filename.localeCompare(b.filename))
    .slice(0, ownership.max_files)
    .map(f => (f.status === 'renamed' && f.previous_filename) ? f.previous_filename : f.filename);
}

function monthsBefore(date, months) {
  const d = new Date(date);
  d.setUTCMonth(d.getUTCMonth() - months);
  return d;
}

/**
 * Ownership per candidate, from the recent commits of each examined file.
 *
 * Each file is split into shares among the candidates who committed to it,
 * weighted by recency, and the shares are averaged over the files. Per-file
 * rather than pooled so that one file with a long history cannot outvote the
 * other twenty the PR touches.
 *
 * A file whose recent history is all contributors still counts, as a 0 for
 * everyone: that is weaker evidence about the PR as a whole, and the score
 * should say so. A file touched only by bots does not count at all.
 */
function ownershipFromCommits(commitsByFile, candidates, ownership, now) {
  const recentCutoff = monthsBefore(now, ownership.recent_months);
  const totals = new Map(candidates.map(c => [c, 0]));
  let files = 0;
  for (const commits of commitsByFile) {
    const human = commits.filter(c => c.login && !isBotLogin(c.login));
    if (!human.length) continue;
    files++;
    const perCandidate = new Map();
    for (const commit of human) {
      const candidate = candidates.find(c => sameLogin(c, commit.login));
      if (!candidate) continue;
      const weight = new Date(commit.date) >= recentCutoff ? ownership.recent_multiplier : 1;
      perCandidate.set(candidate, (perCandidate.get(candidate) || 0) + weight);
    }
    const fileTotal = [...perCandidate.values()].reduce((a, b) => a + b, 0);
    for (const [candidate, weight] of perCandidate) {
      totals.set(candidate, totals.get(candidate) + weight / fileTotal);
    }
  }
  return new Map([...totals].map(([c, total]) => [c, files ? total / files : 0]));
}

async function fetchCommitsByFile(github, owner, repo, paths, ownership, now, core) {
  const since = monthsBefore(now, ownership.since_months).toISOString();
  const until = new Date(now).toISOString();
  const result = [];
  for (const filePath of paths) {
    try {
      const { data } = await github.rest.repos.listCommits({
        owner, repo, path: filePath, since, until, per_page: ownership.commits_per_file,
      });
      result.push(data.map(c => ({ login: c.author?.login, date: c.commit?.author?.date })));
    } catch (e) {
      core.warning(`Could not read the history of ${filePath}: ${e.message}`);
    }
  }
  return result;
}

// ---------------------------------------------------------------------------
// Issue authorship
// ---------------------------------------------------------------------------

/**
 * Authors of the issues this PR closes. GitHub's own closing references, so
 * keyword links in the body and sidebar links both count. A failed lookup is
 * no signal, not a failure.
 */
async function fetchLinkedIssueAuthors(github, owner, repo, prNumber, core) {
  try {
    const data = await github.graphql(
      `query($owner: String!, $repo: String!, $number: Int!) {
        repository(owner: $owner, name: $repo) {
          pullRequest(number: $number) {
            closingIssuesReferences(first: 10) { nodes { number author { login } } }
          }
        }
      }`,
      { owner, repo, number: prNumber });
    const nodes = data.repository.pullRequest.closingIssuesReferences.nodes || [];
    return nodes.map(n => n.author?.login).filter(Boolean);
  } catch (e) {
    core.warning(`Could not read the issues this PR closes: ${e.message}`);
    return [];
  }
}

// ---------------------------------------------------------------------------
// Load
// ---------------------------------------------------------------------------

/**
 * Recent assignment load per candidate, split into contributor PRs and
 * dependency bot PRs so that ~3 bot PRs a day cannot drown out the balance the
 * swap keeps between reviewers of contributor PRs.
 *
 * Counts every PR assigned to the candidate within the window whatever its
 * state now — open, merged or closed — so that working through a queue is not
 * penalised. Assignments in the last `recent_hours` count `recent_multiplier`
 * times, so a burst spreads even before the 30-day totals move. A maintainer
 * assigned to their own PR is not carrying review load and is not counted.
 */
async function fetchAssignmentLoads(github, owner, repo, config, candidates, fairness, now, excludePr) {
  const windowStart = new Date(now - fairness.window_days * DAY_MS);
  const recentStart = new Date(now - fairness.recent_hours * HOUR_MS);
  const since = windowStart.toISOString().slice(0, 10);

  const vars = { };
  const fields = candidates.map((login, i) => {
    vars[`q${i}`] = `repo:${owner}/${repo} is:pr assignee:${login} updated:>=${since}`;
    return `u${i}: search(type: ISSUE, first: 100, query: $q${i}) {
      nodes { ... on PullRequest {
        number
        author { __typename login }
        timelineItems(itemTypes: [ASSIGNED_EVENT], last: 20) {
          nodes { ... on AssignedEvent { createdAt assignee { ... on User { login } } } }
        }
      } }
    }`;
  });
  const decl = candidates.map((_, i) => `$q${i}: String!`).join(', ');
  const data = await github.graphql(`query(${decl}) { ${fields.join('\n')} }`, vars);

  const loads = new Map();
  candidates.forEach((login, i) => {
    const load = { contributor: 0, bot: 0, lastBotAssignment: null };
    for (const node of data[`u${i}`]?.nodes || []) {
      if (!node?.number || node.number === excludePr) continue;
      if (sameLogin(node.author?.login, login)) continue;
      const assigned = (node.timelineItems?.nodes || [])
        .filter(e => sameLogin(e?.assignee?.login, login))
        .map(e => new Date(e.createdAt))
        .sort((a, b) => b - a)[0];
      if (!assigned || assigned < windowStart) continue;
      const weight = assigned >= recentStart ? fairness.recent_multiplier : 1;
      const isBot = node.author?.__typename === 'Bot' || isDependencyBot(config, node.author?.login);
      if (isBot) {
        load.bot += weight;
        if (!load.lastBotAssignment || assigned > load.lastBotAssignment) load.lastBotAssignment = assigned;
      } else {
        load.contributor += weight;
      }
    }
    loads.set(login, load);
  });
  return loads;
}

// ---------------------------------------------------------------------------
// Ranking
// ---------------------------------------------------------------------------

/**
 * Scores and orders the candidates, then applies the swap.
 *
 * `signals` holds per-candidate `interest` and `ownership` (0-1), the
 * `issueAuthors` who opened a linked issue, each candidate's contributor
 * `load`, whether the PR has any area labels (`labelled`), and its `primary`
 * area label.
 *
 * The swap: if the winner's load is well above the runner-up's — both
 * `swap_ratio` times and `swap_min_gap` more — the runner-up gets it, however
 * far behind on score. Both conditions, because at ~40 assignments a month an
 * absolute gap of 3 is noise, and at 2-vs-1 a ratio of 1.5 is too. The swap is
 * what keeps git ownership from piling PRs on whoever has committed most: in
 * the backtest, one reviewer got 43% of contributor PRs without it.
 *
 * It only swaps to a runner-up who wants PRs in the primary area. Specialists
 * carry low load because they cover few areas, not because they are free, so
 * an unguarded swap handed them PRs whose only link to their area was an
 * incidental label (a UI PR that also touched docs going to the docs
 * reviewer). A PR without area labels has no area to be out of scope for, so
 * any runner-up may take it; a labelled PR whose primary area is unknown
 * (no classifier scores) is not swapped at all.
 *
 * Returns `swap` whenever the load condition held, with `applied` saying
 * whether the runner-up actually took the PR, so the comment can explain an
 * overloaded winner who kept it.
 */
function rankCandidates(candidates, signals, settings) {
  const w = settings.weights;
  const rows = candidates.map(login => ({
    login,
    interest: signals.interest.get(login) || 0,
    ownership: signals.ownership.get(login) || 0,
    issueAuthor: signals.issueAuthors.some(a => sameLogin(a, login)),
    load: signals.load.get(login) || 0,
  }));

  const eligible = rows.filter(r => r.interest > 0 || r.ownership > 0 || r.issueAuthor);
  const fallback = eligible.length === 0;
  const ranked = fallback ? rows : eligible;
  const notConsidered = rows.filter(r => !ranked.includes(r)).map(r => r.login);

  for (const r of ranked) {
    r.parts = {
      ownership: w.ownership * r.ownership,
      interest: w.interest * r.interest,
      issue_author: w.issue_author * (r.issueAuthor ? 1 : 0),
    };
    r.score = Object.values(r.parts).reduce((a, b) => a + b, 0);
  }
  ranked.sort((a, b) => b.score - a.score || a.load - b.load || a.login.localeCompare(b.login));

  const [top, next] = ranked;
  let winner = top;
  let swap = null;
  const f = settings.fairness;
  if (top && next
      && top.load >= f.swap_ratio * next.load
      && top.load - next.load >= f.swap_min_gap) {
    let applied;
    if (!signals.labelled) applied = true;
    else if (!signals.primary) applied = false;
    else applied = (interestWeight(settings.reviewers[next.login]?.interest, signals.primary) ?? 0) > 0;
    swap = { from: top, to: next, applied };
    if (applied) winner = next;
  }
  const runnerUp = winner === top ? next : top;
  return { ranked, winner, runnerUp, swap, fallback, notConsidered };
}

/** The least-loaded bot reviewer; ties go to whoever had a bot PR longest ago. */
function pickRotation(candidates, loads) {
  return [...candidates].sort((a, b) => {
    const la = loads.get(a);
    const lb = loads.get(b);
    return la.bot - lb.bot
      || (la.lastBotAssignment?.getTime() ?? 0) - (lb.lastBotAssignment?.getTime() ?? 0)
      || a.localeCompare(b);
  })[0];
}

// ---------------------------------------------------------------------------
// Comment
// ---------------------------------------------------------------------------

const TUNING_DOC = '.github/PR_LIFECYCLE.md#adjusting-which-prs-are-assigned-to-you';

function fmt(n) {
  return n.toFixed(2);
}

/**
 * One visible line for the contributor; the reasoning goes in a collapsed
 * section for maintainers tuning the knobs — the candidate table (raw signals,
 * with the weights in the headers, so each column can be compared directly
 * with the config), the labels with the primary area marked, the swap, who was
 * not considered, and links to the tuning docs and the run log.
 *
 * Logins other than the assignee are in code spans, not @-mentions, so nobody
 * is notified about a PR they were not given.
 */
function formatComment(result, settings, { suggestOnly, labels, primary, links = {} }) {
  const { ranked, winner, swap, fallback, notConsidered } = result;
  const w = settings.weights;
  const days = settings.fairness.window_days;

  const lines = [COMMENT_MARKER];
  lines.push(suggestOnly
    ? `Suggested reviewer: \`${winner.login}\`. Not assigned: maintainers choose their own reviewer.`
    : `Auto-assigned to @${winner.login}.`);
  lines.push('', '<details>', '<summary>Why this reviewer</summary>', '');

  lines.push(`| Reviewer | Score | Ownership ×${fmt(w.ownership)} | Interest ×${fmt(w.interest)} ` +
    `| Issue author ×${fmt(w.issue_author)} | Load (${days}d) |`);
  lines.push('|---|--:|--:|--:|:-:|--:|');
  for (const r of ranked) {
    const name = r === winner ? `**\`${r.login}\`**` : `\`${r.login}\``;
    lines.push(`| ${name} | ${fmt(r.score)} | ${fmt(r.ownership)} | ${fmt(r.interest)} ` +
      `| ${r.issueAuthor ? 'yes' : ''} | ${r.load} |`);
  }
  lines.push('');

  const areaList = labels.map(l => (l === primary ? `\`${l}\` (primary)` : `\`${l}\``)).join(', ');
  lines.push(`Areas: ${areaList || 'none'}`);
  if (swap?.applied) {
    lines.push(`Swapped: \`${swap.from.login}\` scored highest but has ${swap.from.load} ` +
      `assignments in the last ${days} days against ${swap.to.load}, so the runner-up got it.`);
  } else if (swap) {
    const why = primary
      ? `\`${swap.to.login}\` has no interest in the primary area`
      : 'the primary area is unknown';
    lines.push(`Not swapped: \`${swap.from.login}\` has ${swap.from.load} assignments in the ` +
      `last ${days} days against ${swap.to.load}, but ${why}.`);
  }
  if (fallback) {
    lines.push('No reviewer had an area interest, recent commits or a linked issue, ' +
      'so everyone was considered.');
  } else if (notConsidered.length) {
    lines.push(`Not considered (no area interest, recent commits or linked issue): ` +
      `${notConsidered.map(l => `\`${l}\``).join(', ')}`);
  }

  const linkParts = [];
  if (links.tuning) linkParts.push(`[How to adjust assignment](${links.tuning})`);
  if (links.run) linkParts.push(`[Run log](${links.run})`);
  if (linkParts.length) lines.push('', linkParts.join(' · '));
  lines.push('', '</details>');
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

/**
 * The classification step's --output-json file, reduced to what assignment
 * uses:
 *   - `labels`: what it selected, minus what it declined to re-add because a
 *     human removed it before. In a live run these are on the PR already; in a
 *     dry run they are not, and this is what makes a dry run score the PR the
 *     way a live one would.
 *   - `scores`: its similarity score for every configured area label, which
 *     is how the PR's primary area is chosen — including for labels a human
 *     added, since every label is scored.
 * Empty when the file is missing or unreadable.
 */
function readClassification(filePath, core) {
  const empty = { labels: [], scores: {} };
  if (!filePath || !fs.existsSync(filePath)) return empty;
  try {
    const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    const suppressed = new Set(data.area_labels_suppressed || []);
    const labels = (data.area_labels_selected || [])
      .filter(l => typeof l === 'string' && l.startsWith('area/') && !suppressed.has(l));
    const scores = {};
    for (const [label, score] of Object.entries(data.area_label_scores || {})) {
      if (typeof score === 'number') scores[label] = score;
    }
    return { labels, scores };
  } catch (e) {
    core.warning(`Could not read the classification output ${filePath}: ${e.message}`);
    return empty;
  }
}

async function hasMarkerComment(github, owner, repo, prNumber) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: prNumber, per_page: 100,
  });
  return comments.some(c => (c.body || '').includes(COMMENT_MARKER));
}

function candidatePool(config, logins, author, core) {
  return logins.filter(login => {
    if (!isMaintainer(config, login)) {
      core.warning(`reviewer_assignment lists ${login}, who is not in maintainers; ignoring`);
      return false;
    }
    return !sameLogin(login, author);
  });
}

/**
 * Entry point, called from classify.yml. Returns what it decided (for tests
 * and the log), or null when it skipped the PR.
 */
async function assignReviewer({
  github, context, core, prNumber, dryRun = false, classificationPath, config, now = new Date(),
}) {
  config = config || loadConfig();
  const settings = readSettings(config);
  if (!settings) {
    core.info('reviewer_assignment is not configured, skipping');
    return null;
  }
  const { owner, repo } = context.repo;
  const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: prNumber });
  const author = pr.user.login;

  if (pr.state !== 'open' || pr.draft) {
    core.info(`PR #${prNumber} is ${pr.draft ? 'a draft' : pr.state}, skipping`);
    return null;
  }

  let mode;
  if (isDependencyBot(config, author)) mode = 'rotate';
  else if (isMaintainer(config, author)) mode = 'suggest';
  else if (isBotLogin(author)) mode = null;
  else mode = 'assign';
  if (!mode) {
    core.info(`PR #${prNumber} is by ${author}, a bot outside auto_accept, skipping`);
    return null;
  }

  if (mode !== 'suggest' && (pr.assignees || []).length) {
    core.info(`PR #${prNumber} already has an assignee, skipping`);
    return null;
  }
  if (mode === 'suggest'
      && ((pr.requested_reviewers || []).length || (pr.requested_teams || []).length)) {
    core.info(`PR #${prNumber} already has a review requested, skipping`);
    return null;
  }
  if (mode !== 'rotate' && await hasMarkerComment(github, owner, repo, prNumber)) {
    core.info(`PR #${prNumber} already has a reviewer assignment comment, skipping`);
    return null;
  }

  // --- Dependency bot rotation --------------------------------------------
  if (mode === 'rotate') {
    const candidates = candidatePool(config, settings.bot_rotation.reviewers, author, core);
    if (!candidates.length) {
      core.info('bot_rotation has no reviewers, skipping');
      return null;
    }
    let loads;
    try {
      loads = await fetchAssignmentLoads(github, owner, repo, config, candidates,
        settings.fairness, now, prNumber);
    } catch (e) {
      // Without loads the rotation would always pick the same person; better
      // to leave this one unassigned and visible in the log.
      core.warning(`Could not read assignment loads, not assigning: ${e.message}`);
      return null;
    }
    const pick = pickRotation(candidates, loads);
    const summary = candidates.map(c => `${c}=${loads.get(c).bot}`).join(', ');
    core.info(`PR #${prNumber} bot rotation: ${pick} (bot PR load: ${summary})`);
    if (!dryRun) {
      await github.rest.issues.addAssignees({ owner, repo, issue_number: prNumber, assignees: [pick] });
    }
    return { mode, winner: pick, dryRun };
  }

  // --- Scored assignment / suggestion -------------------------------------
  const candidates = candidatePool(config, Object.keys(settings.reviewers), author, core);
  if (!candidates.length) {
    core.info('reviewer_assignment has no eligible reviewers for this PR, skipping');
    return null;
  }

  const classification = readClassification(classificationPath, core);
  const labels = mostSpecificLabels([
    ...(pr.labels || []).map(l => l.name),
    ...classification.labels,
  ]);
  const primary = primaryLabel(labels, classification.scores);
  const interest = new Map(candidates.map(c =>
    [c, interestScore(settings.reviewers[c]?.interest, labels)]));

  const files = await github.paginate(github.rest.pulls.listFiles, {
    owner, repo, pull_number: prNumber, per_page: 100,
  });
  const paths = selectOwnershipFiles(files, settings.ownership);
  const commitsByFile = await fetchCommitsByFile(github, owner, repo, paths, settings.ownership, now, core);
  const ownership = ownershipFromCommits(commitsByFile, candidates, settings.ownership, now);

  const issueAuthors = await fetchLinkedIssueAuthors(github, owner, repo, prNumber, core);

  let load = new Map();
  try {
    const loads = await fetchAssignmentLoads(github, owner, repo, config, candidates,
      settings.fairness, now, prNumber);
    load = new Map([...loads].map(([c, l]) => [c, l.contributor]));
  } catch (e) {
    core.warning(`Could not read assignment loads, so no swap and no load tie-break: ${e.message}`);
  }

  const result = rankCandidates(candidates,
    { interest, ownership, issueAuthors, load, labelled: labels.length > 0, primary }, settings);
  core.info(`PR #${prNumber} areas: ${labels.join(', ') || '(none)'} (primary: ${primary || 'none'}); ` +
    `${paths.length} file(s) examined; linked issue authors: ${issueAuthors.join(', ') || '(none)'}`);
  for (const r of result.ranked) {
    core.info(`  ${r.login}: score ${fmt(r.score)} = ownership ${fmt(r.parts.ownership)} ` +
      `+ interest ${fmt(r.parts.interest)} + issue author ${fmt(r.parts.issue_author)} ` +
      `(raw: ownership ${fmt(r.ownership)}, interest ${fmt(r.interest)}; load ${r.load})`);
  }
  if (result.notConsidered.length) {
    core.info(`  not considered: ${result.notConsidered.join(', ')}`);
  }
  if (result.swap) {
    core.info(`  swap ${result.swap.from.login} -> ${result.swap.to.login}: ` +
      `${result.swap.applied ? 'applied' : 'blocked (runner-up not interested in the primary area)'}`);
  }

  const suggestOnly = mode === 'suggest';
  const serverUrl = context.serverUrl || 'https://github.com';
  const links = {
    tuning: `${serverUrl}/${owner}/${repo}/blob/main/${TUNING_DOC}`,
    run: context.runId ? `${serverUrl}/${owner}/${repo}/actions/runs/${context.runId}` : undefined,
  };
  const body = formatComment(result, settings, { suggestOnly, labels, primary, links });
  if (dryRun) {
    core.info(`[DRY RUN] would post:\n${body}`);
  } else {
    if (!suggestOnly) {
      await github.rest.issues.addAssignees({
        owner, repo, issue_number: prNumber, assignees: [result.winner.login],
      });
    }
    await github.rest.issues.createComment({ owner, repo, issue_number: prNumber, body });
  }
  core.info(`PR #${prNumber} ${suggestOnly ? 'suggested' : 'assigned'}: ${result.winner.login}`);
  return { mode, winner: result.winner.login, result, body, dryRun };
}

module.exports = {
  assignReviewer,
  COMMENT_MARKER,
  // exported for tests and the calibration backtest
  readSettings,
  interestWeight,
  mostSpecificLabels,
  interestScore,
  primaryLabel,
  globToRegExp,
  selectOwnershipFiles,
  ownershipFromCommits,
  fetchCommitsByFile,
  fetchLinkedIssueAuthors,
  fetchAssignmentLoads,
  rankCandidates,
  pickRotation,
  formatComment,
  readClassification,
};
