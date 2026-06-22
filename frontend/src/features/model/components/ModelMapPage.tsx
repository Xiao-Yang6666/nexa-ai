'use client';

import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import {
  useModelAliases,
  useAliasCandidates,
  useCreateAlias,
  useUpdateAlias,
  useDeleteAlias,
  type AliasScope,
  type AliasVM,
} from '../model/model-alias.model';
import styles from './ModelMapPage.module.css';

/* ── 线性图标 ── */
const Ic = {
  key: (
    <path d="M14 7a4 4 0 1 1-3.5 6H7v3H4v-3l2-2a4 4 0 0 1 8-2z" />
  ),
  map: (
    <>
      <circle cx="6" cy="12" r="2.5" />
      <circle cx="18" cy="6" r="2.5" />
      <circle cx="18" cy="18" r="2.5" />
      <path d="M8.3 11l7.4-4M8.3 13l7.4 4" />
    </>
  ),
  wallet: (
    <>
      <path d="M3 7h15a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M16 12h2" />
      <path d="M3 7l13-3v3" />
    </>
  ),
  arrow: (
    <>
      <path d="M5 12h14" />
      <path d="M13 6l6 6-6 6" />
    </>
  ),
  check: <path d="M5 12l5 5L20 7" />,
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
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8h.01" />
      <path d="M11 12h1v4h1" />
    </>
  ),
  shield: <path d="M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z" />,
};

function NxIc({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <svg
      className={`${styles.nxIc} ${className ?? ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

/**
 * ModelMapPage — 我的模型映射（C→A，S6 console/model-map.html 工程化）。
 *
 * 接 GET/POST/DELETE /api/user/self/model_aliases（自助 CRUD）+ candidates 联想。
 * 作用域分用户级 / 分组级；用户级同名优先覆盖分组级（产品规则）。
 * 客户端零泄露：target 仅平台公开模型 A，候选与请求体绝不含上游真实模型 B。
 */
export function ModelMapPage() {
  const { data: aliases, isLoading, isError, refetch } = useModelAliases();
  const { data: candidates } = useAliasCandidates();
  const createMut = useCreateAlias();
  const updateMut = useUpdateAlias();
  const deleteMut = useDeleteAlias();

  const [scope, setScope] = useState<AliasScope>('user');
  const [adding, setAdding] = useState(false);
  const [pendingDel, setPendingDel] = useState<AliasVM | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editTarget, setEditTarget] = useState('');
  const [editEnabled, setEditEnabled] = useState(true);
  const [editDdOpen, setEditDdOpen] = useState(false);

  // 新增表单态
  const [newAlias, setNewAlias] = useState('');
  const [newTarget, setNewTarget] = useState('');
  const [ddOpen, setDdOpen] = useState(false);
  const [aliasErr, setAliasErr] = useState(false);

  const nameSet = useMemo(() => new Set(candidates ?? []), [candidates]);

  const rows = useMemo(
    () => (aliases ?? []).filter((a) => a.scope === scope),
    [aliases, scope],
  );

  // 候选过滤：前缀优先 + 短名优先
  const filtered = useMemo(() => {
    const list = candidates ?? [];
    const q = newTarget.trim().toLowerCase();
    if (!q) return list;
    return list
      .filter((c) => c.toLowerCase().includes(q))
      .sort((a, b) => {
        const pa = a.toLowerCase().indexOf(q);
        const pb = b.toLowerCase().indexOf(q);
        if (pa !== pb) return pa - pb;
        return a.length - b.length;
      });
  }, [candidates, newTarget]);

  const targetMatched = newTarget.trim() !== '' && nameSet.has(newTarget.trim());

  function resetAdd() {
    setAdding(false);
    setNewAlias('');
    setNewTarget('');
    setDdOpen(false);
    setAliasErr(false);
    setEditingId(null);
  }

  function handleSave() {
    if (!newAlias.trim()) {
      setAliasErr(true);
      return;
    }
    if (!newTarget.trim()) return;
    createMut.mutate(
      { alias: newAlias.trim(), target: newTarget.trim(), scope_type: scope, enabled: true },
      { onSuccess: resetAdd },
    );
  }

  function startEdit(r: AliasVM) {
    setEditingId(r.id);
    setEditTarget(r.target);
    setEditEnabled(r.enabled);
    setAdding(false);
    setEditDdOpen(false);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditTarget('');
    setEditEnabled(true);
    setEditDdOpen(false);
  }

  function submitEdit() {
    if (editingId == null) return;
    const t = editTarget.trim();
    if (!t) return;
    updateMut.mutate(
      { id: editingId, payload: { target: t, enabled: editEnabled } },
      { onSuccess: cancelEdit },
    );
  }

  const editFiltered = useMemo(() => {
    const list = candidates ?? [];
    const q = editTarget.trim().toLowerCase();
    if (!q) return list;
    return list
      .filter((c) => c.toLowerCase().includes(q))
      .sort((a, b) => {
        const pa = a.toLowerCase().indexOf(q);
        const pb = b.toLowerCase().indexOf(q);
        if (pa !== pb) return pa - pb;
        return a.length - b.length;
      });
  }, [candidates, editTarget]);

  const actions = (
    <button
      className="btn btn-primary"
      onClick={() => {
        setScope(scope);
        setAdding(true);
      }}
    >
      新增映射
    </button>
  );

  return (
    <AppShell activeId="model-map" title="我的模型映射" crumb={['控制台', '我的模型映射']} actions={actions}>
      {/* 闭环导航 */}
      <div className={`${styles.loopnav} nx-fade`}>
        <a href="/keys">
          <NxIc>{Ic.key}</NxIc>API 密钥
        </a>
        <span className={styles.sep}>·</span>
        <a className={styles.cur} href="/model-map">
          <NxIc>{Ic.map}</NxIc>我的模型映射
        </a>
        <span className={styles.sep}>·</span>
        <a href="/recharge">
          <NxIc>{Ic.wallet}</NxIc>分组与折扣
        </a>
      </div>

      {/* 应用范围条 */}
      <div className={`${styles.applybar} nx-fade`}>
        <NxIc className={styles.abIc}>{Ic.shield}</NxIc>
        {scope === 'user' ? (
          <span>
            <b>用户级映射</b>仅对你账户下的<b>全部 API 密钥</b>生效（含未来新建的 key），与分组无关；同名时优先于分组级。
          </span>
        ) : (
          <span>
            <b>分组级映射</b>对你当前分组 <span className={styles.abChip}>VIP</span> 下的所有成员与 key 生效；你的用户级同名映射会覆盖它。
          </span>
        )}
      </div>

      {/* 说明 */}
      <div className={`${styles.note} nx-fade`}>
        <NxIc>{Ic.info}</NxIc>
        <span>
          把你应用里用惯的模型名，映射到平台模型，<b>客户端零改动</b>。映射只是改名，所有平台模型对你全开。作用域分
          <b>仅本人（用户级）</b>与<b>整个分组（分组级）</b>，同名时<b>用户级优先</b>。
        </span>
      </div>

      {/* 作用域切换 */}
      <div className={`${styles.scopebar} nx-fade`}>
        <div className={styles.seg}>
          <button className={scope === 'user' ? styles.on : ''} onClick={() => { setScope('user'); resetAdd(); }}>
            用户级映射
          </button>
          <button className={scope === 'group' ? styles.on : ''} onClick={() => { setScope('group'); resetAdd(); }}>
            分组级映射
          </button>
        </div>
        <span className={styles.scopeHint}>
          {scope === 'user'
            ? '用户级映射仅对你自己生效，优先于同名的分组级映射。'
            : '分组级映射对整个分组生效；用户级同名映射会覆盖它。'}
        </span>
      </div>

      {/* 映射表 */}
      <div className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th style={{ minWidth: 160 }}>你的模型名 C</th>
                <th className={styles.arrowCell} />
                <th style={{ minWidth: 200 }}>映射到平台模型 A</th>
                <th>作用域</th>
                <th>状态</th>
                <th style={{ width: 96 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={6}>
                    <div className={styles.skRow} />
                    <div className={styles.skRow} />
                    <div className={styles.skRow} />
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={6} className={styles.stateBox}>
                    <div className={styles.t}>映射加载失败</div>
                    <div>网络或服务异常，请稍后重试。</div>
                    <button className="btn btn-sec" style={{ marginTop: 'var(--space-4)' }} onClick={() => refetch()}>
                      重试
                    </button>
                  </td>
                </tr>
              ) : rows.length === 0 && !adding ? (
                <tr>
                  <td colSpan={6} className={styles.stateBox}>
                    <div className={styles.t}>{scope === 'user' ? '还没有用户级映射' : '还没有分组级映射'}</div>
                    <div>点击右上「新增映射」，把你惯用的模型名映射到平台模型。</div>
                  </td>
                </tr>
              ) : (
                rows.map((r) => {
                  const overrides = scope === 'user' && r.alias === 'claude-3-5-sonnet';
                  const isEditing = editingId === r.id;
                  if (isEditing) {
                    return (
                      <tr key={r.id} className={styles.addrow}>
                        <td className={styles.cName}>{r.alias}</td>
                        <td className={styles.arrowCell}><NxIc>{Ic.arrow}</NxIc></td>
                        <td>
                          <div className={styles.edField}>
                            <div className={styles.combo}>
                              <input
                                className="input"
                                placeholder="平台模型 A"
                                autoFocus
                                value={editTarget}
                                onFocus={() => setEditDdOpen(true)}
                                onChange={(e) => { setEditTarget(e.target.value); setEditDdOpen(true); }}
                                onBlur={() => setTimeout(() => setEditDdOpen(false), 120)}
                              />
                              {editDdOpen && (
                                <div className={styles.dropdown} role="listbox">
                                  {editFiltered.length === 0 ? (
                                    <div className={styles.optEmpty}>无匹配的平台模型</div>
                                  ) : editFiltered.slice(0, 20).map((c) => (
                                    <div
                                      key={c}
                                      className={styles.opt}
                                      onMouseDown={(e) => { e.preventDefault(); setEditTarget(c); setEditDdOpen(false); }}
                                    >
                                      <div className={styles.optName}>{c}</div>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginTop: 6, fontSize: 'var(--text-caption)' }}>
                              <input type="checkbox" checked={editEnabled} onChange={(e) => setEditEnabled(e.target.checked)} />
                              启用该映射
                            </label>
                          </div>
                        </td>
                        <td><span className={styles.scopeTag}>{scope === 'user' ? '仅本人' : '整个分组'}</span></td>
                        <td />
                        <td>
                          <div className={styles.edActs}>
                            <button
                              className="btn btn-primary"
                              style={{ height: 30, padding: '0 var(--space-3)' }}
                              disabled={updateMut.isPending}
                              onClick={submitEdit}
                            >
                              {updateMut.isPending ? '保存中…' : '保存'}
                            </button>
                            <button className={styles.iconact} type="button" title="取消" onClick={cancelEdit}>
                              <NxIc><path d="M6 6l12 12M18 6L6 18" /></NxIc>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  }
                  return (
                    <tr key={r.id}>
                      <td className={styles.cName}>{r.alias}</td>
                      <td className={styles.arrowCell}>
                        <NxIc>{Ic.arrow}</NxIc>
                      </td>
                      <td className={styles.aName}>{r.target}</td>
                      <td>
                        <span className={styles.scopeTag}>
                          {scope === 'user' ? '仅本人' : '整个分组'}
                          {overrides ? <span className={styles.ovr}> · 覆盖分组级</span> : null}
                        </span>
                      </td>
                      <td>
                        {r.enabled ? (
                          <span className={styles.matched}>
                            <NxIc>{Ic.check}</NxIc>已生效
                          </span>
                        ) : (
                          <span className="badge b-neutral">
                            <span className="dot" style={{ background: 'var(--color-text-muted)' }} />
                            已停用
                          </span>
                        )}
                      </td>
                      <td>
                        <div className={styles.rowacts}>
                          <button className={styles.iconact} type="button" title="编辑" onClick={() => startEdit(r)}>
                            <NxIc>{Ic.edit}</NxIc>
                          </button>
                          <button
                            className={`${styles.iconact} ${styles.dan}`}
                            type="button"
                            title="删除"
                            onClick={() => setPendingDel(r)}
                          >
                            <NxIc>{Ic.del}</NxIc>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}

              {/* 新增行内编辑 */}
              {adding && (
                <tr className={styles.addrow}>
                  <td>
                    <div className={styles.edField}>
                      <span className={styles.edLabel}>你的模型名 C</span>
                      <input
                        className="input"
                        placeholder="例如 gpt-4o"
                        autoComplete="off"
                        value={newAlias}
                        onChange={(e) => {
                          setNewAlias(e.target.value);
                          setAliasErr(false);
                        }}
                      />
                      {aliasErr ? <span className={styles.edErr}>C 不可为空</span> : null}
                    </div>
                  </td>
                  <td className={styles.arrowCell}>
                    <NxIc>{Ic.arrow}</NxIc>
                  </td>
                  <td>
                    <div className={styles.edField}>
                      <span className={styles.edLabel}>平台模型 A（可键入联想，也可自由输入）</span>
                      <div className={styles.combo}>
                        <input
                          className="input"
                          placeholder="键入联想，如 opus / gpt-4o"
                          autoComplete="off"
                          role="combobox"
                          aria-expanded={ddOpen}
                          aria-controls="alias-candidate-list"
                          aria-autocomplete="list"
                          value={newTarget}
                          onFocus={() => setDdOpen(true)}
                          onChange={(e) => {
                            setNewTarget(e.target.value);
                            setDdOpen(true);
                          }}
                          onBlur={() => setTimeout(() => setDdOpen(false), 120)}
                        />
                        {ddOpen && (
                          <div className={styles.dropdown} role="listbox" id="alias-candidate-list">
                            {filtered.length === 0 ? (
                              <div className={styles.optEmpty}>
                                无匹配的平台模型，可直接保存为自定义名（调用时可能失败）。
                              </div>
                            ) : (
                              filtered.map((c) => (
                                <div
                                  key={c}
                                  className={styles.opt}
                                  role="option"
                                  aria-selected={c === newTarget}
                                  onMouseDown={(e) => {
                                    e.preventDefault();
                                    setNewTarget(c);
                                    setDdOpen(false);
                                  }}
                                >
                                  <div className={styles.optMain}>
                                    <div className={styles.optName}>{c}</div>
                                  </div>
                                </div>
                              ))
                            )}
                          </div>
                        )}
                      </div>
                      {targetMatched ? (
                        <span className={`${styles.comboOk} ${styles.show}`}>
                          <NxIc>{Ic.check}</NxIc>已匹配平台模型
                        </span>
                      ) : newTarget.trim() !== '' ? (
                        <span className={`${styles.comboWarn} ${styles.show}`}>
                          该名称不在平台模型列表中，调用时可能失败。
                        </span>
                      ) : null}
                    </div>
                  </td>
                  <td>
                    <span className={styles.scopeTag}>{scope === 'user' ? '仅本人' : '整个分组'}</span>
                  </td>
                  <td />
                  <td>
                    <div className={styles.edActs}>
                      <button
                        className="btn btn-primary"
                        style={{ height: 30, padding: '0 var(--space-3)' }}
                        disabled={createMut.isPending}
                        onClick={handleSave}
                      >
                        {createMut.isPending ? '保存中…' : '保存'}
                      </button>
                      <button className={styles.iconact} type="button" title="取消" onClick={resetAdd}>
                        <NxIc>
                          <path d="M6 6l12 12M18 6L6 18" />
                        </NxIc>
                      </button>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>
            共 <b style={{ color: 'var(--color-text)' }}>{rows.length}</b> 条映射
          </span>
          <span className={styles.scopeHint}>A 不做白名单强校验：自由输入是产品决策，可先占位待平台后续上架。</span>
        </div>
      </div>

      {/* 删除确认弹窗 */}
      {pendingDel && (
        <div className={`${styles.modalScrim} ${styles.open}`} onClick={(e) => { if (e.target === e.currentTarget) setPendingDel(null); }}>
          <div className={styles.modal} role="dialog" aria-modal="true">
            <h3>删除映射？</h3>
            <p>
              删除后 <b style={{ fontFamily: 'var(--font-mono)' }}>{pendingDel.alias}</b> 将不再被改写，按原名直接调用平台。
            </p>
            <div className={styles.modalActs}>
              <button className="btn btn-sec" onClick={() => setPendingDel(null)}>
                取消
              </button>
              <button
                className="btn btn-danger"
                disabled={deleteMut.isPending}
                onClick={() => deleteMut.mutate(pendingDel.id, { onSuccess: () => setPendingDel(null) })}
              >
                {deleteMut.isPending ? '删除中…' : '删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
