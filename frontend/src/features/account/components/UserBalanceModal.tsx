'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import {
  useCreditUser,
  useDebitUser,
  useBalanceLogs,
  BALANCE_TYPE_MAP,
} from '../model/users-admin.model';
import styles from './UserBalanceModal.module.css';

export interface UserBalanceModalProps {
  /** 目标用户；null = 关闭 */
  user: { id: number; name: string; balanceUsd: number } | null;
  onClose: () => void;
}

function fmtTs(ts: number | undefined | null): string {
  if (!ts || ts <= 0) return '—';
  const d = new Date(ts * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function fmtUsd(v: number | undefined): string {
  const n = typeof v === 'number' ? v : 0;
  return (n < 0 ? '-$' : '$') + Math.abs(n).toFixed(2);
}

/**
 * UserBalanceModal — 用户余额管理（居中弹窗）。
 *
 * 三区：① 充值/扣费（金额+备注+按钮，显示当前余额）；② 账变流水（充值/扣费/兑换/自助）。
 * 充值/扣费走 admin 端点，成功后流水与列表自动刷新；兑换流水统一在账变日志（type=REDEEM）。
 */
export function UserBalanceModal({ user, onClose }: UserBalanceModalProps) {
  const open = user != null;
  const [amount, setAmount] = useState('');
  const [remark, setRemark] = useState('');
  const [err, setErr] = useState<string | null>(null);

  const creditMut = useCreditUser();
  const debitMut = useDebitUser();
  const logsQuery = useBalanceLogs(open ? user!.id : null);

  const busy = creditMut.isPending || debitMut.isPending;

  const reset = () => { setAmount(''); setRemark(''); setErr(null); };

  const submit = async (kind: 'credit' | 'debit') => {
    setErr(null);
    const amt = Number(amount);
    if (Number.isNaN(amt) || amt <= 0) { setErr('请输入大于 0 的金额'); return; }
    try {
      const mut = kind === 'credit' ? creditMut : debitMut;
      await mut.mutateAsync({ id: user!.id, amount: amt, remark: remark.trim() || undefined });
      reset();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : '操作失败，请稍后重试');
    }
  };

  const logs = logsQuery.data ?? [];

  return (
    <div className={`${styles.scrim}${open ? ' ' + styles.open : ''}`}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      {user && (
        <div className={styles.modal} role="dialog" aria-modal="true" aria-label="用户余额管理">
          <div className={styles.head}>
            <div>
              <h3 className={styles.title}>余额管理 · {user.name}</h3>
              <div className={styles.sub}>当前余额 <b>{fmtUsd(user.balanceUsd)}</b></div>
            </div>
            <button className={styles.x} onClick={onClose} aria-label="关闭">×</button>
          </div>

          {/* 充值/扣费 */}
          <div className={styles.adjust}>
            <div className={styles.row}>
              <div className={styles.field}>
                <label className="field-label">金额（USD）</label>
                <input
                  className="input mono-num"
                  inputMode="decimal"
                  placeholder="如 10.00"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label className="field-label">备注（可选）</label>
                <input
                  className="input"
                  placeholder="内部备注"
                  value={remark}
                  onChange={(e) => setRemark(e.target.value)}
                />
              </div>
            </div>
            <div className={styles.acts}>
              <Button variant="primary" size="sm" disabled={busy} onClick={() => submit('credit')}>
                {creditMut.isPending ? '充值中…' : '充值'}
              </Button>
              <Button variant="danger" size="sm" disabled={busy} onClick={() => submit('debit')}>
                {debitMut.isPending ? '扣费中…' : '扣费'}
              </Button>
              <span className={styles.hint}>扣费超出余额时只扣到 $0</span>
            </div>
            {err && <div className={styles.err}>{err}</div>}
          </div>

          {/* 账变流水 */}
          <div className={styles.logsSec}>
            <div className={styles.logsTitle}>账变流水</div>
            <div className={styles.logsWrap}>
              <table className={styles.logsTable}>
                <thead>
                  <tr>
                    <th>时间</th><th>类型</th><th>金额</th><th>变动后</th><th>备注</th>
                  </tr>
                </thead>
                <tbody>
                  {logsQuery.isLoading ? (
                    <tr><td colSpan={5} className={styles.empty}>加载中…</td></tr>
                  ) : logs.length === 0 ? (
                    <tr><td colSpan={5} className={styles.empty}>暂无账变记录</td></tr>
                  ) : (
                    logs.map((t) => {
                      const m = BALANCE_TYPE_MAP[t.type ?? ''] ?? { lab: t.type ?? '—', cls: 'b-neutral' };
                      const amt = typeof t.amount === 'number' ? t.amount : 0;
                      return (
                        <tr key={t.id}>
                          <td className="mono-num muted">{fmtTs(t.created_time)}</td>
                          <td><span className={`badge ${m.cls}`}>{m.lab}</span></td>
                          <td className={`mono-num ${amt < 0 ? styles.neg : styles.pos}`}>{fmtUsd(amt)}</td>
                          <td className="mono-num muted">{fmtUsd(t.balance_after)}</td>
                          <td className="muted">{t.remark || '—'}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className={styles.foot}>
            <Button variant="ghost" onClick={onClose}>关闭</Button>
          </div>
        </div>
      )}
    </div>
  );
}
