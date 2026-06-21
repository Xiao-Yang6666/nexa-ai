'use client';

import { useState, useRef, useMemo, type KeyboardEvent } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useChannels,
  useDeleteChannel,
  useBatchOperateChannels,
  useCreateChannel,
  useUpdateChannel,
  TYPE_OPTIONS,
  type ChannelStatus,
  type ChannelRowVM,
} from '../model/channel.model';
import styles from './ChannelsAdminPage.module.css';

/* ── 排序箭头 SVG ── */
function SortArr() {
  return (
    <svg viewBox="0 0 16 16" width={11} height={11} fill="none" stroke="currentColor"
      strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />
    </svg>
  );
}

const ST_MAP: Record<ChannelStatus, { cls: string; tone: string; lab: string }> = {
  on:   { cls: 'b-suc',     tone: '--color-success',    lab: '启用'     },
  man:  { cls: 'b-neutral', tone: '--color-text-muted', lab: '手动禁用' },
  auto: { cls: 'b-dan',     tone: '--color-danger',     lab: '自动禁用' },
};

function StatusBadge({ st }: { st: ChannelStatus }) {
  const s = ST_MAP[st];
  return (
    <span className={`badge ${s.cls}`}>
      <span className="dot" style={{ background: `var(${s.tone})` }} />
      {s.lab}
    </span>
  );
}

function LatCell({ lat }: { lat: number }) {
  if (!lat) return <span className={`muted ${styles.cellmono}`}>—</span>;
  return <span className={styles.cellmono}>{lat} ms</span>;
}

/* ── 编辑抽屉表单状态 ── */
interface DrawerForm {
  name: string;
  type: string;
  baseUrl: string;
  modelMap: string;
  priority: string;
  weight: string;
  enabled: boolean;
}

const INIT_FORM: DrawerForm = {
  name: '', type: 'OpenAI', baseUrl: '',
  modelMap: '', priority: '10', weight: '5', enabled: true,
};

const TYPE_LABELS = TYPE_OPTIONS.map((t) => t.label);

const PAGE_SIZE = 20;

/**
 * ChannelsAdminPage — 渠道管理（S6 admin/channels.html 工程化，已接真实接口 F-2016）。
 * 筛选栏 + 批量操作栏 + 密集表格 + 分页 + 编辑抽屉。
 * 列表来自 GET /api/channel/；删除/批量接 DELETE /api/channel/{id}、POST /api/channel/batch。
 */
export function ChannelsAdminPage() {
  // 筛选
  const [fType, setFType] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [fSearch, setFSearch] = useState('');

  // 分页
  const [page, setPage] = useState(1);

  // 批量选择
  const [selected, setSelected] = useState<Set<number>>(new Set());

  // 抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'new' | 'edit'>('new');
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<DrawerForm>(INIT_FORM);
  const [saveErr, setSaveErr] = useState<string | null>(null);

  // API Key 标签
  const [keys, setKeys] = useState<string[]>([]);
  const keyInputRef = useRef<HTMLInputElement>(null);

  /* ── 数据（真实接口） ── */
  // status 筛选映射回后端码：启用=1/手动禁用=2/自动禁用=3
  const statusCode =
    fStatus === '启用' ? 1 : fStatus === '手动禁用' ? 2 : fStatus === '自动禁用' ? 3 : undefined;
  const { data, isLoading, isError, error } = useChannels({
    page,
    pageSize: PAGE_SIZE,
    status: statusCode,
  });
  const rows: ChannelRowVM[] = useMemo(() => data?.rows ?? [], [data]);
  const total = data?.total ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const deleteMutation = useDeleteChannel();
  const batchMutation = useBatchOperateChannels();
  const createMutation = useCreateChannel();
  const updateMutation = useUpdateChannel();

  /* ── 客户端过滤（type/search 在当前页过滤；status 已下推后端） ── */
  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (fType && r.type !== fType) return false;
      if (fSearch) {
        const q = fSearch.toLowerCase();
        if (!r.name.toLowerCase().includes(q) && !String(r.id).includes(q)) return false;
      }
      return true;
    });
  }, [rows, fType, fSearch]);

  /* ── 批量选择 ── */
  const allChecked = filtered.length > 0 && filtered.every((r) => selected.has(r.id));
  function toggleAll(checked: boolean) {
    setSelected(checked ? new Set(filtered.map((r) => r.id)) : new Set());
  }
  function toggleOne(id: number, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  async function handleBatch(action: 'enable' | 'disable' | 'delete') {
    if (selected.size === 0) return;
    await batchMutation.mutateAsync({ ids: Array.from(selected), action });
    setSelected(new Set());
  }

  async function handleDelete(id: number) {
    await deleteMutation.mutateAsync(id);
    setSelected((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }

  /* ── 抽屉 ── */
  function openDrawer(mode: 'new' | 'edit', row?: ChannelRowVM) {
    setDrawerMode(mode);
    setSaveErr(null);
    if (mode === 'edit' && row) {
      setEditId(row.id);
      setForm({
        name: row.name,
        type: row.type,
        baseUrl: row.baseUrl,
        modelMap: row.modelMapping,
        priority: String(row.pr),
        weight: String(row.wt),
        enabled: row.st === 'on',
      });
    } else {
      setEditId(null);
      setForm(INIT_FORM);
    }
    setKeys([]);
    setDrawerOpen(true);
  }
  function closeDrawer() { setDrawerOpen(false); }

  /* ── 保存渠道（新建 POST / 编辑 PUT，覆盖式；key 留空=保留原 key） ── */
  async function handleSave() {
    setSaveErr(null);
    const name = form.name.trim();
    if (!name) { setSaveErr('请填写渠道名'); return; }
    const typeCode = TYPE_OPTIONS.find((t) => t.label === form.type)?.code ?? 1;
    const keyStr = keys.join('\n');
    const priority = Number(form.priority) || 0;
    const weight = Number(form.weight) || 0;
    // status 由 enabled 决定：编辑时启用=1，禁用=2（手动）；后端覆盖式更新接受 type/models 等
    const base = {
      type: typeCode,
      name,
      group: 'default',
      base_url: form.baseUrl.trim() || undefined,
      models: form.modelMap.trim() || '',
      model_mapping: form.modelMap.trim() || undefined,
      priority,
      weight,
      auto_ban: 1,
    };
    try {
      if (drawerMode === 'new') {
        if (!keyStr) { setSaveErr('新建渠道必须填写至少一个 API Key'); return; }
        await createMutation.mutateAsync({ ...base, key: keyStr });
      } else if (editId != null) {
        // 编辑：key 留空表示保留原 key（后端聚合处理）
        await updateMutation.mutateAsync({ id: editId, ...base, key: keyStr || undefined } as Parameters<typeof updateMutation.mutateAsync>[0]);
      }
      setDrawerOpen(false);
    } catch (e) {
      setSaveErr(e instanceof ApiError ? e.message : '保存失败，请稍后重试');
    }
  }

  /* ── Key 标签输入 ── */
  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && keyInputRef.current?.value.trim()) {
      e.preventDefault();
      setKeys((prev) => [...prev, keyInputRef.current!.value.trim()]);
      keyInputRef.current!.value = '';
    }
  }
  function removeKey(i: number) {
    setKeys((prev) => prev.filter((_, idx) => idx !== i));
  }

  const selCount = selected.size;
  const colSpan = 11;

  return (
    <AdminShell
      activeId="channels"
      title="渠道管理"
      crumb={['管理后台', '资源管理', '渠道管理']}
      actions={
        <Button variant="primary" size="sm" onClick={() => openDrawer('new')}>
          新建渠道
        </Button>
      }
    >
      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fType} onChange={(e) => setFType(e.target.value)}>
          <option value="">全部类型</option>
          {TYPE_LABELS.map((t) => <option key={t}>{t}</option>)}
        </select>
        <select className={styles.sel} value={fStatus} onChange={(e) => { setFStatus(e.target.value); setPage(1); }}>
          <option value="">全部状态</option>
          <option>启用</option>
          <option>手动禁用</option>
          <option>自动禁用</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索渠道名 / ID"
          value={fSearch}
          onChange={(e) => setFSearch(e.target.value)}
        />
        <span className={styles.grow} />
      </section>

      {/* BatchBar */}
      <section className={`${styles.batchbar}${selCount > 0 ? ' ' + styles.on : ''}`}>
        <span className={styles.cnt}>已选 {selCount} 项</span>
        <span className={styles.grow} />
        <Button variant="sec" size="sm" disabled={batchMutation.isPending} onClick={() => handleBatch('enable')}>批量启用</Button>
        <Button variant="sec" size="sm" disabled={batchMutation.isPending} onClick={() => handleBatch('disable')}>批量禁用</Button>
        <Button variant="danger" size="sm" disabled={batchMutation.isPending} onClick={() => handleBatch('delete')}>批量删除</Button>
      </section>

      {/* 表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th style={{ width: 34 }}>
                  <input
                    type="checkbox"
                    checked={allChecked}
                    onChange={(e) => toggleAll(e.target.checked)}
                    aria-label="全选"
                  />
                </th>
                <th className={styles.sortable}>ID <span className={styles.arr}><SortArr /></span></th>
                <th className={styles.sortable}>渠道名 <span className={styles.arr}><SortArr /></span></th>
                <th>类型</th>
                <th>状态</th>
                <th className={styles.sortable}>优先级 <span className={styles.arr}><SortArr /></span></th>
                <th className={styles.sortable}>权重 <span className={styles.arr}><SortArr /></span></th>
                <th>已用 / 余额</th>
                <th className={styles.sortable}>响应延迟 <span className={styles.arr}><SortArr /></span></th>
                <th>最后测试</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={colSpan} className={styles.emptyCell}>加载中…</td></tr>
              ) : isError ? (
                <tr><td colSpan={colSpan} className={styles.emptyCell}>
                  加载失败：{error instanceof ApiError ? error.message : '请稍后重试'}
                </td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={colSpan} className={styles.emptyCell}>
                  {total === 0 ? '暂无渠道' : '无匹配渠道'}
                </td></tr>
              ) : (
                filtered.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selected.has(r.id)}
                        onChange={(e) => toggleOne(r.id, e.target.checked)}
                        aria-label={`选择 ${r.name}`}
                      />
                    </td>
                    <td className={`${styles.cellmono} muted`}>{r.id}</td>
                    <td>{r.name}</td>
                    <td className="muted">{r.type}</td>
                    <td><StatusBadge st={r.st} /></td>
                    <td className={styles.cellmono}>{r.pr}</td>
                    <td className={styles.cellmono}>{r.wt}</td>
                    <td>
                      <span className={styles.cellmono}>{r.used}</span>{' '}
                      <span className="muted">/ {r.bal}</span>
                    </td>
                    <td><LatCell lat={r.lat} /></td>
                    <td className={`${styles.cellmono} muted`}>{r.testAt}</td>
                    <td>
                      <div className={styles.rowActs}>
                        <a onClick={() => openDrawer('edit', r)}>编辑</a>
                        <a className={styles.dang} onClick={() => handleDelete(r.id)}>删除</a>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {total} 个渠道</span>
          <div className={styles.pg}>
            <button disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>‹</button>
            <span className={styles.pgInfo}>{page} / {totalPages}</span>
            <button disabled={page >= totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>›</button>
          </div>
        </div>
      </section>

      {/* 抽屉遮罩 */}
      <div
        className={`${styles.drawerScrim}${drawerOpen ? ' ' + styles.on : ''}`}
        onClick={closeDrawer}
      />

      {/* 编辑抽屉 */}
      <aside
        className={`${styles.drawer}${drawerOpen ? ' ' + styles.on : ''}`}
        aria-label="渠道编辑"
      >
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{drawerMode === 'new' ? '新建渠道' : '编辑渠道'}</h2>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">×</button>
        </div>

        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">渠道名 <span className="field-req">*</span></label>
            <input
              className="input"
              placeholder="例如：OpenAI 主通道"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div>
            <label className="field-label">渠道类型 <span className="field-req">*</span></label>
            <select
              className="input"
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
            >
              {TYPE_LABELS.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="field-label">Base URL</label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="https://api.openai.com/v1"
              value={form.baseUrl}
              onChange={(e) => setForm((f) => ({ ...f, baseUrl: e.target.value }))}
            />
          </div>
          <div>
            <label className="field-label">API Keys（可多个）</label>
            <div className={styles.tagin}>
              {keys.map((k, i) => (
                <span key={i} className={styles.tag}>
                  {k} <b onClick={() => removeKey(i)}>×</b>
                </span>
              ))}
              <input
                ref={keyInputRef}
                type="text"
                placeholder="粘贴 Key 后回车"
                onKeyDown={handleKeyDown}
              />
            </div>
            <div className="field-hint">支持多 Key 轮询，自动剔除失效 Key</div>
          </div>
          <div>
            <label className="field-label">模型映射</label>
            <textarea
              className={`input ${styles.taArea}`}
              placeholder={'gpt-4o=gpt-4o-2024-08-06\nclaude-3.5=claude-3-5-sonnet-latest'}
              value={form.modelMap}
              onChange={(e) => setForm((f) => ({ ...f, modelMap: e.target.value }))}
            />
          </div>
          <div className={styles.row2}>
            <div>
              <label className="field-label">优先级</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.priority}
                onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              />
            </div>
            <div>
              <label className="field-label">权重</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.weight}
                onChange={(e) => setForm((f) => ({ ...f, weight: e.target.value }))}
              />
            </div>
          </div>
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>启用渠道</label>
            <label className="switch">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
              />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
          {drawerMode === 'edit' && (
            <div className="field-hint" style={{ marginTop: 'var(--space-3)' }}>
              API Key 留空表示保留原有 Key；填入则覆盖。
            </div>
          )}
          {saveErr && (
            <div className="field-hint" style={{ marginTop: 'var(--space-3)', color: 'var(--color-danger)' }}>
              {saveErr}
            </div>
          )}
        </div>

        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>取消</Button>
          <Button
            variant="primary"
            onClick={handleSave}
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? '保存中…' : '保存渠道'}
          </Button>
        </div>
      </aside>
    </AdminShell>
  );
}
