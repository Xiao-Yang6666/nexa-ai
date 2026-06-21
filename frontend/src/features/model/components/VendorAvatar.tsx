import { vendorIcon } from '../model/vendors';
import styles from './ModelsPage.module.css';

export interface VendorAvatarProps {
  vendor: string;
  /** 尺寸变体：md 卡片用，lg 抽屉头部用 */
  size?: 'md' | 'lg';
}

/**
 * VendorAvatar — 厂商头像。
 * 有官方 SVG path → 玻璃片承托 + 厂商品牌色（currentColor）；
 * 占位厂商 → 品牌色圆底 + 首字母。品牌色走 token（var(--v-*)），DOM 无裸 hex。
 */
export function VendorAvatar({ vendor, size = 'md' }: VendorAvatarProps) {
  const icon = vendorIcon(vendor);
  const sizeCls = size === 'lg' ? styles.avLg : '';

  if (!icon.placeholder && icon.path) {
    return (
      <div
        className={`${styles.av} ${styles.avIcon} ${sizeCls}`}
        style={{ color: icon.colorVar }}
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path fill="currentColor" d={icon.path} />
        </svg>
      </div>
    );
  }

  const letter = icon.letter ?? vendor.charAt(0).toUpperCase();
  const bg = `linear-gradient(140deg, color-mix(in oklch, ${icon.colorVar} 86%, transparent), color-mix(in oklch, ${icon.colorVar} 60%, transparent))`;
  return (
    <div className={`${styles.av} ${styles.avPh} ${sizeCls}`} style={{ background: bg }} aria-hidden="true">
      {letter}
    </div>
  );
}
