# T11 · CR-05 Redis 用途清单（架构师先出清单，本 tick 不实施代码）

> 出文档：2026-06-22 夜（编排总控复核代笔，基于 baseline d8f2bf0 实测）
> 背景：项目部署了 Redis 但后端没利用（实测 backend 各 pom **无 redis/lettuce/jedis 依赖**，零接入）。
> 原则：开发第一守则——先接 1-2 个高频只读缓存见效，不一上来全套；优先复用 Spring 现成（spring-boot-starter-data-redis + @Cacheable），不造轮子。

## 1. 候选缓存点（按"高频只读 + 命中收益 + 失效简单"排序）

| 优先 | 缓存对象 | 代码热点 | 读频 | 失效策略 | 风险 |
|---|---|---|---|---|---|
| ⭐P1 | **token/API-key 鉴权校验结果** | `relay/application/RelayAuthContext` + `KeyLimitGuard`（每次转发都查 key 有效性/归属/限额） | 极高（每次 /v1/* 调用） | 写穿：key 增删改/禁用时主动 evict；TTL 兜底 60~300s | key 被禁后若不及时 evict 有短窗仍放行 → TTL 设短 + 禁用时主动 evict |
| ⭐P1 | **/v1/models 公共模型列表** | `model/application/ListPublicModelsUseCase` | 高（客户端启动/刷新频繁拉） | 模型上下架/改价时 evict；TTL 300~600s | 低（纯展示数据，短延迟可接受） |
| P2 | **group_ratio / 模型组倍率热配置** | `modelgroup` + `billing/domain/service/BillingCalculator`（计费每次取倍率） | 高（每次计费） | 倍率配置变更时 evict；TTL 600s | 计费相关——必须保证改价后及时 evict，宁可 TTL 短 |
| P3 | 限流计数器（REQ-17） | 尚无（REQ-17 P2 未做） | — | INCR+EXPIRE 原子 | 留到 REQ-17 一起做，本轮不碰 |

## 2. 本轮建议落地（最小见效，1 个点）

**选 ⭐P1「token/API-key 鉴权校验缓存」** 作为首个落地点：
- 收益最大：每次转发都走鉴权，命中即省一次 DB 查询，命脉路径直接提速。
- 完成判据（CR-05 验收）：同一 key 连续调用，第二次起鉴权走缓存命中（有命中日志/监控证据）；key 被禁用后主动 evict，被禁 key 下次调用立即 403。
- 失效策略明确：写穿失效（key CRUD/禁用 → evict 对应缓存）+ TTL 兜底（建议 120s）。

> 若时间允许再加 **/v1/models 列表缓存**（风险最低、纯展示），作为第二个点凑足 1-2 个。

## 3. 接入方式（反过度工程）

1. 依赖：加 `spring-boot-starter-data-redis`（成熟 starter，不引第三方缓存库）。
2. 配置：`spring.data.redis.host/port`（连现有部署的 Redis），开 `@EnableCaching`。
3. 缓存：用 Spring `@Cacheable`/`@CacheEvict` 注解式，**不手写缓存读写样板**。鉴权缓存挂在 RelayAuthContext 的 key 查询方法上；evict 挂在 key 管理用例的禁用/删除方法上。
4. 验证：起服务 → 同 key 连调两次看命中日志 → 禁用 key 看 evict + 403 → 真实链路证据留档。

## 4. 不做（本轮范围外）

- 不做全套缓存（倍率/限流留后续）。
- 不做 Redis 做分布式锁/会话/队列等扩展用途——超出 CR-05 "缓存什么"的范围，YAGNI。
- 限流计数器并入 REQ-17（P2）一起做。

## 5. 状态

本 tick **仅出清单**（CR-05 前置 T11），**未写 Redis 代码**。实施（接 1-2 个缓存点 + 真实命中证据）属于下一轮 slice（原计划 T12），需起服务真验证命中，建议用户在场或单独 slice 带 E2E 跑，不今晚盲接。
