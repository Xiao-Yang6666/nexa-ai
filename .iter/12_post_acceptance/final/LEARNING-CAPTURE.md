# S12 Round 4 经验沉淀（LEARNING-CAPTURE）

## DB Memory Candidates
- fact: nexa-app 是收费 AI 中继 SaaS，上线就绪的硬判据是"非工程人员用全新账号不碰DB走通 注册→试用→充值(真付钱)→调用→仪表盘看到真实消费"全闭环，不是"测试绿+build过+端点有调用"。
  category: project
  action: add
  reason: 本轮最大教训——技术指标全绿 ≠ 业务闭环通，付费/计费两个 P0 被表面指标掩盖。

## Skill Patch Candidates（高风险=改阶段门判据，标 proposed 待确认）
- skill: post-acceptance-iteration-planning（S12）
  change: pitfall 新增一条——"前端 api-missing 清零 / 端点有调用"不等于"页面接的是真实数据"。S12 复盘必须区分三态：①骨架(无调用) ②假接通(调用了但后端是孤儿用例/放行桩/写死静态数组) ③真接通(端到端通+数据真实)。本轮 RechargePage 调 /api/topup 但后端无控制器、DashboardPage 调用了但渲染写死数组，都属②假接通，被"api-missing清零"掩盖。
  action: proposed
  verification: 下一轮 S12 复盘时对每个"已接通"页面追问"后端控制器真存在吗?返回的是真实数据吗?",抽验 3 个页面的真实数据流。

- skill: post-acceptance-iteration-planning（S12）
  change: pitfall 部署就绪性那条补充——"收费类 SaaS 的上线就绪必须包含'钱的闭环'(付费下单→回调→到账→计费扣减)和'计费正确性'(线上实际走的计费路径有金额级测试)两个一等维度"。本轮付费闭环断裂 + 计费口径分裂(线上路径与被测路径差5个数量级)都是没把"钱"当一等维度导致的。
  action: proposed
  verification: 下一轮对任何收费产品，S12 必须专门核验付费控制器存在性 + 线上计费路径的金额级测试覆盖。

## Similar Issue Prevention
- trigger: 主控基于"测试绿+build过+端点清零"就判断"可上线候选"
  new guardrail: 这类乐观判断必须经 S12 五角色复盘证伪后才能成立。单人(主控)凭技术指标拍"可上线"是 S12 pitfall #2(只让单人脑补)的变体。本轮正是五角色复盘推翻了主控的乐观判断。

- trigger: delegate_task 派复杂代码审查角色,600s 超时
  new guardrail: 前端组长读 180 个文件审查超时被回收(但已落盘)。复盘类重读大量文件的子代理任务,prompt 应限定"聚焦 N 个最关键维度,不要逐文件通读",或拆成更小的审查单元。本轮 4/5 完成 1 个超时但产物已落盘,可接受。
