'use client';

import Link from 'next/link';
import { AdminShell } from '@/features/admin';
import styles from './AdminDashboardPage.module.css';

/* ── 数据（迁移自 S6 admin-dashboard.html script，开发期静态 mock） ── */

/** 6 张 KPI 卡。delta tone: up=同比上升(success) / down=下降(danger) / flat=持平 */
const KPIS: { label: string; val: string; delta: string; tone: 'up' | 'down' | 'flat' }[] = [
  { label: '今日请求量', val: '3,842,157', delta: '较昨日 +6.8%', tone: 'up' },
  { label: '今日费用', val: '$8,421.39', delta: '较昨日 +4.2%', tone: 'up' },
  { label: '活跃渠道数', val: '38 / 42', delta: '4 个自动禁用', tone: 'down' },
  { label: '总用户数', val: '12,480', delta: '较昨日 +37', tone: 'up' },
  { label: '今日任务量', val: '9,264', delta: '较昨日 +11.5%', tone: 'up' },
  { label: '全局成功率', val: '99.2%', delta: '较昨日 -0.1%', tone: 'flat' },
];

/** 近 30 天全站请求量（单位万次） */
const TREND_30D = [286, 302, 295, 318, 330, 322, 345, 360, 352, 374, 392, 385, 408, 420, 412, 438, 452, 470, 463, 488, 502, 495, 520, 536, 528, 552, 571, 564, 588, 612];

/** 模型调用分布（今日占比 %） */
const MODEL_DIST: { name: string; val: number; col: string }[] = [
  { name: 'GPT-4o', val: 34, col: '--chart-1' },
  { name: 'Claude 3.5', val: 26, col: '--chart-2' },
  { name: 'Gemini 1.5', val: 15, col: '--chart-4' },
  { name: 'GPT-4o-mini', val: 13, col: '--chart-5' },
  { name: '其他', val: 12, col: '--chart-6' },
];

/** Top 渠道请求量排名（今日，单位万次） */
const TOP_CHANNELS: { name: string; val: number; col: string }[] = [
  { name: 'OpenAI 主通道', val: 1248, col: '--chart-1' },
  { name: 'Azure OpenAI', val: 986, col: '--chart-2' },
  { name: 'Anthropic 官方', val: 742, col: '--chart-4' },
  { name: 'Google Vertex', val: 531, col: '--chart-5' },
  { name: '第三方聚合 A', val: 418, col: '--chart-6' },
  { name: '第三方聚合 B', val: 307, col: '--chart-7' },
];

/** 渠道健康度状态计数 */
const HEALTH: { lab: string; cnt: number; tone: string }[] = [
  { lab: '启用正常', cnt: 36, tone: '--color-success' },
  { lab: '手动禁用', cnt: 2, tone: '--color-text-muted' },
  { lab: '自动禁用', cnt: 3, tone: '--color-danger' },
  { lab: '限流告警', cnt: 1, tone: '--color-warning' },
];

/** 异常渠道告警（kind: auto=自动禁用 / warn=限流告警） */
const ALERTS: { name: string; type: string; err: string; kind: 'auto' | 'warn'; at: string }[] = [
  { name: '第三方聚合 C', type: 'OpenAI 兼容', err: '12.4%', kind: 'auto', at: '06-20 04:58' },
  { name: 'Cohere 备用', type: 'Cohere', err: '8.7%', kind: 'auto', at: '06-20 04:41' },
  { name: '第三方聚合 D', type: 'OpenAI 兼容', err: '6.2%', kind: 'warn', at: '06-20 04:33' },
  { name: 'Mistral 直连', type: 'Mistral', err: '5.1%', kind: 'warn', at: '06-20 04:12' },
  { name: 'Azure 备区', type: 'Azure OpenAI', err: '21.8%', kind: 'auto', at: '06-20 03:55' },
];

const ALERT_BADGE: Record<'auto' | 'warn', { cls: string; label: string; tone: string }> = {
  auto: { cls: 'b-dan', label: '自动禁用', tone: '--color-danger' },
  warn: { cls: 'b-warn', label: '限流告警', tone: '--color-warning' },
};

/* ── 图表 1：近30天全站请求量趋势（面积折线，--chart-1） ── */
function TrendChart() {
  const W = 760, H = 280;
  const pad = { l: 52, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = Math.max(...TREND_30D) * 1.1;
  const xs = (i: number) => pad.l + i * (iw / (TREND_30D.length - 1));
  const ys = (v: number) => pad.t + ih - (v / max) * ih;

  const points = TREND_30D.map((v, i) => `${xs(i)},${ys(v)}`).join(' ');
  const areaPath = `M${pad.l} ${ys(0)} ${TREND_30D.map((v, i) => `L${xs(i)} ${ys(v)}`).join(' ')} L${xs(TREND_30D.length - 1)} ${ys(0)} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="近30天全站请求量趋势折线图">
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
      {TREND_30D.map((_, i) =>
        i % 5 === 0 ? (
          <text key={i} className={styles.axTxt} x={xs(i)} y={H - 10} textAnchor="middle">
            {i + 1}日
          </text>
        ) : null,
      )}
      <path d={areaPath} fill="url(#adminTg)" />
      <polyline points={points} fill="none" stroke="var(--chart-1)" strokeWidth={2.4} strokeLinejoin="round" />
      <circle cx={xs(TREND_30D.length - 1)} cy={ys(TREND_30D[TREND_30D.length - 1])} r={4} fill="var(--chart-1)" />
    </svg>
  );
}

/* ── 图表 2：模型调用分布（环形 donut） ── */
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
      <text className={styles.donutCenter} x={cx} y={cy - 2} textAnchor="middle" fontSize={24}>
        3.84M
      </text>
      <text className={styles.donutCenterSub} x={cx} y={cy + 18} textAnchor="middle" fontSize={11}>
        今日请求
      </text>
    </svg>
  );
}

/* ── 图表 3：Top 渠道请求量排名（横向柱状，多色） ── */
function TopChannelBars() {
  const W = 720, H = 300;
  const pad = { l: 118, r: 54, t: 10, b: 24 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = 1300;
  const gh = ih / TOP_CHANNELS.length, bh = 18;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Top渠道请求量横向柱状图">
      {[0, 1, 2, 3, 4].map((t) => {
        const v = (max * t) / 4;
        const x = pad.l + (v / max) * iw;
        return (
          <g key={t}>
            <line x1={x} y1={pad.t} x2={x} y2={pad.t + ih} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={x} y={H - 8} textAnchor="middle">
              {v}
            </text>
          </g>
        );
      })}
      {TOP_CHANNELS.map((r, i) => {
        const cy = pad.t + gh * i + gh / 2;
        const w = (r.val / max) * iw;
        return (
          <g key={r.name}>
            <text className={styles.axTxt} x={pad.l - 10} y={cy + 4} textAnchor="end" style={{ fill: 'var(--color-text-secondary)' }}>
              {r.name}
            </text>
            <rect x={pad.l} y={cy - bh / 2} width={w} height={bh} rx={4} fill={`var(${r.col})`} />
            <text className={styles.axTxt} x={pad.l + w + 8} y={cy + 4} textAnchor="start" style={{ fill: 'var(--color-text)' }}>
              {r.val}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/**
 * AdminDashboardPage — 管理后台全局概览（S6 admin/admin-dashboard.html 工程化）。
 *
 * 6 张 KPI 卡（请求量/费用/活跃渠道/用户数/任务量/成功率）+ 近30天全站请求趋势折线 +
 * 模型调用分布环形图 + Top 渠道请求量横向柱状 + 渠道健康度状态计数条 + 异常渠道告警表。
 * 管理端可展示全局成本/费用维度（无客户端零泄露约束）。
 */
export function AdminDashboardPage() {
  const healthTotal = HEALTH.reduce((s, i) => s + i.cnt, 0);

  return (
    <AdminShell activeId="admin-dashboard" title="全局概览" crumb={['管理后台', '全局概览']}>
      {/* KPI 顶行 6 卡 */}
      <section className={styles.kpiRow}>
        {KPIS.map((k) => (
          <div key={k.label} className={`${styles.kpi} nx-fade`}>
            <div className={styles.kpiLabel}>{k.label}</div>
            <div className={styles.kpiVal}>{k.val}</div>
            <div className={`${styles.kpiDelta} ${styles[k.tone]}`}>{k.delta}</div>
          </div>
        ))}
      </section>

      {/* 图表区 1：趋势折线 + 模型环形 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>近 30 天全站请求量趋势</h3>
              <div className={styles.chartSub}>单位：万次请求 · 按日聚合</div>
            </div>
          </div>
          <TrendChart />
        </div>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>模型调用分布</h3>
              <div className={styles.chartSub}>今日按模型占比</div>
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

      {/* 图表区 2：Top 渠道横向柱状 + 渠道健康度 */}
      <section className={styles.chartGrid2}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>Top 渠道请求量排名</h3>
              <div className={styles.chartSub}>今日 · 单位万次</div>
            </div>
          </div>
          <TopChannelBars />
        </div>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>渠道健康度</h3>
              <div className={styles.chartSub}>按状态计数 · 共 42 个渠道</div>
            </div>
          </div>
          <div className={styles.healthRow}>
            {HEALTH.map((it) => {
              const pct = Math.round((it.cnt / healthTotal) * 100);
              return (
                <div key={it.lab} className={styles.healthItem}>
                  <span className={styles.healthLab}>
                    <span className="dot" style={{ background: `var(${it.tone})` }} />
                    {it.lab}
                  </span>
                  <span className={styles.healthBar}>
                    <i className={styles.healthBarInner} style={{ width: `${pct}%`, background: `var(${it.tone})` }} />
                  </span>
                  <span className={styles.healthCnt}>{it.cnt}</span>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* 异常渠道告警表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.thBar}>
          <h3 className={styles.chartTitle}>异常渠道告警</h3>
          <Link className="btn-link" href="/admin/channels">
            进入渠道管理
          </Link>
        </div>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>渠道名</th>
                <th>类型</th>
                <th>错误率</th>
                <th>状态</th>
                <th>最后错误时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {ALERTS.map((a) => {
                const badge = ALERT_BADGE[a.kind];
                return (
                  <tr key={a.name}>
                    <td>{a.name}</td>
                    <td className="muted">{a.type}</td>
                    <td className={styles.errRate}>{a.err}</td>
                    <td>
                      <span className={`badge ${badge.cls}`}>
                        <span className="dot" style={{ background: `var(${badge.tone})` }} />
                        {badge.label}
                      </span>
                    </td>
                    <td className="mono-num muted">{a.at}</td>
                    <td>
                      <Link className="btn-link" href="/admin/channels">
                        处理
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </AdminShell>
  );
}
