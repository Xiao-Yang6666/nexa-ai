'use client';

import { useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import styles from './AdminShell.module.css';

/* ── 线性 stroke 图标库（24x24，stroke:currentColor）。迁移自 S6 admin-shell.js ── */
const ICONS: Record<string, ReactNode> = {
  gauge: (<><path d="M12 14l3-3" /><path d="M3.5 18a9 9 0 1 1 17 0" /><circle cx="12" cy="14" r="1.4" /></>),
  grid: (<><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>),
  server: (<><rect x="3" y="4" width="18" height="7" rx="2" /><rect x="3" y="13" width="18" height="7" rx="2" /><path d="M7 7.5h.01" /><path d="M7 16.5h.01" /></>),
  users: (<><circle cx="9" cy="8" r="3.2" /><path d="M3.5 19a5.5 5.5 0 0 1 11 0" /><path d="M16 6.2a3 3 0 0 1 0 5.6" /><path d="M17.5 19a5.2 5.2 0 0 0-3-4.7" /></>),
  cube: (<><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z" /><path d="M4 7.5l8 4.5 8-4.5" /><path d="M12 12v9" /></>),
  layers: (<><path d="M12 3l9 5-9 5-9-5z" /><path d="M3 13l9 5 9-5" /></>),
  tasks: (<><path d="M9 6h11" /><path d="M9 12h11" /><path d="M9 18h11" /><path d="M3.5 6l1 1 2-2" /><path d="M3.5 12l1 1 2-2" /><path d="M3.5 18l1 1 2-2" /></>),
  calc: (<><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 7h8" /><path d="M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01" /></>),
  ticket: (<><path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z" /><path d="M14 6v12" /></>),
  file: (<><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /><path d="M8 13h8M8 17h5" /></>),
  pulse: (<path d="M3 12h4l2-6 4 12 2-6h6" />),
  settings: (<><circle cx="12" cy="12" r="3" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" /></>),
  bell: (<><path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z" /><path d="M10.5 19a1.7 1.7 0 0 0 3 0" /></>),
  swap: (<><path d="M7 4v13" /><path d="M4 7l3-3 3 3" /><path d="M17 20V7" /><path d="M20 17l-3 3-3-3" /></>),
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
  /** 路由 id，与 AdminShell activeId 对齐 */
  id: string;
  label: string;
  /** 管理台路由 href（/admin 路由组 flat 路径） */
  href: string;
  ic: string;
}

interface NavGroup {
  group: string;
  /** 管理区分组：带「管理」徽章 + 顶部分隔线 */
  admin?: boolean;
  items: NavItem[];
}

/**
 * 管理后台导航树。与 S6 admin-shell.js 的 NAV 同构，
 * href 改为 App Router flat 路径。用户区「仪表盘」回控制台 /dashboard。
 * 管理组带 admin:true（侧栏渲染「管理」徽章 + 分隔线）。
 *
 * 注：本 wave 实现 admin-dashboard / channels / users / tasks / billing / ops 六页路由；
 * models/groups/profit/redeem/logs/sys 为后续 wave，导航项保留（用户可见完整菜单），
 * 路由落地后即可点达。
 */
const NAV: NavGroup[] = [
  {
    group: '用户区',
    items: [{ id: 'dashboard', label: '仪表盘', href: '/dashboard', ic: 'gauge' }],
  },
  {
    group: '管理总览',
    admin: true,
    items: [{ id: 'admin-dashboard', label: '全局概览', href: '/admin', ic: 'grid' }],
  },
  {
    group: '资源管理',
    admin: true,
    items: [
      { id: 'channels', label: '渠道管理', href: '/admin/channels', ic: 'server' },
      { id: 'users', label: '用户管理', href: '/admin/users', ic: 'users' },
      { id: 'models', label: '模型/供应商', href: '/admin/models', ic: 'cube' },
      { id: 'groups', label: '预填分组', href: '/admin/groups', ic: 'layers' },
    ],
  },
  {
    group: '运营',
    admin: true,
    items: [
      { id: 'tasks-monitor', label: '任务监控', href: '/admin/tasks-monitor', ic: 'tasks' },
      { id: 'billing-rules', label: '计费规则', href: '/admin/billing-rules', ic: 'calc' },
      { id: 'profit', label: '利润分析', href: '/admin/profit', ic: 'pulse' },
      { id: 'redeem', label: '兑换码', href: '/admin/redeem', ic: 'ticket' },
    ],
  },
  {
    group: '系统',
    admin: true,
    items: [
      { id: 'logs', label: '日志审计', href: '/admin/logs', ic: 'file' },
      { id: 'ops', label: '运维监控', href: '/admin/ops', ic: 'pulse' },
      { id: 'sys', label: '系统设置', href: '/admin/sys-settings', ic: 'settings' },
    ],
  },
];

export interface AdminShellProps {
  /** 当前激活路由 id（如 'channels'），决定侧栏高亮 */
  activeId: string;
  /** 页面标题（顶部大标题） */
  title: string;
  /** 面包屑路径，如 ['管理后台','渠道管理'] */
  crumb: string[];
  /** 顶部右侧操作区（页面级按钮，如「新增渠道」） */
  actions?: ReactNode;
  /** 顶栏展示的管理员用户名 */
  userName?: string;
  /** 顶栏角色切换当前文案 */
  role?: string;
  children: ReactNode;
}

/**
 * AdminShell — 管理后台共享外壳（顶栏 + 角色切换 + 左侧导航 + 面包屑 + 内容区）。
 *
 * S6 原型 admin-shell.js（运行期 JS 注入外壳）的工程化版本：
 * 改为 React 组件，导航树静态声明、激活态由 props.activeId 驱动、
 * 顶栏多「角色切换」下拉（管理视图 ⇄ 用户视图），移动端汉堡抽屉用受控 state。
 * 样式全部来自 AdminShell.module.css（token 化）。
 *
 * AdminView 可展示全字段（成本/利润/上游 B/供应商）——管理端不受客户端零泄露约束。
 * 每个管理页用 <AdminShell activeId title crumb actions>{页面主区}</AdminShell> 包裹。
 */
export function AdminShell({
  activeId,
  title,
  crumb,
  actions,
  userName = 'admin.root',
  role = '管理视图',
  children,
}: AdminShellProps) {
  const [open, setOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      <header className={styles.top}>
        <button className={styles.burger} aria-label="菜单" onClick={() => setOpen((v) => !v)}>
          <Icon name="tasks" />
        </button>
        <Link className={styles.logo} href="/admin">
          <span className={styles.logoSq}>N</span>
          <span>Nexa·AI</span>
        </Link>
        <div className={styles.topRight}>
          {/* 角色切换下拉：管理视图 ⇄ 用户视图 */}
          <div className={styles.role}>
            <button
              className={styles.roleBtn}
              type="button"
              aria-haspopup="true"
              aria-expanded={roleOpen}
              onClick={(e) => {
                e.stopPropagation();
                setRoleOpen((v) => !v);
              }}
              onBlur={() => setTimeout(() => setRoleOpen(false), 120)}
            >
              <Icon name="swap" />
              <span>{role}</span>
              <Icon name="chevron" />
            </button>
            <div className={`${styles.roleMenu} ${roleOpen ? styles.open : ''}`} role="menu">
              <button type="button" className={`${styles.roleOpt} ${styles.cur}`} role="menuitem">
                <Icon name="swap" />
                <span>管理视图</span>
              </button>
              <Link className={styles.roleOpt} role="menuitem" href="/dashboard">
                <Icon name="gauge" />
                <span>用户视图</span>
              </Link>
            </div>
          </div>
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
          <div
            key={grp.group}
            className={`${styles.navGrp} ${grp.admin ? styles.navGrpAdmin : ''}`}
          >
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
