'use client';

import { useMemo, useState } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
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
function IcArrow() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 12h14" /><path d="M13 6l6 6-6 6" />
    </svg>
  );
}
function IcLock() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x={5} y={11} width={14} height={9} rx={2} /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  );
}
function IcSplit() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3v18" /><path d="M6 8l6-5 6 5" />
    </svg>
  );
}
function IcDollar() {
  return (
    <svg className={styles.ic} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 2v20" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
    </svg>
  );
}

/* ════════════════════════════ 类型 + Mock 数据 ════════════════════════════ */
type Tier = 'full' | 'enhanced' | 'economy';

interface PoolChannel {
  n: string;
  pr: number;
  wt: number;
  cost: string;
}

interface PublicModel {
  a: string;
  disp: string;
  tier: Tier;
  fam: string;
  in: string;
  out: string;
  b: string;
  offIn: string;
  offOut: string;
  on: boolean;
  warn: boolean;
  chs: PoolChannel[];
}

const TIER_MAP: Record<Tier, { cls: string; lab: string }> = {
  full: { cls: styles.tierFull, lab: '旗舰' },
  enhanced: { cls: styles.tierEnh, lab: '增强' },
  economy: { cls: styles.tierEco, lab: '经济' },
};

const PUBS: PublicModel[] = [
  { a: 'opus-4.8', disp: 'Claude Opus 4.8', tier: 'full', fam: 'opus-4.8', in: '15.0', out: '75.0', b: 'claude-opus-4-8-full', offIn: '15.0', offOut: '75.0', on: true, warn: false,
    chs: [{ n: 'Anthropic 官方', pr: 9, wt: 6, cost: '2.0' }, { n: '第三方聚合 A', pr: 6, wt: 4, cost: '1.8' }, { n: '第三方聚合 B', pr: 6, wt: 3, cost: '0.1' }] },
  { a: 'opus-4.8-增强', disp: 'Claude Opus 4.8 增强版', tier: 'enhanced', fam: 'opus-4.8', in: '9.0', out: '45.0', b: 'claude-opus-4-8-enh', offIn: '15.0', offOut: '75.0', on: true, warn: false,
    chs: [{ n: '第三方聚合 A', pr: 6, wt: 5, cost: '1.2' }, { n: '第三方聚合 B', pr: 6, wt: 3, cost: '0.6' }] },
  { a: 'opus-4.8-经济', disp: 'Claude Opus 4.8 经济版', tier: 'economy', fam: 'opus-4.8', in: '3.0', out: '15.0', b: 'claude-opus-4-8-eco', offIn: '15.0', offOut: '75.0', on: true, warn: true,
    chs: [{ n: '第三方聚合 B', pr: 6, wt: 5, cost: '' }, { n: 'Bedrock Claude', pr: 5, wt: 3, cost: '0.9' }] },
  { a: 'gpt-4o', disp: 'GPT-4o', tier: 'full', fam: 'gpt-4o', in: '2.5', out: '10.0', b: 'gpt-4o-2024-11-20', offIn: '2.5', offOut: '10.0', on: true, warn: false,
    chs: [{ n: 'OpenAI 主通道', pr: 10, wt: 8, cost: '1.0' }, { n: 'Azure OpenAI', pr: 9, wt: 7, cost: '0.95' }, { n: '第三方聚合 A', pr: 6, wt: 4, cost: '0.85' }] },
  { a: 'gpt-4o-mini', disp: 'GPT-4o mini', tier: 'economy', fam: 'gpt-4o', in: '0.15', out: '0.60', b: 'gpt-4o-mini-2024', offIn: '0.15', offOut: '0.60', on: true, warn: false,
    chs: [{ n: 'OpenAI 主通道', pr: 10, wt: 8, cost: '0.06' }, { n: 'Azure OpenAI', pr: 9, wt: 6, cost: '0.055' }] },
  { a: 'o1', disp: 'OpenAI o1', tier: 'full', fam: 'o1', in: '15.0', out: '60.0', b: 'o1-2024-12', offIn: '15.0', offOut: '60.0', on: true, warn: false,
    chs: [{ n: 'OpenAI 主通道', pr: 10, wt: 8, cost: '6.0' }, { n: 'Azure OpenAI', pr: 9, wt: 6, cost: '5.8' }] },
  { a: 'o3-mini', disp: 'OpenAI o3-mini', tier: 'economy', fam: 'o3', in: '1.1', out: '4.4', b: 'o3-mini-2025', offIn: '1.1', offOut: '4.4', on: true, warn: false,
    chs: [{ n: 'OpenAI 主通道', pr: 10, wt: 8, cost: '0.45' }] },
  { a: 'claude-3.5-sonnet', disp: 'Claude 3.5 Sonnet', tier: 'full', fam: 'claude-3.5', in: '3.0', out: '15.0', b: 'claude-3-5-sonnet-lat', offIn: '3.0', offOut: '15.0', on: true, warn: false,
    chs: [{ n: 'Anthropic 官方', pr: 9, wt: 6, cost: '1.2' }, { n: 'Bedrock Claude', pr: 6, wt: 4, cost: '1.1' }] },
  { a: 'claude-3.5-haiku', disp: 'Claude 3.5 Haiku', tier: 'economy', fam: 'claude-3.5', in: '0.8', out: '4.0', b: 'claude-3-5-haiku-lat', offIn: '0.8', offOut: '4.0', on: true, warn: false,
    chs: [{ n: 'Anthropic 官方', pr: 9, wt: 6, cost: '0.32' }] },
  { a: 'gemini-2.0-flash', disp: 'Gemini 2.0 Flash', tier: 'enhanced', fam: 'gemini', in: '0.10', out: '0.40', b: 'gemini-2.0-flash-exp', offIn: '0.10', offOut: '0.40', on: true, warn: false,
    chs: [{ n: 'Google Vertex', pr: 8, wt: 5, cost: '0.05' }, { n: '第三方聚合 A', pr: 6, wt: 3, cost: '0.04' }] },
  { a: 'gemini-1.5-pro', disp: 'Gemini 1.5 Pro', tier: 'full', fam: 'gemini', in: '1.25', out: '5.0', b: 'gemini-1-5-pro-002', offIn: '1.25', offOut: '5.0', on: true, warn: false,
    chs: [{ n: 'Google Vertex', pr: 8, wt: 5, cost: '0.6' }] },
  { a: 'deepseek-v3', disp: 'DeepSeek V3', tier: 'enhanced', fam: 'deepseek', in: '0.20', out: '0.80', b: 'deepseek-v3-0324', offIn: '0.20', offOut: '0.80', on: true, warn: false,
    chs: [{ n: 'DeepSeek 直连', pr: 6, wt: 4, cost: '0.10' }, { n: 'Together AI', pr: 5, wt: 3, cost: '0.12' }] },
  { a: 'deepseek-r1', disp: 'DeepSeek R1', tier: 'full', fam: 'deepseek', in: '0.70', out: '2.5', b: 'deepseek-r1-0528', offIn: '0.70', offOut: '2.5', on: true, warn: true,
    chs: [{ n: 'DeepSeek 直连', pr: 6, wt: 4, cost: '' }, { n: '第三方聚合 B', pr: 6, wt: 3, cost: '0.3' }] },
  { a: 'mistral-large', disp: 'Mistral Large', tier: 'enhanced', fam: 'mistral', in: '2.0', out: '6.0', b: 'mistral-large-2411', offIn: '2.0', offOut: '6.0', on: false, warn: false,
    chs: [{ n: 'Mistral 直连', pr: 5, wt: 3, cost: '1.0' }] },
  { a: 'grok-2', disp: 'Grok 2', tier: 'full', fam: 'grok', in: '2.0', out: '10.0', b: 'grok-2-1212', offIn: '2.0', offOut: '10.0', on: false, warn: false,
    chs: [] },
];

/* 真实渠道列表（与 channels 渠道实体对齐）—— 渠道池从这里多选 */
const REAL_CHANNELS: { n: string; ty: string }[] = [
  { n: 'OpenAI 主通道', ty: 'OpenAI 官方' }, { n: 'Azure OpenAI', ty: 'Azure' }, { n: 'Anthropic 官方', ty: 'Anthropic' },
  { n: 'Google Vertex', ty: 'Vertex' }, { n: '第三方聚合 A', ty: 'OpenAI 兼容' }, { n: '第三方聚合 B', ty: 'OpenAI 兼容' },
  { n: 'Bedrock Claude', ty: 'Anthropic' }, { n: 'DeepSeek 直连', ty: 'OpenAI 兼容' }, { n: 'Together AI', ty: 'OpenAI 兼容' },
  { n: 'Mistral 直连', ty: 'Mistral' }, { n: 'Groq 高速', ty: 'OpenAI 兼容' },
];

const B_CANDIDATES = [
  'claude-opus-4-8-full', 'claude-opus-4-8-enh', 'claude-opus-4-8-eco',
  'gpt-4o-2024-11-20', 'gemini-2.0-flash-exp', 'deepseek-v3',
];

/* 供应商成本（ChannelModelCost：渠道×真实模型 B 成本倍率手填，按 B 分组） */
interface CostRow {
  ch: string;
  cost: string;
  upd: string;
  on: boolean;
  unset: boolean;
}
interface CostGroup {
  b: string;
  official: string;
  a: string[];
  rows: CostRow[];
}

const COST_GROUPS: CostGroup[] = [
  { b: 'claude-opus-4-8-full', official: '$15.0/$75.0', a: ['opus-4.8'], rows: [
    { ch: '供应商X · 直连主通道', cost: '2.0', upd: '2026-06-19 14:02', on: true, unset: false },
    { ch: '供应商Y · 聚合通道', cost: '1.8', upd: '2026-06-18 09:41', on: true, unset: false },
    { ch: '供应商Z · 低价兜底', cost: '0.1', upd: '2026-06-15 22:10', on: true, unset: false },
  ] },
  { b: 'claude-opus-4-8-enh', official: '$9.0/$45.0', a: ['opus-4.8-增强'], rows: [
    { ch: '供应商Y · 聚合通道', cost: '1.2', upd: '2026-06-19 10:30', on: true, unset: false },
    { ch: '供应商Z · 低价兜底', cost: '0.6', upd: '2026-06-17 18:55', on: true, unset: false },
  ] },
  { b: 'claude-opus-4-8-eco', official: '$3.0/$15.0', a: ['opus-4.8-经济'], rows: [
    { ch: '供应商Z · 低价兜底', cost: '', upd: '—', on: false, unset: true },
    { ch: '供应商X · 直连主通道', cost: '0.9', upd: '2026-06-16 08:20', on: true, unset: false },
  ] },
  { b: 'gpt-4o-2024-11-20', official: '$2.5/$10.0', a: ['gpt-4o'], rows: [
    { ch: 'OpenAI 官方', cost: '1.0', upd: '2026-06-20 07:00', on: true, unset: false },
    { ch: '供应商Y · 聚合通道', cost: '0.85', upd: '2026-06-19 16:12', on: true, unset: false },
  ] },
  { b: 'gemini-2.0-flash-exp', official: '$0.1/$0.4', a: ['gemini-2.0-flash'], rows: [
    { ch: 'Google Vertex', cost: '1.0', upd: '2026-06-20 06:30', on: true, unset: false },
    { ch: '供应商Y · 聚合通道', cost: '0.7', upd: '2026-06-18 11:48', on: true, unset: false },
  ] },
  { b: 'deepseek-r1-0528', official: '$0.7/$2.5', a: ['deepseek-r1'], rows: [
    { ch: '供应商X · 直连主通道', cost: '', upd: '—', on: false, unset: true },
    { ch: '供应商Z · 低价兜底', cost: '0.3', upd: '2026-06-14 20:05', on: true, unset: false },
  ] },
];

/* 模型元数据 */
type ModelState = 'on' | 'off' | 'pre';
interface ModelMeta {
  nm: string;
  ven: string;
  ctx: string;
  st: ModelState;
  in: string;
  out: string;
  caps: string[];
}
const MODELS: ModelMeta[] = [
  { nm: 'gpt-4o', ven: 'OpenAI', ctx: '128K', st: 'on', in: '$2.50', out: '$7.50', caps: ['chat', 'vision', 'tools'] },
  { nm: 'gpt-4o-mini', ven: 'OpenAI', ctx: '128K', st: 'on', in: '$0.15', out: '$0.60', caps: ['chat', 'tools'] },
  { nm: 'o1-preview', ven: 'OpenAI', ctx: '128K', st: 'on', in: '$7.50', out: '$30.0', caps: ['chat', 'reason'] },
  { nm: 'gpt-4-32k', ven: 'OpenAI', ctx: '32K', st: 'off', in: '$30.0', out: '$60.0', caps: ['chat'] },
  { nm: 'claude-3-5-sonnet', ven: 'Anthropic', ctx: '200K', st: 'on', in: '$1.50', out: '$7.50', caps: ['chat', 'vision', 'tools'] },
  { nm: 'claude-3-opus', ven: 'Anthropic', ctx: '200K', st: 'on', in: '$7.50', out: '$37.5', caps: ['chat', 'vision'] },
  { nm: 'claude-2.1', ven: 'Anthropic', ctx: '200K', st: 'off', in: '$8.00', out: '$24.0', caps: ['chat'] },
  { nm: 'gemini-1.5-pro', ven: 'Google', ctx: '1M', st: 'on', in: '$1.25', out: '$5.00', caps: ['chat', 'vision'] },
  { nm: 'gemini-1.5-flash', ven: 'Google', ctx: '1M', st: 'on', in: '$0.07', out: '$0.30', caps: ['chat'] },
  { nm: 'gemini-2.0-flash', ven: 'Google', ctx: '1M', st: 'pre', in: '$0.10', out: '$0.40', caps: ['chat', 'vision'] },
  { nm: 'deepseek-chat', ven: 'DeepSeek', ctx: '64K', st: 'on', in: '$0.14', out: '$0.28', caps: ['chat', 'tools'] },
  { nm: 'deepseek-reasoner', ven: 'DeepSeek', ctx: '64K', st: 'pre', in: '$0.55', out: '$2.19', caps: ['chat', 'reason'] },
  { nm: 'mistral-large', ven: 'Mistral', ctx: '128K', st: 'on', in: '$2.00', out: '$6.00', caps: ['chat', 'tools'] },
  { nm: 'command-r-plus', ven: 'Cohere', ctx: '128K', st: 'on', in: '$2.50', out: '$10.0', caps: ['chat', 'rag'] },
  { nm: 'text-embedding-3', ven: 'OpenAI', ctx: '8K', st: 'on', in: '$0.02', out: '—', caps: ['embed'] },
];
const ST_MAP: Record<ModelState, { cls: string; tone: string; lab: string }> = {
  on: { cls: 'b-suc', tone: '--color-success', lab: '上架' },
  off: { cls: 'b-dan', tone: '--color-danger', lab: '下架' },
  pre: { cls: 'b-warn', tone: '--color-warning', lab: '预发布' },
};

/* 供应商元数据 */
type VendorState = 'on' | 'off';
interface Vendor {
  nm: string;
  site: string;
  cnt: number;
  st: VendorState;
}
const VENDORS: Vendor[] = [
  { nm: 'OpenAI', site: 'platform.openai.com', cnt: 5, st: 'on' },
  { nm: 'Anthropic', site: 'console.anthropic.com', cnt: 3, st: 'on' },
  { nm: 'Google', site: 'ai.google.dev', cnt: 3, st: 'on' },
  { nm: 'DeepSeek', site: 'platform.deepseek.com', cnt: 2, st: 'on' },
  { nm: 'Mistral', site: 'console.mistral.ai', cnt: 1, st: 'on' },
  { nm: 'Cohere', site: 'dashboard.cohere.com', cnt: 1, st: 'off' },
];

/* 同步预览 mock */
const SYNC_ADD = ['gpt-4o-2024-11-20', 'o1-mini', 'claude-3-5-haiku', 'gemini-2.0-flash', 'deepseek-reasoner', 'mistral-large-2411'];
const SYNC_UPD = ['gpt-4o · 上下文 128K → 256K', 'claude-3-5-sonnet · 输出价调整', 'gemini-1.5-pro · 能力新增 vision'];
const SYNC_MISS = ['gpt-4-32k（拟下架）', 'claude-2.1（拟下架）'];

/* ════════════════════════════ 小展示组件 ════════════════════════════ */
function TierBadge({ t }: { t: Tier }) {
  const m = TIER_MAP[t];
  return (
    <span className={`${styles.tier} ${m.cls}`}>
      <span className={styles.dot} />
      {m.lab}
    </span>
  );
}

function MapCell({ b, warn }: { b: string; warn: boolean }) {
  return (
    <>
      <div className={styles.maprow}>
        <span className={styles.mapGlyph}><IcArrow /></span>
        <span className={styles.mapb}>{b}</span>
        <span className={styles.privTag}>客户不可见</span>
      </div>
      {warn && <span className={styles.costWarn}>成本未配</span>}
    </>
  );
}

function PriceCell({ i, o }: { i: string; o: string }) {
  return (
    <div className={styles.pricecell}>
      <span>输入 ×{i}</span>
      <span>输出 ×{o}</span>
    </div>
  );
}

function PoolCell({ chs }: { chs: PoolChannel[] }) {
  if (!chs.length) return <span className={styles.costWarn}>未绑渠道</span>;
  const sorted = chs.slice().sort((a, b) => b.pr - a.pr);
  const primary = sorted[0].n;
  const backups = chs.length - 1;
  const costs = chs.filter((c) => c.cost !== '').map((c) => parseFloat(c.cost));
  let spread = '';
  if (costs.length > 1) {
    const mn = Math.min(...costs);
    const mx = Math.max(...costs);
    spread = `成本 ${mn.toFixed(2)}~${mx.toFixed(2)}×`;
  }
  const hasUnset = chs.some((c) => c.cost === '');
  return (
    <div className={styles.chspool}>
      <span className={styles.chsCnt}>{chs.length} 渠道</span>
      <span className={styles.chsMain}>主·{primary}{backups ? ` +${backups}备` : ''}</span>
      {spread && <span className={styles.chsSpread}>{spread}</span>}
      {hasUnset && <span className={styles.costWarn}>缺成本</span>}
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
  const m = ST_MAP[st];
  return (
    <span className={`badge ${m.cls}`}>
      <span className="dot" style={{ background: `var(${m.tone})` }} />{m.lab}
    </span>
  );
}

function VendorStateBadge({ st }: { st: VendorState }) {
  return st === 'on' ? (
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
  return (
    <div className={styles.caps}>
      {arr.map((c) => <span key={c} className={styles.cap}>{c}</span>)}
    </div>
  );
}

/* ════════════════════════════ 渠道池选择器（抽屉内） ════════════════════════════ */
interface PickedChannel {
  pr: string;
  wt: string;
  cost: string;
}

function ChsPicker({
  picks,
  onToggle,
  onField,
}: {
  picks: Record<string, PickedChannel>;
  onToggle: (name: string, on: boolean) => void;
  onField: (name: string, field: keyof PickedChannel, value: string) => void;
}) {
  const pickedNames = Object.keys(picks);
  let summary: React.ReactNode;
  if (!pickedNames.length) {
    summary = <span className={styles.costWarn}>未选任何渠道 —— 该对外模型暂无法供货</span>;
  } else {
    const sorted = pickedNames.slice().sort((a, b) => (parseInt(picks[b].pr) || 0) - (parseInt(picks[a].pr) || 0));
    const main = sorted[0];
    const bks = sorted.length - 1;
    const costs = pickedNames.filter((n) => picks[n].cost !== '').map((n) => parseFloat(picks[n].cost));
    let costTxt: React.ReactNode = null;
    if (costs.length > 1) {
      const mn = Math.min(...costs);
      const mx = Math.max(...costs);
      costTxt = <>，成本极差 <b>{mn.toFixed(2)}~{mx.toFixed(2)}×</b>（{(mx / mn).toFixed(1)}x 差异）</>;
    }
    const miss = pickedNames.filter((n) => picks[n].cost === '').length;
    summary = (
      <>
        已选 <b>{pickedNames.length}</b> 渠道：主通道 <b>{main}</b>
        {bks ? `，${bks} 个备用容灾` : ''}
        {costTxt}
        {miss ? <>，<span className={styles.costWarn}>{miss} 个渠道缺成本</span></> : null}
      </>
    );
  }
  return (
    <>
      <div className={styles.chsPicker}>
        {REAL_CHANNELS.map((ch) => {
          const on = !!picks[ch.n];
          const p = picks[ch.n];
          return (
            <div key={ch.n} className={`${styles.chsRow}${on ? ' ' + styles.on : ''}`}>
              <input
                type="checkbox"
                className={styles.ck}
                checked={on}
                onChange={(e) => onToggle(ch.n, e.target.checked)}
                aria-label={`选择渠道 ${ch.n}`}
              />
              <span className={styles.nm}>{ch.n}<span className={styles.ty}>{ch.ty}</span></span>
              <div className={styles.ctl}>
                <label>优先级</label>
                <input className={styles.chsMini} value={on ? p.pr : ''} disabled={!on}
                  onChange={(e) => onField(ch.n, 'pr', e.target.value)} aria-label="优先级" />
              </div>
              <div className={styles.ctl}>
                <label>权重</label>
                <input className={styles.chsMini} value={on ? p.wt : ''} disabled={!on}
                  onChange={(e) => onField(ch.n, 'wt', e.target.value)} aria-label="权重" />
              </div>
              <div className={styles.ctl}>
                <label>成本×</label>
                <input className={styles.chsMini} value={on ? p.cost : ''} disabled={!on} placeholder="未配"
                  onChange={(e) => onField(ch.n, 'cost', e.target.value)} aria-label="成本倍率" />
              </div>
            </div>
          );
        })}
      </div>
      <div className={styles.chsSummary}>{summary}</div>
    </>
  );
}

/* ════════════════════════════ 主组件 ════════════════════════════ */
type TabKey = 'public' | 'costs' | 'models' | 'vendors';

interface PubForm {
  a: string;
  disp: string;
  tier: Tier;
  fam: string;
  in: string;
  out: string;
  b: string;
  on: boolean;
}
const INIT_PUB_FORM: PubForm = {
  a: '', disp: '', tier: 'economy', fam: '', in: '3.0', out: '15.0', b: '', on: true,
};

/**
 * ModelsAdminPage — 模型/供应商管理（S6 admin/models-admin.html 工程化）。
 * 四 Tab：对外模型 / 供应商成本 / 模型元数据 / 供应商元数据；
 * 含缺失检测条、上游同步预览模态、对外模型编辑抽屉（A→B 映射 + 渠道池）、品质拆分引导抽屉。
 */
export function ModelsAdminPage() {
  const [tab, setTab] = useState<TabKey>('public');

  // 缺失检测条
  const [detectOn, setDetectOn] = useState(false);
  // 同步预览模态
  const [syncOpen, setSyncOpen] = useState(false);
  // 品质拆分引导抽屉
  const [splitOpen, setSplitOpen] = useState(false);

  // 对外模型筛选
  const [pTier, setPTier] = useState('');
  const [pFamily, setPFamily] = useState('');
  const [pState, setPState] = useState('');
  const [pSearch, setPSearch] = useState('');

  // 供应商成本筛选
  const [cChannel, setCChannel] = useState('');
  const [cModel, setCModel] = useState('');
  const [cUnset, setCUnset] = useState(false);

  // 模型元数据筛选
  const [fVendor, setFVendor] = useState('');
  const [fState, setFState] = useState('');
  const [fSearch, setFSearch] = useState('');

  // 供应商元数据筛选
  const [vSearch, setVSearch] = useState('');

  // 对外模型抽屉
  const [pubOpen, setPubOpen] = useState(false);
  const [pubTitle, setPubTitle] = useState('新建对外模型');
  const [pubForm, setPubForm] = useState<PubForm>(INIT_PUB_FORM);
  const [pubOff, setPubOff] = useState<{ offIn: string; offOut: string } | null>(null);
  const [picks, setPicks] = useState<Record<string, PickedChannel>>({});

  /* ── 对外模型筛选 ── */
  const filteredPubs = useMemo(() => {
    return PUBS.filter((r) => {
      if (pTier && r.tier !== pTier) return false;
      if (pFamily && r.fam !== pFamily) return false;
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
  }, [pTier, pFamily, pState, pSearch]);

  /* ── 供应商成本筛选（按 B 分组，组内过滤渠道） ── */
  const filteredCosts = useMemo(() => {
    return COST_GROUPS
      .filter((g) => !cModel || g.b === cModel)
      .map((g) => ({
        ...g,
        rows: g.rows.filter((r) => {
          if (cChannel && r.ch !== cChannel) return false;
          if (cUnset && !r.unset) return false;
          return true;
        }),
      }))
      .filter((g) => g.rows.length > 0);
  }, [cChannel, cModel, cUnset]);

  /* ── 模型元数据筛选 ── */
  const filteredModels = useMemo(() => {
    return MODELS.filter((r) => {
      if (fVendor && r.ven !== fVendor) return false;
      if (fState && ST_MAP[r.st].lab !== fState) return false;
      if (fSearch && !r.nm.toLowerCase().includes(fSearch.toLowerCase())) return false;
      return true;
    });
  }, [fVendor, fState, fSearch]);

  /* ── 供应商元数据筛选 ── */
  const filteredVendors = useMemo(() => {
    return VENDORS.filter((v) => !vSearch || v.nm.toLowerCase().includes(vSearch.toLowerCase()));
  }, [vSearch]);

  /* ── 抽屉操作 ── */
  function openPub(title: string, row?: PublicModel) {
    setPubTitle(title);
    if (row) {
      setPubForm({ a: row.a, disp: row.disp, tier: row.tier, fam: row.fam, in: row.in, out: row.out, b: row.b, on: row.on });
      setPubOff({ offIn: row.offIn, offOut: row.offOut });
      const next: Record<string, PickedChannel> = {};
      row.chs.forEach((c) => { next[c.n] = { pr: String(c.pr), wt: String(c.wt), cost: c.cost }; });
      setPicks(next);
    } else {
      setPubForm(INIT_PUB_FORM);
      setPubOff(null);
      setPicks({});
    }
    setPubOpen(true);
  }
  function closePub() { setPubOpen(false); }

  function toggleChs(name: string, on: boolean) {
    setPicks((prev) => {
      const next = { ...prev };
      if (on) next[name] = next[name] ?? { pr: '', wt: '', cost: '' };
      else delete next[name];
      return next;
    });
  }
  function setChsField(name: string, field: keyof PickedChannel, value: string) {
    setPicks((prev) => ({ ...prev, [name]: { ...prev[name], [field]: value } }));
  }
  function useOfficial() {
    if (pubOff) setPubForm((f) => ({ ...f, in: pubOff.offIn, out: pubOff.offOut }));
  }

  const totalCostRows = filteredCosts.reduce((s, g) => s + g.rows.length, 0);

  return (
    <AdminShell
      activeId="models"
      title="模型/供应商"
      crumb={['管理后台', '资源管理', '模型/供应商']}
      actions={
        <>
          <Button variant="sec" size="sm" onClick={() => setDetectOn(true)}>缺失模型检测</Button>
          <Button variant="primary" size="sm" onClick={() => setSyncOpen(true)}>上游模型同步</Button>
        </>
      }
    >
      {/* 检测结果条 */}
      {detectOn && (
        <section className={styles.detectBar}>
          <IcSearch />
          <span className={styles.txt}>
            缺失检测完成：上游存在 <b>6</b> 个本地未登记的模型，<b>2</b> 个本地模型在上游已下线。建议执行同步对齐。
          </span>
          <span className={styles.grow} />
          <Button variant="sec" size="sm" onClick={() => setDetectOn(false)}>关闭</Button>
        </section>
      )}

      {/* Tab */}
      <div className={styles.tabs}>
        <button className={`${styles.tab}${tab === 'public' ? ' ' + styles.on : ''}`} onClick={() => setTab('public')}>对外模型</button>
        <button className={`${styles.tab}${tab === 'costs' ? ' ' + styles.on : ''}`} onClick={() => setTab('costs')}>供应商成本</button>
        <button className={`${styles.tab}${tab === 'models' ? ' ' + styles.on : ''}`} onClick={() => setTab('models')}>模型元数据</button>
        <button className={`${styles.tab}${tab === 'vendors' ? ' ' + styles.on : ''}`} onClick={() => setTab('vendors')}>供应商元数据</button>
      </div>

      {/* ════ 对外模型 Tab ════ */}
      {tab === 'public' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              对外模型即对客户售卖的商品（公开名 <b>A</b>）。基准售价对所有客户恒定、不随内部供应商切换波动；最终扣费 = 基准价 × 分组折扣系数。底仓映射 <b>A 到 B</b> 仅平台内部可见，<b>客户永不可见 B</b>。品质不同请拆成独立对外模型分别定价，切勿混入同一兜底池。
            </span>
          </section>

          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={pTier} onChange={(e) => setPTier(e.target.value)}>
              <option value="">全部品质</option>
              <option value="full">旗舰</option>
              <option value="enhanced">增强</option>
              <option value="economy">经济</option>
            </select>
            <select className={styles.sel} value={pFamily} onChange={(e) => setPFamily(e.target.value)}>
              <option value="">全部家族</option>
              <option>opus-4.8</option>
              <option>gpt-4o</option>
              <option>claude-3.5</option>
              <option>gemini</option>
              <option>deepseek</option>
            </select>
            <select className={styles.sel} value={pState} onChange={(e) => setPState(e.target.value)}>
              <option value="">全部状态</option>
              <option>上架</option>
              <option>下架</option>
            </select>
            <input className={styles.srch} type="search" placeholder="搜索对外名 / 展示名"
              value={pSearch} onChange={(e) => setPSearch(e.target.value)} />
            <span className={styles.grow} />
            <Button variant="sec" size="sm" onClick={() => setSplitOpen(true)}>品质拆分引导</Button>
            <Button variant="primary" size="sm" onClick={() => openPub('新建对外模型')}>新建对外模型</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={`${styles.tableWrap} ${styles.tableWrapWide}`}>
              <table>
                <thead>
                  <tr>
                    <th>对外名 (A)</th><th>品质档</th><th>家族</th>
                    <th>基准售价（输入×输出倍率）</th><th>底仓映射 A 到 B（B 不可见）</th>
                    <th>供应渠道池</th><th>状态</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredPubs.map((r) => (
                    <tr key={r.a}>
                      <td className={styles.cellmono}>
                        {r.a}
                        <div className={`muted ${styles.dispSub}`}>{r.disp}</div>
                      </td>
                      <td><TierBadge t={r.tier} /></td>
                      <td className={`${styles.cellmono} muted`}>{r.fam}</td>
                      <td><PriceCell i={r.in} o={r.out} /></td>
                      <td><MapCell b={r.b} warn={r.warn} /></td>
                      <td><PoolCell chs={r.chs} /></td>
                      <td><PubStateBadge on={r.on} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          <a onClick={() => openPub('编辑对外模型', r)}>编辑</a>
                          <a>{r.on ? '下架' : '上架'}</a>
                          <a className={styles.dang}>删除</a>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.pager}>
              <span>共 {filteredPubs.length} 个对外模型（含 opus-4.8 家族 3 档）</span>
            </div>
          </section>
        </div>
      )}

      {/* ════ 供应商成本 Tab ════ */}
      {tab === 'costs' && (
        <div>
          <section className={`${styles.noteBar} nx-fade`}>
            <IcInfo />
            <span className={styles.txt}>
              成本倍率挂在「供应商渠道 × 真实模型 <b>B</b>」上，<b>仅平台内部、手动填写</b>（成本 = tokens × 倍率，不乘分组折扣）。它只影响利润计算（利润 = 售价 − 成本），<b>不影响对客户售价</b>。同一真实模型 B 在多个供应商各记各的进货价，下方按 B 分组并排呈现成本差异。
            </span>
          </section>

          <section className={`${styles.toolbar} nx-fade`}>
            <select className={styles.sel} value={cChannel} onChange={(e) => setCChannel(e.target.value)}>
              <option value="">全部供应商渠道</option>
              <option>供应商X · 直连主通道</option>
              <option>供应商Y · 聚合通道</option>
              <option>供应商Z · 低价兜底</option>
              <option>OpenAI 官方</option>
              <option>Google Vertex</option>
            </select>
            <select className={styles.sel} value={cModel} onChange={(e) => setCModel(e.target.value)}>
              <option value="">全部真实模型 B</option>
              <option>claude-opus-4-8-full</option>
              <option>gpt-4o-2024-11-20</option>
              <option>gemini-2.0-flash-exp</option>
              <option>deepseek-v3</option>
            </select>
            <label className={styles.swInline}>
              <input type="checkbox" checked={cUnset} onChange={(e) => setCUnset(e.target.checked)} />
              <span>仅看未配成本</span>
            </label>
            <span className={styles.grow} />
            <Button variant="sec" size="sm">批量标记生效</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            {filteredCosts.map((g) => {
              const nums = g.rows.filter((r) => !r.unset && r.cost !== '').map((r) => parseFloat(r.cost));
              let spread: React.ReactNode;
              if (nums.length > 1) {
                const mn = Math.min(...nums);
                const mx = Math.max(...nums);
                spread = <>成本极差 <b>{mn.toFixed(2)}</b> ~ <b>{mx.toFixed(2)}</b>（{(mx / mn).toFixed(1)}x 差异）</>;
              } else {
                spread = '待补全更多供应商成本';
              }
              return (
                <div key={g.b} className={styles.costGrp}>
                  <div className={styles.costGrphead}>
                    <span className={styles.bname}>{g.b}</span>
                    <span className={styles.privTag}>真实模型 B · 客户不可见</span>
                    <span className={styles.meta}>关联对外模型：{g.a.join('、')}</span>
                    <span className={styles.spread}>{spread}</span>
                  </div>
                  <div className={styles.tableWrap}>
                    <table>
                      <thead>
                        <tr>
                          <th>供应商渠道</th><th>成本倍率（手填）</th><th>对标官方价</th>
                          <th>最后更新</th><th>状态</th><th>操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {g.rows.map((r) => (
                          <tr key={g.b + r.ch}>
                            <td>{r.ch}</td>
                            <td>
                              <input className={`${styles.costin}${r.unset ? ' ' + styles.unset : ''}`}
                                defaultValue={r.cost} placeholder="未配" aria-label="成本倍率" />
                            </td>
                            <td className={`${styles.cellmono} muted`}>{g.official}</td>
                            <td className="muted">{r.upd}</td>
                            <td>
                              {r.unset ? (
                                <span className={styles.costWarn}>未配成本，利润无法计算</span>
                              ) : r.on ? (
                                <span className="badge b-suc">
                                  <span className="dot" style={{ background: 'var(--color-success)' }} />已配
                                </span>
                              ) : (
                                <span className="badge b-info">
                                  <span className="dot" style={{ background: 'var(--color-info)' }} />已停用
                                </span>
                              )}
                            </td>
                            <td>
                              <div className={styles.rowActs}>
                                <a>保存</a>
                                <a>{r.on ? '停用' : '启用'}</a>
                                <a className={styles.dang}>删除</a>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              );
            })}
            <div className={styles.pager}>
              <span>共 {filteredCosts.length} 个真实模型 B、{totalCostRows} 条渠道成本配置</span>
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
              <option>OpenAI</option>
              <option>Anthropic</option>
              <option>Google</option>
              <option>DeepSeek</option>
              <option>Mistral</option>
              <option>Cohere</option>
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
            <Button variant="sec" size="sm">新增模型</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>模型名</th><th>供应商</th><th>上下文长度</th><th>状态</th>
                    <th>输入价</th><th>输出价</th><th>能力</th><th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredModels.map((r) => (
                    <tr key={r.nm}>
                      <td className={styles.cellmono}>{r.nm}</td>
                      <td className="muted">{r.ven}</td>
                      <td className={styles.cellmono}>{r.ctx}</td>
                      <td><StateBadge st={r.st} /></td>
                      <td className={styles.cellmono}>{r.in}</td>
                      <td className={styles.cellmono}>{r.out}</td>
                      <td><Caps arr={r.caps} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          <a>编辑</a>
                          <a>{r.st === 'on' ? '下架' : '上架'}</a>
                          <a className={styles.dang}>删除</a>
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
            <Button variant="sec" size="sm">新增供应商</Button>
          </section>

          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr><th>供应商名称</th><th>官网</th><th>模型数</th><th>状态</th><th>操作</th></tr>
                </thead>
                <tbody>
                  {filteredVendors.map((v) => (
                    <tr key={v.nm}>
                      <td>{v.nm}</td>
                      <td className={`${styles.cellmono} muted`}>{v.site}</td>
                      <td className={styles.cellmono}>{v.cnt}</td>
                      <td><VendorStateBadge st={v.st} /></td>
                      <td>
                        <div className={styles.rowActs}>
                          <a>编辑</a>
                          <a>{v.st === 'on' ? '禁用' : '启用'}</a>
                          <a className={styles.dang}>删除</a>
                        </div>
                      </td>
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
      <div className={`${styles.drawerScrim}${pubOpen ? ' ' + styles.on : ''}`} onClick={closePub} />
      <aside className={`${styles.drawer}${pubOpen ? ' ' + styles.on : ''}`} aria-label="对外模型编辑">
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>{pubTitle}</h2>
          <button className={styles.drawerX} onClick={closePub} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          {/* 商品信息 */}
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>商品信息</p>
            <div>
              <label className="field-label">对外名 A <span className="field-req">*</span></label>
              <input className={`input ${styles.cellmono}`} placeholder="例如：opus-4.8-经济"
                value={pubForm.a} onChange={(e) => setPubForm((f) => ({ ...f, a: e.target.value }))} />
              <div className={styles.fieldHint}>客户可见、用于定价的公开名；建后唯一不可改</div>
            </div>
            <div style={{ marginTop: 'var(--space-3)' }}>
              <label className="field-label">展示名</label>
              <input className="input" placeholder="例如：Claude Opus 4.8 经济版"
                value={pubForm.disp} onChange={(e) => setPubForm((f) => ({ ...f, disp: e.target.value }))} />
            </div>
            <div className={styles.row2} style={{ marginTop: 'var(--space-3)' }}>
              <div>
                <label className="field-label">品质档 <span className="field-req">*</span></label>
                <select className="input" value={pubForm.tier}
                  onChange={(e) => setPubForm((f) => ({ ...f, tier: e.target.value as Tier }))}>
                  <option value="full">旗舰</option>
                  <option value="enhanced">增强</option>
                  <option value="economy">经济</option>
                </select>
              </div>
              <div>
                <label className="field-label">家族归属</label>
                <input className={`input ${styles.cellmono}`} placeholder="例如：opus-4.8"
                  value={pubForm.fam} onChange={(e) => setPubForm((f) => ({ ...f, fam: e.target.value }))} />
              </div>
            </div>
            <div className={styles.splitHint}>
              <IcSplit />
              <span className={styles.t}>
                同一 <b>family</b> 可建多个品质档（如 <b>opus-4.8</b> / <b>opus-4.8-增强</b> / <b>opus-4.8-经济</b>），各自独立定价、分别售卖。需要一键生成多档可用顶部「品质拆分引导」。
              </span>
            </div>
          </div>

          {/* 基准售价 */}
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>基准售价（DECISIONS §4：售价挂对外模型 A）</p>
            <div className={styles.priceref}>
              <IcDollar />
              <span className={styles.t}>
                {pubOff ? (
                  <>官方默认价 <b>输入 ×{pubOff.offIn} / 输出 ×{pubOff.offOut}</b>（预置自 model-data.js，可改）</>
                ) : (
                  <>官方默认价 <b>输入 ×15.0 / 输出 ×75.0</b>（来源 anthropic.com/pricing，已自动带入下方，可改）</>
                )}
                {pubOff && (
                  <button type="button" className={styles.lnk} onClick={useOfficial}>恢复官方价</button>
                )}
              </span>
            </div>
            <div className={styles.row2}>
              <div>
                <label className="field-label">基准输入价倍率</label>
                <input className={`input ${styles.cellmono}`} value={pubForm.in}
                  onChange={(e) => setPubForm((f) => ({ ...f, in: e.target.value }))} />
              </div>
              <div>
                <label className="field-label">基准输出价倍率</label>
                <input className={`input ${styles.cellmono}`} value={pubForm.out}
                  onChange={(e) => setPubForm((f) => ({ ...f, out: e.target.value }))} />
              </div>
            </div>
            <div className={styles.fieldHint}>保存后即时生效，对所有客户恒定，不随内部供应商切换波动。</div>
          </div>

          {/* 底仓映射 A 到 B */}
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>底仓映射 A 到 B（仅平台内部）</p>
            <div>
              <label className="field-label">映射目标 · 真实上游模型 B <span className="field-req">*</span></label>
              <input className={`input ${styles.cellmono}`} list="bCandidates" placeholder="例如：claude-opus-4-8-eco"
                value={pubForm.b} onChange={(e) => setPubForm((f) => ({ ...f, b: e.target.value }))} />
              <datalist id="bCandidates">
                {B_CANDIDATES.map((b) => <option key={b} value={b} />)}
              </datalist>
            </div>
            <div className={styles.privBox}>
              <IcLock />
              <span className={styles.t}>
                此映射 <b>客户不可见，仅平台内部</b>。真实上游模型 B 不出现在任何客户视图（模型列表 / 候选下拉 / 日志），客户永远只看到对外名 A。此处只挂 <b>同品质</b> 供应商；品质不同请拆成独立对外模型，切勿混入兜底池。
              </span>
            </div>
          </div>

          {/* 供应渠道池 */}
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>供应渠道池（A 方案：一对多渠道 · 优先级 + 权重 + 容灾切换）</p>
            <div className={styles.fieldHint} style={{ marginBottom: 'var(--space-3)' }}>
              勾选哪些真实渠道为这个 B 供货。请求按 <b>优先级</b> 高者优先、同级按 <b>权重</b> 分流；主通道异常自动切下一优先级（new-api 原生重试逻辑）。每渠道单独记该 B 的成本倍率，进利润计算。
            </div>
            <ChsPicker picks={picks} onToggle={toggleChs} onField={setChsField} />
          </div>

          {/* 上架开关 */}
          <div className={styles.drawerSec}>
            <div className={styles.swRow}>
              <label className="field-label" style={{ margin: 0 }}>上架（对所有客户可见可用）</label>
              <label className="switch">
                <input type="checkbox" checked={pubForm.on}
                  onChange={(e) => setPubForm((f) => ({ ...f, on: e.target.checked }))} />
                <span className="track" />
                <span className="thumb" />
              </label>
            </div>
          </div>
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={closePub}>取消</Button>
          <Button variant="primary" onClick={closePub}>保存</Button>
        </div>
      </aside>

      {/* ════ 品质拆分引导 抽屉 ════ */}
      <div className={`${styles.drawerScrim}${splitOpen ? ' ' + styles.on : ''}`} onClick={() => setSplitOpen(false)} />
      <aside className={`${styles.drawer}${splitOpen ? ' ' + styles.on : ''}`} aria-label="品质拆分引导">
        <div className={styles.drawerHead}>
          <h2 className={styles.drawerTitle}>品质拆分引导</h2>
          <button className={styles.drawerX} onClick={() => setSplitOpen(false)} aria-label="关闭">×</button>
        </div>
        <div className={styles.drawerBody}>
          <div className={styles.splitHint}>
            <IcSplit />
            <span className={styles.t}>
              把一个模型族（如 <b>opus-4.8</b>）拆成 旗舰 / 增强 / 经济 三条独立对外模型、三个价、各自映射不同的真实上游 B。<b>不同品质必须映射不同上游</b>，同档内渠道须同品质。
            </span>
          </div>
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>第 1 步 · 基底模型族</p>
            <input className={`input ${styles.cellmono}`} defaultValue="opus-4.8" placeholder="例如：opus-4.8" />
          </div>
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>第 2 步 · 选择要生成的品质档</p>
            <div className={styles.swRow} style={{ marginBottom: 'var(--space-2)' }}>
              <label className="field-label" style={{ margin: 0 }}>旗舰 <span className={`${styles.cellmono} muted`}>opus-4.8</span></label>
              <label className="switch">
                <input type="checkbox" defaultChecked /><span className="track" /><span className="thumb" />
              </label>
            </div>
            <div className={styles.swRow} style={{ marginBottom: 'var(--space-2)' }}>
              <label className="field-label" style={{ margin: 0 }}>增强 <span className={`${styles.cellmono} muted`}>opus-4.8-增强</span></label>
              <label className="switch">
                <input type="checkbox" defaultChecked /><span className="track" /><span className="thumb" />
              </label>
            </div>
            <div className={styles.swRow}>
              <label className="field-label" style={{ margin: 0 }}>经济 <span className={`${styles.cellmono} muted`}>opus-4.8-经济</span></label>
              <label className="switch">
                <input type="checkbox" defaultChecked /><span className="track" /><span className="thumb" />
              </label>
            </div>
          </div>
          <div className={styles.drawerSec}>
            <p className={styles.secLabel}>第 3 步 · 每档售价 + 映射真实模型 B</p>
            <div className={styles.fieldHint}>系统按命名约定预填对外名；逐档填基准价并选不同的 B（若两档选同一 B 将红色拦截）。</div>
          </div>
        </div>
        <div className={styles.drawerFoot}>
          <Button variant="ghost" onClick={() => setSplitOpen(false)}>取消</Button>
          <Button variant="primary" onClick={() => setSplitOpen(false)}>生成 3 条对外模型</Button>
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
            <div className={styles.syncSec}>
              <h5><span className="dot" style={{ background: 'var(--color-success)' }} />将新增（{SYNC_ADD.length}）</h5>
              <div className={styles.syncList}>
                {SYNC_ADD.map((s) => (
                  <div key={s} className={styles.syncItem}>
                    <span>{s}</span><span className={`${styles.tag} ${styles.tagAdd}`}>NEW</span>
                  </div>
                ))}
              </div>
            </div>
            <div className={styles.syncSec}>
              <h5><span className="dot" style={{ background: 'var(--color-info)' }} />将更新（{SYNC_UPD.length}）</h5>
              <div className={styles.syncList}>
                {SYNC_UPD.map((s) => (
                  <div key={s} className={styles.syncItem}>
                    <span>{s}</span><span className={`${styles.tag} ${styles.tagUpd}`}>UPD</span>
                  </div>
                ))}
              </div>
            </div>
            <div className={styles.syncSec}>
              <h5><span className="dot" style={{ background: 'var(--color-danger)' }} />上游已缺失（{SYNC_MISS.length}）</h5>
              <div className={styles.syncList}>
                {SYNC_MISS.map((s) => (
                  <div key={s} className={styles.syncItem}>
                    <span>{s}</span><span className={`${styles.tag} ${styles.tagMiss}`}>MISS</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className={styles.modalFoot}>
            <Button variant="ghost" onClick={() => setSyncOpen(false)}>取消</Button>
            <Button variant="primary" onClick={() => setSyncOpen(false)}>确认执行同步</Button>
          </div>
        </div>
      </div>
    </AdminShell>
  );
}
