#!/usr/bin/env python3
"""Split getting-started assemblies with leben.py, then run vale over the split output.

Vale on a raw monolithic assembly is noisy by design: NestedSection, AssemblyContents and
friends fire on structure that disappears once the downstream splitter (leben.py) has
turned the monolith into an assembly plus modules. The downstream build lints the split
output, so this runner does the same and is the view CI gates on.

Usage:
  python3 docs/scripts/vale-pipeline.py                        # all getting-started assemblies
  python3 docs/scripts/vale-pipeline.py docs/modules/ROOT/pages/getting-started/assembly-foo.adoc ...
  python3 docs/scripts/vale-pipeline.py --files-from list.txt  # one path per line ('-' = stdin)

Options:
  --out-dir DIR      keep the split output, leben.log and vale.json here
                     (default: a temporary directory under <repo>/.vale, removed on exit)
  --vale PATH        vale binary (default: 'vale' on PATH)
  --config PATH      vale config (default: <repo>/.vale.ini)
  --fail-on LEVEL    error | warning | none  (default: error)
  --summary PATH     append a Markdown summary here (default: $GITHUB_STEP_SUMMARY if set)
  --annotations / --no-annotations
                     print GitHub ::error/::warning commands (default: on when GITHUB_ACTIONS=true)
  --verbose          echo leben's stderr

Exit codes: 0 pass, 1 gate failed (alerts at or above --fail-on), 2 tool failure.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Dict, List, Optional

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
LEBEN = HERE / "leben.py"
ASSEMBLY_DIR = REPO / "docs" / "modules" / "ROOT" / "pages" / "getting-started"
ASSEMBLY_GLOB = "assembly-*.adoc"
DEFAULT_CONFIG = REPO / ".vale.ini"

# Must stay identical to SplitTask.id_pattern in leben.py: it is the split boundary.
LEBEN_ID_RE = re.compile(r'^\s*(?:\[\[([^\]]+)\]\]|\[id="([^"\]]+)"\])')
# leben writes '[id="..."]', '= Title', '' and then the body verbatim.
LEBEN_HEADER_LINES = 3

SEVERITY_RANK = {"suggestion": 0, "warning": 1, "error": 2}
ANNOTATION_CAP = 10          # GitHub renders at most 10 per severity per step
SUMMARY_CAP = 900_000        # GITHUB_STEP_SUMMARY is capped at 1 MiB


class ToolError(Exception):
    """A tool (leben, vale, asciidoctor) is missing or crashed; exit 2."""


@dataclass
class Alert:
    assembly: Path            # repo-relative monolith path
    split_file: str           # e.g. modules/foo.adoc, relative to the assembly's work dir
    check: str                # "AsciiDocDITA.NestedSection" or synthetic "leben.NoRootId"
    severity: str
    line: int                 # line in the split file
    message: str
    monolith_line: Optional[int] = None


@dataclass
class AssemblyResult:
    assembly: Path            # repo-relative
    source: Path              # absolute
    work_dir: Path
    modules: List[Path] = field(default_factory=list)
    assembly_out: Optional[Path] = None
    alerts: List[Alert] = field(default_factory=list)

    def count(self, severity: str) -> int:
        return sum(1 for a in self.alerts if a.severity == severity)


# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------

def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="vale-pipeline.py",
        description="Split assemblies with leben.py and run vale over the split output.",
    )
    p.add_argument("files", nargs="*", help="assembly paths (default: all getting-started assemblies)")
    p.add_argument("--files-from", metavar="FILE", help="read assembly paths from FILE ('-' for stdin)")
    p.add_argument("--out-dir", metavar="DIR", help="keep split output, leben.log and vale.json here")
    p.add_argument("--vale", default="vale", help="vale binary (default: vale)")
    p.add_argument("--config", default=str(DEFAULT_CONFIG), help="vale config (default: <repo>/.vale.ini)")
    p.add_argument("--fail-on", choices=["error", "warning", "none"], default="error")
    p.add_argument("--summary", metavar="PATH", default=os.environ.get("GITHUB_STEP_SUMMARY"))
    ann = p.add_mutually_exclusive_group()
    ann.add_argument("--annotations", dest="annotations", action="store_true")
    ann.add_argument("--no-annotations", dest="annotations", action="store_false")
    p.set_defaults(annotations=os.environ.get("GITHUB_ACTIONS") == "true")
    p.add_argument("--verbose", action="store_true")
    return p.parse_args(argv)


def read_file_list(source: str) -> List[str]:
    text = sys.stdin.read() if source == "-" else Path(source).read_text(encoding="utf-8")
    out = []
    for raw in text.splitlines():
        line = raw.strip()
        if line and not line.startswith("#"):
            out.append(line)
    return out


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


def check_tools(vale_bin: str, config: Path) -> str:
    vale_path = shutil.which(vale_bin)
    if not vale_path:
        raise ToolError(f"vale not found ({vale_bin}); install vale 3.x and put it on PATH")
    if not shutil.which("asciidoctor"):
        raise ToolError("asciidoctor not found on PATH; vale needs it to parse AsciiDoc")
    if not config.is_file():
        raise ToolError(f"vale config not found: {config}")
    if not LEBEN.is_file():
        raise ToolError(f"leben.py not found next to this script: {LEBEN}")
    return vale_path


# ---------------------------------------------------------------------------
# Split
# ---------------------------------------------------------------------------

def boundary_ids(lines: List[str]) -> List[str]:
    ids = []
    for line in lines:
        m = LEBEN_ID_RE.match(line)
        if m:
            ids.append(m.group(1) or m.group(2))
    return ids


def split_assembly(source: Path, work_root: Path, verbose: bool) -> AssemblyResult:
    work_dir = work_root / source.stem
    work_dir.mkdir(parents=True, exist_ok=True)
    result = AssemblyResult(assembly=repo_relative(source), source=source, work_dir=work_dir)

    proc = subprocess.run(
        [sys.executable, str(LEBEN), str(source)],
        cwd=str(work_dir), capture_output=True, text=True,
    )
    with (work_root / "leben.log").open("a", encoding="utf-8") as log:
        log.write(f"### {result.assembly}\n{proc.stderr}\n")
    if verbose and proc.stderr:
        sys.stderr.write(proc.stderr)
    if proc.returncode != 0:
        raise ToolError(f"leben.py failed on {result.assembly} (exit {proc.returncode}):\n{proc.stderr}")

    result.modules = sorted((work_dir / "modules").glob("*.adoc")) if (work_dir / "modules").is_dir() else []
    assemblies = sorted((work_dir / "assemblies").glob("*.adoc")) if (work_dir / "assemblies").is_dir() else []
    result.assembly_out = assemblies[0] if assemblies else None

    monolith_lines = source.read_text(encoding="utf-8").splitlines()
    ids = boundary_ids(monolith_lines)
    if result.assembly_out is None:
        result.alerts.append(Alert(
            result.assembly, "(none)", "leben.NoRootId", "error", 1,
            "leben.py produced no assembly: the file has no [id=\"...\"] line before its title",
        ))
        return result
    expected_modules = max(len(ids) - 1, 0)
    if len(result.modules) != expected_modules:
        result.alerts.append(Alert(
            result.assembly, "modules/", "leben.ModuleCollision", "error", 1,
            f"leben.py wrote {len(result.modules)} modules for {expected_modules} module ids: "
            "two ids in this file map to the same module filename, so one silently overwrote the other",
        ))
    return result


# ---------------------------------------------------------------------------
# Vale
# ---------------------------------------------------------------------------

def run_vale(work_root: Path, vale_path: str, config: Path) -> Dict[str, list]:
    cmd = [vale_path, "--config", str(config.resolve()), "--output=JSON", "--no-exit", "."]
    proc = subprocess.run(cmd, cwd=str(work_root), capture_output=True, text=True)
    (work_root / "vale.json").write_text(proc.stdout, encoding="utf-8")
    if proc.returncode != 0:
        raise ToolError(f"vale failed (exit {proc.returncode}):\n{proc.stderr or proc.stdout}")
    try:
        data = json.loads(proc.stdout or "{}")
    except json.JSONDecodeError as exc:
        raise ToolError(f"vale produced invalid JSON: {exc}\n{proc.stdout[:2000]}") from exc
    if not isinstance(data, dict):
        raise ToolError(f"unexpected vale JSON shape: {type(data).__name__}")
    return data


def attribute_alerts(vale_json: Dict[str, list], results: Dict[str, AssemblyResult], work_root: Path) -> None:
    root = work_root.resolve()
    for key, items in vale_json.items():
        path = Path(key)
        if not path.is_absolute():
            path = root / path
        try:
            rel = path.resolve().relative_to(root)
        except ValueError:
            continue
        if not rel.parts:
            continue
        result = results.get(rel.parts[0])
        if result is None:
            continue
        split_file = "/".join(rel.parts[1:])
        for item in items:
            severity = str(item.get("Severity", "")).lower()
            if severity == "suggestion":
                continue
            result.alerts.append(Alert(
                result.assembly, split_file, str(item.get("Check", "?")), severity,
                int(item.get("Line", 0) or 0), str(item.get("Message", "")).strip(),
            ))


def build_line_map(monolith_lines: List[str], split_path: Path) -> Callable[[int], Optional[int]]:
    """Map a split-file line to the monolith line it came from, when unambiguous."""
    try:
        first = split_path.read_text(encoding="utf-8").splitlines()[0]
    except (OSError, IndexError):
        return lambda n: None
    m = LEBEN_ID_RE.match(first)
    if not m:
        return lambda n: None
    mid = m.group(1) or m.group(2)
    id_lines = [i + 1 for i, line in enumerate(monolith_lines)
                if (lambda mm: mm and (mm.group(1) or mm.group(2)) == mid)(LEBEN_ID_RE.match(line))]
    if len(id_lines) != 1:
        return lambda n: None
    start = id_lines[0]
    later = [i + 1 for i, line in enumerate(monolith_lines) if i + 1 > start and LEBEN_ID_RE.match(line)]
    end = later[0] if later else len(monolith_lines) + 1

    def mapper(n: int) -> Optional[int]:
        if n <= LEBEN_HEADER_LINES:
            return start
        candidate = start + (n - 2)
        return candidate if candidate < end else None

    return mapper


def map_alert_lines(result: AssemblyResult) -> None:
    monolith_lines = result.source.read_text(encoding="utf-8").splitlines()
    mappers: Dict[str, Callable[[int], Optional[int]]] = {}
    for alert in result.alerts:
        if alert.check.startswith("leben."):
            continue
        if alert.split_file not in mappers:
            mappers[alert.split_file] = build_line_map(monolith_lines, result.work_dir / alert.split_file)
        alert.monolith_line = mappers[alert.split_file](alert.line)


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def _esc_data(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def _esc_prop(s: str) -> str:
    return _esc_data(s).replace(":", "%3A").replace(",", "%2C")


def sorted_alerts(results: List[AssemblyResult]) -> List[Alert]:
    alerts = [a for r in results for a in r.alerts]
    return sorted(alerts, key=lambda a: (-SEVERITY_RANK.get(a.severity, 0), str(a.assembly), a.split_file, a.line))


def emit_annotations(results: List[AssemblyResult]) -> None:
    emitted = {"error": 0, "warning": 0}
    skipped = 0
    for a in sorted_alerts(results):
        if a.severity not in emitted:
            continue
        if emitted[a.severity] >= ANNOTATION_CAP:
            skipped += 1
            continue
        emitted[a.severity] += 1
        props = f"file={_esc_prop(str(a.assembly))}"
        if a.monolith_line:
            props += f",line={a.monolith_line}"
        props += f",title={_esc_prop('vale/' + a.check)}"
        print(f"::{a.severity} {props}::{_esc_data(a.message)} (in split {a.split_file}:{a.line})")
    if skipped:
        print(f"{skipped} more alert(s) not annotated (GitHub caps annotations); see the job summary or artifact")


def render_markdown(results: List[AssemblyResult], mode: str) -> str:
    errors = sum(r.count("error") for r in results)
    warnings = sum(r.count("warning") for r in results)
    lines = [
        "### Docs Verification: vale (AsciiDocDITA) on leben-split output",
        "",
        f"Scope: {len(results)} assemblies ({mode}). Errors block; warnings are informational.",
        "",
        "| Assembly | Modules | Errors | Warnings |",
        "|---|---:|---:|---:|",
    ]
    for r in results:
        lines.append(f"| `{r.assembly.name}` | {len(r.modules)} | {r.count('error')} | {r.count('warning')} |")
    lines.append("")
    verdict = "FAIL" if errors else "PASS"
    lines.append(f"**Result: {verdict}, {errors} errors, {warnings} warnings**")
    alerts = sorted_alerts(results)
    if alerts:
        lines += ["", f"<details><summary>Alerts ({len(alerts)})</summary>", ""]
        current = None
        for a in alerts:
            key = (str(a.assembly), a.split_file)
            if key != current:
                current = key
                lines.append(f"**`{a.assembly.name}` / `{a.split_file}`**")
                lines.append("")
            where = f"line {a.line}"
            if a.monolith_line:
                where += f" (monolith line {a.monolith_line})"
            lines.append(f"- **{a.severity}** `{a.check}` {where}: {a.message}")
        lines += ["", "</details>"]
    text = "\n".join(lines) + "\n"
    if len(text) > SUMMARY_CAP:
        text = text[:SUMMARY_CAP] + "\n\n_(summary truncated)_\n"
    return text


def print_report(results: List[AssemblyResult], mode: str) -> None:
    print(f"vale-pipeline: {len(results)} assemblies ({mode})")
    print(f"{'assembly':<60} {'modules':>7} {'errors':>6} {'warnings':>8}")
    for r in results:
        print(f"{r.assembly.name:<60} {len(r.modules):>7} {r.count('error'):>6} {r.count('warning'):>8}")
    alerts = sorted_alerts(results)
    if alerts:
        print()
        for a in alerts:
            where = f"{a.split_file}:{a.line}"
            if a.monolith_line:
                where += f" (monolith line {a.monolith_line})"
            print(f"{a.severity.upper():<8} {a.assembly.name} {where} {a.check}: {a.message}")
    errors = sum(r.count("error") for r in results)
    warnings = sum(r.count("warning") for r in results)
    print()
    print(f"Result: {'FAIL' if errors else 'PASS'}, {errors} errors, {warnings} warnings")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    try:
        requested = list(args.files)
        if args.files_from:
            requested += read_file_list(args.files_from)
        targets = resolve_targets(requested)
        mode = "changed" if requested else "all"
        if not targets:
            print("vale-pipeline: no assemblies to lint")
            return 0
        config = Path(args.config)
        vale_path = check_tools(args.vale, config)

        temp_dir: Optional[tempfile.TemporaryDirectory] = None
        if args.out_dir:
            work_root = Path(args.out_dir).resolve()
            if work_root.exists():
                shutil.rmtree(work_root)
            work_root.mkdir(parents=True)
        else:
            # Under the repo rather than /tmp: snap-confined vale cannot read /tmp.
            scratch_parent = REPO / ".vale"
            scratch_parent.mkdir(exist_ok=True)
            temp_dir = tempfile.TemporaryDirectory(prefix="pipeline-", dir=str(scratch_parent))
            work_root = Path(temp_dir.name)

        try:
            results: Dict[str, AssemblyResult] = {}
            for source in targets:
                if source.stem in results:
                    raise ToolError(f"two assemblies share the stem {source.stem}")
                results[source.stem] = split_assembly(source, work_root, args.verbose)
            vale_json = run_vale(work_root, vale_path, config)
            attribute_alerts(vale_json, results, work_root)
            ordered = [results[s.stem] for s in targets]
            for r in ordered:
                map_alert_lines(r)
        finally:
            if temp_dir is not None:
                temp_dir.cleanup()

        print_report(ordered, mode)
        if args.annotations:
            emit_annotations(ordered)
        if args.summary:
            with open(args.summary, "a", encoding="utf-8") as fh:
                fh.write(render_markdown(ordered, mode))
        if args.out_dir:
            print(f"Split output and vale.json kept in {work_root}")

        if args.fail_on == "none":
            return 0
        threshold = SEVERITY_RANK[args.fail_on]
        failing = any(SEVERITY_RANK.get(a.severity, 0) >= threshold for r in ordered for a in r.alerts)
        return 1 if failing else 0
    except ToolError as exc:
        msg = str(exc)
        print(f"vale-pipeline: {msg}", file=sys.stderr)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error title=vale-pipeline::{_esc_data(msg.splitlines()[0])}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
