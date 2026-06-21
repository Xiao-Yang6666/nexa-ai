'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AuthField, passwordStrength } from './AuthField';
import { OAuthRow } from './OAuthRow';
import { useRegister } from '../model/account.model';

/** 密码强度档位对应中文标签。 */
const STRENGTH_LABEL: Record<0 | 1 | 2 | 3, string> = {
  0: '',
  1: '弱',
  2: '中',
  3: '强',
};

/**
 * RegisterForm — 注册表单（深色门面玻璃风，独立 /register 页用）。
 *
 * 1:1 还原 06_prototype/final/web-public/register.html 的表单结构/交互：
 * 邮箱 + 密码（显隐眼睛 + 实时强度条 meter）+ 确认密码 + 同意条款 + 第三方注册。
 * 标题用全局 .auth-title / .auth-sub；外层卡由 AuthSplitLayout 的 .card 提供。
 *
 * 校验（同原型）：空字段抖动红边；两次密码不一致红边；未勾协议红框。
 * 接 openapi mock（POST /api/user/register，F-1001）：成功后跳 /login（契约不下发 token，需再登录）。
 * 邮箱即用户名：openapi 注册需 username，这里以 email 充当（payload 同时带 email 字段，不臆造其他字段）。
 *
 * @param onSuccess 注册成功回调；省略则默认跳 /login
 * @param loginHref 登录入口链接，默认 /login
 */
export interface RegisterFormProps {
  onSuccess?: () => void;
  loginHref?: string;
}

export function RegisterForm({ onSuccess, loginHref = '/login' }: RegisterFormProps) {
  const router = useRouter();
  const emailRef = useRef<HTMLInputElement>(null);
  const pwdRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLInputElement>(null);
  const agreeRef = useRef<HTMLInputElement>(null);

  const [emailErr, setEmailErr] = useState(false);
  const [pwdErr, setPwdErr] = useState(false);
  const [confirmErr, setConfirmErr] = useState(false);
  const [agreeErr, setAgreeErr] = useState(false);
  const [shakeEmail, setShakeEmail] = useState(false);
  const [shakePwd, setShakePwd] = useState(false);
  const [shakeConfirm, setShakeConfirm] = useState(false);
  const [strength, setStrength] = useState<0 | 1 | 2 | 3>(0);
  const [done, setDone] = useState(false);

  const register = useRegister();

  function shake(setShake: (b: boolean) => void) {
    setShake(true);
    setTimeout(() => setShake(false), 420);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const email = emailRef.current?.value.trim() ?? '';
    const pwd = pwdRef.current?.value.trim() ?? '';
    const confirm = confirmRef.current?.value.trim() ?? '';
    const agreed = agreeRef.current?.checked ?? false;

    let bad = false;
    setEmailErr(false);
    setPwdErr(false);
    setConfirmErr(false);
    setAgreeErr(false);

    if (!email) {
      setEmailErr(true);
      shake(setShakeEmail);
      bad = true;
    }
    if (!pwd) {
      setPwdErr(true);
      shake(setShakePwd);
      bad = true;
    }
    // 确认密码：空 → 抖动；非空但与密码不一致 → 红边
    if (!confirm) {
      setConfirmErr(true);
      shake(setShakeConfirm);
      bad = true;
    } else if (pwd && confirm !== pwd) {
      setConfirmErr(true);
      shake(setShakeConfirm);
      bad = true;
    }
    if (!agreed) {
      setAgreeErr(true);
      bad = true;
    }
    if (bad) return;

    // 注册请求体严格对齐 openapi /api/user/register：username(maxLength 20) 与 email(maxLength 50)
    // 是两个独立字段。原型只采集 email，故由 email 本地部派生一个合法 username（≤20，仅取字母数字
    // 与 ._-，首字符兜底字母，全空则用 'user'），避免把整封邮箱塞进 username 触发后端 20 长度上限。
    const local = email.split('@')[0] ?? '';
    let username = local.replace(/[^a-zA-Z0-9._-]/g, '').slice(0, 20);
    if (!username) username = 'user';
    if (!/^[a-zA-Z]/.test(username)) username = `u${username}`.slice(0, 20);

    register.mutate(
      { username, email, password: pwd },
      {
        onSuccess: () => {
          setDone(true);
          if (onSuccess) onSuccess();
          else setTimeout(() => router.push('/login'), 900);
        },
      },
    );
  }

  return (
    <form id="regForm" onSubmit={handleSubmit} noValidate>
      <h1 className="auth-title">创建账号</h1>
      <p className="auth-sub">几秒注册，即刻用一个 base_url 直连全球大模型</p>

      <AuthField
        ref={emailRef}
        id="email"
        label="邮箱"
        type="email"
        autoComplete="email"
        placeholder="you@example.com"
        invalid={emailErr}
        className={shakeEmail ? 'shake' : ''}
        onInput={() => setEmailErr(false)}
      />

      <div className="field">
        <AuthField
          ref={pwdRef}
          id="password"
          label="密码"
          password
          autoComplete="new-password"
          placeholder="至少 8 位，含字母与数字"
          invalid={pwdErr}
          className={shakePwd ? 'shake' : ''}
          onInput={(ev) => {
            setPwdErr(false);
            setStrength(passwordStrength((ev.target as HTMLInputElement).value));
          }}
        />
        <div className="meter" data-lv={strength}>
          <span className="seg" />
          <span className="seg" />
          <span className="seg" />
        </div>
        {strength > 0 ? (
          <div className="meter-label" data-lv={strength}>
            密码强度：<b>{STRENGTH_LABEL[strength]}</b>
          </div>
        ) : (
          <div className="meter-label" />
        )}
      </div>

      <AuthField
        ref={confirmRef}
        id="confirm"
        label="确认密码"
        password
        autoComplete="new-password"
        placeholder="再次输入密码"
        invalid={confirmErr}
        className={shakeConfirm ? 'shake' : ''}
        onInput={() => setConfirmErr(false)}
      />

      <div className="agree">
        <label className={agreeErr ? 'check err' : 'check'}>
          <input
            ref={agreeRef}
            type="checkbox"
            onChange={() => setAgreeErr(false)}
          />
          <span className="box">
            <svg viewBox="0 0 24 24">
              <path d="m5 12 5 5 9-11" />
            </svg>
          </span>
          我已阅读并同意 <a href="/agreement">《服务协议》</a> 与{' '}
          <a href="/privacy">《隐私政策》</a>
        </label>
      </div>

      {register.isError ? (
        <p className="form-msg err" role="alert">
          {register.error.message}
        </p>
      ) : null}
      {done ? (
        <p className="form-msg ok" role="status">
          注册成功，正在前往登录…
        </p>
      ) : null}

      <button className="btn btn-glow" type="submit" disabled={register.isPending || done}>
        {register.isPending ? '创建中…' : '创建账号'}
      </button>

      <div className="divider">或使用以下方式注册</div>
      <OAuthRow />

      <p className="foot">
        已有账号？<a href={loginHref}>去登录</a>
      </p>
    </form>
  );
}
