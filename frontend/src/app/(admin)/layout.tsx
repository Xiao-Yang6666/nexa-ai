'use client';

import { useEffect, type ReactNode } from 'react';

/**
 * 管理后台路由组布局：给 <html> 挂 data-scheme='dark'，切换到深色工作台底
 * （tokens.css 的 :root[data-scheme="dark"] 一族 token）。
 *
 * 与 (console) 的 ConsoleLayout 对称；进入挂、离开恢复，避免污染公开站浅色门面。
 * S6 原型 admin/*.html 的 <html data-scheme="dark"> 工程化版本。
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.documentElement.setAttribute('data-scheme', 'dark');
    return () => {
      document.documentElement.removeAttribute('data-scheme');
    };
  }, []);

  return <>{children}</>;
}
