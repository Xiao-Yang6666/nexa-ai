'use client';

import type { ReactNode } from 'react';
import { AdminRouteLayout } from '@/features/shell';

/**
 * 管理后台路由组布局：深色 scheme + ≥ADMIN 角色守卫。
 * 非管理员踢回 /dashboard，未登录踢回 /login；逻辑收敛至 features/shell 的 RouteShellLayout。
 * 守卫为前端可见性层，真正越权防线在后端各端点 @RequireRole(ADMIN/ROOT)。
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AdminRouteLayout>{children}</AdminRouteLayout>;
}
