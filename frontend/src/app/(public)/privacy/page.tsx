import type { Metadata } from 'next';
import { PublicShell } from '@/features/marketing';
import { LegalDoc, PRIVACY_DOC } from '@/features/legal';

export const metadata: Metadata = {
  title: 'Nexa·AI · 隐私政策',
  description: 'Nexa·AI 隐私政策：我们如何收集、使用、共享与保护您的个人信息，以及您对自身数据享有的权利。',
};

/** /privacy 路由：隐私政策页（公开站外壳 + LegalDoc）。 */
export default function Page() {
  return (
    <PublicShell footNote="以最小必要原则处理数据">
      <LegalDoc doc={PRIVACY_DOC} />
    </PublicShell>
  );
}
