/**
 * shared/api — openapi 生成的类型 + client 基建 + mock。
 */
export { http, request, ApiError } from './client';
export type { ApiEnvelope } from './client';
export { installMock } from './mock';
export type {
  ApiResponse,
  SuccessResponse,
  ErrorResponse,
  UserView,
  LoginPayload,
  RegisterPayload,
  UserSetting,
  CheckinStatusView,
  CheckinResult,
  TaskUserView,
  UserModelAliasUserView,
  UserModelAliasCreateRequest,
  UserModelAliasUpdateRequest,
  Pagination,
  TokenUserView,
  TokenCreateRequest,
  UserLogView,
  LogStat,
  TopUpRequest,
  UserAdminView,
  AdminLogView,
  StatusAggregateView,
  QuotaDataItem,
} from './types';
export type { PricingPublicView, PricingModelEntry } from './pricing.types';
