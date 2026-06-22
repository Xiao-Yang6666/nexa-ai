'use client';

import type { ReactNode } from 'react';
import { RootRouteLayout } from '@/features/shell';

/**
 * /admin/ops 嵌套布局：在 (admin) 的 ADMIN 守卫之上再加 ROOT 守卫。
 * 运维监控后端为 @RequireRole(ROOT)，菜单仅 root 可见——此守卫堵住非 root 管理员直接打 URL 进来后
 * 触发 403 的入口，role<ROOT 一律踢回 /dashboard。
 */
export default function OpsLayout({ children }: { children: ReactNode }) {
  return <RootRouteLayout>{children}</RootRouteLayout>;
}
