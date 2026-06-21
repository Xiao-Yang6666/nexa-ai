import type { Metadata } from 'next';
import { ChannelsAdminPage } from '@/features/channel';

export const metadata: Metadata = {
  title: 'Nexa·AI · 渠道管理',
  description: '管理后台：渠道管理——渠道 CRUD、连通性测试、Key 轮询配置。',
};

/** /admin/channels 路由：渠道管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <ChannelsAdminPage />;
}
