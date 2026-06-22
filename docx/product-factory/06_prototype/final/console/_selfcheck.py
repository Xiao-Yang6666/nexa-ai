#!/usr/bin/env python3
import os, re, subprocess, sys

D = "/root/.hermes/projects/routifyapi-newapi-product/06_prototype/final/console"
PAGES = ["dashboard.html","keys.html","usage.html","billing.html","recharge.html"]

def extract_inline_js(html):
    # 抓取 <script>...</script> 内联块（无 src 的）
    blocks=[]
    for m in re.finditer(r'<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>', html, re.S|re.I):
        blocks.append(m.group(1))
    return blocks

print("="*64)
print("Nexa·AI 控制台原型 — 自检结果")
print("="*64)

all_ok = True

# ① 5 文件均 > 10KB
print("\n[1] 文件存在且 > 10KB")
for p in PAGES:
    fp=os.path.join(D,p)
    if not os.path.exists(fp):
        print(f"  FAIL  {p}: 不存在"); all_ok=False; continue
    kb=os.path.getsize(fp)/1024
    ok=kb>10
    all_ok &= ok
    print(f"  {'OK ' if ok else 'FAIL'}  {p}: {kb:.1f} KB")
# shell 文件
shp=os.path.join(D,"console-shell.js")
print(f"  ---   console-shell.js: {os.path.getsize(shp)/1024:.1f} KB (共享外壳)")

# ② 裸色值 = 0
print("\n[2] 裸色值扫描 (hex / rgb( / 裸 oklch( )")
hex_re = re.compile(r'(color|background|border|fill|stroke)[^;{:]*:[^;]*#[0-9a-fA-F]{3,6}')
rgb_re = re.compile(r'\brgba?\(')
# 裸 oklch：oklch( 前面不是 "in "（即不在 color-mix(in oklch,...) 里）
oklch_bare_re = re.compile(r'(?<!in )\boklch\(')
files_to_scan = PAGES + ["console-shell.js"]
for p in files_to_scan:
    txt=open(os.path.join(D,p),encoding="utf-8").read()
    h=hex_re.findall(txt)
    rgb=rgb_re.findall(txt)
    # 裸 oklch：所有 oklch( 出现处，凡前面紧跟 "in "（即 color-mix(in oklch,...)）的不算
    bare_oklch = len(re.findall(r'(?<!in )\boklch\(', txt))
    ok = (len(h)==0 and len(rgb)==0 and bare_oklch==0)
    all_ok &= ok
    print(f"  {'OK ' if ok else 'FAIL'}  {p}: hex={len(h)} rgb={len(rgb)} bare-oklch={bare_oklch}")
    if h: print("        hex 样例:", h[:3])

# ③ 无 emoji
print("\n[3] Emoji 扫描")
emoji_re = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F000-\U0001F0FF"
    "\U00002190-\U000021FF\U00002B00-\U00002BFF\U0000FE00-\U0000FE0F\U0001F1E6-\U0001F1FF]"
)
for p in files_to_scan:
    txt=open(os.path.join(D,p),encoding="utf-8").read()
    found=emoji_re.findall(txt)
    ok=len(found)==0
    all_ok &= ok
    print(f"  {'OK ' if ok else 'FAIL'}  {p}: emoji={len(found)}" + (f"  {found[:5]}" if found else ""))

# ④ JS 语法（node --check）
print("\n[4] 内联 JS + shell 语法 (node --check)")
import tempfile
def node_check(code, tag):
    with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False, encoding="utf-8") as f:
        f.write(code); fn=f.name
    r=subprocess.run(["node","--check",fn],capture_output=True,text=True)
    os.unlink(fn)
    ok=r.returncode==0
    print(f"  {'OK ' if ok else 'FAIL'}  {tag}")
    if not ok: print("       ", r.stderr.strip().splitlines()[0] if r.stderr else "")
    return ok
# shell
all_ok &= node_check(open(shp,encoding="utf-8").read(), "console-shell.js")
for p in PAGES:
    txt=open(os.path.join(D,p),encoding="utf-8").read()
    for i,blk in enumerate(extract_inline_js(txt)):
        if blk.strip():
            all_ok &= node_check(blk, f"{p} inline#{i+1}")

# ⑤ dashboard 真图表
print("\n[5] dashboard 真图表检测")
dtxt=open(os.path.join(D,"dashboard.html"),encoding="utf-8").read()
has_svg = "<svg" in dtxt
has_poly = "polyline" in dtxt
has_path = re.search(r"['\"]<path", dtxt) or "<path" in dtxt
has_circle = "circle" in dtxt
chart_tokens = "--chart-" in dtxt
chart_ok = has_svg and (has_poly or has_path) and chart_tokens
all_ok &= chart_ok
print(f"  {'OK ' if chart_ok else 'FAIL'}  <svg>={has_svg} polyline={has_poly} path={bool(has_path)} circle={has_circle} chart-token={chart_tokens}")

print("\n"+"="*64)
print("总判定:", "全部通过 ✓ PASS" if all_ok else "存在失败项 ✗ FAIL")
print("="*64)
sys.exit(0 if all_ok else 1)
