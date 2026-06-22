'use client';

import { useState, useMemo } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useRedemptions,
  useGenerateRedemptions,
  usdToQuota,
  daysToExpiry,
  type RedeemStatus,
  type RedeemRowVM,
} from '../model/redeem.model';
import styles from './RedeemPage.module.css';

/* ── 内联线性图标（迁移自 S6 原型 SVG） ── */
const ICONS: Record<string, React.ReactNode> = {
  ticket: (
    <>
      <path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z" />
      <path d="M14 6v12" />
    </>
  ),
  grid: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>
  ),
};

function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {ICONS[name] ?? null}
    </svg>
  );
}

/* 排序箭头图标 */
function SortArrow() {
  return (
    <span className={styles.arr}>
      <svg
        viewBox="0 0 16 16"
        width={12}
        height={12}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />
      </svg>
    </span>
  );
}

const ST_MAP: Record<RedeemStatus, { cls: string; tone: string; lab: string }> = {
  unused: { cls: 'b-suc', tone: 'var(--color-success)', lab: '未使用' },
  used: { cls: 'b-neutral', tone: 'var(--color-text-muted)', lab: '已使用' },
  expired: { cls: 'b-dan', tone: 'var(--color-danger)', lab: '已过期' },
  disabled: { cls: 'b-dan', tone: 'var(--color-danger)', lab: '已禁用' },
};

function StatusBadge({ st }: { st: RedeemStatus }) {
  const m = ST_MAP[st];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: m.tone }} />
      {m.lab}
    </span>
  );
}

/* ── 复制工具（判空 clipboard） ── */
function copyText(text: string): void {
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    void navigator.clipboard.writeText(text);
  }
}

const PAGE_SIZE = 20;

export function RedeemPage() {
  /* 分页 */
  const [page, setPage] = useState(1);

  /* 列表数据（真实接口） */
  const { data, isLoading, isError, error } = useRedemptions(page, PAGE_SIZE);
  const rows: RedeemRowVM[] = useMemo(() => data?.rows ?? [], [data]);
  const total = data?.total ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  /* 生成 mutation */
  const genMutation = useGenerateRedemptions();

  /* 单个生成表单 */
  const [singleAmt, setSingleAmt] = useState('20');
  const [singleDays, setSingleDays] = useState('90');
  const [singleResult, setSingleResult] = useState<string | null>(null);

  /* 批量生成表单 */
  const [batchCount, setBatchCount] = useState('50');
  const [batchAmt, setBatchAmt] = useState('10');
  const [batchDays, setBatchDays] = useState('180');
  const [batchList, setBatchList] = useState<string[] | null>(null);
  const [batchExtra, setBatchExtra] = useState(0);

  /* 筛选（客户端过滤当前页；后端列表暂无状态/批次过滤参数） */
  const [fStatus, setFStatus] = useState('');
  const [fBatch, setFBatch] = useState('');
  const [search, setSearch] = useState('');

  /* 行复制态 */
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  /* 批次下拉选项从当前页数据动态提取 */
  const batchOptions = useMemo(() => {
    const set = new Set<string>();
    rows.forEach((r) => {
      if (r.batch && r.batch !== '—') set.add(r.batch);
    });
    return Array.from(set);
  }, [rows]);

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (fStatus && r.st !== fStatus) return false;
      if (fBatch && r.batch !== fBatch) return false;
      if (kw && !r.code.toLowerCase().includes(kw) && !r.user.toLowerCase().includes(kw)) {
        return false;
      }
      return true;
    });
  }, [rows, fStatus, fBatch, search]);

  async function handleGenSingle() {
    setSingleResult(null);
    const usd = parseFloat(singleAmt) || 0;
    const days = parseInt(singleDays, 10) || 0;
    if (usd <= 0) return;
    const keys = await genMutation.mutateAsync({
      name: '单个生成',
      quota: usdToQuota(usd),
      count: 1,
      expired_time: daysToExpiry(days),
    });
    setSingleResult(keys[0] ?? null);
  }

  async function handleGenBatch() {
    setBatchList(null);
    setBatchExtra(0);
    const usd = parseFloat(batchAmt) || 0;
    const days = parseInt(batchDays, 10) || 0;
    const n = Math.max(1, Math.min(1000, parseInt(batchCount, 10) || 1));
    if (usd <= 0) return;
    const keys = await genMutation.mutateAsync({
      name: `B-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}`,
      quota: usdToQuota(usd),
      count: n,
      expired_time: daysToExpiry(days),
    });
    const preview = keys.slice(0, 30);
    setBatchList(preview);
    setBatchExtra(keys.length > 30 ? keys.length - 30 : 0);
  }

  function handleExportBatch() {
    if (!batchList) return;
    const csv = batchList.join('\n');
    copyText(csv);
  }

  function handleRowCopy(code: string) {
    copyText(code);
    setCopiedCode(code);
    setTimeout(() => {
      setCopiedCode((cur) => (cur === code ? null : cur));
    }, 1200);
  }

  const generating = genMutation.isPending;

  return (
    <AppShell activeId="redeem" title="兑换码管理" crumb={['管理后台', '运营', '兑换码']}>
      {/* 顶部生成卡 */}
      <section className={styles.genGrid}>
        {/* 单个生成 */}
        <div className={`${styles.genCard} nx-fade`}>
          <h3>
            <Icon name="ticket" className={styles.nxIc} />
            生成单个兑换码
          </h3>
          <div className={styles.genFields}>
            <div>
              <label className="field-label">面额（美元）</label>
              <input
                className={`input ${styles.cellmono}`}
                value={singleAmt}
                inputMode="decimal"
                onChange={(e) => setSingleAmt(e.target.value)}
              />
            </div>
            <div>
              <label className="field-label">有效期（天，0=永久）</label>
              <input
                className={`input ${styles.cellmono}`}
                value={singleDays}
                inputMode="numeric"
                onChange={(e) => setSingleDays(e.target.value)}
              />
            </div>
          </div>
          <div className={styles.genFoot}>
            <span className={styles.grow} />
            <Button onClick={handleGenSingle} disabled={generating}>
              {generating ? '生成中…' : '生成'}
            </Button>
          </div>
          {singleResult && (
            <div className={styles.resultBox}>
              <div className={styles.code}>
                <span>{singleResult}</span>
                <a onClick={() => copyText(singleResult)}>复制</a>
              </div>
            </div>
          )}
        </div>

        {/* 批量生成 */}
        <div className={`${styles.genCard} nx-fade`}>
          <h3>
            <Icon name="grid" className={styles.nxIc} />
            批量生成
          </h3>
          <div className={styles.genFields}>
            <div className={styles.genRow}>
              <div>
                <label className="field-label">数量</label>
                <input
                  className={`input ${styles.cellmono}`}
                  value={batchCount}
                  inputMode="numeric"
                  onChange={(e) => setBatchCount(e.target.value)}
                />
              </div>
              <div>
                <label className="field-label">面额（美元）</label>
                <input
                  className={`input ${styles.cellmono}`}
                  value={batchAmt}
                  inputMode="decimal"
                  onChange={(e) => setBatchAmt(e.target.value)}
                />
              </div>
            </div>
            <div>
              <label className="field-label">有效期（天，0=永久）</label>
              <input
                className={`input ${styles.cellmono}`}
                value={batchDays}
                inputMode="numeric"
                onChange={(e) => setBatchDays(e.target.value)}
              />
            </div>
          </div>
          <div className={styles.genFoot}>
            <span className={styles.muted}>单批最多 1000 个</span>
            <span className={styles.grow} />
            <Button variant="sec" size="sm" onClick={handleExportBatch} disabled={!batchList}>
              复制全部
            </Button>
            <Button onClick={handleGenBatch} disabled={generating}>
              {generating ? '生成中…' : '批量生成'}
            </Button>
          </div>
          {batchList && (
            <div className={styles.resultBox}>
              <div className={styles.resultList}>
                {batchList.map((c, i) => (
                  <div className={styles.ci} key={`${c}-${i}`}>
                    {c}
                  </div>
                ))}
                {batchExtra > 0 && (
                  <div className={`${styles.ci} ${styles.ciMuted}`}>
                    … 其余 {batchExtra} 个已生成，「复制全部」获取
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fStatus} onChange={(e) => setFStatus(e.target.value)}>
          <option value="">全部状态</option>
          <option value="unused">未使用</option>
          <option value="used">已使用</option>
          <option value="expired">已过期</option>
          <option value="disabled">已禁用</option>
        </select>
        <select className={styles.sel} value={fBatch} onChange={(e) => setFBatch(e.target.value)}>
          <option value="">全部批次</option>
          {batchOptions.map((b) => (
            <option key={b} value={b}>
              {b}
            </option>
          ))}
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索兑换码 / 使用者"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className={styles.grow} />
      </section>

      {/* 兑换码列表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>兑换码</th>
                <th className={styles.sortable}>
                  面额 <SortArrow />
                </th>
                <th>状态</th>
                <th>使用者</th>
                <th className={styles.sortable}>
                  创建时间 <SortArrow />
                </th>
                <th className={styles.sortable}>
                  过期时间 <SortArrow />
                </th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={7} className={styles.emptyCell}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={7} className={styles.emptyCell}>
                    加载失败：{error instanceof ApiError ? error.message : '请稍后重试'}
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className={styles.emptyCell}>
                    {total === 0 ? '暂无兑换码' : '无匹配兑换码'}
                  </td>
                </tr>
              ) : (
                filtered.map((r) => {
                  const rowCls =
                    r.st === 'used'
                      ? styles.rowUsed
                      : r.st === 'expired' || r.st === 'disabled'
                        ? styles.rowExpired
                        : '';
                  return (
                    <tr className={rowCls} key={r.id}>
                      <td className={styles.cellmono}>{r.code}</td>
                      <td className={styles.cellmono}>${r.amtUsd.toFixed(2)}</td>
                      <td>
                        <StatusBadge st={r.st} />
                      </td>
                      <td className={styles.cellmono}>
                        {r.user === '—' ? <span className={styles.muted}>—</span> : r.user}
                      </td>
                      <td className={`${styles.cellmono} ${styles.cellMuted}`}>{r.ct}</td>
                      <td className={`${styles.cellmono} ${styles.cellMuted}`}>{r.exp}</td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => handleRowCopy(r.code)}>
                            {copiedCode === r.code ? '已复制' : '复制'}
                          </a>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {total} 个兑换码</span>
          <div className={styles.pg}>
            <button disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
              ‹
            </button>
            <span className={styles.pgInfo}>
              {page} / {totalPages}
            </span>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              ›
            </button>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
