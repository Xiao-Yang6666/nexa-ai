'use client';

import { useState, useMemo } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
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

/* ── Mock 数据（迁移自 S6 原型 script，14+ 行，状态多样） ── */
type RedeemStatus = 'unused' | 'used' | 'expired';

interface RedeemRow {
  code: string;
  amt: number;
  st: RedeemStatus;
  user: string;
  ct: string;
  exp: string;
  batch: string;
}

const DATA: RedeemRow[] = [
  { code: 'NEXA-A1B2-****-9X7K', amt: 20, st: 'unused', user: '—', ct: '06-15 09:00', exp: '09-13 09:00', batch: 'B-20260615' },
  { code: 'NEXA-C3D4-****-8M2P', amt: 20, st: 'used', user: 'alice.chen', ct: '06-15 09:00', exp: '09-13 09:00', batch: 'B-20260615' },
  { code: 'NEXA-E5F6-****-7N4Q', amt: 10, st: 'unused', user: '—', ct: '06-10 10:30', exp: '12-07 10:30', batch: 'B-20260610' },
  { code: 'NEXA-G7H8-****-6R8S', amt: 10, st: 'used', user: 'bob.li', ct: '06-10 10:30', exp: '12-07 10:30', batch: 'B-20260610' },
  { code: 'NEXA-I9J0-****-5T1U', amt: 50, st: 'unused', user: '—', ct: '06-01 08:00', exp: '08-30 08:00', batch: 'B-20260601' },
  { code: 'NEXA-K1L2-****-4V3W', amt: 50, st: 'used', user: 'carol.wang', ct: '06-01 08:00', exp: '08-30 08:00', batch: 'B-20260601' },
  { code: 'NEXA-M3N4-****-3X5Y', amt: 5, st: 'expired', user: '—', ct: '03-12 14:20', exp: '06-10 14:20', batch: 'B-20260601' },
  { code: 'NEXA-O5P6-****-2Z7A', amt: 5, st: 'expired', user: '—', ct: '03-12 14:20', exp: '06-10 14:20', batch: 'B-20260601' },
  { code: 'NEXA-Q7R8-****-1B9C', amt: 100, st: 'unused', user: '—', ct: '06-18 16:45', exp: '12-15 16:45', batch: '单个生成' },
  { code: 'NEXA-S9T0-****-0D2E', amt: 100, st: 'used', user: 'dave.zhao', ct: '06-12 11:10', exp: '09-10 11:10', batch: '单个生成' },
  { code: 'NEXA-U1V2-****-9F4G', amt: 10, st: 'unused', user: '—', ct: '06-10 10:30', exp: '12-07 10:30', batch: 'B-20260610' },
  { code: 'NEXA-W3X4-****-8H6I', amt: 10, st: 'used', user: 'alice.chen', ct: '06-10 10:30', exp: '12-07 10:30', batch: 'B-20260610' },
  { code: 'NEXA-Y5Z6-****-7J8K', amt: 20, st: 'expired', user: '—', ct: '02-28 09:00', exp: '05-29 09:00', batch: 'B-20260601' },
  { code: 'NEXA-A7B8-****-6L0M', amt: 20, st: 'unused', user: '—', ct: '06-15 09:00', exp: '09-13 09:00', batch: 'B-20260615' },
  { code: 'NEXA-C9D0-****-5N2O', amt: 50, st: 'used', user: 'bob.li', ct: '06-01 08:00', exp: '08-30 08:00', batch: 'B-20260601' },
];

const ST_MAP: Record<RedeemStatus, { cls: string; tone: string; lab: string }> = {
  unused: { cls: 'b-suc', tone: 'var(--color-success)', lab: '未使用' },
  used: { cls: 'b-neutral', tone: 'var(--color-text-muted)', lab: '已使用' },
  expired: { cls: 'b-dan', tone: 'var(--color-danger)', lab: '已过期' },
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

/* ── 随机码生成（仅前端演示） ── */
function rand(n: number): string {
  const c = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  for (let i = 0; i < n; i++) s += c[Math.floor(Math.random() * c.length)];
  return s;
}
function newCode(): string {
  return `NEXA-${rand(4)}-${rand(4)}-${rand(4)}`;
}

/* ── 复制工具（判空 clipboard） ── */
function copyText(text: string): void {
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    void navigator.clipboard.writeText(text);
  }
}

export function RedeemPage() {
  /* 单个生成 */
  const [singleCode, setSingleCode] = useState<string | null>(null);

  /* 批量生成 */
  const [batchList, setBatchList] = useState<string[] | null>(null);
  const [batchExtra, setBatchExtra] = useState(0);
  const [batchCount, setBatchCount] = useState('50');
  const [batchNote, setBatchNote] = useState('单批最多 1000 个');

  /* 筛选 */
  const [fStatus, setFStatus] = useState('');
  const [fBatch, setFBatch] = useState('');
  const [search, setSearch] = useState('');

  /* 批量选择 */
  const [selected, setSelected] = useState<Set<string>>(new Set());

  /* 行复制态 */
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return DATA.filter((r) => {
      if (fStatus && r.st !== fStatus) return false;
      if (fBatch && r.batch !== fBatch) return false;
      if (
        kw &&
        !r.code.toLowerCase().includes(kw) &&
        !r.user.toLowerCase().includes(kw)
      ) {
        return false;
      }
      return true;
    });
  }, [fStatus, fBatch, search]);

  const allFilteredSelected =
    filtered.length > 0 && filtered.every((r) => selected.has(r.code));

  function toggleAll(checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        filtered.forEach((r) => next.add(r.code));
      } else {
        filtered.forEach((r) => next.delete(r.code));
      }
      return next;
    });
  }

  function toggleRow(code: string, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(code);
      else next.delete(code);
      return next;
    });
  }

  function handleGenSingle() {
    setSingleCode(newCode());
  }

  function handleGenBatch() {
    const n = Math.max(1, Math.min(1000, parseInt(batchCount, 10) || 1));
    const preview = Math.min(n, 30);
    const list: string[] = [];
    for (let i = 0; i < preview; i++) list.push(newCode());
    setBatchList(list);
    setBatchExtra(n > preview ? n - preview : 0);
  }

  function handleRowCopy(code: string) {
    copyText(code);
    setCopiedCode(code);
    setTimeout(() => {
      setCopiedCode((cur) => (cur === code ? null : cur));
    }, 1200);
  }

  const selCount = selected.size;

  return (
    <AdminShell
      activeId="redeem"
      title="兑换码管理"
      crumb={['管理后台', '运营', '兑换码']}
    >
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
              <input className={`input ${styles.cellmono}`} defaultValue="20" inputMode="decimal" />
            </div>
            <div>
              <label className="field-label">有效期（天）</label>
              <input className={`input ${styles.cellmono}`} defaultValue="90" inputMode="numeric" />
            </div>
          </div>
          <div className={styles.genFoot}>
            <span className={styles.grow} />
            <Button onClick={handleGenSingle}>生成</Button>
          </div>
          {singleCode && (
            <div className={styles.resultBox}>
              <div className={styles.code}>
                <span>{singleCode}</span>
                <a onClick={() => copyText(singleCode)}>复制</a>
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
                <input className={`input ${styles.cellmono}`} defaultValue="10" inputMode="decimal" />
              </div>
            </div>
            <div>
              <label className="field-label">有效期（天）</label>
              <input className={`input ${styles.cellmono}`} defaultValue="180" inputMode="numeric" />
            </div>
          </div>
          <div className={styles.genFoot}>
            <span className={styles.muted}>{batchNote}</span>
            <span className={styles.grow} />
            <Button variant="sec" size="sm" onClick={() => setBatchNote('已导出 CSV')}>
              导出
            </Button>
            <Button onClick={handleGenBatch}>批量生成</Button>
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
                    … 其余 {batchExtra} 个已生成，可导出查看
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select
          className={styles.sel}
          value={fStatus}
          onChange={(e) => setFStatus(e.target.value)}
        >
          <option value="">全部状态</option>
          <option value="unused">未使用</option>
          <option value="used">已使用</option>
          <option value="expired">已过期</option>
        </select>
        <select
          className={styles.sel}
          value={fBatch}
          onChange={(e) => setFBatch(e.target.value)}
        >
          <option value="">全部批次</option>
          <option>B-20260601</option>
          <option>B-20260610</option>
          <option>B-20260615</option>
          <option>单个生成</option>
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

      {/* BatchBar */}
      {selCount > 0 && (
        <section className={styles.batchbar}>
          <span className={styles.cnt}>已选 {selCount} 项</span>
          <span className={styles.grow} />
          <Button variant="danger" size="sm">
            批量作废
          </Button>
        </section>
      )}

      {/* 兑换码列表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th style={{ width: 34 }}>
                  <input
                    type="checkbox"
                    aria-label="全选"
                    checked={allFilteredSelected}
                    onChange={(e) => toggleAll(e.target.checked)}
                  />
                </th>
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
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className={styles.emptyCell}>
                    无匹配兑换码
                  </td>
                </tr>
              ) : (
                filtered.map((r) => {
                  const rowCls =
                    r.st === 'used'
                      ? styles.rowUsed
                      : r.st === 'expired'
                        ? styles.rowExpired
                        : '';
                  return (
                    <tr className={rowCls} key={r.code}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`选择 ${r.code}`}
                          checked={selected.has(r.code)}
                          onChange={(e) => toggleRow(r.code, e.target.checked)}
                        />
                      </td>
                      <td className={styles.cellmono}>{r.code}</td>
                      <td className={styles.cellmono}>${r.amt}</td>
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
                          {r.st === 'unused' && <a className="dang">作废</a>}
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
          <span>共 {filtered.length} 个兑换码</span>
        </div>
      </section>
    </AdminShell>
  );
}
