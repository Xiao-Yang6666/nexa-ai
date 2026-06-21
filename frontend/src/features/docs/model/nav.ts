/**
 * 文档站导航数据模型（NavTree 三级结构 + 每页 ToC）。
 *
 * 与 05_ui/final/ui-design/api-docs.md §2.2 导航树层级 1:1 对齐。
 * 路由用 Next App Router 的 /docs/* 形态（原型 .html 已映射为路由）。
 * 文档站是纯静态内容端，导航数据集中维护于此，组件只消费不内联。
 */

/** 单个导航链接项。 */
export interface NavLink {
  /** 显示名 */
  label: string;
  /** 路由（/docs 或 /docs/xxx） */
  href: string;
}

/** 导航分组（overline 大写标题 + 链接项）。 */
export interface NavGroup {
  /** 分组标题（overline 展示，大写） */
  overline: string;
  links: NavLink[];
}

/** 页内目录单项（ToC）。 */
export interface TocItem {
  /** 锚点 id（对应正文 heading 的 id） */
  id: string;
  /** 显示文字 */
  label: string;
  /** 层级：2=h2、3=h3（h3 缩进） */
  level: 2 | 3;
}

/**
 * 左栏 NavTree 全量分组。路由对齐 S6 原型站点结构：
 * 快速开始 = /docs（route group 下的 index），其余为子路由。
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    overline: '入门 · Getting Started',
    links: [
      { label: '快速开始', href: '/docs' },
      { label: '认证说明', href: '/docs/authentication' },
    ],
  },
  {
    overline: 'API 参考 · API Reference',
    links: [
      { label: '聊天补全 API', href: '/docs/chat-completions' },
      { label: '模型列表 API', href: '/docs/models' },
      { label: '嵌入 API', href: '/docs/embeddings' },
      { label: '图像 API', href: '/docs/images' },
    ],
  },
  {
    overline: '运行约定 · Operations',
    links: [
      { label: '错误码参考', href: '/docs/errors' },
      { label: '限流说明', href: '/docs/rate-limits' },
    ],
  },
  {
    overline: '资源 · Resources',
    links: [
      { label: 'SDK 下载', href: '/docs/sdks' },
      { label: '更新日志', href: '/docs/changelog' },
    ],
  },
];
