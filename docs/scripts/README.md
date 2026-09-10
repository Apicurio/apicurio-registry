# Docs scripts

Tooling for the `getting-started` assemblies that feed the downstream (Red Hat) modular
documentation build. CI runs both checks on every docs PR through
`.github/workflows/verify-docs.yaml`; this page is how to run the same checks locally.

The `apicurio-*.sh` files in this directory are unrelated helper scripts that the
documentation links to.

## The pipeline

```
docs/modules/ROOT/pages/getting-started/assembly-*.adoc   (monolithic assembly, the file you edit)
        |
        v
leben.py            splits it into assemblies/ + modules/, exactly as the downstream build does
        |
        v
vale (AsciiDocDITA) lints the SPLIT output            <- vale-pipeline.py
downstream-compat.py checks the structural contract that leben.py and the downstream build rely on
```

Vale on the raw monolith is noisy by design: rules such as `NestedSection` and
`AssemblyContents` fire on structure that disappears after splitting. Only the split view is
gated, locally and in CI.

## Prerequisites

| Tool | Why | Install |
|---|---|---|
| Python 3.9 or newer | both runners are stdlib-only | already present on most systems |
| vale 3.x | the linter; CI pins 3.17.1 | `sudo snap install vale`, `brew install vale`, or a release tarball |
| asciidoctor | vale shells out to it to parse AsciiDoc | `sudo dnf install asciidoctor`, `sudo apt install asciidoctor`, or `gem install asciidoctor` |
| AsciiDocDITA rules | the rule package pinned in `.vale.ini` | from the repository root: `vale sync` |

Run `vale sync` once, and again whenever the `Packages` line in `.vale.ini` changes. It
downloads the rules into `.vale/styles/`, which is gitignored.

Snap-confined vale cannot read `/tmp`. Both runners keep their scratch files under `.vale/`
inside the repository for that reason; if you pass `--out-dir`, keep it outside `/tmp`.

## Commands

```bash
# All getting-started assemblies (what a push to main runs)
python3 docs/scripts/downstream-compat.py
python3 docs/scripts/vale-pipeline.py

# One or more assemblies (what a PR runs, for the files it touched)
python3 docs/scripts/vale-pipeline.py docs/modules/ROOT/pages/getting-started/assembly-rule-reference.adoc

# Keep the split output and vale.json for inspection
python3 docs/scripts/vale-pipeline.py --out-dir .vale/last-run docs/modules/ROOT/pages/getting-started/assembly-rule-reference.adoc

# Read the file list from stdin or a file (one path per line)
git diff --name-only upstream/main -- 'docs/modules/ROOT/pages/getting-started/assembly-*.adoc' \
  | python3 docs/scripts/vale-pipeline.py --files-from -
```

Useful options: `--fail-on warning` to make warnings fail locally, `--fail-on none` to just
report, `--annotations` to see the GitHub annotation lines CI emits, `--summary FILE` to write
the Markdown summary CI puts in the job summary. Run either script with `--help` for the rest.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | no errors (warnings may be present) |
| 1 | errors found; CI fails the Verification Gate |
| 2 | tool failure: a path does not exist, or vale, asciidoctor or `leben.py` is missing or crashed |

## What vale-pipeline.py reports

One row per assembly with the number of modules leben.py produced and the error and warning
counts from vale. Each alert names the split file and line and, where the mapping is
unambiguous, the line in the monolith you need to edit. Two synthetic alerts cover failures
leben.py itself does not report: `leben.NoRootId` (no `[id="..."]` before the title, nothing was
produced) and `leben.ModuleCollision` (two ids in one file map to the same module filename, so
one module silently overwrote the other).

## Rules checked by downstream-compat.py

Errors block the gate. Warnings are informational.

| Rule | Level | Check | Fix |
|---|---|---|---|
| B1 | error | Every `== ` heading after the first module id has a double-quoted `[id="..."]` on the line above it. leben.py otherwise merges the section into the previous module. The only heading allowed between the title and the first module id is `== Prerequisites` (the assembly preamble). | Add `[id="name_{context}"]` on the line above, or turn it into a bold lead-in paragraph if it belongs to the previous module (`===` nesting is not allowed in DITA). |
| B1b | error | The file starts with a boundary `[id="..."]` followed by the `= ` document title. | Put the id on the line above the title. |
| B2 | error | No `<<...>>` shorthand xrefs. Block titles carry no anchor downstream, so these break the build. | Use `xref:id_{context}[]` against a real `[id=...]`. |
| B3 | error | Every boundary `[id="..."]` line is immediately followed by a heading. A bare id above a table or block becomes a bogus split boundary. | Put block ids in the attribute list, `[id="x_{context}",cols="1,2"]`, and keep nothing between an id and its heading. |
| W4 | warning | An `xref:` whose target is not an id defined in this file, or is path-based, and is not inside `ifndef::service-registry-downstream[]` or `ifdef::apicurio-registry[]`. Downstream books include only a subset of assemblies, so the link may not resolve there. | Guard the xref if the target assembly is not in every downstream book that includes this one, or leave it if it is. |
| W5 | warning | Unbalanced `ifdef`, `ifndef`, `endif`, or an unclosed delimited block. | Close it. |

Only a double-quoted `[id="..."]` at the start of a line is a split boundary. Single-quoted ids
and ids inside an attribute list such as `[id="x",role="y"]` are not.

## leben.py

`leben.py` is an unmodified copy of the downstream splitter (`SCRIPT_VERSION = '20221205'`).
Never edit it here: the downstream repository runs the same file, and the checks above encode
its exact behaviour. If the downstream copy changes, replace this one wholesale.

## Bumping the AsciiDocDITA pin

1. Change the tag in the `Packages` line of `.vale.ini`.
2. Run `vale sync`, then `python3 docs/scripts/vale-pipeline.py` over all assemblies.
3. Fix any new errors in the assemblies before merging the bump; a push to main lints
   everything and a red Verification Gate on main pages the Slack channel.

CI caches `.vale/styles` on the hash of `.vale.ini`, so the cache rolls over on its own.

## How CI uses these

`verify.yaml` calls `verify-docs.yaml` when Decide's `run-docs` output is true, that is when a
change touches `docs/modules/`, `docs/scripts/`, `.vale.ini` or the workflow itself. A pull
request lints only the assemblies it added, modified or renamed; a push to `main` lints all of
them. The contract check runs first, then the vale pipeline, and both always report even when
the first one fails. Errors fail the job, which fails the Verification Gate. Up to ten errors
and ten warnings per step appear as annotations on the changed files; the full report is in the
job summary and the `docs-vale-<sha>` artifact holds the split output and `vale.json`.
