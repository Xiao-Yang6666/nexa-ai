'use client';

import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import styles from './GroupsPage.module.css';

/* ── 内联线性图标（迁移自 S6 原型 SVG path） ── */
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
};

function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 20 20"
      width="18"
      height="18"
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
      <svg
        viewBox="0 0 16 16"
        width="12"
        height="12"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        {ICONS.sort}
      </svg>
    </span>
  );
}

/* ── 折扣等级数据（DECISIONS §6：纯折扣，不控权限） ── */
type LevelStatus = 'on' | 'soft';

interface RatioRow {
  nm: string;
  ratio: number;
  thresh: string;
  users: number;
  st: LevelStatus;
}

const RATIO_DATA: RatioRow[] = [
  { nm: 'free', ratio: 1.0, thresh: '默认档（注册即用）', users: 8421, st: 'on' },
  { nm: 'vip', ratio: 0.85, thresh: '累计充值满 $500 自动升档', users: 1247, st: 'on' },
  { nm: 'svip', ratio: 0.7, thresh: '累计充值满 $2000 / 企业付费包', users: 312, st: 'on' },
  { nm: 'partner', ratio: 0.6, thresh: '人工指派（合作伙伴）', users: 24, st: 'on' },
  { nm: 'trial', ratio: 0.95, thresh: '7 日试用档（到期回落 free）', users: 186, st: 'soft' },
];

function RatioStatusBadge({ st }: { st: LevelStatus }) {
  return st === 'on' ? (
    <span className="badge b-suc">
      <span className="dot" style={{ background: 'var(--color-success)' }} />
      启用
    </span>
  ) : (
    <span className="badge b-neutral">
      <span className="dot" style={{ background: 'var(--color-text-muted)' }} />
      停用
    </span>
  );
}

/* ── 预填分组数据（三类：model / tag / endpoint，状态多样） ── */
type GroupType = 'model' | 'tag' | 'endpoint';
type GroupStatus = 'on' | 'soft';

interface GroupRow {
  nm: string;
  type: GroupType;
  mem: number;
  pr: number;
  st: GroupStatus;
  ct: string;
}

const GROUP_DATA: GroupRow[] = [
  { nm: '高性能聊天', type: 'model', mem: 8, pr: 20, st: 'on', ct: '06-02 09:14' },
  { nm: '经济型模型', type: 'model', mem: 6, pr: 14, st: 'on', ct: '06-02 09:31' },
  { nm: '推理专用', type: 'model', mem: 4, pr: 18, st: 'on', ct: '06-03 11:02' },
  { nm: '视觉多模态', type: 'model', mem: 5, pr: 12, st: 'on', ct: '06-04 15:48' },
  { nm: '已弃用大模型', type: 'model', mem: 3, pr: 2, st: 'soft', ct: '05-21 08:20' },
  { nm: '企业版标签', type: 'tag', mem: 12, pr: 16, st: 'on', ct: '06-01 10:05' },
  { nm: '内测白名单', type: 'tag', mem: 9, pr: 15, st: 'on', ct: '06-05 13:27' },
  { nm: '高配额用户', type: 'tag', mem: 7, pr: 10, st: 'on', ct: '06-06 16:40' },
  { nm: '临时活动标签', type: 'tag', mem: 4, pr: 4, st: 'soft', ct: '05-28 09:11' },
  { nm: '主区端点', type: 'endpoint', mem: 6, pr: 22, st: 'on', ct: '06-01 08:00' },
  { nm: '备区端点', type: 'endpoint', mem: 5, pr: 18, st: 'on', ct: '06-01 08:02' },
  { nm: '低延迟边缘', type: 'endpoint', mem: 3, pr: 20, st: 'on', ct: '06-07 14:55' },
  { nm: '国内合规端点', type: 'endpoint', mem: 4, pr: 16, st: 'on', ct: '06-09 10:33' },
  { nm: '旧版网关', type: 'endpoint', mem: 2, pr: 3, st: 'soft', ct: '05-19 17:42' },
];

const TYPE_MAP: Record<GroupType, { cls: string; lab: string }> = {
  model: { cls: 'b-info', lab: 'model' },
  tag: { cls: 'b-suc', lab: 'tag' },
  endpoint: { cls: 'b-neutral', lab: 'endpoint' },
};

const STATUS_MAP: Record<GroupStatus, { cls: string; tone: string; lab: string }> = {
  on: { cls: 'b-suc', tone: 'var(--color-success)', lab: '启用' },
  soft: { cls: 'b-neutral', tone: 'var(--color-text-muted)', lab: '软删' },
};

function TypeBadge({ type }: { type: GroupType }) {
  const m = TYPE_MAP[type];
  return <span className={`badge ${m.cls} ${styles.typeBadge}`}>{m.lab}</span>;
}

function GroupStatusBadge({ st }: { st: GroupStatus }) {
  const m = STATUS_MAP[st];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: m.tone }} />
      {m.lab}
    </span>
  );
}

/* ── 抽屉类型相关文案 ── */
const TYPE_LABELS: Record<GroupType, { label: string; hint: string }> = {
  model: { label: '包含的模型', hint: '填写模型名，回车添加' },
  tag: { label: '包含的标签', hint: '填写标签，回车添加' },
  endpoint: { label: '包含的端点', hint: '填写端点地址，回车添加' },
};

const THRESH_LABELS: Record<string, string | null> = {
  recharge: '累计充值门槛（USD）',
  pack: '付费包等级 ID',
  manual: null,
};

type Pane = 'ratio' | 'prefill';

export function GroupsPage() {
  // 顶层 Pane 切换
  const [pane, setPane] = useState<Pane>('ratio');

  // 折扣等级
  const [ratioData, setRatioData] = useState<RatioRow[]>(RATIO_DATA);
  const [rSearch, setRSearch] = useState('');
  const [rDrawerOpen, setRDrawerOpen] = useState(false);
  const [rDrawerTitle, setRDrawerTitle] = useState('新增等级');
  const [rThreshType, setRThreshType] = useState('recharge');

  // 预填分组
  const [curTab, setCurTab] = useState<GroupType>('model');
  const [fStatus, setFStatus] = useState('');
  const [fSearch, setFSearch] = useState('');
  const [gDrawerOpen, setGDrawerOpen] = useState(false);
  const [gDrawerTitle, setGDrawerTitle] = useState('新建分组');
  const [dType, setDType] = useState<GroupType>('model');
  const [memTags, setMemTags] = useState<string[]>(['gpt-4o', 'claude-3-5-sonnet']);
  const [memInput, setMemInput] = useState('');

  /* 折扣等级行内系数编辑：回车保存 */
  const filteredRatio = useMemo(() => {
    const kw = rSearch.trim().toLowerCase();
    return ratioData.filter((r) => !kw || r.nm.toLowerCase().includes(kw));
  }, [ratioData, rSearch]);

  function commitRatio(index: number, raw: string) {
    const v = parseFloat(raw);
    if (Number.isNaN(v)) return;
    setRatioData((prev) => prev.map((r, i) => (i === index ? { ...r, ratio: v } : r)));
  }

  /* 预填分组过滤 */
  const filteredGroups = useMemo(() => {
    const kw = fSearch.trim().toLowerCase();
    return GROUP_DATA.filter((r) => {
      if (r.type !== curTab) return false;
      if (fStatus && r.st !== fStatus) return false;
      if (kw && !r.nm.toLowerCase().includes(kw)) return false;
      return true;
    });
  }, [curTab, fStatus, fSearch]);

  const cntModel = GROUP_DATA.filter((r) => r.type === 'model').length;
  const cntTag = GROUP_DATA.filter((r) => r.type === 'tag').length;
  const cntEndpoint = GROUP_DATA.filter((r) => r.type === 'endpoint').length;

  /* 折扣等级抽屉 */
  function openRatioNew() {
    setRDrawerTitle('新增等级');
    setRDrawerOpen(true);
  }
  function openRatioEdit() {
    setRDrawerTitle('编辑等级');
    setRDrawerOpen(true);
  }
  const closeRatio = () => setRDrawerOpen(false);

  /* 预填分组抽屉 */
  function openGroupNew() {
    setGDrawerTitle('新建分组');
    setDType(curTab);
    setGDrawerOpen(true);
  }
  function openGroupEdit() {
    setGDrawerTitle('编辑分组');
    setDType(curTab);
    setGDrawerOpen(true);
  }
  const closeGroup = () => setGDrawerOpen(false);

  /* 成员标签输入 */
  function addMemTag() {
    const v = memInput.trim();
    if (!v) return;
    setMemTags((prev) => [...prev, v]);
    setMemInput('');
  }
  function removeMemTag(idx: number) {
    setMemTags((prev) => prev.filter((_, i) => i !== idx));
  }

  const memMeta = TYPE_LABELS[dType];
  const threshLabel = THRESH_LABELS[rThreshType];

  /* 顶部操作按钮：随 Pane 切换 */
  const actions =
    pane === 'ratio' ? (
      <Button variant="sec" onClick={openRatioNew}>
        新增等级
      </Button>
    ) : (
      <Button variant="primary" onClick={openGroupNew}>
        新建分组
      </Button>
    );

  return (
    <AdminShell
      activeId="groups"
      title="预填分组"
      crumb={['管理后台', '资源管理', '预填分组']}
      actions={actions}
    >
      {/* 分类 Tab */}
      <div className={styles.tabs}>
        <button
          type="button"
          className={`${styles.tab} ${pane === 'ratio' ? styles.tabOn : ''}`}
          onClick={() => setPane('ratio')}
        >
          折扣等级 <span className={styles.cnt}>{ratioData.length}</span>
        </button>
        <button
          type="button"
          className={`${styles.tab} ${pane === 'prefill' ? styles.tabOn : ''}`}
          onClick={() => setPane('prefill')}
        >
          预填分组
        </button>
      </div>

      {/* ════ 折扣等级 Pane ════ */}
      <section className={pane === 'ratio' ? styles.paneOn : styles.pane}>
        <div className={styles.notice}>
          <Icon name="info" className={styles.nxIc} />
          <div className={styles.ntxt}>
            分组现在主要作为「折扣等级」使用——<b>所有对外模型对所有客户全开</b>
            ，分组不再圈定「能用哪些模型」、也不再单独定价。分组只决定<b>折扣系数</b>
            ，决定客户在基准价上享受的折扣力度。
          </div>
        </div>

        <section className={styles.filterbar}>
          <input
            className={styles.srch}
            type="search"
            placeholder="搜索等级名"
            value={rSearch}
            onChange={(e) => setRSearch(e.target.value)}
          />
          <span className={styles.grow} />
          <span className="muted">折扣系数行内可编辑，回车保存</span>
        </section>

        <section className={styles.tableCard}>
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>等级名</th>
                  <th className={styles.sortable}>
                    折扣系数 <SortArrow />
                  </th>
                  <th>升级门槛</th>
                  <th className={styles.sortable}>
                    当前用户数 <SortArrow />
                  </th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {filteredRatio.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="muted"
                      style={{ textAlign: 'center', padding: 'var(--space-6)' }}
                    >
                      无匹配等级
                    </td>
                  </tr>
                ) : (
                  filteredRatio.map((r) => {
                    const off = ratioData.indexOf(r);
                    return (
                      <tr key={r.nm}>
                        <td>
                          <span className={styles.ratioCell}>{r.nm}</span>
                        </td>
                        <td>
                          <input
                            className={styles.ratioEdit}
                            defaultValue={r.ratio.toFixed(2)}
                            inputMode="decimal"
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                e.preventDefault();
                                commitRatio(off, e.currentTarget.value);
                                e.currentTarget.blur();
                              }
                            }}
                          />
                        </td>
                        <td>
                          <span className={styles.thresh}>{r.thresh}</span>
                        </td>
                        <td className={styles.cellmono}>{r.users.toLocaleString()}</td>
                        <td className="keepcolor">
                          <RatioStatusBadge st={r.st} />
                        </td>
                        <td>
                          <div className={styles.rowActs}>
                            <a onClick={openRatioEdit}>编辑</a>
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
            <span>共 {filteredRatio.length} 个折扣等级</span>
          </div>
        </section>

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

      {/* ════ 预填分组 Pane ════ */}
      <section className={pane === 'prefill' ? styles.paneOn : styles.pane}>
        {/* 预填分组子分类 Tab */}
        <div className={styles.tabs}>
          <button
            type="button"
            className={`${styles.tab} ${curTab === 'model' ? styles.tabOn : ''}`}
            onClick={() => setCurTab('model')}
          >
            模型分组 <span className={styles.cnt}>{cntModel}</span>
          </button>
          <button
            type="button"
            className={`${styles.tab} ${curTab === 'tag' ? styles.tabOn : ''}`}
            onClick={() => setCurTab('tag')}
          >
            标签分组 <span className={styles.cnt}>{cntTag}</span>
          </button>
          <button
            type="button"
            className={`${styles.tab} ${curTab === 'endpoint' ? styles.tabOn : ''}`}
            onClick={() => setCurTab('endpoint')}
          >
            端点分组 <span className={styles.cnt}>{cntEndpoint}</span>
          </button>
        </div>

        <div className={styles.notice}>
          <Icon name="info" className={styles.nxIc} />
          <div className={styles.ntxt}>
            预填分组用于<b>批量预设 model / tag / endpoint 成员</b>
            ，是与折扣无关的辅助配置。<b>它不再圈定客户能用哪些模型</b>
            ——模型对所有客户全开，权限不由分组决定。
          </div>
        </div>

        {/* FilterBar */}
        <section className={`${styles.filterbar} nx-fade`}>
          <select
            className={styles.sel}
            value={fStatus}
            onChange={(e) => setFStatus(e.target.value)}
          >
            <option value="">全部状态</option>
            <option value="on">启用</option>
            <option value="soft">软删</option>
          </select>
          <input
            className={styles.srch}
            type="search"
            placeholder="搜索分组名 / 成员"
            value={fSearch}
            onChange={(e) => setFSearch(e.target.value)}
          />
          <span className={styles.grow} />
          <span className="muted" />
        </section>

        {/* 表格 */}
        <section className={`${styles.tableCard} nx-fade`}>
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th className={styles.sortable}>
                    分组名 <SortArrow />
                  </th>
                  <th>类型</th>
                  <th className={styles.sortable}>
                    成员数量 <SortArrow />
                  </th>
                  <th className={styles.sortable}>
                    优先级 <SortArrow />
                  </th>
                  <th>状态</th>
                  <th className={styles.sortable}>
                    创建时间 <SortArrow />
                  </th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {filteredGroups.length === 0 ? (
                  <tr>
                    <td
                      colSpan={7}
                      className="muted"
                      style={{ textAlign: 'center', padding: 'var(--space-6)' }}
                    >
                      无匹配分组
                    </td>
                  </tr>
                ) : (
                  filteredGroups.map((r) => {
                    const soft = r.st === 'soft';
                    return (
                      <tr key={r.nm} className={soft ? styles.soft : undefined}>
                        <td>{r.nm}</td>
                        <td className="keepcolor">
                          <TypeBadge type={r.type} />
                        </td>
                        <td className={styles.cellmono}>{r.mem}</td>
                        <td className={styles.cellmono}>{r.pr}</td>
                        <td className="keepcolor">
                          <GroupStatusBadge st={r.st} />
                        </td>
                        <td className={`${styles.cellmono} muted`}>{r.ct}</td>
                        <td>
                          <div className={styles.rowActs}>
                            {soft ? (
                              <a className="ok">恢复</a>
                            ) : (
                              <>
                                <a onClick={openGroupEdit}>编辑</a>
                                <a className="dang">软删</a>
                              </>
                            )}
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
            <span>共 {filteredGroups.length} 个分组</span>
          </div>
        </section>
      </section>

      {/* 编辑抽屉（预填分组） */}
      <div
        className={`${styles.drawerScrim} ${gDrawerOpen ? styles.drawerScrimOn : ''}`}
        onClick={closeGroup}
      />
      <aside
        className={`${styles.drawer} ${gDrawerOpen ? styles.drawerOn : ''}`}
        aria-label="分组编辑"
      >
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{gDrawerTitle}</h2>
          <button type="button" className={styles.drawerX} aria-label="关闭" onClick={closeGroup}>
            ×
          </button>
        </div>
        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">
              分组名 <span className="field-req">*</span>
            </label>
            <input className="input" placeholder="例如：高性能聊天模型" />
          </div>
          <div>
            <label className="field-label">
              分组类型 <span className="field-req">*</span>
            </label>
            <select
              className="input"
              value={dType}
              onChange={(e) => setDType(e.target.value as GroupType)}
            >
              <option value="model">模型分组（model）</option>
              <option value="tag">标签分组（tag）</option>
              <option value="endpoint">端点分组（endpoint）</option>
            </select>
          </div>
          <div>
            <label className="field-label">{memMeta.label}</label>
            <div className={styles.tagin}>
              {memTags.map((t, i) => (
                <span className={styles.tag} key={`${t}-${i}`}>
                  {t}{' '}
                  <b onClick={() => removeMemTag(i)}>×</b>
                </span>
              ))}
              <input
                type="text"
                placeholder="输入成员后回车"
                value={memInput}
                onChange={(e) => setMemInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    addMemTag();
                  }
                }}
              />
            </div>
            <div className="field-hint">{memMeta.hint}</div>
          </div>
          <div>
            <label className="field-label">优先级</label>
            <input className={`input ${styles.cellmono}`} defaultValue="10" inputMode="numeric" />
            <div className="field-hint">数值越大越优先匹配</div>
          </div>
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>
              启用分组
            </label>
            <label className="switch">
              <input type="checkbox" defaultChecked />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeGroup}>
            取消
          </Button>
          <Button variant="primary">保存分组</Button>
        </div>
      </aside>

      {/* 折扣等级抽屉 */}
      <div
        className={`${styles.drawerScrim} ${rDrawerOpen ? styles.drawerScrimOn : ''}`}
        onClick={closeRatio}
      />
      <aside
        className={`${styles.drawer} ${rDrawerOpen ? styles.drawerOn : ''}`}
        aria-label="折扣等级编辑"
      >
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{rDrawerTitle}</h2>
          <button type="button" className={styles.drawerX} aria-label="关闭" onClick={closeRatio}>
            ×
          </button>
        </div>
        <div className={styles.drawerBody}>
          <div className={styles.notice}>
            <Icon name="info" className={styles.nxIc} />
            <div className={styles.ntxt}>
              等级只决定<b>折扣系数</b>，不再圈定可用模型。系数 1.0 = 原价，越小折扣越大。
            </div>
          </div>
          <div>
            <label className="field-label">
              等级名 <span className="field-req">*</span>
            </label>
            <input className="input" placeholder="例如：vip / svip / 企业版" />
          </div>
          <div>
            <label className="field-label">
              折扣系数 <span className="field-req">*</span>
            </label>
            <input className={`input ${styles.cellmono}`} defaultValue="0.85" inputMode="decimal" />
            <div className="field-hint">客户实付 = 对外模型基准价 × 此系数</div>
          </div>
          <div>
            <label className="field-label">升级门槛方式</label>
            <select
              className="input"
              value={rThreshType}
              onChange={(e) => setRThreshType(e.target.value)}
            >
              <option value="recharge">按累计充值额自动升档</option>
              <option value="pack">绑定付费包等级</option>
              <option value="manual">仅手动指派</option>
            </select>
          </div>
          {threshLabel && (
            <div>
              <label className="field-label">{threshLabel}</label>
              <input className={`input ${styles.cellmono}`} defaultValue="500" inputMode="numeric" />
              <div className="field-hint">客户累计充值达到此额度后自动升入本档</div>
            </div>
          )}
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>
              启用此等级
            </label>
            <label className="switch">
              <input type="checkbox" defaultChecked />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeRatio}>
            取消
          </Button>
          <Button variant="primary">保存等级</Button>
        </div>
      </aside>
    </AdminShell>
  );
}
