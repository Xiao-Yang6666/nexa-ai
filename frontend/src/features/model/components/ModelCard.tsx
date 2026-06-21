'use client';

import { useRef } from 'react';
import type { ModelCardVM } from '../model/model.model';
import { VendorAvatar } from './VendorAvatar';
import { TierBadge } from './TierBadge';
import styles from './ModelsPage.module.css';

/** 价格格式化：<1 保 3 位、≥1 保 2 位，去尾零。 */
export function fmtPrice(v: number): string {
  const s = v < 1 ? v.toFixed(3) : v.toFixed(2);
  return s.replace(/\.?0+$/, '');
}

export interface ModelCardProps {
  model: ModelCardVM;
  onOpen: (model: ModelCardVM) => void;
}

/**
 * ModelCard — 模型广场卡片。
 * 头像 + 名称 + 品质徽章 + 标签 + 上下文 + 基准价（高亮）+ 省X% 营销标。
 * Spotlight 光斑随鼠标移动（--mx/--my）。点击/回车打开详情抽屉。
 *
 * 零泄露：只展示 VM 白名单字段——无成本/利润/上游模型 B/供应商。
 */
export function ModelCard({ model, onOpen }: ModelCardProps) {
  const ref = useRef<HTMLButtonElement>(null);

  const handlePointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty('--mx', `${e.clientX - r.left}px`);
    el.style.setProperty('--my', `${e.clientY - r.top}px`);
  };

  return (
    <button
      ref={ref}
      type="button"
      className={styles.mcard}
      onPointerMove={handlePointerMove}
      onClick={() => onOpen(model)}
      aria-label={`查看 ${model.modelName} 详情`}
    >
      <span className={styles.openHint} aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M7 17 17 7" />
          <path d="M8 7h9v9" />
        </svg>
      </span>
      <div className={styles.top}>
        <VendorAvatar vendor={model.vendor} />
        <div>
          <p className={styles.nmRow}>
            <span className={styles.nm}>{model.modelName}</span>
            <TierBadge tier={model.tier} />
          </p>
          <div className={styles.vd}>{model.vendor}</div>
        </div>
      </div>
      <div className={styles.tags}>
        {model.tags.slice(0, 3).map((t) => (
          <span key={t} className={styles.tag}>
            {t}
          </span>
        ))}
      </div>
      <div className={styles.meta}>
        <span className={styles.ctx}>
          <span className={styles.k}>上下文</span>
          <span className={styles.v}>{model.ctx}</span>
        </span>
        {model.basePrice != null ? (
          <div className={styles.price}>
            {model.savePercent != null ? (
              <span className={styles.save}>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m6 9 6 6 6-6" />
                </svg>
                省 {model.savePercent}%
              </span>
            ) : null}
            <span className={styles.priceNexa}>
              ${fmtPrice(model.basePrice)}
              <span className={styles.u}> /1M</span>
            </span>
          </div>
        ) : (
          <div className={styles.price}>
            <span className={styles.priceNa}>价格以控制台为准</span>
          </div>
        )}
      </div>
    </button>
  );
}
