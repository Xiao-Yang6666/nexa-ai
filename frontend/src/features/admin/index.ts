/**
 * features/admin — 管理后台外壳域（顶栏 + 角色切换 + 左侧管理导航 + 面包屑 + 内容区）。
 * 与 features/console 的 ConsoleShell 对称：承载 admin 端跨页复用的应用壳。
 * AdminView 可展示全字段（成本/利润/上游 B/供应商），不受客户端零泄露约束。
 */
export { AdminShell } from './components/AdminShell';
export type { AdminShellProps } from './components/AdminShell';
