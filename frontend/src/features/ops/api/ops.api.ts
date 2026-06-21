/**
 * features/ops/api — 运维/运营域接口调用（基于 shared/api 的 http）。
 * 路径/方法/出参逐字对齐 openapi.yaml（模块九：ops），不臆造字段。
 */
import { http } from '@/shared/api';
import type { StatusAggregateView } from '@/shared/api';

/**
 * 站点状态聚合（系统配置/能力开关的真实快照）。
 * openapi: GET /api/status (F-4039, public) → ApiResponse{ data: StatusAggregateView }
 *
 * 注：契约 ops 模块未提供 CPU/内存/磁盘/GC/QPS 等主机资源指标端点；
 * /metrics 为 Prometheus 文本（基础设施抓取，非前端消费）。本页以 /api/status
 * 这一真实的系统状态聚合端点为数据源，展示系统信息与各能力开关状态。
 */
export function getSystemStatus(): Promise<StatusAggregateView> {
  return http.get<StatusAggregateView>('/api/status');
}
