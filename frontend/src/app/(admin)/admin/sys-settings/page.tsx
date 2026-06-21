import type { Metadata } from 'next';
import { SysSettingsPage } from '@/features/system';

export const metadata: Metadata = {
  title: 'Nexa·AI · 系统设置',
  description: '管理后台：系统设置——站点信息、注册登录、计费配额、邮件通知、安全策略与高级选项。',
};

/** /admin/sys-settings 路由：系统设置页（AdminShell 内嵌）。 */
export default function Page() {
  return <SysSettingsPage />;
}
