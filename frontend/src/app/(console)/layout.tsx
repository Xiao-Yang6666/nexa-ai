'use client';

import { useEffect, type ReactNode } from 'react';

/**
 * 控制台路由组布局：给 <html> 挂 data-scheme='dark'，切换到深色工作台底
 * （tokens.css 的 :root[data-scheme="dark"] 一族 token）。
 *
 * 与 (public) 的 PublicLayout（给 body 打 data-public 切深色门面）对称；
 * 控制台用 data-scheme=dark（深色应用底），进入挂、离开恢复，避免污染公开站。
 */
export default function ConsoleLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.documentElement.setAttribute('data-scheme', 'dark');
    return () => {
      document.documentElement.removeAttribute('data-scheme');
    };
  }, []);

  return <>{children}</>;
}
