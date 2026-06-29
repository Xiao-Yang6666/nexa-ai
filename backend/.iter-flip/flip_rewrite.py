#!/usr/bin/env python3
"""Phase 1b: rewrite `package` declarations and `com.nexa.*` references in all
Java source files to match the flipped layout.

Transform applies to any dotted segment-run starting with com.nexa:
  com.nexa.<ctx>.<layer>...            -> com.nexa.<layer>.<ctx>...
  com.nexa.account.provider.<layer>... -> com.nexa.<layer>.account.provider...
  com.nexa.shared...                   -> unchanged
  com.nexa.mapping...                  -> unchanged (cross-domain test support)
"""
import os, re, sys

BACKEND = r"E:/dev/code/java/nexa-ai/backend"
LAYERS = {"application", "domain", "infrastructure", "interfaces"}
ROOTS = ["src/main/java", "src/test/java"]

# match com.nexa followed by dotted identifiers (package/import/qualified refs)
PAT = re.compile(r"com\.nexa((?:\.[A-Za-z_][A-Za-z0-9_]*)+)")

def flip_tail(tail):
    """tail starts with '.', e.g. '.account.application.Foo'. Return flipped tail or None."""
    parts = tail[1:].split(".")  # drop leading dot
    if not parts:
        return None
    ctx = parts[0]
    if ctx in ("shared", "mapping"):
        return None
    if ctx == "account" and len(parts) >= 3 and parts[1] == "provider" and parts[2] in LAYERS:
        layer = parts[2]
        rest = parts[3:]
        newparts = [layer, "account", "provider"] + rest
        return "." + ".".join(newparts)
    if len(parts) >= 2 and parts[1] in LAYERS:
        layer = parts[1]
        rest = parts[2:]
        newparts = [layer, ctx] + rest
        return "." + ".".join(newparts)
    return None

def repl(m):
    tail = m.group(1)
    new = flip_tail(tail)
    if new is None:
        return m.group(0)
    return "com.nexa" + new

def main():
    changed = 0
    scanned = 0
    for root in ROOTS:
        absroot = os.path.join(BACKEND, root)
        for dirpath, _, files in os.walk(absroot):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                p = os.path.join(dirpath, fn)
                scanned += 1
                with open(p, "r", encoding="utf-8") as fh:
                    src = fh.read()
                new = PAT.sub(repl, src)
                if new != src:
                    with open(p, "w", encoding="utf-8") as fh:
                        fh.write(new)
                    changed += 1
    print(f"scanned {scanned} java files, rewrote {changed}")

if __name__ == "__main__":
    main()
