import type { Metadata } from 'next';
import { KeysPage } from '@/features/token';

export const metadata: Metadata = {
  title: 'Nexa·AI · API 密钥',
  description: '管理 API 密钥：创建、查看、禁用/删除密钥，查看接入信息。',
};

/** /keys 路由：API 密钥页（控制台 ConsoleShell 内嵌）。 */
export default function Page() {
  return <KeysPage />;
}
