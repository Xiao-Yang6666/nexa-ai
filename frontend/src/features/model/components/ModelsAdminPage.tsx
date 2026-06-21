'use client';

import { useMemo, useState } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  usePublicModels,
  useChannelCosts,
  useModelMetas,
  useVendors,
  useMissingModels,
  useModelSyncPreview,
  useModelSyncExecute,
  useCreatePublicModel,
  useUpdatePublicModel,
  useDeletePublicModel,
  TIER_LABEL,
  MODEL_STATE_MAP,
  type PublicModelVM,
  type CostGroupVM,
  type ModelMetaVM,
  type VendorVM,
  type ModelState,
  type Tier,
} from '../model/model-admin.model';
import styles from './ModelsAdminPage.module.css';

/* ════════════════════════════ 内联 SVG 图标 ════════════════════════════ */
function IcSearch() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx={11} cy={11} r={7} /><path d="m20 20-3.2-3.2" />
    </svg>
  );
}
function IcInfo() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx={12} cy={12} r={9} /><path d="M12 16v-4" /><path d="M12 8h.01" />
    </svg>
  );
}
function IcArrow() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 12h14" /><path d="M13 6l6 6-6 6" />
    </svg>
  );
}

/* ════════════════════════════ 小展示组件 ════════════════════════════ */
const TIER_CLS: Record<string, string> = {
  full: styles.tierFull,
  max: styles.tierEnh,
  air: styles.tierEco,
};

function TierBadge({ t }: { t: string }) {
  return (
    <span className={`${styles.tier} ${TIER_CLS[t] ?? ''}`}>
      <span className={styles.dot} />
      {TIER_LABEL[t] ?? t}
    </span>
  );
}

function MapCell({ b, count }: { b: string; count: number }) {
  if (!b) return <span className={styles.costWarn}>未映射 B</span>;
  return (
    <div className={styles.maprow}>
      <span className={styles.mapGlyph}><IcArrow /></span>
      <span className={styles.mapb}>{b}</span>
      {count > 1 && <span className={styles.privTag}>+{count - 1} 个 B</span>}
    </div>
  );
}

function PoolCell({ count, main }: { count: number; main: string }) {
  if (!count) return <span className={styles.costWarn}>未绑渠道</span>;
  return (
    <div className={styles.chspool}>
      <span className={styles.chsCnt}>{count} 渠道</span>
      {main && <span className={styles.chsMain}>主·{main}</span>}
    </div>
  );
}

function PubStateBadge({ on }: { on: boolean }) {
  return on ? (
    <span className="badge b-suc">
      <span className="dot" style={{ background: 'var(--color-success)' }} />上架
    </span>
  ) : (
    <span className="badge b-dan">
      <span className="dot" style={{ background: 'var(--color-text-muted)' }} />下架
    </span>
  );
}

function StateBadge({ st }: { st: ModelState }) {
  const m = MODEL_STATE_MAP[st];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: `var(${m.tone})` }} />{m.lab}
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

function Caps({ arr }: { arr: string[] }) {
  if (!arr.length) return <span className="muted">—</span>;
  return (
    <div className={styles.caps}>
      {arr.map((c) => <span key={c} className={styles.cap}>{c}</span>)}
    </div>
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
type TabKey = 'public' | 'costs' | 'models' | 'vendors';

/**
 * ModelsAdminPage — 模型/供应商管理（S6 admin/models-admin.html 工程化，已接真实接口）。
 * 四 Tab：对外模型 / 供应商成本 / 模型元数据 / 供应商元数据。
 * 数据源：GET /api/public_models（+ platform_model_mappings + channel/pool）、
 * /api/channel_model_costs、/api/models（+ /api/vendors join）、/api/vendors。
 * 缺失检测 GET /api/models/missing、上游同步预览 POST /api/models/sync/preview。
 */
export function ModelsAdminPage() {
  const [tab, setTab] = useState<TabKey>('public');

  /* 各 Tab 数据 */
  const pubsQuery = usePublicModels();
  const costsQuery = useChannelCosts();
  const metasQuery = useModelMetas();
  const vendorsQuery = useVendors();

  /* 缺失检测 + 同步预览/执行 */
  const missingMutation = useMissingModels();
  const syncMutation = useModelSyncPreview();
  const syncExecMutation = useModelSyncExecute();
  const [syncOpen, setSyncOpen] = useState(false);

  /* 对外模型写操作 */
  const createPub = useCreatePublicModel();
  const updatePub = useUpdatePublicModel();
  const deletePub = useDeletePublicModel();

  /* 对外模型编辑抽屉 */
  const [pubDrawerOpen, setPubDrawerOpen] = useState(false);
  const [pubEditId, setPubEditId] = useState<number | null>(null);
  const [pubForm, setPubForm] = useState({
    public_name: '',
    display_name: '',
    quality_tier: 'air' as Tier,
    base_price_ratio: '1',
    sort_order: '0',
    description: '',
    enabled: true,
  });
  const [pubErr, setPubErr] = useState<string | null>(null);

  /* 对外模型筛选 */
  const [pTier, setPTier] = useState('');
  const [pState, setPState] = useState('');
  const [pSearch, setPSearch] = useState('');

  /* 供应商成本筛选 */
  const [cModel, setCModel] = useState('');
  const [cUnset, setCUnset] = useState(false);

  /* 模型元数据筛选 */
  const [fVendor, setFVendor] = useState('');
  const [fState, setFState] = useState('');
  const [fSearch, setFSearch] = useState('');

  /* 供应商元数据筛选 */
  const [vSearch, setVSearch] = useState('');

  const pubs: PublicModelVM[] = useMemo(() => pubsQuery.data ?? [], [pubsQuery.data]);
  const costGroups: CostGroupVM[] = useMemo(() => costsQuery.data ?? [], [costsQuery.data]);
  const metas: ModelMetaVM[] = useMemo(() => metasQuery.data ?? [], [metasQuery.data]);
  const vendors: VendorVM[] = useMemo(() => vendorsQuery.data ?? [], [vendorsQuery.data]);

  /* ── 对外模型筛选 ── */
  const filteredPubs = useMemo(() => {
    return pubs.filter((r) => {
      if (pTier && r.tier !== pTier) return false;
      if (pState) {
        const lab = r.on ? '上架' : '下架';
        if (lab !== pState) return false;
      }
      if (pSearch) {
        const q = pSearch.toLowerCase();
        if (!r.a.toLowerCase().includes(q) && !r.disp.toLowerCase().includes(q)) return false;
      }
      return true;
    });
  }, [pubs, pTier, pState, pSearch]);

  /* ── 供应商成本筛选 ── */
  const filteredCosts = useMemo(() => {
    return costGroups
      .filter((g) => !cModel || g.b === cModel)
      .map((g) => ({ ...g, rows: g.rows.filter((r) => !cUnset || r.unset) }))
      .filter((g) => g.rows.length > 0);
  }, [costGroups, cModel, cUnset]);

  /* ── 模型元数据筛选 ── */
  const filteredModels = useMemo(() => {
    return metas.filter((r) => {
      if (fVendor && r.ven !== fVendor) return false;
      if (fState && MODEL_STATE_MAP[r.st].lab !== fState) return false;
      if (fSearch && !r.nm.toLowerCase().includes(fSearch.toLowerCase())) return false;
      return true;
    });
  }, [metas, fVendor, fState, fSearch]);

  /* ── 供应商元数据筛选 ── */
  const filteredVendors = useMemo(() => {
    return vendors.filter((v) => !vSearch || v.nm.toLowerCase().includes(vSearch.toLowerCase()));
  }, [vendors, vSearch]);

  /* 动态下拉项 */
  const bOptions = useMemo(() => costGroups.map((g) => g.b), [costGroups]);
  const vendorOptions = useMemo(() => Array.from(new Set(metas.map((m) => m.ven).filter((v) => v !== '—'))), [metas]);

  const totalCostRows = filteredCosts.reduce((s, g) => s + g.rows.length, 0);
  const syncDiff = syncMutation.data?.diff;

  async function handleDetect() {
    await missingMutation.mutateAsync();
  }
  async function handleSync() {
    setSyncOpen(true);
    if (!syncMutation.data) await syncMutation.mutateAsync(undefined);
  }
  async function handleSyncExecute() {
    await syncExecMutation.mutateAsync({});
    setSyncOpen(false);
  }

  /* ── 对外模型抽屉 ── */
  function openPubNew() {
    setPubEditId(null);
    setPubErr(null);
    setPubForm({ public_name: '', display_name: '', quality_tier: 'air', base_price_ratio: '1', sort_order: '0', description: '', enabled: true });
    setPubDrawerOpen(true);
  }
  function openPubEdit(r: PublicModelVM) {
    setPubEditId(r.id);
    setPubErr(null);
    setPubForm({
      public_name: r.a,
      display_name: r.disp,
      quality_tier: (r.tier as Tier) || 'air',
      base_price_ratio: String(r.priceRatioNum),
      sort_order: String(r.sortOrder),
      description: r.description,
      enabled: r.on,
    });
    setPubDrawerOpen(true);
  }
  async function handlePubSave() {
    setPubErr(null);
    const a = pubForm.public_name.trim();
    if (!a) { setPubErr('对外名 A 必填'); return; }
    const ratio = Number(pubForm.base_price_ratio);
    if (Number.isNaN(ratio) || ratio < 0) { setPubErr('基准价倍率非法'); return; }
    const sort = Number(pubForm.sort_order) || 0;
    try {
      if (pubEditId == null) {
        await createPub.mutateAsync({
          public_name: a,
          display_name: pubForm.display_name.trim() || undefined,
          quality_tier: pubForm.quality_tier,
          base_price_ratio: ratio,
          sort_order: sort,
          description: pubForm.description.trim() || undefined,
          enabled: pubForm.enabled,
        });
      } else {
        // A 不可改，更新不传 public_name
        await updatePub.mutateAsync({
          id: pubEditId,
          quality_tier: pubForm.quality_tier,
          base_price_ratio: ratio,
          sort_order: sort,
          display_name: pubForm.display_name.trim() || undefined,
          enabled: pubForm.enabled,
        });
      }
      setPubDrawerOpen(false);
    } catch (e) {
      setPubErr(e instanceof ApiError ? e.message : '保存失败，请稍后重试');
    }
  }
  async function handlePubToggle(r: PublicModelVM) {
    await updatePub.mutateAsync({ id: r.id, enabled: !r.on });
  }
  async function handlePubDelete(r: PublicModelVM) {
    await deletePub.mutateAsync(r.id);
  }
  const pubSaving = createPub.isPending || updatePub.isPending;


  return (
    <AdminShell
      activeId="models"
      title="模型/供应商"
      crumb={['管理后台', '资源管理', '模型/供应商']}
      actions={
        <>
          <Button variant="sec" size="sm" disabled={missingMutation.isPending} onClick={handleDetect}>
            {missingMutation.isPending ? '检测中…' : '缺失模型检测'}
          </Button>
          <Button variant="primary" size="sm" onClick={handleSync}>上游模型同步</Button>
        </>
      }
    >
      {/* 检测结果条 */}
      {missingMutation.data && (
        <section className={styles.detectBar}>
          <IcSearch />
          <span className={styles.txt}>
            {missingMutation.data.length > 0
              ? <>缺失检测完成：上游存在 <b>{missingMutation.data.length}</b> 个本地未登记的模型（{missingMutation.data.slice(0, 6).join('、')}{missingMutation.data.length > 6 ? '…' : ''}）。建议执行同步对齐。</>
              : <>缺失检测完成：本地模型与上游已对齐，无缺失。</>}
          </span>
          <span className={styles.grow} />
          <Button variant="sec" size="sm" onClick={() => missingMutation.reset()}>关闭</Button>
        </section>
      )}

      {/* Tab */}
      <div className={styles.tabs}>
        <button className={`${styles.tab}${tab === 'public' ? ' ' + styles.on : ''}`} onClick={() => setTab('public')}>对外模型</button>
        <button className={`${styles.tab}${tab === 'costs' ? ' ' + styles.on : ''}`} onClick={() => setTab('costs')}>供应商成本</button>
        <button className={`${styles.tab}${tab === 'models' ? ' ' + styles.on : ''}`} onClick={() => setTab('models')}>模型元数据</button>
        <button className={`${styles.tab}${tab === 'vendors' ? ' ' + styles.on : ''}`} onClick={() => setTab('vendors')}>供应商元数据</button>
      </div>

      {/* ════ 对外模型 Tab ════ */}
      {tab === 'public' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              对外模型即对客户售卖的商品（公开名 <b>A</b>）。基准售价对所有客户恒定；底仓映射 <b>A 到 B</b> 仅平台内部可见，<b>客户永不可见 B</b>。
            </span>
          </section>

          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={pTier} onChange={(e) => setPTier(e.target.value)}>
              <option value="">全部品质</option>
              <option value="full">旗舰</option>
              <option value="max">增强</option>
              <option value="air">经济</option>
            </select>
            <select className={styles.sel} value={pState} onChange={(e) => setPState(e.target.value)}>
              <option value="">全部状态</option>
              <option>上架</option>
              <option>下架</option>
            </select>
            <input className={styles.srch} type="search" placeholder="搜索对外名 / 展示名"
              value={pSearch} onChange={(e) => setPSearch(e.target.value)} />
            <span className={styles.grow} />
            <Button variant="primary" size="sm" onClick={openPubNew}>新建对外模型</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={`${styles.tableWrap} ${styles.tableWrapWide}`}>
              <table>
                <thead>
                  <tr>
                    <th>对外名 (A)</th><th>品质档</th>
                    <th>基准售价倍率</th><th>底仓映射 A 到 B（B 不可见）</th>
                    <th>供应渠道池</th><th>状态</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <StateRow colSpan={7} loading={pubsQuery.isLoading} error={pubsQuery.error} empty={filteredPubs.length === 0} />
                  {filteredPubs.map((r) => (
                    <tr key={r.id}>
                      <td className={styles.cellmono}>
                        {r.a}
                        <div className={`muted ${styles.dispSub}`}>{r.disp}</div>
                      </td>
                      <td><TierBadge t={r.tier} /></td>
                      <td className={styles.cellmono}>{r.priceRatio}</td>
                      <td><MapCell b={r.b} count={r.bCount} /></td>
                      <td><PoolCell count={r.poolCount} main={r.poolMain} /></td>
                      <td><PubStateBadge on={r.on} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => openPubEdit(r)}>编辑</a>
                          <a onClick={() => handlePubToggle(r)}>{r.on ? '下架' : '上架'}</a>
                          <a className={styles.dang} onClick={() => handlePubDelete(r)}>删除</a>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {filteredPubs.length} 个对外模型</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 供应商成本 Tab ════ */}
      {tab === 'costs' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              成本倍率挂在「供应商渠道 × 真实模型 <b>B</b>」上，<b>仅平台内部、手动填写</b>。它只影响利润计算，<b>不影响对客户售价</b>。下方按 B 分组呈现各渠道成本差异。
            </span>
          </section>

          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={cModel} onChange={(e) => setCModel(e.target.value)}>
              <option value="">全部真实模型 B</option>
              {bOptions.map((b) => <option key={b} value={b}>{b}</option>)}
            </select>
            <label className={styles.swInline}>
              <input type="checkbox" checked={cUnset} onChange={(e) => setCUnset(e.target.checked)} />
              <span>仅看未配成本</span>
            </label>
            <span className={styles.grow} />
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            {costsQuery.isLoading ? (
              <div className={styles.emptyCell}>加载中…</div>
            ) : costsQuery.isError ? (
              <div className={styles.emptyCell}>
                加载失败：{costsQuery.error instanceof ApiError ? costsQuery.error.message : '请稍后重试'}
              </div>
            ) : filteredCosts.length === 0 ? (
              <div className={styles.emptyCell}>暂无成本配置</div>
            ) : (
              filteredCosts.map((g) => {
                const nums = g.rows.filter((r) => !r.unset && r.costNum != null).map((r) => r.costNum as number);
                let spread: React.ReactNode;
                if (nums.length > 1) {
                  const mn = Math.min(...nums);
                  const mx = Math.max(...nums);
                  spread = <>成本极差 <b>{mn.toFixed(2)}</b> ~ <b>{mx.toFixed(2)}</b>（{(mx / Math.max(mn, 0.0001)).toFixed(1)}x 差异）</>;
                } else {
                  spread = '待补全更多供应商成本';
                }
                return (
                  <div key={g.b} className={styles.costGrp}>
                    <div className={styles.costGrphead}>
                      <span className={styles.bname}>{g.b}</span>
                      <span className={styles.privTag}>真实模型 B · 客户不可见</span>
                      <span className={styles.spread}>{spread}</span>
                    </div>
                    <div className={styles.tableWrap}>
                      <table>
                        <thead>
                          <tr>
                            <th>渠道 ID</th><th>成本倍率</th><th>备注</th><th>最后更新</th><th>状态</th>
                          </tr>
                        </thead>
                        <tbody>
                          {g.rows.map((r) => (
                            <tr key={r.id}>
                              <td className={styles.cellmono}>#{r.channelId}</td>
                              <td className={styles.cellmono}>{r.unset ? '—' : `×${r.cost}`}</td>
                              <td className="muted">{r.remark || '—'}</td>
                              <td className="muted">{r.upd}</td>
                              <td>
                                {r.unset ? (
                                  <span className={styles.costWarn}>未配成本，利润无法计算</span>
                                ) : r.on ? (
                                  <span className="badge b-suc">
                                    <span className="dot" style={{ background: 'var(--color-success)' }} />已配
                                  </span>
                                ) : (
                                  <span className="badge b-info">
                                    <span className="dot" style={{ background: 'var(--color-info)' }} />已停用
                                  </span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                );
              })
            )}
            <div className={styles.pager}>
              <span>共 {filteredCosts.length} 个真实模型 B、{totalCostRows} 条渠道成本配置</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 模型元数据 Tab ════ */}
      {tab === 'models' && (
        <div>
          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={fVendor} onChange={(e) => setFVendor(e.target.value)}>
              <option value="">全部供应商</option>
              {vendorOptions.map((v) => <option key={v}>{v}</option>)}
            </select>
            <select className={styles.sel} value={fState} onChange={(e) => setFState(e.target.value)}>
              <option value="">全部状态</option>
              <option>上架</option>
              <option>下架</option>
              <option>预发布</option>
            </select>
            <input className={styles.srch} type="search" placeholder="搜索模型名"
              value={fSearch} onChange={(e) => setFSearch(e.target.value)} />
            <span className={styles.grow} />
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>模型名</th><th>供应商</th><th>状态</th><th>端点</th><th>标签</th>
                  </tr>
                </thead>
                <tbody>
                  <StateRow colSpan={5} loading={metasQuery.isLoading} error={metasQuery.error} empty={filteredModels.length === 0} />
                  {filteredModels.map((r) => (
                    <tr key={r.id}>
                      <td className={styles.cellmono}>{r.nm}</td>
                      <td className="muted">{r.ven}</td>
                      <td><StateBadge st={r.st} /></td>
                      <td className={`${styles.cellmono} muted`}>{r.endpoints || '—'}</td>
                      <td><Caps arr={r.tags} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {filteredModels.length} 个模型</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 供应商元数据 Tab ════ */}
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

      {/* ════ 对外模型 编辑/新建 抽屉 ════ */}
      <div className={`${styles.drawerScrim}${pubDrawerOpen ? ' ' + styles.on : ''}`} onClick={() => setPubDrawerOpen(false)} />
      <aside className={`${styles.drawer}${pubDrawerOpen ? ' ' + styles.on : ''}`} aria-label="对外模型编辑">
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{pubEditId == null ? '新建对外模型' : '编辑对外模型'}</h2>
          <button className={styles.drawerX} onClick={() => setPubDrawerOpen(false)} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">对外名 A <span className="field-req">*</span></label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="例如：opus-4.8-经济"
              value={pubForm.public_name}
              disabled={pubEditId != null}
              onChange={(e) => setPubForm((f) => ({ ...f, public_name: e.target.value }))}
            />
            {pubEditId != null && <div className={styles.fieldHint}>对外名 A 建后不可改</div>}
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">展示名</label>
            <input
              className="input"
              placeholder="例如：Claude Opus 4.8 经济版"
              value={pubForm.display_name}
              onChange={(e) => setPubForm((f) => ({ ...f, display_name: e.target.value }))}
            />
          </div>
          <div className={styles.row2} style={{ marginTop: 'var(--space-3)' }}>
            <div>
              <label className="field-label">品质档</label>
              <select
                className="input"
                value={pubForm.quality_tier}
                onChange={(e) => setPubForm((f) => ({ ...f, quality_tier: e.target.value as Tier }))}
              >
                <option value="full">旗舰</option>
                <option value="max">增强</option>
                <option value="air">经济</option>
              </select>
            </div>
            <div>
              <label className="field-label">基准价倍率</label>
              <input
                className={`input ${styles.cellmono}`}
                value={pubForm.base_price_ratio}
                inputMode="decimal"
                onChange={(e) => setPubForm((f) => ({ ...f, base_price_ratio: e.target.value }))}
              />
            </div>
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">排序</label>
            <input
              className={`input ${styles.cellmono}`}
              value={pubForm.sort_order}
              inputMode="numeric"
              onChange={(e) => setPubForm((f) => ({ ...f, sort_order: e.target.value }))}
            />
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">描述</label>
            <input
              className="input"
              value={pubForm.description}
              onChange={(e) => setPubForm((f) => ({ ...f, description: e.target.value }))}
            />
          </div>
          <div className={styles.swRow} style={{ marginTop: 'var(--space-4)' }}>
            <label className="field-label" style={{ margin: 0 }}>上架（对所有客户可见可用）</label>
            <label className="switch">
              <input
                type="checkbox"
                checked={pubForm.enabled}
                onChange={(e) => setPubForm((f) => ({ ...f, enabled: e.target.checked }))}
              />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
          <div className={styles.fieldHint} style={{ marginTop: 'var(--space-3)' }}>
            底仓映射 A→B 与供应渠道池在「平台映射 / 渠道池」中单独维护，此处仅管理对外商品本身。
          </div>
          {pubErr && (
            <div className={styles.fieldHint} style={{ marginTop: 'var(--space-3)', color: 'var(--color-danger)' }}>
              {pubErr}
            </div>
          )}
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={() => setPubDrawerOpen(false)}>取消</Button>
          <Button variant="primary" onClick={handlePubSave} disabled={pubSaving}>
            {pubSaving ? '保存中…' : '保存'}
          </Button>
        </div>
      </aside>

      {/* ════ 同步预览 Modal ════ */}
      <div className={`${styles.modalScrim}${syncOpen ? ' ' + styles.on : ''}`}
        onClick={(e) => { if (e.target === e.currentTarget) setSyncOpen(false); }}>
        <div className={styles.modal} role="dialog" aria-label="上游模型同步预览">
          <div className={styles.modalHead}>
            <h2 className={styles.modalTitle}>上游模型同步预览</h2>
            <button className={styles.modalX} onClick={() => setSyncOpen(false)} aria-label="关闭">×</button>
          </div>
          <div className={styles.modalBody}>
            {syncMutation.isPending ? (
              <div className={styles.emptyCell}>加载差异中…</div>
            ) : syncMutation.isError ? (
              <div className={styles.emptyCell}>
                预览失败：{syncMutation.error instanceof ApiError ? syncMutation.error.message : '请稍后重试'}
              </div>
            ) : syncDiff ? (
              <>
                <div className={styles.syncSec}>
                  <h5><span className="dot" style={{ background: 'var(--color-success)' }} />将新增模型（{syncDiff.to_create_models?.length ?? 0}）</h5>
                  <div className={styles.syncList}>
                    {(syncDiff.to_create_models ?? []).map((s) => (
                      <div key={s} className={styles.syncItem}>
                        <span>{s}</span><span className={`${styles.tag} ${styles.tagAdd}`}>NEW</span>
                      </div>
                    ))}
                    {!(syncDiff.to_create_models?.length) && <div className={styles.emptyCell}>无</div>}
                  </div>
                </div>
                <div className={styles.syncSec}>
                  <h5><span className="dot" style={{ background: 'var(--color-info)' }} />将更新（{syncDiff.to_update_models?.length ?? 0}）</h5>
                  <div className={styles.syncList}>
                    {(syncDiff.to_update_models ?? []).map((s) => (
                      <div key={s} className={styles.syncItem}>
                        <span>{s}</span><span className={`${styles.tag} ${styles.tagUpd}`}>UPD</span>
                      </div>
                    ))}
                    {!(syncDiff.to_update_models?.length) && <div className={styles.emptyCell}>无</div>}
                  </div>
                </div>
                <div className={styles.syncSec}>
                  <h5><span className="dot" style={{ background: 'var(--color-warning)' }} />将跳过（{syncDiff.to_skip_models?.length ?? 0}）</h5>
                  <div className={styles.syncList}>
                    {(syncDiff.to_skip_models ?? []).map((s) => (
                      <div key={s} className={styles.syncItem}>
                        <span>{s}</span><span className={`${styles.tag} ${styles.tagMiss}`}>SKIP</span>
                      </div>
                    ))}
                    {!(syncDiff.to_skip_models?.length) && <div className={styles.emptyCell}>无</div>}
                  </div>
                </div>
              </>
            ) : (
              <div className={styles.emptyCell}>暂无差异数据</div>
            )}
          </div>
          <div className={styles.modalFoot}>
            {syncExecMutation.isError && (
              <span className="field-hint" style={{ color: 'var(--color-danger)', marginRight: 'auto' }}>
                {syncExecMutation.error instanceof ApiError ? syncExecMutation.error.message : '同步失败'}
              </span>
            )}
            {syncExecMutation.isSuccess && (
              <span className="field-hint" style={{ color: 'var(--color-success)', marginRight: 'auto' }}>
                同步完成：新增 {syncExecMutation.data.created_models ?? 0} 模型 / {syncExecMutation.data.created_vendors ?? 0} 供应商，更新 {syncExecMutation.data.updated_models ?? 0}，跳过 {syncExecMutation.data.skipped_models ?? 0}。
              </span>
            )}
            <Button variant="ghost" onClick={() => setSyncOpen(false)}>关闭</Button>
            <Button
              variant="primary"
              onClick={handleSyncExecute}
              disabled={syncExecMutation.isPending || syncMutation.isPending || !syncDiff}
            >
              {syncExecMutation.isPending ? '同步中…' : '确认执行同步'}
            </Button>
          </div>
        </div>
      </div>
    </AdminShell>
  );
}
