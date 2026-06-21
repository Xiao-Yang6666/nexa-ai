/**
 * features/docs — 文档站域（api-docs 端）。
 * 对外暴露：外壳组件 DocsShell、CodeBlock、原子组件、导航数据。
 */
export { DocsShell } from './components/DocsShell';
export { CodeBlock } from './components/CodeBlock';
export type { CodePane } from './components/CodeBlock';
export {
  Breadcrumb,
  Callout,
  DocTable,
  Endpoint,
  H2,
  H3,
  Lede,
  PageTitle,
  ParamTable,
  Section,
  Step,
  Steps,
  InlineLink,
} from './components/primitives';
export type { CalloutTone, ParamRow } from './components/primitives';
export { NAV_GROUPS } from './model/nav';
export type { NavGroup, NavLink, TocItem } from './model/nav';
