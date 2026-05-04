#!/usr/bin/env python3
"""
Classify GitHub issues by assigning area labels and issue type
using sentence embeddings and cosine similarity.

Usage:
    python classify.py --repo OWNER/REPO --issue NUMBER
    python classify.py --repo OWNER/REPO --issue NUMBER --dry-run
"""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

import yaml
import numpy as np
from sentence_transformers import SentenceTransformer


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


def get_issue_type(repo, number):
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
        return None
    data = json.loads(result.stdout)
    issue = data["data"]["repository"].get("issue")
    if not issue:
        return None
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


def classify_area_labels(issue_embedding, model, config):
    label_config = config["area_labels"]
    threshold = label_config["threshold"]
    max_labels = label_config["max_labels"]
    labels = label_config["labels"]

    scores = {}
    for label_name, label_info in labels.items():
        label_embedding = model.encode(label_info["description"])
        score = cosine_similarity(issue_embedding, label_embedding)
        scores[label_name] = float(score)

    sorted_labels = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    selected = [
        (name, score) for name, score in sorted_labels
        if score >= labels[name].get("threshold", threshold)
    ]
    selected = selected[:max_labels]

    result = set(name for name, _ in selected)
    for name, _ in selected:
        parent = labels[name].get("parent")
        if parent:
            result.add(parent)

    return result, scores


def classify_issue_type(issue_embedding, model, config):
    type_config = config["issue_types"]
    threshold = type_config["threshold"]
    types = type_config["types"]

    scores = {}
    for type_name, type_info in types.items():
        type_embedding = model.encode(type_info["description"])
        score = cosine_similarity(issue_embedding, type_embedding)
        scores[type_name] = float(score)

    best_type, best_score = max(scores.items(), key=lambda x: x[1])
    if best_score >= threshold:
        type_id = types[best_type]["id"]
        return best_type, type_id, scores
    return None, None, scores


def apply_labels(repo, number, labels):
    for label in labels:
        subprocess.run(
            ["gh", "issue", "edit", str(number),
             "--repo", repo, "--add-label", label],
            check=True,
        )


def apply_issue_type(repo, number, type_id):
    issue_node_id = get_issue_node_id(repo, number)
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


def main():
    parser = argparse.ArgumentParser(description="Classify GitHub issues")
    parser.add_argument("--repo", required=True, help="owner/repo")
    parser.add_argument("--issue", required=True, type=int, help="Issue number")
    parser.add_argument("--dry-run", action="store_true", help="Print results without applying")
    args = parser.parse_args()

    config = load_config()

    print(f"Fetching issue #{args.issue} from {args.repo}...")
    issue = get_issue(args.repo, args.issue)
    title = issue.get("title", "")
    body = issue.get("body", "") or ""
    existing_labels = {l["name"] for l in issue.get("labels", [])}
    existing_area_labels = {l for l in existing_labels if l.startswith("area/")}

    issue_text = f"{title}\n\n{body}"
    # Truncate very long issue bodies to avoid excessive embedding time
    if len(issue_text) > 8000:
        issue_text = issue_text[:8000]

    print(f"Loading model '{config['model']}'...")
    model = SentenceTransformer(config["model"])

    print("Computing embeddings...")
    issue_embedding = model.encode(issue_text)

    # --- Area labels ---
    new_labels, area_scores = classify_area_labels(issue_embedding, model, config)
    # Don't re-apply labels that already exist
    labels_to_add = new_labels - existing_area_labels

    print("\n=== Area Label Scores ===")
    threshold = config["area_labels"]["threshold"]
    for label, score in sorted(area_scores.items(), key=lambda x: x[1], reverse=True):
        marker = ">>>" if label in new_labels else "   "
        print(f"  {marker} {label}: {score:.4f} (threshold: {threshold})")

    if labels_to_add:
        print(f"\nLabels to add: {', '.join(sorted(labels_to_add))}")
    else:
        print("\nNo new area labels to add.")

    # --- Issue type ---
    current_type = get_issue_type(args.repo, args.issue)
    type_name, type_id, type_scores = classify_issue_type(issue_embedding, model, config)

    print("\n=== Issue Type Scores ===")
    type_threshold = config["issue_types"]["threshold"]
    for tname, score in sorted(type_scores.items(), key=lambda x: x[1], reverse=True):
        marker = ">>>" if tname == type_name else "   "
        print(f"  {marker} {tname}: {score:.4f} (threshold: {type_threshold})")

    should_set_type = type_name and not current_type
    if current_type:
        print(f"\nIssue already has type '{current_type['name']}', skipping.")
    elif type_name:
        print(f"\nWill set issue type to: {type_name}")
    else:
        print("\nNo issue type above threshold.")

    # --- Apply ---
    if args.dry_run:
        print("\n[DRY RUN] No changes applied.")
        return

    if labels_to_add:
        print(f"\nApplying labels...")
        apply_labels(args.repo, args.issue, labels_to_add)
        print("Labels applied.")

    if should_set_type:
        print(f"Setting issue type to '{type_name}'...")
        apply_issue_type(args.repo, args.issue, type_id)
        print("Issue type set.")

    print("\nDone.")


if __name__ == "__main__":
    main()
