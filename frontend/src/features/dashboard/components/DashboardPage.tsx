'use client';

import Link from 'next/link';
import { ConsoleShell } from '@/features/console';
import { useKpi, TREND_30D, MODEL_DIST, LATENCY_BY_MODEL } from '../model/dashboard.model';
import styles from './DashboardPage.module.css';

/* ── 静态最近调用记录（演示；后续接真实 log/self 最新 N 条） ── */
const RECENT: { time: string; model: string; tokens: string; fee: string; ms: string; ok: boolean }[] = [
  { time: '10:42:18', model: 'opus-4.8', tokens: '3,182', fee: '$0.0412', ms: '392 ms', ok: true },
  { time: '10:41:55', model: 'gpt-4o', tokens: '5,640', fee: '$0.0689', ms: '451 ms', ok: true },
  { time: '10:41:30', model: 'gpt-4o-mini', tokens: '1,204', fee: '$0.0031', ms: '176 ms', ok: true },
  { time: '10:40:12', model: 'gemini-2.5-pro', tokens: '2,890', fee: '$0.0241', ms: '318 ms', ok: true },
  { time: '10:39:44', model: 'opus-4.8', tokens: '4,021', fee: '$0.0518', ms: '—', ok: false },
  { time: '10:38:09', model: 'gpt-4o', tokens: '6,210', fee: '$0.0762', ms: '489 ms', ok: true },
  { time: '10:37:51', model: 'deepseek-v3', tokens: '1,980', fee: '$0.0099', ms: '244 ms', ok: true },
  { time: '10:36:33', model: 'opus-4.8', tokens: '2,150', fee: '$0.0276', ms: '407 ms', ok: true },
];

/* ── SVG 面积折线图 ─────────────────────────────────────────────────── */
function TrendChart() {
  const W = 720, H = 280;
  const pad = { l: 48, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = Math.max(...TREND_30D) * 1.1;
  const xs = (i: number) => pad.l + i * (iw / (TREND_30D.length - 1));
  const ys = (v: number) => pad.t + ih - (v / max) * ih;

  const points = TREND_30D.map((v, i) => `${xs(i)},${ys(v)}`).join(' ');
  const areaPath = `M${pad.l} ${ys(0)} ${TREND_30D.map((v, i) => `L${xs(i)} ${ys(v)}`).join(' ')} L${xs(TREND_30D.length - 1)} ${ys(0)} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="近30天调用量趋势折线图">
      <defs>
        <linearGradient id="tg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.32} />
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
              {Math.round(v)}k
            </text>
          </g>
        );
      })}
      {TREND_30D.map((_, i) =>
        i % 5 === 0 ? (
          <text key={i} className={styles.axTxt} x={xs(i)} y={H - 10} textAnchor="middle">
            {i + 1}日
          </text>
        ) : null,
      )}
      <path d={areaPath} fill="url(#tg)" />
      <polyline points={points} fill="none" stroke="var(--chart-1)" strokeWidth={2.4} strokeLinejoin="round" />
      <circle cx={xs(TREND_30D.length - 1)} cy={ys(TREND_30D[TREND_30D.length - 1])} r={4} fill="var(--chart-1)" />
    </svg>
  );
}

/* ── SVG 环形图 ──────────────────────────────────────────────────────── */
function DonutChart() {
  const cx = 120, cy = 110, r = 78, rin = 50;
  const total = MODEL_DIST.reduce((s, d) => s + d.val, 0);
  let ang = -Math.PI / 2;

  const pt = (a: number, rad: number) => [cx + Math.cos(a) * rad, cy + Math.sin(a) * rad];

  return (
    <svg viewBox="0 0 240 240" width="100%" height={200} role="img" aria-label="模型调用分布环形图">
      {MODEL_DIST.map((s) => {
        const a0 = ang;
        const a1 = ang + (s.val / total) * Math.PI * 2;
        ang = a1;
        const large = a1 - a0 > Math.PI ? 1 : 0;
        const p0 = pt(a0, r), p1 = pt(a1, r), q0 = pt(a1, rin), q1 = pt(a0, rin);
        const d = `M${p0[0]} ${p0[1]} A${r} ${r} 0 ${large} 1 ${p1[0]} ${p1[1]} L${q0[0]} ${q0[1]} A${rin} ${rin} 0 ${large} 0 ${q1[0]} ${q1[1]} Z`;
        return <path key={s.name} d={d} fill={`var(${s.col})`} />;
      })}
      <text className={styles.donutCenter} x={cx} y={cy - 2} textAnchor="middle" fontSize={26}>
        1.28M
      </text>
      <text className={styles.donutCenterSub} x={cx} y={cy + 18} textAnchor="middle" fontSize={11}>
        本月调用
      </text>
    </svg>
  );
}

/* ── SVG 分组柱状图 ──────────────────────────────────────────────────── */
function LatencyBars() {
  const W = 720, H = 240;
  const pad = { l: 48, r: 18, t: 14, b: 34 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = 1100;
  const gw = iw / LATENCY_BY_MODEL.length, bw = 18;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="模型延迟分布柱状图">
      {[0, 1, 2, 3, 4].map((t) => {
        const v = (max * t) / 4;
        const y = pad.t + ih - (v / max) * ih;
        return (
          <g key={t}>
            <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={pad.l - 8} y={y + 3} textAnchor="end">
              {v}
            </text>
          </g>
        );
      })}
      {LATENCY_BY_MODEL.map((r, i) => {
        const cx2 = pad.l + gw * i + gw / 2;
        const h50 = (r.p50 / max) * ih;
        const h95 = (r.p95 / max) * ih;
        return (
          <g key={r.m}>
            <rect x={cx2 - bw - 3} y={pad.t + ih - h50} width={bw} height={h50} rx={3} fill="var(--chart-1)" />
            <rect x={cx2 + 3} y={pad.t + ih - h95} width={bw} height={h95} rx={3} fill="var(--chart-4)" />
            <text className={styles.axTxt} x={cx2} y={H - 10} textAnchor="middle">
              {r.m}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/**
 * DashboardPage — 仪表盘（S6 console/dashboard.html 工程化）。
 *
 * KPI 顶行（本月调用量/消费/余额/累计请求）+ 30 天趋势 + 模型分布环形图 +
 * P50/P95 延迟柱状图 + 最近调用记录表。
 * 客户端零泄露：仅本人维度调用/消费/余额，无成本/利润/上游B/供应商。
 */
export function DashboardPage() {
  const kpi = useKpi();

  return (
    <ConsoleShell activeId="dashboard" title="仪表盘" crumb={['控制台', '仪表盘']}>
      {/* KPI 顶行 */}
      <section className={styles.kpiRow}>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.kpiLabel}>本月调用量</div>
          <div className={styles.kpiVal}>{kpi.data?.monthCalls?.toLocaleString() ?? '…'}</div>
        </div>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.kpiLabel}>本月消费</div>
          <div className={styles.kpiVal}>{kpi.data?.monthSpendUsd ?? '…'}</div>
        </div>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.kpiLabel}>当前余额</div>
          <div className={styles.kpiVal}>{kpi.data?.balanceUsd ?? '…'}</div>
        </div>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.kpiLabel}>累计请求</div>
          <div className={styles.kpiVal}>{kpi.data?.totalRequests?.toLocaleString() ?? '…'}</div>
        </div>
      </section>

      {/* 图表：趋势 + 环形 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>近 30 天调用量趋势</h3>
              <div className={styles.chartSub}>单位：千次调用 · 按日聚合</div>
            </div>
          </div>
          <TrendChart />
        </div>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>模型调用分布</h3>
              <div className={styles.chartSub}>本月按模型占比</div>
            </div>
          </div>
          <DonutChart />
          <div className={styles.legend}>
            {MODEL_DIST.map((s) => (
              <span key={s.name}>
                <i style={{ background: `var(${s.col})` }} />
                {s.name} {s.val}%
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* P50/P95 延迟 */}
      <section className={styles.chartGrid} style={{ gridTemplateColumns: '1fr' }}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>P50 / P95 延迟分布（按模型）</h3>
              <div className={styles.chartSub}>单位：毫秒 · 近 7 天</div>
            </div>
            <div className={styles.legend}>
              <span>
                <i style={{ background: 'var(--chart-1)' }} />
                P50
              </span>
              <span>
                <i style={{ background: 'var(--chart-4)' }} />
                P95
              </span>
            </div>
          </div>
          <LatencyBars />
        </div>
      </section>

      {/* 最近调用记录 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.thBar}>
          <h3 className={styles.chartTitle}>最近调用记录</h3>
          <Link className={styles.btnLink} href="/usage">
            查看全部用量
          </Link>
        </div>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>模型</th>
                <th>Tokens</th>
                <th>费用</th>
                <th>延迟</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {RECENT.map((r, i) => (
                <tr key={i}>
                  <td className="mono-num">{r.time}</td>
                  <td>{r.model}</td>
                  <td className="mono-num">{r.tokens}</td>
                  <td className="mono-num">{r.fee}</td>
                  <td className="mono-num">{r.ms}</td>
                  <td>
                    <span className={`badge ${r.ok ? 'b-suc' : 'b-dan'}`}>
                      <span className="dot" style={{ background: `var(${r.ok ? '--color-success' : '--color-danger'})` }} />
                      {r.ok ? '成功' : '失败'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </ConsoleShell>
  );
}
