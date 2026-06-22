# 灵活模型组管理重构设计

## 1. 现状分析

### 1.1 当前链路
```
用户请求 → API Key 鉴权 → Token (group字段) → 根据 User.group 决定可用模型和倍率
```

**核心流程：**
1. `RelayApiKeyAuthenticationFilter` 从 Header 提取 API Key
2. 根据 `key` 查询 `Token` 聚合
3. `Token.group` 字段标识分组（如 "default"）
4. `User.group` 字段与用户等级强绑定
5. 倍率计算：`BillingCalculator.calculateSell(usage, modelRatio, groupRatio, completionRatio)`
6. `groupRatio` 目前来源于用户分组，与用户等级强耦合

**问题：**
- ❌ 模型组与用户等级强绑定，无法灵活配置
- ❌ 无法给同一供应商模型设置不同倍率策略
- ❌ 无法实现"公开分组 vs 私有分组"
- ❌ 无法按账号等级自动分配模型组访问权限
- ❌ `Token.group` 和 `User.group` 语义混乱

## 2. 设计目标

### 2.1 核心需求
1. ✅ **模型组独立管理**：供应商模型 → 模型组（配置倍率、可用模型）
2. ✅ **灵活访问控制**：公开分组 / 私有分组 / 按用户等级自动分组
3. ✅ **解耦倍率配置**：模型组级倍率 vs 用户级折扣
4. ✅ **兼容现有链路**：最小化破坏性变更

### 2.2 新架构
```
┌──────────────────────────────────────────────────────────────┐
│                     供应商模型池                                 │
│  Anthropic Claude, OpenAI GPT, Gemini, 自建渠道...              │
└────────────────┬─────────────────────────────────────────────┘
                 │ 管理员配置
                 ↓
┌──────────────────────────────────────────────────────────────┐
│                     模型组（ModelGroup）                         │
│  ┌────────────┬──────────────┬──────────────┐                 │
│  │ 基础组      │ 高级组        │ 企业组        │                 │
│  │ ratio: 1.0 │ ratio: 1.5   │ ratio: 2.0   │                 │
│  │ models: A  │ models: A+B  │ models: All  │                 │
│  └────────────┴──────────────┴──────────────┘                 │
└────────────────┬─────────────────────────────────────────────┘
                 │ 访问策略配置
                 ↓
┌──────────────────────────────────────────────────────────────┐
│            访问策略（ModelGroupAccessPolicy）                   │
│  • 公开分组：所有用户可访问                                        │
│  • 私有分组：指定用户/Token 访问                                   │
│  • 自动分组：根据用户等级自动映射                                   │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────────────────────┐
│                   用户/Token 访问                               │
│  User/Token → 查询有权访问的模型组 → 选择模型组 → 计费           │
└──────────────────────────────────────────────────────────────┘
```

## 3. 领域模型设计

### 3.1 ModelGroup 聚合根

```java
/**
 * 模型组聚合根（充血领域模型）
 * 
 * 职责：
 * - 管理一组供应商模型的售卖策略
 * - 配置模型组级倍率
 * - 定义可用模型列表
 * - 控制访问策略（公开/私有/自动）
 */
public class ModelGroup {
    private Long id;
    private String name;                    // 模型组名称
    private String code;                    // 模型组唯一标识（如 "basic", "premium", "enterprise"）
    private Ratio basePriceRatio;          // 模型组基础倍率
    private String models;                  // 可用模型列表（逗号分隔）
    private AccessPolicy accessPolicy;      // 访问策略枚举
    private ModelGroupStatus status;        // 启用/禁用
    private String description;             // 描述
    private Long createdTime;
    private Long updatedTime;
}

/**
 * 访问策略枚举
 */
public enum AccessPolicy {
    PUBLIC,      // 公开：所有用户可访问
    PRIVATE,     // 私有：需显式授权
    AUTO_LEVEL   // 自动：根据用户等级自动映射
}
```

### 3.2 ModelGroupAccess 聚合根

```java
/**
 * 模型组访问授权聚合根
 * 
 * 职责：
 * - 管理用户/Token 对模型组的访问权限
 * - 支持 User 级授权和 Token 级授权
 */
public class ModelGroupAccess {
    private Long id;
    private Long modelGroupId;          // 关联模型组
    private AccessSubjectType subjectType;  // 授权主体类型
    private Long subjectId;             // 主体 ID（userId 或 tokenId）
    private Long createdTime;
}

public enum AccessSubjectType {
    USER,    // 用户级授权
    TOKEN    // Token 级授权
}
```

### 3.3 UserLevelModelGroupMapping 值对象

```java
/**
 * 用户等级 → 模型组自动映射配置（KV 配置）
 * 
 * 配置示例：
 * {
 *   "COMMON": "basic",      // 普通用户 → 基础组
 *   "VIP": "premium",       // VIP用户 → 高级组
 *   "SVIP": "enterprise"    // SVIP用户 → 企业组
 * }
 */
```

## 4. 数据库设计

### 4.1 model_groups 表

```sql
CREATE TABLE model_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,              -- 唯一标识
    base_price_ratio NUMERIC(10, 4) NOT NULL DEFAULT 1.0,  -- 基础倍率
    models TEXT NOT NULL,                          -- 可用模型列表
    access_policy VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',  -- 访问策略
    status INT NOT NULL DEFAULT 1,                 -- 1=启用 2=禁用
    description TEXT,
    created_time BIGINT NOT NULL,
    updated_time BIGINT NOT NULL,
    deleted_at BIGINT                              -- 软删除
);

CREATE INDEX idx_model_groups_code ON model_groups(code);
CREATE INDEX idx_model_groups_status ON model_groups(status) WHERE deleted_at IS NULL;
```

### 4.2 model_group_access 表

```sql
CREATE TABLE model_group_access (
    id BIGSERIAL PRIMARY KEY,
    model_group_id BIGINT NOT NULL REFERENCES model_groups(id),
    subject_type VARCHAR(10) NOT NULL,             -- USER/TOKEN
    subject_id BIGINT NOT NULL,
    created_time BIGINT NOT NULL,
    UNIQUE(model_group_id, subject_type, subject_id)
);

CREATE INDEX idx_mga_subject ON model_group_access(subject_type, subject_id);
CREATE INDEX idx_mga_group ON model_group_access(model_group_id);
```

## 5. 核心用例设计

### 5.1 管理端用例

```java
// UC-01: 创建模型组
ManageModelGroupUseCase.create(
    name, code, basePriceRatio, models, 
    accessPolicy, description
)

// UC-02: 更新模型组
ManageModelGroupUseCase.update(
    id, name, basePriceRatio, models, 
    accessPolicy, description
)

// UC-03: 启用/禁用模型组
ManageModelGroupUseCase.updateStatus(id, status)

// UC-04: 授权用户/Token 访问模型组
ManageModelGroupAccessUseCase.grant(
    modelGroupId, subjectType, subjectId
)

// UC-05: 撤销访问权限
ManageModelGroupAccessUseCase.revoke(accessId)

// UC-06: 查询模型组列表
QueryModelGroupUseCase.list(filters, pagination)
```

### 5.2 中继链路集成

```java
/**
 * 中继鉴权上下文扩展
 */
public class RelayAuthContext {
    // 现有字段
    private Long userId;
    private String group;  // 保留向后兼容，标记 @Deprecated
    private Long tokenId;
    
    // 新增字段
    private Long modelGroupId;      // 选中的模型组 ID
    private Ratio modelGroupRatio;  // 模型组倍率
}

/**
 * 模型组解析服务（新增）
 */
public class ModelGroupResolver {
    /**
     * 解析 Token 可访问的模型组列表
     */
    public List<ModelGroup> resolveAccessibleGroups(Token token, User user) {
        // 1. 查询 Token 显式授权的私有组
        List<ModelGroup> tokenGroups = findByTokenAccess(token.id());
        
        // 2. 查询 User 显式授权的私有组
        List<ModelGroup> userGroups = findByUserAccess(user.id());
        
        // 3. 查询所有公开组
        List<ModelGroup> publicGroups = findPublicGroups();
        
        // 4. 根据用户等级查询自动映射组
        List<ModelGroup> autoGroups = findByUserLevel(user.role());
        
        // 5. 合并去重
        return mergeAndDeduplicate(tokenGroups, userGroups, publicGroups, autoGroups);
    }
}
```

### 5.3 计费链路集成

```java
/**
 * 计费链路改造
 */
// 原逻辑：groupRatio 来自 User.group 的固定映射
Ratio groupRatio = getGroupRatioByUserGroup(user.group());

// 新逻辑：groupRatio 来自选中的 ModelGroup
ModelGroup selectedGroup = modelGroupResolver.resolveAccessibleGroups(token, user)
    .stream()
    .filter(g -> g.code().equals(requestedGroupCode))
    .findFirst()
    .orElseThrow(() -> new UnauthorizedModelGroupException());

Ratio modelGroupRatio = selectedGroup.basePriceRatio();
Ratio userDiscountRatio = getUserDiscountRatio(user.role()); // VIP 折扣等

// 最终倍率 = 模型组倍率 × 用户折扣
Ratio finalRatio = modelGroupRatio.multiply(userDiscountRatio);
```

## 6. API 设计

### 6.1 管理端 API

```http
### 创建模型组
POST /api/admin/model-groups
Authorization: Bearer <admin-token>
{
  "name": "高级模型组",
  "code": "premium",
  "basePriceRatio": 1.5,
  "models": "gpt-4,claude-3-opus,gemini-pro",
  "accessPolicy": "PRIVATE",
  "description": "高级用户专属模型组"
}

### 更新模型组
PUT /api/admin/model-groups/{id}

### 启用/禁用模型组
PATCH /api/admin/model-groups/{id}/status
{ "status": 1 }

### 授权用户访问模型组
POST /api/admin/model-groups/{id}/access
{
  "subjectType": "USER",
  "subjectId": 12345
}

### 撤销访问权限
DELETE /api/admin/model-groups/access/{accessId}

### 查询模型组列表
GET /api/admin/model-groups?status=1&accessPolicy=PUBLIC
```

### 6.2 用户端 API

```http
### 查询我可访问的模型组
GET /api/v1/model-groups
Authorization: Bearer <api-key>

Response:
{
  "success": true,
  "data": [
    {
      "code": "basic",
      "name": "基础模型组",
      "models": ["gpt-3.5-turbo", "claude-3-haiku"],
      "basePriceRatio": 1.0
    },
    {
      "code": "premium",
      "name": "高级模型组",
      "models": ["gpt-4", "claude-3-opus"],
      "basePriceRatio": 1.5
    }
  ]
}
```

### 6.3 中继 API 改造

```http
### 原请求（兼容）
POST /v1/chat/completions
Authorization: Bearer <api-key>
{
  "model": "gpt-4",
  "messages": [...]
}

### 新请求（可选指定模型组）
POST /v1/chat/completions
Authorization: Bearer <api-key>
X-Model-Group: premium
{
  "model": "gpt-4",
  "messages": [...]
}
```

## 7. 迁移策略

### 7.1 Phase 1: 数据库 & 领域模型（本迭代）
- [x] 创建 model_groups 表
- [x] 创建 model_group_access 表
- [x] 实现 ModelGroup 聚合根
- [x] 实现 ModelGroupAccess 聚合根
- [x] 实现管理端 CRUD 用例

### 7.2 Phase 2: 访问控制集成
- [ ] 实现 ModelGroupResolver
- [ ] 集成到 RelayAuthContext
- [ ] 用户端查询可访问模型组 API
- [ ] 测试公开/私有/自动分组逻辑

### 7.3 Phase 3: 计费链路集成
- [ ] 改造 BillingCalculator 调用链
- [ ] 支持 X-Model-Group Header
- [ ] 兼容旧的 User.group 逻辑
- [ ] 灰度发布

### 7.4 Phase 4: 数据迁移 & 清理
- [ ] 迁移现有 User.group → ModelGroup
- [ ] 标记 User.group 为 @Deprecated
- [ ] 全量切换后清理旧逻辑

## 8. 兼容性保障

### 8.1 向后兼容
- ✅ 保留 `User.group` 字段（标记 @Deprecated）
- ✅ 未指定模型组时，回落到默认公开组
- ✅ 现有 Token 自动映射到公开模型组

### 8.2 灰度策略
- 通过 KV 配置开关控制新旧逻辑
- 优先使用新的 ModelGroup 逻辑
- 失败时回落到旧的 User.group 逻辑

## 9. 验收标准

### 9.1 功能验收
- [ ] 管理员可创建/更新/删除模型组
- [ ] 管理员可配置模型组倍率和可用模型
- [ ] 管理员可设置公开/私有/自动访问策略
- [ ] 用户可查询自己有权访问的模型组
- [ ] Token 鉴权时正确解析模型组权限
- [ ] 计费时正确应用模型组倍率

### 9.2 性能验收
- [ ] 模型组查询 < 50ms (P95)
- [ ] 权限解析不增加中继链路超过 10ms
- [ ] 支持 10000+ 模型组 & 100000+ 授权记录

### 9.3 安全验收
- [ ] 私有模型组无法被未授权用户访问
- [ ] Token 只能访问授权的模型组
- [ ] 管理端操作需要 Admin 角色
