# Nexa-AI 迭代变更请求登记（autonomous-product-iteration / 代码基线驱动）

> 事实源：当前代码 + 用户新需求。V1 文档冻结为参考，不反溯回写。
> 基线分支：integration/s8-backend（含 T4 已合）；worktree 隔离并行开发。
> 本轮目标（用户指令）：今天全部做完 → 跑 S12 分析新需求 → 继续循环迭代。CC 最多 10 并行。

## 迭代分型与分级（B0-B8 / L0-L5）

| CR | 用户反馈（业务意图） | 类型 | 等级 | 影响对象 | 验收方式 | 状态 |
|---|---|---|---|---|---|---|
| CR-01 | root 用户导航后菜单退化成普通用户 | B1 前端bug | L3 | AdminShell/ConsoleShell/路由组/nav | root 任意点跳菜单不退化，刷新保持 | 开发中(T7) |
| CR-02 | 右上角没有退出登录入口 | B1 前端bug | L1 | 顶栏账号下拉 | 点右上角→出现退出→真登出 | 待派(T8, after T7) |
| CR-03 | admin/console 两套割裂外壳 | B1 前端bug | L3 | 同 CR-01（合并处理） | 单一 AppShell + 角色动态菜单 | 开发中(T7) |
| CR-04 | 视频生成功能要隐藏 | B0/no-op | L0 | 无（前端无视频UI） | 调查确认无可隐藏对象 | done(no-op) |
| CR-05 | Redis 部署了但后端没用 | B4 增强 | L2 | 鉴权热点+key管理用例 | 同key二次命中+禁用evict+403 | 开发中(T12) |
| CR-06 | 超管页 mock 假数据要接真实接口 | B2 后端/B1前端 | L2 | 超管页 + 对应 API | 页面数据来自真实 API（Network 验证） | 待派(T10, after T7) |
| REQ-07 | Claude 协议非流式双向 | B2 后端 | L2 | ClaudeProtocolAdapter | 13测试绿+BUILD SUCCESS | done(T4) |
| REQ-08 | 流式 SSE 真打通+末尾计费落库 | B2 后端 | L2 | RelayController/StreamCodec/Billing | stream:true 端到端+落1条计费Log | 开发中(T5) |
| — | compliance 子域归位登记 | B8 梳理 | L1 | compliance 子域 | 登记"已建成待缝合" | done(T6) |

## 任务-CR 映射（派发层 tonight-driver-state.json 的 queue）

- T4 → REQ-07（done，已合 integration）
- T5 → REQ-08（开发中，CC proc_aa94748e3a8d）
- T6 → compliance 登记（done，doc-only）
- T7 → CR-01+CR-03（开发中，CC proc_2ea1ccc93f7d）
- T8 → CR-02（待 T7 完成后派）
- T9 → CR-04（done，no-op）
- T10 → CR-06（待 T7 完成后派）
- T11 → CR-05 用途清单（done，plan-only）
- T12 → CR-05 实施（开发中，CC proc_c59f97261957）

## 成功标准（本轮收口）

1. 所有 CR 状态 done 或明确 defer 理由。
2. 每个 CR 真验证：后端 compile+test+起服务 curl；前端 build+CDP 浏览器点测。
3. integration/s8-backend 全绿，可作发布候选（不自动合 main，不自动推生产——留用户确认）。
4. 全绿后跑 S12（post-acceptance-iteration-planning）分析新需求，登记下一轮 CR，继续循环。

## 红线

- 不自动合 main、不自动推生产：需用户显式确认。
- root 菜单降级(CR-01)代码做完做对+自检通过，但最终真机(澎湃OS)签收留用户。
- CC 以 root 跑必须 `--permission-mode acceptEdits`。
