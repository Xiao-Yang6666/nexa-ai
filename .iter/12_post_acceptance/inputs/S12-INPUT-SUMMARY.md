# S12 输入摘要（Round 4 收口复盘）

## 项目
nexa-app：Java 21 + Spring Boot DDD 后端 + Next.js 14 前端。AI 模型中继/计费 SaaS。
代码路径：/root/nexa-app（backend/ + frontend/）

## 当前基线
- main = cf5e0a4（含 R1+R2+R3+R4）
- 后端 537 测试绿，1069 java 文件
- 前端 build 过，180 tsx/ts，api-missing 全局清零
- JAVA_HOME=/opt/jdk21，mvn 在 backend/ 跑

## 今天迭代脉络（已闭环）
- R2-04：流式 SSE 真实链路 500 修复（注入 HttpServletResponse 直写绕过 MessageConverter），补 MockMvc 回归。is_stream=true 首次真落计费。
- R3-01：系统配置面板接通（18 键名契约 + OptionRegistry 领域校验 + 前端 GET/PUT，敏感键剔除）。
- R4-A：部署门验证——本地代码在全新空库从零部署成功（Flyway V1-V27 全过、服务起、注册/登录/鉴权全链路 200、前端代理端到端通）。
- R4-B：系统设置 3 操作端点（performance/cache-clear/stats-reset，复用已有 UseCase，admin 鉴权 403 验过）。

## 后端域（com.nexa.*）
account, billing, channel, compliance, deployment, growth, log, model, modelgroup, nfr, oauthprovider, observability, ops, passkey, playground, prefill, publicsite, relay, routing

## 前端 feature
account, billing, channel, dashboard, docs, group, growth, legal, log, marketing, system 等

## 已知遗留/阻塞（上轮 S12 记录）
- prod 库 schema 漂移：被野生 V28 删了 platform_model_mappings 表，本地代码 V27 仍有该实体。部署需对齐。
- 合规驻地源：渠道表无 residency 字段，需产品决策运营怎么标境内外。
- 真邮件 SMTP：需用户提供凭证。
- WebAuthn/Passkey：stub 状态，生产化工作量大。

## 复盘目标
你是 S12 的某个角色组长。基于真实代码状态，给出**下一轮迭代见解**：你这个维度下，还有什么该优化/该补强/有风险/有技术债。要具体到域/文件/接口，不要泛泛而谈。
