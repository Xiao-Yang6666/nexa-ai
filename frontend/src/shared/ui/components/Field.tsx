import type { LabelHTMLAttributes, ReactNode } from 'react';

export interface FieldProps {
  /** 字段标签文案 */
  label: ReactNode;
  /** 关联控件 id（label htmlFor） */
  htmlFor?: string;
  /** 是否必填（显示红 *） */
  required?: boolean;
  /** 辅助提示文案 */
  hint?: ReactNode;
  /** 错误文案（存在则替代 hint 显示） */
  error?: ReactNode;
  children: ReactNode;
  labelProps?: LabelHTMLAttributes<HTMLLabelElement>;
}

/**
 * 表单字段容器：label + 控件 + hint/error。
 * token 化样式来自 tokens.css 的 .field-label / .field-hint / .field-err。
 *
 * @example
 * <Field label="邮箱" htmlFor="email" required error={err}>
 *   <Input id="email" />
 * </Field>
 */
export function Field({
  label,
  htmlFor,
  required,
  hint,
  error,
  children,
  labelProps,
}: FieldProps) {
  return (
    <div style={{ marginBottom: 'var(--space-4)' }}>
      <label className="field-label" htmlFor={htmlFor} {...labelProps}>
        {label}
        {required ? <span className="field-req"> *</span> : null}
      </label>
      {children}
      {error ? (
        <div className="field-err" role="alert">
          {error}
        </div>
      ) : hint ? (
        <div className="field-hint">{hint}</div>
      ) : null}
    </div>
  );
}
