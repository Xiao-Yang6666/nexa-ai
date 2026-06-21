# S2 → S3 HANDOFF

## 冻结产物（S3 只读输入）

| 文件 | 角色 | 权威性 |
|---|---|---|
| FUNCTION-LIST.csv | 全量功能清单(232项) | 机器权威 |
| FUNCTION-LIST.md | 人类可读视图 | 展示 |
| REQUIREMENT-TREE.csv | 需求树(336节点,含NFR/合规/RBAC) | 机器权威 |
| MODULE-MAP.md | 14个分片目标 | S3/S4分片依据 |
| COVERAGE-CLOSURE.md | FC覆盖闭环 | 审计 |
| NFR-REQUIREMENTS.md | NFR+数据合规(51条) | 横切契约 |
| ROLE-PERMISSION-MATRIX.md | 7角色×12操作RBAC矩阵 | 权限契约 |
| CAPABILITY-DROP-LEDGER.md | 非产品域drop台账 | 审计 |

## 机械门结果

- compute_gate_counts: function_rows=232, evidence_distinct=232/232, acceptance_distinct=232/232 (无模板填充)
- tree type: 含 NFR=31/Compliance=10/RBAC=9 三横切维度
- repo_capability_coverage: PASS, flagship_blind=0, domain_blind=0
- priority: P0=64/P1=95/P2=70/P3=3 (真分级)

## 给 S3 的要求

- 按 MODULE-MAP 的 14 分片设计流程；一场景一图，禁止跨场景复用图体(slop_probe --stage S3 会查)。
- 流程图屏幕状态清单是 S4/S6 状态的唯一来源。
- 路由/计费/重试类的真实业务规则已在 FUNCTION-LIST 的 state_exception_permission_rule 列，S3 据此画分支异常。

## 已知缺口

- FC-126 控制台动态域未抓动态截图(SG-001)，按证据GAP标注。
- 多租户：MVP不引入组织多租户，沿用用户分组(见ROLE-PERMISSION-MATRIX三阶段决策)。