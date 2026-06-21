# FUNCTION-LIST（功能清单）

> 全量离散功能清单，共 **232** 个功能项。机器权威 = FUNCTION-LIST.csv；本文件为人类可读视图。
> 覆盖 newapi 17 能力域 + 公开站点 + NFR/合规/RBAC 三横切维度。

## Compliance（6 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5016 | 数据分级登记 | 运营/Root | P1 | 对凭证/PII/内容/计量四级数据字段标注分级 | DC-001 |
| F-5017 | prompt留存开关与保留期 | 运营/Root | P0 | 正文留存默认关可开且独立保留期默认<=30天 | DC-005 |
| F-5018 | 合规分组仅境内provider | 运营/Root | P1 | 为合规分组限定仅命中境内数据驻地渠道 | DC-008 |
| F-5019 | 数据出境告知与驻地标注 | 访客/用户及以上 | P0 | 每provider标注境内外驻地并明示请求转发地区 | DC-008/DC-009 |
| F-5020 | 账号注销级联删除 | 用户及以上 | P0 | 注销级联删除令牌/OAuth绑定/PII并匿名化日志 | DC-003/DC-011 |
| F-5021 | 隐私政策与出境条款同意闸门 | 访客/用户 | P0 | 未接受含出境与留存条款的协议不可调用 | DC-010 |

## D1 账号与身份（38 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-1001 | 邮箱密码注册 | 匿名访客 | P0 | 创建 common 角色用户并初始化额度与 aff_code | FC-001 |
| F-1002 | 邮箱密码登录 | 注册用户 | P0 | 校验通过后 setupLogin 建立会话 | FC-001 |
| F-1003 | 用户登出 | 登录用户 | P1 | 清除会话使后续鉴权失效 | FC-001 |
| F-1004 | 发送注册/找回邮箱验证码 | 匿名访客 | P0 | 发送一次性邮箱验证码并受频率限制 | FC-002 |
| F-1005 | 注册时邮箱验证码校验 | 匿名访客 | P0 | 校验码匹配才允许创建账号 | FC-002 |
| F-1006 | 发送重置密码邮件 | 匿名访客 | P0 | 向已注册邮箱发送重置密码链接/令牌 | FC-003 |
| F-1007 | 提交重置新密码 | 匿名访客 | P0 | 校验令牌后更新用户密码 | FC-003 |
| F-1008 | 管理端查询/搜索用户 | 管理员 | P1 | 返回用户列表/匹配结果(分页) | FC-004 |
| F-1009 | 管理端创建用户 | 管理员 | P1 | 直接创建一个新账号 | FC-004 |
| F-1010 | 管理端管理用户状态(ManageUser) | 管理员 | P1 | 启用/禁用/提升/删除指定用户 | FC-004 |
| F-1011 | 更新用户资料(管理端) | 管理员 | P2 | 更新指定用户的资料/额度/分组 | FC-004 |
| F-1012 | 用户角色分级(root/admin/common) | 系统 | P0 | 按角色裁定接口可访问性 | FC-005 |
| F-1013 | 用户分组(group)绑定 | 管理员 | P1 | 用户请求按所属分组应用倍率/可用渠道 | FC-006 |
| F-1014 | 用户备注与个人设置 | 管理员;本人 | P2 | 保存 admin 备注与用户偏好 | FC-007 |
| F-1015 | 生成 OAuth state(CSRF)并暂存 aff | 匿名访客 | P0 | 生成随机 state 写入会话并暂存邀请码 | FC-008 |
| F-1016 | GitHub OAuth 登录/注册 | 匿名访客 | P1 | 换取 GitHub 用户信息后登录或创建用户 | FC-008 |
| F-1017 | GitHub legacy_id 迁移 | 系统 | P2 | 将旧 login 标识迁移到数字 ID | FC-008 |
| F-1018 | Discord OAuth 登录/绑定 | 匿名访客;登录用户 | P2 | 登录创建或绑定 discord_id | FC-009 |
| F-1019 | OIDC 通用 OAuth 登录/绑定 | 匿名访客;登录用户 | P2 | 经 OIDC discovery 完成登录或绑定 oidc_id | FC-010 |
| F-1020 | LinuxDO OAuth 登录/绑定 | 匿名访客;登录用户 | P2 | 登录或绑定 linux_do_id（含信任级校验） | FC-011 |
| F-1021 | WeChat 扫码授权 | 匿名访客 | P2 | 发起微信授权获取 wechat_id | FC-012 |
| F-1022 | WeChat 绑定/登录 | 匿名访客;登录用户 | P2 | 用微信授权码完成绑定或登录 | FC-012 |
| F-1023 | 获取自定义 OAuth discovery | root | P2 | 拉取目标 issuer 的端点配置 | FC-013 |
| F-1024 | 自定义 OAuth provider CRUD | root | P2 | 管理自定义 OAuth 提供商配置 | FC-013 |
| F-1025 | 自定义 provider 登录与绑定写入 | 匿名访客;登录用户 | P2 | 经 GenericOAuthProvider 登录并写 user_oauth_b | FC-014 |
| F-1026 | 解绑自定义 OAuth(本人) | 登录用户 | P2 | 删除本人对应 provider 绑定 | FC-014 |
| F-1027 | 管理端查询/解绑用户 OAuth 绑定 | 管理员 | P2 | 查看或移除指定用户的 OAuth 绑定 | FC-014 |
| F-1028 | Passkey 注册(begin/finish) | 登录用户 | P2 | 完成 WebAuthn 凭据注册 | FC-015 |
| F-1029 | Passkey 登录(begin/finish) | 匿名访客 | P2 | 无密码 WebAuthn 登录 | FC-015 |
| F-1030 | Passkey 二次验证(verify) | 登录用户 | P2 | 用 passkey 完成敏感动作二次校验 | FC-015 |
| F-1031 | Passkey 删除/查询状态 | 登录用户 | P2 | 查询或删除本人 passkey 凭据 | FC-015 |
| F-1032 | 管理端重置用户 Passkey | 管理员 | P2 | 清除指定用户全部 passkey | FC-015 |
| F-1033 | 2FA 启用流程(setup→enable) | 登录用户 | P2 | 绑定 TOTP 并开启双因子 | FC-016 |
| F-1034 | 2FA 关闭 | 登录用户 | P2 | 验证后关闭双因子 | FC-016 |
| F-1035 | 2FA 备份码生成 | 登录用户 | P2 | 生成/重置一次性备份码 | FC-016 |
| F-1036 | 2FA 登录校验 | 匿名访客 | P0 | 密码登录后用 TOTP/备份码完成第二步 | FC-016 |
| F-1037 | 管理端 2FA 统计与禁用 | 管理员 | P2 | 查看 2FA 启用统计或为用户强制关闭 | FC-016 |
| F-1038 | 通用敏感动作二次验证 | 登录用户 | P1 | SecureVerificationRequired 保护下校验身份 | FC-017 |

## D2 签到（5 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-1046 | 每日签到领取随机额度 | 登录用户 | P0 | 当日首次签到发放 MinQuota..MaxQuota 随机额度 | FC-019 |
| F-1047 | 签到状态与本月记录查询 | 登录用户 | P1 | 返回本月签到记录与累计统计 | FC-020 |
| F-1048 | 签到防重复(唯一约束+事务) | 系统 | P0 | 靠 (user_id,checkin_date) 唯一索引与事务拦截重复 | FC-021 |
| F-1049 | 签到开关与额度区间配置 | 管理员 | P1 | 设置 Enabled 与 MinQuota/MaxQuota | FC-022 |
| F-1050 | 签到人机校验 | 登录用户 | P1 | TurnstileCheck 通过后才执行签到 | FC-023 |

## D3 邀请返利分销（7 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-1039 | 获取个人邀请码 | 登录用户 | P0 | 返回本人 aff_code（无则生成唯一 4 位码） | FC-024 |
| F-1040 | 邮箱注册邀请码归因 | 匿名访客 | P0 | 解析 inviterId 并记录 InviterId | FC-025 |
| F-1041 | OAuth 注册邀请码归因 | 匿名访客 | P1 | 从 session 取 aff 解析 inviterId | FC-025 |
| F-1042 | 邀请人奖励发放 | 系统 | P0 | 邀请人 AffCount++ 且 AffQuota/AffHistoryQuot | FC-026 |
| F-1043 | 新用户邀请奖励 | 系统 | P1 | 新用户初始额度叠加 QuotaForNewUser | FC-027 |
| F-1044 | 邀请额度划转为可用额度 | 登录用户 | P0 | 从 AffQuota 扣减并增加可用 Quota | FC-028 |
| F-1045 | 邀请统计展示 | 登录用户 | P1 | 返回 AffCount/AffQuota/AffHistoryQuota | FC-029 |

## D4 Telegram登录Bot（4 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-1051 | Telegram 登录(HMAC 校验) | 匿名访客 | P1 | 校验 HMAC 后按 telegram_id 登录 | FC-018 |
| F-1052 | Telegram 绑定到现有账号 | 登录用户 | P1 | 校验 HMAC 后将 telegram_id 写入当前用户 | FC-018 |
| F-1053 | Telegram Login Widget HMAC 防伪 | 系统 | P0 | 用 BotToken 派生密钥校验参数 hash | FC-018 |
| F-1054 | Telegram 绑定唯一性校验 | 系统 | P1 | 防止同一 telegram_id 被多账号绑定 | FC-018 |

## NFR（5 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5001 | 网关附加延迟与转发开销埋点 | 运维/Root | P0 | 采集网关附加延迟p50/p99并暴露指标 | NFR-P01/P02/P06 |
| F-5002 | 性能压测与基准门禁 | 运维/Root | P1 | 对chat转发做恒定负载压测验证吞吐与并发目标 | NFR-P04/P05 |
| F-5003 | 渠道健康度看板 | 运营/运维/管理员/Root | P0 | 展示每渠道错误率/延迟/AutoBan状态供降级判断 | NFR-A02/A04 |
| F-5004 | SLA月度可用性报表 | 运营/管理员/Root | P1 | 按分钟探针聚合数据面/控制面月度可用性 | NFR-A01 |
| F-5005 | 容灾演练与备份恢复 | 运维/Root | P1 | 主从切换与备份恢复达成RTO/RPO目标 | NFR-A08 |

## Observability（3 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5010 | Prometheus RED指标导出 | 运维/Root | P0 | 暴露按渠道/模型的请求率/错误率/延迟与额度速率 | NFR-O01/O02 |
| F-5011 | 多渠道告警编排 | 运营/运维/管理员/Root | P0 | 渠道错误率/额度/限流/延迟超SLO经Email/Webhook/Bark告警 | NFR-O03 |
| F-5012 | 链路追踪trace_id贯穿 | 运维/Root | P1 | 入站到上游到结算贯穿trace_id并支持OTel导出 | NFR-O04 |

## Playground（1 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4038 | 站内对话试用 Playground（临时令牌走 relay，禁用 access token） | user | P0 | 以临时令牌构造 relay 上下文并转发对话请求 | FC-120 |

## RBAC（6 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5030 | 功能权限组运营集运维集分配 | Root | P1 | Root为admin细分运营/运维最小权限集 | RBAC-matrix |
| F-5031 | 三级系统角色鉴权 | 全部 | P0 | common/admin/root经AdminAuth/RootAuth中间件鉴 | RBAC-matrix |
| F-5032 | self-scope资源越权防护 | 用户/开发者 | P0 | 按user_id强制过滤令牌/额度/任务/日志 | RBAC-matrix |
| F-5033 | 高危操作二次验证闸门 | 用户及以上 | P0 | 取Key/改倍率/重置2FA等统一接入SecureVerification | RBAC-matrix |
| F-5034 | 角色操作权限矩阵配置化 | Root | P2 | 矩阵可后台查看与审计变更 | RBAC-matrix |
| F-5035 | 团队工作区实体阶段二预留 | 用户/Root | P3 | 引入team_id与Owner/Member两级角色及共享额度池 | SG-006 |

## Relay（1 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4046 | 生成视频内容网关代理下发（按渠道类型解析 URL + SSRF 校验 + 流式回传，含 Gemini 分支） | user | P1 | 代理拉取上游视频内容并流式回写给用户 | FC-128 |

## Relay 网关（14 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3026 | OpenAI 兼容 chat/completions 与 completions 中转 | user | P0 | 按 RelayFormatOpenAI 转发到上游并回传响应 | FC-086 |
| F-3027 | embeddings 中转（含后缀匹配兼容） | user | P1 | 按 RelayFormatEmbedding 转发 | FC-087 |
| F-3028 | images 中转（generations/edits，variations 未实现） | user | P1 | 按 RelayFormatOpenAIImage 转发 | FC-088 |
| F-3029 | audio 中转（speech/transcription/translation 统一格式） | user | P1 | 按 RelayFormatOpenAIAudio 转发 | FC-089 |
| F-3030 | moderations 内容审核中转 | user | P2 | 按 RelayFormatOpenAI 转发审核请求 | FC-090 |
| F-3031 | rerank 重排序中转 | user | P2 | 按 RelayFormatRerank 转发 | FC-091 |
| F-3032 | responses 中转（含 compact 紧凑变体） | user | P1 | 按 RelayFormatOpenAIResponses(Compaction) | FC-093 |
| F-3057 | edits 中转（legacy /v1/edits 编辑端点） | user | P2 | 按 RelayModeEdits 识别并以图像/编辑格式转发 | FC-092 |
| F-3033 | realtime WebSocket 实时中转 | user | P2 | 按 RelayFormatOpenAIRealtime 升级并双向转发 | FC-094 |
| F-3034 | Gemini 原生协议中转（/v1beta/models/*） | user | P1 | 按 RelayFormatGemini 转发 | FC-095 |
| F-3035 | Claude 原生协议中转（/v1/messages） | user | P1 | 按 RelayFormatClaude 转发 | FC-096 |
| F-3036 | 各厂商原生协议适配（37 个 provider adapter 转换） | admin|user | P1 | 经 provider adapter 将统一请求转换为厂商原生协议 | FC-097 |
| F-3037 | Relay 统一错误处理与渠道自动禁用（按状态码重试/禁用） | user|admin | P0 | 按状态码决定重试或禁用渠道并记录错误日志 | FC-086 |
| F-3038 | 生成视频内容网关代理下发（/videos/:task_id/content） | user | P2 | 代理下发上游视频内容（含 gemini 分支） | FC-128 |

## Scalability（3 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5013 | 无状态横扩与共享缓存 | 运维/Root | P0 | 转发实例无本地状态状态走Redis可线性扩展 | NFR-E01 |
| F-5014 | 缓存命中率监控 | 运维/Root | P1 | 监控渠道/令牌/Ability缓存命中率 | NFR-E02 |
| F-5015 | 日志归档与分区 | 运维/Root | P1 | 日志表分区并归档超期数据 | NFR-E03/DC-006 |

## Security（4 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-5006 | 令牌Key掩码与受控取明文 | 用户/开发者及以上 | P0 | 列表默认掩码显示取明文走二次验证受控接口 | NFR-S02 |
| F-5007 | 密钥与支付Secret加密静态存储 | 运维/Root | P0 | 渠道Key与WebhookSecret以AES-256-GCM/KMS密文落库 | NFR-S01/S10 |
| F-5008 | 日志prompt/响应脱敏管线 | 运维/Root | P0 | 对开启正文留存的日志按PII规则脱敏后存储 | NFR-S04/DC-005 |
| F-5009 | 高危操作审计留痕 | 运营/运维/管理员/Root | P0 | 取Key/改倍率/禁渠道/改Option等写审计含前后值 | NFR-S09 |

## 亲和缓存（6 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2029 | 会话亲和键提取与渠道粘连 | system | P0 | 按规则的 model_regex/path_regex/key_sources  | FC-066 |
| F-2030 | 内置 codex/claude CLI header 透传模板 | system | P1 | 对命中规则的请求按 pass_headers 模板透传 CLI 专属 heade | FC-067 |
| F-2031 | 亲和缓存策略配置(SwitchOnSuccess/TTL/MaxEntries/SkipRetryOnFailure) | admin | P1 | 配置缓存开关、成功才切换、默认 TTL、最大条目数、失败是否跳过重试等策略 | FC-068 |
| F-2032 | 清空亲和缓存(全部/按规则名) | admin | P2 | 按 all=true 清空全部或按 rule_name 清空指定规则缓存，返回删 | FC-069 |
| F-2033 | 亲和缓存用量统计查询 | admin | P3 | 按 rule_name+key_fp(+using_group)返回该会话键命中 | FC-069 |
| F-2034 | 亲和命中跳过重试(SkipRetryOnFailure) | system | P1 | 命中 SkipRetryOnFailure 规则的请求失败时不触发跨渠道重试 | FC-068 |

## 令牌管理（12 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3001 | 创建令牌（生成唯一明文 key + 校验额度区间与令牌数量上限） | user | P0 | 生成唯一 sk- 前缀 key 并写入 Token 表 | FC-073 |
| F-3002 | 令牌列表分页查询（key 自动打码） | user | P0 | 返回当前用户令牌分页列表且 key 字段已脱敏 | FC-073 |
| F-3003 | 令牌关键词/前缀搜索（SQL LIKE 注入防护 + 强制截断） | user | P0 | 返回匹配的令牌分页结果 | FC-073 |
| F-3004 | 获取单个令牌明文 key（限流 + 禁缓存保护） | user | P0 | 返回该令牌完整明文 key | FC-074 |
| F-3005 | 批量获取令牌明文 key（数量上限 100） | user | P1 | 返回 id 到 key 的映射 | FC-074 |
| F-3006 | 更新令牌（含仅改状态 status_only 与启用前合法性校验） | user | P0 | 更新 Token 字段或仅状态并返回打码后令牌 | FC-073 |
| F-3007 | 删除令牌 / 批量删除令牌 | user | P1 | 软删除令牌记录并返回删除数量 | FC-073 |
| F-3008 | 令牌额度与过期时间管理（无限额度 / -1 永不过期） | user | P0 | 持久化 RemainQuota/UnlimitedQuota/ExpiredTi | FC-075 |
| F-3009 | 令牌模型白名单（ModelLimits 开关 + 列表） | user | P1 | 限制该令牌仅可访问白名单内模型 | FC-076 |
| F-3010 | 令牌 IP 白名单（AllowIps 多行解析） | user | P1 | 限制该令牌仅可从白名单 IP 调用 | FC-077 |
| F-3011 | 令牌按分组绑定（Group + 跨分组重试开关） | user | P1 | 令牌请求按该分组路由渠道 | FC-078 |
| F-3012 | 令牌用量查询（OpenAI 兼容 credit_summary / token_usage） | user | P1 | 返回额度授予/已用/可用与过期时间 | FC-079 |

## 供应商元数据（1 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3018 | 供应商元数据 CRUD（名称唯一约束） | admin | P1 | 增删改查 Vendor 记录 | FC-081 |

## 公开站点（6 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4039 | 营销首页公开状态聚合（系统名/Logo/页脚/登录方式开关/主题/签到开关等） | anonymous | P1 | 返回首页渲染所需的全站公开配置聚合 | FC-121 |
| F-4040 | 首页对话输入框 Playground 入口（placeholder「问点什么…」导向试用） | anonymous;user | P2 | 引导进入 Playground 对话试用 | FC-122 |
| F-4041 | 用户协议公开页（/agreement 路由渲染协议正文） | anonymous | P1 | 展示用户协议正文页 | FC-123 |
| F-4042 | 隐私政策公开页（/privacy 路由渲染隐私正文） | anonymous | P1 | 展示隐私政策正文页 | FC-124 |
| F-4043 | 公开主题/语言切换控件（页面右上「切换主题」「切换语言」） | anonymous;user | P2 | 切换前端主题或界面语言 | FC-125 |
| F-4044 | 控制台/模型广场/API Keys 主入口（动态域，证据 GAP） | user | P2 | 跳转至控制台动态域入口 | FC-126 |

## 异步任务中心（11 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2001 | 通用异步任务记录写入与状态机流转 | system | P0 | 任务以 NOT_START 落库，随轮询/回调推进至 SUBMITTED/QUE | FC-030 |
| F-2002 | 任务状态 CAS 条件更新(防并发覆盖) | system | P0 | 以 fromStatus 为 WHERE 守卫做条件 UPDATE，赢得更新返回 | FC-030 |
| F-2003 | 用户任务列表分页与多条件过滤查询 | user | P0 | 按 user_id 强制隔离返回本人任务，支持 task_id/action/s | FC-031 |
| F-2004 | 管理端全量任务列表查询 | admin | P1 | 跨用户返回全量任务，支持 channel_id/user_id/user_ids | FC-032 |
| F-2005 | Midjourney 绘图任务提交(imagine/change/blend/describe/modal/shorten/action/edits/video) | user | P0 | 按 action 类型构造 MJ 任务，记录 Action/Prompt 并进入 | FC-033 |
| F-2006 | MJ 任务按 ID 拉取与按条件列表查询 | user | P1 | 按 task_id 拉取单任务进度产物，或按条件批量拉取任务集合 | FC-034 |
| F-2007 | Suno 音乐/歌词任务提交与查询(MUSIC/LYRICS) | user | P0 | 按 SunoAction(MUSIC/LYRICS)提交音乐/歌词生成任务并支持 | FC-035 |
| F-2008 | 视频生成任务提交(Sora/Kling/Vidu/Hailuo/Jimeng/Doubao/Vertex/Gemini) | user | P0 | 按 provider adapter 提交视频任务，记录 PrivateData | FC-036 |
| F-2009 | 任务异步退款与差额结算(计费上下文重算) | system | P0 | 读取 PrivateData.BillingContext 重算实际额度，按 B | FC-037 |
| F-2010 | 任务结果产物展示(ImageUrl/VideoUrl/Buttons) | user | P1 | 返回任务产物地址(图片/视频)与可交互按钮，旧数据 ResultURL 回退到  | FC-038 |
| F-2011 | 扫描超时未完成任务 | system | P1 | 拉取 progress!=100% 且非终态、submit_time 早于 cu | FC-030 |

## 日志与用量（14 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4001 | 管理端全量日志查询（按类型/时间/用户名/令牌名/模型/渠道/分组/请求ID过滤分页） | admin | P0 | 返回符合过滤条件的全量日志分页列表与总数 | FC-101 |
| F-4002 | 用户自助日志查询（仅本人 user_id，按类型/时间/令牌名/模型/分组过滤） | user | P0 | 返回当前登录用户本人的日志分页列表 | FC-101 |
| F-4003 | 按令牌明文 key 查询消费日志（令牌读权限 + 限流 + 禁缓存） | user | P1 | 返回该 token_id 对应的日志列表 | FC-102 |
| F-4004 | 管理端日志统计（消费类 quota/rpm/tpm 汇总） | admin | P1 | 返回过滤条件下 LogTypeConsume 日志的 quota、rpm、tpm | FC-102 |
| F-4005 | 用户自助日志统计（按当前用户名汇总 quota/rpm/tpm） | user | P1 | 返回以当前 username 限定的消费日志聚合 quota/rpm/tpm | FC-102 |
| F-4006 | 清理历史日志（按目标时间戳分批删除，每批上限 100） | admin | P1 | 删除早于 target_timestamp 的日志并返回删除条数 | FC-101 |
| F-4007 | 管理端配额按日数据查询（GetAllQuotaDates，可按 username 过滤） | admin | P1 | 返回时间区间内按日聚合的 QuotaData 列表 | FC-103 |
| F-4008 | 配额数据按用户聚合（GetQuotaDatesByUser，全站用户维度） | admin | P2 | 返回时间区间内按用户分组的配额聚合数据 | FC-103 |
| F-4009 | 用户自助配额按日数据（GetUserQuotaDates，时间跨度上限 1 个月） | user | P1 | 返回本人时间区间内的每日 QuotaData | FC-103 |
| F-4010 | 模型/用量排行榜快照（公开，按 period 周/月） | anonymous;user | P2 | 返回指定 period 的用量排行榜快照 | FC-104 |
| F-4011 | 管理/高危操作审计日志记录（type=LogTypeManage，记录 action+params+操作者+IP） | admin | P0 | 落库一条含 action、英文渲染 content、操作者身份与 client  | FC-105 |
| F-4012 | 用户安全敏感操作审计（passkey 绑定/解绑等，无管理员归属） | user | P1 | 记录归属该用户、无 admin_info 的安全审计日志 | FC-105 |
| F-4013 | 登录审计日志记录（type=LogTypeLogin） | user | P1 | 落库一条 type=LogTypeLogin 的登录审计日志 | FC-105 |
| F-4014 | 渠道亲和缓存用量统计查询 | admin | P2 | 返回 channel_affinity_usage_cache 的统计数据 | FC-105 |

## 模型元数据（5 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3013 | 模型元数据列表（分页 + 供应商计数填充） | admin | P1 | 返回模型元数据分页列表及各供应商模型计数 | FC-080 |
| F-3014 | 模型元数据搜索（关键词 + 供应商过滤） | admin | P1 | 返回匹配的模型元数据分页结果 | FC-080 |
| F-3015 | 模型元数据创建（名称查重 + 刷新定价） | admin | P1 | 写入 Model 表并刷新定价缓存 | FC-080 |
| F-3016 | 模型元数据更新（全量更新 / 仅状态 status_only） | admin | P1 | 更新 Model 记录并刷新定价 | FC-080 |
| F-3017 | 模型元数据删除（刷新定价） | admin | P2 | 删除 Model 记录并刷新定价 | FC-080 |

## 模型同步（3 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3019 | 上游模型同步执行（拉取 basellm 元数据创建缺失模型 + 可选字段覆盖） | admin | P1 | 创建本地缺失模型并按需覆盖字段 | FC-082 |
| F-3020 | 上游模型同步预览（差异对比不落库） | admin | P1 | 返回上游与本地差异供弹窗勾选 | FC-082 |
| F-3021 | 缺失模型检测（渠道引用但无元数据的模型） | admin | P1 | 返回被渠道引用但无 ModelMeta 记录的模型名列表 | FC-083 |

## 模型广场（4 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3022 | 模型排行榜（公开 period 维度快照） | user|guest | P2 | 返回指定周期的模型使用排行快照 | FC-084 |
| F-3023 | 公开价格页（按用户可用分组过滤定价 + 分组倍率） | user|guest | P1 | 返回过滤后的模型定价、供应商、分组倍率与可用分组 | FC-065 |
| F-3024 | 模型广场（DashboardListModels 渠道到模型映射） | user | P2 | 返回 channelId 到模型列表的映射 | FC-085 |
| F-3025 | 用户可见模型列表（按用户分组聚合去重） | user | P1 | 返回该用户所有可用分组下启用模型的去重列表 | FC-085 |

## 渠道管理与上游路由（13 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2016 | 渠道 CRUD/搜索/批量操作 | admin | P0 | 创建/编辑/删除/搜索渠道及批量操作，渠道含 Type/Key/Models/G | FC-042 |
| F-2017 | 渠道连通性测试(单/全量) | admin | P1 | 用 TestModel 或首个 model 发测试请求，记录 TestTime  | FC-043 |
| F-2018 | 渠道上游余额查询与更新 | admin | P2 | 查询上游渠道余额并更新本地记录 | FC-044 |
| F-2019 | 按 tag 批量启用/禁用渠道 | admin | P1 | 按 tag 批量将渠道状态置为启用或手动禁用 | FC-045 |
| F-2020 | 渠道多 Key 模式管理(轮询/随机选 Key) | admin | P1 | 按 MultiKeyMode(随机/轮询)从启用 Key 中选一个，轮询模式用渠 | FC-046 |
| F-2021 | 模型映射/重定向 | admin | P1 | 按 ModelMapping 将请求模型名重定向为上游真实模型名 | FC-047 |
| F-2022 | 状态码映射 | admin | P2 | 按 StatusCodeMapping 将上游状态码改写为对外状态码 | FC-048 |
| F-2023 | 渠道自动禁用(AutoBan)与触发条件 | system | P0 | 满足自动禁用条件时将渠道置为自动禁用状态并通知 root 用户 | FC-049 |
| F-2024 | 渠道自动恢复(AutoEnable)条件 | system | P1 | 满足条件时将自动禁用渠道恢复启用并通知 root | FC-050 |
| F-2025 | 参数/请求头覆写(ParamOverride/HeaderOverride) | admin | P2 | 按 ParamOverride 覆写请求体参数、按 HeaderOverride | FC-050 |
| F-2026 | 上游模型探测与同步(fetch_models / detect & apply) | admin | P2 | 探测上游支持的模型列表并预览/应用到渠道 Models | FC-051 |
| F-2027 | Ollama 模型管理(pull/delete/version) | admin | P3 | 对 Ollama 渠道执行模型拉取/删除/查版本 | FC-052 |
| F-2028 | 优先级+权重随机路由选渠道 | system | P0 | 在满足(分组+模型)的渠道中按 Priority 分层、同层按 Weight 加 | FC-053 |

## 渠道运维（1 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4045 | Codex 渠道上游用量查询（解析 OAuth key + 401/403 自动刷新 token 重试） | admin | P1 | 返回该 Codex 渠道上游 wham 用量数据 | FC-127 |

## 计费与额度（11 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2038 | 倍率计费(model_ratio×group_ratio×completion_ratio) | system | P0 | 按 模型倍率×分组倍率×补全倍率 计算消耗额度，补全 token 单独乘 com | FC-055 |
| F-2039 | 缓存倍率计费(cache_ratio/exposed_cache) | system | P1 | 对缓存读/缓存创建 token 按 cache_ratio 单独计价，可对外暴露 | FC-056 |
| F-2040 | 表达式/阶梯计费(billingexpr 变量与函数) | admin | P1 | 用单条表达式定义全部计价逻辑，支持 p/c/cr/cc/img/ai/ao/le | FC-057 |
| F-2041 | 阶梯计费结算(TryTieredSettle 用冻结快照) | system | P0 | 读取请求前冻结的 TieredBillingSnapshot 用真实 token | FC-058 |
| F-2042 | 预扣额度(pre-consume)与多退少不补 | system | P0 | 按估算 token 校验并冻结额度，请求失败全额返还，成功后按真实 token  | FC-059 |
| F-2043 | 倍率配置管理与同步(ratio_config/ratio_sync) | admin | P2 | 管理模型/分组/缓存倍率配置并支持从远端同步 | FC-060 |
| F-2044 | 充值与余额查询(多支付渠道) | user | P1 | 通过支持的支付渠道充值并写入额度，查询当前余额 | FC-061 |
| F-2045 | 兑换码(Redemption 单个/批量/过期) | admin/user | P2 | 管理员按 Quota 生成单个或批量兑换码，用户兑换后额度入账，过期码不可用 | FC-062 |
| F-2046 | 订阅计划与订单(SubscriptionPlan/Order/UserSubscription) | user | P1 | 创建订阅订单并生成 UserSubscription，状态在 active/ex | FC-063 |
| F-2047 | 订阅预扣/退款记录(SubscriptionPreConsumeRecord) | system | P1 | 按 BillingSource=subscription 从订阅项预扣额度并记录 | FC-064 |
| F-2048 | 公开价格页/模型价格暴露 | visitor | P2 | 按 expose_ratio 配置对外暴露各模型价格(含可暴露缓存倍率) | FC-065 |

## 跨分组重试（3 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2035 | auto 分组逐组耗尽优先级后切下一组重试 | system | P0 | 在 auto 分组下逐组从当前优先级选满足渠道，当前组优先级用尽(返回 nil) | FC-070 |
| F-2036 | 令牌级跨分组重试开关(仅 auto 分组有效) | user | P1 | 开启后当前组优先级耗尽即准备切下一组，本次仍用当前组，下次重试用下一组 | FC-071 |
| F-2037 | 全局重试次数配置(RetryTimes) | admin | P1 | 以 common.RetryTimes 作为单分组内优先级降级/重试上限 | FC-072 |

## 运营与运维（23 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-4015 | 系统初始化状态查询（GetSetup，未初始化返回 root 是否存在与数据库类型） | anonymous | P0 | 返回 setup 状态、root_init、database_type | FC-106 |
| F-4016 | 系统初始化提交（PostSetup，创建 root 账号 + 设置自用/演示模式 + 写 setup 记录） | anonymous | P0 | 创建 root 用户、持久化模式开关并标记 constant.Setup=tru | FC-106 |
| F-4017 | 全站选项列表查询（GetOptions，自动剔除敏感键 + 注入补全倍率元信息） | root | P0 | 返回过滤敏感键后的 option 列表并附加 CompletionRatioMe | FC-107 |
| F-4018 | 全站选项更新（UpdateOption，含逐键合法性校验与依赖检查） | root | P0 | 校验通过后落库该选项并记录 option.update 审计 | FC-107 |
| F-4019 | 性能统计查询（缓存/内存/磁盘/GC/Goroutine 与监控阈值配置） | root | P1 | 返回磁盘缓存、内存、磁盘空间、Goroutine 数与监控配置的聚合统计 | FC-108 |
| F-4020 | 清理不活跃磁盘缓存（删除 10 分钟以上未使用缓存文件） | root | P2 | 删除超过 10 分钟未使用的磁盘缓存文件 | FC-108 |
| F-4021 | 强制执行 GC / 重置性能统计计数 | root | P2 | 执行 runtime.GC() 或重置磁盘缓存统计计数 | FC-108 |
| F-4022 | 日志文件列表查询（仅 oneapi-*.log，含总大小与最早/最新时间） | root | P2 | 返回日志目录下日志文件列表、文件数、总大小、最旧/最新时间 | FC-108 |
| F-4023 | 清理过期日志文件（by_count 保留最新 N 个 / by_days 删早于 N 天，跳过活动日志） | root | P2 | 删除符合条件的日志文件并返回删除数与释放字节 | FC-108 |
| F-4024 | 性能指标汇总查询（公开 summary，按 hours + 活动分组聚合） | anonymous;user | P2 | 返回近 N 小时各活动分组的性能指标汇总 | FC-109 |
| F-4025 | 单模型性能指标查询（按 model 必填 + group + hours，过滤非活动分组） | anonymous;user | P2 | 返回该模型按分组的性能指标且仅保留活动分组 | FC-109 |
| F-4026 | Uptime-Kuma 状态接入（并发拉取多组状态页 + 心跳，聚合可用率/状态） | anonymous | P2 | 返回各监控组的 monitor 列表（名称/可用率/状态/分组） | FC-110 |
| F-4027 | 用户协议公开内容读取 | anonymous | P1 | 返回系统配置的用户协议文本 | FC-111 |
| F-4028 | 隐私政策公开内容读取 | anonymous | P1 | 返回系统配置的隐私政策文本 | FC-111 |
| F-4029 | 公告/关于/首页内容公开读取 | anonymous | P2 | 分别返回 OptionMap 中 Notice/About/HomePageCo | FC-111 |
| F-4030 | 支付合规声明确认（仅会话鉴权，写合规版本/时间/操作者/IP） | root | P0 | 落库 5 个 payment_setting.compliance_* 选项并返 | FC-112 |
| F-4031 | 控制台旧设置迁移（ApiInfo/Announcements/FAQ/UptimeKuma 旧键转 console_setting.*） | root | P2 | 将旧 option 键转换为 console_setting.* 新结构并删除旧 | FC-113 |
| F-4032 | 模型请求限流分组配置（按分组设置 [count,duration] 上限并校验） | root | P1 | 校验 JSON 后保存分组限流配置 | FC-114 |
| F-4033 | 敏感词内容过滤配置（启用开关 + 换行分隔词表） | root | P2 | 启用开关与敏感词列表生效于 prompt 检查 | FC-115 |
| F-4034 | 用户可用分组与自动分组配置（auto_group 列表 + 默认启用开关） | root | P1 | 保存自动分组列表与 DefaultUseAutoGroup 开关 | FC-116 |
| F-4035 | 前端主题切换配置（default / classic 二选一，写入即生效） | root | P1 | 校验主题值合法后保存 theme.frontend | FC-117 |
| F-4036 | 后端多语言解析与切换（zh-CN/zh-TW/en，按用户设置或 Accept-Language） | user;anonymous | P2 | 按用户语言设置或 Accept-Language 头返回对应语言文案 | FC-118 |
| F-4037 | 额度预警通知配置（email/webhook/bark 三渠道 + 预警阈值，正数校验） | user | P1 | 保存通知类型、对应渠道参数与额度预警阈值 | FC-119 |

## 部署管理（18 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-3039 | io.net 部署集成开关与设置查询 | admin | P1 | 返回 io.net 启用状态/是否配置 api_key/可连通性 | FC-098 |
| F-3040 | io.net 连接测试（校验 api_key + 返回硬件可用量） | admin | P1 | 用 EnterpriseClient 验证 key 并返回硬件数与可用量 | FC-098 |
| F-3041 | 部署列表查询与状态计数（分页 + 状态聚合） | admin | P1 | 返回 io.net 部署分页列表及各状态计数 | FC-099 |
| F-3042 | 部署搜索（按状态过滤 + 名称关键词本地过滤） | admin | P2 | 返回过滤后的部署列表 | FC-099 |
| F-3043 | 部署详情查询（GPU/容器/计算分钟/地域） | admin | P1 | 返回单个部署的硬件/容器/计算用量明细 | FC-100 |
| F-3044 | 创建部署（DeployContainer 下发容器） | admin | P1 | 向 io.net 下发容器部署请求 | FC-098 |
| F-3045 | 部署更新（UpdateDeployment 配置变更） | admin | P2 | 提交 io.net 部署更新请求 | FC-100 |
| F-3046 | 部署重命名（名称可用性预检） | admin | P2 | 校验新名称可用后更新部署名 | FC-100 |
| F-3047 | 部署续期（ExtendDeployment 延长计算时长） | admin | P2 | 延长部署计算分钟并返回更新后详情 | FC-100 |
| F-3048 | 删除部署（请求终止） | admin | P2 | 向 io.net 发送终止请求 | FC-100 |
| F-3049 | 硬件类型查询（含总可用量统计） | admin | P1 | 返回可用硬件类型列表与总可用量 | FC-099 |
| F-3050 | 部署地域查询 | admin | P2 | 返回可用部署地域列表 | FC-099 |
| F-3051 | 可用副本查询（按硬件 ID + GPU 数） | admin | P2 | 返回指定硬件与 GPU 数下的可用副本 | FC-099 |
| F-3052 | 部署价格预估 | admin | P1 | 返回 io.net 价格预估结果 | FC-100 |
| F-3053 | 集群名称可用性查询 | admin | P2 | 返回该名称是否可用 | FC-100 |
| F-3054 | 部署容器列表（含容器事件） | admin | P2 | 返回容器列表及每容器事件时间线 | FC-100 |
| F-3055 | 容器详情查询 | admin | P2 | 返回容器硬件/状态/事件详情 | FC-100 |
| F-3056 | 容器日志查询（按级别/流/游标分页 + 时间范围） | admin | P2 | 返回容器原始日志（支持过滤与分页） | FC-100 |

## 预填分组（4 项）

| ID | 功能 | 角色 | 优先级 | 核心结果 | 来源 |
|---|---|---|---|---|---|
| F-2012 | 预填分组创建(model/tag/endpoint 三类型) | admin | P1 | 校验 name+type 非空且名称不重复后创建预填分组，Items 以 JSO | FC-039 |
| F-2013 | 预填分组更新与名称冲突校验 | admin | P2 | 校验存在 id 且新名称不与他组冲突后更新分组内容 | FC-039 |
| F-2014 | 预填分组下拉填充渠道/令牌配置 | admin | P2 | GET /api/prefill_group?type=xxx 按类型返回分组列 | FC-040 |
| F-2015 | 预填分组软删除 | admin | P2 | 按 id 软删除分组，保留历史不物理移除 | FC-041 |
