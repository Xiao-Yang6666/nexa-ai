'use client';

import { useEffect, useRef, useState, Fragment } from 'react';
import { useRouter } from 'next/navigation';
import { FlowingThreads } from './FlowingThreads';
import styles from './HomeHero.module.css';

/** 打字机轮播文案池（与原型一致）。 */
const PHRASES = [
  '帮我写一段调用 GPT-4o 的 Python 代码',
  '对比一下 GPT-4o 和 Claude 3.5 Sonnet',
  '怎么用一个 base_url 接入所有模型？',
  '把这段英文翻译成地道中文…',
  '帮我把这个需求拆成开发任务',
  '用 DeepSeek 写个爬虫脚本',
];

/** 引导 chips（点击预填，未登录统一跳登录）。 */
const CHIPS = ['写段调用 GPT-4o 的代码', '对比 GPT-4o 与 Claude 3.5', '一行接入所有模型'];

/** 已直连的上游厂商（仅品牌展示，非供应商/渠道维度，客户端可见）。 */
const VENDORS = ['OpenAI', 'Anthropic', 'Google', 'DeepSeek', 'xAI'];

/**
 * useTypewriter — 只读打字机轮播 hook，逐字打出 → 停留 → 删除 → 换下一句。
 * 复刻原型首页 ask 框的引导动效（纯展示，框不可输入）。
 */
function useTypewriter(phrases: string[]): string {
  const [text, setText] = useState('');
  useEffect(() => {
    let pi = 0;
    let ci = 0;
    let deleting = false;
    let timer: ReturnType<typeof setTimeout>;
    function tick() {
      const full = phrases[pi];
      if (!deleting) {
        ci++;
        setText(full.slice(0, ci));
        if (ci >= full.length) {
          deleting = true;
          timer = setTimeout(tick, 1600);
          return;
        }
        timer = setTimeout(tick, 62 + Math.random() * 46);
      } else {
        ci--;
        setText(full.slice(0, ci));
        if (ci <= 0) {
          deleting = false;
          pi = (pi + 1) % phrases.length;
          timer = setTimeout(tick, 360);
          return;
        }
        timer = setTimeout(tick, 26);
      }
    }
    timer = setTimeout(tick, 700);
    return () => clearTimeout(timer);
  }, [phrases]);
  return text;
}

/**
 * HomeHero — 公开站首页 hero 区（深色门面主视觉）。
 *
 * 1:1 工程化 06_prototype/final/web-public/home.html 的 hero：全屏固定光束 canvas
 * （FlowingThreads fixed）+ aurora 辉光 + 巨字 Nexa·AI（呼吸 teal 光点）+ slogan +
 * 只读打字机 ask 框（整框 = 去登录入口）+ 引导 chips + 底部信任行。
 *
 * ask 框为"登录后开始对话"的入口：未登录点击/提交统一跳 /login（与原型登录态拦截一致）。
 * 导航/页脚由 PublicShell 统一提供，本组件只渲染 hero 主体（工程化收敛，不重复 nav 实现）。
 */
export function HomeHero() {
  const router = useRouter();
  const typed = useTypewriter(PHRASES);
  const formRef = useRef<HTMLFormElement>(null);

  /** ask 框 = 去登录入口（未登录态）。 */
  const goLogin = () => router.push('/login');

  return (
    <div className={styles.stage}>
      {/* 全屏固定光束背景 */}
      <FlowingThreads fixed />

      <section className={styles.hero}>
        <div className={styles.heroInner}>
          <div className={styles.pill}>
            <span className={styles.live} />
            已聚合 <b>148</b> 个模型 · 实时在线
          </div>

          {/* 巨字品牌 Nexa·AI */}
          <h1 className={styles.mega} aria-label="Nexa·AI">
            <span className={styles.word}>Nexa</span>
            <span className={styles.dot} aria-hidden="true" />
            <span className={styles.ai}>AI</span>
          </h1>

          <p className={styles.slogan}>
            满血直连，<span className={styles.accent}>只付零头</span>
          </p>

          <form
            ref={formRef}
            className={styles.ask}
            onSubmit={(e) => {
              e.preventDefault();
              goLogin();
            }}
          >
            <div
              className={styles.askShell}
              role="button"
              tabIndex={0}
              aria-label="登录后开始对话"
              onClick={goLogin}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  goLogin();
                }
              }}
            >
              <input type="text" readOnly aria-hidden="true" tabIndex={-1} value={typed} />
              <button className={styles.send} type="submit" aria-label="开始">
                <svg viewBox="0 0 24 24">
                  <path d="M5 12h14M13 6l6 6-6 6" />
                </svg>
              </button>
            </div>
            <div className={styles.chips}>
              {CHIPS.map((c) => (
                <span key={c} className={styles.chip} onClick={goLogin}>
                  {c}
                </span>
              ))}
            </div>
          </form>
        </div>
      </section>

      {/* 极简信任行 */}
      <div className={styles.trust}>
        <span className={styles.lbl}>已直连</span>
        <span className={styles.vendors}>
          {VENDORS.map((v, i) => (
            <Fragment key={v}>
              <span>{v}</span>
              {i < VENDORS.length - 1 ? <i className={styles.d} /> : null}
            </Fragment>
          ))}
        </span>
      </div>
    </div>
  );
}
