'use client';

import { useEffect, type ReactNode } from 'react';

/**
 * 公开站路由组布局：在 body 上挂 data-public='1'，切换到深色门面底（hd-bg）。
 *
 * App Router 下 body 由 RootLayout 渲染（默认浅色 token），公开站需深色；
 * 这里用客户端 effect 给 body 打标，进入公开页深色、离开恢复，避免污染应用区。
 */
export default function PublicLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.body.setAttribute('data-public', '1');
    return () => {
      document.body.removeAttribute('data-public');
    };
  }, []);

  return <>{children}</>;
}
