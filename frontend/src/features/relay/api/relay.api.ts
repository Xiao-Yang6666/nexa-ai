/**
 * features/relay/api — relay 域接口调用（异步任务自助）。
 * 路径/方法/出参逐字对齐 openapi.yaml，不臆造字段。
 */
import { http } from '@/shared/api';
import type { TaskUserView } from '@/shared/api';

/** 任务列表分页响应（openapi /api/task/self 的 data 结构）。 */
export interface TaskListResponse {
  items: TaskUserView[];
  total: number;
  page: number;
  page_size: number;
}

/** 任务列表查询过滤参数（对齐 openapi query）。 */
export interface TaskListQuery {
  status?: string;
  action?: string;
  platform?: string;
  page?: number;
  page_size?: number;
}

/**
 * 用户任务列表（self-scope，强制 user_id 隔离）。
 * openapi: GET /api/task/self (F-2003) → ApiResponse{ data: { items: TaskUserView[], total, page, page_size } }
 */
export function getSelfTasks(query?: TaskListQuery): Promise<TaskListResponse> {
  return http.get<TaskListResponse>('/api/task/self', {
    query: query as Record<string, string | number | undefined>,
  });
}
