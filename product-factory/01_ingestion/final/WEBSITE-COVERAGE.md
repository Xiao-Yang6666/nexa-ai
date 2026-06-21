# WEBSITE-COVERAGE — 参考站证据闭环

> 来源：`01_ingestion/evidence/website-mirror/routifyapi/`（mirror-manifest.json、rendered/pages.jsonl、3 截图、3 DOM、extracted/* 9 份）。
> 角色：P0 视觉/营销/公开体验来源。**不**作为后台/控制台范围权威。

## 1. 抓取统计
- Root URL：https://routifyapi.com
- 抓取时间：2026-06-18 22:12:41 CST
- Max pages / depth：80 / 3
- 已访问页：3（home×1、content×2）
- 失败页：0
- 外链 URL：6

## 2. 页面清单（PAGE-INVENTORY）
| ID | 类型 | 标题 | URL | 关键标题 |
|---|---|---|---|---|
| page-0001 | home | Routify | https://routifyapi.com/ | rout e ·AI / 全球路由，毫秒直达 / 为什么选择 Routify |
| page-0002 | content | Routify | https://routifyapi.com/agreement | 用户协议 / 产品 / 法律 |
| page-0003 | content | Routify | https://routifyapi.com/privacy | 隐私政策 / 产品 / 法律 |

## 3. 首页语义抽取（visibleText）
产品名：**Routify**（"rout e ·AI"）。

定位文案（一手营销证据）：
- 主张：「原生体验，远低于官方的价格。」「全球路由，毫秒直达」「就近接入，稳定连通每一个模型。」
- 三大卖点：
  1. **远低官方价格** —「同样的原生体验，价格只是官方的零头。用多少付多少，没有套路。」
  2. **原生满血参数** —「不限速、不降智、不裁剪上下文，与官方完全一致的满血模型能力。」
  3. **一行接入兼容** —「完全兼容 OpenAI 协议，只需改一个 base_url，现有代码零成本迁移。」
- 指标条：`0.0% 可用性`、`<0ms 路由开销`、`满血 原厂参数`。
- 已支持厂商展示：**Anthropic、OpenAI**。
- 页脚：「© 2026 Routify. 版权所有 基于 New API」。

## 4. 导航 / 动作 / 实体（公开页）
- 导航：首页、控制台、模型广场、API Keys、文档（页脚分「产品 / 法律」两组）。
- 公开按钮/CTA：控制台、模型广场、切换主题、切换语言、登录、发送、立即开始。
- 公开表单字段：首页对话输入框 placeholder「问点什么…」（Playground/试用入口候选）。

## 5. 设计 token（cssVars 一手证据，供 S5 DESIGN）
| token | 值 | 含义 |
|---|---|---|
| `--page-bg` / `--ui-bg` | `#FFFDF7` | 暖白页面底色 |
| `--ui-bg-subtle` | `#F2F0E9` | 次级底色 |
| `--ui-text` | `#080808` | 主文本 |
| `--ui-text-3` | `#999999` | 次文本 |
| `--ui-accent` / hover | `#222222` / `#333333` | 强调（近黑） |
| `--ui-brand` | `#0072f5` | 品牌蓝 |
| `--ui-danger` | `#ec5e41` | 危险红 |
| `--ui-border` / divider | `#e3e3e3` / `#eeeeee` | 边框/分隔 |
| 代码高亮 tok-* | keyword `#892b8a` / string `#379d4a` / function `#d59300` / property `#B1108E` | 代码块配色（含 Playground/文档高亮） |
> 视觉基调：暖白底 + 近黑强调 + 品牌蓝点缀 + 代码高亮配色 → 偏「极简 / 开发者工具」调性。Tailwind 体系（`--tw-*` 变量大量出现）。

## 6. 失败 / BLOCKER
- 镜像层失败页：无。

## 7. 动态证据 GAP（PASS_WITH_GAPS，非阻塞）
- `app.routifyapi.com` 控制台 / 价格 / API Keys：未访问（跨域动态，需登录）。
- 登录 / 注册流程：仅见公开 CTA，未捕获动态表单。
- 模型广场真实模型/价格列表：未捕获。
> 这些 GAP 不阻塞 S2；后台能力以 repo new-api 源码为权威来源。S5/S6 设计/原型前需补动态证据或显式标注 UI 假设。**本轮未宣称任何动态截图已完成。**
