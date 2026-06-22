'use client';

import Link from 'next/link';
import { AppShell } from '@/features/shell';
import { useBalance, useMonthSpend, useRechargeRecords } from '../model/billing.model';
import styles from './BillingPage.module.css';

/**
 * BillingPage — 账单与计费（S6 console/billing.html 工程化）。
 *
 * 接 GET /api/user/self（F-1045）拿余额；GET /api/log/self/stat（F-4005）拿月消费；
 * GET /api/log/self?type=1（F-4002）拿充值记录。
 * 客户端零泄露：只展示 quota（本人余额/已用）、consumed（本月消费 USD）、充值记录（本人实付）。
 *   不渲染 quota_cost / quota_profit / 上游模型 B / 供应商。
 * loading/empty/error 各态完备。
 */
export function BillingPage() {
  const balance = useBalance();
  const monthSpend = useMonthSpend();
  const recharges = useRechargeRecords(1, 20);

  const actions = (
    <Link className="btn btn-primary" href="/recharge">
      立即充值
    </Link>
  );

  return (
    <AppShell activeId="billing" title="账单与计费" crumb={['控制台', '账单与计费']} actions={actions}>
      {/* 余额卡 + 计费说明 */}
      <section className={styles.topGrid}>
        {/* 余额大卡 */}
        <div className={`${styles.balCard} nx-fade`}>
          <div className={styles.balLbl}>当前余额</div>
          <div className={styles.balVal}>
            {balance.isLoading ? '…' : (balance.data?.balanceUsd ?? '$0.00')}
          </div>
          <div className={styles.balSub}>
            {balance.data ? `分组：${balance.data.group.toUpperCase()}` : '加载中…'}
          </div>
          <div className={styles.balMeta}>
            <div>
              本月已消费
              <b>{monthSpend.data ?? '…'}</b>
            </div>
            <div>
              累计使用
              <b>{balance.data?.usedUsd ?? '…'}</b>
            </div>
            <div>
              邀请累计奖励
              <b>{balance.data?.affHistoryUsd ?? '…'}</b>
            </div>
          </div>
        </div>

        {/* 计费说明卡（产品静态文案，不暴露内部成本） */}
        <div className={`${styles.infoCard} nx-fade`}>
          <h3>计费方式</h3>
          <table className={styles.rateTbl}>
            <tbody>
              <tr>
                <td>计费模型</td>
                <td>按 Token 用量实时扣费</td>
              </tr>
              <tr>
                <td>结算货币</td>
                <td>USD（美元）</td>
              </tr>
              <tr>
                <td>计费精度</td>
                <td>精确到 0.0001 USD</td>
              </tr>
              <tr>
                <td>结算周期</td>
                <td>实时扣费，按月出账单</td>
              </tr>
              <tr>
                <td>按次计费</td>
                <td>图像生成等按次计费</td>
              </tr>
            </tbody>
          </table>
          <div className={styles.note}>
            <svg className={styles.nx_ic} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
              <circle cx="12" cy="12" r="9" />
              <path d="M12 11v5" />
              <path d="M12 8h.01" />
            </svg>
            <span>VIP 用户折扣系数低至 0.85x，SVIP 低至 0.75x。可在「余额充值」页升级。</span>
          </div>
        </div>
      </section>

      {/* 充值记录 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.thBar}>
          <h3>充值记录</h3>
        </div>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th className={styles.num}>充值金额</th>
                <th>描述</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {recharges.isLoading ? (
                <tr>
                  <td colSpan={4} className={styles.stateCell}>
                    加载中…
                  </td>
                </tr>
              ) : recharges.isError ? (
                <tr>
                  <td colSpan={4} className={styles.stateCell}>
                    充值记录加载失败，请稍后重试。
                  </td>
                </tr>
              ) : !recharges.data?.length ? (
                <tr>
                  <td colSpan={4} className={styles.stateCell}>
                    暂无充值记录。
                  </td>
                </tr>
              ) : (
                recharges.data.map((r) => (
                  <tr key={r.id}>
                    <td className="mono-num">{r.time}</td>
                    <td className={`${styles.num} ${styles.amtOk}`}>{r.amountUsd}</td>
                    <td>{r.desc}</td>
                    <td>
                      <span className="badge b-suc">
                        <span className="dot" style={{ background: 'var(--color-success)' }} />
                        成功
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </AppShell>
  );
}
