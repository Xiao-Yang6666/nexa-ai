# T9 · CR-04 隐藏视频生成功能 — 调查结论

> 出文档：2026-06-22 夜（编排总控复核，基于 baseline d8f2bf0 前端实测）
> 结论一句话：**前端当前不存在任何"视频生成"页面/路由/菜单入口——CR-04 在 UI 层基本是空操作（无可隐藏对象）。**

## 1. 实测排查

在 `frontend/src/` 下全量 grep `video|视频|kling|vidu|doubao|jimeng`：
- **唯一命中**：`src/shared/api/schema.ts`（OpenAPI 自动生成的类型定义），出现 `/v1/videos/{task_id}/content` 端点类型 + 异步渠道枚举里提到 `DoubaoVideo/Vidu/Kling` 等。这是**后端端点的类型声明**，不是 UI 页面。
- 路由（App Router）实测目录：`(console)` 下有 dashboard/keys/billing/recharge/usage/tasks/model-map/referral/checkin/settings；`(admin)` 下有 channels/models/model-groups/users/logs/profit/billing-rules/redeem/ops/sys-settings/tasks-monitor。**没有任何 video / 视频生成 / 创作类页面或路由。**
- 菜单：`AdminShell.tsx` / `ConsoleShell.tsx` 两套静态菜单表中**无视频相关入口**。
- 异步任务页 `relay/components/TasksPage.tsx`：是通用异步任务列表（文案举例为"批量 Embedding / 长文档解析 / 批量推理"），按 `typeName` 通用渲染任务类型，**没有"创建视频任务"入口，也不专门展示视频**。

## 2. 与审计的对照

审计 `REQUIREMENT-LIST-v2.md` 第 181 行起：**REQ-11 视频内容代理 VideoProxy 是后端 MISSING 需求**（`RelayController.java:144-149` 返 501 TODO），归属 `relay` 后端模块，**从未有前端 UI 落地**。TONIGHT-PLAN.md 也已写明"后端 REQ-11 视频代理同步不进本轮生产范围"。

## 3. 结论与处置

CR-04 的意图是"视频生成相关页面/功能先隐藏"。但代码事实是：**这块功能前端从未实现**，没有页面、路由、菜单可隐藏。因此：

- **不新建 feature flag、不改 shell/菜单**——没有隐藏对象，硬加 flag 是为不存在的页面造代码，违反开发第一守则（反过度工程 / YAGNI）。
- **CR-04 当前自动达成**：任何角色都看不到视频生成入口（因为根本不存在），直访视频路由也不存在（404 natural）。完成判据"任何角色看不到视频入口、直访路由被挡"在当前代码状态下天然满足。
- **后端侧**：REQ-11 仍是 501 stub，本轮不进生产范围（与计划一致），无需动作。

## 4. 留给用户的提示（明早）

若用户的真实意图是"未来会做视频生成、现在先预埋一个开关防止它意外上线"，那属于**预埋未来功能的 feature flag 脚手架**，应在视频 UI 真正开发时一并设计（flag + 路由守卫 + 菜单条件渲染一套），而不是现在为空页面预埋。本 tick 不替用户做此决定，记录在此供明早确认。

## 5. 状态

T9 **判定为 no-op（无可隐藏对象，CR-04 在当前代码下天然满足）**。无代码改动、无分支 commit。结论已登记，建议明早用户确认意图是否为"预埋未来开关"——若是则转为新 slice 设计。
