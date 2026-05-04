#!/usr/bin/env python3
"""
Test classification accuracy by measuring recall and precision per label.

Phase 1 (recall): For each area label, fetches issues that have that label
and checks if the model would have assigned it.

Phase 2 (precision): Fetches a broad sample of recent issues and checks
for false positives (labels assigned by the model but not by humans).

Uses F2 score (recall-weighted) as the combined metric.

Usage:
    python test_classify.py --repo OWNER/REPO
    python test_classify.py --repo OWNER/REPO --labels area/auth area/ui
    python test_classify.py --repo OWNER/REPO --sample-size 10
    python test_classify.py --repo OWNER/REPO --recall-only
"""

import argparse
import json
import subprocess
from collections import defaultdict
from pathlib import Path

import yaml
import numpy as np
from sentence_transformers import SentenceTransformer


def load_config():
    config_path = Path(__file__).parent / "label-descriptions.yml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def fetch_issues_for_label(repo, label, limit):
    result = subprocess.run(
        ["gh", "issue", "list",
         "--repo", repo,
         "--label", label,
         "--state", "all",
         "--limit", str(limit),
         "--json", "number,title,body,labels"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def fetch_recent_issues(repo, limit):
    result = subprocess.run(
        ["gh", "issue", "list",
         "--repo", repo,
         "--state", "all",
         "--limit", str(limit),
         "--json", "number,title,body,labels"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def cosine_similarity(a, b):
    return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))


def issue_text(issue):
    title = issue.get("title", "")
    body = issue.get("body", "") or ""
    text = f"{title}\n\n{body}"
    if len(text) > 8000:
        text = text[:8000]
    return text


def classify_issue(issue_embedding, label_embeddings, labels_config, default_threshold, max_labels):
    scores = {}
    for label_name, label_emb in label_embeddings.items():
        scores[label_name] = float(cosine_similarity(issue_embedding, label_emb))

    sorted_labels = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    selected = [
        (name, score) for name, score in sorted_labels
        if score >= labels_config[name].get("threshold", default_threshold)
    ]
    selected = selected[:max_labels]

    result = set(name for name, _ in selected)
    for name, _ in selected:
        parent = labels_config[name].get("parent")
        if parent:
            result.add(parent)

    return result, scores


def fbeta(precision, recall, beta=2.0):
    if precision + recall == 0:
        return 0.0
    return (1 + beta ** 2) * (precision * recall) / ((beta ** 2 * precision) + recall)


def main():
    parser = argparse.ArgumentParser(description="Test label classification accuracy")
    parser.add_argument("--repo", required=True, help="owner/repo")
    parser.add_argument("--labels", nargs="*", help="Specific labels to test (default: all)")
    parser.add_argument("--sample-size", type=int, default=10,
                        help="Max issues per label for recall test (default: 10)")
    parser.add_argument("--precision-sample", type=int, default=50,
                        help="Number of recent issues for precision test (default: 50)")
    parser.add_argument("--recall-only", action="store_true",
                        help="Skip precision test")
    args = parser.parse_args()

    config = load_config()
    label_config = config["area_labels"]
    default_threshold = label_config["threshold"]
    max_labels = label_config["max_labels"]
    all_labels = label_config["labels"]

    labels_to_test = all_labels
    if args.labels:
        labels_to_test = {k: v for k, v in all_labels.items() if k in args.labels}
        missing = set(args.labels) - set(labels_to_test.keys())
        if missing:
            print(f"Warning: labels not found in config: {', '.join(missing)}")

    print(f"Loading model '{config['model']}'...")
    model = SentenceTransformer(config["model"])

    print("Embedding label descriptions...")
    label_embeddings = {}
    for label_name, label_info in all_labels.items():
        label_embeddings[label_name] = model.encode(label_info["description"])

    # ── Phase 1: Recall ──────────────────────────────────────────
    print(f"\n{'#' * 60}")
    print("PHASE 1: RECALL")
    print(f"{'#' * 60}")

    recall_data = {}
    recall_issue_numbers = set()

    for label_name, label_info in labels_to_test.items():
        threshold = label_info.get("threshold", default_threshold)
        print(f"\n{'=' * 60}")
        print(f"Testing: {label_name} (threshold: {threshold})")
        print(f"{'=' * 60}")

        issues = fetch_issues_for_label(args.repo, label_name, args.sample_size)
        if not issues:
            print(f"  No issues found with label '{label_name}'")
            recall_data[label_name] = {"found": 0, "total": 0, "missed": [], "scores": []}
            continue

        for iss in issues:
            recall_issue_numbers.add(iss["number"])

        embeddings = model.encode([issue_text(iss) for iss in issues])

        found = 0
        missed = []
        scores_list = []

        for i, issue in enumerate(issues):
            score = cosine_similarity(embeddings[i], label_embeddings[label_name])
            scores_list.append(float(score))

            if score >= threshold:
                found += 1
                print(f"  OK   #{issue['number']} (score: {score:.4f}) {issue['title'][:70]}")
            else:
                missed.append({
                    "number": issue["number"],
                    "title": issue["title"],
                    "score": float(score),
                })
                print(f"  MISS #{issue['number']} (score: {score:.4f}) {issue['title'][:70]}")

        total = len(issues)
        recall = found / total if total > 0 else 0
        scores_arr = np.array(scores_list)

        print(f"\n  Recall: {found}/{total} ({recall:.0%})")
        print(f"  Scores: min={scores_arr.min():.4f}  avg={scores_arr.mean():.4f}  max={scores_arr.max():.4f}")

        if missed:
            sorted_scores = sorted(scores_list)
            target_miss = max(1, int(total * 0.2))
            suggested = sorted_scores[target_miss - 1] - 0.01
            would_find = sum(1 for s in scores_list if s >= suggested)
            print(f"  Suggested threshold: {suggested:.2f} (would find {would_find}/{total})")

        recall_data[label_name] = {
            "found": found,
            "total": total,
            "recall": recall,
            "avg_score": float(scores_arr.mean()),
            "min_score": float(scores_arr.min()),
            "missed": missed,
            "scores": scores_list,
        }

    # ── Phase 2: Precision ───────────────────────────────────────
    precision_data = {}

    if not args.recall_only:
        print(f"\n{'#' * 60}")
        print("PHASE 2: PRECISION (false positive check)")
        print(f"{'#' * 60}")

        print(f"\nFetching {args.precision_sample} recent issues...")
        recent_issues = fetch_recent_issues(args.repo, args.precision_sample)
        recent_embeddings = model.encode([issue_text(iss) for iss in recent_issues])

        fp_by_label = defaultdict(list)
        tp_by_label = defaultdict(int)

        for i, issue in enumerate(recent_issues):
            human_labels = {l["name"] for l in issue.get("labels", []) if l["name"].startswith("area/")}
            predicted, scores = classify_issue(
                recent_embeddings[i], label_embeddings, all_labels,
                default_threshold, max_labels,
            )

            for label in predicted:
                if label not in labels_to_test:
                    continue
                if label in human_labels:
                    tp_by_label[label] += 1
                else:
                    fp_by_label[label].append({
                        "number": issue["number"],
                        "title": issue["title"],
                        "score": scores.get(label, 0),
                    })

        for label_name in labels_to_test:
            tp = tp_by_label.get(label_name, 0)
            fps = fp_by_label.get(label_name, [])
            # Exclude FPs on issues that were in the recall sample (they have the label)
            fps = [fp for fp in fps if fp["number"] not in recall_issue_numbers]
            # Add recall-phase true positives
            recall_tp = recall_data.get(label_name, {}).get("found", 0)
            total_tp = tp + recall_tp
            total_predicted = total_tp + len(fps)
            precision = total_tp / total_predicted if total_predicted > 0 else 1.0
            precision_data[label_name] = {
                "true_positives": total_tp,
                "false_positives": fps,
                "precision": precision,
            }

        if fp_by_label:
            print(f"\n{'=' * 60}")
            print("FALSE POSITIVES (label assigned by model but not by human)")
            print("Note: some may be correct predictions that humans missed")
            print(f"{'=' * 60}")
            for label_name in sorted(fp_by_label.keys()):
                fps = fp_by_label[label_name]
                print(f"\n  {label_name} — {len(fps)} false positive(s)")
                for fp in fps[:5]:
                    print(f"    #{fp['number']} (score: {fp['score']:.4f}) {fp['title'][:65]}")
        else:
            print("\nNo false positives detected.")

    # ── Summary ──────────────────────────────────────────────────
    print(f"\n{'#' * 60}")
    print("SUMMARY")
    print(f"{'#' * 60}")

    has_precision = bool(precision_data)
    if has_precision:
        header = f"{'Label':<25} {'Recall':>12} {'Precision':>12} {'F2':>8} {'Avg':>8} {'Min':>8}"
    else:
        header = f"{'Label':<25} {'Recall':>12} {'Avg':>8} {'Min':>8}"
    print(header)
    print("-" * len(header))

    summary = []
    for label_name in labels_to_test:
        r = recall_data.get(label_name, {})
        p = precision_data.get(label_name, {})

        total = r.get("total", 0)
        if total == 0:
            summary.append((label_name, -1, None, None))
            continue

        recall = r.get("recall", 0)
        precision = p.get("precision")
        f2 = fbeta(precision, recall) if precision is not None else None
        summary.append((label_name, recall, precision, f2))

    sorted_summary = sorted(summary, key=lambda x: (x[3] if x[3] is not None else x[1], x[1]))
    for label_name, recall, precision, f2 in sorted_summary:
        r = recall_data.get(label_name, {})
        if recall < 0:
            print(f"{label_name:<25} {'no issues':>12}")
            continue

        recall_str = f"{r['found']}/{r['total']} ({recall:.0%})"
        avg = r.get("avg_score", 0)
        mn = r.get("min_score", 0)

        if has_precision and precision is not None:
            p = precision_data[label_name]
            prec_str = f"{p['true_positives']}/{p['true_positives']+len(p['false_positives'])} ({precision:.0%})"
            print(f"{label_name:<25} {recall_str:>12} {prec_str:>12} {f2:>8.2f} {avg:>8.4f} {mn:>8.4f}")
        elif has_precision:
            print(f"{label_name:<25} {recall_str:>12} {'N/A':>12} {'N/A':>8} {avg:>8.4f} {mn:>8.4f}")
        else:
            print(f"{label_name:<25} {recall_str:>12} {avg:>8.4f} {mn:>8.4f}")

    # Worst performers
    poor = [(name, rec, prec, f2) for name, rec, prec, f2 in sorted_summary
            if rec >= 0 and rec < 0.6]
    if poor:
        print(f"\n{'=' * 60}")
        print("LABELS NEEDING IMPROVEMENT (recall < 60%)")
        print(f"{'=' * 60}")
        for label_name, _, _, _ in poor:
            r = recall_data[label_name]
            print(f"\n  {label_name} — {r['found']}/{r['total']} found")
            print(f"  Review these missed issues to improve the description:")
            for m in r["missed"]:
                print(f"    #{m['number']} (score: {m['score']:.4f}) {m['title'][:70]}")


if __name__ == "__main__":
    main()
