'use client';

import { Fragment, useMemo, useState } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import {
  useAdminLogs,
  type AdminLogRowVM,
  type AdminLogTone,
} from '@/features/log/model/admin-log.model';
import styles from './LogsAuditPage.module.css';

/* ════════════════════════════════════════════════════════════════════════
   管理端全量调用明细审计 —— 调真后端 GET /api/log/（F-4001, adminAuth）。
   管理侧视图含全字段（B/channel/quota_sell/quota_cost/quota_profit）；
   客户端零泄露铁律只约束 self-scope 客户视图，本页仅管理后台可达。
   ════════════════════════════════════════════════════════════════════════ */

const PAGE_SIZE = 20;

/* type 枚举值（对齐 openapi /api/log/ type 参数 + admin-log.model 的 TYPE_TO_TONE） */
const LOG_TYPE_VALUE: Record<string, number> = {
  consume: 2,
  manage: 3,
  login: 7,
  error: 5,
};

/* tone → 类型徽标展示（lab/cls/tone CSS 变量）。覆盖 AdminLogTone 全枚举。 */
const TONE_MAP: Record<AdminLogTone, { lab: string; cls: string; tone: string }> = {
  consume: { lab: '消费', cls: 'b-pri', tone: '--chart-1' },
  manage: { lab: '管理', cls: 'b-warn', tone: '--color-warning' },
  login: { lab: '登录', cls: 'b-suc', tone: '--color-success' },
  error: { lab: '错误', cls: 'b-dan', tone: '--color-danger' },
  topup: { lab: '充值', cls: 'b-suc', tone: '--chart-2' },
  system: { lab: '系统', cls: 'b-sec', tone: '--chart-3' },
  refund: { lab: '退款', cls: 'b-sec', tone: '--chart-4' },
  unknown: { lab: '其他', cls: 'b-sec', tone: '--color-text-secondary' },
};

/* 类型筛选下拉项（值对应 LOG_TYPE_VALUE 的 key，''=全部） */
const TYPE_OPTS: { v: string; lab: string }[] = [
  { v: '', lab: '全部类型' },
  { v: 'consume', lab: '消费' },
  { v: 'manage', lab: '管理' },
  { v: 'login', lab: '登录' },
  { v: 'error', lab: '错误' },
];
const STATUS_OPTS = [
  { v: '', lab: '全部状态' },
  { v: 'ok', lab: '成功' },
  { v: 'fail', lab: '失败' },
];

/* ── 工具函数 ── */
function money(v: number): string {
  return '$' + v.toFixed(4);
}

/* ── 小图标 ── */
const InfoCircle = () => (
  <svg viewBox="0 0 16 16" width="15" height="15" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="8" cy="8" r="6.5" />
    <path d="M8 5.5v3" />
    <path d="M8 11h.01" />
  </svg>
);
const SortArrows = () => (
  <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />
  </svg>
);
const ChevronRight = ({ size = 10 }: { size?: number }) => (
  <svg viewBox="0 0 16 16" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 4l4 4-4 4" />
  </svg>
);
const LockIcon = () => (
  <svg viewBox="0 0 16 16" width="11" height="11" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="7" width="9" height="6" rx="1" />
    <path d="M5.5 7V5.5a2.5 2.5 0 0 1 5 0V7" />
  </svg>
);

/* ── 单元格子组件（均吃 AdminLogRowVM 真实字段） ── */
function TypeBadge({ tone }: { tone: AdminLogTone }) {
  const m = TONE_MAP[tone];
  return (
    <span className={`badge ${m.cls} ${styles.typeBadge}`}>
      <span className="dot" style={{ background: `var(${m.tone})` }} />
      {m.lab}
    </span>
  );
}
function StatusBadge({ ok }: { ok: boolean }) {
  return ok ? (
    <span className="badge b-suc">
      <span className="dot" style={{ background: 'var(--color-success)' }} />
      成功
    </span>
  ) : (
    <span className="badge b-dan">
      <span className="dot" style={{ background: 'var(--color-danger)' }} />
      失败
    </span>
  );
}
function TokenCell({ r }: { r: AdminLogRowVM }) {
  if (!r.promptTokens && !r.completionTokens) return <span className={`${styles.muted} ${styles.cellmono}`}>—</span>;
  return (
    <span>
      <span className={styles.cellmono}>{r.promptTokens.toLocaleString()}</span>{' '}
      <span className={styles.muted}>/ {r.completionTokens.toLocaleString()}</span>
    </span>
  );
}
function FeeCell({ r }: { r: AdminLogRowVM }) {
  if (!r.hasFee) return <span className={`${styles.muted} ${styles.cellmono}`}>—</span>;
  return <span className={`${styles.money} ${styles.feeCol}`}>{money(r.sellUsd)}</span>;
}
function ModelCell({ r }: { r: AdminLogRowVM }) {
  if (r.resolved === '—' && r.requested === '—') return <span className={`${styles.muted} ${styles.cellmono}`}>—</span>;
  const sameName = r.requested === r.resolved;
  return (
    <div className={styles.modelcell}>
      <span className="req">{r.requested}</span>
      {sameName ? null : (
        <span className="land">
          <ChevronRight />
          实际 {r.resolved}
        </span>
      )}
    </div>
  );
}

/* ── 详情行 ── */
function DetailRow({ r }: { r: AdminLogRowVM }) {
  const hasModel = r.resolved !== '—' || r.requested !== '—';
  const profit = r.profitUsd;
  const pcls = profit < 0 ? styles.profitNeg : styles.profitPos;
  const ptxt = (profit < 0 ? '-$' : '$') + Math.abs(profit).toFixed(4);
  const hasMoney = r.sellUsd > 0 || r.costUsd > 0;

  return (
    <tr className={styles.detailRow}>
      <td colSpan={13}>
        {/* 模型块（含真实上游 B，仅管理可见） */}
        <div className={styles.detSection}>
          <h4>模型</h4>
          {hasModel ? (
            <div className={styles.detModel}>
              <span className="node">
                <span className="lbl">客户请求</span>
                {r.requested}
              </span>
              <span className={styles.arrX}>
                <ChevronRight size={11} />
              </span>
              <span className="node">
                <span className="lbl">实际调用</span>
                {r.resolved}
              </span>
              {r.upstreamModel !== '—' ? (
                <>
                  <span className={styles.arrX}>
                    <ChevronRight size={11} />
                  </span>
                  <span className="node">
                    <span className="lbl">上游真实 (B)</span>
                    {r.upstreamModel}
                  </span>
                </>
              ) : null}
            </div>
          ) : (
            <div className={styles.muted}>本笔无模型调用（登录 / 管理类操作）。</div>
          )}
        </div>

        {/* 计费块（成本/利润仅管理侧可见） */}
        <div className={styles.detSection}>
          <h4>计费明细</h4>
          {hasMoney ? (
            <>
              <div className={styles.detMoney}>
                <span className="k">客户实付</span>
                <span className="k">平台成本</span>
                <span className="k">利润</span>
                <span className="k">利润率</span>
                <span className={styles.money}>{money(r.sellUsd)}</span>
                <span className={styles.money}>{money(r.costUsd)}</span>
                <span className={`${styles.money} ${pcls}`}>{ptxt}</span>
                <span className={`${styles.money} ${pcls}`}>
                  {r.sellUsd ? ((profit / r.sellUsd) * 100).toFixed(1) + '%' : '—'}
                </span>
              </div>
              <div className={styles.adminNote}>
                <LockIcon />
                平台成本 / 利润仅管理侧可见，客户「调用明细」只显示其本人实付费用。
              </div>
            </>
          ) : (
            <div className={styles.muted}>本笔无计费（非消费类或请求失败未产生计费）。</div>
          )}
        </div>

        {/* 请求元信息 */}
        <div className={styles.detSection}>
          <h4>请求元信息</h4>
          <dl className={styles.detailGrid}>
            <div>
              <dt>request_id</dt>
              <dd>{r.requestId}</dd>
            </div>
            <div>
              <dt>上游 request_id</dt>
              <dd>{r.upstreamRequestId}</dd>
            </div>
            <div>
              <dt>供应商渠道</dt>
              <dd>{r.channelName}</dd>
            </div>
            <div>
              <dt>来源 IP</dt>
              <dd>{r.ip}</dd>
            </div>
            <div>
              <dt>User-Agent</dt>
              <dd>{r.userAgent}</dd>
            </div>
            <div>
              <dt>分组（折扣等级）</dt>
              <dd>{r.group}</dd>
            </div>
            <div>
              <dt>是否流式</dt>
              <dd>{r.isStream ? '是' : '否'}</dd>
            </div>
            <div>
              <dt>tokens 入 / 出</dt>
              <dd>
                {r.promptTokens.toLocaleString()} / {r.completionTokens.toLocaleString()}
              </dd>
            </div>
            <div>
              <dt>耗时</dt>
              <dd>{r.useTime} ms</dd>
            </div>
          </dl>
        </div>
      </td>
    </tr>
  );
}

/* ════════════════════════════════════════════════════════════════════════
   主组件
   ════════════════════════════════════════════════════════════════════════ */

/**
 * LogsAuditPage — 全局调用明细审计（S6 admin/logs.html 工程化，接真后端 GET /api/log/）。
 *
 * 管理侧全局视图：4 统计卡 + FilterBar（类型/分组/状态/搜索）
 * + 大表格（时间/类型/用户/模型/分组/Tokens/费用/IP/UA/耗时/状态/详情）
 * + 行内展开详情（模型链路含上游 B + 计费明细含成本利润 + 请求元信息）。
 * 管理端可展示全字段（成本/利润/上游 B/供应商）；客户端同款表格仅显示其本人调用且无成本利润。
 *
 * 数据来源：useAdminLogs（GET /api/log/，服务端分页）。类型/分组筛选下推到后端 query，
 * 状态/搜索为前端二次过滤（契约未提供这两维 query）。
 */
export function LogsAuditPage() {
  const [page, setPage] = useState(1);
  const [fType, setFType] = useState('');
  const [fGroup, setFGroup] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [fSearch, setFSearch] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  // 真后端：GET /api/log/?type&group&page&page_size（分页 + 部分筛选在服务端）。
  const { data, isLoading, isError, error } = useAdminLogs({
    type: fType ? LOG_TYPE_VALUE[fType] : undefined,
    group: fGroup || undefined,
    page,
    page_size: PAGE_SIZE,
  });

  const allRows = data?.rows ?? [];
  const total = data?.total ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  // 状态 + 搜索为前端二次过滤（契约无对应 query 维度）。
  const rows = useMemo(() => {
    const kw = fSearch.trim().toLowerCase();
    return allRows.filter((r) => {
      if (fStatus === 'ok' && !r.ok) return false;
      if (fStatus === 'fail' && r.ok) return false;
      if (
        kw &&
        r.requestId.toLowerCase().indexOf(kw) < 0 &&
        r.ip.toLowerCase().indexOf(kw) < 0 &&
        r.requested.toLowerCase().indexOf(kw) < 0 &&
        r.resolved.toLowerCase().indexOf(kw) < 0 &&
        r.user.toLowerCase().indexOf(kw) < 0
      )
        return false;
      return true;
    });
  }, [allRows, fStatus, fSearch]);

  function reset() {
    setFType('');
    setFGroup('');
    setFStatus('');
    setFSearch('');
    setPage(1);
  }

  return (
    <AdminShell
      activeId="logs"
      title="调用明细"
      crumb={['管理后台', '系统', '调用明细']}
      actions={
        <>
          <Button variant="sec" size="sm">
            导出
          </Button>
          <Button variant="danger" size="sm">
            清理历史日志
          </Button>
        </>
      }
    >
      {/* 统计小卡片（本页范围聚合：基于当前页拉取的真实数据） */}
      <section className={styles.statRow}>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>本页调用数</div>
          <div className={styles.statVal}>{rows.length.toLocaleString()}</div>
        </div>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>本页错误数</div>
          <div className={`${styles.statVal} ${styles.dang}`}>
            {rows.filter((r) => !r.ok).length.toLocaleString()}
          </div>
        </div>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>本页消耗费用</div>
          <div className={`${styles.statVal} ${styles.ok}`}>
            {money(rows.reduce((s, r) => s + r.sellUsd, 0))}
          </div>
        </div>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>总记录数</div>
          <div className={styles.statVal}>{total.toLocaleString()}</div>
        </div>
      </section>

      {/* 全局范围说明条 */}
      <section className={`${styles.scopeNote} nx-fade`}>
        <InfoCircle />
        <span className="t">
          管理侧为 <b>全局视图</b>，展示所有用户的每一次调用明细。客户端「调用明细」同款表格，仅显示其本人的调用。
        </span>
      </section>

      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fType} onChange={(e) => { setFType(e.target.value); setPage(1); }} aria-label="类型筛选">
          {TYPE_OPTS.map((o) => (
            <option key={o.v} value={o.v}>
              {o.lab}
            </option>
          ))}
        </select>
        <input
          className={styles.sel}
          type="text"
          placeholder="分组（如 vip / svip）"
          value={fGroup}
          onChange={(e) => { setFGroup(e.target.value); setPage(1); }}
          aria-label="分组筛选"
        />
        <select className={styles.sel} value={fStatus} onChange={(e) => setFStatus(e.target.value)} aria-label="状态筛选">
          {STATUS_OPTS.map((o) => (
            <option key={o.v} value={o.v}>
              {o.lab}
            </option>
          ))}
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索 request_id / IP / 模型 / 用户"
          value={fSearch}
          onChange={(e) => setFSearch(e.target.value)}
        />
        <span className={styles.grow} />
        <Button variant="ghost" size="sm" onClick={reset}>
          重置
        </Button>
      </section>

      {/* 调用明细表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th className={styles.sortable}>
                  时间
                  <span className={styles.arr}>
                    <SortArrows />
                  </span>
                </th>
                <th>类型</th>
                <th>用户</th>
                <th>模型</th>
                <th>分组</th>
                <th className={styles.sortable}>
                  Tokens (入/出)
                  <span className={styles.arr}>
                    <SortArrows />
                  </span>
                </th>
                <th className={styles.sortable}>
                  费用
                  <span className={styles.arr}>
                    <SortArrows />
                  </span>
                </th>
                <th>IP</th>
                <th>User-Agent</th>
                <th className={styles.sortable}>
                  耗时
                  <span className={styles.arr}>
                    <SortArrows />
                  </span>
                </th>
                <th>状态</th>
                <th>详情</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={13} className={styles.emptyRow}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={13} className={styles.emptyRow} style={{ color: 'var(--color-danger)' }}>
                    加载失败：{error instanceof Error ? error.message : '请稍后重试'}
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={13} className={styles.emptyRow}>
                    暂无调用记录
                  </td>
                </tr>
              ) : (
                rows.map((r) => (
                  <Fragment key={`${r.id}-${r.requestId}`}>
                    <tr>
                      <td className={`${styles.cellmono} ${styles.muted}`}>{r.time}</td>
                      <td>
                        <TypeBadge tone={r.tone} />
                      </td>
                      <td className={styles.cellmono}>{r.user}</td>
                      <td>
                        <ModelCell r={r} />
                      </td>
                      <td className={`${styles.cellmono} ${styles.muted}`}>{r.group}</td>
                      <td>
                        <TokenCell r={r} />
                      </td>
                      <td>
                        <FeeCell r={r} />
                      </td>
                      <td className={styles.ipcell}>{r.ip}</td>
                      <td>
                        <div className={styles.uacell} title={r.userAgent}>
                          {r.userAgent}
                        </div>
                      </td>
                      <td className={`${styles.cellmono} ${styles.muted}`}>{r.useTime} ms</td>
                      <td>
                        <StatusBadge ok={r.ok} />
                      </td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => setExpanded(expanded === r.requestId ? null : r.requestId)}>
                            {expanded === r.requestId ? '收起' : '展开'}
                          </a>
                        </div>
                      </td>
                    </tr>
                    {expanded === r.requestId ? <DetailRow r={r} /> : null}
                  </Fragment>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>
            共 {total.toLocaleString()} 条 · 第 {page} / {totalPages} 页
          </span>
          <div className={styles.pg}>
            <button type="button" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
              上一页
            </button>
            <button type="button" className={styles.on}>
              {page}
            </button>
            <button type="button" disabled={page >= totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>
              下一页
            </button>
          </div>
        </div>
      </section>
    </AdminShell>
  );
}
