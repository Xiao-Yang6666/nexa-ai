/**
 * shared/api/mock-console — 控制台 self-scope 端点的开发期 mock 桩。
 *
 * 覆盖签到 / 异步任务 / 个人信息+设置 / 邀请码 / 用户模型映射 CRUD，
 * 全部返回符合 openapi 客户视图 DTO 的桩数据（零泄露：无 cost/profit/上游B/供应商）。
 * 与 mock.ts 的 auth/pricing 桩合并安装；后端就绪后关 NEXT_PUBLIC_USE_MOCK 即走真实接口。
 *
 * 设计：导出「精确路由表」+「正则路由表」，后者承接带路径参数的端点
 * （如 PUT/DELETE /api/user/self/model_aliases/{id}），由 installMock 统一调度。
 */
import type {
  CheckinStatusView,
  CheckinResult,
  TaskUserView,
  UserView,
  UserModelAliasUserView,
  TokenUserView,
  UserLogView,
  LogStat,
} from './types';

/** mock 响应包络（对齐 ApiResponse）。 */
interface MockEnvelope {
  success: boolean;
  message?: string;
  data?: unknown;
}

/** 精确匹配处理器：key 形如 'GET /api/user/checkin'。 */
export type ExactHandler = (
  init: RequestInit | undefined,
  body: unknown,
) => Response | undefined;

/** 正则匹配处理器：用于带路径参数的端点，params 为捕获组。 */
export type RegexHandler = (
  init: RequestInit | undefined,
  body: unknown,
  params: string[],
) => Response | undefined;

function json(body: MockEnvelope, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/* ── 内存桩状态（仅 mock 期有效，刷新即重置） ───────────────────────────── */

const SELF_USER: UserView = {
  id: 10086,
  username: 'morgan.li',
  display_name: 'Morgan Li',
  role: 1,
  status: 1,
  group: 'vip',
  quota: 12_850_000,
  used_quota: 3_204_180,
  request_count: 18_472,
  aff_code: 'MORGAN-7K2F',
  aff_count: 48,
  aff_quota: 24_680_000,
  aff_history_quota: 24_680_000,
  email: 'morgan.li@nexa.ai',
  last_login_at: Math.floor(Date.now() / 1000),
  setting: {
    display_name: 'Morgan Li',
    company: 'Nexa 智能科技有限公司',
    warning_type: 'email',
    warning_threshold: 20,
    notify_billing: true,
    notify_balance: true,
    notify_security: true,
    notify_weekly: false,
    notify_product: false,
    notify_marketing: false,
    api_default_timeout: 60,
    api_retry: 1,
    api_stream_first: true,
    api_log_request: true,
    totp_enabled: true,
  },
};

/** 当月签到桩：今天 20 号，已签 1..12 + 14,16,18。 */
function buildCheckinStatus(): CheckinStatusView {
  const checkedDays = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18];
  const records = [
    { checkin_date: '2026-06-20', quota_awarded: 200_000 },
    { checkin_date: '2026-06-18', quota_awarded: 200_000 },
    { checkin_date: '2026-06-16', quota_awarded: 200_000 },
    { checkin_date: '2026-06-14', quota_awarded: 200_000 },
    { checkin_date: '2026-06-12', quota_awarded: 1_000_000 },
    { checkin_date: '2026-06-11', quota_awarded: 200_000 },
    { checkin_date: '2026-06-10', quota_awarded: 200_000 },
    { checkin_date: '2026-06-09', quota_awarded: 200_000 },
    { checkin_date: '2026-06-08', quota_awarded: 500_000 },
    { checkin_date: '2026-06-07', quota_awarded: 200_000 },
  ];
  return {
    total_quota: 9_400_000,
    total_checkins: 86,
    checkin_count: checkedDays.length,
    checked_in_today: false,
    records,
  };
}

let checkinStatus = buildCheckinStatus();

/** 异步任务桩列表（contract enum 状态）。 */
const TASKS: TaskUserView[] = [
  { id: 1, task_id: 'tsk_9f3a21', platform: 'embedding', action: '批量 Embedding', status: 'IN_PROGRESS', progress: '64', submit_time: 1_750_000_000, fail_reason: '' },
  { id: 2, task_id: 'tsk_8e1c07', platform: 'parse', action: '长文档解析', status: 'IN_PROGRESS', progress: '23', submit_time: 1_750_000_000, fail_reason: '' },
  { id: 3, task_id: 'tsk_7d92bb', platform: 'inference', action: '批量推理', status: 'QUEUED', progress: '0', submit_time: 1_750_000_000, fail_reason: '' },
  { id: 4, task_id: 'tsk_6c4480', platform: 'transcribe', action: '语音转写', status: 'SUCCESS', progress: '100', submit_time: 1_749_900_000, fail_reason: '' },
  { id: 5, task_id: 'tsk_5b1f9e', platform: 'embedding', action: '批量 Embedding', status: 'SUCCESS', progress: '100', submit_time: 1_749_900_000, fail_reason: '' },
  { id: 6, task_id: 'tsk_4a77d3', platform: 'parse', action: '长文档解析', status: 'FAILURE', progress: '47', submit_time: 1_749_800_000, fail_reason: '输入文件格式错误' },
  { id: 7, task_id: 'tsk_3920ee', platform: 'inference', action: '批量推理', status: 'SUCCESS', progress: '100', submit_time: 1_749_800_000, fail_reason: '' },
  { id: 8, task_id: 'tsk_28f1a5', platform: 'embedding', action: '批量 Embedding', status: 'IN_PROGRESS', progress: '88', submit_time: 1_749_700_000, fail_reason: '' },
  { id: 9, task_id: 'tsk_1d6b40', platform: 'transcribe', action: '语音转写', status: 'SUCCESS', progress: '100', submit_time: 1_749_700_000, fail_reason: '' },
  { id: 10, task_id: 'tsk_0c5e2f', platform: 'parse', action: '长文档解析', status: 'FAILURE', progress: '12', submit_time: 1_749_600_000, fail_reason: '第 12 行超出 token 上限' },
  { id: 11, task_id: 'tsk_fb83c1', platform: 'inference', action: '批量推理', status: 'SUCCESS', progress: '100', submit_time: 1_749_500_000, fail_reason: '' },
  { id: 12, task_id: 'tsk_ea4709', platform: 'embedding', action: '批量 Embedding', status: 'QUEUED', progress: '0', submit_time: 1_749_400_000, fail_reason: '' },
  { id: 13, task_id: 'tsk_d93f8a', platform: 'transcribe', action: '语音转写', status: 'SUCCESS', progress: '100', submit_time: 1_749_300_000, fail_reason: '' },
  { id: 14, task_id: 'tsk_c81b66', platform: 'parse', action: '长文档解析', status: 'SUCCESS', progress: '100', submit_time: 1_749_200_000, fail_reason: '' },
];

/** 候选模型 A 全集（公开名，绝不含 B）——对齐 /api/user/self/model_aliases/candidates。 */
const CANDIDATE_MODELS: string[] = [
  'opus-4.8', 'opus-4.8-增强', 'opus-4.8-经济',
  'gpt-4o', 'gpt-4o-mini', 'gpt-4.5',
  'gemini-2.5-pro', 'gemini-2.5-flash',
  'deepseek-v3', 'deepseek-r1',
  'claude-3-5-sonnet', 'claude-3-opus',
  'qwen-max', 'qwen-plus',
];

/** 用户模型映射桩（user + group 两作用域共存于一表，scope_type 区分）。 */
let aliases: UserModelAliasUserView[] = [
  { id: 1, scope_type: 'user', scope_id: '10086', alias: 'claude-3-5-sonnet', target: 'opus-4.8-经济', enabled: true, created_time: 1_749_000_000 },
  { id: 2, scope_type: 'user', scope_id: '10086', alias: 'my-gpt', target: 'gpt-4o', enabled: true, created_time: 1_749_000_000 },
  { id: 3, scope_type: 'user', scope_id: '10086', alias: 'gpt-4.5', target: 'gpt-4.5', enabled: false, created_time: 1_749_000_000 },
  { id: 4, scope_type: 'user', scope_id: '10086', alias: 'team-fast', target: 'gpt-4o-mini', enabled: true, created_time: 1_749_000_000 },
  { id: 5, scope_type: 'user', scope_id: '10086', alias: 'legacy-opus', target: 'opus-4.8', enabled: true, created_time: 1_749_000_000 },
  { id: 6, scope_type: 'group', scope_id: 'vip', alias: 'default-chat', target: 'opus-4.8-增强', enabled: true, created_time: 1_749_000_000 },
  { id: 7, scope_type: 'group', scope_id: 'vip', alias: 'embed-model', target: 'gpt-4o-mini', enabled: true, created_time: 1_749_000_000 },
  { id: 8, scope_type: 'group', scope_id: 'vip', alias: 'analytics-llm', target: 'deepseek-v3', enabled: true, created_time: 1_749_000_000 },
];

let nextAliasId = 9;

function parseBody(init: RequestInit | undefined): unknown {
  if (typeof init?.body === 'string') {
    try {
      return JSON.parse(init.body);
    } catch {
      return undefined;
    }
  }
  return undefined;
}

/* ── 精确路由表 ───────────────────────────────────────────────────────── */

export const CONSOLE_EXACT_ROUTES: Record<string, ExactHandler> = {
  // GET /api/user/self（F-1045）→ UserView（含 setting）
  'GET /api/user/self': () => json({ success: true, data: SELF_USER }),

  // PUT /api/user/self/setting（F-1014）→ SuccessResponse
  'PUT /api/user/self/setting': (_init, body) => {
    const b = (body ?? {}) as { setting?: Record<string, unknown> };
    if (b.setting && SELF_USER.setting) {
      SELF_USER.setting = { ...SELF_USER.setting, ...b.setting };
    }
    return json({ success: true, message: '设置已保存', data: null });
  },

  // GET /api/user/self/aff（F-1039）→ 邀请码字符串
  'GET /api/user/self/aff': () =>
    json({ success: true, data: SELF_USER.aff_code }),

  // GET /api/user/checkin（F-1047）→ CheckinStatusView
  'GET /api/user/checkin': () =>
    json({ success: true, data: checkinStatus }),

  // POST /api/user/checkin（F-1046）→ CheckinResult
  'POST /api/user/checkin': () => {
    if (checkinStatus.checked_in_today) {
      return json({ success: false, message: '今日已签到' }, 400);
    }
    const awarded = 200_000;
    const newQuota = (checkinStatus.total_quota ?? 0) + awarded;
    checkinStatus = {
      ...checkinStatus,
      checked_in_today: true,
      total_quota: newQuota,
      total_checkins: (checkinStatus.total_checkins ?? 0) + 1,
      checkin_count: (checkinStatus.checkin_count ?? 0) + 1,
    };
    const result: CheckinResult = { quota_awarded: awarded, quota: newQuota };
    return json({ success: true, message: '签到成功', data: result });
  },

  // GET /api/task/self（F-2003）→ 分页任务列表（TaskUserView[]）
  'GET /api/task/self': (init) => {
    const url = (init as RequestInit & { __url?: string })?.__url ?? '';
    const qs = new URLSearchParams(url.split('?')[1] ?? '');
    const status = qs.get('status') ?? '';
    const list = status
      ? TASKS.filter((t) => t.status === status)
      : TASKS;
    return json({
      success: true,
      data: { items: list, total: list.length, page: 1, page_size: 20 },
    });
  },

  // GET /api/user/self/models（F-3025）→ 可见模型名 A 数组
  'GET /api/user/self/models': () =>
    json({ success: true, data: CANDIDATE_MODELS }),

  // GET /api/user/self/model_aliases/candidates（F-6003）→ 候选 A 数组（不含 B）
  'GET /api/user/self/model_aliases/candidates': (init) => {
    const url = (init as RequestInit & { __url?: string })?.__url ?? '';
    const kw = (new URLSearchParams(url.split('?')[1] ?? '').get('keyword') ?? '').toLowerCase();
    const list = kw
      ? CANDIDATE_MODELS.filter((m) => m.toLowerCase().includes(kw))
      : CANDIDATE_MODELS;
    return json({ success: true, data: list });
  },

  // GET /api/user/self/model_aliases → 用户自助映射列表（contract 仅登记 candidates；
  // CRUD 主资源路径按 DTO + new-api REST 惯例推定，待 S7 补登）
  'GET /api/user/self/model_aliases': () =>
    json({ success: true, data: aliases }),

  // POST /api/user/self/model_aliases → 新建映射（UserModelAliasCreateRequest）
  'POST /api/user/self/model_aliases': (_init, body) => {
    const b = (body ?? {}) as {
      alias?: string;
      target?: string;
      scope_type?: 'user' | 'group';
      enabled?: boolean;
    };
    if (!b.alias || !b.target) {
      return json({ success: false, message: 'alias 与 target 必填' }, 400);
    }
    const created: UserModelAliasUserView = {
      id: nextAliasId++,
      scope_type: b.scope_type ?? 'user',
      scope_id: b.scope_type === 'group' ? 'vip' : '10086',
      alias: b.alias,
      target: b.target,
      enabled: b.enabled ?? true,
      created_time: Math.floor(Date.now() / 1000),
    };
    aliases = [created, ...aliases];
    return json({ success: true, message: '映射已创建', data: created });
  },

  /* ── token 令牌域（API 密钥） ───────────────────────────────── */
  // GET /api/token/ → 令牌列表（分页，key 脱敏）
  'GET /api/token/': () => {
    const items: TokenUserView[] = [
      { id: 1, name: '生产环境', key: 'sk-nexa-a91f****', status: 1, remain_quota: 4_000_000, used_quota: 1_000_000, unlimited_quota: false, expired_time: -1, group: 'vip', model_limits_enabled: false, model_limits: '', allow_ips: '', cross_group_retry: false, accessed_time: Math.floor(Date.now() / 1000) - 300, created_time: 1_735_000_000 },
      { id: 2, name: '测试环境', key: 'sk-nexa-7c20****', status: 1, remain_quota: 500_000, used_quota: 500_000, unlimited_quota: false, expired_time: -1, group: 'free', model_limits_enabled: false, model_limits: '', allow_ips: '', cross_group_retry: false, accessed_time: Math.floor(Date.now() / 1000) - 3600, created_time: 1_735_000_000 },
    ];
    return json({ success: true, data: { items, total: items.length, page: 1, page_size: 20 } });
  },
  // POST /api/token/ → 创建令牌
  'POST /api/token/': (_init, body) => {
    const b = (body ?? {}) as { name?: string; remain_quota?: number; unlimited_quota?: boolean; expired_time?: number };
    const created: TokenUserView = {
      id: 99,
      name: b.name ?? '新密钥',
      key: 'sk-nexa-xxxx****',
      status: 1,
      remain_quota: b.unlimited_quota ? 0 : (b.remain_quota ?? 500_000),
      unlimited_quota: b.unlimited_quota ?? false,
      used_quota: 0,
      expired_time: b.expired_time ?? -1,
      group: 'vip',
      model_limits_enabled: false,
      model_limits: '',
      allow_ips: '',
      cross_group_retry: false,
      accessed_time: 0,
      created_time: Math.floor(Date.now() / 1000),
    };
    return json({ success: true, message: '密钥已创建', data: created });
  },

  /* ── log 日志域（调用明细 + 统计） ─────────────────────────── */
  // GET /api/log/self → 本人调用明细（分页；契约已剔除 cost/profit/B/供应商）
  'GET /api/log/self': () => {
    const items: UserLogView[] = [
      { id: 1, created_at: Math.floor(Date.now() / 1000) - 300, type: 2, content: '', token_name: '生产环境', model_name: 'opus-4.8', requested_model: 'opus-4.8', resolved_public_model: 'opus-4.8', group: 'vip', prompt_tokens: 1820, completion_tokens: 642, quota: 23_100, use_time: 1340, is_stream: false, ip: '10.4.21.8', user_agent: 'OpenAI-Python/1.54.3', request_id: 'req_8f2a91c4', inbound_protocol: 'openai', upstream_protocol: 'openai', protocol_converted: false },
      { id: 2, created_at: Math.floor(Date.now() / 1000) - 600, type: 2, content: '', token_name: '生产环境', model_name: 'gpt-4o', requested_model: 'gpt-4o', resolved_public_model: 'gpt-4o', group: 'vip', prompt_tokens: 3120, completion_tokens: 880, quota: 18_800, use_time: 1620, is_stream: false, ip: '10.4.21.8', user_agent: 'OpenAI-Python/1.54.3', request_id: 'req_9e2d77f0', inbound_protocol: 'openai', upstream_protocol: 'openai', protocol_converted: false },
    ];
    return json({ success: true, data: { items, total: items.length } });
  },
  // GET /api/log/self/stat → 本人消费聚合统计
  'GET /api/log/self/stat': () => {
    const stat: LogStat = { quota: 5_200_000, rpm: 2.3, tpm: 12_400 };
    return json({ success: true, data: stat });
  },

  /* ── billing 计费域（充值） ────────────────────────────────── */
  // POST /api/topup → 充值下单
  'POST /api/topup': () => {
    return json({ success: true, message: '充值申请已提交', data: { trade_no: 'topup_mock_001', pay_url: 'https://pay.example.com' } });
  },
};

/* ── 正则路由表（带路径参数） ─────────────────────────────────────────── */

export const CONSOLE_REGEX_ROUTES: { method: string; pattern: RegExp; handler: RegexHandler }[] = [
  // PUT /api/user/self/model_aliases/{id} → 更新映射
  {
    method: 'PUT',
    pattern: /^\/api\/user\/self\/model_aliases\/(\d+)$/,
    handler: (_init, body, params) => {
      const id = Number(params[0]);
      const b = (body ?? {}) as { target?: string; enabled?: boolean };
      const idx = aliases.findIndex((a) => a.id === id);
      if (idx < 0) return json({ success: false, message: '映射不存在' }, 404);
      aliases[idx] = {
        ...aliases[idx],
        ...(b.target !== undefined ? { target: b.target } : {}),
        ...(b.enabled !== undefined ? { enabled: b.enabled } : {}),
      };
      return json({ success: true, message: '映射已更新', data: aliases[idx] });
    },
  },
  // DELETE /api/user/self/model_aliases/{id} → 删除映射
  {
    method: 'DELETE',
    pattern: /^\/api\/user\/self\/model_aliases\/(\d+)$/,
    handler: (_init, _body, params) => {
      const id = Number(params[0]);
      aliases = aliases.filter((a) => a.id !== id);
      return json({ success: true, message: '映射已删除', data: null });
    },
  },
];

export { parseBody };
