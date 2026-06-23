# S12 复盘 Round6 + Round7 规划（夜间迭代 01:15）

## Round6 成果（已亲验，真 PG 真跑）
- Slice A 供应商账号化（account.provider 域，抄 sub2api accounts）— V29 全新库 Flyway 干净跑
- Slice B 模型映射双实体合一 — 跨域闭环 IT 真 PG 2 绿，"超管写=转发读"契约成立
- Slice C 模型广场连通 — 修 visibleModels 读错表（Channel→PublicModel）双源 bug，闭环 5 绿
- 合并体 567 测试全绿，已合入 iter/round6
- Redis timeout 可配置化（REDIS_TIMEOUT，生产默认 2s 不变）

## Round6 E2E 真测结论（重大）
**用真实 ningmeng key 真调上游，第一次真正端到端验证。前 4 步全真通过：**
超管登录 ✅ → 建供应商账号 ✅（credentials 不回显、鉴权 403 都正确）→ 配模型映射 ✅ → 建 APIKey ✅
**relay 转发认证、两层映射、选 channel、转发 ningmeng 都通了**（msg_xxx 真实返回）。

**但暴露致命 P0（推翻"可上线"判断）：**
relay 转发后 **content[] 空 + usage 全 0** — 用户拿不到回复 + 计费扣不了费。
（对照：上游直调 content/usage 正常；经 relay 丢失）

## Round7 Scope（按优先级）

### P0-1：relay 转发 content/usage 丢失 【修复中 r7-relay-resp】
- 根因判断：请求方向 serializeRequest（usage.input_tokens=0 暗示上游收到空请求体）
- CC 正在修

### P0-2：account 域接通 channel 选择逻辑 【relay修完后派】
- 现状：account.provider 域建了（凭证/并发/优先级），但 relay 选 channel 只认旧 channel 表，不认 account
- 这是重构方案"阶段3：转发切新结构"——account 要能作为 relay 的供应商来源
- 风险：架构改动，不与 relay 协议修复并行（避免交叉）

### P1-3：倍率(rate_multiplier)真实生效 【与P0-2同批】
- 用户明确要"配置账号里的模型和倍率"
- account 有 rate_multiplier 字段，但计费链路是否真用它算价，需验证+接通

### P1-4：计费口径对齐 【Round4遗留P0】
- DualPriceBilling vs BillingCalculator 差 5 个数量级，需统一
- E2E 验证"钱真扣"时一并校准

## 夜间迭代节奏
1. relay 修复（单独，最硬P0）→ 亲验真调用拿到内容+计费非0 → 合 round7
2. 派 account接通+倍率 slice → 亲验 → 合
3. 计费口径对齐 → E2E 全链路"钱真扣"验证
4. 每轮 S12 复盘出下一轮，跑到早 8 点
5. 红线：不自动合 main（留用户拍板）、不推生产、上游只用 haiku 省钱

## 给用户的待拍板项（明早）
- round6 + round7 是否合 main
- 计费口径以哪个为准（DualPriceBilling vs BillingCalculator）
- 生产部署时机
