'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { NAV_GROUPS, type TocItem } from '../model/nav';

/**
 * 文档站三栏外壳。客户端组件，承担：
 *  1. body[data-docs='1'] 切换深色文档底（进入挂、离开摘）；
 *  2. 顶栏 + 左栏 NavTree + 右栏 ToC；
 *  3. 移动端汉堡抽屉、scrim 遮罩、点击导航后自动收起；
 *  4. ToC scrollspy（当前可视 h2/h3 高亮）+ 平滑滚动；
 *  5. ScrollReveal（IntersectionObserver 段落渐显）。
 *
 * @param toc       右栏 ToC 列表（仅传当前页 h2/h3）。
 * @param children  正文区内容（通常是 article-inner 内的语义 HTML 块）。
 */
export function DocsShell({
  toc,
  children,
}: {
  toc: TocItem[];
  children: ReactNode;
}) {
  const pathname = usePathname();
  const [navOpen, setNavOpen] = useState(false);
  const [activeAnchor, setActiveAnchor] = useState<string | null>(
    toc[0]?.id ?? null,
  );

  /* body[data-docs='1'] 切深色文档底，离开恢复。 */
  useEffect(() => {
    document.body.setAttribute('data-docs', '1');
    return () => {
      document.body.removeAttribute('data-docs');
    };
  }, []);

  /* ScrollReveal：标 .reveal 的元素进入视口加 .is-in。 */
  useEffect(() => {
    const els = document.querySelectorAll<HTMLElement>('.reveal');
    if (!('IntersectionObserver' in window)) {
      els.forEach((el) => el.classList.add('is-in'));
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((en) => {
          if (en.isIntersecting) {
            en.target.classList.add('is-in');
            io.unobserve(en.target);
          }
        });
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.06 },
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [pathname]);

  /* ToC scrollspy + 平滑滚动。targets 来自 toc 中 id 在文档里的实际元素。 */
  useEffect(() => {
    if (!toc.length) return;
    const targets = toc
      .map((t) => {
        const el = document.getElementById(t.id);
        return el ? { id: t.id, el } : null;
      })
      .filter((x): x is { id: string; el: HTMLElement } => x !== null);
    if (!targets.length) return;

    const spy = () => {
      const pos = window.scrollY + 120;
      let current = targets[0];
      for (const t of targets) {
        if (t.el.offsetTop <= pos) current = t;
      }
      setActiveAnchor(current.id);
    };
    window.addEventListener('scroll', spy, { passive: true });
    spy();
    return () => window.removeEventListener('scroll', spy);
  }, [toc, pathname]);

  /* 切路由时关闭抽屉、回到顶部锚点候选。 */
  useEffect(() => {
    setNavOpen(false);
    setActiveAnchor(toc[0]?.id ?? null);
  }, [pathname, toc]);

  /** 锚点点击：平滑滚动 + 写 history（不刷新路由）。 */
  const handleTocClick = (e: React.MouseEvent<HTMLAnchorElement>, id: string) => {
    const el = document.getElementById(id);
    if (!el) return;
    e.preventDefault();
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    history.replaceState(null, '', `#${id}`);
  };

  return (
    <>
      <header className="topbar">
        <div className="topbar-in">
          <button
            type="button"
            className="burger"
            aria-label="打开导航"
            aria-expanded={navOpen}
            onClick={() => setNavOpen((o) => !o)}
          >
            <svg viewBox="0 0 24 24">
              <path d="M3 6h18M3 12h18M3 18h18" />
            </svg>
          </button>
          <Link className="tb-brand" href="/docs">
            <span className="m">N</span>Nexa·AI 文档
          </Link>
          <span className="tb-spacer" />
          <Link className="btn btn-glow" href="/dashboard">
            进控制台
            <svg viewBox="0 0 24 24">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </Link>
        </div>
      </header>

      <div
        className={`scrim${navOpen ? ' is-on' : ''}`}
        onClick={() => setNavOpen(false)}
      />

      <div className="shell">
        <aside className={`navtree${navOpen ? ' is-open' : ''}`}>
          <div className="nt-search">
            <svg viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.5-3.5" />
            </svg>
            <input
              type="search"
              placeholder="搜索文档…"
              aria-label="搜索文档"
            />
          </div>

          {NAV_GROUPS.map((group) => (
            <div className="nt-group" key={group.overline}>
              <div className="nt-overline">{group.overline}</div>
              {group.links.map((link) => {
                const isCurrent = pathname === link.href;
                return (
                  <Link
                    key={link.href}
                    className={`nt-link${isCurrent ? ' is-current' : ''}`}
                    href={link.href}
                    onClick={() => setNavOpen(false)}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </div>
          ))}
        </aside>

        <main className="article">
          <div className="article-inner">{children}</div>
        </main>

        {toc.length > 0 && (
          <nav className="toc">
            <div className="toc-title">本页目录</div>
            {toc.map((item) => (
              <a
                key={item.id}
                href={`#${item.id}`}
                className={[
                  item.level === 3 ? 'lv3' : '',
                  activeAnchor === item.id ? 'is-active' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={(e) => handleTocClick(e, item.id)}
              >
                {item.label}
              </a>
            ))}
          </nav>
        )}
      </div>
    </>
  );
}
