import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { Providers } from './providers';
import './globals.css';

export const metadata: Metadata = {
  title: 'Nexa·AI — 满血直连，只付零头',
  description:
    'Nexa·AI 聚合直连 OpenAI / Anthropic / Google / DeepSeek 等全球大模型，一个 base_url 接入所有模型。',
};

/**
 * 根布局。公开站为深色门面（hd-bg），故 html 不挂 data-scheme（保持默认浅色 token，
 * 深色由各门面页 body 自管），应用区页面（console/admin）后续挂 data-scheme=dark。
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN" data-theme="teal">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
