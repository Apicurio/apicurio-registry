#!/usr/bin/env python3
"""Static checks for the downstream modular build contract on getting-started assemblies.

The downstream (Red Hat) build splits each monolithic assembly with leben.py and compiles
the result with a strict DocBook toolchain. Antora forgives what that build fails on, so
these checks encode the structural contract that leben.py and the downstream build rely on.
Nothing here needs the downstream repository.

Rules (E = error, blocks; W = warning, reported only):
  B1  (E) every '== ' heading after the first module id has a double-quoted [id="..."] on the
          line above it; otherwise leben.py silently merges the section into the previous module.
          The one heading allowed between the title and the first module id is '== Prerequisites'
          (the assembly preamble, which leben.py keeps in the assembly body).
  B1b (E) the file starts with a boundary id followed by a level-1 '= ' title.
  B2  (E) no <<...>> shorthand xrefs; block titles carry no anchors, so these break downstream.
          Use xref:id_{context}[] with a real [id=...].
  B3  (E) every boundary [id="..."] line is immediately followed by a heading; a bare id above a
          table or block becomes a bogus split boundary. Put block ids in the attribute list:
          [id="x_{context}",cols="1,2"].
  W4  (W) an xref whose target is not an id in this file, or is path-based, and is not inside an
          upstream-only guard (ifndef::service-registry-downstream[] or ifdef::apicurio-registry[]).
          Downstream books include only a subset of assemblies, so such links may not resolve.
  W5  (W) unbalanced ifdef/ifndef/endif.

Usage:
  python3 docs/scripts/downstream-compat.py                       # all getting-started assemblies
  python3 docs/scripts/downstream-compat.py <assembly.adoc> ...
  python3 docs/scripts/downstream-compat.py --files-from list.txt # one path per line ('-' = stdin)

Options:
  --summary PATH     append a Markdown summary here (default: $GITHUB_STEP_SUMMARY if set)
  --annotations / --no-annotations
                     print GitHub ::error/::warning commands (default: on when GITHUB_ACTIONS=true)
  --strict-xrefs     promote W4 to an error

Exit codes: 0 no errors, 1 errors found, 2 tool failure.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Set, Tuple

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
ASSEMBLY_DIR = REPO / "docs" / "modules" / "ROOT" / "pages" / "getting-started"
ASSEMBLY_GLOB = "assembly-*.adoc"

# Must stay identical to SplitTask.id_pattern in leben.py: it is the split boundary.
LEBEN_ID_RE = re.compile(r'^\s*(?:\[\[([^\]]+)\]\]|\[id="([^"\]]+)"\])')
HEADING_RE = re.compile(r'^(=+)\s+(.*)')
ID_ATTR_RE = re.compile(r'^\[.*?\bid="([^"]+)"')
INLINE_ANCHOR_RE = re.compile(r'\[\[([^\],]+)(?:,[^\]]*)?\]\]')
ANCHOR_MACRO_RE = re.compile(r'anchor:([^\[\s]+)\[')
DELIM_RE = re.compile(r'^(-{4,}|\.{4,}|={4,}|\+{4,}|/{4,})\s*$')
COND_OPEN_RE = re.compile(r'^(ifdef|ifndef)::([^\[]+)\[\]\s*$')
IFEVAL_RE = re.compile(r'^ifeval::\[.*\]\s*$')
COND_CLOSE_RE = re.compile(r'^endif::[^\[]*\[\]\s*$')
XREF_RE = re.compile(r'xref:([^\[\s]+)\[')
SHORTHAND_XREF_RE = re.compile(r'<<[^<>]+>>')
CONTEXT_SUFFIX = "_{context}"
UPSTREAM_ONLY_GUARDS = {("ifndef", "service-registry-downstream"), ("ifdef", "apicurio-registry")}
# '== ' headings allowed before the first module id (leben.py keeps them in the assembly body).
PREAMBLE_HEADINGS = {"prerequisites"}

ANNOTATION_CAP = 10
SUMMARY_CAP = 900_000
SEVERITY_RANK = {"warning": 1, "error": 2}


class ToolError(Exception):
    """Bad input; exit 2."""


@dataclass
class Line:
    no: int
    text: str
    in_block: bool
    comment: bool
    conds: Tuple[Tuple[str, str], ...]


@dataclass
class Finding:
    rule: str
    severity: str
    path: Path          # repo-relative
    line: int
    message: str


# ---------------------------------------------------------------------------
# Inputs (kept in sync with vale-pipeline.py by hand; both scripts are standalone)
# ---------------------------------------------------------------------------

def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="downstream-compat.py",
        description="Static checks for the downstream modular build contract.",
    )
    p.add_argument("files", nargs="*", help="assembly paths (default: all getting-started assemblies)")
    p.add_argument("--files-from", metavar="FILE", help="read assembly paths from FILE ('-' for stdin)")
    p.add_argument("--summary", metavar="PATH", default=os.environ.get("GITHUB_STEP_SUMMARY"))
    ann = p.add_mutually_exclusive_group()
    ann.add_argument("--annotations", dest="annotations", action="store_true")
    ann.add_argument("--no-annotations", dest="annotations", action="store_false")
    p.set_defaults(annotations=os.environ.get("GITHUB_ACTIONS") == "true")
    p.add_argument("--strict-xrefs", action="store_true", help="promote W4 to an error")
    return p.parse_args(argv)


def read_file_list(source: str) -> List[str]:
    text = sys.stdin.read() if source == "-" else Path(source).read_text(encoding="utf-8")
    return [ln.strip() for ln in text.splitlines() if ln.strip() and not ln.strip().startswith("#")]


def resolve_targets(paths: List[str]) -> List[Path]:
    if not paths:
        return sorted(ASSEMBLY_DIR.glob(ASSEMBLY_GLOB))
    resolved: List[Path] = []
    seen = set()
    for raw in paths:
        candidate = REPO / raw
        if not candidate.is_file():
            candidate = Path(raw)
        if not candidate.is_file():
            raise ToolError(f"assembly not found: {raw}")
        if candidate.suffix != ".adoc":
            raise ToolError(f"not an .adoc file: {raw}")
        candidate = candidate.resolve()
        if candidate not in seen:
            seen.add(candidate)
            resolved.append(candidate)
    return resolved


def repo_relative(path: Path) -> Path:
    try:
        return path.resolve().relative_to(REPO)
    except ValueError:
        return path


# ---------------------------------------------------------------------------
# Scanner
# ---------------------------------------------------------------------------

def scan_lines(text: str) -> Tuple[List[Line], List[Tuple[int, str]]]:
    """Annotate each line with block/comment/conditional state. Returns (lines, cond_problems)."""
    lines: List[Line] = []
    problems: List[Tuple[int, str]] = []
    block_delim: Optional[str] = None
    stack: List[Tuple[str, str]] = []
    for i, raw in enumerate(text.splitlines(), start=1):
        stripped = raw.rstrip("\r")
        m = DELIM_RE.match(stripped)
        if m:
            token = m.group(1)
            if block_delim is None:
                block_delim = token
            elif token == block_delim:
                block_delim = None
            lines.append(Line(i, stripped, True, False, tuple(stack)))
            continue
        in_block = block_delim is not None
        if not in_block:
            mo = COND_OPEN_RE.match(stripped)
            if mo:
                stack.append((mo.group(1), mo.group(2).strip()))
                lines.append(Line(i, stripped, False, False, tuple(stack)))
                continue
            if IFEVAL_RE.match(stripped):
                stack.append(("ifeval", stripped))
                lines.append(Line(i, stripped, False, False, tuple(stack)))
                continue
            if COND_CLOSE_RE.match(stripped):
                if stack:
                    stack.pop()
                else:
                    problems.append((i, "endif without a matching ifdef/ifndef"))
                lines.append(Line(i, stripped, False, False, tuple(stack)))
                continue
        comment = (not in_block) and stripped.lstrip().startswith("//")
        lines.append(Line(i, stripped, in_block, comment, tuple(stack)))
    if block_delim is not None:
        problems.append((len(lines), f"delimited block opened with '{block_delim}' is never closed"))
    for kind, attr in stack:
        problems.append((len(lines), f"{kind}::{attr}[] is never closed"))
    return lines, problems


def boundary_id(line: Line) -> Optional[str]:
    m = LEBEN_ID_RE.match(line.text)
    return (m.group(1) or m.group(2)) if m else None


def strip_context(ident: str) -> str:
    return ident[:-len(CONTEXT_SUFFIX)] if ident.endswith(CONTEXT_SUFFIX) else ident


def is_upstream_only(conds: Tuple[Tuple[str, str], ...]) -> bool:
    for kind, attr in conds:
        names = {n.strip() for n in re.split(r"[,+]", attr)}
        for guard_kind, guard_attr in UPSTREAM_ONLY_GUARDS:
            if kind == guard_kind and guard_attr in names:
                return True
    return False


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_structure(path: Path, lines: List[Line]) -> List[Finding]:
    out: List[Finding] = []
    boundaries = [idx for idx, ln in enumerate(lines) if boundary_id(ln)]

    # B1b: root id + title
    if not boundaries:
        out.append(Finding("B1b", "error", path, 1,
                           'no [id="..."] line found: leben.py needs a root id before the "= " title'))
        return out
    root = boundaries[0]
    nxt = lines[root + 1] if root + 1 < len(lines) else None
    if nxt is None or not (HEADING_RE.match(nxt.text) and len(HEADING_RE.match(nxt.text).group(1)) == 1):
        out.append(Finding("B1b", "error", path, lines[root].no,
                           'the first [id="..."] must be immediately followed by the "= " document title'))
    for ln in lines[:root]:
        if not ln.in_block and re.match(r"^=\s+", ln.text):
            out.append(Finding("B1b", "error", path, ln.no,
                               'document title appears before the root [id="..."]; move the id above it'))

    # B3: boundary id must be followed by a heading; boundaries inside blocks are hazards
    for idx in boundaries:
        ln = lines[idx]
        if ln.in_block:
            out.append(Finding("B3", "error", path, ln.no,
                               'boundary [id="..."] inside a delimited block: leben.py would split here; '
                               "rewrite the example so the id is not at the start of a line"))
            continue
        following = lines[idx + 1] if idx + 1 < len(lines) else None
        if following is None or not HEADING_RE.match(following.text):
            out.append(Finding("B3", "error", path, ln.no,
                               'bare [id="..."] line is not followed by a heading, so leben.py splits here; '
                               'for a table or block put the id in its attribute list ([id="x_{context}",cols=...]), '
                               "or remove the blank/attribute line between the id and its heading"))

    # B1: '== ' headings after the first module id need an id line above
    first_module = boundaries[1] if len(boundaries) > 1 else None
    for idx, ln in enumerate(lines):
        if ln.in_block or ln.comment:
            continue
        if not re.match(r"^==\s+", ln.text):
            continue
        if first_module is None:
            out.append(Finding("B1", "error", path, ln.no,
                               '"== " section in a file with no module ids: add [id="..._{context}"] on the '
                               "line above so leben.py can split it into a module"))
            continue
        if idx < first_module:
            title = re.sub(r"^==\s+", "", ln.text).strip().lower()
            if title in PREAMBLE_HEADINGS:
                continue  # assembly preamble; leben.py keeps it in the assembly body
            out.append(Finding("B1", "error", path, ln.no,
                               '"== " section before the first module id: only "== Prerequisites" belongs in the '
                               'assembly preamble. Add [id="..._{context}"] on the line above so leben.py splits '
                               "it into a module"))
            continue
        prev = lines[idx - 1] if idx > 0 else None
        if prev is None or boundary_id(prev) is None:
            out.append(Finding("B1", "error", path, ln.no,
                               '"== " heading without [id="..."] on the line above: leben.py merges this section '
                               "into the previous module. Add an id, or turn it into a bold lead-in paragraph if it "
                               "belongs to the previous module (=== nesting is not allowed in DITA)"))
    return out


def check_shorthand_xrefs(path: Path, lines: List[Line]) -> List[Finding]:
    out: List[Finding] = []
    for ln in lines:
        if ln.in_block or ln.comment:
            continue
        for m in SHORTHAND_XREF_RE.finditer(ln.text):
            out.append(Finding("B2", "error", path, ln.no,
                               f"shorthand xref {m.group(0)}: block titles carry no anchor downstream; "
                               "use xref:id_{context}[] against a real [id=...]"))
    return out


def collect_ids(lines: List[Line]) -> Set[str]:
    ids: Set[str] = set()
    for ln in lines:
        b = boundary_id(ln)
        if b:
            ids.add(strip_context(b))
        if ln.in_block:
            continue
        m = ID_ATTR_RE.match(ln.text)
        if m:
            ids.add(strip_context(m.group(1)))
        for m in INLINE_ANCHOR_RE.finditer(ln.text):
            ids.add(strip_context(m.group(1)))
        for m in ANCHOR_MACRO_RE.finditer(ln.text):
            ids.add(strip_context(m.group(1)))
    return ids


def check_xrefs(path: Path, lines: List[Line], ids: Set[str], strict: bool) -> List[Finding]:
    out: List[Finding] = []
    severity = "error" if strict else "warning"
    for ln in lines:
        if ln.in_block or ln.comment or is_upstream_only(ln.conds):
            continue
        for m in XREF_RE.finditer(ln.text):
            target = m.group(1)
            if "{" in target.replace(CONTEXT_SUFFIX, ""):
                continue  # attribute-valued target; cannot be resolved statically
            if ".adoc" in target or "/" in target:
                out.append(Finding("W4", severity, path, ln.no,
                                   f"path-based xref '{target}': downstream resolves ids, not Antora pages, "
                                   "and only if the target assembly is in the same book. Guard it with "
                                   "ifndef::service-registry-downstream[] or use an id-based xref"))
                continue
            ident = target.split("#", 1)[1] if "#" in target else target
            if strip_context(ident) in ids:
                continue
            out.append(Finding("W4", severity, path, ln.no,
                               f"xref target '{target}' is not defined in this file (cross-assembly?): "
                               "it resolves downstream only if the target assembly is in every book that "
                               "includes this one. Otherwise guard it with ifndef::service-registry-downstream[]"))
    return out


def run(paths: List[Path], strict: bool) -> Tuple[List[Finding], List[Path]]:
    findings: List[Finding] = []
    rel_paths: List[Path] = []
    for source in paths:
        rel = repo_relative(source)
        rel_paths.append(rel)
        text = source.read_text(encoding="utf-8")
        lines, cond_problems = scan_lines(text)
        findings += check_structure(rel, lines)
        findings += check_shorthand_xrefs(rel, lines)
        findings += check_xrefs(rel, lines, collect_ids(lines), strict)
        for no, msg in cond_problems:
            findings.append(Finding("W5", "warning", rel, no, msg))
    findings.sort(key=lambda f: (-SEVERITY_RANK[f.severity], str(f.path), f.line, f.rule))
    return findings, rel_paths


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def _esc_data(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def _esc_prop(s: str) -> str:
    return _esc_data(s).replace(":", "%3A").replace(",", "%2C")


def emit_annotations(findings: List[Finding]) -> None:
    emitted = {"error": 0, "warning": 0}
    skipped = 0
    for f in findings:
        if emitted[f.severity] >= ANNOTATION_CAP:
            skipped += 1
            continue
        emitted[f.severity] += 1
        print(f"::{f.severity} file={_esc_prop(str(f.path))},line={f.line},"
              f"title={_esc_prop('downstream-compat/' + f.rule)}::{_esc_data(f.message)}")
    if skipped:
        print(f"{skipped} more finding(s) not annotated (GitHub caps annotations); see the job summary")


def render_markdown(findings: List[Finding], paths: List[Path]) -> str:
    errors = sum(1 for f in findings if f.severity == "error")
    warnings = sum(1 for f in findings if f.severity == "warning")
    lines = [
        "### Docs Verification: downstream build contract",
        "",
        f"Scope: {len(paths)} assemblies. Errors block; warnings are informational.",
        "",
        "| Assembly | Errors | Warnings |",
        "|---|---:|---:|",
    ]
    for p in paths:
        e = sum(1 for f in findings if f.path == p and f.severity == "error")
        w = sum(1 for f in findings if f.path == p and f.severity == "warning")
        lines.append(f"| `{p.name}` | {e} | {w} |")
    lines += ["", f"**Result: {'FAIL' if errors else 'PASS'}, {errors} errors, {warnings} warnings**"]
    if findings:
        lines += ["", f"<details><summary>Findings ({len(findings)})</summary>", ""]
        for f in findings:
            lines.append(f"- **{f.severity}** `{f.rule}` `{f.path.name}` line {f.line}: {f.message}")
        lines += ["", "</details>"]
    text = "\n".join(lines) + "\n"
    if len(text) > SUMMARY_CAP:
        text = text[:SUMMARY_CAP] + "\n\n_(summary truncated)_\n"
    return text


def print_report(findings: List[Finding], paths: List[Path]) -> None:
    print(f"downstream-compat: {len(paths)} assemblies")
    for f in findings:
        print(f"{f.severity.upper():<8} {f.rule:<4} {f.path.name}:{f.line}: {f.message}")
    errors = sum(1 for f in findings if f.severity == "error")
    warnings = sum(1 for f in findings if f.severity == "warning")
    print()
    print(f"Result: {'FAIL' if errors else 'PASS'}, {errors} errors, {warnings} warnings")


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    try:
        requested = list(args.files)
        if args.files_from:
            requested += read_file_list(args.files_from)
        targets = resolve_targets(requested)
        if not targets:
            print("downstream-compat: no assemblies to check")
            return 0
        findings, paths = run(targets, args.strict_xrefs)
        print_report(findings, paths)
        if args.annotations:
            emit_annotations(findings)
        if args.summary:
            with open(args.summary, "a", encoding="utf-8") as fh:
                fh.write(render_markdown(findings, paths))
        return 1 if any(f.severity == "error" for f in findings) else 0
    except ToolError as exc:
        print(f"downstream-compat: {exc}", file=sys.stderr)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error title=downstream-compat::{_esc_data(str(exc))}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
