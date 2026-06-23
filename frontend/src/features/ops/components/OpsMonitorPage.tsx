'use client';

import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { useSystemStatus, type OpsCapabilityVM } from '@/features/ops/model/ops.model';
import styles from './OpsMonitorPage.module.css';

/* ════════════════════════════════════════════════════════════════════════
   运维监控 / 系统状态 —— 调真后端 GET /api/status（F-4039）。
   契约 ops 模块未提供 CPU/内存/磁盘/GC/QPS 等主机资源指标端点（/metrics 为
   Prometheus 文本，供基础设施抓取，非前端消费）；本页以 /api/status 这一真实的
   系统状态聚合端点为数据源，展示系统信息与各能力开关的真实启用状态。
   管理端可展示全字段，不受客户端零泄露约束。
   ════════════════════════════════════════════════════════════════════════ */

/** 单条能力状态卡（启用=正常绿点，停用=灰点）。 */
function CapabilityItem({ cap }: { cap: OpsCapabilityVM }) {
  const tone = cap.enabled ? '--color-success' : '--color-text-secondary';
  const cls = cap.enabled ? 'b-suc' : 'b-sec';
  const lab = cap.enabled ? '已启用' : '未启用';
  return (
    <div className={styles.healthItem}>
      <span className="dot" style={{ background: `var(${tone})` }} />
      <span className={styles.nm}>
        {cap.name}
        <br />
        <span className={styles.sub}>{cap.key}</span>
      </span>
      <span className={`badge ${cls}`}>{lab}</span>
    </div>
  );
}

/**
 * OpsMonitorPage — 运维监控 / 系统状态（S6 admin/ops.html 工程化，接真后端 GET /api/status）。
 *
 * KPI 顶行（系统名 / 主题 / 已启用能力数）+ 系统能力开关状态列表（注册/邮箱验证/
 * 各 OAuth/Turnstile/签到/协议/隐私/自动分组的真实启用状态）。
 * 数据来源：useSystemStatus（GET /api/status，StatusAggregateView）。
 */
export function OpsMonitorPage() {
  const { data, isLoading, isError, error, refetch, isFetching } = useSystemStatus();

  const caps = data?.capabilities ?? [];

  return (
    <AppShell
      activeId="ops"
      title="运维监控"
      crumb={['管理后台', '系统', '运维监控']}
      actions={
        <Button variant="sec" size="sm" onClick={() => refetch()} disabled={isFetching}>
          {isFetching ? '刷新中…' : '刷新指标'}
        </Button>
      }
    >
      {/* 范围说明条 */}
      <section className={`${styles.chartCard} nx-fade`} style={{ marginBottom: 'var(--space-4)' }}>
        <div className={styles.chartSub}>
          数据来源：<code>GET /api/status</code>（系统状态聚合）。契约未提供主机资源指标端点，
          <code>/metrics</code> 为 Prometheus 文本供基础设施抓取，故本页展示系统信息与能力开关的真实状态。
        </div>
      </section>

      {/* KPI 顶行（真实系统信息） */}
      <section className={styles.kpiRow}>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.meta}>
            <div className={styles.kpiLabel}>系统名称</div>
            <div className={styles.kpiVal}>{isLoading ? '…' : data?.systemName ?? '—'}</div>
            <div className={`${styles.kpiDelta} ${styles.flat}`}>StatusAggregateView</div>
          </div>
        </div>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.meta}>
            <div className={styles.kpiLabel}>当前主题</div>
            <div className={styles.kpiVal}>{isLoading ? '…' : data?.theme ?? '—'}</div>
            <div className={`${styles.kpiDelta} ${styles.flat}`}>theme</div>
          </div>
        </div>
        <div className={`${styles.kpi} nx-fade`}>
          <div className={styles.meta}>
            <div className={styles.kpiLabel}>已启用能力</div>
            <div className={styles.kpiVal}>
              {isLoading ? '…' : `${data?.enabledCount ?? 0} / ${data?.totalCount ?? 0}`}
            </div>
            <div className={`${styles.kpiDelta} ${styles.up}`}>能力开关</div>
          </div>
        </div>
      </section>

      {/* 系统能力开关状态 */}
      <section className={styles.lowerGrid}>
        <div className={`${styles.healthCard} nx-fade`} style={{ gridColumn: '1 / -1' }}>
          <h3 className={styles.chartTitle}>系统能力状态</h3>
          <div className={styles.chartSub} style={{ marginTop: 'var(--space-1)' }}>
            各功能/登录方式的实时启用状态（GET /api/status）
          </div>
          <div className={styles.healthList}>
            {isLoading ? (
              <div className="empty">加载中…</div>
            ) : isError ? (
              <div className="empty" style={{ color: 'var(--color-danger)' }}>
                加载失败：{error instanceof Error ? error.message : '请稍后重试'}
              </div>
            ) : caps.length === 0 ? (
              <div className="empty">暂无状态数据</div>
            ) : (
              caps.map((c) => <CapabilityItem key={c.key} cap={c} />)
            )}
          </div>
        </div>
      </section>
    </AppShell>
  );
}
