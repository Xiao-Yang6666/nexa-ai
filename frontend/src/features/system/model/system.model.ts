/**
 * features/system/model — 系统设置视图模型 + React Query hook（/api/option/，RootAuth）。
 *
 * 把扁平 {key,value}[] 选项列表映射为系统设置表单可消费的取值，并提供单键保存 mutation。
 *
 * 仅绑定「契约可确认」的选项键（StatusAggregateView 暴露 + F-4017/4018 校验分支证实）：
 *   SystemName / RegisterEnabled / EmailVerificationEnabled / GitHubOAuthEnabled
 *   / theme.frontend / QuotaForNewUser。
 * 其余表单项（SMTP、RPM/TPM、安全策略、高级/危险操作）的 option 键名未在可见契约中枚举，
 * 且 PUT /api/option/ 为逐键严格校验 —— 不臆造键名，对应控件标注 TODO(api-missing)。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getOptions, updateOption, type OptionItem } from '../api/system.api';

const QUOTA_PER_USD = 500_000;

/** 系统设置表单取值（仅契约可确认键）。 */
export interface SysSettingsVM {
  systemName: string;
  registerEnabled: boolean;
  emailVerification: boolean;
  githubOAuth: boolean;
  /** 前端主题：default（新版）/ classic（经典） */
  themeFrontend: string;
  /** 新用户默认额度（USD，由 QuotaForNewUser quota 换算） */
  newUserQuotaUsd: string;
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
  };
}

/** USD 字符串 → QuotaForNewUser quota 字符串（保存用）。 */
export function usdToQuotaStr(usd: string): string {
  const n = parseFloat(usd);
  return String(Math.round((Number.isFinite(n) ? n : 0) * QUOTA_PER_USD));
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
