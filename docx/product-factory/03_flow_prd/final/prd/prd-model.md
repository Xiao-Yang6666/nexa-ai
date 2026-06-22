# PRD — 模型广场与元数据（FL-model）

> 分片：模型广场与元数据 D9。对应流程图 `flow/FL-model.md`、状态矩阵 `PAGE-STATE-MATRIX.md §A / §I`。
> 数据对象字段一律复用 `DATA-MODEL.md`（`ModelMeta`=`model/model_meta.go Model`、`VendorMeta`=`model/vendor_meta.go Vendor`、`User §1`）逐字段抽取的真实字段。
> 关键函数/常量：`CreateModelMeta`、`UpdateModelMeta`、`IsModelNameDuplicated`、`RefreshPricing`、`SyncUpstreamPreview`、`SyncUpstreamModels`、`GetMissingModels`、`GetUserModels`、`GetUserUsableGroups`、`GetGroupEnabledModels`、`StringsContains`、`filterPricingByUsableGroups`、`GetRankingsSnapshot`、`HeaderNavModuleAuth`。
> 本片覆盖功能 ID：**F-3015 / F-3016 / F-3019 / F-3020 / F-3021 / F-3024 / F-3025 / F-3023 / F-3022 / F-3030 / F-3031 / F-3032 / F-3033 / F-3034 / F-3035**。
> 兼容层+计费+模型分级反溯（2026-06）新增块：**ML-6 对外模型分级商品目录 / ML-7 两层模型映射 C→A→B / ML-8 模型权限全开**（权威源 `COMPAT-BILLING-DECISIONS.md §2/§3/§5`、`reviews/COMPAT-LAYER-ARCHITECTURE.md §3-§5`、`reviews/BILLING-MODEL-ARCHITECTURE.md §1/§4`、`DATA-MODEL.md §16/§17/§18`）。

---

## ML-1 模型元数据创建/更新（名称查重 + status_only 旁路 + 刷新定价）

- **功能 ID / 优先级**：F-3015、F-3016 / P1
- **来源**：FC-080（`controller/model_meta.go CreateModelMeta`：`ModelName` 空校验、`IsModelNameDuplicated(0,name)`、Insert 后 `RefreshPricing()`；`UpdateModelMeta`：`status_only=true` 仅 `Update(status)`，否则 `IsModelNameDuplicated(id,name)` 后 `m.Update()`）
- **角色 / Owner**：管理员；Owner 模块 = 模型广场与元数据
- **触发**：管理员在模型管理页新增模型条目，或编辑模型 / 切换上下架（过 AdminAuth）

### 1. 场景
管理员提交一个模型条目，入口分「创建」与「更新」两路并合流到刷新定价。创建路先判 `ModelName` 是否非空，再 `IsModelNameDuplicated(0,name)` 全表查重，唯一才 Insert。更新路先判 `Id` 是否为 0，再判 `status_only`：为 `true` 时仅 `Update(status)`（上/下架专用，不动其他字段，防误清 `Description/Icon/Tags/Endpoints` 等），为 `false` 时 `IsModelNameDuplicated(id,name)`（排除自身 id）后全量 `m.Update()`。三种落库（Insert / 仅状态 / 全量）都在尾段统一调 `RefreshPricing()` 刷新定价缓存，使广场与价格页随即反映新条目。

### 2. 前置条件
- 管理员会话过 C1 + AdminAuth（`/models` 挂 AdminAuth）。
- 创建必填 `ModelName`（`gorm:size:128;not null;uniqueIndex`）。
- 更新需带有效 `Id`（`Id=0` 视为缺 ID）。
- `status_only` 标志决定走仅状态旁路还是全量更新。

### 3. 主流程（对应 ML-1 节点 mm_in→mm_ok）
1. 管理员提交条目（mm_in），判创建还是更新（mm_route）。
2. 创建路：判 `ModelName` 是否为空（mm_cname）→为空拒绝（mm_e1）；非空 `IsModelNameDuplicated(0,name)` 查重（mm_cdup）→重名拒绝（mm_e2）；唯一则 Insert `Model` 记录（mm_insert）。
3. 更新路：判 `Id=0`（mm_id）→是则缺 ID 拒绝（mm_e3）；否判 `status_only=true`（mm_status）→是走仅 `Update(status)` 旁路（mm_sonly）；否 `IsModelNameDuplicated(id,name)` 排除自身查重（mm_udup）→重名拒绝（mm_e2）→唯一则全量 `m.Update()`（mm_update）。
4. Insert / 仅状态 / 全量三路合流调 `RefreshPricing()` 刷新定价缓存（mm_refresh）→元数据落库且定价已刷新态（mm_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 创建 `ModelName` 为空 | mm_cname-是 | 拒绝写库 | 「模型名称不能为空」 |
| 查重命中（创建/更新） | mm_cdup / mm_udup-重名 | 拒绝写库 | 「模型名称已存在」 |
| 更新 `Id=0` | mm_id-是 | 拒绝更新 | 「缺少模型 ID」 |
| 更新 `status_only=true` | mm_status-是 | 仅 `Update(status)` 不动其他字段 | status_only 旁路（仅上/下架） |
| 更新 `status_only=false` 且唯一 | mm_udup-唯一 | 全量 `m.Update()` | 全量更新成功 |

### 5. 数据对象（复用 DATA-MODEL `ModelMeta`=`model/model_meta.go Model`）
- **写/校验** `Model`：`ModelName(model_name) string`（`gorm:size:128;not null;uniqueIndex:uk_model_name_delete_at,priority:1`，查重键）、`Status(status) int`（`gorm:default:1`，`status_only` 仅改此字段）。
- **全量更新字段** `Model`：`Description(description) string text`、`Icon(icon) string varchar(128)`、`Tags(tags) string varchar(255)`、`VendorID(vendor_id) int index`、`Endpoints(endpoints) string text`、`NameRule(name_rule) int default:0`、`UpdatedTime(updated_time) int64`。
- **不动字段（status_only 防误清）**：上述 `Description/Icon/Tags/Endpoints/VendorID/NameRule` 在 `status_only=true` 时不写。

### 6. 验收标准
- [ ] 创建时 `ModelName` 为空 → 返回「模型名称不能为空」，不写 `Model` 表，`RefreshPricing` 不被调用。
- [ ] 创建时 `IsModelNameDuplicated(0,name)` 命中 → 返回「模型名称已存在」，不 Insert。
- [ ] 创建唯一 `ModelName` → Insert 成功且随后 `RefreshPricing()` 被调用。
- [ ] 更新时 `Id=0` → 返回「缺少模型 ID」，不更新。
- [ ] 更新 `status_only=true` → 仅 `Status` 被写，`Description/Icon/Tags/Endpoints/VendorID` 字段值保持更新前不变。
- [ ] 更新 `status_only=false` 且 `IsModelNameDuplicated(id,name)`（排除自身）未命中 → 全量 `m.Update()` 成功并 `RefreshPricing()` 被调用。

### 7. 所触及页面状态（对齐 §I「模型元数据创建/更新」）
模型条目编辑态（创建/更新）· 名称为空态（创建拒绝）· 重名态（创建/更新查重命中）· 缺 Id 态（更新）· status_only 旁路态（仅改状态不清字段）· 全量更新态 · 元数据落库 + 定价刷新态（终态）。

---

## ML-2 上游模型同步（预览只读差异 → 应用写库，ETag 条件请求 + SyncOfficial 跳过）

- **功能 ID / 优先级**：F-3019、F-3020、F-3021 / P1
- **来源**：FC-082/FC-083（`controller/model_sync.go SyncUpstreamPreview/SyncUpstreamModels`：`basellm.github.io` 上游、`getUpstreamURLs` 按 locale 选 URL、ETag 条件请求、`GetMissingModels`、`SyncOfficial=0` 跳过覆盖、返回 `created_models/created_vendors/updated_models/skipped_models` 计数）
- **角色 / Owner**：管理员；Owner 模块 = 模型广场与元数据
- **触发**：管理员点击「同步上游模型」（先预览差异，确认后应用）

### 1. 场景
同步分两阶段。阶段 1 `SyncUpstreamPreview` 按 `locale`（`en/zh-CN/zh-TW/ja`）选上游 URL，非法 locale 回退默认 URL；带 ETag 条件请求，命中 304 走 bodyCache 复用上次响应，否则拉取 `basellm.github.io`，拉取失败返回「获取上游模型失败」；预览**只计算并返回上游 vs 本地差异，不写任何记录**，弹窗供管理员勾选。阶段 2 管理员确认后 `SyncUpstreamModels` 创建本地缺失模型并按需覆盖字段，逐模型判 `SyncOfficial=0` 的本地模型跳过覆盖（计入 `skipped_models`），返回 `created_models/created_vendors/updated_models/skipped_models` 四项计数；放弃则本地不变；无缺失且无 overwrite 时直接返回零计数不请求上游。`GetMissingModels` 提供被渠道引用但无元数据的模型名用于驱动同步。

### 2. 前置条件
- 管理员会话过 C1 + AdminAuth（modelsRoute）。
- 预览阶段不修改任何 `Model` / `Vendor` 记录。
- 应用阶段对 `SyncOfficial=0` 的本地模型只跳过覆盖、不删除。

### 3. 主流程（对应 ML-2 节点 sy_open→sy_ok/sy_cancel）
1. 管理员点同步（sy_open）→阶段 1 `SyncUpstreamPreview` 拉差异（sy_pre）。
2. 判 `locale` 合法（sy_loc）→非法回退默认 URL（sy_fallback）；合法按 locale 选 URL（sy_url）。
3. 两路汇到 ETag 判定（sy_etag）→命中 304 走 bodyCache（sy_cache）；未命中判上游拉取成功（sy_fetch）→失败返回「获取上游模型失败」（sy_ferr）；成功计算上游 vs 本地差异（sy_diff）。
4. 预览弹窗展示差异不写库（sy_show）→判用户勾选并确认（sy_confirm）：放弃→取消同步本地未变态（sy_cancel）；确认→阶段 2 `SyncUpstreamModels` 写库（sy_apply）。
5. 逐模型判 `SyncOfficial=0`（sy_skip）→是计入 `skipped_models` 不覆盖（sy_skipped）；否创建缺失/覆盖字段（sy_create）。
6. 两路汇总 `created/updated/skipped` 计数（sy_count）→同步完成返回各项计数态（sy_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `locale` 非法 | sy_loc-否 | 回退默认 URL | locale 回退态 |
| ETag 命中 304 | sy_etag-是 | 走 bodyCache 复用 | ETag 命中复用态 |
| 上游拉取失败 | sy_fetch-失败 | 中止预览 | 「获取上游模型失败」 |
| 用户放弃应用 | sy_confirm-放弃 | 不写库 | 取消同步态（本地未变） |
| 本地模型 `SyncOfficial=0` | sy_skip-是 | 跳过覆盖计入 `skipped_models` | 跳过覆盖态 |
| 无缺失且无 overwrite | （阶段 2 短路） | 不请求上游直接返回 | 零计数同步态 |

### 5. 数据对象（复用 DATA-MODEL `ModelMeta`=`Model` + `VendorMeta`=`Vendor`）
- **覆盖跳过键** `Model.SyncOfficial(sync_official) int`（`gorm:default:1`，`=0` 时跳过覆盖计入 `skipped_models`）。
- **创建/覆盖字段** `Model`：`ModelName(model_name)`、`VendorID(vendor_id) int index`、`Description(description)`、`Icon(icon)`、`Tags(tags)`、`Endpoints(endpoints)`、`CreatedTime/UpdatedTime int64`。
- **供应商创建** `Vendor`：`Name(name) string`（`gorm:size:128;not null;uniqueIndex:uk_vendor_name_delete_at`，缺失供应商按此创建计入 `created_vendors`）、`Icon(icon)`、`Status(status) int default:1`。
- **缺失驱动** `GetMissingModels` 返回被渠道引用但无 `Model` 记录的模型名数组（驱动同步入口）。

### 6. 验收标准
- [ ] 预览 `SyncUpstreamPreview` 调用后 `Model`/`Vendor` 表记录数与字段不变（只返回差异）。
- [ ] `locale` 非法 → 回退默认 URL 仍能拉取，不报错。
- [ ] ETag 命中 304 → 走 bodyCache，不重复全量下载，预览仍返回差异。
- [ ] 上游拉取失败 → 返回「获取上游模型失败」，不进入应用阶段。
- [ ] 应用阶段对 `SyncOfficial=0` 的本地模型 → 不覆盖该模型字段且其名计入返回的 `skipped_models`。
- [ ] 应用成功 → 返回体含 `created_models/created_vendors/updated_models/skipped_models` 四项计数；无缺失且无 overwrite 时四项为零且不请求上游。

### 7. 所触及页面状态（对齐 §I「上游模型同步」）
预览拉取态（只读）· locale 回退态（非法→默认 URL）· ETag 命中复用态（304 bodyCache）· 上游拉取失败态 · 差异预览弹窗态（不写库）· 取消同步态（本地未变 终态）· 跳过覆盖态（SyncOfficial=0）· 创建/覆盖态 · 同步完成态（created/updated/skipped 计数 终态）。

---

## ML-3 用户可见模型列表（按用户可用分组聚合去重）

- **功能 ID / 优先级**：F-3025、F-3024 / P1（F-3025）/ P2（F-3024）
- **来源**：FC-085（`controller/user.go GetUserModels`：`GetUserUsableGroups(user.Group)`、`GetGroupEnabledModels`、`StringsContains` 去重；`controller/model.go DashboardListModels` 返回 channelId2Models）
- **角色 / Owner**：登录用户；Owner 模块 = 模型广场与元数据
- **触发**：登录用户查询自己可调用的模型（`/self/models` 过 UserAuth）

### 1. 场景
用户查询可调用模型时 `GetUserModels` 先取 `GetUserUsableGroups(user.Group)` 得到该用户所属的可用分组集合，再对每个可用分组并行调 `GetGroupEnabledModels` 取该分组下启用的模型集，最后用 `StringsContains` 去重合并成一个不含重复项的模型名数组返回。用户不存在时返回错误；可用分组为空时返回空列表。这是「分组扇出 → 各组取启用模型 → 去重合并」的数据聚合流。`DashboardListModels` 是其总览侧伴随接口，返回 `channelId→models` 映射用于广场总览。

### 2. 前置条件
- 用户会话过 C1 + UserAuth（selfRoute `/self/models`）。
- 仅返回该用户所属可用分组的启用模型。
- 去重以 `StringsContains` 判定，不依赖排序。

### 3. 主流程（对应 ML-3 节点 um_open→um_ok/um_none）
1. 用户查询可调用模型（um_open），判用户是否存在（um_user）→不存在报错（um_err）。
2. 存在则 `GetUserUsableGroups(user.Group)`（um_groups），判可用分组是否为空（um_empty）→空则无可用分组空态模型列表为空（um_none）。
3. 非空则对每个分组并行取 `GetGroupEnabledModels`（um_fan）扇出到分组 A/B/N 各启用模型集（um_g1/um_g2/um_gn）。
4. 各组集合用 `StringsContains` 去重合并（um_merge）→可用模型去重列表态（um_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 用户不存在 | um_user-否 | 报错 | 用户不存在错误态 |
| 可用分组为空 | um_empty-是 | 返回空数组 | 无可用分组空态（列表空） |
| 多分组并行取启用模型 | um_fan | `GetGroupEnabledModels` 扇出 | 分组扇出取启用模型态 |
| 同名模型跨组重复 | um_merge | `StringsContains` 去重 | 去重合并后唯一列表 |

### 5. 数据对象（复用 DATA-MODEL `User §1` + `ModelMeta`=`Model`）
- **分组来源** `User.Group(group)`：`GetUserUsableGroups(user.Group)` 得到可用分组集（空集→空态）。
- **启用模型源** `Model.ModelName(model_name)` + `Model.Status(status)`：每个可用分组经 `GetGroupEnabledModels` 取启用模型名，按 `model_name` 去重。
- **总览映射** `DashboardListModels` 返回 `channelId2Models`：`Channel.Id(id)`→可用 `model_name` 列表映射（伴随接口，不参与 `GetUserModels` 去重）。

### 6. 验收标准
- [ ] 用户不存在 → `GetUserModels` 返回错误，不返回模型数组。
- [ ] `GetUserUsableGroups(user.Group)` 为空 → 返回空数组（无可用分组空态），不报错。
- [ ] 多分组下各组 `GetGroupEnabledModels` 的启用模型均被纳入合并结果。
- [ ] 同一 `model_name` 在多个分组重复出现 → 合并结果中只出现一次（`StringsContains` 去重）。
- [ ] 仅启用模型（`Status` 启用）进入结果，被下架模型不出现。
- [ ] `DashboardListModels` 返回 `channelId2Models` 映射且需 UserAuth。

### 7. 所触及页面状态（对齐 §I「用户可见模型列表」）
模型查询加载态 · 用户不存在错误态 · 无可用分组空态（列表空 终态）· 分组扇出取启用模型态（多分组并行）· 去重合并态（StringsContains）· 可用模型去重列表态（终态）。

---

## ML-4 公开价格页（按可用分组过滤定价 + 分组倍率 + 模块可见性）

- **功能 ID / 优先级**：F-3023 / P1
- **来源**：FC-065（`controller/pricing.go GetPricing`：`filterPricingByUsableGroups`、`GetUserUsableGroups`、`GetGroupGroupRatio`、`pricing_version` 常量、返回 `supported_endpoint/auto_groups/pricing_version`；`HeaderNavModuleAuth(pricing)` 模块可见性）
- **角色 / Owner**：访客 / 登录用户；Owner 模块 = 模型广场与元数据
- **触发**：访客或登录用户访问定价页

### 1. 场景
价格页是公开看板数据获取流。先经 `HeaderNavModuleAuth(pricing)` 判模块是否可见，不可见则入口隐藏。可见后按是否登录取可用分组：匿名取公开可用分组，登录取用户可用分组。可用分组为空返回空定价列表。否则 `filterPricingByUsableGroups` 按可用分组过滤模型定价：`EnableGroup` 含 `all` 的项始终展示，`group_ratio` 仅保留可用分组的倍率。最后附上 `supported_endpoint/auto_groups/pricing_version` 元信息一并返回渲染。逐级裁剪「模块可见 → 取可用分组 → 过滤定价 → 附元信息」。

### 2. 前置条件
- `pricing` 模块由 `HeaderNavModuleAuth(pricing)` 控制可见性（可对匿名公开）。
- 匿名走公开可用分组，登录走 `GetUserUsableGroups`。
- 可用分组为空时返回空定价列表。

### 3. 主流程（对应 ML-4 节点 pr_enter→pr_ok/pr_empty）
1. 访客/用户访问价格页（pr_enter），判 `HeaderNavModuleAuth(pricing)` 可见（pr_mod）→不可见则入口隐藏（pr_hide）。
2. 可见则判是否登录（pr_auth）→匿名取公开可用分组（pr_pub）；登录取用户可用分组（pr_user）。
3. 两路汇到判可用分组是否为空（pr_groups）→空则空定价列表占位态（pr_empty）。
4. 非空则 `filterPricingByUsableGroups` 过滤模型定价（pr_filter）→`EnableGroup` 含 `all` 项始终展示（pr_all）→`group_ratio` 仅保留可用分组（pr_ratio）→附 `supported_endpoint/auto_groups/pricing_version`（pr_meta）→价格页渲染态（pr_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `HeaderNavModuleAuth(pricing)` 关 | pr_mod-否 | 入口隐藏 | 模块不可见隐藏态 |
| 匿名访问 | pr_auth-否 | 取公开可用分组 | 匿名取公开分组态 |
| 登录访问 | pr_auth-是 | `GetUserUsableGroups` | 登录取用户分组态 |
| 可用分组为空 | pr_groups-是 | 返回空定价列表 | 空定价列表态（占位） |
| `EnableGroup` 含 `all` | pr_all | 始终展示该项 | all 分组常显态 |

### 5. 数据对象（复用 DATA-MODEL `User §1` + `ModelMeta`=`Model`）
- **可见性控制** `HeaderNavModuleAuth(pricing)`：模块开关，关则入口隐藏（非数据字段，模块级护栏）。
- **分组过滤键** `User.Group(group)`：登录经 `GetUserUsableGroups` 取可用分组，匿名取公开可用分组；`filterPricingByUsableGroups` 据此裁剪。
- **定价主体** `Model.ModelName(model_name)` + `Model.Status(status)`：仅展示对应可用分组启用的模型定价；`EnableGroup` 含 `all` 的项始终展示。
- **返回元信息**：`group_ratio`（仅保留可用分组的分组倍率，`GetGroupGroupRatio`）、`supported_endpoint`、`auto_groups`、`pricing_version`（版本常量）。

### 6. 验收标准
- [ ] `HeaderNavModuleAuth(pricing)` 关闭 → 价格页入口隐藏，不返回定价。
- [ ] 匿名访问 → 按公开可用分组过滤定价；登录访问 → 按 `GetUserUsableGroups(user.Group)` 过滤。
- [ ] 可用分组为空 → 返回空定价列表（不报错）。
- [ ] `EnableGroup` 含 `all` 的模型项 → 在过滤后结果中始终出现。
- [ ] 返回的 `group_ratio` 只包含当前可用分组的倍率，不含不可用分组。
- [ ] 返回体含 `supported_endpoint`、`auto_groups`、`pricing_version` 三项元信息。

### 7. 所触及页面状态（对齐 §A / §I「公开价格页」）
模块不可见隐藏态（pricing 关）· 匿名取公开分组态 / 登录取用户分组态 · 空定价列表态（可用分组为空 终态）· 定价过滤态（按可用分组）· all 分组常显态（EnableGroup 含 all）· 价格页渲染态（含 endpoint/auto_groups/版本 终态）。

---

## ML-5 模型排行榜公开快照（period 维度，最短取数流）

- **功能 ID / 优先级**：F-3022 / P2
- **来源**：FC-084（`controller/rankings.go GetRankings`：`service.GetRankingsSnapshot(DefaultQuery(period,week))`；`api-router.go /rankings` 挂 `HeaderNavModuleAuth(rankings)`）
- **角色 / Owner**：访客 / 登录用户；Owner 模块 = 模型广场与元数据
- **触发**：用户访问模型排行页

### 1. 场景
排行榜是最短的公开看板取数流：先经 `HeaderNavModuleAuth(rankings)` 判模块可见，不可见则入口隐藏；可见后校验 `period`，默认 `week`，非法 period 返回 400 + message；合法或默认则 `GetRankingsSnapshot` 取该周期快照，快照有数据则渲染排行榜，无数据则展示暂无数据占位。刻意为短链，区别于其他多级看板。

### 2. 前置条件
- `rankings` 模块由 `HeaderNavModuleAuth(rankings)` 控制可见性（可对匿名公开）。
- `period` 缺省时由 `DefaultQuery(period,week)` 取 `week`。
- 仅 `week/month` 等合法周期被接受，其余 400。

### 3. 主流程（对应 ML-5 节点 rk_enter→rk_ok/rk_empty）
1. 请求模型排行页（rk_enter），判 `rankings` 模块可见（rk_mod）→不可见则入口隐藏（rk_hide）。
2. 可见则判 `period` 是否合法（rk_period）→非法返回 400 + message（rk_400）；合法或默认 `week` 则 `GetRankingsSnapshot` 取周期快照（rk_snap）。
3. 判快照是否有数据（rk_data）→无则排行空态暂无数据占位（rk_empty）；有则排行榜渲染态 period 维度（rk_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `HeaderNavModuleAuth(rankings)` 关 | rk_mod-否 | 入口隐藏 | 排行入口隐藏态 |
| `period` 非法 | rk_period-非法 | 返回 400 | 非法 period 态（400+message） |
| `period` 缺省 | rk_period-默认 | `DefaultQuery` 取 `week` | 默认 week 取快照 |
| 快照无数据 | rk_data-无 | 返回空快照 | 排行空态（占位） |
| 快照有数据 | rk_data-有 | 渲染快照 | 排行榜渲染态 |

### 5. 数据对象（复用 排行快照运行态，无对应持久化 GORM 表）
- **可见性控制** `HeaderNavModuleAuth(rankings)`：模块开关，关则入口隐藏。
- **查询维度** `period`：`DefaultQuery(period,week)` 缺省取 `week`，合法值 `week/month`，非法 400。
- **快照来源** `GetRankingsSnapshot(DefaultQuery(period,week))`：按周期返回排行快照 `data`（聚合自用量数据，非单表字段），有数据→渲染，无数据→空态。

### 6. 验收标准
- [ ] `HeaderNavModuleAuth(rankings)` 关闭 → 排行入口隐藏，不返回快照。
- [ ] 不传 `period` → `DefaultQuery` 取 `week` 并返回 week 维度快照。
- [ ] 传非法 `period` → 返回 400 + message，不取快照。
- [ ] 合法 `period`（week/month）且有数据 → 返回该周期排行快照 `data`。
- [ ] 合法 `period` 但快照无数据 → 返回空快照（排行空态），不报错。

### 7. 所触及页面状态（对齐 §A / §I「模型排行榜」）
排行入口隐藏态（rankings 模块关）· 非法 period 态（400+message）· 取快照加载态（默认 week）· 排行空态（暂无数据占位 终态）· 排行榜渲染态（period 维度快照 终态）。

---

## ML-6 对外模型分级商品目录（品质拆分独立定价 + 上下架）

- **功能 ID / 优先级**：F-3030、F-3031 / P0（F-3030）/ P1（F-3031）
- **来源**：COMPAT-BILLING-DECISIONS §3（品质分级）+ §4（售价挂对外模型 A）；BILLING-MODEL-ARCHITECTURE §1（PublicModel 与 PlatformModelMapping 一对一互补、ADR-BILL-01a 视图聚合、ADR-BILL-05 红线）；DATA-MODEL §16 PublicModel
- **角色 / Owner**：超管 / 管理员（配商品目录）/ 访客·登录用户（看公开目录）；Owner 模块 = 模型广场与元数据
- **触发**：超管在「成本/售价配置页」新增/编辑一个对外模型条目（品质档 + 基准售价倍率 + 上下架）

### 1. 场景
对外模型（公开名 A）是面向客户的「商品」语义，与 PlatformModelMapping 的「路由」语义一对一互补（同以 A 为业务键，见 ML-7）。**品质/体验不同的对外模型拆成不同 A 分开卖、分别定价**（DECISIONS §3 关键决策）：`opus-4.8`（满血）、`opus-4.8-max`（增强）、`opus-4.8-air`（经济）= `PublicModel` 三条独立记录，三个 `PublicName`、三个 `BasePriceRatio`、各自 `QualityTier` 标签（`full`/`max`/`air`=旗舰/增强/经济，纯展示分类不参与计费数值）。每条 A 在 PlatformModelMapping 里各自映射到**对应品质的真实模型 B**。**红线（ADR-BILL-05）**：品质不同的渠道绝不混进同一个 A 的兜底池——品质差异通过「拆成不同 A→不同 B」实现，而非「同一 A 下混不同品质渠道」（否则客户花满血钱拿残血货，成本算不清）。`BasePriceRatio` 是对客户恒定的基准售价倍率（口径 = `GetModelRatio(A)`），保存时同步刷 `model_ratio` KV（ADR-BILL-01a 视图聚合，计费链路零改动，见 prd-billing BL-7）。`Enabled=false` 即下架、不进客户可见目录。**对外商品目录的唯一权威** = `Enabled=true AND DeletedAt IS NULL` 的 `public_name` 集（取代旧「公开名全集」临时口径）。

### 2. 前置条件
- 配商品目录走超管/管理员会话过 C1 + AdminAuth/RootAuth（客户无写入口）。
- `PublicName` 全表唯一（`uniqueIndex:uk_public_name`）；品质不同必须建独立记录（不复用同一 PublicName）。
- 保存 `BasePriceRatio`/`BasePrice` 时同步刷 `model_ratio`/`model_price` KV（计费口径一致性，ADR-BILL-01a）。

### 3. 主流程（对应 ML-6 节点 pm_in→pm_ok）
1. 超管提交对外模型条目（pm_in），判 `PublicName` 是否非空（pm_name）→空拒绝（pm_e1）。
2. 非空判 `PublicName` 是否已存在（pm_dup）→重名拒绝（pm_e2）；唯一则判同名同品质混档（pm_quality）→若试图把不同品质塞进同一 A 提示拆分（pm_split，红线护栏）。
3. 选品质档 `QualityTier`（full/max/air，pm_tier）→填基准售价倍率 `BasePriceRatio`（或按次 `UsePrice=true` 填 `BasePrice`）（pm_price）。
4. Insert/Update `PublicModel` 记录（pm_persist）→同步刷 `model_ratio`/`model_price` KV（pm_sync）。
5. 判上下架 `Enabled`（pm_enabled）→true 进对外可见目录（pm_on）；false 下架不进目录（pm_off）→对外模型已配置态（pm_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `PublicName` 为空 | pm_name-空 | 拒绝写库 | 「对外模型名不能为空」 |
| `PublicName` 重名 | pm_dup-命中 | 拒绝写库 | 「对外模型名已存在」 |
| 试图同一 A 混不同品质 | pm_quality-混档 | 提示拆成独立 A | 红线护栏：「品质不同请拆独立对外模型」 |
| 选品质档 | pm_tier | 写 `QualityTier`（full/max/air） | 品质档分类态（纯展示） |
| `UsePrice=true` 按次价 | pm_price-按次 | 写 `BasePrice` 同步 `model_price` KV | 按次/固定价态 |
| `Enabled=false` | pm_enabled-否 | 下架不进目录 | 对外模型下架态 |

### 5. 数据对象（复用 DATA-MODEL §16 PublicModel）
- **商品键** `PublicModel.PublicName(public_name)`（`varchar(255);uniqueIndex:uk_public_name`，= A，与 `PlatformModelMapping.PublicName` 同键）。
- **品质档** `PublicModel.QualityTier(quality_tier)`（`varchar(32);index;default:'full'`，full/max/air=旗舰/增强/经济，纯展示分类不参与计费数值）。
- **售价** `PublicModel.BasePriceRatio(base_price_ratio) float64`（基准售价倍率，对客户恒定，口径 = `model_ratio`，保存同步刷 KV）、`UsePrice(use_price) bool`、`BasePrice(base_price) float64`（按次价同步刷 `model_price` KV）。
- **上下架/展示** `PublicModel.Enabled(enabled) bool default:true`（false=不进客户目录）、`DisplayName(display_name)`、`SortOrder(sort_order)`、`Description(description)`。
- **对外全集** = `Enabled=true AND DeletedAt IS NULL` 的 `public_name` 集（内存缓存 key=public_name，`InitPublicModelMap()` 装载，写时失效）。

### 6. 验收标准
- [ ] 提交空 `PublicName` → 拒绝写库，返回「对外模型名不能为空」。
- [ ] 提交已存在 `PublicName` → 拒绝写库，返回「对外模型名已存在」。
- [ ] `opus-4.8`/`opus-4.8-max`/`opus-4.8-air` 三品质 → 必须建成三条独立 `PublicModel` 记录，三个 `BasePriceRatio`、`QualityTier=full/max/air`（不允许复用同一 PublicName）。
- [ ] 保存 `BasePriceRatio` → 同步刷新 `model_ratio` KV，计费链路按 `GetModelRatio(A)` 取到同一售价倍率（ADR-BILL-01a）。
- [ ] `Enabled=false` 的对外模型 → 不进客户可见目录，对外全集不含该 `public_name`。
- [ ] 同一 A 下不得挂不同品质渠道（红线）→ 品质差异只能通过拆独立 A→独立 B 表达（与 ML-7 L2 映射一致）。

### 7. 所触及页面状态（对齐 §I「对外模型商品目录」）
对外模型编辑态（PublicName/QualityTier/BasePriceRatio/Enabled）· 名称为空拒绝态 · 重名拒绝态 · 品质混档红线护栏态（提示拆分）· 品质档分类态（full/max/air）· 倍率/按次价态（同步刷 KV）· 对外模型上架态 / 下架态 · 对外模型已配置态（终态）。

---

## ML-7 两层模型映射 C→A→B（客户层自助 C→A + 超管层底仓 A→B + 候选联想）

- **功能 ID / 优先级**：F-3032、F-3033、F-3034 / P0（F-3032/F-3033）/ P1（F-3034）
- **来源**：COMPAT-BILLING-DECISIONS §2（两层映射 C→A→B 1对1）；COMPAT-LAYER-ARCHITECTURE §3（L1→L2→选渠→L3 执行次序、ADR-COMPAT-03/05/06）+ §4（PlatformModelMapping/UserModelAlias 表）+ §5（B 不可见三道闸）；DATA-MODEL §17 PlatformModelMapping / §18 UserModelAlias
- **角色 / Owner**：登录用户（自助配 C→A，仅本 scope）/ 超管（配 A→B 客户不可见）/ 系统（链式解析）；Owner 模块 = 模型广场与元数据
- **触发**：客户在控制台配置模型别名 C→A（分组/用户级）；超管配置底仓映射 A→B；relay 请求按 C→A→B 链式解析

### 1. 场景
模型映射是链式两层、各 1对1 纯字符串替换，执行顺序固定 **先客户层 L1（C→A），再超管层 L2（A→B），最终调用 B**（ADR-COMPAT-03，选渠之前完成）。
- **C = 客户别名**（客户请求里的 model 值）；**A = 平台公开名**（客户可见、用于定价，= PublicModel.PublicName）；**B = 真实上游模型**（超管底仓实际调用名，客户永不可见）。
- **客户层 C→A（UserModelAlias，DECISIONS §2）**：客户自助配，作用域 = 分组级 + 用户级，**同一 C 命中多 scope 时 user 级 > group 级**（解析先查 user 级、未命中再查 group 级）。任一层未命中则该层恒等（C 未配则 A=C）。
- **超管层 A→B（PlatformModelMapping）**：超管配的全局底仓映射（无 group/user 维），客户全程看不到 B；A 未配底仓则 B=A。
- **候选下拉「开放输入 + 候选提示」（DECISIONS §2，F-3034）**：客户配 `Target(A)` 时，前端从**公开模型 A 全集**（= ML-6 对外目录 `Enabled=true` 的 public_name 集，ML-3/ML-4 同源）联想出候选，但**不强制白名单**——客户硬输平台没有的名字也允许存（可给浅黄提示但不拦），调用时若 A 在 L2 查不到底仓且非直通模型则自然 404（「那是客户的事」）。**候选层只返公开模型 A 全集，绝不含 B**（B 不可见三道闸之一）。
- **B 不可见三道闸**（ADR-COMPAT-05）：数据层 `PlatformModelMapping` 无任何 user 路由读 B 接口 / 序列化层客户视图只给 C→A（UserLogView 无 actual_upstream_model）/ 候选层只返公开模型 A 全集。
- **防循环**：链式解析带环检测 + 最大跳数限制（复用 `relay/helper/model_mapped.go` 内核惯例）。

### 2. 前置条件
- 客户配 C→A 走 UserAuth，写入强制 `scope_type=user AND scope_id=:caller_user_id`（或本人所属 group），禁跨 scope 写（self-scope 越权护栏）。
- 超管配 A→B 走 AdminAuth/RootAuth，客户路由无任何读 B 接口。
- 模型全开背景下映射无「自我授权越权」问题（见 ML-8）。

### 3. 主流程（对应 ML-7 节点 mp_in→mp_b）
1. 请求带 model=C 进入映射（mp_in）→L1 客户层解析（mp_l1）：先查 `scope_type=user AND scope_id=userId`（mp_user）→命中取该 Target=A（mp_uhit）；未命中再查 `scope_type=group AND scope_id=group`（mp_group）→命中取 A（mp_ghit）；都未命中则 A=C 恒等（mp_id1）。
2. 得 A 后 L2 超管层解析（mp_l2）：按 `public_name=A` 查 `PlatformModelMapping`（mp_plat）→命中且 `Enabled=true` 取 Upstream=B（mp_phit）；未命中则 B=A 恒等（mp_id2）。
3. 环检测 + 跳数判定（mp_loop）→超环/超跳报错（mp_eloop）；正常产出 B 写 `UpstreamModelName`（mp_b）→交选渠（B 作为 Ability `model` 维查询键，见 prd-channel CH-6）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| L1 user 级命中 | mp_user-命中 | 取 user 级 Target=A（优先） | 用户级映射生效态 |
| L1 group 级命中（user 未命中） | mp_group-命中 | 取 group 级 Target=A | 分组级映射生效态 |
| L1 两级都未命中 | mp_id1 | A=C 恒等 | 客户层未配恒等态 |
| L2 命中且 Enabled | mp_plat-命中 | 取 Upstream=B（客户不可见） | 底仓映射生效态 |
| L2 未命中 | mp_id2 | B=A 恒等 | 超管层未配恒等态 |
| 客户硬输平台没有的 A | mp_l2-缺底仓 | 落库不拦，调用期自然 404 | 浅黄提示不阻断（DECISIONS §2） |
| 链式成环/超最大跳数 | mp_loop-命中 | 报错中止 | 映射环/超跳错误态 |

### 5. 数据对象（复用 DATA-MODEL §17 PlatformModelMapping + §18 UserModelAlias + §16 PublicModel）
- **客户层 C→A** `UserModelAlias`：`ScopeType(scope_type)`（user/group）+ `ScopeId(scope_id)`（user_id/分组名）+ `Alias(alias)=C`（复合唯一键 `uk_scope_alias`，同作用域 C 唯一）、`Target(target)=A`（**不校验白名单**，可硬输）、`Enabled(enabled)`。优先级 user>group；越权护栏 user 路由强制 scope_id=caller。
- **超管层 A→B** `PlatformModelMapping`：`PublicName(public_name)=A`（`uniqueIndex:uk_public_name` 保证 1对1）、`UpstreamName(upstream_name)=B`（**客户绝不可见，无 user 读接口**）、`Enabled(enabled)`。作用域全局。
- **候选源** `PublicModel.PublicName`（`Enabled=true` 全集）：前端联想候选来源，**不含 B**；落库不强制白名单。

### 6. 验收标准
- [ ] 客户配 C→A 后请求 model=C → L1 解析出 A；C 未配 → A=C 恒等。
- [ ] 同一 C 同时存在 user 级与 group 级映射 → 取 user 级 Target（user>group）。
- [ ] 超管配 A→B 且 `Enabled=true` → L2 解析出 B；A 未配底仓 → B=A 恒等。
- [ ] 客户任何 UserAuth 接口（候选下拉、用量列表）均不返回 `upstream_name(B)`（三道闸）。
- [ ] 客户配 `Target` 候选下拉 → 联想自公开模型 A 全集（`Enabled=true` 的 public_name），**不含任何 B**。
- [ ] 客户硬输平台没有的 A → 允许落库（可浅黄提示），调用时 L2 查不到底仓且非直通则 404，不在配置期拦截。
- [ ] 客户写 UserModelAlias 时被强制 `scope_id=:caller_user_id`，跨 scope 写被拒。
- [ ] 链式解析成环或超最大跳数 → 报错中止，不无限循环。

### 7. 所触及页面状态（对齐 §I「两层模型映射」）
客户层映射编辑态（C→A，scope=user/group，候选联想公开 A 全集不含 B）· 用户级映射生效态 / 分组级映射生效态（user>group）· 客户层未配恒等态（A=C）· 硬输非白名单浅黄提示态（不拦）· 超管层底仓映射编辑态（A→B 客户不可见）· 底仓映射生效态 / 超管层未配恒等态（B=A）· 映射环/超跳错误态 · B 写入 UpstreamModelName 交选渠态（终态）。

---

## ML-8 模型权限全开（不按等级控模型，映射/自选无越权）

- **功能 ID / 优先级**：F-3035 / P0
- **来源**：COMPAT-BILLING-DECISIONS §5（模型权限全开，砍掉等级控权限）；BILLING-MODEL-ARCHITECTURE §4.1（取消「分组→可用模型」过滤步、ADR-BILL-06 停用 GroupSpecialUsableGroup 圈模型用途）
- **角色 / Owner**：系统（模型可用性裁决）/ 登录用户；Owner 模块 = 模型广场与元数据
- **触发**：用户查询/调用对外模型 A；客户配 C→A 映射或建 key 限定范围

### 1. 场景
中转站通行做法：**模型敞开，只按付费等级给不同折扣**（折扣见 prd-billing BL-7）。落地为**取消「分组→可用模型」的过滤步**：所有对外模型 A 对所有客户可见可用，**不再用分组圈定能用哪些模型**（停用 `GroupSpecialUsableGroup` 的「圈定模型可用性」用途，ADR-BILL-06）。Ability 路由仍按 `(UsingGroup × B)` 选渠，但「客户能不能用 A」**不再由分组决定**——只要 A 上架（`PublicModel.Enabled=true`）即可用。因此**不存在「客户自我授权越权」问题**：模型本就全开，客户自由配 C→A 映射（ML-7）、自选 key 模型/端点范围（见 prd-billing key 级减法约束）都是**做减法的自我约束**，无加法可做，天然安全。本块明确收窄旧 ML-3/ML-4 中「按可用分组过滤模型」的语义：分组维持作折扣等级（不再决定可见模型集），可见模型 = 对外目录上架全集。

### 2. 前置条件
- 对外目录已配（ML-6），`PublicModel.Enabled=true` 即对外可用。
- `GroupSpecialUsableGroup` 的「圈定模型可用性」用途停用（分组只剩折扣语义）。
- key 级模型/端点限定为可选减法约束（默认全开），非加法授权。

### 3. 主流程（对应 ML-8 节点 op_in→op_ok/op_deny）
1. 用户请求模型 A（op_in），判 A 是否上架（`PublicModel.Enabled=true`）（op_enabled）→未上架/不存在则拒绝（op_404）。
2. 上架则**不做分组→模型过滤**（op_noscope，模型全开）→判 key 级是否启用模型/端点减法限定（op_keylimit）。
3. 未启用（默认全开）→直接放行（op_pass）；启用则判 A/端点是否在该 key 允许集（op_inset）→在则放行（op_pass）；不在则 key 级减法拒绝（op_deny，自我约束非越权）。
4. 放行 → 模型可用进入计费/路由态（op_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| A 未上架/不存在 | op_enabled-否 | 拒绝 | 模型不可用态（非分组原因，纯上架） |
| A 上架 | op_enabled-是 | 不做分组→模型过滤 | 模型全开态 |
| key 未启用限定（默认） | op_keylimit-否 | 全部可用直接放行 | 全开放行态 |
| key 启用限定且 A 在允许集 | op_inset-是 | 放行 | key 减法放行态 |
| key 启用限定且 A 不在允许集 | op_inset-否 | 拒绝（自我约束） | key 级减法拒绝态（非越权） |

### 5. 数据对象（复用 DATA-MODEL §16 PublicModel + §1 User + §2 Token）
- **可用性唯一裁决** `PublicModel.Enabled(enabled)=true`：上架即对所有客户可用（取代「分组→模型」过滤）。
- **分组语义收窄** `User.Group(group)` / `Token.Group(group)`：仅作折扣等级（prd-billing BL-7），**不再决定可见模型集**；停用 `GroupSpecialUsableGroup` 圈模型用途。
- **key 级减法约束（可选）** `Token.ModelLimits(model_limits)` + `ModelLimitsEnabled`（语义从「加法授权」收窄为「减法约束」，校验对象=A）；端点同理 `Token.EndpointLimits`（DATA-MODEL Token 扩展，默认全开）。

### 6. 验收标准
- [ ] 任一上架对外模型 A（`Enabled=true`）→ 对所有分组客户均可见可用，不因分组被过滤掉。
- [ ] 不再依据 `User.Group`/`GroupSpecialUsableGroup` 圈定「能用哪些模型」（该过滤步停用）。
- [ ] 客户配 C→A 映射或自选 key 范围 → 均为减法约束，无法获得未上架/越权模型（无加法授权路径）。
- [ ] key 未启用 `ModelLimitsEnabled`（默认）→ 全部上架模型可用。
- [ ] key 启用 `ModelLimitsEnabled=true` 且请求 A 不在 `model_limits` → 被拒（自我约束，非越权拒绝）。
- [ ] A 未上架/不存在 → 拒绝原因为「未上架」而非「分组无权限」。

### 7. 所触及页面状态（对齐 §I「模型权限全开」系统内部态）
模型全开态（上架即可用，不做分组过滤）· 模型未上架不可用态（纯上架原因）· key 未启用限定全开放行态 · key 减法放行态（A 在允许集）· key 级减法拒绝态（A 不在允许集，自我约束非越权）· 模型可用进入计费/路由态（终态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-3015 | ML-1 |
| F-3016 | ML-1 |
| F-3019 | ML-2 |
| F-3020 | ML-2 |
| F-3021 | ML-2 |
| F-3024 | ML-3 |
| F-3025 | ML-3 |
| F-3023 | ML-4 |
| F-3022 | ML-5 |
| F-3030 | ML-6 |
| F-3031 | ML-6 |
| F-3032 | ML-7 |
| F-3033 | ML-7 |
| F-3034 | ML-7 |
| F-3035 | ML-8 |

无 `[BLOCKER]`。
