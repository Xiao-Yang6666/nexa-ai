import { Fragment, type ReactNode } from 'react';
import type { LegalDocument } from '../model/legal-content';
import styles from './LegalDoc.module.css';

/**
 * 轻量行内标记解析：**强调** 与 [文本](href)。
 * 只支持这两种受控标记，避免引入 markdown 依赖，且 DOM 安全（不注入 HTML）。
 */
function renderInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  // 先按链接切分，再在非链接片段里处理 **强调**
  const linkRe = /\[([^\]]+)\]\(([^)]+)\)/g;
  let last = 0;
  let key = 0;
  let m: RegExpExecArray | null;
  while ((m = linkRe.exec(text)) !== null) {
    if (m.index > last) {
      nodes.push(<Fragment key={key++}>{renderStrong(text.slice(last, m.index), key)}</Fragment>);
    }
    nodes.push(
      <a key={key++} href={m[2]}>
        {m[1]}
      </a>,
    );
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    nodes.push(<Fragment key={key++}>{renderStrong(text.slice(last), key)}</Fragment>);
  }
  return nodes;
}

/** 处理 **强调** 片段。 */
function renderStrong(text: string, baseKey: number): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /\*\*([^*]+)\*\*/g;
  let last = 0;
  let k = baseKey * 1000;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(<Fragment key={k++}>{text.slice(last, m.index)}</Fragment>);
    out.push(<strong key={k++}>{m[1]}</strong>);
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(<Fragment key={k++}>{text.slice(last)}</Fragment>);
  return out;
}

export interface LegalDocProps {
  /** 法律文档数据（服务协议 / 隐私政策） */
  doc: LegalDocument;
}

/**
 * LegalDoc — 法律文档展示页（web-public/agreement.html、privacy.html 工程化）。
 *
 * 统一的「文档头 + 侧边目录 TOC + 正文 prose」三段式布局，
 * agreement / privacy 共用同一组件，仅传入不同的 LegalDocument 数据。
 * 纯展示页（无接口），目录锚点跳转用原生 hash 链接。
 *
 * @param doc 文档内容（pill/标题/导语/章节）
 */
export function LegalDoc({ doc }: LegalDocProps) {
  return (
    <main className={styles.page}>
      <div className="wrap">
        <div className={styles.docHead}>
          <span className={styles.docPill}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M9 12l2 2 4-4" />
              <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {doc.pill}
          </span>
          <h1>{doc.title}</h1>
          <p className={styles.docSub}>{doc.subtitle}</p>
          <p className={styles.docMeta}>{doc.meta}</p>
        </div>

        <div className={styles.docLayout}>
          <aside className={styles.toc}>
            <p className={styles.tocTitle}>目录</p>
            <nav>
              {doc.sections.map((s) => (
                <a key={s.id} href={`#${s.id}`}>
                  {s.num}. {s.title}
                </a>
              ))}
            </nav>
          </aside>

          <article className={styles.prose}>
            {doc.sections.map((s) => (
              <section key={s.id} id={s.id}>
                <h2>
                  <span className={styles.num}>{s.num}</span>
                  {s.title}
                </h2>
                {s.blocks.map((b, i) => {
                  if (b.type === 'p') {
                    return <p key={i}>{renderInline(b.text)}</p>;
                  }
                  if (b.type === 'callout') {
                    return (
                      <div key={i} className={styles.callout}>
                        {renderInline(b.text)}
                      </div>
                    );
                  }
                  return (
                    <ul key={i}>
                      {b.items.map((it, j) => (
                        <li key={j}>{renderInline(it)}</li>
                      ))}
                    </ul>
                  );
                })}
              </section>
            ))}
          </article>
        </div>
      </div>
    </main>
  );
}
