/**
 * features/modelgroup — 灵活模型组域 public API。
 * 模型组独立售卖单元：可用模型集 + 模型组级倍率 + 访问策略（公开/私有/按等级自动）。
 * 跨域引用只走本入口，不深 import 域内文件。
 */
export { ModelGroupsPage } from './components/ModelGroupsPage';
export {
  useModelGroups,
  useUserModelGroups,
  useSetUserModelGroups,
  POLICY_LABEL,
  MG_STATUS,
  toModelGroupRowVM,
} from './model/modelgroup.model';
export type { ModelGroupRowVM } from './model/modelgroup.model';
export type { AccessPolicy, ModelGroupView } from './api/modelgroup.api';
