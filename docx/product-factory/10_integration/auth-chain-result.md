# 鉴权链路联调结论：登录 → 主页面(dashboard) → 主页面接口取数

> S10 frontend-engineer 收尾本核心鉴权链路。三层取证（截图+console+network）+ curl 协议层隔离判归属。
> 最终复验（_v4）：CORS + session cookie 两个后端阻塞已修后的端到端真链路复验。
> 证据目录：`10_integration/evidence/`（screenshots/*_v4.png、CONSOLE-LOGS/auth-chain_v4.log、network/auth-chain_v4.json）。
> 验证时间：2026-06-21（_v4 复验）。账号 qa10330（id=5，已注册真落库）。

---

## 一、链路最终结论：**会话闭环已打通，dashboard 仍未完整渲染——剩下唯一断点是后端漏实现 `GET /api/user/self`，归 backend-engineer**

| 环节 | _v3 状态 | _v4 最终状态 | status |
|---|---|---|---|
| 登录 `POST /api/user/login` | ✅ 200 但不下发凭据 | ✅ **200 且下发 Set-Cookie** | 200 |
| 登录下发会话凭据 | ❌ 无 Set-Cookie | ✅ **`Set-Cookie: session=<JWT>; Path=/; HttpOnly; SameSite=Lax`** | — |
| 登录后跳转 /dashboard | ✅ 已修 | ✅ 自动跳 `/dashboard`，路由 200 | — |
| 后续请求带 session cookie | ❌ 无凭据可带 | ✅ **浏览器持有 session cookie 且自动带回** | — |
| `GET /api/log/self/stat` | ❌ 403 | ✅ **200**（`{quota:0,rpm:0,tpm:0}`） | **200（修好）** |
| `GET /api/user/self` | ❌ 403 | ❌ **仍 403**（后端未实现该 GET 端点） | **403（后端漏实现）** |
| dashboard KPI 卡渲染真数据 | ❌ 全 `…` | ❌ **仍全 `…`**（useKpi 的 Promise.all 因 self 403 整体失败） | — |

**一句话：CORS + session 两个后端阻塞确已修好——登录现在真下发 session cookie、浏览器真带回、cookie-protected 的 `log/self/stat` 从 403→200，会话闭环打通。但 dashboard KPI 卡仍渲染不出，唯一原因是后端漏实现 `GET /api/user/self`（契约 F-1045 定义了，后端那条路径只挂了 DELETE）——前端 `useKpi` 用 `Promise.all([getSelfAccount(), getSpendStat()])` 聚合，self 这条 403 reject 把整个 Promise 拖垮，4 张 KPI 卡全留 `…`。这是后端缺实现，前端按契约接法完全正确，无可修之处。**

---

## 二、登录 cookie 带上没（核心复验点）

✅ **带上了。** 三重证据：
1. **协议层 curl 实证**：`POST /api/user/login` 响应头含 `Set-Cookie: session=eyJhbG...; Path=/; HttpOnly; SameSite=Lax`，cookie jar 真写入 session。
2. **浏览器持有**：CDP `context.cookies()` 实查到 `name=session domain=172.17.0.1 path=/ httpOnly=True sameSite=Lax value_len=186`（真 JWT）。
3. **后续请求真带回**：`GET /api/log/self/stat`（contract 标 `security: sessionAuth`，必须带 cookie 才放行）返回 **200**——若 cookie 没带回必 403。200 本身就是 cookie 透传成功的铁证。

> 注：network/auth-chain_v4.json 里 `req_has_session_cookie:false` 是 **CDP 抓包伪影**——Cookie 请求头由浏览器在 `requestWillBeSentExtraInfo` 事件补发，不在主 `requestWillBeSent` 事件里，所以脚本读主事件头读不到。以 `log/self/stat` 真 200 + CDP cookies 实查为准，cookie 确实带上了。

---

## 三、dashboard 各接口最终 status（network/auth-chain_v4.json，真抓包）

| method | url | _v4 status | resp body | 判定 |
|---|---|---|---|---|
| POST | /api/user/login | **200** | `{success:true,data:{id:5,username:qa10330,role:1,status:1,quota:0,aff_code:PVET,last_login_at}}` | ✅ 通 |
| GET | /api/log/self/stat?start_timestamp=… | **200** | `{success:true,data:{quota:0,rpm:0.0,tpm:0.0}}` | ✅ **修好（_v3 是 403）** |
| GET | /api/user/self | **403** | （空 body，响应头 `Allow: DELETE`） | ❌ **后端漏实现 GET** |

补充 curl 验证其它 self-scope 端点（带 cookie 全 200，进一步坐实会话闭环）：
- `GET /api/user/self/aff` → **200** `{data:"PVET"}`
- `GET /api/user/self/aff_stats` → **200** `{data:{aff_count:0,...}}`

**`/api/user/self` 的 403 真相（精确定位）**：带 cookie GET 该路径仍 403，且响应头回 `Allow: DELETE`——说明后端在 `/api/user/self` 这条路径**只实现了 DELETE 映射，没有 GET handler**。GET/POST/PUT 都 403（不带 cookie 也 403），是 Spring Security 路径级门把未实现的方法挡在 handler 之前，表现为 403 而非标准 405。**本质 = 契约 F-1045 定义的 `GET /api/user/self`（本人信息）后端没实现。** 这正是 context 里后端角色预报的已知缺口。

---

## 四、KPI 卡真渲染了没（vision 确认）

❌ **没有。4 张 KPI 卡（本月调用量/本月消费/当前余额/累计请求）全是 `…` 占位符。** vision 截图分析确认卡片框架/布局/标签都正常，但 4 个数值位全是占位符（数据未加载/加载失败态）。

**根因精确定位（前端代码已查）**：
- `features/dashboard/model/dashboard.model.ts:34`：`const [self, stat] = await Promise.all([getSelfAccount(), getSpendStat(start)]);`
- `getSelfAccount()` → `http.get('/api/user/self')`（403），`getSpendStat()` → `http.get('/api/log/self/stat')`（200）。
- **`Promise.all` 语义：任一 reject 整个 reject。** self 这条 403 抛错 → 整个 `useKpi` query 失败 → 4 张卡全留 `…`（balanceUsd 来自 self.quota、monthSpend/monthCalls 来自 stat，但 Promise.all 失败后哪个都拿不到）。
- 即：**只要后端补上 `GET /api/user/self`，self 返 200，Promise.all 成功，4 张 KPI 卡立即渲染真数据。** 这是唯一阻塞 dashboard 完整渲染的点。

> 旁支：右上角用户名仍显示 `morgan.li`（CDP 浏览器旧会话残留态，疑 localStorage，clear_cookies 清不掉；不影响接口判定，属验证环境卫生遗留）。下方趋势图/模型分布/最近调用记录是前端写死的静态演示数据（dashboard.model 注释明示"契约暂无聚合 series 端点，暂用客户端静态演示数据"），与接口无关。

---

## 五、console 还有无红错

⚠️ **有 2 条红错，且都是 `/api/user/self` 的 403**（CONSOLE-LOGS/auth-chain_v4.log）：
```
[error] Failed to load resource: the server responded with a status of 403 ()
[error] Failed to load resource: the server responded with a status of 403 ()
```
对应 dashboard 两次调 `/api/user/self` 各一条。**_v3 是 4 条红错（self + log-stat 各两次），_v4 降到 2 条**——log/self/stat 修好不再报错，红错收敛到只剩 self。这 2 条红错随后端补 `GET /api/user/self` 即清零。

---

## 六、零泄露真链路复验结论：✅ 干净

抓 **真后端 Network 响应体**（非前端渲染层），扫敏感字段（cost/profit/upstream/provider/vendor/margin/供应商/成本/利润）：
- 登录响应体：`{id,username,role,status,quota,aff_code,last_login_at}` — 无敏感字段。
- `log/self/stat` 真 200 响应体：`{quota:0,rpm:0.0,tpm:0.0}` — **无 cost/profit/上游模型B/供应商**。
- `self/aff`、`self/aff_stats` 真 200 响应体：仅邀请码/邀请统计，无敏感字段。
- `/api/user/self` 因 403 无响应体，其本应返回的 UserView 在契约里已裁剪（无敏感字段），待后端补实现后补一轮真 200 响应体复验即可闭环。

**扫描结果：✅ 无敏感字段泄露。** 比 _v3 更进一步——这次 log/self/stat 真 200 了，验到了它的真实后端响应体，零泄露覆盖面扩大。

---

## 七、这条链路最终通没通

**部分通——会话链路（登录→cookie→鉴权接口取数）已端到端打通；dashboard 完整渲染未通，卡在后端漏实现一个接口。**

- ✅ **已通**：登录 200 + 下发 session cookie + 浏览器带回 + cookie-protected 接口（log/self/stat、self/aff、self/aff_stats）真 200。CORS + session 两个后端阻塞确认修复生效。
- ❌ **未通**：dashboard KPI 卡未渲染真数据，因 `GET /api/user/self`（契约 F-1045）后端漏实现返 403，拖垮 useKpi 的 Promise.all。

按 S10 铁律「dashboard 真渲染数据 = 通」，**这条链路尚未完全通**——但断点已精确收敛到单一后端缺口，非前端、非 CORS、非会话。

---

## 八、剩余问题逐个判归属

| # | 问题 | 归属 | 怎么修 |
|---|---|---|---|
| 1 | **【唯一阻塞 dashboard 渲染】后端漏实现 `GET /api/user/self`**：契约 F-1045 定义了该端点（本人信息 UserView），后端那条路径只挂了 DELETE，GET 返 403（响应头 `Allow: DELETE` 坐实）→ 前端 useKpi 的 `Promise.all` 因这条 reject 整体失败 → 4 张 KPI 卡全 `…` | **backend-engineer** | 在后端 UserController 实现 `GET /api/user/self`，返回 `ApiResponse{data: UserView}`（本人 quota/used_quota/request_count 等，对齐契约 UserView，零泄露不含 cost/profit/上游）。挂 `sessionAuth` 安全。修完前端无需改即渲染。 |
| 2 | ~~CORS 跨端口被拦~~ | ~~backend~~ | ✅ **已修**：响应头 `Access-Control-Allow-Origin: http://172.17.0.1:3100` + `Allow-Credentials: true`。 |
| 3 | ~~登录不下发会话凭据~~ | ~~backend~~ | ✅ **已修**：登录现下发 `Set-Cookie: session=<JWT>; HttpOnly; SameSite=Lax`，会话闭环打通。 |
| 4 | self 真 200 后的零泄露复验未覆盖该接口响应体 | backend 修完后 frontend-engineer 复跑 | #1 修完重跑 _v4，抓 self 真 200 响应体断言无敏感字段。 |
| 5 | dashboard 趋势图/模型分布/最近调用记录是前端写死静态演示数据 | frontend-engineer（次要，契约暂无对应 series 端点，dashboard.model 注释已声明） | 后续 wave，待契约补聚合端点后切真实。**非本链路阻塞。** |
| 6 | CDP 浏览器残留旧会话态（用户名 morgan.li，疑 localStorage） | 验证环境卫生 | 复验前清浏览器 storage（clear_cookies 清不掉 localStorage），避免幽灵态。**不影响接口判定。** |

**前端侧结论：无可修——前端按契约正确调 `GET /api/user/self`（F-1045）、credentials:'include' 带 cookie、登录跳 /dashboard、Promise.all 聚合 self+stat。全部正确。dashboard 渲染不出纯因后端漏实现该接口。**

---

## 九、阻塞 dashboard 完整渲染的后端缺口（明确）

- **哪个接口**：`GET /api/user/self`（契约 openapi F-1045，本人信息）。
- **现状**：返 403（响应头 `Allow: DELETE`，该路径仅实现 DELETE）。
- **后端该补什么**：实现 `GET /api/user/self` handler，返 `ApiResponse{data: UserView}`（本人 quota/used_quota/request_count 等本人维度字段，零泄露），挂 `sessionAuth` 安全。
- **补完效果**：self 返 200 → 前端 useKpi 的 Promise.all 成功 → 4 张 KPI 卡（本月调用量/本月消费/当前余额/累计请求）立即渲染真数据 → 这条链路端到端真通。**前端零改动。**

---

## 证据存放（_v4）
- 截图：`10_integration/evidence/screenshots/04..07_*_v4.png`（含 07_dashboard_v4.png，vision 已分析）
- 控制台：`10_integration/evidence/CONSOLE-LOGS/auth-chain_v4.log`（2 条 self 403 红错 + dashboard body text）
- Network 真 JSON：`10_integration/evidence/network/auth-chain_v4.json`（login 200 / self 403 / log-stat 200 真响应体）
- 复跑脚本：`/tmp/auth_chain_v4.py`（登录已注册账号 qa10330 + 三层取证 + cookie 检查）
- cookie 实查脚本：`/tmp/check_cookie_v4.py`
