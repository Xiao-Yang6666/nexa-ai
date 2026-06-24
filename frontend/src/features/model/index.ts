/**
 * features/model — 模型广场域：公开模型目录展示 + 价格（零泄露 PublicView）
 * + 用户自助模型映射 C→A（target 仅 A，绝不含 B）。
 */
export { ModelsPage } from './components/ModelsPage';
export { ModelMapPage } from './components/ModelMapPage';
export { useModelCatalog, toModelCardVMs, modelDisplayMeta } from './model/model.model';
export type { ModelCardVM, QualityTier, ModelDisplayMeta } from './model/model.model';
export { vendorIcon, VENDOR_ICONS } from './model/vendors';
export type { VendorIcon } from './model/vendors';
export {
  useModelAliases,
  useAliasCandidates,
  useCreateAlias,
  useUpdateAlias,
  useDeleteAlias,
  toAliasVM,
} from './model/model-alias.model';
export type { AliasVM, AliasScope } from './model/model-alias.model';
export { ModelsAdminPage } from './components/ModelsAdminPage';
