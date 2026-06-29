#!/usr/bin/env python3
"""Phase 2B: JPA persistence objects  *JpaEntity -> *PO,  package .persistence.entity -> .persistence.po

- git mv each .../persistence/entity/XxxJpaEntity.java -> .../persistence/po/XxxPO.java
- rewrite package segment   '.persistence.entity'  -> '.persistence.po'  (imports + package decls)
- rewrite class token       \bXxxJpaEntity\b        -> XxxPO
"""
import os, re, subprocess, sys

BACKEND = r"E:/dev/code/java/nexa-ai/backend"
ROOTS = ["src/main/java", "src/test/java"]

def main():
    # collect JpaEntity class names
    names = set()
    for root in ROOTS:
        for dp, _, files in os.walk(os.path.join(BACKEND, root)):
            for fn in files:
                if fn.endswith("JpaEntity.java"):
                    names.add(fn[:-5])
    print(f"JpaEntity classes: {len(names)}")
    new_of = {n: n[:-len("JpaEntity")] + "PO" for n in names}

    # 1. git mv files: entity/ -> po/, rename file
    moved = 0
    for root in ROOTS:
        for dp, _, files in os.walk(os.path.join(BACKEND, root)):
            dp_n = dp.replace("\\", "/")
            if not dp_n.endswith("/persistence/entity"):
                continue
            for fn in files:
                if not fn.endswith("JpaEntity.java"):
                    continue
                base = fn[:-5]
                old = dp_n + "/" + fn
                newdir = dp_n[:-len("/entity")] + "/po"
                os.makedirs(newdir, exist_ok=True)
                new = newdir + "/" + new_of[base] + ".java"
                r = subprocess.run(["git", "mv", old, new], cwd=BACKEND,
                                   capture_output=True, text=True)
                if r.returncode != 0:
                    print("FAIL mv", old, r.stderr.strip()); sys.exit(1)
                moved += 1
    print(f"git mv: {moved}")

    # 2. rewrite text
    ordered = sorted(names, key=len, reverse=True)
    cls_pat = re.compile(r"\b(" + "|".join(re.escape(n) for n in ordered) + r")\b")
    changed = 0
    for root in ROOTS:
        for dp, _, files in os.walk(os.path.join(BACKEND, root)):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                p = os.path.join(dp, fn)
                with open(p, encoding="utf-8") as fh:
                    src = fh.read()
                new = src.replace(".persistence.entity", ".persistence.po")
                new = cls_pat.sub(lambda m: new_of[m.group(1)], new)
                if new != src:
                    with open(p, "w", encoding="utf-8") as fh:
                        fh.write(new)
                    changed += 1
    print(f"rewrote {changed} files")

if __name__ == "__main__":
    main()
