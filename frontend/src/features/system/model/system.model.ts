/**
 * features/system/model — 系统设置视图模型 + React Query hook（/api/option/，RootAuth）。
 *
 * 把扁平 {key,value}[] 选项列表映射为系统设置表单可消费的取值，并提供单键保存 mutation。
 *
 * 仅绑定「契约可确认」的选项键（StatusAggregateView 暴露 + F-4017/4018 校验分支证实）：
 *   SystemName / RegisterEnabled / EmailVerificationEnabled / GitHubOAuthEnabled
 *   / theme.frontend / QuotaForNewUser。
 * R3-01 系统配置面板键名契约（site.* / register.* / billing.* / ratelimit.* / smtp.* /
 *   security.* / advanced.*）由本 slice 定义，前后端共用；smtp.passwordSecret 为敏感键
 *   （键名以 Secret 结尾，GET 列表自动剔除值）。缓存清理/重置统计为操作非配置，留待专用端点。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getOptions, updateOption, type OptionItem } from '../api/system.api';

const QUOTA_PER_USD = 500_000;

/** 系统设置表单取值（契约可确认键 + R3-01 系统配置面板键）。 */
export interface SysSettingsVM {
  systemName: string;
  registerEnabled: boolean;
  emailVerification: boolean;
  githubOAuth: boolean;
  /** 前端主题：default（新版）/ classic（经典） */
  themeFrontend: string;
  /** 新用户默认额度（USD，由 QuotaForNewUser quota 换算） */
  newUserQuotaUsd: string;

  // ── R3-01 系统配置面板（键名契约见 slice R3-01） ──
  siteDescription: string;
  inviteOnly: boolean;
  billingCurrency: string;
  defaultRpm: string;
  defaultTpm: string;
  smtpHost: string;
  smtpPort: string;
  smtpUsername: string;
  /** SMTP 密码为敏感键（smtp.passwordSecret），GET 列表剔除值 → 仅知是否已设置 */
  smtpPasswordSet: boolean;
  smtpTls: boolean;
  force2fa: boolean;
  lockoutThreshold: string;
  sessionTtlMinutes: string;
  ipWhitelist: string;
  maintenanceMode: boolean;
  debugLog: boolean;
  requestTimeoutSec: string;
  logRetentionDays: string;
}

function find(items: OptionItem[], key: string): string | undefined {
  return items.find((o) => o.key === key)?.value;
}

function asBool(v: string | undefined): boolean {
  return v === 'true' || v === '1';
}

/** 选项列表 → 系统设置表单取值。 */
export function toSysSettings(items: OptionItem[]): SysSettingsVM {
  const quota = Number(find(items, 'QuotaForNewUser') ?? '0');
  return {
    systemName: find(items, 'SystemName') ?? '',
    registerEnabled: asBool(find(items, 'RegisterEnabled')),
    emailVerification: asBool(find(items, 'EmailVerificationEnabled')),
    githubOAuth: asBool(find(items, 'GitHubOAuthEnabled')),
    themeFrontend: find(items, 'theme.frontend') ?? 'default',
    newUserQuotaUsd: Number.isFinite(quota) ? (quota / QUOTA_PER_USD).toFixed(2) : '0.00',

    // R3-01：敏感键 smtp.passwordSecret 的值已被后端 GET 列表剔除 → 仅凭键是否出现判断「已设置」。
    siteDescription: find(items, 'site.description') ?? '',
    inviteOnly: asBool(find(items, 'register.invite_only')),
    billingCurrency: find(items, 'billing.currency') ?? 'USD',
    defaultRpm: find(items, 'ratelimit.default_rpm') ?? '',
    defaultTpm: find(items, 'ratelimit.default_tpm') ?? '',
    smtpHost: find(items, 'smtp.host') ?? '',
    smtpPort: find(items, 'smtp.port') ?? '',
    smtpUsername: find(items, 'smtp.username') ?? '',
    smtpPasswordSet: items.some((o) => o.key === 'smtp.passwordSecret'),
    smtpTls: asBool(find(items, 'smtp.tls')),
    force2fa: asBool(find(items, 'security.force_2fa')),
    lockoutThreshold: find(items, 'security.lockout_threshold') ?? '',
    sessionTtlMinutes: find(items, 'security.session_ttl_minutes') ?? '',
    ipWhitelist: find(items, 'security.ip_whitelist') ?? '',
    maintenanceMode: asBool(find(items, 'advanced.maintenance_mode')),
    debugLog: asBool(find(items, 'advanced.debug_log')),
    requestTimeoutSec: find(items, 'advanced.request_timeout_sec') ?? '',
    logRetentionDays: find(items, 'advanced.log_retention_days') ?? '',
  };
}

/** USD 字符串 → QuotaForNewUser quota 字符串（保存用）。 */
export function usdToQuotaStr(usd: string): string {
  const n = parseFloat(usd);
  return String(Math.round((Number.isFinite(n) ? n : 0) * QUOTA_PER_USD));
}

/**
 * R3-01 表单校验（与后端 OptionRegistry 规则对齐，错误提示中文）。
 * 空字符串视为「未设置」跳过校验（保存时也不会下发该键）。
 * 返回 null 表示通过，否则返回首个错误文案。
 */
export function validateSysSettings(vm: SysSettingsVM): string | null {
  if (vm.siteDescription.length > 500) {
    return '站点描述不能超过 500 字';
  }
  const nonNegInt = (v: string, label: string): string | null => {
    if (v === '') return null;
    if (!/^\d+$/.test(v)) return `${label}必须为非负整数`;
    return null;
  };
  const posInt = (v: string, label: string): string | null => {
    if (v === '') return null;
    if (!/^\d+$/.test(v) || Number(v) < 1) return `${label}必须为不小于 1 的整数`;
    return null;
  };
  return (
    nonNegInt(vm.defaultRpm, '默认 RPM 限制') ??
    nonNegInt(vm.defaultTpm, '默认 TPM 限制') ??
    nonNegInt(vm.lockoutThreshold, '账号锁定阈值') ??
    nonNegInt(vm.logRetentionDays, '日志保留天数') ??
    posInt(vm.sessionTtlMinutes, '会话有效期') ??
    posInt(vm.requestTimeoutSec, '请求超时') ??
    ((): string | null => {
      if (vm.smtpPort === '') return null;
      const p = Number(vm.smtpPort);
      if (!/^\d+$/.test(vm.smtpPort) || p < 1 || p > 65535) return 'SMTP 端口必须在 1-65535 之间';
      return null;
    })() ??
    (vm.billingCurrency !== 'CNY' && vm.billingCurrency !== 'USD' ? '计费货币必须为 CNY 或 USD' : null)
  );
}

/** 系统设置查询 hook（GET /api/option/）。 */
export function useSysSettings() {
  return useQuery({
    queryKey: ['system', 'options'],
    queryFn: getOptions,
    select: toSysSettings,
  });
}

/** 单键保存 mutation（PUT /api/option/），成功后刷新选项缓存。 */
export function useSaveOption() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => updateOption(key, value),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['system', 'options'] });
    },
  });
}
