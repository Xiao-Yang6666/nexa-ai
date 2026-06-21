'use client';

/**
 * features/ops/model — 运维监控视图模型 + React Query hook（GET /api/status）。
 *
 * 把 StatusAggregateView（系统配置/能力开关聚合）转成可展示的「系统信息」+「能力状态卡」。
 * 管理端可见全字段；本页字段均为非敏感站点配置（契约 StatusAggregateView 已剔除敏感配置）。
 */
import { useQuery } from '@tanstack/react-query';
import type { StatusAggregateView } from '@/shared/api';
import { getSystemStatus } from '../api/ops.api';

/** 单个能力开关状态项（启用/停用）。 */
export interface OpsCapabilityVM {
  /** 展示名 */
  name: string;
  /** 契约字段名（诊断用） */
  key: string;
  /** 是否启用 */
  enabled: boolean;
}

/** 系统状态视图模型（系统信息 + 能力开关列表）。 */
export interface OpsStatusVM {
  systemName: string;
  theme: string;
  /** 能力开关分组列表 */
  capabilities: OpsCapabilityVM[];
  /** 启用数 / 总数（用于 KPI） */
  enabledCount: number;
  totalCount: number;
}

function cap(name: string, key: string, v: boolean | undefined): OpsCapabilityVM {
  return { name, key, enabled: !!v };
}

/** StatusAggregateView → 系统状态视图模型。 */
export function toOpsStatusVM(s: StatusAggregateView): OpsStatusVM {
  const capabilities: OpsCapabilityVM[] = [
    cap('用户注册', 'register_enabled', s.register_enabled),
    cap('邮箱验证', 'email_verification', s.email_verification),
    cap('GitHub 登录', 'github_oauth', s.github_oauth),
    cap('Discord 登录', 'discord_oauth', s.discord_oauth),
    cap('OIDC 登录', 'oidc_enabled', s.oidc_enabled),
    cap('LinuxDO 登录', 'linuxdo_oauth', s.linuxdo_oauth),
    cap('微信登录', 'wechat_login', s.wechat_login),
    cap('Telegram 登录', 'telegram_oauth', s.telegram_oauth),
    cap('Turnstile 校验', 'turnstile_check', s.turnstile_check),
    cap('每日签到', 'checkin_enabled', s.checkin_enabled),
    cap('用户协议', 'user_agreement_enabled', s.user_agreement_enabled),
    cap('隐私政策', 'privacy_policy_enabled', s.privacy_policy_enabled),
    cap('自动分组', 'default_use_auto_group', s.default_use_auto_group),
  ];
  const enabledCount = capabilities.filter((c) => c.enabled).length;
  return {
    systemName: s.system_name || '—',
    theme: s.theme || 'default',
    capabilities,
    enabledCount,
    totalCount: capabilities.length,
  };
}

/** 系统状态查询 hook（GET /api/status）。 */
export function useSystemStatus() {
  return useQuery({
    queryKey: ['ops', 'status'],
    queryFn: getSystemStatus,
    select: toOpsStatusVM,
  });
}
