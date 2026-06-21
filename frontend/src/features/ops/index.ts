/**
 * features/ops — 运维监控域 public API（系统健康/资源指标/图表/日志/告警）。
 * 跨域引用只走本入口，不深 import 域内文件。
 * 管理端可展示全字段，不受客户端零泄露约束。
 */
export { OpsMonitorPage } from './components/OpsMonitorPage';
