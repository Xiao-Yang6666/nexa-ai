'use client';

import { useState, useMemo } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useAccounts,
  useCreateAccount,
  useUpdateAccount,
  useDeleteAccount,
  useToggleAccount,
  useProbeModels,
  type AccountStatus,
  type AccountRowVM,
} from '../model/provider-account.model';
import styles from './ProviderAccountsAdminPage.module.css';

const ST_MAP: Record<AccountStatus, { cls: string; tone: string; lab: string }> = {
  active: { cls: 'b-suc', tone: '--color-success', lab: '启用' },
  disabled: { cls: 'b-neutral', tone: '--color-text-muted', lab: '禁用' },
  rate_limited: { cls: 'b-dan', tone: '--color-danger', lab: '限流' },
};

function StatusBadge({ st }: { st: AccountStatus }) {
  const s = ST_MAP[st];
  return (
    <span className={`badge ${s.cls}`}>
      <span className="dot" style={{ background: `var(${s.tone})` }} />
      {s.lab}
    </span>
  );
}

/* ── 编辑抽屉表单状态 ── */
interface DrawerForm {
  name: string;
  platform: string;
  credentials: string;
  baseUrl: string;
  concurrency: string;
  priority: string;
  weight: string;
  tag: string;
  models: string;
  modelMapping: string;
  autoBan: boolean;
  autoPause: boolean;
}

const INIT_FORM: DrawerForm = {
  name: '',
  platform: 'openai',
  credentials: '',
  baseUrl: '',
  concurrency: '3',
  priority: '50',
  weight: '0',
  tag: '',
  models: '',
  modelMapping: '',
  autoBan: false,
  autoPause: true,
};

/** 供应商平台选项（覆盖主流 LLM 供应商）。 */
const PLATFORM_OPTIONS = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic' },
  { value: 'azure', label: 'Azure OpenAI' },
  { value: 'google', label: 'Google (Gemini)' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'mistral', label: 'Mistral' },
  { value: 'moonshot', label: 'Moonshot (Kimi)' },
  { value: 'zhipu', label: '智谱 (GLM)' },
  { value: 'baichuan', label: '百川' },
  { value: 'minimax', label: 'MiniMax' },
  { value: 'custom', label: '自定义 (OpenAI 兼容)' },
];

/* ── 模型重定向 A→B 可视化行 ── */
interface MapRow {
  a: string; // 对外模型名
  b: string; // 上游真实模型名
}

/**
 * 把 model_mapping JSON 串解析成可视化行。
 * 解析成功返回行数组；非法 JSON / 非对象返回 null（调用方回退到原始文本编辑）。
 */
function parseMapping(json: string): MapRow[] | null {
  const s = json.trim();
  if (!s) return [];
  try {
    const obj = JSON.parse(s);
    if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return null;
    return Object.entries(obj as Record<string, unknown>).map(([a, b]) => ({
      a,
      b: typeof b === 'string' ? b : String(b ?? ''),
    }));
  } catch {
    return null;
  }
}

/** 把可视化行序列化回 model_mapping JSON 串（仅纳入 A、B 均非空的行；空映射返回 ''）。 */
function serializeMapping(rows: MapRow[]): string {
  const obj: Record<string, string> = {};
  for (const { a, b } of rows) {
    const ka = a.trim();
    const vb = b.trim();
    if (ka && vb) obj[ka] = vb;
  }
  return Object.keys(obj).length === 0 ? '' : JSON.stringify(obj);
}

const PAGE_SIZE = 20;

/**
 * ProviderAccountsAdminPage — 供应商账号管理（AdminAuth，接 /api/admin/accounts）。
 * 筛选栏（platform）+ 表格 + 分页 + 编辑抽屉（CRUD）+ 启停。
 * credentials 仅在新建/编辑时填入（编辑留空=保留原值），列表/视图绝不回显。
 */
export function ProviderAccountsAdminPage() {
  // 筛选
  const [fPlatform, setFPlatform] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [fSearch, setFSearch] = useState('');

  // 分页
  const [page, setPage] = useState(1);

  // 抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'new' | 'edit'>('new');
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<DrawerForm>(INIT_FORM);
  const [saveErr, setSaveErr] = useState<string | null>(null);

  // 模型探测：候选列表 + 错误
  const [probeErr, setProbeErr] = useState<string | null>(null);
  const [probeResult, setProbeResult] = useState<string[] | null>(null);

  // 模型重定向 A→B 可视化编辑：mapRows 为 UI 真相源；mappingRaw=true 时
  // 表示 modelMapping 是无法结构化解析的遗留 JSON，回退到原始文本编辑避免丢数据。
  const [mapRows, setMapRows] = useState<MapRow[]>([]);
  const [mappingRaw, setMappingRaw] = useState(false);

  /* ── 数据（真实接口） ── */
  const { data, isLoading, isError, error } = useAccounts({
    page,
    pageSize: PAGE_SIZE,
    platform: fPlatform || undefined,
  });
  const rows: AccountRowVM[] = useMemo(() => data?.rows ?? [], [data]);
  const total = data?.total ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const createMutation = useCreateAccount();
  const updateMutation = useUpdateAccount();
  const deleteMutation = useDeleteAccount();
  const toggleMutation = useToggleAccount();
  const probeMutation = useProbeModels();

  /* ── 客户端过滤（status/search 在当前页过滤；platform 已下推后端） ── */
  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (fStatus && r.st !== fStatus) return false;
      if (fSearch) {
        const q = fSearch.toLowerCase();
        if (!r.name.toLowerCase().includes(q) && !String(r.id).includes(q)) return false;
      }
      return true;
    });
  }, [rows, fStatus, fSearch]);

  /* ── 抽屉 ── */
  function openDrawer(mode: 'new' | 'edit', row?: AccountRowVM) {
    setDrawerMode(mode);
    setSaveErr(null);
    setProbeErr(null);
    setProbeResult(null);
    if (mode === 'edit' && row) {
      setEditId(row.id);
      const mm = row.modelMapping || '';
      const parsed = parseMapping(mm);
      setMapRows(parsed ?? []);
      setMappingRaw(parsed === null);
      setForm({
        name: row.name,
        platform: row.platform,
        credentials: '',
        baseUrl: row.baseUrl || '',
        concurrency: String(row.concurrency),
        priority: String(row.priority),
        weight: String(row.weight || 0),
        tag: row.tag || '',
        models: row.models || '',
        modelMapping: mm,
        autoBan: row.autoBan,
        autoPause: row.autoPause,
      });
    } else {
      setEditId(null);
      setMapRows([]);
      setMappingRaw(false);
      setForm(INIT_FORM);
    }
    setDrawerOpen(true);
  }
  function closeDrawer() {
    setDrawerOpen(false);
  }

  /* ── 保存账号（新建 POST / 编辑 PUT，覆盖式；API Key 留空=保留原值） ── */
  async function handleSave() {
    setSaveErr(null);
    const name = form.name.trim();
    const platform = form.platform.trim();
    if (!name) {
      setSaveErr('请填写账号名');
      return;
    }
    if (!platform) {
      setSaveErr('请选择供应商平台');
      return;
    }
    // 新建必填 API Key；编辑留空表示保留原 API Key。
    const apiKey = form.credentials.trim();
    if (drawerMode === 'new' && !apiKey) {
      setSaveErr('请填写 API Key');
      return;
    }
    // API Key wrap 成 credentials JSON（后端聚合保留语义：空白=保留原值）。
    const credentials = apiKey ? JSON.stringify({ key: apiKey }) : undefined;
    const base = {
      name,
      platform,
      type: 'api_key', // 内部固定（OAuth 等扩展后再开放选择）
      base_url: form.baseUrl.trim() || undefined,
      concurrency: Number(form.concurrency) || undefined,
      priority: Number(form.priority) || undefined,
      auto_pause_on_expired: form.autoPause,
      model_mapping: form.modelMapping.trim() || undefined,
      weight: Number(form.weight) || undefined,
      tag: form.tag.trim() || undefined,
      auto_ban: form.autoBan,
      models: form.models.trim() || undefined,
    };
    try {
      if (drawerMode === 'new') {
        await createMutation.mutateAsync({ ...base, credentials });
      } else if (editId != null) {
        await updateMutation.mutateAsync({
          id: editId,
          req: { ...base, credentials },
        });
      }
      setDrawerOpen(false);
    } catch (e) {
      setSaveErr(e instanceof ApiError ? e.message : '保存失败，请稍后重试');
    }
  }

  async function handleDelete(id: number) {
    await deleteMutation.mutateAsync(id);
  }

  async function handleToggle(row: AccountRowVM) {
    await toggleMutation.mutateAsync({ id: row.id, enable: row.st !== 'active' });
  }

  /* ── 探测上游模型列表（点"获取模型列表"按钮，用表单当前 platform/baseUrl/apiKey 调上游） ── */
  async function handleProbe() {
    setProbeErr(null);
    setProbeResult(null);
    const platform = form.platform.trim();
    const apiKey = form.credentials.trim();
    if (!platform) {
      setProbeErr('请先选择供应商平台');
      return;
    }
    if (!apiKey) {
      setProbeErr('请先填写 API Key（编辑态留空时无法重新探测）');
      return;
    }
    try {
      const ids = await probeMutation.mutateAsync({
        platform,
        base_url: form.baseUrl.trim() || undefined,
        api_key: apiKey,
      });
      setProbeResult(ids);
      if (ids.length === 0) {
        setProbeErr('上游返回空模型列表');
      }
    } catch (e) {
      setProbeErr(e instanceof ApiError ? e.message : '获取模型列表失败');
    }
  }

  /** 切换勾选某个候选模型（加入 / 移出 models 字段，逗号分隔保序去重）。 */
  function toggleModelPick(modelId: string) {
    const list = form.models
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    const idx = list.indexOf(modelId);
    if (idx >= 0) {
      list.splice(idx, 1);
    } else {
      list.push(modelId);
    }
    setForm((f) => ({ ...f, models: list.join(',') }));
  }

  /** 一键全选所有探测到的模型。 */
  function selectAllProbed() {
    if (!probeResult || probeResult.length === 0) return;
    setForm((f) => ({ ...f, models: probeResult.join(',') }));
  }

  /* ── 模型重定向 A→B 可视化行编辑：每次变更即序列化回 form.modelMapping（单一真相源） ── */
  function commitMapRows(next: MapRow[]) {
    setMapRows(next);
    setForm((f) => ({ ...f, modelMapping: serializeMapping(next) }));
  }
  function addMapRow() {
    commitMapRows([...mapRows, { a: '', b: '' }]);
  }
  function updateMapRow(idx: number, key: 'a' | 'b', val: string) {
    commitMapRows(mapRows.map((r, i) => (i === idx ? { ...r, [key]: val } : r)));
  }
  function removeMapRow(idx: number) {
    commitMapRows(mapRows.filter((_, i) => i !== idx));
  }
  /** 从「支持模型 A」列表一键预填映射行（A=B 占位，运营再改 B），跳过已存在的 A。 */
  function prefillMapFromModels() {
    const existing = new Set(mapRows.map((r) => r.a.trim()));
    const aList = form.models
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && !existing.has(s));
    if (aList.length === 0) return;
    commitMapRows([...mapRows, ...aList.map((a) => ({ a, b: a }))]);
  }
  /** 从遗留 JSON 文本切回可视化编辑（解析成功才切，失败保持原文本态）。 */
  function switchToVisualMapping() {
    const parsed = parseMapping(form.modelMapping);
    if (parsed === null) return;
    setMapRows(parsed);
    setMappingRaw(false);
  }

  const colSpan = 10;
  const saving = createMutation.isPending || updateMutation.isPending;

  return (
    <AppShell
      activeId="provider-accounts"
      title="供应商账号"
      crumb={['管理后台', '资源管理', '供应商账号']}
      actions={
        <Button variant="primary" size="sm" onClick={() => openDrawer('new')}>
          新建账号
        </Button>
      }
    >
      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select
          className={styles.sel}
          value={fPlatform}
          onChange={(e) => {
            setFPlatform(e.target.value);
            setPage(1);
          }}
        >
          <option value="">全部平台</option>
          {PLATFORM_OPTIONS.map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
        <select
          className={styles.sel}
          value={fStatus}
          onChange={(e) => setFStatus(e.target.value)}
        >
          <option value="">全部状态</option>
          <option value="active">启用</option>
          <option value="disabled">禁用</option>
          <option value="rate_limited">限流</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索账号名 / ID"
          value={fSearch}
          onChange={(e) => setFSearch(e.target.value)}
        />
        <span className={styles.grow} />
      </section>

      {/* 表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th className={`${styles.cellmono}`}>ID</th>
                <th>账号名</th>
                <th>平台</th>
                <th>状态</th>
                <th>并发</th>
                <th>优先级</th>
                <th>权重</th>
                <th>标签</th>
                <th>过期</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={colSpan} className={styles.emptyCell}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={colSpan} className={styles.emptyCell}>
                    加载失败：{error instanceof ApiError ? error.message : '请稍后重试'}
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={colSpan} className={styles.emptyCell}>
                    {total === 0 ? '暂无账号' : '无匹配账号'}
                  </td>
                </tr>
              ) : (
                filtered.map((r) => (
                  <tr key={r.id}>
                    <td className={`${styles.cellmono} muted`}>{r.id}</td>
                    <td>{r.name}</td>
                    <td className="muted">{r.platform}</td>
                    <td>
                      <StatusBadge st={r.st} />
                    </td>
                    <td className={styles.cellmono}>{r.concurrency}</td>
                    <td className={styles.cellmono}>{r.priority}</td>
                    <td className={styles.cellmono}>{r.weight || 0}</td>
                    <td className={`${styles.cellmono} muted`}>{r.tag || '-'}</td>
                    <td className={`${styles.cellmono} muted`}>{r.exp}</td>
                    <td>
                      <div className={styles.rowActs}>
                        <a onClick={() => openDrawer('edit', r)}>编辑</a>
                        <a
                          onClick={() => {
                            if (!toggleMutation.isPending) void handleToggle(r);
                          }}
                        >
                          {r.st === 'active' ? '禁用' : '启用'}
                        </a>
                        <a
                          className={styles.dang}
                          onClick={() => {
                            if (!deleteMutation.isPending) void handleDelete(r.id);
                          }}
                        >
                          删除
                        </a>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {total} 个账号</span>
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


      {/* 抽屉遮罩 */}
      <div
        className={`${styles.drawerScrim}${drawerOpen ? ' ' + styles.on : ''}`}
        onClick={closeDrawer}
      />

      {/* 编辑抽屉 */}
      <aside
        className={`${styles.drawer}${drawerOpen ? ' ' + styles.on : ''}`}
        aria-label="供应商账号编辑"
      >
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{drawerMode === 'new' ? '新建账号' : '编辑账号'}</h2>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">
            ×
          </button>
        </div>

        <div className={styles.drawerBody}>
          {/* ── 基本信息 ── */}
          <div className={styles.sectionTitle}>基本信息</div>
          <div>
            <label className="field-label">
              账号名 <span className="field-req">*</span>
            </label>
            <input
              className="input"
              placeholder="例如：OpenAI 主账号"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div>
            <label className="field-label">
              供应商平台 <span className="field-req">*</span>
            </label>
            <select
              className="input"
              value={form.platform}
              onChange={(e) => setForm((f) => ({ ...f, platform: e.target.value }))}
            >
              {PLATFORM_OPTIONS.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
            <div className="field-hint">决定上游协议（Anthropic 走 Claude 协议，其余走 OpenAI 兼容协议）。</div>
          </div>

          {/* ── 连接配置 ── */}
          <div className={styles.sectionTitle}>连接配置</div>
          <div>
            <label className="field-label">
              API Key {drawerMode === 'new' && <span className="field-req">*</span>}
            </label>
            <input
              className={`input ${styles.cellmono}`}
              type="password"
              autoComplete="new-password"
              placeholder="sk-..."
              value={form.credentials}
              onChange={(e) => setForm((f) => ({ ...f, credentials: e.target.value }))}
            />
            <div className="field-hint">
              敏感凭证，保存后不再回显。
              {drawerMode === 'edit' && '编辑时留空表示保留原 Key；填入则覆盖。'}
            </div>
          </div>
          <div>
            <label className="field-label">Base URL</label>
            <input
              className="input"
              placeholder="留空使用平台默认地址，如 https://api.openai.com/v1"
              value={form.baseUrl}
              onChange={(e) => setForm((f) => ({ ...f, baseUrl: e.target.value }))}
            />
            <div className="field-hint">自定义/代理上游时填写；留空则用平台默认地址。</div>
          </div>

          {/* ── 路由与调度 ── */}
          <div className={styles.sectionTitle}>路由与调度</div>
          <div className={styles.row2}>
            <div>
              <label className="field-label">优先级</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.priority}
                inputMode="numeric"
                onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              />
              <div className="field-hint">数值越小越优先选用。</div>
            </div>
            <div>
              <label className="field-label">权重</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.weight}
                inputMode="numeric"
                onChange={(e) => setForm((f) => ({ ...f, weight: e.target.value }))}
              />
              <div className="field-hint">同优先级内按权重加权随机。</div>
            </div>
          </div>
          <div className={styles.row2}>
            <div>
              <label className="field-label">并发度</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.concurrency}
                inputMode="numeric"
                onChange={(e) => setForm((f) => ({ ...f, concurrency: e.target.value }))}
              />
              <div className="field-hint">该账号最大并发请求数。</div>
            </div>
            <div>
              <label className="field-label">标签</label>
              <input
                className="input"
                placeholder="例如：prod"
                value={form.tag}
                onChange={(e) => setForm((f) => ({ ...f, tag: e.target.value }))}
              />
              <div className="field-hint">用于批量启停/操作。</div>
            </div>
          </div>

          {/* ── 模型配置 ── */}
          <div className={styles.sectionTitle}>模型配置</div>
          <div>
            <div className={styles.modelsLabelRow}>
              <label className="field-label" style={{ margin: 0 }}>
                支持的模型
              </label>
              <button
                type="button"
                className={styles.probeBtn}
                onClick={() => void handleProbe()}
                disabled={probeMutation.isPending}
              >
                {probeMutation.isPending ? '获取中…' : '⟳ 获取模型列表'}
              </button>
            </div>
            <input
              className="input"
              placeholder="gpt-4o, gpt-4o-mini, o1"
              value={form.models}
              onChange={(e) => setForm((f) => ({ ...f, models: e.target.value }))}
            />
            <div className="field-hint">
              逗号分隔。可手动填写，或填好上方 API Key / Base URL 后点「获取模型列表」从上游拉取勾选。
            </div>
            {probeErr && (
              <div className="field-hint" style={{ color: 'var(--color-danger)' }}>
                {probeErr}
              </div>
            )}
            {probeResult && probeResult.length > 0 && (
              <div className={styles.probeBox}>
                <div className={styles.probeBoxHead}>
                  <span>探测到 {probeResult.length} 个模型，点击勾选：</span>
                  <a onClick={selectAllProbed}>全选</a>
                </div>
                <div className={styles.probeChips}>
                  {probeResult.map((m) => {
                    const picked = form.models
                      .split(',')
                      .map((s) => s.trim())
                      .includes(m);
                    return (
                      <button
                        type="button"
                        key={m}
                        className={`${styles.probeChip}${picked ? ' ' + styles.probeChipOn : ''}`}
                        onClick={() => toggleModelPick(m)}
                      >
                        {picked ? '✓ ' : ''}
                        {m}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
          <div>
            <div className={styles.modelsLabelRow}>
              <label className="field-label" style={{ margin: 0 }}>
                模型重定向 A→B（可选）
              </label>
              {!mappingRaw && (
                <button type="button" className={styles.probeBtn} onClick={prefillMapFromModels}>
                  ＋ 从支持模型预填
                </button>
              )}
            </div>

            {mappingRaw ? (
              /* 遗留非结构化 JSON：保留原始文本编辑，避免丢运营已填数据；可一键切回可视化。 */
              <>
                <textarea
                  className={`input ${styles.taArea} ${styles.cellmono}`}
                  placeholder={'{"gpt-4":"gpt-4-turbo"}'}
                  value={form.modelMapping}
                  onChange={(e) => setForm((f) => ({ ...f, modelMapping: e.target.value }))}
                />
                <div className="field-hint">
                  当前为原始 JSON 格式（无法结构化解析）。
                  <button
                    type="button"
                    className={styles.linkBtn}
                    onClick={switchToVisualMapping}
                  >
                    转为可视化编辑
                  </button>
                </div>
              </>
            ) : (
              <>
                {mapRows.length > 0 && (
                  <div className={styles.mapTable}>
                    <div className={styles.mapHeadRow}>
                      <span>对外模型名 (A)</span>
                      <span />
                      <span>上游真实名 (B)</span>
                      <span />
                    </div>
                    {mapRows.map((r, idx) => (
                      <div className={styles.mapRow} key={idx}>
                        <input
                          className={`input ${styles.cellmono}`}
                          placeholder="gpt-4"
                          value={r.a}
                          onChange={(e) => updateMapRow(idx, 'a', e.target.value)}
                        />
                        <span className={styles.mapArrow}>→</span>
                        <input
                          className={`input ${styles.cellmono}`}
                          placeholder="gpt-4-turbo"
                          value={r.b}
                          onChange={(e) => updateMapRow(idx, 'b', e.target.value)}
                        />
                        <button
                          type="button"
                          className={styles.mapDelBtn}
                          onClick={() => removeMapRow(idx)}
                          aria-label="删除此映射"
                        >
                          ✕
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                <button type="button" className={styles.mapAddBtn} onClick={addMapRow}>
                  ＋ 添加映射
                </button>
                <div className="field-hint">
                  把对外模型名 (A) 重定向到上游真实模型名 (B)；未配置的模型保持原名转发。
                </div>
              </>
            )}
          </div>

          {/* ── 高级选项 ── */}
          <div className={styles.sectionTitle}>高级选项</div>
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>
              失败自动封禁
            </label>
            <label className="switch">
              <input
                type="checkbox"
                checked={form.autoBan}
                onChange={(e) => setForm((f) => ({ ...f, autoBan: e.target.checked }))}
              />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>
              过期自动暂停
            </label>
            <label className="switch">
              <input
                type="checkbox"
                checked={form.autoPause}
                onChange={(e) => setForm((f) => ({ ...f, autoPause: e.target.checked }))}
              />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
          {saveErr && (
            <div
              className="field-hint"
              style={{ marginTop: 'var(--space-3)', color: 'var(--color-danger)' }}
            >
              {saveErr}
            </div>
          )}
        </div>

        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>
            取消
          </Button>
          <Button variant="primary" onClick={handleSave} disabled={saving}>
            {saving ? '保存中…' : '保存账号'}
          </Button>
        </div>
      </aside>
    </AppShell>
  );
}
