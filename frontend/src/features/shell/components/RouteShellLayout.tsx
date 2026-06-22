'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useSelf, ROLE } from '@/features/account/model/account.model';

export interface RouteShellLayoutProps {
  /** 本路由组要求的最低角色：COMMON(控制台) / ADMIN(管理台) / ROOT(超管专属页) */
  minRole: number;
  /** 是否托管 data-scheme='dark'（路由组顶层 layout 用 true；嵌套守卫层用 false 避免重复 toggle） */
  manageScheme?: boolean;
  children: ReactNode;
}

/**
 * RouteShellLayout — 路由组统一布局 + 角色守卫（CR-01）。
 *
 * 取代原 (console)/(admin) 两套分别写死的 layout：①给 <html> 挂 data-scheme='dark'
 * （深色工作台底，manageScheme=true 时）；②按 minRole 做客户端角色守卫——self 成功但 role 不足则踢回上一级
 * （admin/root 不足 → /dashboard），self 报错（含未登录 401）→ /login。
 *
 * 守卫为「前端可见性 + 体验」层；真正的越权防线在后端各端点 @RequireRole。
 * minRole=COMMON 时只需登录态（self 成功即放行），普通用户正常驻留控制台。
 * 嵌套在已托管 scheme 的路由组内（如 /admin/ops 的 ROOT 守卫）时传 manageScheme=false，
 * 避免内层卸载时误删外层仍需要的 data-scheme。
 */
export function RouteShellLayout({ minRole, manageScheme = true, children }: RouteShellLayoutProps) {
  const router = useRouter();
  const self = useSelf();

  useEffect(() => {
    if (!manageScheme) return;
    document.documentElement.setAttribute('data-scheme', 'dark');
    return () => {
      document.documentElement.removeAttribute('data-scheme');
    };
  }, [manageScheme]);

  const insufficient = self.isSuccess && self.data.role < minRole;

  useEffect(() => {
    if (insufficient) {
      // 角色不足：admin/root 区踢回控制台；控制台区(minRole=COMMON)不会命中此分支。
      router.replace('/dashboard');
    }
    if (self.isError) {
      router.replace('/login');
    }
  }, [insufficient, self.isError, router]);

  // 鉴权未决 / 越权 / 出错：渲染占位，避免受限界面在重定向前一闪而过（防越权窥视）。
  // COMMON 区也在 isPending 时占位，待 self 落定后再渲染（菜单角色过滤才准确）。
  if (self.isPending || insufficient || self.isError) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'grid',
          placeItems: 'center',
          color: 'var(--text-muted, #8b93a7)',
          fontSize: 14,
        }}
      >
        正在校验访问权限…
      </div>
    );
  }

  return <>{children}</>;
}

/** 控制台路由组守卫（普通用户即可访问，仅需登录态）。 */
export function ConsoleRouteLayout({ children }: { children: ReactNode }) {
  return <RouteShellLayout minRole={ROLE.COMMON}>{children}</RouteShellLayout>;
}

/** 管理台路由组守卫（≥ADMIN）。 */
export function AdminRouteLayout({ children }: { children: ReactNode }) {
  return <RouteShellLayout minRole={ROLE.ADMIN}>{children}</RouteShellLayout>;
}

/** 超管专属页守卫（≥ROOT），用于 ops / sys-settings 嵌套 layout。
 *  嵌套在 (admin) 内，scheme 已由外层托管，故 manageScheme=false。 */
export function RootRouteLayout({ children }: { children: ReactNode }) {
  return (
    <RouteShellLayout minRole={ROLE.ROOT} manageScheme={false}>
      {children}
    </RouteShellLayout>
  );
}
