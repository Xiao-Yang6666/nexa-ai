import type { HTMLAttributes, ReactNode } from 'react';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const TONE_CLASS: Record<BadgeTone, string> = {
  success: 'b-suc',
  warning: 'b-warn',
  danger: 'b-dan',
  info: 'b-info',
  neutral: 'b-neutral',
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  /** 语义色调，默认 neutral，对齐 tokens.css 的 .b-* */
  tone?: BadgeTone;
  /** 是否显示前置状态圆点 */
  dot?: boolean;
  children: ReactNode;
}

/**
 * 徽章 / 状态标签。token 化样式来自 tokens.css 的 .badge / .b-*。
 *
 * @example
 * <Badge tone="success" dot>在线</Badge>
 */
export function Badge({ tone = 'neutral', dot, children, className, ...rest }: BadgeProps) {
  return (
    <span
      className={['badge', TONE_CLASS[tone], className].filter(Boolean).join(' ')}
      {...rest}
    >
      {dot ? <span className="dot" style={{ background: 'currentColor' }} /> : null}
      {children}
    </span>
  );
}
