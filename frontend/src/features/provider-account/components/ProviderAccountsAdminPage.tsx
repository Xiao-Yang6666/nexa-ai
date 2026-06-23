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
  type: string;
  credentials: string;
  concurrency: string;
  priority: string;
  autoPause: boolean;
}

const INIT_FORM: DrawerForm = {
  name: '',
  platform: 'openai',
  type: 'api_key',
  credentials: '',
  concurrency: '3',
  priority: '50',
  autoPause: true,
};

/** 平台预设选项（可自由输入，仅作下拉建议）。 */
const PLATFORM_OPTIONS = ['openai', 'anthropic', 'google', 'azure'];
/** 账号类型预设。 */
const TYPE_OPTIONS = ['api_key', 'oauth'];

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
    if (mode === 'edit' && row) {
      setEditId(row.id);
      setForm({
        name: row.name,
        platform: row.platform,
        type: row.type,
        credentials: '',
        concurrency: String(row.concurrency),
        priority: String(row.priority),
        autoPause: row.autoPause,
      });
    } else {
      setEditId(null);
      setForm(INIT_FORM);
    }
    setDrawerOpen(true);
  }
  function closeDrawer() {
    setDrawerOpen(false);
  }

  /* ── 保存账号（新建 POST / 编辑 PUT，覆盖式；credentials 留空=保留原值） ── */
  async function handleSave() {
    setSaveErr(null);
    const name = form.name.trim();
    const platform = form.platform.trim();
    const type = form.type.trim();
    if (!name) {
      setSaveErr('请填写账号名');
      return;
    }
    if (!platform) {
      setSaveErr('请填写供应商平台');
      return;
    }
    if (!type) {
      setSaveErr('请填写账号类型');
      return;
    }
    const credentials = form.credentials.trim();
    const base = {
      name,
      platform,
      type,
      concurrency: Number(form.concurrency) || undefined,
      priority: Number(form.priority) || undefined,
      auto_pause_on_expired: form.autoPause,
    };
    try {
      if (drawerMode === 'new') {
        await createMutation.mutateAsync({
          ...base,
          credentials: credentials || undefined,
        });
      } else if (editId != null) {
        // 编辑：credentials 留空表示保留原凭证（后端聚合处理）
        await updateMutation.mutateAsync({
          id: editId,
          req: { ...base, credentials: credentials || undefined },
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

  const colSpan = 9;
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
        <input
          className={styles.sel}
          list="provider-platform-filter"
          placeholder="全部平台"
          value={fPlatform}
          onChange={(e) => {
            setFPlatform(e.target.value);
            setPage(1);
          }}
        />
        <datalist id="provider-platform-filter">
          {PLATFORM_OPTIONS.map((p) => (
            <option key={p} value={p} />
          ))}
        </datalist>
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
                <th>类型</th>
                <th>状态</th>
                <th>并发</th>
                <th>优先级</th>
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
                    <td className="muted">{r.type}</td>
                    <td>
                      <StatusBadge st={r.st} />
                    </td>
                    <td className={styles.cellmono}>{r.concurrency}</td>
                    <td className={styles.cellmono}>{r.priority}</td>
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
          <div className={styles.row2}>
            <div>
              <label className="field-label">
                供应商平台 <span className="field-req">*</span>
              </label>
              <input
                className="input"
                list="provider-platform-options"
                placeholder="openai"
                value={form.platform}
                onChange={(e) => setForm((f) => ({ ...f, platform: e.target.value }))}
              />
              <datalist id="provider-platform-options">
                {PLATFORM_OPTIONS.map((p) => (
                  <option key={p} value={p} />
                ))}
              </datalist>
            </div>
            <div>
              <label className="field-label">
                账号类型 <span className="field-req">*</span>
              </label>
              <input
                className="input"
                list="provider-type-options"
                placeholder="api_key"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              />
              <datalist id="provider-type-options">
                {TYPE_OPTIONS.map((t) => (
                  <option key={t} value={t} />
                ))}
              </datalist>
            </div>
          </div>
          <div>
            <label className="field-label">凭证 Credentials（JSON）</label>
            <textarea
              className={`input ${styles.taArea} ${styles.cellmono}`}
              placeholder={'{"key":"sk-..."}'}
              value={form.credentials}
              onChange={(e) => setForm((f) => ({ ...f, credentials: e.target.value }))}
            />
            <div className="field-hint">
              敏感凭证，保存后不再回显。
              {drawerMode === 'edit' && '编辑时留空表示保留原凭证；填入则覆盖。'}
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
            </div>
            <div>
              <label className="field-label">优先级</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.priority}
                inputMode="numeric"
                onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              />
            </div>
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
