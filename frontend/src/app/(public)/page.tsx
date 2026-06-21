import type { Metadata } from 'next';
import { PublicShell, HomeHero } from '@/features/marketing';

export const metadata: Metadata = {
  title: 'Nexa·AI — 满血直连，只付零头',
  description:
    'Nexa·AI 聚合直连 OpenAI / Anthropic / Google / DeepSeek 等全球大模型，一个 base_url 接入所有模型，按量付费只付零头。',
};

/**
 * / 路由：公开站首页（web-public/home）。
 * 统一外壳 PublicShell（玻璃导航 + 页脚）+ HomeHero（巨字主视觉 hero + 信任行）。
 */
export default function Page() {
  return (
    <PublicShell active="">
      <HomeHero />
    </PublicShell>
  );
}
