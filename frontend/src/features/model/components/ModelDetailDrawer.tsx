'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import type { ModelCardVM } from '../model/model.model';
import { VendorAvatar } from './VendorAvatar';
import { fmtPrice } from './ModelCard';
import styles from './ModelsPage.module.css';

export interface ModelDetailDrawerProps {
  /** 当前打开的模型；null 表示关闭 */
  model: ModelCardVM | null;
  onClose: () => void;
}

/**
 * ModelDetailDrawer — 模型详情右侧抽屉。
 * 分组价格对比表（各可用分组的倍率与售价）/ 规格 / 能力标签。
 * Esc 关闭、遮罩点击关闭。零泄露：只渲染 VM 白名单字段。
 */
export function ModelDetailDrawer({ model, onClose }: ModelDetailDrawerProps) {
  const open = model != null;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <div
      className={`${styles.scrim} ${open ? styles.scrimOpen : ''}`}
      aria-hidden={!open}
      onClick={onClose}
    >
      <aside
        className={`${styles.drawer} ${open ? styles.drawerOpen : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={model ? `${model.modelName} 详情` : '模型详情'}
        onClick={(e) => e.stopPropagation()}
      >
        {model ? (
          <>
            <div className={styles.dwHead}>
              <VendorAvatar vendor={model.vendor} size="lg" />
              <div className={styles.dwTtl}>
                <h2>{model.displayName || model.modelName}</h2>
                <div className={styles.vd}>
                  {model.vendor} · <span className={styles.dwModelId}>{model.modelName}</span>
                </div>
              </div>
              <button
                type="button"
                className={styles.dwClose}
                aria-label="关闭详情"
                onClick={onClose}
              >
                <svg viewBox="0 0 24 24">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className={styles.dwBody}>
              {model.description ? (
                <section>
                  <h3 className={styles.dwSecT}>简介</h3>
                  <p className={styles.dwDesc}>{model.description}</p>
                </section>
              ) : null}
              <section>
                <h3 className={styles.dwSecT}>分组价格</h3>
                {model.groups.length > 0 ? (
                  <div className={styles.groupTable}>
                    <div className={`${styles.gtRow} ${styles.gtHead}`}>
                      <span className={styles.gtName}>分组</span>
                      <span className={styles.gtRatio}>倍率</span>
                      <span className={styles.gtPrice}>价格 /1M</span>
                    </div>
                    {model.groups.map((g) => (
                      <div key={g.code || g.name} className={styles.gtRow}>
                        <span className={styles.gtName}>{g.name}</span>
                        <span className={styles.gtRatio}>×{g.ratio}</span>
                        <span className={styles.gtPrice}>
                          {g.price != null ? `$${fmtPrice(g.price)}` : '—'}
                        </span>
                      </div>
                    ))}
                    <div className={styles.pcUnit}>
                      单位：USD / 1M tokens · 登录后按会员等级享折后价
                    </div>
                  </div>
                ) : model.basePrice != null ? (
                  <div className={styles.priceCard}>
                    <div className={styles.pcRow}>
                      <span className={styles.lab}>基准价</span>
                      <span className={styles.nexa}>${fmtPrice(model.basePrice)}</span>
                    </div>
                    <div className={styles.pcUnit}>
                      单位：USD / 1M tokens · 登录后按会员等级享折后价
                    </div>
                  </div>
                ) : (
                  <div className={`${styles.priceCard} ${styles.priceCardNa}`}>
                    <div className={styles.pcNa}>
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <circle cx="12" cy="12" r="9" />
                        <path d="M12 8v5" />
                        <path d="M12 16h.01" />
                      </svg>
                      该模型实时单价以控制台计费页为准。
                    </div>
                  </div>
                )}
              </section>

              <section>
                <h3 className={styles.dwSecT}>规格</h3>
                <div>
                  <div className={styles.specRow}>
                    <span className={styles.sk}>上下文长度</span>
                    <span className={styles.sv}>{model.ctx}</span>
                  </div>
                  <div className={styles.specRow}>
                    <span className={styles.sk}>能力</span>
                    <span className={styles.sv}>{model.capabilityText}</span>
                  </div>
                  <div className={styles.specRow}>
                    <span className={styles.sk}>厂商</span>
                    <span className={styles.sv}>{model.vendor}</span>
                  </div>
                  {model.cacheRatio != null ? (
                    <div className={styles.specRow}>
                      <span className={styles.sk}>缓存命中倍率</span>
                      <span className={styles.sv}>×{model.cacheRatio}</span>
                    </div>
                  ) : null}
                </div>
              </section>

              {model.tags.length ? (
                <section>
                  <h3 className={styles.dwSecT}>能力标签</h3>
                  <div className={styles.dwTags}>
                    {model.tags.map((t) => (
                      <span key={t} className={styles.tag}>
                        {t}
                      </span>
                    ))}
                  </div>
                </section>
              ) : null}

              <div className={styles.note}>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 11v5" />
                  <path d="M12 8h.01" />
                </svg>
                价格为基准价示意，实际计费以控制台实时报价为准。
              </div>
            </div>

            <div className={styles.dwFoot}>
              <Link className={styles.dwCta} href="/register">
                <svg viewBox="0 0 24 24">
                  <path d="M5 12h14" />
                  <path d="m12 5 7 7-7 7" />
                </svg>
                在控制台调用
              </Link>
            </div>
          </>
        ) : null}
      </aside>
    </div>
  );
}
