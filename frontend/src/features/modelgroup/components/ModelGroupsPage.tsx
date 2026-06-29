'use client';

/**
 * features/modelgroup/components/ModelGroupsPage — 价格分组管理页（管理端）。
 *
 * 价格分组 = 独立售卖单元：配置「包含模型集 + 分组倍率 + 谁可以用（公开/指定用户/按等级）+ 启停」。
 * 同一模型可加入多个分组、各组设不同倍率，形成价格档对比（经济/标准/旗舰）。售价 = 模型基准倍率 × 分组倍率。
 */
import { useMemo, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import {
  useModelGroups,
  useCandidateModels,
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
  models: string[]; // 已选模型名集合（勾选编辑）
  policy: AccessPolicy;
  description: string;
}

const EMPTY_DRAFT: DraftState = {
  name: '',
  code: '',
  ratio: '1.0',
  models: [],
  policy: 'PUBLIC',
  description: '',
};

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
  const { data: candidateModels = [] } = useCandidateModels();
  const createMut = useCreateModelGroup();
  const updateMut = useUpdateModelGroup();
  const toggleMut = useToggleModelGroupStatus();
  const deleteMut = useDeleteModelGroup();

  /* 抽屉内模型勾选搜索 */
  const [modelQuery, setModelQuery] = useState('');

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

  // 候选模型 ∪ 已选模型（保证编辑时即便候选未含某历史模型也能显示并保留勾选）。
  const modelOptions = useMemo(() => {
    const set = new Set<string>(candidateModels);
    draft.models.forEach((m) => set.add(m));
    const all = Array.from(set);
    const q = modelQuery.trim().toLowerCase();
    const list = q ? all.filter((m) => m.toLowerCase().includes(q)) : all;
    return list.sort((a, b) => a.localeCompare(b));
  }, [candidateModels, draft.models, modelQuery]);

  const toggleModel = (name: string) => {
    setDraft((d) => ({
      ...d,
      models: d.models.includes(name)
        ? d.models.filter((m) => m !== name)
        : [...d.models, name],
    }));
  };

  const openNew = () => {
    setMode('new');
    setEditId(null);
    setDraft(EMPTY_DRAFT);
    setModelQuery('');
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
      models: [...g.models],
      policy: g.policy,
      description: g.description ?? '',
    });
    setModelQuery('');
    setFormErr(null);
    setDrawerOpen(true);
  };

  const closeDrawer = () => setDrawerOpen(false);

  const submit = async () => {
    setFormErr(null);
    const ratioNum = Number(draft.ratio);
    if (!draft.name.trim()) return setFormErr('请填写价格分组名称');
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
          models: draft.models,
          access_policy: draft.policy,
          description: draft.description.trim() || undefined,
        });
      } else if (editId != null) {
        await updateMut.mutateAsync({
          id: editId,
          req: {
            name: draft.name.trim(),
            base_price_ratio: ratioNum,
            models: draft.models,
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
    if (window.confirm(`确认删除价格分组「${g.name}」(${g.code})？`)) {
      deleteMut.mutate(g.id);
    }
  };

  const saving = createMut.isPending || updateMut.isPending;

  return (
    <AppShell
      activeId="model-groups"
      title="价格分组"
      crumb={['管理后台', '资源管理', '价格分组']}
      actions={<Button onClick={openNew}>新建价格分组</Button>}
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
      <aside className={`${styles.drawer} ${drawerOpen ? styles.drawerOn : ''}`} aria-label="价格分组编辑">
        <div className={styles.drawerHead}>
          <div>
            <h2 className={styles.drawerTitle}>{mode === 'new' ? '新建价格分组' : '编辑价格分组'}</h2>
            <div className={styles.drawerSub}>配置包含模型 · 倍率 · 谁可以用</div>
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
              placeholder="如：旗舰组"
            />
          </div>
          <div>
            <label className="field-label">编码（中继按此选组，创建后不可改）</label>
            <input
              className="input mono-num"
              value={draft.code}
              disabled={mode === 'edit'}
              onChange={(e) => setDraft({ ...draft, code: e.target.value })}
              placeholder="如：flagship"
            />
          </div>
          <div>
            <label className="field-label">分组倍率</label>
            <input
              className="input mono-num"
              value={draft.ratio}
              onChange={(e) => setDraft({ ...draft, ratio: e.target.value })}
              placeholder="1.0"
            />
            <div className="field-hint">售价 = 模型基准倍率 × 分组倍率 × tokens</div>
          </div>
          <div>
            <label className="field-label">谁可以用</label>
            <select
              className="input"
              value={draft.policy}
              onChange={(e) => setDraft({ ...draft, policy: e.target.value as AccessPolicy })}
            >
              <option value="PUBLIC">所有人（{POLICY_LABEL.PUBLIC}，全部用户可用）</option>
              <option value="PRIVATE">指定用户（{POLICY_LABEL.PRIVATE}，需在用户列表显式授权）</option>
              <option value="AUTO_LEVEL">按会员等级（{POLICY_LABEL.AUTO_LEVEL}）</option>
            </select>
          </div>
          <div>
            <label className="field-label">
              包含模型 <span className="muted">（已选 {draft.models.length}）</span>
            </label>
            <input
              className={`${styles.srch} ${styles.modelSearch}`}
              type="search"
              placeholder="搜索模型名筛选…"
              value={modelQuery}
              onChange={(e) => setModelQuery(e.target.value)}
            />
            <div className={styles.modelPicker}>
              {modelOptions.length === 0 ? (
                <div className={styles.modelEmpty}>无匹配模型</div>
              ) : (
                modelOptions.map((name) => {
                  const checked = draft.models.includes(name);
                  return (
                    <label key={name} className={`${styles.modelOpt} ${checked ? styles.modelOptOn : ''}`}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleModel(name)}
                      />
                      <span className="mono-num">{name}</span>
                    </label>
                  );
                })
              )}
            </div>
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
