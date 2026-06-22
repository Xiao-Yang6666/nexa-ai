/**
 * features/shell — 全站统一应用外壳域（CR-01/CR-03）。
 *
 * 单一 AppShell（顶栏 + 角色动态左侧导航 + 面包屑 + 内容区）取代原 console/admin 两套静态壳；
 * 菜单来自单一数据源 nav-tree.ts，按登录用户角色过滤。RouteShellLayout 提供路由组级
 * 深色 scheme + 角色守卫。跨域引用只走本入口。
 */
export { AppShell } from './components/AppShell';
export type { AppShellProps } from './components/AppShell';
export {
  RouteShellLayout,
  ConsoleRouteLayout,
  AdminRouteLayout,
  RootRouteLayout,
} from './components/RouteShellLayout';
export type { RouteShellLayoutProps } from './components/RouteShellLayout';
export { NAV } from './nav-tree';
export type { NavItem, NavGroup } from './nav-tree';
