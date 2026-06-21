'use client';

import { useState, type ReactNode } from 'react';
import Link from 'next/link';
import styles from './PublicShell.module.css';

/** 公开站导航项，对齐 06_prototype web-public 各页顶部导航。 */
const NAV_ITEMS: { href: string; label: string }[] = [
  { href: '/models', label: '模型广场' },
  { href: '/pricing', label: '价格' },
  { href: '/ranking', label: '排行榜' },
  { href: '/docs', label: '文档' },
];

const FOOT_LINKS: { href: string; label: string }[] = [
  { href: '/models', label: '模型广场' },
  { href: '/pricing', label: '价格' },
  { href: '/ranking', label: '排行榜' },
  { href: '/agreement', label: '服务协议' },
  { href: '/privacy', label: '隐私政策' },
];

export interface PublicShellProps {
  /** 当前激活的导航 key（用于高亮），如 'models' | 'pricing' | 'ranking'。 */
  active?: string;
  /** 页脚版权语后缀文案，默认品牌 slogan。 */
  footNote?: string;
  children: ReactNode;
}

/**
 * PublicShell — 公开站（web-public）统一外壳：玻璃质感顶栏 + 移动端汉堡 + 页脚。
 *
 * 从 06_prototype/final/web-public/*.html 的共享导航/页脚 1:1 工程化抽取，
 * 各公开页（models/pricing/ranking/agreement/privacy）共用，保证导航/品牌一致。
 * CTA（登录/免费开始）用 Next <Link> 跳转到 /login /register 真实路由，
 * 不在每页内联重复实现认证抽屉（工程化收敛，避免重复 markup）。
 *
 * @param active 当前页 key，高亮对应导航项
 */
export function PublicShell({
  active,
  footNote = '满血直连，只付零头',
  children,
}: PublicShellProps) {
  const [navOpen, setNavOpen] = useState(false);

  const keyOf = (href: string) => href.replace('/', '');

  return (
    <div className={navOpen ? styles.open : undefined}>
      <header className={styles.bar}>
        <div className="wrap">
          <div className={styles.in}>
            <Link className={styles.brand} href="/">
              <span className={styles.mark}>N</span>Nexa
            </Link>
            <nav className={styles.links}>
              {NAV_ITEMS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className={active === keyOf(item.href) ? styles.active : undefined}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className={styles.cta}>
              <Link className={`${styles.btn} ${styles.glass}`} href="/login">
                登录
              </Link>
              <Link className={`${styles.btn} ${styles.glow}`} href="/register">
                免费开始
              </Link>
            </div>
            <button
              type="button"
              className={styles.toggle}
              aria-label={navOpen ? '关闭菜单' : '打开菜单'}
              aria-expanded={navOpen}
              onClick={() => setNavOpen((v) => !v)}
            >
              <span />
              <span />
              <span />
            </button>
          </div>
        </div>
        <div className={styles.mobile} aria-hidden={!navOpen}>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={active === keyOf(item.href) ? styles.active : undefined}
              onClick={() => setNavOpen(false)}
            >
              {item.label}
            </Link>
          ))}
          <div className={styles.mobileCta}>
            <Link
              className={`${styles.btn} ${styles.glass}`}
              href="/login"
              onClick={() => setNavOpen(false)}
            >
              登录
            </Link>
            <Link
              className={`${styles.btn} ${styles.glow}`}
              href="/register"
              onClick={() => setNavOpen(false)}
            >
              免费开始
            </Link>
          </div>
        </div>
      </header>

      {children}

      <footer className={styles.foot}>
        <div className="wrap">
          <div className={styles.footIn}>
            <span>© 2026 Nexa·AI · {footNote}</span>
            <nav className={styles.footLinks}>
              {FOOT_LINKS.map((item) => (
                <Link key={item.href} href={item.href}>
                  {item.label}
                </Link>
              ))}
            </nav>
          </div>
        </div>
      </footer>
    </div>
  );
}
