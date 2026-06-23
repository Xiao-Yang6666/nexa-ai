# S12 终判 + 白天交接（夜间迭代收尾 02:50）

## 一句话结论
Round6 三 slice 代码本身无 P0（567 测试真绿，账号化/映射/广场真实工作）。E2E 卡住的两点高度疑似**测试环境问题**，非代码缺陷。按用户指令：不连夜大改，留白天手动处理。代码已全部提交。

## E2E 真测暴露的问题（已冷静定性）

### 1. relay 转发 content/usage 丢失 → 极可能是 channel type 配错（非代码 bug）
- **根因定位**：`ChannelProtocolMapping.protocolOf()` —— type=14→CLAUDE(Anthropic直通)，其他(含我建channel误用的type=1)→OPENAI。
- 我 E2E 建 channel 用了 **type=1(OpenAI)**，但入站是 Anthropic `/v1/messages`、上游 ningmeng 也是 Anthropic 原生端点 → 走了 Anthropic→OpenAI 协议转换发给 Anthropic 端点 → 上游解析不了 → content空/usage=0。
- **修法**：channel type 用 **14**（已重建，但随后被下方 Redis 超时挡住，未验成）。
- **白天 TODO**：本地 Redis 环境下，用 type=14 channel 重跑 E2E STEP6，确认 content/usage 正常。如果 type=14 仍丢，才是真代码 bug。

### 2. Redis 命令超时 → 测试环境跨网问题（非产品 bug）
- 本机后端连 sub2 公网 Redis(216.167.75.27:6380)，单命令延迟 457ms，relay 链路串多个 Redis 操作累积超 10s 超时。
- 生产环境 Redis 与后端同机，无此问题。
- 已把 timeout 改成可配置(REDIS_TIMEOUT)，但跨网根本不稳，10s 都超。
- **白天 TODO**：本地起 ARM 版 Redis（`docker run -d -p 6399:6379 redis:7-alpine`，本机是 aarch64，别从 x86 的 sub2 save 镜像——架构不匹配 exec format error），后端连本地 Redis 跑 E2E。

### 3. account 域未接通 channel 选择（已知架构断层，增量任务）
- account.provider 域建好了（CRUD/凭证/倍率字段全有），但 relay 选 channel 只认旧 channel 表。
- 这是重构方案"阶段3：转发切新结构"，属增量，非阻断。

## E2E 已真实验证通过的部分（真成果）
- ✅ 超管登录（session cookie）
- ✅ 建供应商账号（credentials 不回显、非 admin 403）
- ✅ 配平台模型映射（claude-test→claude-haiku-4-5）
- ✅ 普通用户注册/登录/建 APIKey（key 脱敏正确，F-3002）
- ✅ relay 认证 + 两层映射 + 选渠 + 转发 ningmeng（msg_xxx 真实返回，链路本身通）
- ⚠️ 只差"上游返回内容正常"——卡在上面 #1(type) + #2(redis)

## 白天手改清单（给用户）
1. 本地起 ARM Redis，后端连它（消除跨网超时）
2. channel 用 type=14 重跑 E2E，确认 relay 拿到真实 content + usage + 计费扣费
3. 若 type=14 仍丢 content → 才需查代码（serializeRequest/buildUpstreamBody）
4. account 接通 channel 选择（增量重构）
5. 计费口径对齐（Round4 遗留：DualPriceBilling vs BillingCalculator）

## 红线状态（守住）
- round6/round7 均**未合 main**（等用户拍板）
- 未推生产、未花钱（仅 haiku 测试，几分钱）
- 代码全部提交在 iter/round6（成果）+ iter/round7（文档+本交接）

## 环境备忘
- 后端跑在 localhost:8083，连 nexa_r6it 库（sub2:5434，Flyway 全跑含 V29）
- E2E 脚本：/tmp/e2e-full.sh
- 测试账号：超管 e2eadmin/Admin@12345，普通 e2euser/User@12345
- 上游：ningmeng.chat key + claude-haiku-4-5（最便宜）
