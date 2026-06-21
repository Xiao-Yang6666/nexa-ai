import type { Metadata } from 'next';
import { UsersAdminPage } from '@/features/account';

export const metadata: Metadata = {
  title: 'Nexa·AI 管理后台 · 用户管理',
  description: '管理后台用户管理：用户列表、角色/状态/分组筛选、额度与封禁管理。',
};

/** /admin/users 路由：用户管理（AdminShell 内嵌）。 */
export default function Page() {
  return <UsersAdminPage />;
}
