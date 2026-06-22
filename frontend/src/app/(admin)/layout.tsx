'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useSelf, isAdminRole } from '@/features/account/model/account.model';

/**
 * 管理后台路由组布局：①给 <html> 挂 data-scheme='dark'（深色工作台底，tokens.css
 * 的 :root[data-scheme="dark"] 一族 token）；②客户端角色守卫——非管理员（role < admin）
 * 一律重定向回用户控制台 /dashboard，未登录（self 401）同样被踢回。
 *
 * 守卫为「前端可见性 + 体验」层；真正的越权防线在后端（各管理端 @RequireRole(ADMIN/ROOT)）。
 * 与 (console) 的 ConsoleLayout 对称；进入挂、离开恢复，避免污染公开站浅色门面。
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const self = useSelf();

  useEffect(() => {
    document.documentElement.setAttribute('data-scheme', 'dark');
    return () => {
      document.documentElement.removeAttribute('data-scheme');
    };
  }, []);

  // 角色守卫：拿到 self 后若非管理员则踢回控制台；self 报错（含未登录）踢回登录。
  useEffect(() => {
    if (self.isSuccess && !isAdminRole(self.data.role)) {
      router.replace('/dashboard');
    }
    if (self.isError) {
      router.replace('/login');
    }
  }, [self.isSuccess, self.isError, self.data, router]);

  // 鉴权未决 / 越权用户：渲染占位，避免管理界面在重定向前一闪而过（防越权窥视）。
  if (self.isPending || (self.isSuccess && !isAdminRole(self.data.role)) || self.isError) {
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
