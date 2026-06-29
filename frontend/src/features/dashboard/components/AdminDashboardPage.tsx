'use client';

import { AppShell } from '@/features/shell';
import { ApiError } from '@/shared/api';
import {
  useAdminDashboard,
  type TrendPoint,
  type ModelDistItem,
  type TopChannelItem,
} from '../model/admin-dashboard.model';
import styles from './AdminDashboardPage.module.css';

const CHART_COLS = ['--chart-1', '--chart-2', '--chart-4', '--chart-5', '--chart-6', '--chart-7'];

/* ── 图表 1：近 N 天请求量趋势（面积折线，--chart-1） ── */
function TrendChart({ data }: { data: TrendPoint[] }) {
  const W = 760, H = 280;
  const pad = { l: 52, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  if (data.length === 0) {
    return <div className={styles.chartEmpty}>暂无趋势数据</div>;
  }
  const vals = data.map((d) => d.count);
  const max = Math.max(1, ...vals) * 1.1;
  const n = data.length;
  const xs = (i: number) => pad.l + (n === 1 ? iw / 2 : i * (iw / (n - 1)));
  const ys = (v: number) => pad.t + ih - (v / max) * ih;

  const points = data.map((d, i) => `${xs(i)},${ys(d.count)}`).join(' ');
  const areaPath = `M${pad.l} ${ys(0)} ${data.map((d, i) => `L${xs(i)} ${ys(d.count)}`).join(' ')} L${xs(n - 1)} ${ys(0)} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="请求量趋势折线图">
      <defs>
        <linearGradient id="adminTg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.3} />
          <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0} />
        </linearGradient>
      </defs>
      {[0, 1, 2, 3, 4].map((t) => {
        const v = (max * t) / 4;
        const y = ys(v);
        return (
          <g key={t}>
            <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={pad.l - 8} y={y + 3} textAnchor="end">
              {Math.round(v)}
            </text>
          </g>
        );
      })}
      {data.map((d, i) =>
        i % Math.ceil(n / 6) === 0 ? (
          <text key={i} className={styles.axTxt} x={xs(i)} y={H - 10} textAnchor="middle">
            {d.date.slice(5)}
          </text>
        ) : null,
      )}
      <path d={areaPath} fill="url(#adminTg)" />
      <polyline points={points} fill="none" stroke="var(--chart-1)" strokeWidth={2.4} strokeLinejoin="round" />
      <circle cx={xs(n - 1)} cy={ys(data[n - 1].count)} r={4} fill="var(--chart-1)" />
    </svg>
  );
}

/* ── 图表 2：模型调用分布（环形 donut） ── */
function DonutChart({ data, total }: { data: ModelDistItem[]; total: number }) {
  const cx = 120, cy = 110, r = 78, rin = 50;
  if (data.length === 0) {
    return <div className={styles.chartEmpty}>暂无分布数据</div>;
  }
  const sum = data.reduce((s, d) => s + d.val, 0) || 1;
  let ang = -Math.PI / 2;
  const pt = (a: number, rad: number) => [cx + Math.cos(a) * rad, cy + Math.sin(a) * rad];

  return (
    <svg viewBox="0 0 240 240" width="100%" height={200} role="img" aria-label="模型调用分布环形图">
      {data.map((s, i) => {
        const a0 = ang;
        const a1 = ang + (s.val / sum) * Math.PI * 2;
        ang = a1;
        const large = a1 - a0 > Math.PI ? 1 : 0;
        const p0 = pt(a0, r), p1 = pt(a1, r), q0 = pt(a1, rin), q1 = pt(a0, rin);
        const d = `M${p0[0]} ${p0[1]} A${r} ${r} 0 ${large} 1 ${p1[0]} ${p1[1]} L${q0[0]} ${q0[1]} A${rin} ${rin} 0 ${large} 0 ${q1[0]} ${q1[1]} Z`;
        return <path key={s.name} d={d} fill={`var(${CHART_COLS[i % CHART_COLS.length]})`} />;
      })}
      <text className={styles.donutCenter} x={cx} y={cy - 2} textAnchor="middle" fontSize={22}>
        {total >= 1000 ? `${(total / 1000).toFixed(1)}K` : String(total)}
      </text>
      <text className={styles.donutCenterSub} x={cx} y={cy + 18} textAnchor="middle" fontSize={11}>
        区间请求
      </text>
    </svg>
  );
}

/* ── 图表 3：Top 渠道售出额排名（横向柱状，多色） ── */
function TopChannelBars({ data }: { data: TopChannelItem[] }) {
  const W = 720, H = 300;
  const pad = { l: 118, r: 54, t: 10, b: 24 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  if (data.length === 0) {
    return <div className={styles.chartEmpty}>暂无渠道数据</div>;
  }
  const max = Math.max(1, ...data.map((d) => d.sellUsd)) * 1.1;
  const gh = ih / data.length, bh = 18;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Top渠道售出额横向柱状图">
      {[0, 1, 2, 3, 4].map((t) => {
        const v = (max * t) / 4;
        const x = pad.l + (v / max) * iw;
        return (
          <g key={t}>
            <line x1={x} y1={pad.t} x2={x} y2={pad.t + ih} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={x} y={H - 8} textAnchor="middle">
              ${Math.round(v)}
            </text>
          </g>
        );
      })}
      {data.map((r, i) => {
        const cy = pad.t + gh * i + gh / 2;
        const w = (r.sellUsd / max) * iw;
        return (
          <g key={r.name}>
            <text className={styles.axTxt} x={pad.l - 10} y={cy + 4} textAnchor="end" style={{ fill: 'var(--color-text-secondary)' }}>
              {r.name.length > 12 ? r.name.slice(0, 11) + '…' : r.name}
            </text>
            <rect x={pad.l} y={cy - bh / 2} width={w} height={bh} rx={4} fill={`var(${CHART_COLS[i % CHART_COLS.length]})`} />
            <text className={styles.axTxt} x={pad.l + w + 8} y={cy + 4} textAnchor="start" style={{ fill: 'var(--color-text)' }}>
              ${r.sellUsd.toFixed(0)}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/**
 * AdminDashboardPage — 管理后台全局概览（S6 admin/admin-dashboard.html 工程化，已接真实接口）。
 *
 * 数据组合自两个管理端接口（后端无单一聚合端点）：
 *  - GET /api/data/（按日配额）→ KPI 今日/区间请求量、消费额、趋势、模型分布
 *  - GET /api/profit/dashboard?dimension=channel → Top 渠道售出额
 */
export function AdminDashboardPage() {
  const { data, isLoading, isError, error } = useAdminDashboard(30);

  const kpis = data?.kpis ?? [];
  const trend: TrendPoint[] = data?.trend ?? [];
  const modelDist: ModelDistItem[] = data?.modelDist ?? [];
  const topChannels: TopChannelItem[] = data?.topChannels ?? [];
  const totalReq = trend.reduce((s, t) => s + t.count, 0);

  if (isError) {
    return (
      <AppShell activeId="admin-dashboard" title="全局概览" crumb={['管理后台', '全局概览']}>
        <section className={styles.errorBox}>
          加载概览失败：{error instanceof ApiError ? error.message : '请稍后重试'}
          {error instanceof ApiError && error.status === 403 ? '（需管理员权限）' : ''}
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell activeId="admin-dashboard" title="全局概览" crumb={['管理后台', '全局概览']}>
      {/* KPI 顶行 */}
      <section className={styles.kpiRow}>
        {isLoading
          ? Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className={`${styles.kpi} nx-fade`}>
                <div className={styles.kpiLabel}>—</div>
                <div className={styles.kpiVal}>…</div>
              </div>
            ))
          : kpis.map((k) => (
              <div key={k.label} className={`${styles.kpi} nx-fade`}>
                <div className={styles.kpiLabel}>{k.label}</div>
                <div className={styles.kpiVal}>{k.val}</div>
              </div>
            ))}
      </section>

      {/* 图表区 1：趋势折线 + 模型环形 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>近 30 天请求量趋势</h3>
              <div className={styles.chartSub}>按日聚合 · 来自 /api/data</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartEmpty}>加载中…</div> : <TrendChart data={trend} />}
        </div>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>模型调用分布</h3>
              <div className={styles.chartSub}>区间按模型占比</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartEmpty}>加载中…</div> : <DonutChart data={modelDist} total={totalReq} />}
          <div className={styles.legend}>
            {modelDist.map((s, i) => (
              <span key={s.name}>
                <i style={{ background: `var(${CHART_COLS[i % CHART_COLS.length]})` }} />
                {s.name} {s.val}%
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* 图表区 2：Top 渠道横向柱状 */}
      <section className={styles.chartGrid2}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>Top 渠道售出额排名</h3>
              <div className={styles.chartSub}>区间 · 单位 USD · 来自利润看板</div>
            </div>
          </div>
          {isLoading ? <div className={styles.chartEmpty}>加载中…</div> : <TopChannelBars data={topChannels} />}
        </div>
      </section>
    </AppShell>
  );
}
