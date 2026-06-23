# S12 Round 2 复盘与下一轮范围决策

> 复盘时间：2026-06-23 08:35　主控亲自跑　基于 S11 真实端到端复验结果

## 一、本轮（Round 2）交付盘点

| Slice | 内容 | 状态 | 实证 |
|---|---|---|---|
| R2-01 | 流式计费逻辑（修2个真漏钱bug：passthrough usage解析无try/catch打断流 + 已交付token后断流不落计费） | ✅ merged 781973f | 514测试绿 |
| R2-02 | 验证码/密码重置/OAuth state 切 Redis（原生TTL+集群共享，InMemory降级fallback） | ✅ merged 467ba36 | Redis不可用时降级内存WARN正常 |
| R2-03 | compliance 接选渠主干（合规分组仅境内渠道 + fail-closed护栏 + 6测试） | ✅ merged 4d047be | 测试覆盖境内/境外/未知驻地 |
| R2-04 | 流式SSE真实链路500修复（直写HttpServletResponse绕过MessageConverter） | ✅ merged b5d2395+b96783b | 真实curl流式200 + 回归测试 |

## 二、S11 端到端复验结果（真实服务 + 真curl + DB实证）

- ✅ 非流式命脉：curl 200，C→A→B 模型映射对（gpt-test→mock-model-b），返回 pong+usage
- ✅ 上游 key 注入：mock 收到 `Bearer mock-upstream-key-xyz`（渠道key），非客户key → key替换链路对
- ✅ 鉴权：真实 token key 鉴权通过
- ✅ 流式 SSE：R2-04 修复后真实 curl 返回完整 SSE 流（role块+content块+finish_reason），不再 500
- ✅ **流式计费落库（本轮核心目标）**：logs id=13 `is_stream=t, prompt_tokens=11, completion_tokens=7, quota=36, model=mock-model-b` —— 历史首次 is_stream=true 计费记录。R2-01（计费逻辑）+ R2-04（写出层）合起来命脉闭环达成。

## 三、本轮最大教训（已固化）

**单测全绿 ≠ 真实链路能用。** R2-04 修的流式写出层 500 bug 在 baseline d8f2bf0 就存在，单测因 mock 了 useCase 没走真实 Spring MVC 返回值处理器，从未暴露。是 S11 真实 curl 才打出来。R2-01 辛苦修的流式计费逻辑，若没有 R2-04，在真实链路根本跑不到（请求 500）。
→ 已让 CC 补 MockMvc 回归测试（走真实返回值处理器链），防回归。

## 四、剩余已知 gap（按影响分级）

### P1（影响完整度，不影响核心中继）
- **前端 api-missing × 8**：SysSettingsPage 8 组配置项（站点描述/邀请码开关/计费货币/默认RPM-TPM/SMTP/安全策略/高级选项）后端无对应 option 键。需后端补 option 键枚举 + PUT /api/option 校验放行。当前诚实标 TODO 不造假。

### P2（次要功能 stub，核心命脉不依赖）
- WebAuthn 真实验签是 StubWebAuthnRelyingParty（需引 webauthn4j）
- Passkey challenge 内存桩（多实例需 Redis）
- Playground relay 桥未接真实 RelayForwardUseCase
- TwoFA 持久化是内存桩
- 真邮件服务（需用户提供 SMTP 凭证）

## 五、下一轮（Round 3）范围决策

### 判定：尚不可上线，但核心中继命脉已可用
核心 relay 中继链路（鉴权→选渠→C→A→B映射→双价计费→流式/非流式落库→compliance境内约束）**已端到端真实跑通**。阻碍上线的是运营配置完整度（P1 api-missing）和次要安全功能（P2 stub）。

### Round 3 候选（待用户拍板优先级）
1. **【推荐 P1】后端补 8 个 option 键** → 前端 SysSettings 8处 api-missing 接通，配置面板完整可用。改动可控（后端 option 注册 + 前端去 TODO）。
2. **【P1】compliance 驻地数据源接线**：R2-03 留的 ChannelSettingResidencyAdapter 当前恒返回 empty（fail-closed）。需在 channel setting JSON 加 residency 键或建映射表，让境内渠道真能被识别放行（否则合规分组永远拒绝=可用性为0）。
3. **【P2】WebAuthn/Passkey/TwoFA 生产化**：引 webauthn4j + Redis challenge store。工作量大，非核心。
4. **【需用户】真邮件 SMTP**：等用户给凭证。

### 暂缓
- Playground relay 桥、OTel 导出：非上线必需。

## 六、给用户的拍板点
Round 3 建议先做 **候选1（option键）+ 候选2（compliance驻地源）** —— 这两个让"配置完整"和"合规真能放行"，是上线前最该补的。候选3/4 工作量大或需外部凭证，可放 Round 4 或上线后迭代。

**红线守住**：本轮全程未合 main、未推生产、未花钱。等用户拍板 Round 3 scope。
