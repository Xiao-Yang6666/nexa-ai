#!/usr/bin/env python3
"""验证 login->dashboard 后半链路：用已落库的短用户名账号真登录走会话+dashboard接口。"""
import json, time
from playwright.sync_api import sync_playwright

EV = "/root/.hermes/projects/routifyapi-newapi-product/10_integration/evidence"
SHOTS = f"{EV}/screenshots"; CONS = f"{EV}/CONSOLE-LOGS"; NET = f"{EV}/network"
FRONT = "http://172.17.0.1:3100"
USER = "qa104523"; PWD = "QaTest2026xyz"

console_msgs = []; net_events = {}; req_meta = {}

def main():
    pw = sync_playwright().start()
    browser = pw.chromium.connect_over_cdp("http://127.0.0.1:9222")
    ctx = browser.contexts[0] if browser.contexts else browser.new_context()
    page = ctx.new_page()
    page.on("console", lambda m: console_msgs.append({"type": m.type, "text": m.text}))
    page.on("pageerror", lambda e: console_msgs.append({"type": "pageerror", "text": str(e)}))
    client = ctx.new_cdp_session(page); client.send("Network.enable")
    client.on("Network.responseReceived", lambda e: net_events.__setitem__(e["requestId"], {"url": e["response"]["url"], "status": e["response"]["status"]}))
    client.on("Network.requestWillBeSent", lambda e: req_meta.__setitem__(e["requestId"], {"method": e["request"]["method"], "postData": e["request"].get("postData","")}))

    def snap(n): page.screenshot(path=f"{SHOTS}/{n}_v2.png"); print(f"[SHOT] {n}")
    def bodies(sub):
        out=[]
        for rid,m in net_events.items():
            if sub in m["url"]:
                b=""
                try: b=client.send("Network.getResponseBody",{"requestId":rid}).get("body","")
                except Exception as ex: b=f"<no-body:{ex}>"
                r=req_meta.get(rid,{})
                out.append({"url":m["url"],"status":m["status"],"reqMethod":r.get("method",""),"reqBody":r.get("postData",""),"respBody":b})
        return out

    log={"user":USER,"steps":{}}

    print("\n===== LOGIN (valid short user) =====")
    net_events.clear(); req_meta.clear(); console_msgs.clear()
    page.goto(f"{FRONT}/login", wait_until="networkidle"); time.sleep(1.2)
    page.fill("#account", USER); page.fill("#password", PWD); snap("c2b_login_filled_validuser")
    net_events.clear(); req_meta.clear()
    page.click("button[type=submit]"); time.sleep(3.5); snap("c3b_login_submitted_validuser")
    lb=bodies("/api/user/login")
    log["steps"]["login"]={"console":list(console_msgs),"network":lb,"url_after":page.url}
    print("LOGIN net:",json.dumps(lb,ensure_ascii=False)[:1500]); print("URL after:",page.url)
    print("LOGIN errs:",[c for c in console_msgs if c["type"] in ("error","pageerror")])

    print("\n===== DASHBOARD =====")
    net_events.clear(); req_meta.clear(); console_msgs.clear()
    page.goto(f"{FRONT}/dashboard", wait_until="networkidle"); time.sleep(3.5); snap("d1b_dashboard_validuser")
    db=bodies("/api/")
    log["steps"]["dashboard"]={"console":list(console_msgs),"network":db,"url_after":page.url}
    print("DASH net:",json.dumps(db,ensure_ascii=False)[:2500]); print("URL:",page.url)
    print("DASH errs:",[c for c in console_msgs if c["type"] in ("error","pageerror")])

    with open(f"{NET}/auth_chain_v2_validuser_network.json","w") as f: json.dump(log,f,ensure_ascii=False,indent=2)
    print(f"[DUMP] {NET}/auth_chain_v2_validuser_network.json")
    page.close()

if __name__=="__main__": main()
