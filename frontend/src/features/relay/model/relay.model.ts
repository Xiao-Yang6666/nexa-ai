/**
 * features/relay/model — relay 域视图模型：异步任务。
 *
 * 把契约 TaskUserView（status enum：NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN）
 * 映射为 UI 四态（queued/running/done/failed）+ 进度 + 时间格式化。
 * 客户端零泄露：TaskUserView 已 Omit channel_id / 无 PrivateData，仅展示自助字段。
 */
import { useQuery } from '@tanstack/react-query';
import type { TaskUserView } from '@/shared/api';
import { getSelfTasks, type TaskListQuery } from '../api/relay.api';

/** UI 任务状态（合并契约 7 态为 4 态）。 */
export type TaskUiStatus = 'queued' | 'running' | 'done' | 'failed';

/** 单个任务视图模型。 */
export interface TaskVM {
  id: number;
  taskId: string;
  /** 类型展示名（action） */
  typeName: string;
  /** 提交时间文案（YYYY-MM-DD HH:mm） */
  submitTime: string;
  /** UI 状态 */
  status: TaskUiStatus;
  /** 进度百分比 0-100 */
  progress: number;
  /** 失败原因（status=failed 时有意义） */
  failReason: string;
}

/** 契约 status enum → UI 四态。 */
function mapStatus(s: string | undefined): TaskUiStatus {
  switch (s) {
    case 'IN_PROGRESS':
      return 'running';
    case 'SUCCESS':
      return 'done';
    case 'FAILURE':
    case 'UNKNOWN':
      return 'failed';
    case 'NOT_START':
    case 'SUBMITTED':
    case 'QUEUED':
    default:
      return 'queued';
  }
}

/** UI 状态 → 契约 status enum（用于按状态筛选查询参数）。 */
export function uiStatusToContract(ui: TaskUiStatus): string {
  switch (ui) {
    case 'running':
      return 'IN_PROGRESS';
    case 'done':
      return 'SUCCESS';
    case 'failed':
      return 'FAILURE';
    case 'queued':
    default:
      return 'QUEUED';
  }
}

/** Unix 秒 → 'YYYY-MM-DD HH:mm'。 */
function fmtTime(ts: number | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** DTO → 任务视图模型。 */
export function toTaskVM(t: TaskUserView): TaskVM {
  const status = mapStatus(t.status);
  const progress = status === 'done' ? 100 : Math.max(0, Math.min(100, Number(t.progress ?? '0') || 0));
  return {
    id: t.id ?? 0,
    taskId: t.task_id ?? '',
    typeName: t.action ?? t.platform ?? '任务',
    submitTime: fmtTime(t.submit_time),
    status,
    progress,
    failReason: t.fail_reason ?? '',
  };
}

/**
 * 任务列表查询 hook。
 * 服务端按 status 过滤（传契约 enum）；前端再映射成 VM。
 */
export function useSelfTasks(uiStatus?: TaskUiStatus) {
  const query: TaskListQuery = uiStatus ? { status: uiStatusToContract(uiStatus) } : {};
  return useQuery({
    queryKey: ['tasks', 'self', uiStatus ?? 'all'],
    queryFn: () => getSelfTasks(query),
    select: (res) => ({
      items: (res.items ?? []).map(toTaskVM),
      total: res.total ?? 0,
    }),
  });
}
