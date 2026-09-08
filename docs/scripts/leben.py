#!/usr/bin/env python3
"""
Standalone Nebel Flat Script (updated)

Extracted 'split' functionality without AsciiDoc metadata annotations.

Supports both [[id]] and [id="..."] styles, and directory-wide processing of .adoc files.

New behavior per file:
  1. Derive category and assembly ID from the first [[id]] or [id="..."] encountered.
  2. Treat the first section as the assembly, subsequent as modules.
  3. Module filenames are the ID with .adoc; assemblies prefixed with 'assembly-'.
  4. Preserve assembly body before modules.
  5. Write modules under 'modules/', assemblies under 'assemblies/'.
  6. Assembly includes all modules via include:: directives.

Usage:
  leben.py <file.adoc|glob|directory>
"""
import os
import re
import sys
import glob
import argparse


def eprint(*args, **kwargs):
    """Print to stderr for logging and debugging."""
    print(*args, file=sys.stderr, **kwargs)

SCRIPT_VERSION = '20221205'

class NebelContext:
    def __init__(self):
        self.ASSEMBLIES_DIR = 'assemblies'
        self.MODULES_DIR    = 'modules'
        self.ASSEMBLY_PREFIX = 'assembly-'

class ModuleFactory:
    def __init__(self, ctx: NebelContext):
        self.ctx = ctx

    def name_of_file(self, mid, is_assembly=False):
        core = mid.replace('{context}', '').rstrip('_-').replace('_', '-')
        if is_assembly:
            return f"{self.ctx.ASSEMBLY_PREFIX}{core}.adoc"
        return f"{core}.adoc"

    def write(self, mid, title, lines, is_assembly=False):
        """Write an assembly or module file, logging path to stderr."""
        dirp = self.ctx.ASSEMBLIES_DIR if is_assembly else self.ctx.MODULES_DIR
        os.makedirs(dirp, exist_ok=True)
        fname = self.name_of_file(mid, is_assembly)
        path = os.path.join(dirp, fname)
        eprint(f"Writing {'assembly' if is_assembly else 'module'}: {path}")
        with open(path, 'w') as f:
            f.write(f"[id=\"{mid}\"]\n")
            f.write(f"= {title}\n\n")
            f.writelines(lines)
        return path

class SplitTask:
    def __init__(self):
        self.ctx = NebelContext()
        self.fact = ModuleFactory(self.ctx)
        # Match [[id]] OR [id="..."]
        self.id_pattern = re.compile(r'^\s*(?:\[\[([^\]]+)\]\]|\[id="([^"\]]+)"\])')
        self.heading_pattern = re.compile(r'^(=+)\s+(.*)')

    def process_file(self, filepath):
        """Split a single .adoc file into one assembly and its modules."""
        with open(filepath) as fh:
            lines = fh.readlines()
        if not lines:
            eprint(f"No content in {filepath}")
            return

        # Identify root assembly ID and title
        root_mid = root_title = None
        idx = 0
        for i, ln in enumerate(lines):
            m = self.id_pattern.match(ln)
            if m:
                root_mid = m.group(1) or m.group(2)
                # Check next line for level-1 heading
                if i+1 < len(lines):
                    mh = self.heading_pattern.match(lines[i+1])
                    if mh and len(mh.group(1)) == 1:
                        root_title = mh.group(2)
                        idx = i + 2
                        break
                root_title = root_mid
                idx = i + 1
                break
        if not root_mid:
            eprint(f"ERROR: No root id in {filepath}")
            return

        # Collect assembly body until first module id
        assembly_body = []
        while idx < len(lines) and not self.id_pattern.match(lines[idx]):
            assembly_body.append(lines[idx])
            idx += 1

        # Process modules
        includes = []
        while idx < len(lines):
            m = self.id_pattern.match(lines[idx])
            if not m:
                idx += 1
                continue
            mid = m.group(1) or m.group(2)
            idx += 1
            title = mid
            if idx < len(lines):
                mh = self.heading_pattern.match(lines[idx])
                if mh and len(mh.group(1)) > 1:
                    title = mh.group(2)
                    idx += 1
            module_body = []
            while idx < len(lines) and not self.id_pattern.match(lines[idx]):
                module_body.append(lines[idx])
                idx += 1
            module_path = self.fact.write(mid, title, module_body, is_assembly=False)
            rel = os.path.relpath(module_path, self.ctx.ASSEMBLIES_DIR)
            includes.append(f"include::{rel}[leveloffset=+1]\n")

        # Write assembly
        assembly_lines = assembly_body + [''] + includes
        self.fact.write(root_mid, root_title, assembly_lines, is_assembly=True)

    def adoc_split(self, infile):
        # Determine list of files: single, glob, or directory
        if os.path.isdir(infile):
            filepaths = [os.path.join(infile, fn) for fn in os.listdir(infile) if fn.endswith('.adoc')]
        else:
            filepaths = glob.glob(infile)
        if not filepaths:
            eprint(f"No .adoc files found for {infile}")
            sys.exit(1)
        for fp in filepaths:
            if os.path.isdir(fp):
                continue
            eprint(f"Processing file: {fp}")
            self.process_file(fp)


def main():
    p = argparse.ArgumentParser(
        prog='leben.py',
        description='Split AsciiDoc without annotations'
    )
    p.add_argument('FROM_FILE', help='Input .adoc, glob, or directory')
    args = p.parse_args()
    SplitTask().adoc_split(args.FROM_FILE)

if __name__ == '__main__':
    main()
