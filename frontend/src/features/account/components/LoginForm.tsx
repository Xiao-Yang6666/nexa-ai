'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AuthField } from './AuthField';
import { OAuthRow } from './OAuthRow';
import { useLogin, type AccountVM } from '../model/account.model';

/**
 * LoginForm — 登录表单（深色门面玻璃风，独立 /login 页用）。
 *
 * 1:1 还原 06_prototype/final/web-public/login.html 的表单结构/交互：
 * 邮箱或用户名 + 密码（显隐眼睛）+ 记住我 + 忘记密码 + 第三方登录。
 * 标题用全局 .auth-title / .auth-sub（auth.module.css），外层卡由 AuthSplitLayout 的 .card 提供，
 * 故本组件不再包 auth-card（避免独立页双层卡）。
 *
 * 接 openapi mock（POST /api/user/login，F-1002）：空值抖动校验 → loading → 成功/失败。
 * 客户端零泄露：成功只拿裁剪过的 AccountVM（成本/利润/上游模型 B/供应商不进客户端）。
 *
 * @param onSuccess 登录成功回调；省略则默认跳 /console
 * @param registerHref 注册入口链接，默认 /register
 */
export interface LoginFormProps {
  onSuccess?: (user: AccountVM) => void;
  registerHref?: string;
}

export function LoginForm({ onSuccess, registerHref = '/register' }: LoginFormProps) {
  const router = useRouter();
  const accRef = useRef<HTMLInputElement>(null);
  const pwdRef = useRef<HTMLInputElement>(null);
  const [accErr, setAccErr] = useState(false);
  const [pwdErr, setPwdErr] = useState(false);
  const [shakeAcc, setShakeAcc] = useState(false);
  const [shakePwd, setShakePwd] = useState(false);
  const login = useLogin();

  function shake(setShake: (b: boolean) => void) {
    setShake(true);
    setTimeout(() => setShake(false), 420);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const acc = accRef.current?.value.trim() ?? '';
    const pwd = pwdRef.current?.value.trim() ?? '';
    let bad = false;
    setAccErr(false);
    setPwdErr(false);
    if (!acc) {
      setAccErr(true);
      shake(setShakeAcc);
      bad = true;
    }
    if (!pwd) {
      setPwdErr(true);
      shake(setShakePwd);
      bad = true;
    }
    if (bad) return;
    login.mutate(
      { username: acc, password: pwd },
      {
        onSuccess: (user) => {
          if (onSuccess) onSuccess(user);
          else router.push('/dashboard');
        },
      },
    );
  }

  return (
    <form id="loginForm" onSubmit={handleSubmit} noValidate>
      <h1 className="auth-title">登录</h1>
      <p className="auth-sub">登录你的 Nexa 账号，继续直连</p>

      <AuthField
        ref={accRef}
        id="account"
        label="邮箱 / 用户名"
        autoComplete="username"
        placeholder="you@example.com"
        invalid={accErr}
        className={shakeAcc ? 'shake' : ''}
      />
      <AuthField
        ref={pwdRef}
        id="password"
        label="密码"
        password
        autoComplete="current-password"
        placeholder="输入密码"
        invalid={pwdErr}
        className={shakePwd ? 'shake' : ''}
      />

      <div className="row-between">
        <label className="check">
          <input type="checkbox" defaultChecked />
          <span className="box">
            <svg viewBox="0 0 24 24">
              <path d="m5 12 5 5 9-11" />
            </svg>
          </span>
          记住我
        </label>
        <a className="link" href="#">
          忘记密码？
        </a>
      </div>

      {login.isError ? (
        <p className="form-msg err" role="alert">
          {login.error.message}
        </p>
      ) : null}

      <button className="btn btn-glow" type="submit" disabled={login.isPending}>
        {login.isPending ? '登录中…' : '登录'}
      </button>

      <div className="divider">或使用以下方式登录</div>
      <OAuthRow />

      <p className="foot">
        还没有账号？<a href={registerHref}>去注册</a>
      </p>
    </form>
  );
}
