/**
 * features/provider-account/api — 供应商账号管理域接口调用（管理端，AdminAuth）。
 * 路径/方法/出参对齐后端 AccountController（/api/admin/accounts），不臆造字段。
 *
 * 出参 AccountView 为管理端视图：绝不下发 credentials 原始凭证（后端已脱敏即「不下发」）。
 * 注意：供应商账号域与用户「account」域同名但无关，前端独立命名 provider-account 避免混淆。
 */
import { http } from '@/shared/api';

/** 账号-分组关联视图（group 字符串 + 组内 priority，对齐 channel/abilities 的 group）。 */
export interface AccountGroupView {
  group: string;
  priority?: number | null;
}

/**
 * 供应商账号管理视图（AccountView，credentials 绝不回显）。
 * 字段 snake_case 对齐后端 record。
 */
export interface AccountView {
  id: number;
  name: string;
  platform: string;
  type: string;
  /** 上游 API base url（可空） */
  base_url?: string | null;
  concurrency: number;
  priority: number;
  /** 路由权重 */
  weight: number;
  /** 标签（批量操作用） */
  tag?: string | null;
  /** 自动封禁 */
  auto_ban: boolean;
  /** 模型映射 JSON（A→B） */
  model_mapping?: string | null;
  /** 支持的模型（逗号分隔） */
  models?: string | null;
  /** 状态码：active / disabled / rate_limited */
  status: string;
  rate_limited_at?: number | null;
  rate_limit_reset_at?: number | null;
  overload_until?: number | null;
  expires_at?: number | null;
  auto_pause_on_expired: boolean;
  /** 账号级售价倍率（>=0，默认 1.0） */
  rate_multiplier?: number | null;
  groups?: AccountGroupView[];
  created_time?: number | null;
  updated_time?: number | null;
}

/** 创建账号请求（name/platform/type 必填，credentials 敏感）。 */
export interface AccountCreateRequest {
  name: string;
  platform: string;
  type: string;
  credentials?: string;
  base_url?: string;
  concurrency?: number;
  priority?: number;
  weight?: number;
  tag?: string;
  auto_ban?: boolean;
  model_mapping?: string;
  models?: string;
  expires_at?: number;
  auto_pause_on_expired?: boolean;
  rate_multiplier?: number;
  groups?: AccountGroupView[];
}

/** 编辑账号请求（覆盖式，credentials 空白=保留原值）。 */
export type AccountUpdateRequest = AccountCreateRequest;

/** /api/admin/accounts 列表响应（后端 AccountListView：items + total）。 */
export interface AccountListResponse {
  items: AccountView[];
  total: number;
}

/**
 * 账号列表分页。
 * GET /api/admin/accounts (adminAuth) → ApiResponse{ data: { items, total } }
 * 分页参数 p（页码）+ page_size，支持 platform 过滤。
 */
export function getAccounts(params: {
  page?: number;
  pageSize?: number;
  platform?: string;
} = {}): Promise<AccountListResponse> {
  return http.get<AccountListResponse>('/api/admin/accounts', {
    query: {
      p: params.page,
      page_size: params.pageSize,
      platform: params.platform,
    },
  });
}

/**
 * 账号详情。
 * GET /api/admin/accounts/{id} → ApiResponse{ data: AccountView }
 */
export function getAccount(id: number): Promise<AccountView> {
  return http.get<AccountView>(`/api/admin/accounts/${id}`);
}

/**
 * 创建账号。
 * POST /api/admin/accounts → ApiResponse{ data: AccountView }
 */
export function createAccount(req: AccountCreateRequest): Promise<AccountView> {
  return http.post<AccountView>('/api/admin/accounts', { json: req });
}

/**
 * 编辑账号（覆盖式）。
 * PUT /api/admin/accounts/{id} → ApiResponse{ data: AccountView }
 */
export function updateAccount(id: number, req: AccountUpdateRequest): Promise<AccountView> {
  return http.put<AccountView>(`/api/admin/accounts/${id}`, { json: req });
}

/**
 * 删除账号。
 * DELETE /api/admin/accounts/{id} → ApiResponse
 */
export function deleteAccount(id: number): Promise<void> {
  return http.delete<void>(`/api/admin/accounts/${id}`);
}

/**
 * 启停账号。
 * PATCH /api/admin/accounts/{id}/toggle?enable={bool} → ApiResponse{ data: AccountView }
 */
export function toggleAccount(id: number, enable: boolean): Promise<AccountView> {
  return http.patch<AccountView>(`/api/admin/accounts/${id}/toggle`, {
    query: { enable },
  });
}

/** 探测上游模型列表请求（用表单当前连接信息直接探测，不落库）。 */
export interface ProbeModelsRequest {
  platform: string;
  base_url?: string;
  api_key: string;
}

/**
 * 探测上游模型列表。
 * POST /api/admin/accounts/probe-models → ApiResponse{ data: string[] }
 * 用表单填写的 platform/base_url/api_key 调上游 /models 拉候选模型，无需先保存账号。
 */
export function probeModels(req: ProbeModelsRequest): Promise<string[]> {
  return http.post<string[]>('/api/admin/accounts/probe-models', { json: req });
}

/** 模型连通性测试请求（对已保存账号的指定模型发一次 chat 调用；apiKey 服务端从凭证取，不传）。 */
export interface TestModelRequest {
  model: string;
  prompt?: string;
}

/** 模型连通性测试结果（成功视图；失败走 ApiError）。 */
export interface TestModelView {
  ok: boolean;
  latency_ms: number;
  reply?: string | null;
}

/**
 * 模型连通性测试。
 * POST /api/admin/accounts/{id}/test-model → ApiResponse{ data: { ok, latency_ms, reply } }
 * 对账号已存凭证 + 指定模型发一次非流式 chat 补全，验证账号是否能跑通该模型。
 */
export function testModel(id: number, req: TestModelRequest): Promise<TestModelView> {
  return http.post<TestModelView>(`/api/admin/accounts/${id}/test-model`, { json: req });
}

/** 流式测试回调（逐 token + 收束 + 错误）。 */
export interface TestModelStreamHandlers {
  /** 收到一片增量文本 */
  onDelta: (text: string) => void;
  /** 流正常收束（总耗时 ms） */
  onDone: (latencyMs: number) => void;
  /** 出错（首字节前的失败：错误信息已脱敏） */
  onError: (message: string) => void;
}

/**
 * 模型连通性测试（流式）。
 * POST /api/admin/accounts/{id}/test-model/stream → text/event-stream
 *
 * <p>逐 SSE 事件解析：event:delta → onDelta(text)；event:done → onDone(latency_ms)。
 * 首字节前失败（账号缺失 / 凭证缺失 / 上游错误）走标准 ApiResponse 错误信封（非 2xx），解出
 * message 交给 onError。手搓 fetch + ReadableStream（http 客户端会整段缓冲，不能逐字读）。</p>
 *
 * @param signal 可选 AbortSignal（关闭弹窗时中止）
 */
export async function testModelStream(
  id: number,
  req: TestModelRequest,
  handlers: TestModelStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const base = process.env.NEXT_PUBLIC_API_BASE ?? '';
  let res: Response;
  try {
    res = await fetch(`${base}/api/admin/accounts/${id}/test-model/stream`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(req),
      signal,
    });
  } catch (e) {
    handlers.onError(e instanceof Error ? e.message : '网络请求失败，请检查网络连接');
    return;
  }

  // 首字节前失败：后端尚未提交响应，走 ApiResponse 错误信封（JSON），解出 message。
  if (!res.ok || !res.body) {
    let msg = `请求失败（${res.status}）`;
    try {
      const payload = await res.json();
      if (payload && typeof payload === 'object' && 'message' in payload && payload.message) {
        msg = String(payload.message);
      }
    } catch {
      /* 非 JSON 错误体，用默认 msg */
    }
    handlers.onError(msg);
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      // 按 SSE 事件分隔（空行 \n\n）切块，逐块解析。
      let sep: number;
      while ((sep = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, sep);
        buffer = buffer.slice(sep + 2);
        dispatchSseBlock(block, handlers);
      }
    }
  } catch (e) {
    // 流中途断开：首片可能已显示，按错误提示但不清空已收文本（由调用方决定）。
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      handlers.onError(e instanceof Error ? e.message : '流式读取中断');
    }
  }
}

/** 解析单个 SSE 事件块（event: + data: 行），分派到对应回调。 */
function dispatchSseBlock(block: string, handlers: TestModelStreamHandlers): void {
  let event = 'message';
  let data = '';
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data += line.slice(5).trim();
  }
  if (!data) return;
  try {
    const obj = JSON.parse(data);
    if (event === 'delta' && typeof obj.text === 'string') handlers.onDelta(obj.text);
    else if (event === 'done') handlers.onDone(Number(obj.latency_ms) || 0);
  } catch {
    /* 非 JSON data 行，忽略 */
  }
}
