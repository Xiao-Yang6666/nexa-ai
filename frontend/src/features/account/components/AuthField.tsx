'use client';

import { forwardRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';

/**
 * AuthField — 深色门面专用输入框（登录/注册）。
 *
 * 1:1 还原原型 .inp / .ctrl / .eye / 密码强度 的视觉与交互，token 化。
 * 与 shared/ui 的 Input（浅色应用区）区分：门面是深色玻璃风、更大尺寸、带显隐眼睛。
 * 类名来自门面页 scoped css（auth.module.css 的 :global 类），故用原型同名 class。
 */
export interface AuthFieldProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string;
  label: string;
  /** 是否密码框（启用显隐眼睛） */
  password?: boolean;
  /** 错误态（红边 + 抖动由父级控制 className） */
  invalid?: boolean;
}

const EyeOn = () => (
  <svg className="on" viewBox="0 0 24 24">
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);
const EyeOff = () => (
  <svg className="off" viewBox="0 0 24 24">
    <path d="M2 12s3.5-7 10-7c2 0 3.7.6 5.2 1.5" />
    <path d="M22 12s-3.5 7-10 7c-2 0-3.7-.6-5.2-1.5" />
    <path d="M9.5 9.5a3 3 0 0 0 4.2 4.2" />
    <path d="m3 3 18 18" />
  </svg>
);

export const AuthField = forwardRef<HTMLInputElement, AuthFieldProps>(
  ({ id, label, password, invalid, type, className, ...rest }, ref) => {
    const [show, setShow] = useState(false);
    const inputType = password ? (show ? 'text' : 'password') : (type ?? 'text');
    return (
      <div className="field">
        <label htmlFor={id}>{label}</label>
        <div className={password ? 'ctrl has-eye' : 'ctrl'}>
          <input
            ref={ref}
            id={id}
            type={inputType}
            className={['inp', invalid ? 'err' : '', className]
              .filter(Boolean)
              .join(' ')}
            aria-invalid={invalid || undefined}
            {...rest}
          />
          {password ? (
            <button
              className={show ? 'eye is-on' : 'eye'}
              type="button"
              aria-pressed={show}
              aria-label={show ? '隐藏密码' : '显示密码'}
              onClick={() => setShow((v) => !v)}
            >
              <EyeOn />
              <EyeOff />
            </button>
          ) : null}
        </div>
      </div>
    );
  },
);
AuthField.displayName = 'AuthField';

/** 密码强度评估，与原型逻辑一致：长度 + 字符种类 → 0~3 档。 */
export function passwordStrength(v: string): 0 | 1 | 2 | 3 {
  let kinds = 0;
  if (/[a-z]/.test(v)) kinds++;
  if (/[A-Z]/.test(v)) kinds++;
  if (/[0-9]/.test(v)) kinds++;
  if (/[^a-zA-Z0-9]/.test(v)) kinds++;
  if (v.length >= 8 && kinds >= 3) return 3;
  if (v.length >= 6 && kinds >= 2) return 2;
  if (v.length > 0) return 1;
  return 0;
}

/** 强度条组件（注册用）。 */
export function PasswordStrength({ score }: { score: 0 | 1 | 2 | 3 }) {
  const label = score === 0 ? '密码强度' : score === 1 ? '弱' : score === 2 ? '中' : '强';
  return (
    <div className={`pw-strength${score ? ` s${score}` : ''}`}>
      <i />
      <i />
      <i />
      <span className="pw-label">{label}</span>
    </div>
  );
}
