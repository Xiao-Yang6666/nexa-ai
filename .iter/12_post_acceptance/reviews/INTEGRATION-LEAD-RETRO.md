# 集成联调组长复盘（S12 / Round 4 收口）

视角：真实链路、环境配置、生产化稳定性。结论先行——**功能链路已能端到端跑通（R4-A 全新空库部署验过），但"可观测/告警/健康检查"这一层基本是空壳，生产上一旦出问题会处于"瞎飞"状态。** 下面具体到域/文件/接口。

---

## 一、最容易断的链路（按风险排序）

### 1. 流式 SSE（/v1/chat/completions 等，is_stream=true）— 最脆弱
- 实现：`RelayController.forwardRelay`（L227-258）对 `useCase.wantsStream(body)` 直写 `HttpServletResponse` 输出流，绕过 MessageConverter。R2-04 已修 500，但这条路有结构性脆点：
  - 写出**第一个 chunk 之后**上游中断，由 `forwardStream` 内部消化、不外抛（见 L244-246 注释）。意味着**流中途断裂对客户端表现为"正常结束的残缺响应"**，没有错误信封、没有重试、当前也没有任何指标记录。客户端拿到截断的 SSE 很难判定成败。
  - 计费时点：is_stream=true 首次真落计费（R2-04）。需复验"流中断"与"计费"的一致性——半截流是否照常扣费？这是最容易产生客诉/对账纠纷的地方。
- **下一轮 S10 必复验旅程**：①正常流式问答全程；②上游在流中途 502/断连；③客户端主动断开（关页面）后服务端 writeStream 的行为与计费落点。

### 2. Relay API-Key 鉴权 → JWT 过滤器的顺序耦合
- `SecurityConfig` L142-147：`/v1/**` 必须先过 `RelayApiKeyAuthenticationFilter` 再过 JWT，否则 `sk-` key 会被 JWT 当令牌解析失败误判 401。这是**靠过滤器注册顺序维持的隐式契约**，没有测试锁死顺序。任何人调整 filter 链顺序都会静默打断所有 `/v1` 流量。
- **建议**：加一条集成测试，断言带 `sk-` key 请求 `/v1/chat/completions` 不会先撞 JWT 401。

### 3. Redis 降级路径
- `application.yml` L55-68：Lettuce 惰性连接，Redis 不可用不阻断启动，`CacheErrorHandler` 降级直查 DB（`AuthCacheConfig`）。设计正确，但意味着 **Redis 挂掉时鉴权全部打到 DB**，而 Hikari 池只有 5（L26 `DB_POOL_MAX:5`）。高并发 + Redis 抖动 = DB 连接瞬间打满（注释自己写了 `FATAL: too many clients`）。降级是"能用"，不是"扛得住"。
- 同理 `account.store.type=redis`（验证码/重置令牌/OAuth state）连不上时降级回内存——**多实例下内存桩不共享**，会出现"A 实例发的验证码 B 实例验不过"。
- **下一轮 S10 必复验旅程**：Redis 主动下线后，①登录鉴权；②注册发码→验码（强制打到不同实例）；③观察 DB 连接数。

---

## 二、环境/配置风险（生产化前的硬清单）

配置集中在 `backend/nexa-service/src/main/resources/application.yml` + `backend/.env.example` + `frontend/.env.example`。

| 配置项 | 当前默认值 | 生产前动作 | 风险等级 |
|---|---|---|---|
| `SECURITY_ENCRYPTION_KEY`（L90） | **空字符串** | 必须注入 Base64 32 字节；空值下 AES-GCM 字段加密会启动期/运行期失败 | 🔴 阻断 |
| `JWT_SECRET`（L82） | `change-me-local-dev-secret-key-min-32-bytes-0123456789` | 必须换强随机；当前默认值进了仓库，**任何人可伪造任意用户 JWT** | 🔴 阻断 |
| `DB_PASSWORD`（L21） | `postgres` | 必须换 | 🔴 |
| `REDIS_PASSWORD`（L62） | 空 | 生产 Redis 必须设密码并注入；空密码 + 暴露端口 = 缓存可被任意读写（含鉴权缓存） | 🔴 |
| `APP_CORS_ALLOWED_ORIGINS`（L98） | `http://localhost:3100` | 必须改成真实前端域名（https）。**allowCredentials=true，origin 绝不能配 `*`**（SecurityConfig L170-171 已正确约束） | 🟠 |
| `JWT_TTL_SECONDS`（L83） | 604800（7 天） | 评估是否过长；7 天令牌 + 无刷新轮换机制，泄露后窗口大 | 🟠 |
| `DB_POOL_MAX`（L26） | 5 | 单实例 5 连接，多实例水平扩展时要核 PG `max_connections` 总账，叠加 Redis 降级直查更紧张 | 🟠 |
| `deployment.ionet.*`（IonetSettings） | 全空、enabled=false | io.net 集成默认关；启用前需注入 `api-key`，缺省按"未配置"失败（非启动报错），行为正确 | 🟢 |

**演练用临时值（部署后必须改）**：上表所有 🔴 项的默认值都是"能让本地/演练跑起来"的占位，**没有任何机制阻止它们被带进生产**。`ddl-auto: validate`（L37）是亮点——生产不会自动改表，安全。

### 配置正确的地方（值得保留）
- `spring.jpa.hibernate.ddl-auto: validate` + Flyway 管表，生产不乱改 schema。
- `open-in-view: false`（L42），避免懒加载拖长事务。
- Hikari `max-lifetime` / `keepalive-time`（L29-30）做了连接轮换与探活。
- CORS 用具体 origin 白名单 + 显式 OPTIONS permitAll 兜底（SecurityConfig L78-81），预检不会被路径级角色门槛拦成 403。

---

## 三、前端反代/API_BASE 的生产形态风险

存在**两套互斥的后端寻址机制并存**，是联调期混乱的高发点：

1. `frontend/next.config.js` rewrites：`/api/:path*` → `BACKEND_ORIGIN`（默认 `http://localhost:8080`）。这是**同源代理**形态，浏览器只看到前端域名。
2. `frontend/src/shared/api/client.ts` L36：`API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? ''`。空串=走同源（吃 rewrites）；非空=**前端直连后端跨域**（吃 CORS）。

风险：
- 两条路对 CORS 的需求完全相反。走 rewrites 时根本不需要 CORS；走 `NEXT_PUBLIC_API_BASE` 直连时 CORS 白名单必须精确，否则 `Failed to fetch`。生产部署若两个变量都设了，**行为取决于 client.ts 优先级（NEXT_PUBLIC_API_BASE 赢）**，rewrites 形同虚设——容易出现"配了 rewrites 以为走同源，实际跨域被 CORS 挡"。
- `NEXT_PUBLIC_*` 是 **build 时烘进 bundle**（.env.example 已警告），改完必须重新 `npm run build`。生产用环境变量热改 `NEXT_PUBLIC_API_BASE` **不生效**，是经典踩坑。
- `.env.example` L8 明确警告不能用 docker 内网 IP（172.17.x），但没有任何校验阻止误配。

**建议（下一轮）**：明确二选一并文档化。生产推荐 **rewrites/nginx 同源代理**（`NEXT_PUBLIC_API_BASE` 留空），避免 CORS 与凭证 cookie 的跨站复杂度；或者明确走直连并把 CORS/Cookie SameSite 配套讲清。当前两套并存且无防呆，是部署事故源。

---

## 四、可观测性/NFR 域：**这是最大的隐患——基本是空壳**

代码里有完整的"可观测域骨架"，但**没有一处把它接到真实请求链路上**。证据如下：

### 4.1 指标（observability.metrics）— 端点活着，但永远是空的
- `MetricRegistry.recordRequest(...)`（domain/metrics/MetricRegistry.java L59）是唯一的埋点入口。**全主代码搜索：零调用**，只有 `MetricsTest.java` 在调。
- `/metrics` 端点（`MetricsController` L42，RootAuth）→ `ExportMetricsUseCase.currentSnapshots()` → `registry.snapshot()`。链路通，但因为没人 `recordRequest`，**生产上 `GET /metrics` 永远返回空指标**。Prometheus 抓到的是空集。
- 类注释（MetricRegistry L22）写"relay 链路埋点只调 registry.recordRequest"——**这个埋点从未被实现**。`RelayController` / `RelayForwardUseCase` 里没有任何 MetricRegistry 注入。

### 4.2 告警（observability.alert）— 编排存在，无人触发，且不发送
- `DispatchAlertsUseCase.evaluateAndDispatch(...)`（L54）：**全主代码零调用**。类注释自己说"由 relay 链路/指标聚合循环/定时巡检调用"——**这三者都不存在**（无 `@Scheduled`、无 `@EnableScheduling`，全仓库搜不到）。
- `LoggingAlertNotifier`（infrastructure/alert）：自述"本期为骨架实现"，三个渠道（EMAIL/WEBHOOK/BARK）`isEnabled` 全返回 true，但 `notify()` **只打一条 `log.warn`，不真正发送**。真实送达"待 F-4037 配置接通后实现"。
- 即"告警系统"目前 = 永不被调用的编排 + 只写日志的桩。生产 SLO 越界**没有任何人会知道**。

### 4.3 健康检查 — 不存在
- **没有 Spring Boot Actuator**（pom 搜不到 `spring-boot-starter-actuator`、`micrometer`）。
- **没有 `/health` / `/healthz` / `/readyz` / liveness / readiness 端点**（全仓库搜零）。
- `SecurityConfig` 把 `/api/status`、`/api/status-page/*`、`/heartbeat/*` 放行了——`/api/status`（`PublicStatusController`）是**营销首页公开状态聚合**，不是运维健康检查；`/status-page`、`/heartbeat` 是**反代外部 Uptime-Kuma**（`HttpUptimeStatusClient`），依赖外部监控系统存活、且 base-url 需配置。**本服务自身没有可供 K8s/LB 探活的轻量端点。**
- 后果：容器编排无法做存活/就绪探针，滚动发布、自动重启、摘流量全都没有依据。

### 4.4 日志 — 非结构化
- **没有 `logback-spring.xml` / `logback.xml`**（resources 下搜零）。用的是 Spring Boot 默认 console 文本日志。
- 没有 JSON 结构化、没有 traceId 关联（`observability.trace.TracePropagationFilter` 存在，但既然指标埋点都没接，trace 是否真贯穿全链路存疑——需复验）。
- 生产排障靠 grep 纯文本，跨实例无法按请求聚合。

### 4.5 缓存统计 — 计数器在转，但没人喂
- `InMemoryCacheStatsProvider.recordHit()/recordMiss()`（ops/infrastructure/monitoring）：**主代码零调用**（只有 `adjustEntryCount` 在内部）。运维端点 `cache_stats`（F-4019）采到的命中率**永远是 0/0**。
- NFR 域（`nfr.application.port.CacheHitRateMonitor`、`CacheHitStats`、`LatencyBudget`、`AvailabilityTarget` 等）全是 VO/契约定义，**没有运行期实现接管**。NFR 目标（如 MetricRegistry 注释提到的 p50≤15ms/p99≤60ms）目前**无法度量、无法验证**。

> 小结：observability/nfr 两个域是"DDD 充血模型示范"，单元测试绿，但**与生产流量零接触**。当前系统对自身运行状态是"完全盲视"的。

---

## 五、其余隐患证据

- **Schema 漂移（已知阻塞，重申严重性）**：prod 库被野生 V28 删了 `platform_model_mappings`，本地 V27 + JPA 实体（`PlatformModelMappingJpaEntity`）仍引用该表。因 `ddl-auto: validate`，**用本地代码连 prod 库会启动期校验失败直接起不来**。这不是"将来要对齐"，是"现在部不上去"。部署前必须先决定 V28 是回滚还是补正向迁移。
- **WebAuthn/Passkey stub**：登录入口已 permitAll（SecurityConfig L109-110），但 stub 状态。生产化前要么真实现，要么下线入口，避免暴露半成品鉴权面。
- **真邮件 SMTP 缺凭证**：注册/找回验证码链路依赖。配套 `account.store.type=redis` 的 TTL 才有意义；SMTP 不通时整个注册/找回旅程断在发码步骤。
- **上游超时**：`UpstreamHttpProperties` 读超时 300s（合理，容大模型慢响应），但 300s 占着一个虚拟线程 + 一个 Hikari/上游连接，配合 5 连接池，少量慢请求即可拖垮吞吐。需压测确认。

---

## 六、给下一轮（S10）的明确建议

### 必做（生产化阻断项）
1. **接入真实健康检查**：引入 actuator（仅 health/readiness）或手写一个轻量 `/healthz`（含 DB ping + Redis ping），供 LB/编排探活。这是所有稳定性工作的前提。
2. **把 MetricRegistry 接进 relay 链路**：在 `RelayForwardUseCase`（流式与非流式两条路）出口调 `recordRequest(channel, model, error, latencySec, quotaSpent)`。否则 `/metrics` 和告警全是死代码。
3. **流式中断 × 计费一致性复验 + 修正**：明确半截流的计费规则，并在 `forwardStream` 内对中断落一条结构化日志/指标。
4. **配置防呆**：启动期校验 `JWT_SECRET`/`SECURITY_ENCRYPTION_KEY` 非默认/非空，命中默认值即拒绝启动（fail-fast），杜绝弱密钥进生产。
5. **解决 V28/V27 schema 漂移**，否则部署门是假绿。

### 应做
6. **结构化日志**：加 `logback-spring.xml`（JSON + traceId），确认 `TracePropagationFilter` 的 traceId 真的注入 MDC 并贯穿。
7. **告警落地**：至少接通一个真实渠道（Webhook/Bark），并加一个 `@Scheduled` 巡检循环调 `evaluateAndDispatch`，否则告警域永远不触发。
8. **前端寻址收敛**：文档化并默认走同源 rewrites，`NEXT_PUBLIC_API_BASE` 留空；说明 build-time 烘焙特性。
9. **缓存命中埋点**：在鉴权缓存切面接 `recordHit/recordMiss`，让 `cache_stats` 有真值。

### E2E 脚本（强烈建议补，当前完全没有）
仓库内**无任何 E2E/集成冒烟脚本**（无 playwright/cypress/deploy.sh/e2e.sh，也无 Dockerfile/compose）。R4-A 的"全新空库部署验证"是人工跑的，不可重复。建议补一个可重跑的冒烟脚本覆盖关键旅程：
- 注册 → 发码 → 验码 → 登录拿 JWT
- 创建 token（sk-key）→ `/v1/chat/completions` 非流式 200 + 计费落账
- `/v1/chat/completions` 流式 200 + 计费落账 + 中断场景
- admin 端点 403/200 鉴权边界
- Redis 下线后的降级旅程
- 前端代理端到端（同源 rewrites 形态）

### 重点复验旅程（S10 优先级）
1. 流式问答（正常 / 上游中断 / 客户端断开）× 计费
2. Redis 下线 → 鉴权降级 + 注册发码跨实例
3. relay filter 链顺序（sk-key 不撞 JWT 401）
4. 用本地代码连"已对齐的"库能否启动（validate 通过）
5. 前端两种寻址形态各自的 CORS/cookie 行为
