# BUSINESS-FLOW — 业务流程总索引（S3 聚合入口）

> 项目：基于 new-api 的 AI API 网关 SaaS（RoutifyAPI）。
> 本文件是 S3「业务流程 PRD」的**索引/导航总入口**：汇总整体大图 `OVERALL-FLOW.md` 与 14 个分片细图 `flow/FL-*.md`（共 77 张图，diversity=1.0），并给出覆盖核对表。
> S3 配套聚合产物：本索引 + `BUSINESS-MAIN-FLOW.md`（端到端主流程）+ `EXCEPTION-FLOW.md`（全局异常）+ `PAGE-STATE-MATRIX.md`（页面状态矩阵，S4/S6 状态覆盖权威输入）+ `FLOW-COVERAGE.csv`（功能覆盖映射）。

---

## 0. 阅读顺序建议

1. 先读 [`OVERALL-FLOW.md`](OVERALL-FLOW.md) —— 跨端/跨角色主干大图 + 4 条主干路径清单 + 5 条跨切面契约（C1~C5）。
2. 再读 [`BUSINESS-MAIN-FLOW.md`](BUSINESS-MAIN-FLOW.md) —— 访客→注册→建 Key→Playground→Relay→扣费→看用量 的端到端闭环，及管理员侧运营链路。
3. 按需深入下表 14 个分片细图。
4. 异常处理统一查 [`EXCEPTION-FLOW.md`](EXCEPTION-FLOW.md)；页面状态统一查 [`PAGE-STATE-MATRIX.md`](PAGE-STATE-MATRIX.md)。

---

## 1. 整体大图

| 文件 | 内容 | 图数 |
|---|---|---|
| [`OVERALL-FLOW.md`](OVERALL-FLOW.md) | 端与角色总览、主干大图（按端/角色 subgraph）、4 条主干路径（MP-1~MP-4）、6 条主异常（EX-1~EX-6）、5 条跨切面契约（C1 未登录先登录 / C2 externalJump / C3 二次验证 / C4 Turnstile / C5 self-scope） | 1 |

---

## 2. 分片细图索引（14 个 FL-*.md）

| 分片文件 | 域 | 角色 |
|---|---|---|
| [`flow/FL-public.md`](flow/FL-public.md) | 公开站点/营销（D-公开） | 访客 / 用户 |
| [`flow/FL-account.md`](flow/FL-account.md) | 账号与身份 D1 + Telegram 登录 D4 | 访客 / 用户 / 管理员 / Root / 系统 |
| [`flow/FL-growth.md`](flow/FL-growth.md) | 增长：签到 D2 + 邀请返利分销 D3 | 用户 / 系统 / 管理员 |
| [`flow/FL-token.md`](flow/FL-token.md) | 令牌管理 D3 | 用户 / 外部客户端 / 系统 |
| [`flow/FL-usagelog.md`](flow/FL-usagelog.md) | 日志与用量 D4 | 管理员 / 用户 / 外部客户端 / 系统 |
| [`flow/FL-asynctask.md`](flow/FL-asynctask.md) | 异步任务中心 D5（MJ/Suno/视频） | 用户 / 管理员 / 系统 |
| [`flow/FL-prefill.md`](flow/FL-prefill.md) | 预填分组 D6 | 管理员 |
| [`flow/FL-channel.md`](flow/FL-channel.md) | 渠道管理与上游路由 D7/D10（含亲和缓存、跨分组重试） | 管理员 / 系统 / Root |
| [`flow/FL-billing.md`](flow/FL-billing.md) | 计费与钱包 D8（充值/预扣结算/订阅/兑换码/阶梯计费） | 用户 / 系统 / 管理员 / 访客 |
| [`flow/FL-model.md`](flow/FL-model.md) | 模型广场与元数据 D9（元数据/同步/价格/排行） | 管理员 / 用户 / 访客 |
| [`flow/FL-relay.md`](flow/FL-relay.md) | Relay 网关多协议中转 D11/D13（含视频内容代理） | 用户 / 外部客户端 / 系统 / 上游 |
| [`flow/FL-deploy.md`](flow/FL-deploy.md) | 部署管理 D14（io.net 集成） | 管理员 |
| [`flow/FL-ops.md`](flow/FL-ops.md) | 运营 / 系统设置 / 运维 D16 | 匿名 / Root |
| [`flow/FL-playground.md`](flow/FL-playground.md) | Playground 在线试用 D17 | 用户 / 匿名 |

---

## 3. 覆盖核对表（分片 × 场景 × 图 × 关键场景）

> 图数 = 该文件内 ```mermaid 图块数；场景数 = 编号场景（含状态机/数据流/时序）的数量。

| 分片 | 场景数 | 图数 | 关键场景列表 |
|---|---:|---:|---|
| OVERALL-FLOW | 1（主干大图）+4 路径 | 1 | 主干大图（PUB/AUTH/CONSOLE/RELAY/ADMIN subgraph）、MP-1 访客自助开通到首次调用、MP-2 增长闭环、MP-3 管理员运营链路、MP-4 用量与计费观测 |
| FL-public | 6 | 6 | P-1 首页公开状态聚合、P-2 Playground 试用入口、P-3 用户协议页、P-4 隐私政策页、P-5 主题/语言切换、P-6 控制台/广场/API Keys 入口跳转 |
| FL-account | 11 | 11 | AC-1 邮箱注册、AC-2 邮箱登录、AC-3 找回密码、AC-4 OAuth 登录/绑定、AC-5 微信扫码、AC-6 Telegram 登录、AC-7 Telegram 绑定、AC-8 2FA、AC-9 Passkey、AC-10 用户管理、AC-11 OAuth 绑定管理 |
| FL-growth | 5 | 5 | GR-1 每日签到状态机、GR-2 签到统计查询、GR-3 签到配置、GR-4 邀请返利分销全链、GR-5 邀请额度划转 |
| FL-token | 6 | 6 | TK-1 创建令牌、TK-2 生命周期状态机、TK-3 列表/搜索、TK-4 密钥访问（明文）、TK-5 额度策略组合、TK-6 用量查询（外部 API） |
| FL-usagelog | 8 | 8 | UL-1 日志查询（全量/自助）、UL-2 用量统计看板、UL-3 按日配额、UL-4 按 key 查日志、UL-5 审计日志、UL-6 排行榜快照、UL-7 历史日志清理、UL-8 亲和缓存统计 |
| FL-asynctask | 5 | 5 | AT-1 状态机轮询（CAS）、AT-2 用户任务列表、AT-3 全量看板+超时扫描、AT-4 退款结算+产物、AT-5 MJ 多 action 提交 |
| FL-prefill | 3 | 3 | PF-1 创建分组、PF-2 更新分组、PF-3 下拉填充+软删除 |
| FL-channel | 5 | 5 | CH-1 渠道创建/编辑、CH-2 优先级+权重选渠、CH-3 自动禁用/恢复状态机、CH-4 会话亲和缓存、CH-5 auto 跨分组重试 |
| FL-billing | 5 | 5 | BL-1 在线充值入账、BL-2 预扣结算（多退少不补）、BL-3 订阅生命周期、BL-4 兑换码、BL-5 阶梯/表达式计费 |
| FL-model | 5 | 5 | ML-1 元数据创建/更新、ML-2 上游同步、ML-3 可见模型列表、ML-4 公开价格页、ML-5 模型排行榜 |
| FL-relay | 5 | 5 | RL-1 OpenAI 主链路时序、RL-2 协议格式分发、RL-3 错误处理/重试/禁用、RL-4 厂商适配、RL-5 视频内容代理 |
| FL-deploy | 5 | 5 | DP-1 集成开关+连接测试、DP-2 部署生命周期、DP-3 列表/搜索/详情、DP-4 容器列表/详情/日志、DP-5 资源选型 |
| FL-ops | 5 | 5 | OP-1 系统初始化、OP-2 选项读写、OP-3 性能监控+运维、OP-4 日志维护+Uptime、OP-5 合规确认+控制台迁移 |
| FL-playground | 3 | 3 | PG-1 首页入口+登录引导、PG-2 站内对话提交主链路、PG-3 试用错误回显 |
| **合计** | **82 场景** | **78 图** | 14 分片 + OVERALL，跨 17 个域，diversity=1.0 |

> 注：77 张分片图 + OVERALL 主干 1 图 = 78 图。FL-playground PG-1/PG-2 与 FL-public P-2 同涉首页入口（F-4039/F-4040），以 FL-public 为主归属、FL-playground 为入口引用。

---

## 4. 配套聚合产物

| 产物 | 用途 | 下游消费 |
|---|---|---|
| [`BUSINESS-MAIN-FLOW.md`](BUSINESS-MAIN-FLOW.md) | 端到端业务主流程（用户侧闭环 + 管理员侧运营链路） | S4 信息架构 / S5 原型主线 |
| [`EXCEPTION-FLOW.md`](EXCEPTION-FLOW.md) | 全局异常流程汇总（鉴权/额度/渠道/支付/超时/限流等）+ 处理策略 | S4/S6 异常态设计、错误文案 |
| [`PAGE-STATE-MATRIX.md`](PAGE-STATE-MATRIX.md) | 页面/组件 × 状态矩阵（默认/loading/空/成功/失败/权限拦截/特殊态） | **S4/S6 状态覆盖权威输入** |
| [`FLOW-COVERAGE.csv`](FLOW-COVERAGE.csv) | 231 功能 ID × 是否被 flow 覆盖映射 | S3 完备性核对、追溯 |
