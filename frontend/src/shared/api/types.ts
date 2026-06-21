/**
 * shared/api/types — 从 openapi 生成的 schema.ts 提取的稳定 DTO 别名。
 *
 * schema.ts 由 `npm run gen:api` 从 07_dev_contract/final/openapi.yaml 生成，
 * 不手维护接口类型（契约变了重新生成即可）。本文件只做命名收敛，便于上层引用。
 */
import type { components } from './schema';

type Schemas = components['schemas'];

/** 通用响应包络 */
export type ApiResponse = Schemas['ApiResponse'];
export type SuccessResponse = Schemas['SuccessResponse'];
export type ErrorResponse = Schemas['ErrorResponse'];

/**
 * 用户客户视图（self-scope / 登录返回）。
 * 契约已裁掉 password/access_token；客户端零泄露：不含 cost/profit/上游模型 B/供应商。
 */
export type UserView = Schemas['UserView'];

/** 登录请求体（openapi /api/user/login requestBody） */
export interface LoginPayload {
  username: string;
  password: string;
}

/** 注册请求体（openapi /api/user/register requestBody） */
export interface RegisterPayload {
  username: string;
  password: string;
  email?: string;
  verification_code?: string;
  aff_code?: string;
}

/* ============ 控制台 self-scope DTO（均为客户视图，零泄露：无 cost/profit/上游B/供应商） ============ */

/** 个人设置（dto.UserSetting）；语言/边栏偏好 + 额度预警配置（openapi F-4037） */
export type UserSetting = Schemas['UserSetting'];

/** 签到状态与本月记录（openapi GET /api/user/checkin，F-1047；记录已脱敏不含 id/user_id） */
export type CheckinStatusView = Schemas['CheckinStatusView'];

/** 签到领取结果（openapi POST /api/user/checkin，F-1046） */
export type CheckinResult = Schemas['CheckinResult'];

/** 异步任务用户视图（openapi /api/task/self，F-2003；Omit channel_id，无 PrivateData） */
export type TaskUserView = Schemas['TaskUserView'];

/** 用户自助模型映射 C→A 客户视图（openapi UserModelAliasUserView；target 仅 A，绝不含 B） */
export type UserModelAliasUserView = Schemas['UserModelAliasUserView'];

/** 新建用户映射请求（openapi UserModelAliasCreateRequest） */
export type UserModelAliasCreateRequest = Schemas['UserModelAliasCreateRequest'];

/** 更新用户映射请求（openapi UserModelAliasUpdateRequest） */
export type UserModelAliasUpdateRequest = Schemas['UserModelAliasUpdateRequest'];

/** 通用分页元数据（openapi Pagination：total/page/page_size） */
export type Pagination = Schemas['Pagination'];

/** 令牌客户视图（self-scope，key 已脱敏；无 cost/profit/上游 B/供应商） */
export type TokenUserView = Schemas['TokenUserView'];

/** 令牌创建/更新入参（openapi TokenCreateRequest） */
export type TokenCreateRequest = Schemas['TokenCreateRequest'];

/** 用户侧调用日志视图（已结构级剔除 quota_cost/quota_profit/B/供应商等字段） */
export type UserLogView = Schemas['UserLogView'];

/** 日志聚合统计（仅 Type=2 Consume；quota/rpm/tpm） */
export type LogStat = Schemas['LogStat'];

/** 充值下单请求（openapi TopUpRequest） */
export type TopUpRequest = Schemas['TopUpRequest'];

/* ============ 管理端 DTO（admin/root-scope，含全字段；仅管理后台使用） ============ */

/** 用户管理视图（openapi UserAdminView，F-1008 GET /api/user/）。含 remark/inviter_id。 */
export type UserAdminView = Schemas['UserAdminView'];

/**
 * 管理侧日志视图（openapi AdminLogView，F-4001 GET /api/log/）。
 * 全局 + 全字段：含 B(actual_upstream_model)/channel/quota_sell/quota_cost/quota_profit。
 * 仅管理后台（adminAuth）使用——客户端零泄露铁律只约束 self-scope 客户视图。
 */
export type AdminLogView = Schemas['AdminLogView'];

/** 营销首页公开状态聚合（openapi StatusAggregateView，F-4039 GET /api/status）。 */
export type StatusAggregateView = Schemas['StatusAggregateView'];

/** 按日配额聚合数据项（openapi QuotaDataItem，F-4007 GET /api/data/）。 */
export type QuotaDataItem = Schemas['QuotaDataItem'];
