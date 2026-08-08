#!/usr/bin/env python3
"""
Classify GitHub issues and pull requests by assigning area labels
(and, for issues, the issue type) using sentence embeddings and cosine similarity.

Pull requests are classified from their title, body, and changed file paths,
which lets the classification inform CI test group selection in the Verify
workflow (see verify.yaml "AI area-label augmentation").

Usage:
    python classify.py --repo OWNER/REPO --issue NUMBER
    python classify.py --repo OWNER/REPO --issue NUMBER --dry-run
    python classify.py --repo OWNER/REPO --pr NUMBER
    python classify.py --repo OWNER/REPO --pr NUMBER --dry-run
"""

import argparse
import json
import subprocess
from pathlib import Path

import yaml
import numpy as np
from sentence_transformers import SentenceTransformer

_FAILED = object()


def load_config():
    config_path = Path(__file__).parent / "label-descriptions.yml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def get_issue(repo, number):
    result = subprocess.run(
        ["gh", "api", f"repos/{repo}/issues/{number}"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def get_pull_request(repo, number):
    result = subprocess.run(
        ["gh", "api", f"repos/{repo}/pulls/{number}"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def get_changed_files(repo, number, limit=150):
    """Returns the file paths changed in a pull request (capped to `limit`)."""
    result = subprocess.run(
        ["gh", "api", f"repos/{repo}/pulls/{number}/files", "--paginate"],
        capture_output=True, text=True, check=True,
    )
    files = json.loads(result.stdout)
    return [f.get("filename", "") for f in files if f.get("filename")][:limit]


def build_text(title, body, extra=""):
    text = f"{title}\n\n{body or ''}"
    if extra:
        text += f"\n\n{extra}"
    if len(text) > 8000:
        text = text[:8000]
    return text


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


def classify_area_labels(issue_embedding, label_embeddings, config):
    label_config = config["area_labels"]
    default_threshold = label_config["threshold"]
    labels = label_config["labels"]

    scores = {}
    for label_name in labels:
        score = cosine_similarity(issue_embedding, label_embeddings[label_name])
        scores[label_name] = float(score)

    sorted_labels = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    selected = [
        (name, score) for name, score in sorted_labels
        if score >= labels[name].get("threshold", default_threshold)
    ]
    selected = selected[:label_config["max_labels"]]

    result = set(name for name, _ in selected)
    for name, _ in selected:
        parent = labels[name].get("parent")
        if parent:
            result.add(parent)

    return result, scores


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


def build_label_embeddings(model, config):
    label_names = list(config["area_labels"]["labels"].keys())
    label_descs = [config["area_labels"]["labels"][n]["description"] for n in label_names]
    label_vecs = model.encode(label_descs)
    return dict(zip(label_names, label_vecs))


def build_type_embeddings(model, config):
    type_names = list(config["issue_types"]["types"].keys())
    type_descs = [config["issue_types"]["types"][n]["description"] for n in type_names]
    type_vecs = model.encode(type_descs)
    return dict(zip(type_names, type_vecs))


def apply_labels(repo, number, labels):
    for label in labels:
        subprocess.run(
            ["gh", "issue", "edit", str(number),
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


def print_area_scores(area_scores, new_labels, config):
    default_threshold = config["area_labels"]["threshold"]
    labels_config = config["area_labels"]["labels"]
    print("\n=== Area Label Scores ===")
    for label, score in sorted(area_scores.items(), key=lambda x: x[1], reverse=True):
        effective_threshold = labels_config[label].get("threshold", default_threshold)
        marker = ">>>" if label in new_labels else "   "
        print(f"  {marker} {label}: {score:.4f} (threshold: {effective_threshold})")


def classify_issue(args, model, config, label_embeddings, type_embeddings):
    print(f"Fetching issue #{args.issue} from {args.repo}...")
    issue = get_issue(args.repo, args.issue)
    existing_labels = {l["name"] for l in issue.get("labels", [])}
    existing_area_labels = {l for l in existing_labels if l.startswith("area/")}

    text = build_text(issue.get("title", ""), issue.get("body", ""))

    print("Computing embeddings...")
    issue_embedding = model.encode(text)

    # --- Area labels ---
    new_labels, area_scores = classify_area_labels(issue_embedding, label_embeddings, config)
    labels_to_add = new_labels - existing_area_labels

    print_area_scores(area_scores, new_labels, config)

    if labels_to_add:
        print(f"\nLabels to add: {', '.join(sorted(labels_to_add))}")
    else:
        print("\nNo new area labels to add.")

    # --- Issue type ---
    current_type = get_issue_type(args.repo, args.issue)
    type_name, type_id, type_scores = classify_issue_type(issue_embedding, type_embeddings, config)

    print("\n=== Issue Type Scores ===")
    type_threshold = config["issue_types"]["threshold"]
    for tname, score in sorted(type_scores.items(), key=lambda x: x[1], reverse=True):
        marker = ">>>" if tname == type_name else "   "
        print(f"  {marker} {tname}: {score:.4f} (threshold: {type_threshold})")

    if current_type is _FAILED:
        print("\nWarning: failed to fetch current issue type, skipping type assignment.")
        should_set_type = False
    elif current_type:
        print(f"\nIssue already has type '{current_type['name']}', skipping.")
        should_set_type = False
    elif type_name:
        print(f"\nWill set issue type to: {type_name}")
        should_set_type = True
    else:
        print("\nNo issue type above threshold.")
        should_set_type = False

    # --- Apply ---
    if args.dry_run:
        print("\n[DRY RUN] No changes applied.")
        return

    if labels_to_add:
        print("\nApplying labels...")
        apply_labels(args.repo, args.issue, labels_to_add)
        print("Labels applied.")

    if should_set_type:
        print(f"Setting issue type to '{type_name}'...")
        apply_issue_type(args.repo, args.issue, type_id)
        print("Issue type set.")

    print("\nDone.")


def classify_pull_request(args, model, config, label_embeddings):
    print(f"Fetching pull request #{args.pr} from {args.repo}...")
    pr = get_pull_request(args.repo, args.pr)
    existing_labels = {l["name"] for l in pr.get("labels", [])}
    existing_area_labels = {l for l in existing_labels if l.startswith("area/")}

    print("Fetching changed files...")
    changed_files = get_changed_files(args.repo, args.pr)
    print(f"  {len(changed_files)} changed file(s).")

    extra = f"Changed files:\n{'\n'.join(changed_files)}" if changed_files else ""
    text = build_text(pr.get("title", ""), pr.get("body", ""), extra)

    print("Computing embeddings...")
    pr_embedding = model.encode(text)

    new_labels, area_scores = classify_area_labels(pr_embedding, label_embeddings, config)
    labels_to_add = new_labels - existing_area_labels

    print_area_scores(area_scores, new_labels, config)

    if labels_to_add:
        print(f"\nLabels to add: {', '.join(sorted(labels_to_add))}")
    else:
        print("\nNo new area labels to add.")

    # Structured result so downstream steps (e.g. the Verify decide job) can
    # consume it, and so workflow logs are greppable.
    result = {
        "pr": args.pr,
        "area_labels": sorted(new_labels),
        "labels_to_add": sorted(labels_to_add),
        "scores": area_scores,
        "changed_files": len(changed_files),
    }
    print(f"\nPR_CLASSIFICATION_JSON: {json.dumps(result)}")

    # --- Apply ---
    if args.dry_run:
        print("\n[DRY RUN] No changes applied.")
        return

    if labels_to_add:
        print("\nApplying labels...")
        apply_labels(args.repo, args.pr, labels_to_add)
        print("Labels applied.")

    print("\nDone.")


def main():
    parser = argparse.ArgumentParser(
        description="Classify GitHub issues and pull requests")
    parser.add_argument("--repo", required=True, help="owner/repo")
    parser.add_argument("--issue", type=int, help="Issue number")
    parser.add_argument("--pr", type=int, help="Pull request number")
    parser.add_argument("--dry-run", action="store_true", help="Print results without applying")
    args = parser.parse_args()

    if bool(args.issue) == bool(args.pr):
        parser.error("exactly one of --issue or --pr must be provided")

    config = load_config()

    print(f"Loading model '{config['model']}'...")
    model = SentenceTransformer(config["model"])

    print("Computing label embeddings...")
    label_embeddings = build_label_embeddings(model, config)
    type_embeddings = build_type_embeddings(model, config)

    if args.pr is not None:
        classify_pull_request(args, model, config, label_embeddings)
    else:
        classify_issue(args, model, config, label_embeddings, type_embeddings)


if __name__ == "__main__":
    main()
