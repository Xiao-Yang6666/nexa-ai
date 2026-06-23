# DECISION-LOG — Slice r6b 模型映射

## R6B-D1：任务书机械前提（"两张独立表，写一个读另一个"）经核实不成立

**结论**：model 域与 relay 域的映射 JPA 实体**共用同一张物理表**，并非两张独立表。"超管写→转发读"
在当前代码下**已经走通**（经共享表）。

**证据**：
- 仅有 `V12__create_platform_model_mappings.sql` / `V13__create_user_model_aliases.sql` 两条建表迁移，
  全仓 `CREATE TABLE platform_model_mapping* / user_model_alias*` 各只 1 处（最高迁移版本 V27）。
- 两域实体的 `@Table` 同名，仅 Hibernate 逻辑实体名不同以避免冲突：
  - `model/.../PlatformModelMappingJpaEntity` → `@Entity("ModelPlatformModelMappingJpaEntity")` `@Table("platform_model_mappings")`
  - `relay/.../PlatformModelMappingJpaEntity` → `@Entity("RelayPlatformModelMappingJpaEntity")` `@Table("platform_model_mappings")`
  - `user_model_aliases` 同理。
- 超管 `POST /api/platform_model_mappings`（model 域）写入的行，正是
  `RelayForwardUseCase.resolveModel` → `TwoLayerModelResolver` 经 relay 域仓储读取的行
  （读侧附加 `enabled=true AND deleted_at IS NULL` 过滤，与写侧语义兼容）。

**因此**：任务书预分配的 `V30__merge_model_mapping.sql`（合并双表）**没有必要**——没有第二张表可合并、可 drop。

## R6B-D2：放弃任务书指定的"删 model 域实体 / Controller 改接 relay 域仓储"破坏性合并

**决策**：不执行"权威方=relay、删除 model 域领域模型+仓储+实体、把 Controller 改接 relay 域用例"
这一步。改为**新增跨域闭环集成测试作为回归护栏**，保留两域应用/接口层现状。

**为什么（可逆性 + 风险）**：
1. **数据层本就统一**（见 D1），合并的原始目标（让"超管配的"=“转发读的"）已达成，破坏性重构
   解决的是一个不存在的数据割裂。
2. **按任务书方向合并会引入安全回归**：用户自助别名的越权护栏只存在于 **model 域**
   `ManageUserModelAliasUseCase`（`resolveAndAuthorizeScopeId` 强制 scope_id=本人、`requireOwnership`
   防改他人/他组）。relay 域 `ManageMappingUseCase` **完全没有**这些 self-scope 护栏。把
   `UserModelAliasController` 裸接到 relay 用例会丢失越权防护——属于"偏离用户原意、引入未授权权衡"
   的改动，按纪律须先确认而非擅自执行。
3. model 域用例还独有分页/count/candidates 等能力，relay 用例不具备；硬切会丢功能。
4. 还存在**第三套**入口 `relay/interfaces/RelayMappingController`（`/api/relay/mappings`、`/api/relay/aliases`），
   任务书未提及；贸然重构易牵连未盘点的调用面。

**真正的技术债（记录，未在本 slice 处理）**：同一张表上挂了两套 `@Entity`+SpringData 仓储+RepositoryImpl，
是真实的脆弱点（双写路径、约束/软删语义需手工对齐）。但收敛持久化层属于有回归风险的结构调整，
应在能跑集成测试的环境下单独成一个 slice 做，并以本 slice 新增的闭环测试作为安全网。

## R6B-D3：命脉验收以集成测试落地，但本沙箱无法执行（仅 CI 可跑）

**交付**：`ModelMappingCrossDomainIT`（`src/test/java/com/nexa/mapping/`）两条用例：
- 超管 `POST /api/platform_model_mappings` 写 A→B → relay 域 `PlatformModelMappingRepository`
  + `TwoLayerModelResolver` 解析出 B；
- 用户 `POST /api/user/self/model_aliases` 写 C→A → relay 域 `UserModelAliasRepository`
  + `TwoLayerModelResolver` 解析出 A。

这正是任务书要求的"超管写→转发读"端到端护栏；它也锁死了 D1 的"单表共享"契约——未来任一侧改去
读写另一张表，本测试立即变红。

**环境限制（非代码问题）**：本沙箱内核未实现 SysV 共享内存（`shmget` → `Function not implemented`），
`postgres:16-alpine` 的 `initdb` 无法 bootstrap，PG 起不来。故所有 `@SpringBootTest`+真 PG 的集成测试
（含既有 `UserControllerIT` 与本新增 IT）**在此环境无法运行**，只能在具备 PG 的 CI 执行。
已验证：测试**编译通过**；全量**单元测试 537 通过**（`mvn test -Dtest='!*IT'`）。
