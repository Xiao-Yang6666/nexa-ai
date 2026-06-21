'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { PageHead, Accent } from '@/features/marketing';
import { vendorIcon } from '@/features/model';
import {
  BOARDS,
  buildRankingModels,
  sortForBoard,
  fmtPrice,
  type BoardConfig,
  type BoardId,
} from '../model/ranking.model';
import { VENDOR_COLOR } from '../model/ranking-data';
import styles from './RankingPage.module.css';

/* ── tab 图标 ── */
const TAB_ICONS: Record<BoardId, JSX.Element> = {
  overall: <path d="M12 2l2.4 7.4H22l-6 4.6 2.3 7.4L12 17l-6.3 4.4L8 14 2 9.4h7.6z" />,
  value: (
    <>
      <path d="M12 1v22" />
      <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
    </>
  ),
  speed: <path d="M13 2 3 14h9l-1 8 10-12h-9z" />,
  popular: (
    <path d="M12 21s-7.5-4.6-10-9.3C.4 8.3 2.1 4.5 5.7 4.5c2 0 3.4 1.1 4.3 2.5.9-1.4 2.3-2.5 4.3-2.5 3.6 0 5.3 3.8 3.7 7.2C19.5 16.4 12 21 12 21z" />
  ),
};

function vc(vendor: string): string {
  return VENDOR_COLOR[vendor] ?? 'var(--hd-mint)';
}

/** 厂商图标内容（官方 SVG path 或首字母占位），承托框样式由父 .vico 提供。 */
function VendorMark({ vendor }: { vendor: string }) {
  const icon = vendorIcon(vendor);
  if (!icon.placeholder && icon.path) {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path fill="currentColor" d={icon.path} />
      </svg>
    );
  }
  return <span className={styles.ltr}>{icon.letter ?? vendor.charAt(0)}</span>;
}

/**
 * RankingPage — 模型排行榜（web-public/ranking.html 工程化）。
 *
 * 四维榜单（综合/性价比/最快响应/最受欢迎）tab 切换，前三领奖台 + 列表，
 * 主指标可视化条 + Nexa 价 / 官方价 / 省 X% 对比。
 * 纯展示页：数据来自 features/ranking/model 的公开评估常量（无榜单接口），
 * 零泄露：只展示官方价、对外 Nexa 价与节省幅度，不含成本 B/利润/供应商。
 */
export function RankingPage() {
  const [board, setBoard] = useState<BoardId>('overall');
  const models = useMemo(() => buildRankingModels(), []);

  const cfg: BoardConfig = BOARDS.find((b) => b.id === board) ?? BOARDS[0];
  const ranked = useMemo(() => sortForBoard(models, cfg.key), [models, cfg.key]);
  const maxVal = ranked.length ? ranked[0][cfg.key] : cfg.max;

  const top3 = ranked.slice(0, 3);
  const rest = ranked.slice(3);

  return (
    <main className={styles.page}>
      <PageHead
        pill="实时榜单 · 综合多维评估"
        title={<>模型<Accent>排行榜</Accent></>}
        lead={
          <>
            从综合实力、性价比、响应速度到调用热度四个维度，一眼看出哪个模型最值得用。所有模型均经
            Nexa 网关统一接入，价格已应用平台倍率，相比官方价直接省到位。
          </>
        }
      />

      <div className="wrap">
        {/* tab 切换 */}
        <div className={styles.tabs} role="tablist" aria-label="排行榜维度">
          {BOARDS.map((b) => {
            const on = b.id === board;
            return (
              <button
                key={b.id}
                type="button"
                role="tab"
                aria-selected={on}
                className={`${styles.tab}${on ? ` ${styles.tabActive}` : ''}`}
                onClick={() => setBoard(b.id)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  {TAB_ICONS[b.id]}
                </svg>
                {b.title}
              </button>
            );
          })}
        </div>

        <div className={styles.boardCap}>
          <h2>{cfg.title}</h2>
          <p>{cfg.desc}</p>
        </div>

        {/* 领奖台：前三 */}
        <div className={styles.podium} aria-label="前三名领奖台">
          {top3.map((m, i) => {
            const rank = i + 1;
            const accent =
              rank === 1
                ? 'var(--color-warning)'
                : rank === 2
                  ? 'color-mix(in oklch, var(--hd-text) 62%, transparent)'
                  : 'color-mix(in oklch, var(--v-mistral) 70%, transparent)';
            return (
              <div
                key={m.name}
                className={`${styles.pod} ${styles[`p${rank}`]}`}
                style={
                  {
                    '--pod-accent': accent,
                    '--vc': vc(m.vendor),
                  } as React.CSSProperties
                }
              >
                {rank === 1 ? (
                  <span className={styles.crown} aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M3 7l5 4 4-6 4 6 5-4-2 12H5z" />
                    </svg>
                  </span>
                ) : null}
                <div className={styles.rankBadge}>{rank}</div>
                <div className={styles.vico}>
                  <VendorMark vendor={m.vendor} />
                </div>
                <p className={styles.mname}>{m.name}</p>
                <p className={styles.mvendor}>
                  {m.vendor} · {m.ctx}
                </p>
                <div className={styles.metric}>
                  {cfg.fmt(m[cfg.key])}
                  {cfg.unit}
                </div>
                <div className={styles.metricL}>{cfg.label}</div>
                <div className={styles.priceRow}>
                  <span className={styles.nexaPrice}>
                    Nexa 价 <b>{fmtPrice(m.nexaBlend)}</b>
                    <span className={styles.per}>/1M</span>
                  </span>
                  <span className={styles.saveBadge}>省 {m.save}%</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* 列表：第 4 名起 */}
        <div className={styles.list} aria-label="排行列表">
          {rest.map((m, i) => {
            const rank = i + 4;
            const pct = Math.max(4, Math.round((m[cfg.key] / maxVal) * 100));
            return (
              <div
                key={m.name}
                className={styles.row}
                style={{ '--vc': vc(m.vendor) } as React.CSSProperties}
              >
                <div className={styles.rk}>{rank}</div>
                <div className={styles.vico}>
                  <VendorMark vendor={m.vendor} />
                </div>
                <div className={styles.nm}>
                  <p className={styles.mname}>{m.name}</p>
                  <p className={styles.mvendor}>
                    {m.vendor} · {m.ctx}
                  </p>
                </div>
                <div className={styles.barCell}>
                  <span className={styles.bval}>
                    {cfg.label}：{cfg.fmt(m[cfg.key])}
                    {cfg.unit}
                  </span>
                  <span className={styles.bar}>
                    <i style={{ width: `${pct}%` }} />
                  </span>
                </div>
                <div className={styles.priceCell}>
                  <span className={styles.nexaPrice}>{fmtPrice(m.nexaBlend)}/1M</span>
                  <span className={styles.official}>官方 {fmtPrice(m.offBlend)}</span>
                </div>
                <div className={styles.act}>
                  <span className={styles.saveBadge}>省 {m.save}%</span>
                  <Link className={`${styles.btn} ${styles.glass} ${styles.btnSm}`} href="/register">
                    接入
                  </Link>
                </div>
              </div>
            );
          })}
        </div>

        {/* 图例 */}
        <div className={styles.legend}>
          <span>
            <span className={styles.dot} style={{ background: 'var(--hd-mint)' }} />
            Nexa 网关价（已应用平台倍率）
          </span>
          <span>
            <span
              className={styles.dot}
              style={{ background: 'color-mix(in oklch, var(--color-success) 70%, transparent)' }}
            />
            相对官方价节省幅度
          </span>
          <span>
            <span className={styles.dot} style={{ background: 'var(--color-primary-500)' }} />
            该榜主指标可视化
          </span>
        </div>
      </div>
    </main>
  );
}
