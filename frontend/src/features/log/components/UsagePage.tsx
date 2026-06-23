'use client';

import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { useSelfLogs, useSelfLogStat, type LogRowVM } from '../model/log.model';
import type { LogSelfQuery } from '../api/log.api';
import styles from './UsagePage.module.css';

/** 类型语义色 → token CSS 变量名（徽章圆点）。 */
const TONE_VAR: Record<string, string> = {
  consume: '--color-info',
  topup: '--color-success',
  error: '--color-danger',
  refund: '--color-warning',
  system: '--color-text-muted',
  login: '--color-text-muted',
  manage: '--color-text-muted',
  unknown: '--color-text-muted',
};
const TONE_BADGE: Record<string, string> = {
  consume: 'b-info',
  topup: 'b-suc',
  error: 'b-dan',
  refund: 'b-warn',
  system: 'b-neutral',
  login: 'b-neutral',
  manage: 'b-neutral',
  unknown: 'b-neutral',
};

/** 简单 chevron-down 排序图标占位（仅装饰）。 */
function SortIcon() {
  return (
    <svg className={styles.arr} viewBox="0 0 16 16" width={12} height={12} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />
    </svg>
  );
}

/** 单行明细 + 可展开详情。 */
function LogRow({ row }: { row: LogRowVM }) {
  const [open, setOpen] = useState(false);
  const sameName = row.requested === row.resolved;
  const hasModel = row.resolved !== '—' || row.requested !== '—';
  return (
    <>
      <tr>
        <td className={`${styles.cellmono} muted`}>{row.time}</td>
        <td>
          <span className={`badge ${TONE_BADGE[row.tone]} ${styles.typeBadge}`}>
            <span className="dot" style={{ background: `var(${TONE_VAR[row.tone]})` }} />
            {row.typeLabel}
          </span>
        </td>
        <td>
          {hasModel ? (
            <div className={styles.modelcell}>
              <span className={styles.req}>{row.requested}</span>
              {sameName ? null : <span className={styles.land}>实际 {row.resolved}</span>}
            </div>
          ) : (
            <span className={`muted ${styles.cellmono}`}>—</span>
          )}
        </td>
        <td className={`${styles.cellmono} muted`}>{row.group}</td>
        <td>
          {row.promptTokens || row.completionTokens ? (
            <span className={styles.cellmono}>
              {row.promptTokens.toLocaleString()}{' '}
              <span className="muted">/ {row.completionTokens.toLocaleString()}</span>
            </span>
          ) : (
            <span className={`muted ${styles.cellmono}`}>—</span>
          )}
        </td>
        <td>
          {row.hasFee ? (
            <span className={styles.money}>{row.feeUsd}</span>
          ) : (
            <span className={`muted ${styles.cellmono}`}>—</span>
          )}
        </td>
        <td className={styles.ipcell}>{row.ip}</td>
        <td>
          <div className={styles.uacell} title={row.userAgent}>
            {row.userAgent}
          </div>
        </td>
        <td className={`${styles.cellmono} muted`}>{row.useTime} ms</td>
        <td>
          <span className={`badge ${row.ok ? 'b-suc' : 'b-dan'}`}>
            <span className="dot" style={{ background: `var(${row.ok ? '--color-success' : '--color-danger'})` }} />
            {row.ok ? '成功' : '失败'}
          </span>
        </td>
        <td>
          <div className={styles.rowActs}>
            <a className={styles.detailLink} onClick={() => setOpen((v) => !v)}>
              {open ? '收起' : '展开'}
            </a>
          </div>
        </td>
      </tr>
      {open ? (
        <tr className={styles.detailRow}>
          <td colSpan={11}>
            {hasModel ? (
              <div className={styles.detSection}>
                <h4>模型</h4>
                <div className={styles.detModel}>
                  <span className={styles.node}>
                    <span className={styles.lbl}>您请求</span>
                    {row.requested}
                  </span>
                  <span className={styles.arrX}>→</span>
                  <span className={styles.node}>
                    <span className={styles.lbl}>实际调用</span>
                    {row.resolved}
                  </span>
                </div>
              </div>
            ) : null}
            <div className={styles.detSection}>
              <h4>计费明细</h4>
              {row.hasFee ? (
                <div className={styles.detMoney}>
                  <span className={styles.k}>本笔实付</span>
                  <span className={styles.money}>{row.feeUsd}</span>
                </div>
              ) : (
                <div className="muted">本笔无计费（请求失败未产生费用）。</div>
              )}
            </div>
            <div className={styles.detSection}>
              <h4>请求元信息</h4>
              <dl className={styles.detailGrid}>
                <div>
                  <dt>request_id</dt>
                  <dd>{row.requestId}</dd>
                </div>
                <div>
                  <dt>来源 IP</dt>
                  <dd>{row.ip}</dd>
                </div>
                <div>
                  <dt>User-Agent</dt>
                  <dd>{row.userAgent}</dd>
                </div>
                <div>
                  <dt>分组（折扣等级）</dt>
                  <dd>{row.group}</dd>
                </div>
                <div>
                  <dt>tokens 入 / 出</dt>
                  <dd>
                    {row.promptTokens.toLocaleString()} / {row.completionTokens.toLocaleString()}
                  </dd>
                </div>
                <div>
                  <dt>耗时</dt>
                  <dd>{row.useTime} ms</dd>
                </div>
              </dl>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  );
}

/**
 * UsagePage — 用量统计 / 调用明细（S6 console/usage.html 工程化）。
 *
 * 接 GET /api/log/self（F-4002）拉本人调用明细，GET /api/log/self/stat（F-4005）拉聚合统计。
 * 客户端零泄露：仅展示本人请求模型 / 实际公开模型(A) / 本人实付 / Token / 耗时 / 状态，
 *   绝不渲染成本(quota_cost)/利润(quota_profit)/上游真实模型 B/供应商（契约已结构级剔除，视图层不取）。
 * 含筛选（模型 / 分组 / 状态 / 关键词）+ loading/empty/error 各态。
 */
export function UsagePage() {
  const [fModel, setFModel] = useState('');
  const [fGroup, setFGroup] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [search, setSearch] = useState('');

  // 服务端按 model/group 过滤；状态/关键词在客户端再筛（契约无对应 query）
  const query = useMemo<LogSelfQuery>(
    () => ({
      type: 2, // 仅消费类
      model_name: fModel || undefined,
      group: fGroup || undefined,
      page: 1,
      page_size: 50,
    }),
    [fModel, fGroup],
  );

  const { data, isLoading, isError, refetch } = useSelfLogs(query);
  const stat = useSelfLogStat();

  const rows = useMemo(() => {
    const all = data?.rows ?? [];
    const kw = search.trim().toLowerCase();
    return all.filter((r) => {
      if (fStatus === 'ok' && !r.ok) return false;
      if (fStatus === 'fail' && r.ok) return false;
      if (
        kw &&
        !r.requestId.toLowerCase().includes(kw) &&
        !r.ip.toLowerCase().includes(kw) &&
        !r.requested.toLowerCase().includes(kw) &&
        !r.resolved.toLowerCase().includes(kw)
      )
        return false;
      return true;
    });
  }, [data, fStatus, search]);

  const reset = () => {
    setFModel('');
    setFGroup('');
    setFStatus('');
    setSearch('');
  };

  const actions = (
    <button className="btn btn-sec btn-sm" type="button" onClick={() => refetch()}>
      刷新
    </button>
  );

  return (
    <AppShell activeId="usage" title="调用明细" crumb={['控制台', '调用明细']} actions={actions}>
      {/* 统计小卡（仅本人维度） */}
      <section className={styles.statRow}>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>本期消费</div>
          <div className={`${styles.statVal} ${styles.ok}`}>{stat.data?.quotaUsd ?? '$0.0000'}</div>
        </div>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>RPM（每分钟请求）</div>
          <div className={styles.statVal}>{stat.data?.rpm ?? 0}</div>
        </div>
        <div className={`${styles.stat} nx-fade`}>
          <div className={styles.statLabel}>TPM（每分钟 Token）</div>
          <div className={styles.statVal}>{stat.data?.tpm ?? 0}</div>
        </div>
      </section>

      {/* 本人范围说明条 */}
      <section className={`${styles.scopeNote} nx-fade`}>
        <svg viewBox="0 0 16 16" width={15} height={15} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
          <circle cx="8" cy="8" r="6.5" />
          <path d="M8 5.5v3" />
          <path d="M8 11h.01" />
        </svg>
        <span className={styles.t}>
          此页仅展示 <b>您本人</b> 的每一次调用明细，含您请求的模型、实际调用的公开模型、您的实付费用、Token、耗时与状态。
        </span>
      </section>

      {/* FilterBar：无「用户」筛选 */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fModel} onChange={(e) => setFModel(e.target.value)} aria-label="模型筛选">
          <option value="">全部模型</option>
          <option value="gpt-4o">gpt-4o</option>
          <option value="gpt-4o-mini">gpt-4o-mini</option>
          <option value="claude-3-5-sonnet">claude-3-5-sonnet</option>
          <option value="gemini-2.5-pro">gemini-2.5-pro</option>
          <option value="deepseek-v3">deepseek-v3</option>
        </select>
        <select className={styles.sel} value={fGroup} onChange={(e) => setFGroup(e.target.value)} aria-label="分组筛选">
          <option value="">全部分组</option>
          <option value="free">free</option>
          <option value="vip">vip</option>
          <option value="svip">svip</option>
        </select>
        <select className={styles.sel} value={fStatus} onChange={(e) => setFStatus(e.target.value)} aria-label="状态筛选">
          <option value="">全部状态</option>
          <option value="ok">成功</option>
          <option value="fail">失败</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索 request_id / IP / 模型"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className={styles.grow} />
        <button className="btn btn-ghost btn-sm" type="button" onClick={reset}>
          重置
        </button>
      </section>

      {/* 明细表 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th className={styles.sortable}>
                  时间 <SortIcon />
                </th>
                <th>类型</th>
                <th>模型</th>
                <th>分组</th>
                <th className={styles.sortable}>
                  Tokens (入/出) <SortIcon />
                </th>
                <th className={styles.sortable}>
                  费用 <SortIcon />
                </th>
                <th>IP</th>
                <th>User-Agent</th>
                <th className={styles.sortable}>
                  耗时 <SortIcon />
                </th>
                <th>状态</th>
                <th>详情</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={11} className={styles.stateCell}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={11} className={styles.stateCell}>
                    调用明细加载失败，
                    <a className={styles.detailLink} onClick={() => refetch()}>
                      重试
                    </a>
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={11} className={styles.stateCell}>
                    无匹配记录
                  </td>
                </tr>
              ) : (
                rows.map((r) => <LogRow key={r.id} row={r} />)
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {rows.length} 条</span>
        </div>
      </section>
    </AppShell>
  );
}
