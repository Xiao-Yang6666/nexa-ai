'use client';

import { useState, useMemo } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { useAdminUsers, type UserRole } from '@/features/account/model/users-admin.model';
import { UserBalanceModal } from './UserBalanceModal';
import {
  useModelGroups,
  useUserModelGroups,
  useSetUserModelGroups,
  POLICY_LABEL,
} from '@/features/modelgroup';
import styles from './UsersAdminPage.module.css';

const ROLE_MAP: Record<UserRole, { cls: string; lab: string }> = {
  root: { cls: styles.bRoot, lab: 'root' },
  admin: { cls: styles.bAdmin, lab: 'admin' },
  common: { cls: 'b-neutral', lab: 'common' },
};

function QuotaCell({ q, used }: { q: number; used: number }) {
  const pct = q > 0 ? Math.min(Math.round((used / q) * 100), 100) : 0;
  const cls = pct >= 95 ? styles.dang : pct >= 80 ? styles.warn : '';
  return (
    <div className={styles.quota}>
      <span className={styles.barMini}>
        <i className={cls} style={{ width: `${pct}%` }} />
      </span>
      <span className={styles.quotaTxt}>
        ${used.toFixed(2)} / ${q.toFixed(2)}
      </span>
    </div>
  );
}

const PAGE_SIZE = 20;

export function UsersAdminPage() {
  const [fRole, setFRole] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [fGroup, setFGroup] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerUser, setDrawerUser] = useState<{ id: number; name: string; mode: 'manage' | 'edit' | 'new' } | null>(null);
  /** 余额管理弹窗目标用户（null=关闭）。 */
  const [balanceUser, setBalanceUser] = useState<{ id: number; name: string; balanceUsd: number } | null>(null);

  // 真后端：GET /api/user/?page&page_size（分页在服务端）。
  const { data, isLoading, isError, error } = useAdminUsers({ page, page_size: PAGE_SIZE });
  const allRows = data?.rows ?? [];
  const total = data?.total ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  // 客户端二次筛选（角色/状态/分组/关键词）——服务端分页之上的本页过滤。
  const filtered = useMemo(() => {
    return allRows.filter((u) => {
      if (fRole && u.role !== fRole) return false;
      if (fStatus === '启用' && u.status !== 'on') return false;
      if (fStatus === '封禁' && u.status !== 'ban') return false;
      if (fGroup && u.group !== fGroup) return false;
      if (search) {
        const q = search.toLowerCase();
        if (
          !u.username.toLowerCase().includes(q) &&
          !u.email.toLowerCase().includes(q) &&
          !String(u.id).includes(q)
        )
          return false;
      }
      return true;
    });
  }, [allRows, fRole, fStatus, fGroup, search]);

  const allChecked = filtered.length > 0 && filtered.every((u) => selected.has(u.id));
  const toggleAll = () => {
    if (allChecked) setSelected(new Set());
    else setSelected(new Set(filtered.map((u) => u.id)));
  };
  const toggleOne = (id: number) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelected(next);
  };

  const openDrawer = (mode: 'manage' | 'edit' | 'new', id = 0, name = '') => {
    setDrawerUser({ id, name, mode });
    setDrawerOpen(true);
  };
  const closeDrawer = () => setDrawerOpen(false);

  return (
    <AppShell
      activeId="users"
      title="用户管理"
      crumb={['管理后台', '资源管理', '用户管理']}
      actions={<Button onClick={() => openDrawer('new')}>创建用户</Button>}
    >
      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fRole} onChange={(e) => setFRole(e.target.value)}>
          <option value="">全部角色</option>
          <option value="root">root</option>
          <option value="admin">admin</option>
          <option value="common">common</option>
        </select>
        <select className={styles.sel} value={fStatus} onChange={(e) => setFStatus(e.target.value)}>
          <option value="">全部状态</option>
          <option>启用</option>
          <option>封禁</option>
        </select>
        <select className={styles.sel} value={fGroup} onChange={(e) => setFGroup(e.target.value)}>
          <option value="">全部分组</option>
          <option value="default">default</option>
          <option value="vip">vip</option>
          <option value="enterprise">enterprise</option>
          <option value="internal">internal</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索用户名 / 邮箱 / ID"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </section>

      {/* BatchBar */}
      {selected.size > 0 && (
        <section className={styles.batchbar}>
          <span className={styles.cnt}>已选 {selected.size} 项</span>
          <span className={styles.grow} />
          <Button variant="sec" size="sm">批量改分组</Button>
          <Button variant="sec" size="sm">批量启用</Button>
          <Button variant="danger" size="sm">批量封禁</Button>
        </section>
      )}

      {/* Table */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th style={{ width: 34 }}>
                  <input type="checkbox" checked={allChecked} onChange={toggleAll} aria-label="全选" />
                </th>
                <th>ID</th>
                <th>用户名</th>
                <th>角色</th>
                <th>状态</th>
                <th>分组</th>
                <th>额度 / 已用</th>
                <th>邀请数</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={10} style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
                    加载中…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={10} style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-danger)' }}>
                    加载失败：{error instanceof Error ? error.message : '请求出错'}
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={10} style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-secondary)' }}>
                    无匹配用户
                  </td>
                </tr>
              ) : (
                filtered.map((u) => {
                  const rm = ROLE_MAP[u.role];
                  return (
                    <tr key={u.id}>
                      <td>
                        <input
                          type="checkbox"
                          checked={selected.has(u.id)}
                          onChange={() => toggleOne(u.id)}
                          aria-label={`选择 ${u.username}`}
                        />
                      </td>
                      <td className="mono-num muted">{u.id}</td>
                      <td>{u.username}</td>
                      <td>
                        <span className={`badge ${rm.cls}`}>{rm.lab}</span>
                      </td>
                      <td>
                        {u.status === 'on' ? (
                          <span className="badge b-suc">
                            <span className="dot" style={{ background: 'var(--color-success)' }} />
                            启用
                          </span>
                        ) : (
                          <span className="badge b-dan">
                            <span className="dot" style={{ background: 'var(--color-danger)' }} />
                            封禁
                          </span>
                        )}
                      </td>
                      <td className="muted">{u.group}</td>
                      <td>
                        <QuotaCell q={u.quotaUsd} used={u.usedUsd} />
                      </td>
                      <td className="mono-num">{u.affCount}</td>
                      <td className="mono-num muted">{u.registered}</td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => openDrawer('edit', u.id, u.username)}>编辑</a>
                          <a onClick={() => openDrawer('manage', u.id, u.username)}>管理</a>
                          <a onClick={() => setBalanceUser({ id: u.id, name: u.username, balanceUsd: u.quotaUsd })}>充值</a>
                          <a>重置</a>
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
          <span>共 {total} 名用户</span>
          <div className={styles.pg}>
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}>
              ‹
            </button>
            <button className={styles.on}>{page}</button>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}>
              ›
            </button>
          </div>
        </div>
      </section>

      {/* Drawer Scrim */}
      {drawerOpen && <div className={styles.drawerScrim} onClick={closeDrawer} />}

      {/* Drawer */}
      <aside className={`${styles.drawer} ${drawerOpen ? styles.drawerOn : ''}`} aria-label="用户管理">
        <div className={styles.drawerHead}>
          <div>
            <h2 className={styles.drawerTitle}>
              {drawerUser?.mode === 'new'
                ? '创建用户'
                : drawerUser?.mode === 'edit'
                ? '编辑用户'
                : `管理 · ${drawerUser?.name ?? ''}`}
            </h2>
            <div className={styles.drawerSub}>
              {drawerUser?.mode === 'new'
                ? '设置初始额度 · 分组 · 角色'
                : drawerUser?.mode === 'edit'
                ? '基础资料与联系方式'
                : '调整额度 · 分组 · 角色 · 封禁'}
            </div>
          </div>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">
            ×
          </button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">调整额度</label>
            <input className="input mono-num" defaultValue="$500.00" />
            <div className="field-hint">当前余额 $312.40 · 已用 $187.60</div>
          </div>
          <div>
            <label className="field-label">所属分组</label>
            <select className="input">
              <option>default</option>
              <option>vip</option>
              <option>enterprise</option>
              <option>internal</option>
            </select>
          </div>
          <div>
            <label className="field-label">角色</label>
            <select className="input">
              <option>common</option>
              <option>admin</option>
              <option>root</option>
            </select>
          </div>
          {drawerUser && drawerUser.id > 0 && drawerUser.mode !== 'new' && (
            <ModelGroupsField userId={drawerUser.id} />
          )}
          <div>
            <label className="field-label">备注</label>
            <textarea
              className="input"
              style={{ height: 72, padding: 'var(--space-2) var(--space-3)' }}
              placeholder="内部备注，仅管理员可见"
            />
          </div>
          <div className={styles.swRow}>
            <div>
              <div className={styles.lab}>封禁此用户</div>
              <div className={styles.hint}>封禁后该用户所有 Key 立即失效</div>
            </div>
            <label className="switch">
              <input type="checkbox" />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>
            取消
          </Button>
          <Button>保存变更</Button>
        </div>
      </aside>

      {/* 余额管理弹窗（居中） */}
      <UserBalanceModal user={balanceUser} onClose={() => setBalanceUser(null)} />
    </AppShell>
  );
}

/**
 * 抽屉内「私有模型组」配置区：多选勾选 + 覆盖式保存。
 *
 * 拉两份数据：所有模型组（可选项）+ 该用户已授权组（初始选中态）。勾选后整体提交
 * PUT /api/admin/model_group/user/{userId}，后端做 diff 增删。仅展示 PRIVATE 策略组
 * （公开组人人可用、自动组按等级，无需逐用户授权）。
 */
function ModelGroupsField({ userId }: { userId: number }) {
  const { data: allGroups = [], isLoading: loadingAll } = useModelGroups();
  const { data: userGroups = [], isLoading: loadingUser } = useUserModelGroups(userId);
  const setMut = useSetUserModelGroups();
  const [draft, setDraft] = useState<Set<string> | null>(null);
  const [saved, setSaved] = useState(false);

  // 仅私有组可逐用户授权（公开/自动无需）。
  const privateGroups = useMemo(
    () => allGroups.filter((g) => g.policy === 'PRIVATE'),
    [allGroups],
  );

  // 初始选中态来自该用户已授权组的 code 集；draft 为 null 时回退到服务端态。
  const initialCodes = useMemo(
    () => new Set(userGroups.map((g) => g.code)),
    [userGroups],
  );
  const checked = draft ?? initialCodes;

  const toggle = (code: string) => {
    const next = new Set(checked);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    setDraft(next);
    setSaved(false);
  };

  const onSave = async () => {
    setSaved(false);
    await setMut.mutateAsync({ userId, codes: Array.from(checked) });
    setDraft(null); // 提交后回退到服务端最新态
    setSaved(true);
  };

  const loading = loadingAll || loadingUser;
  const dirty = draft != null;

  return (
    <div>
      <label className="field-label">私有模型组授权</label>
      {loading ? (
        <div className="field-hint">加载中…</div>
      ) : privateGroups.length === 0 ? (
        <div className="field-hint">
          暂无私有模型组。可在「模型组管理」创建 {POLICY_LABEL.PRIVATE} 策略的组。
        </div>
      ) : (
        <>
          <div className={styles.mgList}>
            {privateGroups.map((g) => (
              <label key={g.id} className={styles.mgItem}>
                <input
                  type="checkbox"
                  checked={checked.has(g.code)}
                  onChange={() => toggle(g.code)}
                />
                <span className={styles.mgName}>{g.name}</span>
                <span className={styles.mgCode}>{g.code}</span>
                <span className={styles.mgRatio}>×{g.ratio}</span>
              </label>
            ))}
          </div>
          <div className={styles.mgFoot}>
            <span className="field-hint">
              {dirty ? '有未保存改动' : saved ? '已保存' : '勾选后保存即覆盖式生效'}
            </span>
            <Button
              size="sm"
              variant="sec"
              onClick={onSave}
              disabled={setMut.isPending || !dirty}
            >
              {setMut.isPending ? '保存中…' : '保存授权'}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

