import type { Metadata } from 'next';
import { BillingRulesPage } from '@/features/billing';

export const metadata: Metadata = {
  title: 'Nexa·AI 管理后台 · 计费规则',
  description: '管理后台计费规则：模型/分组/缓存/补全倍率配置、阶梯计费规则、合规闸门与倍率档位分布。',
};

/** /admin/billing-rules 路由：计费规则（AdminShell 内嵌）。 */
export default function Page() {
  return <BillingRulesPage />;
}
