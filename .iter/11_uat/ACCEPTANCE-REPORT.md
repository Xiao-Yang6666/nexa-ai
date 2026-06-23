# Nexa-AI S11 业务验收报告（命脉链路端到端真验证）

> 环境：后端 localhost:8080（连 sub2 PG 216.167.75.27:5434/nexa + Redis 6380）
> 验收方式：真起服务 + curl 命脉链 + DB/Redis 取证。PG 密码见 sub2 容器 env。
> 状态：首轮验收（主控 + 子代理协作，子代理超时但产出真实数据）

## 服务启动 PASS
- PG 连接 ✓（PostgreSQL 16.14，Flyway validate 通过，238 表）
- 后端启动 ✓（Started NexaApplication，Tomcat 8080，28.8s）
- Redis 连接 ✓（sub2:6380，鉴权缓存键 apiKeyAuth::sk-e2e...0001 曾出现，TTL 120s 后过期=容错配置生效）
- 关键配置坑：SECURITY_ENCRYPTION_KEY 必须配（Base64 32字节 AES key），否则 aesGcmFieldEncryptor bean 启动失败。已记入 nexa-backend-start.sh

## 命脉链路验证

| 步骤 | 状态 | 证据 |
|---|---|---|
| 建供应商 channel | PASS | channels 表 2 行 |
| 建 token | PASS | tokens 表 1 行 |
| 发起调用落库 | PASS | logs 表 6 行 |
| T4 非流式计费落库 | **PASS** | log id=7 type=2(消费) prompt_tokens=11 completion_tokens=7 is_stream=f —— 计费真落库 |
| T12 Redis 鉴权缓存 | PASS | Redis 出现 apiKeyAuth::sk-e2e...0001 缓存键，证明鉴权走了缓存 |
| **T5 流式计费落库** | **GAP** | 未见 is_stream=t 的计费 log。流式调用可能未落计费或未验到。**S12 重点复查** |

## 发现的问题（待 S12 分析/下一轮修）

| ID | 问题 | 严重度 | 备注 |
|---|---|---|---|
| S11-01 | 未取得 T5 流式计费(is_stream=t)的端到端落库证据 | P1 | 单测过(478绿)但端到端缺证据。需补流式 curl + 查 is_stream=t |
| S11-02 | type=5 错误 log prompt/completion=0 | P3 | 上游假地址连不上的正常错误 log，非 bug |

## 总评
命脉链路**基本端到端通**：建供应商→配模型→调用→**非流式计费真落库**（T4 坐实）。T12 鉴权缓存生效。
**唯一 P1 gap**：T5 流式计费缺端到端证据（单测已绿，但真实链路未验到 is_stream=t）。
无阻塞上线的 P0。S12 应把 S11-01 作为下一轮首要复查/修复项。
