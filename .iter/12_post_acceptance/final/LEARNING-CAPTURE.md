# Learning Capture

## DB Memory Candidates
- fact: Nexa 后端已拆为 Maven reactor：`nexa-common` 公共组件 + `nexa-service` Spring Boot 服务；公共组件包名保持 `com.nexa.shared.*`，服务模块通过 Maven 依赖引用。
  category: project
  action: add
  reason: 稳定架构事实，后续开发需要知道模块边界。

## Skill Patch Candidates
- skill: backend-engineer
  change: Java/Spring 单模块拆 Maven reactor 时，应先迁移 shared/common 代码与测试，再验证 common 不反向依赖业务包，并用 `-pl service -am` 启动服务模块。
  action: patched
  verification: 本轮 compile/test 已通过。

## Similar Issue Prevention
- trigger: 用户要求把全局异常处理/公共代码抽成 Maven 组件。
  new guardrail: 不改包名优先，只移动 Maven 模块边界；先保持 `com.nexa.shared.*`，避免无意义 import 风暴。
