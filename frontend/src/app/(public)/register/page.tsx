import type { Metadata } from 'next';
import { AuthSplitLayout, Accent, RegisterForm } from '@/features/account';

export const metadata: Metadata = {
  title: '注册 · Nexa·AI',
  description: '几秒注册 Nexa 账号，即刻用一个 base_url 直连 OpenAI / Anthropic / Google / DeepSeek。',
};

/**
 * /register 路由：注册页（web-public/register）。
 * 左右分栏壳 AuthSplitLayout（品牌光束侧 + 玻璃卡）+ RegisterForm（接 openapi mock）。
 */
export default function Page() {
  return (
    <AuthSplitLayout
      tagline={
        <>
          开始用 Nexa <Accent>直连全球大模型</Accent>
        </>
      }
    >
      <RegisterForm />
    </AuthSplitLayout>
  );
}
