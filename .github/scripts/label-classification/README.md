# Label Classification

Automatically assigns `area/*` labels to GitHub issues and pull requests, and issue types to issues, using sentence embeddings. Runs in GitHub Actions on every issue open/edit and every PR open — no inference API calls, no API keys, no GPU required. The embedding model is downloaded from Hugging Face Hub on first run and cached between workflow runs.

## Usage

```bash
# Dry run — shows what would be assigned without making changes
python classify.py --repo Apicurio/apicurio-registry --issue 7891 --dry-run

# Apply labels and issue type
python classify.py --repo Apicurio/apicurio-registry --issue 7891

# Classify a pull request (area labels only)
python classify.py --repo Apicurio/apicurio-registry --pr 9004

# Write the raw scores to a file for another tool to consume
python classify.py --repo Apicurio/apicurio-registry --pr 9004 --output-json scores.json
```

Exactly one of `--issue` or `--pr` is required.

Requires: `pip install pyyaml sentence-transformers`

In GitHub Actions, the workflow (`.github/workflows/classify.yml`) runs this automatically.

## Issues vs Pull Requests

Both kinds share the same `area_labels` descriptions and thresholds. Two things differ:

- **PRs contribute the directories they touch** to the embedded text, inserted *between* the title and the body (first 50 directories). Paths carry area signal that PR prose routinely omits: "fix NPE in the resolver" says nothing, `app/src/main/java/io/apicurio/registry/storage/impl/sql/` says a great deal.
- **PRs get no issue type.** GitHub's issue type field does not exist on pull requests, so that whole stage — including embedding the type descriptions — is skipped.

#### Why directories, and why before the body

Both details are load-bearing, and neither is obvious. `all-MiniLM-L6-v2` truncates its input at **256 word pieces** — a typical PR description in this repository blows past that on its own (PR #10086: 1407 tokens). Paths appended *after* the body are therefore never seen by the model at all.

Measured over 40 recent merged PRs, scoring whether the area label implied by the PR's conventional-commit scope was among those assigned:

| Text layout | Expected label found | Avg labels/PR | PRs with no labels |
|---|---|---|---|
| title + body | 65% | 2.75 | 3/40 |
| title + body + files | 68% | 2.80 | 3/40 |
| title + files + body | 74% | 3.17 | 0/40 |
| **title + directories + body** | **76%** | **2.92** | **0/40** |
| title + directories | 50% | 2.60 | 2/40 |

Directories beat raw file paths because a PR touching 30 files in one package spends 30 slots saying the same thing; collapsing them leaves room for the other packages it touched. And prose still carries real signal — paths alone drop to 50%.

#### Don't "fix" this by raising `max_seq_length`

256 word pieces really is short: across the same 40 PRs the median title+body is **777 tokens**, p90 is 1411, and 37 of 40 exceed 256. The obvious reaction is to raise the limit — `max_seq_length` is a config value in the model's `sentence_bert_config.json`, not an architectural ceiling, and the underlying transformer has 512 position embeddings. That was measured too, and it makes things **worse**:

| Layout | `max_seq_length` | Expected label found | Avg labels/PR |
|---|---|---|---|
| title + body | 256 | 65% | 2.75 |
| **title + directories + body** | **256** | **76%** | **2.92** |
| title + body | 512 | 71% | 3.33 |
| title + directories + body | 512 | 62% | 3.35 |

The reason is **mean pooling**. This model produces its 384-dimension vector by averaging the per-token embeddings, so every token dilutes every other one. The directory block is a small, concentrated, high-signal region; doubling the window halves its share of the average and buries it under PR-template boilerplate. Longer context is not free accuracy here — for a mean-pooled sentence embedding, it is actively a cost unless the added text is as informative as the text already in the window. (Running at 512 is also off-distribution: the model was fine-tuned at 256. Both effects push the same way.)

The right lever is therefore *what* goes in the window, not how big it is. If PR descriptions ever need to be read more deeply than this, that is a case for a different model — one with a longer trained context and CLS-style rather than mean pooling — not for turning this knob.

Treat these numbers as directional. The conventional-commit scope is a proxy for human judgement, not ground truth (`fix(perf)` → `area/QE` is a judgement call), and the measure is recall of a single expected label — it says nothing about the precision of the *other* labels assigned. Re-run `variants` style comparisons against real human-assigned labels once enough PRs have them.

PRs are classified when they open and when a draft is marked ready for review, matching the two moments the lifecycle orchestrator treats as a PR entering the lifecycle (`initNewPr` in `../pr-lifecycle.js`). Drafts are skipped.

### `--output-json`

Writes the scores and decisions as JSON so another tool can consume them rather than scraping the log. In CI, the reviewer assignment step that follows classification in `classify.yml` reads it: area labels drive each maintainer's interest score (see [Reviewer assignment](../../PR_LIFECYCLE.md#reviewer-assignment)). So the labels a PR gets also decide who reviews it. Example output:

```json
{
  "repo": "Apicurio/apicurio-registry",
  "number": 9004,
  "kind": "pr",
  "dry_run": false,
  "area_label_scores": { "area/CI": 0.5142, "area/ui": 0.0871 },
  "area_labels_selected": ["area/CI"],
  "area_labels_capped": ["area/QE"],
  "area_labels_suppressed": [],
  "area_labels_applied": ["area/CI"],
  "issue_type_scores": {},
  "issue_type_selected": null,
  "changed_files": [".github/workflows/classify.yml"]
}
```

`area_labels_selected` is what the classifier chose; `area_labels_capped` is labels that cleared their threshold but lost to `max_labels`; `area_labels_suppressed` is labels skipped because someone removed them before; `area_labels_applied` excludes labels the target already carried. The file is written under `--dry-run` too, with `dry_run: true` recorded — so a caller can see what would happen without it happening.

## How It Works

The script compares the **meaning** of an issue's or PR's text against the **meaning** of each label's description, and assigns labels whose descriptions are semantically closest.

### Sentence Embeddings

The key technique is **sentence embeddings** — converting text into a vector (a list of 384 numbers) that captures its meaning. Texts with similar meaning produce vectors that point in similar directions.

1. The title and body (plus the directories touched, for a PR) are concatenated into a single string. The **entire text** is embedded as one unit — not individual words. The model reads all words in context (e.g. it knows "Kubernetes operator" is different from "mathematical operator").

2. Each label's description from `label-descriptions.yml` is embedded the same way.

3. [Cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity) measures how close two vectors are:
   - `1.0` = identical meaning, `0.0` = unrelated, `-1.0` = opposite
   - In practice, scores for this model range from `-0.1` to `0.6`

4. Labels scoring above their threshold are assigned (up to 4, preferring the most nested — see [Hierarchical Labels](#hierarchical-labels)). Issue type works the same way but is single-select, and only set if the issue doesn't already have one.

### Example

For an issue titled *"Support high availability (HA) for the Apicurio Registry Operator"*:

```
area/operator:      0.5542   >>> assigned (above threshold)
area/storage:       0.1821       not assigned
area/auth:          0.0860       not assigned
```

The model understood that "Operator" + "HA" + "deployment" is semantically close to the `area/operator` description, even though the issue doesn't use the exact keywords.

### Reading the `[capped]` marker

A label can clear its threshold and still not be assigned, because `max_labels` (4) cut it. The score listing marks those explicitly, so you are never left wondering why a label that looks like a match was skipped:

```
  >>> area/CI: 0.4368 (threshold: 0.3)
      area/maven-plugin: 0.3936 (threshold: 0.4)
  >>> area/sdk: 0.3481 (threshold: 0.2)
      area/QE: 0.3312 (threshold: 0.2)  [capped: over threshold, but max_labels=4]

5 label(s) cleared their threshold but lost to max_labels=4: area/CLI, area/QE, ...
```

`area/maven-plugin` scored higher than `area/QE` but is *not* capped — it never cleared its own 0.40 threshold. Those are two different failure modes with two different fixes: raise `max_labels`, or adjust the threshold.

A lot of capped labels across many targets means thresholds are collectively too loose, not that `max_labels` is too small.

### Corrections stick

Classification is not a one-shot event — issues reclassify on every edit, PRs on every draft/ready cycle. So **if you remove a label the classifier got wrong, it will not come back.**

Before applying anything, the script reads the target's event timeline and drops any label that has been removed before. No extra state is involved; GitHub already records the history.

```
Not re-adding 1 label(s) removed earlier: area/storage/sql
```

Two consequences worth knowing:

- The classifier is **add-only**. It never removes a label you added by hand, and after your removal it never re-adds one either. Fixing its output is a one-time action.
- If you remove a label and later decide it did belong, add it back manually — the classifier will not do it for you.

This is what makes the label history usable as training data (#10160). A classifier that reinstates its own mistakes produces a label set that looks like human agreement but is really just the model agreeing with itself.

### A note on where the accuracy ceiling actually is

Tuning descriptions and thresholds is the lever this design gives you, and it works — but it is not the biggest lever available. Measured against 854 human-labeled issues, fitting a classifier on those existing labels beats cosine-similarity-to-description by **17 points top-1** using the same model and the same embeddings, while swapping in a larger embedding model gains ~1 point at best.

See **#10160** for the numbers, the trade-offs (rare labels, label noise, explainability) and the suggested hybrid. Until that is picked up, the tuning workflow below is the way to improve accuracy.

### The Embedding Model

[`all-MiniLM-L6-v2`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) — a small (80 MB), fast, general-purpose sentence embedding model from the [sentence-transformers](https://www.sbert.net/) library. Runs on CPU in ~5 ms per sentence. Downloaded from [Hugging Face Hub](https://huggingface.co/) on first run and cached locally.

The model is **not** being trained or fine-tuned. It's used as-is — all the "learning" about our labels comes from the descriptions we write in `label-descriptions.yml`.

### Hierarchical Labels

Labels nest **by name**: `area/storage/sql` is a child of `area/storage` because that is the longest configured label its name extends by whole `/` segments. There is no `parent:` key to keep in sync — the hierarchy is exactly the one visible on GitHub. A config test requires every nested label's immediate parent to be configured, so a child can never be orphaned: whenever the parent is the best fit, it is there to be picked on its own.

Nesting affects selection in two ways:

- **A pick brings its ancestors along, free.** Choosing `area/artifact-types/avro` applies `area/artifact-types` too, without spending a second `max_labels` slot.
- **The more nested label wins.** Candidates are taken strongest first. A child that clears its own threshold *replaces* an ancestor already picked rather than sitting beside it, and a parent whose child is already picked is skipped as implied. So `area/storage` + `area/storage/sql` costs one slot, not two.

A child still has to clear **its own** threshold — a strong `area/AI` score does not make an issue `area/AI/MCP`. The consequence for description writing: a parent's description should hold what the whole family has in common, and anything specific to one child belongs in that child.

#### Why a child is not gated on its parent's score

It is tempting to add a second guard: only let a child replace its parent if it fits the text about as well as the parent does, so that a PR touching some *new* REST API does not land on `area/rest/ccompat` just because it is REST-shaped. That was measured, on 234 PRs (changed paths as ground truth) and 1000 issues (title keywords as ground truth), across every child label that ground truth can be derived for:

| Rule | PR P / R / F1 | Issue P / R / F1 |
|---|---|---|
| no gate (shipped) | 41% / 67% / 0.51 | 34% / 75% / 0.47 |
| child score ≥ parent − 0.05 | 41% / 62% / 0.50 | 37% / 73% / 0.49 |
| child score ≥ parent | 41% / 54% / 0.47 | 40% / 71% / 0.51 |
| child margin over threshold ≥ parent's | 43% / 50% / 0.46 | 41% / 69% / 0.52 |

Every gate trades recall for precision roughly one-for-one. The reason is visible in the misfires themselves: a child is almost never wrong *because its parent was a better fit* — in most of them the parent scores far below the child. They are wrong because the child's own description overlaps some other label's vocabulary. `area/rest/ccompat` used to say "Confluent Schema Registry *compatible* API" and "ccompat *compatibility* mode", and so matched every issue about compatibility *rules*, while `area/rest` scored 0.2–0.3 on the same text. The fix was the description, not a gate.

So when a child fires where it should not, look for the shared word before reaching for a threshold.

#### Don't open a description with "Apicurio Registry"

Almost every PR body and a good share of issues contain that phrase, so a description that starts with it matches almost everything. `area/converter` and `area/maven-plugin` both did, and were being applied to 184 and 109 of the last 1000 issues respectively — at roughly 1–3% precision. With the phrase removed (and thresholds re-fitted) those figures are 11 and 31.

## Tuning Accuracy

The main lever is **editing label descriptions** in `label-descriptions.yml`:

- Adding keywords that appear in issues for an area improves recall (fewer misses)
- Making descriptions more specific improves precision (fewer false positives)
- Each label can have its own `threshold` override — useful because broad labels (storage, rest, auth) naturally score lower than specific ones (rest/iceberg, serdes)

Descriptions don't need to be grammatical sentences — keyword lists work well.

### Tuning Workflow

1. Run the test: `python test_classify.py --repo Apicurio/apicurio-registry`
2. Look at labels with low recall — the output lists the specific missed issues
3. Read those issues and identify keywords the description is missing
4. Add terms to the label's description in `label-descriptions.yml`
5. Optionally adjust the label's `threshold` (lower = catch more, higher = fewer but more precise)
6. Re-run the test to verify improvement

### Tuning for Pull Requests

`test_classify.py` measures accuracy against issues only, and cannot be pointed at PRs: the tuning loop needs human-assigned ground truth, and no PR in this repository carries an `area/*` label yet. PRs therefore start on the thresholds tuned for issues.

That is a deliberate starting point, not a verified one. On the 40-PR sample above, the shipped layout assigns **2.92 labels per PR** and puts 10 of 40 at the 4-label cap — noticeably hotter than issue labelling. Some of that is genuine (PRs really do span more areas than issues), some is not: a CI-only PR picking up `area/storage/sql` from workflow files that merely *name* storage shards is a false positive the thresholds should have caught.

Once enough PRs have been labelled — and corrected — by hand to form a ground truth, re-tune, and add PR-specific threshold overrides to `label-descriptions.yml` if the two distributions turn out to need different numbers. Lowering `max_labels` for PRs is the other obvious lever.

#### Changed paths as ground truth

Until hand-labelled PRs exist, many labels have a better PR ground truth than any human: the files the PR touched. A PR that changes `storage/impl/kafkasql/` is `area/storage/kafkasql`; one that changes `ui/ui-editors/` is `area/ui/editors`. The thresholds of the labels added in the 2026-09 taxonomy revision were fitted that way — changed paths for 234 merged PRs, title keywords for 1000 issues, keeping a value only if it held on both, because a threshold is shared by the two.

It is a proxy with known blind spots: incidental touches count as positives (a refactor that brushes one `schema-util/` file), and labels with no path of their own (`area/security`, `area/performance` beyond the perf-test modules) can only be checked against issue titles. Treat the fitted values as a better starting point than the issue-tuned defaults, not as final.

The same measurement showed several **existing** labels running far hotter on PRs than on issues — `area/CLI`, `area/storage/sql` and `area/storage/gitops` at 17–25% precision on PRs. Those were left alone: they are tuned for issue recall, and lowering their PR noise is the PR-specific-override job described above.

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

Label names above are as they were then. Since the 2026-09 revision: `avro` → `artifact-types/avro`, `protobuf` → `artifact-types/protobuf`, `converter` → `serdes/converter`, `caching` → `performance/caching`, `lakehouse` → `rest/iceberg`. The `maven-plugin` and `converter` figures above also predate the finding that their descriptions matched almost everything (see *Don't open a description with "Apicurio Registry"*).

### 2026-09 taxonomy revision

Added 5 labels that already existed on GitHub but were never classifiable (`storage/kafkasql`, `AI/MCP`, `rules/compatibility`, `sdk/java`, `sdk/python`), 13 new ones, nesting from label names, and nested-label preference. Measured on the same data before and after:

| | before | after |
|---|---|---|
| PR: label implied by the commit scope assigned (143 PRs whose scope maps to a label in both) | 78% | 80% (81% counting a parent match) |
| PR: most specific labels per PR (avg) | 2.40 | 2.60 |
| PR: all labels per PR, parents included (avg) | 2.55 | 3.27 |
| Issues with no `area/*` label today that would get one (of 104) | 20 | 56 |
| `area/ai-agents` issues that get `area/AI` (of 46) | 38 | 45 — 30 as `AI/A2A`, 14 `AI/MCP`, 4 `AI/prompt-templates` |

The rise in labels per PR is mostly parents that nesting now implies (a child plus its parent where there used to be one flat label), not extra areas. The cap is hit more often (59 of 155 PRs, from 44), which is the next thing to look at if PR labelling feels noisy.

## Files

| File | Purpose |
|------|---------|
| `classify.py` | Main classification script |
| `test_classify.py` | Accuracy testing (recall + precision) — needs the model, network and `gh` |
| `test_classify_unit.py` | Offline unit tests for the decision logic — runs in CI on every scripts change |
| `label-descriptions.yml` | Label/type descriptions and threshold configuration |
| `README.md` | This file |
