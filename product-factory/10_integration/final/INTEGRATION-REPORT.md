# S10 前后端联调报告 — Nexa AI 平台

**联调日期:** 2026-06-21
**后端:** Spring Boot 3.2.5 / Java 21，8080，连 PostgreSQL（连接信息由环境变量注入），ddl-auto:validate
**前端:** Next.js 14 prod，0.0.0.0:3100，API_BASE 指向后端（局域网/域名地址，环境相关），mock 全切真后端
**验证方式:** 主控亲验 — curl 协议层一条龙 + cloakbrowser CDP(127.0.0.1:9222) 三层取证(截图/console/network)。不信 subagent 自报。

## 一、联调结论

**全部核心业务链路端到端联调通过。** 17 个核心端点 root 实测全 200，前端各页真渲染 PG 真实数据，console 0 红错，mock 全切干净。

## 二、链路覆盖

### P0 核心鉴权链路 ✅
注册 → 登录(200 + Set-Cookie session JWT) → GET /api/user/self(带 cookie 200) → dashboard 渲染真实 KPI(0/$0.00 新账号真值)。curl 一条龙 + 浏览器三层取证双验。

### P1 console 用户端 (11 页全通) ✅
| 页面 | 接口 | 状态 |
|---|---|---|
| dashboard | user/self + log/self/stat | ✅ KPI 真数据 |
| keys | token/ | ✅ |
| usage | log/self | ✅ RPM/TPM 真值 |
| billing | user/self + log/self/stat | ✅ |
| recharge | (静态充值选项) | ✅ |
| checkin | user/checkin | ✅ 修复后通 |
| referral | user/self/aff | ✅ |
| settings | user/self | ✅ |
| model-map | self/model_aliases(+candidates) | ✅ |
| tasks | task/self | ✅ |
| pricing(公开) | /api/pricing | ✅ 修复后通，零泄露 |

### P2 admin 管理端 (核心接口 + 3 个 mock 页切真) ✅
- 接口全通：user 列表 / channel / models / vendors / redemption / option / log / profit/dashboard / status
- 普通用户访问 admin 端点全 403(鉴权未削弱，回归验证过)
- /admin/users：mock(morgan.li/chen.wei 假数据)→ 真接口 GET /api/user，渲染 PG 真实 7 用户 ✅
- /admin/logs：mock → GET /api/log/，真空态 ✅
- /admin/ops：mock → GET /api/status，渲染真实系统能力状态 1/13 ✅

## 三、契约偏差归位记录(本次联调修复，全部后端归属，前端按契约写得准)

| # | 现象 | 根因 | 归属 | 修复 |
|---|---|---|---|---|
| 1 | 跨域请求被浏览器拦 | CORS 未配 | 后端 | CorsProperties + SecurityConfig 白名单含两 origin + OPTIONS permitAll |
| 2 | 登录 200 但 self 全 403 | 登录不下发会话凭据 | 后端 | login 加 Set-Cookie session JWT(HttpOnly/SameSite=Lax) |
| 3 | GET /api/user/self 403 | handler 缺实现 | 后端 | 新建 GetSelfUserUseCase + handler |
| 4 | GET /api/pricing 403(公开页) | 白名单漏放行 + handler 缺实现 | 后端 | 补 9 个公开端点白名单 + 实现 PricingController(零泄露 PublicView) |
| 5 | GET /api/user/checkin 403 | admin 路径门槛误伤 USER 子路径 | 后端 | SecurityConfig 精确化，USER self-scope 子路径前置放行 |
| 6 | 签到禁用透传 403 非 400 | growth 子域缺异常处理器 | 后端 | 新建 GrowthExceptionHandler(@RestControllerAdvice) |
| 7 | admin 3 端点 root 403 | user列表/redemption/profit handler 缺实现 + matcher | 后端 | 补 AdminUserController/ProfitController 等 + SecurityConfig |
| 8 | admin 3 页假数据 | S9 写死 demo mock 没切真接口 | 前端 | UsersAdmin/LogsAudit/OpsMonitor 切真接口 hook + 重 build |

**统计：后端 7 类、前端 1 类。** 前端按 openapi 契约对接准确，问题集中在后端契约覆盖缺口(多个 F-xxx handler 漏实现)和 Spring Security 配置(CORS/会话/路径门槛)。无"只改一端凑通"，无回 S7 重冻结(契约本身无错)。

## 四、客户端零泄露真链路复验
- /api/pricing(公开)：Network 响应体仅 model_name/base_price_ratio/quality_tier/group_ratio，无 cost/profit/上游B/供应商。带真数据验过 enabled 过滤。
- /api/user/self、admin user 列表：admin 视图字段(quota/used_quota/request_count)，无上游供应商/成本泄露。
- 抓后端真返回 JSON 断言，非仅看前端渲染。

## 五、残留非阻塞项
- dashboard 下半部趋势图/模型分布/延迟分布仍是前端静态 demo 数据(契约暂无 series/trend 端点，dashboard.model.ts 已注释标明，待后续 wave 补后端 series 端点)。
- 测试数据：临时账号 adminqa(role=100)、qa10330(common) 在 PG；checkin_settings.enabled=true(联调用)。

## 六、出口门
| 检查项 | 结果 |
|---|---|
| mock 切干净 | ✅ admin 3 页已切，全局指真后端 |
| 每条链路端到端通 | ✅ console 11 + admin 核心 |
| F12 三层取证 | ✅ 截图+console+network |
| Console 无红错 | ✅ 各页 0 error |
| Network status 正确 | ✅ 17 端点全 200 |
| 零泄露真链路复验 | ✅ Network 响应体断言 |
| 契约偏差归位 | ✅ 8 类全归位，无单边凑通 |
| 独立验收 | ✅ 主控亲自 curl + CDP |
