# S12 后端开发组长复盘 — nexa-service

> 角色：后端开发组长 · 基线 main=cf5e0a4 · 1069 java 文件 / 537 测试绿
> 关注：实现质量、领域演进、relay/billing/routing 健壮性、测试盲区、下一轮并行任务
> 复盘方式：直读核心域源码 + 测试分布统计，不泛谈，定位到域/类/方法。

---

## 0. 一句话结论

后端 DDD 分层规整、领域服务纯函数化做得好，relay 流式刚补的防漏钱测试质量高。但有**三处必须在下一轮处理的硬问题**：
1. **计费口径分裂**——线上 relay 走的 `DualPriceBilling` 与被重度测试的 `BillingCalculator` 公式不一致（缺 `÷QuotaPerUnit`、取整方式相反），存在实账与设计偏差风险。
2. **路由亲和/跨组重试是"建好但没接线"的死子系统**——`ResolveChannelRouteUseCase`（CH-4/CH-5 全套，6 个测试）根本没被转发主干调用。
3. **集成测试几乎为零**——539 个测试方法里只有 1 个 `*IT`，无 `@SpringBootTest`/`@DataJpaTest`/Testcontainers，关键路径全是 mock 单测。

---

## 1. 测试覆盖盲区（最高优先级）

### 1.1 测试分布（实测）
按域统计测试**文件**数：

| 域 | 测试文件 | 域 | 测试文件 |
|---|---|---|---|
| relay | 13 | compliance | 4 |
| account | 11 | channel | 4 |
| routing | 6 | token / telegram / observability | 3 |
| modelgroup | 6 | publicsite/prefill/passkey/**billing** | 2 |
| deployment | 6 | task/sensitiveverify/playground/nfr | 1 |
| ops | 4 | | |

- `@Test` 方法总数 **539**，但 `MockMvc` 测试仅 **4 个**，`*IT` 集成测试仅 **1 个**（`account/.../UserControllerIT.java`）。
- 全仓**无** `@SpringBootTest`、**无** `@DataJpaTest`、**无** Testcontainers。

### 1.2 盲区定性
- **537 测试主要覆盖什么**：绝大多数是**领域层纯函数/值对象/聚合状态机单测**（`BillingCalculatorTest`、`RoutingValueObjectsTest`、`AffinityResolverTest`、`TokenValueObjectsTest` 等），以及少量用 Mockito 桩驱动的应用层单测。这类测试质量高、跑得快，但**全部在 mock 边界内**。
- **只有单测、没有集成测试的关键路径**：
  - **Repository 实现层**（`*RepositoryImpl` + `SpringData*` + JPA 实体）几乎零真实 DB 验证。`ddl-auto=validate` 下实体↔迁移列映射只有靠 R4-A 的"全新空库部署"人工兜底，没有自动化 `@DataJpaTest`。**`platform_model_mappings` 的 prod schema 漂移（V27 vs 野生 V28）正是这种盲区的代价**——实体与库不一致只有部署时才炸。
  - **`RelayForwardUseCase.forward()`（非流式主干，896 行的核心）**：只有 `RelayForwardUseCaseRetryTest` 和 `...StreamTest` 两个 mock 单测。流式有 `RelayControllerStreamMvcTest` 走真实 MVC 返回值处理器链（R2-04 修 bug 时补的，质量好），但**非流式 forward 全链路没有等价的 MockMvc 测试**——控制器→鉴权 filter→useCase→billing→落库这条线没有端到端验证。
  - **`JdbcUserQuotaAccount`**：直接对 `users` 表裸 SQL 自增/自减，**无任何测试**（既无单测也无集成）。这是真金白银的扣款路径，零覆盖。
  - **`RelayApiKeyAuthenticationFilter`**：relay 入口鉴权，无 MockMvc 测试。

### 1.3 建议
- 引入 Testcontainers + `@DataJpaTest`，至少覆盖 `logs`/`channel_model_costs`/`affinity_cache`/`tokens`/`redemptions` 五张高风险表的 RepositoryImpl 往返与实体映射，**把 schema 漂移挡在 CI 而非部署**。
- 给 `RelayForwardUseCase.forward()` 补一条 `RelayControllerMvcTest`（非流式），对齐已有 stream 版。
- `JdbcUserQuotaAccount` 至少补 H2/Testcontainers 集成测试，验证 `deleted_at IS NULL` 守卫、`affected==0` 抛错、并发自增原子性。

---

## 2. 计费域（billing）——正确性风险

### 2.1 两套计费公式并存且不一致 ⚠️ 必须裁决
存在两个独立计费实现，**线上 relay 走的那个反而测试薄弱**：

| | `billing.domain.service.BillingCalculator` | `relay.domain.service.DualPriceBilling` |
|---|---|---|
| 被谁调用 | （未见 relay 主干调用） | **`RelayForwardUseCase` 实际计费路径** |
| 测试 | `BillingCalculatorTest`（4 个 @Nested 重度覆盖） | 仅在 `DualPriceBillingTest` + relay 单测间接 |
| 是否 ÷ QuotaPerUnit(500000) | **是**（`divide(500000, FLOOR)`） | **否**（`basePriceRatio × groupRatio × tokens` 直接取整） |
| 取整 | `RoundingMode.FLOOR`（向下） | `RoundingMode.HALF_UP`（四舍五入） |
| 最小计费 1 守卫 | 有（非零倍率 quota≤0→1） | 无 |
| 免费模型 quota=0 守卫 | 有 | 无（靠 ratio=0 自然得 0） |

**问题**：两者对同一笔用量会算出**差 5 个数量级**的 quota（少了 `÷500000`）。要么 `DualPriceBilling` 的 ratio 口径本就是"已折算后的小数倍率"（那 doc 里 BL-6 引用 QuotaPerUnit 就是误导），要么线上实际在超额扣费。这条不能靠读代码确定对错——**`DualPriceBilling.compute` 缺少独立的、对照真实 ratio 量级的金额断言测试**。下一轮第一件事：明确哪个是权威口径，消除其一，并补"给定真实 PublicModel.basePriceRatio + ChannelModelCost.costRatio → 期望 quota"的金额级测试。

### 2.2 结算只有"响应后一次性扣"，无预扣/余额闸
`RelayForwardUseCase.settle()` + `JdbcUserQuotaAccount.debit()` 注释明说："本期最小结算不做余额下限保护（允许欠费）"。后果：
- 并发请求可把余额扣成大负数（无 `quota >= ?` 守卫，纯 `quota = quota - ?`）。
- 没有"选渠前预扣 + 响应后多退少补"（PRD REQ-05 §6 第 8-9/19 步的完整分段结算仍是 TODO）。
- 流式断流补偿计费按 `cumulativeUsage()` 计，**若上游从未下发 usage 块，则按 0 计费静默漏钱**（`billStreamConsume` 注释承认 usage 缺失按 0，仍落 Log 但金额为 0）。这是流式主力场景的隐性收入流失。

### 2.3 兑换码并发
`Redemption.redeem()` 状态机正确（仅 UNUSED 可兑、过期守卫），但"并发提交两次仅一次成功"依赖应用层 `@Transactional` + 行锁。`RedemptionRepositoryImpl` 是否对 `status=UNUSED` 做乐观锁/`SELECT ... FOR UPDATE` 没有集成测试佐证，建议补并发兑换测试。

---

## 3. relay 中继域——健壮性（核心命脉）

### 3.1 优点
- 流式刚修过的三条防漏钱路径（正常结束 / 坏块仍转发 / 部分交付后断流）测试写得**很扎实**，覆盖了真实链路漏钱的三个场景，`StreamConversionHandler` 对 passthrough"usage 解析仅副作用、绝不打断转发"的契约清晰。
- R2-04 的 `RelayControllerStreamMvcTest` 用 `standaloneSetup` 跑真实 MVC 返回值处理器链，正确地捕获了"`StreamingResponseBody` 被丢给 `HttpMessageConverter` → 500"这类只有真实 dispatch 才暴露的 bug。这是后端测试策略的正面样板。

### 3.2 问题
- **`RelayForwardUseCase` 是 896 行 god class**：单类里揉了协议识别、两层映射、key 校验、模型组闸门、选渠重试循环、协议转换（请求/响应/流式三套）、双价计费、结算扣减、错误脱敏、Type=2/Type=5 落 Log、SSE 错误写出。`forward()` 与 `forwardStream()` 大量重复（选渠循环、重试判定、计费几乎平行两份）。建议抽出 `RelayBillingRecorder`、`UpstreamRetryLoop`、`StreamPipeline` 三个协作者，降复杂度、提升可测性。
- **死代码**：`selectChannelPlaceholder()` / `declaresModel()`（line 606+）是 REQ-02 骨架占位，主干已改走 `selectRelayChannelUseCase`，应删除以免误读。
- **IP / User-Agent 未透传**：`recordConsumeLog`/`recordErrorLog` 都把 `ip`/`userAgent` 落空串（TODO 注释在案）。`logs` 表为这俩字段建了 `idx_logs_ip` 索引却永远存空串——索引无效 + 风控/审计缺数据。下一轮应从 `RelayController` 经 `RelayAuthContext` 透传 `remoteAddr/UA`。
- **`notifyChannelAutoDisabled` 是空实现**：渠道 AutoBan 自动禁用后只有 TODO 占位，root 收不到告警（CH-3 通知未接 observability `AlertNotifierPort`）。运营侧盲区。

---

## 4. 路由域（routing）——演进断层

### 4.1 最大技术债：CH-4 亲和 + CH-5 跨组重试"建好未接线"
- `ResolveChannelRouteUseCase`（亲和判定 + 跨组重试调度，配套 `AffinityResolver`/`CrossGroupRetryScheduler`/`AffinityCache` 表 + V15 迁移 + 6 个测试）**功能完整且有测试**。
- 但 `RelayForwardUseCase` 的转发主干调的是 **`SelectRelayChannelUseCase`**，其 doc 自承："聚焦无亲和的加权随机 + 重试切换最小闭环，亲和作为后续接缝"。
- **结论**：整套会话亲和（codex/claude 粘连，FC-068 内置规则种子都写进 V15 了）和 auto 分组跨组容灾，在生产转发路径上是**死代码**。投入产出严重倒挂——做了完整实现和测试却没收益。下一轮应把主干切到 `ResolveChannelRouteUseCase`（需把 `AffinityRequestContext`/path/header 从控制器透传进来），这是 routing 域兑现价值的关键一步。

### 4.2 合规驻地选渠 fail-closed 正确但依赖未落地字段
`SelectRelayChannelUseCase.selectDomesticForComplianceGroup` 的 fail-closed（驻地未知即不放行）逻辑严谨。但 S12 输入已记"渠道表无 residency 字段"——即 `ChannelResidencyPort` 当前可能恒返回 unknown，导致**所有合规分组请求都被判出境拒绝**或恒回落。需产品决策 residency 标注后才能真正生效，否则这条护栏要么空转要么误杀。

### 4.3 亲和缓存性能
V15 迁移注释自承："hit_count 写入与每次命中续期都走 DB……后续可叠加 Caffeine 一级缓存"。一旦 4.1 接线，每次命中一写 DB 会成为 relay 热路径的写放大。接线时应同步上 Caffeine L1（`AffinityCacheRepository` 已是端口，加装饰器即可）。

---

## 5. 数据库 / 迁移 / 索引

- **迁移整体健康**：V1–V27 单调递增，R4-A 验过全新空库从零部署成功。V15 注释专门记了"原 V9 与 token 域撞号推到 V15"的教训——版本管理意识好。
- **schema 漂移（已知阻塞）**：prod 被野生 V28 删了 `platform_model_mappings`，本地 V27 仍有该实体。**根因是缺 `@DataJpaTest`/Testcontainers 把实体↔库一致性纳入 CI**（见 §1.2）。建议：① 部署门加 `flyway validate` + 实体映射自检；② 补 repository 集成测试。
- **`logs` 表索引**：建了 11 个索引（含 `idx_logs_ip`、三段模型各一）。`ip` 恒空串（§3.2）使该索引纯负担；高写入的 `logs` 表上过多单列索引会拖慢插入——relay 每请求落 1–2 行 Log，建议按真实查询模式（看板基本按 `created_at`/`user_id`/`group` 过滤）精简，删掉 `idx_logs_ip` 及低基数单列索引。
- **`channel_model_costs`**：`uk_channel_model (channel_id, upstream_model) WHERE deleted_at IS NULL` 部分唯一索引设计正确（软删后可重建）。
- **`affinity_cache`** 唯一键用 `COALESCE(using_group,'')` 表达式索引处理 PG NULL 不去重，正确。

---

## 6. 下一轮后端任务（建议并行分工）

可并行（互不冲突，分属不同域/文件）：

| # | 任务 | 域/文件 | 风险 |
|---|---|---|---|
| P1 | **裁决计费口径**：统一 `DualPriceBilling` vs `BillingCalculator`，补金额级断言测试 | billing/relay domain service | 高（实账） |
| P2 | **接线路由亲和/跨组重试**：relay 主干切 `ResolveChannelRouteUseCase` + 上 Caffeine L1 | relay.application + routing | 中高 |
| P3 | **集成测试地基**：引入 Testcontainers + `@DataJpaTest`，覆盖 5 张高风险表 RepositoryImpl | 全域 test | 中（纯增量） |
| P4 | **余额闸 + 预扣结算**：`JdbcUserQuotaAccount` 加余额下限守卫，补预扣/多退少补 | billing.infrastructure + relay | 高（财务） |
| P5 | **relay god class 拆分**：抽 `RelayBillingRecorder`/`UpstreamRetryLoop`/`StreamPipeline`，删占位死代码 | relay.application | 中（重构） |
| P6 | **IP/UA 透传 + 渠道禁用告警**：控制器透传 remoteAddr/UA；`notifyChannelAutoDisabled` 接 observability | relay + observability | 低 |

依赖关系：P1 应最先（决定 P4 的金额口径）；P2 与 P5 都改 `RelayForwardUseCase`，**不宜同时并行同一文件**——建议 P5 重构先行，P2 在拆分后的 `UpstreamRetryLoop` 上接线。P3/P6 与其余完全独立，可立即并行启动。

---

## 7. 风险清单（按严重度）

1. 🔴 **计费口径分裂**——线上路径可能多扣/少扣，且缺金额级测试佐证（§2.1）。
2. 🔴 **无余额闸 + 流式 usage 缺失漏钱**——允许欠费、断流按 0 计（§2.2）。
3. 🟠 **集成测试缺位**——schema 漂移类问题只能部署时暴露（§1）。
4. 🟠 **亲和/跨组重试死代码**——大量已测实现零生产收益（§4.1）。
5. 🟡 **合规驻地护栏空转/误杀**——依赖未落地的 residency 字段（§4.2）。
6. 🟡 **relay god class**——896 行难维护、forward/forwardStream 重复（§3.2）。
7. 🟡 **运营盲区**——渠道自动禁用无告警、IP/UA 不落库（§3.2）。
