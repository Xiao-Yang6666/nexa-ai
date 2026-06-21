import { TIER_DISPLAY, type QualityTier } from '../model/model.model';
import styles from './ModelsPage.module.css';

/** 各品质档的内联线性图标（零 emoji，stroke + currentColor）。 */
const TIER_ICON: Record<QualityTier, string> = {
  full: 'm12 3 2.6 5.3 5.9.9-4.3 4.1 1 5.9L12 16.6 6.8 19.2l1-5.9L3.5 9.2l5.9-.9z',
  max: 'M13 2 4 14h7l-1 8 9-12h-7z',
  air: '',
};

export interface TierBadgeProps {
  tier?: QualityTier;
  /** 抽屉说明区用的大徽章变体 */
  inNote?: boolean;
}

/**
 * TierBadge — 品质档徽章（旗舰=teal 实心 / 增强=warning / 经济=中性灰）。
 * tier 缺失时不渲染（兼容非三档模型）。
 */
export function TierBadge({ tier, inNote }: TierBadgeProps) {
  if (!tier) return null;
  const cfg = TIER_DISPLAY[tier];
  const path = TIER_ICON[tier];
  return (
    <span
      className={`${styles.tierBadge} ${styles[cfg.cls]} ${inNote ? styles.tnBadge : ''}`}
    >
      {tier === 'air' ? (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="8" />
          <path d="M12 8v8" />
          <path d="M9.5 10.5h3.5a1.8 1.8 0 0 1 0 3.5H10" />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d={path} />
        </svg>
      )}
      {cfg.label}
    </span>
  );
}
