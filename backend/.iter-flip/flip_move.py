#!/usr/bin/env python3
"""Phase 1: flip context-first -> layer-first package structure.

  com.nexa.<ctx>.<layer>.<rest>            -> com.nexa.<layer>.<ctx>.<rest>
  com.nexa.account.provider.<layer>.<rest> -> com.nexa.<layer>.account.provider.<rest>
  com.nexa.shared.*                        -> unchanged (handled in Phase 2)
  NexaApplication.java (root)              -> unchanged
"""
import os, re, subprocess, sys

BACKEND = r"E:/dev/code/java/nexa-ai/backend"
LAYERS = {"application", "domain", "infrastructure", "interfaces"}
ROOTS = ["src/main/java/com/nexa", "src/test/java/com/nexa"]

def fqcn_old_to_new(pkgpath):
    """pkgpath: dotted segments after com.nexa, e.g. 'account.application' or
    'account.provider.domain.model'. Returns new dotted path or None if unchanged."""
    parts = pkgpath.split(".")
    if not parts:
        return None
    ctx = parts[0]
    if ctx == "shared":
        return None
    # account.provider.<layer>...
    if ctx == "account" and len(parts) >= 3 and parts[1] == "provider" and parts[2] in LAYERS:
        layer = parts[2]
        rest = parts[3:]
        return ".".join([layer, "account", "provider"] + rest)
    # <ctx>.<layer>...
    if len(parts) >= 2 and parts[1] in LAYERS:
        layer = parts[1]
        rest = parts[2:]
        return ".".join([layer, ctx] + rest)
    return None

def build_moves():
    moves = []  # (old_relpath, new_relpath) within BACKEND
    unhandled = []
    for root in ROOTS:
        absroot = os.path.join(BACKEND, root)
        for dirpath, _, files in os.walk(absroot):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                absf = os.path.join(dirpath, fn).replace("\\", "/")
                rel_in_root = absf[len(absroot)+1:]  # e.g. account/application/Foo.java
                segs = rel_in_root.split("/")
                if len(segs) == 1:  # root file like NexaApplication.java
                    continue
                ctx = segs[0]
                if ctx == "shared":
                    continue
                pkgpath = ".".join(segs[:-1])  # dir part dotted
                newpkg = fqcn_old_to_new(pkgpath)
                if newpkg is None:
                    unhandled.append(absf)
                    continue
                newrel = newpkg.replace(".", "/") + "/" + segs[-1]
                moves.append((absf, (absroot + "/" + newrel)))
    return moves, unhandled

def main():
    moves, unhandled = build_moves()
    print(f"main+test moves: {len(moves)}")
    print(f"UNHANDLED: {len(unhandled)}")
    for u in unhandled:
        print("  UNHANDLED:", u)
    # sample
    for o, n in moves[:6]:
        print("  ", o.split("/com/nexa/")[1], "->", n.split("/com/nexa/")[1])
    prov = [m for m in moves if "/provider/" in m[0]]
    print(f"provider moves: {len(prov)}")
    for o, n in prov[:3]:
        print("  ", o.split("/com/nexa/")[1], "->", n.split("/com/nexa/")[1])
    if "--apply" not in sys.argv:
        print("\n(dry-run; pass --apply to execute git mv)")
        return
    # apply via git mv
    for o, n in moves:
        os.makedirs(os.path.dirname(n), exist_ok=True)
        r = subprocess.run(["git", "mv", o, n], cwd=BACKEND,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print("FAIL git mv", o, "->", n, ":", r.stderr.strip())
            sys.exit(1)
    print(f"moved {len(moves)} files via git mv")

if __name__ == "__main__":
    main()
