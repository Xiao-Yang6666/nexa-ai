import type { Metadata } from 'next';
import { PublicShell } from '@/features/marketing';
import { PricingPage } from '@/features/billing';

export const metadata: Metadata = {
  title: 'Nexa·AI · 价格',
  description:
    '按量付费或预付费额度，满血直连只付零头。与官方价对比，主流模型每百万 token 直连价一目了然，注册即送测试额度。',
};

/** /pricing 路由：价格页（公开站外壳 + PricingPage）。 */
export default function Page() {
  return (
    <PublicShell active="pricing">
      <PricingPage />
    </PublicShell>
  );
}
