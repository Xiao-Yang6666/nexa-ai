#!/usr/bin/env python3
"""Phase 2C: rename shared kernel package  com.nexa.shared -> com.nexa.common

Pure package rename: directory shared/ -> common/, and rewrite every
'com.nexa.shared' occurrence to 'com.nexa.common' in all java sources.
"""
import os, re, subprocess, sys

BACKEND = r"E:/dev/code/java/nexa-ai/backend"
ROOTS = ["src/main/java", "src/test/java"]

def main():
    # 1. git mv directory shared -> common for both roots
    for root in ROOTS:
        old = f"{root}/com/nexa/shared"
        new = f"{root}/com/nexa/common"
        if os.path.isdir(os.path.join(BACKEND, old)):
            r = subprocess.run(["git", "mv", old, new], cwd=BACKEND,
                               capture_output=True, text=True)
            if r.returncode != 0:
                print("FAIL mv", old, r.stderr.strip()); sys.exit(1)
            print(f"git mv {old} -> {new}")

    # 2. rewrite references  com.nexa.shared -> com.nexa.common
    pat = re.compile(r"\bcom\.nexa\.shared\b")
    changed = 0
    for root in ROOTS:
        for dp, _, files in os.walk(os.path.join(BACKEND, root)):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                p = os.path.join(dp, fn)
                with open(p, encoding="utf-8") as fh:
                    src = fh.read()
                new = pat.sub("com.nexa.common", src)
                if new != src:
                    with open(p, "w", encoding="utf-8") as fh:
                        fh.write(new)
                    changed += 1
    print(f"rewrote {changed} files")

if __name__ == "__main__":
    main()
