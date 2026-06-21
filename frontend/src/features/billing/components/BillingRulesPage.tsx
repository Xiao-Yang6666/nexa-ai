'use client';

import { useState, useMemo } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import styles from './BillingRulesPage.module.css';

/* ── 内联线性图标（参照 ConsoleShell Icon 写法） ── */
const ICONS: Record<string, React.ReactNode> = {
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8h.01" />
      <path d="M11 12h1v4h1" />
    </>
  ),
  warn: (
    <>
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
      <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    </>
  ),
  check: <path d="M20 6 9 17l-5-5" />,
};

function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {ICONS[name] ?? null}
    </svg>
  );
}

/* ── Mock 数据（迁移自 S6 原型 script） ── */
type RuleKind = 'model' | 'group' | 'cache' | '补全';

interface RatioRow {
  nm: string;
  kind: RuleKind;
  in: number;
  out: number;
  cache: number;
  expose: boolean;
}

const DATA: RatioRow[] = [
  { nm: 'gpt-4o', kind: 'model', in: 2.5, out: 7.5, cache: 0.25, expose: true },
  { nm: 'gpt-4o-mini', kind: 'model', in: 0.15, out: 0.6, cache: 0.08, expose: true },
  { nm: 'o1-preview', kind: 'model', in: 7.5, out: 30.0, cache: 0.5, expose: false },
  { nm: 'claude-3-5-sonnet', kind: 'model', in: 1.5, out: 7.5, cache: 0.15, expose: true },
  { nm: 'claude-3-opus', kind: 'model', in: 7.5, out: 37.5, cache: 0.3, expose: false },
  { nm: 'gemini-1.5-pro', kind: 'model', in: 1.25, out: 5.0, cache: 0.2, expose: true },
  { nm: 'gemini-1.5-flash', kind: 'model', in: 0.07, out: 0.3, cache: 0.05, expose: true },
  { nm: 'deepseek-chat', kind: 'model', in: 0.14, out: 0.28, cache: 0.014, expose: true },
  { nm: 'text-embedding-3', kind: 'model', in: 0.02, out: 0.0, cache: 0.0, expose: true },
  { nm: 'default 分组', kind: 'group', in: 1.0, out: 1.0, cache: 1.0, expose: true },
  { nm: 'vip 分组', kind: 'group', in: 0.8, out: 0.8, cache: 0.8, expose: false },
  { nm: 'internal 分组', kind: 'group', in: 0.0, out: 0.0, cache: 0.0, expose: false },
  { nm: 'trial 分组', kind: 'group', in: 1.5, out: 1.5, cache: 1.5, expose: true },
  { nm: '全局缓存倍率', kind: 'cache', in: 0.25, out: 0.0, cache: 0.25, expose: true },
  { nm: '补全惩罚倍率', kind: '补全', in: 1.0, out: 1.2, cache: 1.0, expose: false },
];

const KIND_MAP: Record<RuleKind, { cls: string; tone: string }> = {
  model: { cls: 'b-info', tone: 'var(--color-info)' },
  group: { cls: 'b-suc', tone: 'var(--color-success)' },
  cache: { cls: 'b-warn', tone: 'var(--color-warning)' },
  '补全': { cls: 'b-neutral', tone: 'var(--color-text-muted)' },
};

function KindBadge({ kind }: { kind: RuleKind }) {
  const m = KIND_MAP[kind];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: m.tone }} />
      {kind}
    </span>
  );
}

function NumIn({ value }: { value: number }) {
  return <input className={styles.numIn} defaultValue={value.toFixed(2)} inputMode="decimal" />;
}

/* ── 阶梯计费静态数据 ── */
const MONTH_TIERS = [
  { rng: '$0 – $100', rate: '×1.00' },
  { rng: '$100 – $500', rate: '×0.95' },
  { rng: '$500 – $2000', rate: '×0.90' },
  { rng: '$2000+', rate: '×0.85' },
];

const GROUP_TIERS = [
  { rng: 'default', rate: '×1.00' },
  { rng: 'vip', rate: '×0.80' },
  { rng: 'internal', rate: '×0.00' },
  { rng: 'trial', rate: '×1.50' },
];

/* ── 倍率档位分布柱状图数据 ── */
const BINS = [
  { lab: '0–0.5', val: 5 },
  { lab: '0.5–1', val: 3 },
  { lab: '1–2', val: 4 },
  { lab: '2–5', val: 6 },
  { lab: '5–10', val: 3 },
  { lab: '10+', val: 2 },
];

function RatioDistChart() {
  const W = 1080;
  const H = 280;
  const pad = { l: 40, r: 14, t: 16, b: 34 };
  const iw = W - pad.l - pad.r;
  const ih = H - pad.t - pad.b;
  const max = 8;

  const gridLines = [];
  for (let t = 0; t <= 4; t++) {
    const v = (max * t) / 4;
    const y = pad.t + ih - (v / max) * ih;
    gridLines.push(
      <g key={`g${t}`}>
        <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
        <text className={styles.axTxt} x={pad.l - 7} y={y + 3} textAnchor="end">
          {v}
        </text>
      </g>,
    );
  }

  const gw = iw / BINS.length;
  const bw = gw * 0.5;
  const bars = BINS.map((b, i) => {
    const x = pad.l + gw * i + (gw - bw) / 2;
    const h = (b.val / max) * ih;
    const y = pad.t + ih - h;
    return (
      <g key={b.lab}>
        <rect x={x} y={y} width={bw} height={h} rx={4} fill="var(--chart-1)" />
        <text
          className={styles.axTxt}
          x={x + bw / 2}
          y={y - 6}
          textAnchor="middle"
          style={{ fill: 'var(--color-text)' }}
        >
          {b.val}
        </text>
        <text className={styles.axTxt} x={x + bw / 2} y={H - 10} textAnchor="middle">
          {b.lab}
        </text>
      </g>
    );
  });

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="倍率档位分布柱状图">
      {gridLines}
      {bars}
    </svg>
  );
}

export function BillingRulesPage() {
  const [fKind, setFKind] = useState('');
  const [search, setSearch] = useState('');
  const [confirmed, setConfirmed] = useState(false);

  const filtered = useMemo(() => {
    return DATA.filter((r) => {
      if (fKind && r.kind !== fKind) return false;
      if (search && !r.nm.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [fKind, search]);

  return (
    <AdminShell
      activeId="billing-rules"
      title="计费规则"
      crumb={['管理后台', '运营', '计费规则']}
      actions={<Button>同步倍率</Button>}
    >
      {/* 说明 */}
      <section className={`${styles.notice} nx-fade`}>
        <Icon name="info" className={styles.nxIc} />
        <div className={styles.txt}>
          本页配置<b>全站计费倍率</b>，包括模型倍率、分组倍率、缓存倍率与补全倍率。倍率变更将
          <b>影响所有渠道的定价计算</b>
          与用户实际扣费，请在合规确认后再点击「同步倍率」下发到生产环境。价格暴露开关（expose_ratio）控制是否向终端用户展示该模型的真实倍率。
        </div>
      </section>

      {/* 合规闸门 */}
      <section className={`${styles.gate} ${confirmed ? styles.confirmed : styles.pending}`}>
        <Icon name={confirmed ? 'check' : 'warn'} className={styles.nxIc} />
        <span className={styles.txt}>
          {confirmed
            ? '合规已确认：3 项倍率改动审核通过，可点击「同步倍率」下发到生产环境。'
            : '合规未确认：倍率配置存在 3 项待审改动，须经合规确认后方可同步至生产。'}
        </span>
        <span className={styles.grow} />
        <Button variant="sec" size="sm" onClick={() => setConfirmed((v) => !v)}>
          {confirmed ? '撤销确认' : '合规确认'}
        </Button>
      </section>

      {/* 工具条 */}
      <section className={`${styles.toolbar} nx-fade`}>
        <select className={styles.sel} value={fKind} onChange={(e) => setFKind(e.target.value)}>
          <option value="">全部类型</option>
          <option value="model">model</option>
          <option value="group">group</option>
          <option value="cache">cache</option>
          <option value="补全">补全</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索模型 / 分组名"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className={styles.grow} />
        <Button variant="sec" size="sm">
          新增倍率项
        </Button>
        <Button variant="sec" size="sm">
          从模板导入
        </Button>
      </section>

      {/* 倍率配置表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>模型 / 分组</th>
                <th>类型</th>
                <th>输入倍率</th>
                <th>输出倍率</th>
                <th>缓存倍率</th>
                <th>价格暴露</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.nm}>
                  <td>{r.nm}</td>
                  <td>
                    <KindBadge kind={r.kind} />
                  </td>
                  <td>
                    <NumIn value={r.in} />
                  </td>
                  <td>
                    <NumIn value={r.out} />
                  </td>
                  <td>
                    <NumIn value={r.cache} />
                  </td>
                  <td>
                    <label className="switch">
                      <input type="checkbox" defaultChecked={r.expose} />
                      <span className="track" />
                      <span className="thumb" />
                    </label>
                  </td>
                  <td>
                    <div className={styles.rowActs}>
                      <a>编辑</a>
                      <a>同步</a>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* 阶梯计费配置区块 */}
      <h3 className={styles.chartTitle} style={{ marginBottom: 'var(--space-3)' }}>
        阶梯计费规则
      </h3>
      <section className={styles.tierGrid}>
        <div className={`${styles.tierCard} nx-fade`}>
          <h4>按月用量阶梯</h4>
          <div className={styles.desc}>按用户当月累计消费分档折扣</div>
          <div className={styles.tierRows}>
            {MONTH_TIERS.map((t) => (
              <div className={styles.tierRow} key={t.rng}>
                <span className={styles.rng}>{t.rng}</span>
                <span className={styles.rate}>{t.rate}</span>
              </div>
            ))}
          </div>
        </div>
        <div className={`${styles.tierCard} nx-fade`}>
          <h4>缓存命中折扣表达式</h4>
          <div className={styles.desc}>命中缓存的输入按缓存倍率计费</div>
          <div className={styles.tierExpr}>
            cost = in_tokens × price
            <br />
            &nbsp;&nbsp;× (hit ? cache_ratio : 1.0)
            <br />
            &nbsp;&nbsp;× group_ratio
            <br />
            cache_ratio ∈ [0.10, 0.50]
          </div>
        </div>
        <div className={`${styles.tierCard} nx-fade`}>
          <h4>分组叠加规则</h4>
          <div className={styles.desc}>分组倍率与模型倍率相乘叠加</div>
          <div className={styles.tierRows}>
            {GROUP_TIERS.map((t) => (
              <div className={styles.tierRow} key={t.rng}>
                <span className={styles.rng}>{t.rng}</span>
                <span className={styles.rate}>{t.rate}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 倍率分布图表 */}
      <section className={`${styles.chartCard} nx-fade`}>
        <div className={styles.chartHead}>
          <div>
            <h3 className={styles.chartTitle}>倍率档位分布</h3>
            <div className={styles.chartSub}>各输入倍率档位的模型数量</div>
          </div>
        </div>
        <RatioDistChart />
      </section>
    </AdminShell>
  );
}
