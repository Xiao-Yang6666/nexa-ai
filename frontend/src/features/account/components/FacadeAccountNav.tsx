'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useSelf, useLogout, roleLabel, isAdminRole } from '../model/account.model';
import styles from './FacadeAccountNav.module.css';

/** 头像/箭头/外链小图标（线性 stroke，继承 currentColor）。 */
const ChevronIcon = () => (
  <svg className={styles.chev} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M6 9l6 6 6-6" />
  </svg>
);
const PanelIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="4" width="18" height="16" rx="2" /><path d="M3 9h18M9 9v11" />
  </svg>
);
const GearIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="3" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
  </svg>
);
const OutIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="M16 17l5-5-5-5" /><path d="M21 12H9" />
  </svg>
);

export interface FacadeAccountNavProps {
  /** bar=顶栏内联（头像+下拉）；stacked=移动抽屉内整列展开。 */
  variant?: 'bar' | 'stacked';
  /** 移动抽屉项点击后回调（用于收起抽屉）。 */
  onNavigate?: () => void;
}

/**
 * FacadeAccountNav — 公开站 / 文档站（深色门面）通用账号控件。
 *
 * 读取 useSelf 判定登录态：未登录显示「登录 / 免费开始」CTA；已登录显示头像 + 下拉
 * （用户信息头 + 进控制台 + 账号设置 + 退出登录）。登出后 self 缓存清除自动回落未登录态。
 * 用 hd-* 深色门面 token，无需 data-scheme；PublicShell 与 DocsShell 共用，单点维护登出逻辑。
 */
export function FacadeAccountNav({ variant = 'bar', onNavigate }: FacadeAccountNavProps) {
  const self = useSelf();
  const logout = useLogout();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const account = self.data;

  // 登出：清后端会话 + 缓存，self 主动 refetch → 自动回落未登录 CTA。
  function handleLogout() {
    setOpen(false);
    logout.mutate(undefined, { onSettled: () => router.refresh() });
  }

  // 未登录（含 self pending/401）：展示 CTA。stacked 全宽，bar 内联。
  if (!account) {
    return (
      <div className={variant === 'stacked' ? styles.ctaStacked : styles.cta}>
        <Link className={`${styles.btn} ${styles.glass}`} href="/login" onClick={onNavigate}>
          登录
        </Link>
        <Link className={`${styles.btn} ${styles.glow}`} href="/register" onClick={onNavigate}>
          免费开始
        </Link>
      </div>
    );
  }

  const displayName = account.displayName || account.username || '用户';
  const consoleHref = isAdminRole(account.role) ? '/admin' : '/dashboard';
  const initial = displayName.charAt(0).toUpperCase();

  // 已登录 · 移动抽屉：整列展开（头 + 链接），不用浮层下拉。
  if (variant === 'stacked') {
    return (
      <div className={styles.stacked}>
        <div className={styles.head}>
          <span className={styles.avatar}>{initial}</span>
          <div className={styles.ident}>
            <div className={styles.name}>{displayName}</div>
            <div className={styles.role}>{roleLabel(account.role)}</div>
          </div>
        </div>
        <Link className={styles.item} href={consoleHref} onClick={onNavigate}>
          <PanelIcon /> <span>进入控制台</span>
        </Link>
        <Link className={styles.item} href="/settings" onClick={onNavigate}>
          <GearIcon /> <span>账号设置</span>
        </Link>
        <button className={styles.item} type="button" onClick={() => { handleLogout(); onNavigate?.(); }} disabled={logout.isPending}>
          <OutIcon /> <span>{logout.isPending ? '退出中…' : '退出登录'}</span>
        </button>
      </div>
    );
  }

  // 已登录 · 顶栏：头像按钮 + 浮层下拉。
  return (
    <div className={styles.wrap} ref={ref}>
      <button
        className={styles.trigger}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={styles.avatar}>{initial}</span>
        <span className={styles.triggerName}>{displayName}</span>
        <ChevronIcon />
      </button>
      {open ? (
        <div className={styles.menu} role="menu">
          <div className={styles.head}>
            <span className={styles.avatarLg}>{initial}</span>
            <div className={styles.ident}>
              <div className={styles.name}>{displayName}</div>
              {account.email ? <div className={styles.email}>{account.email}</div> : null}
            </div>
          </div>
          <div className={styles.meta}>
            <span className={styles.tag}>{roleLabel(account.role)}</span>
            {account.group ? <span className={styles.tagSec}>{account.group}</span> : null}
          </div>
          <div className={styles.sep} />
          <Link className={styles.item} role="menuitem" href={consoleHref} onClick={() => setOpen(false)}>
            <PanelIcon /> <span>进入控制台</span>
          </Link>
          <Link className={styles.item} role="menuitem" href="/settings" onClick={() => setOpen(false)}>
            <GearIcon /> <span>账号设置</span>
          </Link>
          <button className={styles.item} type="button" role="menuitem" onClick={handleLogout} disabled={logout.isPending}>
            <OutIcon /> <span>{logout.isPending ? '退出中…' : '退出登录'}</span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
