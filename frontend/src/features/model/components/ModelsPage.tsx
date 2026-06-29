'use client';

import { useMemo, useState } from 'react';
import { PageHead, Accent } from '@/features/marketing';
import { useModelCatalog, type ModelCardVM } from '../model/model.model';
import type { ModelCapability } from '../model/catalog';
import { ModelCard } from './ModelCard';
import { ModelDetailDrawer } from './ModelDetailDrawer';
import styles from './ModelsPage.module.css';

type CatFilter = 'all' | ModelCapability;
type SortKey = 'default' | 'price' | 'ctx';

const CAT_PILLS: { key: CatFilter; label: string; icon: JSX.Element }[] = [
  { key: 'all', label: '全部', icon: (<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>) },
  { key: 'chat', label: '对话', icon: (<path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.4-.6L3 21l1.6-4.5A8.4 8.4 0 1 1 21 11.5z" />) },
  { key: 'reasoning', label: '推理', icon: (<><path d="M9 18h6" /><path d="M10 21h4" /><path d="M12 3a6 6 0 0 0-4 10.5c.6.6 1 1.4 1 2.5h6c0-1.1.4-1.9 1-2.5A6 6 0 0 0 12 3z" /></>) },
  { key: 'vision', label: '多模态', icon: (<><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="m21 17-5-5-7 7" /></>) },
  { key: 'code', label: '代码', icon: (<><path d="m8 8-4 4 4 4" /><path d="m16 8 4 4-4 4" /><path d="m13 5-2 14" /></>) },
];

/** 上下文体量解析（M→x1000, K→x1）用于排序。 */
function ctxVal(c: string): number {
  const n = parseFloat(c);
  if (Number.isNaN(n)) return 0;
  if (c.includes('M')) return n * 1000;
  if (c.includes('K')) return n;
  return n / 1000;
}

/**
 * ModelsPage — 模型广场（web-public/models.html 工程化）。
 *
 * 接 /api/pricing（mock 起桩，公开 PublicView 零泄露）→ 卡片 VM 列表，
 * 能力 pill 筛选 + 搜索 + 排序，一模型一卡，详情抽屉看分组价格对比。
 * loading/empty/error 各态完备。
 */
export function ModelsPage() {
  const { data, isLoading, isError, refetch } = useModelCatalog();

  const [cat, setCat] = useState<CatFilter>('all');
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
        (m.modelName.toLowerCase().includes(q) || m.vendor.toLowerCase().includes(q)),
    );
    if (sort === 'price') {
      filtered = filtered
        .slice()
        .sort((a, b) => (a.fromPrice ?? Infinity) - (b.fromPrice ?? Infinity));
    } else if (sort === 'ctx') {
      filtered = filtered.slice().sort((a, b) => ctxVal(b.ctx) - ctxVal(a.ctx));
    }
    return filtered;
  }, [data, cat, sort, query]);

  const resetFilters = () => {
    setCat('all');
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

          {!isLoading && !isError ? (
            <div className={styles.count}>
              共 <b>{list.length}</b> 个模型
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
        ) : list.length === 0 ? (
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
            {list.map((m) => (
              <ModelCard key={m.modelName} model={m} onOpen={setActive} />
            ))}
          </div>
        )}
      </div>

      <ModelDetailDrawer model={active} onClose={() => setActive(null)} />
    </main>
  );
}
