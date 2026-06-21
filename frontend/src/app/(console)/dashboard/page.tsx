import type { Metadata } from 'next';
import { DashboardPage } from '@/features/dashboard';

export const metadata: Metadata = {
  title: 'Nexa·AI · 仪表盘',
  description: '控制台核心数据概览：本月调用量、消费、余额、趋势图表。',
};

/** /dashboard 路由：仪表盘页（控制台 ConsoleShell 内嵌）。 */
export default function Page() {
  return <DashboardPage />;
}
