import type { HTMLAttributes, ReactNode } from 'react';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  /** 玻璃质感（深色门面），对齐 tokens.css 的 .glass；默认 false 用 .card */
  glass?: boolean;
}

/**
 * 卡片 / 表面容器。token 化样式来自 tokens.css 的 .card / .glass。
 *
 * @example
 * <Card>内容</Card>
 * <Card glass>深色门面玻璃卡</Card>
 */
export function Card({ children, glass, className, ...rest }: CardProps) {
  return (
    <div
      className={[glass ? 'glass' : 'card', className].filter(Boolean).join(' ')}
      {...rest}
    >
      {children}
    </div>
  );
}
