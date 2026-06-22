/**
 * shared/api — 与后端握手的唯一入口。
 *
 * 封装 fetch：统一 baseURL、JSON、凭证（session cookie）、错误归一化。
 * 后端响应遵循 new-api 惯例的 ApiResponse 包络：{ success, message, data }。
 * 接口类型由 openapi-typescript 从 07_dev_contract/final/openapi.yaml 生成（schema.ts），
 * 不手搓接口类型避免与契约漂移。
 */

/** ApiResponse 包络（对齐 openapi components.schemas.ApiResponse）。 */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
}

/** 业务/网络错误统一类型。 */
export class ApiError extends Error {
  /** HTTP 状态码（网络层失败时为 0） */
  readonly status: number;
  /** 后端 success=false 时的 message */
  readonly business: boolean;

  constructor(message: string, status: number, business: boolean) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.business = business;
  }
}

/**
 * API base。开发期默认走同源（Next rewrites / mock 拦截）；
 * 可用 NEXT_PUBLIC_API_BASE 覆盖指向真实后端。
 */
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? '';

interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** JSON body（自动 stringify + 设 Content-Type） */
  json?: unknown;
  /** query 参数 */
  query?: Record<string, string | number | boolean | undefined>;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${API_BASE}${path}`;
  if (!query) return url;
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined) qs.append(k, String(v));
  }
  const s = qs.toString();
  return s ? `${url}?${s}` : url;
}

/**
 * 核心请求器。解包 ApiResponse，success=false 抛 ApiError，网络/HTTP 错误抛 ApiError。
 * 返回 envelope.data（按调用方泛型 T 约束，类型应来自 schema.ts 生成的契约类型）。
 */
export async function request<T>(
  path: string,
  { json, query, headers, ...init }: RequestOptions = {},
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      credentials: 'include',
      headers: {
        ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: json !== undefined ? JSON.stringify(json) : undefined,
      ...init,
    });
  } catch (e) {
    throw new ApiError(
      e instanceof Error ? e.message : '网络请求失败，请检查网络连接',
      0,
      false,
    );
  }

  let payload: unknown = null;
  const text = await res.text();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!res.ok) {
    const msg =
      isEnvelope(payload) && payload.message
        ? payload.message
        : `请求失败（${res.status}）`;
    throw new ApiError(msg, res.status, false);
  }

  // 多数端点是 ApiResponse 包络；解包并按 success 判定业务结果
  if (isEnvelope(payload)) {
    if (payload.success === false) {
      throw new ApiError(payload.message ?? '操作失败', res.status, true);
    }
    return payload.data as T;
  }

  // 非包络端点（如 relay 透传），直接返回原始 payload
  return payload as T;
}

function isEnvelope(v: unknown): v is ApiEnvelope<unknown> {
  return (
    typeof v === 'object' &&
    v !== null &&
    'success' in (v as Record<string, unknown>)
  );
}

export const http = {
  get: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'GET' }),
  post: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'POST' }),
  put: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'PUT' }),
  patch: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'PATCH' }),
  delete: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'DELETE' }),
};
