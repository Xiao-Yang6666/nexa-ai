'use client';

/**
 * features/modelgroup/components/ModelGroupsPage — 灵活模型组管理页（管理端）。
 *
 * 模型组独立售卖单元：配置可用模型集 + 模型组级倍率 + 访问策略（公开/私有/按等级自动）+ 启停。
 * 替代「分组绑死账号等级」，管理员据此灵活配置售卖策略。
 */
import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import {
  useModelGroups,
  useCreateModelGroup,
  useUpdateModelGroup,
  useToggleModelGroupStatus,
  useDeleteModelGroup,
  MG_STATUS,
  POLICY_LABEL,
  type ModelGroupRowVM,
} from '../model/modelgroup.model';
import type { AccessPolicy } from '../api/modelgroup.api';
import styles from './ModelGroupsPage.module.css';

type DrawerMode = 'new' | 'edit';

interface DraftState {
  name: string;
  code: string;
  ratio: string;
  models: string; // 逗号/换行分隔的编辑态文本
  policy: AccessPolicy;
  description: string;
}

const EMPTY_DRAFT: DraftState = {
  name: '',
  code: '',
  ratio: '1.0',
  models: '',
  policy: 'PUBLIC',
  description: '',
};

function parseModels(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export function ModelGroupsPage() {
  const [fPolicy, setFPolicy] = useState<'' | AccessPolicy>('');
  const [search, setSearch] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [mode, setMode] = useState<DrawerMode>('new');
  const [editId, setEditId] = useState<number | null>(null);
  const [draft, setDraft] = useState<DraftState>(EMPTY_DRAFT);
  const [formErr, setFormErr] = useState<string | null>(null);

  const { data: rows = [], isLoading, isError, error } = useModelGroups(
    fPolicy === '' ? undefined : fPolicy,
  );
  const createMut = useCreateModelGroup();
  const updateMut = useUpdateModelGroup();
  const toggleMut = useToggleModelGroupStatus();
  const deleteMut = useDeleteModelGroup();

  const filtered = useMemo(() => {
    if (!search) return rows;
    const q = search.toLowerCase();
    return rows.filter(
      (g) =>
        g.name.toLowerCase().includes(q) ||
        g.code.toLowerCase().includes(q) ||
        g.models.some((m) => m.toLowerCase().includes(q)),
    );
  }, [rows, search]);

  const openNew = () => {
    setMode('new');
    setEditId(null);
    setDraft(EMPTY_DRAFT);
    setFormErr(null);
    setDrawerOpen(true);
  };

  const openEdit = (g: ModelGroupRowVM) => {
    setMode('edit');
    setEditId(g.id);
    setDraft({
      name: g.name,
      code: g.code,
      ratio: String(g.ratio),
      models: g.models.join('\n'),
      policy: g.policy,
      description: g.description ?? '',
    });
    setFormErr(null);
    setDrawerOpen(true);
  };

  const closeDrawer = () => setDrawerOpen(false);

  const submit = async () => {
    setFormErr(null);
    const ratioNum = Number(draft.ratio);
    if (!draft.name.trim()) return setFormErr('请填写模型组名称');
    if (mode === 'new' && !/^[a-z0-9_-]+$/.test(draft.code.trim().toLowerCase())) {
      return setFormErr('编码必须为 [a-z0-9_-]，且不可为空');
    }
    if (!Number.isFinite(ratioNum) || ratioNum < 0) return setFormErr('倍率必须为 ≥0 的数字');

    try {
      if (mode === 'new') {
        await createMut.mutateAsync({
          name: draft.name.trim(),
          code: draft.code.trim().toLowerCase(),
          base_price_ratio: ratioNum,
          models: parseModels(draft.models),
          access_policy: draft.policy,
          description: draft.description.trim() || undefined,
        });
      } else if (editId != null) {
        await updateMut.mutateAsync({
          id: editId,
          req: {
            name: draft.name.trim(),
            base_price_ratio: ratioNum,
            models: parseModels(draft.models),
            access_policy: draft.policy,
            description: draft.description.trim(),
          },
        });
      }
      setDrawerOpen(false);
    } catch (e) {
      setFormErr(e instanceof Error ? e.message : '保存失败');
    }
  };

  const onToggle = (g: ModelGroupRowVM) => {
    toggleMut.mutate({
      id: g.id,
      status: g.enabled ? MG_STATUS.DISABLED : MG_STATUS.ENABLED,
    });
  };

  const onDelete = (g: ModelGroupRowVM) => {
    if (window.confirm(`确认删除模型组「${g.name}」(${g.code})？`)) {
      deleteMut.mutate(g.id);
    }
  };

  const saving = createMut.isPending || updateMut.isPending;

  return (
    <AppShell
      activeId="model-groups"
      title="模型组管理"
      crumb={['管理后台', '资源管理', '模型组管理']}
      actions={<Button onClick={openNew}>新建模型组</Button>}
    >
      <section className={`${styles.filterbar} nx-fade`}>
        <select
          className={styles.sel}
          value={fPolicy}
          onChange={(e) => setFPolicy(e.target.value as '' | AccessPolicy)}
        >
          <option value="">全部策略</option>
          <option value="PUBLIC">{POLICY_LABEL.PUBLIC}</option>
          <option value="PRIVATE">{POLICY_LABEL.PRIVATE}</option>
          <option value="AUTO_LEVEL">{POLICY_LABEL.AUTO_LEVEL}</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索名称 / 编码 / 模型"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </section>

      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>编码</th>
                <th>倍率</th>
                <th>访问策略</th>
                <th>模型数</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={8} className={styles.empty}>加载中…</td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={8} className={styles.err}>
                    加载失败：{error instanceof Error ? error.message : '请求出错'}
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className={styles.empty}>无匹配模型组</td>
                </tr>
              ) : (
                filtered.map((g) => (
                  <tr key={g.id}>
                    <td>
                      {g.name}
                      {g.description ? <div className={styles.desc}>{g.description}</div> : null}
                    </td>
                    <td className="mono-num muted">{g.code}</td>
                    <td className="mono-num">×{g.ratio}</td>
                    <td>
                      <span className={`badge ${policyBadge(g.policy)}`}>{g.policyLabel}</span>
                    </td>
                    <td className="mono-num">{g.modelCount}</td>
                    <td>
                      {g.enabled ? (
                        <span className="badge b-suc">启用</span>
                      ) : (
                        <span className="badge b-dan">禁用</span>
                      )}
                    </td>
                    <td className="mono-num muted">{g.updatedAt}</td>
                    <td>
                      <div className={styles.rowActs}>
                        <a onClick={() => openEdit(g)}>编辑</a>
                        <a onClick={() => onToggle(g)}>{g.enabled ? '禁用' : '启用'}</a>
                        <a className={styles.danger} onClick={() => onDelete(g)}>删除</a>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {drawerOpen && <div className={styles.drawerScrim} onClick={closeDrawer} />}
      <aside className={`${styles.drawer} ${drawerOpen ? styles.drawerOn : ''}`} aria-label="模型组编辑">
        <div className={styles.drawerHead}>
          <div>
            <h2 className={styles.drawerTitle}>{mode === 'new' ? '新建模型组' : '编辑模型组'}</h2>
            <div className={styles.drawerSub}>配置模型集 · 倍率 · 访问策略</div>
          </div>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">名称</label>
            <input
              className="input"
              value={draft.name}
              onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              placeholder="如：高级模型组"
            />
          </div>
          <div>
            <label className="field-label">编码（中继按此选组，创建后不可改）</label>
            <input
              className="input mono-num"
              value={draft.code}
              disabled={mode === 'edit'}
              onChange={(e) => setDraft({ ...draft, code: e.target.value })}
              placeholder="如：premium"
            />
          </div>
          <div>
            <label className="field-label">模型组倍率</label>
            <input
              className="input mono-num"
              value={draft.ratio}
              onChange={(e) => setDraft({ ...draft, ratio: e.target.value })}
              placeholder="1.0"
            />
            <div className="field-hint">售价 = 对外模型基准倍率 × 模型组倍率 × tokens</div>
          </div>
          <div>
            <label className="field-label">访问策略</label>
            <select
              className="input"
              value={draft.policy}
              onChange={(e) => setDraft({ ...draft, policy: e.target.value as AccessPolicy })}
            >
              <option value="PUBLIC">{POLICY_LABEL.PUBLIC}（所有用户可用）</option>
              <option value="PRIVATE">{POLICY_LABEL.PRIVATE}（需在用户列表显式授权）</option>
              <option value="AUTO_LEVEL">{POLICY_LABEL.AUTO_LEVEL}（按账号等级映射）</option>
            </select>
          </div>
          <div>
            <label className="field-label">可用模型（逗号或换行分隔）</label>
            <textarea
              className="input"
              style={{ height: 110, padding: 'var(--space-2) var(--space-3)' }}
              value={draft.models}
              onChange={(e) => setDraft({ ...draft, models: e.target.value })}
              placeholder={'gpt-4o\nclaude-3-opus\ngemini-pro'}
            />
          </div>
          <div>
            <label className="field-label">描述</label>
            <textarea
              className="input"
              style={{ height: 60, padding: 'var(--space-2) var(--space-3)' }}
              value={draft.description}
              onChange={(e) => setDraft({ ...draft, description: e.target.value })}
              placeholder="可选"
            />
          </div>
          {formErr && <div className={styles.formErr}>{formErr}</div>}
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>取消</Button>
          <Button onClick={submit} disabled={saving}>{saving ? '保存中…' : '保存'}</Button>
        </div>
      </aside>
    </AppShell>
  );
}

function policyBadge(p: AccessPolicy): string {
  if (p === 'PUBLIC') return 'b-suc';
  if (p === 'PRIVATE') return 'b-warn';
  return 'b-neutral';
}
