import type { Metadata } from 'next';
import { UsagePage } from '@/features/log';

export const metadata: Metadata = {
  title: 'Nexa·AI · 调用明细',
  description: '本人调用明细：按模型、分组、状态筛选，展开查看详情。',
};

/** /usage 路由：调用明细页（控制台 ConsoleShell 内嵌）。 */
export default function Page() {
  return <UsagePage />;
}
