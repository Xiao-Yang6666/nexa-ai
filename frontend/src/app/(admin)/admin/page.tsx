import type { Metadata } from 'next';
import { AdminDashboardPage } from '@/features/dashboard';

export const metadata: Metadata = {
  title: 'Nexa·AI 管理后台 · 全局概览',
  description: '管理后台全局概览：全站请求量/费用/渠道健康/用户与任务量 KPI 与趋势图表。',
};

/** /admin 路由：管理后台全局概览（AdminShell 内嵌）。 */
export default function Page() {
  return <AdminDashboardPage />;
}
