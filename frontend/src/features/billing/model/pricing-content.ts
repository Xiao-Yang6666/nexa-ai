/**
 * features/billing/model/pricing-content — 价格页静态营销内容（typed）。
 *
 * 从 06_prototype/final/web-public/pricing.html 的 MODES/PLANS/FAQ 数据 1:1 工程化迁移。
 * 这些是营销文案/套餐档位，属公开展示内容（非接口数据），故落为页面常量。
 * 价格对比表的真实价格走 /api/pricing（usePricing），与本文件互补。
 */

export type BillingMode = 'usage' | 'prepaid';

export interface ModeCard {
  /** 图标 key（线性 SVG，见 PricingPage 的 ico 函数） */
  icon: 'gauge' | 'wallet';
  tag: string;
  title: string;
  desc: string;
  points: string[];
}

export const MODES: ModeCard[] = [
  {
    icon: 'gauge',
    tag: '随用随付',
    title: '按量付费',
    desc: '按实际消耗的 token 数实时扣费，余额随用随减。零起付门槛，适合调试、低频或负载波动大的场景。',
    points: [
      '无月费、无最低消费，账户余额到 $0 自动停',
      '每个请求按 prompt + completion token 精确计量',
      '计费明细实时可查，可导出对账',
      '支持 API Key 维度限额与告警',
    ],
  },
  {
    icon: 'wallet',
    tag: '充值更省',
    title: '预付费额度',
    desc: '一次性充值额度包，享阶梯折扣，到账即用、永久有效。适合稳定规模化生产与团队统一预算管控。',
    points: [
      '充值 $50 起享 5%、$200 享 10%、$500 享 15% 返点',
      '额度永久有效，不清零、不过期',
      '团队成员共享额度池，按子 Key 分摊',
      '余额预警 + 自动续费可选，避免断供',
    ],
  },
];

export interface PlanCard {
  name: string;
  tagline: string;
  /** 价格金额；null = 定制 */
  amount: number | null;
  amountText?: string;
  per: string;
  sub: string;
  featured?: boolean;
  ctaLabel: string;
  ctaHref: string;
  ctaVariant: 'glass' | 'glow';
  feats: { ok: boolean; text: string }[];
}

export const PLANS: PlanCard[] = [
  {
    name: '免费版',
    tagline: '个人体验与原型验证',
    amount: 0,
    per: '/ 月',
    sub: '注册即送 $1 测试额度',
    ctaLabel: '免费开始',
    ctaHref: '/register',
    ctaVariant: 'glass',
    feats: [
      { ok: true, text: '$1 一次性测试额度' },
      { ok: true, text: '全部模型可调用' },
      { ok: true, text: '5 并发 / 60 RPM 限速' },
      { ok: true, text: '社区支持' },
      { ok: false, text: '团队协作与子 Key' },
      { ok: false, text: '专属 SLA 与优先通道' },
    ],
  },
  {
    name: '个人版',
    tagline: '独立开发者的日常生产',
    amount: 9,
    per: '/ 月',
    sub: '按量计费，月费可抵扣额度',
    ctaLabel: '选择个人版',
    ctaHref: '/register',
    ctaVariant: 'glass',
    feats: [
      { ok: true, text: '按量付费 + 月费抵额度' },
      { ok: true, text: '全部模型 · 直连零头价' },
      { ok: true, text: '20 并发 / 300 RPM 限速' },
      { ok: true, text: '用量看板与对账导出' },
      { ok: true, text: '邮件支持（工作日）' },
      { ok: false, text: '团队成员与权限管理' },
    ],
  },
  {
    name: '团队版',
    tagline: '多人协作与统一预算',
    amount: 49,
    per: '/ 月',
    sub: '含 5 席位，额外席位 $8/人',
    featured: true,
    ctaLabel: '选择团队版',
    ctaHref: '/register',
    ctaVariant: 'glow',
    feats: [
      { ok: true, text: '共享额度池 + 子 Key 分摊' },
      { ok: true, text: '5 团队席位（可扩展）' },
      { ok: true, text: '100 并发 / 1500 RPM 限速' },
      { ok: true, text: '成员权限与用量配额管理' },
      { ok: true, text: '预付费阶梯返点最高 15%' },
      { ok: true, text: '优先邮件 + 工单支持' },
    ],
  },
  {
    name: '企业版',
    tagline: '规模化与合规要求',
    amount: null,
    amountText: '定制',
    per: '',
    sub: '按调用规模与 SLA 议价',
    ctaLabel: '联系销售',
    ctaHref: '/register',
    ctaVariant: 'glass',
    feats: [
      { ok: true, text: '专属高并发与独享通道' },
      { ok: true, text: '99.9% SLA + 故障转移' },
      { ok: true, text: '私有部署 / VPC 直连可选' },
      { ok: true, text: 'SSO、审计日志、数据合规' },
      { ok: true, text: '专属客户成功经理' },
      { ok: true, text: '定制账期与发票结算' },
    ],
  },
];

export interface FaqItem {
  q: string;
  a: string;
}

export const FAQ: FaqItem[] = [
  { q: '按量付费和预付费额度可以同时用吗？', a: '可以。账户余额本质上是一个额度池——你既可以随时按量小额充值随用随减，也可以一次性充值较大额度享受阶梯返点。两者共用同一份余额，不区分来源，调用时统一从余额扣费。' },
  { q: 'Nexa 的价格为什么能比官方便宜这么多？', a: '我们通过规模化采购与多上游路由聚合获得批量优惠，并把绝大部分让利直接返还给开发者。你拿到的是满血、未阉割的官方同款模型，只是支付的是经过聚合优化后的「零头价」，不牺牲任何模型能力或上下文长度。' },
  { q: 'token 是怎么计量和计费的？', a: '每个请求按 prompt（输入）token + completion（输出）token 分别计量，单价对照价格表中各模型的「每百万 token」定价换算。控制台计费页会展示每一次调用的 token 数与扣费明细，可按时间、模型、API Key 维度筛选并导出对账单。' },
  { q: '预付费额度会过期吗？', a: '不会。预付费充值的额度永久有效，不清零、不过期。即使账户长期闲置，额度依然保留，下次调用时继续从中扣费。' },
  { q: '免费额度用完后会怎样？', a: '免费的 $1 测试额度用完后，调用会因余额不足而被拦截，并返回明确的余额不足提示。你只需在控制台充值任意金额即可立即恢复调用，无需重新注册或申请。' },
  { q: '团队版的席位和额度是怎么分配的？', a: '团队版默认含 5 个成员席位，共享同一个额度池。管理员可为每个成员或子 API Key 设置独立的用量配额与限速，统一查看全团队消耗。需要更多席位时按 $8/人/月 扩展，额度按实际调用从共享池扣减。' },
  { q: '支持哪些付款方式和发票？', a: '支持主流信用卡与对公转账充值。个人与团队版可在控制台自助开具普通发票；企业版支持定制账期、专用发票抬头与合规结算流程，由专属客户成功经理对接。' },
];
