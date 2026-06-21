import type { Metadata } from 'next';
import { OpsMonitorPage } from '@/features/ops';

export const metadata: Metadata = {
  title: 'Nexa·AI 管理后台 · 运维监控',
  description: '管理后台运维监控：系统健康状态、CPU/内存/磁盘/QPS 资源指标、GC 分布、日志文件管理。',
};

/** /admin/ops 路由：运维监控（AdminShell 内嵌）。 */
export default function Page() {
  return <OpsMonitorPage />;
}
