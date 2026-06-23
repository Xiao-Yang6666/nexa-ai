# S12 Round 3 复盘与 Round 4 Scope 决策

> 生成时间：2026-06-23（Round 3 收口）
> 基线：main = 8353481（含 R1+R2+R3-01）
> 验证库：nexa_r3test（独立，未受 prod 库 V28 漂移影响）

## Round 3 交付回顾（已闭环）

### R3-01 系统配置面板接通 ✅
- 18 个键名契约 + OptionRegistry 领域校验 + 前端 GET/PUT 接线
- 后端 532 测试绿（+18），BUILD SUCCESS
- 前端 build 过，api-missing 从 8 → 2（剩 2 处为"缓存清理/重置统计"操作类，非配置键）
- **亲验真实 HTTP 5 场景全过**：
  - 非法布尔 → 400 + 中文"必须为 true 或 false"
  - 合法 CNY → 200 "更新成功"（DB 确认落库）
  - 非法货币 JPY → 400 + 中文"可选值：CNY、USD"
  - 端口越界 99999 → 400
  - SMTP 密码明文剔除（DB 存真值，GET 列表不回显）
- 已合 iter/round3 → main(8353481) → 推 GitHub 远端

## Round 3 重大发现（诊断副产品）

### D1：prod 库 schema 漂移（已决策搁置）
- sub2 上 `nexa-backend-prod`(8091) 已跑 41 小时，连库 172.17.0.1:5434/nexa
- prod 库 flyway 最高 = **V28（drop platform_model_mappings）**
- **V28 从未进过任何 git 分支**（所有 19 个本地/远端分支 V28 文件数=0）
- 本地 main 代码 = V27，且 `platform_model_mapping` 实体完整存在（model+relay 两套域 + V12 建表）
- **用户决策：以本地版本为主，prod 漂移不管**
- 影响：若直接把本地 main 部署到 prod 库会 schema-validation 失败（缺表）。部署时需让库与本地代码 V12 状态对齐（重建表 or 用干净库）。**Round 4 部署门要处理。**

### D2：前端 mock 不阻塞上线（已核验）
- mock 是开关式（`NEXT_PUBLIC_USE_MOCK`，默认 0=关），仅开发期拦截 auth/pricing/console
- 生产构建 mock 不安装，client.ts 直打真后端（`NEXT_PUBLIC_API_BASE`）
- **核验：mock 拦截的 14 个端点后端全部有实现**，关 mock 上生产不会 404 ✅

## Round 4 候选池

| ID | 候选 | 业务价值 | 成本 | 风险 | 依赖 | 优先级 |
|----|------|---------|------|------|------|--------|
| R4-A | **部署门：本地 main 真实部署验证**（库对齐 V12 + 起服务 + 真实链路冒烟） | 高（决定能否上线） | 中 | 中（碰 prod 库需谨慎） | 用户拍板部署策略 | **P0** |
| R4-B | 前端剩余 2 处操作端点（缓存清理/重置统计） | 中 | 低 | 低 | 后端加 2 个操作端点 | P1 |
| R4-C | S12-001/002 工程治理（nexa-common 边界文档 + Maven Enforcer 禁反向依赖） | 中 | 低-中 | 低 | 无 | P1 |
| R4-D | 候选2 合规驻地源接线（渠道表加 residency 字段） | 中 | 中 | 中 | **用户拍板运营怎么标境内外** | P2（阻塞中） |
| R4-E | 真邮件 SMTP 生产化 | 中 | 低 | 低 | **用户提供 SMTP 凭证** | P2（阻塞中） |
| R4-F | WebAuthn/Passkey 生产化 | 低 | 高 | 中 | 无 | P3 |

## 推荐 Round 4 Scope

**核心矛盾**：用户目标是"明天能上线"，而最大的未验证风险是 **D1 部署门**——本地 main 从未真实部署验证过（一直在测试库/独立库验单元功能，没验过真实部署链路）。

**推荐 Round 4 = R4-A（部署门）为主线 + R4-B/R4-C 顺带清扫**：
1. **R4-A（P0）**：在独立干净库上做一次完整部署演练——本地 main 构建 → Flyway 从头建表（V1-V27 全过，platform_model_mappings 存在）→ 起服务 → 前端关 mock 指向真后端 → 真实浏览器链路冒烟（登录/注册/配置/中继）。这一步过了，才能说"明天能上线"有底。
2. **R4-B（P1）**：补 2 个操作端点，前端 api-missing 清零。
3. **R4-C（P1）**：工程治理（低成本，提升可维护性）。

**阻塞项（R4-D/E）继续等用户拍板**，不进 Round 4 主线。

## 待用户决策点

1. **部署策略**：R4-A 部署演练用「独立干净库」还是「把 prod 库的 platform_model_mappings 表重建对齐本地代码」？（前者零风险，后者动 prod 库需谨慎）
2. **R4-D 驻地源**：运营侧怎么标渠道境内外？（不定无法接线）
3. **R4-E 真邮件**：是否提供 SMTP 凭证？
