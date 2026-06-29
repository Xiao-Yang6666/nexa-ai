/**
 * features/provider-account/model — 供应商账号管理域视图模型 + React Query hooks。
 *
 * - AccountView → 列表行视图模型（status 码派生展示状态）。
 * - 后端 status 码：active=启用 / disabled=禁用 / rate_limited=限流。
 * - credentials 绝不在列表/视图出现（后端不下发）。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getModelGroups } from '@/features/modelgroup/api/modelgroup.api';
import {
  getAccounts,
  createAccount,
  updateAccount,
  deleteAccount,
  toggleAccount,
  probeModels,
  testModel,
  type AccountView,
  type AccountCreateRequest,
  type AccountUpdateRequest,
  type AccountListResponse,
  type ProbeModelsRequest,
  type TestModelRequest,
} from '../api/provider-account.api';

/** 账号展示状态。 */
export type AccountStatus = 'active' | 'disabled' | 'rate_limited';

/** 由后端 status 码派生展示状态（未知回落 active）。 */
export function deriveAccountStatus(status: string | undefined): AccountStatus {
  if (status === 'disabled') return 'disabled';
  if (status === 'rate_limited') return 'rate_limited';
  return 'active';
}

/** 账号列表行视图模型。 */
export interface AccountRowVM {
  id: number;
  name: string;
  platform: string;
  type: string;
  st: AccountStatus;
  concurrency: number;
  priority: number;
  weight: number;
  tag?: string;
  /** Base URL */
  baseUrl?: string;
  /** 模型映射 JSON */
  modelMapping?: string;
  /** 支持的模型 */
  models?: string;
  /** 自动封禁 */
  autoBan: boolean;
  /** 过期时间文案（空=永久） */
  exp: string;
  /** 过期自动暂停 */
  autoPause: boolean;
  /** 账号级成本倍率（默认 1.0） */
  rateMultiplier: number;
  /** 更新时间文案 */
  updatedAt: string;
  /** 所属分组数 */
  groupCount: number;
  /** 所属分组名列表（用于分组列展示 + 分组筛选） */
  groupNames: string[];
}

function fmtTime(ts: number | undefined | null): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** AccountView → 列表行视图模型。 */
export function toAccountRowVM(view: AccountView): AccountRowVM {
  const exp = view.expires_at ?? 0;
  return {
    id: view.id ?? 0,
    name: view.name || '—',
    platform: view.platform || '—',
    type: view.type || '—',
    st: deriveAccountStatus(view.status),
    concurrency: view.concurrency ?? 0,
    priority: view.priority ?? 0,
    weight: view.weight ?? 0,
    tag: view.tag ?? undefined,
    baseUrl: view.base_url ?? undefined,
    modelMapping: view.model_mapping ?? undefined,
    models: view.models ?? undefined,
    autoBan: view.auto_ban ?? false,
    exp: exp > 0 ? fmtTime(exp) : '永久',
    autoPause: view.auto_pause_on_expired ?? true,
    rateMultiplier: view.rate_multiplier ?? 1,
    updatedAt: fmtTime(view.updated_time),
    groupCount: view.groups?.length ?? 0,
    groupNames: (view.groups ?? []).map((g) => g.group).filter((g) => !!g),
  };
}

/* ── React Query hooks ─────────────────────────────────────────────────── */

/** 账号列表查询 hook。返回 { rows, total }。 */
export function useAccounts(params: {
  page?: number;
  pageSize?: number;
  platform?: string;
} = {}) {
  return useQuery({
    queryKey: ['provider-account', 'list', params],
    queryFn: () => getAccounts(params),
    select: (data: AccountListResponse) => ({
      rows: (data.items ?? []).map(toAccountRowVM),
      total: data.total ?? 0,
    }),
  });
}

/** 创建账号 mutation。 */
export function useCreateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AccountCreateRequest) => createAccount(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-account'] }),
  });
}

/** 编辑账号 mutation。 */
export function useUpdateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: AccountUpdateRequest }) => updateAccount(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-account'] }),
  });
}

/** 删除账号 mutation。 */
export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteAccount(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-account'] }),
  });
}

/** 启停账号 mutation。 */
export function useToggleAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enable }: { id: number; enable: boolean }) => toggleAccount(id, enable),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provider-account'] }),
  });
}

/** 探测上游模型列表 mutation（不影响列表缓存）。 */
export function useProbeModels() {
  return useMutation({
    mutationFn: (req: ProbeModelsRequest) => probeModels(req),
  });
}

/** 模型连通性测试 mutation（对已保存账号的指定模型发一次 chat 调用，不影响列表缓存）。 */
export function useTestModel() {
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: TestModelRequest }) => testModel(id, req),
  });
}

/**
 * 价格分组选项（账号编辑抽屉「所属分组」勾选源）。
 *
 * 账号通过 group（=价格分组 code）供货：用户令牌 group → 选该 group 下的账号 → 用该组倍率定价。
 * 勾选已有价格分组即把账号 group 对齐到分组 code，避免手敲拼错供不上货。
 */
export function useAccountGroupOptions() {
  return useQuery({
    queryKey: ['provider-account', 'group-options'],
    queryFn: async () => {
      const groups = await getModelGroups();
      return groups.map((g) => ({ code: g.code, name: g.name, ratio: g.base_price_ratio }));
    },
    staleTime: 5 * 60 * 1000,
  });
}

