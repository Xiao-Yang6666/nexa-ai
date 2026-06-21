import type { Metadata } from 'next';
import { TasksMonitorPage } from '@/features/task';

export const metadata: Metadata = {
  title: 'Nexa·AI · 任务监控',
  description: '管理后台任务监控：队列状态、超时扫描、状态分布、类型耗时、全量任务列表。',
};

/** /admin/tasks-monitor 路由：任务监控页（AdminShell 内嵌）。 */
export default function Page() {
  return <TasksMonitorPage />;
}
