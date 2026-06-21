'use client';

import { useState } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import styles from './ProfitPage.module.css';

/* ════════════════════════════════════════════════════════════════════════
   静态数据（迁移自 S6 admin/profit.html script，演示用）
   管理端视图：可展示成本/利润/上游供应商，不受客户端零泄露约束。
   ════════════════════════════════════════════════════════���═══════════════ */

/** 时间范围切换选项 */
const RANGES: { id: string; lab: string }[] = [
  { id: 'today', lab: '今日' },
  { id: '7d', lab: '近 7 日' },
  { id: '30d', lab: '近 30 日' },
  { id: 'custom', lab: '自定义' },
];

/** 经营洞察提示条 */
type InsightTone = 'good' | 'warn' | 'info';
const INSIGHTS: { tone: InsightTone; body: React.ReactNode }[] = [
  {
    tone: 'warn',
    body: (
      <>
        供应商 <b>满血直采 B</b> 的 <b>claude-opus</b> 利润率仅 <b>6%</b>，进货成本 2.0/M
        偏高，建议复议采购价或下调其在该模型下的权重。
      </>
    ),
  },
  {
    tone: 'good',
    body: (
      <>
        对外模型 <b>gpt-4o</b> 利润率高达 <b>62%</b>，需求旺、毛利厚，可考虑导入更多流量或上调展示权重。
      </>
    ),
  },
  {
    tone: 'info',
    body: (
      <>
        <b>vip</b> 分组贡献 <b>45%</b> 利润，是利润主力；<b>svip</b> 折扣最深、利润最薄属预期，但需持续盯防转负。
      </>
    ),
  },
];

/** KPI 顶行 4 卡 */
const KPIS: {
  label: string;
  val: string;
  valCls?: 'profit' | 'rateOk' | 'rateLow';
  delta: string;
  tone: 'up' | 'down' | 'flat';
}[] = [
  { label: '总营收（售价合计）', val: '$48,720.50', delta: '较上期 +8.4%', tone: 'up' },
  { label: '总成本（进货合计）', val: '$22,184.20', delta: '较上期 +5.1%', tone: 'up' },
  { label: '总利润', val: '$26,536.30', valCls: 'profit', delta: '较上期 +11.2%', tone: 'up' },
  { label: '整体利润率', val: '54.5%', valCls: 'rateOk', delta: '较上期 +1.6pt', tone: 'up' },
];

/** 图表 1：近 30 天 营收 / 成本 双折线（按日聚合） */
const REV = [
  1240, 1188, 1305, 1352, 1290, 1410, 1466, 1402, 1520, 1588, 1545, 1632, 1690, 1648, 1720, 1788,
  1742, 1830, 1896, 1854, 1928, 2002, 1968, 2050, 2118, 2086, 2164, 2232, 2198, 2280,
];
const COST = [
  560, 542, 598, 612, 588, 640, 668, 642, 690, 720, 705, 742, 770, 752, 786, 818, 800, 836, 866,
  850, 882, 916, 902, 938, 968, 956, 990, 1020, 1008, 1042,
];

/** 图表 2：利润按对外模型（横向柱状，亏损标红） */
const PROFIT_BY_MODEL: { name: string; val: number }[] = [
  { name: 'gpt-4o', val: 9480 },
  { name: 'claude-sonnet', val: 6240 },
  { name: 'gpt-4o-mini', val: 4120 },
  { name: 'gemini-1.5-pro', val: 2980 },
  { name: 'claude-opus', val: 1860 },
  { name: 'gpt-3.5-turbo', val: -640 },
];

/** 图表 3：利润按供应商（横向柱状，亏损标红） */
const PROFIT_BY_VENDOR: { name: string; val: number }[] = [
  { name: '残血聚合 A（成本0.1）', val: 11240 },
  { name: '第三方聚合 B', val: 7860 },
  { name: 'Azure OpenAI', val: 5120 },
  { name: 'Google Vertex', val: 3340 },
  { name: '满血直采 A（成本1.6）', val: 1180 },
  { name: '满血直采 B（成本2.0）', val: -820 },
];

/* ── 明细表维度数据 ── */
type ModelRow = { name: string; calls: number; rev: number; cost: number };
const TBL_MODEL: ModelRow[] = [
  { name: 'gpt-4o', calls: 842150, rev: 15280.4, cost: 5806.55 },
  { name: 'claude-sonnet', calls: 512300, rev: 11420.8, cost: 5180.3 },
  { name: 'gpt-4o-mini', calls: 1284600, rev: 6240.1, cost: 2120.4 },
  { name: 'gemini-1.5-pro', calls: 248900, rev: 5860.0, cost: 2880.0 },
  { name: 'claude-opus', calls: 96400, rev: 8120.2, cost: 7260.8 },
  { name: 'gpt-3.5-turbo', calls: 418200, rev: 1798.0, cost: 2438.0 },
];
const TBL_VENDOR: ModelRow[] = [
  { name: '残血聚合 A（成本0.1）', calls: 986400, rev: 14820.0, cost: 3580.0 },
  { name: '第三方聚合 B', calls: 642300, rev: 12640.0, cost: 4780.0 },
  { name: 'Azure OpenAI', calls: 418900, rev: 9860.0, cost: 4740.0 },
  { name: 'Google Vertex', calls: 248900, rev: 5860.0, cost: 2520.0 },
  { name: '满血直采 A（成本1.6）', calls: 62400, rev: 4180.0, cost: 3000.0 },
  { name: '满血直采 B（成本2.0）', calls: 38200, rev: 3560.0, cost: 4380.0 },
];
type GroupRow = { name: string; users: number; calls: number; rev: number; cost: number };
const TBL_GROUP: GroupRow[] = [
  { name: 'free', users: 9820, calls: 486200, rev: 4280.0, cost: 2680.0 },
  { name: 'vip', users: 2140, calls: 1024800, rev: 24180.5, cost: 10240.2 },
  { name: 'svip', users: 520, calls: 642100, rev: 20260.0, cost: 9264.0 },
];

/* ════════════════════════════════════════════════════════════════════════
   工具函数 + 小图标
   ════════════════════════════════════════════════════════════════════════ */

/** 金额格式化（千分位 + 两位小数，负数前置 -$）。 */
function money(v: number): string {
  const abs = Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return (v < 0 ? '-$' : '$') + abs;
}

/** 整数千分位格式化。 */
function intFmt(v: number): string {
  return v.toLocaleString('en-US');
}

/** 利润率 pill 的色档：<0 亏损红、<20 薄利黄、其余绿。 */
function rateClass(rate: number): 'ok' | 'mid' | 'bad' {
  if (rate < 0) return 'bad';
  if (rate < 20) return 'mid';
  return 'ok';
}

/** 上箭头图标（KPI delta / 表头排序用）。 */
const UpArrow = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 14l7-7 7 7" />
  </svg>
);
const DownArrowSmall = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 5v14" />
    <path d="M6 13l6 6 6-6" />
  </svg>
);
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

const INSIGHT_ICON: Record<InsightTone, () => JSX.Element> = {
  good: GoodIcon,
  warn: WarnIcon,
  info: InfoIcon,
};

/* ════════════════════════════════════════════════════════════════════════
   SVG 图表组件
   ════════════════════════════════════════════════════════════════════════ */

/**
 * RevCostTrend — 近 30 天营收 vs 成本双折线，两线之间填充为利润。
 * 营收线 chart-1，成本线 warning，填充渐变取营收色低透明度。
 */
function RevCostTrend() {
  const W = 760, H = 280;
  const pad = { l: 54, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = Math.max(...REV) * 1.08;
  const min = 0;
  const xs = (i: number) => pad.l + i * (iw / (REV.length - 1));
  const ys = (v: number) => pad.t + ih - ((v - min) / (max - min)) * ih;

  // 利润填充：沿营收线正向 + 沿成本线逆向闭合
  let fill = `M${xs(0)} ${ys(REV[0])}`;
  REV.forEach((v, i) => {
    fill += ` L${xs(i)} ${ys(v)}`;
  });
  for (let k = COST.length - 1; k >= 0; k--) {
    fill += ` L${xs(k)} ${ys(COST[k])}`;
  }
  fill += ' Z';

  const revPts = REV.map((v, i) => `${xs(i)},${ys(v)}`).join(' ');
  const costPts = COST.map((v, i) => `${xs(i)},${ys(v)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="近30天营收与成本双折线图，中间填充为利润">
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
            <text className={styles.axTxt} x={pad.l - 8} y={y + 3} textAnchor="end">
              ${Math.round(v)}
            </text>
          </g>
        );
      })}
      {REV.map((_, d) =>
        d % 5 === 0 ? (
          <text key={d} className={styles.axTxt} x={xs(d)} y={H - 10} textAnchor="middle">
            {d + 1}日
          </text>
        ) : null,
      )}
      <path d={fill} fill="url(#profitFill)" />
      <polyline points={revPts} fill="none" stroke="var(--chart-1)" strokeWidth={2.4} strokeLinejoin="round" />
      <polyline points={costPts} fill="none" stroke="var(--color-warning)" strokeWidth={2.2} strokeLinejoin="round" />
      <circle cx={xs(REV.length - 1)} cy={ys(REV[REV.length - 1])} r={4} fill="var(--chart-1)" />
      <circle cx={xs(COST.length - 1)} cy={ys(COST[COST.length - 1])} r={4} fill="var(--color-warning)" />
    </svg>
  );
}

/**
 * HBarChart — 通用横向柱状图（含零基准线，负值标红）。
 * 用于「利润按对外模型」「利润按供应商」两张图。
 */
function HBarChart({
  rows,
  W,
  H,
  pad,
  maxv,
  minv,
  posColor,
  ticks = false,
}: {
  rows: { name: string; val: number }[];
  W: number;
  H: number;
  pad: { l: number; r: number; t: number; b: number };
  maxv: number;
  minv: number;
  posColor: string;
  /** 是否绘制 X 轴刻度网格（供应商图开启） */
  ticks?: boolean;
}) {
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const span = maxv - minv;
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
                <text className={styles.axTxt} x={x} y={H - 8} textAnchor="middle">
                  ${Math.round(v)}
                </text>
              </g>
            );
          })
        : null}
      {/* 零基准线 */}
      <line
        x1={zx}
        y1={pad.t}
        x2={zx}
        y2={pad.t + ih}
        stroke={ticks ? 'var(--color-text-muted)' : 'var(--chart-grid)'}
        strokeWidth={1.4}
      />
      {rows.map((r, i) => {
        const cy = pad.t + gh * i + gh / 2;
        const bx = pad.l + ((Math.min(r.val, 0) - minv) / span) * iw;
        const bw = (Math.abs(r.val) / span) * iw;
        const col = r.val < 0 ? 'var(--color-danger)' : posColor;
        const lx = r.val < 0 ? bx - 8 : bx + bw + 8;
        const anc = r.val < 0 ? 'end' : 'start';
        const lab = r.val < 0 ? `-$${Math.abs(r.val)}` : `$${r.val}`;
        return (
          <g key={r.name}>
            <text className={styles.axTxt} x={pad.l - 10} y={cy + 4} textAnchor="end" style={{ fill: 'var(--color-text-secondary)' }}>
              {r.name}
            </text>
            <rect x={bx} y={cy - bh / 2} width={bw} height={bh} rx={4} fill={col} />
            <text className={styles.axTxt} x={lx} y={cy + 4} textAnchor={anc} style={{ fill: 'var(--color-text)' }}>
              {lab}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/* ════════════════════════════════════════════════════════════════════════
   明细表行渲染（利润 / 利润率单元格）
   ════════════════════════════════════════════════════════════════════════ */

/** 利润单元格：正绿负红，等宽字体。 */
function ProfitCell({ v }: { v: number }) {
  return <span className={v < 0 ? styles.profitNeg : styles.profitPos}>{money(v)}</span>;
}

/** 利润率 pill。 */
function RatePill({ rate }: { rate: number }) {
  return <span className={`${styles.ratePill} ${styles[rateClass(rate)]}`}>{rate.toFixed(1)}%</span>;
}

type TipKind = 'lossModel' | 'lowModel' | 'goodModel' | 'lossVendor' | 'goodVendor' | 'thinVendor' | null;

/** 诊断标签（按模型 / 按供应商规则不同）。 */
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

/** 模型维度诊断规则。 */
function modelTip(rate: number, loss: boolean): TipKind {
  if (loss) return 'lossModel';
  if (rate < 20) return 'lowModel';
  if (rate >= 55) return 'goodModel';
  return null;
}
/** 供应商维度诊断规则。 */
function vendorTip(rate: number, loss: boolean): TipKind {
  if (loss) return 'lossVendor';
  if (rate >= 60) return 'goodVendor';
  if (rate < 25) return 'thinVendor';
  return null;
}

/* ════════════════════════════════════════════════════════════════════════
   主组件
   ════════════════════════════════════════════════════════════════════════ */

type DimTab = 'model' | 'vendor' | 'group';

/**
 * ProfitPage — 利润分析看板（S6 admin/profit.html 工程化）。
 *
 * 售价/成本/利润三段式聚合，按对外模型/供应商/分组多维拆解。
 * 含时间范围切换、经营洞察提示条、4 KPI、营收成本双折线（利润填充）、
 * 利润按模型/供应商横向柱状、三维度可切换明细表（亏损标红 + 诊断标签）。
 * 管理端视图，可展示全字段（成本/利润/上游供应商），不受客户端零泄露约束。
 */
export function ProfitPage() {
  const [range, setRange] = useState('7d');
  const [dim, setDim] = useState<DimTab>('model');

  return (
    <AdminShell
      activeId="profit"
      title="利润分析"
      crumb={['管理后台', '运营', '利润分析']}
      actions={
        <Button variant="sec" size="sm">
          导出报表
        </Button>
      }
    >
      {/* 时间范围切换 */}
      <section className={styles.rangeBar}>
        <div className={styles.rangeSeg}>
          {RANGES.map((r) => (
            <button key={r.id} className={range === r.id ? styles.on : ''} onClick={() => setRange(r.id)} type="button">
              {r.lab}
            </button>
          ))}
        </div>
        <span className={styles.rangeNote}>数据来源：Log 表实时聚合（售价 / 成本 / 利润三段式）</span>
      </section>

      {/* 经营洞察提示条 */}
      <section className={styles.insights}>
        {INSIGHTS.map((it, i) => {
          const IconEl = INSIGHT_ICON[it.tone];
          return (
            <div key={i} className={`${styles.insight} ${styles[it.tone]} nx-fade`}>
              <span className={styles.insightIc} aria-hidden="true">
                <IconEl />
              </span>
              <div className={styles.insightBody}>{it.body}</div>
            </div>
          );
        })}
      </section>

      {/* KPI 顶行 */}
      <section className={styles.kpiRow}>
        {KPIS.map((k) => (
          <div key={k.label} className={`${styles.kpi} nx-fade`}>
            <div className={styles.kpiLabel}>{k.label}</div>
            <div className={`${styles.kpiVal} ${k.valCls ? styles[k.valCls] : ''}`}>{k.val}</div>
            <div className={`${styles.kpiDelta} ${styles[k.tone]}`}>
              {k.tone === 'up' ? <UpArrow /> : null}
              {k.delta}
            </div>
          </div>
        ))}
      </section>

      {/* 图表区 1：营收 vs 成本双折线 + 利润按对外模型 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>近 30 天 营收 vs 成本</h3>
              <div className={styles.chartSub}>单位：$ · 按日聚合 · 两线之间填充为利润</div>
            </div>
          </div>
          <RevCostTrend />
          <div className={styles.legend}>
            <span>
              <i style={{ background: 'var(--chart-1)' }} />
              营收
            </span>
            <span>
              <i style={{ background: 'var(--color-warning)' }} />
              成本
            </span>
            <span>
              <i style={{ background: 'color-mix(in oklch, var(--chart-1) 22%, transparent)' }} />
              利润（填充区）
            </span>
          </div>
        </div>

        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>利润按对外模型</h3>
              <div className={styles.chartSub}>横向柱状 · 亏损模型标红</div>
            </div>
          </div>
          <HBarChart
            rows={PROFIT_BY_MODEL}
            W={460}
            H={320}
            pad={{ l: 118, r: 62, t: 10, b: 24 }}
            maxv={10000}
            minv={-2000}
            posColor="var(--chart-1)"
          />
        </div>
      </section>

      {/* 图表区 2：利润按供应商（全宽） */}
      <section className={styles.chartGridFull}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>利润按供应商</h3>
              <div className={styles.chartSub}>
                识别哪个供应商在亏 · 残血低成本高毛利、满血高成本薄利甚至亏损 · 亏损标红
              </div>
            </div>
          </div>
          <HBarChart
            rows={PROFIT_BY_VENDOR}
            W={980}
            H={300}
            pad={{ l: 188, r: 78, t: 10, b: 24 }}
            maxv={12000}
            minv={-2000}
            posColor="var(--chart-7)"
            ticks
          />
        </div>
      </section>

      {/* 三维度明细表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.thBar}>
          <h3 className={styles.chartTitle}>利润明细</h3>
          <div className={styles.tabs}>
            <button className={dim === 'model' ? styles.on : ''} onClick={() => setDim('model')} type="button">
              按对外模型
            </button>
            <button className={dim === 'vendor' ? styles.on : ''} onClick={() => setDim('vendor')} type="button">
              按供应商
            </button>
            <button className={dim === 'group' ? styles.on : ''} onClick={() => setDim('group')} type="button">
              按分组
            </button>
          </div>
        </div>

        {/* 按对外模型 */}
        {dim === 'model' ? (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>对外模型 A</th>
                  <th className={styles.num}>调用量</th>
                  <th className={styles.num}>营收</th>
                  <th className={styles.num}>成本</th>
                  <th className={styles.num}>利润</th>
                  <th className={styles.num}>
                    <span className={styles.headSort}>
                      利润率
                      <DownArrowSmall />
                    </span>
                  </th>
                  <th>诊断</th>
                </tr>
              </thead>
              <tbody>
                {TBL_MODEL.map((r) => {
                  const profit = r.rev - r.cost;
                  const rate = (profit / r.rev) * 100;
                  const loss = profit < 0;
                  return (
                    <tr key={r.name} className={loss ? styles.loss : ''}>
                      <td className={styles.cellName}>{r.name}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{intFmt(r.calls)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.rev)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.cost)}</td>
                      <td className={styles.num}>
                        <ProfitCell v={profit} />
                      </td>
                      <td className={styles.num}>
                        <RatePill rate={rate} />
                      </td>
                      <td>
                        <DiagTag kind={modelTip(rate, loss)} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}

        {/* 按供应商 */}
        {dim === 'vendor' ? (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>供应商 / 渠道</th>
                  <th className={styles.num}>调用量</th>
                  <th className={styles.num}>承接营收</th>
                  <th className={styles.num}>成本</th>
                  <th className={styles.num}>利润</th>
                  <th className={styles.num}>
                    <span className={styles.headSort}>
                      利润率
                      <DownArrowSmall />
                    </span>
                  </th>
                  <th>导流建议</th>
                </tr>
              </thead>
              <tbody>
                {TBL_VENDOR.map((r) => {
                  const profit = r.rev - r.cost;
                  const rate = (profit / r.rev) * 100;
                  const loss = profit < 0;
                  return (
                    <tr key={r.name} className={loss ? styles.loss : ''}>
                      <td className={styles.cellName}>{r.name}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{intFmt(r.calls)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.rev)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.cost)}</td>
                      <td className={styles.num}>
                        <ProfitCell v={profit} />
                      </td>
                      <td className={styles.num}>
                        <RatePill rate={rate} />
                      </td>
                      <td>
                        <DiagTag kind={vendorTip(rate, loss)} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}

        {/* 按分组 */}
        {dim === 'group' ? (
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>分组</th>
                  <th className={styles.num}>用户数</th>
                  <th className={styles.num}>调用量</th>
                  <th className={styles.num}>营收</th>
                  <th className={styles.num}>成本</th>
                  <th className={styles.num}>利润</th>
                  <th className={styles.num}>利润率</th>
                </tr>
              </thead>
              <tbody>
                {TBL_GROUP.map((r) => {
                  const profit = r.rev - r.cost;
                  const rate = (profit / r.rev) * 100;
                  const loss = profit < 0;
                  return (
                    <tr key={r.name} className={loss ? styles.loss : ''}>
                      <td className={styles.cellName}>{r.name}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{intFmt(r.users)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{intFmt(r.calls)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.rev)}</td>
                      <td className={`${styles.num} ${styles.monoNum}`}>{money(r.cost)}</td>
                      <td className={styles.num}>
                        <ProfitCell v={profit} />
                      </td>
                      <td className={styles.num}>
                        <RatePill rate={rate} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </AdminShell>
  );
}
