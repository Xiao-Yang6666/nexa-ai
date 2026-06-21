import type { Metadata } from 'next';
import { PublicShell } from '@/features/marketing';
import { RankingPage } from '@/features/ranking';

export const metadata: Metadata = {
  title: 'Nexa·AI · 模型排行榜',
  description:
    '从综合实力、性价比、响应速度到调用热度四个维度评估主流大模型，所有模型均经 Nexa 网关统一接入，相比官方价省到位。',
};

/** /ranking 路由：模型排行榜页（公开站外壳 + RankingPage）。 */
export default function Page() {
  return (
    <PublicShell active="ranking">
      <RankingPage />
    </PublicShell>
  );
}
