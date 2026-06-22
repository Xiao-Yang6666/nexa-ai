'use client';

import { useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
// 从 account model 叶子模块导入（非 barrel）：打破 account↔shell 循环依赖。
import { useSelf, ROLE } from '@/features/account/model/account.model';
import { NAV } from '../nav-tree';
import styles from './AppShell.module.css';

/* ── 线性 stroke 图标库（24x24，stroke:currentColor）。合并自 S6 console/admin shell ── */
const ICONS: Record<string, ReactNode> = {
  gauge: (<><path d="M12 14l3-3" /><path d="M3.5 18a9 9 0 1 1 17 0" /><circle cx="12" cy="14" r="1.4" /></>),
  key: (<><circle cx="7.5" cy="15.5" r="3.5" /><path d="M10 13l8-8" /><path d="M15 5l3 3" /><path d="M17 7l2-2" /></>),
  bar: (<><path d="M4 20V10" /><path d="M10 20V4" /><path d="M16 20v-7" /><path d="M21 20H3" /></>),
  tasks: (<><path d="M9 6h11" /><path d="M9 12h11" /><path d="M9 18h11" /><path d="M3.5 6l1 1 2-2" /><path d="M3.5 12l1 1 2-2" /><path d="M3.5 18l1 1 2-2" /></>),
  receipt: (<><path d="M5 3v18l2-1.4L9 21l2-1.4L13 21l2-1.4L17 21l2-1.4V3l-2 1.4L15 3l-2 1.4L11 3 9 4.4 7 3 5 4.4 5 3z" /><path d="M8 8h8" /><path d="M8 12h8" /></>),
  wallet: (<><path d="M3 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><path d="M16 11h4v4h-4a2 2 0 0 1 0-4z" /></>),
  calendar: (<><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 9h18" /><path d="M8 3v4" /><path d="M16 3v4" /></>),
  share: (<><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="6" r="2.5" /><circle cx="18" cy="18" r="2.5" /><path d="M8.2 11l7.6-4" /><path d="M8.2 13l7.6 4" /></>),
  settings: (<><circle cx="12" cy="12" r="3" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" /></>),
  bell: (<><path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z" /><path d="M10.5 19a1.7 1.7 0 0 0 3 0" /></>),
  grid: (<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>),
  server: (<><rect x="3" y="4" width="18" height="7" rx="2" /><rect x="3" y="13" width="18" height="7" rx="2" /><path d="M7 7.5h.01" /><path d="M7 16.5h.01" /></>),
  users: (<><circle cx="9" cy="8" r="3.2" /><path d="M3.5 19a5.5 5.5 0 0 1 11 0" /><path d="M16 6.2a3 3 0 0 1 0 5.6" /><path d="M17.5 19a5.2 5.2 0 0 0-3-4.7" /></>),
  cube: (<><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z" /><path d="M4 7.5l8 4.5 8-4.5" /><path d="M12 12v9" /></>),
  layers: (<><path d="M12 3l9 5-9 5-9-5z" /><path d="M3 13l9 5 9-5" /></>),
  calc: (<><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 7h8" /><path d="M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01" /></>),
  ticket: (<><path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z" /><path d="M14 6v12" /></>),
  file: (<><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /><path d="M8 13h8M8 17h5" /></>),
  pulse: (<path d="M3 12h4l2-6 4 12 2-6h6" />),
  chevron: (<path d="M9 6l6 6-6 6" />),
};

/** 渲染指定名称的线性图标 SVG。 */
function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg
      className={`${styles.ic} ${className ?? ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {ICONS[name] ?? null}
    </svg>
  );
}
export interface AppShellProps {
  /** 当前激活路由 id（如 'channels'），决定侧栏高亮 */
  activeId: string;
  /** 页面标题（顶部大标题） */
  title: string;
  /** 面包屑路径，如 ['管理后台','渠道管理'] */
  crumb: string[];
  /** 顶部右侧操作区（页面级按钮，如「新增渠道」） */
  actions?: ReactNode;
  /** 顶栏展示的用户名（缺省取 self 接口的真实用户名） */
  userName?: string;
  children: ReactNode;
}

/**
 * AppShell — 全站统一应用外壳（顶栏 + 左侧角色动态导航 + 面包屑 + 内容区）。
 *
 * 取代原 ConsoleShell / AdminShell 两套写死的静态壳：菜单来自单一数据源 nav-tree.ts，
 * 按当前登录用户角色过滤（role >= item.minRole），root/admin 自然看到普通用户菜单超集 + 管理入口，
 * 普通用户只见 COMMON 项。删除了跨路由组「视图切换」链接（菜单退化 bug 根因）。
 *
 * 角色兜底：self pending/未取到时按 COMMON 渲染（绝不闪退成 admin、也不越权 flash root/admin 项）。
 * page 级越权防线仍在各路由组 layout 守卫 + 后端 @RequireRole。
 */
export function AppShell({ activeId, title, crumb, actions, userName, children }: AppShellProps) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const self = useSelf();

  // 角色兜底 COMMON：refetch 瞬间 self.data undefined 时绝不升格为 admin/root。
  const currentRole = self.data?.role ?? ROLE.COMMON;
  const displayName = userName ?? self.data?.displayName ?? self.data?.username ?? '用户';

  // 单一数据源按角色过滤：去掉 minRole 高于本人的项，丢弃过滤后变空的分组。
  const nav = NAV.map((grp) => ({
    ...grp,
    items: grp.items.filter((it) => currentRole >= it.minRole),
  })).filter((grp) => grp.items.length > 0);

  // 顶栏 logo 落点：管理员回管理总览，普通用户回仪表盘。
  const homeHref = currentRole >= ROLE.ADMIN ? '/admin' : '/dashboard';

  return (
    <>
      <header className={styles.top}>
        <button className={styles.burger} aria-label="菜单" onClick={() => setOpen((v) => !v)}>
          <Icon name="tasks" />
        </button>
        <Link className={styles.logo} href={homeHref}>
          <span className={styles.logoSq}>N</span>
          <span>Nexa·AI</span>
        </Link>
        <div className={styles.topRight}>
          <button className={styles.iconBtn} aria-label="通知">
            <Icon name="bell" />
            <span className={styles.dotMark} />
          </button>
          <button className={styles.user} type="button">
            <span className={styles.avatar}>{displayName.charAt(0).toUpperCase()}</span>
            <span className={styles.userName}>{displayName}</span>
            <Icon name="chevron" />
          </button>
        </div>
      </header>

      <aside className={`${styles.side} ${open ? styles.open : ''}`}>
        {nav.map((grp) => (
          <div key={grp.group} className={`${styles.navGrp} ${grp.admin ? styles.navGrpAdmin : ''}`}>
            <div className={styles.navHead}>
              {grp.group}
              {grp.admin ? <span className={`badge b-info ${styles.navBadge}`}>管理</span> : null}
            </div>
            {grp.items.map((it) => {
              const isActive = it.id === activeId || pathname === it.href;
              return (
                <Link
                  key={it.id}
                  className={`${styles.navLink} ${isActive ? styles.on : ''}`}
                  href={it.href}
                  onClick={() => setOpen(false)}
                >
                  <span className={styles.navBar} aria-hidden="true" />
                  <Icon name={it.ic} className={styles.navIc} />
                  <span>{it.label}</span>
                </Link>
              );
            })}
          </div>
        ))}
      </aside>
      <button
        className={`${styles.scrim} ${open ? styles.open : ''}`}
        aria-label="关闭菜单"
        onClick={() => setOpen(false)}
      />

      <div className={styles.main}>
        <div className={styles.content}>
          <div className={styles.pageHead}>
            <div>
              <div className={styles.crumb}>
                {crumb.map((c, i) => (
                  <span
                    key={`${c}-${i}`}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)' }}
                  >
                    {i > 0 ? (
                      <span className={styles.crumbSep}>
                        <Icon name="chevron" />
                      </span>
                    ) : null}
                    <span className={styles.crumbItem}>{c}</span>
                  </span>
                ))}
              </div>
              <h1 className={styles.pageTitle}>{title}</h1>
            </div>
            {actions ? <div className={styles.pageActs}>{actions}</div> : null}
          </div>
          {children}
        </div>
      </div>
    </>
  );
}
