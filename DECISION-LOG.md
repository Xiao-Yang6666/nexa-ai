# DECISION-LOG — Slice A 供应商账号化（r6a-account）

## D1：`com/nexa/account/` 已被「用户账号(User)」域占用 → 供应商账号落 `account/provider/**`
任务书假设 `com/nexa/account/` 是空的四层文件夹，实测**已存在完整的用户账号域**（User/OAuth/login/register 等 80+ 文件）。
「供应商账号(Account)」与「用户账号(User)」语义不同但都叫 account，直接同包会命名/语义冲突。
**决策**：供应商账号聚合落子包 `com.nexa.account.provider.**`（domain/application/infrastructure/interfaces 四层自带），
不碰任何既有 User 域文件，满足「只在 com/nexa/account/** 内新增」的硬约束。
可逆：纯新增子包，零改动既有文件。

## D2：AccountGroup 建模为「Account 聚合内的关联集合」而非独立聚合
任务书允许二选一。选关联集合：Account 持有 `List<AccountGroupRef>(groupId, priority)`，
仓储 save 时按 account_id fan-out 到 `account_groups` 表（仿 channel→abilities 的 fan-out/fan-in），
findById/findPage 回读组装。避免独立聚合带来的 AccountGroup 仓储/用例「建了没人用」的半成品死代码。
account_groups 表仍按任务建（复合主键 account_id+group_id）。
可逆：如未来需独立管理 group，可抽出独立聚合，迁移成本低。

## D3：时间字段统一 epoch 秒 Long（对齐 channel 现网习惯）
sub2api 参考表用 timestamptz，但任务明确「nexa 习惯用 epoch 秒 Long，看 channel 怎么存」。
故 rateLimitedAt/rateLimitResetAt/overloadUntil/expiresAt/createdTime/updatedTime 全部 BIGINT(epoch 秒)。

## D4：status 用 String 码（active/disabled/rate_limited），对齐参考表 varchar(20)
状态枚举 AccountStatus 持久化为字符串码（参考 sub2api status varchar(20) default 'active'），
区别于 channel 的 int 码——状态语义不同域可各自选择，此处与权威参考表对齐。

## D5：credentials 安全——视图 DTO 完全不回显
credentials 承载敏感 JSON。AccountView **不含任何 credentials 字段**（仿 channel key 脱敏即「不下发」）。
编辑时 credentials 传 null/空白 = 保留原值（避免脱敏回显后回写空值清空凭证，同 channel.update 的 key 处理）。

## D6：前端 feature 命名 `provider-account`，路由 `/admin/provider-accounts`
前端 `src/features/account` 已被用户账号域占用（与后端 D1 同源冲突）。供应商账号前端域独立命名
`features/provider-account`，路由 `/admin/provider-accounts`，nav 入口归「资源管理」组（minRole=ADMIN，
紧邻渠道管理），与后端 `@RequireRole(ADMIN)` 对齐。本地定义 AccountView 等 TS 类型（不走 openapi 生成的
shared/api 契约类型，因后端新端点尚未纳入 openapi.yaml），布局复用渠道管理（筛选/表格/分页/抽屉）。
可逆：纯新增 feature 目录 + 路由 + 一行 nav，零改动既有页面。
