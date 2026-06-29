#!/usr/bin/env python3
"""Phase 2A: rename interface output DTOs  *View -> *VO.

- git mv the 66 *View.java files to *VO.java
- replace exact class-name token \bXxxView\b -> XxxVO across all sources
  (scoped to the renamed set, so ModelAndViewContainer etc. untouched)
- also rename factory/helper method names to<Xxx>View -> to<Xxx>VO for consistency
"""
import os, re, subprocess, sys

BACKEND = r"E:/dev/code/java/nexa-ai/backend"
ROOTS = ["src/main/java", "src/test/java"]

def collect_view_classes():
    names = set()
    for root in ROOTS:
        absroot = os.path.join(BACKEND, root)
        for dp, _, files in os.walk(absroot):
            for fn in files:
                if fn.endswith("View.java"):
                    names.add(fn[:-5])  # strip .java
    return names

def main():
    names = collect_view_classes()
    print(f"View classes: {len(names)}")
    new_of = {n: n[:-4] + "VO" for n in names}  # Foo'View' -> Foo'VO'

    # 1. git mv files
    moved = 0
    for root in ROOTS:
        absroot = os.path.join(BACKEND, root)
        for dp, _, files in os.walk(absroot):
            for fn in files:
                if fn.endswith("View.java") and fn[:-5] in names:
                    old = os.path.join(dp, fn).replace("\\", "/")
                    new = os.path.join(dp, new_of[fn[:-5]] + ".java").replace("\\", "/")
                    r = subprocess.run(["git", "mv", old, new], cwd=BACKEND,
                                       capture_output=True, text=True)
                    if r.returncode != 0:
                        print("FAIL mv", old, r.stderr.strip()); sys.exit(1)
                    moved += 1
    print(f"git mv: {moved}")

    # 2. build replacement patterns
    # exact class token  + method names to<Name>  (where <Name>View -> <Name>VO)
    # process longer names first to avoid partial overlap
    ordered = sorted(names, key=len, reverse=True)
    # class token pattern
    cls_pat = re.compile(r"\b(" + "|".join(re.escape(n) for n in ordered) + r")\b")
    # method-name pattern: to + base + View  -> to + base + VO  (base = name without trailing View)
    bases = sorted({n[:-4] for n in names}, key=len, reverse=True)  # strip "View"
    meth_pat = re.compile(r"\bto(" + "|".join(re.escape(b) for b in bases) + r")View\b")

    changed = 0
    for root in ROOTS:
        absroot = os.path.join(BACKEND, root)
        for dp, _, files in os.walk(absroot):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                p = os.path.join(dp, fn)
                with open(p, encoding="utf-8") as fh:
                    src = fh.read()
                new = meth_pat.sub(lambda m: "to" + m.group(1) + "VO", src)
                new = cls_pat.sub(lambda m: new_of[m.group(1)], new)
                if new != src:
                    with open(p, "w", encoding="utf-8") as fh:
                        fh.write(new)
                    changed += 1
    print(f"rewrote {changed} files")

if __name__ == "__main__":
    main()
