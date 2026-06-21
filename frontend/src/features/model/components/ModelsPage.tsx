'use client';

import { useMemo, useState } from 'react';
import { PageHead, Accent } from '@/features/marketing';
import { useModelCatalog, type ModelCardVM } from '../model/model.model';
import type { ModelCapability } from '../model/catalog';
import type { QualityTier } from '../model/model.model';
import { ModelCard } from './ModelCard';
import { ModelDetailDrawer } from './ModelDetailDrawer';
import styles from './ModelsPage.module.css';

type CatFilter = 'all' | ModelCapability;
type TierFilter = 'all' | QualityTier;
type SortKey = 'default' | 'price' | 'ctx';

const CAT_PILLS: { key: CatFilter; label: string; icon: JSX.Element }[] = [
  { key: 'all', label: '全部', icon: (<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>) },
  { key: 'chat', label: '对话', icon: (<path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.4-.6L3 21l1.6-4.5A8.4 8.4 0 1 1 21 11.5z" />) },
  { key: 'reasoning', label: '推理', icon: (<><path d="M9 18h6" /><path d="M10 21h4" /><path d="M12 3a6 6 0 0 0-4 10.5c.6.6 1 1.4 1 2.5h6c0-1.1.4-1.9 1-2.5A6 6 0 0 0 12 3z" /></>) },
  { key: 'vision', label: '多模态', icon: (<><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="m21 17-5-5-7 7" /></>) },
  { key: 'code', label: '代码', icon: (<><path d="m8 8-4 4 4 4" /><path d="m16 8 4 4-4 4" /><path d="m13 5-2 14" /></>) },
];

const TIER_PILLS: { key: TierFilter; label: string; icon: JSX.Element }[] = [
  { key: 'all', label: '全部品质', icon: (<><path d="M3 7h18" /><path d="M3 12h18" /><path d="M3 17h18" /></>) },
  { key: 'full', label: '旗舰', icon: (<path d="m12 3 2.6 5.3 5.9.9-4.3 4.1 1 5.9L12 16.6 6.8 19.2l1-5.9L3.5 9.2l5.9-.9z" />) },
  { key: 'max', label: '增强', icon: (<path d="M13 2 4 14h7l-1 8 9-12h-7z" />) },
  { key: 'air', label: '经济', icon: (<><circle cx="12" cy="12" r="8" /><path d="M12 8v8" /><path d="M9.5 10.5h3.5a1.8 1.8 0 0 1 0 3.5H10" /></>) },
];

/** 上下文体量解析（M→x1000, K→x1）用于排序。 */
function ctxVal(c: string): number {
  const n = parseFloat(c);
  if (Number.isNaN(n)) return 0;
  if (c.includes('M')) return n * 1000;
  if (c.includes('K')) return n;
  return n / 1000;
}

/** 家族归组保序：同 family 条目聚到首次出现位置，无 family 保持原位。 */
function clusterFamilies(list: ModelCardVM[]): ModelCardVM[] {
  const out: (ModelCardVM | ModelCardVM[])[] = [];
  const slots = new Map<string, ModelCardVM[]>();
  for (const m of list) {
    if (m.family) {
      let arr = slots.get(m.family);
      if (!arr) {
        arr = [];
        slots.set(m.family, arr);
        out.push(arr);
      }
      arr.push(m);
    } else {
      out.push(m);
    }
  }
  return out.flat();
}

/**
 * ModelsPage — 模型广场（web-public/models.html 工程化）。
 *
 * 接 /api/pricing（mock 起桩，公开 PublicView 零泄露）→ 卡片 VM 列表，
 * 能力/品质 pill 筛选 + 搜索 + 排序 + 同族归组 + 详情抽屉。
 * loading/empty/error 各态完备。
 */
export function ModelsPage() {
  const { data, isLoading, isError, refetch } = useModelCatalog();

  const [cat, setCat] = useState<CatFilter>('all');
  const [tier, setTier] = useState<TierFilter>('all');
  const [sort, setSort] = useState<SortKey>('default');
  const [query, setQuery] = useState('');
  const [active, setActive] = useState<ModelCardVM | null>(null);

  const total = data?.length ?? 0;

  const list = useMemo(() => {
    const models = data ?? [];
    const q = query.trim().toLowerCase();
    let filtered = models.filter(
      (m) =>
        (cat === 'all' || m.cats.includes(cat)) &&
        (tier === 'all' || m.tier === tier) &&
        (m.modelName.toLowerCase().includes(q) || m.vendor.toLowerCase().includes(q)),
    );
    if (sort === 'price') {
      filtered = filtered
        .slice()
        .sort((a, b) => (a.basePrice ?? Infinity) - (b.basePrice ?? Infinity));
    } else if (sort === 'ctx') {
      filtered = filtered.slice().sort((a, b) => ctxVal(b.ctx) - ctxVal(a.ctx));
    }
    return clusterFamilies(filtered);
  }, [data, cat, tier, sort, query]);

  // 把归组后的扁平列表按家族组渲染
  const groups = useMemo(() => buildGroups(list), [list]);

  const resetFilters = () => {
    setCat('all');
    setTier('all');
    setSort('default');
    setQuery('');
  };

  return (
    <main className={styles.page}>
      <PageHead
        pill="实时在线 · 统一协议接入"
        title={<>模型<Accent>广场</Accent></>}
        lead={
          <>
            聚合主流厂商共 <b>{total}</b> 个模型，统一协议、统一计费。点击标签快速筛选，搜索模型名或厂商，按价格 / 上下文排序。
          </>
        }
      />

      <div className="wrap">
        <div className={styles.toolbar}>
          <div className={styles.filterBar}>
            <div className={styles.search}>
              <span className={styles.si} aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="11" cy="11" r="7" />
                  <path d="m20 20-3.5-3.5" />
                </svg>
              </span>
              <input
                className={styles.ctrlInp}
                placeholder="搜索模型名或厂商…"
                aria-label="搜索模型"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <div className={styles.selw}>
              <select
                className={styles.ctrlInp}
                aria-label="排序方式"
                value={sort}
                onChange={(e) => setSort(e.target.value as SortKey)}
              >
                <option value="default">默认排序</option>
                <option value="price">价格从低到高</option>
                <option value="ctx">上下文从大到小</option>
              </select>
              <span className={styles.chev} aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </span>
            </div>
          </div>

          <div className={styles.pills} role="group" aria-label="能力筛选">
            {CAT_PILLS.map((p) => (
              <button
                key={p.key}
                type="button"
                className={`${styles.pill} ${cat === p.key ? styles.pillOn : ''}`}
                onClick={() => setCat(p.key)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  {p.icon}
                </svg>
                {p.label}
              </button>
            ))}
          </div>

          <div className={styles.pills} role="group" aria-label="品质筛选">
            {TIER_PILLS.map((p) => (
              <button
                key={p.key}
                type="button"
                className={`${styles.pill} ${tier === p.key ? styles.pillOn : ''}`}
                onClick={() => setTier(p.key)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  {p.icon}
                </svg>
                {p.label}
              </button>
            ))}
          </div>

          {!isLoading && !isError ? (
            <div className={styles.count}>
              共 <b>{countCards(list)}</b> 个模型
            </div>
          ) : null}
        </div>

        {isLoading ? (
          <div className={styles.skeletonGrid}>
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className={styles.skeletonCard} />
            ))}
          </div>
        ) : isError ? (
          <div className={styles.errorBox}>
            <div className={styles.t}>模型数据加载失败</div>
            <div>网络或服务异常，请稍后重试。</div>
            <button type="button" className={styles.btnGlass} onClick={() => refetch()}>
              重试
            </button>
          </div>
        ) : countCards(list) === 0 ? (
          <div className={styles.empty}>
            <span className={styles.ic} aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="7" />
                <path d="m20 20-3.5-3.5" />
                <path d="M8.5 11h5" />
              </svg>
            </span>
            <div className={styles.t}>没有匹配的模型</div>
            <div className={styles.s}>换个关键词，或重置筛选条件试试。</div>
            <button type="button" className={styles.btnGlass} onClick={resetFilters}>
              重置筛选
            </button>
          </div>
        ) : (
          <div className={styles.grid}>
            {groups.map((g, gi) =>
              g.kind === 'family' ? (
                <div key={`fam-${g.family}-${gi}`} className={styles.famGroup}>
                  <div className={styles.famHead}>
                    <span className={styles.ft}>{g.label}</span>
                    <span className={styles.fsub}>
                      同模型 {g.members.length} 个品质档 · 按需选价
                    </span>
                    <span className={styles.fline} aria-hidden="true" />
                  </div>
                  <div className={styles.famCards}>
                    {g.members.map((m) => (
                      <ModelCard key={m.modelName} model={m} onOpen={setActive} />
                    ))}
                  </div>
                </div>
              ) : (
                <ModelCard key={g.model.modelName} model={g.model} onOpen={setActive} />
              ),
            )}
          </div>
        )}
      </div>

      <ModelDetailDrawer model={active} onClose={() => setActive(null)} />
    </main>
  );
}

type RenderGroup =
  | { kind: 'single'; model: ModelCardVM }
  | { kind: 'family'; family: string; label: string; members: ModelCardVM[] };

/** 把扁平归组列表切成渲染组（连续同 family 多条 → family 组，单条退化为 single）。 */
function buildGroups(list: ModelCardVM[]): RenderGroup[] {
  const out: RenderGroup[] = [];
  let i = 0;
  while (i < list.length) {
    const m = list[i];
    if (m.family) {
      const fam = m.family;
      const members: ModelCardVM[] = [];
      while (i < list.length && list[i].family === fam) {
        members.push(list[i]);
        i++;
      }
      if (members.length > 1) {
        out.push({
          kind: 'family',
          family: fam,
          label: members[0].familyLabel ?? fam,
          members,
        });
      } else {
        out.push({ kind: 'single', model: members[0] });
      }
    } else {
      out.push({ kind: 'single', model: m });
      i++;
    }
  }
  return out;
}

/** 统计渲染卡片总数（扁平列表长度）。 */
function countCards(list: ModelCardVM[]): number {
  return list.length;
}
