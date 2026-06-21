import type { Metadata } from 'next';
import { AuthSplitLayout, Accent, LoginForm } from '@/features/account';

export const metadata: Metadata = {
  title: '登录 · Nexa·AI',
  description: '登录你的 Nexa 账号，继续用一个 base_url 直连全球大模型。',
};

/**
 * /login 路由：登录页（web-public/login）。
 * 左右分栏壳 AuthSplitLayout（品牌光束侧 + 玻璃卡）+ LoginForm（接 openapi mock）。
 */
export default function Page() {
  return (
    <AuthSplitLayout
      tagline={
        <>
          欢迎回来，<Accent>满血直连</Accent>继续
        </>
      }
    >
      <LoginForm />
    </AuthSplitLayout>
  );
}
