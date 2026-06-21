#!/usr/bin/env python3
"""
S10 联调复验 v2：注册→登录→dashboard 核心鉴权链路端到端三层取证。
连 cloakbrowser CDP 127.0.0.1:9222，容器内访问宿主前端走 172.17.0.1:3100。
每步：① 截图(vision) ② console 抓取 ③ Network 抓 status+req+resp 真 JSON。
"""
import json, time, sys, os
from datetime import datetime
from playwright.sync_api import sync_playwright

EV = "/root/.hermes/projects/routifyapi-newapi-product/10_integration/evidence"
SHOTS = f"{EV}/screenshots"
CONS = f"{EV}/CONSOLE-LOGS"
NET = f"{EV}/network"
FRONT = "http://172.17.0.1:3100"

# 唯一账号
TS = datetime.now().strftime("%m%d%H%M%S")
USERNAME = f"qa_v2_{TS}@nexa-test.local"
PASSWORD = "QaTest2026!xyz"   # >=8 位，含字母数字符号

console_msgs = []      # 所有 console 消息
net_events = {}        # requestId -> {url, method, status, type}
net_bodies = {}        # requestId -> response body text

def main():
    pw = sync_playwright().start()
    browser = pw.chromium.connect_over_cdp("http://127.0.0.1:9222")
    ctx = browser.contexts[0] if browser.contexts else browser.new_context()
    page = ctx.new_page()

    # console 抓取
    page.on("console", lambda m: console_msgs.append({
        "type": m.type, "text": m.text, "location": str(m.location)
    }))
    page.on("pageerror", lambda e: console_msgs.append({
        "type": "pageerror", "text": str(e), "location": ""
    }))

    # CDP Network 抓包
    client = ctx.new_cdp_session(page)
    client.send("Network.enable")

    def on_resp(e):
        rid = e["requestId"]
        r = e["response"]
        net_events[rid] = {
            "url": r["url"], "status": r["status"],
            "method": e.get("type", ""),
            "mimeType": r.get("mimeType", ""),
        }
    client.on("Network.responseReceived", on_resp)

    req_meta = {}
    def on_req(e):
        rid = e["requestId"]
        req = e["request"]
        req_meta[rid] = {
            "url": req["url"], "method": req["method"],
            "postData": req.get("postData", ""),
        }
    client.on("Network.requestWillBeSent", on_req)

    def snap(name, q=""):
        path = f"{SHOTS}/{name}_v2.png"
        try:
            page.screenshot(path=path, full_page=False)
            print(f"[SHOT] {path}")
        except Exception as ex:
            print(f"[SHOT-ERR] {name}: {ex}")
        return path

    def grab_bodies(filter_sub="/api/"):
        """取目标接口 response body 真 JSON"""
        out = []
        for rid, meta in net_events.items():
            if filter_sub in meta["url"]:
                body = ""
                try:
                    res = client.send("Network.getResponseBody", {"requestId": rid})
                    body = res.get("body", "")
                except Exception as ex:
                    body = f"<no-body: {ex}>"
                req = req_meta.get(rid, {})
                out.append({
                    "url": meta["url"], "status": meta["status"],
                    "reqMethod": req.get("method", ""),
                    "reqBody": req.get("postData", ""),
                    "respBody": body,
                })
        return out

    log = {"username": USERNAME, "steps": {}}

    # ---------- 步骤 a：注册 ----------
    print("\n===== STEP a: REGISTER =====")
    net_events.clear(); req_meta.clear(); console_msgs.clear()
    page.goto(f"{FRONT}/register", wait_until="networkidle", timeout=30000)
    time.sleep(1.5)
    snap("a1_register_open")
    # 填表
    page.fill("#email", USERNAME)
    page.fill("#password", PASSWORD)
    page.fill("#confirm", PASSWORD)
    # 勾选协议 checkbox（自定义样式，原生 input 视觉隐藏/离屏，用 JS 直接置 checked + 触发 change）
    page.evaluate("""() => {
        const cb = document.querySelector('input[type=checkbox]');
        if (cb && !cb.checked) {
            cb.checked = true;
            cb.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }""")
    time.sleep(0.3)
    snap("a2_register_filled")
    net_events.clear(); req_meta.clear()
    page.click("button[type=submit]")
    time.sleep(3.0)   # 等接口返回
    snap("a3_register_submitted")
    reg_bodies = grab_bodies("/api/user/register")
    log["steps"]["register"] = {
        "console": list(console_msgs),
        "network": reg_bodies,
        "url_after": page.url,
    }
    print("REGISTER network:", json.dumps(reg_bodies, ensure_ascii=False)[:800])
    print("REGISTER console errors:", [c for c in console_msgs if c["type"] in ("error","pageerror")])

    # ---------- 步骤 c：登录 ----------
    print("\n===== STEP c: LOGIN =====")
    net_events.clear(); req_meta.clear(); console_msgs.clear()
    page.goto(f"{FRONT}/login", wait_until="networkidle", timeout=30000)
    time.sleep(1.2)
    snap("c1_login_open")
    page.fill("#account", USERNAME)
    page.fill("#password", PASSWORD)
    snap("c2_login_filled")
    net_events.clear(); req_meta.clear()
    page.click("button[type=submit]")
    time.sleep(3.5)
    snap("c3_login_submitted")
    login_bodies = grab_bodies("/api/user/login")
    log["steps"]["login"] = {
        "console": list(console_msgs),
        "network": login_bodies,
        "url_after": page.url,
    }
    print("LOGIN network:", json.dumps(login_bodies, ensure_ascii=False)[:1200])
    print("LOGIN url after:", page.url)
    print("LOGIN console errors:", [c for c in console_msgs if c["type"] in ("error","pageerror")])

    # ---------- 步骤 d：进 dashboard ----------
    print("\n===== STEP d: DASHBOARD =====")
    net_events.clear(); req_meta.clear(); console_msgs.clear()
    page.goto(f"{FRONT}/dashboard", wait_until="networkidle", timeout=30000)
    time.sleep(3.5)
    snap("d1_dashboard")
    dash_bodies = grab_bodies("/api/")
    log["steps"]["dashboard"] = {
        "console": list(console_msgs),
        "network": dash_bodies,
        "url_after": page.url,
    }
    print("DASHBOARD network:", json.dumps(dash_bodies, ensure_ascii=False)[:2000])
    print("DASHBOARD url:", page.url)
    print("DASHBOARD console errors:", [c for c in console_msgs if c["type"] in ("error","pageerror")])

    # dump 所有证据
    with open(f"{NET}/auth_chain_v2_network.json", "w") as f:
        json.dump(log, f, ensure_ascii=False, indent=2)
    with open(f"{CONS}/auth_chain_v2_console.json", "w") as f:
        json.dump({k: v["console"] for k, v in log["steps"].items()}, f, ensure_ascii=False, indent=2)
    print(f"\n[DUMP] {NET}/auth_chain_v2_network.json")
    print(f"[DUMP] {CONS}/auth_chain_v2_console.json")
    print(f"\nUSERNAME={USERNAME}")
    page.close()

if __name__ == "__main__":
    main()
