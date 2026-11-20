#!/usr/bin/env python3
"""
Classify GitHub issues and pull requests by assigning area labels
(and, for issues, an issue type) using sentence embeddings and cosine
similarity.

Usage:
    python classify.py --repo OWNER/REPO --issue NUMBER
    python classify.py --repo OWNER/REPO --issue NUMBER --dry-run
    python classify.py --repo OWNER/REPO --pr NUMBER
    python classify.py --repo OWNER/REPO --pr NUMBER --output-json scores.json

Issues and PRs share the same area label descriptions and thresholds. A PR
additionally contributes the directories it touches to the embedded text:
paths carry area signal that PR prose routinely omits ("fix NPE in the
resolver" says nothing, `app/src/.../storage/impl/sql/` says a great deal).

Issue types are not classified for PRs — GitHub's issue type field does not
exist on pull requests.
"""

import argparse
import json
import subprocess
from collections import OrderedDict
from pathlib import Path

import yaml
import numpy as np

# Directories, not files, and placed ahead of the body — both matter, because
# all-MiniLM-L6-v2 truncates its input at 256 word pieces and a typical PR
# description in this repository is well past that on its own (PR #10086: 1407
# tokens). Paths appended after the body are simply never seen by the model.
#
# Measured over 40 recent merged PRs, scoring whether the area label implied by
# the conventional-commit scope was assigned:
#
#   title + body                      65%   (paths ignored entirely)
#   title + body + files              68%   (files fall outside the window)
#   title + files + body              74%
#   title + directories + body        76%   <- this layout
#   title + directories               50%   (prose still carries real signal)
#
# Directories beat raw file paths because a PR touching 30 files in one package
# spends 30 slots saying the same thing; collapsing them leaves room for the
# other packages it touched.
#
# Raising the model's max_seq_length to its 512-position ceiling is NOT the fix
# for the truncation — it drops this layout to 62%. The model mean-pools its
# token embeddings, so a wider window dilutes the concentrated directory block
# under PR-template boilerplate. See the README for the full table.
MAX_PR_DIRS = 50

# Hard cap on the embedded text. Well past what the model reads, so it is a
# guard against pathological input rather than the effective limit.
MAX_TEXT_CHARS = 8000

_FAILED = object()


def _load_model(name):
    """Imported lazily so this module's pure functions can be unit-tested
    without pulling in sentence-transformers (a ~2 GB torch install)."""
    from sentence_transformers import SentenceTransformer
    return SentenceTransformer(name)


def load_config():
    config_path = Path(__file__).parent / "label-descriptions.yml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def get_issue(repo, number):
    """Title, body and labels. The issues endpoint serves pull requests too,
    so this is the shared metadata fetch for both kinds."""
    result = subprocess.run(
        ["gh", "api", f"repos/{repo}/issues/{number}"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def is_pull_request(target_data):
    """The issues endpoint serves both kinds; only pull requests carry this key."""
    return "pull_request" in target_data


def get_pr_files(repo, number):
    """Every changed file path, across all pages. Returns [] if the lookup
    fails — a PR still classifies on its title and body alone, just less
    accurately, and that beats failing the workflow."""
    result = subprocess.run(
        ["gh", "api", "--paginate", f"repos/{repo}/pulls/{number}/files",
         "--jq", ".[].filename"],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"Warning: could not list changed files: {result.stderr.strip()}")
        return []
    return [line for line in result.stdout.splitlines() if line]


def get_removed_area_labels(repo, number):
    """Every `area/*` label that has been taken off this issue or PR.

    Classification is not a one-shot event: issues reclassify on every edit and
    PRs on every draft/ready cycle. Without this, a maintainer who deletes a
    wrong label gets it back the next time anybody touches the description, and
    the only way to make a correction stick is to argue with a cron job.

    No new state is needed — GitHub's events timeline already records it.

    The `area/` filter is in the jq, not left to the caller. The timeline is
    dominated by the PR lifecycle orchestrator's `lifecycle/*` churn, which is
    irrelevant here and can run to dozens of entries on an active PR.

    Removals are not filtered by actor, which is safe only because nothing in
    .github/scripts removes an `area/*` label: pr-lifecycle.js:1107 is reached
    only via the label-guard job, which the workflow gates to `lifecycle/` and
    `orchestrator/` prefixes, and pr-validation.js only touches its own
    validation label. If some future automation starts removing area labels,
    this must begin filtering by actor — otherwise one bulk removal would
    suppress a label permanently. Filtering to `area/*` above does not help
    with that; it is about noise, not safety.
    """
    result = subprocess.run(
        ["gh", "api", "--paginate", f"repos/{repo}/issues/{number}/events",
         "--jq", '.[] | select(.event == "unlabeled") | .label.name '
                 '| select(startswith("area/"))'],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        # Degrade towards the old behaviour rather than towards applying
        # nothing: a missing history should not silently stop classification.
        print(f"Warning: could not read label history: {result.stderr.strip()}")
        return set()
    return {line for line in result.stdout.splitlines() if line}


def labels_to_apply(selected, existing_area_labels, removed_labels):
    """What to actually add: the classifier's picks, minus what is already
    there, minus anything a human has removed before."""
    return selected - existing_area_labels - removed_labels


def directories_of(file_paths):
    """Unique parent directories, in first-seen order. A file at the repository
    root stands in for itself, having no directory to collapse into."""
    return list(OrderedDict.fromkeys(
        "/".join(p.split("/")[:-1]) or p for p in file_paths))


def build_text(title, body, file_paths=None):
    """The string handed to the embedding model. Directories go between the
    title and the body so they land inside the model's 256-token window — see
    the MAX_PR_DIRS comment for why the ordering is load-bearing."""
    parts = [title]
    if file_paths:
        parts.append("\n".join(directories_of(file_paths)[:MAX_PR_DIRS]))
    parts.append(body)
    return "\n\n".join(parts)[:MAX_TEXT_CHARS]


def get_issue_type(repo, number):
    """Returns the issue type dict, None if no type is set, or _FAILED on query failure."""
    query = """
    query($owner: String!, $name: String!, $number: Int!) {
      repository(owner: $owner, name: $name) {
        issue(number: $number) {
          issueType { id name }
        }
      }
    }
    """
    owner, name = repo.split("/")
    result = subprocess.run(
        ["gh", "api", "graphql",
         "-f", f"query={query}",
         "-f", f"owner={owner}",
         "-f", f"name={name}",
         "-F", f"number={number}"],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        return _FAILED
    data = json.loads(result.stdout)
    issue = data["data"]["repository"].get("issue")
    if not issue:
        return _FAILED
    return issue.get("issueType")


def get_issue_node_id(repo, number):
    query = """
    query($owner: String!, $name: String!, $number: Int!) {
      repository(owner: $owner, name: $name) {
        issue(number: $number) { id }
      }
    }
    """
    owner, name = repo.split("/")
    result = subprocess.run(
        ["gh", "api", "graphql",
         "-f", f"query={query}",
         "-f", f"owner={owner}",
         "-f", f"name={name}",
         "-F", f"number={number}"],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None
    data = json.loads(result.stdout)
    issue = data["data"]["repository"].get("issue")
    return issue["id"] if issue else None


def cosine_similarity(a, b):
    return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))


def parent_of(name, labels):
    """The label `name` nests under: the longest configured label that its name
    extends by whole `/` segments, or None for a top-level label.

    Derived from the name rather than declared, so the hierarchy is exactly the
    one a maintainer sees on GitHub and cannot drift from it. "Longest
    configured" rather than "drop the last segment" so that a gap in the chain
    (area/a/b/c configured without area/a/b) still nests under area/a. The
    first segment on its own ("area") is a namespace, never a label."""
    parts = name.split("/")
    for end in range(len(parts) - 1, 1, -1):
        candidate = "/".join(parts[:end])
        if candidate in labels:
            return candidate
    return None


def ancestors_of(name, labels):
    """Every label `name` nests under, nearest first."""
    result = []
    parent = parent_of(name, labels)
    while parent:
        result.append(parent)
        parent = parent_of(parent, labels)
    return result


def classify_area_labels(issue_embedding, label_embeddings, config):
    """Pick area labels, preferring the most nested label available.

    Candidates are every label that clears its own threshold, taken strongest
    first. Each one either:
      - is skipped, if a more nested label under it is already picked — it
        comes along as an ancestor anyway;
      - takes over the slot of an ancestor already picked, without costing a
        second one — area/storage/sql replaces area/storage, it does not sit
        next to it;
      - or claims a new slot, while fewer than max_labels are taken.
    Finally every pick brings its ancestors, free of the budget.

    Nesting only ever narrows a pick; it never lowers a bar. A child still has
    to clear its own threshold — a strong area/AI score does not, by itself,
    make an issue area/AI/MCP.
    """
    label_config = config["area_labels"]
    default_threshold = label_config["threshold"]
    labels = label_config["labels"]
    max_labels = label_config["max_labels"]

    scores = {}
    for label_name in labels:
        score = cosine_similarity(issue_embedding, label_embeddings[label_name])
        scores[label_name] = float(score)

    # Name as the tie-breaker so equal scores cannot reorder between runs.
    qualified = sorted(
        (name for name, score in scores.items()
         if score >= labels[name].get("threshold", default_threshold)),
        key=lambda name: (-scores[name], name))

    picks = []
    for name in qualified:
        ancestors = ancestors_of(name, labels)
        if any(name in ancestors_of(pick, labels) for pick in picks):
            continue
        superseded = [pick for pick in picks if pick in ancestors]
        if superseded:
            # At most one: picks never nest under one another.
            picks[picks.index(superseded[0])] = name
        elif len(picks) < max_labels:
            picks.append(name)

    result = set(picks)
    for pick in picks:
        result.update(ancestors_of(pick, labels))

    return result, scores


def capped_labels(scores, config, selected):
    """Labels that cleared their threshold but lost to the max_labels cap.

    Without this, the score listing is quietly misleading during tuning: a
    label can sit well above its threshold and still not be assigned, with
    nothing in the output saying why. Parents added mechanically by
    classify_area_labels are never reported as capped — they are in `selected`
    regardless of their own score."""
    label_config = config["area_labels"]
    default_threshold = label_config["threshold"]
    labels = label_config["labels"]
    above = {
        name for name, score in scores.items()
        if score >= labels[name].get("threshold", default_threshold)
    }
    return above - selected


def classify_issue_type(issue_embedding, type_embeddings, config):
    type_config = config["issue_types"]
    threshold = type_config["threshold"]
    types = type_config["types"]

    scores = {}
    for type_name in types:
        score = cosine_similarity(issue_embedding, type_embeddings[type_name])
        scores[type_name] = float(score)

    best_type, best_score = max(scores.items(), key=lambda x: x[1])
    if best_score >= threshold:
        type_id = types[best_type]["id"]
        return best_type, type_id, scores
    return None, None, scores


def apply_labels(repo, number, labels, is_pr=False):
    subcommand = "pr" if is_pr else "issue"
    # Sorted, because `labels` is a set and Python randomises string hashing per
    # process: without this the log order differs between otherwise identical
    # runs, which makes two runs annoying to diff.
    for label in sorted(labels):
        subprocess.run(
            ["gh", subcommand, "edit", str(number),
             "--repo", repo, "--add-label", label],
            check=True,
        )


def apply_issue_type(repo, number, type_id):
    issue_node_id = get_issue_node_id(repo, number)
    if not issue_node_id:
        print("Warning: could not resolve issue node ID, skipping issue type assignment.")
        return
    mutation = """
    mutation($issueId: ID!, $typeId: ID!) {
      updateIssue(input: { id: $issueId, issueTypeId: $typeId }) {
        issue { id issueType { name } }
      }
    }
    """
    subprocess.run(
        ["gh", "api", "graphql",
         "-f", f"query={mutation}",
         "-f", f"issueId={issue_node_id}",
         "-f", f"typeId={type_id}"],
        check=True,
    )


def write_output_json(path, payload):
    """Machine-readable classification result, for callers that need the raw
    scores rather than the human-readable log (see #9005, reviewer assignment).
    Written even under --dry-run, with dry_run recorded in the payload, so a
    caller can inspect what would happen without it happening."""
    with open(path, "w") as f:
        json.dump(payload, f, indent=2, sort_keys=True)


def main():
    parser = argparse.ArgumentParser(description="Classify GitHub issues and pull requests")
    parser.add_argument("--repo", required=True, help="owner/repo")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--issue", type=int, help="Issue number")
    target.add_argument("--pr", type=int, help="Pull request number")
    parser.add_argument("--dry-run", action="store_true", help="Print results without applying")
    parser.add_argument("--output-json", metavar="PATH",
                        help="Write the scores and decisions to PATH as JSON")
    args = parser.parse_args()

    config = load_config()
    is_pr = args.pr is not None
    number = args.pr if is_pr else args.issue
    kind = "PR" if is_pr else "issue"

    print(f"Fetching {kind} #{number} from {args.repo}...")
    target_data = get_issue(args.repo, number)

    # Issue and PR numbers share one sequence, so --pr 9416 on an issue is an
    # easy mistake and silently misclassifies: it would skip the issue type and
    # look up changed files that do not exist. Fail loudly instead.
    actual_is_pr = is_pull_request(target_data)
    if actual_is_pr != is_pr:
        actual, asked = ("a pull request", "--issue") if actual_is_pr else ("an issue", "--pr")
        raise SystemExit(f"Error: #{number} is {actual}, but {asked} was used. "
                         f"Pass {'--pr' if actual_is_pr else '--issue'} instead.")

    title = target_data.get("title", "")
    body = target_data.get("body", "") or ""
    existing_labels = {l["name"] for l in target_data.get("labels", [])}
    existing_area_labels = {l for l in existing_labels if l.startswith("area/")}

    file_paths = []
    if is_pr:
        file_paths = get_pr_files(args.repo, number)
        dirs = directories_of(file_paths)
        print(f"Changed files: {len(file_paths)} in {len(dirs)} directories "
              f"({min(len(dirs), MAX_PR_DIRS)} used for classification)")

    text = build_text(title, body, file_paths)

    print(f"Loading model '{config['model']}'...")
    model = _load_model(config["model"])

    print("Computing embeddings...")
    target_embedding = model.encode(text)

    label_names = list(config["area_labels"]["labels"].keys())
    label_descs = [config["area_labels"]["labels"][n]["description"] for n in label_names]
    label_vecs = model.encode(label_descs)
    label_embeddings = dict(zip(label_names, label_vecs))

    # --- Area labels ---
    default_threshold = config["area_labels"]["threshold"]
    labels_config = config["area_labels"]["labels"]
    new_labels, area_scores = classify_area_labels(target_embedding, label_embeddings, config)
    removed_labels = get_removed_area_labels(args.repo, number)
    labels_to_add = labels_to_apply(new_labels, existing_area_labels, removed_labels)
    suppressed = (new_labels & removed_labels) - existing_area_labels

    max_labels = config["area_labels"]["max_labels"]
    capped = capped_labels(area_scores, config, new_labels)

    print("\n=== Area Label Scores ===")
    for label, score in sorted(area_scores.items(), key=lambda x: x[1], reverse=True):
        effective_threshold = labels_config[label].get("threshold", default_threshold)
        marker = ">>>" if label in new_labels else "   "
        note = f"  [capped: over threshold, but max_labels={max_labels}]" if label in capped else ""
        print(f"  {marker} {label}: {score:.4f} (threshold: {effective_threshold}){note}")

    if capped:
        print(f"\n{len(capped)} label(s) cleared their threshold but lost to "
              f"max_labels={max_labels}: {', '.join(sorted(capped))}")

    if suppressed:
        print(f"\nNot re-adding {len(suppressed)} label(s) removed earlier: "
              f"{', '.join(sorted(suppressed))}")

    if labels_to_add:
        print(f"\nLabels to add: {', '.join(sorted(labels_to_add))}")
    else:
        print("\nNo new area labels to add.")

    # --- Issue type ---
    # PRs have no issue type field, so the whole stage is skipped for them —
    # including the type embeddings, which would otherwise be computed and
    # thrown away.
    type_name, type_id, type_scores = None, None, {}
    should_set_type = False

    if not is_pr:
        type_names = list(config["issue_types"]["types"].keys())
        type_descs = [config["issue_types"]["types"][n]["description"] for n in type_names]
        type_vecs = model.encode(type_descs)
        type_embeddings = dict(zip(type_names, type_vecs))

        current_type = get_issue_type(args.repo, number)
        type_name, type_id, type_scores = classify_issue_type(
            target_embedding, type_embeddings, config)

        print("\n=== Issue Type Scores ===")
        type_threshold = config["issue_types"]["threshold"]
        for tname, score in sorted(type_scores.items(), key=lambda x: x[1], reverse=True):
            marker = ">>>" if tname == type_name else "   "
            print(f"  {marker} {tname}: {score:.4f} (threshold: {type_threshold})")

        if current_type is _FAILED:
            print("\nWarning: failed to fetch current issue type, skipping type assignment.")
        elif current_type:
            print(f"\nIssue already has type '{current_type['name']}', skipping.")
        elif type_name:
            print(f"\nWill set issue type to: {type_name}")
            should_set_type = True
        else:
            print("\nNo issue type above threshold.")

    # --- Apply ---
    applied_labels = []
    if args.dry_run:
        print("\n[DRY RUN] No changes applied.")
    else:
        if labels_to_add:
            print("\nApplying labels...")
            apply_labels(args.repo, number, labels_to_add, is_pr=is_pr)
            applied_labels = sorted(labels_to_add)
            print("Labels applied.")

        if should_set_type:
            print(f"Setting issue type to '{type_name}'...")
            apply_issue_type(args.repo, number, type_id)
            print("Issue type set.")

    if args.output_json:
        write_output_json(args.output_json, {
            "repo": args.repo,
            "number": number,
            "kind": "pr" if is_pr else "issue",
            "dry_run": args.dry_run,
            "area_label_scores": area_scores,
            "area_labels_selected": sorted(new_labels),
            "area_labels_capped": sorted(capped),
            "area_labels_suppressed": sorted(suppressed),
            "area_labels_applied": applied_labels,
            "issue_type_scores": type_scores,
            "issue_type_selected": type_name,
            "changed_files": file_paths,
        })
        print(f"Wrote classification output to {args.output_json}")

    print("\nDone.")


if __name__ == "__main__":
    main()
