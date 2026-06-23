# 后端组长复盘

## 做对了
- 先拆 Maven reactor，再迁移 shared 代码，避免业务包和公共包互相缠绕。
- 公共模块未反向依赖业务 bounded context，模块边界干净。
- 先跑 compile，再跑全量 test，确认不是只靠静态移动。

## 风险
- 后续如果公共模块继续承载太多 Spring Security 实现，可能变成“公共垃圾桶”。公共组件边界应保持：响应模型、全局异常处理、共享安全基础设施、共享内核类型。
- 业务异常仍有各子域局部 handler；下一轮可以继续收敛重复 handler，但不要一次性删除所有子域 handler。

## 下一轮建议
- 给 `nexa-common` 增加 README 或 package-info，明确什么能进公共组件、什么必须留在业务服务。
- 可考虑增加 Maven Enforcer，强制 Java 21 和禁止 common 依赖 service/business 包。
