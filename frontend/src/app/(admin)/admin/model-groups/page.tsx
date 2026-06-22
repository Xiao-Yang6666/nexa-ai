import type { Metadata } from 'next';
import { ModelGroupsPage } from '@/features/modelgroup';

export const metadata: Metadata = {
  title: 'Nexa·AI 管理后台 · 模型组管理',
  description: '管理后台：灵活模型组——可用模型集、模型组级倍率、公开/私有/按等级自动访问策略的 CRUD 与启停。',
};

/** /admin/model-groups 路由：模型组管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <ModelGroupsPage />;
}
