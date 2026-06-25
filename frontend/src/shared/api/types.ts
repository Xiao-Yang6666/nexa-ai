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

/** 用量排行榜公开快照条目（仅对外名 A + 聚合用量；绝不含成本/利润/B/供应商） */
export type RankingPublicView = Schemas['RankingPublicView'];

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

/** 兑换码管理视图（openapi RedemptionAdminView，F-2045 GET /api/redemption/）。含明文 key/核销人/核销时间。 */
export type RedemptionAdminView = Schemas['RedemptionAdminView'];

/** 生成兑换码请求（openapi RedemptionCreateRequest，F-2045 POST /api/redemption/）。 */
export type RedemptionCreateRequest = Schemas['RedemptionCreateRequest'];

/** 渠道管理视图（openapi ChannelAdminView，F-2016 GET /api/channel/）。key 已脱敏；balance 为渠道余额（管理端运维数据）。 */
export type ChannelAdminView = Schemas['ChannelAdminView'];

/** 创建渠道请求（openapi ChannelCreateRequest，F-2016 POST /api/channel/）。 */
export type ChannelCreateRequest = Schemas['ChannelCreateRequest'];

/** 编辑渠道请求（openapi ChannelUpdateRequest，F-2016 PUT /api/channel/）。 */
export type ChannelUpdateRequest = Schemas['ChannelUpdateRequest'];

/* ============ 模型/供应商管理端 DTO（admin-scope，F-3013~/F-6001~） ============ */

/** 对外模型管理视图（openapi PublicModelAdminView，F-6001 GET /api/public_models）。 */
export type PublicModelAdminView = Schemas['PublicModelAdminView'];
/** 创建对外模型请求（openapi PublicModelCreateRequest）。 */
export type PublicModelCreateRequest = Schemas['PublicModelCreateRequest'];
/** 更新对外模型请求（openapi PublicModelUpdateRequest，A 不可改）。 */
export type PublicModelUpdateRequest = Schemas['PublicModelUpdateRequest'];

/** 模型元数据管理视图（openapi ModelMetaAdminView，F-3013 GET /api/models）。 */
export type ModelMetaAdminView = Schemas['ModelMetaAdminView'];
/** 创建模型元数据请求（openapi ModelMetaCreateRequest）。 */
export type ModelMetaCreateRequest = Schemas['ModelMetaCreateRequest'];
/** 更新模型元数据请求（openapi ModelMetaUpdateRequest，支持 status_only）。 */
export type ModelMetaUpdateRequest = Schemas['ModelMetaUpdateRequest'];
/** 上游模型同步差异（openapi ModelSyncDiff，F-3020 sync/preview）。 */
export type ModelSyncDiff = Schemas['ModelSyncDiff'];
/** 上游模型同步结果（openapi ModelSyncResult，F-3019 sync）。 */
export type ModelSyncResult = Schemas['ModelSyncResult'];

/** 供应商管理视图（openapi VendorAdminView，F-3018 GET /api/vendors）。 */
export type VendorAdminView = Schemas['VendorAdminView'];
/** 供应商写入请求（openapi VendorWriteRequest，创建/更新共用）。 */
export type VendorWriteRequest = Schemas['VendorWriteRequest'];

/** 渠道成本管理视图（openapi ChannelModelCostAdminView，F-6006 GET /api/channel_model_costs）。 */
export type ChannelModelCostAdminView = Schemas['ChannelModelCostAdminView'];
/** 渠道成本写入请求（openapi ChannelModelCostWriteRequest，upsert）。 */
export type ChannelModelCostWriteRequest = Schemas['ChannelModelCostWriteRequest'];

/** 渠道池成员（openapi ChannelPoolMember，F-6005 GET /api/channel/pool）。 */
export type ChannelPoolMember = Schemas['ChannelPoolMember'];

/** 利润看板聚合项（openapi ProfitDashboardItem，F-6009 GET /api/profit/dashboard）。含成本/利润，仅 admin。 */
export type ProfitDashboardItem = Schemas['ProfitDashboardItem'];
