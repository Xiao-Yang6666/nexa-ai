# S7 下一轮交接

## 下一轮目标
加固公共组件模块边界，防止 `nexa-common` 反向依赖业务包或承载业务逻辑。

## 输入
- `backend/pom.xml`
- `backend/nexa-common/pom.xml`
- `backend/nexa-service/pom.xml`
- `backend/nexa-common/src/main/java/com/nexa/shared/**`

## 成功标准
- `nexa-common` 有明确边界说明。
- 构建或静态检查能发现 common 依赖业务包。
- `mvn test` 继续通过。
