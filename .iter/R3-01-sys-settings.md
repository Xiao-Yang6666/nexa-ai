# SLICE R3-01: 系统配置面板接通（SysSettings 8 处 api-missing）

## 背景（已调查清楚，勿推翻）
- 后端 `PUT /api/option/` 是**直通语义**——任意键覆盖写入（OptionRegistry.validate 只对 theme/限流/合规键做特殊校验，其余直通）。**后端不需要"补键"**。
- 前端 8 处标 `TODO(api-missing)` 的真因：**契约没规定这些配置该用什么键名**，前端怕臆造键名。
- 所以本 slice 的真正工作 = **定键名契约 + 前端按键名接 GET/PUT + 给有约束的键加领域校验**。

## 键名契约（本 slice 权威，前后端都按这个）
| 配置项 | 键名 | 值类型 | 校验 |
|---|---|---|---|
| 站点描述 | `site.description` | string | 直通（≤500字，前端限长即可） |
| 邀请码注册开关 | `register.invite_only` | "true"/"false" | 布尔字符串 |
| 计费货币 | `billing.currency` | 枚举 CNY/USD | 白名单校验 |
| 默认 RPM 限制 | `ratelimit.default_rpm` | 非负整数字符串 | 整数≥0 |
| 默认 TPM 限制 | `ratelimit.default_tpm` | 非负整数字符串 | 整数≥0 |
| SMTP 服务器 | `smtp.host` | string | 直通 |
| SMTP 端口 | `smtp.port` | 整数字符串 | 整数 1-65535 |
| SMTP 账号 | `smtp.username` | string | 直通 |
| SMTP 密码 | `smtp.passwordSecret` | string | **键名以 Secret 结尾→敏感键，GET 列表自动剔除值**（复用 OptionKey.isSensitive） |
| SMTP TLS | `smtp.tls` | "true"/"false" | 布尔字符串 |
| 2FA 强制 | `security.force_2fa` | "true"/"false" | 布尔字符串 |
| 账号锁定阈值 | `security.lockout_threshold` | 非负整数字符串 | 整数≥0 |
| 会话有效期(分钟) | `security.session_ttl_minutes` | 正整数字符串 | 整数≥1 |
| IP 白名单 | `security.ip_whitelist` | 逗号分隔 string | 直通 |
| 维护模式 | `advanced.maintenance_mode` | "true"/"false" | 布尔字符串 |
| 调试日志 | `advanced.debug_log` | "true"/"false" | 布尔字符串 |
| 请求超时(秒) | `advanced.request_timeout_sec` | 正整数字符串 | 整数≥1 |
| 日志保留(天) | `advanced.log_retention_days` | 非负整数字符串 | 整数≥0 |

> 注：高级选项里的"缓存清理/重置统计"是**操作**不是配置，本 slice 不做（需专用端点，标注 TODO 留下轮）。

## 后端工作（最小）
1. 在 `OptionRegistry.validate` 增对上述有约束键的校验：布尔键值必须 "true"/"false"；整数键必须合法整数且满足范围；`billing.currency` 必须 CNY/USD；`smtp.port` 1-65535；`security.session_ttl_minutes`/`advanced.request_timeout_sec` ≥1。校验失败抛 `InvalidOptionValueException`（→400），文案中文清晰。
2. `smtp.passwordSecret` 键名以 `Secret` 结尾，复用现有 `OptionKey.isSensitive()` 机制——确认 GET /api/option 列表查询会剔除其值（应已自动，写测试验证）。
3. 给 OptionRegistry 新校验补单测（每个键的合法/非法各一例）。

## 前端工作
1. `SysSettingsPage.tsx` 8 处 `TODO(api-missing)` 注释处：按上表键名接 GET（读现值）+ PUT（保存）。
2. 用现有 `system.api.ts` / `system.model.ts` 的 option 读写封装（如没有则按现有 api 模式加）。
3. SMTP 密码字段：保存走 `smtp.passwordSecret`，读取时后端剔除值（显示为"已设置/未设置"占位，不回显明文）。
4. 表单校验前端也做一份（数字/布尔/枚举），与后端校验对齐，错误提示中文。
5. "缓存清理/重置统计"两个操作按钮保留 TODO（本轮不做），其余 TODO(api-missing) 全部去掉换成真实接线。

## 验收（你有全部权限，自己跑到绿+自验）
1. `cd backend && JAVA_HOME=/opt/jdk21 mvn -pl nexa-service -am test -Dsurefire.failIfNoSpecifiedTests=false` 全绿（当前 514，新增 option 校验测试，总数应增加）。
2. `cd frontend && npm run build` 成功。
3. **活体验证**（你现在有 curl/ssh 权限）：
   - 服务在 8080 跑（连 sub2 PG 5434 + Redis 6380），启动用 `bash /root/.hermes/scripts/nexa-backend-start.sh`。
   - 需先登录拿 JWT（admin 端点）。若无 admin 账号，查 DB users 表找 root/admin，或在报告里说明无法活体验证的原因（不要假装验过）。
   - curl PUT 一个布尔键非法值（如 `register.invite_only=maybe`）应返回 400 + 中文错误。
   - curl PUT 合法值应 200，再 GET 确认值回读正确。
   - curl GET /api/option 列表确认 `smtp.passwordSecret` 的值被剔除（不出现明文）。
4. 前端 8 处 TODO(api-missing) 减少到只剩"缓存清理/重置统计"两个操作（grep 确认）。

## 禁区（allow 全开，靠这些指令约束）
- **不 push origin、不合 main、不动生产、不花钱**（CC 无 git push 权限，但也别试）。
- 不碰 relay 中继命脉（鉴权/选渠/计费/流式）——那是已验收的，本 slice 只动 ops/option 域 + 前端 system 域。
- 不动 compliance 驻地源（那是暂缓的候选2）。

## 完成标记
最后一行：`DRIVER_ITEM_DONE: R3-01` + commit hash（后端 + 前端可分别 commit）。
卡住：`DRIVER_ITEM_BLOCKED: R3-01 <原因>`。
