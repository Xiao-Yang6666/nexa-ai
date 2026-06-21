# ARCH-MEMORY（架构师项目级记忆）

---

## 技术栈冻结（S2.5，2026-06-20，用户拍板）

**后端**：Java 21+ LTS（虚拟线程 + ZGC）+ Spring Boot 3.2+（`spring.threads.virtual.enabled=true`）。用户 Java 出身，团队主场。
**架构**：DDD（用户硬性偏好）。轻量打底 + 核心域战术完整（Relay转发/计费/选渠/异步任务/对账 5 域：聚合根/值对象/领域事件，金额用值对象）；外围 CRUD 轻量。模块化单体（DDD≠微服务）。
**DB**：PostgreSQL 单库（放弃三库兼容，可用 JSONB/分区/部分索引）+ Redis（必选）。异步任务起步 Spring 调度/轻量队列，用量统计起步 PG 预聚合 CQRS 读模型（预留 NATS/ClickHouse）。
**前端**：统一 React。营销站/文档站 Next.js（SSR/SSG，SEO）+ 控制台/管理台/Playground SPA。
**部署**：模块化单体 + Docker，起步少节点 → 演进 K8s。CI/CD + 监控告警。

**工程纪律**：虚拟线程用 ReentrantLock 不用 synchronized（避 pinning）；显式 ZGC + 容器内存给足 + MaxRAMPercentage；并发控制用 Semaphore 不用固定线程池；SSE 设超时/空闲检测/背压。

**下游硬约束**：S7 DB-SCHEMA 需修订（GORM struct → JPA Entity，PG 单库，表结构逻辑不变；openapi/BACKLOG/覆盖校验不受影响）；S8 后端 Java/Spring/DDD 分层照此；S9 前端 React/Next 照此。不得另选栈。
