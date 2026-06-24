/**
 * features/billing — 计费/价格域：价格页（公开站）+ 控制台账单/充值。
 * 与后端 bounded context「billing」同名。客户端零泄露：均为 self-scope / public 视图。
 */
export { PricingPage } from './components/PricingPage';
export { BillingPage } from './components/BillingPage';
export { RechargePage } from './components/RechargePage';
export { BillingRulesPage } from './components/BillingRulesPage';
export { usePricing, toPriceRowVM, toPriceRowVMs } from './model/pricing.model';
export type { PriceRowVM } from './model/pricing.model';
export { MODES, PLANS, FAQ } from './model/pricing-content';
export type { BillingMode, ModeCard, PlanCard, FaqItem } from './model/pricing-content';
export {
  useBalance,
  useMonthSpend,
  useRechargeRecords,
  useCreateTopUp,
  giftFor,
  quotaToUsd,
  quotaUsdValue,
} from './model/billing.model';
export type { BalanceVM, RechargeRecordVM } from './model/billing.model';
