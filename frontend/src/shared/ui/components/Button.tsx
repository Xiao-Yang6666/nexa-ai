import { forwardRef } from 'react';
import type { ButtonHTMLAttributes, AnchorHTMLAttributes } from 'react';

/**
 * Button 变体，1:1 对齐 tokens.css 的 .btn-* 类。
 * - primary  主操作（teal 实色）
 * - sec      次操作（描边）
 * - ghost    幽灵
 * - danger   危险
 * - link     文字链接
 * - glow     深色门面渐变发光（hero/CTA）
 * - glass    深色门面玻璃（导航）
 */
export type ButtonVariant =
  | 'primary'
  | 'sec'
  | 'ghost'
  | 'danger'
  | 'link'
  | 'glow'
  | 'glass';

export type ButtonSize = 'sm' | 'md' | 'lg';

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'btn-primary',
  sec: 'btn-sec',
  ghost: 'btn-ghost',
  danger: 'btn-danger',
  link: 'btn-link',
  glow: 'btn-glow',
  glass: 'btn-glass',
};

const SIZE_CLASS: Record<ButtonSize, string> = {
  sm: 'btn-sm',
  md: '',
  lg: 'btn-lg',
};

function composeClass(
  variant: ButtonVariant,
  size: ButtonSize,
  extra?: string,
): string {
  return ['btn', VARIANT_CLASS[variant], SIZE_CLASS[size], extra]
    .filter(Boolean)
    .join(' ');
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 视觉变体，默认 primary */
  variant?: ButtonVariant;
  /** 尺寸，默认 md（40px 高） */
  size?: ButtonSize;
}

/**
 * 基础按钮。token 化样式来自 tokens.css 的 .btn 系统，禁裸色值。
 *
 * @example
 * <Button variant="glow" size="lg">免费开始</Button>
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', className, type, ...rest }, ref) => (
    <button
      ref={ref}
      type={type ?? 'button'}
      className={composeClass(variant, size, className)}
      {...rest}
    />
  ),
);
Button.displayName = 'Button';

export interface ButtonLinkProps
  extends AnchorHTMLAttributes<HTMLAnchorElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/**
 * 链接形态的按钮（语义是导航 <a>，外观同 Button）。
 * 用于「登录 / 免费开始」这类跳转型 CTA。
 */
export const ButtonLink = forwardRef<HTMLAnchorElement, ButtonLinkProps>(
  ({ variant = 'primary', size = 'md', className, ...rest }, ref) => (
    <a
      ref={ref}
      className={composeClass(variant, size, className)}
      {...rest}
    />
  ),
);
ButtonLink.displayName = 'ButtonLink';
