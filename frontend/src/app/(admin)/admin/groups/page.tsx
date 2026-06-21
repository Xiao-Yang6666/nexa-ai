import type { Metadata } from 'next';
import { GroupsPage } from '@/features/group';

export const metadata: Metadata = {
  title: 'Nexa·AI · 预填分组',
  description: '管理后台：预填分组——折扣等级 CRUD、model/tag/endpoint 成员分组的增删软删。',
};

/** /admin/groups 路由：预填分组管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <GroupsPage />;
}
