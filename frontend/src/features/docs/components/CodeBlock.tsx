'use client';

import { useState, type ReactNode } from 'react';
import { highlight } from './highlight';

/** 单个语言面板：lang 标识 + tab 显示文字 + 代码原文。 */
export interface CodePane {
  /** 语言标识，用于高亮（curl/bash/python/node/go/json/text） */
  lang: string;
  /** tab 显示名（如 'curl'、'Python'、'Node'、'Go'、'响应 JSON'） */
  label: string;
  /** 代码原文（多行字符串） */
  code: string;
}

/**
 * 代码块组件（文档端第一公民）。
 *
 * 视觉/交互 1:1 复用 06_prototype docs-shell.css 的 .codeblock 类系统：
 *  - 头部栏：多语言 tab + 复制按钮；
 *  - 代码体：行内 token 高亮（手工 .t-* 类，零裸色值），横向滚动不限宽；
 *  - 复制反馈：1.6s 内显示「已复制」，符合 §10.1 single 微交互动效。
 *
 * @param panes  语言面板列表（按数组顺序展示 tab）。
 *
 * @example
 * <CodeBlock panes={[
 *   { lang: 'curl',   label: 'curl',   code: 'curl https://...' },
 *   { lang: 'python', label: 'Python', code: 'from openai...' },
 * ]}/>
 */
export function CodeBlock({ panes }: { panes: CodePane[] }) {
  const [activeIdx, setActiveIdx] = useState(0);
  const [copied, setCopied] = useState(false);
  const active = panes[activeIdx];

  const handleCopy = async () => {
    if (!active) return;
    const text = active.code;
    const done = () => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    };
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try {
          document.execCommand('copy');
        } finally {
          document.body.removeChild(ta);
        }
      }
      done();
    } catch {
      done();
    }
  };

  return (
    <div className="codeblock">
      <div className="cb-bar">
        {panes.map((p, i) => (
          <button
            key={p.lang + i}
            type="button"
            className={`cb-tab${i === activeIdx ? ' is-active' : ''}`}
            data-lang={p.lang}
            aria-selected={i === activeIdx}
            onClick={() => setActiveIdx(i)}
          >
            {p.label}
          </button>
        ))}
        <button
          type="button"
          className={`cb-copy${copied ? ' is-copied' : ''}`}
          aria-label="复制代码"
          onClick={handleCopy}
        >
          <svg className="ic-copy" viewBox="0 0 24 24">
            <rect x="9" y="9" width="11" height="11" rx="2" />
            <path d="M5 15V5a2 2 0 0 1 2-2h10" />
          </svg>
          <svg className="ic-check" viewBox="0 0 24 24">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          <span className="cb-copy-label">{copied ? '已复制' : '复制'}</span>
        </button>
      </div>
      <div className="cb-body">
        {panes.map((p, i) => (
          <div
            key={p.lang + i}
            className={`cb-pane${i === activeIdx ? ' is-active' : ''}`}
            data-lang={p.lang}
          >
            <pre>
              <code>{renderHighlighted(p.code, p.lang)}</code>
            </pre>
          </div>
        ))}
      </div>
    </div>
  );
}

/** 把代码高亮为 ReactNode（行内 span + 换行）。 */
function renderHighlighted(code: string, lang: string): ReactNode {
  const lines = highlight(code, lang);
  return lines.map((tokens, lineIdx) => (
    <span key={lineIdx}>
      {tokens.map((t, i) =>
        t.cls ? (
          <span key={i} className={t.cls}>
            {t.text}
          </span>
        ) : (
          <span key={i}>{t.text}</span>
        ),
      )}
      {lineIdx < lines.length - 1 ? '\n' : ''}
    </span>
  ));
}
