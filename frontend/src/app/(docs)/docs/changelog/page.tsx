import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import {
  Breadcrumb, DocsShell, H2, Lede, PageTitle, Section,
} from '@/features/docs';
import type { TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '更新日志 · Nexa·AI 文档',
  description: 'Nexa·AI 网关的模型接入、能力增强与问题修复历史。',
};

const TOC: TocItem[] = [
  { id: 'legend', label: '图例', level: 2 },
  { id: 'timeline', label: '版本时间线', level: 2 },
];

/** 变更类型：与原型 .tl-tag.add / .opt / .fix 一致。 */
type ChangeKind = 'add' | 'opt' | 'fix';

const KIND_LABEL: Record<ChangeKind, string> = {
  add: '新增',
  opt: '优化',
  fix: '修复',
};

interface ChangeEntry {
  kind: ChangeKind;
  desc: ReactNode;
}

interface Release {
  version: string;
  date: string;
  entries: ChangeEntry[];
}

/** 时间线节点（圆环 + 中心点）SVG。 */
function TimelineNode() {
  return (
    <svg viewBox="0 0 20 20">
      <circle className="ring" cx="10" cy="10" r="8" />
      <circle className="dot" cx="10" cy="10" r="4" />
    </svg>
  );
}

const RELEASES: Release[] = [
  {
    version: 'v2.8.0',
    date: '2026-06-12',
    entries: [
      { kind: 'add', desc: <>接入 <code>gemini-2.0-flash</code> 与 <code>gemini-2.0-pro</code>，支持百万级长上下文。</> },
      { kind: 'opt', desc: '流式响应首 token 延迟（TTFT）平均降低约 22%。' },
    ],
  },
  {
    version: 'v2.7.2',
    date: '2026-05-28',
    entries: [
      { kind: 'fix', desc: <>修复 <code>embeddings</code> 接口在传入 <code>dimensions</code> 时偶发维度未截断的问题。</> },
      { kind: 'fix', desc: <>修正部分 <code>429</code> 响应缺失 <code>Retry-After</code> 头的情况。</> },
    ],
  },
  {
    version: 'v2.7.0',
    date: '2026-05-15',
    entries: [
      { kind: 'add', desc: <>图像接口接入 <code>flux-pro</code> 与 <code>flux-schnell</code>，支持更高分辨率出图。</> },
      { kind: 'add', desc: <>聊天补全支持 <code>stream_options.include_usage</code>，可在流式末尾返回 token 用量。</> },
      { kind: 'opt', desc: <>统一各厂商的 <code>finish_reason</code> 取值，行为与 OpenAI 对齐。</> },
    ],
  },
  {
    version: 'v2.6.1',
    date: '2026-04-30',
    entries: [
      { kind: 'fix', desc: <>修复函数调用场景下 <code>tool_calls</code> 在跨厂商模型间字段映射不一致的问题。</> },
      { kind: 'opt', desc: <>错误响应统一补齐 <code>error.code</code> 字段，便于客户端做分支处理。</> },
    ],
  },
  {
    version: 'v2.6.0',
    date: '2026-04-18',
    entries: [
      { kind: 'add', desc: <>接入 <code>claude-3-7-sonnet</code>，强化代码与推理能力。</> },
      { kind: 'add', desc: <>限流响应回携 <code>x-ratelimit-*</code> 系列头，实时暴露配额余量。</> },
    ],
  },
  {
    version: 'v2.5.3',
    date: '2026-03-29',
    entries: [
      { kind: 'fix', desc: <>修复长上下文请求在上游超时后未正确返回 <code>504</code> 的问题。</> },
      { kind: 'opt', desc: '降低冷启动场景下的连接建立耗时。' },
    ],
  },
  {
    version: 'v2.5.0',
    date: '2026-03-10',
    entries: [
      { kind: 'add', desc: <>引入分组（Group）级配额管理，支持组内多 Key 共享 RPM/TPM。</> },
      { kind: 'add', desc: <>新增 <code>/v1/models</code> 按 owner 过滤参数，便于按厂商发现模型。</> },
      { kind: 'opt', desc: <>控制台用量页支持按模型 / Key 维度筛选。</> },
    ],
  },
];

/** /docs/changelog：更新日志页（按时间倒序的版本时间线）。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="资源" page="更新日志" />
      <PageTitle>更新日志</PageTitle>
      <Lede>
        记录 Nexa·AI 网关的模型接入、能力增强与问题修复。我们遵循向后兼容原则——除非另行公告，既有接口与字段保持稳定。下方按时间倒序排列各版本。
      </Lede>

      <Section>
        <H2 id="legend">图例</H2>
        <p>每条变更以标签标注类型：</p>
        <ul className="tl-list" style={{ marginTop: 'var(--space-3)' }}>
          {(['add', 'opt', 'fix'] as ChangeKind[]).map((k) => (
            <li key={k} className="tl-entry">
              <span className={`tl-tag ${k}`}>{KIND_LABEL[k]}</span>
              <span>
                {k === 'add' && '引入全新的模型、端点或能力。'}
                {k === 'opt' && '对既有能力的性能、体验或行为改进。'}
                {k === 'fix' && '修正缺陷或不符合预期的行为。'}
              </span>
            </li>
          ))}
        </ul>
      </Section>

      <Section>
        <H2 id="timeline">版本时间线</H2>
        <ul className="timeline">
          {RELEASES.map((rel) => (
            <li className="tl-item" key={rel.version}>
              <span className="tl-line" />
              <span className="tl-node">
                <TimelineNode />
              </span>
              <div className="tl-meta">
                <span className="tl-ver">{rel.version}</span>
                <span className="tl-date">{rel.date}</span>
              </div>
              <ul className="tl-list">
                {rel.entries.map((e, i) => (
                  <li className="tl-entry" key={i}>
                    <span className={`tl-tag ${e.kind}`}>{KIND_LABEL[e.kind]}</span>
                    <span>{e.desc}</span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </Section>
    </DocsShell>
  );
}
