'use client';

import { useState, useRef, useMemo, type KeyboardEvent } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import { fetchUpstreamModels } from '../api/channel.api';
import {
  useChannels,
  useDeleteChannel,
  useBatchOperateChannels,
  useCreateChannel,
  useUpdateChannel,
  useBatchUpsertChannelCosts,
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
interface ModelMapRow {
  a: string;
  b: string;
}

interface DrawerForm {
  name: string;
  type: string;
  baseUrl: string;
  models: string;
  group: string;
  priority: string;
  weight: string;
  enabled: boolean;
}

const INIT_FORM: DrawerForm = {
  name: '', type: 'OpenAI', baseUrl: '', models: '',
  group: 'default', priority: '10', weight: '5', enabled: true,
};

/** 解析渠道 modelMapping JSON（{"A":"B"}）为行数组；非法/空 → 空数组。 */
function parseModelMap(json: string): ModelMapRow[] {
  if (!json || !json.trim()) return [];
  try {
    const obj = JSON.parse(json) as Record<string, unknown>;
    return Object.entries(obj)
      .filter(([, v]) => typeof v === 'string')
      .map(([a, b]) => ({ a, b: String(b) }));
  } catch {
    return [];
  }
}

/** 行数组 → modelMapping JSON 字符串（空数组 → 空串=不映射）。 */
function serializeModelMap(rows: ModelMapRow[]): string {
  const obj: Record<string, string> = {};
  for (const r of rows) {
    const a = r.a.trim();
    const b = r.b.trim();
    if (a && b) obj[a] = b;
  }
  return Object.keys(obj).length === 0 ? '' : JSON.stringify(obj);
}

const TYPE_LABELS = TYPE_OPTIONS.map((t) => t.label);

/* ── 可输入下拉（free-input combobox：给候选 + 支持手输任意值） ── */
function Combobox({ value, candidates, placeholder, onChange }: {
  value: string;
  candidates: string[];
  placeholder?: string;
  onChange: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const q = value.trim().toLowerCase();
  const filtered = useMemo(() => {
    const list = candidates.filter(Boolean);
    if (!q) return list.slice(0, 50);
    return list
      .filter((c) => c.toLowerCase().includes(q))
      .sort((a, b) => a.toLowerCase().indexOf(q) - b.toLowerCase().indexOf(q) || a.length - b.length)
      .slice(0, 50);
  }, [candidates, q]);
  return (
    <div className={styles.combo}>
      <input
        className={`input ${styles.cellmono}`}
        placeholder={placeholder}
        value={value}
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 120)}
      />
      {open && filtered.length > 0 && (
        <div className={styles.comboDd} role="listbox">
          {filtered.map((c) => (
            <div
              key={c}
              className={styles.comboOpt}
              onMouseDown={(e) => { e.preventDefault(); onChange(c); setOpen(false); }}
            >
              {c}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ── 模型多选（已选 chip + 候选下拉 + 手输回车添加） ── */
function ModelMultiSelect({ selected, candidates, onChange }: {
  selected: string[];
  candidates: string[];
  onChange: (next: string[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const sel = new Set(selected);
  const q = input.trim().toLowerCase();
  const filtered = useMemo(() => {
    const list = candidates.filter((c) => c && !sel.has(c));
    if (!q) return list.slice(0, 50);
    return list.filter((c) => c.toLowerCase().includes(q)).slice(0, 50);
  }, [candidates, q, selected.join(',')]);
  function add(v: string) {
    const t = v.trim();
    if (!t || sel.has(t)) return;
    onChange([...selected, t]);
    setInput('');
  }
  function remove(v: string) {
    onChange(selected.filter((s) => s !== v));
  }
  return (
    <div>
      <div className={styles.msBox}>
        {selected.map((s) => (
          <span key={s} className={styles.tag}>
            {s} <b onClick={() => remove(s)}>×</b>
          </span>
        ))}
        <div className={styles.combo} style={{ flex: 1, minWidth: 140 }}>
          <input
            type="text"
            placeholder={selected.length ? '继续添加…' : '选择或输入模型名，回车添加'}
            value={input}
            onChange={(e) => { setInput(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onBlur={() => setTimeout(() => setOpen(false), 120)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && input.trim()) { e.preventDefault(); add(input); }
            }}
          />
          {open && filtered.length > 0 && (
            <div className={styles.comboDd} role="listbox">
              {filtered.map((c) => (
                <div
                  key={c}
                  className={styles.comboOpt}
                  onMouseDown={(e) => { e.preventDefault(); add(c); }}
                >
                  {c}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

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
  const [fGroup, setFGroup] = useState('');
  const [fSearch, setFSearch] = useState('');

  // 分页
  const [page, setPage] = useState(1);

  // 批量选择
  const [selected, setSelected] = useState<Set<number>>(new Set());

  // 批量设成本倍率弹窗
  const [costModalOpen, setCostModalOpen] = useState(false);
  const [costRatioInput, setCostRatioInput] = useState('1');
  const [costErr, setCostErr] = useState<string | null>(null);

  // 抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'new' | 'edit'>('new');
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<DrawerForm>(INIT_FORM);
  const [modelMapRows, setModelMapRows] = useState<ModelMapRow[]>([]);
  const [fetchedModels, setFetchedModels] = useState<string[]>([]);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const [fetchingModels, setFetchingModels] = useState(false);
  const [fetchModelsErr, setFetchModelsErr] = useState<string | null>(null);

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
  const batchCostMutation = useBatchUpsertChannelCosts();

  /* ── 客户端过滤（type/group/search 在当前页过滤；status 已下推后端） ── */
  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (fType && r.type !== fType) return false;
      if (fGroup && r.group !== fGroup) return false;
      if (fSearch) {
        const q = fSearch.toLowerCase();
        if (!r.name.toLowerCase().includes(q) && !String(r.id).includes(q)) return false;
      }
      return true;
    });
  }, [rows, fType, fGroup, fSearch]);

  /* 分组下拉项（来自当前页渠道的去重 group）。 */
  const groupOptions = useMemo(
    () => Array.from(new Set(rows.map((r) => r.group).filter(Boolean))),
    [rows],
  );

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

  /* ── 批量设成本倍率：对选中渠道 × 各支持模型 A（经该渠道 A→B 重定向解析为 B，未配则 B=A）写统一成本 ── */
  async function handleBatchCost() {
    setCostErr(null);
    const ratio = Number(costRatioInput);
    if (Number.isNaN(ratio) || ratio < 0) { setCostErr('成本倍率非法'); return; }
    const selectedRows = filtered.filter((r) => selected.has(r.id));
    const items: { channel_id: number; upstream_model: string; cost_ratio: number; enabled: boolean }[] = [];
    for (const r of selectedRows) {
      const mapping = parseModelMap(r.modelMapping);
      const a2b = new Map(mapping.map((m) => [m.a, m.b]));
      const models = r.models.split(',').map((s) => s.trim()).filter(Boolean);
      const bSet = new Set<string>();
      for (const a of models) {
        bSet.add(a2b.get(a) || a); // 配了重定向用 B，否则 B=A
      }
      for (const b of bSet) {
        items.push({ channel_id: r.id, upstream_model: b, cost_ratio: ratio, enabled: true });
      }
    }
    if (items.length === 0) { setCostErr('选中渠道无可设成本的模型'); return; }
    try {
      await batchCostMutation.mutateAsync(items as Parameters<typeof batchCostMutation.mutateAsync>[0]);
      setCostModalOpen(false);
      setSelected(new Set());
    } catch (e) {
      setCostErr(e instanceof ApiError ? e.message : '批量设成本失败');
    }
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
    setFetchModelsErr(null);
    setFetchedModels([]);
    if (mode === 'edit' && row) {
      setEditId(row.id);
      setForm({
        name: row.name,
        type: row.type,
        baseUrl: row.baseUrl,
        models: row.models,
        group: row.group || 'default',
        priority: String(row.pr),
        weight: String(row.wt),
        enabled: row.st === 'on',
      });
      setModelMapRows(parseModelMap(row.modelMapping));
    } else {
      setEditId(null);
      setForm(INIT_FORM);
      setModelMapRows([]);
    }
    setKeys([]);
    if (keyInputRef.current) keyInputRef.current.value = '';
    setDrawerOpen(true);
  }
  function closeDrawer() { setDrawerOpen(false); }

  /* ── 获取上游模型列表（按当前表单 type/baseUrl/key 探测，填入支持模型 A） ── */
  async function handleFetchModels() {
    setFetchModelsErr(null);
    const pendingKey = keyInputRef.current?.value.trim();
    const allKeys = pendingKey ? [...keys, pendingKey] : keys;
    const keyStr = allKeys.join('\n');
    if (!keyStr) { setFetchModelsErr('请先填写至少一个 API Key 再获取模型列表'); return; }
    const typeCode = TYPE_OPTIONS.find((t) => t.label === form.type)?.code ?? 1;
    setFetchingModels(true);
    try {
      const models = await fetchUpstreamModels({
        type: typeCode,
        baseUrl: form.baseUrl.trim() || undefined,
        key: keyStr,
      });
      if (models.length === 0) {
        setFetchModelsErr('上游未返回任何模型');
        return;
      }
      // 拉到的模型作为候选池（供多选下拉 + 映射 B 候选）。
      setFetchedModels(models);
      // 同时合并进已选支持模型（保序去重：已有在前，新增在后）；用户可在标签上再增删。
      const existing = form.models.split(',').map((s) => s.trim()).filter(Boolean);
      const merged = Array.from(new Set([...existing, ...models]));
      setForm((f) => ({ ...f, models: merged.join(',') }));
    } catch (e) {
      setFetchModelsErr(e instanceof ApiError ? e.message : '获取模型列表失败');
    } finally {
      setFetchingModels(false);
    }
  }

  /* ── 模型映射行编辑 ── */
  function addMapRow() {
    setModelMapRows((rows) => [...rows, { a: '', b: '' }]);
  }
  function updateMapRow(i: number, field: 'a' | 'b', value: string) {
    setModelMapRows((rows) => rows.map((r, idx) => (idx === i ? { ...r, [field]: value } : r)));
  }
  function removeMapRow(i: number) {
    setModelMapRows((rows) => rows.filter((_, idx) => idx !== i));
  }

  /* ── 保存渠道（新建 POST / 编辑 PUT，覆盖式；key 留空=保留原 key） ── */
  async function handleSave() {
    setSaveErr(null);
    const name = form.name.trim();
    if (!name) { setSaveErr('请填写渠道名'); return; }
    const modelsStr = form.models.trim();
    if (!modelsStr) { setSaveErr('请填写支持模型（逗号分隔），渠道至少声明一个对外模型 A'); return; }
    const typeCode = TYPE_OPTIONS.find((t) => t.label === form.type)?.code ?? 1;
    // 收集 key：已成标签的 keys + 输入框中尚未回车提交的残留文本（用户常填完不回车直接保存）
    const pendingKey = keyInputRef.current?.value.trim();
    const allKeys = pendingKey ? [...keys, pendingKey] : keys;
    const keyStr = allKeys.join('\n');
    const priority = Number(form.priority) || 0;
    const weight = Number(form.weight) || 0;
    const modelMapJson = serializeModelMap(modelMapRows);
    // status 由 enabled 决定：编辑时启用=1，禁用=2（手动）；后端覆盖式更新接受 type/models 等
    const base = {
      type: typeCode,
      name,
      group: form.group.trim() || 'default',
      base_url: form.baseUrl.trim() || undefined,
      models: modelsStr,
      model_mapping: modelMapJson || undefined,
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
  const colSpan = 12;

  return (
    <AppShell
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
        <select className={styles.sel} value={fGroup} onChange={(e) => setFGroup(e.target.value)}>
          <option value="">全部分组</option>
          {groupOptions.map((g) => <option key={g} value={g}>{g}</option>)}
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
        <Button variant="sec" size="sm" onClick={() => { setCostErr(null); setCostRatioInput('1'); setCostModalOpen(true); }}>批量设成本倍率</Button>
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
                <th>分组</th>
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
                    <td className="muted">{r.group}</td>
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
                placeholder="粘贴 Key，回车可添加多个"
                onKeyDown={handleKeyDown}
              />
            </div>
            <div className="field-hint">支持多 Key 轮询，自动剔除失效 Key</div>
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
              <label className="field-label" style={{ margin: 0 }}>支持模型 A *</label>
              <Button
                variant="sec"
                size="sm"
                disabled={fetchingModels}
                onClick={handleFetchModels}
              >
                {fetchingModels ? '获取中…' : '获取模型列表'}
              </Button>
            </div>
            <div style={{ marginTop: 'var(--space-2)' }}>
              <ModelMultiSelect
                selected={form.models.split(',').map((s) => s.trim()).filter(Boolean)}
                candidates={Array.from(new Set([...fetchedModels, ...form.models.split(',').map((s) => s.trim()).filter(Boolean)]))}
                onChange={(next) => setForm((f) => ({ ...f, models: next.join(',') }))}
              />
            </div>
            <div className="field-hint">该渠道对外声明支持的<b>平台模型 A</b>列表（必填）。选渠按 A 匹配。填好 Key 后点「获取模型列表」从上游自动拉取，再在下拉里勾选；也可手输任意模型名后回车添加。</div>
            {fetchModelsErr && (
              <div className="field-hint" style={{ color: 'var(--color-danger)' }}>{fetchModelsErr}</div>
            )}
          </div>
          <div>
            <label className="field-label">模型重定向 A → B（本渠道私有）</label>
            <div className="field-hint" style={{ marginBottom: 'var(--space-2)' }}>
              把平台模型名 A 重定向到<b>本渠道上游真实名 B</b>。不配则按 A 原样请求上游。仅本渠道生效，客户不可见 B。
            </div>
            <div className={styles.mapTable}>
              {modelMapRows.length === 0 ? (
                <div className="field-hint" style={{ padding: 'var(--space-2) 0' }}>暂无重定向，按 A 原样转发。</div>
              ) : (
                modelMapRows.map((r, i) => (
                  <div key={i} className={styles.mapRow}>
                    <Combobox
                      value={r.a}
                      placeholder="平台模型 A"
                      candidates={form.models.split(',').map((s) => s.trim()).filter(Boolean)}
                      onChange={(v) => updateMapRow(i, 'a', v)}
                    />
                    <span className={styles.mapArrow} aria-hidden="true">→</span>
                    <Combobox
                      value={r.b}
                      placeholder="上游真实名 B"
                      candidates={fetchedModels}
                      onChange={(v) => updateMapRow(i, 'b', v)}
                    />
                    <button type="button" className={styles.mapDel} onClick={() => removeMapRow(i)} aria-label="删除映射">×</button>
                  </div>
                ))
              )}
            </div>
            <Button variant="sec" size="sm" onClick={addMapRow} style={{ marginTop: 'var(--space-2)' }}>
              + 添加重定向
            </Button>
          </div>
          <div>
            <label className="field-label">分组</label>
            <input
              className="input"
              placeholder="default"
              value={form.group}
              onChange={(e) => setForm((f) => ({ ...f, group: e.target.value }))}
            />
            <div className="field-hint">渠道分组：选渠按分组圈定候选；客户分组与本分组同名即可命中该渠道池。</div>
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

      {/* 批量设成本倍率弹窗 */}
      {costModalOpen && (
        <div
          className={`${styles.drawerScrim} ${styles.on}`}
          onClick={(e) => { if (e.target === e.currentTarget) setCostModalOpen(false); }}
        >
          <div
            role="dialog"
            aria-label="批量设成本倍率"
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%,-50%)',
              background: 'var(--color-surface)', border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-lg)', padding: 'var(--space-5)', width: 'min(420px, 92vw)',
              zIndex: 1000, boxShadow: 'var(--shadow-lg, 0 12px 40px rgba(0,0,0,.18))',
            }}
          >
            <h2 style={{ margin: '0 0 var(--space-3)', fontSize: 'var(--text-h4, 16px)' }}>批量设成本倍率</h2>
            <div className="field-hint" style={{ marginBottom: 'var(--space-3)' }}>
              对已选 <b>{selCount}</b> 个渠道的每个支持模型（按渠道 A→B 重定向解析为真实 B，未配则按 A）写入统一成本倍率（覆盖式 upsert）。
            </div>
            <label className="field-label">成本倍率</label>
            <input
              className={`input ${styles.cellmono}`}
              inputMode="decimal"
              value={costRatioInput}
              onChange={(e) => setCostRatioInput(e.target.value)}
            />
            {costErr && (
              <div className="field-hint" style={{ marginTop: 'var(--space-2)', color: 'var(--color-danger)' }}>
                {costErr}
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', marginTop: 'var(--space-4)' }}>
              <Button variant="ghost" onClick={() => setCostModalOpen(false)}>取消</Button>
              <Button variant="primary" disabled={batchCostMutation.isPending} onClick={handleBatchCost}>
                {batchCostMutation.isPending ? '保存中…' : '应用到选中渠道'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
