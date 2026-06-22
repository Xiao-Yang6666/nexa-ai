'use client';

import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useProfitDashboard,
  type ProfitRow,
  type ProfitBarItem,
  type RevCostPoint,
} from '../model/profit.model';
import styles from './ProfitPage.module.css';

/* 时间范围切换 → 区间天数 */
const RANGES: { id: string; lab: string; days: number }[] = [
  { id: 'today', lab: '今日', days: 1 },
  { id: '7d', lab: '近 7 日', days: 7 },
  { id: '30d', lab: '近 30 日', days: 30 },
];

/* ── 工具函数 ── */
function money(v: number): string {
  const abs = Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return (v < 0 ? '-$' : '$') + abs;
}
function rateClass(rate: number): 'ok' | 'mid' | 'bad' {
  if (rate < 0) return 'bad';
  if (rate < 20) return 'mid';
  return 'ok';
}

/* ── 小图标 ── */
const WarnIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 9v4" />
    <path d="M12 17h.01" />
    <path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
  </svg>
);
const GoodIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 16l5-5 4 4 8-9" />
    <path d="M16 6h5v5" />
  </svg>
);
const InfoIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 8h.01" />
    <path d="M11 12h1v4h1" />
  </svg>
);

type InsightTone = 'good' | 'warn' | 'info';
const INSIGHT_ICON: Record<InsightTone, () => JSX.Element> = { good: GoodIcon, warn: WarnIcon, info: InfoIcon };

/**
 * RevCostTrend — 营收按日折线（来自 /api/data 的 quota→USD）。
 * 契约 /api/data 不提供按日成本拆分，故此处仅绘营收线（成本/利润趋势无端点支撑）。
 */
function RevCostTrend({ data }: { data: RevCostPoint[] }) {
  const W = 760, H = 280;
  const pad = { l: 54, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  if (data.length === 0) return <div className={styles.chartSub}>暂无营收数据</div>;
  const max = Math.max(1, ...data.map((d) => d.rev)) * 1.08;
  const n = data.length;
  const xs = (i: number) => pad.l + (n === 1 ? iw / 2 : i * (iw / (n - 1)));
  const ys = (v: number) => pad.t + ih - (v / max) * ih;

  const revPts = data.map((d, i) => `${xs(i)},${ys(d.rev)}`).join(' ');
  const area = `M${xs(0)} ${ys(0)} ${data.map((d, i) => `L${xs(i)} ${ys(d.rev)}`).join(' ')} L${xs(n - 1)} ${ys(0)} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="近期营收按日折线图">
      <defs>
        <linearGradient id="profitFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.22} />
          <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0.04} />
        </linearGradient>
      </defs>
      {[0, 1, 2, 3, 4].map((t) => {
        const v = (max * t) / 4;
        const y = ys(v);
        return (
          <g key={t}>
            <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={pad.l - 8} y={y + 3} textAnchor="end">${Math.round(v)}</text>
          </g>
        );
      })}
      {data.map((d, i) =>
        i % Math.ceil(n / 6) === 0 ? (
          <text key={i} className={styles.axTxt} x={xs(i)} y={H - 10} textAnchor="middle">{d.date.slice(5)}</text>
        ) : null,
      )}
      <path d={area} fill="url(#profitFill)" />
      <polyline points={revPts} fill="none" stroke="var(--chart-1)" strokeWidth={2.4} strokeLinejoin="round" />
      <circle cx={xs(n - 1)} cy={ys(data[n - 1].rev)} r={4} fill="var(--chart-1)" />
    </svg>
  );
}

/**
 * HBarChart — 通用横向柱状图（动态零基准 + 自适应量程，负值标红）。
 */
function HBarChart({
  rows,
  W,
  H,
  pad,
  posColor,
  ticks = false,
}: {
  rows: ProfitBarItem[];
  W: number;
  H: number;
  pad: { l: number; r: number; t: number; b: number };
  posColor: string;
  ticks?: boolean;
}) {
  if (rows.length === 0) return <div className={styles.chartSub}>暂无数据</div>;
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const vals = rows.map((r) => r.val);
  const rawMax = Math.max(0, ...vals);
  const rawMin = Math.min(0, ...vals);
  const maxv = rawMax * 1.1 || 1;
  const minv = rawMin * 1.1;
  const span = maxv - minv || 1;
  const zx = pad.l + ((0 - minv) / span) * iw;
  const gh = ih / rows.length;
  const bh = ticks ? 22 : 20;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="利润横向柱状图，亏损标红">
      {ticks
        ? [0, 1, 2, 3, 4, 5].map((t) => {
            const v = minv + (span * t) / 5;
            const x = pad.l + ((v - minv) / span) * iw;
            return (
              <g key={t}>
                <line x1={x} y1={pad.t} x2={x} y2={pad.t + ih} stroke="var(--chart-grid)" strokeWidth={1} />
                <text className={styles.axTxt} x={x} y={H - 8} textAnchor="middle">${Math.round(v)}</text>
              </g>
            );
          })
        : null}
      <line x1={zx} y1={pad.t} x2={zx} y2={pad.t + ih} stroke={ticks ? 'var(--color-text-muted)' : 'var(--chart-grid)'} strokeWidth={1.4} />
      {rows.map((r, i) => {
        const cy = pad.t + gh * i + gh / 2;
        const bx = pad.l + ((Math.min(r.val, 0) - minv) / span) * iw;
        const bw = (Math.abs(r.val) / span) * iw;
        const col = r.val < 0 ? 'var(--color-danger)' : posColor;
        const lx = r.val < 0 ? bx - 8 : bx + bw + 8;
        const anc = r.val < 0 ? 'end' : 'start';
        const lab = r.val < 0 ? `-$${Math.abs(r.val)}` : `$${r.val}`;
        const nm = r.name.length > 14 ? r.name.slice(0, 13) + '…' : r.name;
        return (
          <g key={r.name}>
            <text className={styles.axTxt} x={pad.l - 10} y={cy + 4} textAnchor="end" style={{ fill: 'var(--color-text-secondary)' }}>{nm}</text>
            <rect x={bx} y={cy - bh / 2} width={bw} height={bh} rx={4} fill={col} />
            <text className={styles.axTxt} x={lx} y={cy + 4} textAnchor={anc} style={{ fill: 'var(--color-text)' }}>{lab}</text>
          </g>
        );
      })}
    </svg>
  );
}

/* ── 明细表单元格 ── */
function ProfitCell({ v }: { v: number }) {
  return <span className={v < 0 ? styles.profitNeg : styles.profitPos}>{money(v)}</span>;
}
function RatePill({ rate }: { rate: number }) {
  return <span className={`${styles.ratePill} ${styles[rateClass(rate)]}`}>{rate.toFixed(1)}%</span>;
}

type TipKind = 'lossModel' | 'lowModel' | 'goodModel' | 'lossVendor' | 'goodVendor' | 'thinVendor' | null;

function DiagTag({ kind }: { kind: TipKind }) {
  if (!kind) return null;
  const map: Record<NonNullable<TipKind>, { suggest: boolean; icon: JSX.Element; text: string }> = {
    lossModel: { suggest: false, icon: <WarnIcon />, text: '定价偏低，已亏损' },
    lowModel: { suggest: false, icon: <WarnIcon />, text: '定价偏低' },
    goodModel: { suggest: true, icon: <GoodIcon />, text: '毛利充裕，可导流' },
    lossVendor: { suggest: false, icon: <WarnIcon />, text: '在亏损，应减量或调成本' },
    goodVendor: { suggest: true, icon: <GoodIcon />, text: '毛利最高，建议加权重' },
    thinVendor: { suggest: false, icon: <WarnIcon />, text: '薄利，降低导流' },
  };
  const t = map[kind];
  return (
    <span className={`${styles.tipTag} ${t.suggest ? styles.suggest : ''}`}>
      {t.icon}
      {t.text}
    </span>
  );
}
function modelTip(rate: number, loss: boolean): TipKind {
  if (loss) return 'lossModel';
  if (rate < 20) return 'lowModel';
  if (rate >= 55) return 'goodModel';
  return null;
}
function vendorTip(rate: number, loss: boolean): TipKind {
  if (loss) return 'lossVendor';
  if (rate >= 60) return 'goodVendor';
  if (rate < 25) return 'thinVendor';
  return null;
}

/** 利润明细表（售价/成本/利润/利润率 + 诊断；调用量/用户数无端点支撑，故不展示）。 */
function ProfitTable({
  rows,
  nameLabel,
  diag,
}: {
  rows: ProfitRow[];
  nameLabel: string;
  diag?: (rate: number, loss: boolean) => TipKind;
}) {
  if (rows.length === 0) return <div className={styles.chartSub} style={{ padding: 'var(--space-5)' }}>暂无数据</div>;
  return (
    <div className={styles.tableWrap}>
      <table>
        <thead>
          <tr>
            <th>{nameLabel}</th>
            <th className={styles.num}>营收</th>
            <th className={styles.num}>成本</th>
            <th className={styles.num}>利润</th>
            <th className={styles.num}>利润率</th>
            {diag ? <th>诊断</th> : null}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const loss = r.profit < 0;
            return (
              <tr key={r.name} className={loss ? styles.loss : ''}>
                <td className={styles.cellName}>{r.name}</td>
                <td className={`${styles.num} ${styles.monoNum}`}>{money(r.sell)}</td>
                <td className={`${styles.num} ${styles.monoNum}`}>{money(r.cost)}</td>
                <td className={styles.num}><ProfitCell v={r.profit} /></td>
                <td className={styles.num}><RatePill rate={r.rate} /></td>
                {diag ? <td><DiagTag kind={diag(r.rate, loss)} /></td> : null}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

type DimTab = 'model' | 'vendor' | 'group';

/** 由真实利润行派生经营洞察（最薄利供应商 / 最高毛利模型 / 利润主力分组）。 */
function buildInsights(
  modelRows: ProfitRow[],
  channelRows: ProfitRow[],
  groupRows: ProfitRow[],
): { tone: InsightTone; body: React.ReactNode }[] {
  const out: { tone: InsightTone; body: React.ReactNode }[] = [];

  const worstChannel = channelRows.filter((r) => r.sell > 0).slice().sort((a, b) => a.rate - b.rate)[0];
  if (worstChannel) {
    out.push({
      tone: worstChannel.rate < 20 ? 'warn' : 'info',
      body: (
        <>
          供应商/渠道 <b>{worstChannel.name}</b> 利润率最低，仅 <b>{worstChannel.rate.toFixed(1)}%</b>，
          建议复议采购价或下调其权重。
        </>
      ),
    });
  }

  const bestModel = modelRows.filter((r) => r.sell > 0).slice().sort((a, b) => b.rate - a.rate)[0];
  if (bestModel) {
    out.push({
      tone: 'good',
      body: (
        <>
          对外模型 <b>{bestModel.name}</b> 利润率最高，达 <b>{bestModel.rate.toFixed(1)}%</b>，可考虑导入更多流量。
        </>
      ),
    });
  }

  const totGroupProfit = groupRows.reduce((s, r) => s + r.profit, 0);
  const topGroup = groupRows.slice().sort((a, b) => b.profit - a.profit)[0];
  if (topGroup && totGroupProfit > 0) {
    const share = Math.round((topGroup.profit / totGroupProfit) * 100);
    out.push({
      tone: 'info',
      body: (
        <>
          <b>{topGroup.name}</b> 分组贡献 <b>{share}%</b> 利润，是当前利润主力。
        </>
      ),
    });
  }

  return out;
}

/**
 * ProfitPage — 利润分析看板（已接真实接口）。
 *
 * 数据组合自 /api/profit/dashboard（model/channel/group 三维：售价/成本/利润/利润率）
 * 与 /api/data（按日营收趋势）。售价/成本/利润三段式，按对外模型/供应商/分组多维拆解。
 * 管理端视图，可展示全字段（成本/利润/上游供应商），不受客户端零泄露约束。
 *
 * 契约缺口（无端点支撑，已从展示中移除，不前端造假）：
 *  - 按日成本拆分 → 趋势图仅绘营收线（/api/data 只返回 quota/count，无成本）
 *  - 各维度调用量 / 分组用户数 → 明细表不含该列（ProfitDashboardItem 不含）
 */
export function ProfitPage() {
  const [rangeId, setRangeId] = useState('30d');
  const [dim, setDim] = useState<DimTab>('model');
  const days = RANGES.find((r) => r.id === rangeId)?.days ?? 30;

  const { data, isLoading, isError, error } = useProfitDashboard(days);

  const kpis = data?.kpis ?? [];
  const trend = data?.trend ?? [];
  const byModelBars = data?.byModelBars ?? [];
  const byChannelBars = data?.byChannelBars ?? [];
  const modelRows = data?.modelRows ?? [];
  const channelRows = data?.channelRows ?? [];
  const groupRows = data?.groupRows ?? [];

  const insights = useMemo(
    () => buildInsights(data?.modelRows ?? [], data?.channelRows ?? [], data?.groupRows ?? []),
    [data],
  );

  if (isError) {
    return (
      <AppShell activeId="profit" title="利润分析" crumb={['管理后台', '运营', '利润分析']}>
        <section className={styles.chartCard}>
          加载利润数据失败：{error instanceof ApiError ? error.message : '请稍后重试'}
          {error instanceof ApiError && error.status === 403 ? '（需管理员权限）' : ''}
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell
      activeId="profit"
      title="利润分析"
      crumb={['管理后台', '运营', '利润分析']}
      actions={<Button variant="sec" size="sm">导出报表</Button>}
    >
      {/* 时间范围切换 */}
      <section className={styles.rangeBar}>
        <div className={styles.rangeSeg}>
          {RANGES.map((r) => (
            <button key={r.id} className={rangeId === r.id ? styles.on : ''} onClick={() => setRangeId(r.id)} type="button">
              {r.lab}
            </button>
          ))}
        </div>
        <span className={styles.rangeNote}>数据来源：利润看板 /api/profit/dashboard + 按日配额 /api/data（售价 / 成本 / 利润）</span>
      </section>

      {/* 经营洞察提示条（由真实利润行派生） */}
      {insights.length > 0 ? (
        <section className={styles.insights}>
          {insights.map((it, i) => {
            const IconEl = INSIGHT_ICON[it.tone];
            return (
              <div key={i} className={`${styles.insight} ${styles[it.tone]} nx-fade`}>
                <span className={styles.insightIc} aria-hidden="true"><IconEl /></span>
                <div className={styles.insightBody}>{it.body}</div>
              </div>
            );
          })}
        </section>
      ) : null}

      {/* KPI 顶行 */}
      <section className={styles.kpiRow}>
        {isLoading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className={`${styles.kpi} nx-fade`}>
                <div className={styles.kpiLabel}>—</div>
                <div className={styles.kpiVal}>…</div>
              </div>
            ))
          : kpis.map((k) => (
              <div key={k.label} className={`${styles.kpi} nx-fade`}>
                <div className={styles.kpiLabel}>{k.label}</div>
                <div className={`${styles.kpiVal} ${k.valCls ? styles[k.valCls] : ''}`}>{k.val}</div>
              </div>
            ))}
      </section>

      {/* 图表区 1：营收趋势 + 利润按对外模型 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>营收趋势</h3>
              <div className={styles.chartSub}>单位：$ · 按日聚合 · 来自 /api/data</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartSub}>加载中…</div> : <RevCostTrend data={trend} />}
          <div className={styles.legend}>
            <span><i style={{ background: 'var(--chart-1)' }} />营收</span>
          </div>
        </div>

        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>利润按对外模型</h3>
              <div className={styles.chartSub}>横向柱状 · 亏损模型标红</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartSub}>加载中…</div> : (
            <HBarChart rows={byModelBars} W={460} H={320} pad={{ l: 118, r: 62, t: 10, b: 24 }} posColor="var(--chart-1)" />
          )}
        </div>
      </section>

      {/* 图表区 2：利润按供应商/渠道（全宽） */}
      <section className={styles.chartGridFull}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>利润按供应商 / 渠道</h3>
              <div className={styles.chartSub}>识别哪个渠道在亏 · 亏损标红</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartSub}>加载中…</div> : (
            <HBarChart rows={byChannelBars} W={980} H={300} pad={{ l: 188, r: 78, t: 10, b: 24 }} posColor="var(--chart-7)" ticks />
          )}
        </div>
      </section>

      {/* 三维度明细表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.thBar}>
          <h3 className={styles.chartTitle}>利润明细</h3>
          <div className={styles.tabs}>
            <button className={dim === 'model' ? styles.on : ''} onClick={() => setDim('model')} type="button">按对外模型</button>
            <button className={dim === 'vendor' ? styles.on : ''} onClick={() => setDim('vendor')} type="button">按供应商</button>
            <button className={dim === 'group' ? styles.on : ''} onClick={() => setDim('group')} type="button">按分组</button>
          </div>
        </div>

        {isLoading ? (
          <div className={styles.chartSub} style={{ padding: 'var(--space-5)' }}>加载中…</div>
        ) : dim === 'model' ? (
          <ProfitTable rows={modelRows} nameLabel="对外模型 A" diag={modelTip} />
        ) : dim === 'vendor' ? (
          <ProfitTable rows={channelRows} nameLabel="供应商 / 渠道" diag={vendorTip} />
        ) : (
          <ProfitTable rows={groupRows} nameLabel="分组" />
        )}
      </section>
    </AppShell>
  );
}




