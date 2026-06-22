/**
 * features/shell — 单一导航数据源（CR-01/CR-03）。
 *
 * 合并原 ConsoleShell / AdminShell 两张写死的静态菜单为一棵带 minRole 的树：
 * 普通项(COMMON) / 管理项(ADMIN) / 超管项(ROOT)。root 天然是普通用户菜单的超集。
 * AppShell 渲染时按当前登录用户角色过滤：nav.items.filter(it => role >= it.minRole)，
 * 过滤后空分组丢弃。彻底取代「两套 Shell + 跨组视图切换链接」导致的菜单退化 bug。
 *
 * 角色可见性与后端鉴权对齐：管理端 @RequireRole(ADMIN) → minRole=ADMIN；
 * 运维监控(ops)/系统设置(sys) 后端 @RequireRole(ROOT) → minRole=ROOT。
 *
 * 注意：从 account model 叶子模块导入 ROLE（非 barrel），打破 account↔admin 循环依赖——
 * barrel 经 CJS 转译后 re-export 非活绑定，在本模块顶层求值 NAV 时会拿到 undefined。
 */
import { ROLE } from '@/features/account/model/account.model';

export interface NavItem {
  /** 路由 id，与各页 AppShell activeId 对齐，决定侧栏高亮 */
  id: string;
  label: string;
  /** App Router flat 路径（/admin/* 前缀保留，避免大规模改 href） */
  href: string;
  ic: string;
  /** 可见所需最低角色：COMMON / ADMIN / ROOT */
  minRole: number;
}

export interface NavGroup {
  group: string;
  /** 管理区分组：带「管理」徽章 + 顶部分隔线 */
  admin?: boolean;
  items: NavItem[];
}

/**
 * 全量导航树（合并 console + admin）。普通分组在前（COMMON），管理分组在后（ADMIN/ROOT）。
 * root/admin 用户 = 普通用户菜单全部入口 + 管理入口；普通用户只见 COMMON 项。
 */
export const NAV: NavGroup[] = [
  {
    group: '概览',
    items: [{ id: 'dashboard', label: '仪表盘', href: '/dashboard', ic: 'gauge', minRole: ROLE.COMMON }],
  },
  {
    group: '接入',
    items: [
      { id: 'keys', label: 'API 密钥', href: '/keys', ic: 'key', minRole: ROLE.COMMON },
      { id: 'model-map', label: '模型映射', href: '/model-map', ic: 'share', minRole: ROLE.COMMON },
      { id: 'usage', label: '用量统计', href: '/usage', ic: 'bar', minRole: ROLE.COMMON },
      { id: 'tasks', label: '异步任务', href: '/tasks', ic: 'tasks', minRole: ROLE.COMMON },
    ],
  },
  {
    group: '账户',
    items: [
      { id: 'billing', label: '账单与计费', href: '/billing', ic: 'receipt', minRole: ROLE.COMMON },
      { id: 'recharge', label: '余额充值', href: '/recharge', ic: 'wallet', minRole: ROLE.COMMON },
    ],
  },
  {
    group: '增长',
    items: [
      { id: 'checkin', label: '每日签到', href: '/checkin', ic: 'calendar', minRole: ROLE.COMMON },
      { id: 'referral', label: '分销推广', href: '/referral', ic: 'share', minRole: ROLE.COMMON },
    ],
  },
  {
    group: '设置',
    items: [{ id: 'settings', label: '个人设置', href: '/settings', ic: 'settings', minRole: ROLE.COMMON }],
  },
  {
    group: '管理总览',
    admin: true,
    items: [{ id: 'admin-dashboard', label: '全局概览', href: '/admin', ic: 'grid', minRole: ROLE.ADMIN }],
  },
  {
    group: '资源管理',
    admin: true,
    items: [
      { id: 'channels', label: '渠道管理', href: '/admin/channels', ic: 'server', minRole: ROLE.ADMIN },
      { id: 'users', label: '用户管理', href: '/admin/users', ic: 'users', minRole: ROLE.ADMIN },
      { id: 'models', label: '模型/供应商', href: '/admin/models', ic: 'cube', minRole: ROLE.ADMIN },
      { id: 'model-groups', label: '模型组管理', href: '/admin/model-groups', ic: 'layers', minRole: ROLE.ADMIN },
      { id: 'groups', label: '预填分组', href: '/admin/groups', ic: 'layers', minRole: ROLE.ADMIN },
    ],
  },
  {
    group: '运营',
    admin: true,
    items: [
      { id: 'tasks-monitor', label: '任务监控', href: '/admin/tasks-monitor', ic: 'tasks', minRole: ROLE.ADMIN },
      { id: 'billing-rules', label: '计费规则', href: '/admin/billing-rules', ic: 'calc', minRole: ROLE.ADMIN },
      { id: 'profit', label: '利润分析', href: '/admin/profit', ic: 'pulse', minRole: ROLE.ADMIN },
      { id: 'redeem', label: '兑换码', href: '/admin/redeem', ic: 'ticket', minRole: ROLE.ADMIN },
    ],
  },
  {
    group: '系统',
    admin: true,
    items: [
      { id: 'logs', label: '日志审计', href: '/admin/logs', ic: 'file', minRole: ROLE.ADMIN },
      { id: 'ops', label: '运维监控', href: '/admin/ops', ic: 'pulse', minRole: ROLE.ROOT },
      { id: 'sys', label: '系统设置', href: '/admin/sys-settings', ic: 'settings', minRole: ROLE.ROOT },
    ],
  },
];
