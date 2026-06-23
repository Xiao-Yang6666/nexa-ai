import type { Metadata } from 'next';
import { ProviderAccountsAdminPage } from '@/features/provider-account';

export const metadata: Metadata = {
  title: 'Nexa·AI · 供应商账号',
  description: '管理后台：供应商账号管理——账号 CRUD、启停、并发/优先级配置、凭证脱敏。',
};

/** /admin/provider-accounts 路由：供应商账号管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <ProviderAccountsAdminPage />;
}
