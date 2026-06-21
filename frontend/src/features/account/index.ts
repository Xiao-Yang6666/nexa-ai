/**
 * features/account — 账号域 public API（登录/注册/本人信息/设置）。
 * 跨域引用只走本入口，不深 import 域内文件。
 */
export { LoginForm } from './components/LoginForm';
export type { LoginFormProps } from './components/LoginForm';
export { RegisterForm } from './components/RegisterForm';
export type { RegisterFormProps } from './components/RegisterForm';
export { SettingsPage } from './components/SettingsPage';
export { UsersAdminPage } from './components/UsersAdminPage';
export { AuthSplitLayout, Accent } from './components/AuthSplitLayout';
export type { AuthSplitLayoutProps } from './components/AuthSplitLayout';
export {
  useLogin,
  useRegister,
  useSelf,
  useSaveSetting,
  toAccountVM,
} from './model/account.model';
export type { AccountVM } from './model/account.model';
