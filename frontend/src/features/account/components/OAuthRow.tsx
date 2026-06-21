'use client';

/**
 * OAuthRow — 第三方登录横排（门面深色玻璃风）。
 *
 * 1:1 还原原型 .oauth 的结构/视觉：GitHub / Google / 微信 / Telegram 四枚玻璃按钮，
 * 横排四等分网格，hover 上浮 + 主色描边。类名沿用门面 scoped css（auth.module.css :global）。
 *
 * 纯展示/入口组件：每枚为 OAuth 授权入口链接，授权流由后端 redirect 承接（href 待接真 OAuth 端点）。
 * 登录与注册视图共用同一排。
 */
export function OAuthRow() {
  return (
    <div className="oauth">
      <a href="#" aria-label="使用 GitHub 登录">
        <svg viewBox="0 0 24 24">
          <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.7c-2.78.6-3.37-1.34-3.37-1.34-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.34 1.09 2.91.83.09-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02a9.5 9.5 0 0 1 5 0c1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.69-4.57 4.94.36.31.68.92.68 1.85v2.74c0 .27.18.58.69.48A10 10 0 0 0 12 2z" />
        </svg>
      </a>
      <a href="#" aria-label="使用 Google 登录">
        <svg viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v4.5h5.2" />
          <path d="M16.5 16.5A6 6 0 1 1 18 11" />
        </svg>
      </a>
      <a href="#" aria-label="使用微信登录">
        <svg viewBox="0 0 24 24">
          <path d="M9 4C5.1 4 2 6.6 2 9.8c0 1.8 1 3.4 2.6 4.5L4 17l2.7-1.4c.7.2 1.5.3 2.3.3" />
          <circle cx="6.6" cy="9" r=".6" fill="currentColor" stroke="none" />
          <circle cx="11" cy="9" r=".6" fill="currentColor" stroke="none" />
          <path d="M22 15.4c0-2.6-2.6-4.7-5.8-4.7-3.2 0-5.8 2.1-5.8 4.7s2.6 4.7 5.8 4.7c.7 0 1.4-.1 2-.3l2.3 1.2-.6-2c1.3-.9 2.1-2.2 2.1-3.6z" />
          <circle cx="14.3" cy="14.8" r=".5" fill="currentColor" stroke="none" />
          <circle cx="18.1" cy="14.8" r=".5" fill="currentColor" stroke="none" />
        </svg>
      </a>
      <a href="#" aria-label="使用 Telegram 登录">
        <svg viewBox="0 0 24 24">
          <path d="M21.5 4.3 2.8 11.5c-.9.4-.9 1.1-.1 1.3l4.7 1.5 1.8 5.6c.2.5.4.6.9.2l2.6-2.4 4.6 3.4c.5.3.9.1 1-.5l3-13.9c.1-.7-.3-1-.9-.8z" />
          <path d="m7.4 14.3 9-5.7-7.4 6.5" />
        </svg>
      </a>
    </div>
  );
}
