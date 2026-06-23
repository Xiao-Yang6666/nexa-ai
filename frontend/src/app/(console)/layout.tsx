'use client';

import type { ReactNode } from 'react';
import { ConsoleRouteLayout } from '@/features/shell';

/**
 * 控制台路由组布局：深色 scheme + 登录态守卫（普通用户即可访问）。
 * scheme/守卫逻辑收敛至 features/shell 的 RouteShellLayout，与管理台共用单一实现。
 */
export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return <ConsoleRouteLayout>{children}</ConsoleRouteLayout>;
}
