'use client';

import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useUnifiedModels,
  useVendors,
  useGroupOptions,
  useShelveModel,
  useUnshelveModel,
  type UnifiedModelVM,
  type VendorVM,
} from '../model/model-admin.model';
import styles from './ModelsAdminPage.module.css';

/* ════════════════════════════ 内联 SVG 图标 ════════════════════════════ */
function IcInfo() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx={12} cy={12} r={9} /><path d="M12 16v-4" /><path d="M12 8h.01" />
    </svg>
  );
}

/* ════════════════════════════ 小展示组件 ════════════════════════════ */
function GroupsCell({ groups }: { groups: { name: string; ratio: number }[] }) {
  if (!groups.length) return <span className="muted">—</span>;
  return (
    <div className={styles.caps}>
      {groups.map((g) => (
        <span key={g.name} className={styles.cap}>{g.name} ×{Number(g.ratio).toFixed(2)}</span>
      ))}
    </div>
  );
}

/** 广场上架状态徽章。 */
function SquareBadge({ on, enabled }: { on: boolean; enabled: boolean }) {
  if (!on) {
    return (
      <span className="badge b-dan">
        <span className="dot" style={{ background: 'var(--color-text-muted)' }} />未上架
      </span>
    );
  }
  return enabled ? (
    <span className="badge b-suc">
      <span className="dot" style={{ background: 'var(--color-success)' }} />已上架
    </span>
  ) : (
    <span className="badge b-warn">
      <span className="dot" style={{ background: 'var(--color-warning)' }} />已下架
    </span>
  );
}

function VendorStateBadge({ on }: { on: boolean }) {
  return on ? (
    <span className="badge b-suc">
      <span className="dot" style={{ background: 'var(--color-success)' }} />上架
    </span>
  ) : (
    <span className="badge b-dan">
      <span className="dot" style={{ background: 'var(--color-danger)' }} />下架
    </span>
  );
}

/** 统一占位行（加载/错误/空）。 */
function StateRow({ colSpan, loading, error, empty }: {
  colSpan: number;
  loading: boolean;
  error: unknown;
  empty: boolean;
}) {
  let text = '';
  if (loading) text = '加载中…';
  else if (error) text = `加载失败：${error instanceof ApiError ? error.message : '请稍后重试'}`;
  else if (empty) text = '暂无数据';
  if (!text) return null;
  return (
    <tr>
      <td colSpan={colSpan} className={styles.emptyCell}>{text}</td>
    </tr>
  );
}

/* ════════════════════════════ 主组件 ════════════════════════════ */
type TabKey = 'models' | 'vendors';

interface ShelveForm {
  publicName: string;
  displayName: string;
  basePriceRatio: string;
  description: string;
  enabled: boolean;
  groupIds: number[];
}

/**
 * ModelsAdminPage — 模型管理（以底层真实模型为主体的统一视图）。
 *
 * 主「模型」Tab：一行一个真实模型，显示广场上架状态 / 基准价 / 所在价格分组。
 * 未上架 → 一键「上架到广场」；已上架 → 「管理」编辑价/描述/分组、「下架」。
 * 次「供应商」Tab：只读供应商元数据。
 * 上架编排：createPublicModel + 同步该模型在各价格分组的归属（updateModelGroup）。
 */
export function ModelsAdminPage() {
  const [tab, setTab] = useState<TabKey>('models');

  const modelsQuery = useUnifiedModels();
  const vendorsQuery = useVendors();
  const shelveMut = useShelveModel();
  const unshelveMut = useUnshelveModel();

  /* 模型筛选 */
  const [fVendor, setFVendor] = useState('');
  const [fState, setFState] = useState('');
  const [fSearch, setFSearch] = useState('');

  /* 供应商筛选 */
  const [vSearch, setVSearch] = useState('');

  /* 上架/管理抽屉 */
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<UnifiedModelVM | null>(null);
  const [form, setForm] = useState<ShelveForm>({
    publicName: '', displayName: '', basePriceRatio: '1', description: '', enabled: true, groupIds: [],
  });
  const [formErr, setFormErr] = useState<string | null>(null);
  const groupOptsQuery = useGroupOptions(drawerOpen ? (editing?.name ?? null) : null);

  const models: UnifiedModelVM[] = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const vendors: VendorVM[] = useMemo(() => vendorsQuery.data ?? [], [vendorsQuery.data]);

  const vendorOptions = useMemo(
    () => Array.from(new Set(models.map((m) => m.vendor).filter((v) => v !== '—'))),
    [models],
  );

  const filteredModels = useMemo(() => {
    return models.filter((m) => {
      if (fVendor && m.vendor !== fVendor) return false;
      if (fState) {
        const lab = !m.onSquare ? '未上架' : m.enabled ? '已上架' : '已下架';
        if (lab !== fState) return false;
      }
      if (fSearch && !m.name.toLowerCase().includes(fSearch.toLowerCase())) return false;
      return true;
    });
  }, [models, fVendor, fState, fSearch]);

  const filteredVendors = useMemo(
    () => vendors.filter((v) => !vSearch || v.nm.toLowerCase().includes(vSearch.toLowerCase())),
    [vendors, vSearch],
  );

  /* ── 抽屉开关 ── */
  function openShelve(m: UnifiedModelVM) {
    setEditing(m);
    setFormErr(null);
    setForm({
      publicName: m.name,
      displayName: m.displayName ?? '',
      basePriceRatio: m.basePriceRatio != null ? String(m.basePriceRatio) : '1',
      description: m.description ?? '',
      enabled: m.onSquare ? m.enabled : true,
      groupIds: m.groups.map((g) => g.id),
    });
    setDrawerOpen(true);
  }
  function closeDrawer() { setDrawerOpen(false); }

  function toggleGroup(id: number) {
    setForm((f) => ({
      ...f,
      groupIds: f.groupIds.includes(id) ? f.groupIds.filter((x) => x !== id) : [...f.groupIds, id],
    }));
  }

  async function handleSave() {
    setFormErr(null);
    const name = form.publicName.trim();
    if (!name) { setFormErr('对外名 A 必填'); return; }
    const ratio = Number(form.basePriceRatio);
    if (Number.isNaN(ratio) || ratio < 0) { setFormErr('基准价倍率非法'); return; }
    try {
      await shelveMut.mutateAsync({
        publicModelId: editing?.publicModelId,
        publicName: name,
        displayName: form.displayName.trim() || undefined,
        basePriceRatio: ratio,
        description: form.description.trim() || undefined,
        enabled: form.enabled,
        groupIds: form.groupIds,
      });
      setDrawerOpen(false);
    } catch (e) {
      setFormErr(e instanceof ApiError ? e.message : '保存失败，请稍后重试');
    }
  }

  async function handleUnshelve(m: UnifiedModelVM) {
    if (m.publicModelId == null) return;
    await unshelveMut.mutateAsync(m.publicModelId);
  }

  const saving = shelveMut.isPending;

  return (
    <AppShell
      activeId="models"
      title="模型"
      crumb={['管理后台', '资源管理', '模型']}
    >
      {/* Tab */}
      <div className={styles.tabs}>
        <button className={`${styles.tab}${tab === 'models' ? ' ' + styles.on : ''}`} onClick={() => setTab('models')}>模型</button>
        <button className={`${styles.tab}${tab === 'vendors' ? ' ' + styles.on : ''}`} onClick={() => setTab('vendors')}>供应商</button>
      </div>
      {/* ════ 模型 Tab ════ */}
      {tab === 'models' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              下方为全部底层模型。<b>上架到广场</b>即生成对客户售卖的商品；把模型<b>加入不同价格分组</b>可形成不同售价（售价 = 基准价倍率 × 分组倍率）。分组在「价格分组」页管理。
            </span>
          </section>

          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={fVendor} onChange={(e) => setFVendor(e.target.value)}>
              <option value="">全部供应商</option>
              {vendorOptions.map((v) => <option key={v}>{v}</option>)}
            </select>
            <select className={styles.sel} value={fState} onChange={(e) => setFState(e.target.value)}>
              <option value="">全部状态</option>
              <option>已上架</option>
              <option>已下架</option>
              <option>未上架</option>
            </select>
            <input className={styles.srch} type="search" placeholder="搜索模型名"
              value={fSearch} onChange={(e) => setFSearch(e.target.value)} />
            <span className={styles.grow} />
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={`${styles.tableWrap} ${styles.tableWrapWide}`}>
              <table>
                <thead>
                  <tr>
                    <th>模型</th><th>供应商</th><th>广场状态</th>
                    <th>基准价倍率</th><th>所在价格分组</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <StateRow colSpan={6} loading={modelsQuery.isLoading} error={modelsQuery.error} empty={filteredModels.length === 0} />
                  {filteredModels.map((m) => (
                    <tr key={m.name}>
                      <td className={styles.cellmono}>
                        {m.name}
                        {m.displayName ? <div className={`muted ${styles.dispSub}`}>{m.displayName}</div> : null}
                      </td>
                      <td className="muted">{m.vendor}</td>
                      <td><SquareBadge on={m.onSquare} enabled={m.enabled} /></td>
                      <td className={styles.cellmono}>{m.basePriceRatio != null ? `×${m.basePriceRatio.toFixed(2)}` : '—'}</td>
                      <td><GroupsCell groups={m.groups} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          {m.onSquare ? (
                            <>
                              <a onClick={() => openShelve(m)}>管理</a>
                              {m.enabled
                                ? <a className={styles.dang} onClick={() => handleUnshelve(m)}>下架</a>
                                : <a onClick={() => openShelve(m)}>重新上架</a>}
                            </>
                          ) : (
                            <a onClick={() => openShelve(m)}>＋上架到广场</a>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {filteredModels.length} 个模型 · 已上架 {models.filter((m) => m.onSquare && m.enabled).length}</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 供应商 Tab ════ */}
      {tab === 'vendors' && (
        <div>
          <section className={`${styles.toolbar} nx-fade`}>
            <input className={styles.srch} type="search" placeholder="搜索供应商名"
              value={vSearch} onChange={(e) => setVSearch(e.target.value)} />
            <span className={styles.grow} />
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr><th>供应商名称</th><th>图标</th><th>状态</th></tr>
                </thead>
                <tbody>
                  <StateRow colSpan={3} loading={vendorsQuery.isLoading} error={vendorsQuery.error} empty={filteredVendors.length === 0} />
                  {filteredVendors.map((v) => (
                    <tr key={v.id}>
                      <td>{v.nm}</td>
                      <td className={`${styles.cellmono} muted`}>{v.icon || '—'}</td>
                      <td><VendorStateBadge on={v.st === 'on'} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {filteredVendors.length} 个供应商</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 上架/管理 抽屉 ════ */}
      <div className={`${styles.drawerScrim}${drawerOpen ? ' ' + styles.on : ''}`} onClick={closeDrawer} />
      <aside className={`${styles.drawer}${drawerOpen ? ' ' + styles.on : ''}`} aria-label="模型上架管理">
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{editing?.onSquare ? '管理模型' : '上架到广场'}</h2>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">对外名 A <span className="field-req">*</span></label>
            <input
              className={`input ${styles.cellmono}`}
              value={form.publicName}
              disabled={editing?.onSquare}
              onChange={(e) => setForm((f) => ({ ...f, publicName: e.target.value }))}
            />
            {editing?.onSquare && <div className={styles.fieldHint}>对外名 A 建后不可改</div>}
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">展示名</label>
            <input
              className="input"
              placeholder="客户看到的名称，留空用对外名"
              value={form.displayName}
              onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
            />
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">基准价倍率</label>
            <input
              className={`input ${styles.cellmono}`}
              value={form.basePriceRatio}
              inputMode="decimal"
              onChange={(e) => setForm((f) => ({ ...f, basePriceRatio: e.target.value }))}
            />
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">描述</label>
            <input
              className="input"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            />
          </div>
          <div style={{ marginTop: 'var(--space-4)' }}>
            <label className="field-label">
              加入价格分组 <span className="muted">（已选 {form.groupIds.length}）</span>
            </label>
            {groupOptsQuery.isLoading ? (
              <div className={styles.fieldHint}>加载分组…</div>
            ) : (groupOptsQuery.data ?? []).length === 0 ? (
              <div className={styles.fieldHint}>暂无价格分组，请先到「价格分组」页创建。</div>
            ) : (
              <div className={styles.groupPicker}>
                {(groupOptsQuery.data ?? []).map((g) => {
                  const checked = form.groupIds.includes(g.id);
                  return (
                    <label key={g.id} className={`${styles.groupOpt}${checked ? ' ' + styles.groupOptOn : ''}`}>
                      <input type="checkbox" checked={checked} onChange={() => toggleGroup(g.id)} />
                      <span>{g.name}</span>
                      <span className={`${styles.cellmono} muted`}>×{Number(g.ratio).toFixed(2)}</span>
                    </label>
                  );
                })}
              </div>
            )}
          </div>
          <div className={styles.swRow} style={{ marginTop: 'var(--space-4)' }}>
            <label className="field-label" style={{ margin: 0 }}>上架（对客户可见可用）</label>
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
          {formErr && (
            <div className={styles.fieldHint} style={{ marginTop: 'var(--space-3)', color: 'var(--color-danger)' }}>
              {formErr}
            </div>
          )}
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>取消</Button>
          <Button variant="primary" onClick={handleSave} disabled={saving}>
            {saving ? '保存中…' : (editing?.onSquare ? '保存' : '上架')}
          </Button>
        </div>
      </aside>
    </AppShell>
  );
}

