# BUSINESS-SUBJECTS（业务主体/对象/关系）

## 角色主体

| 角色 | 说明 |
|---|---|
| 访客 | 未登录,可浏览公开站/价格/协议 |
| 用户(开发者) | 注册后自助建Key调用,看用量计费,充值续费 |
| 运营 | 配置模型广场/价格展示/营销内容 |
| 运维 | 渠道健康/部署/监控/系统设置 |
| 管理员 | 渠道/模型/计费/用户管理 |
| Root | 全权+系统初始化+高危操作 |

## 业务对象主体

| 对象 | 核心字段(见DATA-MODEL) | 关键状态 |
|---|---|---|
| User | id,username,role,group,quota,aff_code | 正常/封禁 |
| Token | id,key,remain_quota,status,expired_time | 启用/禁用/过期/耗尽 |
| Channel | id,type,key,weight,priority,status | 启用/手动禁用/自动禁用 |
| Order/充值 | id,amount,status,callback | 待支付/已支付/失败 |
| Subscription | id,status,end_time | active/cancelled/expired |
| Task | task_id,platform,status,progress | NOT_START/进行中/SUCCESS/FAILURE |
| Redemption | id,key,quota,status | 未用/已用 |

## 外部系统主体

- 上游 LLM provider(52个,OpenAI/Claude/Gemini/Bedrock等)
- 支付网关(Stripe/Creem/ePay/Waffo等6个)
- OAuth(GitHub/Discord/OIDC/LinuxDO) + Telegram Bot
- io.net(部署)、Uptime-Kuma(监控)、Turnstile(人机)

## 主体关系

User 1:N Token；Token 调用经 Relay 选 Channel；Channel 计费写 Quota 扣减 + Log 记录；Order/Redemption 充值 Quota；Subscription 影响计费策略。