# PRD — 预填分组（FL-prefill）

> 分片：预填分组（model/tag/endpoint 三类型）。对应流程图 `flow/FL-prefill.md`（3 图）、状态矩阵 `PAGE-STATE-MATRIX.md`。
> 数据对象字段一律复用 `DATA-MODEL.md §14 PrefillGroup`。
> 跨切面契约见 `OVERALL-FLOW.md §3`：所有 `/api/prefill_group` 路由挂 AdminAuth（仅管理员）。与渠道/令牌配置衔接见 `FL-channel.md`（本片只画分组侧）。
> 本片覆盖功能 ID：**F-2012 / F-2013 / F-2014 / F-2015**。

---

## PF-1 预填分组创建（Name+Type 非空 + 类型枚举 + 全局名称查重）

- **功能 ID / 优先级**：F-2012 / P1
- **来源**：FC-039（`controller/prefill_group.go:24-48 CreatePrefillGroup`：校验 Name/Type 非空、`IsPrefillGroupNameDuplicated(0,name)` 全局查重）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 预填分组
- **触发**：管理员 `POST /api/prefill_group` 提交新建分组表单

### 1. 场景
管理员创建一个预填分组，把常用的模型/标签/端点集合存为一个命名分组，后续在渠道/令牌配置时一键填充。创建是一条串行校验闸门链（短路返回）：先 Name 与 Type 双非空校验，再 Type 枚举校验，再全局名称查重（传 id=0 表示对所有未软删记录查重），全部通过才落库，`Items` 以 JSON 数组存储。

### 2. 前置条件
- 操作者为管理员（AdminAuth；非管理员 403）。
- 提交体含 `Name`、`Type`、`Items`。
- `Type` 须取 `model/tag/endpoint` 之一。

### 3. 主流程（对应 PF-1 节点 pc_in→pc_out）
1. `POST /api/prefill_group`（pc_in），过 AdminAuth（pc_auth）。
2. 校验 `Name` 与 `Type` 均非空（pc_name）：任一空 → 短路返回「组名称和类型不能为空」。
3. 校验 `Type∈model/tag/endpoint`（pc_type）：非法 → 返回类型不合法。
4. 调 `IsPrefillGroupNameDuplicated(0, name)`（pc_dup）：已存在 → 返回「组名称已存在」。
5. 未占用 → `Create` 落库，`Items` 存 JSON 数组（pc_save）→ 创建成功，出现在列表（pc_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 非管理员调用 | pc_auth-否 | AdminAuth 拦截 | 越权拒绝态（403） |
| `Name` 或 `Type` 任一为空 | pc_name-空 | 短路返回，不落库 | 字段为空态「组名称和类型不能为空」 |
| `Type` 不在枚举内 | pc_type-非法 | 拒绝，不落库 | 类型非法态 |
| 名称全局已存在 | pc_dup-已存在 | 拒绝，不落库 | 重名拒绝态「组名称已存在」 |

### 5. 数据对象（复用 DATA-MODEL §14 PrefillGroup）
- **写** `PrefillGroup`：`Name(name)`（`size:64;uniqueIndex:uk_prefill_name`，软删除条件唯一）、`Type(type)`（枚举 `model/tag/endpoint`）、`Items(items)`（JSONValue，字符串数组如 `["gpt-4o","gpt-3.5-turbo"]`）、`Description(description)`、`CreatedTime/UpdatedTime`。
- **查重** `IsPrefillGroupNameDuplicated(0, name)`：id=0 表示全局（不排除任何记录）查重。

### 6. 验收标准
- [ ] 非管理员调用 `POST /api/prefill_group` → 返回 403，不创建记录。
- [ ] `Name=""` 或 `Type=""` → 返回「组名称和类型不能为空」，不落库。
- [ ] `Type="invalid"`（非 model/tag/endpoint）→ 返回类型不合法，不落库。
- [ ] 用一个已存在的 `Name` 创建 → 返回「组名称已存在」，不新增记录。
- [ ] 合法创建 → DB 新增一条 `PrefillGroup`，`Items` 以 JSON 数组持久化，且在列表（`GetPrefillGroups`）中可查到。

### 7. 所触及页面状态（对齐 PF-1 创建分组）
越权拒绝态（403）· 字段为空态（Name/Type 任一空）· 类型非法态（Type 不在枚举）· 重名拒绝态（全局查重命中）· 创建成功态（落库，Items 存 JSON，列表可查）。

---

## PF-2 预填分组更新（缺 id 校验 + 排除自身的名称冲突）

- **功能 ID / 优先级**：F-2013 / P2
- **来源**：FC-039（`controller/prefill_group.go:51-75 UpdatePrefillGroup`：校验 `Id!=0`、`IsPrefillGroupNameDuplicated(g.Id, name)` 排除自身查重）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 预填分组
- **触发**：管理员 `PUT /api/prefill_group` 提交编辑分组

### 1. 场景
管理员编辑已有分组。与 PF-1 创建链的关键差异在查重的「排除自身」：先校验 `Id!=0`（缺则「缺少组 ID」），再确认分组存在，再调 `IsPrefillGroupNameDuplicated(g.Id, name)`——传自身 Id 排除本记录查重。因此把名称改成别的分组已用的名会冲突，而改回原名（即等于自身名）不算冲突可成功。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- 提交体含 `Id`（非 0）、`Name`、`Items`、`Type`。
- 目标分组存在（未被软删）。

### 3. 主流程（对应 PF-2 节点 pu_in→pu_out）
1. `PUT /api/prefill_group`（pu_in），判 `Id!=0`（pu_id）：Id=0 → 返回「缺少组 ID」。
2. 判分组存在（pu_exist）：不存在 → 返回「分组不存在」。
3. 调 `IsPrefillGroupNameDuplicated(g.Id, name)` 排除自身查重（pu_dup）：命中他组同名 → 返回「组名称已存在」。
4. 改回原名/无冲突 → `Update` 更新 `Name/Items/Type`（pu_upd）→ 更新成功，内容回读为新值（pu_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `Id=0`（缺 id） | pu_id-Id=0 | 拒绝，不更新 | 缺 ID 态「缺少组 ID」 |
| id 无对应记录 | pu_exist-不存在 | 拒绝 | 分组不存在态 |
| 改名命中他组同名 | pu_dup-命中 | 拒绝，不更新 | 改名冲突态「组名称已存在」 |
| 改回原名（=自身名） | pu_dup-无冲突 | 排除自身后不算冲突，允许 | 改回原名成功态 |

### 5. 数据对象（复用 DATA-MODEL §14 PrefillGroup）
- **读/写** `PrefillGroup`：`Id(id)`（主键，非 0 必填）、`Name(name)`、`Items(items)`（JSON 数组）、`Type(type)`、`UpdatedTime`。
- **查重** `IsPrefillGroupNameDuplicated(g.Id, name)`：传自身 `Id` 以排除本记录，仅在「他组」命中才算冲突。

### 6. 验收标准
- [ ] 提交 `Id=0` 的更新 → 返回「缺少组 ID」，不更新。
- [ ] 更新一个不存在的 id → 返回「分组不存在」。
- [ ] 把分组改名为另一已存在分组的名称 → 返回「组名称已存在」，不更新。
- [ ] 不改名（Name 维持原值，即等于自身名）提交更新 → 成功（排除自身查重不冲突）。
- [ ] 合法更新 → `Name/Items/Type` 回读为新提交值。

### 7. 所触及页面状态（对齐 PF-2 更新分组）
缺 ID 态（Id=0）· 分组不存在态 · 改名冲突态（命中他组同名）· 改回原名成功态（排除自身查重不冲突）· 更新成功态（Name/Items/Type 回读为新值）。

---

## PF-3 按 type 下拉填充 + 软删除（消费侧填充 / 维护侧删除）

- **功能 ID / 优先级**：F-2014、F-2015 / P2
- **来源**：FC-040（`GetPrefillGroups` 按 `c.Query(type)` 过滤 `GetAllPrefillGroups`）、FC-041（`DeletePrefillGroup` 调 `DeletePrefillGroupByID` 软删除）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 预填分组
- **触发**：前端配置渠道/令牌时 `GET /api/prefill_group?type=xxx` 取数填充；管理员 `DELETE /api/prefill_group/:id` 删除

### 1. 场景
两条独立操作共享同一分组数据源：**消费侧**——前端配渠道/令牌时按 `type` 拉对应类型分组（`type=model` 仅返回 model 类型），选中后用其 `Items` 一键填充模型多选框/令牌配置，减少重复输入；**维护侧**——管理员删除走软删除（`DeletePrefillGroupByID`，非物理移除，保留历史），删除后 `GetPrefillGroups` 不再返回该组，非数字 id 返回参数错误。

### 2. 前置条件
- 取数与删除均受 AdminAuth 保护。
- 取数请求带 `type` 查询参数（`model/tag/endpoint`）。
- 删除请求 `id` 须为数字。

### 3. 主流程（对应 PF-3 节点 pd_root→pd_fill/pd_gone）
1. 分组数据源 `PrefillGroup`（pd_root），判操作（pd_op）。
2. 前端配置取数 → `GET ?type=xxx`（pd_fetch），按 `type` 过滤（pd_filt）：`type=model` 返 model 类型列表（pd_m）、`tag` 返 tag 列表（pd_t）、`endpoint` 返 endpoint 列表（pd_e）→ 选中分组用 `Items` 一键填充多选框（pd_fill，终态）。
3. 管理员删除 → `DELETE /:id`（pd_del），判 id 为数字（pd_idchk）：非数字 → 返回参数错误（pd_err）；是 → `DeletePrefillGroupByID` 软删除保留历史（pd_soft）→ 列表不再返回该组（pd_gone，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 取数带 `type=model` | pd_filt-model | 仅返回 model 类型分组 | type 过滤取数态 |
| 选中分组填充 | pd_fill | 用 `Items` 填模型多选框/令牌配置 | 一键填充态 |
| 删除 id 非数字 | pd_idchk-非数字 | 返回参数错误，不删 | 非数字 id 态 |
| 删除合法 id | pd_soft | `DeletePrefillGroupByID` 软删除，保留历史 | 软删除态 → 删除后消失态 |

### 5. 数据对象（复用 DATA-MODEL §14 PrefillGroup）
- **读过滤** `PrefillGroup` by `Type(type)`：`GET ?type=model` 仅返回 `Type=model` 的分组列表；选中后取其 `Items(items)`（JSON 数组）填充前端多选框。
- **软删除** `PrefillGroup.DeletedAt`（gorm.DeletedAt，`index`）：`DeletePrefillGroupByID` 设置软删除标记，非物理删，历史保留；`GetPrefillGroups` 不返回已软删记录。

### 6. 验收标准
- [ ] `GET /api/prefill_group?type=model` → 仅返回 `Type=model` 的分组，不含 tag/endpoint 类型。
- [ ] 选中某分组后，前端可用其 `Items` 数组填充模型多选框（填充值等于 `Items` 内容）。
- [ ] `DELETE /api/prefill_group/abc`（非数字 id）→ 返回参数错误，不删除任何记录。
- [ ] `DELETE /api/prefill_group/:id`（合法 id）→ 该记录 `DeletedAt` 被置（软删除），DB 行仍在（非物理删）。
- [ ] 软删后 `GetPrefillGroups` 不再返回该组。

### 7. 所触及页面状态（对齐 PF-3 下拉填充+软删除）
type 过滤取数态（type=model 仅返回 model 类型）· 一键填充态（Items 填模型多选框/令牌配置）· 非数字 id 态（删除参数错误）· 软删除态（保留历史不物理删）· 删除后消失态（GetPrefillGroups 不再返回）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-2012 | PF-1 |
| F-2013 | PF-2 |
| F-2014 | PF-3 |
| F-2015 | PF-3 |

无 `[BLOCKER]`。
