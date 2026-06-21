import type { Metadata } from 'next';
import { PublicShell } from '@/features/marketing';
import { ModelsPage } from '@/features/model';

export const metadata: Metadata = {
  title: 'Nexa·AI · 模型广场',
  description:
    '聚合 OpenAI / Anthropic / Google / DeepSeek 等主流厂商模型，统一协议、统一计费，满血直连只付零头。',
};

/** /models 路由：模型广场页（公开站外壳 + ModelsPage）。 */
export default function Page() {
  return (
    <PublicShell active="models">
      <ModelsPage />
    </PublicShell>
  );
}
