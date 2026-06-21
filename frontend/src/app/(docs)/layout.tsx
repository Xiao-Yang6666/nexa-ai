import type { ReactNode } from 'react';

/**
 * /docs 路由组布局。
 * 文档站为深色场景端，深色底由 DocsShell 在客户端给 body 挂 data-docs='1' 实现，
 * 故本 layout 不引入额外样式，仅作为 route group 的结构包裹。
 * 每个 page.tsx 自行包 DocsShell（因 ToC 每页不同，需在页面层注入）。
 */
export default function DocsLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
