import type { Metadata } from 'next';
import { ModelsAdminPage } from '@/features/model';

export const metadata: Metadata = {
  title: 'Nexa·AI · 模型/供应商',
  description: '管理后台：模型/供应商——对外模型与基准售价、供应商成本倍率、模型与供应商元数据、上游模型同步与缺失检测。',
};

/** /admin/models 路由：模型/供应商管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <ModelsAdminPage />;
}
