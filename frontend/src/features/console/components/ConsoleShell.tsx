'use client';

import { useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import styles from './ConsoleShell.module.css';

/* ── 线性 stroke 图标库（24x24，stroke:currentColor）。迁移自 S6 console-shell.js ── */
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

interface NavItem {
  /** 路由 id，与 App Router 段名一致 */
  id: string;
  label: string;
  /** 控制台路由 href（/console/<id>） */
  href: string;
  ic: string;
}

interface NavGroup {
  group: string;
  items: NavItem[];
}

/**
 * 控制台导航树。与 S6 console-shell.js 的 NAV 同构，
 * href 改为 App Router flat 路径（/<page>，路由组不加段）。
 */
const NAV: NavGroup[] = [
  { group: '概览', items: [{ id: 'dashboard', label: '仪表盘', href: '/dashboard', ic: 'gauge' }] },
  {
    group: '接入',
    items: [
      { id: 'keys', label: 'API 密钥', href: '/keys', ic: 'key' },
      { id: 'model-map', label: '模型映射', href: '/model-map', ic: 'share' },
      { id: 'usage', label: '用量统计', href: '/usage', ic: 'bar' },
      { id: 'tasks', label: '异步任务', href: '/tasks', ic: 'tasks' },
    ],
  },
  {
    group: '账户',
    items: [
      { id: 'billing', label: '账单与计费', href: '/billing', ic: 'receipt' },
      { id: 'recharge', label: '余额充值', href: '/recharge', ic: 'wallet' },
    ],
  },
  {
    group: '增长',
    items: [
      { id: 'checkin', label: '每日签到', href: '/checkin', ic: 'calendar' },
      { id: 'referral', label: '分销推广', href: '/referral', ic: 'share' },
    ],
  },
  { group: '设置', items: [{ id: 'settings', label: '个人设置', href: '/settings', ic: 'settings' }] },
];

export interface ConsoleShellProps {
  /** 当前激活路由 id（如 'checkin'），决定侧栏高亮 */
  activeId: string;
  /** 页面标题（顶部大标题） */
  title: string;
  /** 面包屑路径，如 ['控制台','每日签到'] */
  crumb: string[];
  /** 顶部右侧操作区（页面级按钮，如「新增映射」） */
  actions?: ReactNode;
  /** 顶栏展示的余额文案（来自 self 接口，客户视图，非成本） */
  balance?: string;
  /** 顶栏展示的用户名 */
  userName?: string;
  children: ReactNode;
}

/**
 * ConsoleShell — 控制台共享外壳（顶栏 + 左侧导航 + 面包屑 + 内容区）。
 *
 * S6 原型 console-shell.js（运行期 JS 注入外壳）的工程化版本：
 * 改为 React 组件，导航树静态声明、激活态由 props.activeId 驱动、
 * 移动端汉堡抽屉用受控 state。样式全部来自 ConsoleShell.module.css（token 化）。
 *
 * 每个控制台页面用 <ConsoleShell activeId title crumb actions>{页面主区}</ConsoleShell> 包裹。
 */
export function ConsoleShell({
  activeId,
  title,
  crumb,
  actions,
  balance = '$128.50',
  userName = 'morgan.li',
  children,
}: ConsoleShellProps) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      <header className={styles.top}>
        <button
          className={styles.burger}
          aria-label="菜单"
          onClick={() => setOpen((v) => !v)}
        >
          <Icon name="tasks" />
        </button>
        <Link className={styles.logo} href="/dashboard">
          <span className={styles.logoSq}>N</span>
          <span>Nexa·AI</span>
        </Link>
        <div className={styles.topRight}>
          <span className={styles.bal}>
            余额 <b>{balance}</b>
          </span>
          <button className={styles.iconBtn} aria-label="通知">
            <Icon name="bell" />
            <span className={styles.dotMark} />
          </button>
          <button className={styles.user} type="button">
            <span className={styles.avatar}>{userName.charAt(0).toUpperCase()}</span>
            <span className={styles.userName}>{userName}</span>
            <Icon name="chevron" />
          </button>
        </div>
      </header>

      <aside className={`${styles.side} ${open ? styles.open : ''}`}>
        {NAV.map((grp) => (
          <div key={grp.group} className={styles.navGrp}>
            <div className={styles.navHead}>{grp.group}</div>
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
                  <span key={`${c}-${i}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-1)' }}>
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
