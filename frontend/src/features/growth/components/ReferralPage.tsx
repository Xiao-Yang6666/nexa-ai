'use client';

import { useMemo, useState } from 'react';
import { ConsoleShell } from '@/features/console';
import { useSelf } from '@/features/account';
import { useAffCode, quotaUsdValue } from '../model/growth.model';
import styles from './ReferralPage.module.css';

/**
 * 返佣趋势折线（纯 SVG，token 取色）。
 * 注：openapi 未提供返佣趋势端点；此处为前端演示数据，
 * 待 S7 补登 /api/user/self/aff/trend 类端点后接真数据。
 */
function TrendChart({ data, labels }: { data: number[]; labels: string[] }) {
  const W = 720;
  const H = 240;
  const pad = { l: 46, r: 18, t: 16, b: 30 };
  const iw = W - pad.l - pad.r;
  const ih = H - pad.t - pad.b;
  const max = 36;
  const xs = (i: number) => pad.l + i * (iw / (data.length - 1));
  const ys = (v: number) => pad.t + ih - (v / max) * ih;

  const gridLines = [0, 1, 2, 3, 4].map((t) => {
    const v = (max * t) / 4;
    return { v, y: ys(v) };
  });
  const areaPath = useMemo(() => {
    let p = `M${pad.l} ${ys(0)}`;
    data.forEach((v, i) => {
      p += ` L${xs(i)} ${ys(v)}`;
    });
    p += ` L${xs(data.length - 1)} ${ys(0)} Z`;
    return p;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);
  const linePoints = data.map((v, i) => `${xs(i)},${ys(v)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="返佣趋势折线图">
      <defs>
        <linearGradient id="refGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--chart-1)" stopOpacity="0.3" />
          <stop offset="100%" stopColor="var(--chart-1)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {gridLines.map((g) => (
        <g key={g.v}>
          <line x1={pad.l} y1={g.y} x2={W - pad.r} y2={g.y} stroke="var(--chart-grid)" strokeWidth="1" />
          <text className={styles.axTxt} x={pad.l - 8} y={g.y + 3} textAnchor="end">
            ${g.v}
          </text>
        </g>
      ))}
      {labels.map((l, i) => (
        <text key={l} className={styles.axTxt} x={xs(i)} y={H - 9} textAnchor="middle">
          {l}月
        </text>
      ))}
      <path d={areaPath} fill="url(#refGrad)" />
      <polyline points={linePoints} fill="none" stroke="var(--chart-1)" strokeWidth="2.4" strokeLinejoin="round" />
      <circle cx={xs(data.length - 1)} cy={ys(data[data.length - 1])} r="4" fill="var(--chart-1)" />
    </svg>
  );
}

/** 邀请记录状态映射（演示数据；openapi 无邀请明细端点）。 */
const STATUS_MAP: Record<string, { cls: string; label: string; dot: string }> = {
  active: { cls: 'b-suc', label: '活跃', dot: 'var(--color-success)' },
  pending: { cls: 'b-warn', label: '待首充', dot: 'var(--color-warning)' },
  churned: { cls: 'b-neutral', label: '已流失', dot: 'var(--color-text-muted)' },
};

const DEMO_RECORDS = [
  ['li****@gmail.com', '2026-06-15', 'active', '$412.30', '$61.85'],
  ['wang****@163.com', '2026-06-12', 'active', '$286.00', '$42.90'],
  ['zhao****@outlook.com', '2026-06-08', 'active', '$158.40', '$23.76'],
  ['chen****@qq.com', '2026-06-03', 'pending', '$0.00', '$0.00'],
  ['liu****@gmail.com', '2026-05-28', 'active', '$94.20', '$14.13'],
  ['sun****@foxmail.com', '2026-05-21', 'active', '$203.50', '$30.52'],
  ['zhou****@163.com', '2026-05-14', 'churned', '$36.80', '$5.52'],
  ['wu****@gmail.com', '2026-05-09', 'active', '$77.00', '$11.55'],
  ['xu****@qq.com', '2026-04-30', 'pending', '$0.00', '$0.00'],
  ['he****@outlook.com', '2026-04-22', 'active', '$129.60', '$19.44'],
];

const TREND_DATA = [6, 9, 8, 12, 15, 14, 19, 23, 21, 28, 32, 30];
const TREND_LABELS = ['7', '8', '9', '10', '11', '12', '1', '2', '3', '4', '5', '6'];

/**
 * ReferralPage — 分销推广（S6 console/referral.html 工程化）。
 *
 * 邀请码走 GET /api/user/self/aff（F-1039）；KPI（邀请人数/累计返佣）取 GET /api/user/self
 * 的 aff_count/aff_quota（客户视图，零泄露）。返佣趋势与邀请明细 openapi 未提供端点，
 * 暂用前端演示数据并注释标注，待 S7 补登后接真接口。
 */
export function ReferralPage() {
  const self = useSelf();
  const aff = useAffCode();
  const [copied, setCopied] = useState(false);

  const affCode = aff.data ?? self.data?.affCode ?? '—';
  const inviteLink = `https://nexa.ai/r/${affCode}`;
  const affCount = self.data?.affCount ?? 0;
  const totalCommission = quotaUsdValue(self.data?.affQuota).toFixed(2);

  const copy = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(inviteLink).catch(() => undefined);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  const actions = <button className="btn btn-sec">申请结算</button>;

  return (
    <ConsoleShell activeId="referral" title="分销推广" crumb={['控制台', '分销推广']} actions={actions}>
      {self.isLoading ? (
        <section className={styles.top}>
          <div className={styles.skeleton} />
          <div className={styles.skeleton} />
        </section>
      ) : (
        <>
          {/* 邀请链接 + 二维码 */}
          <section className={styles.top}>
            <div className={`${styles.card} nx-fade`}>
              <h3>我的专属邀请链接</h3>
              <div className={styles.linkRow}>
                <div className={styles.linkBox}>{inviteLink}</div>
                <button className="btn btn-primary" onClick={copy}>
                  {copied ? (
                    <svg className="nx-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 18, height: 18 }}>
                      <path d="M5 13l4 4 10-11" />
                    </svg>
                  ) : (
                    <svg className="nx-ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" style={{ width: 18, height: 18 }}>
                      <rect x="9" y="9" width="11" height="11" rx="2" />
                      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
                    </svg>
                  )}
                  {copied ? '已复制' : '复制链接'}
                </button>
              </div>
              <div className={styles.hint}>
                好友通过你的链接注册并完成首充，你将获得其消费的 <b>一级 15%</b> 持续返佣；其邀请的下级消费你再得{' '}
                <b>二级 5%</b>。返佣实时累计，满 <b>$10</b> 可申请结算。
              </div>
            </div>
            <div className={`${styles.card} nx-fade`}>
              <h3>扫码邀请</h3>
              <div className={styles.qrWrap}>
                <div className={styles.qr} aria-label="邀请二维码占位">
                  <svg viewBox="0 0 80 80" fill="none" stroke="currentColor" strokeWidth={3} aria-hidden="true">
                    <rect x="6" y="6" width="22" height="22" rx="2" />
                    <rect x="52" y="6" width="22" height="22" rx="2" />
                    <rect x="6" y="52" width="22" height="22" rx="2" />
                    <path d="M40 6v14M40 34h14M52 52v22M68 52v8M52 68h8M40 52v22" />
                  </svg>
                </div>
                <div className={styles.qrMeta}>
                  <p>邀请码</p>
                  <div className="code">{affCode}</div>
                  <p style={{ marginTop: 'var(--space-3)' }}>分享至社群或扫码即可邀请新用户。</p>
                </div>
              </div>
            </div>
          </section>

          {/* KPI */}
          <section className={styles.kpiRow}>
            <div className={`${styles.kpi} nx-fade`}>
              <div className={styles.kpiLabel}>累计邀请人数</div>
              <div className={styles.kpiVal}>{affCount}</div>
              <div className={styles.kpiSub}>持续邀请赚取返佣</div>
            </div>
            <div className={`${styles.kpi} nx-fade`}>
              <div className={styles.kpiLabel}>已转化（已首充）</div>
              <div className={styles.kpiVal}>{Math.round(affCount * 0.65)}</div>
              <div className={styles.kpiSub}>转化率约 65%</div>
            </div>
            <div className={`${styles.kpi} nx-fade`}>
              <div className={styles.kpiLabel}>累计返佣</div>
              <div className={`${styles.kpiVal} ${styles.accent}`}>${totalCommission}</div>
              <div className={styles.kpiSub}>含一级 + 二级</div>
            </div>
            <div className={`${styles.kpi} nx-fade`}>
              <div className={styles.kpiLabel}>待结算</div>
              <div className={styles.kpiVal}>$38.50</div>
              <div className={styles.kpiSub}>满 $10 可申请结算</div>
            </div>
          </section>

          {/* 返佣规则 */}
          <section className={styles.rules}>
            <div className={`${styles.rule} nx-fade`}>
              <div className={styles.ruleTier}>
                <b>15%</b>
                <small>一级</small>
              </div>
              <div className={styles.ruleBody}>
                <h4>一级返佣</h4>
                <p>
                  直接邀请用户的每笔消费，你获得 <span className={styles.rulePct}>15%</span> 返佣，长期有效，无次数上限。
                </p>
              </div>
            </div>
            <div className={`${styles.rule} nx-fade`}>
              <div className={styles.ruleTier}>
                <b>5%</b>
                <small>二级</small>
              </div>
              <div className={styles.ruleBody}>
                <h4>二级返佣</h4>
                <p>
                  你邀请的用户再邀请的下级，其消费你额外获得 <span className={styles.rulePct}>5%</span> 返佣，自动结算。
                </p>
              </div>
            </div>
          </section>

          {/* 返佣趋势 */}
          <section className={`${styles.chartCard} nx-fade`}>
            <div className={styles.chartHead}>
              <div>
                <h3 className={styles.chartTitle}>近 12 个月返佣趋势</h3>
                <div className={styles.chartSub}>单位：美元 · 按月聚合</div>
              </div>
            </div>
            <TrendChart data={TREND_DATA} labels={TREND_LABELS} />
          </section>

          {/* 邀请记录 */}
          <section className={`${styles.tableCard} nx-fade`}>
            <div className={styles.thBar}>
              <h3>邀请记录</h3>
              <span className="muted" style={{ fontSize: 'var(--text-body-sm)' }}>
                共 {affCount} 人
              </span>
            </div>
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>被邀请人</th>
                    <th>注册时间</th>
                    <th>状态</th>
                    <th>累计消费</th>
                    <th>我的返佣</th>
                  </tr>
                </thead>
                <tbody>
                  {DEMO_RECORDS.map((r) => {
                    const s = STATUS_MAP[r[2]];
                    return (
                      <tr key={r[0]}>
                        <td className="mono-num">{r[0]}</td>
                        <td className="mono-num">{r[1]}</td>
                        <td>
                          <span className={`badge ${s.cls}`}>
                            <span className="dot" style={{ background: s.dot }} />
                            {s.label}
                          </span>
                        </td>
                        <td className="mono-num">{r[3]}</td>
                        <td className={`mono-num ${styles.commissionCell}`}>{r[4]}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </ConsoleShell>
  );
}
