# S12 输入摘要

## 本轮变更
- 将后端从单 Maven 模块重构为 Maven reactor。
- 新增 `nexa-common` 公共组件模块，承载共享内核、统一响应、全局异常处理和安全公共构件。
- 新增 `nexa-service` 服务模块，承载业务 Spring Boot 应用并通过 Maven 依赖引用 `nexa-common`。
- 迁移 shared 相关单测到公共组件模块。
- 更新 README 后端启动/测试命令。

## 验证
- `JAVA_HOME=/opt/jdk21 /opt/maven/bin/mvn -q -DskipTests compile` 通过。
- `JAVA_HOME=/opt/jdk21 /opt/maven/bin/mvn -q test` 通过。

## 观察
- 当前只是模块边界拆分，包名保持 `com.nexa.shared.*`，避免大规模 import 变更。
- Spring Boot 扫描根仍是 `com.nexa`，服务模块能扫描依赖 jar 中的公共组件。
