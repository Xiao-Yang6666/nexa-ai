/**
 * features/growth — 增长域 public API（签到 + 分销推广）。
 * 与后端 growth bounded context 同名对齐；仅触达 self-scope 客户视图，零泄露。
 */
export { CheckinPage } from './components/CheckinPage';
export { ReferralPage } from './components/ReferralPage';
export {
  useCheckinStatus,
  useCheckinMutation,
  useAffCode,
  toCheckinVM,
  buildLadder,
  quotaToUsd,
  quotaUsdValue,
} from './model/growth.model';
export type { CheckinVM, LadderVM, CheckinRecordVM } from './model/growth.model';
