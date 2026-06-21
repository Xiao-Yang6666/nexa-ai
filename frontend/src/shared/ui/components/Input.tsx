import { forwardRef } from 'react';
import type { InputHTMLAttributes } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  /** 错误态（红边 + 红底），对齐 tokens.css 的 .input.err */
  invalid?: boolean;
}

/**
 * 基础输入框。token 化样式来自 tokens.css 的 .input。
 * 用于浅色应用区表单（console/admin）。深色门面登录注册用 features/account 的 AuthField。
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ invalid, className, type, ...rest }, ref) => (
    <input
      ref={ref}
      type={type ?? 'text'}
      className={['input', invalid ? 'err' : '', className]
        .filter(Boolean)
        .join(' ')}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  ),
);
Input.displayName = 'Input';
