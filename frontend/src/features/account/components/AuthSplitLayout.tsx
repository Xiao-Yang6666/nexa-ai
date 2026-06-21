'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { FlowingThreads } from '@/features/marketing';
import styles from './AuthSplitLayout.module.css';

/** 品牌侧卖点项（线性图标 + 文案）。 */
interface SellPoint {
  /** 内联 SVG path 组（沿用原型线性图标） */
  icon: ReactNode;
  label: string;
}

/** 登录/注册页共用的三条卖点（与原型 login/register 一致）。 */
const SELL_POINTS: SellPoint[] = [
  {
    icon: <path d="M13 2 3 14h7l-1 8 10-12h-7l1-8z" />,
    label: '一个 base_url，直连全球大模型',
  },
  {
    icon: (
      <>
        <path d="M12 2 4 6v6c0 5 3.5 8 8 10 4.5-2 8-5 8-10V6l-8-4z" />
        <path d="m9 12 2 2 4-4" />
      </>
    ),
    label: '满血参数，企业级稳定与安全',
  },
  {
    icon: (
      <>
        <path d="M3 17l6-6 4 4 8-8" />
        <path d="M14 7h7v7" />
      </>
    ),
    label: '实时计量，按量付费只付零头',
  },
];

export interface AuthSplitLayoutProps {
  /** 品牌侧价值主张（含高亮片段，由调用方组装），如「欢迎回来，<accent>满血直连</accent>继续」。 */
  tagline: ReactNode;
  /** 右栏表单卡内容（LoginForm / RegisterForm）。 */
  children: ReactNode;
}

/**
 * AuthSplitLayout — 登录/注册独立页的左右分栏外壳。
 *
 * 1:1 工程化 06_prototype/final/web-public/{login,register}.html 的 .auth 分栏：
 * 左栏深色光束品牌区（FlowingThreads canvas + aurora 辉光 + 巨字 Nexa·AI + 三条卖点），
 * 右栏玻璃卡容器（.card，装入 children 表单）。移动端上下堆叠（卖点收起）。
 *
 * 品牌侧为纯展示，卖点与视觉固定；可变的只有 tagline 文案。表单逻辑由 children 承载，
 * 与布局解耦——登录/注册复用同一壳，只换标题/表单与 tagline。
 *
 * @param tagline 品牌侧价值主张文案（可含 <Accent> 高亮）
 * @param children 右栏表单卡（含 <h1>/<p class="sub"> 标题与字段）
 */
export function AuthSplitLayout({ tagline, children }: AuthSplitLayoutProps) {
  return (
    <main className={styles.auth}>
      {/* 左栏：深色光束品牌区 */}
      <section className={styles.brand}>
        <FlowingThreads />

        <div className={styles.brandTop}>
          <Link className={styles.brandLogo} href="/">
            <span className={styles.mark1}>N</span>Nexa·AI
          </Link>
        </div>

        <div className={styles.brandMid}>
          <h2 className={styles.mark} aria-label="Nexa·AI">
            <span className={styles.word}>Nexa</span>
            <span className={styles.dot} aria-hidden="true" />
            <span className={styles.ai}>AI</span>
          </h2>
          <p className={styles.tagline}>{tagline}</p>
          <ul className={styles.points}>
            {SELL_POINTS.map((p) => (
              <li key={p.label}>
                <span className={styles.ic}>
                  <svg viewBox="0 0 24 24">{p.icon}</svg>
                </span>
                {p.label}
              </li>
            ))}
          </ul>
        </div>

        <div className={styles.brandBottom}>
          © 2026 Nexa·AI · 直连 OpenAI / Anthropic / Google / DeepSeek
        </div>
      </section>

      {/* 右栏：玻璃表单卡 */}
      <section className={styles.panel}>
        <div className={styles.card}>{children}</div>
      </section>
    </main>
  );
}

/** 品牌侧 tagline 内的高亮片段（teal 渐变文字），用于组装 tagline。 */
export function Accent({ children }: { children: ReactNode }) {
  return <span className={styles.accent}>{children}</span>;
}
