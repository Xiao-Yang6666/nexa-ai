import type { ReactNode } from 'react';

/**
 * 文档站呈现型组件集合（无状态、可服务端渲染）。
 * 全部 1:1 复用 06_prototype docs-shell.css 的同名 class，零裸色值。
 */

/** 面包屑：文档 / {分组} / {当前页}。 */
export function Breadcrumb({ group, page }: { group: string; page: string }) {
  return (
    <nav className="breadcrumb reveal">
      <a href="/docs">文档</a>
      <span className="sep">/</span>
      <span>{group}</span>
      <span className="sep">/</span>
      <span>{page}</span>
    </nav>
  );
}

/** 页面主标题 h1（带 id=top，进 reveal）。 */
export function PageTitle({ children }: { children: ReactNode }) {
  return (
    <h1 className="reveal" id="top">
      {children}
    </h1>
  );
}

/** 引导段 lede。 */
export function Lede({ children }: { children: ReactNode }) {
  return <p className="lede reveal">{children}</p>;
}

/** 区段 h2（进 ToC + 锚点）。 */
export function H2({ id, children }: { id: string; children: ReactNode }) {
  return <h2 id={id}>{children}</h2>;
}

/** 子区段 h3。 */
export function H3({ id, children }: { id: string; children: ReactNode }) {
  return <h3 id={id}>{children}</h3>;
}

/** 一个可 reveal 的 section 容器。 */
export function Section({ children }: { children: ReactNode }) {
  return <section className="reveal">{children}</section>;
}

/** 行内链接（站内/站外）。 */
export function InlineLink({
  href,
  children,
}: {
  href: string;
  children: ReactNode;
}) {
  return (
    <a className="inline" href={href}>
      {children}
    </a>
  );
}

/** HTTP 方法 + 端点路径栏。 */
export function Endpoint({
  method,
  path,
}: {
  method: 'GET' | 'POST' | 'DELETE';
  path: string;
}) {
  const cls = method.toLowerCase();
  return (
    <div className="endpoint">
      <span className={`method ${cls}`}>{method}</span>
      <span className="path">{path}</span>
    </div>
  );
}

/** 提示块 Callout。 */
export type CalloutTone = 'info' | 'warn' | 'success' | 'danger';

const CALLOUT_ICON: Record<CalloutTone, ReactNode> = {
  info: (
    <svg viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 7.5v.5" />
    </svg>
  ),
  warn: (
    <svg viewBox="0 0 24 24">
      <path d="M12 3 2 20h20L12 3Z" />
      <path d="M12 10v4M12 17v.5" />
    </svg>
  ),
  success: (
    <svg viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12 3 3 5-6" />
    </svg>
  ),
  danger: (
    <svg viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v6M12 16v.5" />
    </svg>
  ),
};

/**
 * Callout 提示块。原型 docs-shell.css 内置 .callout.info / .callout.warn 两态；
 * success/danger 复用 warn 的结构（语义色由 className 区分，深底已 token 化）。
 */
export function Callout({
  tone = 'info',
  children,
}: {
  tone?: CalloutTone;
  children: ReactNode;
}) {
  // docs-shell.css 只定义了 .info/.warn；success/danger 落到 warn 视觉档（暖色警示），
  // info/success 用 info 档（teal），以保持零裸色值不新增样式。
  const visualCls = tone === 'success' ? 'info' : tone === 'danger' ? 'warn' : tone;
  return (
    <div className={`callout ${visualCls}`}>
      {CALLOUT_ICON[tone]}
      <div className="co-body">{children}</div>
    </div>
  );
}

/** 参数表 / 字段表的一行。 */
export interface ParamRow {
  /** 字段名（mono 高亮） */
  name: string;
  /** 类型（mono） */
  type: string;
  /** 是否必填 */
  required?: boolean;
  /** 说明（可含 ReactNode，如行内 code） */
  desc: ReactNode;
}

/**
 * 参数表 ParamTable（接口页核心）。
 * 列：字段名 | 类型 | 必填 | 说明。复用原型 .tbl-wrap 表格样式。
 */
export function ParamTable({ rows }: { rows: ParamRow[] }) {
  return (
    <div className="tbl-wrap">
      <table>
        <thead>
          <tr>
            <th>字段</th>
            <th>类型</th>
            <th>必填</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.name}>
              <td className="pn">{r.name}</td>
              <td className="ty">{r.type}</td>
              <td className={r.required ? 'req-yes' : 'req-no'}>
                {r.required ? '必填' : '可选'}
              </td>
              <td>{r.desc}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** 通用表格（错误码表 / 限流字段表等），列头自定义。 */
export function DocTable({
  head,
  rows,
}: {
  head: string[];
  /** 每行单元格（ReactNode），列数与 head 对齐 */
  rows: ReactNode[][];
}) {
  return (
    <div className="tbl-wrap">
      <table>
        <thead>
          <tr>
            {head.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((cells, i) => (
            <tr key={i}>
              {cells.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** quickstart 的编号步骤列表容器。 */
export function Steps({ children }: { children: ReactNode }) {
  return <ol className="steps">{children}</ol>;
}

/** 单个编号步骤。 */
export function Step({ children }: { children: ReactNode }) {
  return <li className="step">{children}</li>;
}
