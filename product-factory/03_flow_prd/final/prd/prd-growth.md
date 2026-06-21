# PRD — 增长：签到 + 邀请返利分销（FL-growth）

> 分片：签到 D2 + 邀请返利分销 D3。对应流程图 `flow/FL-growth.md`、状态矩阵 `PAGE-STATE-MATRIX.md §C`。
> 数据对象字段一律复用 `DATA-MODEL.md §10 Checkin / §1 User`。
> 本片覆盖功能 ID：**F-1039 / F-1040 / F-1041 / F-1042 / F-1043 / F-1044 / F-1045 / F-1046 / F-1047 / F-1048 / F-1049 / F-1050**。

---

## GR-1 每日签到状态机（未签到→签到→已签到）

- **功能 ID / 优先级**：F-1046、F-1048、F-1050 / P0
- **来源**：FC-019/FC-021/FC-023（`controller/checkin.go DoCheckin`、`model/checkin.go UserCheckin`、唯一索引 `idx_user_checkin_date`、事务、前置 `TurnstileCheck`）
- **角色 / Owner**：登录用户 / 系统；Owner 模块 = 签到
- **触发**：登录用户在签到页点「签到」（前置过 C4 Turnstile）

### 1. 场景
登录用户每天可领一次随机额度奖励。这是一个日级状态机：当日首次签到发放 `[MinQuota,MaxQuota]` 随机额度并同步增 `quota`；同日重复签到被唯一索引+事务拦截；跨日 0 点复位回未签到。签到前必须过人机校验。

### 2. 前置条件
- 用户已登录（需登录）。
- `CheckinSetting.Enabled=true`（关闭则接口直接返回未启用）。
- 当日 `(user_id, checkin_date)` 无记录。
- 签到请求过 `TurnstileCheck` 中间件。

### 3. 主流程（对应 GR-1 状态机迁移）
1. `Enabled=true 且 当日无记录` → 今日未签到态。
2. 用户点「签到」→ 人机校验（Turnstile）。
3. Turnstile 通过 → 写入中（loading）。
4. 事务成功：发 `[MinQuota,MaxQuota]` 随机额度、`user.quota++`（增 quota_awarded）、写 Checkin 记录 → 今日已签到态。
5. 跨日 0 点 → 复位回今日未签到态。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `CheckinSetting.Enabled=false` | [*]→未启用 | 返回未启用错误（终态） | 「签到功能未启用」 |
| Turnstile 失败 | 人机校验→今日未签到 | 拦截，不进入签到逻辑（回退） | 人机校验失败回退态 |
| 同日重复签到（并发） | 写入中→今日已签到 | 被 `idx_user_checkin_date` 唯一索引+事务拦截 | 「今日已签到」 |
| 已签到后再点 | 今日已签到→今日已签到 | 不再发额度 | 按钮置灰，「明日再来」 |

### 5. 数据对象（复用 DATA-MODEL §10 / §1）
- **写** `Checkin`：`UserId(user_id)` + `CheckinDate(checkin_date)`（`YYYY-MM-DD`，复合唯一 `idx_user_checkin_date`）、`QuotaAwarded(quota_awarded)`（`[MinQuota,MaxQuota]` 随机）、`CreatedAt(created_at)`。
- **写** `User.Quota(quota)`：增加 `QuotaAwarded`（同步增余额）。
- **读** `CheckinSetting`：`Enabled(enabled)`（默认 false）、`MinQuota(min_quota)`（默认 1000）、`MaxQuota(max_quota)`（默认 10000）。
- **原子性**：MySQL/PG 用事务，SQLite 用顺序操作+手动回滚，保证「记录写入」与「额度增加」原子。

### 6. 验收标准
- [ ] `Enabled=false` 时调用签到 → 返回「签到功能未启用」，不写 Checkin、不增 quota。
- [ ] 当日首签 → 写入一条 `(user_id, today)` 记录，`quota_awarded ∈ [MinQuota,MaxQuota]`，`user.quota` 精确增加 `quota_awarded`。
- [ ] 同一用户同日第二次签到 → 返回「今日已签到」，不重复发额度、不新增记录。
- [ ] 并发两次签到同一用户同日 → 仅一笔成功，另一笔被唯一索引拦截返回「今日已签到」，quota 只增一次。
- [ ] Turnstile 校验失败 → 请求被拦截，不进入签到逻辑、不发额度。

### 7. 所触及页面状态（对齐 §C「每日签到」）
今日未签到态（按钮高亮）· 签到写入中态（loading）· 签到成功态（展示随机额度+新余额）· 今日已签到态（置灰）· 人机校验失败回退态 · 并发重复拦截态 · 人机校验进行态（C4）· 签到未启用态。

---

## GR-2 签到记录与累计统计查询

- **功能 ID / 优先级**：F-1047 / P1
- **来源**：FC-020（`controller/checkin.go GetCheckinStatus`、`model/checkin.go GetUserCheckinStats`）
- **角色 / Owner**：登录用户；Owner 模块 = 签到
- **触发**：用户进入签到页或带 `?month=` 查询本月统计

### 1. 场景
用户在签到页看到本月签到日历及累计统计（累计领取额度、累计签到次数、本月已签数、今日是否已签）。数据获取流，无控制分支，返回的记录脱敏不含 id/user_id。

### 2. 前置条件
- 用户已登录。
- `CheckinSetting.Enabled=true`（否则返回未启用）。
- 请求可带 `month` 参数（不带默认本月）。

### 3. 主流程（对应 GR-2 节点 C0→V/EMPTY）
1. 进入签到页（C0），校验签到功能启用（C1）。
2. 启用 → 请求本月签到统计，脱敏（C2）。
3. 加载骨架态（C3）。
4. 判本月有签到记录（C4）：无 → 本月空记录态（引导首签，EMPTY）；有 → 日历热力态 + 累计额度/连签数卡片（V）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `Enabled=false` | C1-否 | 返回未启用 | 未启用空壳态 |
| 本月无记录 | C4-否 | 返回空统计 | 本月空记录态 + 引导首签 |

### 5. 数据对象（复用 DATA-MODEL §10）
- **读脱敏** `CheckinRecord`：仅 `checkin_date`、`quota_awarded`（**不含** `id`/`user_id`）。
- **统计返回字段**：`total_quota`、`total_checkins`、`checkin_count`、`checked_in_today`。
- **查询条件**：`GetUserCheckinRecords(userId, startDate, endDate)`，`checkin_date` 在本月范围，`checkin_date DESC`。

### 6. 验收标准
- [ ] `Enabled=false` → 返回未启用错误，不返回记录。
- [ ] 返回的每条记录只含 `checkin_date` 和 `quota_awarded`，断言响应里不含 `id`、`user_id` 字段。
- [ ] 本月无签到 → `checkin_count=0` 且返回空记录态。
- [ ] 已签到 N 天 → `checkin_count=N`、`total_quota` 等于这 N 条 `quota_awarded` 之和。
- [ ] 今日已签 → `checked_in_today=true`；今日未签 → `false`。

### 7. 所触及页面状态（对齐 §C「签到统计」）
日历热力态 · 加载骨架态 · 本月空记录态（引导首签）· 累计统计卡片态（total_quota/total_checkins/checkin_count/checked_in_today）· 签到未启用空壳态。

---

## GR-3 签到开关与额度区间配置（管理端）

- **功能 ID / 优先级**：F-1049 / P1
- **来源**：FC-022（`setting/operation_setting/checkin_setting.go`，默认 Enabled=false/Min=1000/Max=10000）
- **角色 / Owner**：管理员；Owner 模块 = 签到
- **触发**：管理员进入签到设置修改 `Enabled / MinQuota / MaxQuota`

### 1. 场景
管理员配置签到功能：是否开启、奖励额度区间。配置校验型——区间必须合法（Min≤Max 且都 ≥0），保存后立即影响签到接口行为。

### 2. 前置条件
- 操作者具备管理端权限。
- 读取当前 `checkin_setting` 回显。

### 3. 主流程（对应 GR-3 节点 S0→SON/SOFF）
1. 管理员进入签到设置，读取当前 `checkin_setting`（S0→S1）。
2. 修改 `Enabled / MinQuota / MaxQuota`（S2）。
3. 校验 `Min <= Max`（S3）。
4. 校验 `Min/Max >= 0`（S4）。
5. 保存配置（S5）→ 按 `Enabled` 切换：true→签到已开启态、区间生效（SON）；false→签到已关闭、接口返回未启用（SOFF）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `Min > Max` | S3-否 | 拒绝保存 | 区间非法拒绝态 |
| `Min < 0` 或 `Max < 0` | S4-否 | 拒绝保存 | 额度为负拒绝态 |
| `Enabled` 切为 false | S6-false | 保存后签到接口返回未启用 | 签到已关闭态 |

### 5. 数据对象（复用 DATA-MODEL §10 CheckinSetting）
- **读/写** `CheckinSetting`：`Enabled(enabled) bool`（默认 false）、`MinQuota(min_quota) int`（默认 1000）、`MaxQuota(max_quota) int`（默认 10000）。
- 落库为 `Option` KV（`model/option.go`）。

### 6. 验收标准
- [ ] 设 `Min=5000 Max=1000`（Min>Max）→ 拒绝保存，配置不变。
- [ ] 设 `Min=-1` → 拒绝保存（额度为负）。
- [ ] 设 `Enabled=false` 保存 → 签到接口（GR-1）返回「签到功能未启用」。
- [ ] 设 `Min=2000 Max=8000 Enabled=true` 保存 → 后续签到 `quota_awarded ∈ [2000,8000]`。

### 7. 所触及页面状态（对齐 §C「签到配置（管理端）」）
配置读取态（回显当前值）· 区间非法拒绝态（Min>Max）· 额度为负拒绝态 · 签到已开启态（区间生效）· 签到已关闭态。

---

## GR-4 邀请返利分销全链（生成邀请码→被注册→返利入账）

- **功能 ID / 优先级**：F-1039、F-1040、F-1041、F-1042、F-1043 / P0（F-1041/F-1043 P1）
- **来源**：FC-024~FC-027（`GetAffCode`、`Register` 解析 InviterId、`oauth.go session.Get(aff)`、`model/user.go:344-347` 邀请人奖励、`QuotaForNewUser`）
- **角色 / Owner**：邀请人 + 被邀请人 + 系统；Owner 模块 = 邀请返利
- **触发**：邀请人取邀请码 → 被邀请人带 aff_code 注册 → 系统回调发放返利

### 1. 场景
跨两个主体的返利状态流：邀请人拿到自己唯一的 4 位 `aff_code`/邀请链接；被邀请人通过邮箱或 OAuth 链路带 aff_code 注册，系统解析出 `InviterId`；新用户创建完成后回调，使邀请人 `AffCount++`、`AffQuota/AffHistoryQuota += QuotaForInviter`，新用户初始额度叠加 `QuotaForNewUser`。仅 inviterId 有效时发放。

### 2. 前置条件
- 邀请人已登录（取码）。
- 被邀请人为新注册用户。
- `RegisterEnabled=true`（注册链路前提，见账号 PRD）。

### 3. 主流程（对应 GR-4 节点 I0→OK）
1. 邀请人进入邀请页（I0），判是否已有 `aff_code`（I1）：无→生成唯一 4 位随机码落库（I2）；有→返回已有码（I3）。
2. 展示邀请码/邀请链接（I4），被邀请人收到带 aff_code 的链接（I5）。
3. 按注册渠道（I6）：邮箱→注册请求携 aff_code（I7）；OAuth→`/oauth/state?aff=xxx` 暂存 session（I8），回调从 session 取 aff（I9）。
4. 解析 aff_code → inviterId（I10）：有效→新用户 `InviterId=邀请人`、初始额度叠加 `QuotaForNewUser`（I12）。
5. 回调：邀请人 `AffCount++`（I13）、`AffQuota += QuotaForInviter`、`AffHistoryQuota += QuotaForInviter`（I14）→ 返利已入账态（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| aff_code 无效/空，解析不出 inviterId | I10-否/空 | `InviterId=0`，不发返利，新用户按默认初始额度 | 无返利态（无归因） |
| OAuth 渠道 session 无 aff | I9（取不到） | 视同无效，`InviterId=0` | 无归因 |
| inviterId 有效 | I10-是 | 双侧奖励发放 | 邀请人统计 +1、新用户额度叠加 |

### 5. 数据对象（复用 DATA-MODEL §1 User）
- **邀请人写** `User.AffCode(aff_code)`（`varchar(32);uniqueIndex`，无则生成 4 位）、`User.AffCount(aff_count)`（++）、`User.AffQuota(aff_quota)`（+= `QuotaForInviter`）、`User.AffHistoryQuota(aff_history_quota)`（+= `QuotaForInviter`）。
- **被邀请人写** `User.InviterId(inviter_id)`（=邀请人 Id，无效则 0）、`User.Quota(quota)`（叠加 `QuotaForInviter` 之外的 `QuotaForNewUser` 初始额度）。
- **常量** `QuotaForInviter`、`QuotaForNewUser`。

### 6. 验收标准
- [ ] 邀请人首次进邀请页 → 生成一个全局唯一的 4 位 `aff_code` 并落库；再次进入返回同一个码。
- [ ] 被邀请人带有效 aff_code 注册 → 新用户 `inviter_id` = 邀请人 Id（非 0）。
- [ ] 带无效/空 aff_code 注册 → 新用户 `inviter_id=0`，邀请人 `aff_count`/`aff_quota` 不变。
- [ ] 有效归因后 → 邀请人 `aff_count` 加 1、`aff_quota` 增加 `QuotaForInviter`、`aff_history_quota` 同步增加。
- [ ] OAuth 链路 `/oauth/state?aff=X` 注册后从 session 取到 aff → 归因生效；session 无 aff → 不归因。

### 7. 所触及页面状态（对齐 §C「邀请返利全链」）
邀请页生成码态 · 邀请页已有码态 · 无效/空 aff_code 无返利态 · 被邀请人归因成功态 · 返利入账态（AffCount++/AffQuota 增）。

---

## GR-5 邀请额度划转为可用额度 + 邀请统计展示

- **功能 ID / 优先级**：F-1044、F-1045 / P0（F-1045 P1）
- **来源**：FC-028/FC-029（`controller/user.go TransferAffQuota`、`model TransferAffQuotaToQuota`、`payment_compliance`、`GET /api/user/self`）
- **角色 / Owner**：邀请人（登录用户）；Owner 模块 = 邀请返利
- **触发**：邀请人查看邀请统计并提交划转额度 `POST /self/aff_transfer`

### 1. 场景
邀请人把累积的邀请奖励额度（AffQuota）划转为可直接调用的可用额度（Quota）。这是带双重前置校验的资金动作：金额不低于最小单位、AffQuota 充足，且需通过支付合规校验，才原子执行 `aff_quota -= quota`、`quota += quota`。同时个人信息接口展示邀请三项统计。

### 2. 前置条件
- 用户已登录（复用 C5 self-scope，仅操作本人）。
- 已展示 `AffCount/AffQuota/AffHistoryQuota`。
- 划转金额 `quota >= QuotaPerUnit` 且 `AffQuota` 充足。

### 3. 主流程（对应 GR-5 节点 T0→OK）
1. 邀请人查看可划转额度，展示 `AffCount/AffQuota/AffHistoryQuota`（T0→T1）。
2. 输入要划转的 quota 数额（T2）。
3. 校验 `quota >= QuotaPerUnit`（T3）。
4. 校验 `AffQuota` 充足（T4）。
5. 通过 `payment_compliance` 校验（T5）。
6. 原子执行 `aff_quota -= quota`、`quota += quota`（T6）→ 划转成功、双侧刷新（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `quota < QuotaPerUnit` | T3-否 | 拒绝，不划转 | 「转移额度最小为 {QuotaPerUnit}」最小额度错 |
| `AffQuota < quota`（不足） | T4-否 | 拒绝，不划转 | 邀请额度不足拒绝态 |
| `payment_compliance` 未过 | T5-否 | 拒绝，不划转 | 合规校验未过拒绝态 |

### 5. 数据对象（复用 DATA-MODEL §1 User）
- **读展示** `User.AffCount(aff_count)`、`User.AffQuota(aff_quota)`、`User.AffHistoryQuota(aff_history_quota)`（个人信息接口 `GET /self` 返回三项）。
- **写划转** `User.AffQuota(aff_quota)`（-= quota）、`User.Quota(quota)`（+= quota），原子执行。
- **常量** `QuotaPerUnit`（最小划转单位）。

### 6. 验收标准
- [ ] 提交 `quota < QuotaPerUnit` → 拒绝 + 最小额度错，`aff_quota`/`quota` 都不变。
- [ ] 提交 `quota > AffQuota` → 拒绝（邀请额度不足），双侧不变。
- [ ] `payment_compliance` 不通过 → 拒绝，双侧不变。
- [ ] 合法划转 100 → `aff_quota` 精确减 100、`quota` 精确增 100，操作原子（要么都生效要么都不变）。
- [ ] `GET /self` 返回 `aff_count`、`aff_quota`、`aff_history_quota` 三项数值。

### 7. 所触及页面状态（对齐 §C「邀请额度划转」）
邀请统计展示态（C5）· 输入划转额态 · 低于最小单位拒绝态 · 邀请额度不足拒绝态 · 合规校验未过态 · 划转成功态（双侧刷新）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-1039 | GR-4 |
| F-1040 | GR-4 |
| F-1041 | GR-4 |
| F-1042 | GR-4 |
| F-1043 | GR-4 |
| F-1044 | GR-5 |
| F-1045 | GR-5 |
| F-1046 | GR-1 |
| F-1047 | GR-2 |
| F-1048 | GR-1 |
| F-1049 | GR-3 |
| F-1050 | GR-1 |

无 `[BLOCKER]`。
