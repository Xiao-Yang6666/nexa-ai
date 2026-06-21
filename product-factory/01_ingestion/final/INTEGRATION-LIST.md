# INTEGRATION-LIST — 集成点全景

> 来源：repo `repo/new-api/`（P0 逻辑权威）—— `constant/channel.go`（渠道类型常量 1~57）、`controller/*.go`、`setting/**`、`relay/channel/*`；`AGENTS.md` 自述「40+ upstream providers (OpenAI, Claude, Gemini, Azure, AWS Bedrock, etc.)」。
> 角色：所有外部 / 上游集成点清单，供 S2 判定保留范围、S3 架构、S6 接口设计。
> 量级声明：渠道类型常量编到 **57（ChannelTypeCodex=57）**，其中已命名上游 provider **约 52 个**（部分编号空缺/保留）。实际数量已**超过** AGENTS.md 自述的「40+」。

## 1. 上游 AI Provider（relay channel types，证据：constant/channel.go）

> 完整枚举（按常量值），证据一手。这是「40+ upstream providers」的真实落地。

### 1.1 主流大模型 provider
| ID | Provider | 备注 |
|---|---|---|
| 1 | OpenAI | OpenAI 协议基准 |
| 3 | Azure | Azure OpenAI |
| 6 | OpenAIMax | OpenAI 变体 |
| 14 | Anthropic | Claude 原生协议 |
| 24 | Gemini | Google Gemini 原生 `/v1beta` |
| 11 | PaLM | Google PaLM |
| 33 | AWS | AWS Bedrock |
| 41 | VertexAI | Google Vertex AI |
| 20 | OpenRouter | 聚合路由 |
| 42 | Mistral | |
| 34 | Cohere | |
| 27 | Perplexity | |
| 48 | xAI | Grok |
| 43 | DeepSeek | |
| 25 | Moonshot | Kimi |

### 1.2 国内大模型 provider
| ID | Provider | 备注 |
|---|---|---|
| 15 | Baidu | 文心 |
| 46 | BaiduV2 | 文心 v2 |
| 16 | Zhipu | 智谱 |
| 26 | ZhipuV4 | 智谱 v4 |
| 17 | Ali | 通义/阿里 |
| 18 | Xunfei | 讯飞星火 |
| 19 | 360 | 360 智脑 |
| 23 | Tencent | 腾讯混元 |
| 31 | LingYiWanWu | 零一万物 |
| 35 | MiniMax | |
| 45 | VolcEngine | 火山方舟 |
| 49 | Coze | 扣子 |

### 1.3 自部署 / 推理框架 / 边缘
| ID | Provider | 备注 |
|---|---|---|
| 4 | Ollama | 本地模型（含 pull/delete/version） |
| 47 | Xinference | |
| 39 | Cloudflare | Workers AI |
| 40 | SiliconFlow | 硅基流动 |
| 44 | MokaAI | |
| 38 | Jina | rerank/embedding |
| 53 | Submodel | |
| 56 | Replicate | |
| 8 | Custom | 自定义 OpenAI 兼容端点 |

### 1.4 第三方聚合 / 代理 provider
| ID | Provider | 备注 |
|---|---|---|
| 7 | OhMyGPT | |
| 9 | AILS | |
| 10 | AIProxy | |
| 12 | API2GPT | |
| 13 | AIGC2D | |
| 21 | AIProxyLibrary | |
| 22 | FastGPT | |
| 37 | Dify | |

### 1.5 多媒体 / 任务型 provider（接入异步任务中心 D5）
| ID | Provider | 备注 |
|---|---|---|
| 2 | Midjourney | 绘图任务 |
| 5 | MidjourneyPlus | 绘图任务 |
| 36 | SunoAPI | 音乐 / 歌词 |
| 50 | Kling | 视频（可灵） |
| 51 | Jimeng | 视频（即梦） |
| 52 | Vidu | 视频 |
| 54 | DoubaoVideo | 视频（豆包） |
| 55 | Sora | 视频 |
| 57 | Codex | OpenAI Codex（含 codex_usage / 亲和缓存模板） |

> 另有 relay/channel/task/ 下 hailuo、ali、vertex、gemini 等任务 adapter（部分共用上述渠道类型，按 platform 区分）。
> 0 = Unknown（占位）；ChannelTypeDummy 仅用于计数。

## 2. 协议端点类型（relay_mode，对外暴露的 API 形态）

> 证据：relay/constant/relay_mode.go。这些是「集成进来的客户端协议」。

- OpenAI 兼容：`/v1/chat/completions`、`/v1/completions`、`/v1/embeddings`、`/v1/moderations`、`/v1/images/*`、`/v1/edits`、`/v1/audio/*`、`/v1/responses`、`/v1/realtime`（WS）、`/v1/rerank`
- Claude 原生：`/v1/messages`
- Gemini 原生：`/v1beta/models/*`
- 任务型：`/mj/submit/*`、`/mj/task/*`、`/suno/submit/:action`、`/suno/fetch[/:id]`、`/video/*`
- Playground：`/pg/chat/completions`

## 3. 身份认证集成（OAuth / 第三方登录）

| 集成 | 类型 | 路由 / 证据 | 备注 |
|---|---|---|---|
| GitHub | OAuth2（标准） | `/api/oauth/github`、controller/oauth.go | |
| Discord | OAuth2（标准） | `/api/oauth/discord`、system_setting/discord.go | |
| OIDC（通用） | OIDC discovery | `/api/oauth/oidc`、system_setting/oidc.go | 可对接任意 OIDC IdP |
| LinuxDO | OAuth2（标准） | `/api/oauth/linuxdo`、model/user.go LinuxDOId | |
| 自定义 OAuth provider | OIDC/OAuth2（root 配置） | controller/custom_oauth.go、model/custom_oauth_provider.go | discovery + CRUD |
| WeChat（微信） | 非标准 OAuth | controller/wechat.go | 单独路由 |
| Telegram | Login Widget（HMAC-SHA256，BotToken） | `/api/oauth/telegram/login`、`/api/oauth/telegram/bind`、controller/telegram.go | ★旗舰#3 |
| Passkey / WebAuthn | FIDO2 | controller/passkey.go | 无密码登录 |

## 4. 支付 / 充值集成（证据：controller/topup_*.go、subscription_payment_*.go、setting/payment_*.go）

| 网关 | 类型 | 证据 | 备注 |
|---|---|---|---|
| Stripe | 卡 / 国际 | controller/topup_stripe.go、subscription_payment_stripe.go、setting/payment_stripe.go（StripeWebhookSecret） | 充值 + 订阅，含 webhook |
| Creem | 国际 | controller/topup_creem.go、setting/payment_creem.go（CreemWebhookSecret） | 含 webhook |
| ePay（易支付） | 国内聚合（支付宝/微信扫码） | controller/subscription_payment_epay.go | 订阅支付 |
| Waffo | 自定义网关 | controller/topup_waffo.go、SettingsPaymentGatewayWaffo.jsx | 充值 |
| Waffo Pancake | 自定义网关变体 | controller/topup_waffo_pancake.go、SettingsPaymentGatewayWaffoPancake.jsx | 充值 |
| 通用支付网关 | 抽象层 | SettingsPaymentGateway.jsx、payment_webhook_availability.go | webhook 可用性探测 |

> 充值产物落地：model/topup.go（TopUp 记录）、model/redemption.go（兑换码）、订阅 model/subscription.go。

## 5. 通知 / 消息渠道（证据：NotificationSettings.jsx）

| 渠道 | 用途 | 证据 |
|---|---|---|
| Email（SMTP） | 注册验证、找回密码、额度预警通知 | warningType='email' |
| Webhook | 额度预警 / 事件回调 | warningType='webhook' |
| Bark | 额度预警推送（iOS） | warningType='bark' |

## 6. Webhook（入站回调）

| Webhook | 方向 | 证据 |
|---|---|---|
| Stripe webhook | 入站（支付状态回调） | StripeWebhookSecret |
| Creem webhook | 入站（支付状态回调） | CreemWebhookSecret |
| 支付通用 webhook 可用性 | 自检 | controller/payment_webhook_availability.go |
| 任务异步回调 | 入站（MJ/Suno/视频上游回调进度） | relay/channel/task/* |
| 出站通知 webhook | 出站（额度预警） | NotificationSettings warningType='webhook' |

## 7. 基础设施 / 运维集成

| 集成 | 用途 | 证据 |
|---|---|---|
| Redis | 缓存 / 限流 / 会话 / 分布式锁 | go-redis（AGENTS.md） |
| SQLite / MySQL≥5.7.8 / PostgreSQL≥9.6 | 持久化（三选一全兼容） | GORM v2 |
| Uptime-Kuma | 站点状态监控接入 | controller/uptime_kuma.go |
| io.net | GPU 模型部署集群 | controller/deployment.go、pkg/ionet |
| Turnstile（Cloudflare） | 人机校验（签到 / 注册） | TurnstileCheck（checkin.go） |

## 8. 数量小结

- **上游 AI provider**：渠道类型常量编号到 57，已命名 provider **约 52 个**（覆盖主流国际 + 国内 + 自部署 + 聚合 + 多媒体任务型）。**超过 AGENTS.md 自述「40+」**。
- **OAuth / 登录集成**：8 类（GitHub / Discord / OIDC / LinuxDO / 自定义 OAuth / WeChat / Telegram / Passkey）。
- **支付网关**：6 个（Stripe / Creem / ePay / Waffo / Waffo Pancake / 通用网关）。
- **通知渠道**：3 个（Email / Webhook / Bark）。
- **入/出站 Webhook**：支付回调 + 任务回调 + 额度预警出站。
- **基础设施**：Redis、三类数据库、Uptime-Kuma、io.net、Turnstile。

## 9. GAP（详见 SEMANTIC-GAPS.md）

- 各 provider 的实际启用范围、计费倍率具体数值未在 repo 固化（运营期配置）→ SG-002。
- routifyapi.com 公开仅展示 Anthropic / OpenAI 两家，与 repo 52 provider 能力面差异 → S2 需裁决「上线哪些」 → SG-001 / SG-005。
- 支付网关在 routifyapi SaaS 下的最终启用组合未定（动态域未访问）→ SG-003。
