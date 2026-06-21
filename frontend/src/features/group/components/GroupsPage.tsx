'use client';

import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import {
  usePrefillGroups,
  useCreatePrefillGroup,
  useUpdatePrefillGroup,
  useDeletePrefillGroup,
  type PrefillRowVM,
} from '../model/prefill.model';
import { getOptions, updateOption } from '@/features/billing/api/ratio.api';
import type { PrefillGroupType } from '../api/prefill.api';
import styles from './GroupsPage.module.css';

/* ── 内联线性图标 ── */
const ICONS: Record<string, ReactNode> = {
  info: (
    <>
      <circle cx="10" cy="10" r="8" />
      <path d="M10 9v4" />
      <path d="M10 6.4v.2" />
    </>
  ),
  sort: <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />,
  doc: (
    <>
      <rect x="3" y="3" width="14" height="14" rx="2" />
      <path d="M7 8h6M7 12h4" />
    </>
  ),
  edit: (
    <>
      <path d="M4 20h4L18 10l-4-4L4 16z" />
      <path d="M14 6l4 4" />
    </>
  ),
  del: (
    <>
      <path d="M5 7h14" />
      <path d="M9 7V5h6v2" />
      <path d="M7 7l1 13h8l1-13" />
    </>
  ),
};

function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 20 20"
      width={18}
      height={18}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {ICONS[name] ?? null}
    </svg>
  );
}

function SortArrow() {
  return (
    <span className={styles.arr}>
      <svg viewBox="0 0 16 16" width={12} height={12} fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {ICONS.sort}
      </svg>
    </span>
  );
}

type Pane = 'ratio' | 'prefill';
type GroupType = 'model' | 'tag' | 'endpoint';

/* ══════════════════════════════════════════════
   折扣等级 Pane（读 GroupRatio Option，ROOT-only 写）
   ══════════════════════════════════════════════ */
interface RatioRow {
  nm: string;
  ratio: number;
}

function RatioPane() {
  const qc = useQueryClient();
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['options'],
    queryFn: getOptions,
    retry: false,
  });

  const [rows, setRows] = useState<RatioRow[] | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [search, setSearch] = useState('');
  const [forbidden, setForbidden] = useState(false);
  const [dirty, setDirty] = useState(false);

  // Parse GroupRatio on first load
  if (data && !loaded) {
    const gr = data.find((o) => o.key === 'GroupRatio');
    let parsed: Record<string, number> = {};
    if (gr?.value) {
      try {
        const v = JSON.parse(gr.value);
        if (v && typeof v === 'object') {
          for (const [k, n] of Object.entries(v)) {
            const num = typeof n === 'number' ? n : parseFloat(String(n));
            if (!Number.isNaN(num)) parsed[k] = num;
          }
        }
      } catch {
        /* ignore */
      }
    }
    setRows(Object.entries(parsed).map(([nm, ratio]) => ({ nm, ratio })));
    setLoaded(true);
    setForbidden(false);
  }
  if (isError && !loaded) {
    // 403 = not root, show restricted view
    const status = (error as { status?: number })?.status;
    if (status === 403) setForbidden(true);
    setLoaded(true);
  }

  const filtered = useMemo(() => {
    if (!rows) return [];
    const kw = search.trim().toLowerCase();
    return kw ? rows.filter((r) => r.nm.toLowerCase().includes(kw)) : rows;
  }, [rows, search]);

  const saveMut = useMutation({
    mutationFn: async () => {
      if (!rows) return;
      const obj: Record<string, number> = {};
      for (const r of rows) obj[r.nm] = r.ratio;
      await updateOption('GroupRatio', JSON.stringify(obj));
    },
    onSuccess: () => {
      setDirty(false);
      qc.invalidateQueries({ queryKey: ['options'] });
    },
  });

  function commitRatio(index: number, raw: string) {
    const v = parseFloat(raw);
    if (Number.isNaN(v) || v < 0) return;
    setRows((prev) => {
      if (!prev) return prev;
      return prev.map((r, i) => (i === index ? { ...r, ratio: v } : r));
    });
    setDirty(true);
  }

  function addRow() {
    setRows((prev) => [...(prev ?? []), { nm: `group_${(prev?.length ?? 0) + 1}`, ratio: 1.0 }]);
    setDirty(true);
  }

  function delRow(index: number) {
    setRows((prev) => (prev ? prev.filter((_, i) => i !== index) : prev));
    setDirty(true);
  }

  return (
    <section className={styles.paneOn}>
      <div className={styles.notice}>
        <Icon name="info" className={styles.nxIc} />
        <div className={styles.ntxt}>
          分组现在主要作为「折扣等级」使用——<b>所有对外模型对所有客户全开</b>
          ，分组不再圈定「能用哪些模型」、也不再单独定价。分组只决定<b>折扣系数</b>
          ，决定客户在基准价上享受的折扣力度。
          {forbidden ? ' 折扣系数仅超级管理员可修改。' : null}
        </div>
      </div>

      {forbidden ? (
        <div className={styles.permBox}>
          <b>权限不足</b>
          <span className="muted">折扣等级管理仅超级管理员（root）可操作。请使用 root 账号登录后修改。</span>
        </div>
      ) : (
        <>
          <section className={styles.filterbar}>
            <input
              className={styles.srch}
              type="search"
              placeholder="搜索等级名"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <span className={styles.grow} />
            <span className="muted">折扣系数行内可编辑，回车保存到「未保存改动」</span>
          </section>

          <section className={styles.tableCard}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>等级名</th>
                    <th className={styles.sortable}>折扣系数 <SortArrow /></th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {isLoading && !loaded ? (
                    <tr><td colSpan={3} className={styles.stateCell}>加载中…</td></tr>
                  ) : !filtered.length ? (
                    <tr><td colSpan={3} className={styles.stateCell}>无匹配等级</td></tr>
                  ) : (
                    filtered.map((r) => {
                      const idx = rows!.findIndex((x) => x.nm === r.nm);
                      return (
                        <tr key={r.nm}>
                          <td><span className={styles.ratioCell}>{r.nm}</span></td>
                          <td>
                            <input
                              className={styles.ratioEdit}
                              defaultValue={r.ratio.toFixed(2)}
                              inputMode="decimal"
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                  e.preventDefault();
                                  commitRatio(idx, e.currentTarget.value);
                                  e.currentTarget.blur();
                                }
                              }}
                            />
                          </td>
                          <td>
                            <div className={styles.rowActs}>
                              <a className="dang" onClick={() => delRow(idx)}>删除</a>
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {rows?.length ?? 0} 个折扣等级</span>
              <span className={styles.grow} />
              <Button variant="sec" onClick={addRow}>新增等级</Button>
              <Button
                variant="primary"
                onClick={() => saveMut.mutate()}
                disabled={!dirty || saveMut.isPending}
              >
                {saveMut.isPending ? '保存中…' : dirty ? '保存改动' : '已保存'}
              </Button>
            </div>
          </section>
        </>
      )}

      <div className={styles.formulaCard}>
        <Icon name="doc" />
        <div>
          <div className={styles.ftitle}>最终扣费公式</div>
          <div className={styles.fcode}>
            客户实付 <em>=</em> 对外模型基准价 <em>×</em> 分组折扣系数
          </div>
        </div>
      </div>
    </section>
  );
}

/* ══════════════════════════════════════════════
   预填分组 Pane（接 /api/prefill_group 真接口）
   ══════════════════════════════════════════════ */
const TYPE_LABEL: Record<GroupType, string> = {
  model: '模型分组',
  tag: '标签分组',
  endpoint: '端点分组',
};

function PrefillPane() {
  const [curTab, setCurTab] = useState<GroupType>('model');
  const [search, setSearch] = useState('');
  const { data: rows = [], isLoading, isError, refetch } = usePrefillGroups(curTab);
  const createMut = useCreatePrefillGroup();
  const updateMut = useUpdatePrefillGroup();
  const deleteMut = useDeletePrefillGroup();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<PrefillRowVM | null>(null);
  const [dName, setDName] = useState('');
  const [dDesc, setDDesc] = useState('');
  const [dItems, setDItems] = useState<string[]>([]);
  const [dItemInput, setDItemInput] = useState('');
  const [pendingDel, setPendingDel] = useState<PrefillRowVM | null>(null);

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    if (!kw) return rows;
    return rows.filter((r) => r.name.toLowerCase().includes(kw) || r.items.some((i) => i.toLowerCase().includes(kw)));
  }, [rows, search]);

  function openCreate() {
    setEditing(null);
    setDName('');
    setDDesc('');
    setDItems([]);
    setDItemInput('');
    setDrawerOpen(true);
  }
  function openEdit(r: PrefillRowVM) {
    setEditing(r);
    setDName(r.name);
    setDDesc(r.description ?? '');
    setDItems([...r.items]);
    setDItemInput('');
    setDrawerOpen(true);
  }
  function closeDrawer() {
    setDrawerOpen(false);
    setEditing(null);
  }
  function addItem() {
    const v = dItemInput.trim();
    if (!v) return;
    if (!dItems.includes(v)) setDItems([...dItems, v]);
    setDItemInput('');
  }
  function removeItem(i: number) {
    setDItems(dItems.filter((_, idx) => idx !== i));
  }

  function submit() {
    if (!dName.trim()) return;
    if (editing) {
      updateMut.mutate(
        { id: editing.id, name: dName.trim(), items: dItems },
        { onSuccess: closeDrawer },
      );
    } else {
      createMut.mutate(
        { name: dName.trim(), type: curTab, items: dItems, description: dDesc || undefined },
        { onSuccess: closeDrawer },
      );
    }
  }

  const isPending = createMut.isPending || updateMut.isPending;

  return (
    <section className={styles.paneOn}>
      <div className={styles.tabs}>
        {(['model', 'tag', 'endpoint'] as GroupType[]).map((t) => (
          <button
            key={t}
            type="button"
            className={`${styles.tab} ${curTab === t ? styles.tabOn : ''}`}
            onClick={() => setCurTab(t)}
          >
            {TYPE_LABEL[t]} <span className={styles.cnt}>{rows.length}</span>
          </button>
        ))}
      </div>

      <div className={styles.notice}>
        <Icon name="info" className={styles.nxIc} />
        <div className={styles.ntxt}>
          预填分组用于<b>批量预设 model / tag / endpoint 成员</b>
          ，是与折扣无关的辅助配置。<b>它不再圈定客户能用哪些模型</b>
          ——模型对所有客户全开，权限不由分组决定。
        </div>
      </div>

      <section className={styles.filterbar}>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索分组名 / 成员"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className={styles.grow} />
        <Button variant="primary" onClick={openCreate}>新建分组</Button>
      </section>

      <section className={styles.tableCard}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th className={styles.sortable}>分组名 <SortArrow /></th>
                <th>类型</th>
                <th className={styles.sortable}>成员数量 <SortArrow /></th>
                <th className={styles.sortable}>创建时间 <SortArrow /></th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={5} className={styles.stateCell}>加载中…</td></tr>
              ) : isError ? (
                <tr><td colSpan={5} className={styles.stateCell}>加载失败，<a onClick={() => refetch()}>重试</a></td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={5} className={styles.stateCell}>无匹配分组，点击右上角「新建分组」创建。</td></tr>
              ) : (
                filtered.map((r) => (
                  <tr key={r.id}>
                    <td>{r.name}</td>
                    <td className="keepcolor">
                      <span className={`badge ${r.type === 'model' ? 'b-info' : r.type === 'tag' ? 'b-suc' : 'b-neutral'}`}>
                        {r.type}
                      </span>
                    </td>
                    <td className={styles.cellmono}>{r.memberCount}</td>
                    <td className={`${styles.cellmono} muted`}>{r.createdAt}</td>
                    <td>
                      <div className={styles.rowActs}>
                        <a onClick={() => openEdit(r)}><Icon name="edit" /> 编辑</a>
                        <a className="dang" onClick={() => setPendingDel(r)}><Icon name="del" /> 软删</a>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {filtered.length} 个分组</span>
        </div>
      </section>

      {/* 编辑/创建抽屉 */}
      {drawerOpen && (
        <>
          <div className={styles.drawerScrimOn} onClick={closeDrawer} />
          <aside className={styles.drawerOn} aria-label="分组编辑">
            <div className={styles.drawerHead}>
              <h2 className={styles.drawerTitle}>{editing ? '编辑分组' : '新建分组'}</h2>
              <button type="button" className={styles.drawerX} aria-label="关闭" onClick={closeDrawer}>×</button>
            </div>
            <div className={styles.drawerBody}>
              <div>
                <label className="field-label">分组名 <span className="field-req">*</span></label>
                <input className="input" placeholder="例如：高性能聊天模型" value={dName} onChange={(e) => setDName(e.target.value)} />
              </div>
              <div>
                <label className="field-label">分组类型</label>
                <input className="input" value={TYPE_LABEL[editing?.type ?? curTab]} disabled />
                {!editing && <div className="field-hint">新建后类型不可修改</div>}
              </div>
              <div>
                <label className="field-label">描述（可选）</label>
                <input className="input" placeholder="用途说明" value={dDesc} onChange={(e) => setDDesc(e.target.value)} />
              </div>
              <div>
                <label className="field-label">成员列表</label>
                <div className={styles.tagin}>
                  {dItems.map((t, i) => (
                    <span className={styles.tag} key={`${t}-${i}`}>
                      {t} <b onClick={() => removeItem(i)}>×</b>
                    </span>
                  ))}
                  <input
                    type="text"
                    placeholder="输入成员后回车"
                    value={dItemInput}
                    onChange={(e) => setDItemInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') { e.preventDefault(); addItem(); }
                    }}
                  />
                </div>
                <div className="field-hint">回车添加成员；成员名按 {curTab} 类型语义填写（模型名/tag 名/端点名）。</div>
              </div>
            </div>
            <div className={styles.drawerFoot}>
              <Button variant="ghost" onClick={closeDrawer}>取消</Button>
              <Button variant="primary" onClick={submit} disabled={isPending || !dName.trim()}>
                {isPending ? '保存中…' : '保存分组'}
              </Button>
            </div>
          </aside>
        </>
      )}

      {/* 软删确认 */}
      {pendingDel && (
        <div className={styles.modalScrim} onClick={() => setPendingDel(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3>软删分组？</h3>
            <p>软删后「<b>{pendingDel.name}</b>」不再作为预填选项出现，但可由超级管理员在数据库中恢复。</p>
            <div className={styles.modalActs}>
              <button className="btn btn-sec" onClick={() => setPendingDel(null)}>取消</button>
              <button
                className="btn btn-danger"
                disabled={deleteMut.isPending}
                onClick={() => {
                  deleteMut.mutate(pendingDel.id, { onSuccess: () => setPendingDel(null) });
                }}
              >
                {deleteMut.isPending ? '删除中…' : '确认软删'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

/* ══════════════════════════════════════════════
   主页面
   ══════════════════════════════════════════════ */
export function GroupsPage() {
  const [pane, setPane] = useState<Pane>('prefill');

  return (
    <AdminShell
      activeId="groups"
      title="预填分组"
      crumb={['管理后台', '资源管理', '预填分组']}
    >
      <div className={styles.tabs}>
        <button
          type="button"
          className={`${styles.tab} ${pane === 'prefill' ? styles.tabOn : ''}`}
          onClick={() => setPane('prefill')}
        >
          预填分组
        </button>
        <button
          type="button"
          className={`${styles.tab} ${pane === 'ratio' ? styles.tabOn : ''}`}
          onClick={() => setPane('ratio')}
        >
          折扣等级
        </button>
      </div>

      {pane === 'ratio' ? <RatioPane /> : <PrefillPane />}
    </AdminShell>
  );
}
