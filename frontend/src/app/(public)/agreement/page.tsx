import type { Metadata } from 'next';
import { PublicShell } from '@/features/marketing';
import { LegalDoc, AGREEMENT_DOC } from '@/features/legal';

export const metadata: Metadata = {
  title: 'Nexa·AI · 用户服务协议',
  description: 'Nexa·AI 用户服务协议：服务内容、账户与计费规则、行为规范、知识产权、免责声明与争议解决。',
};

/** /agreement 路由：用户服务协议页（公开站外壳 + LegalDoc）。 */
export default function Page() {
  return (
    <PublicShell footNote="为开发者而生">
      <LegalDoc doc={AGREEMENT_DOC} />
    </PublicShell>
  );
}
