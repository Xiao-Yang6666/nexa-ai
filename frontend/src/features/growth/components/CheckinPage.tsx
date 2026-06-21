'use client';

import { useMemo } from 'react';
import { ConsoleShell } from '@/features/console';
import {
  useCheckinStatus,
  useCheckinMutation,
  buildLadder,
} from '../model/growth.model';
import styles from './CheckinPage.module.css';

const DOW = ['日', '一', '二', '三', '四', '五', '六'];
const RING_LEN = 326.7; // 2πr, r=52

/** 对勾 SVG（已签单元 / 记录）。 */
function Tick() {
  return (
    <svg
      className={styles.calTick}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 13l4 4 10-11" />
    </svg>
  );
}

/**
 * CheckinPage — 每日签到（S6 console/checkin.html 工程化）。
 *
 * 接 GET /api/user/checkin（F-1047）→ 连续天数环 + 当月日历 + 奖励阶梯 + 记录表；
 * 「立即签到」走 POST /api/user/checkin（F-1046），成功后刷新状态。
 * 客户端零泄露：仅展示本人额度（USD 换算），无成本/利润/上游字段。
 * loading/error/success 各态完备。
 */
export function CheckinPage() {
  const { data, isLoading, isError, refetch } = useCheckinStatus();
  const checkin = useCheckinMutation();

  const ladder = useMemo(() => buildLadder(data?.streak ?? 0), [data?.streak]);

  // 连续天数环偏移：以 15 天阶梯为满环参考
  const ringOffset = useMemo(() => {
    const ratio = Math.min((data?.streak ?? 0) / 15, 1);
    return (RING_LEN * (1 - ratio)).toFixed(1);
  }, [data?.streak]);

  // 临近 15 天阶梯的提示
  const nearTier = ladder.find((l) => l.state !== 'reached');

  const actions = (
    <button
      className="btn btn-primary"
      disabled={data?.checkedToday || checkin.isPending}
      onClick={() => checkin.mutate()}
    >
      {data?.checkedToday ? '今日已签到' : checkin.isPending ? '签到中…' : '快速签到'}
    </button>
  );

  return (
    <ConsoleShell activeId="checkin" title="每日签到" crumb={['控制台', '每日签到']} actions={actions}>
      {isLoading ? (
        <section className={styles.hero}>
          <div className={styles.skeleton} />
          <div className={styles.skeleton} />
        </section>
      ) : isError || !data ? (
        <div className={`${styles.tableCard} ${styles.stateBox}`}>
          <div className={styles.t}>签到信息加载失败</div>
          <div>网络或服务异常，请稍后重试。</div>
          <button className="btn btn-sec" style={{ marginTop: 'var(--space-4)' }} onClick={() => refetch()}>
            重试
          </button>
        </div>
      ) : (
        <>
          {/* 连续天数 + 今日签到 */}
          <section className={styles.hero}>
            <div className={`${styles.card} nx-fade`}>
              <div className={styles.streak}>
                <div className={styles.streakRing}>
                  <svg viewBox="0 0 120 120" width={120} height={120} aria-hidden="true">
                    <circle cx="60" cy="60" r="52" fill="none" stroke="var(--color-surface-sunken)" strokeWidth="9" />
                    <circle
                      cx="60"
                      cy="60"
                      r="52"
                      fill="none"
                      stroke="var(--color-primary-500)"
                      strokeWidth="9"
                      strokeLinecap="round"
                      transform="rotate(-90 60 60)"
                      strokeDasharray={RING_LEN}
                      strokeDashoffset={ringOffset}
                      style={{ transition: 'stroke-dashoffset var(--dur-4) var(--ease-out)' }}
                    />
                  </svg>
                  <div className={styles.streakRingInner}>
                    <span className={styles.streakNum}>{data.streak}</span>
                    <span className={styles.streakUnit}>连续天数</span>
                  </div>
                </div>
                <div className={styles.streakMeta}>
                  <h3>已连续签到 {data.streak} 天</h3>
                  <p>
                    {nearTier ? (
                      <>
                        再坚持 <span className={styles.hl}>{nearTier.remain} 天</span> 即可领取 {nearTier.days} 天阶梯奖励{' '}
                        <span className={styles.hl}>{nearTier.rewardUsd}</span>。<br />
                      </>
                    ) : null}
                    累计签到 <span className={styles.hl}>{data.totalCheckins} 天</span>，已获额度{' '}
                    <span className={styles.hl}>{data.totalRewardUsd}</span>。
                  </p>
                </div>
              </div>
            </div>
            <div className={`${styles.card} ${styles.today} nx-fade`}>
              <div className={styles.todayLabel}>今日签到奖励</div>
              <div className={styles.todayReward}>
                <span className={styles.cur}>+$0.20</span> 调用额度
              </div>
              <button
                className={`btn btn-primary ${styles.btnBig}`}
                disabled={data.checkedToday || checkin.isPending}
                onClick={() => checkin.mutate()}
              >
                {data.checkedToday ? '今日已签到' : checkin.isPending ? '签到中…' : '立即签到'}
              </button>
              {checkin.isError ? (
                <div className="field-err">{(checkin.error as Error)?.message ?? '签到失败'}</div>
              ) : null}
            </div>
          </section>

          {/* 签到日历 */}
          <section className={styles.calWrap}>
            <div className={`${styles.card} ${styles.calCard} ${styles.calPanel} nx-fade`}>
              <div className={styles.calHead}>
                <span className={styles.calMonth}>{data.calMonthLabel}</span>
                <span className="muted" style={{ fontSize: 'var(--text-body-sm)' }}>
                  已签到 <b style={{ color: 'var(--color-primary-700)' }}>{data.monthChecked}</b> 天
                </span>
              </div>
              <div className={styles.calGrid}>
                {DOW.map((d) => (
                  <div key={d} className={styles.calDow}>
                    {d}
                  </div>
                ))}
                {data.calendar.map((cell, i) =>
                  cell.state === 'empty' ? (
                    <div key={`e-${i}`} className={`${styles.calCell} ${styles.empty}`} />
                  ) : (
                    <div key={`d-${cell.day}`} className={`${styles.calCell} ${styles[cell.state]}`}>
                      {cell.day}
                      {cell.state === 'done' || cell.state === 'today' ? <Tick /> : null}
                    </div>
                  ),
                )}
              </div>
              <div className={styles.calLegend}>
                <span>
                  <i className={styles.legDone} />
                  已签到
                </span>
                <span>
                  <i className={styles.legToday} />
                  今天
                </span>
                <span>
                  <i className={styles.legFuture} />
                  未签到 / 未来
                </span>
              </div>
            </div>
            <div className={`${styles.card} ${styles.calStatus} nx-fade`}>
              <h3 className={styles.sectionTitle} style={{ margin: '0 0 var(--space-2)' }}>
                本月签到概览
              </h3>
              <div className={styles.calStat}>
                <span className="k">本月已签到</span>
                <span className={`${styles.v} ${styles.hl}`}>{data.monthChecked} 天</span>
              </div>
              <div className={styles.calStat}>
                <span className="k">累计获得额度</span>
                <span className={`${styles.v} ${styles.hl}`}>{data.totalRewardUsd}</span>
              </div>
              <div className={styles.calStat}>
                <span className="k">当前连续</span>
                <span className={styles.v}>{data.streak} 天</span>
              </div>
              <div className={styles.calStat}>
                <span className="k">累计签到</span>
                <span className={styles.v}>{data.totalCheckins} 天</span>
              </div>
              <p className={styles.calStatusNote}>
                坚持每日签到，连续 <span className={styles.hl}>15 天</span> 可额外获得{' '}
                <span className={styles.hl}>$2.00</span> 阶梯奖励。
              </p>
            </div>
          </section>

          {/* 奖励阶梯 */}
          <h2 className={styles.sectionTitle}>连续签到奖励阶梯</h2>
          <section className={styles.ladder}>
            {ladder.map((l) => (
              <div key={l.days} className={`${styles.lad} ${l.state === 'reached' ? styles.reached : ''} nx-fade`}>
                <div className={styles.ladDays}>
                  {l.days} <small>天</small>
                </div>
                <div className={styles.ladReward}>
                  额外奖励 <b>{l.rewardUsd}</b>
                </div>
                <div className={styles.ladState}>
                  {l.state === 'reached' ? (
                    <span className="badge b-suc">
                      <span className="dot" style={{ background: 'var(--color-success)' }} />
                      已达成
                    </span>
                  ) : l.state === 'near' ? (
                    <span className="badge b-info">
                      <span className="dot" style={{ background: 'var(--color-info)' }} />
                      还差 {l.remain} 天
                    </span>
                  ) : (
                    <span className="badge b-neutral">
                      <span className="dot" style={{ background: 'var(--color-text-muted)' }} />
                      还差 {l.remain} 天
                    </span>
                  )}
                </div>
              </div>
            ))}
          </section>

          {/* 签到记录 */}
          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.thBar}>
              <h3>签到记录</h3>
              <span className="muted" style={{ fontSize: 'var(--text-body-sm)' }}>
                近 {data.records.length} 次
              </span>
            </div>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>日期</th>
                    <th>获得额度</th>
                  </tr>
                </thead>
                <tbody>
                  {data.records.map((r) => (
                    <tr key={r.date}>
                      <td className="mono-num">{r.date}</td>
                      <td className={`mono-num ${styles.rewardCell}`}>{r.rewardUsd}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </ConsoleShell>
  );
}
