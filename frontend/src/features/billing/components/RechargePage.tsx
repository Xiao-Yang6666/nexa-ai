'use client';

import { useState } from 'react';
import { AppShell } from '@/features/shell';
import { useCreateTopUp, giftFor } from '../model/billing.model';
import styles from './RechargePage.module.css';

const TIERS: { amt: number; gift: number }[] = [
  { amt: 10, gift: 0 },
  { amt: 50, gift: 2 },
  { amt: 100, gift: 5 },
  { amt: 500, gift: 50 },
];

const PAY_METHODS = [
  { id: 'alipay', label: '支付宝', desc: '扫码支付 · 实时到账' },
  { id: 'wechat', label: '微信支付', desc: '扫码支付 · 实时到账' },
  { id: 'transfer', label: '对公转账', desc: '企业开票 · 1-2 个工作日到账' },
];

function fmt(v: number): string {
  return `$${v.toFixed(2)}`;
}

/**
 * RechargePage — 余额充值（S6 console/recharge.html 工程化）。
 *
 * 档位卡 + 自定义金额 + 支付方式 + 订单摘要（赠送额度、到账余额）。
 * 确认充值走 POST /api/topup (F-2044)。
 * 客户端零泄露：仅展示本人充值金额/赠送额度，无成本/利润/上游字段。
 */
export function RechargePage() {
  const [amt, setAmt] = useState(100);
  const [custom, setCustom] = useState('');
  const [activeTier, setActiveTier] = useState<number | null>(100);
  const [payMethod, setPayMethod] = useState('alipay');
  const [done, setDone] = useState(false);

  const gift = giftFor(amt);
  const topup = useCreateTopUp();

  const selectTier = (a: number) => {
    setActiveTier(a);
    setAmt(a);
    setCustom('');
  };

  const handleCustom = (v: string) => {
    setCustom(v);
    const n = parseFloat(v) || 0;
    setActiveTier(null);
    setAmt(n);
  };

  const confirm = () => {
    if (!amt) return;
    topup.mutate(
      {
        money: amt,
        amount: Math.round(amt * 500_000),
        payment_method: payMethod === 'alipay' ? 'stripe' : payMethod === 'wechat' ? 'creem' : 'balance',
        payment_provider: payMethod,
      },
      {
        onSuccess: () => {
          setDone(true);
        },
      },
    );
  };

  return (
    <AppShell activeId="recharge" title="余额充值" crumb={['控制台', '账单与计费', '余额充值']}>
      {done ? (
        <section className={`${styles.panel} nx-fade`}>
          <div className={styles.successBox}>
            <svg viewBox="0 0 24 24" width={48} height={48} fill="none" stroke="currentColor" strokeWidth={1.6}>
              <circle cx="12" cy="12" r="9" />
              <path d="M8 12l3 3 5-5" />
            </svg>
            <h3>充值申请已提交</h3>
            <p>系统正在处理您的充值，到账后余额将自动更新。</p>
            <button className="btn btn-primary" type="button" onClick={() => setDone(false)}>
              继续充值
            </button>
          </div>
        </section>
      ) : (
        <div className={styles.payGrid}>
          <div className={styles.payLeft}>
            {/* 步骤 1：金额 */}
            <div className={`${styles.panel} nx-fade`}>
              <h3>
                <span className={styles.stepNo}>1</span>选择充值金额
              </h3>
              <div className={styles.tiers}>
                {TIERS.map((t) => (
                  <button
                    key={t.amt}
                    type="button"
                    className={`${styles.tier} ${activeTier === t.amt ? styles.on : ''}`}
                    onClick={() => selectTier(t.amt)}
                  >
                    <svg
                      className={styles.tick}
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth={2.2}
                    >
                      <path d="M5 12l5 5 9-10" />
                    </svg>
                    <div className={styles.tierAmt}>{fmt(t.amt)}</div>
                    <div className={styles.tierGift}>
                      {t.gift > 0 ? `赠 $${t.gift}` : '无赠送'}
                    </div>
                  </button>
                ))}
              </div>
              <div className={styles.custom}>
                <label className="field-label">或自定义金额（USD）</label>
                <div className={styles.amtInput}>
                  <span>$</span>
                  <input
                    className="input"
                    type="number"
                    min="1"
                    max="10000"
                    placeholder="输入 1 - 10000"
                    value={custom}
                    onChange={(e) => handleCustom(e.target.value)}
                  />
                </div>
              </div>
              <div className={styles.giftNote}>
                <svg viewBox="0 0 24 24" width={18} height={18} fill="none" stroke="currentColor" strokeWidth={1.6}>
                  <rect x="3" y="8" width="18" height="13" rx="1" />
                  <path d="M3 12h18" />
                  <path d="M12 8v13" />
                  <path d="M12 8C9 8 7 4 9 4s3 4 3 4 1-4 3-4 0 4-3 4z" />
                </svg>
                <div>
                  充得多送得多，赠送额永久有效：
                  <ul>
                    <li>满 $50 赠 4%，满 $100 赠 5%</li>
                    <li>满 $500 赠 10%，满 $1000 赠 12%</li>
                    <li>对公转账单笔满 $2000 额外赠 2%</li>
                  </ul>
                </div>
              </div>
            </div>

            {/* 步骤 2：支付方式 */}
            <div className={`${styles.panel} nx-fade`}>
              <h3>
                <span className={styles.stepNo}>2</span>选择支付方式
              </h3>
              <div className={styles.pays}>
                {PAY_METHODS.map((p) => (
                  <label
                    key={p.id}
                    className={`${styles.pay} ${payMethod === p.id ? styles.on : ''}`}
                    onClick={() => setPayMethod(p.id)}
                  >
                    <div>
                      <div className={styles.payName}>{p.label}</div>
                      <div className={styles.payDesc}>{p.desc}</div>
                    </div>
                    <span className={styles.payRadio} />
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* 订单摘要 */}
          <aside className={`${styles.panel} ${styles.summary} nx-fade`}>
            <h3 style={{ marginBottom: 'var(--space-3)' }}>订单摘要</h3>
            <div className={styles.sumRow}>
              <span className={styles.k}>充值金额</span>
              <span className={styles.v}>{amt ? fmt(amt) : '$—'}</span>
            </div>
            <div className={`${styles.sumRow} ${styles.gift}`}>
              <span className={styles.k}>赠送额度</span>
              <span className={styles.v}>+{fmt(gift)}</span>
            </div>
            <div className={styles.sumRow}>
              <span className={styles.k}>支付方式</span>
              <span className={styles.v}>{PAY_METHODS.find((p) => p.id === payMethod)?.label}</span>
            </div>
            <div className={styles.sumRow}>
              <span className={styles.k}>应付金额</span>
              <span className={styles.v}>{amt ? fmt(amt) : '$—'}</span>
            </div>
            <div className={styles.sumTotal}>
              <span className={styles.k}>到账余额</span>
              <span className={styles.v}>{fmt(amt + gift)}</span>
            </div>
            <button
              className={`btn btn-primary btn-lg ${styles.sumBtn}`}
              type="button"
              disabled={!amt || amt <= 0 || topup.isPending}
              onClick={confirm}
            >
              {topup.isPending ? '处理中…' : '确认充值'}
            </button>
            <div className={styles.sumFine}>
              点击即表示同意《充值服务协议》，余额不支持原路退款。
            </div>
            {topup.isError ? (
              <div className="field-err" style={{ marginTop: 'var(--space-2)' }}>
                {(topup.error as Error)?.message ?? '充值下单失败，请稍后重试'}
              </div>
            ) : null}
          </aside>
        </div>
      )}
    </AppShell>
  );
}
