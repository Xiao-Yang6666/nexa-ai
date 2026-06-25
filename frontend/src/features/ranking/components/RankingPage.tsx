'use client';

import { useState } from 'react';
import Link from 'next/link';
import { PageHead, Accent } from '@/features/marketing';
import { vendorIcon } from '@/features/model';
import { useRankings, PERIODS, type RankingPeriod, type RankingRowVM } from '../model/ranking.model';
import styles from './RankingPage.module.css';

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

/** 厂商品牌色（取 vendorIcon 的 colorVar token，未命中回退 hd-mint）。 */
function vc(vendor: string): string {
  return vendorIcon(vendor).colorVar ?? 'var(--hd-mint)';
}

/**
 * RankingPage — 模型用量排行榜（真实 /api/rankings 用量快照）。
 *
 * 周期切换（近 7 天 / 近 30 天），前三领奖台 + 列表，主指标为「经 Nexa 网关的真实调用用量」
 * （售价 quota 口径换算 USD）。数据来自 features/ranking/model 的 useRankings（后端日志聚合）。
 * 零泄露：只展示对外名 A 与聚合用量，不含成本 B/利润/供应商。
 *
 * 注：本期只做用量榜。综合/性价比/最快响应三榜及官方价对比涉及人工策划数据（能力分/速度/竞品价），
 * 运行时无法测得，本期暂不做——详见 ranking.model.ts 顶部 TODO(ranking-multidim)。
 */
export function RankingPage() {
  const [period, setPeriod] = useState<RankingPeriod>('week');
  const { data, isLoading, isError, refetch } = useRankings(period);

  const cfg = PERIODS.find((p) => p.id === period) ?? PERIODS[0];
  const ranked: RankingRowVM[] = data ?? [];
  const maxVal = ranked.length ? Math.max(ranked[0].usedUsdValue, 0.0001) : 1;
  const top3 = ranked.slice(0, 3);
  const rest = ranked.slice(3);

  return (
    <main className={styles.page}>
      <PageHead
        pill="实时榜单 · 真实调用用量"
        title={<>模型<Accent>排行榜</Accent></>}
        lead={
          <>
            按经 Nexa 网关的真实调用用量排序，一眼看出当下最受欢迎的模型。所有模型均经 Nexa
            统一接入，相比官方价省到位。
          </>
        }
      />

      <div className="wrap">
        {/* 周期切换 */}
        <div className={styles.tabs} role="tablist" aria-label="排行榜周期">
          {PERIODS.map((p) => {
            const on = p.id === period;
            return (
              <button
                key={p.id}
                type="button"
                role="tab"
                aria-selected={on}
                className={`${styles.tab}${on ? ` ${styles.tabActive}` : ''}`}
                onClick={() => setPeriod(p.id)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 7v5l3 2" />
                  <circle cx="12" cy="12" r="9" />
                </svg>
                {p.label}
              </button>
            );
          })}
        </div>

        <div className={styles.boardCap}>
          <h2>用量榜</h2>
          <p>{cfg.desc}</p>
        </div>

        <RankingBody
          isLoading={isLoading}
          isError={isError}
          ranked={ranked}
          top3={top3}
          rest={rest}
          maxVal={maxVal}
          onRetry={() => refetch()}
        />
      </div>
    </main>
  );
}

/** 排行主体：按数据态分发（加载骨架 / 错误 / 空 / 领奖台+列表）。 */
function RankingBody({
  isLoading,
  isError,
  ranked,
  top3,
  rest,
  maxVal,
  onRetry,
}: {
  isLoading: boolean;
  isError: boolean;
  ranked: RankingRowVM[];
  top3: RankingRowVM[];
  rest: RankingRowVM[];
  maxVal: number;
  onRetry: () => void;
}) {
  if (isLoading) {
    return (
      <div className={styles.podium} aria-busy="true">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className={`${styles.pod} ${styles[`p${i + 1}`]} ${styles.skel}`} />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className={styles.state}>
        <div className={styles.stateTitle}>排行数据加载失败</div>
        <div className={styles.stateSub}>网络或服务异常，请稍后重试。</div>
        <button type="button" className={`${styles.btn} ${styles.glass}`} onClick={onRetry}>
          重试
        </button>
      </div>
    );
  }

  if (ranked.length === 0) {
    return (
      <div className={styles.state}>
        <div className={styles.stateTitle}>该周期暂无调用数据</div>
        <div className={styles.stateSub}>等首批调用产生后，排行榜会自动展示。</div>
      </div>
    );
  }

  return (
    <>
      {/* 领奖台：前三 */}
      <div className={styles.podium} aria-label="前三名领奖台">
        {top3.map((m, i) => (
          <PodiumCard key={m.modelName} model={m} rank={i + 1} />
        ))}
      </div>

      {/* 列表：第 4 名起 */}
      {rest.length > 0 ? (
        <div className={styles.list} aria-label="排行列表">
          {rest.map((m) => {
            const pct = Math.max(4, Math.round((m.usedUsdValue / maxVal) * 100));
            return (
              <div
                key={m.modelName}
                className={styles.row}
                style={{ '--vc': vc(m.vendor) } as React.CSSProperties}
              >
                <div className={styles.rk}>{m.rank}</div>
                <div className={styles.vico}>
                  <VendorMark vendor={m.vendor} />
                </div>
                <div className={styles.nm}>
                  <p className={styles.mname}>{m.modelName}</p>
                  <p className={styles.mvendor}>
                    {m.vendor}
                    {m.ctx && m.ctx !== '—' ? ` · ${m.ctx}` : ''}
                  </p>
                </div>
                <div className={styles.barCell}>
                  <span className={styles.bval}>用量：{m.usedUsdLabel}</span>
                  <span className={styles.bar}>
                    <i style={{ width: `${pct}%` }} />
                  </span>
                </div>
                <div className={styles.priceCell}>
                  <span className={styles.nexaPrice}>{m.usedUsdLabel}</span>
                </div>
                <div className={styles.act}>
                  <Link className={`${styles.btn} ${styles.glass} ${styles.btnSm}`} href="/register">
                    接入
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      ) : null}

      {/* 图例 */}
      <div className={styles.legend}>
        <span>
          <span className={styles.dot} style={{ background: 'var(--hd-mint)' }} />
          经 Nexa 网关的真实调用用量（售价口径换算 USD）
        </span>
        <span>
          <span className={styles.dot} style={{ background: 'var(--color-primary-500)' }} />
          用量相对榜首的可视化占比
        </span>
      </div>
    </>
  );
}

/** 领奖台单卡（前三名）。 */
function PodiumCard({ model: m, rank }: { model: RankingRowVM; rank: number }) {
  const accent =
    rank === 1
      ? 'var(--color-warning)'
      : rank === 2
        ? 'color-mix(in oklch, var(--hd-text) 62%, transparent)'
        : 'color-mix(in oklch, var(--v-mistral) 70%, transparent)';
  return (
    <div
      className={`${styles.pod} ${styles[`p${rank}`]}`}
      style={{ '--pod-accent': accent, '--vc': vc(m.vendor) } as React.CSSProperties}
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
      <p className={styles.mname}>{m.modelName}</p>
      <p className={styles.mvendor}>
        {m.vendor}
        {m.ctx && m.ctx !== '—' ? ` · ${m.ctx}` : ''}
      </p>
      <div className={styles.metric}>{m.usedUsdLabel}</div>
      <div className={styles.metricL}>本期用量</div>
    </div>
  );
}

