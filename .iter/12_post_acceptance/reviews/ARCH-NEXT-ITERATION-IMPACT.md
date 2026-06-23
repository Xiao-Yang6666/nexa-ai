# S12 架构复盘：下一轮迭代的架构影响与技术风险

> 角色：S12 架构师 · 验收后复盘
> 基线：main = cf5e0a4（R1+R2+R3+R4），后端 537 测试绿，Java 21 + Spring Boot 3.2.5 DDD（reactor: `nexa-common` + `nexa-service`）
> 范围：基于真实代码状态的下一轮迭代见解，具体到域/包/类/迁移文件

---

## 0. 结论摘要（TL;DR）

下一轮迭代的**头号架构阻塞**不是新功能，而是 **`platform_model_mappings` 这一张表上同时存在两套完整的 DDD 实现栈（`model` 域与 `relay` 域）**，叠加 prod 库已被野生 V28 删表的 schema 漂移。这是真实的重复/冗余，且两套实现的**约束语义、JPA 映射、软删除策略、REST 契约都不一致**，构成数据一致性与维护性双重风险。

排序后的下一轮动作优先级：

| 优先级 | 事项 | 性质 |
|---|---|---|
| P0 | `platform_model_mappings` 双实体收口 + prod 漂移对齐（补 V28/V29 迁移）| 阻塞、数据风险 |
| P0 | OpenAPI 契约重冻结（双 controller 路径冲突 + relay forward 多处 TODO 占位）| 契约风险 |
| P1 | `relay` 域跨域耦合直连其它域 domain 层（应统一走 port）| 技术债、边界腐蚀 |
| P1 | 计费分段结算未闭环（`forward` 仅响应后一次性扣费）| 计费正确性 |
| P2 | WebAuthn/Passkey stub、SMTP 凭证、合规 residency 字段 | 已知遗留 |

---

## 1. 重点深挖一：`platform_model_mappings` 双实体 / schema 漂移

### 1.1 事实认定：这是确凿的重复，不是良性的 BC 隔离

同一张物理表 `platform_model_mappings`（迁移文件 `V12__create_platform_model_mappings.sql`）上，挂着**两套从 domain 到 interfaces 的完整实现栈**：

**model 域栈（F-6002）**
- 聚合：`com.nexa.model.domain.model.PlatformModelMapping`
- 仓储接口：`com.nexa.model.domain.repository.PlatformModelMappingRepository`
- JPA 实体：`com.nexa.model.infrastructure.persistence.entity.PlatformModelMappingJpaEntity`（`@Entity(name="ModelPlatformModelMappingJpaEntity")`）
- Spring Data：`SpringDataPlatformModelMappingJpaRepository`
- 仓储实现：`PlatformModelMappingRepositoryImpl`（`@Repository("modelPlatformModelMappingRepositoryImpl")`）
- 应用：`ManagePlatformModelMappingUseCase`
- 控制器：`PlatformModelMappingController` → `/api/platform_model_mappings`（GET/POST/PUT/DELETE）
- DTO：`PlatformModelMappingAdminView` / `...ListView` / `...CreateRequest` / `...UpdateRequest`

**relay 域栈（F-6011）**
- 聚合：`com.nexa.relay.domain.model.PlatformModelMapping`
- 仓储接口：`com.nexa.relay.domain.repository.PlatformModelMappingRepository`
- JPA 实体：`com.nexa.relay.infrastructure.persistence.entity.PlatformModelMappingJpaEntity`（`@Entity(name="RelayPlatformModelMappingJpaEntity")`）
- Spring Data：`SpringDataPlatformModelMappingRepository`
- 仓储实现：`PlatformModelMappingRepositoryImpl`（`@Repository("relayPlatformModelMappingRepositoryImpl")`）
- 应用：`ManageMappingUseCase`
- 控制器：`RelayMappingController` → `/api/relay/mappings`（GET/POST/DELETE）

两套都自称对齐「DB-SCHEMA」——但一个写 §17、一个写 §20，文档引用都对不上，说明这是**两次独立实现各自落地、从未收口**的产物。

### 1.2 危险点：两套实现的语义不一致（不是简单 DRY 问题）

| 维度 | model 域实体 | relay 域实体 | 风险 |
|---|---|---|---|
| `@SQLRestriction("deleted_at IS NULL")` | **有** | **无** | model 域全局自动过滤软删除行；relay 域靠每条 JPQL 手写 `deletedAt IS NULL`。两者对"什么是存活行"的定义在 JPA 层不一致 |
| 唯一约束 | 声明 `uk_pmm_public_name`（`@UniqueConstraint`）| 仅声明 `idx_..._deleted_at` 索引，无唯一约束 | 两实体对同表声明的约束不同；`ddl-auto: validate` 下若 Hibernate 校验两套元数据，唯一约束声明分歧是潜在校验风险 |
| `enabled` 类型 | `Boolean`（可空，`null→true`）| `boolean`（原始，`null→false`）| **同一行数据，两域读出的 enabled 兜底结果相反**。relay 是中继转发实际生效路径（`findEnabledByPublicName` 过滤 `enabled=true`），model 是管理面写入路径——写入端默认 true、读取端 null 归 false，存在"管理面建了映射但中继读不到"的潜在不一致 |
| 软删除写法 | `@Modifying UPDATE ... SET deletedAt`（`softDeleteById`）| `findActiveById` 读出后 `setDeletedAt` 再 `save` | 两套软删除实现并存，行为需各自回归 |
| 列 nullable | `created_time/updated_time` 可空 | `created_time/updated_time` `nullable=false` | 同表列约束声明分歧 |
| 唯一索引 vs V12 | V12 建的是 `uk_public_name`（partial index `WHERE deleted_at IS NULL`）| relay 实体未声明唯一约束 | model 实体声明的 `uk_pmm_public_name` 与 V12 实际索引名 `uk_public_name` **不同名**——若哪天开 `ddl-auto` 生成或严格校验会冲突 |

两个 `@Entity` 通过 `@Entity(name=...)` 逻辑名区分（`ModelPlatformModelMappingJpaEntity` / `RelayPlatformModelMappingJpaEntity`），所以 Hibernate 启动期能并存、不报重名——这正是问题被掩盖至今的原因。但它们映射**同一张表**，是两个写入入口对同一份数据各自维护，长期必然漂移。

### 1.3 REST 契约层面的冲突

两套栈暴露了**两组管理同一份数据的 API**：
- `/api/platform_model_mappings`（model 域，含 PUT 更新，分页用 `p`/`page_size`，`@RequireRole(ADMIN)` 类级）
- `/api/relay/mappings`（relay 域，无 PUT，create/delete 要求 `ROOT`，list 要求 `ADMIN`）

权限模型都不一致：model 域 create/delete 是 ADMIN，relay 域 create/delete 是 ROOT。**前端到底调哪个、哪个是权威写入口、哪个该下线**，是下一轮必须由架构拍板的契约问题。这直接触发 **OpenAPI 契约重冻结**。

### 1.4 prod schema 漂移（野生 V28 删表）

已知遗留记录：prod 库被野生 V28 删了 `platform_model_mappings` 表，本地代码 V27 仍带该实体。当前本地迁移目录确认**只到 V27，无 V28**（已核验：`db/migration/` 无 `V28*.sql`）。

风险链：
1. 本地 `ddl-auto: validate` + 两个 JPA 实体都映射 `platform_model_mappings` → **若 prod 该表已被删，服务启动 validate 阶段直接失败**（两实体都会校验失败）。
2. R4-A 的部署门只验证了"全新空库从 V1 跑到 V27"——这条路径下表是存在的，所以没暴露漂移。但**对存量 prod 库**（已被 V28 删表）部署时，Flyway 历史里有 V28 而代码 classpath 里没有，会触发 Flyway 的 "missing migration" / checksum 校验问题，且表已不存在 → validate 失败。

下一轮必须做的迁移动作（二选一，取决于 1.2 的收口决策）：
- 若保留表：补 `V28__recreate_platform_model_mappings.sql`（幂等 `CREATE TABLE IF NOT EXISTS`），并把本地 Flyway 与 prod 历史对齐（可能需 `flyway repair` 或在 prod 手工补登记）。
- 若确认废弃表：把两套实体栈一起删除，补迁移正式 `DROP`，并清掉所有引用。

**这一项不解决，下一轮任何涉及 prod 的部署都会被卡在服务启动。**

### 1.5 架构师建议（收口方向）

按 DDD「一张表一个聚合一个写权威」原则：
- **保留 relay 域的 `PlatformModelMapping` 作为唯一权威**——理由：它是中继转发实际生效路径（`TwoLayerModelResolver` L2 lookup → `RelayForwardUseCase.resolveModel`），是业务真正读这张表的地方。
- model 域那套（`ManagePlatformModelMappingUseCase` + `PlatformModelMappingController` + 整个 model 包下 PMM 实体/仓储/DTO）应当：要么删除、要么改成调用 relay 域的应用服务/port。管理面 CRUD 归到 relay 域或抽一个共享的 admin BFF。
- 统一 `enabled` 兜底语义（强烈建议统一为非空 + DB default true），消除"写入默认 true、读取默认 false"的隐患。
- 统一软删除策略与唯一索引命名（对齐 V12 的 `uk_public_name`）。

这是下一轮**必须先做的基座重构**，否则计费/中继域上层任何改动都踩在不稳的双写地基上。

---

## 2. 重点深挖二：DDD 边界与跨域耦合

### 2.1 域清单与分层

`com.nexa.*` 下各域均规范遵循四层（`domain` / `application` / `infrastructure` / `interfaces`），domain 层零框架依赖（聚合纯 POJO + 静态工厂 + Builder 重建），这是好的。已核验 `task`、`telegram`、`token`、`twofa`、`relay`、`model` 等域结构一致、聚合充血（行为在聚合方法上，非贫血）。

### 2.2 跨域耦合热点：`relay` 域直连其它域的 domain 层

`RelayForwardUseCase`（应用层，48KB 巨型类）直接 `import` 了多个**其它域的 domain 内部类型**：

```
import com.nexa.billing.application.port.UserQuotaAccount;   // ✅ 走 port，OK
import com.nexa.billing.domain.vo.Quota;                     // ⚠ 直连 billing domain vo
import com.nexa.channel.domain.model.Channel;                // ⚠ 直连 channel domain 聚合
import com.nexa.channel.domain.model.ChannelModelCost;       // ⚠ 直连 channel domain 聚合
import com.nexa.channel.domain.repository.ChannelModelCostRepository;  // ⚠ 直连 channel 仓储
import com.nexa.channel.domain.repository.ChannelRepository;          // ⚠ 直连 channel 仓储
import com.nexa.model.domain.model.PublicModel;              // ⚠ 直连 model domain 聚合
import com.nexa.model.domain.repository.PublicModelRepository;        // ⚠ 直连 model 仓储
import com.nexa.routing.application.SelectRelayChannelUseCase;        // ⚠ 直连 routing 应用层
```

问题分析：
- relay 域**已经建立了 port 抽象的良好实践**——`relay/domain/port/` 下有 `ModelGroupAccessPort`、`ModelGroupPricingPort`、`UpstreamHttpPort`（modelgroup 域通过 port 解耦，是正确做法）。
- 但对 `channel`、`model`、`routing`、`billing(domain vo)` 却是**直接 import 对方 domain 层的聚合根与仓储接口**。这意味着 relay 域被这些域的内部模型变更直接波及——`Channel` 聚合改个方法签名、`PublicModel` 改字段，relay 就要跟着改。

这是**边界腐蚀**：同一个类里一半依赖走 port（解耦）、一半直连 domain（耦合），标准不统一。`RelayForwardUseCase` 自身注释也承认是"骨架"（"后续 wave 注入跨 BC 端口"），说明这是已知但未还的债。

下一轮建议：把 `channel`/`model`/`routing` 的访问统一收敛到 relay 域自己的 port（如已有的 `ModelGroupPricingPort` 模式），在 relay 域定义 `ChannelSelectionPort`、`ChannelCostPort`、`PublicModelPricingPort`，由 infrastructure 适配。这样 relay 域 domain/application 不再 import 任何其它域的 domain 类型。

### 2.3 `RelayForwardUseCase` 单类过大

896 行、48KB，承担协议识别→映射→key 校验→选渠→协议转换→调上游→重试→双价计费→落 Log 的全链路编排，且内含内部类 `StreamConversionHandler`。虽然业务规则确实下沉在各 domain service，但**应用层编排本身已过载**。下一轮可按职责拆分：转发编排 / 流式编排 / 计费结算编排，降低单类变更半径。

---

## 3. 重点深挖三：`nexa-common` 模块边界

`nexa-common`（artifact `nexa-common`，包 `com.nexa.shared.*`）作为 shared kernel，边界**总体清晰且合理**：

- `shared.web`：`ApiResponse`、`PageView`、`GlobalApiExceptionHandler`（统一响应信封）
- `shared.security.*`：完整的 RBAC（`AuthLevel`/`ActorRole`/`RbacPolicy`/`AuthenticatedActor`/`OperationDomain`）、JWT 鉴权过滤器链、字段加密（`AesGcmFieldEncryptor`/`EncryptedValue`/`FieldEncryptor`）、`@RequireRole`/`@CurrentActor` 注解与拦截器、`SafeText`/`SafeIdentifier` 注入防护、HTTPS 强制过滤器
- `shared.kernel.DomainException`：领域异常基类

边界评价：
- **优点**：放进 common 的都是真正横切关注点（鉴权、响应信封、加密、注入防护），且自带测试（`nexa-common/src/test`）。pom 依赖收敛合理（web/security/validation/jjwt）。
- **风险点**：
  1. `nexa-common` 依赖了 `spring-boot-starter-web` 与 `spring-boot-starter-security` 全量 starter。作为被所有域依赖的底座，这把整个 web/security 栈强绑给每个使用方——若未来要拆出纯 domain 用的工具，会被迫拖进 web 依赖。可接受，但需注意 common 不应继续膨胀业务逻辑。
  2. 各域的 `DomainException` 出现**多处同名独立定义**（如 `task/domain/exception/DomainException`、`telegram/domain/exception/DomainException`）与 `shared.kernel.DomainException` 并存。需确认这些是否应统一继承 `shared.kernel.DomainException`，否则全局异常处理 `GlobalApiExceptionHandler` 的兜底分类会有遗漏。下一轮值得审计各域异常基类与 common 基类的继承关系是否一致。

结论：`nexa-common` 边界不是阻塞项，但要守住"只放横切、不放业务"的红线，并统一域异常基类继承。

---

## 4. 其它维度的下一轮风险

### 4.1 OpenAPI 契约：需要重冻结
触发点：
- 第 1.3 节的双 controller 路径冲突（`/api/platform_model_mappings` vs `/api/relay/mappings`）。
- `RelayForwardUseCase.forward` 多处 `TODO`：`③ key 减法校验`、`⑥⑦ 双价记账`、`⑦' 分段结算`均标注"本期仅最小闭环 / 占位"。这些一旦补全，请求/响应字段（尤其计费回执、限额错误码）会变，契约需重冻结。
**建议下一轮开工前先冻结 openapi，明确废弃哪组 mapping 端点。**

### 4.2 计费正确性：分段结算未闭环
`forward` 注释明确：`TODO REQ-05 完整：补「选渠后预扣 + 响应后多退少补」分段结算，本期仅响应后一次性扣 quota_sell`。当前是**响应后一次性扣费**，存在并发超扣/欠扣风险（高并发下用户余额可能被透支，因为扣费在上游返回后才发生，期间可放行多个请求）。流式路径已注意到"防漏钱"（`billStreamConsume` 在已写出 chunk 后必落 Log），但预扣机制缺失是计费域下一轮的正确性重点。`task` 域已有 `SettleRefundUseCase`/`RefundPort`/`BillingContext`，可作为分段结算闭环的基座。

### 4.3 审计/可观测
存在 `observability`、`log`、`compliance`、`nfr` 域。relay 链路对错误 Log（Type=5）+ 消费 Log（Type=2）已落，但跨域审计链（谁改了 L2 底仓映射 B、谁动了计费配置）未见统一审计切面。下一轮若做合规，需在 admin 写操作上加审计。

### 4.4 已知遗留（来自输入，确认仍在）
- 合规 residency：`channel` 表无 residency 字段 → 需迁移加列 + 产品决策（境内外标注）。属下一轮 DB 模型调整项。
- SMTP 真凭证：需用户提供，非架构阻塞。
- WebAuthn/Passkey：`passkey` 域 + `V6__create_passkey_credentials.sql` 存在但为 stub，生产化工作量大，需单独立项评估。

### 4.5 部署形态
R4-A 已验证全新空库 V1-V27 部署成功。但 4.1.4 指出**存量 prod 库（含野生 V28）的部署路径未验证且当前会失败**。下一轮部署门必须新增"存量漂移库"测试场景，不能只测空库。

---

## 5. 下一轮"先重构基座"清单（架构师视角）

按依赖顺序：

1. **`platform_model_mappings` 双实体收口**（P0，基座）——定权威栈、删/改冗余栈、统一 enabled/软删除/唯一索引语义。先做这个，否则 model 与 relay 域上层都不稳。
2. **prod 漂移迁移对齐**（P0）——补 V28/V29 + Flyway repair 策略 + 存量库部署门。与第 1 项同批。
3. **OpenAPI 重冻结**（P0）——废弃冲突端点、锁定计费回执契约。
4. **relay 跨域耦合 port 化**（P1）——`channel`/`model`/`routing` 访问统一走 relay 自有 port，对齐已有的 `ModelGroup*Port` 模式。
5. **计费分段结算闭环**（P1）——预扣 + 多退少补。
6. **域异常基类统一**（P2）——各域 `DomainException` 对齐 `shared.kernel.DomainException`。

> 一句话：下一轮**不要急着加功能**，先用 1-3 项把 `platform_model_mappings` 这块"双写 + 漂移"的地基填实，再做 relay 解耦与计费闭环。当前 537 测试绿掩盖了双实体并存的结构债——测试都各自测各自那套，没有测到"两套写同一张表"的一致性。
