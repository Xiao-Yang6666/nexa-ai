# REFERENCE-TRIAGE — URL 角色分流

> 对 routifyapi.com 站内/外链以及 repo 关联 URL 逐个分类角色并记录动作与去向。证据来源：`website-mirror/routifyapi/extracted/INTEGRATION-LIST.md`、`rendered/pages.jsonl` 的 links/nav 字段、`sitemap/external-urls.txt`，以及 PROJECT.md。

| URL | 出处 | 角色 | 已访问? | 提取了什么 | 去向 |
|---|---|---|---|---|---|
| https://routifyapi.com/ | PROJECT.md / 镜像首页 | 设计/视觉参考 + 营销定位 | ✅ 镜像+截图+DOM | 首页 sections、导航、CTA、cssVars 设计 token、定位文案「原生满血，只付零头」 | WEBSITE-COVERAGE.md / DESIGN（下游 S5） |
| https://routifyapi.com/agreement | 站内导航「用户协议」 | 法律/合规公开页 | ✅ 镜像+DOM | 用户协议页存在（内容待 OCR/正文细读） | WEBSITE-COVERAGE.md（公开合规页候选 FC） |
| https://routifyapi.com/privacy | 站内导航「隐私政策」 | 法律/合规公开页 | ✅ 镜像+DOM | 隐私政策页存在 | WEBSITE-COVERAGE.md（公开合规页候选 FC） |
| https://app.routifyapi.com/console | 首页链接「控制台」 | 业务逻辑参考（控制台主入口） | ❌ 跨域动态，需登录 | 仅确认存在控制台子域 | GAP（非阻塞）；后台能力以 repo 为权威 |
| https://app.routifyapi.com/pricing | 首页链接「模型广场」 | 业务逻辑参考（价格/模型广场） | ❌ 跨域动态 | 仅确认存在价格页 | GAP；repo `controller/pricing.go` 为权威 |
| https://app.routifyapi.com/console/token | 首页链接「API Keys」 | 业务逻辑参考（密钥管理） | ❌ 跨域动态 | 仅确认存在 token 管理页 | GAP；repo `controller/token.go` 为权威 |
| https://docs.newapi.pro/ | 首页页脚「文档」 | 文档/背景参考 | ❌（外部文档站） | 确证产品底层 = New API | 背景记录；repo 为一手逻辑权威 |
| https://github.com/QuantumNous/new-api | 页脚「New API」 / PROJECT.md | license/源仓库 | ✅ 已克隆到 repo/new-api | 全量源码 | REPO-INSPECTION.md（一手逻辑来源） |
| https://github.com/huey1in | 首页 GitHub 图标 | 竞品/背景（作者主页） | ❌ | 仅链接 | 记录不深挖 |
| mailto:2926957031@qq.com | 首页 Email 图标 | 联系方式（背景） | N/A | 客服/联系邮箱 | 记录不深挖 |

## 角色分类小结
- **设计/视觉参考**：routifyapi.com 首页（P0 视觉）。
- **法律/合规公开页**：/agreement、/privacy（公开页候选功能）。
- **业务逻辑参考（动态 GAP）**：app.routifyapi.com 的 console/pricing/token —— 未访问，后台逻辑改以 repo 为权威。
- **源仓库/license**：QuantumNous/new-api（一手功能逻辑来源，已克隆）。
- **文档/背景**：docs.newapi.pro、作者主页、联系邮箱（记录不深挖）。

## 反 overclaim 声明
本轮**未**对 app.routifyapi.com 任何动态页做真实访问/截图；凡涉及控制台/价格/密钥 UI 的事实，一律记为 GAP 或以 repo 源码逻辑替代，**不**宣称「控制台截图已完成」。
