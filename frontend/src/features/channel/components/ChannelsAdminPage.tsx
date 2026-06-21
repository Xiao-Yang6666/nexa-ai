'use client';

import { useState, useRef, type KeyboardEvent } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import styles from './ChannelsAdminPage.module.css';

/* ── 排序箭头 SVG ── */
function SortArr() {
  return (
    <svg viewBox="0 0 16 16" width={11} height={11} fill="none" stroke="currentColor"
      strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 6l3-3 3 3M5 10l3 3 3-3" />
    </svg>
  );
}

/* ── 渠道数据（mock，开发期静态） ── */
type ChannelStatus = 'on' | 'man' | 'auto' | 'warn';
interface Channel {
  id: number;
  name: string;
  type: string;
  st: ChannelStatus;
  pr: number;
  wt: number;
  used: string;
  bal: string;
  lat: number;
}

const CHANNELS: Channel[] = [
  { id: 101, name: 'OpenAI 主通道',   type: 'OpenAI 官方',   st: 'on',   pr: 10, wt: 8, used: '$4,120', bal: '$880',   lat: 392 },
  { id: 102, name: 'Azure OpenAI',    type: 'Azure OpenAI',  st: 'on',   pr: 9,  wt: 7, used: '$3,560', bal: '$1,440', lat: 418 },
  { id: 103, name: 'Anthropic 官方',  type: 'Anthropic',     st: 'on',   pr: 9,  wt: 6, used: '$2,980', bal: '$2,020', lat: 451 },
  { id: 104, name: 'Google Vertex',   type: 'Google Vertex', st: 'on',   pr: 8,  wt: 5, used: '$1,740', bal: '$1,260', lat: 368 },
  { id: 105, name: '第三方聚合 A',    type: 'OpenAI 兼容',   st: 'on',   pr: 6,  wt: 4, used: '$1,210', bal: '$790',   lat: 512 },
  { id: 106, name: '第三方聚合 B',    type: 'OpenAI 兼容',   st: 'on',   pr: 6,  wt: 3, used: '$960',   bal: '$540',   lat: 548 },
  { id: 107, name: 'Mistral 直连',    type: 'Mistral',       st: 'warn', pr: 5,  wt: 3, used: '$420',   bal: '$580',   lat: 634 },
  { id: 108, name: 'Cohere 备用',     type: 'Cohere',        st: 'auto', pr: 4,  wt: 2, used: '$210',   bal: '$290',   lat: 0   },
  { id: 109, name: '第三方聚合 C',    type: 'OpenAI 兼容',   st: 'auto', pr: 4,  wt: 2, used: '$180',   bal: '$120',   lat: 0   },
  { id: 110, name: 'Azure 备区',      type: 'Azure OpenAI',  st: 'auto', pr: 7,  wt: 4, used: '$1,020', bal: '$480',   lat: 0   },
  { id: 111, name: 'OpenAI 备通道',   type: 'OpenAI 官方',   st: 'on',   pr: 8,  wt: 5, used: '$2,310', bal: '$1,690', lat: 401 },
  { id: 112, name: '内测沙箱',        type: 'OpenAI 兼容',   st: 'man',  pr: 2,  wt: 1, used: '$0',     bal: '$1,000', lat: 0   },
  { id: 113, name: 'DeepSeek 直连',   type: 'OpenAI 兼容',   st: 'on',   pr: 6,  wt: 4, used: '$680',   bal: '$1,320', lat: 288 },
  { id: 114, name: 'Together AI',     type: 'OpenAI 兼容',   st: 'on',   pr: 5,  wt: 3, used: '$540',   bal: '$960',   lat: 472 },
  { id: 115, name: 'Groq 高速',       type: 'OpenAI 兼容',   st: 'on',   pr: 7,  wt: 5, used: '$390',   bal: '$610',   lat: 142 },
  { id: 116, name: 'Bedrock Claude',  type: 'Anthropic',     st: 'man',  pr: 6,  wt: 4, used: '$1,480', bal: '$520',   lat: 0   },
];

const ST_MAP: Record<ChannelStatus, { cls: string; tone: string; lab: string }> = {
  on:   { cls: 'b-suc',     tone: '--color-success',    lab: '启用'     },
  man:  { cls: 'b-neutral', tone: '--color-text-muted', lab: '手动禁用' },
  auto: { cls: 'b-dan',     tone: '--color-danger',     lab: '自动禁用' },
  warn: { cls: 'b-warn',    tone: '--color-warning',    lab: '限流告警' },
};

function StatusBadge({ st }: { st: ChannelStatus }) {
  const s = ST_MAP[st];
  return (
    <span className={`badge ${s.cls}`}>
      <span className="dot" style={{ background: `var(${s.tone})` }} />
      {s.lab}
    </span>
  );
}

function LatCell({ lat }: { lat: number }) {
  if (!lat) return <span className={`muted ${styles.cellmono}`}>—</span>;
  return <span className={styles.cellmono}>{lat} ms</span>;
}

/* ── 编辑抽屉表单状态 ── */
interface DrawerForm {
  name: string;
  type: string;
  baseUrl: string;
  modelMap: string;
  priority: string;
  weight: string;
  enabled: boolean;
}

const INIT_FORM: DrawerForm = {
  name: '', type: 'OpenAI 官方', baseUrl: '',
  modelMap: '', priority: '10', weight: '5', enabled: true,
};

const TYPE_OPTIONS = [
  'OpenAI 官方', 'Azure OpenAI', 'Anthropic',
  'Google Vertex', 'OpenAI 兼容', 'Mistral', 'Cohere',
];

/**
 * ChannelsAdminPage — 渠道管理（S6 admin/channels.html 工程化）。
 * 筛选栏 + 批量操作栏 + 密集表格 + 分页 + 编辑抽屉。
 */
export function ChannelsAdminPage() {
  // 筛选
  const [fType, setFType] = useState('');
  const [fStatus, setFStatus] = useState('');
  const [fSearch, setFSearch] = useState('');

  // 批量选择
  const [selected, setSelected] = useState<Set<number>>(new Set());

  // 抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'new' | 'edit'>('new');
  const [form, setForm] = useState<DrawerForm>(INIT_FORM);

  // API Key 标签
  const [keys, setKeys] = useState<string[]>(['sk-****a91f', 'sk-****7d2c']);
  const keyInputRef = useRef<HTMLInputElement>(null);

  /* ── 筛选逻辑 ── */
  const filtered = CHANNELS.filter((r) => {
    if (fType && r.type !== fType) return false;
    if (fStatus) {
      const stLabel = ST_MAP[r.st].lab;
      if (stLabel !== fStatus) return false;
    }
    if (fSearch) {
      const q = fSearch.toLowerCase();
      if (!r.name.toLowerCase().includes(q) && !String(r.id).includes(q)) return false;
    }
    return true;
  });

  /* ── 批量选择 ── */
  const allChecked = filtered.length > 0 && filtered.every((r) => selected.has(r.id));
  function toggleAll(checked: boolean) {
    setSelected(checked ? new Set(filtered.map((r) => r.id)) : new Set());
  }
  function toggleOne(id: number, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      checked ? next.add(id) : next.delete(id);
      return next;
    });
  }

  /* ── 抽屉 ── */
  function openDrawer(mode: 'new' | 'edit') {
    setDrawerMode(mode);
    setForm(INIT_FORM);
    setKeys(['sk-****a91f', 'sk-****7d2c']);
    setDrawerOpen(true);
  }
  function closeDrawer() { setDrawerOpen(false); }

  /* ── Key 标签输入 ── */
  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && keyInputRef.current?.value.trim()) {
      e.preventDefault();
      setKeys((prev) => [...prev, keyInputRef.current!.value.trim()]);
      keyInputRef.current!.value = '';
    }
  }
  function removeKey(i: number) {
    setKeys((prev) => prev.filter((_, idx) => idx !== i));
  }

  const selCount = selected.size;

  return (
    <AdminShell
      activeId="channels"
      title="渠道管理"
      crumb={['管理后台', '资源管理', '渠道管理']}
      actions={
        <Button variant="primary" size="sm" onClick={() => openDrawer('new')}>
          新建渠道
        </Button>
      }
    >
      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={fType} onChange={(e) => setFType(e.target.value)}>
          <option value="">全部类型</option>
          {TYPE_OPTIONS.map((t) => <option key={t}>{t}</option>)}
        </select>
        <select className={styles.sel} value={fStatus} onChange={(e) => setFStatus(e.target.value)}>
          <option value="">全部状态</option>
          <option>启用</option>
          <option>手动禁用</option>
          <option>自动禁用</option>
          <option>限流告警</option>
        </select>
        <input
          className={styles.srch}
          type="search"
          placeholder="搜索渠道名 / Base URL / ID"
          value={fSearch}
          onChange={(e) => setFSearch(e.target.value)}
        />
        <span className={styles.grow} />
        <Button variant="sec" size="sm">测试连通性</Button>
      </section>

      {/* BatchBar */}
      <section className={`${styles.batchbar}${selCount > 0 ? ' ' + styles.on : ''}`}>
        <span className={styles.cnt}>已选 {selCount} 项</span>
        <span className={styles.grow} />
        <Button variant="sec" size="sm">批量启用</Button>
        <Button variant="sec" size="sm">批量禁用</Button>
        <Button variant="danger" size="sm">批量删除</Button>
      </section>

      {/* 表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th style={{ width: 34 }}>
                  <input
                    type="checkbox"
                    checked={allChecked}
                    onChange={(e) => toggleAll(e.target.checked)}
                    aria-label="全选"
                  />
                </th>
                <th className={styles.sortable}>ID <span className={styles.arr}><SortArr /></span></th>
                <th className={styles.sortable}>渠道名 <span className={styles.arr}><SortArr /></span></th>
                <th>类型</th>
                <th>状态</th>
                <th className={styles.sortable}>优先级 <span className={styles.arr}><SortArr /></span></th>
                <th className={styles.sortable}>权重 <span className={styles.arr}><SortArr /></span></th>
                <th>已用 / 余额</th>
                <th className={styles.sortable}>响应延迟 <span className={styles.arr}><SortArr /></span></th>
                <th>最后测试</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.id}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selected.has(r.id)}
                      onChange={(e) => toggleOne(r.id, e.target.checked)}
                      aria-label={`选择 ${r.name}`}
                    />
                  </td>
                  <td className={`${styles.cellmono} muted`}>{r.id}</td>
                  <td>{r.name}</td>
                  <td className="muted">{r.type}</td>
                  <td><StatusBadge st={r.st} /></td>
                  <td className={styles.cellmono}>{r.pr}</td>
                  <td className={styles.cellmono}>{r.wt}</td>
                  <td>
                    <span className={styles.cellmono}>{r.used}</span>{' '}
                    <span className="muted">/ {r.bal}</span>
                  </td>
                  <td><LatCell lat={r.lat} /></td>
                  <td className={`${styles.cellmono} muted`}>
                    06-20 04:{String(10 + (r.id % 50)).padStart(2, '0')}
                  </td>
                  <td>
                    <div className={styles.rowActs}>
                      <a>测试</a>
                      <a onClick={() => openDrawer('edit')}>编辑</a>
                      <a>{r.st === 'man' || r.st === 'auto' ? '启用' : '禁用'}</a>
                      <a className={styles.dang}>删除</a>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {filtered.length} 个渠道</span>
          <div className={styles.pg}>
            <button>‹</button>
            <button className={styles.on}>1</button>
            <button>2</button>
            <button>›</button>
          </div>
        </div>
      </section>

      {/* 抽屉遮罩 */}
      <div
        className={`${styles.drawerScrim}${drawerOpen ? ' ' + styles.on : ''}`}
        onClick={closeDrawer}
      />

      {/* 编辑抽屉 */}
      <aside
        className={`${styles.drawer}${drawerOpen ? ' ' + styles.on : ''}`}
        aria-label="渠道编辑"
      >
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{drawerMode === 'new' ? '新建渠道' : '编辑渠道'}</h2>
          <button className={styles.drawerX} onClick={closeDrawer} aria-label="关闭">×</button>
        </div>

        <div className={styles.drawerBody}>
          <div>
            <label className="field-label">渠道名 <span className="field-req">*</span></label>
            <input
              className="input"
              placeholder="例如：OpenAI 主通道"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div>
            <label className="field-label">渠道类型 <span className="field-req">*</span></label>
            <select
              className="input"
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
            >
              {TYPE_OPTIONS.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="field-label">Base URL</label>
            <input
              className={`input ${styles.cellmono}`}
              placeholder="https://api.openai.com/v1"
              value={form.baseUrl}
              onChange={(e) => setForm((f) => ({ ...f, baseUrl: e.target.value }))}
            />
          </div>
          <div>
            <label className="field-label">API Keys（可多个）</label>
            <div className={styles.tagin}>
              {keys.map((k, i) => (
                <span key={i} className={styles.tag}>
                  {k} <b onClick={() => removeKey(i)}>×</b>
                </span>
              ))}
              <input
                ref={keyInputRef}
                type="text"
                placeholder="粘贴 Key 后回车"
                onKeyDown={handleKeyDown}
              />
            </div>
            <div className="field-hint">支持多 Key 轮询，自动剔除失效 Key</div>
          </div>
          <div>
            <label className="field-label">模型映射</label>
            <textarea
              className={`input ${styles.taArea}`}
              placeholder={'gpt-4o=gpt-4o-2024-08-06\nclaude-3.5=claude-3-5-sonnet-latest'}
              value={form.modelMap}
              onChange={(e) => setForm((f) => ({ ...f, modelMap: e.target.value }))}
            />
          </div>
          <div className={styles.row2}>
            <div>
              <label className="field-label">优先级</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.priority}
                onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
              />
            </div>
            <div>
              <label className="field-label">权重</label>
              <input
                className={`input ${styles.cellmono}`}
                value={form.weight}
                onChange={(e) => setForm((f) => ({ ...f, weight: e.target.value }))}
              />
            </div>
          </div>
          <div className={styles.swRow}>
            <label className="field-label" style={{ margin: 0 }}>启用渠道</label>
            <label className="switch">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
              />
              <span className="track" />
              <span className="thumb" />
            </label>
          </div>
        </div>

        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closeDrawer}>取消</Button>
          <Button variant="primary">保存渠道</Button>
        </div>
      </aside>
    </AdminShell>
  );
}
