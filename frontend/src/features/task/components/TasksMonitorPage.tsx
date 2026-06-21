'use client';

import { useState } from 'react';
import { AdminShell } from '@/features/admin';
import { Button } from '@/shared/ui';
import styles from './TasksMonitorPage.module.css';

/* ── 静态 KPI 数据 ── */
const KPI = [
  { label: '今日任务总量', val: '9,264', delta: '较昨日 +11.5%', trend: 'up' },
  { label: '处理中', val: '148', delta: '队列稳定', trend: 'flat' },
  { label: '已完成', val: '8,946', delta: '成功率 98.4%', trend: 'up' },
  { label: '失败', val: '142', delta: '较昨日 +18', trend: 'down' },
  { label: '平均耗时', val: '3.8 s', delta: '较昨日 -0.4s', trend: 'up' },
];

/* ── 近7天趋势数据 ── */
const TREND_DONE = [7.8, 8.1, 7.6, 8.4, 8.9, 8.6, 8.95];
const TREND_FAIL = [0.11, 0.13, 0.18, 0.1, 0.16, 0.12, 0.14];
const TREND_LABS = ['6/14', '6/15', '6/16', '6/17', '6/18', '6/19', '6/20'];

/* ── 任务状态分布 ── */
const STATUS_DIST = [
  { name: '已完成', val: 8946, col: '--chart-1' },
  { name: '处理中', val: 148, col: '--chart-2' },
  { name: '排队', val: 96, col: '--chart-3' },
  { name: '失败', val: 142, col: '--color-danger' },
  { name: '超时', val: 28, col: '--chart-6' },
];

/* ── 各任务类型耗时 ── */
const LATENCY_BY_TYPE = [
  { name: '图像生成', val: 9.4, col: '--chart-6' },
  { name: '批量推理', val: 7.8, col: '--chart-4' },
  { name: '文档解析', val: 5.2, col: '--chart-3' },
  { name: '语音转写', val: 4.1, col: '--chart-5' },
  { name: '对话补全', val: 2.6, col: '--chart-1' },
  { name: '嵌入向量', val: 0.9, col: '--chart-2' },
];

/* ── 表格数据 ── */
const TASKS = [
  { id: 'tsk_9f3a1c', type: '对话补全', user: 'u_alice', ts: '06-20 05:28:11', st: 'run', pg: 62, dur: '2.1s' },
  { id: 'tsk_9f3a18', type: '图像生成', user: 'u_bob', ts: '06-20 05:27:54', st: 'run', pg: 38, dur: '5.8s' },
  { id: 'tsk_9f3a05', type: '嵌入向量', user: 'u_carol', ts: '06-20 05:27:40', st: 'done', pg: 100, dur: '0.8s' },
  { id: 'tsk_9f39f2', type: '批量推理', user: 'svc_etl', ts: '06-20 05:26:33', st: 'run', pg: 74, dur: '12.4s' },
  { id: 'tsk_9f39e0', type: '文档解析', user: 'u_dave', ts: '06-20 05:25:18', st: 'done', pg: 100, dur: '4.9s' },
  { id: 'tsk_9f39c4', type: '语音转写', user: 'u_erin', ts: '06-20 05:24:02', st: 'time', pg: 55, dur: '31.2s' },
  { id: 'tsk_9f39a1', type: '对话补全', user: 'u_frank', ts: '06-20 05:23:47', st: 'done', pg: 100, dur: '2.3s' },
  { id: 'tsk_9f3988', type: '图像生成', user: 'u_grace', ts: '06-20 05:22:30', st: 'fail', pg: 18, dur: '1.1s' },
  { id: 'tsk_9f396f', type: '对话补全', user: 'u_heidi', ts: '06-20 05:21:55', st: 'queue', pg: 0, dur: '—' },
  { id: 'tsk_9f3951', type: '嵌入向量', user: 'svc_index', ts: '06-20 05:20:12', st: 'done', pg: 100, dur: '0.6s' },
  { id: 'tsk_9f3940', type: '批量推理', user: 'svc_etl', ts: '06-20 05:19:08', st: 'time', pg: 40, dur: '34.7s' },
  { id: 'tsk_9f3922', type: '文档解析', user: 'u_ivan', ts: '06-20 05:18:44', st: 'done', pg: 100, dur: '5.5s' },
  { id: 'tsk_9f3910', type: '对话补全', user: 'u_judy', ts: '06-20 05:17:21', st: 'cancel', pg: 30, dur: '1.4s' },
  { id: 'tsk_9f38f3', type: '语音转写', user: 'u_karl', ts: '06-20 05:16:09', st: 'done', pg: 100, dur: '3.8s' },
  { id: 'tsk_9f38e1', type: '图像生成', user: 'u_lena', ts: '06-20 05:15:50', st: 'fail', pg: 22, dur: '2.0s' },
  { id: 'tsk_9f38c0', type: '对话补全', user: 'u_mike', ts: '06-20 05:14:33', st: 'done', pg: 100, dur: '2.7s' },
  { id: 'tsk_9f38a5', type: '嵌入向量', user: 'svc_index', ts: '06-20 05:13:11', st: 'queue', pg: 0, dur: '—' },
  { id: 'tsk_9f3890', type: '批量推理', user: 'svc_etl', ts: '06-20 05:12:02', st: 'done', pg: 100, dur: '9.8s' },
];

const ST_MAP: Record<string, { cls: string; tone: string; lab: string }> = {
  queue: { cls: 'b-neutral', tone: '--color-text-muted', lab: '排队' },
  run: { cls: 'b-info', tone: '--color-info', lab: '处理中' },
  done: { cls: 'b-suc', tone: '--color-success', lab: '已完成' },
  fail: { cls: 'b-dan', tone: '--color-danger', lab: '失败' },
  time: { cls: 'b-warn', tone: '--color-warning', lab: '超时' },
  cancel: { cls: 'b-neutral', tone: '--color-text-muted', lab: '已取消' },
};

/* ── SVG 近7天趋势折线图 ── */
function TrendChart() {
  const W = 760, H = 280, pad = { l: 48, r: 18, t: 18, b: 32 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = 10, min = 0;
  const xs = (i: number) => pad.l + i * (iw / (TREND_DONE.length - 1));
  const ys = (v: number) => pad.t + ih - ((v - min) / (max - min)) * ih;

  const areaPath = `M${pad.l} ${ys(0)} ${TREND_DONE.map((v, i) => `L${xs(i)} ${ys(v)}`).join(' ')} L${xs(TREND_DONE.length - 1)} ${ys(0)} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="近7天任务量趋势折线图">
      <defs>
        <linearGradient id="tg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.28} />
          <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0} />
        </linearGradient>
      </defs>
      {[0, 1, 2, 3, 4, 5].map((t) => {
        const v = min + ((max - min) * t) / 5;
        const y = ys(v);
        return (
          <g key={t}>
            <line x1={pad.l} y1={y} x2={W - pad.r} y2={y} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={pad.l - 8} y={y + 3} textAnchor="end">
              {v}
            </text>
          </g>
        );
      })}
      {TREND_LABS.map((l, i) => (
        <text key={i} className={styles.axTxt} x={xs(i)} y={H - 10} textAnchor="middle">
          {l}
        </text>
      ))}
      <path d={areaPath} fill="url(#tg)" />
      <polyline
        points={TREND_DONE.map((v, i) => `${xs(i)},${ys(v)}`).join(' ')}
        fill="none"
        stroke="var(--chart-1)"
        strokeWidth={2.4}
        strokeLinejoin="round"
      />
      <polyline
        points={TREND_FAIL.map((v, i) => `${xs(i)},${ys(v * 10)}`).join(' ')}
        fill="none"
        stroke="var(--color-danger)"
        strokeWidth={2.2}
        strokeDasharray="5 4"
        strokeLinejoin="round"
      />
      {TREND_DONE.map((v, i) => (
        <circle key={i} cx={xs(i)} cy={ys(v)} r={3} fill="var(--chart-1)" />
      ))}
    </svg>
  );
}

/* ── SVG 任务状态分布环形图 ── */
function DonutChart() {
  const cx = 120, cy = 110, r = 78, rin = 50;
  const total = STATUS_DIST.reduce((s, d) => s + d.val, 0);
  let ang = -Math.PI / 2;
  const pt = (a: number, rad: number) => [cx + Math.cos(a) * rad, cy + Math.sin(a) * rad];

  return (
    <svg viewBox="0 0 240 240" width="100%" height={200} role="img" aria-label="任务状态分布环形图">
      {STATUS_DIST.map((s) => {
        const a0 = ang, a1 = ang + (s.val / total) * Math.PI * 2;
        ang = a1;
        const large = a1 - a0 > Math.PI ? 1 : 0;
        const p0 = pt(a0, r), p1 = pt(a1, r), q0 = pt(a1, rin), q1 = pt(a0, rin);
        const d = `M${p0[0]} ${p0[1]} A${r} ${r} 0 ${large} 1 ${p1[0]} ${p1[1]} L${q0[0]} ${q0[1]} A${rin} ${rin} 0 ${large} 0 ${q1[0]} ${q1[1]} Z`;
        return <path key={s.name} d={d} fill={`var(${s.col})`} />;
      })}
      <text className={styles.donutCenter} x={cx} y={cy - 2} textAnchor="middle" fontSize={22}>
        9,360
      </text>
      <text className={styles.donutCenterSub} x={cx} y={cy + 18} textAnchor="middle" fontSize={11}>
        任务总数
      </text>
    </svg>
  );
}

/* ── SVG 各任务类型耗时横向柱状图 ── */
function LatencyBars() {
  const W = 1180, H = 300, pad = { l: 96, r: 60, t: 10, b: 24 };
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const max = 10, gh = ih / LATENCY_BY_TYPE.length, bh = 20;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="各任务类型耗时横向柱状图">
      {[0, 1, 2, 3, 4, 5].map((t) => {
        const v = (max * t) / 5;
        const x = pad.l + (v / max) * iw;
        return (
          <g key={t}>
            <line x1={x} y1={pad.t} x2={x} y2={pad.t + ih} stroke="var(--chart-grid)" strokeWidth={1} />
            <text className={styles.axTxt} x={x} y={H - 8} textAnchor="middle">
              {v}s
            </text>
          </g>
        );
      })}
      {LATENCY_BY_TYPE.map((r, i) => {
        const cy = pad.t + gh * i + gh / 2;
        const w = (r.val / max) * iw;
        return (
          <g key={r.name}>
            <text className={styles.axTxt} x={pad.l - 10} y={cy + 4} textAnchor="end" style={{ fill: 'var(--color-text-secondary)' }}>
              {r.name}
            </text>
            <rect x={pad.l} y={cy - bh / 2} width={w} height={bh} rx={4} fill={`var(${r.col})`} />
            <text className={styles.axTxt} x={pad.l + w + 8} y={cy + 4} textAnchor="start" style={{ fill: 'var(--color-text)' }}>
              {r.val}s
            </text>
          </g>
        );
      })}
    </svg>
  );
}

/**
 * TasksMonitorPage — 任务监控页（S6 admin/tasks-monitor.html 工程化）。
 *
 * KPI 顶行（今日总量/处理中/完成/失败/平均耗时）+ 超时告警条 +
 * 近7天趋势折线 + 状态环形图 + 类型耗时柱状 + 全量任务表格 + 筛选/分页。
 * 管理端可展示全字段，无客户端零泄露约束。
 */
export function TasksMonitorPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  return (
    <AdminShell
      activeId="tasks-monitor"
      title="任务监控"
      crumb={['管理后台', '运营', '任务监控']}
      actions={
        <Button variant="sec" size="sm">
          刷新
        </Button>
      }
    >
      {/* KPI 顶行 */}
      <section className={styles.kpiRow}>
        {KPI.map((k, i) => (
          <div key={i} className={`${styles.kpi} nx-fade`}>
            <div className={styles.kpiLabel}>{k.label}</div>
            <div className={styles.kpiVal}>{k.val}</div>
            <div className={`${styles.kpiDelta} ${styles[k.trend]}`}>{k.delta}</div>
          </div>
        ))}
      </section>

      {/* 超时告警条 */}
      <section className={`${styles.alertBar} nx-fade`}>
        <svg className={styles.alertIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M12 9v4" />
          <path d="M12 17h.01" />
          <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
        </svg>
        <span className={styles.alertTxt}>
          超时扫描：检测到 <b>28</b> 个任务超过 30s 阈值，其中 <b>9</b> 个已自动重试，<b>3</b> 个进入死信队列待人工处理。
        </span>
        <span className={styles.grow} />
        <Button variant="sec" size="sm">
          查看超时清单
        </Button>
      </section>

      {/* 图表区 1：近7天趋势 + 状态环形 */}
      <section className={styles.chartGrid}>
        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>近 7 天任务量趋势</h3>
              <div className={styles.chartSub}>完成 / 失败 分线 · 单位：千次</div>
            </div>
          </div>
          <TrendChart />
          <div className={styles.legend}>
            <span>
              <i style={{ background: 'var(--chart-1)' }} />
              已完成（千次）
            </span>
            <span>
              <i style={{ background: 'var(--color-danger)' }} />
              失败（×10 显示）
            </span>
          </div>
        </div>

        <div className={`${styles.chartCard} nx-fade`}>
          <div className={styles.chartHead}>
            <div>
              <h3 className={styles.chartTitle}>任务状态分布</h3>
              <div className={styles.chartSub}>当前快照</div>
            </div>
          </div>
          <DonutChart />
          <div className={styles.legend}>
            {STATUS_DIST.map((s) => (
              <span key={s.name}>
                <i style={{ background: `var(${s.col})` }} />
                {s.name} {s.val}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* 图表区 2：各任务类型耗时柱状 */}
      <section className={`${styles.chartCard} nx-fade`} style={{ marginBottom: 'var(--space-5)' }}>
        <div className={styles.chartHead}>
          <div>
            <h3 className={styles.chartTitle}>各任务类型平均耗时对比</h3>
            <div className={styles.chartSub}>单位：秒 · P95</div>
          </div>
        </div>
        <LatencyBars />
      </section>

      {/* FilterBar */}
      <section className={`${styles.filterbar} nx-fade`}>
        <select className={styles.sel} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">全部状态</option>
          <option>排队</option>
          <option>处理中</option>
          <option>已完成</option>
          <option>失败</option>
          <option>超时</option>
          <option>已取消</option>
        </select>
        <select className={styles.sel} value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
          <option value="">全部类型</option>
          <option>对话补全</option>
          <option>嵌入向量</option>
          <option>图像生成</option>
          <option>语音转写</option>
          <option>文档解析</option>
          <option>批量推理</option>
        </select>
        <select className={styles.sel}>
          <option>近 1 小时</option>
          <option>近 24 小时</option>
          <option>近 7 天</option>
        </select>
        <input className={styles.srch} type="search" placeholder="搜索任务 ID / 用户" />
        <span className={styles.grow} />
        <Button variant="sec" size="sm">
          导出 CSV
        </Button>
      </section>

      {/* 全量任务表格 */}
      <section className={`${styles.tableCard} nx-fade`}>
        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th>任务 ID</th>
                <th>类型</th>
                <th>提交用户</th>
                <th>提交时间</th>
                <th>状态</th>
                <th>进度</th>
                <th>耗时</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {TASKS.map((r) => {
                const st = ST_MAP[r.st];
                return (
                  <tr key={r.id}>
                    <td className="mono-num">{r.id}</td>
                    <td className="muted">{r.type}</td>
                    <td className="mono-num">{r.user}</td>
                    <td className="mono-num muted">{r.ts}</td>
                    <td>
                      <span className={`badge ${st.cls}`}>
                        <span className="dot" style={{ background: `var(${st.tone})` }} />
                        {st.lab}
                      </span>
                    </td>
                    <td>
                      {r.st === 'queue' ? (
                        <span className="muted mono-num">—</span>
                      ) : (
                        <span className={styles.prog}>
                          <span className={styles.bar}>
                            <i style={{ width: `${r.pg}%` }} />
                          </span>
                          <span className={styles.pct}>{r.pg}%</span>
                        </span>
                      )}
                    </td>
                    <td className="mono-num">{r.dur}</td>
                    <td>
                      <div className={styles.rowActs}>
                        <a className="btn-link">查看</a>
                        {(r.st === 'run' || r.st === 'queue') && <a className="btn-link" style={{ color: 'var(--color-danger)' }}>取消</a>}
                        {(r.st === 'fail' || r.st === 'time') && <a className="btn-link">重试</a>}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <span>共 {TASKS.length} 个任务（本页）· 全量 9,360</span>
          <div className={styles.pg}>
            <button>‹</button>
            <button className={styles.on}>1</button>
            <button>2</button>
            <button>3</button>
            <button>›</button>
          </div>
        </div>
      </section>
    </AdminShell>
  );
}
