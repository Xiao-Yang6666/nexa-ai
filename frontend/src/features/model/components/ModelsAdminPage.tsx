'use client';

import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  usePublicModels,
  useModelMetas,
  useVendors,
  useMissingModels,
  useModelSyncPreview,
  useModelSyncExecute,
  useCreatePublicModel,
  useUpdatePublicModel,
  useDeletePublicModel,
  useCreateModelMeta,
  useUpdateModelMeta,
  useDeleteModelMeta,
  TIER_LABEL,
  MODEL_STATE_MAP,
  type PublicModelVM,
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

/** 模型元数据抽屉的剩余字段（标签/描述/图标 + 错误），抽出以控制单块体积。 */
interface MetaFormState {
  model_name: string;
  vendor_id: string;
  status: string;
  tags: string;
  endpoints: string;
  description: string;
  icon: string;
}
function MetaDrawerRest({ metaForm, setMetaForm, metaErr }: {
  metaForm: MetaFormState;
  setMetaForm: React.Dispatch<React.SetStateAction<MetaFormState>>;
  metaErr: string | null;
}) {
  return (
    <>
      <div style={{ marginTop: 'var(--space-3)' }}>
        <label className="field-label">标签（逗号分隔，可空）</label>
        <input
          className="input"
          placeholder="vision,tools,reasoning"
          value={metaForm.tags}
          onChange={(e) => setMetaForm((f) => ({ ...f, tags: e.target.value }))}
        />
      </div>
      <div style={{ marginTop: 'var(--space-3)' }}>
        <label className="field-label">描述（可空）</label>
        <input
          className="input"
          value={metaForm.description}
          onChange={(e) => setMetaForm((f) => ({ ...f, description: e.target.value }))}
        />
      </div>
      <div style={{ marginTop: 'var(--space-3)' }}>
        <label className="field-label">图标（可空）</label>
        <input
          className="input"
          value={metaForm.icon}
          onChange={(e) => setMetaForm((f) => ({ ...f, icon: e.target.value }))}
        />
      </div>
      {metaErr && (
        <div className="field-hint" style={{ marginTop: 'var(--space-3)', color: 'var(--color-danger)' }}>
          {metaErr}
        </div>
      )}
    </>
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
type TabKey = 'public' | 'models' | 'vendors';

/**
 * ModelsAdminPage — 模型商品管理（S6 admin/models-admin.html 工程化，已接真实接口）。
 * 三 Tab：对外模型 / 模型元数据 / 模型厂牌。
 * 数据源：GET /api/public_models（+ 供货渠道池 channel/pool 摘要）、
 * /api/models（+ /api/vendors join）、/api/vendors。
 * 缺失检测 GET /api/models/missing、上游同步预览 POST /api/models/sync/preview。
 * A→B 底仓映射已下沉为渠道级（渠道管理页配置），本页不再展示/维护 A→B。
 */
export function ModelsAdminPage() {
  const [tab, setTab] = useState<TabKey>('public');

  /* 各 Tab 数据 */
  const pubsQuery = usePublicModels();
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

  /* 模型元数据写操作 */
  const createMeta = useCreateModelMeta();
  const updateMeta = useUpdateModelMeta();
  const deleteMeta = useDeleteModelMeta();

  /* 模型元数据编辑抽屉 */
  const [metaDrawerOpen, setMetaDrawerOpen] = useState(false);
  const [metaEditId, setMetaEditId] = useState<number | null>(null);
  const [metaForm, setMetaForm] = useState({
    model_name: '',
    vendor_id: '',
    status: '1',
    tags: '',
    endpoints: '',
    description: '',
    icon: '',
  });
  const [metaErr, setMetaErr] = useState<string | null>(null);

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

  /* 模型元数据筛选 */
  const [fVendor, setFVendor] = useState('');
  const [fState, setFState] = useState('');
  const [fSearch, setFSearch] = useState('');

  /* 供应商元数据筛选 */
  const [vSearch, setVSearch] = useState('');

  const pubs: PublicModelVM[] = useMemo(() => pubsQuery.data ?? [], [pubsQuery.data]);
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

  /* ── 模型元数据筛选 ── */
  const filteredModels = useMemo(() => {
    return metas.filter((r) => {
      if (fVendor && r.ven !== fVendor) return false;
      if (fState && MODEL_STATE_MAP[r.st].lab !== fState) return false;
      if (fSearch && !r.nm.toLowerCase().includes(fSearch.toLowerCase())) return false;
      return true;
    });
  }, [metas, fVendor, fState, fSearch]);

  /* ── 模型厂牌筛选 ── */
  const filteredVendors = useMemo(() => {
    return vendors.filter((v) => !vSearch || v.nm.toLowerCase().includes(vSearch.toLowerCase()));
  }, [vendors, vSearch]);

  /* 动态下拉项 */
  const vendorOptions = useMemo(() => Array.from(new Set(metas.map((m) => m.ven).filter((v) => v !== '—'))), [metas]);

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
  /**
   * 从模型元数据「发布」：模型ID（public_name，调用键）直接 = 底层模型名，不拼任何后缀；
   * 外显名（display_name）默认同名、可改。品质档只影响倍率/分组展示，不参与命名。
   */
  function openPublishFromMeta(r: ModelMetaVM) {
    setPubEditId(null);
    setPubErr(null);
    setPubForm({
      public_name: r.nm,
      display_name: r.nm,
      quality_tier: 'air',
      base_price_ratio: '1',
      sort_order: '0',
      description: r.description || '',
      enabled: true,
    });
    setTab('public');
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

  /* ── 模型元数据抽屉 ── */
  function openMetaNew() {
    setMetaEditId(null);
    setMetaErr(null);
    setMetaForm({ model_name: '', vendor_id: '', status: '1', tags: '', endpoints: '', description: '', icon: '' });
    setMetaDrawerOpen(true);
  }
  function openMetaEdit(r: ModelMetaVM) {
    setMetaEditId(r.id);
    setMetaErr(null);
    setMetaForm({
      model_name: r.nm,
      vendor_id: r.vendorId ? String(r.vendorId) : '',
      status: String(r.statusCode || 1),
      tags: r.tagsRaw,
      endpoints: r.endpoints,
      description: r.description,
      icon: r.icon,
    });
    setMetaDrawerOpen(true);
  }
  async function handleMetaSave() {
    setMetaErr(null);
    const name = metaForm.model_name.trim();
    if (!name) { setMetaErr('模型名必填'); return; }
    const vendorId = metaForm.vendor_id.trim() ? Number(metaForm.vendor_id) : undefined;
    if (vendorId != null && Number.isNaN(vendorId)) { setMetaErr('供应商非法'); return; }
    const status = Number(metaForm.status) || 0;
    try {
      if (metaEditId == null) {
        await createMeta.mutateAsync({
          model_name: name,
          vendor_id: vendorId,
          tags: metaForm.tags.trim() || undefined,
          endpoints: metaForm.endpoints.trim() || undefined,
          description: metaForm.description.trim() || undefined,
          icon: metaForm.icon.trim() || undefined,
        });
      } else {
        await updateMeta.mutateAsync({
          id: metaEditId,
          status,
          model_name: name,
          vendor_id: vendorId,
          tags: metaForm.tags.trim() || undefined,
          endpoints: metaForm.endpoints.trim() || undefined,
          description: metaForm.description.trim() || undefined,
          icon: metaForm.icon.trim() || undefined,
        });
      }
      setMetaDrawerOpen(false);
    } catch (e) {
      setMetaErr(e instanceof ApiError ? e.message : '保存失败，请稍后重试');
    }
  }
  async function handleMetaToggle(r: ModelMetaVM) {
    // status_only 切换上下架：1=上架、2=下架。
    await updateMeta.mutateAsync({ id: r.id, status_only: true, status: r.st === 'on' ? 2 : 1 });
  }
  async function handleMetaDelete(r: ModelMetaVM) {
    await deleteMeta.mutateAsync(r.id);
  }
  const metaSaving = createMeta.isPending || updateMeta.isPending;


  return (
    <AppShell
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
        <button className={`${styles.tab}${tab === 'models' ? ' ' + styles.on : ''}`} onClick={() => setTab('models')}>模型元数据</button>
        <button className={`${styles.tab}${tab === 'vendors' ? ' ' + styles.on : ''}`} onClick={() => setTab('vendors')}>模型厂牌</button>
      </div>

      {/* ════ 对外模型 Tab ════ */}
      {tab === 'public' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              对外模型即对客户售卖的商品（公开名 <b>A</b>）。基准售价对所有客户恒定；底仓映射 <b>A→B</b> 与成本倍率已下沉到<b>渠道管理</b>页，按渠道各自配置（<b>客户永不可见 B</b>）。
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
                    <th>模型 ID / 外显名</th><th>品质档</th>
                    <th>基准售价倍率</th>
                    <th>供应渠道池</th><th>状态</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <StateRow colSpan={6} loading={pubsQuery.isLoading} error={pubsQuery.error} empty={filteredPubs.length === 0} />
                  {filteredPubs.map((r) => (
                    <tr key={r.id}>
                      <td className={styles.cellmono}>
                        {r.a}
                        <div className={`muted ${styles.dispSub}`}>{r.disp}</div>
                      </td>
                      <td><TierBadge t={r.tier} /></td>
                      <td className={styles.cellmono}>{r.priceRatio}</td>
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
            <Button variant="primary" size="sm" onClick={openMetaNew}>新建模型</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>模型名</th><th>供应商</th><th>供货渠道</th><th>状态</th><th>端点</th><th>标签</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <StateRow colSpan={7} loading={metasQuery.isLoading} error={metasQuery.error} empty={filteredModels.length === 0} />
                  {filteredModels.map((r) => (
                    <tr key={r.id}>
                      <td className={styles.cellmono}>{r.nm}</td>
                      <td className="muted">{r.ven}</td>
                      <td>
                        {r.poolCount > 0
                          ? <span className={styles.chsCnt}>{r.poolCount} 渠道</span>
                          : <span className={styles.costWarn}>未绑渠道</span>}
                      </td>
                      <td><StateBadge st={r.st} /></td>
                      <td className={`${styles.cellmono} muted`}>{r.endpoints || '—'}</td>
                      <td><Caps arr={r.tags} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => openPublishFromMeta(r)}>发布</a>
                          <a onClick={() => openMetaEdit(r)}>编辑</a>
                          <a onClick={() => handleMetaToggle(r)}>{r.st === 'on' ? '下架' : '上架'}</a>
                          <a className={styles.dang} onClick={() => handleMetaDelete(r)}>删除</a>
                        </div>
                      </td>
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
            <label className="field-label">模型 ID <span className="field-req">*</span></label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="例如：claude-haiku-4.5"
              value={pubForm.public_name}
              disabled={pubEditId != null}
              onChange={(e) => setPubForm((f) => ({ ...f, public_name: e.target.value }))}
            />
            <div className={styles.fieldHint}>
              {pubEditId != null
                ? '模型 ID 建后不可改'
                : '客户调用时使用的模型名，须与底层模型 / 渠道支持的模型名一致（不要拼接后缀）。'}
            </div>
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">外显名称</label>
            <input
              className="input"
              placeholder="例如：Claude Haiku 4.5"
              value={pubForm.display_name}
              onChange={(e) => setPubForm((f) => ({ ...f, display_name: e.target.value }))}
            />
            <div className={styles.fieldHint}>仅用于模型广场展示，不影响调用。</div>
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
            底仓映射 A→B、成本倍率与供应渠道池在<b>渠道管理</b>页按渠道各自维护，此处仅管理对外商品本身。
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

      {/* ════ 模型元数据 编辑/新建 抽屉 ════ */}
      <div className={`${styles.drawerScrim}${metaDrawerOpen ? ' ' + styles.on : ''}`} onClick={() => setMetaDrawerOpen(false)} />
      <aside className={`${styles.drawer}${metaDrawerOpen ? ' ' + styles.on : ''}`} aria-label="模型元数据编辑">
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{metaEditId == null ? '新建模型' : '编辑模型'}</h2>
          <button className={styles.drawerX} onClick={() => setMetaDrawerOpen(false)} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">模型名 <span className="field-req">*</span></label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="例如：gpt-4o"
              value={metaForm.model_name}
              onChange={(e) => setMetaForm((f) => ({ ...f, model_name: e.target.value }))}
            />
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">供应商（厂牌 vendor id，可空）</label>
            <select
              className="input"
              value={metaForm.vendor_id}
              onChange={(e) => setMetaForm((f) => ({ ...f, vendor_id: e.target.value }))}
            >
              <option value="">（无）</option>
              {vendors.map((v) => <option key={v.id} value={String(v.id)}>{v.nm}</option>)}
            </select>
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">状态</label>
            <select
              className="input"
              value={metaForm.status}
              onChange={(e) => setMetaForm((f) => ({ ...f, status: e.target.value }))}
            >
              <option value="1">上架</option>
              <option value="2">下架</option>
              <option value="3">预发布</option>
            </select>
          </div>
          <div style={{ marginTop: 'var(--space-3)' }}>
            <label className="field-label">端点（逗号分隔，可空）</label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="/v1/chat/completions,/v1/embeddings"
              value={metaForm.endpoints}
              onChange={(e) => setMetaForm((f) => ({ ...f, endpoints: e.target.value }))}
            />
          </div>
          <MetaDrawerRest
            metaForm={metaForm}
            setMetaForm={setMetaForm}
            metaErr={metaErr}
          />
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={() => setMetaDrawerOpen(false)}>取消</Button>
          <Button variant="primary" onClick={handleMetaSave} disabled={metaSaving}>
            {metaSaving ? '保存中…' : '保存'}
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
    </AppShell>
  );
}
