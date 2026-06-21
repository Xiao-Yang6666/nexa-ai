/**
 * features/dashboard — 仪表盘域：KPI 概览 + 趋势/分布/延迟图表 + 最近调用。
 * 与后端 bounded context「dashboard/概览」对齐。客户端零泄露：仅本人维度，无 cost/profit/上游字段。
 */
export { DashboardPage } from './components/DashboardPage';
export { AdminDashboardPage } from './components/AdminDashboardPage';
export { useKpi } from './model/dashboard.model';
export type { KpiVM } from './model/dashboard.model';
