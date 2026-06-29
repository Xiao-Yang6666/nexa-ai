'use client';

import { useState } from 'react';
import { AppShell } from '@/features/shell';
import {
  useTokens,
  useCreateToken,
  useDeleteToken,
  useToggleToken,
  useTokenKey,
  useUserGroups,
  type TokenRowVM,
  type GroupOptionVM,
} from '../model/token.model';
import type { TokenCreateRequest } from '@/shared/api';
import styles from './KeysPage.module.css';

const BASE_URL = 'https://api.nexa.ai/v1';

/** 行内分组徽标 → 折扣文案（仅折扣层，不限制可用模型）。 */
const GROUP_DISC: Record<string, string> = {
  free: '×1.00',
  vip: '×0.85',
  svip: '×0.75',
  default: '×1.00',
  auto: '×1.00',
};

/** 生成 cURL 示例文本（OpenAI / Anthropic 兼容）。 */
function curlText(proto: 'openai' | 'anthropic', key: string): string {
  if (proto === 'anthropic') {
    return [
      `curl ${BASE_URL}/messages \\`,
      `  -H "x-api-key: ${key}" \\`,
      `  -H "anthropic-version: 2023-06-01" \\`,
      `  -H "content-type: application/json" \\`,
      `  -d '{"model":"opus-4.8","max_tokens":1024,"messages":[{"role":"user","content":"你好"}]}'`,
    ].join('\n');
  }
  return [
    `curl ${BASE_URL}/chat/completions \\`,
    `  -H "Authorization: Bearer ${key}" \\`,
    `  -H "Content-Type: application/json" \\`,
    `  -d '{"model":"opus-4.8","messages":[{"role":"user","content":"你好"}]}'`,
  ].join('\n');
}

/** 单行 + 接入信息展开。 */
function KeyRow({
  row,
  onToggle,
  onDelete,
}: {
  row: TokenRowVM;
  onToggle: (id: number, enable: boolean) => void;
  onDelete: (id: number) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [proto, setProto] = useState<'openai' | 'anthropic'>('openai');
  const [copied, setCopied] = useState(false);
  const [revealed, setRevealed] = useState<string | null>(null);
  const reveal = useTokenKey();

  const displayKey = revealed ?? row.keyMasked;

  const doReveal = async () => {
    if (revealed) {
      setRevealed(null);
      return;
    }
    try {
      const plain = await reveal.mutateAsync(row.id);
      setRevealed(plain);
    } catch {
      /* 错误由全局提示处理 */
    }
  };

  const copyKey = async () => {
    try {
      // 复制必须是明文：未揭示时先拉明文再写剪贴板（旧实现会复制脱敏值，等于复制了错的 key）。
      let k = revealed;
      if (!k) {
        k = await reveal.mutateAsync(row.id);
        setRevealed(k);
      }
      await navigator.clipboard.writeText(k);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* 错误由全局提示处理 */
    }
  };

  const totalLabel = row.unlimited
    ? '不限'
    : `$${(row.totalUsd ?? 0).toFixed(2)}`;
  const usedLabel = `$${row.usedUsd.toFixed(2)}/${totalLabel}`;

  const copyCurl = async () => {
    try {
      // 同样确保 cURL 里嵌的是明文 key（否则示例不可用）。
      let k = revealed;
      if (!k) {
        k = await reveal.mutateAsync(row.id);
        setRevealed(k);
      }
      await navigator.clipboard.writeText(curlText(proto, k));
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* 错误由全局提示处理 */
    }
  };

  return (
    <>
      <tr className={styles.rowExpandable} onClick={() => setExpanded((v) => !v)}>
        <td style={{ color: 'var(--color-text)', fontWeight: 'var(--fw-medium)' }}>{row.name}</td>
        <td className={styles.keyprefix}>
          <code className={styles.keycell}>{displayKey}</code>
        </td>
        <td>
          <span className={`badge ${row.badgeClass}`}>
            <span className="dot" style={{ background: `var(${row.dotVar})` }} />
            {row.stateLabel}
          </span>
        </td>
        <td>
          <span className={`${styles.grpPill} ${styles[row.group] ?? styles.free}`}>
            {row.group.toUpperCase()}
            <span className={styles.disc}>{GROUP_DISC[row.group] ?? '×1.00'}</span>
          </span>
        </td>
        <td>
          <div className={styles.prog}>
            <div className={styles.progTrack}>
              <div
                className={`${styles.progFill} ${row.progTone ? styles[row.progTone] : ''}`}
                style={{ width: `${row.unlimited ? 0 : row.pct}%` }}
              />
            </div>
            <span className={styles.progTxt}>{usedLabel}</span>
          </div>
        </td>
        <td className="mono-num">{row.createdAt}</td>
        <td className="muted">{row.accessedAt}</td>
        <td onClick={(e) => e.stopPropagation()}>
          <div className={styles.rowacts}>
            <button
              className={styles.iconact}
              type="button"
              title={revealed ? '隐藏明文' : '显示明文'}
              onClick={doReveal}
              disabled={reveal.isPending}
            >
              {revealed ? (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                  <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </button>
            <button
              className={styles.iconact}
              type="button"
              title="复制密钥"
              onClick={copyKey}
            >
              {copied ? (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="var(--color-success)" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              )}
            </button>
            <button
              className={styles.iconact}
              type="button"
              title={row.enabled ? '禁用' : '启用'}
              onClick={() => onToggle(row.id, !row.enabled)}
            >
              {row.enabled ? (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="8" />
                  <path d="M6 6l12 12" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="var(--color-success)" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="8" />
                  <path d="M8 12l3 3 5-6" />
                </svg>
              )}
            </button>
            <button className={`${styles.iconact} ${styles.dan}`} type="button" title="删除" onClick={() => onDelete(row.id)}>
              <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 7h14" />
                <path d="M9 7V5h6v2" />
                <path d="M7 7l1 13h8l1-13" />
              </svg>
            </button>
          </div>
        </td>
      </tr>
      {expanded ? (
        <tr className={styles.expandRow}>
          <td colSpan={8}>
            <div className={styles.access}>
              <div className={styles.accessLine}>
                <span className={styles.lbl}>base_url</span>
                <span className={styles.codepill}>{BASE_URL}</span>
                <span className={styles.lbl}>密钥</span>
                <span className={styles.codepill} style={{ fontFamily: 'var(--ff-mono)' }}>{displayKey}</span>
                {revealed ? (
                  <button className="btn btn-sec btn-sm" type="button" onClick={() => setRevealed(null)}>
                    隐藏明文
                  </button>
                ) : null}
              </div>
              <div className={styles.accessLine}>
                <span className={styles.lbl}>支持端点</span>
              </div>
              <div className={styles.epts}>
                <div className={styles.ept}>
                  <span className="badge b-suc">
                    <span className="dot" style={{ background: 'var(--color-success)' }} />
                    OpenAI 兼容
                  </span>
                  <span className={styles.epPath}>POST {BASE_URL}/chat/completions</span>
                </div>
                <div className={styles.ept}>
                  <span className="badge b-suc">
                    <span className="dot" style={{ background: 'var(--color-success)' }} />
                    Anthropic 兼容
                  </span>
                  <span className={styles.epPath}>POST {BASE_URL}/messages</span>
                </div>
              </div>
              <div className={styles.universal}>
                <svg viewBox="0 0 24 24" width={14} height={14} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12l5 5L20 7" />
                </svg>
                一个 key 两种协议通用，按你客户端 SDK 的协议选端点即可。
              </div>
              <div className={styles.curlTabs}>
                <button
                  type="button"
                  className={proto === 'openai' ? styles.on : ''}
                  onClick={() => setProto('openai')}
                >
                  OpenAI
                </button>
                <button
                  type="button"
                  className={proto === 'anthropic' ? styles.on : ''}
                  onClick={() => setProto('anthropic')}
                >
                  Anthropic
                </button>
              </div>
              <div className={styles.curlBlock}>
                <button className={styles.copybtn} type="button" onClick={copyCurl}>
                  {copied ? '已复制' : '复制'}
                </button>
                <pre>{curlText(proto, displayKey)}</pre>
              </div>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  );
}

/** 创建抽屉表单。 */
function CreateDrawer({
  open,
  onClose,
  onSubmit,
  isSubmitting,
  groups,
  groupsLoading,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (req: TokenCreateRequest) => void;
  isSubmitting: boolean;
  groups: GroupOptionVM[];
  groupsLoading: boolean;
}) {
  const [name, setName] = useState('');
  const [limit, setLimit] = useState('500');
  const [unlimited, setUnlimited] = useState(false);
  const [expired, setExpired] = useState('-1');
  const [group, setGroup] = useState('');

  // 分组列表到达后，缺省选中首个套餐（避免用户漏选导致 key 不绑分组）。
  if (!group && groups.length > 0) {
    setGroup(groups[0].code);
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !group) return;
    const req: TokenCreateRequest = {
      name: name.trim(),
      group,
      unlimited_quota: unlimited,
      remain_quota: unlimited ? undefined : Math.max(0, Math.round(Number(limit) || 0)) * 500_000,
      expired_time: Number(expired),
    };
    onSubmit(req);
  };

  return (
    <>
      <div className={`${styles.drawerScrim} ${open ? styles.open : ''}`} onClick={onClose} />
      <aside className={`${styles.drawer} ${open ? styles.open : ''}`}>
        <form onSubmit={submit} className={styles.drawerForm}>
          <div className={styles.drawerHead}>
            <h3>创建 API 密钥</h3>
            <button className={styles.iconact} type="button" aria-label="关闭" onClick={onClose}>
              <svg viewBox="0 0 24 24" width={18} height={18} fill="none" stroke="currentColor" strokeWidth={1.8}>
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
          <div className={styles.drawerBody}>
            <div className={styles.fld}>
              <label className="field-label">
                密钥名称 <span className="field-req">*</span>
              </label>
              <input
                className="input"
                placeholder="如：生产环境-后端服务"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
              <div className="field-hint">用于在列表中识别此密钥的用途。</div>
            </div>
            <div className={styles.fld}>
              <label className="field-label">
                套餐分组 <span className="field-req">*</span>
              </label>
              <select
                className="input"
                value={group}
                onChange={(e) => setGroup(e.target.value)}
                disabled={groupsLoading || groups.length === 0}
                required
              >
                {groupsLoading ? (
                  <option value="">加载分组中…</option>
                ) : groups.length === 0 ? (
                  <option value="">暂无可用分组</option>
                ) : (
                  groups.map((g) => (
                    <option key={g.code} value={g.code}>
                      {g.name}（{g.code} · ×{g.ratio.toFixed(2)}）
                    </option>
                  ))
                )}
              </select>
              <div className="field-hint">
                分组决定此密钥<b>可调用的模型</b>与<b>计费倍率</b>。需要别的价位/模型？换个分组再建一把 key。
              </div>
            </div>
            <div className={styles.fld}>
              <label className="field-label">额度上限（USD）</label>
              <div className={styles.chiprow}>
                <label className={styles.chk}>
                  <input
                    type="checkbox"
                    checked={unlimited}
                    onChange={(e) => setUnlimited(e.target.checked)}
                  />
                  不限额度
                </label>
              </div>
              <input
                className="input mono-num"
                type="number"
                min="0"
                step="1"
                value={limit}
                onChange={(e) => setLimit(e.target.value)}
                disabled={unlimited}
                style={{ marginTop: 'var(--space-2)' }}
              />
              <div className="field-hint">超出后该密钥将被自动暂停。不限额度=信任密钥承担总额度。</div>
            </div>
            <div className={styles.fld}>
              <label className="field-label">过期时间</label>
              <select
                className="input"
                value={expired}
                onChange={(e) => setExpired(e.target.value)}
              >
                <option value="-1">永不过期</option>
                <option value={String(Math.floor(Date.now() / 1000) + 30 * 86400)}>30 天后</option>
                <option value={String(Math.floor(Date.now() / 1000) + 90 * 86400)}>90 天后</option>
              </select>
            </div>
            <div className={styles.noticeBox}>
              <b>安全提示：</b>创建完成后点击密钥行的「眼睛」图标可查看明文并立即复制，明文仅在主动点击时展示。
            </div>
          </div>
          <div className={styles.drawerFoot}>
            <button className="btn btn-sec" type="button" onClick={onClose}>
              取消
            </button>
            <button className="btn btn-primary" type="submit" disabled={isSubmitting || !name.trim() || !group}>
              {isSubmitting ? '生成中…' : '生成密钥'}
            </button>
          </div>
        </form>
      </aside>
    </>
  );
}

/**
 * KeysPage — API 密钥（S6 console/keys.html 工程化）。
 *
 * 接 GET /api/token/（F-3002）拉令牌列表（key 已脱敏），创建走 POST /api/token/（F-3001），
 * 删除/启用/禁用走 DELETE /api/token/{id} / PUT /api/token/，明文走 POST /api/token/{id}/key。
 * 客户端零泄露：TokenUserView 无成本/利润/上游字段；本页仅展示用量额度（USD 换算）。
 * 含 loading/empty/error 各态 + 接入信息展开 + 协议 cURL 切换 + 二次确认弹窗 + 明文受控显示。
 */
export function KeysPage() {
  const { data, isLoading, isError, refetch } = useTokens(1, 50);
  const create = useCreateToken();
  const toggle = useToggleToken();
  const del = useDeleteToken();
  const { data: groups, isLoading: groupsLoading } = useUserGroups();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [confirm, setConfirm] = useState<{ kind: 'delete' | 'toggle'; id: number; name: string; enable?: boolean } | null>(null);

  const onCreate = (req: TokenCreateRequest) => {
    create.mutate(req, {
      onSuccess: () => {
        setDrawerOpen(false);
        refetch();
      },
    });
  };

  const onConfirm = () => {
    if (!confirm) return;
    if (confirm.kind === 'delete') del.mutate(confirm.id);
    if (confirm.kind === 'toggle') toggle.mutate({ id: confirm.id, enable: !!confirm.enable });
    setConfirm(null);
  };

  const actions = (
    <button className="btn btn-primary" type="button" onClick={() => setDrawerOpen(true)}>
      创建密钥
    </button>
  );

  return (
    <AppShell activeId="keys" title="API 密钥" crumb={['控制台', 'API 密钥']} actions={actions}>
      {/* 闭环导航：API 密钥 ↔ 分组 */}
      <div className={`${styles.loopnav} nx-fade`}>
        <a className={styles.cur} aria-current="page">
          API 密钥
        </a>
        <span className={styles.sep}>·</span>
        <a href="/recharge">分组与折扣</a>
      </div>

      {/* 套餐分组说明卡（数据驱动：套餐制下每把 key 绑定一个分组，决定可用模型与计费倍率） */}
      <div className={`${styles.grpcard} nx-fade`}>
        <span className={styles.gcBadge}>套餐</span>
        <div className={styles.gcBlock}>
          <span className={styles.gcK}>可选套餐分组</span>
          <span className={styles.gcV}>
            {groupsLoading ? '加载中…' : `${groups?.length ?? 0} 个`}
          </span>
        </div>
        <div className={styles.gcBlock}>
          <span className={styles.gcK}>倍率区间</span>
          <span className={`${styles.gcV} ${styles.gcDisc}`}>
            {groups && groups.length > 0
              ? `×${Math.min(...groups.map((g) => g.ratio)).toFixed(2)} ~ ×${Math.max(...groups.map((g) => g.ratio)).toFixed(2)}`
              : '—'}
          </span>
        </div>
        <div className={styles.gcSpacer} />
        <div className={styles.gcUp}>
          每把密钥绑定<b>一个套餐分组</b>，分组决定<b>可调用的模型</b>与<b>计费倍率</b>。创建密钥时选择对应套餐。
        </div>
        <a className="btn btn-sec btn-sm" href="/recharge">
          查看套餐分组
        </a>
      </div>

      <div className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>密钥名</th>
                <th>Key</th>
                <th>状态</th>
                <th>分组</th>
                <th>已用额度</th>
                <th>创建时间</th>
                <th>最后使用</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={8} className={styles.stateCell}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={8} className={styles.stateCell}>
                    密钥列表加载失败，
                    <a className={styles.errLink} onClick={() => refetch()}>
                      重试
                    </a>
                  </td>
                </tr>
              ) : !data?.rows?.length ? (
                <tr>
                  <td colSpan={8} className={styles.stateCell}>
                    暂无密钥。点击「创建密钥」生成你的第一把 key。
                  </td>
                </tr>
              ) : (
                data.rows.map((r) => (
                  <KeyRow
                    key={r.id}
                    row={r}
                    onToggle={(id, enable) =>
                      setConfirm({ kind: 'toggle', id, name: r.name, enable })
                    }
                    onDelete={(id) =>
                      setConfirm({ kind: 'delete', id, name: r.name })
                    }
                  />
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>
            共 <b style={{ color: 'var(--color-text)' }}>{data?.total ?? 0}</b> 个密钥
          </span>
        </div>
      </div>

      <CreateDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSubmit={onCreate}
        isSubmitting={create.isPending}
        groups={groups ?? []}
        groupsLoading={groupsLoading}
      />

      {confirm ? (
        <div className={`${styles.modalScrim} ${styles.open}`} onClick={() => setConfirm(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3>
              {confirm.kind === 'delete'
                ? '删除密钥？'
                : confirm.enable
                ? '启用密钥？'
                : '禁用密钥？'}
            </h3>
            <p>
              {confirm.kind === 'delete'
                ? `删除「${confirm.name}」后无法恢复，使用此密钥的所有请求将立即失败。`
                : confirm.enable
                ? `启用「${confirm.name}」后该密钥可立即用于调用 API。`
                : `禁用「${confirm.name}」后使用此密钥的请求将立即返回 401，可随时重新启用。`}
            </p>
            <div className={styles.modalActs}>
              <button className="btn btn-sec" type="button" onClick={() => setConfirm(null)}>
                取消
              </button>
              <button
                className={confirm.kind === 'delete' || !confirm.enable ? 'btn btn-danger' : 'btn btn-primary'}
                type="button"
                onClick={onConfirm}
              >
                确认
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </AppShell>
  );
}
