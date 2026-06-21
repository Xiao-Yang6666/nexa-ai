'use client';

import { useState, useMemo } from 'react';
import { AdminShell } from '@/features/admin';
import { ApiError } from '@/shared/api';
import { useBillingRatios, type RatioKind, type RatioRowVM, type RatioBin } from '../model/ratio.model';
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

const KIND_MAP: Record<RatioKind, { cls: string; tone: string; lab: string }> = {
  model: { cls: 'b-info', tone: 'var(--color-info)', lab: 'model' },
  group: { cls: 'b-suc', tone: 'var(--color-success)', lab: 'group' },
};

function KindBadge({ kind }: { kind: RatioKind }) {
  const m = KIND_MAP[kind];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: m.tone }} />
      {m.lab}
    </span>
  );
}

/* ── 倍率档位分布柱状图（数据来自真实倍率分箱） ── */
function RatioDistChart({ bins }: { bins: RatioBin[] }) {
  const W = 1080;
  const H = 280;
  const pad = { l: 40, r: 14, t: 16, b: 34 };
  const iw = W - pad.l - pad.r;
  const ih = H - pad.t - pad.b;
  const max = Math.max(8, ...bins.map((b) => b.val));

  const gridLines = [];
  for (let t = 0; t <= 4; t++) {
    const v = (max * t) / 4;
    const y = pad.t + ih - (v / max) * ih;
    gridLines.push(
      <g key={`g${t}`}>
        <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
        <text className={styles.axTxt} x={pad.l - 7} y={y + 3} textAnchor="end">
          {Math.round(v)}
        </text>
      </g>,
    );
  }

  const gw = iw / Math.max(1, bins.length);
  const bw = gw * 0.5;
  const bars = bins.map((b, i) => {
    const x = pad.l + gw * i + (gw - bw) / 2;
    const h = (b.val / max) * ih;
    const y = pad.t + ih - h;
    return (
      <g key={b.lab}>
        <rect x={x} y={y} width={bw} height={h} rx={4} fill="var(--chart-1)" />
        <text className={styles.axTxt} x={x + bw / 2} y={y - 6} textAnchor="middle" style={{ fill: 'var(--color-text)' }}>
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

  const { data, isLoading, isError, error } = useBillingRatios();
  const rows: RatioRowVM[] = useMemo(() => data?.rows ?? [], [data]);
  const bins: RatioBin[] = data?.bins ?? [];

  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (fKind && r.kind !== fKind) return false;
      if (search && !r.nm.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [rows, fKind, search]);

  /* 分组折扣阶梯（来自真实 GroupRatio 行） */
  const groupTiers = useMemo(
    () => rows.filter((r) => r.kind === 'group').map((r) => ({ rng: r.nm.replace(' 分组', ''), rate: `×${r.in.toFixed(2)}` })),
    [rows],
  );

  return (
    <AdminShell
      activeId="billing-rules"
      title="计费规则"
      crumb={['管理后台', '运营', '计费规则']}
    >
      {/* 说明 */}
      <section className={`${styles.notice} nx-fade`}>
        <Icon name="info" className={styles.nxIc} />
        <div className={styles.txt}>
          本页展示<b>全站计费倍率</b>（模型倍率、分组折扣、缓存与补全倍率），数据来自系统选项（<code>ModelRatio</code>/<code>CompletionRatio</code>/<code>GroupRatio</code>/<code>CacheRatio</code>）。
          倍率变更影响所有渠道的定价计算与用户扣费，修改请在系统设置中按键操作并经合规确认。
        </div>
      </section>

      {/* 工具条 */}
      <section className={`${styles.toolbar} nx-fade`}>
        <select className={styles.sel} value={fKind} onChange={(e) => setFKind(e.target.value)}>
          <option value="">全部类型</option>
          <option value="model">model</option>
          <option value="group">group</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索模型 / 分组名"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className={styles.grow} />
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
                <th>输出 / 补全倍率</th>
                <th>缓存倍率</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={5} className={styles.emptyCell}>加载中…</td></tr>
              ) : isError ? (
                <tr><td colSpan={5} className={styles.emptyCell}>
                  加载失败：{error instanceof ApiError ? error.message : '请稍后重试'}
                  {error instanceof ApiError && error.status === 403 ? '（需 root 权限）' : ''}
                </td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={5} className={styles.emptyCell}>
                  {rows.length === 0 ? '未配置任何倍率' : '无匹配项'}
                </td></tr>
              ) : (
                filtered.map((r) => (
                  <tr key={`${r.kind}-${r.nm}`}>
                    <td>{r.nm}</td>
                    <td><KindBadge kind={r.kind} /></td>
                    <td className={styles.cellmono}>{r.in.toFixed(2)}</td>
                    <td className={styles.cellmono}>{r.kind === 'model' ? r.out.toFixed(2) : '—'}</td>
                    <td className={styles.cellmono}>{r.kind === 'model' ? r.cache.toFixed(2) : '—'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* 计费公式说明区块（BillingCalculator 语义，固定规则，非可配置数据） */}
      <h3 className={styles.chartTitle} style={{ marginBottom: 'var(--space-3)' }}>
        计费公式说明
      </h3>
      <section className={styles.tierGrid}>
        <div className={`${styles.tierCard} nx-fade`}>
          <h4>分组折扣（实时）</h4>
          <div className={styles.desc}>来自 GroupRatio，分组倍率与模型倍率相乘叠加</div>
          <div className={styles.tierRows}>
            {groupTiers.length === 0 ? (
              <div className={styles.desc}>未配置分组倍率</div>
            ) : (
              groupTiers.map((t) => (
                <div className={styles.tierRow} key={t.rng}>
                  <span className={styles.rng}>{t.rng}</span>
                  <span className={styles.rate}>{t.rate}</span>
                </div>
              ))
            )}
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
          </div>
        </div>
        <div className={`${styles.tierCard} nx-fade`}>
          <h4>等效输入计费</h4>
          <div className={styles.desc}>补全 token 按补全倍率放大计入等效输入</div>
          <div className={styles.tierExpr}>
            eff_in = prompt
            <br />
            &nbsp;&nbsp;+ completion × completion_ratio
            <br />
            quota = eff_in × model_ratio × group_ratio
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
        <RatioDistChart bins={bins} />
      </section>
    </AdminShell>
  );
}
