# S7 HANDOFF — 开发契约交接 S8/S9

> S7（开发契约设计）已完成并通过覆盖校验 + 独立评审。本文件交接给 S8（后端）/ S9（前端）。

## 契约位置（07_dev_contract/final/）

| 产物 | 用途 | 给谁 |
|---|---|---|
| `DB-SCHEMA.md` | 22 张表 GORM model + 索引/约束 | S8 后端建表 |
| `MIGRATION-PLAN.md` | AutoMigrate 顺序 + 加列迁移 + 缓存 Init | S8 后端 |
| `openapi.yaml` | OpenAPI 3.0.3，133 paths / 159 ops / 84 schemas | **S8 照建 + S9 mock**（前后端解耦点） |
| `API-ENDPOINTS.md` | 16 模块人读契约（含横切/系统内部登记） | S8/S9 对照阅读 |
| `BACKLOG.csv` | 243 task（=243 功能 1:1），含 wave/tier/依赖 | S8/S9 派活 |
| `WAVE-PLAN.md` | 5 波依赖 + 前后端并行策略 | 排期 |
| `API-COVERAGE.csv` | 243 功能 × 端点 × task 覆盖账 | 防漏审计 |

## 质量门状态

- **覆盖校验 miss=0**（脚本实算）：243 功能全部有 API 登记 + 全部有 task；无孤儿端点。
- REST 端点功能 109 个进 openapi.yaml；其余 134 个为横切/系统内部/交叉引用功能（NFR/RBAC/计费登记/选渠调度等），由中间件/基础设施实现，在 API-ENDPOINTS.md 登记——属正常，非漏。
- **客户视图零泄露**（铁律）：客户/公开视图 schema 不含 quota_cost/quota_profit/actual_upstream_model(B)/供应商；A→B 映射、成本配置仅 AdminView，profit 端点强制 adminAuth。
- 独立 reviewer：**PASS_WITH_GAPS**，三类硬伤（漏功能/契约含糊/客户视图泄露）均无；2 个 gap 已修复（openapi 管理端 CRUD 补全 91→109、BACKLOG 依赖回填）。

## 并行策略

**API 契约（openapi.yaml）= 前后端唯一握手面，已冻结。**
- S8 后端：照 `DB-SCHEMA.md` + `MIGRATION-PLAN.md` 建表，照 `openapi.yaml` 实现端点，按 WAVE-PLAN 5 波推进（W1 基础鉴权→W2 数据层→W3 转发→W4 增长→W5 运维）。
- S9 前端：照 `openapi.yaml`（prism 起 mock）+ S5/S6 设计原型实现页面，不等后端真实现。

## 冻结声明

openapi.yaml 一旦冻结，S8/S9 **不准私改契约**。如需改 → 回 S7 重冻结 + 重跑覆盖校验。

## 已知 gap（移交 S8 处理，不阻塞）

1. DB-SCHEMA 标注 5 个 DATA-MODEL 表述不全的点（Channel 软删除字段、SubscriptionPlan 支付商品号字段独立 tag、SubscriptionOrder 枚举/索引、UserSubscription 字段 tag、UserModelAlias 缓存 Init 命名）——建表时回查 DATA-MODEL 补全，必要时先跑 change-backpropagation。
2. F-5020 账号注销端点路径待 repo 实际承载确认。
3. BACKLOG depends_on 为波次级依赖（177/243 行）；task 级细粒度依赖在 WAVE-PLAN.md 散文中完整，S8 派活时细化。
