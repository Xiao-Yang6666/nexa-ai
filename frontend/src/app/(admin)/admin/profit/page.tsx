import type { Metadata } from 'next';
import { ProfitPage } from '@/features/profit';

export const metadata: Metadata = {
  title: 'Nexa·AI · 利润分析',
  description: '管理后台：利润分析看板——售价/成本/利润三段式聚合，按模型/供应商/分组多维拆解。',
};

/** /admin/profit 路由：利润分析看板页（AdminShell 内嵌）。 */
export default function Page() {
  return <ProfitPage />;
}
