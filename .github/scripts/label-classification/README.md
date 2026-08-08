# Label Classification

Automatically assigns `area/*` labels (and, for issues, the issue type) using sentence embeddings. Runs in GitHub Actions on every issue open/edit and every pull request open/edit/push — no inference API calls, no API keys, no GPU required. The embedding model is downloaded from Hugging Face Hub on first run and cached between workflow runs.

## Usage

```bash
# Dry run — shows what would be assigned without making changes
python classify.py --repo Apicurio/apicurio-registry --issue 7891 --dry-run

# Apply labels and issue type
python classify.py --repo Apicurio/apicurio-registry --issue 7891

# Classify a pull request (title + body + changed file paths)
python classify.py --repo Apicurio/apicurio-registry --pr 7992

# Dry run for a pull request
python classify.py --repo Apicurio/apicurio-registry --pr 7992 --dry-run
```

Requires: `pip install pyyaml sentence-transformers`

In GitHub Actions, the workflows (`.github/workflows/classify-issues.yml` and `.github/workflows/classify-pr.yml`) run this automatically.

## How It Works

The script compares the **meaning** of an issue's text against the **meaning** of each label's description, and assigns labels whose descriptions are semantically closest.

### Sentence Embeddings

The key technique is **sentence embeddings** — converting text into a vector (a list of 384 numbers) that captures its meaning. Texts with similar meaning produce vectors that point in similar directions.

1. The title and body are concatenated into a single string. For pull requests, the changed file paths are appended too (file paths carry strong signal about which area a PR affects). The **entire text** is embedded as one unit — not individual words. The model reads all words in context (e.g. it knows "Kubernetes operator" is different from "mathematical operator").

2. Each label's description from `label-descriptions.yml` is embedded the same way.

3. [Cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity) measures how close two vectors are:
   - `1.0` = identical meaning, `0.0` = unrelated, `-1.0` = opposite
   - In practice, scores for this model range from `-0.1` to `0.6`

4. Labels scoring above their threshold are assigned (up to 4). Issue type works the same way but is single-select, and only set if the issue doesn't already have one.

### Example

For an issue titled *"Support high availability (HA) for the Apicurio Registry Operator"*:

```
area/operator:      0.5542   >>> assigned (above threshold)
area/storage:       0.1821       not assigned
area/auth:          0.0860       not assigned
```

The model understood that "Operator" + "HA" + "deployment" is semantically close to the `area/operator` description, even though the issue doesn't use the exact keywords.

### The Embedding Model

[`all-MiniLM-L6-v2`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) — a small (80 MB), fast, general-purpose sentence embedding model from the [sentence-transformers](https://www.sbert.net/) library. Runs on CPU in ~5 ms per sentence. Downloaded from [Hugging Face Hub](https://huggingface.co/) on first run and cached locally.

The model is **not** being trained or fine-tuned. It's used as-is — all the "learning" about our labels comes from the descriptions we write in `label-descriptions.yml`.

### Hierarchical Labels

Some labels have parent-child relationships (e.g. `area/storage/sql` → `area/storage`). When a child label is assigned, the parent is automatically added too. This is configured via the `parent` field in `label-descriptions.yml`.

## Pull Request Classification and CI Integration

Pull requests are classified from their **title, body, and changed file paths** to auto-assign `area/*` labels. The `classify-pr.yml` workflow runs on `pull_request_target` (opened/edited/synchronize/ready_for_review) so labels are applied to PRs from forks too.

Those labels are consumed by the **Verify** workflow's `decide` job as an *additional* signal for test group selection:

- The deterministic path-based logic (`dorny/paths-filter` + the `decide()` function in `verify.yaml`) stays authoritative for **narrowing** which test groups run.
- The AI area labels can only **widen** that selection: if the classifier flags an area whose test group the path filter didn't select, that group is enabled — provided the PR's lifecycle scope already permits it (e.g. `area/operator` enables the operator tests when the PR is `lifecycle/ready-for-review`).
- Per-PR opt-out via the `ci/disable-ai-select` label.

## Tuning Accuracy

The main lever is **editing label descriptions** in `label-descriptions.yml`:

- Adding keywords that appear in issues for an area improves recall (fewer misses)
- Making descriptions more specific improves precision (fewer false positives)
- Each label can have its own `threshold` override — useful because broad labels (storage, rest, auth) naturally score lower than specific ones (lakehouse, serdes)

Descriptions don't need to be grammatical sentences — keyword lists work well.

### Tuning Workflow

1. Run the test: `python test_classify.py --repo Apicurio/apicurio-registry`
2. Look at labels with low recall — the output lists the specific missed issues
3. Read those issues and identify keywords the description is missing
4. Add terms to the label's description in `label-descriptions.yml`
5. Optionally adjust the label's `threshold` (lower = catch more, higher = fewer but more precise)
6. Re-run the test to verify improvement

## Measuring Accuracy

The test script (`test_classify.py`) checks classification against existing labeled issues.

### Key Concepts

**Recall** — of all issues that *should* have a label, how many did the classifier find? Low recall means the classifier is **missing labels** (false negatives).

**Precision** — of all issues the classifier *assigned* a label to, how many were correct? Low precision means the classifier is **assigning wrong labels** (false positives).

**F2 score** — combines precision and recall, with **recall weighted 4x more**. This reflects our preference: missing a correct label is worse than occasionally assigning an extra one (humans can remove wrong labels, but they're less likely to notice missing ones).

### How the Test Works

**Phase 1 — Recall:** For each label, fetch real issues that have it and check if the classifier would have assigned it.

**Phase 2 — Precision:** Fetch a broad sample of recent issues and check for false positives (labels assigned by the model but not by humans). Note: some "false positives" may actually be correct predictions where the human forgot to add the label.

### Reading the Output

```
Label                       Recall    Precision       F2      Avg      Min
--------------------------------------------------------------------------
area/operator            3/5 (60%)    5/6 (83%)     0.64   0.4162   0.2075
area/serdes             5/5 (100%)    5/7 (71%)     0.93   0.4913   0.3639
```

- **Recall** `3/5 (60%)` — found 3 of 5 issues that have this label
- **Precision** `5/6 (83%)` — of 6 times it assigned this label, 5 were correct
- **F2** — combined score (0 to 1, higher is better)
- **Avg/Min** — cosine similarity scores for matching issues

Labels with low recall are listed at the bottom with the specific issues they missed.

### Running the Tests

```bash
# Test all labels (default: 10 issues per label, 50 for precision)
python test_classify.py --repo Apicurio/apicurio-registry

# Evaluate accuracy on pull requests instead of issues
python test_classify.py --repo Apicurio/apicurio-registry --prs

# Test specific labels only
python test_classify.py --repo Apicurio/apicurio-registry --labels area/auth area/ui

# More issues per label for higher confidence
python test_classify.py --repo Apicurio/apicurio-registry --sample-size 20

# Skip precision test (faster)
python test_classify.py --repo Apicurio/apicurio-registry --recall-only

# Larger precision sample
python test_classify.py --repo Apicurio/apicurio-registry --precision-sample 100
```

### Current Accuracy (2026-05-04)

Tested against 5 issues per label, 50 recent issues for precision. 28 labels evaluated.

| F2 Score | Labels |
|----------|--------|
| 1.00 (perfect) | build, CI, examples, lakehouse, sdk, search, storage/gitops |
| 0.90 – 0.99 | QE, storage/sql, maven-plugin, observability, serdes |
| 0.80 – 0.89 | auth, CLI, dependencies, references, ui, caching, rest, rules, rest/ccompat, protobuf |
| 0.65 – 0.79 | storage, operator, avro, documentation |
| < 0.65 | AI (50%, 2 samples), converter (56%, 1 sample) |

Key observations:
- Labels with specific vocabulary (lakehouse, serdes, maven-plugin) perform best
- Broad labels (storage, rest, auth) needed per-label threshold overrides (lowered to 0.20–0.25)
- Labels with very few issues (AI, converter) have unreliable scores due to small sample size
- Some "false positives" are likely correct predictions where the human forgot to add the label

## Files

| File | Purpose |
|------|---------|
| `classify.py` | Main classification script (issues and pull requests) |
| `test_classify.py` | Accuracy testing (recall + precision; `--prs` for pull requests) |
| `label-descriptions.yml` | Label/type descriptions and threshold configuration |
| `README.md` | This file |
| `.github/workflows/classify-issues.yml` | Runs classification on issue open/edit |
| `.github/workflows/classify-pr.yml` | Runs classification on PR open/edit/push |
